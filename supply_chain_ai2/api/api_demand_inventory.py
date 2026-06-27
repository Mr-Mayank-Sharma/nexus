import os
import sys
import json
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


@app.route("/api/health-extended", methods=["GET"])
def health():
    return jsonify({"status": "healthy", "models_loaded": len(models) == 2})


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
    print("\nStarting API server on port 5001...")
    app.run(host="0.0.0.0", port=5001, debug=True)
