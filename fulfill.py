#!/usr/bin/env python3
"""Fulfill orders through complete workflow."""
import json, urllib.request, urllib.error, time, random, sys
from urllib.parse import urlencode

BASE = "http://localhost:8080/api/v1"
TOKEN = None

def api(m, p, d=None, params=None, raw=False):
    url = BASE + p
    if params:
        url += "?" + urlencode({k:v for k,v in params.items() if v is not None})
    req = urllib.request.Request(url, method=m)
    req.add_header("Content-Type", "application/json")
    if TOKEN: req.add_header("Authorization", f"Bearer {TOKEN}")
    if d is not None: req.data = json.dumps(d).encode()
    for _ in range(3):
        try:
            with urllib.request.urlopen(req, timeout=60) as r:
                b = json.loads(r.read())
                if raw: return b
                if b.get("success"): return b.get("data")
                return {"error": str(b.get("message",""))[:200]}
        except urllib.error.HTTPError as e:
            err = e.read().decode()
            try: msg = json.loads(err).get("message", err)
            except: msg = err
            if e.code in (409, 429): time.sleep(1); continue
            return {"error": f"HTTP {e.code}: {str(msg)[:200]}"}
        except Exception as e:
            if _ < 2: time.sleep(1); continue
            return {"error": str(e)[:200]}
    return {"error": "timeout"}

def fulfill_ai(order_id):
    """Fulfill an order using AI execute (confirm -> allocate -> ship)."""
    time.sleep(0.05)
    r = api("POST", f"/orders/{order_id}/ai-execute", {"actionType":"CONFIRM"})
    if isinstance(r, dict) and r.get("error"):
        return False, r["error"]
    time.sleep(0.05)
    r = api("POST", f"/orders/{order_id}/ai-execute", {"actionType":"ALLOCATE"})
    if isinstance(r, dict) and r.get("error"):
        return False, r["error"]
    time.sleep(0.05)
    r = api("POST", f"/orders/{order_id}/ai-execute", {"actionType":"SHIP"})
    if isinstance(r, dict) and r.get("error"):
        return False, r["error"]
    return True, "ok"

def fulfill_direct(order_id):
    """Fulfill an order using direct endpoints (confirm -> allocate -> ship)."""
    time.sleep(0.05)
    r = api("POST", f"/orders/{order_id}/confirm")
    if isinstance(r, dict) and r.get("error"):
        return False, r["error"]
    time.sleep(0.05)
    r = api("POST", f"/orders/{order_id}/allocate")
    if isinstance(r, dict) and r.get("error"):
        return False, r["error"]
    time.sleep(0.05)
    tn = f"TN-{random.randint(10000000,99999999)}"
    r = api("POST", f"/orders/{order_id}/ship", params={"carrierId":"FEDEX","trackingNumber":tn})
    if isinstance(r, dict) and r.get("error"):
        return False, r["error"]
    return True, "ok"

def main():
    global TOKEN
    
    # Login
    L = api("POST", "/auth/login", {"username":"admin","password":"Test1234!"}, raw=True)
    if not L.get("success"):
        api("POST", "/auth/register", {"username":"admin","password":"Test1234!","role":"ADMIN"})
        L = api("POST", "/auth/login", {"username":"admin","password":"Test1234!"}, raw=True)
    TOKEN = L["data"]["accessToken"]
    TENANT_ID = L["data"]["tenantId"]
    print(f"Tenant: {TENANT_ID}")
    
    # Get ALL orders (pending)
    orders_resp = api("GET", "/orders", params={"page": 0, "size": 2000, "status": "PENDING"}, raw=True)
    all_orders = []
    if orders_resp.get("success"):
        data = orders_resp.get("data", {})
        # Handle both paginated and list responses
        if isinstance(data, dict) and data.get("content"):
            all_orders = data["content"]
        elif isinstance(data, list):
            all_orders = data
    print(f"Pending orders: {len(all_orders)}")
    
    if len(all_orders) == 0:
        print("No pending orders!")
        return
    
    # Batch 1: Direct ship 100 orders
    print(f"\n=== Batch 1: Ship 100 orders directly ===")
    shipped1 = 0
    failed1 = 0
    for i, order in enumerate(all_orders[:100]):
        ok, msg = fulfill_direct(order["id"])
        if ok: shipped1 += 1
        else: failed1 += 1
        if (i+1) % 25 == 0 and ok:
            print(f"  {i+1}/100: {shipped1} shipped")
    
    print(f"  Result: {shipped1} shipped, {failed1} failed")
    
    # Batch 2: Direct ship 300 more
    remaining2 = [o for o in all_orders if o.get("status") == "PENDING"][:300]
    print(f"\n=== Batch 2: Ship {len(remaining2)} more directly ===")
    shipped2 = 0
    failed2 = 0
    for i, order in enumerate(remaining2):
        ok, msg = fulfill_direct(order["id"])
        if ok: shipped2 += 1
        else: failed2 += 1
        if (i+1) % 50 == 0 and ok:
            print(f"  {i+1}/{len(remaining2)}: {shipped2} shipped")
    
    print(f"  Result: {shipped2} shipped, {failed2} failed")
    
    # Batch 3: AI fulfill remaining
    remaining3 = [o for o in all_orders if o.get("status") == "PENDING"]
    print(f"\n=== Batch 3: AI fulfill {len(remaining3)} orders ===")
    shipped3 = 0
    failed3 = 0
    for i, order in enumerate(remaining3):
        ok, msg = fulfill_ai(order["id"])
        if ok: shipped3 += 1
        else: failed3 += 1
        if (i+1) % 100 == 0:
            print(f"  {i+1}/{len(remaining3)}: {shipped3} fulfilled")
    
    print(f"  Result: {shipped3} fulfilled, {failed3} failed")
    
    # Summary
    total_shipped = shipped1 + shipped2 + shipped3
    total_failed = failed1 + failed2 + failed3
    print(f"\n=== SUMMARY ===")
    print(f"Total orders processed: {total_shipped + total_failed}")
    print(f"Shipped: {total_shipped}")
    print(f"Failed: {total_failed}")
    
    # Check final state
    print(f"\n=== Final state ===")
    ship_resp = api("GET", "/orders", params={"page":0,"size":10,"status":"SHIPPED"}, raw=True)
    if ship_resp.get("success"):
        data = ship_resp.get("data", {})
        shipped_total = 0
        if isinstance(data, dict):
            shipped_total = data.get("totalElements", len(data.get("content", [])))
        elif isinstance(data, list):
            shipped_total = len(data)
        print(f"SHIPPED orders: {shipped_total}")
    
    pend_resp = api("GET", "/orders", params={"page":0,"size":10,"status":"PENDING"}, raw=True)
    if pend_resp.get("success"):
        data = pend_resp.get("data", {})
        pend_total = 0
        if isinstance(data, dict):
            pend_total = data.get("totalElements", len(data.get("content", [])))
        elif isinstance(data, list):
            pend_total = len(data)
        print(f"PENDING orders: {pend_total}")

if __name__ == "__main__":
    main()
