import numpy as np
import pandas as pd
from datetime import datetime, timedelta
import random

np.random.seed(42)
random.seed(42)

CITIES = {
    "Delhi": {"state": "DL", "lat": 28.61, "lon": 77.23, "pincodes": list(range(110001, 110099))},
    "Mumbai": {"state": "MH", "lat": 19.08, "lon": 72.88, "pincodes": list(range(400001, 400099))},
    "Bangalore": {"state": "KA", "lat": 12.97, "lon": 77.59, "pincodes": list(range(560001, 560099))},
    "Chennai": {"state": "TN", "lat": 13.08, "lon": 80.27, "pincodes": list(range(600001, 600099))},
    "Hyderabad": {"state": "TS", "lat": 17.38, "lon": 78.46, "pincodes": list(range(500001, 500099))},
    "Kolkata": {"state": "WB", "lat": 22.57, "lon": 88.36, "pincodes": list(range(700001, 700099))},
    "Pune": {"state": "MH", "lat": 18.52, "lon": 73.86, "pincodes": list(range(411001, 411099))},
    "Ahmedabad": {"state": "GJ", "lat": 23.02, "lon": 72.57, "pincodes": list(range(380001, 380099))},
    "Jaipur": {"state": "RJ", "lat": 26.91, "lon": 75.78, "pincodes": list(range(302001, 302099))},
    "Lucknow": {"state": "UP", "lat": 26.85, "lon": 80.95, "pincodes": list(range(226001, 226099))},
}

WAREHOUSES = {
    "WH_DELHI": {"lat": 28.61, "lon": 77.23, "city": "Delhi"},
    "WH_MUMBAI": {"lat": 19.08, "lon": 72.88, "city": "Mumbai"},
    "WH_BANGALORE": {"lat": 12.97, "lon": 77.59, "city": "Bangalore"},
    "WH_CHENNAI": {"lat": 13.08, "lon": 80.27, "city": "Chennai"},
}

CARRIERS = ["delhivery", "blue_dart", "ecom_express", "xpressbees", "shadowfax"]
CHANNELS = ["Amazon", "Flipkart", "Meesho", "Shopify", "Website"]
CATEGORIES = ["Electronics", "Clothing", "Home & Kitchen", "Books", "Sports", "Beauty", "Toys", "Groceries", "Automotive", "Health"]
BOX_SIZES = ["SMALL", "MEDIUM", "LARGE", "EXTRA_LARGE"]
CITIES_LIST = list(CITIES.keys())


def generate_orders(n=10000):
    rows = []
    base_date = datetime(2024, 1, 1)
    for i in range(n):
        order_id = f"ORD-{i+1:06d}"
        customer_id = f"CUST-{random.randint(1000, 9999)}"
        channel = random.choice(CHANNELS)
        order_date = base_date + timedelta(days=random.randint(0, 545), hours=random.randint(0, 23), minutes=random.randint(0, 59))
        city = random.choice(CITIES_LIST)
        city_data = CITIES[city]
        pincode = random.choice(city_data["pincodes"])
        lat = city_data["lat"] + random.uniform(-0.05, 0.05)
        lon = city_data["lon"] + random.uniform(-0.05, 0.05)
        sku = f"SKU-{random.randint(10000, 99999)}"
        category = random.choice(CATEGORIES)
        quantity = random.randint(1, 5)
        weight = round(random.uniform(0.1, 15.0), 2)
        price = round(random.uniform(99, 50000), 2)
        warehouse = random.choice(list(WAREHOUSES.keys()))
        carrier = random.choice(CARRIERS)
        delivery_status = random.choice(["Delivered", "Delivered", "Delivered", "Delivered", "Delivered", "In Transit", "Returned", "Cancelled"])
        actual_delivery_days = random.randint(1, 10) if delivery_status == "Delivered" else None
        shipping_cost = round(random.uniform(20, 500), 2)
        is_fragile = random.choice([0, 0, 0, 1])
        if weight <= 2:
            box = "SMALL"
        elif weight <= 5:
            box = "MEDIUM"
        elif weight <= 10:
            box = "LARGE"
        else:
            box = "EXTRA_LARGE"
        pickers = random.randint(1, 3)
        pick_time = random.randint(5, 60)
        demand_date = order_date + timedelta(days=random.randint(-7, 30))
        demand_qty = random.randint(10, 500)
        rows.append({
            "order_id": order_id, "customer_id": customer_id, "channel": channel,
            "order_date": order_date, "shipping_address_city": city,
            "shipping_address_pincode": pincode, "latitude": round(lat, 4), "longitude": round(lon, 4),
            "item_sku": sku, "item_category": category, "item_quantity": quantity,
            "item_weight_kg": weight, "item_price": price, "origin_warehouse": warehouse,
            "carrier": carrier, "delivery_status": delivery_status,
            "actual_delivery_days": actual_delivery_days, "shipping_cost": shipping_cost,
            "box_size": box, "pickers_required": pickers, "pick_time_minutes": pick_time,
            "demand_date": demand_date, "demand_quantity": demand_qty,
        })
    df = pd.DataFrame(rows)
    return df


if __name__ == "__main__":
    df = generate_orders(10000)
    df.to_csv("/home/mayanksharma/Desktop/Nexus/supply_chain_ai/data/training_data.csv", index=False)
    df2 = generate_orders(500)
    df2.to_csv("/home/mayanksharma/Desktop/Nexus/supply_chain_ai2/data/training_data_extra.csv", index=False)
    print(f"Generated {len(df)} training records -> training_data.csv")
    print(f"Generated {len(df2)} extra records -> supply_chain_ai2/data/training_data_extra.csv")
    print("Columns:", list(df.columns))
