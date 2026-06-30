#!/usr/bin/env python3
"""Generate 500+ record CSV sample files for all import types with relationships and invalid records."""

import csv
import os
import random
import string
from datetime import datetime, timedelta

OUTPUT_DIR = "sample_import_files"
os.makedirs(OUTPUT_DIR, exist_ok=True)

def random_string(length=8):
    return ''.join(random.choices(string.ascii_uppercase, k=length))

def random_email(name):
    domains = ["acmecorp.com", "globex.com", "initech.com", "umbrella.com", "cyberdyne.com",
               "stark.com", "wayne.com", "oscorp.com", "massive.com", "hooli.com"]
    return f"{name.lower().replace(' ', '.')}@{random.choice(domains)}"

def random_phone():
    return f"+1-{random.randint(200,999)}-{random.randint(100,999)}-{random.randint(1000,9999)}"

def random_address():
    streets = ["Oak Ave", "Elm St", "Main St", "Broadway", "Park Ave", "Market St",
               "Washington Blvd", "Lake Dr", "Hill Rd", "River Rd"]
    cities = ["New York", "Los Angeles", "Chicago", "Houston", "Phoenix", "Philadelphia",
              "San Antonio", "San Diego", "Dallas", "Austin", "Portland", "Seattle",
              "Denver", "Boston", "Nashville", "Atlanta", "Miami", "Minneapolis"]
    states = ["NY", "CA", "IL", "TX", "AZ", "PA", "FL", "OH", "CO", "MA", "TN", "GA", "MN", "OR", "WA"]
    return {
        "street": f"{random.randint(100,9999)} {random.choice(streets)}",
        "city": random.choice(cities),
        "state": random.choice(states),
        "zip": f"{random.randint(10000,99999)}"
    }

def random_sku():
    cats = ["ELEC", "HOME", "CLTH", "FOOD", "BOOK", "TOYS", "SPRT", "BEAU", "AUTO", "GARD"]
    return f"{random.choice(cats)}-{random.randint(1000,9999)}"

def random_product_name():
    prefixes = ["Premium", "Basic", "Pro", "Ultra", "Eco", "Smart", "Classic", "Modern"]
    items = ["Widget", "Gadget", "Component", "Device", "Tool", "Kit", "Set", "Pack",
             "System", "Module", "Assembly", "Unit"]
    return f"{random.choice(prefixes)} {random.choice(items)}"

def random_company():
    prefixes = ["Apex", "Global", "Premier", "Elite", "First", "United", "Allied", "Superior"]
    suffixes = ["Supplies", "Industries", "Distributors", "Corp", "Enterprises", "Logistics", "Trading Co"]
    return f"{random.choice(prefixes)} {random.choice(suffixes)}"

# Track generated IDs for relationships
generated_customers = []
generated_orders = []
generated_products = []
generated_suppliers = []
generated_warehouses = []

