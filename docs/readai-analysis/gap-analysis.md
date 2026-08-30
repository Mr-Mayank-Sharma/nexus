# Gap Analysis — Where Real-Market Operations Exceed Nexus OMS

This document details the specific capability gaps revealed by the 11 real-market meetings. Each gap is grounded in a concrete operational scenario from a transcript, then assessed against Nexus.

---

## 1. Fulfillment Routing / Order Brokering (Highest Priority)

**Market evidence (multiple accounts):**
- **gorjana:** "HotWax demonstrated a generic API-based flow covering warehouse brokering, NetSuite fulfillment, OMS synchronization..." — ship-to-me must route through the warehouse flow as free expedited shipping.
- **Rails:** Routing "prioritizes locations within capacity and ultimately allows fallback to the original location by overriding its facility order limit." Shopify cannot natively reroute when the final location can't fulfill.
- **SoC:** Order-routing test JSONs and import instructions were pending; routing must respect ship-by/ship-after dates.
- **RFID:** Jonathan planned a "deeper dive into the HotWax routing engine to support business and operational consulting."

**What the market needs:**
- Location selection by inventory availability + capacity limits
- Fallback / override rules
- Ship-from-store vs. ship-from-warehouse decisions
- Automatic rerouting on rejection or stockout
- Routing by country/international location (Rails, International Routing)

**Nexus status:** ❌ No routing engine. Orders are CRUD'd but not brokered to a fulfillment location.

**Recommendation:** This is the single highest-value build. A routing engine is the defining feature of an OMS vs. a simple order database.

---

## 2. Store Operations (BOPIS / DoorDash / In-Store Fulfillment)

**Market evidence:**
- **Lovers (kickoff):** Import only BOPIS + DoorDash orders; unified store screen; product name/size/color/SKU display; customer receipts on Epson mobile printers; extended/non-expiring store sessions; whole-order DoorDash cancellation sync; P1: DoorDash priority sorting + associate fulfillment tracking.
- **gorjana:** Store-rejected pickup → ship-to-me conversion; store-credit exchanges.

**What the market needs:**
- A store-facing app/screen (not desktop WMS) for picking BOPIS and on-demand orders
- On-demand (DoorDash/Uber) order type with all-or-nothing cancellation
- Customer receipt + pick ticket printing (mobile/Epson)
- Store session management (long-lived logins)
- Associate fulfillment tracking & productivity reporting
- Partial fulfillment for BOPIS, all-or-nothing for DoorDash

**Nexus status:** ❌ No store-ops layer, no on-demand order type, no printing, no associate tracking.

---

## 3. Bidirectional Channel Synchronization with Conflict Handling

**Market evidence:**
- **Chelan:** OFBiz↔end-to-end sync breaks — "OFBiz changes can be overwritten when updates do not synchronize to end-to-end promptly." Discussed stale-version indicators, order locks, sync-in-progress edit blocking, failed-sync error display. A consignment-shipment API was missing entirely.
- **Shopify/NetSuite:** Fulfillment mismatches (changed locations, incomplete routed fulfillment, strict matching), unmarked store fulfillments blocking NetSuite feeds, partial shipments unsupported, location mapping.
- **Rails:** Gross-amount vs. rate mapping; international order-sync mappings.

**What the market needs:**
- Bidirectional sync with **conflict detection** (stale flags, versioning, locks)
- Idempotent, retryable sync jobs with recovery (Chelan's 15-min recovery job)
- Reconciliation reports (fulfillment sync counts: pending/queued/completed)
- Field-level mapping control (gross vs. rate)
- Sync status surfacing on order pages

**Nexus status:** 🟡 Webhooks + Kafka events exist, but no bidirectional sync framework with conflict resolution or reconciliation tooling.

---

## 4. Transfer Orders & Cycle Counting

**Market evidence (SIMKHAI + RFID):**
- Transfer-order receiving: shared transfer numbers, tracking-code scanning, partial receipts, over-receipts, unexpected items, real-time Shopify inventory updates, discrepancy handling.
- Transfer-order fulfillment: partial shipments, carrier labels, tracking codes, pick tickets, store-created transfers.
- Cycle counting: assigned counts, concurrent sessions, scanner workflows, hard/directed/dynamic counts, manager approval, variance approval/rejection, sync to NetSuite + Shopify.
- RFID: serialized EPC tracking, dedup of high-volume scans, middleware layer, handheld/fixed readers, RFID printers.

**Nexus status:** 🟡 Purchase-order entity + inventory adjustments exist, but no store transfer-order workflow, no cycle-count module, no serialized/RFID tracking.

---

## 5. Replenishment & Availability (ATP)

**Market evidence (SoC):**
- System-directed replenishment drop-off locations (vs. worker-selected)
- Flexible SKU-to-location suggestions; any flavor accepted when inventory is zero
- Locked-source-location handling: cancel tasks, notify, search unlocked locations
- Skip-item reason enforcement
- Zero-online-ATP investigation

**Nexus status:** ❌ No replenishment engine, no ATP calculation surfaced.

---

## 6. Returns / Exchanges Complexity

**Market evidence (gorjana):**
- Exchanges producing **multiple outcomes within a single claim** (split transactions while retaining the HotWax sales-return identifier)
- Store-credit exchanges; payment/customer-deposit balancing; warranties; variances
- NetSuite discount items for exchange price differences; tax differences across locations
- Missing inventory reductions for exchange orders (corrected retroactively)

**Nexus status:** 🟡 Returns/RMA exist; multi-outcome claims, store credit, and exchange-specific accounting are gaps.

---

## 7. Production Upgrade / Migration Tooling

**Market evidence (Agape):**
- Safe production upgrade: parallel validation, REST request mirroring, replay-based validation
- DNS cutover coordination, database migration, MQTT config
- BlackBrick import-orders 2–3 min/order vs. ms for direct REST (import performance)
- Knowledge-transfer risk on staff departure

**Nexus status:** 🟡 Not an OMS feature per se, but highlights **import engine performance** and **deployment/migration tooling** as operational concerns.

---

## 8. AI-Agent Integration (MCP / GraphQL) — Strategic Opportunity

**Market evidence (MCP/GraphQL meeting):**
- Publish REST/GraphQL components as **MCP tools** for safe AI-agent access to production systems
- Query permissions + cost limits
- Order debugging, reconciliation, test validation via AI agents
- GQL for master data over REST; SuiteQL for NetSuite; MCP endpoints co-configured
- OAuth/SSO for client-facing access; API-key for internal
- gorjana pilot → then Lovers, ADOC, Mephisto

**Nexus status:** 🟡 Nexus has `/ai/**` predictions but no **MCP server** exposing OMS tools to AI agents. This is a differentiator Nexus could own.

---

## Priority Ranking

| Priority | Gap | Market pull | Nexus effort |
|----------|-----|-------------|--------------|
| P0 | Fulfillment routing / brokering | Very high (all accounts) | High |
| P0 | Store ops (BOPIS/DoorDash/printing) | High (Lovers, gorjana) | High |
| P1 | Bidirectional sync + conflict handling | High (Chelan, Rails, NetSuite) | Medium-High |
| P1 | Transfer orders & cycle counting | Medium-High (SIMKHAI, RFID) | Medium |
| P1 | MCP server for AI-agent access | Emerging (strategic) | Low-Medium |
| P2 | Replenishment & ATP | Medium (SoC) | Medium |
| P2 | Returns/exchange complexity | Medium (gorjana) | Medium |
| P3 | Import engine performance | Low (Agape) | Low |
