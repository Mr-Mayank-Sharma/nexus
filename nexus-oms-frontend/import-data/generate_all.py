#!/usr/bin/env python3
"""Generate 1000+ record CSV files for all 10 entity types."""

import csv, os, random, json
from datetime import datetime, timedelta

OUT = os.path.dirname(os.path.abspath(__file__))
random.seed(42)

# ── Realistic data pools ──────────────────────────────────────────────

CITIES = [
    ("Mumbai", "MH", "400001"), ("Delhi", "DL", "110001"), ("Bangalore", "KA", "560001"),
    ("Hyderabad", "TS", "500001"), ("Ahmedabad", "GJ", "380001"), ("Chennai", "TN", "600001"),
    ("Kolkata", "WB", "700001"), ("Pune", "MH", "411001"), ("Jaipur", "RJ", "302001"),
    ("Lucknow", "UP", "226001"), ("Surat", "GJ", "395001"), ("Nagpur", "MH", "440001"),
    ("Indore", "MP", "452001"), ("Bhopal", "MP", "462001"), ("Ludhiana", "PB", "141001"),
    ("Agra", "UP", "282001"), ("Nashik", "MH", "422001"), ("Faridabad", "HR", "121001"),
    ("Meerut", "UP", "250001"), ("Rajkot", "GJ", "360001"), ("Varanasi", "UP", "221001"),
    ("Srinagar", "JK", "190001"), ("Amritsar", "PB", "143001"), ("Allahabad", "UP", "211001"),
    ("Visakhapatnam", "AP", "530001"), ("Tiruvananthapuram", "KL", "695001"),
    ("Kochi", "KL", "682001"), ("Coimbatore", "TN", "641001"), ("Vadodara", "GJ", "390001"),
    ("Guwahati", "AS", "781001"), ("Chandigarh", "CH", "160001"), ("Mysore", "KA", "570001"),
    ("Udaipur", "RJ", "313001"), ("Goa", "GA", "403001"), ("Bhubaneswar", "OD", "751001"),
]

FIRST_NAMES = [
    "Aarav", "Vivaan", "Aditya", "Vihaan", "Arjun", "Sai", "Reyansh", "Ayaan", "Krishna",
    "Ishaan", "Ananya", "Diya", "Myra", "Aadhya", "Sara", "Aanya", "Saanvi", "Aaradhya",
    "Anaya", "Ishita", "Rohan", "Kabir", "Dhruv", "Shaurya", "Dev", "Arnav", "Yash",
    "Raj", "Vikram", "Neha", "Priya", "Sneha", "Pooja", "Riya", "Shreya", "Nisha",
    "Rahul", "Amit", "Sunil", "Vijay", "Sanjay", "Manish", "Deepak", "Anil", "Suresh",
    "Aarti", "Kavita", "Geeta", "Usha", "Rekha", "Nita", "Lata", "Anita", "Sarita",
]

LAST_NAMES = [
    "Sharma", "Verma", "Gupta", "Singh", "Patel", "Kumar", "Joshi", "Reddy", "Nair",
    "Menon", "Chopra", "Malhotra", "Mehta", "Agarwal", "Mishra", "Pandey", "Saxena",
    "Choudhary", "Bhatt", "Desai", "Shah", "Modi", "Patil", "Rao", "Das", "Sen", "Ghosh",
]

COMPANIES = [
    "Acme Corp", "TechStart Inc", "GlobalMart", "InnovateTech", "PrimeSourcing",
    "NexGen Logistics", "Reliable Retail", "Quantum Distribution", "Pinnacle Wholesale",
    "Elite Traders", "Metro Supplies", "Apex Ventures", "Zenith Group", "Orion Industries",
    "Delta Commerce", "Sigma Networks", "Vega Exports", "Omega Brands", "Crest Enterprises",
    "Nova Distributors", "Atlas Merchants", "Titan Trading", "Polaris Retail",
    "Astra Corporation", "Helix Suppliers", "Vertex Solutions", "Eclipse Logistics",
    "Phoenix Trade", "Horizon Wholesale", "Cascade Imports",
]

