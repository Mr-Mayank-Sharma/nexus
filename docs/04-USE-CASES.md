# Nexus OMS — Use Cases

> Complete use-case catalogue across all modules. Each use case states **actor**, **precondition**, **flow**, and **result**. See [`features/`](./features/) for module-specific deep dives and [`06-BUSINESS-FLOW-RBAC.md`](./06-BUSINESS-FLOW-RBAC.md) for who may act.

**Actors** are the 14 RBAC roles: `ADMIN`, `CEO`, `OPS_MANAGER`, `WAREHOUSE_MANAGER`, `PICKER`, `PACKER`, `LOADER`, `STORE_MANAGER`, `BOPIS_OWNER`, `CUSTOMER_SUPPORT`, `PROCUREMENT_MANAGER`, `FINANCE`, `LOGISTICS_MANAGER`, `VIEWER`.

---

## 1. Commerce & Order Intake

### UC-01 — Place order via channel (Shopify/BigCommerce/Amazon/eBay/Walmart/Magento)
- **Actor:** External customer via channel
- **Pre:** Connector configured in Integration Hub; store active
- **Flow:** Webhook/poll → `IntegrationMessage` → order normalized → `NxOrder` created → routing rules applied → allocation/brokering queue
- **Result:** Order in `NEW`/`PARKED`/`BROKERED` state; confirmation returned to channel

### UC-02 — Manual order entry
- **Actor:** CUSTOMER_SUPPORT, OPS_MANAGER
- **Flow:** Create order with customer/items/address; validation; save
- **Result:** Order enters same pipeline as channel orders

### UC-03 — Bulk import orders
- **Actor:** OPS_MANAGER, ADMIN
- **Pre:** Valid signed `ImportToken`
- **Flow:** CSV/Excel/JSON upload → `GenericImportService` → `ImportHistory`/`ImportRecordLog` → records imported/rejected
- **Result:** Import report with per-record status

### UC-04 — Email order ingestion
- **Actor:** CUSTOMER_SUPPORT (configuration), system (processing)
- **Flow:** Inbound email → `EmailOrderParsingService`/`EmailOrderTextParser` → structured order → review queue
- **Result:** `NxEmailParsedOrder` created; operator confirms or rejects

### UC-05 — Park / brokering of unfulfillable orders
- **Actor:** System (automated)
- **Flow:** Routing/ATP check fails → `NxParkedOrder` or `NxBrokeringQueue` → later resolution
- **Result:** Order not silently dropped; exception queue visible to OPS

---

## 2. Inventory & Network

### UC-06 — Receive inventory (PO / transfer)
- **Actor:** WAREHOUSE_MANAGER, LOADER
- **Flow:** Receive → `NxInventoryReceipt` → stock added to bin/node → ATP recomputed
- **Result:** `NxInventory.onHand` updated; snapshots recorded

### UC-07 — Check available-to-promise (ATP)
- **Actor:** System, CUSTOMER_SUPPORT, STORE_MANAGER
- **Flow:** `NxATPRules` evaluate reservations + on-hand across nodes → `NxATPSnapshot`
- **Result:** Reliable promise dates for orders/endless aisle

### UC-08 — Cycle count
- **Actor:** WAREHOUSE_MANAGER
- **Flow:** Count scheduled → `NxCycleCount` → variance vs on-hand → adjustment
- **Result:** Inventory corrected with audit trail

### UC-09 — Transfer stock between nodes/stores
- **Actor:** WAREHOUSE_MANAGER, STORE_MANAGER
- **Flow:** `NxTransferOrder` from→to node → items → pick/ship/receive cycle
- **Result:** Stock moved; both nodes re-ATP'd

### UC-10 — Replenishment suggestion
- **Actor:** System (AI/rules)
- **Flow:** `NxReplenishmentRule` → `NxReplenishmentSuggestion` → approval → PO/transfer
- **Result:** Suggested buys surfaced for PROCUREMENT_MANAGER

---

## 3. Fulfillment (Wave → Ship)

### UC-11 — Wave planning
- **Actor:** OPS_MANAGER, WAREHOUSE_MANAGER
- **Pre:** Orders ready; `NxWaveRule` defined (strategy: FIFO/priority/zone/ship-by)
- **Flow:** Batch orders → `NxWave` → picklists generated
- **Result:** Picklists in queue; workload known

