import os
import platform
import sys
import tempfile
import threading
import time
import uuid
from pathlib import Path

# MLflow prints an emoji run-summary banner to stdout; on Windows the console's
# default codepage can't encode it, which crashes the request rather than just
# the print. Force UTF-8 stdout/stderr regardless of how this process is launched.
if sys.stdout.encoding is None or sys.stdout.encoding.lower() != "utf-8":
    sys.stdout.reconfigure(encoding="utf-8", errors="replace")
    sys.stderr.reconfigure(encoding="utf-8", errors="replace")

import numpy as np
import torch
import torch.nn as nn
from flask import Flask, jsonify, request
from PIL import Image
from torch.utils.data import DataLoader, Dataset, random_split

app = Flask(__name__)

MOCK_TRAINING = os.environ.get("ML_SERVICE_MOCK_TRAINING", "false").lower() == "true"

ARCHITECTURES = [
    {
        "id": "efficientnet_b0",
        "displayName": "EfficientNet-B0",
        "description": "Lightweight, fast — good baseline for small datasets",
    },
    {
        "id": "resnet50",
        "displayName": "ResNet-50",
        "description": "Well-established baseline, strong general performance",
    },
    {
        "id": "vit_base_patch16_224",
        "displayName": "ViT-Base/16",
        "description": "Transformer-based — stronger on larger datasets",
    },
]
ARCHITECTURE_IDS = {a["id"] for a in ARCHITECTURES}

IMAGE_SIZE = 224
IMAGENET_MEAN = torch.tensor([0.485, 0.456, 0.406]).view(3, 1, 1)
IMAGENET_STD = torch.tensor([0.229, 0.224, 0.225]).view(3, 1, 1)
RANDOM_SEED = 42
VALIDATION_SPLIT = 0.2

# In-memory job status channel for polling GET /train/<job_id>/status while a training run is
# in flight (and after it finishes, since /train now returns immediately and the run continues
# in a background thread). Keyed by the caller-supplied jobId (backend's TrainingJob id, as a
# string). Entries are never cleaned up: this is a long-lived dev process and job ids are unique
# per training job, so unbounded growth is not a real concern at this codebase's scale.
_training_jobs: dict[str, dict] = {}

# Same idea for /annotate: keyed by the caller-supplied jobId (backend's point-annotation job id,
# as a string). Tracks per-image progress while a batch of annotate predictions runs in the
# background, plus the final result once complete.
_annotate_jobs: dict[str, dict] = {}


@app.route("/health", methods=["GET"])
def health_check():
    """Simple health check endpoint for Phase 0 verification."""
    return jsonify({
        "status": "healthy",
        "service": "coral-lineage-ml-service",
        "port": 5001
    }), 200


@app.route("/architectures", methods=["GET"])
def architectures():
    return jsonify({"architectures": ARCHITECTURES}), 200


def load_model(architecture: str, num_classes: int):
    """Loads a curated architecture via timm, fine-tune ready with a fresh classifier head."""
    if architecture not in ARCHITECTURE_IDS:
        raise ValueError(f"Unsupported architecture: {architecture}")
    import timm
    return timm.create_model(architecture, pretrained=True, num_classes=num_classes)


class ImageFolderDataset(Dataset):
    """Minimal ImageFolder-equivalent: walks datasetPath/<class>/<image> without torchvision.

    Avoids depending on torchvision's dataset/transform utilities so the service
    only needs torchvision's model zoo (already required by timm).
    """

    def __init__(self, root: str):
        root_path = Path(root)
        self.classes = sorted(p.name for p in root_path.iterdir() if p.is_dir())
        if len(self.classes) < 2:
            raise ValueError(f"Need at least 2 classes to train, found {len(self.classes)} in {root}")
        self.class_to_idx = {c: i for i, c in enumerate(self.classes)}
        self.samples = []
        for class_name in self.classes:
            class_dir = root_path / class_name
            for image_path in class_dir.iterdir():
                if image_path.suffix.lower() in (".jpg", ".jpeg", ".png"):
                    self.samples.append((image_path, self.class_to_idx[class_name]))
        if len(self.samples) == 0:
            raise ValueError(f"No images found under {root}")

    def __len__(self):
        return len(self.samples)

    def __getitem__(self, idx):
        image_path, label = self.samples[idx]
        with Image.open(image_path) as img:
            img = img.convert("RGB").resize((IMAGE_SIZE, IMAGE_SIZE))
            tensor = torch.from_numpy(np.array(img)).permute(2, 0, 1).float() / 255.0
            tensor = (tensor - IMAGENET_MEAN) / IMAGENET_STD
        return tensor, label


