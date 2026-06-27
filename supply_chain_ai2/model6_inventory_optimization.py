import pandas as pd
import numpy as np
import joblib
import warnings
import os
from sklearn.model_selection import train_test_split
from sklearn.preprocessing import StandardScaler
from sklearn.metrics import accuracy_score
from xgboost import XGBClassifier

warnings.filterwarnings("ignore")

DATA_PATH = os.path.join(os.path.dirname(__file__), "data", "training_data_extra.csv")
MODEL_DIR = os.path.join(os.path.dirname(__file__), "models")
os.makedirs(MODEL_DIR, exist_ok=True)


def load_data():
    df = pd.read_csv(DATA_PATH)
    df["demand_date"] = pd.to_datetime(df["demand_date"])
    return df


def engineer_features(df):
    features = pd.DataFrame()
    features["current_stock"] = np.random.randint(0, 200, size=len(df))
    np.random.seed(42)
    base_demand = df["demand_quantity"]
    features["demand_forecast_next_7"] = (base_demand * np.random.uniform(0.8, 1.2, size=len(df))).astype(int)
    features["demand_forecast_next_30"] = (base_demand * np.random.uniform(3.0, 5.0, size=len(df))).astype(int)
    features["lead_time_days"] = np.random.randint(2, 15, size=len(df))
    features["avg_daily_sales"] = (base_demand / 30).clip(1)
    features["safety_stock"] = np.random.randint(10, 100, size=len(df))
    features["seasonality_factor"] = np.random.uniform(0.5, 2.0, size=len(df))
    target = np.where(features["current_stock"] < (features["avg_daily_sales"] * features["lead_time_days"] + features["safety_stock"]), 1, 0).astype(int)
    return features, target


def preprocess_features(features, scaler=None):
    X = features.copy()
    for col in X.columns:
        X[col] = pd.to_numeric(X[col], errors="coerce").fillna(0).astype(float)
    if scaler:
        X_scaled = scaler.transform(X)
        X = pd.DataFrame(X_scaled, columns=X.columns)
    else:
        scaler = StandardScaler()
        X_scaled = scaler.fit_transform(X)
        X = pd.DataFrame(X_scaled, columns=X.columns)
    return X, scaler


def train_model():
    print("=" * 60)
    print("Model 6: Inventory Optimization - Reorder Recommendation")
    print("=" * 60)
    df = load_data()
    print(f"Loaded {len(df)} records")
    features, target = engineer_features(df)
    print(f"Target distribution: 0={sum(target==0)}, 1={sum(target==1)}")
    X, scaler = preprocess_features(features)
    X_train, X_test, y_train, y_test = train_test_split(X, target, test_size=0.2, random_state=42, stratify=target)
    print(f"Train: {len(X_train)}, Test: {len(X_test)}")
    model = XGBClassifier(n_estimators=200, max_depth=8, learning_rate=0.1, random_state=42,
                          objective="binary:logistic", eval_metric="logloss", use_label_encoder=False)
    model.fit(X_train, y_train)
    y_pred = model.predict(X_test)
    acc = accuracy_score(y_test, y_pred)
    print(f"Accuracy: {acc:.4f}")
    joblib.dump(model, os.path.join(MODEL_DIR, "inventory_optimization_model.pkl"))
    joblib.dump(scaler, os.path.join(MODEL_DIR, "inventory_optimization_scaler.pkl"))
    print(f"Model saved to {MODEL_DIR}/inventory_optimization_model.pkl")
    print(f"Feature columns: {list(X.columns)}")
    print("Training complete!\n")
    return model, scaler


if __name__ == "__main__":
    train_model()
