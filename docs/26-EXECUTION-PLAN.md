# 26. Execution plan: workstreams you can fork

Each workstream below is self-contained: what it covers, what to read first, what "done" means, and what it must not
touch. Fork a session per workstream and paste its brief. Order matters where marked "after".

Common rules for every workstream:
- Read first: [doc 23](23-DATA-MODEL-OFBIZ-ALIGNED.md) (data model), [doc 24](24-OMS-FEATURES.md) (features),
  [doc 25](25-AI-ARCHITECTURE.md) (AI), and the agreed decisions: one database per client, OFBiz names, text ids
  from a sequence table, real foreign keys, PostgreSQL and MySQL both supported.
- HotWax reference code lives in `~/Desktop/hotwaxoms/moqui2`. Read only Moqui framework (CC0) and HotWax's
  Apache-2.0 components (`ofbiz-oms-udm`, `oms`, `order-routing`, `shopify-connector`, `netsuite-connector`). Never
  `poorti`, `ai`, `maarg-util`, `gql` or client folders. Learn from it; write Nexus code fresh.
- Customer call material stays anonymised ("Partner A", doc 21). Never put client names in the repository.
- Maven needs `export JAVA_HOME=$(/usr/libexec/java_home -v 17)`. Before pushing run the frontend `lint`,
  `typecheck`, `test -- --run`, `build` and the backend `mvn -B clean verify`. Commit and push to `main` and
  `origin main:audit/benchmark-and-security`.
- Two workstreams never edit the same files at the same time. Workstreams W3 onward run one data-model step at a time.

---

## W1. Split the repository: core, OMS, parked WMS  (first; blocks W3+)

**Status: done 2026-10-21.** WMS code parked in `nexus-wms/` (see its README); entitlements removed; OMS gained a
Locations screen. `nexus-core` is created in W3 together with the new model, rather than moving old classes twice.

Brief: Turn the backend into Maven modules `nexus-core` (shared model: party, product, store, facility, inventory,
shipment, picklist, users, jobs, messages, settings), `nexus-oms` (orders, routing, fulfilment, returns, integrations)
and `nexus-wms` (parked: not in the default build). Move the WMS backend code and its tests into `nexus-wms`:
`AsnService, AutomationEmulatorService, AutomationService, CycleCountService, EdiAutomationService,
EquipmentCallbackService, EquipmentWatchdog, LaborService, PickerService, RfidIngestionService, SlottingService,
TrailerService, WarehouseService, WaveService, YardService` with their controllers, entities, repositories and DTOs.
Move the WMS screens (`AutomationSystems, CycleCount, EdiAutomation, InventoryReceiving, LaborManagement, Pickers,
Rfid, SlottingOptimization, WarehouseDashboard, Warehouse, WavePlanning, YardDock`, the 3D warehouse twin) into
`nexus-wms-frontend`. Remove their routes and menu entries from the OMS. Remove the editions/entitlements switch
from the OMS. Store picking/packing, inventory, transfers and returns stay in the OMS.
Done when: the OMS builds, runs and passes all its tests with no WMS code; every OMS screen still opens; the WMS
module compiles on its own or is clearly marked parked; docs updated.

## W2. Write the OMS-WMS contract  (any time; no code)

**Status: done 2026-10-21:** [doc 27](27-OMS-WMS-CONTRACT.md).

Brief: Write `docs/27-OMS-WMS-CONTRACT.md`: the messages between the OMS and any warehouse system (Nexus WMS, SAP
EWM, Manhattan, a 3PL): fulfilment request, cancellation request and response, shipment confirmation (lines,
packages, tracking), short-pick, stock snapshot and stock change, receipt confirmation, return received. For each:
fields (OFBiz names), who sends it, statuses, idempotency key, ordering, retries, error responses. State ownership:
the WMS owns physical stock in the warehouses it runs; the OMS owns network availability; the OMS reserves at
facility level, the WMS at bin level. Transport: system messages (doc 23 section 3) over HTTPS webhooks and files.
Done when: the document is reviewed and every message has an example payload.

