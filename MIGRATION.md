# Nexus OMS — Migration Guide (Aug 2026)

## 1. Credentials

### Nexus Application
| Item | Value |
|------|-------|
| URL | http://localhost:3000 |
| All 6 users share | `nexusadmin` / `Nexus@2026Admin` |
| Tenant ID | `f226ca95-8668-4118-86f1-8e1da072d5da` |
| HashRouter | All URLs use `/#/<route>` e.g. `http://localhost:3000/#/orders` |
| DB | PostgreSQL `nexus_oms_dev`, user `nexus`, password `nexus` |
| Admin API | `POST http://localhost:8080/api/v1/auth/login` → returns JWT (valid ~10 min, refresh via same endpoint) |

### BigCommerce Store
> **Retrieve credentials from DB:** `PGPASSWORD=nexus psql -U nexus -d nexus_oms_dev -c "SELECT store_name, api_key, store_url FROM nx_integration_store_settings WHERE channel='BIGCOMMERCE';"`

| Item | Value |
|------|-------|
| Store hash | `XXXXXX` (see DB query above) |
| Store URL | `https://store-XXXXXX.mybigcommerce.com` |
| Admin API token | `BC_XXXX...` (stored in DB, never commit to git) |
| BC order statuses | PENDING=1, SHIPPED=2, CANCELLED=5 |
| BC sync endpoints | `/integrations/bigcommerce/sync/orders`, `/products`, `/inventory`, `/shipments`, `/refunds`, `/customers`, `/config` |
| BC import status | ✅ Complete — 1000 orders fulfilled end-to-end |

### Shopify Store
> **Retrieve credentials from DB:** `PGPASSWORD=nexus psql -U nexus -d nexus_oms_dev -c "SELECT store_name, api_key, store_url FROM nx_integration_store_settings WHERE channel='SHOPIFY';"`

| Item | Value |
|------|-------|
| Store ID (Nexus UUID) | `1eb666ed-ce37-4e0c-946c-59b408d53f88` |
| Shopify Domain | `nexus-ship-ufb7gevv.myshopify.com` |
| Access Token | `shpat_XXXX...` (stored in DB, never commit to git) |
| API Version | `2024-10` |
| Shopify Import Endpoint | `POST http://localhost:8080/api/v1/shopify/stores/1eb666ed-ce37-4e0c-946c-59b408d53f88/sync/orders` |
| Products in Shopify | ~4,935 with variants |
| Orders seeded on Shopify | ~1,904 (896 succeeded + remaining as AI_BATCH) |
| Orders imported to Nexus | 1,810 |
| Fulfillment type breakdown | STANDARD=866, AI_BATCH=417, BOPIS=246, EXPRESS=156, WHOLESALE=120 |
| Shopify rate limit | ~1 order creation/sec sustained. GraphQL: 2000 points/bucket, 100 refill/s, 10 points/order |
| Status | ✅ Import pipeline working end-to-end |

---

## 2. Running Nexus on a New PC

### Prerequisites
- Java 17 (OpenJDK): `sudo apt install openjdk-17-jdk`
- Node.js 18+: `sudo apt install nodejs npm`
- PostgreSQL 14+: `sudo apt install postgresql postgresql-contrib`
- Git, Maven

### Backend Setup
```bash
# Clone repo
git clone https://github.com/Mr-Mayank-Sharma/nexus.git
cd nexus/nexus-oms-backend

# Build (offline mode — all deps are in .m2)
export JAVA_HOME=/usr/lib/jvm/java-17-openjdk-amd64
mvn -q package -DskipTests -o

# Create DB (if not exists)
sudo -u postgres psql -c "CREATE DATABASE nexus_oms_dev;"
sudo -u postgres psql -c "CREATE USER nexus WITH PASSWORD 'nexus';"
sudo -u postgres psql -c "GRANT ALL PRIVILEGES ON DATABASE nexus_oms_dev TO nexus;"

# Flyway is disabled — schema is auto-created by JPA/Hibernate on first run
# Start backend
export JAVA_HOME=/usr/lib/jvm/java-17-openjdk-amd64
nohup $JAVA_HOME/bin/java -jar target/oms-1.0.0.jar --spring.profiles.active=dev > /tmp/backend.log 2>&1 &

# Verify (should return 401)
curl -s -o /dev/null -w "%{http_code}" -X POST http://localhost:8080/api/v1/auth/login \
  -H "Content-Type: application/json" -d '{"username":"x","password":"y"}'
```

