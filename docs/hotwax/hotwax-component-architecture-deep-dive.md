# HotWax Moqui Component Architecture — Code-Level Deep Dive

> **Purpose:** A source-verified map of the entire HotWax Commerce codebase, taken directly from the running Moqui application — every component, its data model, its service surface, its integration seams, and the real end-to-end order flow.
>
> **Source of truth:** `hotwaxoms/moqui2/runtime/component/` — read from the deployed component tree, not from documentation or sales claims. Every claim below cites a file and, where relevant, an exact Moqui service identifier.
>
> **Relationship to other docs:** This is the *code* evidence base. [`nexus-vs-hotwax-technical.md`](./nexus-vs-hotwax-technical.md) compares this architecture to Nexus's Spring Boot stack. `hotwax-company-analysis.md` (kept locally, not in the repo) covers how HotWax operates as a company.

---

## How to Read This Document

Moqui services are identified as `verb#noun` and declared with **two separate XML attributes**, not a single name:

```xml
<service verb="create" noun="OrderFulfillmentWave">
```

So a service is *called* as `co.hotwax.poorti.FulfillmentServices.create#OrderFulfillmentWave`. Grepping for `<service name="..."` finds nothing — this tripped up the first pass of this research and is worth knowing.

---

## 1. The Component Tree

Fourteen components under `runtime/component/`. `poorti` contains its own nested `.git`, so it is effectively an independently-versioned product dropped into the stack.

| Component | Internal name | Version | Declared deps | Files |
|-----------|---------------|---------|---------------|-------|
| `ofbiz-oms-udm` | ofbiz-oms-udm | 4.0.0-RC1 | — | 160 |
| `maarg-util` | maarg-util | 4.0.5 | `moqui-fop` | 785 |
| `oms` | oms | 3.0.9 | maarg-util, ofbiz-oms-udm | 319 |
| `poorti` | poorti | 3.0.3 | oms | 944 |
| `order-routing` | order-routing | 2.1.0 | oms | 88 |
| `ai` | moqui-ai | 1.0.0 | oms | 201 |
| `shopify-connector` | shopify-connector | 4.0.7 | oms | 455 |
| `netsuite-connector` | **mantle**-netsuite-connector | 3.3.0 | poorti | 167 |
| `gql` | moqui-gql | 1.1.0 | oms, shopify-connector | 173 |
| `lovers` | lovers | 1.0.1 | shopify-connector, oms, poorti | 82 |
| `adoc-maarg` | adoc-maarg | 2.0.1 | shopify-connector, oms | 137 |
| `oms-test` | oms-test | 2.1.1 | oms | 328 |
| `hotwax-moqui-firebase` | moqui-firebase | 2.0.0-RC1 | — | 55 |
| `maarg-docker-config` | — | — | (no `component.xml`) | 230 |

### Layering

```
Layer 0  Foundation     ofbiz-oms-udm (data model)   maarg-util (jobs/auth/search/agents)   moqui-firebase
Layer 1  Core OMS       oms  ── depends on both
Layer 2  Engines        poorti (fulfillment+WMS)   order-routing (brokering)   shopify-connector   ai
Layer 3  Extensions     netsuite-connector (→ poorti)   gql (→ oms, shopify)   lovers, adoc-maarg, oms-test
```

Three dependency facts that matter commercially:

1. **`netsuite-connector` depends on `poorti`, not on `oms`.** The NetSuite connector reaches fulfillment through the WMS component. NetSuite integration is therefore coupled to Poorti's transfer-order model — a real constraint, not a stylistic choice.
2. **`gql` depends on `shopify-connector`.** The GraphQL/MCP API surface is not channel-neutral; it is built over the Shopify connector's data shape.
3. **`lovers` and `adoc-maarg` are named customer components.** They are real customer deployments living in the same tree as the product. This is the single most important structural fact about the codebase: *the product and the customers' customizations are not separated.*

---

## 2. The Data Model — `ofbiz-oms-udm`

`ofbiz-oms-udm` is a **modular unified data model**: OFBiz's entity definitions, decomposed into per-domain files, plus HotWax extensions. It holds **278 entity declarations** — more than every other component combined. (That is 277 *unique* names; see the `ProductType` finding below.)

| Model file | Entities | Domain |
|-----------|----------|--------|
| `HwmappsEntitymodel.xml` | 56 | HotWax application entities |
| `DatamodelProductEntitymodel.xml` | 56 | Product, inventory, facility |
| `DatamodelOrderEntitymodel.xml` | 43 | Order, order item, ship group |
| `DatamodelPartyEntitymodel.xml` | 34 | Party, contact, role |
| `DatamodelShipmentEntitymodel.xml` | 30 | Shipment, package, route segment, picklist |
| `ShopifyConnectorEntitymodel.xml` | 14 | **Shopify-specific entities — inside the "neutral" UDM** |
| `DatamodelAccountingEntitymodel.xml` | 10 | Invoice, payment |
| `DatamodelContentEntitymodel.xml` | 7 | Content, image |
| `SecurityEntitymodel.xml` | 6 | Security groups/permissions |
| `OmsEntities.xml` | 6 | OMS extension entities |
| `CommonEntitymodel.xml` | 4 | Shared/common |
| `DatamodelManufacturingEntitymodel.xml` | 4 | Manufacturing |
| `DatamodelWorkeffortEntitymodel.xml` | 4 | Work effort |
| `EntityEntitymodel.xml` | 2 | Entity metadata |
| `HwmappsFirebaseEntitymodel.xml` | 1 | Firebase |
| `HwmappsIntegrationEntitymodel.xml` | 1 | Integration |
| **Total** | **278** | |

