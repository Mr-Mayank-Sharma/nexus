# Nexus OMS — Use Cases

> Complete use-case catalogue across all modules. Each use case states **actor**, **precondition**, **flow**, and **result** — plus a **real-life example** so anyone (even a kid 🧒) can picture it. See [`features/`](./features/) for module deep dives and [`06-BUSINESS-FLOW-RBAC.md`](./06-BUSINESS-FLOW-RBAC.md) for who may act.

**Actors** are the 14 RBAC roles: `ADMIN`, `CEO`, `OPS_MANAGER`, `WAREHOUSE_MANAGER`, `PICKER`, `PACKER`, `LOADER`, `STORE_MANAGER`, `BOPIS_OWNER`, `CUSTOMER_SUPPORT`, `PROCUREMENT_MANAGER`, `FINANCE`, `LOGISTICS_MANAGER`, `VIEWER`.

> 🧒 **What is a "use case"?** A recipe: *who* does *what*, *when*, and *what happens after*. E.g. "A customer (actor) buys a t-shirt (flow) → the t-shirt is reserved (result)."

---

## 1. Commerce & Order Intake

### UC-01 — Place order via channel (Shopify/BigCommerce/Amazon/eBay/Walmart/Magento)
- **Actor:** External customer via channel
- **Pre:** Connector configured in Integration Hub; store active
- **Flow:** Webhook/poll → `IntegrationMessage` → order normalized → `NxOrder` created → routing rules applied → allocation/brokering queue
- **Result:** Order in `NEW`/`PARKED`/`BROKERED` state; confirmation returned to channel
- 🛍️ **Real life:** Mia clicks "Buy" on your Shopify site for a hoodie. Within a second the hoodie appears in Nexus, gets routed to the store that has it, and Shopify shows "Order confirmed."

### UC-02 — Manual order entry
- **Actor:** CUSTOMER_SUPPORT, OPS_MANAGER
- **Flow:** Create order with customer/items/address; validation; save
- **Result:** Order enters same pipeline as channel orders
- ☎️ **Real life:** A customer calls because their online order failed. Support types the order in manually — Nexus treats it exactly like a web order from that point on.

### UC-03 — Bulk import orders
- **Actor:** OPS_MANAGER, ADMIN
- **Pre:** Valid signed `ImportToken`
- **Flow:** CSV/Excel/JSON upload → `GenericImportService` → `ImportHistory`/`ImportRecordLog` → records imported/rejected
- **Result:** Import report with per-record status
- 📄 **Real life:** A brand migrating from spreadsheets uploads 5,000 orders in a CSV. Nexus imports them and gives a report: "4,970 ok, 30 failed (missing SKU)". No mystery.

### UC-04 — Email order ingestion
- **Actor:** CUSTOMER_SUPPORT (configuration), system (processing)
- **Flow:** Inbound email → `EmailOrderParsingService` → structured order → review queue
- **Result:** `NxEmailParsedOrder` created; operator confirms or rejects
- 📧 **Real life:** A wholesale customer emails "order 2 boxes of medium gloves" in their usual format. Nexus reads it, builds a draft order, and Support clicks *Confirm*.

### UC-05 — Park / brokering of unfulfillable orders
- **Actor:** System (automated)
- **Flow:** Routing/ATP check fails → `NxParkedOrder` or `NxBrokeringQueue` → later resolution
- **Result:** Order not silently dropped; exception queue visible to OPS
- ⚠️ **Real life:** You promise delivery but every store is out of stock. Instead of the order vanishing, Nexus parks it in a visible queue: "5 orders waiting for stock." Honest, not lost.

---

## 2. Inventory & Network

### UC-06 — Receive inventory (PO / transfer)
- **Actor:** WAREHOUSE_MANAGER, LOADER
- **Flow:** Receive → `NxInventoryReceipt` → stock added to bin/node → ATP recomputed
- **Result:** `NxInventory.onHand` updated; snapshots recorded
- 📦 **Real life:** 50 boxes of pens arrive. The loader scans them in. Instantly the count says "50 pens on Shelf B" and customers can order them again.

