#!/usr/bin/env python3
"""
Nexus OMS — Demand Forecast Trainer (Phase 0+1)

Trains a global LightGBM quantile model (P10/P50/P90) on per-SKU daily demand
exported by the OMS backend, exports it to a single ONNX artifact, and writes:

  model.onnx            median (P50) model, served in-process by ONNX Runtime
  p10.onnx / p90.onnx   lower / upper quantile models
  feature_columns.json  ordered feature list (contract shared with Java serving)
  metrics.json          holdout WAPE / MAE / RMSE / pinball loss + P10/P90 ratio factors
  calibration.json      per-SKU observed/forecast ratio baseline (global model -> tenant calibrate)

Usage:
    python3 forecast.py \
        --input demand_export.jsonl \
        --output-dir artifacts/ \
        --horizon 7

Input format (JSON Lines, one record per SKU per day):
    {"sku": "SKU-1", "date": "2024-08-19", "demand": 97}

The model is deliberately global (all SKUs pooled) with calendar + lag features;
per-SKU/per-tenant adjustment happens at serving time via the calibration table,
matching the "global model + per-tenant calibration" architecture.
"""
import argparse
import json
import math
import os
import sys
from collections import defaultdict

import numpy as np
import pandas as pd

# LightGBM is the core requirement; ONNX export backends are optional.
try:
    import lightgbm as lgb
except ImportError:
    sys.exit("lightgbm is required. Install with: pip install lightgbm")

try:
    import onnxmltools
    from onnxmltools.convert.common.data_types import FloatTensorType
    from onnxmltools.convert import convert_lightgbm
    _ONNX_BACKEND = "onnxmltools"
except ImportError:
    _ONNX_BACKEND = None

try:
    import onnxruntime
    _ORT_AVAILABLE = True
except ImportError:
    _ORT_AVAILABLE = False

FEATURE_COLUMNS = [
    "day_of_week", "month", "is_weekend", "is_festive_season",
    "lag_1", "lag_2", "lag_3", "lag_4", "lag_5", "lag_6", "lag_7",
    "rolling_mean_7", "rolling_std_7",
]
LOOKBACK = 7
FESTIVE_MONTHS = {10, 11, 12, 1}


def load_data(path):
    if not os.path.exists(path):
        sys.exit(f"Input file not found: {path}")
    rows = []
    if path.endswith(".jsonl"):
        with open(path) as f:
            for line in f:
                line = line.strip()
                if line:
                    rows.append(json.loads(line))
    else:  # csv
        df = pd.read_csv(path)
        df = df.rename(columns=lambda c: c.strip().lower())
        date_col = "demand_date" if "demand_date" in df.columns else "date"
        df[date_col] = pd.to_datetime(df[date_col])
        return df

    if not rows:
        sys.exit("No records in input file")
    df = pd.DataFrame(rows)
    df["date"] = pd.to_datetime(df["date"])
    return df


def aggregate_daily(df):
    """Aggregate order-level rows into (sku, date) -> daily demand."""
    grp = df.groupby(["sku", "date"])["demand"].sum().reset_index()
    return grp


def build_features(df):
    """Per-SKU feature rows: calendar + lag + rolling window features."""
    frames = []
    for sku, group in df.groupby("sku", sort=False):
        group = group.sort_values("date").reset_index(drop=True)
        features = pd.DataFrame(index=group.index)
        features["sku"] = sku
        features["date"] = group["date"]
        features["demand"] = group["demand"]
        features["day_of_week"] = group["date"].dt.dayofweek
        features["month"] = group["date"].dt.month
        features["is_weekend"] = (features["day_of_week"] >= 5).astype(int)
        features["is_festive_season"] = features["month"].isin(FESTIVE_MONTHS).astype(int)
        for lag in range(1, LOOKBACK + 1):
            features[f"lag_{lag}"] = group["demand"].shift(lag)
        features["rolling_mean_7"] = group["demand"].shift(1).rolling(LOOKBACK).mean()
        features["rolling_std_7"] = group["demand"].shift(1).rolling(LOOKBACK).std()
        frames.append(features)
    out = pd.concat(frames, ignore_index=True)
    out[FEATURE_COLUMNS] = out[FEATURE_COLUMNS].fillna(0.0).astype(float)
    return out


def pinball_loss(y_true, y_pred, alpha):
    err = y_true - y_pred
    return float(np.mean(np.where(err >= 0, alpha * err, (alpha - 1) * err)))


def evaluate(y_true, y_pred, alpha):
    y_true = np.asarray(y_true, dtype=float)
    y_pred = np.asarray(y_pred, dtype=float)
    mae = float(np.mean(np.abs(y_true - y_pred)))
    rmse = float(np.sqrt(np.mean((y_true - y_pred) ** 2)))
    wape = float(np.sum(np.abs(y_true - y_pred)) / max(np.sum(np.abs(y_true)), 1.0))
    return {
        "mae": round(mae, 4),
        "rmse": round(rmse, 4),
        "wape": round(wape, 4),
        "pinball": round(pinball_loss(y_true, y_pred, alpha), 4),
    }


def calibration_baseline(history, horizon):
    """Per-SKU ratio of observed holdout demand to median forecast (a0 = 1.0)."""
    baseline = {}
    for sku, group in history.groupby("sku", sort=False):
        actual = float(group["demand"].sum())
        pred = float(group["prediction"].sum())
        baseline[sku] = round(actual / max(pred, 1e-6), 4)
    return baseline


