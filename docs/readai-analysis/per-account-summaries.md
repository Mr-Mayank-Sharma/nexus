# Per-Account Operational Summaries

One-page operational summary per account, distilled from the Read AI transcripts. Each captures the account's business, the OMS workflows in play, and the specific requirements/pain points that matter for Nexus.

---

## gorjana (Jewelry) — `gorjana-weekly.json`

**Business:** DTC jewelry retailer on Shopify, warehouse + stores.

**OMS workflows in play:**
- Shopify native **Opus** transition, esp. store-rejected pickup → **ship-to-me**
- Generic API-based flow: warehouse brokering, NetSuite fulfillment, OMS sync, cancellations, pickup-location changes
- Ship-to-me mapped as **free expedited shipping** through warehouse flow
- Exchanges/returns with price + tax differences; store-credit exchanges; multi-outcome claims
- Missing exchange-order inventory reductions corrected retroactively (Aug 10–25)

**Key requirements / pain points:**
- Suppress Shopify's automatic fulfillment notification for pickup orders (AfterShip handles comms)
- NetSuite discount-item mapping for exchange price differences
- Cross-location exchange tax handling
- Split outcomes within one HG claim (integration change)
- August transaction quantification to prioritize finance work

**Nexus relevance:** Routing/brokering, ship-to-me conversion, returns/exchange complexity, NetSuite sync.

---

## Spoonful of Comfort (DTC Food) — `soc-hotwax.json`

**Business:** DTC comfort-food brand (soups), seasonal/holiday peaks, warehouse fulfillment.

**OMS workflows in play:**
- Fulfillment improvements; packing-label fix (box change during packing — deadlock/transaction contention)
- Picker-performance reporting (identity, category, location, duration)
- Replenishment drop-off (system-directed locations, flexible SKU-to-location)
- Shipping-label integration (Jitsu); SAPI endpoints
- Holiday order planning: filter by ship-by date, ship-after date, days-in-transit (destination-specific)
- Manual pick waivers + payment creation; no peak-profile change

**Key requirements / pain points:**
- Replenishment "skip item" reason enforcement
- Zero-online-ATP product investigation
- Transit duration varies by destination despite same ground service
- UAT with 50–100 representative orders before peak

**Nexus relevance:** Replenishment, ATP, transit-time model, shipping-label integration, picker analytics.

---

## SIMKHAI (Fashion) — `simkhai-hotwax.json`

**Business:** Fashion brand; NetSuite + Shopify; stores + warehouse.

**OMS workflows in play:**
- Transfer-order receiving (shared numbers, tracking scan, partial/over-receipts, unexpected items, real-time Shopify updates)
- Transfer-order fulfillment (partial, carrier labels, tracking, pick tickets, store-created transfers)
- Customer-order fulfillment (pick waves, shipping-priority filters, multiple packages, rejection reasons, auto-rerouting, configurable carrier accounts)
- Cycle counting (hard/directed/dynamic, concurrent, manager review, variance approval → NetSuite + Shopify)
- Pricing: $750/mo >2,500 orders; $1k implementation; $10k try-and-buy/consignment customization

**Key requirements / pain points:**
- Discrepancy units removed from inventory (not auto-returned to source warehouse)
- Price-point variance filtering unavailable (use COGS + % thresholds instead)
- RFID supported but no full supply-chain RFID implementation yet

**Nexus relevance:** Transfer orders, cycle counting, pick waves, routing, serialized/RFID, pricing model.

---

## Rails (Fashion) — `rails-hotwax.json`

**Business:** Fashion retailer; multi-shop Shopify rollout; NetSuite; international (EU/UK/Canada/Paris/Antwerp/Amsterdam).

**OMS workflows in play:**
- Fulfillment routing: prioritize locations within capacity, fallback overrides facility order limit
- Shopify can't reroute final-location stockout → manual ops routing (until POS→OMS API or metafield sync)
- Store credit treated like gift card (credit-memo line item)
- International: DHL (EU/UK), UPS Worldwide (USA); Canada prioritizes Ponyride inventory
- Cutover = config/access, not deployment; no downtime; US flow unaffected
- Aggregate locations only where multiple locations serve a shop

**Key requirements / pain points:**
- Gross-amount vs. rate mapping for Paris order sync
- Labor Day sale volume (Canada) — moved cutover to Thursday
- Closed locations: Antwerp, UK pop-up; Amsterdam paused

**Nexus relevance:** Multi-location routing, international mappings, store credit, cutover planning.

---

## Chelan Fresh (Produce) — `chelan-weekly.json`

**Business:** Produce grower/packer; OFBiz legacy OMS ↔ end-to-end (HotWax) sync.