def generate_customers_csv(num_records=520, output_dir=OUTPUT_DIR):
    """Generate customer CSV with realistic data + invalid records."""
    filepath = os.path.join(output_dir, "customers.csv")
    with open(filepath, "w", newline="") as f:
        writer = csv.writer(f)
        writer.writerow(["name", "email", "phone", "street", "city", "state", "zip", "country"])

        for i in range(num_records):
            if i < 510:  # 510 valid records
                name = f"{random.choice(['John','Jane','Bob','Alice','Mike','Sarah','David','Emma','Chris','Lisa'])} {random.choice(['Smith','Johnson','Williams','Brown','Jones','Garcia','Miller','Davis','Wilson','Taylor'])}"
                addr = random_address()
                writer.writerow([
                    name,
                    random_email(name.split()[0]),
                    random_phone(),
                    addr["street"],
                    addr["city"],
                    addr["state"],
                    addr["zip"],
                    "US"
                ])
                generated_customers.append({"name": name, "email": random_email(name.split()[0]), "phone": random_phone(), "address": addr})
            elif i == 510:  # Missing name
                writer.writerow(["", "missingname@test.com", random_phone(), "123 Test St", "NYC", "NY", "10001", "US"])
            elif i == 511:  # Missing email
                writer.writerow(["No Email Guy", "", random_phone(), "456 Oak Ave", "LA", "CA", "90001", "US"])
            elif i == 512:  # Invalid phone
                writer.writerow(["Bad Phone", "badphone@test.com", "NOT_A_PHONE", "789 Elm St", "Chicago", "IL", "60601", "US"])
            elif i == 513:  # Empty row (all blanks)
                writer.writerow(["", "", "", "", "", "", "", ""])
            elif i == 514:  # Duplicate of first customer
                writer.writerow([generated_customers[0]["name"], generated_customers[0]["email"],
                                generated_customers[0]["phone"], generated_customers[0]["address"]["street"],
                                generated_customers[0]["address"]["city"], generated_customers[0]["address"]["state"],
                                generated_customers[0]["address"]["zip"], "US"])
            elif i == 515:  # Extra long name
                writer.writerow(["A" * 300, "longname@test.com", random_phone(), "1 Main St", "Boston", "MA", "02101", "US"])
            elif i == 516:  # Invalid country
                writer.writerow(["Invalid Country", "invalidcountry@test.com", random_phone(), "2 Broad St", "Miami", "FL", "33101", "INVALIDCOUNTRYTHATISWAYTOOLONG"])
            elif i == 517:  # Missing address
                writer.writerow(["No Address", "noaddress@test.com", random_phone(), "", "", "", "", "US"])
            elif i == 518:  # Wrong data type for zip
                writer.writerow(["Bad Zip", "badzip@test.com", random_phone(), "3 Test Ave", "Seattle", "WA", "ZIPCODE", "US"])
            elif i == 519:  # All valid
                writer.writerow(["Valid Extra", "validextra@test.com", random_phone(), "4 Extra St", "Denver", "CO", "80201", "US"])

    print(f"Generated {filepath} - {num_records} records (10 invalid)")
    return filepath

def generate_products_csv(num_records=520, output_dir=OUTPUT_DIR):
    """Generate product-like data (maps to inventory)."""
    filepath = os.path.join(output_dir, "products.csv")
    with open(filepath, "w", newline="") as f:
        writer = csv.writer(f)
        writer.writerow(["sku", "name", "description", "category", "price", "quantity", "reorder_level"])

        for i in range(num_records):
            sku = random_sku()
            if i < 510:
                writer.writerow([
                    sku,
                    random_product_name(),
                    f"High-quality {random.choice(['electronic','household','clothing','sports','automotive'])} product",
                    random.choice(["Electronics", "Home", "Clothing", "Sports", "Auto", "Books", "Toys"]),
                    round(random.uniform(1.99, 999.99), 2),
                    random.randint(0, 5000),
                    random.randint(10, 200)
                ])
                generated_products.append({"sku": sku, "name": random_product_name()})
            else:
                # Invalid records
                if i == 510:
                    writer.writerow(["", "No SKU", "Missing SKU", "Test", 10.0, 100, 10])
                elif i == 511:
                    writer.writerow(["INVALID-PRICE", "Bad Price", "Invalid price", "Test", "FREE", 100, 10])
                elif i == 512:
                    writer.writerow(["NEGATIVE-QTY", "Negative Qty", "Negative", "Test", 10.0, -5, 10])
                elif i == 513:
                    writer.writerow(["", "", "", "", "", "", ""])
                elif i == 514:
                    writer.writerow([sku, "Duplicate SKU", "Has same SKU as valid record", "Test", 15.0, 50, 5])
                elif i == 515:
                    writer.writerow(["HUGE-NAME-" + "X" * 500, "Long SKU", "Very long SKU", "Test", 10.0, 100, 10])
                elif i == 516:
                    writer.writerow(["ZERO-PRICE", "Zero Price", "Zero price item", "Test", 0.0, 100, 10])
                elif i == 517:
                    writer.writerow(["NON-NUMERIC-QTY", "Bad Qty", "String qty", "Test", 10.0, "MANY", 10])
                elif i == 518:
                    writer.writerow(["NEGATIVE-REORDER", "Bad Reorder", "Negative reorder", "Test", 10.0, 100, -10])
                elif i == 519:
                    writer.writerow(["ALLEMPTY,,,,,", "", "", "", "", "", ""])

    print(f"Generated {filepath} - {num_records} records (10 invalid)")
    return filepath

