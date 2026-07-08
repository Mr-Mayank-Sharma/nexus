#!/usr/bin/env python3
"""Fulfill ALL remaining orders via direct ship endpoints."""
import json, urllib.request, urllib.error, time, random, sys
from urllib.parse import urlencode

BASE = "http://localhost:8080/api/v1"
TOKEN = None
ORDER_COUNT = 0
ERROR_COUNT = 0

def api(m, p, d=None, params=None, raw=True):
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
                return json.loads(r.read())
        except urllib.error.HTTPError as e:
            err = e.read().decode()
            try: msg = json.loads(err).get("message", err)
            except: msg = err
            if e.code in (409, 429): time.sleep(1); continue
            return {"error": msg[:200]}
        except Exception as e:
            if _ < 2: time.sleep(1); continue
            return {"error": str(e)[:200]}
    return {"error": "timeout"}

def ship_one(oid):
    global ORDER_COUNT, ERROR_COUNT
    try:
        r = api("POST", f"/orders/{oid}/confirm")
        if r.get("error"):
            ERROR_COUNT += 1
            return
        time.sleep(0.02)
        r = api("POST", f"/orders/{oid}/allocate")
        if r.get("error"):
            ERROR_COUNT += 1
            return
        time.sleep(0.02)
        tn = f"TN-{random.randint(10000000,99999999)}"
        r = api("POST", f"/orders/{oid}/ship",
                params={"carrierId":"FEDEX","trackingNumber":tn})
        if r.get("error"):
            ERROR_COUNT += 1
            return
    except:
        ERROR_COUNT += 1
        return
    ORDER_COUNT += 1

def main():
    global TOKEN
    
    print("Logging in...")
    L = api("POST", "/auth/login", {"username":"admin","password":"Test1234!"})
    if L.get("error"):
        api("POST", "/auth/register", {"username":"admin","password":"Test1234!","role":"ADMIN"})
        L = api("POST", "/auth/login", {"username":"admin","password":"Test1234!"})
    TOKEN = L.get("data", {}).get("accessToken", "")
    if not TOKEN: print("Login failed!"); return
    print(f"Token: {TOKEN[:20]}...")
    
    # Get all PENDING orders
    r = api("GET", "/orders", params={"page": 0, "size": 2000, "status": "PENDING"}, raw=True)
    pending = []
    if r.get("success"):
        data = r.get("data", {})
        if isinstance(data, dict):
            pending = data.get("content", [])
        elif isinstance(data, list):
            pending = data
    print(f"Pending orders: {len(pending)}")
    if not pending:
        print("No pending orders!")
        return
    
    # Save order IDs
    oids = [o["id"] for o in pending]
    total = len(oids)
    print(f"Total to ship: {total}")
    
    # Process in batches with progress
    start = time.time()
    for i, oid in enumerate(oids):
        ship_one(oid)
        if (i+1) % 200 == 0:
            elapsed = time.time() - start
            rate = (i+1) / elapsed if elapsed > 0 else 0
            print(f"  {i+1}/{total}: {ORDER_COUNT} shipped, {ERROR_COUNT} errors ({rate:.0f}/s)")
    
    elapsed = time.time() - start
    print(f"\nDone! {ORDER_COUNT} shipped, {ERROR_COUNT} errors in {elapsed:.0f}s")
    
    # Verify
    print("\nVerifying...")
    r = api("GET", "/orders", params={"page": 0, "size": 10, "status": "SHIPPED"}, raw=True)
    if r.get("success"):
        data = r.get("data", {})
        total_elements = 0
        if isinstance(data, dict):
            total_elements = data.get("totalElements", 0)
        elif isinstance(data, list):
            total_elements = len(data)
        print(f"SHIPPED orders: {total_elements}")
    
    r = api("GET", "/orders", params={"page": 0, "size": 10, "status": "PENDING"}, raw=True)
    if r.get("success"):
        data = r.get("data", {})
        total_elements = 0
        if isinstance(data, dict):
            total_elements = data.get("totalElements", 0)
        elif isinstance(data, list):
            total_elements = len(data)
        print(f"PENDING orders: {total_elements}")

if __name__ == "__main__":
    main()