### UC-07 — Check available-to-promise (ATP)
- **Actor:** System, CUSTOMER_SUPPORT, STORE_MANAGER
- **Flow:** `NxATPRules` evaluate reservations + on-hand across nodes → `NxATPSnapshot`
- **Result:** Reliable promise dates for orders/endless aisle
- ✅ **Real life:** Support answers "can I get it by Friday?" from a real answer — not a guess: "14 in stock, 2 reserved, so yes, 12 can ship today."

### UC-08 — Cycle count
- **Actor:** WAREHOUSE_MANAGER
- **Flow:** Count scheduled → `NxCycleCount` → variance vs on-hand → adjustment
- **Result:** Inventory corrected with audit trail
- 🔢 **Real life:** The system says 10 mugs but a worker counts 9. Nexus records the variance, adjusts to 9, and the history shows why.

### UC-09 — Transfer stock between nodes/stores
- **Actor:** WAREHOUSE_MANAGER, STORE_MANAGER
- **Flow:** `NxTransferOrder` from→to node → items → pick/ship/receive cycle
- **Result:** Stock moved; both nodes re-ATP'd
- 🚚 **Real life:** Downtown store has 2 hoodies left, mall store has 30. Nexus creates a transfer: "move 10 to downtown." Downtown customers can promise again.

### UC-10 — Replenishment suggestion
- **Actor:** System (AI/rules)
- **Flow:** `NxReplenishmentRule` → `NxReplenishmentSuggestion` → approval → PO/transfer
- **Result:** Suggested buys surfaced for PROCUREMENT_MANAGER
- 🤖 **Real life:** Nexus notices hoodies sell out every winter and suggests "order 120 more in September." The buyer approves or adjusts.

---

## 3. Fulfillment (Wave → Ship)

### UC-11 — Wave planning
- **Actor:** OPS_MANAGER, WAREHOUSE_MANAGER
- **Pre:** Orders ready; `NxWaveRule` defined (FIFO/priority/zone/ship-by)
- **Flow:** Batch orders → `NxWave` → picklists generated
- **Result:** Picklists in queue; workload known
- 🌊 **Real life:** Instead of 50 workers running randomly, Nexus groups orders by zone so each worker walks one aisle once. "Wave" = one organized sweep.

### UC-12 — Pick items
- **Actor:** PICKER
- **Flow:** Scan picklist → `NxPicklistItem` per bin → confirm quantity
- **Result:** Items staged; picker productivity logged
- 🧺 **Real life:** The scanner says "Bin A4: 2 hoodies." The picker grabs them, scans, done. No printed list, no guesswork.

### UC-13 — Pack orders
- **Actor:** PACKER
- **Flow:** Pick staged → box selection → `NxPackage` → weight/dims → label
- **Result:** Package ready for manifest/shipment
- 📦 **Real life:** The screen suggests "small box, these fit." Packer tapes it, enters weight, Nexus prints the label.

### UC-14 — Load & ship
- **Actor:** LOADER, LOGISTICS_MANAGER
- **Flow:** Packages → `NxShipment` → carrier selection (rate shopping) → `NxShippingLabel` → `NxManifest`
- **Result:** Tracking number + `NxTrackingEvent` feed
- 🚛 **Real life:** 40 packages are grouped into a truck manifest. The truck leaves; customers get tracking numbers automatically.

### UC-15 — Fulfillment exception handling
- **Actor:** WAREHOUSE_MANAGER
- **Flow:** Missing/damaged item → `NxFulfillmentException` → resolve (reallocate/replace/cancel line)
- **Result:** Exception closed; order corrected
- 🔧 **Real life:** The scanner says bin A4 has 1 hoodie but the picklist needs 2. Nexus flags it, and a manager decides: ship 1 now, 1 from another store, or call the customer.

