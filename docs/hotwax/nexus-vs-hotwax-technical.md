# Nexus OMS vs. HotWax Commerce — Technical Architecture Comparison

Source-grounded comparison of the HotWax Commerce OMS (Moqui-based, `hotwaxoms/moqui2/`) against Nexus OMS (Spring Boot 3 + React). Every HotWax claim below is verified against the actual source in the HotWax repo; Nexus claims are verified against `nexus-p0/`.

Companion to `nexus-vs-hotwax-competitive.md` (strategic positioning). This document is the technical evidence base.

---

## 1. Architecture Paradigm

| Dimension | HotWax (Moqui) | Nexus |
|---|---|---|
| Runtime | Moqui framework (Groovy + XML) on Java | Spring Boot 3, Java 17 |
| Service layer | XML service definitions (`<service verb="X" noun="Y">`) executed by framework runners | Java `@Service` classes invoked by `@RestController`s |
| Business logic | Groovy scripts + `<actions>` XML blocks | Java methods |
| REST exposure | Declarative `*.rest.xml` mapping services/entities to HTTP verbs | Explicit `@GetMapping`/`@PostMapping` annotations |
| UI | XML screens (`screen/Oms/Order/FindOrder.xml`) rendered by Moqui ScreenFacade | React 19 + Vite + TypeScript + Tailwind (93 pages) |
| Schema | Auto-created at startup from entity definitions (`EntityDbMeta.groovy`) | 57 Flyway migrations |
| Data access | `EntityFacade`/`EntityFind`/`EntityValue` (dynamic, no compile-time model) | JPA repositories + DTOs |

**Key structural difference:** HotWax is a *declarative framework* — services, REST endpoints, screens, and even the database schema are all generated from XML/Groovy definitions. Nexus is a *conventional codebase* — everything is explicit Java/TypeScript.

### Moqui service execution (verified)

- `framework/src/main/groovy/org/moqui/impl/service/runner/`:
  - `EntityAutoServiceRunner.groovy` — auto-generates CRUD services for any entity: verbs `create`, `update`, `delete`, `store`. Skips fields `ec`, `_entity`, `authUsername`, `authPassword`.
  - `ScriptServiceRunner.java` — runs Groovy script services.
  - `JavaServiceRunner.groovy` — runs Java-implemented services.
  - `InlineServiceRunner.java` — runs inline `<actions>` XML.
  - `RemoteRestServiceRunner.groovy` / `RemoteJsonRpcServiceRunner.groovy` — call remote services.
- `ServiceRunner.groovy` orchestrates: auth, transaction, validation, and dispatch to the right runner.

**Implication for Nexus:** HotWax gets CRUD endpoints for *every* entity for free (via `EntityAutoServiceRunner` + `rest.xml`). Nexus must write each controller/repository explicitly. This is a HotWax velocity advantage for internal tooling, but it also means HotWax's API surface is implicit and inconsistent — every endpoint needs manual review to know what it actually does. Nexus's explicit controllers are self-documenting and auditable.

### Schema management (verified)

- `framework/src/main/groovy/org/moqui/impl/entity/EntityDbMeta.groovy`:
  - `createTable()` builds `CREATE TABLE` DDL from entity definitions (~line 466).
  - Also emits Liquibase changeSets (~line 1203) so schema changes are tracked.
- Entities live in `runtime/component/*/entity/*.xml` (e.g. `ofbiz-oms-udm/entity/DatamodelOrderEntitymodel.xml`).

**Implication:** HotWax has no migration files to review — schema is derived from entity XML at startup. Nexus's 57 Flyway migrations are explicit, reviewable, and rollback-aware. For a sales pitch: Nexus migrations are auditable; HotWax schema changes are implicit and can drift between environments.

---

## 2. Reconciliation API — the sharpest comparison

HotWax's Darpan reconciliation is the cleanest, most deliberate piece of the codebase. It was rebuilt (2026-08-03 requirements) from an async export-job + SFTP-file transport to a **direct, paginated read**. This is the contract Nexus must match or beat.

### `get ReconciliationOrders` (verified — `oms/service/co/hotwax/oms/recon/OrderReconServices.xml`)

**Inputs:**
- `orderDateFrom` (Timestamp, required) — window start, **inclusive**
- `orderDateThru` (Timestamp, required) — window end, **EXCLUSIVE**
- `externalId` (List, optional) — batch point lookup
- `pageIndex` (Integer, default 0), `pageSize` (Integer, default 200)

**Outputs:**
- `orders` — `orderId, externalId, orderName, grandTotal, orderDate (epoch ms), statusId, isExchange, originalOrderId, originalExternalId`
- `ordersCount` — total across all pages (consumer told when to stop)
- `hasMore`, `pageIndex`, `pageSize`
- `exchangeOrderCount` — exchange orders in whole window (subset of ordersCount)
- `excludedNonSalesOrderCount` — non-sales orders in window, excluded from response
- `missingExternalIdCount` — delivered orders with null/empty externalId

