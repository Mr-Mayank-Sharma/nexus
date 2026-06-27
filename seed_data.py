import psycopg2
import json
from datetime import datetime, timedelta
from uuid import UUID

DB_CONFIG = {
    "host": "localhost",
    "port": 5433,
    "dbname": "nexus_oms",
    "user": "nexus",
    "password": "nexus_secret",
}

TENANT_ID = "a1b2c3d4-e5f6-7890-abcd-ef1234567890"
ADMIN_USER_ID = "8529e0f3-9770-4960-98ec-e6c0faf30830"

NODE_IDS = [
    "a1000000-0000-0000-0000-000000000001",
    "a1000000-0000-0000-0000-000000000002",
    "a1000000-0000-0000-0000-000000000003",
    "a1000000-0000-0000-0000-000000000004",
    "a1000000-0000-0000-0000-000000000005",
]
NODE_MUMBAI, NODE_DELHI, NODE_BLR, NODE_CHENNAI, NODE_KOLKATA = NODE_IDS

CUSTOMER_IDS = [
    "b1000000-0000-0000-0000-000000000001",
    "b1000000-0000-0000-0000-000000000002",
    "b1000000-0000-0000-0000-000000000003",
    "b1000000-0000-0000-0000-000000000004",
    "b1000000-0000-0000-0000-000000000005",
    "b1000000-0000-0000-0000-000000000006",
    "b1000000-0000-0000-0000-000000000007",
    "b1000000-0000-0000-0000-000000000008",
]

ORDER_IDS = [f"c1000000-0000-0000-0000-{i:012d}" for i in range(1, 31)]

def oid(n):
    return f"c2000000-0000-0000-0000-{n:012d}"

def teid(n):
    return f"d2000000-0000-0000-0000-{n:012d}"

SHIPMENT_IDS = [f"d1000000-0000-0000-0000-{i:012d}" for i in range(1, 11)]

RETURN_IDS = [f"e1000000-0000-0000-0000-{i:012d}" for i in range(1, 6)]

CARRIER_IDS = [
    "f1000000-0000-0000-0000-000000000001",
    "f1000000-0000-0000-0000-000000000002",
    "f1000000-0000-0000-0000-000000000003",
    "f1000000-0000-0000-0000-000000000004",
]

INVENTORY_IDS = [f"aa000000-0000-0000-0000-{i:012d}" for i in range(1, 26)]

AUDIT_IDS = [f"ab000000-0000-0000-0000-{i:012d}" for i in range(1, 11)]