def log_confusion_matrix(y_true: list, y_pred: list, class_names: list):
    """Plots and logs a confusion matrix as an MLflow artifact (confusion_matrix.png)."""
    import matplotlib
    matplotlib.use("Agg")
    import matplotlib.pyplot as plt
    import mlflow

    n = len(class_names)
    matrix = np.zeros((n, n), dtype=int)
    for true_label, pred_label in zip(y_true, y_pred):
        matrix[true_label][pred_label] += 1

    fig, ax = plt.subplots(figsize=(max(4, n), max(4, n)))
    ax.imshow(matrix, cmap="Blues")
    ax.set_xticks(range(n))
    ax.set_yticks(range(n))
    ax.set_xticklabels(class_names, rotation=45, ha="right")
    ax.set_yticklabels(class_names)
    ax.set_xlabel("Predicted")
    ax.set_ylabel("Actual")
    ax.set_title("Confusion Matrix")
    for i in range(n):
        for j in range(n):
            ax.text(j, i, str(matrix[i][j]), ha="center", va="center")
    fig.tight_layout()

    with tempfile.TemporaryDirectory() as tmp_dir:
        image_path = Path(tmp_dir) / "confusion_matrix.png"
        fig.savefig(image_path)
        plt.close(fig)
        mlflow.log_artifact(str(image_path))


def split_train_val(dataset: "ImageFolderDataset"):
    """Splits the dataset into a train subset and a held-out validation subset.

    Uses a fixed-seed generator so the split is reproducible across runs of the
    same dataset (same RANDOM_SEED already used for weight init). Guards the
    tiny-dataset edge case (this is a research prototype, datasets can be a
    handful of images) so both subsets always have at least one sample.
    """
    total = len(dataset)
    val_size = max(1, round(total * VALIDATION_SPLIT))
    if val_size >= total:
        val_size = total - 1
    train_size = total - val_size
    generator = torch.Generator().manual_seed(RANDOM_SEED)
    return random_split(dataset, [train_size, val_size], generator=generator)


