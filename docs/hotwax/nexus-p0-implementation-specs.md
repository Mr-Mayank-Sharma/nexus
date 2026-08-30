# Nexus P0 Implementation Specs — T-09 & T-12

> **Purpose:** Turn the two **P0** market-gap tickets from [`nexus-market-gap-tickets-2.md`](./nexus-market-gap-tickets-2.md) into **buildable implementation specs** grounded in the actual Nexus codebase. These are the two deadline-bound tickets (SoC peak: label integration ready **Sept 11**, test shipments **Sept 14**, full volume **Oct 28**), so they get full engineering detail: Flyway migrations, Spring Boot services, REST endpoints, React UI, and acceptance tests.
>
> **Scope:** T-09 (Shopify→OMS fulfillment reconciliation) and T-12 (carrier shipping-label integration). All other tickets (T-08, T-10, T-11, T-13, T-03+) remain at the ticket level in the round-2 doc.
>
> **Codebase grounding (verified):**
> - Backend: Spring Boot 3 + Java 17, package `com.nexus.oms`
> - Latest Flyway migration: **V60** → new migrations start at **V61**
> - Existing entities: `NxShippingLabel` (has `label_source` SIMULATED/CARRIER, `label_base64`, `label_url`, carrier/service/tracking), `NxShipment`, `NxOrder`, `NxOrderItem`, `NxOrderAllocation`, `NxInventory`
> - Existing services: `ShippingLabelService` (currently **SIMULATED** labels only), `CarrierService`, `ShipmentService`, `IntegrationPlatformService`, `IntegrationStoreService`
> - Existing controllers: `ShippingLabelController`, `CarrierController`, `ShipmentController`
> - Existing frontend: `LabelPrintingPage.tsx`, `CarriersPage.tsx`, `ShippingPage.tsx`, `CarrierRateShoppingPage.tsx`; api modules `carriers.ts`, `shipping.ts`
> - Integration hub: `com.nexus.oms.integration` (batch, connector, core, dto, protocol, webhook)

---

## T-09: Shopify→OMS Fulfillment Reconciliation (Partial / Cross-Location) — **P0**

### Problem (from transcript `shopify-fulfillment-netsuite-integration.json`)

HotWax's strict ship-group matching breaks when Shopify fulfills **partially** or at a **different location** than routed. Result: order state diverges across **Shopify / OMS / NetSuite**, and inventory (QH) is reduced in Shopify but not in NetSuite → **direct inventory corruption**. This is the **#1 integration pain** across accounts.

**Core principle: "Fulfillment is the truth."** Once Shopify says an item is fulfilled, that is a fact. The OMS must accept it, adjust ship groups, and propagate to NetSuite — not reject it.

### Design

The reconciliation engine has three layers:

1. **Tolerant matcher** — replaces strict exact-match with a matcher that accepts partial line fulfillment, facility changes, and quantity deltas, while flagging genuine anomalies.
2. **Truth-acceptance** — on a Shopify fulfillment sync, mark the fulfilled items as truth, adjust ship groups, and propagate the delta to NetSuite.
3. **Cross-system repair job** — a scheduled job that detects orders fulfilled in Shopify but not in OMS/NetSuite, and repairs state (including inventory QH).

### Flyway migration — `V61__fulfillment_reconciliation.sql`

```sql
-- T-09: Fulfillment reconciliation ledger.
-- Records every Shopify fulfillment event and how the OMS resolved it,
-- so partial/cross-location fulfillments are auditable and repairable.

CREATE TABLE nx_fulfillment_reconciliation (
    id                  UUID PRIMARY KEY,
    tenant_id           UUID NOT NULL,
    order_id            UUID NOT NULL REFERENCES nx_orders(id),
    order_number        VARCHAR(64) NOT NULL,
    shopify_fulfillment_id VARCHAR(64),
    -- How the incoming Shopify fulfillment was matched to OMS order items
    match_type          VARCHAR(20) NOT NULL,  -- EXACT | PARTIAL | LOCATION_CHANGE | QUANTITY_DELTA | ANOMALY
    -- The facility/location Shopify actually fulfilled from
    fulfilled_location  VARCHAR(64),
    -- The facility the OMS had routed to (may differ on LOCATION_CHANGE)
    routed_location     VARCHAR(64),
    -- JSON: the set of order items + quantities accepted as fulfilled
    fulfilled_items     JSONB,
    -- JSON: the set of order items + quantities still open after reconciliation
    remaining_items     JSONB,
    -- Resolution taken by the engine
    resolution          VARCHAR(20) NOT NULL,  -- ACCEPTED | PARTIAL_ACCEPTED | FLAGGED | REPAIRED
    -- NetSuite sync status for the propagated delta
    netsuite_sync_status VARCHAR(20) DEFAULT 'PENDING',  -- PENDING | SYNCED | FAILED
    -- Inventory QH delta applied (negative = reduction)
    inventory_delta     INTEGER,
    notes               TEXT,
    created_at          TIMESTAMP NOT NULL DEFAULT now(),
    updated_at          TIMESTAMP NOT NULL DEFAULT now()
);

CREATE INDEX idx_fulfill_recon_order ON nx_fulfillment_reconciliation(tenant_id, order_id);
CREATE INDEX idx_fulfill_recon_status ON nx_fulfillment_reconciliation(netsuite_sync_status);
```