**Semantics (verified):**
- Half-open window `[from, thru)` with explicit `>= from` / `< thru` Timestamp conditions. Deliberately NOT `<search-form-inputs/>` because `_thru` suffix is inclusive and would double-count boundary orders.
- **`externalId` is NOT unique in the response.** Exchange orders repeat their original's externalId (assigned in `hotwax-shopify-oms-bridge soblegacy/order/ReturnServices.xml:860,868`). Consumers MUST partition on `isExchange` BEFORE joining on `externalId`.
- Exchange orders are **delivered, not excluded**, each flagged `isExchange: Y/N`. A "Y" record carries `originalOrderId` + `originalExternalId` (read from the original order itself, which always carries the clean Shopify order id).
- Deterministic ordering: `ORDER BY orderDate, orderId` — a record is returned exactly once across the page sequence.
- Exchange lookup is scoped to the current page (two bounded reads per request), not the whole window.
- If an exchange order references more than one original, the first is kept and a warning is logged (not silently dropped).

**Errors (machine-distinguishable):**
- `INVALID_WINDOW` — `orderDateThru <= orderDateFrom`
- `PAGE_SIZE_TOO_LARGE` — states the maximum; over-max requests are **rejected, never silently capped**. Max from `darpan.recon.max.page.size` (default 1000, set in `oms/MoquiConf.xml`).

### `get ReconciliationReturns` (verified — `ReturnReconServices.xml`)

Same shape, keyed on `returnDateFrom`/`returnDateThru`, plus:
- `shopifyReturnId` (List, optional) — batch lookup
- Outputs: `returns, returnsCount, hasMore, pageIndex, pageSize, excludedNoShopifyRefCount`
- Same errors: `INVALID_WINDOW`, `PAGE_SIZE_TOO_LARGE`

### Nexus counterpart (verified — `SyncController.java`, `SyncReconciliationReport.java`)

Nexus has `GET /sync/reconciliation` returning `SyncReconciliationReport`:
- `directions[]` — per direction (INBOUND/OUTBOUND): `pending, queued, completed, failed`
- `openConflicts`, `resolvedConflicts`

**This is a different animal.** Nexus reports *sync job status counts*; HotWax serves *the actual order/return records* for external comparison. They answer different questions:
- HotWax: "Here are the orders in this window — compare them against Shopify yourself."
- Nexus: "Here's how my sync jobs are doing."

**Gap / opportunity:** Nexus has no equivalent of `get ReconciliationOrders`/`get ReconciliationReturns` — a paginated, windowed, exchange-aware read of order records for external reconciliation. If a prospect's reconciliation pipeline expects the HotWax contract (Darpan-style), Nexus needs a compatible endpoint. The HotWax contract is a good spec to copy: half-open windows, `isExchange` flag, `originalOrderId`/`originalExternalId`, count fields for excluded/missing, machine-distinguishable errors, deterministic ordering.

---

## 3. Order Routing (DOM)

### HotWax `order-routing` component (verified)

Services (`OrderRoutingServices.xml`, `CommonServices.xml`, `InventoryReservationServices.xml`):
- `run OrderRoutingGroup` (transaction-timeout 36000), `run OrderRouting`, `run OrderRoutingRule`
- `scheduleNow OrderRoutingGroup`, `store/get OrderRoutingGroupSchedule`
- `eval OrderRoutingActions`
- `get RoutingOrderCount`, `get OrderFilterSql`, `get OrderRoutingRuleSql`
- `calculate ItemSubtotal`, `get SuggestedShippingMethod`
- `reject OrderItems`, `get RecentRoutingHistory`
- `clean OrderRoutingRun`
- `clone`/`delete` for OrderRoutingGroup/OrderRouting/OrderRoutingRule

REST surface (`orderrouting.rest.xml`, `rest-api-3.xsd`):
- `/groups` — GET list, POST store; `/groups/{routingGroupId}` — GET one, POST store, DELETE
- `/groups/{id}/runNow` (POST), `/groups/{id}/run` (POST, test single order), `/groups/{id}/schedule` (GET/POST), `/groups/{id}/clone` (POST)
- `/groups/{id}/routings`, `/groups/{id}/routingRuns`, `/groups/{id}/raw`
- `/routings` — POST store; `/routings/{id}` GET/POST; `/routings/{id}/rules`, `/orderFilters`, `/routingRuns`, `/clone`, `/sql`, `/orderCount`
- `/rules` — POST store; `/rules/{id}` GET/POST; `/rules/{id}/inventoryFilters`, `/actions`, `/clone`, `/sql`
- `/orders/{orderId}/reject` (POST), `/orders/{orderId}/routing-history/recent` (GET)
- `/categories/{productStoreId}` — GET catalog category rollup

Entity model: `OrderRoutingGroup`, `OrderRouting`, `OrderRoutingRule`, `OrderRoutingRuleInvCond`, `OrderRoutingRuleAction`, `OrderFilterCondition`, `OrderRoutingRun` (all `co.hotwax.order.routing.*`).

### Nexus counterpart (verified)

- `OrderRoutingController.java`, `RoutingRuleController.java`, `BrokeringController.java`
- Frontend: `OrderRoutingPage.tsx`, `RoutingRulePage.tsx`

**Assessment:** Concept-level parity only (groups, rules, conditions, actions, runs, reject) — **and that is where the comparison stops.** HotWax's routing is a *scheduled batch* engine (`run OrderRoutingGroup` with 10-hour transaction timeouts, `scheduleNow`, `clean OrderRoutingRun`); Nexus's is request-driven. HotWax's `get OrderFilterSql` / `get OrderRoutingRuleSql` — exposing the generated SQL for a routing rule — is a distinctive debugging feature worth noting.