def run_training(architecture: str, dataset_path: str, hyperparameters: dict, job_id: str):
    learning_rate = float(hyperparameters.get("learningRate", 0.001))
    batch_size = int(hyperparameters.get("batchSize", 32))
    epochs = int(hyperparameters.get("epochs", 10))

    torch.manual_seed(RANDOM_SEED)

    dataset = ImageFolderDataset(dataset_path)
    class_mappings = {str(i): name for name, i in dataset.class_to_idx.items()}

    train_subset, val_subset = split_train_val(dataset)
    train_loader = DataLoader(train_subset, batch_size=batch_size, shuffle=True)
    val_loader = DataLoader(val_subset, batch_size=batch_size, shuffle=False)
    # Full-dataset loader, unchanged from before the validation split: the final
    # per-class confusion matrix and reported accuracy still summarize the whole
    # dataset rather than shrinking to only the validation slice.
    full_loader = DataLoader(dataset, batch_size=batch_size, shuffle=False)

    model = load_model(architecture, num_classes=len(dataset.classes))
    optimizer = torch.optim.Adam(model.parameters(), lr=learning_rate)
    criterion = nn.CrossEntropyLoss()

    import mlflow
    import mlflow.pytorch

    mlflow.set_tracking_uri(os.environ.get("MLFLOW_TRACKING_URI", "http://localhost:5000"))
    mlflow.pytorch.autolog()

    per_class_correct = {i: 0 for i in range(len(dataset.classes))}
    per_class_total = {i: 0 for i in range(len(dataset.classes))}

    with mlflow.start_run() as run:
        # Make the run id available to pollers as early as possible, before the epoch loop
        # even starts, so GET /train/<job_id>/status can report mlflowRunId right away and
        # the caller can start polling MLflow's own metric history for live epoch progress.
        _training_jobs[job_id] = {
            "status": "RUNNING",
            "mlflowRunId": run.info.run_id,
            "result": None,
            "error": None,
        }
        mlflow.log_param("architecture", architecture)
        mlflow.log_param("pythonVersion", platform.python_version())
        mlflow.log_param("pytorchVersion", torch.__version__)
        mlflow.log_param("cudaVersion", "cpu")
        mlflow.log_param("randomSeed", str(RANDOM_SEED))
        mlflow.log_param("validationSplit", str(VALIDATION_SPLIT))
        for epoch in range(epochs):
            model.train()
            train_loss_total = 0.0
            train_total = 0
            for images, labels in train_loader:
                optimizer.zero_grad()
                outputs = model(images)
                loss = criterion(outputs, labels)
                loss.backward()
                optimizer.step()

                train_loss_total += loss.item() * images.size(0)
                train_total += labels.size(0)

            train_loss = train_loss_total / train_total if train_total else 0.0

            # Real held-out validation pass, standard eval-mode/no-grad loop.
            model.eval()
            val_loss_total = 0.0
            val_correct = 0
            val_total = 0
            with torch.no_grad():
                for images, labels in val_loader:
                    outputs = model(images)
                    loss = criterion(outputs, labels)
                    val_loss_total += loss.item() * images.size(0)
                    predicted = outputs.argmax(dim=1)
                    val_correct += (predicted == labels).sum().item()
                    val_total += labels.size(0)

            val_loss = val_loss_total / val_total if val_total else 0.0
            val_accuracy = val_correct / val_total if val_total else 0.0

            mlflow.log_metric("train_loss", train_loss, step=epoch)
            mlflow.log_metric("val_loss", val_loss, step=epoch)
            mlflow.log_metric("val_accuracy", val_accuracy, step=epoch)

        # Final pass to build per-class metrics over the full dataset (train and
        # validation combined), same scope as before the validation split was
        # added, so the reported overall accuracy and confusion matrix keep the
        # same meaning for existing callers.
        model.eval()
        y_true, y_pred = [], []
        with torch.no_grad():
            for images, labels in full_loader:
                outputs = model(images)
                predicted = outputs.argmax(dim=1)
                for label, pred in zip(labels.tolist(), predicted.tolist()):
                    per_class_total[label] += 1
                    if label == pred:
                        per_class_correct[label] += 1
                    y_true.append(label)
                    y_pred.append(pred)

        log_confusion_matrix(y_true, y_pred, dataset.classes)

        overall_accuracy = (
            sum(per_class_correct.values()) / sum(per_class_total.values())
            if sum(per_class_total.values())
            else 0.0
        )

        weights_dir = Path(dataset_path).parent / "weights"
        weights_dir.mkdir(parents=True, exist_ok=True)
        weights_path = weights_dir / f"{run.info.run_id}.pt"
        torch.save(model.state_dict(), weights_path)

        performance_metrics = {
            "perClass": [
                {
                    "labelCode": class_mappings[str(i)],
                    "accuracy": (per_class_correct[i] / per_class_total[i]) if per_class_total[i] else 0.0,
                    "sampleCount": per_class_total[i],
                }
                for i in range(len(dataset.classes))
            ]
        }

        result = {
            "mlflowRunId": run.info.run_id,
            "weightsPath": str(weights_path),
            "accuracy": overall_accuracy,
            "performanceMetrics": performance_metrics,
            "classMappings": class_mappings,
        }

    _training_jobs[job_id] = {
        "status": "COMPLETED",
        "mlflowRunId": result["mlflowRunId"],
        "result": result,
        "error": None,
    }
    return result