Of the 30 files in that directory, **16** are the non-zero models in the table above and **14** contain **zero** `<entity>` elements: the 12 `Oms*ViewEntities.xml` files, `ServiceJobViewEntities.xml`, and `CommonextEntitymodel.xml`. They are `view-entity` projections that record `# TODO: skipped element view-entity` markers. So the UDM ships view-entity placeholders it does not actually implement.

Per-component entity declaration counts: `ofbiz-oms-udm` 278, `poorti` 19, `ai` 19, `shopify-connector` 10, `order-routing` 9, `netsuite-connector` 9, `oms` 8, `gql` 5.

#### Two real defects found in the UDM

**1. `ProductType` is declared twice.** In `DatamodelProductEntitymodel.xml`, the same entity name + package is declared at **line 1029** and again at **line 1071** — identical field sets, 42 lines apart. The first carries `<seed-data>` for the 8 standard product types (`GOOD`, `FINISHED_GOOD`, `SERVICE`, `DIGITAL_GOOD`, `MARKETING_PKG`, `MARKETING_PKG_AUTO`, `MARKETING_PKG_PICK`, `GIFT_CARD`); the second is a bare re-declaration with **no seed data**. Whichever Moqui's loader retains, the other is silently discarded — and the seeded data is on the one that has a redundant twin. This is not cosmetic: `ProductType` is referenced by 15 source files outside the UDM, across four components — 7 in `oms` (`OrderReservationServices.xml`, `OrderServices.xml`, `orderledger/system/FeedServices.xml`, `oms.rest.xml`, the `FindProduct`/`ViewProduct` screens, and `WarehouseHelper.groovy`), 3 in `netsuite-connector`, 3 in `shopify-connector`, and 2 in `poorti` (`FulfillmentServices.xml`, `FulfillmentViewEntities.xml`). A raw text match returns 17, but the two extras are `netsuite-connector` markdown files (`docs/inventory-cycle-count-netsuite-feed-design.md`, `upgrade/v3.0.0-RC1/upgradeSteps.md`), not code.

**2. Shopify entities live in the channel-neutral UDM.** `ShopifyConnectorEntitymodel.xml` puts 14 entities inside the shared data-model foundation, while `shopify-connector` defines 10 more of its own. Shopify's data shape is therefore baked into the layer every other component depends on. This is the concrete mechanism behind the `gql` → `shopify-connector` coupling in §11, and it is a design flaw worth naming: a channel-specific model has leaked into the shared kernel.

**The architecture in one line:** the *data model* is a shared, versioned foundation component; *business logic* is layered on top in separate components that extend it. This is why the dependency graph is shallow and legible — a component can only touch entities it can see. The cost of that design is that the foundation is where defects like the two above accumulate, because every component inherits them.

---

## 3. Four Reusable Patterns

The components are not 14 unrelated applications. Four patterns recur, and recognising them is what makes the codebase comprehensible.

### 3.1 The Rules-Engine Pattern

`order-routing` and `poorti`'s picking both implement the **identical** seven-entity shape:

| Purpose | `order-routing` | `poorti` picking |
|---------|-----------------|------------------|
| Configured set | `OrderRoutingGroup` | `PickProfileGroup` |
| Rule | `OrderRouting` / `OrderRoutingRule` | `PickProfile` |
| Condition | `OrderFilterCondition` | `PickProfileCondition` |
| Action | `OrderRoutingRuleAction` | `PickProfileAction` |
| Run instance | `OrderRoutingRun` | `PickProfileRun` |
| Batch | `OrderRoutingBatch` | `PickProfileBatch` |
| Status | `OrderRoutingStatus` | `PickProfileStatus` |

Same conditions, same cascade-with-early-exit, same per-rule transaction isolation, same run/batch audit trail. Once you understand one, you understand both. This is genuine product engineering — not accidental duplication.

### 3.2 The SystemMessage Integration Bus

External communication rides on Moqui's core `moqui.service.message.SystemMessage` entity, which HotWax **extends** (`maarg-util/entity/ServiceEntities.xml`, `shopify-connector/entity/ShopifyEntities.xml`).

`co.hotwax.maarg-util`'s `org.moqui.impl.SystemMessageServices` provides:

- `queue#SystemMessage` — persists an outgoing message in `SmsgProduced`; `sendNow=true` (default) fires `send#ProducedSystemMessage` either `async` or synchronously in a `force-new` transaction.
- `send#AllProducedSystemMessages` — scheduled sweeper; `retryMinutes=60`, `retryLimit=24` (~1 day), then `SmsgError`.

So every outbound integration is **durably queued, retried for a day, then dead-lettered**. Nothing is fire-and-forget HTTP. This is a real strength: Shopify/NetSuite downtime degrades to backlog, not data loss.

The file even documents a hard-won Moqui gotcha in a comment: `extend-entity` field merging across components depends on **alphabetical deployment order** and does not reliably merge all fields.

### 3.3 The Feed Pattern

Cross-component and cross-system sync uses paired services:

```
generate#XxxIdsFeed   →  generate#XxxFeed   →  queue#FeedSystemMessage   →  consume#XxxFeed
```

Real examples: `generate#NewOrderIdsFeed` → `generate#NewOrdersFeed`; `generate#UpdatedOrderIdsFeed` → `generate#OrderUpdatesFeed`; `generate#ProductVariantUpdatesFeed`; `generate#ReturnsAndExchangeFeed`; `generate#RefundsFeed`.

The ID-feed-then-detail-feed split exists so the expensive, rate-limited detail fetch only runs for a known ID set. The `queue#` step pushes onto the SystemMessage bus, so every feed is retryable.

