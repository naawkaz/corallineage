from flask import Flask, jsonify

app = Flask(__name__)

@app.route('/health', methods=['GET'])
def health_check():
    """Simple health check endpoint for Phase 0 verification."""
    return jsonify({
        "status": "healthy",
        "service": "coral-lineage-ml-service",
        "port": 5001
    }), 200

if __name__ == '__main__':
    # Force the server to run on port 5001 as specified by Phase 0
    app.run(host='0.0.0.0', port=5001, debug=True)