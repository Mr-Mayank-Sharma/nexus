#!/usr/bin/env python3
"""Complete Nexus OMS seed script."""
import json, urllib.request, urllib.error, time, random, sys
from urllib.parse import urlencode

BASE = "http://localhost:8080/api/v1"
TOKEN = None
TENANT_ID = None

def api(method, path, data=None, params=None, raw=False, retries=3):
    for attempt in range(retries):
        try:
            url = f"{BASE}{path}"
            if params:
                url += "?" + urlencode(params)
            req = urllib.request.Request(url, method=method)
            req.add_header("Content-Type", "application/json")
            if TOKEN:
                req.add_header("Authorization", f"Bearer {TOKEN}")
            if data is not None:
                req.data = json.dumps(data).encode()
            with urllib.request.urlopen(req, timeout=30) as resp:
                body = json.loads(resp.read())
                if raw:
                    return body
                if body.get("success"):
                    return body.get("data")
                raise Exception(body.get("message", "API error"))
        except urllib.error.HTTPError as e:
            err = e.read().decode()
            try:
                msg = json.loads(err).get("message", err)
            except:
                msg = err
            if attempt < retries - 1 and e.code in (409, 429, 500):
                time.sleep(1)
                continue
            raise Exception(f"HTTP {e.code}: {msg[:200]}")
        except Exception as e:
            if attempt < retries - 1:
                time.sleep(1)
                continue
            raise

def login():
    global TOKEN, TENANT_ID
    L = api("POST", "/auth/login", {"username":"admin","password":"Test1234!"}, raw=True)
    if not L.get("success"):
        api("POST", "/auth/register", {"username":"admin","password":"Test1234!","role":"ADMIN"})
        L = api("POST", "/auth/login", {"username":"admin","password":"Test1234!"}, raw=True)
    TOKEN = L["data"]["accessToken"]
    TENANT_ID = L["data"]["tenantId"]
    print(f"Logged in, tenant={TENANT_ID[:8]}...")

