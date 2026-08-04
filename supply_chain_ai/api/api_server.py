import os
import sys
import json
import time
import threading
import numpy as np
import pandas as pd
import joblib
from flask import Flask, request, jsonify
from flask_cors import CORS

app = Flask(__name__)
CORS(app)

MODEL_DIR = os.path.join(os.path.dirname(os.path.dirname(__file__)), "models")

models = {}
encoders = {}
scalers = {}
loaded = False

# Track file modification times for hot-reload detection
_model_mtimes = {}

MODEL_CONFIGS = {
    "order_routing": {
        "model": "order_routing_model.pkl",
        "label_encoder": "order_routing_label_encoder.pkl",
        "fe_encoders": "order_routing_fe_encoders.pkl",
        "encoder_key": "order_routing_fe",
        "label_key": "order_routing_label",
    },
    "shipping_aggregator": {
        "model": "shipping_aggregator_model.pkl",
        "label_encoder": "shipping_aggregator_label_encoder.pkl",
        "fe_encoders": "shipping_aggregator_fe_encoders.pkl",
        "encoder_key": "shipping_aggregator_fe",
        "label_key": "shipping_aggregator_label",
    },
    "box_size": {
        "model": "box_size_model.pkl",
        "label_encoder": "box_size_label_encoder.pkl",
        "fe_encoders": "box_size_fe_encoders.pkl",
        "encoder_key": "box_size_fe",
        "label_key": "box_size_label",
    },
    "pick_pack": {
        "model": "pick_pack_model.pkl",
        "scaler": "pick_pack_scaler.pkl",
    },
}


def load_models():
    global loaded
    try:
        models["order_routing"] = joblib.load(os.path.join(MODEL_DIR, "order_routing_model.pkl"))
        encoders["order_routing_label"] = joblib.load(os.path.join(MODEL_DIR, "order_routing_label_encoder.pkl"))
        encoders["order_routing_fe"] = joblib.load(os.path.join(MODEL_DIR, "order_routing_fe_encoders.pkl"))
        print("[OK] Order routing model loaded")
    except Exception as e:
        print(f"[ERR] Order routing: {e}")

    try:
        models["shipping_aggregator"] = joblib.load(os.path.join(MODEL_DIR, "shipping_aggregator_model.pkl"))
        encoders["shipping_aggregator_label"] = joblib.load(os.path.join(MODEL_DIR, "shipping_aggregator_label_encoder.pkl"))
        encoders["shipping_aggregator_fe"] = joblib.load(os.path.join(MODEL_DIR, "shipping_aggregator_fe_encoders.pkl"))
        print("[OK] Shipping aggregator model loaded")
    except Exception as e:
        print(f"[ERR] Shipping aggregator: {e}")

    try:
        models["box_size"] = joblib.load(os.path.join(MODEL_DIR, "box_size_model.pkl"))
        encoders["box_size_label"] = joblib.load(os.path.join(MODEL_DIR, "box_size_label_encoder.pkl"))
        encoders["box_size_fe"] = joblib.load(os.path.join(MODEL_DIR, "box_size_fe_encoders.pkl"))
        print("[OK] Box size model loaded")
    except Exception as e:
        print(f"[ERR] Box size: {e}")

    try:
        models["pick_pack"] = joblib.load(os.path.join(MODEL_DIR, "pick_pack_model.pkl"))
        scalers["pick_pack"] = joblib.load(os.path.join(MODEL_DIR, "pick_pack_scaler.pkl"))
        print("[OK] Pick/pack model loaded")
    except Exception as e:
        print(f"[ERR] Pick/pack: {e}")

    loaded = True
    print(f"Loaded {len(models)} models")

    # Record mtimes for hot-reload detection
    _track_mtimes()


def _track_mtimes():
    """Record modification times of all model files."""
    for name, config in MODEL_CONFIGS.items():
        for artifact_key in ["model", "label_encoder", "fe_encoders", "scaler"]:
            filename = config.get(artifact_key)
            if filename:
                path = os.path.join(MODEL_DIR, filename)
                if os.path.exists(path):
                    _model_mtimes[path] = os.path.getmtime(path)