### 3.4 Config-Driven Service Dispatch

The gateway pattern, described in full in §6. Services are never called by hard-coded carrier logic; a lookup resolves a *service name string* at runtime.

---

## 4. `poorti` — The Fulfillment Application (944 files)

Poorti is the WMS/fulfillment app. It is **~100% declarative XML** — its only custom source file is a test (`TransferOrderReceiveConcurrencyTests.groovy`). All business logic lives in `service/co/hotwax/poorti/FulfillmentServices.xml` (2,071 lines) and friends.

### 4.1 Poorti-Owned Entities (19)

`FulfillmentEntities.xml` — `PicklistShipment` (joins OFBiz `Picklist` to HotWax `Shipment`), `ExternalInventoryReset`, `CarrierManifest`, `ShippingCarrierConfig`, `ShippingCarrierBillingConfig`, `CarrierGeoMapping`, `ReturnLabel`.

Plus 7 `PickProfile*` and 5 `InventoryCount*` entities, and 1 `TransferEntities` / 1 `PartyEntities`.

### 4.2 The Fulfillment Wave — the central operation

`create#OrderFulfillmentWave` (`FulfillmentServices.xml:234`) is the entry point for picking work:

1. Groups requested `orderItems` by `[orderId, shipGroupSeqId]`.
2. Per group, checks for an existing shipment in `SHIPMENT_APPROVED`, `SHIPMENT_PACKED`, or `SHIPMENT_SHIPPED`. **If one exists it does not create another** — it just re-indexes the order items into Solr (`create#OrderItemIndex`, `transaction="force-new"`, `ignore-error="true"`).
3. Otherwise calls `create#SalesOrderShipment` per group.
4. If *no* shipment was created, it returns early: *"Fulfillment already started for selected orders. Continue fulfillment using in-progress shipments for these orders."*
5. Creates an OFBiz `Picklist` with `PicklistShipment` links and picker roles.
6. Sets Solr `fulfillmentStatus = InProgress` for every item.
7. Checks whether the facility is a member of the **`AUTO_SHIPPING_LABEL`** facility group — the flag that switches a facility to automatic label purchasing.

Idempotency is deliberate and defensive, because the wave is driven by a UI that can double-submit.

`handle#OrderFulfillmentWave` (`:341`, `authenticate="anonymous-view"`) is the **second phase**: for each shipment it calls `co.hotwax.poorti.shipping.ShippingServices.do#RateShopping` with `transaction="force-new"`. The docstring says it does *"Rate Shopping and get Shipping Label."* Splitting create-then-handle is what lets the UI create a wave and have rating happen asynchronously per shipment.

### 4.3 The Service Surface (41 services)

Full lifecycle, in order: `create#SalesOrderShipment` → `create#OrderFulfillmentWave` → `handle#OrderFulfillmentWave` → `pack#Shipments` / `pack#Shipment` → `unpack#Shipment` → `ship#Shipments` / `ship#Shipment`, with `quickShip#OrderShipGroup` as a one-shot path.

Inventory truth: `adjust#InventoryDeltaByIdentification`, `receive#ProductInventory`, `receive#PurchaseShipment`, `create#PhysicalInventory`, `create#ExternalInventoryReset`, `reset#ProductFacilityInventory`, `get#InventoryAvailableByFacility`.

Output: `print#Picklist`, `print#PicklistCsv`, `print#Label`, `print#ReturnLabel`, `print#Manifest`, `print#PackingSlip`, `print#PackingSlipAndLabel`.

Corrections and exceptions: `reject#ShipmentItem`, `reject#OutstandingOrders`, `reject#InProgressOrders`.

Carrier administration: `update#ShipmentTracking`, `update#ShipmentCarrierAndMethod`, `update#RouteShipmentCarrierAndMethod`, `get#Shipments`, `get#ShipmentPackageRouteSegDetails`, `get#ShipmentFacets`, `get#OrderBillingDetails`.

Facility on/off: `enable#StoreForFulfillment` / `disable#StoreForFulfillment`.

Three `authenticate="false"` services stand out — `encode#Base64` (`:1840`), `encode#ReturnLabelBase64` (`:1870`), and `create#ShipmentPackage` (`:1894`) — unauthenticated base64, label, and shipment-package-creation endpoints. `create#ShipmentPackage` is the more serious of the three: it auto-populates from `ShipmentPackage` with only `dateCreated` excluded, so it is a write endpoint, not just a read. Worth a security review in any competitive conversation.

### 4.4 Transfer Orders — a Second, Parallel Lifecycle

`TransferOrderFulfillmentServices.xml` (1,503 lines, 13 services) implements store-to-store transfer orders as a full mirror of outbound fulfillment: create → pick → pack → ship → receive → reconcile. This is a genuine WMS capability, not a field on the order model.

### 4.5 Integration Seams in `FulfillmentServices.xml`

| Called component | Count | Role |
|-----------------|-------|------|
| OFBiz core (`update#...Shipment`, `create#...Picklist`, `ItemIssuance`, `PhysicalInventory`, `ShipmentReceipt`) | 20 | Shipment/picklist state, issuance, inventory documents |
| `co.hotwax.poorti.shipping.ShippingServices` | 6 | Rate shop, label, manifest |
| `co.hotwax.oms.product.InventoryServices` | 4 | Inventory truth |
| `co.hotwax.oms.order.OrderServices` | 3 | Order status, ship-group updates |
| `co.hotwax.oms.product.ProductServices.findOrCreate#FacilityInventoryItem` | 3 | Inventory item lifecycle |
| `co.hotwax.solr.SolrServices` + `co.hotwax.oms.search.SearchServices` | 3 | Search index |
| `co.hotwax.orderledger.product.ProductServices` | 1 | Product data |

