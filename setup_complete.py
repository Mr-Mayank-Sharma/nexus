#!/usr/bin/env python3
"""One-shot complete setup - creates all infra + 1900 orders + fulfills them."""
import json, urllib.request, urllib.error, time, random, sys, os
from urllib.parse import urlencode

BASE = "http://localhost:8080/api/v1"
TOKEN = None
TENANT_ID = None

def api(method, path, data=None, params=None, raw=False, retries=5):
    last_err = None
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
            with urllib.request.urlopen(req, timeout=60) as resp:
                body = json.loads(resp.read())
                if raw:
                    return body
                if body.get("success"):
                    return body.get("data")
                if "already exists" in str(body.get("message","")):
                    return None
                raise Exception(body.get("message","API error"))
        except urllib.error.HTTPError as e:
            err = e.read().decode()
            try:
                msg = json.loads(err).get("message", err)
            except:
                msg = err
            if e.code in (409, 429):
                time.sleep(2)
                continue
            raise Exception(f"HTTP {e.code}: {str(msg)[:200]}")
        except Exception as e:
            last_err = e
            if attempt < retries - 1:
                time.sleep(2)
                continue
            raise
    raise last_err

def login():
    global TOKEN, TENANT_ID
    L = api("POST", "/auth/login", {"username":"admin","password":"Test1234!"}, raw=True)
    if not L.get("success"):
        api("POST", "/auth/register", {"username":"admin","password":"Test1234!","role":"ADMIN"})
        L = api("POST", "/auth/login", {"username":"admin","password":"Test1234!"}, raw=True)
    TOKEN = L["data"]["accessToken"]
    TENANT_ID = L["data"]["tenantId"]
    print(f"Tenant: {TENANT_ID}")

def setup_db_infra():
    """Create NxNode, NxInventory, fix JSONB via SQL."""
    import subprocess
    sql = f"""
    -- Create NxNode for this tenant
    INSERT INTO nx_nodes (id, tenant_id, name, type, address, is_active, capacity_daily)
    SELECT gen_random_uuid(), '{TENANT_ID}', 'Main DC', 'WAREHOUSE', '{{"city":"Dallas","state":"TX"}}', true, 10000
    WHERE NOT EXISTS (SELECT 1 FROM nx_nodes WHERE tenant_id = '{TENANT_ID}' LIMIT 1);
    
    -- Create inventory records for our tenant's SKUs
    INSERT INTO nx_inventory (id, tenant_id, sku, node_id, quantity_on_hand, quantity_allocated, quantity_reserved, safety_stock, reorder_point, reorder_qty, created_at, updated_at)
    SELECT gen_random_uuid(), '{TENANT_ID}', p.sku, n.id, 9999, 0, 0, 100, 50, 200, now(), now()
    FROM (SELECT DISTINCT sku FROM products WHERE tenant_id = '{TENANT_ID}') p
    CROSS JOIN (SELECT id FROM nx_nodes WHERE tenant_id = '{TENANT_ID}' LIMIT 1) n
    ON CONFLICT DO NOTHING;
    """
    subprocess.run(["docker","exec","-i","nexus-postgres","psql","-U","nexus","-d","nexus_oms"],
                   input=sql.encode(), capture_output=True, timeout=30)
    print("DB infra ready")

def create_products():
    prods = [
        ("SKU-ELEC-001","Wireless Mouse","Electronics",29.99),
        ("SKU-ELEC-002","USB-C Hub","Electronics",49.99),
        ("SKU-ELEC-003","Bluetooth Speaker","Electronics",79.99),
        ("SKU-ELEC-004","Mechanical Keyboard","Electronics",149.99),
        ("SKU-ELEC-005","Webcam HD","Electronics",89.99),
        ("SKU-ELEC-006","Laptop Stand","Electronics",39.99),
        ("SKU-ELEC-007","Noise Cancelling Earbuds","Electronics",199.99),
        ("SKU-ELEC-008","Portable Charger","Electronics",34.99),
        ("SKU-APP-001","Cotton T-Shirt","Apparel",19.99),
        ("SKU-APP-002","Denim Jacket","Apparel",89.99),
        ("SKU-APP-003","Running Shoes","Apparel",129.99),
        ("SKU-APP-004","Wool Scarf","Apparel",29.99),
        ("SKU-HOME-001","Water Bottle","Home",24.99),
        ("SKU-HOME-002","LED Desk Lamp","Home",44.99),
        ("SKU-HOME-003","Yoga Mat","Home",34.99),
        ("SKU-HOME-004","Coffee Maker","Home",69.99),
        ("SKU-HOME-005","Throw Blanket","Home",39.99),
        ("SKU-BOOK-001","Python Cookbook","Books",49.99),
        ("SKU-BOOK-002","Data Science Handbook","Books",59.99),
        ("SKU-BOOK-003","PM Guide","Books",39.99),
        ("SKU-OFF-001","Desk Converter","Office",299.99),
        ("SKU-OFF-002","Office Chair","Office",499.99),
        ("SKU-OFF-003","Monitor Arm","Office",79.99),
        ("SKU-OFF-004","Desk Organizer","Office",24.99),
    ]
    ids = []
    for sku, name, cat, price in prods:
        try:
            r = api("POST","/products",{"sku":sku,"productName":name,"category":cat,"unitPrice":price,"isActive":True})
            if r:
                ids.append(r["id"])
        except Exception as e:
            pass
    print(f"Products: {len(ids)}")
    return ids