def run_mock_training(architecture: str, dataset_path: str, hyperparameters: dict, job_id: str):
    """Canned fast response, bypasses torch/mlflow entirely, for demos or when MOCK_TRAINING is set."""
    mock_run_id = f"mock-{uuid.uuid4().hex[:12]}"
    _training_jobs[job_id] = {
        "status": "RUNNING",
        "mlflowRunId": mock_run_id,
        "result": None,
        "error": None,
    }
    time.sleep(1)
    result = {
        "mlflowRunId": mock_run_id,
        "weightsPath": f"{dataset_path}/mock-weights.pt",
        "accuracy": 0.85,
        "performanceMetrics": {"perClass": []},
        "classMappings": {"0": "ACROPORA", "1": "SAND"},
    }
    _training_jobs[job_id] = {
        "status": "COMPLETED",
        "mlflowRunId": mock_run_id,
        "result": result,
        "error": None,
    }
    return result


def _run_training_job(architecture: str, dataset_path: str, hyperparameters: dict, job_id: str):
    """Background-thread entry point for a training job: runs the real or mock trainer,

    then reflects any failure into _training_jobs so pollers see a terminal FAILED status
    instead of the job silently hanging at RUNNING forever.
    """
    try:
        if MOCK_TRAINING:
            run_mock_training(architecture, dataset_path, hyperparameters, job_id=job_id)
        else:
            run_training(architecture, dataset_path, hyperparameters, job_id=job_id)
    except Exception as ex:
        existing = _training_jobs.get(job_id, {})
        _training_jobs[job_id] = {
            "status": "FAILED",
            "mlflowRunId": existing.get("mlflowRunId"),
            "result": None,
            "error": str(ex),
        }


def run_annotate(architecture: str, weights_path: str, class_mappings: dict, images: list, job_id: str):
    idx_to_label = {int(k): v for k, v in class_mappings.items()}
    num_classes = len(idx_to_label)

    model = load_model(architecture, num_classes=num_classes)
    state_dict = torch.load(weights_path, map_location="cpu")
    model.load_state_dict(state_dict)
    model.eval()

    total = len(images)
    predictions = []
    with torch.no_grad():
        for index, image_entry in enumerate(images):
            image_id = image_entry["imageId"]
            file_path = image_entry["filePath"]
            with Image.open(file_path) as img:
                img = img.convert("RGB").resize((IMAGE_SIZE, IMAGE_SIZE))
                tensor = torch.from_numpy(np.array(img)).permute(2, 0, 1).float() / 255.0
                tensor = (tensor - IMAGENET_MEAN) / IMAGENET_STD
            logits = model(tensor.unsqueeze(0))
            probabilities = torch.softmax(logits, dim=1).squeeze(0)
            predicted_idx = int(torch.argmax(probabilities).item())
            confidence = float(probabilities[predicted_idx].item())
            predictions.append({
                "imageId": image_id,
                "points": [{
                    "pointIndex": 0,
                    "predictedLabelCode": idx_to_label[predicted_idx],
                    "confidenceScore": confidence,
                }],
            })
            _annotate_jobs[job_id] = {
                "status": "RUNNING",
                "processedCount": index + 1,
                "totalCount": total,
                "currentImageId": image_id,
                "result": None,
                "error": None,
            }

    result = {"predictions": predictions}
    _annotate_jobs[job_id] = {
        "status": "COMPLETED",
        "processedCount": total,
        "totalCount": total,
        "currentImageId": None,
        "result": result,
        "error": None,
    }
    return result


def run_mock_annotate(images: list, job_id: str):
    """Canned instant response, bypasses torch entirely, mirrors run_mock_training's demo path."""
    total = len(images)
    predictions = []
    for index, image_entry in enumerate(images):
        predictions.append({
            "imageId": image_entry["imageId"],
            "points": [{"pointIndex": 0, "predictedLabelCode": "ACROPORA", "confidenceScore": 0.85}],
        })
        _annotate_jobs[job_id] = {
            "status": "RUNNING",
            "processedCount": index + 1,
            "totalCount": total,
            "currentImageId": image_entry["imageId"],
            "result": None,
            "error": None,
        }

    result = {"predictions": predictions}
    _annotate_jobs[job_id] = {
        "status": "COMPLETED",
        "processedCount": total,
        "totalCount": total,
        "currentImageId": None,
        "result": result,
        "error": None,
    }
    return result


