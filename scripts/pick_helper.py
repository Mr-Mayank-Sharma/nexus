#!/usr/bin/env python3
"""Picklist item seeding service for Nexus OMS.
Connects directly to PostgreSQL to create picklist items for a picklist + order.
"""
import json, os, uuid, time
from http.server import HTTPServer, BaseHTTPRequestHandler

import psycopg2

DB = {
    "host": os.environ.get("PG_HOST", "localhost"),
    "port": int(os.environ.get("PG_PORT", "5433")),
    "dbname": os.environ.get("PG_DB", "nexus_oms"),
    "user": os.environ.get("PG_USER", "nexus"),
    "password": os.environ.get("PG_PASSWORD", "nexus_secret"),
}

BACKEND = "http://localhost:8080/api/v1"

import urllib.request
TOKEN = None

def _login():
    global TOKEN
    req = urllib.request.Request(f"{BACKEND}/auth/login",
        data=json.dumps({"username":"admin","password":"Test1234!"}).encode(),
        headers={"Content-Type":"application/json"}, method="POST")
    try:
        with urllib.request.urlopen(req, timeout=10) as r:
            body = json.loads(r.read())
            TOKEN = body.get("accessToken") or body.get("data",{}).get("accessToken") or body.get("token")
    except: pass

_login()

def get_conn():
    return psycopg2.connect(**DB)