def main():
    conn = psycopg2.connect(**DB_CONFIG)
    conn.autocommit = False
    cur = conn.cursor()

    try:
        # Disable triggers for clean truncation
        cur.execute("SET session_replication_role = 'replica';")

        tables = [
            "nx_tracking_events", "nx_shipments", "nx_order_items",
            "nx_returns", "nx_audit_log", "nx_inventory",
            "nx_orders", "nx_carrier_accounts", "nx_customers", "nx_nodes",
        ]
        for t in tables:
            cur.execute(f"TRUNCATE TABLE {t} CASCADE;")

        cur.execute("SET session_replication_role = 'origin';")

        # ============================================================
        # 1. NODES (5)
        # ============================================================
        nodes_data = [
            (NODE_MUMBAI, "WH_MUMBAI", "WAREHOUSE", json.dumps({"line1": "Mindspace, Malad West", "city": "Mumbai", "state": "Maharashtra", "pincode": "400064", "country": "India"}), 19.0760, 72.8777, True, 5000, "17:00:00"),
            (NODE_DELHI, "WH_DELHI", "WAREHOUSE", json.dumps({"line1": "Okhla Industrial Estate, Phase III", "city": "New Delhi", "state": "Delhi", "pincode": "110020", "country": "India"}), 28.5355, 77.2730, True, 4500, "16:30:00"),
            (NODE_BLR, "WH_BLR", "WAREHOUSE", json.dumps({"line1": "Whitefield", "city": "Bangalore", "state": "Karnataka", "pincode": "560066", "country": "India"}), 12.9716, 77.5946, True, 4000, "17:30:00"),
            (NODE_CHENNAI, "WH_CHENNAI", "WAREHOUSE", json.dumps({"line1": "Guindy Industrial Estate", "city": "Chennai", "state": "Tamil Nadu", "pincode": "600032", "country": "India"}), 13.0067, 80.2206, True, 3500, "18:00:00"),
            (NODE_KOLKATA, "WH_KOLKATA", "WAREHOUSE", json.dumps({"line1": "Tangra Industrial Area", "city": "Kolkata", "state": "West Bengal", "pincode": "700015", "country": "India"}), 22.5351, 88.3477, True, 3000, "17:00:00"),
        ]
        cur.executemany(
            "INSERT INTO nx_nodes (id, tenant_id, name, type, address, latitude, longitude, is_active, capacity_daily, cut_off_time) VALUES (%s, %s, %s, %s, %s::jsonb, %s, %s, %s, %s, %s::time)",
            [(row[0], TENANT_ID) + row[1:] for row in nodes_data]
        )

        # ============================================================
        # 2. CUSTOMERS (8)
        # ============================================================
        customers_data = [
            (CUSTOMER_IDS[0], "Rahul Sharma", "rahul.sharma@email.com", "+91-9876543210", "EXT-CUST-001", json.dumps({"line1": "42, Marine Drive", "city": "Mumbai", "state": "Maharashtra", "pincode": "400001"})),
            (CUSTOMER_IDS[1], "Priya Patel", "priya.patel@email.com", "+91-9876543211", "EXT-CUST-002", json.dumps({"line1": "15, Ashram Road", "city": "Ahmedabad", "state": "Gujarat", "pincode": "380009"})),
            (CUSTOMER_IDS[2], "Amit Singh", "amit.singh@email.com", "+91-9876543212", "EXT-CUST-003", json.dumps({"line1": "8, Hazratganj", "city": "Lucknow", "state": "Uttar Pradesh", "pincode": "226001"})),
            (CUSTOMER_IDS[3], "Sneha Reddy", "sneha.reddy@email.com", "+91-9876543213", "EXT-CUST-004", json.dumps({"line1": "27, Jubilee Hills", "city": "Hyderabad", "state": "Telangana", "pincode": "500033"})),
            (CUSTOMER_IDS[4], "Vikram Joshi", "vikram.joshi@email.com", "+91-9876543214", "EXT-CUST-005", json.dumps({"line1": "55, FC Road", "city": "Pune", "state": "Maharashtra", "pincode": "411004"})),
            (CUSTOMER_IDS[5], "Ananya Gupta", "ananya.gupta@email.com", "+91-9876543215", "EXT-CUST-006", json.dumps({"line1": "12, MI Road", "city": "Jaipur", "state": "Rajasthan", "pincode": "302001"})),
            (CUSTOMER_IDS[6], "Arjun Kumar", "arjun.kumar@email.com", "+91-9876543216", "EXT-CUST-007", json.dumps({"line1": "33, Connaught Place", "city": "New Delhi", "state": "Delhi", "pincode": "110001"})),
            (CUSTOMER_IDS[7], "Deepika Verma", "deepika.verma@email.com", "+91-9876543217", "EXT-CUST-008", json.dumps({"line1": "19, Indiranagar", "city": "Bangalore", "state": "Karnataka", "pincode": "560038"})),
        ]
        cur.executemany(
            "INSERT INTO nx_customers (id, tenant_id, name, email, phone, external_id, address) VALUES (%s, %s, %s, %s, %s, %s, %s::jsonb)",
            [(row[0], TENANT_ID) + row[1:] for row in customers_data]
        )

        # ============================================================
        # 3. CARRIER ACCOUNTS (4)
        # ============================================================
        carriers_data = [
            (CARRIER_IDS[0], "DELHIVERY", "ACC-DLH-001", True),
            (CARRIER_IDS[1], "BLUE_DART", "ACC-BLD-001", True),
            (CARRIER_IDS[2], "FEDEX", "ACC-FDX-001", True),
            (CARRIER_IDS[3], "INDIA_POST", "ACC-IP-001", True),
        ]
        cur.executemany(
            "INSERT INTO nx_carrier_accounts (id, tenant_id, carrier_id, account_number, is_active) VALUES (%s, %s, %s, %s, %s)",
            [(row[0], TENANT_ID) + row[1:] for row in carriers_data]
        )

        # ============================================================
        # 4. ORDERS (30)
        # ============================================================
        # fmt: (id, external_id, channel, channel_order_id, customer_id, status, currency, subtotal, shipping_cost, tax_amount, total, payment_status, payment_reference, ship_to, billing_address, created_at)
        now = datetime.now()
        orders_data = [
            (ORDER_IDS[0],  "ORD-EX-001", "SHOPIFY",    "SHOP-1001", CUSTOMER_IDS[0], "PENDING",    "INR", 2499.00, 49.00,  199.92, 2747.92,  "UNPAID",   None,                                          json.dumps({"line1": "42, Marine Drive", "city": "Mumbai", "state": "Maharashtra", "pincode": "400001"}),                                   json.dumps({"line1": "42, Marine Drive", "city": "Mumbai", "state": "Maharashtra", "pincode": "400001"}),                                   now - timedelta(days=1)),
            (ORDER_IDS[1],  "ORD-EX-002", "AMAZON",     "AZ-2001",   CUSTOMER_IDS[1], "CONFIRMED",  "INR", 5999.00, 79.00,  479.92, 6557.92,  "PAID",     "PAY-TXN-001",                                json.dumps({"line1": "15, Ashram Road", "city": "Ahmedabad", "state": "Gujarat", "pincode": "380009"}),                                    json.dumps({"line1": "15, Ashram Road", "city": "Ahmedabad", "state": "Gujarat", "pincode": "380009"}),                                    now - timedelta(days=2)),
            (ORDER_IDS[2],  "ORD-EX-003", "WOOCOMMERCE", "WC-3001",   CUSTOMER_IDS[2], "ALLOCATED",  "INR", 4497.00, 99.00,  359.76, 4955.76,  "PAID",     "PAY-TXN-002",                                json.dumps({"line1": "8, Hazratganj", "city": "Lucknow", "state": "Uttar Pradesh", "pincode": "226001"}),                                  json.dumps({"line1": "8, Hazratganj", "city": "Lucknow", "state": "Uttar Pradesh", "pincode": "226001"}),                                  now - timedelta(days=3)),
            (ORDER_IDS[3],  "ORD-EX-004", "SHOPIFY",    "SHOP-1002", CUSTOMER_IDS[3], "SHIPPED",   "INR", 1299.00, 59.00,  103.92, 1461.92,  "PAID",     "PAY-TXN-003",                                json.dumps({"line1": "27, Jubilee Hills", "city": "Hyderabad", "state": "Telangana", "pincode": "500033"}),                                 json.dumps({"line1": "27, Jubilee Hills", "city": "Hyderabad", "state": "Telangana", "pincode": "500033"}),                                 now - timedelta(days=4)),
            (ORDER_IDS[4],  "ORD-EX-005", "AMAZON",     "AZ-2002",   CUSTOMER_IDS[4], "DELIVERED",  "INR", 1499.00, 69.00,  119.92, 1687.92,  "PAID",     "PAY-TXN-004",                                json.dumps({"line1": "55, FC Road", "city": "Pune", "state": "Maharashtra", "pincode": "411004"}),                                      json.dumps({"line1": "55, FC Road", "city": "Pune", "state": "Maharashtra", "pincode": "411004"}),                                      now - timedelta(days=6)),
            (ORDER_IDS[5],  "ORD-EX-006", "MANUAL",     "MAN-4001",   CUSTOMER_IDS[5], "CANCELLED",  "INR", 3498.00, 89.00,  279.84, 3866.84,  "REFUNDED", "PAY-TXN-005",                                json.dumps({"line1": "12, MI Road", "city": "Jaipur", "state": "Rajasthan", "pincode": "302001"}),                                      json.dumps({"line1": "12, MI Road", "city": "Jaipur", "state": "Rajasthan", "pincode": "302001"}),                                      now - timedelta(days=5)),
            (ORDER_IDS[6],  "ORD-EX-007", "AMAZON",     "AZ-2003",   CUSTOMER_IDS[6], "PENDING",    "INR", 799.00,  39.00,  63.92,  901.92,   "UNPAID",   None,                                          json.dumps({"line1": "33, Connaught Place", "city": "New Delhi", "state": "Delhi", "pincode": "110001"}),                                 json.dumps({"line1": "33, Connaught Place", "city": "New Delhi", "state": "Delhi", "pincode": "110001"}),                                 now - timedelta(hours=6)),
            (ORDER_IDS[7],  "ORD-EX-008", "SHOPIFY",    "SHOP-1003", CUSTOMER_IDS[7], "CONFIRMED",  "INR", 2198.00, 69.00,  175.84, 2442.84,  "PAID",     "PAY-TXN-006",                                json.dumps({"line1": "19, Indiranagar", "city": "Bangalore", "state": "Karnataka", "pincode": "560038"}),                               json.dumps({"line1": "19, Indiranagar", "city": "Bangalore", "state": "Karnataka", "pincode": "560038"}),                               now - timedelta(days=1)),
            (ORDER_IDS[8],  "ORD-EX-009", "WOOCOMMERCE", "WC-3002",   CUSTOMER_IDS[0], "ALLOCATED",  "INR", 2499.00, 59.00,  199.92, 2757.92,  "PAID",     "PAY-TXN-007",                                json.dumps({"line1": "42, Marine Drive", "city": "Mumbai", "state": "Maharashtra", "pincode": "400001"}),                                  json.dumps({"line1": "42, Marine Drive", "city": "Mumbai", "state": "Maharashtra", "pincode": "400001"}),                                  now - timedelta(days=2)),
            (ORDER_IDS[9],  "ORD-EX-010", "MANUAL",     "MAN-4002",   CUSTOMER_IDS[1], "SHIPPED",   "INR", 5597.00, 99.00,  447.76, 6143.76,  "PAID",     "PAY-TXN-008",                                json.dumps({"line1": "15, Ashram Road", "city": "Ahmedabad", "state": "Gujarat", "pincode": "380009"}),                                   json.dumps({"line1": "15, Ashram Road", "city": "Ahmedabad", "state": "Gujarat", "pincode": "380009"}),                                   now - timedelta(days=3)),
            (ORDER_IDS[10], "ORD-EX-011", "AMAZON",     "AZ-2004",   CUSTOMER_IDS[2], "DELIVERED",  "INR", 3798.00, 79.00,  303.84, 4180.84,  "PAID",     "PAY-TXN-009",                                json.dumps({"line1": "8, Hazratganj", "city": "Lucknow", "state": "Uttar Pradesh", "pincode": "226001"}),                                  json.dumps({"line1": "8, Hazratganj", "city": "Lucknow", "state": "Uttar Pradesh", "pincode": "226001"}),                                  now - timedelta(days=7)),
            (ORDER_IDS[11], "ORD-EX-012", "SHOPIFY",    "SHOP-1004", CUSTOMER_IDS[3], "RETURNED",  "INR", 3298.00, 69.00,  263.84, 3630.84,  "REFUNDED", "PAY-TXN-010",                                json.dumps({"line1": "27, Jubilee Hills", "city": "Hyderabad", "state": "Telangana", "pincode": "500033"}),                                json.dumps({"line1": "27, Jubilee Hills", "city": "Hyderabad", "state": "Telangana", "pincode": "500033"}),                                now - timedelta(days=8)),
            (ORDER_IDS[12], "ORD-EX-013", "WOOCOMMERCE", "WC-3003",   CUSTOMER_IDS[4], "PENDING",    "INR", 6747.00, 119.00, 539.76, 7405.76,  "UNPAID",   None,                                          json.dumps({"line1": "55, FC Road", "city": "Pune", "state": "Maharashtra", "pincode": "411004"}),                                      json.dumps({"line1": "55, FC Road", "city": "Pune", "state": "Maharashtra", "pincode": "411004"}),                                      now - timedelta(hours=12)),
            (ORDER_IDS[13], "ORD-EX-014", "AMAZON",     "AZ-2005",   CUSTOMER_IDS[5], "CONFIRMED",  "INR", 999.00,  49.00,  79.92,  1127.92,  "PAID",     "PAY-TXN-011",                                json.dumps({"line1": "12, MI Road", "city": "Jaipur", "state": "Rajasthan", "pincode": "302001"}),                                      json.dumps({"line1": "12, MI Road", "city": "Jaipur", "state": "Rajasthan", "pincode": "302001"}),                                      now - timedelta(days=1)),
            (ORDER_IDS[14], "ORD-EX-015", "SHOPIFY",    "SHOP-1005", CUSTOMER_IDS[6], "ALLOCATED",  "INR", 1798.00, 59.00,  143.84, 2000.84,  "PAID",     "PAY-TXN-012",                                json.dumps({"line1": "33, Connaught Place", "city": "New Delhi", "state": "Delhi", "pincode": "110001"}),                                json.dumps({"line1": "33, Connaught Place", "city": "New Delhi", "state": "Delhi", "pincode": "110001"}),                                now - timedelta(days=2)),
            (ORDER_IDS[15], "ORD-EX-016", "MANUAL",     "MAN-4003",   CUSTOMER_IDS[7], "SHIPPED",   "INR", 3499.00, 79.00,  279.92, 3857.92,  "PAID",     "PAY-TXN-013",                                json.dumps({"line1": "19, Indiranagar", "city": "Bangalore", "state": "Karnataka", "pincode": "560038"}),                               json.dumps({"line1": "19, Indiranagar", "city": "Bangalore", "state": "Karnataka", "pincode": "560038"}),                               now - timedelta(days=3)),
            (ORDER_IDS[16], "ORD-EX-017", "AMAZON",     "AZ-2006",   CUSTOMER_IDS[0], "DELIVERED",  "INR", 2598.00, 69.00,  207.84, 2874.84,  "PAID",     "PAY-TXN-014",                                json.dumps({"line1": "42, Marine Drive", "city": "Mumbai", "state": "Maharashtra", "pincode": "400001"}),                                  json.dumps({"line1": "42, Marine Drive", "city": "Mumbai", "state": "Maharashtra", "pincode": "400001"}),                                  now - timedelta(days=9)),
            (ORDER_IDS[17], "ORD-EX-018", "WOOCOMMERCE", "WC-3004",   CUSTOMER_IDS[1], "CANCELLED",  "INR", 799.00,  39.00,  63.92,  901.92,   "REFUNDED", "PAY-TXN-015",                                json.dumps({"line1": "15, Ashram Road", "city": "Ahmedabad", "state": "Gujarat", "pincode": "380009"}),                                   json.dumps({"line1": "15, Ashram Road", "city": "Ahmedabad", "state": "Gujarat", "pincode": "380009"}),                                   now - timedelta(days=4)),
            (ORDER_IDS[18], "ORD-EX-019", "SHOPIFY",    "SHOP-1006", CUSTOMER_IDS[2], "DELIVERED",  "INR", 6297.00, 99.00,  503.76, 6899.76,  "PAID",     "PAY-TXN-016",                                json.dumps({"line1": "8, Hazratganj", "city": "Lucknow", "state": "Uttar Pradesh", "pincode": "226001"}),                                  json.dumps({"line1": "8, Hazratganj", "city": "Lucknow", "state": "Uttar Pradesh", "pincode": "226001"}),                                  now - timedelta(days=10)),
            (ORDER_IDS[19], "ORD-EX-020", "AMAZON",     "AZ-2007",   CUSTOMER_IDS[3], "SHIPPED",   "INR", 2798.00, 79.00,  223.84, 3100.84,  "PAID",     "PAY-TXN-017",                                json.dumps({"line1": "27, Jubilee Hills", "city": "Hyderabad", "state": "Telangana", "pincode": "500033"}),                                json.dumps({"line1": "27, Jubilee Hills", "city": "Hyderabad", "state": "Telangana", "pincode": "500033"}),                                now - timedelta(days=2)),
            (ORDER_IDS[20], "ORD-EX-021", "MANUAL",     "MAN-4004",   CUSTOMER_IDS[4], "PENDING",    "INR", 1999.00, 59.00,  159.92, 2217.92,  "UNPAID",   None,                                          json.dumps({"line1": "55, FC Road", "city": "Pune", "state": "Maharashtra", "pincode": "411004"}),                                      json.dumps({"line1": "55, FC Road", "city": "Pune", "state": "Maharashtra", "pincode": "411004"}),                                      now - timedelta(hours=3)),
            (ORDER_IDS[21], "ORD-EX-022", "SHOPIFY",    "SHOP-1007", CUSTOMER_IDS[5], "CONFIRMED",  "INR", 3498.00, 79.00,  279.84, 3856.84,  "PAID",     "PAY-TXN-018",                                json.dumps({"line1": "12, MI Road", "city": "Jaipur", "state": "Rajasthan", "pincode": "302001"}),                                      json.dumps({"line1": "12, MI Road", "city": "Jaipur", "state": "Rajasthan", "pincode": "302001"}),                                      now - timedelta(days=1)),
            (ORDER_IDS[22], "ORD-EX-023", "AMAZON",     "AZ-2008",   CUSTOMER_IDS[6], "ALLOCATED",  "INR", 5497.00, 99.00,  439.76, 6035.76,  "PAID",     "PAY-TXN-019",                                json.dumps({"line1": "33, Connaught Place", "city": "New Delhi", "state": "Delhi", "pincode": "110001"}),                                json.dumps({"line1": "33, Connaught Place", "city": "New Delhi", "state": "Delhi", "pincode": "110001"}),                                now - timedelta(days=2)),
            (ORDER_IDS[23], "ORD-EX-024", "WOOCOMMERCE", "WC-3005",   CUSTOMER_IDS[7], "DELIVERED",  "INR", 1499.00, 59.00,  119.92, 1677.92,  "PAID",     "PAY-TXN-020",                                json.dumps({"line1": "19, Indiranagar", "city": "Bangalore", "state": "Karnataka", "pincode": "560038"}),                               json.dumps({"line1": "19, Indiranagar", "city": "Bangalore", "state": "Karnataka", "pincode": "560038"}),                               now - timedelta(days=11)),
            (ORDER_IDS[24], "ORD-EX-025", "AMAZON",     "AZ-2009",   CUSTOMER_IDS[0], "RETURNED",  "INR", 3598.00, 79.00,  287.84, 3964.84,  "REFUNDED", "PAY-TXN-021",                                json.dumps({"line1": "42, Marine Drive", "city": "Mumbai", "state": "Maharashtra", "pincode": "400001"}),                                  json.dumps({"line1": "42, Marine Drive", "city": "Mumbai", "state": "Maharashtra", "pincode": "400001"}),                                  now - timedelta(days=12)),
            (ORDER_IDS[25], "ORD-EX-026", "SHOPIFY",    "SHOP-1008", CUSTOMER_IDS[1], "SHIPPED",   "INR", 999.00,  49.00,  79.92,  1127.92,  "PAID",     "PAY-TXN-022",                                json.dumps({"line1": "15, Ashram Road", "city": "Ahmedabad", "state": "Gujarat", "pincode": "380009"}),                                   json.dumps({"line1": "15, Ashram Road", "city": "Ahmedabad", "state": "Gujarat", "pincode": "380009"}),                                   now - timedelta(days=1)),
            (ORDER_IDS[26], "ORD-EX-027", "MANUAL",     "MAN-4005",   CUSTOMER_IDS[2], "DELIVERED",  "INR", 2798.00, 69.00,  223.84, 3090.84,  "PAID",     "PAY-TXN-023",                                json.dumps({"line1": "8, Hazratganj", "city": "Lucknow", "state": "Uttar Pradesh", "pincode": "226001"}),                                  json.dumps({"line1": "8, Hazratganj", "city": "Lucknow", "state": "Uttar Pradesh", "pincode": "226001"}),                                  now - timedelta(days=13)),
            (ORDER_IDS[27], "ORD-EX-028", "AMAZON",     "AZ-2010",   CUSTOMER_IDS[3], "CANCELLED",  "INR", 5097.00, 99.00,  407.76, 5603.76,  "REFUNDED", "PAY-TXN-024",                                json.dumps({"line1": "27, Jubilee Hills", "city": "Hyderabad", "state": "Telangana", "pincode": "500033"}),                                json.dumps({"line1": "27, Jubilee Hills", "city": "Hyderabad", "state": "Telangana", "pincode": "500033"}),                                now - timedelta(days=4)),
            (ORDER_IDS[28], "ORD-EX-029", "WOOCOMMERCE", "WC-3006",   CUSTOMER_IDS[4], "DELIVERED",  "INR", 1499.00, 59.00,  119.92, 1677.92,  "PAID",     "PAY-TXN-025",                                json.dumps({"line1": "55, FC Road", "city": "Pune", "state": "Maharashtra", "pincode": "411004"}),                                      json.dumps({"line1": "55, FC Road", "city": "Pune", "state": "Maharashtra", "pincode": "411004"}),                                      now - timedelta(days=14)),
            (ORDER_IDS[29], "ORD-EX-030", "SHOPIFY",    "SHOP-1009", CUSTOMER_IDS[5], "PENDING",    "INR", 2198.00, 69.00,  175.84, 2442.84,  "UNPAID",   None,                                          json.dumps({"line1": "12, MI Road", "city": "Jaipur", "state": "Rajasthan", "pincode": "302001"}),                                      json.dumps({"line1": "12, MI Road", "city": "Jaipur", "state": "Rajasthan", "pincode": "302001"}),                                      now - timedelta(hours=1)),
        ]

        # Add allocated_node, shipped_at, delivered_at for relevant orders
        orders_extended = []
        for row in orders_data:
            oid_val = row[0]
            status = row[5]
            ship_to = row[12]
            allocated = None
            shipped_at = None
            delivered_at = None
            if status == "ALLOCATED":
                allocated = NODE_MUMBAI
            elif status == "SHIPPED":
                allocated = NODE_MUMBAI
                shipped_at = row[15] + timedelta(hours=12)
            elif status == "DELIVERED":
                allocated = NODE_MUMBAI
                shipped_at = row[15] + timedelta(days=1)
                delivered_at = row[15] + timedelta(days=3)
            orders_extended.append(row + (allocated, shipped_at, delivered_at))

        for row in orders_extended:
            cur.execute(
                "INSERT INTO nx_orders (id, tenant_id, external_id, channel, channel_order_id, customer_id, status, currency, subtotal, shipping_cost, tax_amount, total, payment_status, payment_reference, ship_to, billing_address, created_at, allocated_node, shipped_at, delivered_at) VALUES (%s,%s,%s,%s,%s,%s,%s,%s,%s,%s,%s,%s,%s,%s,%s::jsonb,%s::jsonb,%s,%s,%s,%s)",
                (row[0], TENANT_ID) + row[1:12] + (row[12], row[13], row[14], row[15]) + (row[16], row[17], row[18])
            )

        # ============================================================
        # 5. ORDER ITEMS (~58 items across 30 orders)
        # ============================================================
        order_items_data = [
            # Order 01 (PENDING) - 2 items
            (oid(1),  ORDER_IDS[0],  "ELE-SMART-WATCH-001", "Smart Watch Pro",      1, 1499.00, 1499.00),
            (oid(2),  ORDER_IDS[0],  "FAS-COTTON-TSH-001",  "Cotton T-Shirt",       2, 500.00,  1000.00),
            # Order 02 (CONFIRMED) - 1 item
            (oid(3),  ORDER_IDS[1],  "ELE-WIRELESS-EAR-001", "Wireless Earbuds",     1, 5999.00, 5999.00),
            # Order 03 (ALLOCATED) - 3 items
            (oid(4),  ORDER_IDS[2],  "HOM-CERAMIC-MUG-001", "Ceramic Mug Set",      1, 999.00,  999.00),
            (oid(5),  ORDER_IDS[2],  "BOK-FICTION-001",     "Fiction Novel",        2, 499.00,  998.00),
            (oid(6),  ORDER_IDS[2],  "SPO-YOGA-MAT-001",    "Yoga Mat Premium",     1, 2500.00, 2500.00),
            # Order 04 (SHIPPED) - 2 items
            (oid(7),  ORDER_IDS[3],  "FAS-LINEN-SHIRT-001", "Linen Shirt",          1, 799.00,  799.00),
            (oid(8),  ORDER_IDS[3],  "BOK-SCIENCE-001",     "Science Book",         1, 500.00,  500.00),
            # Order 05 (DELIVERED) - 1 item
            (oid(9),  ORDER_IDS[4],  "ELE-POWER-BANK-001",  "Power Bank 20K",       1, 1499.00, 1499.00),
            # Order 06 (CANCELLED) - 2 items
            (oid(10), ORDER_IDS[5],  "HOM-STEEL-COOK-001",  "Steel Cookware Set",   1, 2499.00, 2499.00),
            (oid(11), ORDER_IDS[5],  "BOK-SELF-HELP-001",   "Self Help Book",       2, 499.00,  998.00),
            # Order 07 (PENDING) - 1 item
            (oid(12), ORDER_IDS[6],  "FAS-JEANS-BLUE-001",  "Blue Jeans",           1, 799.00,  799.00),
            # Order 08 (CONFIRMED) - 2 items
            (oid(13), ORDER_IDS[7],  "HOM-BED-SHEET-001",   "Bed Sheet King Size",  1, 1299.00, 1299.00),
            (oid(14), ORDER_IDS[7],  "SPO-SKIPPING-R-001",  "Skipping Rope",        1, 899.00,  899.00),
            # Order 09 (ALLOCATED) - 1 item
            (oid(15), ORDER_IDS[8],  "ELE-BLUETOOTH-SP-001","Bluetooth Speaker",    1, 2499.00, 2499.00),
            # Order 10 (SHIPPED) - 3 items
            (oid(16), ORDER_IDS[9],  "FAS-SAREE-SILK-001",  "Silk Saree",           1, 2999.00, 2999.00),
            (oid(17), ORDER_IDS[9],  "HOM-TOWEL-SET-001",   "Luxury Towel Set",     1, 1599.00, 1599.00),
            (oid(18), ORDER_IDS[9],  "BOK-COOKING-001",     "Cookbook Indian",      1, 999.00,  999.00),
            # Order 11 (DELIVERED) - 2 items
            (oid(19), ORDER_IDS[10], "ELE-SMART-WATCH-001", "Smart Watch Pro",      1, 1499.00, 1499.00),
            (oid(20), ORDER_IDS[10], "HOM-DECOR-LAMP-001",  "Decor Lamp",           1, 2299.00, 2299.00),
            # Order 12 (RETURNED) - 2 items
            (oid(21), ORDER_IDS[11], "FAS-COTTON-TSH-001",  "Cotton T-Shirt",       2, 500.00,  1000.00),
            (oid(22), ORDER_IDS[11], "SPO-WATER-BOT-001",   "Water Bottle Steel",   1, 2298.00, 2298.00),
            # Order 13 (PENDING) - 3 items
            (oid(23), ORDER_IDS[12], "ELE-WIRELESS-EAR-001","Wireless Earbuds",     1, 5999.00, 5999.00),
            (oid(24), ORDER_IDS[12], "FAS-LINEN-SHIRT-001", "Linen Shirt",          1, 799.00,  799.00),
            (oid(25), ORDER_IDS[12], "BOK-FICTION-001",     "Fiction Novel",        1, 499.00,  499.00),
            # Order 14 (CONFIRMED) - 1 item
            (oid(26), ORDER_IDS[13], "SPO-DUMBBELL-001",    "Dumbbell Set 5kg",     1, 999.00,  999.00),
            # Order 15 (ALLOCATED) - 2 items
            (oid(27), ORDER_IDS[14], "ELE-POWER-BANK-001",  "Power Bank 20K",       1, 1499.00, 1499.00),
            (oid(28), ORDER_IDS[14], "FAS-JEANS-BLUE-001",  "Blue Jeans",           1, 799.00,  799.00),
            # Order 16 (SHIPPED) - 1 item
            (oid(29), ORDER_IDS[15], "HOM-BED-SHEET-001",   "Bed Sheet King Size",  1, 1299.00, 1299.00),
            # Order 17 (DELIVERED) - 2 items
            (oid(30), ORDER_IDS[16], "BOK-SCIENCE-001",     "Science Book",         2, 500.00,  1000.00),
            (oid(31), ORDER_IDS[16], "SPO-SKIPPING-R-001",  "Skipping Rope",        1, 899.00,  899.00),
            # Order 18 (CANCELLED) - 1 item
            (oid(32), ORDER_IDS[17], "FAS-SAREE-SILK-001",  "Silk Saree",           1, 799.00,  799.00),
            # Order 19 (DELIVERED) - 3 items
            (oid(33), ORDER_IDS[18], "HOM-TOWEL-SET-001",   "Luxury Towel Set",     1, 1599.00, 1599.00),
            (oid(34), ORDER_IDS[18], "BOK-COOKING-001",     "Cookbook Indian",      1, 999.00,  999.00),
            (oid(35), ORDER_IDS[18], "ELE-BLUETOOTH-SP-001","Bluetooth Speaker",    1, 3499.00, 3499.00),
            # Order 20 (SHIPPED) - 2 items
            (oid(36), ORDER_IDS[19], "HOM-DECOR-LAMP-001",  "Decor Lamp",           1, 2299.00, 2299.00),
            (oid(37), ORDER_IDS[19], "FAS-COTTON-TSH-001",  "Cotton T-Shirt",       1, 500.00,  500.00),
            # Order 21 (PENDING) - 1 item
            (oid(38), ORDER_IDS[20], "ELE-SMART-WATCH-001", "Smart Watch Pro",      1, 1499.00, 1499.00),
            # Order 22 (CONFIRMED) - 2 items
            (oid(39), ORDER_IDS[21], "SPO-YOGA-MAT-001",    "Yoga Mat Premium",     1, 2500.00, 2500.00),
            (oid(40), ORDER_IDS[21], "HOM-CERAMIC-MUG-001", "Ceramic Mug Set",      1, 999.00,  999.00),
            # Order 23 (ALLOCATED) - 3 items
            (oid(41), ORDER_IDS[22], "ELE-WIRELESS-EAR-001","Wireless Earbuds",     1, 2999.00, 2999.00),
            (oid(42), ORDER_IDS[22], "FAS-LINEN-SHIRT-001", "Linen Shirt",          1, 799.00,  799.00),
            (oid(43), ORDER_IDS[22], "BOK-SELF-HELP-001",   "Self Help Book",       2, 499.00,  998.00),
            # Order 24 (DELIVERED) - 1 item
            (oid(44), ORDER_IDS[23], "SPO-WATER-BOT-001",   "Water Bottle Steel",   1, 1499.00, 1499.00),
            # Order 25 (RETURNED) - 2 items
            (oid(45), ORDER_IDS[24], "HOM-STEEL-COOK-001",  "Steel Cookware Set",   1, 2499.00, 2499.00),
            (oid(46), ORDER_IDS[24], "FAS-JEANS-BLUE-001",  "Blue Jeans",           1, 799.00,  799.00),
            # Order 26 (SHIPPED) - 1 item
            (oid(47), ORDER_IDS[25], "ELE-BLUETOOTH-SP-001","Bluetooth Speaker",    1, 999.00,  999.00),
            # Order 27 (DELIVERED) - 2 items
            (oid(48), ORDER_IDS[26], "FAS-KURTA-COTTON-001","Cotton Kurta",         1, 1299.00, 1299.00),
            (oid(49), ORDER_IDS[26], "BOK-CHILDREN-001",    "Children Book Set",    1, 1499.00, 1499.00),
            # Order 28 (CANCELLED) - 3 items
            (oid(50), ORDER_IDS[27], "ELE-POWER-BANK-001",  "Power Bank 20K",       1, 1499.00, 1499.00),
            (oid(51), ORDER_IDS[27], "HOM-BED-SHEET-001",   "Bed Sheet King Size",  1, 1299.00, 1299.00),
            (oid(52), ORDER_IDS[27], "SPO-RESIST-BND-001",  "Resistance Bands Set", 1, 2299.00, 2299.00),
            # Order 29 (DELIVERED) - 1 item
            (oid(53), ORDER_IDS[28], "FAS-KURTA-COTTON-001","Cotton Kurta",         1, 1499.00, 1499.00),
            # Order 30 (PENDING) - 2 items
            (oid(54), ORDER_IDS[29], "HOM-DECOR-LAMP-001",  "Decor Lamp",           1, 1299.00, 1299.00),
            (oid(55), ORDER_IDS[29], "BOK-CHILDREN-001",    "Children Book Set",    1, 899.00,  899.00),
        ]

        for row in order_items_data:
            cur.execute(
                "INSERT INTO nx_order_items (id, order_id, sku, product_name, quantity, unit_price, total_price) VALUES (%s,%s,%s,%s,%s,%s,%s)",
                row
            )

        # ============================================================
        # 6. SHIPMENTS (10)
        # ============================================================
        shipments_data = [
            # Shipment 01 - Order 04 (SHIPPED)
            ("d1000000-0000-0000-0000-000000000001", ORDER_IDS[3],  "DELHIVERY",  "STANDARD",   "DLH-TRK-1001", NODE_MUMBAI, "SHIPPED",    now - timedelta(days=3, hours=4),  now - timedelta(days=3)),
            # Shipment 02 - Order 05 (DELIVERED)
            ("d1000000-0000-0000-0000-000000000002", ORDER_IDS[4],  "BLUE_DART",  "EXPRESS",    "BLD-TRK-2001", NODE_MUMBAI, "DELIVERED",  now - timedelta(days=5, hours=6),  now - timedelta(days=5)),
            # Shipment 03 - Order 10 (SHIPPED)
            ("d1000000-0000-0000-0000-000000000003", ORDER_IDS[9],  "DELHIVERY",  "STANDARD",   "DLH-TRK-1002", NODE_DELHI,  "SHIPPED",    now - timedelta(days=2, hours=8),  now - timedelta(days=2)),
            # Shipment 04 - Order 11 (DELIVERED)
            ("d1000000-0000-0000-0000-000000000004", ORDER_IDS[10], "FEDEX",      "PRIORITY",   "FDX-TRK-3001", NODE_DELHI,  "DELIVERED",  now - timedelta(days=6, hours=2),  now - timedelta(days=6)),
            # Shipment 05 - Order 16 (SHIPPED)
            ("d1000000-0000-0000-0000-000000000005", ORDER_IDS[15], "BLUE_DART",  "STANDARD",   "BLD-TRK-2002", NODE_BLR,    "SHIPPED",    now - timedelta(days=2),           now - timedelta(days=2)),
            # Shipment 06 - Order 17 (DELIVERED)
            ("d1000000-0000-0000-0000-000000000006", ORDER_IDS[16], "DELHIVERY",  "EXPRESS",    "DLH-TRK-1003", NODE_MUMBAI, "DELIVERED",  now - timedelta(days=8, hours=1), now - timedelta(days=8)),
            # Shipment 07 - Order 19 (DELIVERED)
            ("d1000000-0000-0000-0000-000000000007", ORDER_IDS[18], "INDIA_POST", "STANDARD",   "IP-TRK-4001",  NODE_BLR,    "DELIVERED",  now - timedelta(days=9, hours=3), now - timedelta(days=9)),
            # Shipment 08 - Order 20 (SHIPPED)
            ("d1000000-0000-0000-0000-000000000008", ORDER_IDS[19], "FEDEX",      "EXPRESS",    "FDX-TRK-3002", NODE_CHENNAI,"SHIPPED",    now - timedelta(days=1, hours=5),  now - timedelta(days=1)),
            # Shipment 09 - Order 24 (DELIVERED)
            ("d1000000-0000-0000-0000-000000000009", ORDER_IDS[23], "DELHIVERY",  "STANDARD",   "DLH-TRK-1004", NODE_BLR,    "DELIVERED",  now - timedelta(days=10, hours=2),now - timedelta(days=10)),
            # Shipment 10 - Order 26 (SHIPPED)
            ("d1000000-0000-0000-0000-000000000010", ORDER_IDS[25], "BLUE_DART",  "EXPRESS",    "BLD-TRK-2003", NODE_MUMBAI, "SHIPPED",    now - timedelta(hours=12),        now - timedelta(hours=12)),
        ]

        for row in shipments_data:
            sid, order_id_val, carrier, service, tracking, origin, status, created, shipped = row
            estimated = shipped + timedelta(days=3)
            actual = shipped + timedelta(days=2, hours=12) if status == "DELIVERED" else None
            cur.execute(
                "INSERT INTO nx_shipments (id, order_id, tenant_id, carrier_id, service_level, tracking_number, origin_node_id, status, created_at, shipped_at, estimated_delivery, actual_delivery) VALUES (%s,%s,%s,%s,%s,%s,%s,%s,%s,%s,%s,%s)",
                (sid, order_id_val, TENANT_ID, carrier, service, tracking, origin, status, created, shipped, estimated, actual)
            )

        # ============================================================
        # 7. TRACKING EVENTS (36)
        # ============================================================
        # Shipment 01 (Order 04 - SHIPPED via DELHIVERY) - 4 events
        tracking_events_data = [
            (teid(1),  "d1000000-0000-0000-0000-000000000001", "PICKED_UP",       "Mumbai, Maharashtra",       now - timedelta(days=3, hours=4), "Package picked up from warehouse"),
            (teid(2),  "d1000000-0000-0000-0000-000000000001", "ARRIVED_AT_HUB",  "Mumbai HUB, Maharashtra",   now - timedelta(days=3, hours=2), "Arrived at Mumbai sorting hub"),
            (teid(3),  "d1000000-0000-0000-0000-000000000001", "DEPARTED_HUB",    "Mumbai HUB, Maharashtra",   now - timedelta(days=3, hours=0), "Departed from Mumbai hub"),
            (teid(4),  "d1000000-0000-0000-0000-000000000001", "IN_TRANSIT",      "Hyderabad, Telangana",      now - timedelta(days=2, hours=6), "Package in transit to destination"),
            # Shipment 02 (Order 05 - DELIVERED via BLUE_DART) - 4 events
            (teid(5),  "d1000000-0000-0000-0000-000000000002", "PICKED_UP",       "Mumbai, Maharashtra",       now - timedelta(days=5, hours=6), "Package picked up from warehouse"),
            (teid(6),  "d1000000-0000-0000-0000-000000000002", "ARRIVED_AT_HUB",  "Pune HUB, Maharashtra",     now - timedelta(days=5, hours=2), "Arrived at Pune hub"),
            (teid(7),  "d1000000-0000-0000-0000-000000000002", "OUT_FOR_DELIVERY","Pune, Maharashtra",         now - timedelta(days=4, hours=8), "Package out for delivery"),
            (teid(8),  "d1000000-0000-0000-0000-000000000002", "DELIVERED",       "Pune, Maharashtra",         now - timedelta(days=4, hours=6), "Package delivered successfully"),
            # Shipment 03 (Order 10 - SHIPPED via DELHIVERY) - 4 events
            (teid(9),  "d1000000-0000-0000-0000-000000000003", "PICKED_UP",       "Delhi, Delhi",              now - timedelta(days=2, hours=8), "Package picked up from warehouse"),
            (teid(10), "d1000000-0000-0000-0000-000000000003", "ARRIVED_AT_HUB",  "Delhi HUB, Delhi",          now - timedelta(days=2, hours=4), "Arrived at Delhi sorting hub"),
            (teid(11), "d1000000-0000-0000-0000-000000000003", "DEPARTED_HUB",    "Delhi HUB, Delhi",          now - timedelta(days=2, hours=2), "Departed from Delhi hub"),
            (teid(12), "d1000000-0000-0000-0000-000000000003", "IN_TRANSIT",      "Ahmedabad, Gujarat",        now - timedelta(days=1, hours=8), "Package in transit to Ahmedabad"),
            # Shipment 04 (Order 11 - DELIVERED via FEDEX) - 4 events
            (teid(13), "d1000000-0000-0000-0000-000000000004", "PICKED_UP",       "Delhi, Delhi",              now - timedelta(days=6, hours=2), "Package picked up from warehouse"),
            (teid(14), "d1000000-0000-0000-0000-000000000004", "ARRIVED_AT_HUB",  "Lucknow HUB, UP",           now - timedelta(days=5, hours=18), "Arrived at Lucknow hub"),
            (teid(15), "d1000000-0000-0000-0000-000000000004", "OUT_FOR_DELIVERY","Lucknow, Uttar Pradesh",    now - timedelta(days=5, hours=10), "Package out for delivery"),
            (teid(16), "d1000000-0000-0000-0000-000000000004", "DELIVERED",       "Lucknow, Uttar Pradesh",    now - timedelta(days=5, hours=8), "Package delivered successfully"),
            # Shipment 05 (Order 16 - SHIPPED via BLUE_DART) - 3 events
            (teid(17), "d1000000-0000-0000-0000-000000000005", "PICKED_UP",       "Bangalore, Karnataka",      now - timedelta(days=2),         "Package picked up from warehouse"),
            (teid(18), "d1000000-0000-0000-0000-000000000005", "ARRIVED_AT_HUB",  "Bangalore HUB, Karnataka",  now - timedelta(days=1, hours=18), "Arrived at Bangalore hub"),
            (teid(19), "d1000000-0000-0000-0000-000000000005", "IN_TRANSIT",      "Bangalore, Karnataka",      now - timedelta(days=1, hours=6), "Package in transit to destination"),
            # Shipment 06 (Order 17 - DELIVERED via DELHIVERY) - 4 events
            (teid(20), "d1000000-0000-0000-0000-000000000006", "PICKED_UP",       "Mumbai, Maharashtra",       now - timedelta(days=8, hours=1), "Package picked up from warehouse"),
            (teid(21), "d1000000-0000-0000-0000-000000000006", "ARRIVED_AT_HUB",  "Mumbai HUB, Maharashtra",   now - timedelta(days=7, hours=18), "Arrived at Mumbai hub"),
            (teid(22), "d1000000-0000-0000-0000-000000000006", "OUT_FOR_DELIVERY","Mumbai, Maharashtra",       now - timedelta(days=7, hours=8), "Package out for delivery"),
            (teid(23), "d1000000-0000-0000-0000-000000000006", "DELIVERED",       "Mumbai, Maharashtra",       now - timedelta(days=7, hours=5), "Package delivered successfully"),
            # Shipment 07 (Order 19 - DELIVERED via INDIA_POST) - 3 events
            (teid(24), "d1000000-0000-0000-0000-000000000007", "PICKED_UP",       "Bangalore, Karnataka",      now - timedelta(days=9, hours=3), "Package picked up from warehouse"),
            (teid(25), "d1000000-0000-0000-0000-000000000007", "ARRIVED_AT_HUB",  "Lucknow HUB, UP",           now - timedelta(days=8, hours=12), "Arrived at Lucknow hub"),
            (teid(26), "d1000000-0000-0000-0000-000000000007", "DELIVERED",       "Lucknow, Uttar Pradesh",    now - timedelta(days=8, hours=4), "Package delivered successfully"),
            # Shipment 08 (Order 20 - SHIPPED via FEDEX) - 4 events
            (teid(27), "d1000000-0000-0000-0000-000000000008", "PICKED_UP",       "Chennai, Tamil Nadu",       now - timedelta(days=1, hours=5), "Package picked up from warehouse"),
            (teid(28), "d1000000-0000-0000-0000-000000000008", "ARRIVED_AT_HUB",  "Chennai HUB, Tamil Nadu",   now - timedelta(days=1, hours=1), "Arrived at Chennai hub"),
            (teid(29), "d1000000-0000-0000-0000-000000000008", "DEPARTED_HUB",    "Chennai HUB, Tamil Nadu",   now - timedelta(hours=23),       "Departed from Chennai hub"),
            (teid(30), "d1000000-0000-0000-0000-000000000008", "IN_TRANSIT",      "Hyderabad, Telangana",      now - timedelta(hours=12),       "Package in transit to Hyderabad"),
            # Shipment 09 (Order 24 - DELIVERED via DELHIVERY) - 3 events
            (teid(31), "d1000000-0000-0000-0000-000000000009", "PICKED_UP",       "Bangalore, Karnataka",      now - timedelta(days=10, hours=2), "Package picked up from warehouse"),
            (teid(32), "d1000000-0000-0000-0000-000000000009", "OUT_FOR_DELIVERY","Bangalore, Karnataka",      now - timedelta(days=9, hours=12), "Package out for delivery"),
            (teid(33), "d1000000-0000-0000-0000-000000000009", "DELIVERED",       "Bangalore, Karnataka",      now - timedelta(days=9, hours=8), "Package delivered successfully"),
            # Shipment 10 (Order 26 - SHIPPED via BLUE_DART) - 3 events
            (teid(34), "d1000000-0000-0000-0000-000000000010", "PICKED_UP",       "Mumbai, Maharashtra",       now - timedelta(hours=12),       "Package picked up from warehouse"),
            (teid(35), "d1000000-0000-0000-0000-000000000010", "ARRIVED_AT_HUB",  "Mumbai HUB, Maharashtra",   now - timedelta(hours=8),        "Arrived at Mumbai hub"),
            (teid(36), "d1000000-0000-0000-0000-000000000010", "DEPARTED_HUB",    "Mumbai HUB, Maharashtra",   now - timedelta(hours=4),        "Departed from Mumbai hub"),
        ]

        for row in tracking_events_data:
            cur.execute(
                "INSERT INTO nx_tracking_events (id, shipment_id, event_type, location, timestamp, description) VALUES (%s,%s,%s,%s,%s,%s)",
                row
            )

        # ============================================================
        # 8. RETURNS (5)
        # ============================================================
        returns_data = [
            (RETURN_IDS[0], ORDER_IDS[11], CUSTOMER_IDS[3], "Product damaged during shipping",  "RETURNED",  "DAMAGED",   "DELHIVERY", "DLH-RTN-5001", 3298.00, "REF-RTN-001", now - timedelta(days=5)),
            (RETURN_IDS[1], ORDER_IDS[24], CUSTOMER_IDS[0], "Wrong item received",               "PENDING",   "WRONG_ITEM","BLUE_DART", "BLD-RTN-5002", 3598.00, "REF-RTN-002", now - timedelta(days=2)),
            (RETURN_IDS[2], ORDER_IDS[4],  CUSTOMER_IDS[4], "Defective product on arrival",       "APPROVED",  "DEFECTIVE", "FEDEX",     "FDX-RTN-5003", 1499.00, "REF-RTN-003", now - timedelta(days=7)),
            (RETURN_IDS[3], ORDER_IDS[10], CUSTOMER_IDS[2], "Size mismatch - ordered smaller",   "INSPECTING","SIZE_MISMATCH","DELHIVERY","DLH-RTN-5004", 1499.00, "REF-RTN-004", now - timedelta(days=4)),
            (RETURN_IDS[4], ORDER_IDS[26], CUSTOMER_IDS[2], "Changed mind - no longer needed",   "PENDING",   "CUSTOMER_INITIATED", "INDIA_POST", "IP-RTN-5005", 2798.00, "REF-RTN-005", now - timedelta(days=1)),
        ]

        for row in returns_data:
            rid, order_id_val, cid, reason, status, disposition, carrier, tracking, amount, ref, created = row
            cur.execute(
                "INSERT INTO nx_returns (id, tenant_id, order_id, customer_id, reason, status, disposition, carrier_id, tracking_number, refund_amount, refund_reference, created_at) VALUES (%s,%s,%s,%s,%s,%s,%s,%s,%s,%s,%s,%s)",
                (rid, TENANT_ID, order_id_val, cid, reason, status, disposition, carrier, tracking, amount, ref, created)
            )

        # ============================================================
        # 9. INVENTORY (25)
        # ============================================================
        # 5 SKUs per node, 5 nodes
        inventory_data = [
            # WH_MUMBAI
            ("aa000000-0000-0000-0000-000000000001", NODE_MUMBAI, "ELE-SMART-WATCH-001",  150, 15, 5,  10, 20, 0,  25, 50,  100, "LOT-MUM-001", "2026-12-31"),
            ("aa000000-0000-0000-0000-000000000002", NODE_MUMBAI, "FAS-COTTON-TSH-001",   500, 40, 20, 30, 50, 0, 100, 200, 300, "LOT-MUM-002", None),
            ("aa000000-0000-0000-0000-000000000003", NODE_MUMBAI, "HOM-CERAMIC-MUG-001",  200, 10, 5,  0,  0,  2,  30, 50,  100, "LOT-MUM-003", None),
            ("aa000000-0000-0000-0000-000000000004", NODE_MUMBAI, "BOK-FICTION-001",      800, 60, 20, 40, 30, 5, 150, 200, 400, "LOT-MUM-004", None),
            ("aa000000-0000-0000-0000-000000000005", NODE_MUMBAI, "SPO-YOGA-MAT-001",     120, 8,  3,  5,  10, 0,  20, 30,  60,  "LOT-MUM-005", None),
            # WH_DELHI
            ("aa000000-0000-0000-0000-000000000006", NODE_DELHI, "ELE-WIRELESS-EAR-001",   80, 20, 10, 5,  15, 1,  15, 25,  50,  "LOT-DLH-001", "2027-03-15"),
            ("aa000000-0000-0000-0000-000000000007", NODE_DELHI, "FAS-LINEN-SHIRT-001",   300, 25, 10, 20, 30, 0,  50, 100, 200, "LOT-DLH-002", None),
            ("aa000000-0000-0000-0000-000000000008", NODE_DELHI, "HOM-STEEL-COOK-001",     60, 5,  2,  0,  0,  1,  10, 15,  30,  "LOT-DLH-003", None),
            ("aa000000-0000-0000-0000-000000000009", NODE_DELHI, "BOK-SCIENCE-001",       450, 35, 15, 25, 20, 3, 100, 150, 250, "LOT-DLH-004", None),
            ("aa000000-0000-0000-0000-000000000010", NODE_DELHI, "SPO-DUMBBELL-001",       90, 10, 5,  0,  8,  0,  15, 20,  40,  "LOT-DLH-005", None),
            # WH_BLR
            ("aa000000-0000-0000-0000-000000000011", NODE_BLR, "ELE-LAPTOP-BAG-001",      200, 18, 8,  10, 12, 2,  30, 50,  100, "LOT-BLR-001", None),
            ("aa000000-0000-0000-0000-000000000012", NODE_BLR, "FAS-JEANS-BLUE-001",      250, 30, 12, 20, 25, 0,  40, 80,  150, "LOT-BLR-002", None),
            ("aa000000-0000-0000-0000-000000000013", NODE_BLR, "HOM-BED-SHEET-001",       180, 15, 8,  5,  10, 2,  25, 40,  80,  "LOT-BLR-003", None),
            ("aa000000-0000-0000-0000-000000000014", NODE_BLR, "BOK-SELF-HELP-001",       600, 50, 20, 30, 25, 4, 120, 180, 300, "LOT-BLR-004", None),
            ("aa000000-0000-0000-0000-000000000015", NODE_BLR, "SPO-SKIPPING-R-001",      100, 12, 6,  0,  5,  0,  20, 30,  50,  "LOT-BLR-005", None),
            # WH_CHENNAI
            ("aa000000-0000-0000-0000-000000000016", NODE_CHENNAI, "ELE-POWER-BANK-001",  130, 25, 10, 8,  20, 1,  20, 35,  70,  "LOT-CHE-001", "2027-06-30"),
            ("aa000000-0000-0000-0000-000000000017", NODE_CHENNAI, "FAS-SAREE-SILK-001",   70, 8,  3,  0,  5,  0,  10, 15,  30,  "LOT-CHE-002", None),
            ("aa000000-0000-0000-0000-000000000018", NODE_CHENNAI, "HOM-TOWEL-SET-001",   160, 12, 8,  5,  8,  1,  25, 40,  80,  "LOT-CHE-003", None),
            ("aa000000-0000-0000-0000-000000000019", NODE_CHENNAI, "BOK-COOKING-001",     350, 28, 12, 15, 20, 2,  80, 120, 200, "LOT-CHE-004", None),
            ("aa000000-0000-0000-0000-000000000020", NODE_CHENNAI, "SPO-WATER-BOT-001",   220, 18, 8,  10, 12, 0,  30, 50,  100, "LOT-CHE-005", None),
            # WH_KOLKATA
            ("aa000000-0000-0000-0000-000000000021", NODE_KOLKATA, "ELE-BLUETOOTH-SP-001", 85, 15, 8,  5,  10, 1,  15, 25,  50,  "LOT-KOL-001", "2027-09-30"),
            ("aa000000-0000-0000-0000-000000000022", NODE_KOLKATA, "FAS-KURTA-COTTON-001", 180, 22, 10, 10, 15, 0,  30, 50,  100, "LOT-KOL-002", None),
            ("aa000000-0000-0000-0000-000000000023", NODE_KOLKATA, "HOM-DECOR-LAMP-001",   95, 10, 5,  0,  0,  0,  15, 25,  50,  "LOT-KOL-003", None),
            ("aa000000-0000-0000-0000-000000000024", NODE_KOLKATA, "BOK-CHILDREN-001",    400, 40, 15, 20, 25, 3,  80, 120, 250, "LOT-KOL-004", None),
            ("aa000000-0000-0000-0000-000000000025", NODE_KOLKATA, "SPO-RESIST-BND-001",  110, 14, 6,  0,  5,  0,  20, 30,  50,  "LOT-KOL-005", None),
        ]

        for row in inventory_data:
            iid, nid, sku, qoh, qalloc, qres, qit, qoo, qdam, ss, rp, rq, lot, exp = row
            exp_val = exp if exp else None
            cur.execute(
                "INSERT INTO nx_inventory (id, tenant_id, sku, node_id, quantity_on_hand, quantity_allocated, quantity_reserved, quantity_in_transit, quantity_on_order, quantity_damaged, safety_stock, reorder_point, reorder_qty, lot_number, expiry_date) VALUES (%s,%s,%s,%s,%s,%s,%s,%s,%s,%s,%s,%s,%s,%s,%s::date)",
                (iid, TENANT_ID, sku, nid, qoh, qalloc, qres, qit, qoo, qdam, ss, rp, rq, lot, exp_val)
            )

        # ============================================================
        # 10. AUDIT LOGS (10)
        # ============================================================
        audit_logs_data = [
            (AUDIT_IDS[0],  "ORDER",        ORDER_IDS[0],  "ORDER_CREATED",    ADMIN_USER_ID, "USER", json.dumps({"channel": "SHOPIFY", "status": "PENDING"}),                                               now - timedelta(days=1)),
            (AUDIT_IDS[1],  "ORDER",        ORDER_IDS[1],  "ORDER_CONFIRMED",  ADMIN_USER_ID, "USER", json.dumps({"payment_status": "PAID", "payment_reference": "PAY-TXN-001"}),                          now - timedelta(days=2)),
            (AUDIT_IDS[2],  "ORDER",        ORDER_IDS[2],  "ORDER_ALLOCATED",  ADMIN_USER_ID, "USER", json.dumps({"allocated_node": NODE_MUMBAI, "allocation_rule": "NEAREST"}),                            now - timedelta(days=3)),
            (AUDIT_IDS[3],  "ORDER",        ORDER_IDS[3],  "ORDER_SHIPPED",    ADMIN_USER_ID, "USER", json.dumps({"carrier": "DELHIVERY", "tracking": "DLH-TRK-1001"}),                                   now - timedelta(days=3)),
            (AUDIT_IDS[4],  "ORDER",        ORDER_IDS[4],  "ORDER_DELIVERED",  ADMIN_USER_ID, "USER", json.dumps({"tracking": "BLD-TRK-2001", "delivered_at": (now - timedelta(days=4)).isoformat()}),     now - timedelta(days=4)),
            (AUDIT_IDS[5],  "ORDER",        ORDER_IDS[5],  "ORDER_CANCELLED",  ADMIN_USER_ID, "USER", json.dumps({"reason": "Customer requested cancellation", "refund_status": "PROCESSED"}),             now - timedelta(days=5)),
            (AUDIT_IDS[6],  "RETURN",       RETURN_IDS[0], "RETURN_CREATED",   ADMIN_USER_ID, "USER", json.dumps({"order_id": ORDER_IDS[11], "reason": "Product damaged during shipping"}),                now - timedelta(days=5)),
            (AUDIT_IDS[7],  "INVENTORY",    INVENTORY_IDS[0], "INVENTORY_ADJUSTED", ADMIN_USER_ID, "USER", json.dumps({"sku": "ELE-SMART-WATCH-001", "quantity_on_hand": 150, "previous": 145}), now - timedelta(days=6)),
            (AUDIT_IDS[8],  "NODE",         NODE_MUMBAI,   "NODE_CONFIG_UPDATED", ADMIN_USER_ID, "USER", json.dumps({"capacity_daily": 5000, "cut_off_time": "17:00:00"}),                               now - timedelta(days=7)),
            (AUDIT_IDS[9],  "CARRIER",      CARRIER_IDS[0],"CARRIER_ACTIVATED", ADMIN_USER_ID, "USER", json.dumps({"carrier_id": "DELHIVERY", "is_active": True}),                                       now - timedelta(days=8)),
        ]

        for row in audit_logs_data:
            cur.execute(
                "INSERT INTO nx_audit_log (id, tenant_id, entity_type, entity_id, event_type, actor_id, actor_type, data, created_at) VALUES (%s,%s,%s,%s,%s,%s,%s,%s::jsonb,%s)",
                (row[0], TENANT_ID) + row[1:5] + (row[5], row[6], row[7])
            )

        # ============================================================
        # COMMIT
        # ============================================================
        conn.commit()
        print("Seed data inserted successfully!")
        print(f"  Nodes:          {len(nodes_data)}")
        print(f"  Customers:      {len(customers_data)}")
        print(f"  Carrier Accts:  {len(carriers_data)}")
        print(f"  Orders:         {len(orders_data)}")
        print(f"  Order Items:    {len(order_items_data)}")
        print(f"  Shipments:      {len(shipments_data)}")
        print(f"  Tracking Events:{len(tracking_events_data)}")
        print(f"  Returns:        {len(returns_data)}")
        print(f"  Inventory:      {len(inventory_data)}")
        print(f"  Audit Logs:     {len(audit_logs_data)}")

    except Exception as e:
        conn.rollback()
        print(f"ERROR: {e}")
        raise
    finally:
        cur.close()
        conn.close()


if __name__ == "__main__":
    main()