def _run_annotate_job(architecture: str, weights_path: str, class_mappings: dict, images: list, job_id: str):
    """Background-thread entry point for an annotate batch, mirrors _run_training_job's shape."""
    try:
        if MOCK_TRAINING:
            run_mock_annotate(images, job_id)
        else:
            run_annotate(architecture, weights_path, class_mappings, images, job_id)
    except Exception as ex:
        existing = _annotate_jobs.get(job_id, {})
        _annotate_jobs[job_id] = {
            "status": "FAILED",
            "processedCount": existing.get("processedCount", 0),
            "totalCount": existing.get("totalCount", len(images)),
            "currentImageId": None,
            "result": None,
            "error": str(ex),
        }


@app.route("/annotate", methods=["POST"])
def annotate():
    payload = request.get_json(force=True, silent=True) or {}
    architecture = payload.get("architecture")
    weights_path = payload.get("weightsPath")
    class_mappings = payload.get("classMappings")
    images = payload.get("images", [])
    job_id = payload.get("jobId")

    if architecture not in ARCHITECTURE_IDS:
        return jsonify({"error": f"Unsupported architecture: {architecture}"}), 400
    if not weights_path or not Path(weights_path).is_file():
        return jsonify({"error": f"weightsPath does not exist: {weights_path}"}), 400
    if not class_mappings:
        return jsonify({"error": "classMappings is required"}), 400
    if not images:
        return jsonify({"error": "images list is empty"}), 400
    if not job_id:
        return jsonify({"error": "jobId is required"}), 400

    _annotate_jobs[job_id] = {
        "status": "RUNNING",
        "processedCount": 0,
        "totalCount": len(images),
        "currentImageId": None,
        "result": None,
        "error": None,
    }
    thread = threading.Thread(
        target=_run_annotate_job,
        args=(architecture, weights_path, class_mappings, images, job_id),
        daemon=True,
    )
    thread.start()
    return jsonify({"status": "STARTED", "jobId": job_id}), 202


@app.route("/annotate/<job_id>/status", methods=["GET"])
def annotate_status(job_id):
    return jsonify(_annotate_jobs.get(job_id, {"status": "UNKNOWN"})), 200


@app.route("/train", methods=["POST"])
def train():
    payload = request.get_json(force=True, silent=True) or {}
    architecture = payload.get("architecture")
    dataset_path = payload.get("datasetPath")
    hyperparameters = payload.get("hyperparameters", {})
    job_id = payload.get("jobId")

    if architecture not in ARCHITECTURE_IDS:
        return jsonify({"error": f"Unsupported architecture: {architecture}"}), 400
    if not dataset_path or not Path(dataset_path).is_dir():
        return jsonify({"error": f"datasetPath does not exist: {dataset_path}"}), 400
    if not MOCK_TRAINING and not job_id:
        return jsonify({"error": "jobId is required"}), 400

    # Mock mode may not be given a jobId by every existing caller; synthesize one so the
    # background thread and status dict still have a key to work with.
    if not job_id:
        job_id = f"mock-job-{uuid.uuid4().hex[:12]}"

    thread = threading.Thread(
        target=_run_training_job,
        args=(architecture, dataset_path, hyperparameters, job_id),
        daemon=True,
    )
    thread.start()
    return jsonify({"status": "STARTED", "jobId": job_id}), 202


@app.route("/train/<job_id>/status", methods=["GET"])
def train_status(job_id):
    return jsonify(_training_jobs.get(job_id, {"status": "UNKNOWN"})), 200


if __name__ == "__main__":
    # Force the server to run on port 5001 as specified by Phase 0. threaded=True lets
    # short-lived status-poll requests and job-kickoff requests be served concurrently
    # with an in-flight background job thread, instead of queuing behind it.
    app.run(host="0.0.0.0", port=5001, debug=True, threaded=True)