def _reload_changed_models():
    """Check for changed model files and hot-reload them. Called by watcher thread."""
    changed = []
    for path, old_mtime in list(_model_mtimes.items()):
        try:
            new_mtime = os.path.getmtime(path)
            if new_mtime > old_mtime:
                changed.append(path)
        except OSError:
            pass

    if not changed:
        return

    print(f"[HOT-RELOAD] Detected changes in: {[os.path.basename(p) for p in changed]}")

    # Determine which models need reloading
    reload_models = set()
    for path in changed:
        basename = os.path.basename(path)
        for name, config in MODEL_CONFIGS.items():
            for artifact_key in ["model", "label_encoder", "fe_encoders", "scaler"]:
                if config.get(artifact_key) == basename:
                    reload_models.add(name)

    for name in reload_models:
        try:
            config = MODEL_CONFIGS[name]
            model_path = os.path.join(MODEL_DIR, config["model"])
            models[name] = joblib.load(model_path)

            if "label_encoder" in config:
                encoders[config["label_key"]] = joblib.load(os.path.join(MODEL_DIR, config["label_encoder"]))
            if "fe_encoders" in config:
                encoders[config["encoder_key"]] = joblib.load(os.path.join(MODEL_DIR, config["fe_encoders"]))
            if "scaler" in config:
                scalers[name] = joblib.load(os.path.join(MODEL_DIR, config["scaler"]))

            print(f"[HOT-RELOAD] Successfully reloaded model: {name}")
        except Exception as e:
            print(f"[HOT-RELOAD] Failed to reload model {name}: {e}")

    _track_mtimes()


def _model_watcher(interval_seconds=30):
    """Background thread that polls for model file changes."""
    while True:
        time.sleep(interval_seconds)
        try:
            _reload_changed_models()
        except Exception as e:
            print(f"[HOT-RELOAD] Watcher error: {e}")


def encode_features(features_df, fe_encoders):
    X = features_df.copy()
    cat_cols = [c for c in X.columns if c in fe_encoders]
    for col in cat_cols:
        le = fe_encoders[col]
        X[col] = X[col].map(lambda v: le.transform([v])[0] if v in le.classes_ else -1)
    for col in X.columns:
        X[col] = pd.to_numeric(X[col], errors="coerce").fillna(0).astype(float)
    return X


@app.route("/api/health", methods=["GET"])
def health():
    return jsonify({"status": "healthy", "models_loaded": len(models) == 4})


@app.route("/api/warmup", methods=["POST"])
def warmup():
    """Pre-warm models with a synthetic request to avoid cold-start timeouts.
    Called by Spring Boot AiHealthCheck during startup."""
    warmed = []
    for name, model in models.items():
        try:
            if name == "order_routing":
                test = pd.DataFrame([{
                    "shipping_address_city": "Mumbai", "shipping_address_pincode": 400001,
                    "latitude": 19.08, "longitude": 72.88, "item_category": "Electronics",
                    "item_weight_kg": 1.0, "item_quantity": 1, "channel": "Amazon"
                }])
                enc = encoders.get("order_routing_fe")
                le = encoders.get("order_routing_label")
                if enc and le:
                    X = encode_features(test, enc)
                    model.predict_proba(X)
                    warmed.append(name)
            elif name == "shipping_aggregator":
                test = pd.DataFrame([{
                    "origin_warehouse": "WH_DELHI", "shipping_address_city": "Mumbai",
                    "item_weight_kg": 1.0, "item_category": "Electronics", "box_size": "MEDIUM"
                }])
                enc = encoders.get("shipping_aggregator_fe")
                le = encoders.get("shipping_aggregator_label")
                if enc and le:
                    X = encode_features(test, enc)
                    model.predict_proba(X)
                    warmed.append(name)
            elif name == "box_size":
                test = pd.DataFrame([{
                    "item_quantity": 1, "item_weight_kg": 1.0, "item_category": "Electronics",
                    "item_price": 1000, "is_fragile": 0
                }])
                enc = encoders.get("box_size_fe")
                le = encoders.get("box_size_label")
                if enc and le:
                    X = encode_features(test, enc)
                    model.predict_proba(X)
                    warmed.append(name)
            elif name == "pick_pack":
                test = pd.DataFrame([{
                    "item_count": 1, "total_weight": 1.0, "box_size": 2,
                    "items_distance_meters": 50, "warehouse_layout_type": 1
                }])
                sc = scalers.get("pick_pack")
                if sc:
                    X = sc.transform(test)
                    model.predict(X)
                    warmed.append(name)
        except Exception as e:
            print(f"[WARMUP] Failed to warm {name}: {e}")

    return jsonify({"warmed": warmed, "total": len(models)})


