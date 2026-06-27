import pandas as pd
import numpy as np
import joblib
import warnings
import os
from sklearn.model_selection import train_test_split
from sklearn.preprocessing import LabelEncoder
from sklearn.metrics import accuracy_score
from xgboost import XGBClassifier

warnings.filterwarnings("ignore")

DATA_PATH = os.path.join(os.path.dirname(__file__), "data", "training_data.csv")
MODEL_DIR = os.path.join(os.path.dirname(__file__), "models")
os.makedirs(MODEL_DIR, exist_ok=True)


def load_data():
    df = pd.read_csv(DATA_PATH)
    return df


def engineer_features(df):
    features = df[["item_quantity", "item_weight_kg", "item_category", "item_price"]].copy()
    features["is_fragile"] = np.where(df["item_weight_kg"] > 8, 1, 0)
    features["is_fragile"] = np.where(df["item_price"] > 10000, 1, features["is_fragile"])
    target = df["box_size"].copy()
    return features, target


def preprocess_features(features, le_dict=None):
    X = features.copy()
    encoders = le_dict if le_dict else {}
    if "item_category" in features.columns:
        col = "item_category"
        if col in encoders:
            X[col] = X[col].map(lambda v: encoders[col].transform([v])[0] if v in encoders[col].classes_ else -1)
        else:
            le = LabelEncoder()
            X[col] = le.fit_transform(X[col].astype(str))
            encoders[col] = le
    for col in X.columns:
        X[col] = pd.to_numeric(X[col], errors="coerce").fillna(0).astype(float)
    return X, encoders


def train_model():
    print("=" * 60)
    print("Model 3: Box Size Optimizer")
    print("=" * 60)
    df = load_data()
    print(f"Loaded {len(df)} records")
    features, target = engineer_features(df)
    le_target = LabelEncoder()
    y = le_target.fit_transform(target)
    print(f"Target classes: {list(le_target.classes_)}")
    X, fe_encoders = preprocess_features(features)
    X_train, X_test, y_train, y_test = train_test_split(X, y, test_size=0.2, random_state=42, stratify=y)
    print(f"Train: {len(X_train)}, Test: {len(X_test)}")
    model = XGBClassifier(n_estimators=100, max_depth=6, learning_rate=0.1, random_state=42,
                          objective="multi:softprob", eval_metric="mlogloss", verbosity=1)
    model.fit(X_train, y_train, eval_set=[(X_test, y_test)], verbose=False)
    y_pred = model.predict(X_test)
    acc = accuracy_score(y_test, y_pred)
    print(f"Accuracy: {acc:.4f}")
    joblib.dump(model, os.path.join(MODEL_DIR, "box_size_model.pkl"))
    joblib.dump(le_target, os.path.join(MODEL_DIR, "box_size_label_encoder.pkl"))
    joblib.dump(fe_encoders, os.path.join(MODEL_DIR, "box_size_fe_encoders.pkl"))
    print(f"Model saved to {MODEL_DIR}/box_size_model.pkl")
    print(f"Feature columns: {list(X.columns)}")
    print("Training complete!\n")
    return model, le_target, fe_encoders


if __name__ == "__main__":
    train_model()