**Poorti owns execution; `oms` owns truth.** Fulfillment writes to OFBiz shipment entities and calls `oms` for inventory and order status. That boundary is clean and is worth respecting in any comparison.

---

## 5. `order-routing` — Brokering as a Scheduled Rules Engine (88 files)

### 5.1 Execution Model

`OrderRoutingServices.xml` runs a cascade:

- `run#OrderRoutingGroup` / `run#OrderRouting` / `run#OrderRoutingRule` — `transaction-timeout="36000"` (**10 hours**), cleanup at 600s.
- Per-rule transactions. A failing rule is **logged and skipped, not fatal** — one bad rule cannot stall a batch.
- First matching rule wins; the cascade short-circuits.
- Eligible orders are read via dynamic FreeMarker SQL: `component://order-routing/sql/EligibleOrdersQuery.sql.ftl`, mapped to `co.hotwax.order.OrderItemsQueue`.
- Facility candidates come from `InventorySourceSelector.sql.ftl`, which measures distance in the customer's measurement system and applies a timezone offset.

### 5.2 Configuration

`OrderFilterCondition` carries two condition types: `ENTCT_FILTER` (predicate) and `ENTCT_SORT_BY` (ordering key). Assignment modes `ORA_SINGLE` and `ORA_MULTI` control whether an order goes to one facility or is split. Actions: `ORA_MV_TO_QUEUE` (move to the order-items queue), `ORA_RM_CANCEL_DATE` (remove cancel date), `ORA_AUTO_CANCEL_DAYS` (auto-cancel threshold). A broker facility can see all availability; non-brokers respect a shipment threshold.

### 5.3 The Critical Handoff

Routing **decides; it does not reserve.** The actual allocation call is:

```
co.hotwax.oms.impl.OrderReservationServices.process#OrderFacilityAllocation   (transaction-timeout 120)
```

`oms/service/co/hotwax/oms/impl/OrderReservationServices.xml` holds the reservation core:

- `create#OrderItemInventoryReservation` / `update#` / `cancel#` — hard reservations.
- `reserve#SoftAllocatedInventory` — soft allocation.
- `process#OrderFacilityAllocation` — iterates `facilityAllocation` → per item `process#OrderItemAllocation` → per facility `increase#FacilityOrderCount` with **`ignore-error="true"`** (the counter is best-effort and never fails an allocation).
- `create#POSOrderReservationAndIssuance` — point-of-sale path.

### 5.4 Operational Diagnostics

`get#OrderFilterSql` and `get#OrderRoutingRuleSql` return the *compiled SQL*. Given routing bugs are configuration bugs, shipping the generated SQL to support is a genuinely thoughtful touch — and a real contrast with a black-box routing service.

---

## 6. Shipping — How HotWax Actually Buys Labels

This is the most commonly mis-stated part of the HotWax architecture, so it is documented precisely.

### 6.1 Verified: core `oms` has no rating or label logic

A tree-wide search for services named `RateShopping`, `ShippingLabel`, `ShippingRates`, `Manifest`, `ReturnLabel` returns matches in **exactly one component: `poorti`.** Core `oms` defines **zero** services with those nouns anywhere under `oms/service/`.

What core `oms` does have on the carrier side is carrier *master data* and gateway plumbing only — `create#Carrier` in `co/hotwax/oms/carrier/CarrierServices.xml` (the sole service in that file), `get#CarrierDetails` in `orderledger/email/EmailServices.xml`, `update#ShippingMethod` in `oms/order/OrderServices.xml`, and the Unigate transport plus credential registry in `co/hotwax/unigate/UnigateServices.xml` (§6.4). None of it rates, buys, or prints a label.

### 6.2 The dispatcher: `poorti/shipping/ShippingServices.xml`

Every entry point resolves a target service, then calls it by name:

| Entry point | `requestType` | Behaviour if unconfigured |
|-------------|---------------|---------------------------|
| `do#RateShopping` | rate shopping | `log level="warn"` and continue |
| `get#ShippingRates` | `GET_SHIPPING_RATE` | hard error |
| `get#ShippingLabel` | `GET_SHIPPING_LABEL` | silent no-op |
| `void#ShippingLabel` | `VOID_SHIPPING_LABEL` | hard error |
| `retry#ShippingLabel` | `RETRY_SHIPPING_LABEL` | per-shipment fallback |
| `generate#Manifest` | `GENERATE_MANIFEST` | hard error |

All use `transaction-timeout="120"`.

`retry#ShippingLabel` is the most sophisticated: if a batch retry service is configured it delegates the **whole batch in one call**; otherwise it iterates shipments and rate-shops first when the carrier is `_NA_` or `forceRateShop=Y`, then requests the label.

### 6.3 Resolution order (`PoortiHelperServices.get#ConfiguredCarrierService`)

```
1. co.hotwax.warehouse.packing.ShipmentRequest   ← most specific: store/carrier/method + requestType
2. SystemProperty 'shipment' / 'shipment.<requestType>.service.name'   ← global default
```

### 6.4 Credentials are configured separately from code

`co.hotwax.shipment.shipment.ShippingCarrierConfig` (Poorti) holds `gatewayAuthId`, `carrierAccountId`, `customerNumber`, `weightUomId`, `packagingType`, `dropoffType`, `labelSize`, `labelImageType`, `zplLabelImageType`, `physicalStoreLabelSize`, `distributionCenterLabelSize`, `smartPostHub`, scoped by `productStoreId` + `carrierPartyId` + `facilityId`.

