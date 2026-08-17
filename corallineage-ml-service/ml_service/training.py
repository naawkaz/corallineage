import os
import platform
import time
import uuid
from pathlib import Path

import torch
import torch.nn as nn
from torch.utils.data import DataLoader

from ml_service.config import MOCK_TRAINING, RANDOM_SEED, VALIDATION_SPLIT
from ml_service.dataset import ImageFolderDataset, split_train_val
from ml_service.diagnostics import log_confusion_matrix
from ml_service.models import load_model

# In-memory job status channel for polling GET /train/<job_id>/status while a training run is
# in flight (and after it finishes, since /train now returns immediately and the run continues
# in a background thread). Keyed by the caller-supplied jobId (backend's TrainingJob id, as a
# string). Entries are never cleaned up: this is a long-lived dev process and job ids are unique
# per training job, so unbounded growth is not a real concern at this codebase's scale.
_training_jobs: dict[str, dict] = {}


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