@app.route("/api/predict/route", methods=["POST"])
def predict_route():
    try:
        data = request.get_json()
        if not data:
            return jsonify({"error": "No input data provided"}), 400
        req_df = pd.DataFrame([data])
        fe_cols = ["shipping_address_city", "shipping_address_pincode", "latitude", "longitude",
                   "item_category", "item_weight_kg", "item_quantity", "channel"]
        missing = [c for c in fe_cols if c not in req_df.columns]
        if missing:
            return jsonify({"error": f"Missing features: {missing}"}), 400
        features = req_df[fe_cols]
        X = encode_features(features, encoders["order_routing_fe"])
        model = models.get("order_routing")
        le = encoders.get("order_routing_label")
        if model is None or le is None:
            return jsonify({"error": "Order routing model not loaded"}), 500
        proba = model.predict_proba(X)[0]
        pred_idx = int(np.argmax(proba))
        confidence = float(proba[pred_idx])
        warehouse = le.inverse_transform([pred_idx])[0]
        return jsonify({"warehouse": warehouse, "confidence": round(confidence, 4)})
    except Exception as e:
        return jsonify({"error": str(e)}), 500


@app.route("/api/predict/carrier", methods=["POST"])
def predict_carrier():
    try:
        data = request.get_json()
        if not data:
            return jsonify({"error": "No input data provided"}), 400
        req_df = pd.DataFrame([data])
        fe_cols = ["origin_warehouse", "shipping_address_city", "item_weight_kg",
                   "item_category", "box_size"]
        missing = [c for c in fe_cols if c not in req_df.columns]
        if missing:
            return jsonify({"error": f"Missing features: {missing}"}), 400
        features = req_df[fe_cols]
        X = encode_features(features, encoders["shipping_aggregator_fe"])
        model = models.get("shipping_aggregator")
        le = encoders.get("shipping_aggregator_label")
        if model is None or le is None:
            return jsonify({"error": "Shipping aggregator model not loaded"}), 500
        proba = model.predict_proba(X)[0]
        pred_idx = int(np.argmax(proba))
        confidence = float(proba[pred_idx])
        carrier = le.inverse_transform([pred_idx])[0]
        cost_map = {"delhivery": 285, "blue_dart": 320, "ecom_express": 210, "xpressbees": 250, "shadowfax": 180}
        transit_map = {"delhivery": 2, "blue_dart": 1, "ecom_express": 4, "xpressbees": 3, "shadowfax": 2}
        return jsonify({
            "carrier": carrier,
            "cost": cost_map.get(carrier, 250),
            "transit_days": transit_map.get(carrier, 3),
            "confidence": round(confidence, 4)
        })
    except Exception as e:
        return jsonify({"error": str(e)}), 500