def create_products():
    products = [
        {"sku":"SKU-ELEC-001","productName":"Wireless Mouse","category":"Electronics","unitPrice":29.99,"costPrice":15.00,"weight":0.2,"isActive":True},
        {"sku":"SKU-ELEC-002","productName":"USB-C Hub","category":"Electronics","unitPrice":49.99,"costPrice":25.00,"weight":0.15,"isActive":True},
        {"sku":"SKU-ELEC-003","productName":"Bluetooth Speaker","category":"Electronics","unitPrice":79.99,"costPrice":40.00,"weight":0.5,"isActive":True},
        {"sku":"SKU-ELEC-004","productName":"Mechanical Keyboard","category":"Electronics","unitPrice":149.99,"costPrice":75.00,"weight":0.8,"isActive":True},
        {"sku":"SKU-ELEC-005","productName":"Webcam HD","category":"Electronics","unitPrice":89.99,"costPrice":45.00,"weight":0.25,"isActive":True},
        {"sku":"SKU-ELEC-006","productName":"Laptop Stand","category":"Electronics","unitPrice":39.99,"costPrice":20.00,"weight":0.6,"isActive":True},
        {"sku":"SKU-ELEC-007","productName":"Noise Cancelling Earbuds","category":"Electronics","unitPrice":199.99,"costPrice":100.00,"weight":0.08,"isActive":True},
        {"sku":"SKU-ELEC-008","productName":"Portable Charger","category":"Electronics","unitPrice":34.99,"costPrice":17.00,"weight":0.3,"isActive":True},
        {"sku":"SKU-APP-001","productName":"Cotton T-Shirt","category":"Apparel","unitPrice":19.99,"costPrice":8.00,"weight":0.15,"isActive":True},
        {"sku":"SKU-APP-002","productName":"Denim Jacket","category":"Apparel","unitPrice":89.99,"costPrice":45.00,"weight":0.8,"isActive":True},
        {"sku":"SKU-APP-003","productName":"Running Shoes","category":"Apparel","unitPrice":129.99,"costPrice":65.00,"weight":0.4,"isActive":True},
        {"sku":"SKU-APP-004","productName":"Wool Scarf","category":"Apparel","unitPrice":29.99,"costPrice":12.00,"weight":0.1,"isActive":True},
        {"sku":"SKU-HOME-001","productName":"Stainless Water Bottle","category":"Home","unitPrice":24.99,"costPrice":10.00,"weight":0.3,"isActive":True},
        {"sku":"SKU-HOME-002","productName":"LED Desk Lamp","category":"Home","unitPrice":44.99,"costPrice":22.00,"weight":0.5,"isActive":True},
        {"sku":"SKU-HOME-003","productName":"Yoga Mat","category":"Home","unitPrice":34.99,"costPrice":15.00,"weight":0.7,"isActive":True},
        {"sku":"SKU-HOME-004","productName":"Coffee Maker","category":"Home","unitPrice":69.99,"costPrice":35.00,"weight":2.0,"isActive":True},
        {"sku":"SKU-HOME-005","productName":"Throw Blanket","category":"Home","unitPrice":39.99,"costPrice":18.00,"weight":0.5,"isActive":True},
        {"sku":"SKU-BOOK-001","productName":"Python Cookbook","category":"Books","unitPrice":49.99,"costPrice":25.00,"weight":0.6,"isActive":True},
        {"sku":"SKU-BOOK-002","productName":"Data Science Handbook","category":"Books","unitPrice":59.99,"costPrice":30.00,"weight":0.7,"isActive":True},
        {"sku":"SKU-BOOK-003","productName":"Project Management Guide","category":"Books","unitPrice":39.99,"costPrice":20.00,"weight":0.5,"isActive":True},
        {"sku":"SKU-OFF-001","productName":"Standing Desk Converter","category":"Office","unitPrice":299.99,"costPrice":150.00,"weight":8.0,"isActive":True},
        {"sku":"SKU-OFF-002","productName":"Ergonomic Office Chair","category":"Office","unitPrice":499.99,"costPrice":250.00,"weight":15.0,"isActive":True},
        {"sku":"SKU-OFF-003","productName":"Monitor Arm","category":"Office","unitPrice":79.99,"costPrice":35.00,"weight":1.5,"isActive":True},
        {"sku":"SKU-OFF-004","productName":"Desk Organizer","category":"Office","unitPrice":24.99,"costPrice":10.00,"weight":0.3,"isActive":True},
    ]
    ids = []
    for p in products:
        try:
            r = api("POST", "/products", p)
            ids.append(r["id"])
        except Exception as e:
            if "already" in str(e).lower():
                print(f"  SKIP {p['sku']}: exists")
                continue
            print(f"  ERR {p['sku']}: {e}")
    print(f"Products: {len(ids)}")
    return ids

def create_customers():
    first_names = ["John","Jane","Bob","Alice","Mike","Sarah","David","Emma","Chris","Lisa",
                   "Tom","Rachel","Steve","Megan","Dan","Amy","Ryan","Kate","Mark","Anna"]
    last_names = ["Smith","Johnson","Williams","Brown","Jones","Garcia","Miller","Davis","Martinez","Wilson"]
    cities = [("New York","NY","10001"),("Los Angeles","CA","90001"),("Chicago","IL","60601"),
              ("Houston","TX","77001"),("Phoenix","AZ","85001")]
    ids = []
    for i in range(120):
        fn = random.choice(first_names)
        ln = random.choice(last_names)
        city, state, zipc = random.choice(cities)
        cust = {
            "name": f"{fn} {ln}",
            "email": f"{fn.lower()}.{ln.lower()}{i}@example.com",
            "phone": f"+1{random.randint(200,999)}{random.randint(100,999)}{random.randint(1000,9999)}",
            "addressLine1": f"{random.randint(100,9999)} {random.choice(['Oak','Elm','Maple','Pine'])} {random.choice(['St','Ave','Blvd'])}",
            "city": city,"state": state,"pincode": zipc,"country": "US","isActive": True,
        }
        try:
            r = api("POST", "/customers", cust)
            ids.append(r["id"])
        except:
            pass
    print(f"Customers: {len(ids)}")
    return ids