### UC-12 — Pick items
- **Actor:** PICKER
- **Flow:** Scan picklist → `NxPicklistItem` per bin → confirm quantity
- **Result:** Items staged; picker productivity logged

### UC-13 — Pack orders
- **Actor:** PACKER
- **Flow:** Pick staged → box selection → `NxPackage` → weight/dims → label
- **Result:** Package ready for manifest/shipment

### UC-14 — Load & ship
- **Actor:** LOADER, LOGISTICS_MANAGER
- **Flow:** Packages → `NxShipment` → carrier selection (rate shopping) → `NxShippingLabel` → `NxManifest`
- **Result:** Tracking number + `NxTrackingEvent` feed

### UC-15 — Fulfillment exception handling
- **Actor:** WAREHOUSE_MANAGER
- **Flow:** Missing/damaged item → `NxFulfillmentException` → resolve (reallocate/replace/cancel line)
- **Result:** Exception closed; order corrected

### UC-16 — Pickup order (BOPIS)
- **Actor:** BOPIS_OWNER, STORE_MANAGER
- **Flow:** Order tagged `fulfillmentType=BOPIS` → `NxPickupOrder` → store picks → customer collects → `NxPickupOrderItem` confirmed
- **Result:** Pickup ready notifications + POD

---

## 4. Shipping, Carriers & Yard

### UC-17 — Rate shopping
- **Actor:** System
- **Flow:** Shipment → compare `NxCarrierRate` across accounts/zones → cheapest/guaranteed → `NxRateShoppingLog`
- **Result:** Carrier + service chosen deterministically

### UC-18 — Trailer / yard visibility
- **Actor:** LOGISTICS_MANAGER, LOADER
- **Flow:** Trailer check-in → `NxYardLocation` → events (`NxTrailerEvent`) → dock door assignment (`NxDockDoor`) → check-out
- **Result:** Yard state real-time; dwell tracked

### UC-19 — Appointment scheduling
- **Actor:** LOGISTICS_MANAGER
- **Flow:** Slot request → `NxAppointment` linked to door/dock → confirm
- **Result:** Dock scheduling avoided contention

### UC-20 — Freight audit
- **Actor:** FINANCE
- **Flow:** Carrier invoice → `nxFreight_invoices` → lines matched vs rate card (`NxCarrierRate`) → overcharge flagged (`nxFreight_audit_logs`)
- **Result:** Payable amounts verified

---

## 5. Returns (RMA)

### UC-21 — Create return request
- **Actor:** CUSTOMER_SUPPORT
- **Flow:** Order lookup → `NxReturn` + `NxReturnItem` with condition → RMA number
- **Result:** Return authorized; label/RMA communicated

### UC-22 — Inspect & dispose
- **Actor:** WAREHOUSE_MANAGER
- **Flow:** Receipt → inspection → disposition (restock/destroy/donate) → refund decision
- **Result:** Inventory or write-off posted; `NxRejectionReason` recorded if rejected

---

## 6. Procurement & Suppliers

### UC-23 — Purchase request → approval → PO
- **Actor:** PROCUREMENT_MANAGER, OPS_MANAGER, APPROVAL RULE
- **Flow:** `NxPurchaseRequest` (+items) → `NxApprovalRule` → approved → `NxPurchaseOrder`
- **Result:** PO issued to `NxSupplier`; receipt planned

### UC-24 — RFQ / bidding
- **Actor:** PROCUREMENT_MANAGER
- **Flow:** `NxRfq` → `NxRfqResponse` bids → compare → award → PO
- **Result:** Best-qualified supplier awarded

### UC-25 — Supplier management
- **Actor:** PROCUREMENT_MANAGER
- **Flow:** Supplier profile + contacts + contracts maintained
- **Result:** Vendor master current; contract terms referenced by POs

---

## 7. Automation & Alerting

### UC-26 — Command warehouse automation
- **Actor:** WAREHOUSE_MANAGER (or scheduled AI)
- **Flow:** `NxAutomationSystem` → `NxAutomationCommand` (conveyor/AGV/ASRS) → execution → `NxAutomationLog`
- **Result:** Command executed; real `elapsed_ms` and `simulated` flag recorded