**OMS workflows in play:**
- Bidirectional sync: end-to-end→OFBiz every 5 min; OFBiz→end-to-end async ~seconds; 15-min recovery job
- Conflict handling: stale-version flags, order locks, block edits during sync, failed-sync error display
- Production deploy = reset + resync
- Consignment-shipment API missing (overwrote a consigned-shipment change)

**Key requirements / pain points:**
- Order details overwritten when base order not synced (defect)
- FRTTRA freight updates unsupported
- Shipped-order edit questions unresolved

**Nexus relevance:** Bidirectional sync framework, conflict detection, reconciliation, consignment support.

---

## Agape (Automation / 3PL) — `agape-weekly.json`

**Business:** Automation/3PL with BlackBrick POS + MQTT machine integration.

**OMS workflows in play:**
- Safe production upgrade: parallel validation, REST request mirroring, replay-based validation
- DNS cutover, kiosk endpoints, tenant broker data, HTTP/MQTT access, database migration
- BlackBrick import-orders 2–3 min/order vs. ms for direct REST

**Key requirements / pain points:**
- Import-engine performance (2–3 min/order is a blocker)
- Knowledge-transfer risk on staff departure
- DNS as a schedule constraint

**Nexus relevance:** Import performance, deployment/migration tooling, POS integration.

---

## Lovers (Retail) — `lovers-kickoff.json`

**Business:** Retail chain (AllPoint); BOPIS + DoorDash fulfillment; Shopify.

**OMS workflows in play:**
- Import **only** BOPIS + DoorDash orders from Shopify (tag/sales-channel filter)
- Unified store screen for both order types
- P0: product details (name/size/color/SKU), customer receipts (Epson mobile), extended store sessions, whole-order DoorDash cancellation sync
- P1: DoorDash priority sorting, associate fulfillment tracking, cancellation/refund sync
- BOPIS partial fulfillment; DoorDash all-or-nothing
- DoorDash connects via Shopify (no direct HotWax–DoorDash agreement)

**Key requirements / pain points:**
- Receipt printer paper size pending
- Pick ticket vs. combined customer receipt decision
- P0 target: current week; testing early next week

**Nexus relevance:** Store ops (BOPIS/DoorDash), printing, on-demand order type, associate tracking.

---

## International Shopify Routing (Internal) — `international-shopify-routing.json`

**Business context:** Internal HotWax decision on international routing (Berlin, Paris, Canada).

**OMS workflows in play:**
- Keep simple Shopify→NetSuite flow where routing works; avoid extra locations unless needed
- Canada may need capacity for Labour Day sale
- International config may be needed due to inventory-sync problems

**Nexus relevance:** Validates that routing complexity should be added only when required — supports a configurable, not always-on, routing engine.

---

## Shopify Fulfillment / NetSuite Integration (Rails ecosystem) — `shopify-fulfillment-netsuite-integration.json`

**Business context:** Improving Shopify ↔ OMS ↔ NetSuite sync across Rails accounts.

**OMS workflows in play:**
- Partial payments unresolved (pending Rails guidance)
- Rejected orders: POS UI extension + order metafield → order-updated event
- Fulfillment mismatches: changed locations, incomplete routed fulfillment, strict matching
- Partial shipments common but unsupported by Rails app
- Shopify fulfillment as source of truth vs. OMS
- Location mapping: single Shopify warehouse → facility; propagate inventory changes
- Fulfillment status page: pending/queued/completed sync counts
- NetSuite throttling on bulk ops
- Moki AI / Moki GQL / Moki SSO (GraphQL, SSO, MCP API access, chat, production debugging)

**Key requirements / pain points:**
- Quantify Shopify-completed fulfillments missing from OMS
- Unmarked store fulfillments blocking NetSuite feeds + distorting inventory
- Multi-store Shopify sandbox for testing

**Nexus relevance:** Bidirectional sync, fulfillment reconciliation, partial shipments, MCP/AI access (Moki).

---

## MCP / GraphQL / Shopify OMS Integration (Internal) — `mcp-graphql-shopify-oms-integration.json`

**Business context:** Internal design for secure, testable, AI-agent-friendly integration.

**OMS workflows in play:**
- Publish REST/GraphQL as MCP tools for AI agents (order debugging, reconciliation, test validation)
- Query permissions + cost limits
- GQL for master data over REST; SuiteQL for NetSuite; MCP endpoints co-configured
- Shopify sandbox, OMS, receiving app, inventory cycle count
- Auth: API-key (internal), OAuth/SSO (client-facing)
- Pilot gorjana → expand to Lovers, ADOC, Mephisto

**Nexus relevance:** MCP server for AI-agent access is a strategic Nexus opportunity.