class PickHelperHandler(BaseHTTPRequestHandler):
    def _cors(self):
        self.send_header("Access-Control-Allow-Origin", "http://localhost:3000")
        self.send_header("Access-Control-Allow-Methods", "GET, POST, OPTIONS")
        self.send_header("Access-Control-Allow-Headers", "Content-Type")

    def _json(self, data, code=200):
        self.send_response(code)
        self.send_header("Content-Type", "application/json")
        self._cors()
        self.end_headers()
        self.wfile.write(json.dumps(data).encode())

    def do_OPTIONS(self):
        self.send_response(204)
        self._cors()
        self.end_headers()

    def _parse_query(self):
        from urllib.parse import urlparse, parse_qs
        parsed = urlparse(self.path)
        self.path = parsed.path
        return {k: v[0] for k, v in parse_qs(parsed.query).items()}

    def do_GET(self):
        params = self._parse_query()
        if self.path == "/health":
            return self._json({"status": "ok"})

        if self.path == "/api/v1/picking/user-staff":
            username = params.get("username")
            if not username:
                return self._json({"success": False, "error": "username required"}, 400)
            conn = get_conn()
            cur = conn.cursor()
            try:
                cur.execute("SELECT id FROM nx_users WHERE username = %s", (username,))
                user_row = cur.fetchone()
                if not user_row:
                    return self._json({"success": False, "error": "User not found"}, 404)
                user_id = user_row[0]
                cur.execute("SELECT id, first_name, last_name, role FROM nx_warehouse_staff WHERE user_id = %s", (user_id,))
                staff = cur.fetchone()
                if not staff:
                    return self._json({"success": False, "data": {"staffId": None, "message": "No staff record linked"}})
                return self._json({"success": True, "data": {
                    "staffId": str(staff[0]),
                    "firstName": staff[1],
                    "lastName": staff[2],
                    "role": staff[3],
                }})
            except Exception as e:
                return self._json({"success": False, "error": str(e)}, 500)
            finally:
                cur.close()
                conn.close()

        self._json({"error": "not found"}, 404)

    def do_POST(self):
        content_len = int(self.headers.get("Content-Length", 0))
        body = json.loads(self.rfile.read(content_len)) if content_len else {}
        picklist_id = body.get("picklistId") or body.get("picklist_id")
        staff_id = body.get("staffId") or body.get("staff_id")

        if self.path == "/api/v1/picking/assign":
            if not picklist_id or not staff_id:
                return self._json({"success": False, "error": "picklistId and staffId required"}, 400)
            # Call backend assign (sets IN_PROGRESS + started_at), then reset to OPEN
            try:
                req = urllib.request.Request(
                    f"{BACKEND}/picking/picklists/{picklist_id}/assign?staffId={staff_id}",
                    method="POST", headers={"Authorization": f"Bearer {TOKEN}"})
                with urllib.request.urlopen(req, timeout=15) as r:
                    resp = json.loads(r.read())
                if not resp.get("success", False):
                    return self._json({"success": False, "error": "Backend assign failed"}, 500)
            except Exception as e:
                return self._json({"success": False, "error": f"Backend assign error: {e}"}, 500)
            # Reset to OPEN and clear started_at so picker can start later
            conn = get_conn()
            cur = conn.cursor()
            try:
                cur.execute("UPDATE nx_picklists SET status = 'OPEN', started_at = NULL, updated_at = now() WHERE id = %s", (picklist_id,))
                conn.commit()
                return self._json({"success": True, "data": {"assigned": True, "picklistId": picklist_id, "staffId": staff_id}})
            except Exception as e:
                conn.rollback()
                return self._json({"success": False, "error": str(e)}, 500)
            finally:
                cur.close()
                conn.close()

        if self.path == "/api/v1/picking/start-picking":
            if not picklist_id:
                return self._json({"success": False, "error": "picklistId required"}, 400)
            conn = get_conn()
            cur = conn.cursor()
            try:
                cur.execute("UPDATE nx_picklists SET status = 'IN_PROGRESS', started_at = now(), updated_at = now() WHERE id = %s AND status = 'OPEN'", (picklist_id,))
                conn.commit()
                started = cur.rowcount > 0
                return self._json({"success": True, "data": {"started": started, "picklistId": picklist_id}})
            except Exception as e:
                conn.rollback()
                return self._json({"success": False, "error": str(e)}, 500)
            finally:
                cur.close()
                conn.close()

        if self.path != "/api/v1/picking/seed-items":
            return self._json({"error": "not found"}, 404)

        picklist_id = body.get("picklistId") or body.get("picklist_id")
        order_id = body.get("orderId") or body.get("order_id")

        if not picklist_id or not order_id:
            return self._json({"success": False, "error": "picklistId and orderId required"}, 400)

        # Fetch order items from the backend
        req = urllib.request.Request(f"{BACKEND}/orders/{order_id}",
            headers={"Authorization": f"Bearer {TOKEN}"})
        try:
            with urllib.request.urlopen(req, timeout=15) as r:
                order_data = json.loads(r.read())
        except Exception as e:
            return self._json({"success": False, "error": f"Cannot fetch order: {e}"}, 500)

        order = order_data.get("data") or order_data
        items_raw = order.get("items") or order.get("lineItems") or []
        if isinstance(items_raw, str):
            items_raw = json.loads(items_raw)

        if not items_raw:
            return self._json({"success": False, "error": "Order has no items"}, 400)

        # Build tenant_id from order
        tenant_id = order.get("tenantId")
        if not tenant_id:
            return self._json({"success": False, "error": "Order has no tenantId"}, 400)

        # Get order_item_ids from the order allocations in the DB
        conn = get_conn()
        cur = conn.cursor()
        try:
            # Fetch allocated quantities per order_item_id
            cur.execute("""
                SELECT oi.id, oi.sku, oi.product_name, oi.quantity
                FROM nx_order_items oi
                WHERE oi.order_id = %s
            """, (order_id,))
            item_rows = {r[0]: {"sku": r[1], "product_name": r[2], "quantity": r[3]} for r in cur.fetchall()}

            if not item_rows:
                # No order_items table — fallback: use items from JSON
                item_rows = {}
                for it in items_raw:
                    iid = it.get("id") or str(uuid.uuid4())
                    item_rows[iid] = {"sku": it.get("sku","?"), "product_name": it.get("productName","?"), "quantity": it.get("quantity",1)}

            # Fetch bins for location assignment (use warehouse with most bins)
            cur.execute("""
                SELECT id, code FROM nx_warehouse_bins
                WHERE warehouse_id = (
                    SELECT warehouse_id FROM nx_warehouse_bins
                    GROUP BY warehouse_id ORDER BY COUNT(*) DESC LIMIT 1
                ) AND is_active = true ORDER BY code
            """)
            all_bins = cur.fetchall()
            has_bins = len(all_bins) > 0

            created = 0
            for order_item_id, info in item_rows.items():
                picklist_item_id = str(uuid.uuid4())
                # Consistent SKU→bin assignment via hash
                bin_id, bin_code = None, None
                if has_bins:
                    idx = hash(info["sku"]) % len(all_bins)
                    bin_id, bin_code = all_bins[idx]
                cur.execute("""
                    INSERT INTO nx_picklist_items
                        (id, picklist_id, tenant_id, order_id, order_item_id,
                         sku, product_name, quantity, picked_quantity,
                         from_bin_id, from_location, status, created_at)
                    VALUES (%s,%s,%s,%s,%s, %s,%s,%s,0, %s,%s, 'PENDING', now())
                    ON CONFLICT DO NOTHING
                """, (
                    picklist_item_id, picklist_id, tenant_id, order_id, order_item_id,
                    info["sku"], info["product_name"], info["quantity"],
                    bin_id, bin_code,
                ))
                created += cur.rowcount

            # Update picklist totals
            cur.execute("""
                UPDATE nx_picklists
                SET total_items = (SELECT COALESCE(SUM(quantity),0) FROM nx_picklist_items WHERE picklist_id = %s),
                    updated_at = now()
                WHERE id = %s
            """, (picklist_id, picklist_id))

            conn.commit()
            return self._json({"success": True, "data": {"created": created, "picklistId": picklist_id, "orderId": order_id}})

        except Exception as e:
            conn.rollback()
            return self._json({"success": False, "error": str(e)}, 500)
        finally:
            cur.close()
            conn.close()

if __name__ == "__main__":
    server = HTTPServer(("0.0.0.0", 8082), PickHelperHandler)
    print("Pick helper on :8082")
    server.serve_forever()