**The architectural point: which gateway code runs, and which credentials it uses, are configured in two different places.** `UnigateShippingServices.xml` (1,887 lines) reads `shippingCarrierConfig.gatewayAuthId` → `shippingGatewayAuthId` at lines 203, 648, 954, 1217, and 1805 (rate shop, label, validate, return label, manifest) and calls `send#UnigateRequest` in core `oms` for transport. The credential registry itself — `create/update/delete#ShippingGatewayAuth`, `get#ShippingGatewayConfigs` — lives in `oms/service/co/hotwax/unigate/UnigateServices.xml` (326 lines), alongside `get#Tenants`, `create#Tenant`, `generate#LoginKey`, `get/create/update/delete#CommGatewayAuth`, and `create#KlaviyoEmailFlow`.

So the accurate picture is: **core `oms` owns the Unigate transport and the gateway-credential registry; Poorti owns the entire shipping business lifecycle.** HotWax's own "we're just an OMS" framing understates how much fulfillment logic sits in `poorti`.

### 6.5 Verified live lifecycle

Rate shop → label → tracking/void/retry → manifest → label PDFs. `reinitialize#Shipment` (`:354`) sets the shipment back to `SHIPMENT_INPUT` and voids an existing label only when a tracking number exists and `carrierServiceStatusId != SHRSCS_VOIDED`; otherwise it resets to `SHRSCS_NOT_STARTED`. Return labels are gated: *"Return ID: X is not in Authorized status. Label generation is only allowed for Authorized returns."*

**Every customer ships labels in production.** The architecture advantage available to Nexus is not capability — it is that the gateway *code* and the *credentials* would both sit in a typed Java service layer rather than split between a 1,887-line XML service and a database config table.

---

## 7. `shopify-connector` (455 files)

### 7.1 Webhook ingest

`co/hotwax/shopify/webhook/ShopifyWebhookServices.xml`: `create#WebhookSubscription`, `get#WebhookSubscriptions`, `delete#WebhookSubscription`, **`verify#Hmac`**, `receive#WebhookPayload`, `queue#WebhookSubscriptionSystemMessage`, `send#WebhookSubscriptionSystemMessage`, `consume#WebhookPayloadSystemMessage`.

HMAC verification is a first-class service, and receipt pushes onto the SystemMessage bus — so a Shopify outage during a webhook storm produces backlog, not loss.

### 7.2 Order sync

`co/hotwax/sob/order/OrderFeedServices.xml`: `generate#OMSOrderCancelUpdatesFeed`, `send#ShopifyOrderSync`.
`co/hotwax/sob/fulfillment/FulfillmentFeedServices.xml`: `generate#OMSFulfillmentFeed`, `consume#ShopifyFulfillmentAckFeed`, `post#ShopifyFulfillment`.

**This is the loop closure:** Poorti ships → `generate#OMSFulfillmentFeed` → `post#ShopifyFulfillment` → Shopify's fulfillment ack returns → `consume#ShopifyFulfillmentAckFeed`. The acknowledgement is a first-class inbound feed, not a fire-and-forget assumption.

### 7.3 The outbound side is GraphQL-native

`ShopifySystemMessageServices.xml` (2,550 lines, 40 services) is far more than order sync. It contains a full **Shopify Bulk Operations implementation**: `queue#BulkQuerySystemMessage`, `send#ShopifyBulkQueryMessage`, `poll#ShopifyBulkOperationResult`, `process#BulkOperationResult`, `consume#BulkOperationResult`, `consume#BulkOperationsFinishWebhookPayload`, `send#BulkMutationSystemMessage`, `consume#GraphQLBulkImportFeed`, `consume#ShopifyProductDataFile`, `poll#BulkOperationResult`, `consume#SyncedRefundsFeed`.

Plus purpose-built feeds: `generate#ReturnsAndExchangeFeed`, `generate#RefundsFeed`, `generate#RefundIdsFeed`, `generate#ReturnedOrderIdsFeed`, `generate#OrderMetafieldsFeed`, `generate#FulfillmentOrderIdsFeed`, `generate#OrderAgreementUpdatesFeed`.

This is GraphQL Bulk Operations done properly — async job polling plus a finish-webhook, which is the only correct way to sync at Shopify scale. Custom Groovy supports it: `GraphqlFacade.groovy`, `GraphqlQueryBuilder.groovy`, `GraphqlAstUtils.groovy`, `GraphqlSelectionBuilder.groovy`, `AppBridgeJwtManager.groovy`, `ShopifyRequestFilter.groovy`.

### 7.4 Architectural debt: two parallel namespaces

The connector carries **both** `co.hotwax.shopify.*` and legacy `co.hotwax.sob.*` / `co.hotwax.soblegacy.*`, with duplicated classes — `ShopifyHelper.groovy` exists at both `co/hotwax/sob/shopify/` and `co/hotwax/shopify/util/`; `ShopifyRequestFilter.groovy` at both `co/hotwax/shopify/` and `co/hotwax/shopify/filter/`. A migration is visibly incomplete.

---

## 8. `netsuite-connector` (167 files)

Declared name is **`mantle-netsuite-connector`**, and it depends on **`poorti`** — not on `oms`. The connector reaches fulfillment through the WMS component, which is why it owns transfer-order synchronisation as a first-class concern.

`co/hotwax/system/FeedServices.xml` is the spine: `generate#OMSFeed`, `consume#NetsuiteFeed`. `co/hotwax/orderledger/system/FeedServices.xml` (core `oms`) is the shared producer side: `consume#OMSFeed`, `transform#JsonLToJsonForUpdatedProducts`, `consume#UpdatedProductHistories`, `consume#ProductUpdateWorker`.