@app.route("/api/predict/box", methods=["POST"])
def predict_box():
    try:
        data = request.get_json()
        if not data:
            return jsonify({"error": "No input data provided"}), 400
        req_df = pd.DataFrame([data])
        fe_cols = ["item_quantity", "item_weight_kg", "item_category", "item_price"]
        missing = [c for c in fe_cols if c not in req_df.columns]
        if missing:
            return jsonify({"error": f"Missing features: {missing}"}), 400
        features = req_df[fe_cols]
        features["is_fragile"] = int(data.get("item_price", 0) > 10000 or data.get("item_weight_kg", 0) > 8)
        X = encode_features(features, encoders["box_size_fe"])
        model = models.get("box_size")
        le = encoders.get("box_size_label")
        if model is None or le is None:
            return jsonify({"error": "Box size model not loaded"}), 500
        proba = model.predict_proba(X)[0]
        pred_idx = int(np.argmax(proba))
        confidence = float(proba[pred_idx])
        box_size = le.inverse_transform([pred_idx])[0]
        weight = data.get("item_weight_kg", 1)
        qty = data.get("item_quantity", 1)
        box_count = max(1, int(np.ceil(weight * qty / 5)))
        return jsonify({"box_size": box_size, "box_count": box_count, "confidence": round(confidence, 4)})
    except Exception as e:
        return jsonify({"error": str(e)}), 500


@app.route("/api/predict/pick-pack", methods=["POST"])
def predict_pick_pack():
    try:
        data = request.get_json()
        if not data:
            return jsonify({"error": "No input data provided"}), 400
        req_df = pd.DataFrame([data])
        box_map = {"SMALL": 1, "MEDIUM": 2, "LARGE": 3, "EXTRA_LARGE": 4}
        layout_map = {"Grid": 0, "Zone": 1, "Flow": 2, "Custom": 3}
        features = pd.DataFrame({
            "item_count": [data.get("item_count", 1)],
            "total_weight": [data.get("total_weight", 1.0)],
            "box_size": [box_map.get(data.get("box_size", "MEDIUM"), 2)],
            "items_distance_meters": [data.get("items_distance_meters", 50)],
            "warehouse_layout_type": [layout_map.get(data.get("warehouse_layout_type", "Zone"), 1)],
        })
        for col in features.columns:
            features[col] = pd.to_numeric(features[col], errors="coerce").fillna(0).astype(float)
        scaler = scalers.get("pick_pack")
        model = models.get("pick_pack")
        if model is None or scaler is None:
            return jsonify({"error": "Pick/pack model not loaded"}), 500
        X_scaled = scaler.transform(features)
        pred = float(model.predict(X_scaled)[0])
        pickers = max(1, int(np.ceil(data.get("item_count", 1) / 5)))
        strategy = "FIFO"
        return jsonify({"pickers_required": pickers, "estimated_minutes": round(pred, 1), "picking_strategy": strategy})
    except Exception as e:
        return jsonify({"error": str(e)}), 500


def generate_sample_requests():
    return {
        "route": {
            "shipping_address_city": "Mumbai", "shipping_address_pincode": 400001,
            "latitude": 19.08, "longitude": 72.88, "item_category": "Electronics",
            "item_weight_kg": 2.5, "item_quantity": 1, "channel": "Amazon"
        },
        "carrier": {
            "origin_warehouse": "WH_DELHI", "shipping_address_city": "Mumbai",
            "item_weight_kg": 2.5, "item_category": "Electronics", "box_size": "MEDIUM"
        },
        "box": {
            "item_quantity": 2, "item_weight_kg": 3.0, "item_category": "Home & Kitchen", "item_price": 1500
        },
        "pick_pack": {
            "item_count": 3, "total_weight": 4.5, "box_size": "MEDIUM",
            "items_distance_meters": 45, "warehouse_layout_type": "Zone"
        },
    }


if __name__ == "__main__":
    print("Loading models...")
    load_models()
    print("\nSample test requests:")
    print(json.dumps(generate_sample_requests(), indent=2))
    print("\nStarting model file watcher (30s interval)...")
    watcher = threading.Thread(target=_model_watcher, args=(30,), daemon=True)
    watcher.start()
    print("\nStarting API server on port 5000...")
    app.run(host="0.0.0.0", port=5000, debug=True)
