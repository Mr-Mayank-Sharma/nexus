# 29. Cut-over to the core model

Decision 2026-10-22: the old model (`nx_*` tables and the `com.nexus.oms` entity, repository, service and controller
code over them) is removed. Everything is stored in the core model (doc 23, doc 28), one database per client, and the
frontend reads and writes only core APIs. Features the core lacks are ported first; the warehouse-floor pages leave the
OMS for the Nexus WMS (W15). The old data is dev and demo data only; a backup was taken before the first step
(`private/backups/`, not in git).

## How each area moves

For every area, in this order, with the tests green after each step:

1. **Core:** the entities (spec in `scripts/core_model_spec.py`, generated) and a service with the area's rules.
2. **API:** a thin controller under the area's new path; no queries in controllers (doc 28, code style).
3. **Screens:** the frontend pages of the area call the new API; old API calls are removed.
4. **Delete:** the area's old controller, service, repository, entity and its `nx_*` tables (a migration drops them).
5. **Check:** backend and frontend tests, and the pages clicked through in the browser.

## Phases

| Phase | What | Old code it replaces |
|---|---|---|
| A | Remove the warehouse-floor pages (waves, labor, yards, slotting, trailers, RFID, cycle counts, task queues, automation, pickers) from the OMS frontend and backend | `RfController`, `TaskQueueController`, `TwinController`, `WarehouseController` pages, frontend `/labor` `/yards` `/waves` `/slotting` `/automation` `/rfid` `/trailers` `/pickers` |
| B | **Tenancy:** a control database (companies, sign-in index) and one core database per company; every request routed to its company's database; migrations run per database; a new company gets its database | tenant filters, `TenantContext` routing of the old model |
| C | **Identity:** sign-in, users, roles and permissions on the core security model (`UserAccount`, `UserGroup`, `UserPermission`), JWT unchanged for the frontend | `AuthController`, `RbacController`, `NxUser`, `UserRole`, `RolePermission`, `Team` |
| D | **Settings and master data:** company settings, stores, customers, products, locations, carriers | `SettingsController`, `CustomerController`, `ProductController`, `IntegrationStoreController`, `CarrierController`, `CarrierLabelConfigController` |
| E | **Inventory:** stock, receipts, ATP rules, replenishment, cycle counts at stores | `InventoryController`, `InventoryReceiptController`, `ATPController`, `ReplenishmentController` |
| F | **Order intake:** order create, imports (CSV, email parser), channel connectors (Shopify, BigCommerce, NetSuite, Amazon), webhooks, sync, integration platform | `OrderController` (create/read), `ImportHistoryController`, `EmailOrderParsingController`, `ShopifyController`, `ShopifyInventoryController`, `BigCommerceController`, `NetSuiteController`, `ConnectorController`, `WebhookController`, `SyncController`, `IntegrationPlatformController` |
| G | **Order handling:** approvals, parked orders, rejections, release, routing rules and brokering, fulfilment limits, promotions, endless aisle | `OrderApprovalController`, `ParkedOrderController`, `RejectionController`, `OrderReleaseController`, `OrderRoutingController`, `RoutingRuleController`, `BrokeringController`, `FulfillmentLimitController`, `PromotionController`, `EndlessAisleController` |
| H | **Fulfilment:** picking, packing, pickup (BOPIS), shipments, labels, manifests, rate shopping (through the gateway), reconciliation | `PickingController`, `PackingController`, `PackingConfigController`, `PickupController`, `ShipmentController`, `ShipmentControllerEnhanced`, `ShippingLabelController`, `ManifestController`, `RateShoppingController`, `FulfillmentReconciliationController` |
| I | **After the sale:** returns, return finance, transfers | `ReturnController`, `ReturnFinanceController`, `TransferOrderController` |
| J | **Money:** invoicing, billing statements, rate cards, freight audit | `InvoicingController`, `BillingController`, `RateCardController`, `FreightAuditController` |
| K | **Procurement:** suppliers, purchase requests and orders, RFQs, ASNs | `ProcurementController` |
| L | **Platform:** notifications, workflows, documents, reports, analytics, dashboard, client portal, AI (old endpoints onto `/core/ai`), MCP tools, sample data | `NotificationController`, `WorkflowController`, `DocumentController`, `ReportController`, `AnalyticsController`, `DashboardController`, `ClientPortalController`, `AiPlatformController`, `AiController`, `AiAgentController`, `AiChatController`, `DemandForecastController`, `McpController`, `SampleDataController` |
| M | **Remove the old model:** the last `nx_*` tables, `CoreMirror` and `coremigration`, the old Kafka and scheduler code that only fed them | everything left in `com.nexus.oms.entity`, `repository`, old `service` |

Phases D to L each end with their old code deleted, so the old model shrinks with every phase and nothing is kept
"for later". The core mirror keeps the not-yet-moved areas in step until phase M.

## What stays the same

The frontend's look and routes, the JWT sign-in flow seen by the browser, the gateway, and the WMS contract (doc 27).