⚠ **Nexus is behind on allocation, not ahead.** A line-level read of `OrderRoutingService.java` (see [`nexus-order-routing-comparison.md`](./nexus-order-routing-comparison.md)) found that HotWax's allocator is **stock-aware SQL** with facility caps, substitute-item resolution, shipment-group brokering, and distance ranking, while Nexus's allocation path has **live correctness defects** (over-allocation; a fallback that selects a warehouse that cannot fulfill) and lacks that depth. This is a **P0 blocker** — T-17 (correctness) and T-14 (allocator depth) must land before routing is demo-ready; T-18 (rule expressiveness) is P1. Do not lead with Nexus routing.

*Note: the Shopify "won't reroute when the last location holds the inventory" constraint is a **Shopify** limitation HotWax works around — not a HotWax routing gap, and not a Nexus win.*

---

## 4. Carrier Labels — a real but *unfinished* Nexus area

### HotWax (verified — core `oms/service/`, four files)

**Core `oms` is genuinely thin:** it defines **zero** services named `RateShopping`, `ShippingLabel`, `ShippingRates`, `Manifest`, or `ReturnLabel` anywhere under `oms/service/`. What it has on the carrier side is master data plus gateway plumbing — exactly **three** carrier services: `create#Carrier` (Party + PartyGroup + PartyRole `CARRIER`, and the only service in `co/hotwax/oms/carrier/CarrierServices.xml`), `get#CarrierDetails` (`co/hotwax/orderledger/email/EmailServices.xml`), and `update#ShippingMethod` (`co/hotwax/oms/order/OrderServices.xml`). No rating, no label services. (Core also owns `send#UnigateRequest` in `co/hotwax/unigate/UnigateServices.xml` — generic gateway transport, not a shipping lifecycle.)

**But label generation is real, and it is first-party — it lives in `poorti`, not core `oms` and not a customer component.** Do not conclude "HotWax has no label story" from the core component alone:
- A tree-wide search for `RateShopping|ShippingLabel|ShippingRates|Manifest|ReturnLabel` returns hits in **exactly one component: `poorti`** — 4 files, no customer components involved
- Full lifecycle in `poorti/service/co/hotwax/poorti/shipping/ShippingServices.xml`: `do#RateShopping`, `get#ShippingRates`, `get#ShippingLabel`, `void#ShippingLabel`, `retry#ShippingLabel`, `generate#Manifest`
- The gateway implementation itself is `UnigateShippingServices.xml` (1,887 lines) plus `OmsRestShippingServices.xml`
- Print artifacts over REST: `Label.pdf`, `ReturnLabel.pdf`, `PackingSlipAndLabel.pdf`, `Manifest.pdf`, plus `carrierConfigs` and `carrierManifests`
- Dispatch is by config-string (`PoortiHelperServices.get#ConfiguredCarrierService`), resolving `ShipmentRequest` → `SystemProperty`, then calling the resolved service by name
- Credentials live on the gateway (`ShippingCarrierConfig` stores only a `gatewayAuthId`); the credential registry (`ShippingGatewayAuth`, `CommGatewayAuth`, tenants) is in core `oms/UnigateServices.xml`; the OMS never calls a carrier API directly

⚠ **The honest framing:** HotWax's carrier story is a **per-deployment integration project**, not a zero-config product feature. The *code* ships in `poorti`, but which gateway runs, and with which credentials, is configured per store/carrier/method/facility. Core ships the data model; the gateway + adapter work is configured and billed per customer. See §12 and [`hotwax-component-architecture-deep-dive.md` §6](./hotwax-component-architecture-deep-dive.md#6-shipping--how-hotwax-actually-buys-labels).

### Nexus (verified)

**Shipped today** (3 files only):
- `NxShippingLabel.java` entity, `ShippingLabelController.java`, `ShippingLabelService.java`
- Frontend: `LabelPrintingPage.tsx`
- Carrier rate-shopping migrations: `V15__carrier_rate_shopping.sql`, `V22__carriers.sql`

**How it actually behaves:**
- `ShippingLabelService.generateLabel` / `generateLabelsForOrder` / `generateBopisLabel` synthesize labels locally
- `labelSource` is **hardcoded to `"SIMULATED"` in all three code paths** — the field is *documented* as `SIMULATED (local) or CARRIER (real connector purchase)`, but **no code path ever sets `CARRIER`**
- `generateTrackingNumber(carrier)` fabricates tracking numbers from a carrier prefix (`FEDEX`→`FX`, `UPS`→`UP`, `USPS`→`US`, `DHL`→`DH`, `LOCAL`→`LC`, else `GN`) — **not carrier-issued numbers**

**But a real carrier integration exists, just not wired to labels:**
- `integration/connector/shipping/FedExConnector.java` extends `BaseApiConnector` and implements `getRates`, `createShipment`, `track`, `pushFulfillments`, `registerWebhooks` — a genuine FedEx REST integration
- It is **not connected** to the label-generation path. There is a designed-but-unimplemented seam at `labelSource`.

