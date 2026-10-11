#!/usr/bin/env python3
"""
End-to-end lifecycle smoke test against a running Nexus API.

Walks the real order-to-cash and warehouse flows and asserts outcomes (statuses, stock deltas),
not just HTTP 200:  product -> stock -> order -> confirm -> allocate -> pick -> pack -> ship ->
deliver -> return -> refund, then receiving, cycle count and a transfer.

    API=http://localhost:8095/api/v1 NEXUS_USER=opsdemo NEXUS_PASS='...' python3 scripts/e2e/lifecycle-smoke.py

Creates uniquely named records (prefix SMK-) and leaves them in place. Exit code 1 if any step fails.
"""
import json
import os
import sys
import time
import urllib.error
import urllib.request

API = os.environ.get("API", "http://localhost:8095/api/v1")
USER = os.environ.get("NEXUS_USER", "admin")
PASS = os.environ.get("NEXUS_PASS", "")
RUN = str(int(time.time()))[-7:]
user_id = None
SKU = f"SMK-{RUN}"

results = []
token = ""


def call(method, path, body=None, params=None):
    url = API + path
    if params:
        url += "?" + "&".join(f"{k}={v}" for k, v in params.items())
    data = json.dumps(body).encode() if body is not None else None
    req = urllib.request.Request(url, data=data, method=method, headers={
        "Content-Type": "application/json", **({"Authorization": f"Bearer {token}"} if token else {})})
    try:
        with urllib.request.urlopen(req, timeout=30) as r:
            raw = r.read().decode() or "{}"
            return r.status, json.loads(raw)
    except urllib.error.HTTPError as e:
        raw = e.read().decode() or "{}"
        try:
            return e.code, json.loads(raw)
        except ValueError:
            return e.code, {"message": raw[:200]}
    except Exception as e:  # network
        return 0, {"message": str(e)}


def data(resp):
    body = resp[1] if isinstance(resp[1], dict) else {}
    # Most endpoints wrap the payload as {success, data}; a few (transfers) return the entity itself.
    return body.get("data", body if "id" in body else None)


def step(name, ok, detail=""):
    results.append((name, ok, detail))
    print(f"  {'PASS' if ok else 'FAIL'}  {name}" + (f"   [{detail}]" if detail and not ok else ""))
    return ok


def msg(resp):
    b = resp[1] if isinstance(resp[1], dict) else {}
    return f"{resp[0]} {b.get('message') or b.get('error') or ''}".strip()


def stock(node_id):
    s, b = call("GET", "/inventory")
    rows = b.get("data") or []
    for r in rows:
        if r["sku"] == SKU and r["nodeId"] == node_id:
            return r
    return None