PRODUCTS = [
    ("WIDGET-%04d", "Aluminum Widget", 29.99), ("WIDGET-%04d", "Plastic Widget XL", 14.99),
    ("GADGET-%04d", "Smart Gadget Pro", 89.99), ("GADGET-%04d", "Smart Gadget Lite", 49.99),
    ("TOOL-%04d", "Precision Tool Kit", 59.99), ("TOOL-%04d", "Basic Tool Set", 24.99),
    ("ELEC-%04d", "Power Adapter 100W", 34.99), ("ELEC-%04d", "USB-C Hub 7-in-1", 44.99),
    ("CABLE-%04d", "HDMI Cable 6ft", 12.99), ("CABLE-%04d", "USB-C Cable 3ft", 8.99),
    ("BATT-%04d", "Lithium Battery Pack", 79.99), ("BATT-%04d", "AA Rechargeable 4pk", 19.99),
    ("LIGHT-%04d", "LED Desk Lamp", 39.99), ("LIGHT-%04d", "Smart Bulb WiFi", 24.99),
    ("AUDIO-%04d", "Bluetooth Speaker", 59.99), ("AUDIO-%04d", "Noise Canceling Headphones", 149.99),
    ("CHARG-%04d", "Wireless Charger Pad", 29.99), ("CHARG-%04d", "Car Charger 36W", 19.99),
    ("CASE-%04d", "Phone Case Silicone", 14.99), ("CASE-%04d", "Tablet Folio Case", 29.99),
    ("FILTER-%04d", "Air Purifier Filter", 39.99), ("FILTER-%04d", "Water Filter Cartridge", 24.99),
    ("PACK-%04d", "Shipping Box S", 4.99), ("PACK-%04d", "Shipping Box M", 6.99),
    ("PACK-%04d", "Shipping Box L", 8.99), ("PACK-%04d", "Bubble Wrap Roll", 12.99),
    ("SEAL-%04d", "Packing Tape 36yd", 3.99), ("SEAL-%04d", "Tamper Seal 100pk", 9.99),
    ("LABEL-%04d", "Thermal Label 4x6 250pk", 24.99), ("LABEL-%04d", "Address Label 100pk", 7.99),
    ("GLOVE-%04d", "Nitrile Gloves M 100pk", 14.99), ("GLOVE-%04d", "Cut Resistant Gloves L", 19.99),
    ("UNIF-%04d", "Warehouse Vest Orange", 22.99), ("UNIF-%04d", "Safety Goggles Anti-Fog", 11.99),
    ("CART-%04d", "Utility Cart 300lb", 199.99), ("CART-%04d", "Platform Truck 600lb", 349.99),
    ("RACK-%04d", "Wire Shelving Unit 5-tier", 129.99), ("RACK-%04d", "Pallet Rack 96in", 249.99),
    ("SCAN-%04d", "Barcode Scanner USB", 89.99), ("SCAN-%04d", "Mobile Computer PDA", 599.99),
    ("PRINT-%04d", "Label Printer 4x6", 299.99), ("PRINT-%04d", "Receipt Printer Thermal", 199.99),
]

WAREHOUSES = [
    ("WH-MUM-01", "Mumbai Central Warehouse", "Mumbai"),
    ("WH-DEL-01", "Delhi Logistics Hub", "Delhi"),
    ("WH-BLR-01", "Bangalore Fulfillment Center", "Bangalore"),
    ("WH-HYD-01", "Hyderabad Distribution Center", "Hyderabad"),
    ("WH-CHE-01", "Chennai Port Warehouse", "Chennai"),
    ("WH-KOL-01", "Kolkata Storage Facility", "Kolkata"),
    ("WH-PNE-01", "Pune Industrial Depot", "Pune"),
    ("WH-JAI-01", "Jaipur Regional Warehouse", "Jaipur"),
    ("WH-AHM-01", "Ahmedabad Logistics Park", "Ahmedabad"),
    ("WH-LKO-01", "Lucknow Supply Hub", "Lucknow"),
]

SUPPLIERS = [
    "Alpha Components Ltd", "Beta Manufacturing Inc", "Gamma Raw Materials",
    "Delta Parts Supply", "Epsilon Industrial", "Zeta Fabrication",
    "Eta Packaging Co", "Theta Electronics", "Iota Hardware Solutions",
    "Kappa Machinery Works", "Lambda Tools & Dies", "Mu Engineering Corp",
    "Nu Systems Inc", "Xi Industrial Supply", "Omicron Materials Tech",
]