Large service files: `NetSuiteRestServices.xml` (1,814), `TransferOrderServices.xml` (1,483), `OrderServices.xml` (1,078), `NetSuiteRestletServices.xml` (784). Transfer feed generation includes `generate#TransferOrderShipmentReceiptFeed`, `generate#TransferOrderShipmentFeed`, `generate#TransferOrderSyncAckFeed`.

Entities: `NetsuiteEntities.xml` (6) and `NetSuiteCustomFieldEntities.xml` (2) — custom fields are modelled explicitly, which is the correct approach for NetSuite integration.

---

## 9. `ai` — `moqui-ai` 1.0.0 (201 files, 38 custom sources)

The XML is a thin orchestration shell; the engine is Groovy. `run#Agent` is literally a shim:

```xml
<script><![CDATA[
    def r = new org.moqui.ai.AgentRunner(ec).run(agentId, userMessage, conversationId)
    ec.context.putAll(r)   // the result map keys ARE the out-parameters
]]></script>
```

Note `transaction="ignore"` — an agent run does not participate in the caller's transaction.

### 9.1 The engine

`org/moqui/ai/`: `AgentRunner`, `LlmProvider` + `AbstractLlmProvider` + **`AnthropicProvider` / `OpenAiProvider` / `MockProvider`**, `ContextAssembler`, `DefinitionLoader`, `AiToolFactory`, `ServiceSchemas`, `CostCalc`.

`MockProvider` is a real asset: the agent loop is testable without an LLM key.

### 9.2 Moqui services *are* the AI tools

`DefinitionLoader.groovy` loads tool definitions from the `moqui.ai.AiTool` entity; `AgentRunner` persists `serviceName: td?.serviceName` on each tool call. `ServiceSchemas.groovy` exposes Moqui service signatures to the model. **The AI layer does not invent actions — it calls existing business services**, which is why the approval gate below matters.

### 9.3 Fail-closed, whole-turn human-in-the-loop

This is the most carefully engineered part of the codebase:

- `maxIterations` defaults to 8; `maxToolCalls` is separately capped.
- A tool whose catalog `requiresApproval` is `Boolean.TRUE` cannot execute. The runner instead persists `AiToolCallRequest` rows and returns `statusId = AI_RUN_SUSPENDED`, `awaitingApproval = true`, `toolCallRequestIds = [...]`.
- On `resume()`, the precondition is **fail-closed across the whole turn**: if *any* tool call is neither `AI_TCREQ_APPROVED` nor `AI_TCREQ_REJECTED`, the entire turn stays suspended. You cannot partially execute an approved turn.
- **Preview mode** treats *every* mutating tool as requiring approval, so a draft agent can be sandbox-run safely.
- `AiToolDenylist` provides a hard blocklist; `requiresApprovalOverride` grants exist per agent.

Human approval is enforced in code with a documented rationale, not by convention.

### 9.4 Audit and memory

`AiAgentRun`, `AiAgentRunStep`, `AiToolCall`, `AiToolCallRequest`, `AiConversation`, `AiConversationMessage`, `AiConversationFact`, `AiModelPrice`, `AiAgentModel`, `AiAgentTool`, `AiAgentKnowledge`, `AiKnowledgeTopic`, `AiCapabilityRequest`, `AiDomainTerm`, `AiTermSynonym`, `AiNamingSignal` — 19 entities.

Every run persists steps, token counts (`tokensIn`/`tokensOut`), `iterations`, `truncated`, `servedByModelId`, `providerName`, `providerRunId`, and `estimatedCost`. Cost is a first-class product concern, not an afterthought.

### 9.5 Authoring surface

Composer services author agents rather than run them: `find/describe#Capability`, `list#DomainTerm`, `propose#Naming`, `set#Guardrail`, `request#Capability`, `enhance#Instructions`, `preview/activate#Agent`, `discard#Draft`, plus glossary governance (`seed/find/capture/promote/list/propose/store/approve/reject#Glossary`) and `approve/reject/decide#getPending` on `ToolCallServices`.

MCP is bidirectional: `McpServices.dispatch#Request`, `get/build#ToolCatalog`, `exec#Tool`; `McpMethod` discover/init/list/call.

---

## 10. `maarg-util` (785 files, 24 custom sources) — the Platform Layer

Despite the name, this is infrastructure, not utilities: `UtilityServices.xml` (1,233 lines), `admin.rest.xml`, `AuthServices.xml`, `JobServices.xml` (`run#ServiceJob`, `get#ServiceJobDetail`, `get#ServiceJobs`, `update#ServiceJob`, `clone#ServiceJob`), `SearchServices.xml`, `SolrServices.xml`, `SolrSchemaServices.xml`, `UserServices.xml`, `AgentControlServices.xml`, `NiFiCommunicationServices.xml`, `HttpServices.xml`, `SftpMessageServices.xml`, `ServerServices.xml`.

Two details worth noting:

- **`maarg-util.mcp.xml`** — the platform itself exposes MCP. So does `oms` (`oms.mcp.xml`).
- `JobServices` provides clone/update/get — a managed job-definition model rather than ad-hoc cron config. Combined with the 10-hour routing transaction and the 24-retry message sweeper, HotWax has a real scheduler.

---

## 11. `gql` — `moqui-gql` 1.1.0 (173 files, 57 custom sources)

The heaviest custom-code component, and it depends on `shopify-connector` — the API surface is not channel-neutral. `gql.mcp.xml` exposes the GraphQL layer over MCP.