**Not yet built** (specified in `nexus-p0-implementation-specs.md` as ticket T-12 / Flyway V61–V62, but **no such files exist in the repo**): `CarrierLabelAdapter`, `JitsuCarrierAdapter`, `SapiCarrierAdapter`, `SimulatedCarrierAdapter`, `CarrierShipmentRequest`/`Response`, `NxCarrierLabelConfig`, `CarrierLabelConfigController`/`Service`/`Repository`, `V62__carrier_label_adapter.sql`.

**Assessment:** ⚠ **Nexus is behind here, not ahead.** HotWax runs the full live label lifecycle in production at multiple customers; Nexus is simulated end to end and has no working carrier connectivity. There is no "opportunity" to claim a win here — this is a **catch-up item**. The only genuine Nexus advantage is *architectural*: a compile-time `CarrierLabelAdapter` interface and injectable service versus HotWax's runtime string dispatch, plus self-hostable credentials (HotWax holds them on the gateway). That is a real but modest differentiator, and it is **planned, not shipped**. Frame it as: *"our abstraction is cleaner and your credentials stay in your database — but we owe you the integration work HotWax already charges for."* Shipping T-12 (wiring real label purchase through `FedExConnector`, flipping `labelSource` to `CARRIER`) is what makes the claim true. See §11, §12, §13.

---

## 5. Shopify Integration

### HotWax `shopify-connector` component (verified)

Service inventory (`service/co/hotwax/sob/`):
- `ShopifyOrderServices.xml` — order sync core
- `ReturnServices.xml` — returns/exchanges (assigns original Shopify order id to exchange orders at lines 860/868)
- `ShopifyWebhookServices.xml` — webhook ingestion
- `FeedServices.xml`, `OrderFeedServices.xml` — feed processing
- `ShopifyOrderMappingServices.xml`, `ShopifyOrderHelperServices.xml`, `ShopifyHelperServices.xml` — mapping/helpers
- `ShopifyOrderSyncHistoryServices.xml` — sync history
- `ShopifyOrderIntegrationSetupServices.xml` — integration setup
- `ShopifyTransferOrderServices.xml` — transfer orders
- `SystemMessageServices.xml` — system messages

### Nexus counterpart (verified)

- `ShopifyController.java`, `WebhookController.java`, `SyncController.java`, `IntegrationStoreController.java`, `IntegrationPlatformController.java`, `ConnectorController.java`
- `integration/connector/ecommerce/` + `BaseApiConnector.java`, `GenericHttpConnector.java`
- `SyncConflictService.java`, `SyncReconciliationReport.java`, `NxSyncJobStatus.java`

**Assessment:** Both have full Shopify sync. HotWax's is Shopify-specific and deeply specialized (mapping services, sync history, integration setup); Nexus's is connector-abstracted (BaseApiConnector + per-platform controllers) with a sync-conflict/reconciliation layer HotWax lacks. Nexus's abstraction is the stronger architecture story; HotWax's specialization is the stronger out-of-box Shopify story.

---

## 6. Order Services — breadth comparison

### HotWax `OrderServices.xml` (verified, 42 services)

`get OrderItemSalesTaxTotal`, `prepare SalesOrderShipGroups`, `create SalesOrder`, `change OrderItemStatus`, `checkCancelComplete Order`, `change OrderStatus`, `find OrderItemShipGroup`, `reject OrderItems`, `reject OrderItem`, `delete OrderItemReservations`, `complete SalesOrderItemByShipment`, `send OrderWebhook` (type `oms-rest`, POST, 180s timeout, anonymous-view), `check OrderItemAndCapturePayment`, `split OrderASNItems`, `set PreOrderPromisedDate`, `approve Order`, `reserve OrderItems`, `create POSOrderReservationAndIssuance`, `get/create OrderTask`, `change WorkEffortTaskStatus`, `resolve OpenOrderTasks`, `create CommunicationEventWorkEffort`, `swap SalesOrderShipGroupProduct`, `add SalesOrderItem`, `update OrderItemShipGroup`, `update ShippingInformationOfShipGroup` (7200s timeout), `park Order`, `update ShippingMethod`, `get OrderFulfillmentTimeline`, `move ItemToParking`, `get OpenSalesOrders`, `get InflightSalesOrders`, `get PackedSalesOrders`, `get FulfillmentProgress`, `get FunnelDashboardOpenOrders`, `get FunnelDashboardUnfillable`, `get FacilityOrderVolume`, `get FacilityFulfillmentVelocity`, `get FacilityPartialFulfillments`, `get OrderHoldTaskCounts`, `get FacilityRejections`.

Notable: **pre-order support** (`set PreOrderPromisedDate`), **POS** (`create POSOrderReservationAndIssuance`), **parking** (`park Order`, `move ItemToParking`), **funnel/dashboard analytics** (`get FunnelDashboard*`, `get FacilityOrderVolume`, `get FacilityFulfillmentVelocity`, `get FacilityPartialFulfillments`, `get FacilityRejections`), **task management** (`get/create OrderTask`, `change WorkEffortTaskStatus`, `resolve OpenOrderTasks`).

### Nexus counterpart (verified)

`OrderController.java`, `OrderApprovalController.java`, `ParkedOrderController.java`, `RejectionController.java`, `FulfillmentLimitController.java`, `DashboardController.java`, `AnalyticsController.java`, `TaskQueueController.java`, `WorkflowController.java`, `PickupController.java`, `EndlessAisleController.java`.

