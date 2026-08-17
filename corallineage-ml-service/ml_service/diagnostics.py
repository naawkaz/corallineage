import tempfile
from pathlib import Path

import numpy as np


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
