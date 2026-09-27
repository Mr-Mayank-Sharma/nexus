# HotWax vs Nexus — Order Routing, Shopify & NetSuite Integration

> **Purpose:** A source-verified, line-level comparison of how HotWax and Nexus actually route an order, and how each side integrates with Shopify and NetSuite. Every claim below cites a file and line range from the two codebases.
>
> **HotWax source:** `/Users/mayanksharma/Desktop/hotwaxoms/moqui2/runtime/component/order-routing`
> **Nexus source:** `/Users/mayanksharma/Desktop/nexus/nexus-oms-backend/src/main/java/com/nexus/oms`
>
> **Companion docs:** [`nexus-market-gap-tickets-3.md`](./nexus-market-gap-tickets-3.md) (T-14 → T-18) · [`nexus-vs-hotwax-technical.md`](./nexus-vs-hotwax-technical.md)

---

## 1. Executive Summary

| Area | HotWax | Nexus | Verdict |
|------|--------|-------|---------|
| Routing architecture | Config-driven SQL allocator | 3 Java strategies, one of which is a no-op fallback | HotWax wins decisively |
| Allocation dimensions | ~11+ (facility group, brokering, substitutes, distance, week-of-supply, velocity, broken style, order caps, promise cutoff, exclusions) | 3 (order total, region, priority) | HotWax |
| Split / multi-node allocation | Greedy, stock-aware, session-variable state machine | Splits by arithmetic only, never checks stock | HotWax |
| Distance | `ST_Distance_Sphere` on real lat/long | Hardcoded `50.00` or `500.00` km | HotWax |
| Shipping cost | Real carrier rate-shopping result | `$5.00 + 5% of order total × qty` | HotWax |
| Delivery confidence | Derived from allocator state | Hardcoded `0.65` / `0.75` / `0.85` | HotWax |
| Backorder handling | Yes, explicit flag | No | HotWax |
| Shopify integration | Config-driven component | 1,817 LOC, real Admin API + webhooks | **Nexus wins** |
| NetSuite integration | Shipped component | **Does not exist** (registry slot only) | HotWax |
| Sync conflict handling | — | 345 LOC, field-level, real | **Nexus wins** |

**The headline:** Nexus has better-built *connectors* than HotWax, and a substantially weaker *router*. The routing engine is the problem, not the integration framework.

---

## 2. HotWax Order Routing — What Actually Happens

HotWax routing is a **two-query pipeline**. The allocation itself is executed by the database, not by application code.

### 2.1 Step 1 — Which orders deserve attention?

`OrderRoutingServices.xml:952` `get#OrderFilterSql` loads the `OrderRouting` record, its `statusId`, and the parent `OrderRoutingGroup`. It then reads `OrderFilterCondition` rows, splits them into `ENTCT_FILTER` (WHERE clauses) and `ENTCT_SORT_BY` (ORDER BY), and returns generated SQL.

`sql/EligibleOrdersQuery.sql.ftl` builds that query. It joins:

- `ORDER_HEADER` (`OH`)
- `PRODUCT_STORE` (`PS`) — carries `ENABLE_BROKERING`
- `ORDER_ITEM_SHIP_GROUP` (`OIS`)
- `ORDER_ITEM` (`OI`)
- optionally `PRODUCT_CATEGORY_MEMBER` and `FACILITY_GROUP_MEMBER`

Every condition is emitted through `co.hotwax.order.routing.OrderRoutingHelper.makeSqlWhere(...)`, so the filter is **data, not code** — a planner changes routing behavior by editing rows.

### 2.2 Step 2 — Which facility, for which item, in what order?

`OrderRoutingServices.xml:1028` `get#OrderRoutingRuleSql` returns the matching `InventorySourceSelector.sql.ftl` query.

This is the interesting part. HotWax implements a **greedy multi-warehouse allocator as a single MySQL statement**, using session/user variables as mutable state:

| Variable | Meaning |
|----------|---------|
| `@rn` | Row counter — walks candidate facility×item rows in order |
| `@rtd` | Routed item quantity for the row currently being evaluated |
| `@fpuim` | Facility-Product-Unallocated-Inventory map, CSV-encoded as `facilityId-productId:qty` |
| `@ps` | Partial-allocation item map, `orderId-orderItemSeqId:qty` |
| `@s` | Allocated-item set, `orderId-orderItemSeqId` |
| `@fom` | Facility-Order map — enforces per-facility daily order caps |
| `@aq` | Allocated order quantity |
| `retained` | Set to 1 if anything was allocated at all |

Because MySQL evaluates `SELECT` expressions in written order, and `ORDER BY` fixes the row sequence, the outer query walks candidate facilities in priority order and **mutates state as it goes** — decrementing available inventory, marking items allocated, and refusing rows that would exceed a facility's cap. That is a stateful allocator expressed as SQL.

### 2.3 The allocation policy is the ORDER BY

Rows are ranked, in order:

1. Whatever the admin configured in `inventorySortByList`
2. `rank_by_item_cnt_above_threshold DESC`
3. `availablity_pct DESC`
4. `facility_id` — with the source comment *"to avoid random sorting"*

**The sort order *is* the routing policy.** An operator changes routing behavior by reordering a list in the admin UI — no deploy, no code.

### 2.4 What HotWax filters on before allocating

- `ALLOW_BROKERING='Y'` per `product_facility` — per-facility brokering opt-in
- **Product substitutes** — available ATP is `GREATEST(last_inventory_count - minimum_stock, substitute_stock)` via `PRODUCT_SUBSTITUTE` / `product_assoc`
- **Facility order limits** — `facility_order_count` tracked in `@fom`, capped by `maximum_order_limit`, reset daily via `entry_date` + UTC date conversion
- **Real geo distance** — `ST_Distance_Sphere(LONGITUDE(…), LATITUDE(…))`, switchable miles/km
- **Facility groups** — `BROKERING_GROUP` membership
- **Manual exclusions** — `excluded_order_facility`
- **Week of supply** and **sales velocity** as ranking inputs
- **Broken style** — incomplete-assortment handling
- **Promise-date cutoff** — `promiseDaysCutoff` routes at the order-**item** level rather than the whole order
- **`ORA_SINGLE` vs `ORA_MULTI`** — keep an entire ship group at one facility, or split it
- **Backorder flag** when no candidate rows qualify

### 2.5 HotWax action application

`OrderRoutingServices.xml:259` `run#OrderRouting` → `:449` `run#OrderRoutingRule` → `:539` `eval#OrderRoutingActions`. Rules are `OrderRoutingRule` (`:114`) with inverse conditions `OrderRoutingRuleInvCond` (`:147`) and actions `OrderRoutingRuleAction` (`:167`). Filters (`:94`) and groups (`:22`) are first-class entities.

---

## 3. Nexus Order Routing — What Actually Happens

`service/OrderRoutingService.java`, 732 lines. `allocateOrder(AllocationRequest)` (`:73`) dispatches on `strategy` (`:100`):

```java
case "RULE_BASED"  -> ruleBasedAllocation(...)
case "AI_OPTIMIZED"-> aiOptimizedAllocation(...)
case "HYBRID"      -> selectBestAllocations(ruleAllocs, aiAllocs, threshold)
```

### 3.1 RULE_BASED — rules are evaluated, but they bypass inventory entirely

`ruleBasedAllocation` (`:213`) is better than it first appears, and worth stating precisely:

```java
List<NxRoutingRule> rules = routingRuleRepository.findByTenantIdAndIsActiveTrue(tenantId);  // :214
...
if (!conditionEvaluator.evaluate(rule, order)) continue;                                   // :223
JsonNode actions = conditionEvaluator.parseJson(rule.getActions());                        // :225
if (actions == null || !actions.has("nodeIds")) continue;
...
allocations.add(buildAllocation(order, tenantId, wh, rule, "RULE_BASED"));                // :232
if (!allocations.isEmpty()) break;                                                         // :237
```

