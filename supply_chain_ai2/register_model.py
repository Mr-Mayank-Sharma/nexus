#!/usr/bin/env python3
"""
register_model.py - P1.6: register the demand model end-to-end.

Chain: model -> version (with backtest metrics) -> ONNX artifact upload ->
gated deploy -> gateway route.

Honesty contract:
  - Refuses to run unless models/metrics.json exists (produced by
    model5_demand_forecasting.py ONLY when a legitimate rolling-origin
    backtest succeeded).
  - Deploys WITHOUT ?force. If the Java validation gate blocks the deploy
    (WAPE/MASE/champion rules), the block is reported verbatim - that is
    the system working, not an error to bypass.

Usage:
    .venv/bin/python register_model.py                 # production artifacts
    .venv/bin/python register_model.py --selftest      # exercise wiring using
                                                       # selftest artifacts
                                                       #(expect gate 422)
"""

import argparse
import json
import os
import subprocess
import sys
import urllib.error
import urllib.request
import uuid

BASE_DIR = os.path.dirname(os.path.abspath(__file__))
MODEL_DIR = os.path.join(BASE_DIR, "models")

API = "http://localhost:8085/api/v1"
DB_ARGS = ["-h", "localhost", "-p", "5433", "-U", "nexus", "-d", "nexus_oms"]
DB_ENV = {"PGPASSWORD": "nexus", "PATH": os.environ.get("PATH", "")}

MODEL_NAME = "demand_forecast_v2"
MODEL_TYPE = "DEMAND_FORECAST"


def http(method, path, token=None, json_body=None, data=None, headers=None):
    req = urllib.request.Request(API + path, method=method)
    if token:
        req.add_header("Authorization", f"Bearer {token}")
    if json_body is not None:
        req.add_header("Content-Type", "application/json")
        data = json.dumps(json_body).encode()
    for k, v in (headers or {}).items():
        req.add_header(k, v)
    try:
        with urllib.request.urlopen(req, data=data) as resp:
            return resp.status, json.loads(resp.read().decode())
    except urllib.error.HTTPError as e:
        body = e.read().decode()
        try:
            return e.code, json.loads(body)
        except Exception:
            return e.code, {"raw": body}


def login():
    status, resp = http("POST", "/auth/login", json_body={
        "username": "pipeline", "password": "Pipeline@2026"})
    if status != 200 or not resp.get("data", {}).get("accessToken"):
        sys.exit(f"FATAL: login failed ({status}): {resp}")
    return resp["data"]["accessToken"]


def psql(sql):
    return subprocess.run(["psql"] + DB_ARGS + ["-tAc", sql],
                          capture_output=True, text=True, env=DB_ENV, check=True).stdout.strip()


def find_or_create_model(token):
    status, resp = http("GET", f"/ai/models?category=GLOBAL&size=200", token)
    for m in resp.get("data", {}).get("content", []):
        if m.get("name") == MODEL_NAME:
            print(f"model       : found {m['id']}")
            return m["id"]
    status, resp = http("POST", "/ai/models", token, json_body={
        "name": MODEL_NAME,
        "displayName": "Demand Forecast v2 (Tweedie LightGBM)",
        "description": "Global LightGBM-Tweedie demand model; "
                       "rolling-origin backtest validated",
        "modelType": MODEL_TYPE,
        "category": "GLOBAL",
    })
    if status != 200:
        sys.exit(f"FATAL: create model failed ({status}): {resp}")
    print(f"model       : created {resp['data']['id']}")
    return resp["data"]["id"]


def create_version(token, model_id, metrics):
    tag = "v" + metrics.get("trained_at", "").replace("-", "").replace(":", "") \
        .split(".")[0].replace("T", "").replace("Z", "")
    status, resp = http("POST", f"/ai/models/{model_id}/versions", token, json_body={
        "version": tag,
        "framework": "LightGBM",
        "artifactFormat": "ONNX",
        "commitMessage": f"rolling-origin backtest wape={metrics.get('wape')} "
                         f"mase={metrics.get('mase')} source={metrics.get('data', {}).get('source')}",
        "metrics": json.dumps(metrics),
    })
    if status != 200:
        sys.exit(f"FATAL: create version failed ({status}): {resp}")
    print(f"version     : created {resp['data']['id']} ({tag})")
    return resp["data"]["id"]