### Frontend Setup
```bash
cd ../nexus-oms-frontend
npm install
nohup npm run dev > /tmp/frontend.log 2>&1 &
# Verify
curl -s -o /dev/null -w "%{http_code}" http://localhost:3000/   # expect 200
```

### Get Auth Token
```bash
TOKEN=$(curl -s -X POST http://localhost:8080/api/v1/auth/login \
  -H "Content-Type: application/json" \
  -d '{"username":"nexusadmin","password":"Nexus@2026Admin"}' \
  | python3 -c "import sys,json; print(json.load(sys.stdin)['data']['accessToken'])")
echo "$TOKEN" > /tmp/nexus_token.txt
```

---

## 3. Retesting BigCommerce (Shopify-Style — UI + AI)

The BC 1000 order run was completed using a combination of UI automation (Playwright) and AI Assistant. To retest:

### Step 1: Clear existing BC orders (optional, for clean retest)
```sql
-- Reset BC order statuses back to PENDING
PGPASSWORD=nexus psql -U nexus -d nexus_oms_dev -c "
  UPDATE nx_orders SET status='PENDING', allocated_at=NULL, shipped_at=NULL
  WHERE channel='BIGCOMMERCE' AND channel_order_id ~ '^[0-9]+$';
"
```

### Step 2: Re-sync from BigCommerce
```bash
TOKEN=$(cat /tmp/nexus_token.txt)
curl -s -X POST http://localhost:8080/api/v1/bigcommerce/sync/orders \
  -H "Authorization: Bearer $TOKEN" | python3 -m json.tool
```

### Step 3: Fulfillment via UI (bulk flow)
1. Open `http://localhost:3000/#/orders` — filter by channel=BIGCOMMERCE, status=PENDING
2. **Bulk Reassign**: Select all → Reassign to picker
3. **Create Picklists**: `POST /api/v1/picking/picklists` — batch 50 orders each
4. **Pick All**: `POST /api/v1/picking/picklists/{id}/pick-all` — auto-completes picking
5. **Book Shipment**: `POST /api/v1/shipping/shipments` — bulk shipment booking
6. Verify dashboard: Orders Shipped count

### Step 4: Fulfillment via AI (Shopify-Style)
The AI Assistant does: CONFIRM → ALLOCATE → SHIP in sequence.
Human still physically: picks, packs, loads.

```bash
# AI Execute for individual order
TOKEN=$(cat /tmp/nexus_token.txt)
ORDER_ID="<uuid>"

# Step 1: AI CONFIRM
curl -s -X POST "http://localhost:8080/api/v1/ai/execute/$ORDER_ID" \
  -H "Authorization: Bearer $TOKEN" \
  -H "Content-Type: application/json" \
  -d '{"actionType":"CONFIRM","reason":"AI batch confirmation"}'

# Step 2: AI ALLOCATE
curl -s -X POST "http://localhost:8080/api/v1/ai/execute/$ORDER_ID" \
  -H "Authorization: Bearer $TOKEN" \
  -H "Content-Type: application/json" \
  -d '{"actionType":"ALLOCATE","reason":"AI batch allocation"}'

# Step 3: AI SHIP
curl -s -X POST "http://localhost:8080/api/v1/ai/execute/$ORDER_ID" \
  -H "Authorization: Bearer $TOKEN" \
  -H "Content-Type: application/json" \
  -d '{"actionType":"SHIP","reason":"AI batch shipment"}'
```