def create_warehouse_and_node():
    wh = {
        "code": "WH-MAIN", "name": "Main Distribution Center", "type": "WAREHOUSE",
        "status": "ACTIVE", "addressLine1": "1000 Logistics Way", "city": "Dallas",
        "state": "TX", "zipCode": "75201", "country": "US",
        "totalCapacitySqm": 100000, "totalCapacityCbm": 50000,
        "usedCapacitySqm": 0, "usedCapacityCbm": 0, "dockCount": 20,
        "operatingHours": '{"mon-fri":"06:00-22:00","sat":"08:00-18:00","sun":"10:00-16:00"}',
    }
    wh_id = api("POST", "/warehouses", wh)["id"]
    print(f"Warehouse: {wh_id[:8]}...")

    zones_data = [
        ("Z-BULK","Bulk Storage","BULK_STORAGE","STORAGE"),
        ("Z-PICK","Picking Area","PICKING","PICKING"),
        ("Z-PACK","Packing Station","PACKING","PACKING"),
        ("Z-SHIP","Shipping Dock","SHIPPING","SHIPPING"),
    ]
    zone_ids = {}
    for code, name, ztype, cat in zones_data:
        z = api("POST", "/warehouses/zones", {
            "warehouseId": wh_id, "code": code, "name": name,
            "zoneType": ztype, "zoneCategory": cat, "capacity": 5000,
            "metadata": "{}"
        })
        zone_ids[code] = z["id"]
    print(f"Zones: {len(zones_data)}")

    bin_ids = []
    for row in "ABCD":
        for col in range(1, 6):
            for level in range(1, 4):
                zone = random.choice(list(zone_ids.values()))
                b = api("POST", "/warehouses/bins", {
                    "warehouseId": wh_id, "zoneId": zone,
                    "code": f"{row}-{col:02d}-L{level}",
                    "binType": "STANDARD","binClass": "FLOOR",
                    "maxWeightKg": 100,"maxVolumeCbm": 1.0,
                    "isEmpty": True,"isReserved": False,
                })
                bin_ids.append(b["id"])
    print(f"Bins: {len(bin_ids)}")

    staff_data = [
        ("EMP-1001","John","Smith","PICKER"),("EMP-1002","Emma","Johnson","PACKER"),
        ("EMP-1003","Mike","Brown","PICKER"),("EMP-1004","Sarah","Davis","PACKER"),
        ("EMP-1005","Tom","Wilson","SUPERVISOR"),("EMP-1006","Lisa","Taylor","PICKER"),
        ("EMP-1007","James","Anderson","PACKER"),("EMP-1008","Megan","Thomas","PICKER"),
        ("EMP-1009","Chris","Jackson","PACKER"),("EMP-1010","Amy","White","OPERATOR"),
        ("EMP-1011","Ryan","Harris","PICKER"),("EMP-1012","Kate","Martin","PACKER"),
        ("EMP-1013","Dan","Lee","PICKER"),("EMP-1014","Nina","Clark","PACKER"),
        ("EMP-1015","Paul","Walker","SUPERVISOR"),
    ]
    staff_ids = []
    for emp, fn, ln, role in staff_data:
        s = api("POST", "/warehouses/staff", {
            "warehouseId": wh_id, "employeeCode": emp, "firstName": fn, "lastName": ln,
            "role": role, "shift": random.choice(["MORNING","AFTERNOON"]),
            "skills": '["PICKING","PACKING"]', "isActive": True,
        })
        staff_ids.append(s["id"])
    print(f"Staff: {len(staff_ids)}")
    return wh_id, zone_ids, bin_ids, staff_ids

def create_carriers():
    carriers = [
        {"name":"FedEx","code":"FEDEX","isActive":True,"type":"SHIPPING","metadata":"{}"},
        {"name":"UPS","code":"UPS","isActive":True,"type":"SHIPPING","metadata":"{}"},
        {"name":"USPS","code":"USPS","isActive":True,"type":"SHIPPING","metadata":"{}"},
        {"name":"DHL","code":"DHL","isActive":True,"type":"SHIPPING","metadata":"{}"},
    ]
    ids = []
    for c in carriers:
        try:
            r = api("POST", "/carriers", c)
            ids.append(r["id"])
        except Exception as e:
            print(f"  SKIP {c['name']}: {e}")
    print(f"Carriers: {len(ids)}")
    return ids

