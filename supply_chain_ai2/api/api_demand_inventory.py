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
scalers = {}
loaded = False

_model_mtimes = {}

MODEL_CONFIGS = {
    "demand_forecast": {
        "model": "demand_forecast_model.pkl",
        "scaler": "demand_forecast_scaler.pkl",
    },
    "inventory_optimization": {
        "model": "inventory_optimization_model.pkl",
        "scaler": "inventory_optimization_scaler.pkl",
    },
}


def load_models():
    global loaded
    try:
        models["demand_forecast"] = joblib.load(os.path.join(MODEL_DIR, "demand_forecast_model.pkl"))
        scalers["demand_forecast"] = joblib.load(os.path.join(MODEL_DIR, "demand_forecast_scaler.pkl"))
        print("[OK] Demand forecast model loaded")
    except Exception as e:
        print(f"[ERR] Demand forecast: {e}")

    try:
        models["inventory_optimization"] = joblib.load(os.path.join(MODEL_DIR, "inventory_optimization_model.pkl"))
        scalers["inventory_optimization"] = joblib.load(os.path.join(MODEL_DIR, "inventory_optimization_scaler.pkl"))
        print("[OK] Inventory optimization model loaded")
    except Exception as e:
        print(f"[ERR] Inventory optimization: {e}")

    loaded = True
    print(f"Loaded {len(models)} models")
    _track_mtimes()


def _track_mtimes():
    for name, config in MODEL_CONFIGS.items():
        for artifact_key in ["model", "scaler"]:
            filename = config.get(artifact_key)
            if filename:
                path = os.path.join(MODEL_DIR, filename)
                if os.path.exists(path):
                    _model_mtimes[path] = os.path.getmtime(path)


def _reload_changed_models():
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

    reload_models = set()
    for path in changed:
        basename = os.path.basename(path)
        for name, config in MODEL_CONFIGS.items():
            for artifact_key in ["model", "scaler"]:
                if config.get(artifact_key) == basename:
                    reload_models.add(name)

    for name in reload_models:
        try:
            config = MODEL_CONFIGS[name]
            models[name] = joblib.load(os.path.join(MODEL_DIR, config["model"]))
            if "scaler" in config:
                scalers[name] = joblib.load(os.path.join(MODEL_DIR, config["scaler"]))
            print(f"[HOT-RELOAD] Successfully reloaded model: {name}")
        except Exception as e:
            print(f"[HOT-RELOAD] Failed to reload model {name}: {e}")

    _track_mtimes()


def _model_watcher(interval_seconds=30):
    while True:
        time.sleep(interval_seconds)
        try:
            _reload_changed_models()
        except Exception as e:
            print(f"[HOT-RELOAD] Watcher error: {e}")


@app.route("/api/health-extended", methods=["GET"])
def health():
    return jsonify({"status": "healthy", "models_loaded": len(models) == 2})


@app.route("/api/warmup", methods=["POST"])
def warmup():
    """Pre-warm models with synthetic input to avoid cold-start timeouts."""
    warmed = []
    for name, model in models.items():
        try:
            if name == "demand_forecast":
                test = pd.DataFrame([{
                    "day_of_week": 2, "month": 6, "day_of_month": 15, "weekend": 0,
                    "days_to_event": 0, "is_festive_season": 0,
                    "lag_1": 20, "lag_2": 20, "lag_3": 20, "lag_4": 20,
                    "lag_5": 20, "lag_6": 20, "lag_7": 20
                }])
                for col in test.columns:
                    test[col] = pd.to_numeric(test[col], errors="coerce").fillna(0).astype(float)
                sc = scalers.get("demand_forecast")
                if sc:
                    X = sc.transform(test)
                    model.predict(X)
                    warmed.append(name)
            elif name == "inventory_optimization":
                test = pd.DataFrame([{
                    "current_stock": 50, "demand_forecast_next_7": 100,
                    "demand_forecast_next_30": 400, "lead_time_days": 7,
                    "avg_daily_sales": 10, "safety_stock": 30, "seasonality_factor": 1.0
                }])
                for col in test.columns:
                    test[col] = pd.to_numeric(test[col], errors="coerce").fillna(0).astype(float)
                sc = scalers.get("inventory_optimization")
                if sc:
                    X = sc.transform(test)
                    model.predict_proba(X)
                    warmed.append(name)
        except Exception as e:
            print(f"[WARMUP] Failed to warm {name}: {e}")

    return jsonify({"warmed": warmed, "total": len(models)})