def create_customers():
    fn_list = ["John","Jane","Bob","Alice","Mike","Sarah","David","Emma","Chris"]
    ln_list = ["Smith","Johnson","Williams","Brown","Jones","Garcia","Miller","Davis"]
    ids = []
    for i in range(80):
        fn = random.choice(fn_list); ln = random.choice(ln_list)
        cust = {"name":f"{fn} {ln}","email":f"{fn.lower()}.{ln.lower()}{i}@example.com",
                "phone":f"+1{random.randint(200,999)}{random.randint(100,999)}{random.randint(1000,9999)}",
                "addressLine1":f"{random.randint(100,9999)} Main St",
                "city":"Dallas","state":"TX","pincode":"75201","country":"US","isActive":True}
        try:
            r = api("POST","/customers",cust)
            if r: ids.append(r["id"])
        except:
            pass
    print(f"Customers: {len(ids)}")
    return ids

def create_warehouse():
    wh = {"code":"WH-MAIN","name":"Main DC","type":"WAREHOUSE","status":"ACTIVE",
          "addressLine1":"1000 Logistics Way","city":"Dallas","state":"TX","zipCode":"75201",
          "country":"US","dockCount":20,"operatingHours':'{"hours":"24/7"},"isActive":True}
    try:
        r = api("POST","/warehouses",wh)
        wh_id = r["id"]
    except:
        wh_id = None
        print("WH creation skipped")

    staff_ids = []
    for i,(fn,ln,role) in enumerate([("John","Smith","PICKER"),("Emma","Johnson","PACKER"),
        ("Mike","Brown","PICKER"),("Sarah","Davis","PACKER"),("Tom","Wilson","SUPERVISOR")]):
        try:
            s = api("POST","/warehouses/staff",{"employeeCode":f"EMP-{1000+i}","firstName":fn,"lastName":ln,
                "role":role,"shift":"MORNING","skills":'["PICKING","PACKING"]',"isActive":True})
            if s: staff_ids.append(s["id"])
        except:
            pass
    print(f"Staff: {len(staff_ids)}")
    return wh_id, staff_ids

def create_carriers():
    ids = []
    for name,code in [("FedEx","FEDEX"),("UPS","UPS"),("USPS","USPS"),("DHL","DHL")]:
        try:
            r = api("POST","/carriers",{"name":name,"code":code,"type":"SHIPPING","isActive":True})
            if r: ids.append(r["id"])
        except:
            pass
    print(f"Carriers: {len(ids)}")
    return ids

def create_orders(customer_ids, count):
    channels = ["web","mobile","api","marketplace","pos"]
    skus = ["SKU-ELEC-001","SKU-ELEC-002","SKU-ELEC-003","SKU-ELEC-004","SKU-ELEC-005",
            "SKU-APP-001","SKU-APP-002","SKU-APP-003","SKU-HOME-001","SKU-HOME-002",
            "SKU-HOME-003","SKU-BOOK-001","SKU-BOOK-002","SKU-OFF-001","SKU-OFF-003"]
    pnames = ["Mouse","USB Hub","Speaker","Keyboard","Webcam",
              "T-Shirt","Jacket","Shoes","Bottle","Lamp",
              "Yoga Mat","Python Book","DS Book","Desk Converter","Monitor Arm"]
    order_ids = []
    for i in range(count):
        num = random.randint(1,3)
        items = []
        for _ in range(num):
            idx = random.randint(0,len(skus)-1)
            items.append({"sku":skus[idx],"productName":pnames[idx],
                         "quantity":random.randint(1,4),"unitPrice":round(random.uniform(10,200),2)})
        cust = api("GET",f"/customers/{random.choice(customer_ids)}")
        order = {"channel":random.choice(channels),
                 "customerName":cust.get("name","Cust"),
                 "customerEmail":cust.get("email","c@x.com"),
                 "shippingAddress":{"line1":"123 Main St","city":"Dallas","state":"TX","pincode":"75201"},
                 "items":items}
        try:
            r = api("POST","/orders",order)
            if r: order_ids.append(r["id"])
        except Exception as e:
            if "Insufficient inventory" in str(e):
                print(f"  STOP at order {i+1}: inventory issue")
                break
        if (i+1)%200 == 0:
            print(f"  {i+1}/{count} orders")
    print(f"Orders: {len(order_ids)}")
    return order_ids

def main():
    print("="*60)
    login()
    setup_db_infra()
    print("\nProducts...")
    product_ids = create_products()
    print("\nCustomers...")
    customer_ids = create_customers()
    print("\nWarehouse...")
    wh_id, staff_ids = create_warehouse()
    print("\nCarriers...")
    carrier_ids = create_carriers()
    print("\nOrders (1900)...")
    order_ids = create_orders(customer_ids, 1900)
    print("="*60)

if __name__=="__main__":
    main()