### Backend — new service `FulfillmentReconciliationService`

Package: `com.nexus.oms.service`

```java
@Service
public class FulfillmentReconciliationService {

    // 1. TOLERANT MATCHER
    // Given a Shopify fulfillment (list of {line_item_id, quantity, location}),
    // match against the OMS order's open items. Returns a MatchResult with:
    //   - matchType: EXACT | PARTIAL | LOCATION_CHANGE | QUANTITY_DELTA | ANOMALY
    //   - acceptedItems: items to mark fulfilled
    //   - remainingItems: items still open
    // Rules:
    //   - EXACT: all open items fulfilled at routed location, full qty
    //   - PARTIAL: subset of items fulfilled (accept the fulfilled ones, keep rest open)
    //   - LOCATION_CHANGE: fulfilled at a different facility than routed (accept as truth, re-ship-group)
    //   - QUANTITY_DELTA: qty differs from routed (accept actual, flag delta)
    //   - ANOMALY: cannot reconcile (e.g. unknown line item) -> flag for manual review
    public MatchResult matchFulfillment(NxOrder order, ShopifyFulfillment fulfillment) { ... }

    // 2. TRUTH-ACCEPTANCE
    // Accept the matched fulfillment as truth:
    //   - mark accepted items fulfilled on the order
    //   - adjust ship groups (re-ship-group on LOCATION_CHANGE)
    //   - write a nx_fulfillment_reconciliation row
    //   - emit Kafka event order.fulfilled (partial or full)
    //   - trigger NetSuite delta sync (inventory QH reduction for accepted items)
    @Transactional
    public ReconciliationResult acceptFulfillment(UUID orderId, ShopifyFulfillment fulfillment) { ... }

    // 3. CROSS-SYSTEM REPAIR JOB (scheduled)
    // Detect orders fulfilled in Shopify but not in OMS/NetSuite:
    //   - query Shopify for fulfilled orders in window
    //   - compare against OMS order status
    //   - for mismatches, repair: accept fulfillment, adjust inventory QH, sync NetSuite
    @Scheduled(cron = "${nexus.reconciliation.cron:0 */15 * * * *}")
    public void repairCrossSystemMismatches() { ... }

    // 4. MISMATCH REPORT
    // "Completed on Shopify but not in OMS" orders, with reason
    // (ship-group mismatch, facility change, partial).
    public List<ReconciliationReportRow> getMismatchReport(UUID tenantId, ...) { ... }
}
```

### Backend — REST endpoints (`FulfillmentReconciliationController`)

| Method | Path | Purpose |
|--------|------|---------|
| `POST` | `/api/v1/reconciliation/fulfillments` | Accept a Shopify fulfillment as truth (idempotent by `shopify_fulfillment_id`) |
| `GET` | `/api/v1/reconciliation/mismatches` | Mismatch report (completed-on-Shopify-but-not-in-OMS) |
| `POST` | `/api/v1/reconciliation/repair` | Trigger the cross-system repair job on demand |
| `GET` | `/api/v1/reconciliation/orders/{orderId}` | Reconciliation history for one order |

### Frontend — React

New page `FulfillmentReconciliationPage.tsx`:
- **Mismatch report table** — order, reason (ship-group mismatch / facility change / partial), status, "Repair" action
- **Order detail drawer** — reconciliation history, fulfilled vs remaining items, NetSuite sync status
- **Repair confirmation** — before applying a cross-system repair, show the inventory delta

### Acceptance test

