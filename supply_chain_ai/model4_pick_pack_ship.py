import pandas as pd
import numpy as np
import joblib
import warnings
import os
from sklearn.model_selection import train_test_split
from sklearn.preprocessing import LabelEncoder, StandardScaler
from sklearn.metrics import mean_absolute_error
from sklearn.ensemble import RandomForestRegressor

warnings.filterwarnings("ignore")

DATA_PATH = os.path.join(os.path.dirname(__file__), "data", "training_data.csv")
MODEL_DIR = os.path.join(os.path.dirname(__file__), "models")
os.makedirs(MODEL_DIR, exist_ok=True)

WAREHOUSE_LAYOUTS = ["Grid", "Zone", "Flow", "Custom"]


def load_data():
    df = pd.read_csv(DATA_PATH)
    return df


def engineer_features(df):
    features = pd.DataFrame()
    features["item_count"] = df["item_quantity"]
    features["total_weight"] = df["item_weight_kg"]
    box_map = {"SMALL": 1, "MEDIUM": 2, "LARGE": 3, "EXTRA_LARGE": 4}
    features["box_size"] = df["box_size"].map(box_map).fillna(2)
    features["items_distance_meters"] = np.random.randint(10, 200, size=len(df))
    np.random.seed(42)
    features["warehouse_layout_type"] = np.random.choice(range(len(WAREHOUSE_LAYOUTS)), size=len(df))
    target = df["pick_time_minutes"].copy()
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
    print("Model 4: Pick/Pack Time Estimation")
    print("=" * 60)
    df = load_data()
    print(f"Loaded {len(df)} records")
    features, target = engineer_features(df)
    X, scaler = preprocess_features(features)
    X_train, X_test, y_train, y_test = train_test_split(X, target, test_size=0.2, random_state=42)
    print(f"Train: {len(X_train)}, Test: {len(X_test)}")
    model = RandomForestRegressor(n_estimators=200, max_depth=12, random_state=42, n_jobs=-1)
    model.fit(X_train, y_train)
    y_pred = model.predict(X_test)
    mae = mean_absolute_error(y_test, y_pred)
    print(f"Mean Absolute Error: {mae:.4f}")
    joblib.dump(model, os.path.join(MODEL_DIR, "pick_pack_model.pkl"))
    joblib.dump(scaler, os.path.join(MODEL_DIR, "pick_pack_scaler.pkl"))
    print(f"Model saved to {MODEL_DIR}/pick_pack_model.pkl")
    print(f"Feature columns: {list(X.columns)}")
    print("Training complete!\n")
    return model, scaler


if __name__ == "__main__":
    train_model()
