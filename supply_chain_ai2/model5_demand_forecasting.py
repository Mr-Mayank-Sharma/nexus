import pandas as pd
import numpy as np
import joblib
import warnings
import os
from sklearn.model_selection import train_test_split
from sklearn.preprocessing import StandardScaler
from sklearn.metrics import r2_score
from xgboost import XGBRegressor

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
    features["day_of_week"] = df["demand_date"].dt.dayofweek
    features["month"] = df["demand_date"].dt.month
    features["day_of_month"] = df["demand_date"].dt.day
    features["weekend"] = (features["day_of_week"] >= 5).astype(int)
    features["days_to_event"] = np.random.randint(-10, 30, size=len(df))
    features["is_festive_season"] = features["month"].isin([10, 11, 12, 1]).astype(int)
    np.random.seed(42)
    for lag in range(1, 8):
        features[f"lag_{lag}"] = np.random.poisson(20, size=len(df)) + np.random.randint(-5, 5, size=len(df))
        features[f"lag_{lag}"] = features[f"lag_{lag}"].clip(0)
    target = df["demand_quantity"].copy()
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
    print("Model 5: Demand Forecasting")
    print("=" * 60)
    df = load_data()
    print(f"Loaded {len(df)} records")
    features, target = engineer_features(df)
    X, scaler = preprocess_features(features)
    X_train, X_test, y_train, y_test = train_test_split(X, target, test_size=0.2, random_state=42)
    print(f"Train: {len(X_train)}, Test: {len(X_test)}")
    model = XGBRegressor(n_estimators=200, max_depth=8, learning_rate=0.1, random_state=42)
    model.fit(X_train, y_train)
    y_pred = model.predict(X_test)
    r2 = r2_score(y_test, y_pred)
    print(f"R² Score: {r2:.4f}")
    joblib.dump(model, os.path.join(MODEL_DIR, "demand_forecast_model.pkl"))
    joblib.dump(scaler, os.path.join(MODEL_DIR, "demand_forecast_scaler.pkl"))
    print(f"Model saved to {MODEL_DIR}/demand_forecast_model.pkl")
    print(f"Feature columns: {list(X.columns)}")
    print("Training complete!\n")
    return model, scaler


if __name__ == "__main__":
    train_model()
