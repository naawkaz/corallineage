import sys
import threading
import uuid
from pathlib import Path

# MLflow prints an emoji run-summary banner to stdout; on Windows the console's
# default codepage can't encode it, which crashes the request rather than just
# the print. Force UTF-8 stdout/stderr regardless of how this process is launched.
if sys.stdout.encoding is None or sys.stdout.encoding.lower() != "utf-8":
    sys.stdout.reconfigure(encoding="utf-8", errors="replace")
    sys.stderr.reconfigure(encoding="utf-8", errors="replace")

from flask import Flask, jsonify, request

from ml_service import inference, training
from ml_service.config import ARCHITECTURES, ARCHITECTURE_IDS, MOCK_TRAINING

app = Flask(__name__)


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

    inference._annotate_jobs[job_id] = {
        "status": "RUNNING",
        "processedCount": 0,
        "totalCount": len(images),
        "currentImageId": None,
        "result": None,
        "error": None,
    }
    thread = threading.Thread(
        target=inference._run_annotate_job,
        args=(architecture, weights_path, class_mappings, images, job_id),
        daemon=True,
    )
    thread.start()
    return jsonify({"status": "STARTED", "jobId": job_id}), 202


@app.route("/annotate/<job_id>/status", methods=["GET"])
def annotate_status(job_id):
    return jsonify(inference._annotate_jobs.get(job_id, {"status": "UNKNOWN"})), 200


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
        target=training._run_training_job,
        args=(architecture, dataset_path, hyperparameters, job_id),
        daemon=True,
    )
    thread.start()
    return jsonify({"status": "STARTED", "jobId": job_id}), 202


@app.route("/train/<job_id>/status", methods=["GET"])
def train_status(job_id):
    return jsonify(training._training_jobs.get(job_id, {"status": "UNKNOWN"})), 200


if __name__ == "__main__":
    # Force the server to run on port 5001 as specified by Phase 0. threaded=True lets
    # short-lived status-poll requests and job-kickoff requests be served concurrently
    # with an in-flight background job thread, instead of queuing behind it.
    app.run(host="0.0.0.0", port=5001, debug=True, threaded=True)