So rules **are** consulted, and the first matching rule wins outright. But:

**A rule can only express three conditions** — `RuleConditionEvaluator.java:40-72`:
- `maxTotal` / `minTotal` (order total range)
- `regions` (ship-to state, falling back to country)
- `priority` (read out of `order.metadata.priority` JSON)

**Then `buildAllocation` (`:643`) marks the node ALLOCATED with no stock check:**

```java
int qty = estimateOrderQuantity(order);
return NxOrderAllocation.builder()
        .quantityAllocated(qty)
        .quantityRequested(qty)
        .status("ALLOCATED")     // unconditional
        .priority(1)             // always 1
        .costEstimated(estimateShippingCost(wh, order, qty))
        .distanceKm(estimateDistance(wh, ...))
        .build();
```

**Bug — N× over-allocation.** If a rule's `nodeIds` lists 3 warehouses, each receives `quantityAllocated = quantityRequested =` the **full order quantity**. The order is triplicated across three nodes. `canFulfill` is never consulted on this path.

### 3.2 Fallback — `createDefaultAllocation` (`:404`) is `findFirst()`

When no rule matches, or no rules exist:

```java
Warehouse best = warehouses.stream()
        .filter(wh -> canFulfill(order, wh, demanded))
        .findFirst()
        .orElse(warehouses.get(0));     // :418-420
```

`best` is a misnomer — it is the **first** warehouse in repository order that can fulfill, with no scoring, no sorting, no ranking. `canFulfill` (`:462`) requires **all** items via `allMatch`, so a genuinely split order (no single warehouse has everything) matches nothing and falls to `.orElse(warehouses.get(0))` — a warehouse just proven incapable.

### 3.3 AI_OPTIMIZED — there is no AI

`aiOptimizedAllocation` (`:245`) is a hand-tuned constant score. No model, no inference, no external call.

```java
BigDecimal base = scoreNodeForOrder(wh, order, shipRegion);   // :258
if (fulfillsAll)          base = base.add(40);
else if (canFulfillAny)   base = base.add(10);
else                      base = base.subtract(50);
```

`scoreNodeForOrder` (`:474`) is pure magic numbers:

| Signal | Points |
|--------|--------|
| Base | 50 |
| Warehouse state == ship-to state | +30 |
| Both US (different state) | +15 |
| Utilization < 85% | +10 |
| Utilization > 95% | −20 |
| Status ACTIVE | +10 |

It sorts descending, **`.limit(3)`** (`:268`), then splits the order quantity arithmetically:

```java
int allocQty = (i == scored.size() - 1) ? remaining
              : Math.max(1, remaining / (scored.size() - i));   // :272
```

**Bug — no stock check on split.** After scoring, the per-node quantity is pure division. It allocates qty to a node that `canFulfill` may have already failed, and still marks it `ALLOCATED`.

### 3.4 HYBRID — comparing two fabricated confidences

`selectBestAllocations` (`:599`) averages the `deliveryPromiseConfidence` of each strategy's allocations and picks the AI set if it beats the rule set and clears a 0.70 threshold (`:115`).

Since both sets' confidences are hardcoded (`0.75` base, `0.85` for AI, `0.65` for the default path), **HYBRID is choosing between two constants.** `calculateHybridConfidence` (`:627`) weights them 40/60 — also a constant.

### 3.5 The fabricated measurements

| Field | Implementation | Line |
|-------|----------------|------|
| `distanceKm` | `50.00` if same state, else `500.00` | `:514` |
| `costEstimated` | `$5.00 + 5% of order total`, × quantity | `:508` |
| `deliveryPromiseConfidence` (default) | hardcoded `0.6500` | `:429` |
| `calculateNodeConfidence` | `0.75` base, +0.10 ACTIVE, −0.15 if util > 90% | `:527` |

None of these consult the carrier, the distance, or the allocator state.

### 3.6 What is missing entirely vs HotWax

