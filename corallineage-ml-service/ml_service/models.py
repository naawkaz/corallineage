from ml_service.config import ARCHITECTURE_IDS


def load_model(architecture: str, num_classes: int):
    """Loads a curated architecture via timm, fine-tune ready with a fresh classifier head."""
    if architecture not in ARCHITECTURE_IDS:
        raise ValueError(f"Unsupported architecture: {architecture}")
    import timm
    return timm.create_model(architecture, pretrained=True, num_classes=num_classes)