### Step 5: Bulk AI for all pending
```python
import requests, json, time

TOKEN = open("/tmp/nexus_token.txt").read().strip()
BASE = "http://localhost:8080/api/v1"
HEADERS = {"Authorization": f"Bearer {TOKEN}", "Content-Type": "application/json"}

# Get all PENDING BC orders
orders = requests.get(f"{BASE}/orders?channel=BIGCOMMERCE&status=PENDING",
                       headers=HEADERS).json()["data"]["content"]

for order in orders:
    oid = order["id"]
    for action in ["CONFIRM", "ALLOCATE", "SHIP"]:
        r = requests.post(f"{BASE}/ai/execute/{oid}",
                          headers=HEADERS,
                          json={"actionType": action, "reason": f"AI batch {action.lower()}"})
        if r.ok and r.json().get("success"):
            print(f"  {action} OK: {oid[:8]}")
        else:
            print(f"  {action} FAIL: {oid[:8]} — {r.json().get('message','?')}")
    time.sleep(0.2)  # rate limit
```

---

## 4. Retesting Shopify (Full Matrix)

### Step 1: Import orders to Nexus
```bash
TOKEN=$(cat /tmp/nexus_token.txt)
SID="1eb666ed-ce37-4e0c-946c-59b408d53f88"

# Full import (paginated, handles 1900+ orders)
curl -s -X POST "http://localhost:8080/api/v1/shopify/stores/$SID/sync/orders" \
  -H "Authorization: Bearer $TOKEN" | python3 -m json.tool
```

### Step 2: Verify import
```sql
PGPASSWORD=nexus psql -U nexus -d nexus_oms_dev -c "
  SELECT fulfillment_type, status, count(*)
  FROM nx_orders WHERE channel='SHOPIFY'
  GROUP BY fulfillment_type, status
  ORDER BY fulfillment_type, status;
"
```

### Step 3: Fulfillment matrix by type

| Type | How to fulfill | Physical human steps |
|------|---------------|---------------------|
| STANDARD (866) | UI bulk: Reassign → Picklists → Pick All → Ship | Pick, pack, load |
| BOPIS (246) | BOPIS pickup screen (`/#/bopis`) | In-store handoff |
| EXPRESS (156) | Priority picklist, fast-track shipping | Pick, pack immediately |
| WHOLESALE (120) | Bulk allocation, pallet-level packing | Pick pallets, load |
| AI_BATCH (417) | AI Assistant: CONFIRM → ALLOCATE → SHIP | Pick, pack, load |

### Step 4: AI fulfillment for AI_BATCH orders
```python
import requests, json, time

TOKEN = open("/tmp/nexus_token.txt").read().strip()
BASE = "http://localhost:8080/api/v1"
HEADERS = {"Authorization": f"Bearer {TOKEN}", "Content-Type": "application/json"}

# Get all PENDING Shopify AI_BATCH orders
all_orders = []
page = 0
while True:
    r = requests.get(f"{BASE}/orders?channel=SHOPIFY&status=PENDING&page={page}&size=100",
                     headers=HEADERS).json()
    batch = r["data"]["content"]
    ai_batch = [o for o in batch if o.get("metadata",{}).get("fulfillmentType") == "AI_BATCH"]
    all_orders.extend(ai_batch)
    if len(batch) < 100:
        break
    page += 1

print(f"Found {len(all_orders)} AI_BATCH orders to fulfill")

for order in all_orders:
    oid = order["id"]
    for action in ["CONFIRM", "ALLOCATE", "SHIP"]:
        r = requests.post(f"{BASE}/ai/execute/{oid}",
                          headers=HEADERS,
                          json={"actionType": action, "reason": f"Shopify AI batch {action.lower()}"})
        status = "OK" if r.ok and r.json().get("success") else f"FAIL: {r.json().get('message','?')}"
        print(f"  {action}: {oid[:8]} → {status}")
    time.sleep(0.2)
```

### Step 5: Standard/BOPIS/Express/Wholesale fulfillment via UI
1. Go to `http://localhost:3000/#/orders`
2. Filter: channel=SHOPIFY, fulfillment_type=STANDARD, status=PENDING
3. Select all → Reassign → Create picklists → Pick All → Ship
4. Repeat for EXPRESS (priority), WHOLESALE (bulk), BOPIS (pickup screen)