def generate_orders_csv(num_records=520, output_dir=OUTPUT_DIR):
    """Generate orders CSV with relationships to customers and products."""
    if not generated_customers:
        print("ERROR: Generate customers first!")
        return None

    filepath = os.path.join(output_dir, "orders.csv")
    with open(filepath, "w", newline="") as f:
        writer = csv.writer(f)
        writer.writerow(["CustomerName", "Email", "Phone", "channel", "SKU", "Description",
                         "Quantity", "Price", "ShippingStreet", "ShippingCity", "ShippingState", "ShippingZip"])

        for i in range(num_records):
            cust = random.choice(generated_customers)
            prod = random.choice(generated_products) if generated_products else {"sku": random_sku(), "name": random_product_name()}
            addr = cust["address"]

            if i < 505:
                writer.writerow([
                    cust["name"], cust["email"], cust["phone"],
                    random.choice(["WEB", "PHONE", "EMAIL", "POS", "MARKETPLACE"]),
                    prod["sku"], prod["name"],
                    random.randint(1, 10),
                    round(random.uniform(5.0, 500.0), 2),
                    addr["street"], addr["city"], addr["state"], addr["zip"]
                ])
                generated_orders.append({"cust": cust, "prod": prod})
            else:
                if i == 505:
                    writer.writerow(["", "", "", "WEB", "SKU-9999", "Missing Customer", 1, 10.0, "1 St", "City", "ST", "12345"])
                elif i == 506:
                    writer.writerow(["No SKU", "nosku@test.com", random_phone(), "WEB", "", "No SKU", 1, 10.0, "2 St", "City", "ST", "12345"])
                elif i == 507:
                    writer.writerow(["Bad Qty", "badqty@test.com", random_phone(), "WEB", "SKU-BADQ", "Bad Qty", -1, 10.0, "3 St", "City", "ST", "12345"])
                elif i == 508:
                    writer.writerow(["Bad Price", "badprice@test.com", random_phone(), "WEB", "SKU-BPR", "Bad Price", 1, -5.0, "4 St", "City", "ST", "12345"])
                elif i == 509:
                    writer.writerow(["No Address", "noaddr@test.com", random_phone(), "WEB", "SKU-NAD", "No Addr", 1, 10.0, "", "", "", ""])
                elif i == 510:
                    writer.writerow(["", "", "", "", "", "", "", "", "", "", "", ""])
                elif i == 511:
                    writer.writerow(["Null Channel", "nullchan@test.com", random_phone(), "", "SKU-NC", "Null Channel", 1, 10.0, "5 St", "City", "ST", "12345"])
                elif i == 512:
                    writer.writerow(["Invalid Numeric", "invnum@test.com", random_phone(), "WEB", "SKU-INV", "Invalid", "ZERO", "FREE", "6 St", "City", "ST", "12345"])
                elif i == 513:
                    writer.writerow(["Very " + "Long " * 50, "toolong@test.com", random_phone(), "WEB", "SKU-VL", "Very Long Name", 1, 10.0, "7 St", "City", "ST", "12345"])
                elif i == 514:
                    cust2 = random.choice(generated_customers)
                    writer.writerow([cust2["name"], cust2["email"], cust2["phone"], "WEB", "SKU-DUP", "Duplicate Pattern", 1, 10.0, "8 St", "City", "ST", "12345"])
                elif i >= 515 and i < 520:
                    writer.writerow([cust["name"], cust["email"], cust["phone"], "WEB", prod["sku"], prod["name"], random.randint(1, 5), round(random.uniform(5.0, 200.0), 2), addr["street"], addr["city"], addr["state"], addr["zip"]])

    print(f"Generated {filepath} - {num_records} records (15 invalid)")
    return filepath