CATEGORIES = ["Electronics", "Packaging", "Tools", "Safety Equipment", "Office Supplies",
              "Warehouse Equipment", "Batteries & Power", "Cables & Adapters",
              "Lighting", "Cleaning Supplies", "Automation", "Labels & Printing"]


def pick_city():
    return random.choice(CITIES)

def name():
    return f"{random.choice(FIRST_NAMES)} {random.choice(LAST_NAMES)}"

def email(name_part=None):
    if not name_part:
        name_part = name().lower().replace(" ", ".")
    domains = ["gmail.com", "yahoo.com", "outlook.com", "nexusmail.com", "company.in",
               "rediffmail.com", "proton.me", "zoho.com", "fastmail.com", "icloud.com"]
    return f"{name_part}@{random.choice(domains)}"

def phone():
    return f"+91-{random.randint(70000,99999)}{random.randint(10000,99999)}"

def company():
    return random.choice(COMPANIES)

def sku():
    fmt, name, price = random.choice(PRODUCTS)
    return fmt % random.randint(1, 9999)

def product_name():
    _, name, _ = random.choice(PRODUCTS)
    return name

def product_price():
    _, _, price = random.choice(PRODUCTS)
    return price

def street():
    num = random.randint(1, 999)
    names = ["Main St", "Park Ave", "Industrial Blvd", "Technology Park", "Business Center",
             "Ring Road", "Sector", "Lake View Rd", "Market Complex", "Tower Road",
             "MG Road", "Station Road", "Hospital Road", "College Road", "Bazaar Street"]
    return f"{num}, {random.choice(names)}"

def order_date():
    start = datetime(2025, 1, 1)
    end = datetime(2026, 6, 28)
    delta = end - start
    random_days = random.randint(0, delta.days)
    return (start + timedelta(days=random_days)).strftime("%Y-%m-%d")


# ── Generators ────────────────────────────────────────────────────────

def gen_customers(path, n=1000):
    """name, email, phone, street, city, state, zip, country"""
    seen = set()
    with open(path, "w", newline="") as f:
        w = csv.writer(f)
        w.writerow(["name", "email", "phone", "street", "city", "state", "zip", "country"])
        for i in range(n):
            nm = name()
            em = email()
            while em in seen:
                em = email()
            seen.add(em)
            cty, st, zp = pick_city()
            w.writerow([nm, em, phone(), street(), cty, st, zp, "India"])
    print(f"  {n} customers → {path}")


def gen_orders(path, n=1000):
    """CustomerName, Email, Phone, Price, Quantity, SKU, Description,
       ShippingStreet, ShippingCity, ShippingState, ShippingZip, OrderNotes"""
    notes_pool = ["Rush delivery needed", "Handle with care", "Fragile items",
                  "Gift wrap please", "Leave at front door", "Call before delivery",
                  "Standard shipping", "Express delivery", "Weekend delivery",
                  "Office hours only", "", "", "", ""]
    with open(path, "w", newline="") as f:
        w = csv.writer(f)
        w.writerow(["CustomerName", "Email", "Phone", "Price", "Quantity",
                     "SKU", "Description", "ShippingStreet", "ShippingCity",
                     "ShippingState", "ShippingZip", "OrderNotes"])
        for i in range(n):
            cname = company()
            cem = email(cname.lower().replace(" ", "."))
            cty, st, zp = pick_city()
            qty = random.randint(1, 50)
            price = round(product_price() + random.uniform(-2, 2), 2)
            note = random.choice(notes_pool)
            w.writerow([cname, cem, phone(), f"{price:.2f}", qty,
                         sku(), product_name(), street(),
                         cty, st, zp, note])
    print(f"  {n} orders → {path}")


def gen_products(path, n=1000):
    """sku, name, description, price, category, supplier"""
    with open(path, "w", newline="") as f:
        w = csv.writer(f)
        w.writerow(["sku", "name", "description", "price", "category", "supplier"])
        for i in range(n):
            s = sku()
            pn = product_name()
            desc = f"{pn} - high quality {random.choice(CATEGORIES).lower()} product"
            price = f"{product_price():.2f}"
            cat = random.choice(CATEGORIES)
            sup = random.choice(SUPPLIERS)
            w.writerow([s, pn, desc, price, cat, sup])
    print(f"  {n} products → {path}")