---

## 5. Key API Endpoints Reference

### Auth
| Method | Endpoint | Body |
|--------|----------|------|
| POST | `/api/v1/auth/login` | `{"username":"nexusadmin","password":"Nexus@2026Admin"}` |

### Orders
| Method | Endpoint | Notes |
|--------|----------|-------|
| GET | `/api/v1/orders` | Query: `channel`, `status`, `page`, `size` |
| GET | `/api/v1/orders/{id}` | Single order detail |
| POST | `/api/v1/ai/execute/{orderId}` | `{"actionType":"CONFIRM\|ALLOCATE\|SHIP","reason":"..."}` |

### Picking
| Method | Endpoint | Notes |
|--------|----------|-------|
| POST | `/api/v1/picking/picklists` | `{"warehouseId":"<uuid>","orderIds":["<uuid>",...]}` |
| POST | `/api/v1/picking/picklists/{id}/pick-all` | Auto-completes all items |
| POST | `/api/v1/picking/lists/{id}/pick-all` | Alternative path |

### Shipping
| Method | Endpoint | Notes |
|--------|----------|-------|
| POST | `/api/v1/shipping/shipments` | `{"orderId":"<uuid>","carrier":"...","method":"..."}` |

### Sync (BigCommerce)
| Method | Endpoint | Notes |
|--------|----------|-------|
| POST | `/api/v1/bigcommerce/sync/orders` | Import all BC orders |
| POST | `/api/v1/bigcommerce/sync/products` | Import BC products |
| POST | `/api/v1/bigcommerce/sync/inventory` | Sync inventory levels |

### Sync (Shopify)
| Method | Endpoint | Notes |
|--------|----------|-------|
| POST | `/api/v1/shopify/stores/{storeId}/sync/orders` | Import Shopify orders (paginated) |
| POST | `/api/v1/shopify/stores/{storeId}/sync/products` | Sync products |
| POST | `/api/v1/shopify/stores/{storeId}/sync/inventory` | Sync inventory |

### Transfers
| Method | Endpoint | Notes |
|--------|----------|-------|
| POST | `/api/v1/transfers` | Create transfer order |
| POST | `/api/v1/transfers/{id}/submit` | Submit for approval |

### Receiving
| Method | Endpoint | Notes |
|--------|----------|-------|
| POST | `/api/v1/warehouse/receiving` | Record receipt |

---

## 6. Infrastructure Facts

| Item | Detail |
|------|--------|
| Backend JAR | `nexus-oms-backend/target/oms-1.0.0.jar` |
| Java | Must use Java 17 (`/usr/lib/jvm/java-17-openjdk-amd64/bin/java`) — Java 8 gives class version error |
| Maven build | `mvn -q package -DskipTests -o` (offline, all deps cached in ~/.m2) |
| Frontend dev server | Port 3000, `npm run dev` |
| Database | PostgreSQL `nexus_oms_dev`, user `nexus`, password `nexus` |
| Flyway | Disabled — schema auto-created by JPA/Hibernate |
| Rate limiting | `RateLimitingFilter`: 100 POST/s, 200 GET/s per IP (Redis-backed) |
| Cache | Dashboard has `@Cacheable("dashboard")` — clears on backend restart |
| Git remote | `origin https://github.com/Mr-Mayank-Sharma/nexus.git` branch `main` |
| Anti-abuse throttle | Shopify limits ~1 order creation/sec sustained |
| BC status push | Only on CONFIRMED, SHIPPED, CANCELLED |
| AI filter | Confidence > 0.3 (CANCEL at 0.15 excluded) |
| Playwright | No installed browsers in headless — use `/usr/bin/google-chrome --no-sandbox` |

---

## 7. Completed Progress (Aug 2026)