### UC-16 — Pickup order (BOPIS)
- **Actor:** BOPIS_OWNER, STORE_MANAGER
- **Flow:** Order tagged `fulfillmentType=BOPIS` → `NxPickupOrder` → store picks → customer collects → `NxPickupOrderItem` confirmed
- **Result:** Pickup ready notifications + POD
- 🏬 **Real life:** Customer orders online, chooses "pick up at the mall store." The store gets a pickup ticket, picks it, customer gets "ready!" text, signs on pickup.

---

## 4. Shipping, Carriers & Yard

### UC-17 — Rate shopping
- **Actor:** System
- **Flow:** Shipment → compare `NxCarrierRate` across accounts/zones → cheapest/guaranteed → `NxRateShoppingLog`
- **Result:** Carrier + service chosen deterministically
- 🏷️ **Real life:** Nexus asks 3 carriers for the same box. Blue=$8, FastVan=$10, Swift=$7.5. It picks Swift and *logs the comparison* so finance can see why.

### UC-18 — Trailer / yard visibility
- **Actor:** LOGISTICS_MANAGER, LOADER
- **Flow:** Trailer check-in → `NxYardLocation` → events (`NxTrailerEvent`) → dock door → check-out
- **Result:** Yard state real-time; dwell tracked
- 🅿️ **Real life:** 5 trailers parked outside. The screen shows each: "Truck 7 arrived 8:00, dock 3, loading, 65% full." No more "where is that truck?"

### UC-19 — Appointment scheduling
- **Actor:** LOGISTICS_MANAGER
- **Flow:** Slot request → `NxAppointment` linked to door/dock → confirm
- **Result:** Dock scheduling avoided contention
- 🗓️ **Real life:** Carriers book dock slots online. Two trucks never fight for the same door at the same time.

### UC-20 — Freight audit
- **Actor:** FINANCE
- **Flow:** Carrier invoice → `nxFreight_invoices` → lines matched vs rate card → overcharge flagged
- **Result:** Payable amounts verified
- 🧾 **Real life:** FastVan bills you $12 for a zone-2 box that should cost $8. Nexus flags the $4 difference automatically. Finance approves or disputes.

---

## 5. Returns (RMA)

### UC-21 — Create return request
- **Actor:** CUSTOMER_SUPPORT
- **Flow:** Order lookup → `NxReturn` + `NxReturnItem` with condition → RMA number
- **Result:** Return authorized; label/RMA communicated
- 🎁 **Real life:** "I'd like to return the hoodie." Support finds the order, creates a return with an RMA number, and emails a return label.

### UC-22 — Inspect & dispose
- **Actor:** WAREHOUSE_MANAGER
- **Flow:** Receipt → inspection → disposition (restock/destroy/donate) → refund decision
- **Result:** Inventory or write-off posted; `NxRejectionReason` recorded if rejected
- 🔍 **Real life:** The hoodie comes back stained. Inspection says "destroy." Nexus marks it destroyed and tells Finance to issue a partial refund. Recorded, not hidden.

---

## 6. Procurement & Suppliers

### UC-23 — Purchase request → approval → PO
- **Actor:** PROCUREMENT_MANAGER, OPS_MANAGER, APPROVAL RULE
- **Flow:** `NxPurchaseRequest` (+items) → `NxApprovalRule` → approved → `NxPurchaseOrder`
- **Result:** PO issued to `NxSupplier`; receipt planned
- 📝 **Real life:** Nexus suggests 120 hoodies. The request goes to the rule engine: "over $500 needs OPS approval." Approved → PO sent to the supplier automatically.