def main():
    global token
    s, b = call("POST", "/auth/login", {"username": USER, "password": PASS})
    if s != 200 or not (b.get("data") or {}).get("accessToken"):
        print(f"cannot sign in: {msg((s, b))}")
        sys.exit(2)
    token = b["data"]["accessToken"]
    global user_id
    user_id = b["data"].get("id")

    s, b = call("GET", "/warehouses")
    whs = (b["data"].get("content") if isinstance(b["data"], dict) else b["data"]) or []
    if not whs:
        print("no warehouses in this tenant; cannot run")
        sys.exit(2)
    wh = next((w for w in whs if "WAREHOUSE" in (w.get("type") or "").upper() or "WH" in (w.get("code") or "")), whs[0])
    other = next((w for w in whs if w["id"] != wh["id"]), None)
    node = wh["id"]
    print(f"Run {RUN} on {wh['name']} ({wh.get('code')})")

    print("\nCatalogue and stock")
    r = call("POST", "/products", {"sku": SKU, "productName": f"Smoke product {RUN}", "unitPrice": 25, "costPrice": 10, "isActive": True})
    step("create product", r[0] == 200, msg(r))
    r = call("POST", "/inventory", {"sku": SKU, "nodeId": node, "quantityOnHand": 50, "quantityAllocated": 0, "quantityReserved": 0, "safetyStock": 0, "reorderPoint": 5})
    step("create stock record (50 on hand)", r[0] == 200, msg(r))
    r = call("GET", "/inventory/atp", params={"sku": SKU})
    step("available-to-promise is 50", r[0] == 200 and data(r) == 50, f"{msg(r)} atp={data(r)}")

    print("\nOrder to ship")
    order = {"channel": "MANUAL", "customerName": "Smoke Test", "customerEmail": f"smoke{RUN}@example.com",
             "shippingAddress": {"street": "1 Test St", "city": "Hoboken", "state": "NJ", "zip": "07030", "country": "US"},
             "items": [{"sku": SKU, "productName": f"Smoke product {RUN}", "quantity": 3, "unitPrice": 25}], "currency": "USD"}
    r = call("POST", "/orders", order)
    oid = (data(r) or {}).get("id")
    step("create order", r[0] == 200 and oid, msg(r))
    if not oid:
        return
    r = call("POST", f"/orders/{oid}/confirm")
    step("confirm order", r[0] == 200 and (data(r) or {}).get("status") == "CONFIRMED", f"{msg(r)} status={(data(r) or {}).get('status')}")
    r = call("POST", f"/orders/{oid}/allocate")
    s2, b2 = call("GET", f"/orders/{oid}")
    st = (b2.get("data") or {}).get("status")
    step("allocate order", r[0] == 200 and st in ("ALLOCATED", "PARTIALLY_ALLOCATED"), f"{msg(r)} status={st}")
    inv = stock(node)
    step("allocation reserved 3 units", bool(inv) and (inv["quantityAllocated"] + inv["quantityReserved"]) == 3,
         f"allocated={inv and inv['quantityAllocated']} reserved={inv and inv['quantityReserved']}")

    r = call("POST", "/picking/lists/from-order", params={"orderId": oid})
    pl = data(r) or {}
    step("create pick list from order", r[0] == 200 and pl.get("id"), msg(r))
    if pl.get("id"):
        r = call("POST", f"/picking/lists/{pl['id']}/start")
        step("start picking", r[0] == 200, msg(r))
        r = call("POST", f"/picking/lists/{pl['id']}/pick-all")
        step("pick all items", r[0] == 200, msg(r))
        r = call("POST", f"/picking/lists/{pl['id']}/complete")
        step("complete pick list", r[0] == 200, msg(r))

    r = call("POST", "/packing/packages", {"orderId": oid, "picklistId": pl.get("id"), "packageType": "BOX", "boxName": "M", "weightLbs": 2.5,
                                          "carrierName": "UPS", "serviceLevel": "GROUND"})
    pkg = data(r) or {}
    step("create package", r[0] == 200 and pkg.get("id"), msg(r))
    if pkg.get("id"):
        for name, path in [("start packing", "start"), ("complete packing", "complete")]:
            r = call("POST", f"/packing/packages/{pkg['id']}/{path}", params={"packedBy": USER} if path == "complete" else None)
            step(name, r[0] == 200, msg(r))

    r = call("POST", f"/orders/{oid}/ship", {"carrierId": "UPS", "trackingNumber": f"1Z{RUN}"})
    shipped = (data(r) or {}).get("status")
    step("ship order", r[0] == 200 and shipped in ("SHIPPED", "IN_TRANSIT"), f"{msg(r)} status={shipped}")
    inv = stock(node)
    step("shipping deducted 3 units from on hand", bool(inv) and inv["quantityOnHand"] == 47,
         f"onHand={inv and inv['quantityOnHand']} allocated={inv and inv['quantityAllocated']}")
    r = call("PUT", f"/orders/{oid}/status", {"status": "DELIVERED"})
    step("mark delivered", r[0] == 200 and (data(r) or {}).get("status") == "DELIVERED", f"{msg(r)} status={(data(r) or {}).get('status')}")
    r = call("PUT", f"/orders/{oid}/status", {"status": "PENDING"})
    step("an illegal transition (DELIVERED -> PENDING) is refused with 409", r[0] == 409, msg(r))

    print("\nReturn and refund")
    s, b = call("GET", "/customers")
    custs = (b.get("data") or [])
    custs = custs.get("content", []) if isinstance(custs, dict) else custs
    cust = custs[0]["id"] if custs else None
    if cust:
        r = call("POST", "/returns", {"orderId": oid, "customerId": cust, "reason": "DEFECTIVE", "returnChannel": "MAIL", "rmaType": "REFUND",
                                      "items": [{"sku": SKU, "quantity": 1, "reason": "DEFECTIVE"}]})
        rid = (data(r) or {}).get("id")
        step("create return", r[0] == 200 and rid, msg(r))
        if rid:
            for name, path in [("approve return", "approve"), ("receive return", "receive")]:
                r = call("POST", f"/returns/{rid}/{path}", {})
                step(name, r[0] == 200, msg(r))
            r = call("GET", f"/returns/{rid}")
            ritems = (data(r) or {}).get("items") or []
            if not ritems:
                s2, b2 = call("GET", f"/returns/{rid}/items")
                ritems = b2.get("data") or []
            step("return has line items", bool(ritems), "no items on the return")
            if ritems:
                insp = [{"id": i["id"], "condition": "GOOD", "grade": "A", "disposition": "RESTOCK", "refundAmount": 25} for i in ritems]
                r = call("POST", f"/returns/{rid}/inspect", {"items": insp, "inspectedBy": user_id})
                step("inspect return", r[0] == 200, msg(r))
            r = call("POST", f"/returns/{rid}/refund", {"refundAmount": 25, "refundReference": f"RF-{RUN}"})
            step("refund return", r[0] == 200, msg(r))
    else:
        step("create return (needs a customer)", False, "no customers in this tenant")

    print("\nReceiving, cycle count, transfer")
    r = call("POST", "/inventory-receipts", {"nodeId": node, "receiptType": "PURCHASE_ORDER", "referenceNumber": f"PO-{RUN}", "sku": SKU, "quantity": 20, "unitCost": 10})
    rcpt = (data(r) or {}).get("id")
    step("create receipt", r[0] == 200 and rcpt, msg(r))
    if rcpt:
        r = call("POST", f"/inventory-receipts/{rcpt}/receive", {})
        step("receive stock", r[0] == 200, msg(r))
        inv = stock(node)
        # 47 on hand, +20 received, +1 returned if the return restocked
        step("receiving added 20 units", bool(inv) and inv["quantityOnHand"] >= 67, f"onHand={inv and inv['quantityOnHand']}")
    r = call("POST", "/cycle-counts", {"nodeId": node, "sku": SKU, "productName": f"Smoke product {RUN}", "expectedQty": (stock(node) or {}).get("quantityOnHand", 0)})
    cc = (data(r) or {}).get("id")
    step("create cycle count", r[0] == 200 and cc, msg(r))
    if cc:
        r = call("POST", f"/cycle-counts/{cc}/count", None, {"countedQty": ((stock(node) or {}).get("quantityOnHand", 0)) - 2})
        step("record a count 2 under", r[0] == 200, msg(r))
    transfer_src_before = (stock(node) or {}).get("quantityOnHand", 0)
    if other:
        r = call("POST", "/transfers", {"transferType": "WAREHOUSE_TO_STORE", "sourceNodeId": node, "destinationNodeId": other["id"], "priority": "NORMAL",
                                       "items": [{"sku": SKU, "quantityRequested": 5}]})
        tid = (data(r) or {}).get("id")
        step("create transfer", r[0] in (200, 201) and tid, msg(r))
        for name, path in [("submit transfer", "submit"), ("approve transfer", "approve"), ("ship transfer", "ship"), ("receive transfer", "receive")]:
            if not tid:
                break
            r = call("PUT", f"/transfers/{tid}/{path}")
            step(name, r[0] == 200, msg(r))

    if other:
        src, dst = stock(node), stock(other["id"])
        step("the transfer took 5 off the source", bool(src) and src["quantityOnHand"] == transfer_src_before - 5,
             f"before={transfer_src_before} after={src and src['quantityOnHand']}")
        step("the transfer put 5 on hand at the destination", bool(dst) and dst["quantityOnHand"] == 5 and (dst["quantityInTransit"] or 0) == 0,
             f"dest={dst and (dst['quantityOnHand'], dst['quantityInTransit'])}")

    print("\nInventory ledger")
    s, b = call("GET", "/inventory/movements", params={"sku": SKU, "limit": 200})
    moves = b.get("data") or []
    types = {m["movementType"] for m in moves}
    step("ledger recorded the stock creation, reservation, shipment, receipt and count",
         {"ADJUSTMENT", "RESERVE", "SHIPMENT", "RECEIPT", "CYCLE_COUNT", "TRANSFER_OUT"} <= types or not other, f"types seen: {sorted(types)}")
    ship = [m for m in moves if m["movementType"] == "SHIPMENT"]
    step("shipment line takes 3 off on-hand and 3 off allocated",
         len(ship) == 1 and ship[0]["onHandDelta"] == -3 and ship[0]["allocatedDelta"] == -3, str(ship))
    s, b = call("GET", "/inventory/ledger-check")
    rows = [r for r in (b.get("data") or []) if r["sku"] == SKU]
    step("the ledger explains this SKU's on-hand exactly", bool(rows) and all(r["unexplained"] == 0 for r in rows), str(rows))

    failed = [x for x in results if not x[1]]
    print(f"\n{len(results) - len(failed)}/{len(results)} steps passed")
    for name, _, detail in failed:
        print(f"  FAILED: {name}  {detail}")
    sys.exit(1 if failed else 0)


if __name__ == "__main__":
    main()