1. Order with 2 items routed to a store; store fulfills **1 item**. OMS marks the fulfilled item, keeps the other open, syncs the partial to NetSuite (QH reduced correctly). → `matchType=PARTIAL`, `resolution=PARTIAL_ACCEPTED`.
2. Order fulfilled at a **different facility** than routed. OMS accepts as truth, re-ship-groups, syncs. → `matchType=LOCATION_CHANGE`, `resolution=ACCEPTED`.
3. Order fulfilled in Shopify but not in OMS. Repair job detects it, accepts, adjusts QH, syncs NetSuite. → `resolution=REPAIRED`.
4. No order is left in a stuck state; inventory stays consistent across Shopify/OMS/NetSuite.
5. Idempotency: re-posting the same `shopify_fulfillment_id` does not double-apply.

### Notes
- **This is the #1 integration pain** — fixing it is a direct market win and serves Nexus's "honest data" value.
- **Idempotency is critical** — key the reconciliation ledger on `shopify_fulfillment_id` to prevent double-application on webhook retries.
- **NetSuite sync** should reuse the existing integration hub (`com.nexus.oms.integration`) rather than a bespoke client.

---

## T-12: Carrier Shipping-Label Integration (Jitsu/SAPI, ZPL/PDF) — **P0**

### Problem (from transcript `soc-hotwax.json`)

SoC is integrating a new carrier (Jitsu) for shipping labels with a **hard peak-season deadline** (integration ready Sept 11, test shipments Sept 14, full volume Oct 28). Work covers label format (ZPL/PDF), carrier API integration, and label generation in the pick/pack flow. A known issue: some required fields weren't showing on the last implementation.

**Current state:** Nexus's `ShippingLabelService` generates **SIMULATED** labels locally (no real carrier API call). The `NxShippingLabel` entity already has `label_source` (SIMULATED/CARRIER), `label_base64`, `label_url`, carrier/service/tracking fields — so this ticket adds the **real carrier call** behind the existing abstraction.

### Design

A **pluggable carrier-label adapter** — the key abstraction. Adding any carrier (Jitsu, SAPI, UPS, FedEx) becomes a config change, not a code change.

```
CarrierLabelAdapter (interface)
├── createShipment(request) -> ShipmentResponse   // create shipment in carrier, get label
├── getLabelFormat() -> "ZPL" | "PDF"             // what the carrier returns
└── validateLabel(label) -> List<String>          // required-field validation

JitsuCarrierAdapter    // new — Jitsu API (JSON schema)
SapiCarrierAdapter     // new — custom SAPI label (production)
VhoCarrierAdapter      // existing VHO integration pattern
SimulatedCarrierAdapter // existing local SIMULATED path (fallback / dev)
```

### Flyway migration — `V62__carrier_label_adapter.sql`

```sql
-- T-12: Carrier label adapter config.
-- Per-tenant carrier label settings: which adapter, label format, required fields.

CREATE TABLE nx_carrier_label_config (
    id              UUID PRIMARY KEY,
    tenant_id       UUID NOT NULL,
    carrier_code    VARCHAR(32) NOT NULL,   -- JITSU, SAPI, VHO, FEDEX, UPS...
    adapter_name    VARCHAR(64) NOT NULL,   -- JitsuCarrierAdapter, SapiCarrierAdapter...
    label_format    VARCHAR(8)  NOT NULL DEFAULT 'PDF',  -- ZPL | PDF
    -- JSON: required fields that must appear on the label (ship-from, ship-to,
    -- service, tracking, weight, dimensions). Validated before print.
    required_fields JSONB,
    is_active       BOOLEAN NOT NULL DEFAULT TRUE,
    created_at      TIMESTAMP NOT NULL DEFAULT now(),
    updated_at      TIMESTAMP NOT NULL DEFAULT now(),
    UNIQUE (tenant_id, carrier_code)
);

-- Extend shipping labels with the adapter + format used to produce them.
ALTER TABLE nx_shipping_labels ADD COLUMN IF NOT EXISTS label_format VARCHAR(8) DEFAULT 'PDF';
ALTER TABLE nx_shipping_labels ADD COLUMN IF NOT EXISTS adapter_name VARCHAR(64);
```

### Backend — new adapter layer

Package: `com.nexus.oms.integration.carrier`

```java
public interface CarrierLabelAdapter {
    String getName();                                   // "JitsuCarrierAdapter"
    String getLabelFormat();                            // "ZPL" | "PDF"
    CarrierShipmentResponse createShipment(CarrierShipmentRequest req);
    List<String> validateLabel(ShippingLabel label);    // missing required fields
}

@Component
public class JitsuCarrierAdapter implements CarrierLabelAdapter {
    // POST to Jitsu API with the carrier's JSON schema
    // returns tracking number + label (ZPL or PDF, base64)
    // on success: labelSource = CARRIER
}

@Component
public class SapiCarrierAdapter implements CarrierLabelAdapter {
    // Custom SAPI label for production (like existing VHO integration)
    // needs SAPI endpoints + details (config-driven)
}

@Component
public class SimulatedCarrierAdapter implements CarrierLabelAdapter {
    // Existing local SIMULATED path — fallback for dev / no carrier configured
}
```

