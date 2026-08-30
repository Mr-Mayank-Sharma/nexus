# Workflow Mappings — Real-Market Operations → Nexus

Concrete operational workflows observed in the meetings, mapped to how Nexus would (or should) handle them. Each workflow names the source account and the Nexus touchpoints.

---

## 1. BOPIS (Buy Online, Pick Up In Store) — Lovers

**Real-market flow (from kickoff):**
1. Shopify imports only BOPIS + DoorDash orders into OMS (filtered by tag / sales channel).
2. Store sees both order types on **one unified screen**.
3. Store picks with product name, size, color, SKU visible.
4. BOPIS can be **partially fulfilled**.
5. Customer receipt printed (Epson mobile printer) — possibly replacing the pick ticket.

**Nexus mapping:**
- Import engine filters by tag/sales-channel → needs a Shopify connector with selective import
- `/orders/**` holds the order; needs a `fulfillmentType` (BOPIS) and store-location field
- No store screen exists → new store-ops UI
- No printing subsystem → new

---

## 2. DoorDash On-Demand Fulfillment — Lovers

**Real-market flow:**
1. DoorDash orders flow through Shopify (HotWax does NOT connect to DoorDash directly).
2. Cancellation is **all-or-nothing** (no partial).
3. Cancellation can originate in HotWax, DoorDash (dasher), or Shopify — all must stay in sync via two-way sync.
4. P1: DoorDash orders sorted above BOPIS for priority picking.

**Nexus mapping:**
- New `on-demand` order type with all-or-nothing cancellation semantics
- Two-way cancellation sync (webhooks + API)
- Priority-sort in store screen

---

## 3. Store-Rejected Pickup → Ship-to-Me — gorjana

**Real-market flow:**
1. Customer selects pickup; store rejects (can't fulfill).
2. Order converts to **ship-to-me** fulfillment.
3. Mapped in OMS as **free expedited shipping** through the warehouse flow.
4. Customer status page + AfterShip handle communication; Shopify pickup comms disabled.

**Nexus mapping:**
- Order needs a fulfillment-type transition (pickup → ship)
- Needs a shipping-method override (free expedited)
- Warehouse brokering picks the fulfillment location → routing engine (P0)
- AfterShip integration for customer comms

---

## 4. Fulfillment Routing with Capacity Fallback — Rails

**Real-market flow:**
1. Routing picks locations with inventory **within capacity**.
2. Fallback overrides the facility order limit to the original location.
3. When Shopify can't reroute a final-location stockout, store teams manually route to ops (until a POS→OMS API or metafield sync lands).
4. International: DHL (EU/UK), UPS Worldwide (USA); Canada prioritizes Ponyride inventory.

**Nexus mapping:**
- Routing engine with capacity + fallback-override rules (P0)
- International location mapping + carrier assignment
- Automated reroute on stockout (closes the manual-ops gap)

---

## 5. Transfer Order Receiving — SIMKHAI

**Real-market flow:**
1. Store receives transfer with shared transfer numbers, scans tracking codes.
2. Supports partial receipts, over-receipts, unexpected items.
3. Discrepancies: store count is accepted, transfer closed in NetSuite, inventory adjusted.
4. Real-time Shopify inventory updates on receipt.

**Nexus mapping:**
- Transfer-order entity (partial of purchase-orders) with receiving workflow
- Discrepancy handling + inventory adjustment
- NetSuite/Shopify sync on receipt

---

## 6. Cycle Counting — SIMKHAI

**Real-market flow:**
1. Assigned counts, concurrent sessions, scanner-optimized.
2. Hard / directed / dynamic counting modes.
3. Manager review, compliance filters, bulk damage adjustments.
4. Variance approval/rejection → sync to NetSuite (item adjustments) + Shopify.
5. Exportable count data; reconcile via internal ID + HotWax ID.

**Nexus mapping:**
- Cycle-count module with count modes + approval workflow
- Variance sync + reconciliation

---

## 7. RFID Serialized Inventory — RFID / SIMKHAI

**Real-market flow:**
1. Store EPC per item in backend; derive UPC for inventory ops.
2. Middleware for Bluetooth readers, dedup, decode/encode, printing.
3. Handheld (receiving/counts), fixed (checkout/monitoring), RFID printers (retag).
4. Serialized tracking preferred over aggregate UPC.
5. Hardest part: **POS selling** — associate EPC with Shopify order items without disrupting checkout (iPad app + Shopify POS extension).

**Nexus mapping:**
- Serialized item tracking (new data model)
- RFID read/dedup ingestion
- Shopify POS integration for checkout

---

## 8. Replenishment — Spoonful of Comfort

**Real-market flow:**
1. System-directed drop-off locations (not worker-selected).
2. Flexible suggestions; any flavor acceptable when inventory is zero at a location.
3. Locked source location → cancel tasks, notify, next job searches unlocked locations.
4. Skip-item reason enforcement.
5. Holiday: filter by ship-by date, ship-after date, days-in-transit (destination-specific).

**Nexus mapping:**
- Replenishment engine (P2)
- Transit-time model (carrier postal-route mapping)
- Holiday release filtering

---

## 9. Bidirectional OFBiz ↔ OMS Sync — Chelan Fresh

**Real-market flow:**
1. end-to-end→OFBiz sync every 5 min; OFBiz→end-to-end async ~seconds; 15-min recovery job.
2. Conflicts: OFBiz changes overwritten when sync lags.
3. Safeguards: stale-version flags, order locks, block edits during sync, show failed-sync errors.
4. Production deploy = reset + resync.

**Nexus mapping:**
- Bidirectional sync framework with conflict handling (P1)
- Sync-status UI on order pages
- Recovery/reconciliation jobs

---

## 10. Production Upgrade with Parallel Validation — Agape

**Real-market flow:**
1. Run upgrade server in parallel; mirror production REST requests into it.
2. Validate orders + changes via replay; later repoint DNS.
3. Coordinate DNS, kiosk endpoints, MQTT, database migration.

**Nexus mapping:**
- Not an OMS feature, but motivates robust deploy/migration tooling + fast import engine
- Highlights import-engine performance as an operational risk

---

## 11. AI-Agent Access via MCP — MCP/GraphQL

**Real-market flow:**
1. Publish REST/GraphQL as MCP tools for AI agents.
2. Query permissions + cost limits; safe, controlled production access.
3. GQL for master data (over REST); SuiteQL for NetSuite; MCP endpoints co-configured.
4. Use cases: order debugging, reconciliation, test validation.
5. Pilot gorjana → expand to Lovers, ADOC, Mephisto.

**Nexus mapping:**
- Expose Nexus as an MCP server (P1 strategic)
- Auth: API-key (internal), OAuth/SSO (client-facing)
