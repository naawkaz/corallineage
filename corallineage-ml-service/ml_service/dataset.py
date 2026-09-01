from pathlib import Path

import numpy as np
import torch
from PIL import Image
from torch.utils.data import Dataset, random_split

from ml_service.config import IMAGE_SIZE, IMAGENET_MEAN, IMAGENET_STD, RANDOM_SEED, VALIDATION_SPLIT


class ImageFolderDataset(Dataset):
    # Avoids depending on torchvision's dataset/transform utilities so the service
    # only needs torchvision's model zoo

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


def split_train_val(dataset: "ImageFolderDataset"):
    # Splits the dataset into a train subset and a held-out validation subset.
    # Uses a fixed-seed generator so the split is reproducible across runs of the
    # same dataset.
    
    total = len(dataset)
    val_size = max(1, round(total * VALIDATION_SPLIT))
    if val_size >= total:
        val_size = total - 1
    train_size = total - val_size
    generator = torch.Generator().manual_seed(RANDOM_SEED)
    return random_split(dataset, [train_size, val_size], generator=generator)