**Assessment:** Broad parity. HotWax's `get FunnelDashboard*` / `get Facility*` analytics services are **confirmed gaps** against Nexus's `DashboardController`/`AnalyticsController` (3 mock endpoints, mislabeled `onTimeDelivery` — see §7); pre-order/POS flows remain worth checking. Deep dives in §7 (Analytics) and §8 (Order Identification).

---

## 7. Analytics & Operator Dashboard

### HotWax funnel services (verified — `oms/service/co/hotwax/oms/order/OrderServices.xml`)

All 8 services are GET, take `productStoreId` + `dateFilter` (+ `facilityId` where noted), and are backed by pre-aggregated view entities (package `co.hotwax.order`). REST surface: `oms.rest.xml` `funnelDashboard` resource (lines 77–113) + `facilityRejections` (line 607).

| Service | What it returns | Source |
|---|---|---|
| `get#FulfillmentProgress` | Total orders + `oldestOrderEntryDatetime`; `totalShipGroupsCount` (sum of all); `brokeredShipGroupsCount` (only `parentFacilityTypeId in ['PHYSICAL_STORE','DISTRIBUTION_CENTER']`); picked/packed/shipped counts mapped from `shipmentStatusId` | `OrderFunnelDashboardView`; counts logic at 3016–3064 |
| `get#FunnelDashboardOpenOrders` | Open SALES_ORDER count + oldest open order datetime (status not COMPLETED/CANCELLED) | `OrderFunnelDashboardView` |
| `get#FunnelDashboardUnfillable` | Hourly unfillable counts, `facilityId = UNFILLABLE_PARKING`, pageSize 24, sorted DESC | `OrderFunnelDashboardView` |
| `get#OrderHoldTaskCounts` | Open hold-task counts by `workEffortPurposeTypeId` | `OrderHoldTaskPurposeCount` view + `BuildOrderHoldTaskCounts.groovy` script service |
| `get#FacilityOrderVolume` | Today's per-facility order counts | `FacilityOrderCount` |
| `get#FacilityFulfillmentVelocity` | Shipped ship-groups per facility | `FacilityFulfillmentView` |
| `get#FacilityPartialFulfillments` | Ratio `hasRejection=Y / total` per facility | `FacilityFulfillmentView` |
| `get#FacilityRejections` | Distinct order/shipGroup combos in `REJECTED_ITM_PARKING`, by facility + datetime | `OrderFunnelDashboardView` |

**Notable:** HotWax's funnel is **facility-aware and temporal** — it distinguishes brokered (store + DC) vs non-brokered volume, tracks *unfillable* as a first-class hourly metric, counts hold tasks by purpose type, and surfaces partial-fulfillment ratios and rejections per facility.

### Nexus counterpart (verified)

- `DashboardController.java` — `GET /dashboard` → `DashboardService.getKPIs`
- `AnalyticsController.java` — real endpoints: `/activity`, `/orders/velocity` (hours=24), `/alerts` (FulfillmentException), `/order-status-distribution`, `/task-queue-summary`, `/exceptions`, `/returns`, `/dashboard`
- `DashboardService.getKPIs` — `@Cacheable(value = "dashboard", key = "#tenantId")`; KPIs: `ordersToday`, `onTimeDelivery`, `activeExceptions`, `avgShipTime`, `revenueToday`, `activePickers`
- **Caching is bounded, not unbounded** — `RedisConfig.java:67` sets `cacheConfigs.put("dashboard", defaultConfig.entryTtl(Duration.ofMinutes(2)))`, so the worst-case staleness window is **2 minutes**. Seven `DashboardService` methods are `@Cacheable` into that one cache (`getKPIs`, velocity, exceptions, alerts, status-dist, task-queue, activity) and none of them is `@CacheEvict`-ed — worth noting because six other services in the codebase (`OrderService`, `InventoryService`, `ProductService`, `WarehouseService`, `RoutingRuleService`, `IntegrationStoreService`) do follow an `@CacheEvict(allEntries = true)` convention. So this is a *consistency-of-convention* gap, not unbounded staleness.

**⚠ Mock data findings (`AnalyticsController.java`):**
- `/carrier-performance` — **hardcoded** `Map.of(totalShipments=150, onTimeRate=0.94, avgDeliveryDays=2.5)`
- `/cost-breakdown` — **hardcoded** (shipping=45230, labor=28300, packaging=12400, warehouse=18700, returns=5600)
- `/lanes` — **hardcoded** (NYC→LAX vol 120/onTime 0.95, CHI→ATL 85/0.92, LAX→SEA 65/0.98)

**Assessment:** Nexus has real order/velocity/exceptions/returns analytics, but three endpoints serve mock data, and `onTimeDelivery` is actually `delivered / (total − CANCELLED)` — a delivery rate, not true on-time delivery (it ignores ship-time vs promise entirely). HotWax's facility-level funnel depth (unfillable, hold-task counts, partial-fulfillment ratio, rejections, velocity) has no Nexus equivalent. The 2-minute `dashboard` TTL is a reasonable trade-off, not a defect to lead with.

---

## 8. Order Identification