### Phase 1: BigCommerce ✅ COMPLETE
- 1000 BC orders seeded and fulfilled end-to-end
  - 562 via normal UI bulk flow (Reassign → 12 batch picklists → Pick All → Complete → Book Shipment)
  - 433 via AI Assistant (OrderDetailPage Apply loop: CONFIRM→ALLOCATE→SHIP)
  - 5 pre-existing shipped
- Audit trail: AI_CONFIRM=433, AI_ALLOCATE=433, AI_SHIP=433 in `nx_audit_log`
- All 95 routes toured clean (page_tour.py)
- Module exercises ≥5 each: shipments(7), rate-shopping(5), manifest, invoices(5), returns(5), transfers(5), receipts(5), cycle-counts(5)

### Phase 2: Operations Data Seeding ✅ COMPLETE
- 7 notification templates, 128 notification logs
- 3 approval rules, 15 approvals (8 PENDING_REVIEW)
- 5 rejection reasons, 9 rejections (3 PENDING, 6 PROCESSED)
- 4 routing rules, 5 fulfillment limits
- 51 active warehouse nodes, 155 staff, 2353 customers, 3111+ order items

### Phase 3: Shopify Import ✅ COMPLETE
- 1904 orders seeded on Shopify (seeder completed)
- 1899 successfully imported to Nexus
- 1810 unique orders (some overlap from multiple import attempts)
- Fulfillment types: STANDARD=866, AI_BATCH=417, BOPIS=246, EXPRESS=156, WHOLESALE=120
- Fulfillment pipeline: Shopify API → Nexus import → fulfillment_type mapping → order items with images

### Phase 4: Shopify Fulfillment ⏳ PENDING (ready to run on new PC)
- Fulfillment automation scripts ready (see Section 4 above)
- All bugs fixed: pagination, duplicate handling, varchar truncation

### Bugs Fixed (this session)
1. **Dashboard stale data**: Envelope unwrap, order-based fulfillment funnel
2. **@NotBlank on UUIDs**: Changed to @NotNull in NxInventory, NxTransferOrder, NxWave, AiPrompt
3. **Invoice ClassCastException**: ObjectMapper-based LinkedHashMap→entity conversion
4. **Transfer submit**: Added missing endpoint + service method
5. **Deadlock in inventory**: Atomic reserveAtomic/releaseAtomic with FOR UPDATE SKIP LOCKED
6. **Allocate lock ordering**: Idempotency guard + consistent lock sequence
7. **Picking Pick All**: Added `pickAllItems` method + `/pick-all` endpoint + frontend button
8. **EndlessAisle crash**: `getEndlessAisleOrders` → `getOrders` rename
9. **Shopify pagination**: `page_info` cursor loop with correct param handling
10. **Shopify duplicate customers**: `findAllByEmail` → take first
11. **Shopify duplicate product mappings**: `findAllByTenantIdAndBcSku` → take first with image
12. **last_sync_message varchar overflow**: Truncate to 250 chars in all 4 sync services

---

## 8. Git History (latest)
```
aec82eb fix: Shopify import robustness — pagination, duplicate handling, truncation
0a17c42 feat: Pick All button + BC order 105 cancellation
6b58707 feat: Production readiness — atomic inventory, lock ordering, BC import fixes
4b8bc33 chore: nginx reverse proxy configuration
...
```

---

## 9. Quick Start Checklist (New PC)
- [ ] Install Java 17, Node.js, PostgreSQL, Maven
- [ ] Clone repo: `git clone https://github.com/Mr-Mayank-Sharma/nexus.git`
- [ ] Create DB: `nexus_oms_dev` (user: `nexus`, pass: `nexus`)
- [ ] Build backend: `mvn -q package -DskipTests -o`
- [ ] Start backend: `java -jar target/oms-1.0.0.jar --spring.profiles.active=dev`
- [ ] Start frontend: `cd nexus-oms-frontend && npm install && npm run dev`
- [ ] Login: `http://localhost:3000/#/login` → `nexusadmin` / `Nexus@2026Admin`
- [ ] Verify: Dashboard shows orders, all 95 routes load
- [ ] Retest BC: Run Section 3 steps
- [ ] Retest Shopify: Run Section 4 steps
