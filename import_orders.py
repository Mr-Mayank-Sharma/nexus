#!/usr/bin/env python3
"""Generate and import 100 fake orders via the backend API."""
import json, urllib.request, urllib.error, time, random
from urllib.parse import urlencode

BASE = "http://localhost:8080/api/v1"
TOKEN = None

def api(m, p, d=None, params=None):
    url = BASE + p
    if params:
        url += "?" + urlencode({k:v for k,v in params.items() if v is not None})
    req = urllib.request.Request(url, method=m)
    req.add_header("Content-Type", "application/json")
    if TOKEN: req.add_header("Authorization", f"Bearer {TOKEN}")
    if d is not None: req.data = json.dumps(d).encode()
    for _ in range(3):
        try:
            with urllib.request.urlopen(req, timeout=30) as r:
                b = json.loads(r.read())
                if b.get("success"): return b.get("data")
                return None
        except urllib.error.HTTPError as e:
            err = e.read().decode()
            if e.code in (409, 429): time.sleep(1); continue
            return None
        except: return None
    return None

# Login
L = api("POST", "/auth/login", {"username":"admin","password":"Test1234!"})
if not L:
    api("POST", "/auth/register", {"username":"admin","password":"Test1234!","role":"ADMIN"})
    L = api("POST", "/auth/login", {"username":"admin","password":"Test1234!"})
if not L: print("Login failed!"); exit(1)
TOKEN = L.get("accessToken", "")
print(f"Logged in")

# Get existing customers
custs = api("GET", "/customers")
customers = custs if isinstance(custs, list) else []
print(f"Customers: {len(customers)}")

# Products to pick from
products = [
    ("SKU-ELEC-001","Mouse",49.99),("SKU-ELEC-002","USB Hub",29.99),("SKU-ELEC-003","Speaker",79.99),
    ("SKU-ELEC-004","Keyboard",89.99),("SKU-ELEC-005","Webcam",59.99),("SKU-APP-001","T-Shirt",19.99),
    ("SKU-APP-002","Jacket",89.99),("SKU-HOME-001","Bottle",14.99),("SKU-HOME-002","Lamp",39.99),
    ("SKU-HOME-003","Mat",24.99),("SKU-BOOK-001","Python Book",49.99),("SKU-BOOK-002","DS Book",54.99),
    ("SKU-OFF-001","Desk Converter",199.99),("SKU-OFF-003","Monitor Arm",79.99),
]
channels = ["web","mobile","api","marketplace"]
fnames = ["James","Mary","Robert","Patricia","John","Jennifer","Michael","Linda","David","Elizabeth",
          "William","Barbara","Richard","Susan","Joseph","Jessica","Thomas","Sarah","Christopher","Karen",
          "Charles","Lisa","Daniel","Nancy","Matthew","Betty","Anthony","Margaret","Mark","Sandra"]
lnames = ["Smith","Johnson","Williams","Brown","Jones","Garcia","Miller","Davis","Rodriguez","Martinez",
          "Hernandez","Lopez","Gonzalez","Wilson","Anderson","Thomas","Taylor","Moore","Jackson","Martin"]

print(f"Creating 100 orders...")
created = 0
for i in range(100):
    n_items = random.randint(1, 3)
    items = []
    for _ in range(n_items):
        sku, pname, price = random.choice(products)
        qty = random.randint(1, 4)
        items.append({"sku": sku, "productName": pname, "quantity": qty, "unitPrice": price})
    
    # Pick a customer and use their address
    if customers:
        c = random.choice(customers)
        cname = c.get("name","Customer")
        cemail = c.get("email","c@x.com")
        addr = {
            "street": c.get("addressLine1") or f"{random.randint(100,9999)} Main St",
            "city": c.get("city") or "Dallas",
            "state": c.get("state") or "TX",
            "zip": c.get("pincode") or "75201",
            "country": c.get("country") or "US",
        }
    else:
        fn = random.choice(fnames); ln = random.choice(lnames)
        cname = f"{fn} {ln}"; cemail = f"{fn.lower()}.{ln.lower()}@ex.com"
        addr = {"street":f"{random.randint(100,9999)} Main St","city":"Dallas","state":"TX","zip":"75201","country":"US"}
    
    order = {
        "customerName": cname,
        "customerEmail": cemail,
        "shippingAddress": addr,
        "channel": random.choice(channels),
        "items": items,
    }
    r = api("POST", "/orders", order)
    if r:
        created += 1
    
    if (i+1) % 20 == 0:
        print(f"  {i+1}/100 created")

print(f"\nDone! {created} orders created (PENDING).")

# Verify
r = api("GET", "/orders", params={"page":0,"size":10,"status":"PENDING"})
if isinstance(r, dict):
    print(f"Total PENDING orders: {r.get('totalElements', 0)}")
elif isinstance(r, list):
    print(f"Total PENDING orders: {len(r)}")