### HotWax `OrderIdentification` (verified — `ofbiz-oms-udm/entity/HwmappsEntitymodel.xml:607-622`)

Entity `co.hotwax.order.OrderIdentification`:
- **Composite PK** `(orderIdentificationTypeId, orderId, fromDate)` — an order can carry *many* identifiers, typed and versioned
- `idValue` (`id-long`), `thruDate` (`date-time`) — `fromDate`/`thruDate` make identifier values **temporal** (history preserved)
- Rel → `moqui.basic.Enumeration` (`orderIdentificationTypeId` → `enumId`) — typed identifiers
- Rel → `org.apache.ofbiz.order.order.OrderHeader` (`orderId`)
- Index `ORDER_ID_VALIDX` (non-unique) on `idValue` — **reverse lookup** by value

**Identifier types** (verified — `ofbiz-oms-udm/data/DT_ExtSeed_ShopifySeedData.xml:15-20`, enumTypeId `ORDER_IDENTITY`): `SHOPIFY_ORD_ID`, `SHOPIFY_ORD_NO`, `SHOPIFY_ORD_NAME`, plus `OLD_SHOPIFY_ORD_*` variants (migration), and `NETSUITE_ORDER_ID` (netsuite-connector). So one order can be found by Shopify ID, Shopify number, Shopify name, or NetSuite ID — with full history.

**Lookup surface:**
- **GQL** (`gql/graphql/OmsSchema.gql.xml:44`): `orderByIdentification(type, val)` root query with `by-identification="true"` (`identificationTypeId` → `orderIdentificationTypeId`, `idValue` → `idValue`, `fk-field="orderId"`); plus `identifications` edge on Order
- **Import**: `createUpdateOrderIdentification` service (used by OFBiz MDM import `IMP_ORDER_IDENT` — `ofbiz-oms-udm/data/ED_Ext_ExtCommerceData.xml:126`), accepts repeat writes
- **Companion**: `ReturnIdentification` entity (`HwmappsEntitymodel.xml:653`) — same pattern for returns
- **Search indexing** uses `OrderIdentification` (`search/SearchServices.xml:319, 853`)

### Nexus counterpart (verified)

`NxOrder.java`: `channelOrderId` (line 34), `externalId` (line 29) — **single value each, no type, no history**. Lookups: `findByTenantIdAndChannelAndChannelOrderId` / `findByTenantIdAndChannelOrderId` (`OrderRepository.java:19,23`; used in `ShopifyOrderImportService:167,169`, `BigCommerceOrderImportService:145`, `ConnectorController:277`, `OrderService:166`, `GenericImportService:948,1025,1276`).

**Assessment:** HotWax models identifiers as **typed, temporal, multi-valued records** (order ↔ {type, value, valid-from, valid-thru}) with indexed reverse lookup and a dedicated GQL root query. Nexus stores a single `channelOrderId` + single `externalId` per order — last write wins, no history, no typed lookup, no return-identification equivalent. This is a **confirmed gap** for multi-channel order matching (e.g., NetSuite ID vs Shopify order number vs legacy IDs).

---

## 9. Data Model

### HotWax `OrderHeader` (verified — `ofbiz-oms-udm/entity/DatamodelOrderEntitymodel.xml:166`)

Fields: `orderId` (id, PK), `orderTypeId`, `orderName`, `externalId` (id-long), `salesChannelEnumId`, `orderDate` (date-time), `priority` (text-indicator), `entryDate`, `pickSheetPrintedDate`, `visitId`, `statusId`, `createdBy`, `firstAttemptOrderId`, `currencyUom`, `syncStatusId`, `billingAccountId`, `originFacilityId`, `webSiteId`, `productStoreId`, `terminalId`, `transactionId`, `autoOrderShoppingListId`, `needsInventoryIssuance`, `isRushOrder`, `internalCode`, `remainingSubTotal`, `grandTotal`, `isViewed`.

Supporting entities: `OrderItem`, `OrderItemShipGroup`, `OrderRole`, `OrderIdentification` (`co.hotwax.order.OrderIdentification`), `OrderItemAssoc` (EXCHANGE type links exchange order → original via `toOrderId`), view entity `co.hotwax.oms.order.OrderItemAssocAndOrderHeader`.

### Nexus counterpart

`NxOrder` entity + supporting entities under `entity/`.

**Assessment:** HotWax's model is OFBiz-derived (20+ years of schema evolution, `externalId` as `id-long` with the exchange-duplication quirk). Nexus's model is purpose-built. The `OrderIdentification` entity (multiple identifiers per order) is a **verified gap** — Nexus carries only single-value `channelOrderId`/`externalId` columns on `NxOrder` (see §8 for the full deep-dive).

---

## 10. UI / Screens

### HotWax (verified — `screen/Oms/Order/FindOrder.xml`, `ViewOrder.xml`)

- XML screens rendered by Moqui ScreenFacade.
- `FindOrder.xml`: `<transition name="ViewOrder">` with required `orderId` param; `<entity-find>` actions for ProductStore, StatusItem, OrderHeader with `<search-form-inputs default-order-by="-orderDate"/>`; `form-list` widgets with `header-dialog="true" show-page-size="true"` and `text-find` header fields.
- `ViewOrder.xml`: `form-single` for OrderHeader + sections for ship groups (method, carrier, est. delivery, instructions, ship-to/ship-from addresses), order identifications, order roles.
- `BuildOrderHoldTaskCounts.groovy` — script service backing the hold-task counts.