def upload_artifact(token, model_id, version_id, onnx_path, features_path):
    boundary = uuid.uuid4().hex
    with open(onnx_path, "rb") as fh:
        onnx_bytes = fh.read()
    with open(features_path) as fh:
        feature_columns = fh.read()

    parts = []
    parts.append(
        f'--{boundary}\r\nContent-Disposition: form-data; name="file"; '
        f'filename="{os.path.basename(onnx_path)}"\r\n'
        f"Content-Type: application/octet-stream\r\n\r\n".encode() + onnx_bytes + b"\r\n")
    for field, value in [("featureColumns", feature_columns)]:
        parts.append(
            f'--{boundary}\r\nContent-Disposition: form-data; name="{field}"\r\n\r\n'
            f"{value}\r\n".encode())
    parts.append(f"--{boundary}--\r\n".encode())
    body = b"".join(parts)

    status, resp = http("POST", f"/ai/models/{model_id}/versions/{version_id}/artifact",
                        token, data=body, headers={
                            "Content-Type": f"multipart/form-data; boundary={boundary}"})
    if status != 200:
        sys.exit(f"FATAL: artifact upload failed ({status}): {resp}")
    print(f"artifact    : stored ({len(onnx_bytes)} bytes, parity-verified at build time)")


def gated_deploy(token, model_id, version_id):
    status, resp = http("POST", f"/ai/models/{model_id}/deploy/{version_id}", token)
    if status == 200:
        d = resp["data"]
        print(f"deploy      : ACTIVE {d['id']} (weight {d['trafficWeight']})")
        return True
    if status == 422:
        print(f"deploy      : BLOCKED BY VALIDATION GATE (422) - this is correct behavior")
        print(f"              {resp.get('message', '')[:300]}")
        return False
    sys.exit(f"FATAL: unexpected deploy response ({status}): {resp}")


def ensure_route(model_type=MODEL_TYPE):
    existing = psql(f"SELECT id FROM ai_gateway_routes WHERE model_type='{model_type}' LIMIT 1")
    if existing:
        print(f"route       : exists {existing}")
        return
    psql(f"INSERT INTO ai_gateway_routes (id, name, model_type, fallback_strategy, "
         f"rate_limit_per_minute, timeout_ms, retry_count, is_active, created_by, created_at, updated_at) "
         f"VALUES (gen_random_uuid(), 'demand-forecast-global', '{model_type}', "
         f"'RULE_ENGINE', 600, 3000, 1, true, 'register_model', now(), now())")
    print("route       : created global DEMAND_FORECAST route (fallback RULE_ENGINE)")


def main():
    ap = argparse.ArgumentParser()
    ap.add_argument("--selftest", action="store_true",
                    help="exercise the chain with selftest artifacts (gate should block)")
    args = ap.parse_args()

    metrics_path = os.path.join(MODEL_DIR, "metrics.selftest.json" if args.selftest
                                else "metrics.json")
    onnx_name = "demand_forecast_selftest.onnx" if args.selftest else "demand_forecast_v2.onnx"
    onnx_path = os.path.join(MODEL_DIR, onnx_name)
    features_path = os.path.join(MODEL_DIR, "feature_columns.json")

    if not os.path.exists(metrics_path):
        sys.exit("NO_VALIDATED_METRICS: models/metrics.json missing. Run "
                 "model5_demand_forecasting.py once real history supports a "
                 "legitimate backtest. Nothing unvalidated gets registered.")
    if not os.path.exists(onnx_path):
        sys.exit(f"FATAL: missing artifact {onnx_path}")

    with open(metrics_path) as fh:
        metrics = json.load(fh)
    print(f"metrics     : wape={metrics.get('wape')} mase={metrics.get('mase')} "
          f"source={metrics.get('data', {}).get('source')}")

    token = login()
    model_id = find_or_create_model(token)
    version_id = create_version(token, model_id, metrics)
    upload_artifact(token, model_id, version_id, onnx_path, features_path)
    deployed = gated_deploy(token, model_id, version_id)
    ensure_route()

    print("-" * 64)
    if deployed:
        print("REGISTERED AND LIVE: gateway will serve this version for "
              f"{MODEL_TYPE} predictions.")
    else:
        print("REGISTRATION INCOMPLETE (by design): artifact stored, deploy "
              "blocked by the validation gate. When a retrain produces "
              "metrics that pass, rerun this script.")
    print("-" * 64)


if __name__ == "__main__":
    main()