This is strategically significant: HotWax already has an MCP surface, so "Nexus needs MCP" is not a differentiator by itself. The differentiator would be a **channel-neutral, permission-aware** one — `gql` inheriting Shopify's data shape is the weakness to target.

---

## 12. Remaining Components

| Component | What it is |
|-----------|-----------|
| `ofbiz-oms-udm` | 278-entity unified data model, 277 unique names (§2) |
| `hotwax-moqui-firebase` | `moqui-firebase` 2.0.0-RC1, no deps; `HwmappsFirebaseEntitymodel.xml` |
| `oms-test` | 328 files of test fixtures/services against `oms` |
| `lovers` | Named customer deployment; the only component depending on poorti + shopify-connector + oms together — a reference multi-channel implementation |
| `adoc-maarg` | Named customer deployment; shopify-connector + oms, no poorti (no WMS) |
| `maarg-docker-config` | Deployment config only; no `component.xml` |

`lovers` and `adoc-maarg` are the clearest evidence of the product/customer boundary problem: two customers' customizations are versioned in the same tree as the product, and neither is declared as a plugin of a customer-extension layer.

### Core `oms` service inventory

`OrderServices.xml` (3,267 lines, 42 services) — `create#SalesOrder`, `prepare#SalesOrderShipGroups`, `approve#Order`, `reserve#OrderItems`, `reject#OrderItems`, `complete#SalesOrderItemByShipment`, `change#OrderStatus`, `split#OrderASNItems`, `send#OrderWebhook`, `check#OrderItemAndCapturePayment`, plus a full analytics/dashboard set (`get#FulfillmentProgress`, `get#FunnelDashboardOpenOrders`, `get#FacilityFulfillmentVelocity`, `get#FacilityOrderVolume`, `get#OrderHoldTaskCounts`).

Also: `FacilityServices.xml` (561), `InventoryServices.xml`, `ReturnServices.xml`, `AtpServices.xml`, `OrderReconServices.xml` + `ReturnReconServices.xml`, `OrderRiskServices.xml`, `SearchServices.xml`, `FulfillmentOrderServices.xml`, `OrderReservationServices.xml`, `DecisionRuleServices.xml`, `TransferOrderServices.xml`, `PurchaseOrderServices.xml`, `MigrationServices.xml`, `WebhookServices.xml`, `UnigateServices.xml`, `CarrierServices.xml`, plus `oms.rest.xml` (1,398 lines), `oms.mcp.xml`, `oms.secas.xml`.

Core `oms` is broader than "just an order core" — it contains ATP, returns, reconciliation, risk, and decision rules. It is simply *not* a WMS, and it does not buy labels.

---

## 13. End-to-End Flow

```
Shopify order
   │  webhook: verify#Hmac → receive#WebhookPayload → queue#…SystemMessage
   ▼
oms: create#SalesOrder → prepare#SalesOrderShipGroups → approve#Order
   │
   ├── order-routing: run#OrderRoutingGroup  (10h batch tx, per-rule isolation)
   │       EligibleOrdersQuery.sql.ftl → OrderRoutingRule cascade
   │       InventorySourceSelector.sql.ftl  → ranked facilities
   │       ▼
   │   oms: OrderReservationServices.process#OrderFacilityAllocation (120s)
   │       └── process#OrderItemAllocation → reserve#SoftAllocatedInventory
   │
   ▼
poorti: create#OrderFulfillmentWave
   │       ├── create#SalesOrderShipment  (skips if already APPROVED/PACKED/SHIPPED)
   │       ├── create#…Picklist + PicklistShipment + pickers
   │       └── Solr fulfillmentStatus = InProgress
   │   handle#OrderFulfillmentWave → do#RateShopping   [force-new per shipment]
   │       └── get#ConfiguredCarrierService → ShipmentRequest | SystemProperty
   │             └── UnigateShippingServices (1887 lines) → send#UnigateRequest
   │
   ├── pack#Shipment(s) → ship#Shipment(s) → print#Label / print#Manifest
   │
   ▼
shopify-connector: generate#OMSFulfillmentFeed → post#ShopifyFulfillment
   │   └── Shopify ack → consume#ShopifyFulfillmentAckFeed
   ▼
networking
   ▼
netsuite-connector: generate#OMSFeed → consume#NetsuiteFeed
   (via poorti transfer-order model, not directly via oms)
```

**All outbound edges traverse the SystemMessage bus**, so every arrow above is durable and retryable.

---

## 14. Findings for Nexus

**What HotWax genuinely does well — do not pretend otherwise:**

1. Durable integration bus with a day-long retry budget and dead-lettering.
2. Configuration-driven gateway dispatch; adding a carrier is config, not a fork.
3. Reusable rules-engine pattern (routing and picking share a shape).
4. AI with fail-closed, whole-turn approval gates and full per-step audit.
5. Genuine WMS depth: wave picking, packing, manifesting, cycle counts, transfer orders.
6. Compiled-SQL debugging endpoints for configuration-driven logic.

**Where the architecture is genuinely exposed — and where Nexus can win:**

