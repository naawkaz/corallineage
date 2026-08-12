import os
import sys
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
from torch.utils.data import DataLoader, Dataset

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


def run_training(architecture: str, dataset_path: str, hyperparameters: dict):
    learning_rate = float(hyperparameters.get("learningRate", 0.001))
    batch_size = int(hyperparameters.get("batchSize", 32))
    epochs = int(hyperparameters.get("epochs", 10))

    dataset = ImageFolderDataset(dataset_path)
    class_mappings = {str(i): name for name, i in dataset.class_to_idx.items()}
    loader = DataLoader(dataset, batch_size=batch_size, shuffle=True)

    model = load_model(architecture, num_classes=len(dataset.classes))
    model.train()
    optimizer = torch.optim.Adam(model.parameters(), lr=learning_rate)
    criterion = nn.CrossEntropyLoss()

    import mlflow
    import mlflow.pytorch

    mlflow.set_tracking_uri(os.environ.get("MLFLOW_TRACKING_URI", "http://localhost:5000"))
    mlflow.pytorch.autolog()

    per_class_correct = {i: 0 for i in range(len(dataset.classes))}
    per_class_total = {i: 0 for i in range(len(dataset.classes))}

    with mlflow.start_run() as run:
        mlflow.log_param("architecture", architecture)
        for epoch in range(epochs):
            epoch_loss = 0.0
            correct = 0
            total = 0
            for images, labels in loader:
                optimizer.zero_grad()
                outputs = model(images)
                loss = criterion(outputs, labels)
                loss.backward()
                optimizer.step()

                epoch_loss += loss.item() * images.size(0)
                predicted = outputs.argmax(dim=1)
                correct += (predicted == labels).sum().item()
                total += labels.size(0)

            epoch_accuracy = correct / total if total else 0.0
            mlflow.log_metric("loss", epoch_loss / total if total else 0.0, step=epoch)
            mlflow.log_metric("accuracy", epoch_accuracy, step=epoch)

        # Final pass to build per-class metrics on the training set (no held-out
        # split — dataset is typically tiny for this prototype; C-07 explicitly
        # notes accuracy is not an evaluation criterion here).
        model.eval()
        with torch.no_grad():
            for images, labels in loader:
                outputs = model(images)
                predicted = outputs.argmax(dim=1)
                for label, pred in zip(labels.tolist(), predicted.tolist()):
                    per_class_total[label] += 1
                    if label == pred:
                        per_class_correct[label] += 1

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

        return {
            "mlflowRunId": run.info.run_id,
            "weightsPath": str(weights_path),
            "accuracy": overall_accuracy,
            "performanceMetrics": performance_metrics,
            "classMappings": class_mappings,
        }


def run_mock_training(architecture: str, dataset_path: str, hyperparameters: dict):
    """Canned fast response, bypasses torch/mlflow entirely — for demos or when MOCK_TRAINING is set."""
    time.sleep(1)
    return {
        "mlflowRunId": f"mock-{uuid.uuid4().hex[:12]}",
        "weightsPath": f"{dataset_path}/mock-weights.pt",
        "accuracy": 0.85,
        "performanceMetrics": {"perClass": []},
        "classMappings": {"0": "ACROPORA", "1": "SAND"},
    }


@app.route("/train", methods=["POST"])
def train():
    payload = request.get_json(force=True, silent=True) or {}
    architecture = payload.get("architecture")
    dataset_path = payload.get("datasetPath")
    hyperparameters = payload.get("hyperparameters", {})

    if architecture not in ARCHITECTURE_IDS:
        return jsonify({"error": f"Unsupported architecture: {architecture}"}), 400
    if not dataset_path or not Path(dataset_path).is_dir():
        return jsonify({"error": f"datasetPath does not exist: {dataset_path}"}), 400

    try:
        if MOCK_TRAINING:
            result = run_mock_training(architecture, dataset_path, hyperparameters)
        else:
            result = run_training(architecture, dataset_path, hyperparameters)
        return jsonify(result), 200
    except ValueError as ex:
        return jsonify({"error": str(ex)}), 400
    except Exception as ex:
        return jsonify({"error": f"Training failed: {ex}"}), 500


if __name__ == "__main__":
    # Force the server to run on port 5001 as specified by Phase 0
    app.run(host="0.0.0.0", port=5001, debug=True)
