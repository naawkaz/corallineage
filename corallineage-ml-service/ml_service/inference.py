import numpy as np
import torch
from PIL import Image

from ml_service.config import IMAGE_SIZE, IMAGENET_MEAN, IMAGENET_STD, MOCK_TRAINING
from ml_service.models import load_model

_annotate_jobs: dict[str, dict] = {}


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