def gen_inventory(path, n=1000):
    """sku, warehouse_code, quantity, reorder_level, bin_location"""
    bins = [f"{random.choice('ABCDEFGH')}{random.randint(1,99)}" for _ in range(50)]
    with open(path, "w", newline="") as f:
        w = csv.writer(f)
        w.writerow(["sku", "warehouse_code", "quantity", "reorder_level", "bin_location"])
        for i in range(n):
            s = sku()
            wh = random.choice(WAREHOUSES)[0]
            qty = random.randint(0, 5000)
            rl = random.randint(10, 200)
            bl = random.choice(bins)
            w.writerow([s, wh, qty, rl, bl])
    print(f"  {n} inventory → {path}")


def gen_shipments(path, n=1000):
    """order_id, carrier, tracking_number, status, origin_city, origin_state,
       dest_city, dest_state, ship_date, weight_kg"""
    carriers = ["UPS", "FedEx", "DHL", "BlueDart", "Delhivery", "DTDC", "India Post",
                "Ekart", "XpressBees", "ShadowFax"]
    statuses = ["PENDING", "PICKED_UP", "IN_TRANSIT", "OUT_FOR_DELIVERY", "DELIVERED",
                "EXCEPTION"]
    with open(path, "w", newline="") as f:
        w = csv.writer(f)
        w.writerow(["order_id", "carrier", "tracking_number", "status",
                     "origin_city", "origin_state", "dest_city", "dest_state",
                     "ship_date", "weight_kg"])
        for i in range(n):
            oid = f"ORD-{random.randint(10000,99999)}"
            car = random.choice(carriers)
            tn = f"1Z{random.randint(100,999)}{random.choice('AEIOU')}{random.randint(100000000,999999999)}"
            st = random.choice(statuses)
            oc, os_, _ = pick_city()
            dc, ds_, _ = pick_city()
            sd = order_date()
            wt = round(random.uniform(0.5, 50.0), 2)
            w.writerow([oid, car, tn, st, oc, os_, dc, ds_, sd, wt])
    print(f"  {n} shipments → {path}")


def gen_returns(path, n=1000):
    """order_id, sku, reason, condition, refund_amount, return_date"""
    reasons = ["DEFECTIVE", "WRONG_ITEM", "DAMAGED", "NOT_AS_DESCRIBED",
               "DELAYED_DELIVERY", "DUPLICATE_ORDER", "CHANGED_MIND", "SIZE_ISSUE"]
    conditions = ["NEW", "LIKE_NEW", "GOOD", "FAIR", "DAMAGED"]
    with open(path, "w", newline="") as f:
        w = csv.writer(f)
        w.writerow(["order_id", "sku", "reason", "condition", "refund_amount", "return_date"])
        for i in range(n):
            oid = f"ORD-{random.randint(10000,99999)}"
            s = sku()
            r = random.choice(reasons)
            c = random.choice(conditions)
            ra = round(random.uniform(5.0, 500.0), 2)
            rd = order_date()
            w.writerow([oid, s, r, c, f"{ra:.2f}", rd])
    print(f"  {n} returns → {path}")


def gen_suppliers(path, n=500):
    """name, contact_person, email, phone, address, city, state, zip, country, category"""
    with open(path, "w", newline="") as f:
        w = csv.writer(f)
        w.writerow(["name", "contact_person", "email", "phone", "address",
                     "city", "state", "zip", "country", "category"])
        for i in range(n):
            sup = random.choice(SUPPLIERS)
            cp = name()
            em = email(cp.lower().replace(" ", "."))
            ph = phone()
            addr = street()
            cty, st, zp = pick_city()
            cat = random.choice(CATEGORIES)
            w.writerow([sup, cp, em, ph, addr, cty, st, zp, "India", cat])
    print(f"  {n} suppliers → {path}")