def generate_inventory_csv(num_records=510, output_dir=OUTPUT_DIR):
    """Generate inventory CSV."""
    filepath = os.path.join(output_dir, "inventory.csv")
    with open(filepath, "w", newline="") as f:
        writer = csv.writer(f)
        writer.writerow(["sku", "quantity", "reorder_level"])

        for i in range(num_records):
            sku = random_sku()
            if i < 505:
                writer.writerow([sku, random.randint(0, 10000), random.randint(10, 500)])
            elif i == 505:
                writer.writerow(["", 100, 10])
            elif i == 506:
                writer.writerow(["NEGATIVE-QTY", -50, 10])
            elif i == 507:
                writer.writerow(["STRING-QTY", "NONE", 10])
            elif i == 508:
                writer.writerow(["", "", ""])
            elif i == 509:
                writer.writerow(["VALID-EXTRA", 500, 25])

    print(f"Generated {filepath} - {num_records} records (5 invalid)")
    return filepath

def generate_shipments_csv(num_records=510, output_dir=OUTPUT_DIR):
    """Generate shipments CSV."""
    filepath = os.path.join(output_dir, "shipments.csv")
    with open(filepath, "w", newline="") as f:
        writer = csv.writer(f)
        writer.writerow(["carrier", "tracking_number", "status"])

        carriers = ["FEDEX", "UPS", "DHL", "USPS", "CANADA_POST", "BLUE_DART", "DELHIVERY"]
        statuses = ["PENDING", "PICKED_UP", "IN_TRANSIT", "OUT_FOR_DELIVERY", "DELIVERED", "EXCEPTION"]

        for i in range(num_records):
            if i < 505:
                writer.writerow([
                    random.choice(carriers),
                    f"1Z{random.randint(1000000000,9999999999)}",
                    random.choice(statuses)
                ])
            elif i == 505:
                writer.writerow(["", "", ""])
            elif i == 506:
                writer.writerow(["INVALID_CARRIER_THAT_IS_WAY_TOO_LONG", "TRACK001", "PENDING"])
            elif i == 507:
                writer.writerow(["FEDEX", "", "PENDING"])  # Missing tracking
            elif i == 508:
                writer.writerow(["FEDEX", "TRACK002", "INVALID_STATUS_THAT_DOES_NOT_EXIST"])
            elif i == 509:
                writer.writerow(["DHL", "TRACK003", "DELIVERED"])

    print(f"Generated {filepath} - {num_records} records (5 invalid)")
    return filepath

def generate_returns_csv(num_records=510, output_dir=OUTPUT_DIR):
    """Generate returns CSV."""
    filepath = os.path.join(output_dir, "returns.csv")
    with open(filepath, "w", newline="") as f:
        writer = csv.writer(f)
        writer.writerow(["reason", "condition", "refund_amount"])

        reasons = ["DEFECTIVE", "DAMAGED", "WRONG_ITEM", "NOT_AS_DESCRIBED", "LATE_DELIVERY",
                   "NO_LONGER_NEEDED", "DUPLICATE_ORDER", "SIZE_ISSUE", "QUALITY_ISSUE", "OTHER"]
        conditions = ["NEW", "LIKE_NEW", "GOOD", "FAIR", "POOR", "DAMAGED"]

        for i in range(num_records):
            if i < 505:
                writer.writerow([
                    random.choice(reasons),
                    random.choice(conditions),
                    round(random.uniform(5.0, 500.0), 2)
                ])
            elif i == 505:
                writer.writerow(["", "", ""])
            elif i == 506:
                writer.writerow(["INVALID_REASON_CODE_XXXX", "NEW", 50.0])
            elif i == 507:
                writer.writerow(["DEFECTIVE", "INVALID_CONDITION_XXX", 50.0])
            elif i == 508:
                writer.writerow(["DEFECTIVE", "NEW", -50.0])  # Negative refund
            elif i == 509:
                writer.writerow(["OTHER", "NEW", 100.0])

    print(f"Generated {filepath} - {num_records} records (5 invalid)")
    return filepath