def create_orders(product_ids, customer_ids, count):
    channels = ["web","mobile","api","marketplace","pos"]
    order_ids = []
    skus = ["SKU-ELEC-001","SKU-ELEC-002","SKU-ELEC-003","SKU-ELEC-004","SKU-ELEC-005",
            "SKU-ELEC-006","SKU-ELEC-007","SKU-ELEC-008","SKU-APP-001","SKU-APP-002",
            "SKU-APP-003","SKU-APP-004","SKU-HOME-001","SKU-HOME-002","SKU-HOME-003",
            "SKU-HOME-004","SKU-HOME-005","SKU-BOOK-001","SKU-BOOK-002","SKU-BOOK-003",
            "SKU-OFF-001","SKU-OFF-002","SKU-OFF-003","SKU-OFF-004"]
    pnames = ["Wireless Mouse","USB-C Hub","Bluetooth Speaker","Keyboard","Webcam",
              "Laptop Stand","Earbuds","Charger","T-Shirt","Denim Jacket",
              "Running Shoes","Wool Scarf","Water Bottle","LED Lamp","Yoga Mat",
              "Coffee Maker","Throw Blanket","Python Cookbook","Data Science Book","PM Guide",
              "Desk Converter","Office Chair","Monitor Arm","Desk Organizer"]

    for i in range(count):
        num_items = random.randint(1, 3)
        items = []
        for _ in range(num_items):
            idx = random.randint(0, len(skus)-1)
            qty = random.randint(1, 4)
            price = round(random.uniform(10, 200), 2)
            items.append({"sku": skus[idx], "productName": pnames[idx], "quantity": qty, "unitPrice": price})
        cust_id = random.choice(customer_ids)
        cust = api("GET", f"/customers/{cust_id}")
        order = {
            "channel": random.choice(channels),
            "customerName": cust.get("name", "Customer"),
            "customerEmail": cust.get("email", "cust@example.com"),
            "shippingAddress": {
                "line1": f"{random.randint(100,9999)} {random.choice(['Oak','Elm','Maple','Pine'])} St",
                "city": random.choice(["New York","Los Angeles","Chicago","Houston","Phoenix","Dallas"]),
                "state": random.choice(["NY","CA","IL","TX","AZ","TX"]),
                "pincode": str(random.randint(10000,99999)),
            },
            "items": items,
        }
        api("POST", "/orders", order)
        order_ids.append(None)
        if (i+1) % 100 == 0:
            print(f"  Created {i+1}/{count} orders")
    print(f"Orders: {count}")
    return order_ids

def verify_screens():
    screens = [("Orders","/orders?page=0&size=5"),("Products","/products"),
               ("Customers","/customers?page=0&size=5"),("Warehouses","/warehouses"),
               ("Inventory","/inventory"),("Carriers","/carriers"),
               ("Picking KPI","/picking/kpis"),("Packing KPI","/packing/kpis"),
               ("Shipping KPI","/shipping/kpis"),("Dashboard KPI","/analytics/kpis"),
               ("Dashboard Act.","/analytics/activity")]
    for name, path in screens:
        try:
            r = api("GET", path, raw=True)
            print(f"  {name}: {'OK' if r.get('success') else 'ERR'}")
        except Exception as e:
            print(f"  {name}: FAIL {str(e)[:60]}")

def main():
    print("="*60+"\nNEXUS OMS - SEED\n"+"="*60)
    login()

    print("\n--- Products ---")
    product_ids = create_products()

    print("\n--- Customers ---")
    customer_ids = create_customers()

    print("\n--- Warehouse & Infrastructure ---")
    wh_id, zone_ids, bin_ids, staff_ids = create_warehouse_and_node()

    print("\n--- Carriers ---")
    carrier_ids = create_carriers()

    print("\n--- Orders (1900) ---")
    order_ids = create_orders(product_ids, customer_ids, 1900)

    print("\n--- Verify Screens ---")
    verify_screens()

    state = {
        "product_ids": product_ids, "customer_ids": customer_ids,
        "wh_id": wh_id, "zone_ids": zone_ids, "bin_ids": bin_ids,
        "staff_ids": staff_ids, "carrier_ids": carrier_ids,
        "order_count": 1900,
    }
    with open("/tmp/nexus_state.json","w") as f:
        json.dump(state, f, indent=2)
    print("\nState saved to /tmp/nexus_state.json")

if __name__ == "__main__":
    main()