### UC-27 — Alerting on thresholds
- **Actor:** System
- **Flow:** `NxAlertRule` evaluates metrics → `NxAutomationAlert` → notification via templates
- **Result:** Stakeholders alerted before exceptions become incidents

---

## 8. Finance

### UC-28 — Invoice & payments
- **Actor:** FINANCE
- **Flow:** Shipments/orders → `NxInvoice` + `NxInvoiceItem` → payments (`NxPayments`, Stripe) → credit memos (`NxCreditMemo`)
- **Result:** Ledger-accurate billing; reconciliation-ready

### UC-29 — Promotion usage
- **Actor:** System
- **Flow:** `NxPromotion` applied → `NxPromotionUsage` tracked per order
- **Result:** Promotion ROI measurable

---

## 9. Integrations & EDI

### UC-30 — Configure a store connector
- **Actor:** ADMIN, OPS_MANAGER
- **Flow:** Integration Hub → store → credentials in `CredentialVault` → sync config → health check
- **Result:** Store live; messages flowing via `EventBus`

### UC-31 — EDI partner exchange
- **Actor:** LOGISTICS_MANAGER (retail EDI)
- **Flow:** `NxEdiPartner` → EDI 850/856/810 docs → `EdiProtocolAdapter` → orders/ASNs/invoices
- **Result:** Trading-partner transactions automated

### UC-32 — Transform & validate integration messages
- **Actor:** ADMIN
- **Flow:** `IntegrationFlow` + steps → transform mappings + validation rules → DLQ on failure
- **Result:** Bad payloads quarantined (`NxIntegrationDlq`) not lost

---

## 10. AI Platform

### UC-33 — Train a model with real metrics
- **Actor:** ADMIN, OPS_MANAGER
- **Flow:** Dataset → `AiTrainingJob` → train → evaluate → job stores **real** metrics (`metricsSource=REAL`) or marks `NO_METRICS` → model version only on success
- **Result:** Honest model registry; no fabricated accuracy

### UC-34 — Deterministic rule evaluation
- **Actor:** System
- **Flow:** Order/context → `AiRuleEngineService` computes formula/threshold deterministically from config + real input
- **Result:** Repeatable, auditable decisions

### UC-35 — Demand forecast & briefing
- **Actor:** CEO, OPS_MANAGER, LOGISTICS_MANAGER
- **Flow:** Signals → forecasting/briefing engine → explanation + drill-downs
- **Result:** Decisions informed by AI + human-readable rationale

### UC-36 — Model experiments & drift monitoring
- **Actor:** ADMIN
- **Flow:** Experiment compare → deploy champion → `DriftDetectionService` monitors → fallback to `AiRuleFallback`
- **Result:** Safe deployment; rules are the floor

---

## 11. Import / Export & iPaaS

### UC-37 — Signed bulk import
- **Actor:** OPS_MANAGER, ADMIN
- **Pre:** `ImportTokenService` issues signed token
- **Flow:** Upload → validate → load → audit in `ImportHistory`/`ImportRecordLog`
- **Result:** Importable at scale without exposing raw endpoints

### UC-38 — Scheduled export / batch
- **Actor:** System (scheduled)
- **Flow:** `IntegrationExportJob` → `BatchJobService` → delivery (SFTP/API) → sync log
- **Result:** Downstream systems receive data on schedule

---

## 12. Identity, RBAC & Administration

### UC-39 — User & role management
- **Actor:** ADMIN
- **Flow:** Create user → assign role → role-permissions rows scoped by tenant
- **Result:** Least-privilege access enforceable; 60s permission cache

### UC-40 — Audit trail
- **Actor:** ADMIN, CEO (read)
- **Flow:** Every sensitive action writes `NxAuditLog`
- **Result:** Forensics and compliance traceability

### UC-41 — View-only access
- **Actor:** VIEWER
- **Flow:** Read-only access to dashboards/reports
- **Result:** Stakeholder visibility without write risk

---

## 13. Workflow & Platform

### UC-42 — Configurable workflows
- **Actor:** ADMIN, OPS_MANAGER
- **Flow:** Define `NxWorkflow` + steps → executions for approval/onboarding chains
- **Result:** Process steps enforced without code changes

---

*Next: [`05-DATA-FLOW.md`](./05-DATA-FLOW.md) — end-to-end data movement, and [`features/`](./features/) for UC/ER/flow per module.*