## W3. Data model step 1: foundations  (after W1)

**Status: done 2026-10-21.** 38 framework entities generated from one spec into PostgreSQL, MySQL and JPA
([doc 28](28-CORE-MODEL-GUIDE.md)); sequence ids, status flows, enumerations, settings and field audit; the whole app
starts on PostgreSQL with both schemas. Not done: a real MySQL run (no image; H2 MySQL mode instead), and removing
`tenant_id` from new code is moot until areas move (W4+).

Brief: In `nexus-core`, add the framework entities from doc 23 section 3 (enumeration, status with flows, uom, geo,
sequence_value_item, system_property, user account and groups, service jobs, system messages, data manager,
entity_audit_log, note_data) as a clean baseline schema that runs on PostgreSQL and MySQL 8; id generation from
`sequence_value_item`; an entity-change listener that writes history; seed data for types, statuses and
enumerations; Testcontainers tests on both databases. Remove `tenant_id`, row-level security and the tenant-aware
data source from new code.
Done when: both databases build the schema from empty, tests pass on both, and the existing app still runs.

## W4-W9. Data model steps 2-7  (one at a time, after W3)

Each is one brief, run in order, following doc 23 sections 4-9 and the "Done" rule from W3, plus: the screens of
that area work in the browser on the new tables, and their old tables are no longer written.
- W4 Party and contacts (and sign-in moves to `user_account`).
  **Status 2026-10-21: model, services and customer copy done** (21 entities; `PartyService`, `GeoService`;
  `POST /core-migration/customers` copied 4,934 dev customers, idempotent). **Not done: sign-in on `user_account`** (left
  for a supervised session; a mistake there locks people out) and orders still reference `nx_customers` (W7).
- W5 Product and product store (variants, substitutes, identifications, features, categories, prices, store settings).
  **Status 2026-10-21: model, services and copy done** (24 entities; core `ProductService`, `ProductStoreService`;
  `POST /core-migration/products` copied 4 stores and 6,962 dev products in 15 s, idempotent). Prices are copied in the
  company's default currency because legacy products carry none. Channel product ids in `nx_product_mappings` are not
  copied (their channel is ambiguous); they come with the connector rewrite. Screens still read the legacy tables (W7).
- W6 Facility and inventory (facility merges nodes and warehouses; product_facility; inventory items and details;
  reservations).
  **Status 2026-10-21: model, services and copy done** (23 entities; core `FacilityService`: outside ids, brokering
  groups in order, store links, the daily order limit on `facility_order_count`; core `InventoryService`: receive,
  reserve, release, ship, adjust, count with variance reason, transfers through the `INVENTORY_XFER_STATUS` flow, ATP
  per facility, `product_facility.last_inventory_count` following ATP and `computed_last_inventory_count` = ATP less
  safety stock). `POST /core-migration/stock` copies `nx_nodes` and `nx_warehouses` into facilities (warehouse codes
  kept as ids, daily capacity becomes the order limit) and `nx_inventory` into items, one per lot, with ATP = on hand -
  allocated - reserved. On the dev data: 5 facilities and 6,929 items in 14 s; on hand and available totals match the
  legacy tables exactly. 25 stock rows (12 SKUs, e.g. packaging) have no product in the legacy model and are skipped.
  Screens and routing still read the legacy tables (W7, W9).