### Nexus (verified)

React 19 + Vite + TypeScript + Tailwind, 93 pages under `src/pages/`.

**Assessment:** No contest on UX velocity and modern tooling — Nexus's React stack is a selling point against Moqui's XML screens. HotWax's screens are functional but dated; every UI change is an XML edit + framework render.

---

## 11. Where Nexus Wins (technical evidence)

1. **Explicit carrier abstraction — but no working integration** (⚠ partial at best) — Nexus models the carrier seam as a Java interface + injectable service; HotWax resolves it at runtime by *service-name string* (`ShipmentRequest` row, else `SystemProperty`) with no compile-time contract — see §12. That is a real architectural difference. **But Nexus has no live carrier connectivity**: `labelSource` is hardcoded `"SIMULATED"`, tracking numbers are synthesized locally (`ShippingLabelService.generateTrackingNumber`), and the real `FedExConnector` (rates/shipments/tracking/fulfillments) is **not wired to labels**. HotWax by contrast runs the full label lifecycle against a live gateway (get / void / retry + manifests + label PDFs). The pluggable `CarrierLabelAdapter` architecture is **spec'd (T-12) but not built**. Sell the abstraction; **do not claim carrier capability** — see §12.
2. **Explicit, auditable API surface** — Nexus controllers are self-documenting; HotWax's auto-generated entity CRUD is implicit.
3. **Schema governance** — 57 explicit Flyway migrations vs. implicit auto-create from entity XML.
4. **Modern UI** — React/TypeScript vs. Moqui XML screens.
5. **Connector abstraction** — `BaseApiConnector`/`GenericHttpConnector` + sync-conflict/reconciliation layer vs. Shopify-specific services.
6. **Sync conflict management** — `SyncConflictService` + `GET /sync/conflicts` + resolve workflow; HotWax has no equivalent.

## 12. Where HotWax Wins (technical evidence)

1. **Reconciliation contract** — `get ReconciliationOrders`/`get ReconciliationReturns` are a deliberate, well-specified external API (half-open windows, exchange-aware, machine-distinguishable errors). Nexus's `GET /sync/reconciliation` reports job counts, not order records. **This is the #1 gap to close.**
2. **Pluggable shipping/rating gateway (per-deployment)** — HotWax completes the full carrier lifecycle against a live gateway; Nexus has none. The lifecycle **code is first-party in `poorti`**, not core `oms` and not a customer component:
   - **Dispatch by config, not interface** — `PoortiHelperServices.get#ConfiguredCarrierService` resolves a service *name* for `(carrierPartyId, shipmentMethodTypeId, requestType)`: first the `ShipmentRequest` entity (PK = those three fields → `serviceName`), else `SystemProperty` `shipment.<requestType>.service.name`. Six request types: `DO_RATE_SHOP`, `GET_SHIPPING_RATE`, `GET_SHIPPING_LABEL`, `VOID_SHIPPING_LABEL`, `GENERATE_MANIFEST`, `RETRY_SHIPPING_LABEL`. Clients are switched by repointing the property — `OmsRestShippingServices` (legacy, REST hop back into OFBIZ) vs `UnigateShippingServices` (direct Moqui call).
   - **Three aggregators wired in this repo** — **Unigate** (`poorti`), **maarg-shipping-aggregator** (ADOC), **hotwax-shiphawk** (Gorjana, not in this snapshot). Source: `poorti/docs/Poorti Shipping Services_ Production Rollout Plan.md`. `mantle-shippo` (Shippo) is in `addons.xml` v1.2.1 but is **not deployed** — zero references in the runtime, absent from `runtime/component`, no `myaddons.xml`.
   - **Gateway indirection** — OMS never talks to a carrier directly. `send#UnigateRequest` POSTs to `SystemMessageRemote[UNIGATE_CONFIG].sendUrl + endpoint` (JSON, 60s default timeout, headers `api_key`=`publicKey`, `tenant_Id`=`internalId`); credentials live on Unigate and OMS stores only the returned `gatewayAuthId` via `ShippingCarrierConfig` (`productStoreId` + `carrierPartyId` + optional `facilityId`).
   - **Rate shopping with SLA filtering** — `do#RateShopping` draws eligible carriers from `ShippingCarrierConfig` (*not* `FacilityParty`), drops rates whose `estimatedDeliveryDate` falls after `requestedDeliveryDate` for non-`STANDARD` methods, then picks `eligibleRates.sort { it.shippingEstimateAmount }[0]` — **cheapest eligible, not fastest** — and writes `carrierPartyId`, `actualCost`, `carrierServiceStatusId=SHRSCS_CONFIRMED`, `carrierService`, `actualCarrierCode`, `gatewayRateId` back to `ShipmentRouteSegment`. SLA targets come from `CarrierShipmentMethod` (`deliveryDays` + `carrierServiceCode` per carrier).
   - **Label lifecycle + print artifacts** — `get/void/retry#ShippingLabel`, `generate#Manifest`, return labels, and REST-backed PDFs: `Label.pdf`, `ReturnLabel.pdf`, `PackingSlipAndLabel.pdf`, `Manifest.pdf`, plus `carrierConfigs`, `carrierManifests`, `updateShipmentCarrierAndMethod`, `updateShipmentTracking`.
   - ⚠ **Important caveat** — core `oms` has only `create#Carrier`, `get#CarrierDetails`, `update#ShippingMethod`: no rating and no label services. The lifecycle code is first-party **`poorti`**, but *which* gateway runs is **per-deployment configuration** (Unigate, maarg-shipping-aggregator, shiphawk, OMS-REST), so the integration work is something HotWax bills for rather than a zero-effort product feature. Core's gap is that there is no default carrier integration at all.