| Capability | HotWax | Nexus |
|------------|--------|-------|
| Inventory decrement / reservation | Yes (`@fpuim` mutation) | **No** — nothing reserves stock |
| Backorder flag | Yes | No |
| Product substitutes | Yes | No |
| Facility daily order cap | Yes (`@fom`) | No |
| Manual facility exclusion | Yes | No |
| Facility groups / brokering groups | Yes | No |
| Real geo distance | `ST_Distance_Sphere` | Fake constant |
| Week of supply / sales velocity | Ranking inputs | No |
| Broken style | Handled | No |
| Promise-date cutoff | Item-level routing | No |
| `ORA_SINGLE` / `ORA_MULTI` | Explicit | Implicit, unchecked |

**`BrokeringService` (386 lines) is a different concern** — a persisted work queue with `WAITING`/`RUNNING` states, priority processing, and `NxBrokeringQueue` persistence. It manages *who processes* a brokering task; it does not decide *which facility* to allocate from. It should not be mistaken for an allocation engine.

---

## 4. Side-by-Side: One Order Through Both Systems

**Scenario:** Order with 3 line items. Warehouse A has all 3. Warehouse B has item 1 only and is 40 km from the ship-to. A rule routes to `[A, B]`.

### HotWax

1. `get#OrderFilterSql` → `EligibleOrdersQuery.sql.ftl` matches the order (status, ship-group, product store, optional category/facility filters).
2. `get#OrderRoutingRuleSql` → `InventorySourceSelector.sql.ftl` emits one statement.
3. Candidate rows expand the order × all eligible facilities.
4. ATP per facility = `GREATEST(last_inventory_count − minimum_stock, substitute_stock)`, gated by `ALLOW_BROKERING`.
5. Rows sort by configured policy — say distance first: **B (40 km) before A (120 km)**.
6. `@rn` walks rows: B is nearest, allocates item 1 (its only stocked line), decrements `@fpuim`, marks the item in `@s`.
7. Items 2 and 3 fail at B; the next rows in sort order reach A, which stocks both — allocated, `@fpuim` decremented.
8. `@fom` enforces B's daily cap; if B is over cap, its rows are skipped.
9. `ORA_MULTI` permits the split → **order splits B(item 1) + A(items 2,3)**, each with true distance, real cost from rate-shopping, and allocator-derived state.
10. Actions persist; if nothing matched, backorder is flagged.

### Nexus

1. `allocateOrder` (`:73`) requires `PENDING` or `CONFIRMED`, then dispatches on strategy.
2. **RULE_BASED:** the first active rule whose three conditions match wins (`:223`).
3. Its `actions.nodeIds` is `[A, B]` → **`buildAllocation` is called for both.**
4. Each is built with `quantityAllocated = quantityRequested =` the **full order quantity** and `status="ALLOCATED"`. No `canFulfill` call on this path.
5. Result: **the entire order is allocated to A *and* the entire order to B.** Quantity doubles.
6. `distanceKm` = 500 for A, 50 for B — two constants, not distances.
7. `costEstimated` = `$5.00 + 5% of order total`, twice.
8. If no rule matches, `createDefaultAllocation` → `.findFirst()` on a repository-ordered list, or `.orElse(warehouses.get(0))` if the order genuinely needs a split.

**Under `AI_OPTIMIZED`:** A scores 50+30(state)/+15(US)+10(util)+10(ACTIVE)+40(fulfillsAll); B scores similarly but +10 instead of +40. Sorted desc, `limit(3)`, quantity split by `remaining / (3 − i)` — arithmetic, not stock. Both marked ALLOCATED.

---

## 5. Shopify Integration

### Nexus — the strongest connector work in the codebase

1,817 LOC across 10 files, and it is **real**:

| File | LOC | Role |
|------|-----|------|
| `service/shopify/ShopifyOrderImportService.java` | 350 | Inbound order import |
| `service/shopify/ShopifyProductSyncService.java` | 238 | Catalog push/pull |
| `service/shopify/ShopifyFulfillmentPushService.java` | 200 | Fulfillment → Shopify |
| `service/shopify/ShopifyClient.java` | 182 | REST client |
| `service/shopify/ShopifyInventorySyncService.java` | 171 | Level → Shopify |
| `service/shopify/ShopifyRefundSyncService.java` | 131 | Refund sync |
| `service/shopify/ShopifyWebhookService.java` | 115 | Webhook registration |
| `controller/ShopifyController.java` | 98 | HTTP surface |
| `service/shopify/ShopifyTokenService.java` | 78 | Token lifecycle |
| `entity/NxShopifyWebhook.java` | 46 | Webhook persistence |

`ShopifyClient.java:25-28` builds a real Admin API client against the tenant's own shop domain with an `X-Shopify-Access-Token` header, and `:86`/`:89` register and delete webhooks against `/webhooks.json`.

**Assessment:** genuinely implemented, and ahead of HotWax's config-driven component in terms of dedicated domain logic.

### HotWax

A `shopify-connector` Moqui component — 3 source files, configuration-driven, with the shop-credential and import/export definitions living in component data rather than Java services.

**Verdict:** Nexus is stronger on code depth; HotWax is easier to reconfigure per tenant without a deploy.

---

## 6. NetSuite Integration

### Nexus — it does not exist

`grep -ril 'netsuite'` across the entire source tree returns **two files**, and neither is an implementation:

- `integration/core/ConnectorFactoryConfiguration.java:64-65` — registers a *factory slot*:
  ```java
  registry.registerFactory("NETSUITE", createGenericFactory("NETSUITE", "NetSuite", "Oracle", "ERP",
          "Oracle NetSuite SuiteTalk REST API"));
  ```
  This is a placeholder wired to the generic HTTP factory. No SuiteTalk call, no token exchange, no entity mapping.
- `service/SyncConflictService.java:32,290` — **comments only**, describing a past NetSuite bug ("the OMS got UNDONE when NetSuite fed back stale data") and a NetSuite transaction type (`FRTTRA`).

There is no `NetSuiteConnector`, no `NetSuiteClient`, no NetSuite service, entity, or repository — despite 14 sibling connectors existing for SAP, QuickBooks, Stripe, Salesforce, Twilio, Okta, OpenAI, Amazon, BigCommerce, Magento, FedEx, and the generic HTTP connector.

### The sting in the tail

`SyncConflictService.java` is **345 lines of real, working conflict logic** — `detectConflict` (`:73`), `resolveFieldWinners` (`:110`), `resolveConflict` (`:137`), `recoveryJob` (`:179`), `getReconciliationReport` (`:255`), `handleUnsupportedTransaction` (`:293`).

The conflict-resolution layer was built, tested against a NetSuite workload, and **the ERP side it was designed to protect was never written.** The generic factory slot means a NETSUITE connector would resolve today — and would do nothing real.

### HotWax

A `netsuite-connector` Moqui component with 3 source files, including a custom document-generation and field-transformation layer for pushing NetSuite custom fields.

**Verdict:** HotWax ships the capability; Nexus ships the conflict plumbing for a customer that does not exist.

---

## 7. Conclusions

1. **The routing engine is the critical gap.** HotWax runs a stock-aware, config-driven, multi-warehouse allocator. Nexus has a rule short-circuit, a `findFirst()`, and a constant-scoring function that splits quantities without checking stock — and marks everything `ALLOCATED` regardless.
2. **Two allocation bugs ship today.** The rule path multiplies full order quantity across every node in `nodeIds`; the AI path splits by arithmetic after scoring, bypassing the stock check it just performed.
3. **Three of Nexus's four routing measurements are constants.** Distance, cost, and confidence are not derived from anything the system knows.
4. **NetSuite is a P0 lie of omission.** A registry slot, a comment about a past bug, and 345 lines of conflict resolution with no connector behind them. Any "we support NetSuite" claim is currently unsupportable.
5. **Shopify is the counter-example.** It proves the team can build real integrations when scoped to one system — the connector framework works and is used (`WebhookIntegrationController`, `IntegrationHubController`, `BatchJobService`). The routing gap is about algorithm depth, not architectural incapacity.