### Backend — extend `ShippingLabelService`

Add a method that routes label generation through the configured adapter:

```java
@Transactional
public NxShippingLabel generateCarrierLabel(NxShippingLabel label) {
    // 1. Look up nx_carrier_label_config for (tenant, carrier)
    // 2. Resolve the CarrierLabelAdapter bean by adapter_name
    // 3. Build CarrierShipmentRequest from label (ship-from, ship-to, service, weight, dims)
    // 4. adapter.createShipment(req) -> tracking + label bytes
    // 5. Set labelSource = CARRIER, labelFormat, adapterName, labelBase64/labelUrl
    // 6. adapter.validateLabel(label) -> if missing required fields, log + flag
    // 7. Save + emit Kafka event order.label_generated
}
```

### Backend — REST endpoints (`ShippingLabelController` additions)

| Method | Path | Purpose |
|--------|------|---------|
| `POST` | `/api/v1/labels/carrier` | Generate a real carrier label via the configured adapter |
| `GET` | `/api/v1/labels/{id}/download` | Download label (ZPL or PDF) |
| `GET` | `/api/v1/labels/{id}/validate` | Run required-field validation, return missing fields |
| `GET/POST` | `/api/v1/carrier-label-config` | CRUD for `nx_carrier_label_config` |

### Frontend — extend `LabelPrintingPage.tsx`

- **Carrier selection** — pick the carrier/adapter (Jitsu, SAPI, VHO, Simulated)
- **Label preview** — render ZPL or PDF preview before print
- **Field validation** — show missing required fields (the known issue from the transcript)
- **Print** — trigger print of the ZPL/PDF label

### Acceptance test

1. A pick wave is created for an order with the Jitsu carrier. The system calls the Jitsu API, gets a real label (ZPL/PDF), stores it with `labelSource=CARRIER`, and prints correctly in the UI.
2. All required fields (ship-from, ship-to, service, tracking, weight, dimensions) appear on the label — validation passes.
3. The same flow works with the SAPI custom label on production.
4. A **50–100 order UAT** confirms labels are correct at volume (Sept 14 test → Oct 28 full volume).
5. Adding a new carrier (UPS, FedEx) is a config change (new `nx_carrier_label_config` row + adapter bean), not a code change.

### Notes
- **P0 because of the hard deadline** — SoC's peak season is time-boxed; label correctness at volume is non-negotiable.
- **Pluggable adapter is the market win** — every OMS account needs carrier label integration; a clean adapter makes it repeatable.
- **Reuse the existing `NxShippingLabel` entity** — it already has `label_source`, `label_base64`, `label_url`, carrier/service/tracking. This ticket adds the real carrier call behind it.
- **ZPL rendering** — the UI must render ZPL data to a printable label (a ZPL→image/PDF renderer or a print driver).

---

## Sequencing & Dependencies

| Order | Ticket | Why this order |
|-------|--------|----------------|
| 1 | **T-12** (carrier labels) | Hardest deadline (Sept 11/14/Oct 28). Label correctness at volume is non-negotiable for peak. |
| 2 | **T-09** (fulfillment reconciliation) | #1 integration pain; data integrity affects every live account. No hard date but highest recurring cost. |
| 3 | T-13 (bidirectional sync) | Builds on T-09's reconciliation ledger — same "truth" principle, extends to conflict handling. |
| 4 | T-08, T-10, T-11 | P1 differentiators, no deadline pressure. |

**Shared infrastructure:** both T-09 and T-12 emit Kafka events (`order.fulfilled`, `order.label_generated`) and use the integration hub. Build the integration-hub carrier/reconciliation abstractions first so both tickets share them.

---

## Definition of Done (per ticket)

- [ ] Flyway migration (V61 for T-09, V62 for T-12) applied cleanly on a fresh DB
- [ ] Backend service + controller with tenant-scoped RBAC (no cross-tenant leakage)
- [ ] Kafka event emitted on the key state change
- [ ] React UI page/extension with the workflow
- [ ] Acceptance tests pass (the scenarios above)
- [ ] Idempotency verified (T-09: re-post same fulfillment; T-12: re-generate same label)
- [ ] Peak-volume UAT documented (T-12: 50–100 orders)