@app.route("/api/predict/demand", methods=["POST"])
def predict_demand():
    try:
        data = request.get_json()
        if not data:
            return jsonify({"error": "No input data provided"}), 400
        req_df = pd.DataFrame([data])
        features = pd.DataFrame()
        features["day_of_week"] = [data.get("day_of_week", 1)]
        features["month"] = [data.get("month", 6)]
        features["day_of_month"] = [data.get("day_of_month", 15)]
        features["weekend"] = [int(features["day_of_week"].iloc[0] >= 5)]
        features["days_to_event"] = [data.get("days_to_event", 0)]
        features["is_festive_season"] = [int(features["month"].iloc[0] in [10, 11, 12, 1])]
        for lag in range(1, 8):
            features[f"lag_{lag}"] = [data.get(f"lag_{lag}", 20)]
        for col in features.columns:
            features[col] = pd.to_numeric(features[col], errors="coerce").fillna(0).astype(float)
        model = models.get("demand_forecast")
        scaler = scalers.get("demand_forecast")
        if model is None or scaler is None:
            return jsonify({"error": "Demand forecast model not loaded"}), 500
        X_scaled = scaler.transform(features)
        pred_7 = float(model.predict(X_scaled)[0])
        pred_30 = pred_7 * 4.0 * np.random.uniform(0.95, 1.05)
        return jsonify({
            "next_7_days": max(0, int(round(pred_7))),
            "next_30_days": max(0, int(round(pred_30))),
            "confidence_interval": {
                "p10": max(0, int(round(pred_7 * 0.85))),
                "p90": max(0, int(round(pred_7 * 1.15)))
            }
        })
    except Exception as e:
        return jsonify({"error": str(e)}), 500


@app.route("/api/predict/inventory", methods=["POST"])
def predict_inventory():
    try:
        data = request.get_json()
        if not data:
            return jsonify({"error": "No input data provided"}), 400
        features = pd.DataFrame()
        features["current_stock"] = [data.get("current_stock", 50)]
        features["demand_forecast_next_7"] = [data.get("demand_forecast_next_7", 100)]
        features["demand_forecast_next_30"] = [data.get("demand_forecast_next_30", 400)]
        features["lead_time_days"] = [data.get("lead_time_days", 7)]
        features["avg_daily_sales"] = [data.get("avg_daily_sales", 10)]
        features["safety_stock"] = [data.get("safety_stock", 30)]
        features["seasonality_factor"] = [data.get("seasonality_factor", 1.0)]
        for col in features.columns:
            features[col] = pd.to_numeric(features[col], errors="coerce").fillna(0).astype(float)
        model = models.get("inventory_optimization")
        scaler = scalers.get("inventory_optimization")
        if model is None or scaler is None:
            return jsonify({"error": "Inventory optimization model not loaded"}), 500
        X_scaled = scaler.transform(features)
        proba = model.predict_proba(X_scaled)[0]
        pred = int(model.predict(X_scaled)[0])
        confidence = float(proba[pred]) if len(proba) > 1 else float(max(proba))
        current_stock = data.get("current_stock", 50)
        avg_sales = data.get("avg_daily_sales", 10)
        lead_time = data.get("lead_time_days", 7)
        safety = data.get("safety_stock", 30)
        recommended_qty = max(0, int((avg_sales * lead_time + safety) * 1.5 - current_stock))
        return jsonify({
            "needs_reorder": bool(pred == 1),
            "recommended_qty": max(0, recommended_qty),
            "confidence": round(confidence, 4)
        })
    except Exception as e:
        return jsonify({"error": str(e)}), 500


def generate_sample_requests():
    return {
        "demand": {
            "day_of_week": 2, "month": 11, "day_of_month": 15, "days_to_event": -5,
            "lag_1": 22, "lag_2": 18, "lag_3": 25, "lag_4": 20, "lag_5": 19, "lag_6": 23, "lag_7": 21
        },
        "inventory": {
            "current_stock": 50, "demand_forecast_next_7": 120, "demand_forecast_next_30": 480,
            "lead_time_days": 7, "avg_daily_sales": 12, "safety_stock": 40, "seasonality_factor": 1.2
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
    print("\nStarting API server on port 5001...")
    app.run(host="0.0.0.0", port=5001, debug=True)