def generate_suppliers_csv(num_records=510, output_dir=OUTPUT_DIR):
    """Generate suppliers CSV."""
    filepath = os.path.join(output_dir, "suppliers.csv")
    with open(filepath, "w", newline="") as f:
        writer = csv.writer(f)
        writer.writerow(["name", "email", "phone", "address", "city", "state", "zip", "country"])

        for i in range(num_records):
            company = random_company()
            if i < 505:
                addr = random_address()
                writer.writerow([
                    company,
                    random_email(company.split()[0]),
                    random_phone(),
                    addr["street"],
                    addr["city"],
                    addr["state"],
                    addr["zip"],
                    "US"
                ])
                generated_suppliers.append({"name": company})
            elif i == 505:
                writer.writerow(["", "", "", "", "", "", "", ""])
            elif i == 506:
                writer.writerow([company, "bademail", random_phone(), "1 St", "City", "ST", "12345", "US"])
            elif i == 507:
                writer.writerow([random_company(), "", random_phone(), "2 St", "City", "ST", "12345", "US"])
            elif i == 508:
                writer.writerow([random_company(), random_email("test"), random_phone(), "3 St", "City", "ST", "12345", "XX"])
            elif i == 509:
                writer.writerow(["Valid " + random_company(), random_email("valid"), random_phone(), "4 St", "City", "ST", "12345", "US"])

    print(f"Generated {filepath} - {num_records} records (5 invalid)")
    return filepath

def generate_purchase_orders_csv(num_records=510, output_dir=OUTPUT_DIR):
    """Generate purchase orders CSV."""
    filepath = os.path.join(output_dir, "purchase-orders.csv")
    with open(filepath, "w", newline="") as f:
        writer = csv.writer(f)
        writer.writerow(["status", "notes"])

        statuses = ["DRAFT", "SUBMITTED", "APPROVED", "RECEIVED", "CANCELLED"]

        for i in range(num_records):
            if i < 505:
                writer.writerow([
                    random.choice(statuses),
                    f"Purchase order #{i+1} - {random.choice(['urgent','standard','priority','scheduled'])} delivery"
                ])
            elif i == 505:
                writer.writerow(["", ""])
            elif i == 506:
                writer.writerow(["INVALID_STATUS_XYZ", "Bad status"])
            elif i == 507:
                writer.writerow(["DRAFT", ""])
            elif i == 508:
                writer.writerow(["DRAFT", "A" * 2000])  # Very long notes
            elif i == 509:
                writer.writerow(["APPROVED", "Valid PO"])

    print(f"Generated {filepath} - {num_records} records (5 invalid)")
    return filepath

def generate_invoices_csv(num_records=510, output_dir=OUTPUT_DIR):
    """Generate invoices CSV."""
    filepath = os.path.join(output_dir, "invoices.csv")
    with open(filepath, "w", newline="") as f:
        writer = csv.writer(f)
        writer.writerow(["invoice_number", "status", "amount"])

        statuses = ["DRAFT", "SENT", "PAID", "OVERDUE", "CANCELLED"]

        for i in range(num_records):
            if i < 505:
                writer.writerow([
                    f"INV-{datetime.now().strftime('%Y%m')}-{i+1:05d}",
                    random.choice(statuses),
                    round(random.uniform(50.0, 50000.0), 2)
                ])
            elif i == 505:
                writer.writerow(["", "", ""])
            elif i == 506:
                writer.writerow(["", "SENT", 100.0])
            elif i == 507:
                writer.writerow(["INV-INVALID", "INVALID_STATUS", 100.0])
            elif i == 508:
                writer.writerow(["INV-NEGATIVE", "DRAFT", -100.0])
            elif i == 509:
                writer.writerow(["INV-FINAL", "DRAFT", 1000.0])

    print(f"Generated {filepath} - {num_records} records (5 invalid)")
    return filepath