1. **No product/customer boundary.** `lovers`, `adoc-maarg`, and `poorti`'s own nested `.git` sit in the product tree. `netsuite-connector` depends on the WMS. This is upgrade pain, and it is structural.
2. **Split-brain shipping configuration.** *Which* gateway code runs (`ShipmentRequest`/`SystemProperty`) is separated from *which credentials* it uses (`ShippingCarrierConfig` → `ShippingGatewayAuth`), and the gateway itself is a 1,887-line XML service. A typed Java adapter with credentials in one place is a real architectural improvement — **not** a capability HotWax lacks.
3. **Incomplete migration debt.** Duplicate `sob`/`soblegacy`/`shopify` namespaces and duplicated Groovy helpers.
4. **Configuration-shaped complexity.** Routing is 10-hour batch transactions and dynamic SQL; a customer's bug is a customer's SQL. Support cost scales with configuration surface.
5. **Channel-coupled API *and* a channel-contaminated kernel.** `gql` depends on `shopify-connector`, and 14 Shopify entities sit inside `ofbiz-oms-udm` — the shared foundation every component inherits. The coupling is not just a dependency-graph smell; it has leaked into the data model itself. This is the strongest architectural argument Nexus has, and it is a *design* argument, not a feature gap.
6. **Data-model defects in the shared kernel.** `ProductType` is declared twice in `DatamodelProductEntitymodel.xml` (lines 1029 and 1071), the second without the 8-record `<seed-data>` block, while 15 source files across four components reference that entity. Fourteen of the 30 model files ship unimplemented `view-entity` placeholders. A 278-entity foundation with no schema-migration discipline will accumulate exactly this class of defect — and every downstream component inherits it.
7. **Unauthenticated endpoints.** Three `authenticate="false"` services in `poorti`'s `FulfillmentServices.xml` — `encode#Base64` (`:1840`), `encode#ReturnLabelBase64` (`:1870`), and `create#ShipmentPackage` (`:1894`). The third is a write endpoint that auto-populates a `ShipmentPackage` from the request with only `dateCreated` excluded, so it is the one to raise in a competitive conversation.
8. **MCP is not a differentiator.** `maarg-util.mcp.xml` and `oms.mcp.xml` already exist.

---

## 15. Corrections to Earlier Assumptions in This Project

Recorded deliberately, because this project previously shipped a wrong claim:

1. **"HotWax cannot generate carrier labels."** **False.** `poorti` implements the full rate-shop → label → void/retry → manifest → PDF lifecycle. Already corrected in `nexus-vs-hotwax-technical.md`. T-12's business case rests on a real SoC/Jitsu deadline, not on missing capability.
2. **"Unigate is only in customer components."** **Incomplete.** The *lifecycle* is Poorti-only, but core `oms` owns `send#UnigateRequest`, the `ShippingGatewayAuth`/`CommGatewayAuth` credential registry, and tenant administration. §6.4 is the corrected, more precise account.
3. **"`maarg-util` has no dependencies."** **False** — it declares `depends-on name="moqui-fop"`.
4. **"Moqui services are declared as `<service name="…">`."** **False** — separate `verb` and `noun` attributes.
5. **"`poorti` is a thin wrapper."** **False** — it is 944 files and ~100% of the WMS/fulfillment implementation.

---

## 16. Evidence Index

| Claim | File |
|-------|------|
| 41 fulfillment services | `poorti/service/co/hotwax/poorti/FulfillmentServices.xml` |
| Wave creation + idempotency | same, `:234-352` |
| Reinitialize/void logic | same, `:354-387` |
| Shipping dispatch table | `poorti/service/co/hotwax/poorti/shipping/ShippingServices.xml` |
| Dispatch resolution order | `poorti/service/co/hotwax/poorti/PoortiHelperServices.xml` |
| Unigate lifecycle + credentials | `poorti/service/co/hotwax/poorti/shipping/UnigateShippingServices.xml` |
| Carrier credential config | `poorti/entity/FulfillmentEntities.xml` |
| Transfer-order lifecycle | `poorti/service/co/hotwax/poorti/TransferOrderFulfillmentServices.xml` |
| Routing cascade + 10h tx | `order-routing/service/co/hotwax/order/routing/OrderRoutingServices.xml` |
| Routing entities | `order-routing/entity/OrderRoutingEntities.xml` |
| Allocation + soft reservation | `oms/service/co/hotwax/oms/impl/OrderReservationServices.xml` |
| Message bus + retry budget | `maarg-util/service/org/moqui/impl/SystemMessageServices.xml` |
| Job model | `maarg-util/service/co/hotwax/job/JobServices.xml` |
| Shopify webhooks + HMAC | `shopify-connector/service/co/hotwax/shopify/webhook/ShopifyWebhookServices.xml` |
| Shopify bulk operations | `shopify-connector/service/co/hotwax/shopify/system/ShopifySystemMessageServices.xml` |
| Fulfillment ack loop | `shopify-connector/service/co/hotwax/sob/fulfillment/FulfillmentFeedServices.xml` |
| NetSuite feed spine | `netsuite-connector/service/co/hotwax/system/FeedServices.xml` |
| Unigate transport + credential registry | `oms/service/co/hotwax/unigate/UnigateServices.xml` |
| AI agent loop + approval gates | `ai/src/main/groovy/org/moqui/ai/AgentRunner.groovy` |
| AI provider abstraction | `ai/src/main/groovy/org/moqui/ai/provider/` |
| AI tool definitions | `ai/src/main/groovy/org/moqui/ai/DefinitionLoader.groovy` |
| AI shim service | `ai/service/ai/AgentServices.xml` |
| AI data model (19 entities) | `ai/entity/AiEntities.xml` |
| Data model (278 declarations / 277 unique) | `ofbiz-oms-udm/entity/*.xml` |
| `ProductType` duplicate declaration | `ofbiz-oms-udm/entity/DatamodelProductEntitymodel.xml:1029` and `:1071` |
| Shopify entities inside the neutral UDM | `ofbiz-oms-udm/entity/ShopifyConnectorEntitymodel.xml` |
| Unimplemented view-entity placeholders | `ofbiz-oms-udm/entity/Oms*ViewEntities.xml`, `ServiceJobViewEntities.xml` |
| Component versions/deps | `<component>/component.xml` |