### UC-24 — RFQ / bidding
- **Actor:** PROCUREMENT_MANAGER
- **Flow:** `NxRfq` → `NxRfqResponse` bids → compare → award → PO
- **Result:** Best-qualified supplier awarded
- ⚖️ **Real life:** You ask 3 fabric suppliers for a price on 500m of cotton. Nexus tables the bids and records which one won and why.

### UC-25 — Supplier management
- **Actor:** PROCUREMENT_MANAGER
- **Flow:** Supplier profile + contacts + contracts maintained
- **Result:** Vendor master current; contract terms referenced by POs
- 🗂️ **Real life:** Supplier "CottonCo" has a contact, a contract ("2% discount over 1000m"), all stored. POs reference it automatically.

---

## 7. Automation & Alerting

### UC-26 — Command warehouse automation
- **Actor:** WAREHOUSE_MANAGER (or scheduled AI)
- **Flow:** `NxAutomationSystem` → `NxAutomationCommand` (conveyor/AGV/ASRS) → execution → `NxAutomationLog`
- **Result:** Command executed; real `elapsed_ms` and `simulated` flag recorded
- 🤖 **Real life:** "Start conveyor, speed 2." The conveyor actually starts, Nexus times it honestly (e.g. 1.2s), and labels the result `simulated:false`. No fake "it took 5 seconds" claims.

### UC-27 — Alerting on thresholds
- **Actor:** System
- **Flow:** `NxAlertRule` evaluates metrics → `NxAutomationAlert` → notification via templates
- **Result:** Stakeholders alerted before exceptions become incidents
- 🚨 **Real life:** "Conveyor stopped twice in an hour" triggers an alert to the manager — before the whole line stalls.

---

## 8. Finance

### UC-28 — Invoice & payments
- **Actor:** FINANCE
- **Flow:** Shipments/orders → `NxInvoice` + `NxInvoiceItem` → payments (Stripe) → credit memos
- **Result:** Ledger-accurate billing; reconciliation-ready
- 💰 **Real life:** Every shipped order becomes a receipt line. Customer pays by card → payment recorded. End of month, totals add up exactly.

### UC-29 — Promotion usage
- **Actor:** System
- **Flow:** `NxPromotion` applied → `NxPromotionUsage` tracked per order
- **Result:** Promotion ROI measurable
- 🎟️ **Real life:** "Buy 5 get 1 free" — Nexus counts every use so marketing knows if the promo made money.

---

## 9. Integrations & EDI

### UC-30 — Configure a store connector
- **Actor:** ADMIN, OPS_MANAGER
- **Flow:** Integration Hub → store → credentials in `CredentialVault` → sync config → health check
- **Result:** Store live; messages flowing via `EventBus`
- 🔌 **Real life:** Mila adds her new TikTok shop: plug in, test, live. No 6-week project.

### UC-31 — EDI partner exchange
- **Actor:** LOGISTICS_MANAGER (retail EDI)
- **Flow:** `NxEdiPartner` → EDI 850/856/810 → `EdiProtocolAdapter` → orders/ASNs/invoices
- **Result:** Trading-partner transactions automated
- 🏢 **Real life:** A big retailer sends EDI 850 (purchase order). Nexus reads it, creates the order, and later sends back the 856 (ship notice) and 810 (invoice).

### UC-32 — Transform & validate integration messages
- **Actor:** ADMIN
- **Flow:** `IntegrationFlow` + steps → transform mappings + validation rules → DLQ on failure
- **Result:** Bad payloads quarantined (`NxIntegrationDlq`) not lost
- 🚮 **Real life:** Amazon sends a malformed order. Nexus quarantines it in the DLQ (not deleted!), so ops can inspect and retry.

---

## 10. AI Platform

### UC-33 — Train a model with real metrics
- **Actor:** ADMIN, OPS_MANAGER
- **Flow:** Dataset → `AiTrainingJob` → train → evaluate → job stores **real** metrics (`metricsSource=REAL`) or marks **`NO_METRICS`** → model version only on success
- **Result:** Honest model registry; no fabricated accuracy
- 🎓 **Real life:** The robot learns from 12 months of real sales. Its report card shows real accuracy. If we have no test data, the card says `NO_METRICS` — we never fake a grade.