def train_and_export(df, output_dir, horizon):
    os.makedirs(output_dir, exist_ok=True)
    print(f"Loaded {len(df)} daily demand records across {df['sku'].nunique()} SKUs")

    featured = build_features(df)
    featured = featured.dropna(subset=["demand"])
    if featured.empty:
        sys.exit("No complete feature rows after lag window; need >7 days of history per SKU")

    # Chronological split (no leakage)
    dates = featured["date"].sort_values().reset_index(drop=True)
    split_idx = int(len(featured) * 0.8)
    split_date = dates.iloc[split_idx]
    train = featured[featured["date"] < split_date]
    test = featured[featured["date"] >= split_date]
    print(f"Train: {len(train)} rows, Holdout: {len(test)} rows (split at {split_date.date()})")

    X_train = train[FEATURE_COLUMNS]
    y_train = train["demand"]
    X_test = test[FEATURE_COLUMNS]
    y_test = test["demand"]

    params = {
        "objective": "quantile",
        "learning_rate": 0.05,
        "num_leaves": 63,
        "min_data_in_leaf": 20,
        "feature_fraction": 0.9,
        "bagging_fraction": 0.9,
        "bagging_freq": 1,
        "verbose": -1,
        "seed": 42,
    }

    artifacts = []
    quantile_models = {}
    for alpha, name in [(0.5, "model"), (0.1, "p10"), (0.9, "p90")]:
        model = lgb.LGBMRegressor(alpha=alpha, n_estimators=400, **params)
        model.fit(X_train, y_train)
        pred = model.predict(X_test)
        metrics = evaluate(y_test, pred, alpha)
        quantile_models[name] = model
        artifacts.append((name, model, metrics, alpha))
        print(f"[{name}] alpha={alpha} {metrics}")

    # Export ONNX artifacts (single tree-ensemble graph each)
    onnx_paths = {}
    for name, model, metrics, alpha in artifacts:
        if _ONNX_BACKEND == "onnxmltools":
            path = os.path.join(output_dir, f"{name}.onnx")
            try:
                initial_types = [
                    ("input", FloatTensorType([None, len(FEATURE_COLUMNS)]))
                ]
                onnx_model = convert_lightgbm(model, initial_types=initial_types)
                with open(path, "wb") as f:
                    f.write(onnx_model.SerializeToString())
                onnx_paths[name] = path
                print(f"Exported {path}")
            except Exception as e:
                print(f"[WARN] ONNX export failed for {name}: {e}")
                onnx_paths[name] = None
        else:
            print(f"[WARN] onnxmltools not installed — skipping ONNX export for {name}")
            onnx_paths[name] = None

    # Median-based P10/P90 ratio factors for interval construction at serving time
    p10_pred = quantile_models["p10"].predict(X_test)
    p50_pred = quantile_models["model"].predict(X_test)
    p90_pred = quantile_models["p90"].predict(X_test)
    ratio_p10 = float(np.median(p10_pred / np.maximum(p50_pred, 1e-6)))
    ratio_p90 = float(np.median(p90_pred / np.maximum(p50_pred, 1e-6)))

    # Per-SKU calibration baseline (global model observed/predicted ratio)
    test_history = test[["sku", "date", "demand"]].copy()
    test_history["prediction"] = quantile_models["model"].predict(X_test)
    calibration = calibration_baseline(test_history, horizon)

    # Feature importances
    importances = quantile_models["model"].feature_importances_
    importance_map = {
        col: int(imp) for col, imp in zip(FEATURE_COLUMNS, importances)
    }

    metrics_out = {
        "horizon_days": horizon,
        "feature_columns": FEATURE_COLUMNS,
        "p50": None,
        "p10": None,
        "p90": None,
        "ratio_p10_to_median": round(ratio_p10, 4),
        "ratio_p90_to_median": round(ratio_p90, 4),
        "feature_importance": importance_map,
    }
    for name, _, m, alpha in artifacts:
        metrics_out[name if name != "model" else "p50"] = m

    with open(os.path.join(output_dir, "feature_columns.json"), "w") as f:
        json.dump(FEATURE_COLUMNS, f, indent=2)
    with open(os.path.join(output_dir, "metrics.json"), "w") as f:
        json.dump(metrics_out, f, indent=2)
    with open(os.path.join(output_dir, "calibration.json"), "w") as f:
        json.dump(calibration, f, indent=2)

    print(f"\nArtifacts written to {output_dir}")
    if _ORT_AVAILABLE and onnx_paths.get("model"):
        sess = onnxruntime.InferenceSession(onnx_paths["model"])
        sample = X_test.iloc[[0]][FEATURE_COLUMNS].values.astype(np.float32)
        out = sess.run(None, {sess.get_inputs()[0].name: sample})
        print(f"ONNX smoke test passed: median={float(out[0][0][0]) if isinstance(out[0][0], np.ndarray) else float(out[0][0]):.2f}")
    return metrics_out


def main():
    parser = argparse.ArgumentParser(description="Train LightGBM demand forecast -> ONNX")
    parser.add_argument("--input", required=True, help="demand export (jsonl or csv)")
    parser.add_argument("--output-dir", default="artifacts", help="output directory")
    parser.add_argument("--horizon", type=int, default=7)
    args = parser.parse_args()

    df = load_data(args.input)
    if "demand_date" in df.columns:
        df = df.rename(columns={"demand_date": "date", "demand_quantity": "demand"})
    df = aggregate_daily(df)
    train_and_export(df, args.output_dir, args.horizon)


if __name__ == "__main__":
    main()