def gen_purchase_orders(path, n=1000):
    """supplier, sku, quantity, unit_price, status, order_date, expected_date, notes"""
    statuses = ["DRAFT", "PENDING_APPROVAL", "APPROVED", "ORDERED", "PARTIAL",
                "RECEIVED", "CANCELLED"]
    notes = ["Urgent requirement", "Standard order", "Quarterly restock",
             "Promotional order", "Backfill stock", "", "", ""]
    with open(path, "w", newline="") as f:
        w = csv.writer(f)
        w.writerow(["supplier", "sku", "quantity", "unit_price", "status",
                     "order_date", "expected_date", "notes"])
        for i in range(n):
            sup = random.choice(SUPPLIERS)
            s = sku()
            qty = random.randint(50, 5000)
            price = round(product_price() * random.uniform(0.6, 0.9), 2)
            st = random.choice(statuses)
            od = order_date()
            exp = (datetime.strptime(od, "%Y-%m-%d") + timedelta(days=random.randint(7, 45))).strftime("%Y-%m-%d")
            nt = random.choice(notes)
            w.writerow([sup, s, qty, f"{price:.2f}", st, od, exp, nt])
    print(f"  {n} purchase-orders → {path}")


def gen_invoices(path, n=1000):
    """order_id, invoice_number, amount, status, due_date, issued_date, customer_name"""
    statuses = ["DRAFT", "SENT", "PAID", "OVERDUE", "CANCELLED", "PARTIAL"]
    with open(path, "w", newline="") as f:
        w = csv.writer(f)
        w.writerow(["order_id", "invoice_number", "amount", "status",
                     "due_date", "issued_date", "customer_name"])
        for i in range(n):
            oid = f"ORD-{random.randint(10000,99999)}"
            inv = f"INV-{datetime.now().year}-{random.randint(1000,99999)}"
            amt = round(random.uniform(50.0, 5000.0), 2)
            st = random.choice(statuses)
            issued = order_date()
            due = (datetime.strptime(issued, "%Y-%m-%d") + timedelta(days=30)).strftime("%Y-%m-%d")
            cn = company()
            w.writerow([oid, inv, f"{amt:.2f}", st, due, issued, cn])
    print(f"  {n} invoices → {path}")


def gen_warehouses(path, n=100):
    """code, name, address, city, state, zip, capacity_sqft, manager_name, manager_email, manager_phone"""
    with open(path, "w", newline="") as f:
        w = csv.writer(f)
        w.writerow(["code", "name", "address", "city", "state", "zip",
                     "capacity_sqft", "manager_name", "manager_email", "manager_phone"])
        for code, wh_name, city in WAREHOUSES:
            cty, st, zp = pick_city()
            cap = random.randint(20000, 200000)
            mgr = name()
            w.writerow([code, wh_name, street(), cty, st, zp, cap, mgr,
                        email(mgr.lower().replace(" ", ".")), phone()])
        # Pad to at least 100
        for i in range(n - len(WAREHOUSES)):
            code = f"WH-{random.choice('ABCDEFGH')}{random.randint(10,99)}"
            wh_name = f"{random.choice(['Regional','Metro','City','Zone','Area'])} Warehouse {i+1}"
            cty, st, zp = pick_city()
            cap = random.randint(10000, 150000)
            mgr = name()
            w.writerow([code, wh_name, street(), cty, st, zp, cap, mgr,
                        email(mgr.lower().replace(" ", ".")), phone()])
    print(f"  {n} warehouses → {path}")


# ── Main ──────────────────────────────────────────────────────────────

if __name__ == "__main__":
    print("Generating 1000+ record CSVs for all entity types...\n")

    gen_customers(os.path.join(OUT, "customers.csv"), 1000)
    gen_orders(os.path.join(OUT, "orders.csv"), 1000)
    gen_products(os.path.join(OUT, "products.csv"), 1000)
    gen_inventory(os.path.join(OUT, "inventory.csv"), 1000)
    gen_shipments(os.path.join(OUT, "shipments.csv"), 1000)
    gen_returns(os.path.join(OUT, "returns.csv"), 1000)
    gen_suppliers(os.path.join(OUT, "suppliers.csv"), 500)
    gen_purchase_orders(os.path.join(OUT, "purchase-orders.csv"), 1000)
    gen_invoices(os.path.join(OUT, "invoices.csv"), 1000)
    gen_warehouses(os.path.join(OUT, "warehouses.csv"), 100)

    print("\nDone! Files created in:", OUT)
    print("Total records: ~9,600")