### UC-34 — Deterministic rule evaluation
- **Actor:** System
- **Flow:** Order/context → `AiRuleEngineService` computes formula/threshold deterministically from config + real input
- **Result:** Repeatable, auditable decisions
- 🔁 **Real life:** The same order always gets the same risk score (0.62). Run it 100 times — same answer. Auditors can verify any decision.

### UC-35 — Demand forecast & briefing
- **Actor:** CEO, OPS_MANAGER, LOGISTICS_MANAGER
- **Flow:** Signals → forecasting/briefing engine → explanation + drill-downs
- **Result:** Decisions informed by AI + human-readable rationale
- 📊 **Real life:** The briefing says "hoodie demand expected +30% in November, driven by last year's pattern." The CEO drills in and sees the chart — then orders early stock.

### UC-36 — Model experiments & drift monitoring
- **Actor:** ADMIN
- **Flow:** Experiment compare → deploy champion → `DriftDetectionService` monitors → fallback to `AiRuleFallback`
- **Result:** Safe deployment; rules are the floor
- 🧪 **Real life:** Robot v2 beats robot v1 in an experiment → v2 goes live. If reality shifts (everyone switches to crop tops), drift detection switches back to safe rules until retraining.

---

## 11. Import / Export & iPaaS

### UC-37 — Signed bulk import
- **Actor:** OPS_MANAGER, ADMIN
- **Pre:** `ImportTokenService` issues signed token
- **Flow:** Upload → validate → load → audit in `ImportHistory`/`ImportRecordLog`
- **Result:** Importable at scale without exposing raw endpoints
- 🔐 **Real life:** A partner uploads 10,000 rows. The token proves they're allowed and expires after use. The whole load is audited.

### UC-38 — Scheduled export / batch
- **Actor:** System (scheduled)
- **Flow:** `IntegrationExportJob` → `BatchJobService` → delivery (SFTP/API) → sync log
- **Result:** Downstream systems receive data on schedule
- ⏰ **Real life:** Every night at 2am, Nexus ships today's orders to the accounting system. The sync log shows success or retries.

---

## 12. Identity, RBAC & Administration

### UC-39 — User & role management
- **Actor:** ADMIN
- **Flow:** Create user → assign role → role-permissions rows scoped by tenant
- **Result:** Least-privilege access enforceable; 60s permission cache
- 🪪 **Real life:** New picker hired → ADMIN creates the account with the `PICKER` badge. They can scan picking screens but can't open Finance.

### UC-40 — Audit trail
- **Actor:** ADMIN, CEO (read)
- **Flow:** Every sensitive action writes `NxAuditLog`
- **Result:** Forensics and compliance traceability
- 📔 **Real life:** Who changed that order's price? The audit log answers: "User 12, 14:02, changed unit_price 19.99→24.99."

### UC-41 — View-only access
- **Actor:** VIEWER
- **Flow:** Read-only access to dashboards/reports
- **Result:** Stakeholder visibility without write risk
- 👁️ **Real life:** An intern gets a `VIEWER` badge — they can watch the dashboards and learn, but can't touch a single order.

---

## 13. Workflow & Platform

### UC-42 — Configurable workflows
- **Actor:** ADMIN, OPS_MANAGER
- **Flow:** Define `NxWorkflow` + steps → executions for approval/onboarding chains
- **Result:** Process steps enforced without code changes
- 📋 **Real life:** "New store onboarding" = steps 1..5 (create store → connect → test → train staff → go live). Nexus walks each store through it.

---

*Next: [`05-DATA-FLOW.md`](./05-DATA-FLOW.md) — end-to-end data movement, and [`features/`](./features/) for UC/ER/flow per module.*
