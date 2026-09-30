# T-09: Shopify→OMS Fulfillment Reconciliation — Implementation Plan

**Date:** 2026-09-30
**Branch:** `feature/p0-reconciliation-carrier-labels` (worktree: `/Users/mayanksharma/Desktop/nexus-p0`)
**Spec:** `docs/hotwax/nexus-p0-implementation-specs.md` §T-09 (lines 18–148)

---

## 0. Corrected state of the world (supersedes prior session summary)

| Item | Status |
|---|---|
| **T-12 carrier labels** | ✅ **DONE** — commit `2d0456c` on this branch, pushed. `CarrierLabelAdapter` SPI + Jitsu/Sapi/Simulated, `NxCarrierLabelConfig` CRUD, `ShippingLabelService` extension, `V62__carrier_label_adapter.sql`, `LabelPrintingPage.tsx`, tests. |
| **T-09 fulfillment reconciliation** | ❌ **NOT done** — this plan. |
| **Migration numbering** | Spec numbering is **correct as-is**: T-09 = `V61`, T-12 = `V62`. Main has a V61/V62 gap (V60 → V63); the branch already fills V62. T-09 fills V61. **No renumbering needed.** (Prior summary's "V68/V69" claim was based on a stale checkout that does not match main or this branch.) |
| **Branch drift** | Branch is 1 commit behind main (`da7bb5e`, docs-only). Clean merge expected. |

---

## 1. Scope

Implement the T-09 reconciliation engine per spec:

1. **Tolerant matcher** — EXACT / PARTIAL / LOCATION_CHANGE / QUANTITY_DELTA / ANOMALY
2. **Truth-acceptance** — accept Shopify fulfillment as truth, mark items fulfilled, adjust ship groups, write ledger row, emit `order.fulfilled`, record NetSuite delta as PENDING
3. **Cross-system repair job** — scheduled, detects fulfilled-on-Shopify-but-not-in-OMS, repairs
4. **Mismatch report** — completed-on-Shopify-but-not-in-OMS with reason
5. **REST endpoints** — 4 endpoints under `/api/v1/reconciliation`
6. **Frontend** — `FulfillmentReconciliationPage.tsx` (mismatch table, order drawer, repair confirm)
7. **Tests** — matcher unit tests + idempotency + controller test

**Out of scope (honest):** actual NetSuite propagation. The NetSuite connector is T-14 (not built). The ledger records `netsuite_sync_status=PENDING` and the delta is emitted as an event; real NetSuite sync lands with T-14. The repair job adjusts OMS inventory QH directly (that part is real).

---

## 2. Files to create

### 2.1 Migration — `nexus-oms-backend/src/main/resources/db/migration/V61__fulfillment_reconciliation.sql`

Per spec SQL, adjusted to codebase conventions (same style as `V62__carrier_label_adapter.sql`):

```sql
CREATE TABLE IF NOT EXISTS nx_fulfillment_reconciliation (
    id                      UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    tenant_id               UUID NOT NULL,
    order_id                UUID NOT NULL REFERENCES nx_orders(id),
    order_number            VARCHAR(64) NOT NULL,
    shopify_fulfillment_id  VARCHAR(64),
    match_type              VARCHAR(20) NOT NULL,   -- EXACT|PARTIAL|LOCATION_CHANGE|QUANTITY_DELTA|ANOMALY
    fulfilled_location      VARCHAR(64),
    routed_location         VARCHAR(64),
    fulfilled_items         JSONB,
    remaining_items         JSONB,
    resolution              VARCHAR(20) NOT NULL,   -- ACCEPTED|PARTIAL_ACCEPTED|FLAGGED|REPAIRED
    netsuite_sync_status    VARCHAR(20) DEFAULT 'PENDING',
    inventory_delta         INTEGER,
    notes                   TEXT,
    created_at              TIMESTAMP NOT NULL DEFAULT now(),
    updated_at              TIMESTAMP NOT NULL DEFAULT now()
);
CREATE INDEX IF NOT EXISTS idx_fulfill_recon_order   ON nx_fulfillment_reconciliation(tenant_id, order_id);
CREATE INDEX IF NOT EXISTS idx_fulfill_recon_status  ON nx_fulfillment_reconciliation(netsuite_sync_status);
```

### 2.2 Entity — `entity/NxFulfillmentReconciliation.java`

JPA entity mirroring the table. JSONB columns (`fulfilled_items`, `remaining_items`) mapped the same way `NxCarrierLabelConfig.requiredFields` maps JSONB (check that pattern during implementation — likely `@JdbcTypeCode(SqlTypes.JSON)` with `List<Map<String,Object>>` or a String + Jackson converter; follow whatever the branch already uses).

### 2.3 Repository — `repository/FulfillmentReconciliationRepository.java`

- `Optional<NxFulfillmentReconciliation> findByTenantIdAndShopifyFulfillmentId(UUID tenantId, String shopifyFulfillmentId)` — idempotency key
- `List<NxFulfillmentReconciliation> findByTenantIdAndOrderIdOrderByCreatedAtDesc(UUID tenantId, UUID orderId)` — history
- `List<NxFulfillmentReconciliation> findByTenantIdAndResolutionInOrderByCreatedAtDesc(UUID tenantId, List<String> resolutions)` — mismatch report
- `boolean existsByTenantIdAndShopifyFulfillmentId(UUID tenantId, String shopifyFulfillmentId)`

### 2.4 DTOs — `dto/` (records, per codebase convention)

- `ShopifyFulfillment` — `shopifyFulfillmentId`, `orderId` (external Shopify order id), `status`, `createdAt`, `List<ShopifyFulfillmentLine>` where line = `lineItemId`, `quantity`, `locationId`
- `MatchResult` — `matchType` (enum), `acceptedItems` (`List<FulfilledItem>`), `remainingItems`, `notes`
- `FulfilledItem` — `orderItemId`, `sku`, `quantity`, `location`
- `ReconciliationResult` — `reconciliationId`, `matchType`, `resolution`, `inventoryDelta`, `orderNumber`
- `ReconciliationReportRow` — `orderId`, `orderNumber`, `matchType`, `resolution`, `reason`, `netsuiteSyncStatus`, `createdAt`
- `MatchType` / `Resolution` enums (or string constants — follow branch style)

### 2.5 Service — `service/FulfillmentReconciliationService.java`

```java
@Service
public class FulfillmentReconciliationService {

    // 1. TOLERANT MATCHER
    public MatchResult matchFulfillment(NxOrder order, ShopifyFulfillment fulfillment)

    // 2. TRUTH-ACCEPTANCE (idempotent by shopifyFulfillmentId)
    @Transactional
    public ReconciliationResult acceptFulfillment(UUID orderId, ShopifyFulfillment fulfillment)

    // 3. CROSS-SYSTEM REPAIR JOB
    @Scheduled(cron = "${nexus.reconciliation.cron:0 */15 * * * *}")
    public void repairCrossSystemMismatches()

    // 4. MISMATCH REPORT
    public List<ReconciliationReportRow> getMismatchReport(UUID tenantId)

    // 5. HISTORY
    public List<NxFulfillmentReconciliation> getOrderHistory(UUID tenantId, UUID orderId)
}
```

**Matcher rules (per spec):**
- EXACT: all open items fulfilled at routed location, full qty
- PARTIAL: subset of items fulfilled → accept fulfilled, keep rest open
- LOCATION_CHANGE: fulfilled at different facility than routed → accept as truth, re-ship-group
- QUANTITY_DELTA: qty differs → accept actual, flag delta
- ANOMALY: cannot reconcile (unknown line item) → flag for manual review

**Truth-acceptance flow (`acceptFulfillment`):**
1. Tenant from `TenantContext.getCurrentTenantId()`
2. Idempotency check: `existsByTenantIdAndShopifyFulfillmentId` → return existing result (no double-apply)
3. Load order; run matcher
4. Mark accepted items fulfilled on `NxOrderItem` (add `fulfilled_qty` column via migration — see §2.6) / update order status if fully fulfilled
5. On LOCATION_CHANGE: update ship-group/facility on the order items
6. Write `nx_fulfillment_reconciliation` row (resolution from matchType; `inventory_delta` = negative accepted qty)
7. Apply inventory QH delta via `InventoryService` (real adjustment)
8. Emit `order.fulfilled` via `EventBus` (graceful when Kafka absent)
9. `netsuite_sync_status=PENDING` (real NetSuite sync is T-14)

**Repair job:** query Shopify (`ShopifyClient`) for fulfilled orders in the window, compare against OMS order status, repair mismatches by calling `acceptFulfillment`. Guard with tenant iteration (multi-tenant). Log + ledger rows with `resolution=REPAIRED`.

### 2.6 Migration addition — `fulfilled_qty` on order items

Add to `V61__fulfillment_reconciliation.sql`:

```sql
ALTER TABLE nx_order_items ADD COLUMN IF NOT EXISTS fulfilled_qty INTEGER DEFAULT 0;
```

(Needed to mark partial fulfillment without destroying the original `quantity`.)

### 2.7 Controller — `controller/FulfillmentReconciliationController.java`

`@RestController @RequestMapping("/api/v1/reconciliation")`, `ResponseEntity<ApiResponse<T>>` pattern (match `OrderController`):

| Method | Path | Purpose |
|---|---|---|
| POST | `/fulfillments` | Accept fulfillment as truth (idempotent by `shopify_fulfillment_id`) |
| GET | `/mismatches` | Mismatch report |
| POST | `/repair` | Trigger repair job on demand |
| GET | `/orders/{orderId}` | Reconciliation history for one order |

Use `@Operation` (Swagger), `@Valid`, `TenantContext.getCurrentTenantId()`.

### 2.8 Kafka — `kafka/Topics.java`

Add: `public static final String ORDER_FULFILLED = "order.fulfilled";`

### 2.9 Webhook hook — `service/shopify/ShopifyWebhookService.java`

The `orders/fulfilled` webhook is already **registered** (line 59). Wire the **receipt** path: when a fulfillment webhook arrives, parse `ShopifyFulfillment` and call `acceptFulfillment`. (Verify the webhook receive endpoint exists — likely a controller; if the receive path is a stub, add the handler there.)

### 2.10 Frontend — `nexus-oms-frontend/src/pages/FulfillmentReconciliationPage.tsx`

- Mismatch report table (order, reason, matchType, status, Repair action)
- Order detail drawer (reconciliation history, fulfilled vs remaining items, NetSuite sync status)
- Repair confirmation showing inventory delta
- API module additions in `src/api/newBackend.ts` (follow existing pattern)
- Route + nav link (follow `LabelPrintingPage` wiring)

### 2.11 Tests

- `src/test/java/com/nexus/oms/service/FulfillmentReconciliationServiceTest.java`
  - EXACT match → ACCEPTED
  - PARTIAL (1 of 2 items) → PARTIAL_ACCEPTED, other item stays open
  - LOCATION_CHANGE → ACCEPTED + re-ship-group
  - QUANTITY_DELTA → accepted actual qty, delta flagged
  - ANOMALY (unknown line item) → FLAGGED
  - Idempotency: re-post same `shopify_fulfillment_id` → no double-apply, no duplicate ledger row
- `FulfillmentReconciliationControllerTest` — endpoint smoke tests (or extend existing controller test pattern)

---

## 3. Implementation order

1. Migration V61 (+ `fulfilled_qty` column)
2. Entity + repository
3. DTOs + enums
4. `FulfillmentReconciliationService` (matcher → accept → repair → report)
5. `Topics.ORDER_FULFILLED` + EventBus emission
6. Controller
7. Webhook receipt wiring
8. Frontend page + API module + route
9. Tests
10. `mvn -q -DskipTests package` + targeted test run; frontend `npm run build` (or tsc)

---

## 4. Acceptance criteria mapping (spec §Acceptance test)

| # | Spec criterion | Where verified |
|---|---|---|
| 1 | 2-item order, 1 fulfilled → PARTIAL, PARTIAL_ACCEPTED, other open, QH reduced | Matcher + accept unit test |
| 2 | Different facility → LOCATION_CHANGE, ACCEPTED, re-ship-group | Matcher unit test |
| 3 | Fulfilled in Shopify not in OMS → repair job detects, REPAIRED, QH adjusted | Repair job test (mock ShopifyClient) |
| 4 | No stuck states; inventory consistent | Integration of accept + inventory delta |
| 5 | Re-post same fulfillment id → no double-apply | Idempotency unit test |

---

## 5. Risks / notes

- **NetSuite sync is PENDING-only** until T-14 (NetSuite connector). Ledger + event carry the delta; do not fake a NetSuite call.
- **JSONB mapping** — copy the exact pattern from `NxCarrierLabelConfig` (already on this branch) to stay consistent.
- **`nx_orders` FK** — spec references `nx_orders(id)`; verify the actual table name in this branch (`nx_orders` vs `orders`) during implementation and adjust.
- **Webhook receipt path** — verify whether `orders/fulfilled` receipt is currently handled or a stub; wire accordingly.
- **Repair job tenant iteration** — iterate tenants with active Shopify stores; never run cross-tenant.