def generate_warehouses_csv(num_records=510, output_dir=OUTPUT_DIR):
    """Generate warehouses CSV."""
    filepath = os.path.join(output_dir, "warehouses.csv")
    with open(filepath, "w", newline="") as f:
        writer = csv.writer(f)
        writer.writerow(["code", "name", "address", "city", "state", "zip", "country",
                         "capacity_sqft", "manager_name", "manager_phone", "manager_email"])

        warehouse_names = ["Alpha Distribution Hub", "Beta Fulfillment Center", "Gamma Logistics Park",
                          "Delta Storage Facility", "Epsilon Warehouse", "Zeta Cross-Dock",
                          "Eta Distribution Center", "Theta Logistics Hub", "Iota Storage",
                          "Kappa Fulfillment"]

        for i in range(num_records):
            name = random.choice(warehouse_names)
            addr = random_address()
            code = f"WH-{random_string(4)}"

            if i < 505:
                writer.writerow([
                    code, name, addr["street"], addr["city"], addr["state"], addr["zip"], "US",
                    random.randint(5000, 200000),
                    f"Manager {random_string(5)}", random_phone(), random_email("manager")
                ])
                generated_warehouses.append({"code": code, "name": name})
            elif i == 505:
                writer.writerow(["", "", "", "", "", "", "", "", "", "", ""])
            elif i == 506:
                writer.writerow(["NO-NAME", "", addr["street"], addr["city"], addr["state"], addr["zip"], "US", 10000, "Mgr", random_phone(), random_email("mgr")])
            elif i == 507:
                writer.writerow(["BAD-CAPACITY", name, addr["street"], addr["city"], addr["state"], addr["zip"], "US", "HUGE", "Mgr", random_phone(), random_email("mgr")])
            elif i == 508:
                writer.writerow(["", "", "", "", "", "", "", "", "", "", ""])
            elif i == 509:
                writer.writerow(["WH-FINAL", "Final Warehouse", addr["street"], addr["city"], addr["state"], addr["zip"], "US", 50000, "Final Mgr", random_phone(), random_email("final")])

    print(f"Generated {filepath} - {num_records} records (5 invalid)")
    return filepath


if __name__ == "__main__":
    print("=" * 60)
    print("GENERATING ENTERPRISE SAMPLE DATA FOR IMPORT TESTING")
    print("=" * 60)

    # Must generate customers first (others depend on it)
    generate_customers_csv()
    generate_products_csv()
    generate_inventory_csv()
    generate_shipments_csv()
    generate_returns_csv()
    generate_suppliers_csv()
    generate_purchase_orders_csv()
    generate_invoices_csv()
    generate_warehouses_csv()

    # orders depends on customers and products
    generate_orders_csv()

    # Generate edge case files
    print("\nGenerating edge case test files...")

    # Empty file
    with open(os.path.join(OUTPUT_DIR, "empty.csv"), "w") as f:
        f.write("")

    # Header-only file
    with open(os.path.join(OUTPUT_DIR, "header_only.csv"), "w") as f:
        f.write("name,email,phone,street,city,state,zip,country\n")

    # Corrupted file (binary garbage)
    with open(os.path.join(OUTPUT_DIR, "corrupted.csv"), "wb") as f:
        f.write(b"\x00\x01\x02\xFF\xFE\xFD\x00Corrupted\x00Data\x01\x02\x03")

    # Wrong extension (text file named as csv)
    with open(os.path.join(OUTPUT_DIR, "wrong_extension.txt"), "w") as f:
        f.write("name,email\nTest User,test@test.com\n")

    # Extra large row (many columns)
    with open(os.path.join(OUTPUT_DIR, "extra_columns.csv"), "w") as f:
        f.write("col1,col2,col3,col4,col5,col6,col7,col8,col9,col10\n")
        for i in range(10):
            f.write(f"val1,val2,val3,val4,val5,val6,val7,val8,val9,val10\n")

    # Missing columns
    with open(os.path.join(OUTPUT_DIR, "missing_columns.csv"), "w") as f:
        f.write("name,email\n")
        f.write("Only Name\n")

    print(f"\nAll files generated in '{OUTPUT_DIR}/':")
    for f in sorted(os.listdir(OUTPUT_DIR)):
        size = os.path.getsize(os.path.join(OUTPUT_DIR, f))
        print(f"  {f:40s} {size:>8,} bytes")

    print("\nGeneration complete!")