- W7 Orders (header, ship groups, items with status, roles, adjustments, payments, identifications; Shopify and
  BigCommerce import write the new model).
  **Status 2026-10-21: model, service and copy done; imports not switched yet** (20 entities; core `OrderService`:
  create with roles, contacts, ship groups, items, adjustments, payments, outside ids and attributes; status changes
  through the flow with an `order_status` row each; approving approves items, cancelling cancels open items and gives
  their stock back; partial item cancel shrinks the reservation; reserve and release through `InventoryService`;
  grand total kept current). `POST /core-migration/orders` copied all 5,224 dev orders (9,617 items) in 54 s; the
  grand totals add up to the legacy totals to the cent (3,702,542.02), and 163 orders whose old total differed from its
  parts carry the difference as a discount or charge adjustment. Findings on the dev data: 3,161 items have SKUs with
  no product (kept with their description, no product); 156 orders have no items (copied with one zero-price line
  saying so) and no customer (140 had an address and got a guest party). Reservations: legacy holds are not
  consistent (1,016 allocated in `nx_inventory`, 259 in `nx_order_allocations` by order and location, 20 on items),
  and the order-level allocations name no item, so none are copied as reservations; the stock copy already keeps held
  stock out of available. **Mirror (2026-10-21, approved):** `CoreMirror` runs every minute and, for each company whose
  orders changed, copies new customers, products, locations, stock items and orders, then brings status and tracking
  changes across to copied orders. Every import (Shopify, BigCommerce, generic, manual) is covered because the mirror
  reads what they wrote; the earlier model stays the system of record and the screens still read it. Checked on the dev
  server: an order created through the API appeared in the core within a minute and followed its cancel. Stock
  quantity changes after the first copy are not mirrored (routing on the core, W9, needs them). Next: screens read the
  core once the two have been compared.
- W8 Fulfilment and returns (picklists, shipments, packages, issuance, receipts, returns).
- W9 Routing (routing groups, rules, conditions, actions, runs; the new brokering query; reallocation and split
  control features from doc 24 section 2).

## W9b. Store network  (after W9)

Brief: Following doc 24 section 4b: cross-store lookup with confidence, reserve at another store, endless aisle
routing, the full transfer lifecycle on `inventory_transfer` and transfer shipments, in transit as its own facility,
receive by scan with tolerance-based settlement, sales credit split (selling and fulfilling store on the order),
and rebalancing suggestions within store clusters.
Done when: a transfer runs end to end in the browser with stock correct at every step, including a short receipt.

## W10. Jobs and messages on the new framework, then remove old tables  (after W9)

Brief: Move all schedulers to `service_job` (with run history and a run lock) and all integration queues to
`system_message`; delete the old tables and code; update docs and counts.

## W11. Nexus Gateway: shipping and customer messages  (after W3; separate service)

Brief: A separate service (own repository folder `nexus-gateway`) that client installations call for rates,
labels, void, return labels, address validation, tracking, and customer emails and events. Credentials per client
and store live only in the gateway. Start with FedEx, UPS and one email provider; one API for all; request log
with timings. The OMS stores only the gateway address and key per product store.
Done when: a test label and a test email go through end to end from the OMS against carrier sandboxes.

## W12. AI foundations  (after W3; parallel with W4+ if it touches only its own files)

Brief: Following doc 25 sections 2 and 4.1: `LlmGateway` (Anthropic Messages API, tools, retries, budgets,
masking, prompt caching), proposals with an approval queue and per-client policy, audit and undo, an evaluation
harness, and the AI scorecard on order replay. Re-point the existing AI order suggestions to it.
Done when: one agent (operations assistant, read-only) works end to end with audit, and the evaluation runs in CI.

## W13. AI features  (after W6 for 13.1; after W9 for the rest)

Briefs, one per session, each following doc 25 section 3:
- 13.1 Phantom stock detection and store reliability score.
- 13.2 Delivery-date prediction and the availability and promise service.
- 13.3 Exception agent and plain-language routing with simulation and approval.
- 13.4 Agent-ready order API (ACP and UCP) and agent-safe post-purchase changes.

## W14. Website  (independent)

Brief: Keep the existing dark, bold design. Update product pages to the OMS feature list (doc 24) and the AI
section (doc 25); make the order film look more realistic and easier to follow; WMS marked as coming later.

## W15. Nexus WMS  (after the OMS is live with clients and the user's warehouse visits)

Brief: Rebuild Nexus WMS on `nexus-core` and the W2 contract, using the parked module only as reference.
