import os

import torch

MOCK_TRAINING = os.environ.get("ML_SERVICE_MOCK_TRAINING", "false").lower() == "true"

ARCHITECTURES = [
    {
        "id": "efficientnet_b0",
        "displayName": "EfficientNet-B0",
        "description": "Lightweight, fast - good baseline for small datasets",
    },
    {
        "id": "resnet50",
        "displayName": "ResNet-50",
        "description": "Well-established baseline, strong general performance",
    },
    {
        "id": "vit_base_patch16_224",
        "displayName": "ViT-Base/16",
        "description": "Transformer-based - stronger on larger datasets",
    },
]
ARCHITECTURE_IDS = {a["id"] for a in ARCHITECTURES}

IMAGE_SIZE = 224
IMAGENET_MEAN = torch.tensor([0.485, 0.456, 0.406]).view(3, 1, 1)
IMAGENET_STD = torch.tensor([0.229, 0.224, 0.225]).view(3, 1, 1)
RANDOM_SEED = 42
VALIDATION_SPLIT = 0.2