3. **Zero-config CRUD** — `EntityAutoServiceRunner` gives every entity create/update/delete/store endpoints for free.
4. **Routing SQL introspection** — `get OrderFilterSql` / `get OrderRoutingRuleSql` expose generated SQL for debugging.
5. **Order analytics services** — 8 server-side services (`get FulfillmentProgress`, `get FunnelDashboardOpenOrders`, `get FunnelDashboardUnfillable`, `get OrderHoldTaskCounts`, `get FacilityOrderVolume`, `get FacilityFulfillmentVelocity`, `get FacilityPartialFulfillments`, `get FacilityRejections`) are facility-level, reusable, and backed by view entities (`OrderFunnelDashboardView`, `FacilityOrderCount`, `FacilityFulfillmentView`, `OrderHoldTaskPurposeCount`) and parking facilities (`UNFILLABLE_PARKING`, `REJECTED_ITM_PARKING`). Nexus's `AnalyticsController` ships **3 hardcoded mock endpoints** (`/carrier-performance`, `/cost-breakdown`, `/lanes`); its `onTimeDelivery` is `delivered / (total − CANCELLED)` — a delivery rate, not true on-time. See §7.
6. **Pre-order & POS flows** — `set PreOrderPromisedDate`, `create POSOrderReservationAndIssuance`.
7. **Multi-identifier orders** — typed, temporal `OrderIdentification` (composite PK `(orderIdentificationTypeId, orderId, fromDate)`, `thruDate`, indexed `idValue`) with per-type identifiers (`SHOPIFY_ORD_ID`, `SHOPIFY_ORD_NO`, `SHOPIFY_ORD_NAME`, `NETSUITE_ORDER_ID`), a `ReturnIdentification` companion, GQL `orderByIdentification` lookup, and an import service that accepts repeated identifiers. Nexus has a single `channelOrderId`/`externalId` per order — last-write-wins, no history. See §8.

## 13. Recommended Nexus Actions

1. **Build a Darpan-compatible reconciliation endpoint** (`GET /reconciliation/orders`, `GET /reconciliation/returns`) matching the HotWax contract: half-open `[from, thru)` windows, `isExchange`/`originalOrderId`/`originalExternalId`, count fields (`excludedNonSalesOrderCount`, `missingExternalIdCount`), deterministic `ORDER BY orderDate, orderId`, and machine-distinguishable errors (`INVALID_WINDOW`, `PAGE_SIZE_TOO_LARGE`). This is the single highest-value compatibility item — it lets Nexus drop into existing Darpan pipelines.
2. **Add multi-identifier order lookup (confirmed gap)** — Nexus stores a single `channelOrderId`/`externalId` per order; HotWax's typed, temporal `OrderIdentification` model (composite PK + `thruDate`, `ReturnIdentification` companion, GQL `orderByIdentification`) supports channel-order-number, order-name, and legacy identifiers with history. Add a typed identifier table + lookup endpoint (see §8).
3. **Replace mock analytics and add facility-level funnel metrics (confirmed gap)** — HotWax exposes server-side funnel services (`get FunnelDashboard*`, `get FacilityOrderVolume`, `get FacilityFulfillmentVelocity`, `get FacilityPartialFulfillments`, `get FacilityRejections`) backed by view entities. Nexus's `AnalyticsController` serves 3 hardcoded mocks (`/carrier-performance`, `/cost-breakdown`, `/lanes`) and `onTimeDelivery` is a delivery rate rather than true on-time. Replace mocks with real queries and add per-facility funnel KPIs (see §7).
4. **Build real carrier connectivity — this is the actual gap, not a talking point.** ⚠ Do **not** lead with carrier labels: HotWax completes the full live label lifecycle (rate shop → label → void/retry → manifest → label PDFs) via Unigate/maarg/shiphawk, and Nexus is simulated end to end. Ship T-12 as an *adapter interface plus a first real connector*, then lead with the explicitness of the abstraction (compile-time interface, injectable, no runtime string dispatch) and self-hostable credentials — not with a capability HotWax already runs in production.
5. **Use the reconciliation contract as a sales artifact** — it shows Nexus understands the integration reality (exchange orders, non-unique externalIds, boundary windows) better than a generic "sync status" report does.

---

*Verified against: `hotwaxoms/moqui2/runtime/component/{oms,order-routing,shopify-connector,ofbiz-oms-udm}/` and `hotwaxoms/moqui2/framework/src/main/groovy/org/moqui/impl/`; `nexus-p0/nexus-oms-backend/src/main/java/com/nexus/oms/` and `nexus-p0/nexus-oms-frontend/src/`.*