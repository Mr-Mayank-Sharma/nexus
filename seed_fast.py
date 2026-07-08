#!/usr/bin/env python3
"""Fast seed: login, fix DB, create products/customers/orders."""
import json, urllib.request, urllib.error, time, random, sys, subprocess
from urllib.parse import urlencode

BASE = "http://localhost:8080/api/v1"
TOKEN = None
TENANT_ID = None

def api(m, p, d=None, raw=False):
    url = BASE + p
    req = urllib.request.Request(url, method=m)
    req.add_header("Content-Type", "application/json")
    if TOKEN: req.add_header("Authorization", f"Bearer {TOKEN}")
    if d is not None: req.data = json.dumps(d).encode()
    for _ in range(5):
        try:
            with urllib.request.urlopen(req, timeout=30) as r:
                b = json.loads(r.read())
                return b if raw else (b.get("data") if b.get("success") else None)
        except urllib.error.HTTPError as e:
            if e.code in (409, 429): time.sleep(2); continue
            return None
        except: time.sleep(2); continue
    return None

def login():
    global TOKEN, TENANT_ID
    L = api("POST", "/auth/login", {"username":"admin","password":"Test1234!"}, raw=True)
    if not L or not L.get("success"):
        api("POST", "/auth/register", {"username":"admin","password":"Test1234!","role":"ADMIN"})
        L = api("POST", "/auth/login", {"username":"admin","password":"Test1234!"}, raw=True)
    TOKEN = L["data"]["accessToken"]
    TENANT_ID = L["data"]["tenantId"]
    print(f"Tenant: {TENANT_ID}")

def fix_db():
    sql = f"""
    INSERT INTO nx_nodes (id, tenant_id, name, type, address, is_active, capacity_daily)
    SELECT gen_random_uuid(), '{TENANT_ID}', 'Main DC', 'WAREHOUSE', '{{"city":"Dallas","state":"TX"}}', true, 10000
    WHERE NOT EXISTS (SELECT 1 FROM nx_nodes WHERE tenant_id = '{TENANT_ID}');
    INSERT INTO nx_inventory (id, tenant_id, sku, node_id, quantity_on_hand, quantity_allocated, quantity_reserved, safety_stock, reorder_point, reorder_qty, created_at, updated_at)
    SELECT gen_random_uuid(), '{TENANT_ID}', p.sku, n.id, 9999, 0, 0, 100, 50, 200, now(), now()
    FROM (SELECT DISTINCT sku FROM products WHERE tenant_id = '{TENANT_ID}') p
    CROSS JOIN (SELECT id FROM nx_nodes WHERE tenant_id = '{TENANT_ID}' LIMIT 1) n
    ON CONFLICT DO NOTHING;
    """
    subprocess.run(["docker","exec","-i","nexus-postgres","psql","-U","nexus","-d","nexus_oms"],
                   input=sql.encode(), capture_output=True, timeout=30)
    print("DB fix done")

def main():
    login()
    fix_db()
    
    # products
    prods = [("SKU-ELEC-001","Wireless Mouse","Electronics",29.99),
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
        ("SKU-OFF-004","Desk Organizer","Office",24.99)]
    for sku,nm,cat,pr in prods:
        api("POST","/products",{"sku":sku,"productName":nm,"category":cat,"unitPrice":pr,"isActive":True})
    print("Products done")
    
    # customers
    fn_list = ["John","Jane","Bob","Alice","Mike","Sarah","David","Emma"]
    ln_list = ["Smith","Johnson","Williams","Brown","Jones","Garcia","Miller"]
    cids = []
    for i in range(80):
        fn = random.choice(fn_list); ln = random.choice(ln_list)
        r = api("POST","/customers",{"name":f"{fn} {ln}",
            "email":f"{fn.lower()}.{ln.lower()}{i}@ex.com","phone":"+1212555"+f"{i:04d}",
            "addressLine1":f"{random.randint(100,9999)} Main St","city":"Dallas","state":"TX",
            "pincode":"75201","country":"US","isActive":True})
        if r: cids.append(r["id"])
    print(f"Customers: {len(cids)}")
    
    # warehouse + staff
    wh = api("POST","/warehouses",{"code":"WH-MAIN","name":"Main DC","type":"WAREHOUSE",
        "status":"ACTIVE","addressLine1":"1000 Logistics Way","city":"Dallas","state":"TX",
        "zipCode":"75201","country":"US","dockCount":20,"isActive":True})
    for i,(fn,ln,role) in enumerate([("John","S","PICKER"),("Emma","J","PACKER"),
        ("Mike","B","PICKER"),("Sarah","D","PACKER")]):
        api("POST","/warehouses/staff",{"employeeCode":f"EMP-{1000+i}","firstName":fn,"lastName":ln,
            "role":role,"shift":"MORNING","skills":'["PICKING","PACKING"]',"isActive":True})
    print("Warehouse + staff done")
    
    # carriers
    for n,c in [("FedEx","FEDEX"),("UPS","UPS"),("USPS","USPS")]:
        api("POST","/carriers",{"name":n,"code":c,"type":"SHIPPING","isActive":True})
    print("Carriers done")
    
    # orders - 1900
    chs = ["web","mobile","api","marketplace"]
    skus = ["SKU-ELEC-001","SKU-ELEC-002","SKU-ELEC-003","SKU-ELEC-004","SKU-ELEC-005",
            "SKU-APP-001","SKU-APP-002","SKU-HOME-001","SKU-HOME-002","SKU-HOME-003",
            "SKU-BOOK-001","SKU-BOOK-002","SKU-OFF-001","SKU-OFF-003"]
    pnms = ["Mouse","USB Hub","Speaker","Keyboard","Webcam",
            "T-Shirt","Jacket","Bottle","Lamp","Mat",
            "Python Book","DS Book","Desk Converter","Monitor Arm"]
    oids = []
    for i in range(1900):
        items = []
        for _ in range(random.randint(1,3)):
            idx = random.randint(0,len(skus)-1)
            items.append({"sku":skus[idx],"productName":pnms[idx],
                         "quantity":random.randint(1,4),"unitPrice":round(random.uniform(10,200),2)})
        cust = api("GET",f"/customers/{random.choice(cids)}")
        if not cust: cust = {"name":"Customer","email":"c@x.com"}
        r = api("POST","/orders",{"channel":random.choice(chs),
            "customerName":cust.get("name","Cust"),"customerEmail":cust.get("email","c@x.com"),
            "shippingAddress":{"line1":"123 Main St","city":"Dallas","state":"TX","pincode":"75201"},
            "items":items})
        if r: oids.append(r["id"])
        if (i+1)%200==0: print(f"  {i+1}/1900 orders")
        if r is None: print(f"  FAIL at order {i+1}"); break
    print(f"Orders: {len(oids)}")

if __name__=="__main__":
    main()
