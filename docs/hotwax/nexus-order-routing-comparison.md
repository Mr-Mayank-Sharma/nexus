# HotWax vs Nexus — Order Routing, Shopify & NetSuite Integration

> **Purpose:** A source-verified, line-level comparison of how HotWax and Nexus actually route an order, and how each side integrates with Shopify and NetSuite. Every claim below cites a file and line range from the two codebases.
>
> **HotWax source:** `/Users/mayanksharma/Desktop/hotwaxoms/moqui2/runtime/component/order-routing`
> **Nexus source:** `/Users/mayanksharma/Desktop/nexus/nexus-oms-backend/src/main/java/com/nexus/oms` — this is the `main` worktree at `da7bb5e`, which is the baseline the body of this document audits. See the status note below for how `fix/t17-allocation-correctness` (`/Users/mayanksharma/Desktop/nexus-t17`) changes it.
>
> **Companion docs:** [`nexus-market-gap-tickets-3.md`](./nexus-market-gap-tickets-3.md) (T-14 → T-18) · [`nexus-vs-hotwax-technical.md`](./nexus-vs-hotwax-technical.md)

---

> ## ⚠️ Status — this document describes commit `da7bb5e`, not the T-17 branch
>
> Every line reference below was verified against `OrderRoutingService.java` **at commit `da7bb5e`** (732 lines). It was accurate as written for that baseline.
>
> Branch **`fix/t17-allocation-correctness`** (worktree `nexus-t17`, HEAD `da7bb5e`) rewrites large parts of the router — `OrderRoutingService.java` grows **732 → 1029 lines** (+421/−124). The branch is currently **uncommitted working-tree changes**: 11 files, +1326/−178 — 5 production, 4 test, 2 documentation. The changes sit on top of HEAD `da7bb5e`, which already includes the merged commit `90bde84`. **Sections 1, 3.1–3.6, 4 and the summary table are superseded** and must not be cited as current Nexus behaviour.
>
> ### Claims that T-17 invalidates
>
> | Section | Claim at `da7bb5e` | Reality on `fix/t17-allocation-correctness` |
> |---------|--------------------|------------------------------------------|
> | 3.1, 3.3, 4 | Splits by arithmetic, **never checks stock** | `allocateStockAware(...)` (`:551`) walks candidates and calls `inventoryService.resolveStock(...)` (`:577`); `canFulfill` / `canFulfillAny` gate candidates |
> | 3.6, summary | Backorder flag: **No** | Explicit backorder rows via `findBackorder` / `backorderAllocation`, plus `PARTIALLY_ALLOCATED` / `FAILED` order resolution and a raised exception |
> | 3.6, summary | Inventory decrement / reservation: **No — nothing reserves stock** | Consumed on the winning node (commit `90bde84`) |
> | 3.2, 3.3 | `createDefaultAllocation` is a no-op `findFirst()` | Retained at `:620`, but is now a **fallback** — both `RULE_BASED` (`:359`) and `AI_OPTIMIZED` (`:405`) route through `allocateStockAware` first |
> | 3.4, 3.5 | Delivery confidence is hardcoded `0.65` / `0.75` / `0.85` | **Superseded** — the order-level `confidenceScore` is no longer a per-strategy constant at all. It is derived by `derivePlanConfidence` (`:795`) from coverage, node health, capacity saturation and promise slack. The per-node `deliveryPromiseConfidence` remains formula-based (`calculateNodeConfidence`, `:893`) and is still synthetic — see §3.6 |
>
> ### Still-valid claims (do **not** discount these)
>
> Not everything in §3.1–3.6 is obsolete. Two findings survive on this branch unchanged:
>
> - **§3.3 "there is no AI" is still true.** `AI_OPTIMIZED` only reorders candidates before handing them to `allocateStockAware` (`:405`); the router contains no model call, no `AiService`, no inference. The only "AI" in `OrderRoutingService.java` is the cosmetic explanation string `"Allocated using AI optimization model"` (`:107`). Despite the strategy name, the scoring is the same hand-tuned `scoreNodeForOrder` heuristic.
> - **§3.5's synthetic distance / cost criticism still stands.** `estimateDistance` (`:762`) still returns a hardcoded `50.00` / `500.00` and `estimateShippingCost` (`:753`) a flat rate, neither consulting a carrier. Unchanged by T-17.
> - **§3.6's *node*-confidence criticism still stands, in a narrower form.** `calculateNodeConfidence` (`:893`) is still a hand-tuned formula over status and capacity. T-17 stopped letting that synthetic number stand in for the order-level score — but it is still what populates `deliveryPromiseConfidence` on each allocation row, so it is still persisted and still shown.
>
> ### T-17 bug inventory (all fixed on this branch)
>
> The router carries three in-code bug labels plus two further defects found during T-17 review:
>
> | Label | Site | Defect | Fix |
> |-------|------|--------|-----|
> | **Bug A** | `:338` | Rule path called `buildAllocation()` once per node, and `buildAllocation()` assigned the **full** order quantity to each — a rule listing three nodes over-committed the order 3× and blew up later at reservation time | Rules now only produce an **ordered candidate list**; the shared allocator decides quantity |
> | **Bug B** | `:397` | AI path split demand as `remaining / remainingNodes` with a `Math.max(1, …)` floor, so a node that **failed** its stock check still received an allocation and the split could exceed what any node held | Score only orders candidates; `allocateStockAware` caps every node at real availability |
> | **Bug C** | `:626` | Fallback used `.orElse(warehouses.get(0))`, so an order **no warehouse could fulfil** was still committed to warehouse #1 as a full `ALLOCATED` row and only failed at reservation time | All active nodes become candidates; falls back to an explicit backorder |
> | *(unlabelled)* | `calculateDeliveryPromise` `:787` | Any `FAILED` row suppressed the promise, so **every partial allocation** shipped a null delivery date | Filter to `status == "ALLOCATED"` |
> | *(unlabelled)* | `confidenceScore` (former `calculateHybridConfidence`) | **Resolved by removal, not by patching.** The first fix filtered the backorder row out of `averageConfidence`'s denominator. The better fix was to delete the blend: the 40/60 hybrid re-used the same `calculateNodeConfidence` evidence that `derivePlanConfidence` already consumes, so node health was counted twice while the two real risks — how much of the order is sourced, and how much slack the promise has — were counted not at all | `calculateHybridConfidence` deleted. `confidenceScore` is now `derivePlanConfidence` (`:795`), which scores coverage, node health, capacity saturation and promise slack directly. `averageConfidence` (`:1045`) survives **only** as an internal tie-break in `selectBestAllocations` (`:1006`) |
>
> Bugs A and C are the "over-allocation" and "fallback picks a warehouse that cannot fulfil" defects flagged in `docs/hotwax/README.md`.
>
> ### Verified fixes landed on this branch (2026-09-27, confidence revised 2026-09-29)
>
> - `calculateDeliveryPromise` (`:932`) now filters to `status == "ALLOCATED"`. Previously any `FAILED` row suppressed the promise, so **every partial allocation shipped a null delivery date** even though a node was fulfilling part of the order.
> - Regression test: `partialAllocation_stillReportsAnEstimatedDeliveryDate` (plus `orderThatBackordersInFull_reportsNoEstimatedDeliveryDate`, which guards the unchanged full-backorder behaviour).
> - That fix was **mutation-verified**: temporarily restoring the old logic fails `partialAllocation_stillReportsAnEstimatedDeliveryDate`.
>
> #### Confidence: fixed twice, then replaced
>
> The confidence fix went through three iterations, and only the last one is in the tree. The history matters because the earlier iterations are easy to mistake for the current design.
>
> | Iteration | Design | Why it was not good enough |
> |-----------|--------|----------------------------|
> | 1 | Keep the 40/60 hybrid, but fix `averageConfidence` to filter the backorder row out of the denominator | Fixed a real arithmetic bug — a 50% backorder row was halving reported confidence (`0.85 → 0.4250`) — but the blend itself was still the problem: it averaged in the *previous* decision's confidence and fixed the *current* plan's score, so a higher-stakes plan could land on a lower number than a lower-stakes one |
> | 2 | Keep the blend, replace the per-strategy constants (`0.75` RULE_BASED / `0.85` AI_OPTIMIZED) with an averaged node score | Still double-counted node health: `derivePlanConfidence` consumed the same `calculateNodeConfidence` values that the blend averaged. Two real risks — how much of the order is sourced, and how much slack the promise has — still had no term at all |
> | **3 (shipped)** | Delete `calculateHybridConfidence`. `confidenceScore` = `derivePlanConfidence` (`:795`) | One score, computed once, from the order's actual risk surface. Nothing is averaged into it that is not evidence about *this* plan |
>
> `derivePlanConfidence` starts from a neutral `0.9000` prior and moves it with four evidence terms:
>
> | Term | Penalty | Notes |
> |------|---------|-------|
> | Coverage | `0.40 × (1 − allocated/demand)` | The dominant term. A half-sourced order is half a promise |
> | Backorder | **cap at `0.4000`** | Capped, not subtracted — no number of healthy nodes makes a short order a confident full delivery |
> | Node health | `0.30 × unhealthy share`, `0.15 × saturated share` | Weighted by *quantity held*, not row count, so a 1-unit row on a bad node cannot dilute a 50-unit row on a healthy one. A vanished node counts as unverified, not healthy |
> | Promise slack | `−0.4000` if already past, `−0.1500` if under 24h | Computed from the same `ALLOCATED` rows that set the promise date, so the two can never disagree |
>
> The result is clamped to `[0, 1]`, rounded to 4dp, and stored on `order.allocationConfidence` (and, per node, on `deliveryPromiseConfidence`).
>
> `averageConfidence` (`:1045`) still exists but has a **much narrower job**: it is the tie-break inside `selectBestAllocations` (`:1006`), used only among plans that already tie on coverage. It filters to `ALLOCATED` rows and divides by `real.size()`. Because every candidate reaching that comparison has the same coverage, the backorder-row exclusion is now correctness and defense-in-depth rather than an observable change in plan selection — a weaker claim than the iteration-1 doc made, and deliberately stated as such.
>
> Regression tests: `planConfidenceRisesWithCoverageAndIsCappedByTheBackorder`, `nodeConfidenceAndPlanConfidenceAreDistinctMeasures`.
>
> `planConfidenceRisesWithCoverageAndIsCappedByTheBackorder` is **mutation-verified** against the exact regression it guards: restoring a fixed `0.7500` score makes a half-sourced order and a fully-sourced one both report `0.7500`, failing the assertion with `partial=0.7500 full=0.7500`.
>
> A third test, `backorderRowDoesNotDeflateTheAverageUsedToChooseBetweenPlans`, was **deleted rather than kept**. It asserted against `derivePlanConfidence(...)` while its name claimed to cover `averageConfidence(...)`; and since it exercised the public API, it could not actually observe the private averaging at all. Adding reflection to force the distinction would have tested the JUnit setup, not the router.
>
> ### Second pass — what counts as *available* (2026-09-28)
>
> The first pass fixed the allocation *arithmetic*. This pass fixes the *input* that arithmetic reads, because `allocateStockAware` can only cap a node at what `InventoryService` tells it is there.
>
> | Defect | Site | Symptom | Fix |
> |--------|------|---------|-----|
> | Safety stock was allocatable | `InventoryService.available` (`:157`) | Nexus allocated stock a node was holding back. HotWax subtracts `MINIMUM_STOCK` in its ATP expression | `available()` subtracts `safetyStock`, and `InventoryRepository.reserveAtomicByRow` / `getAvailableToPromise` apply the **identical** arithmetic in JPQL. The guarded UPDATE is the real enforcement point — `reserveInventory` has no Java-side precondition and only inspects the affected-row count afterwards — so the two must never drift |
> | Reservation decremented **two** rows | `InventoryRepository.reserveAtomicByRow` | The predicate was `(nodeId = :nodeId OR nodeId IS NULL)` in a single bulk UPDATE, so a SKU holding both a node row and a shared-pool row was decremented by the full requested quantity **twice** | `InventoryService.resolveStock` (`:132`) resolves the one backing row first; reservation targets it **by id** |
> | Availability read the wrong node's stock | `InventoryService.checkAvailability` (`:118`) | `anyMatch` over every tenant+SKU row could report coverage sourced from a *different* node, so the allocator picked that node and the failure only surfaced at reservation time | Node row first (`findByTenantIdAndSkuAndNodeId`), shared pool only as fallback |
> | Shared pool committed N× over | `OrderRoutingService.allocateStockAware` (`:551`) | Every node without its own row resolves to the *same* shared row, so each saw the full pool independently | `StockPosition.fromSharedPool` (`:155`) plus `drawnFromShared` netting (`:581`) as candidates are walked |
> | Storefront could sell protected stock | `BigCommerceInventorySyncService` / `ShopifyInventorySyncService` (`:168` each) | Both published raw `quantityOnHand`. BigCommerce **rejects** a negative `inventory_level` outright, and either channel would offer units the allocator treats as reserved — a storefront order that backorders on arrival | Both now publish `max(0, onHand − allocated − reserved − safetyStock)`, aggregated across every holding row |
> | NULL quantity columns threw NPE | both sync services, `available()` | `mapToInt(NxInventory::getQuantityOnHand)` unboxed a seeded NULL straight to `int`; schema defaults are 0 but legacy/seed rows carry explicit NULLs | All four columns read null-safe and treated as 0 |
>
> **Deliberate behaviour change:** withholding safety stock was originally recorded here as a business-policy decision awaiting sign-off. It is now implemented, which means a node can no longer be driven to zero on hand. To revert, drop the `- nz(inv.getSafetyStock())` term in `available()` **and** the `COALESCE(i.safetyStock, 0)` term from both JPQL twins — reverting one of the three silently re-opens the bug.
>
> **`reorderPoint` is intentionally still unread by the allocator.** It is a replenishment trigger, not an allocation-withholding term; `ReplenishmentService` and `AiAgentService` compare it against raw on-hand, which is correct.
>
> **Tests added this pass (21):** 7 in `InventoryServiceTest` (safety stock, reserved stock, clamping, nulls, node precedence, missing row, non-positive qty — the last asserts `verifyNoInteractions` because `checkAvailability` short-circuits before touching the repository) and 7 each in the new `BigCommerceInventorySyncServiceTest` / `ShopifyInventorySyncServiceTest` (identical cases through the publication path, deliberately kept in lockstep with each other). Focused: 47 tests. Full suite: **747 tests, 0 failures** (up from 726).
>
> ### Still open (not fixed on this branch)
>
> - Distance, cost, and node confidence remain synthetic (§3.5 largely still stands).
> - Product substitutes, facility daily caps, manual facility exclusion, brokering groups, week-of-supply / sales velocity, and promise-date cutoffs are still absent from Nexus routing. (`substitute` hits in `PickupOrderService` / `NxPickupOrderItem` are pickup substitutions, not allocation substitutes.)
> - `ConnectorController` (`/connectors/{id}`) still returns raw `quantityOnHand` as `fulfillable` alongside `reserved` and `allocated`. That is a reporting contract change, not a routing bug, so it was left alone deliberately — see `ConnectorController` in the main worktree.

---

## 1. Executive Summary

> **Status note (post-T-17).** Sections 1, 3, 4 and 7 were rewritten after the T-17 allocation
> fixes landed. The earlier draft of this document described the *pre-fix* allocator and reported
> three live correctness bugs. Those are now fixed; the remaining gaps are capability breadth and
> fabricated measurements, not allocation correctness.

| Area | HotWax | Nexus | Verdict |
|------|--------|-------|---------|
| Routing architecture | Config-driven SQL allocator | 3 Java strategies over one shared stock-aware allocator | **Nexus wins** (testable) |
| Allocation correctness | Greedy, stock-aware, stateful SQL | Greedy, stock-aware, stock-bounded per node | Parity |
| Allocation dimensions | ~11+ (facility group, brokering, substitutes, distance, week-of-supply, velocity, broken style, order caps, promise cutoff, exclusions) | 3 rule conditions (order total, region, priority) | HotWax |
| Split / multi-node allocation | Greedy, stock-aware, session-variable state machine | Greedy, stock-aware, `sharedDrawn` netted | Parity |
| Inventory reservation | Yes (`@fpuim` mutation) | Yes — per node, per SKU, release-on-failure | Parity |
| Backorder handling | Yes, explicit flag | Yes — explicit `FAILED` row, honest quantity | Parity |
| Candidate breadth | All qualifying facilities | **`.limit(3)`** in `AI_OPTIMIZED` | HotWax |
| Distance | `ST_Distance_Sphere` on real lat/long | Hardcoded `50.00` or `500.00` km | HotWax |
| Shipping cost | Real carrier rate-shopping result | `$5.00 + 5% of order total × qty` | HotWax |
| Delivery confidence | Derived from allocator state | `0.75` base ± status/capacity, **and** a separate order-level score that now tracks coverage, backorder, node health and promise slack | **Partial** |
| Rules expressiveness | Data-driven filter + sort + rules | 3 hardcoded conditions in code | HotWax |
| Configurability | Admin UI, no deploy | Code change + redeploy | HotWax |
| Shopify integration | Config-driven component | 1,637 LOC, real Admin API + webhooks | **Nexus wins** |
| NetSuite integration | Shipped component | **Does not exist** (registry slot only) | HotWax |
| Sync conflict handling | — | 345 LOC, field-level, real | **Nexus wins** |

**The headline:** Nexus now allocates *correctly* — it will not over-commit an order, will not mark
stock it does not have as `ALLOCATED`, and will raise a real backorder when it cannot cover demand.
That is the bar HotWax also clears. Where HotWax still leads is **breadth** (11+ allocation
dimensions vs 3) and **measurement quality** (real distance and carrier rates vs constants).
So the remaining routing work is feature depth, not correctness — a materially different and smaller
problem than the one this document originally described.

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

`service/OrderRoutingService.java`, 1,029 lines. `allocateOrder(AllocationRequest)` (`:73`) dispatches
on `strategy` (`:100`):

```java
case "RULE_BASED"  -> ruleBasedAllocation(...)
case "AI_OPTIMIZED"-> aiOptimizedAllocation(...)
case "HYBRID"      -> selectBestAllocations(ruleAllocs, aiAllocs, threshold)
```

**The important structural change is that all three paths now converge on one allocator.** The
strategies no longer decide *quantities*; they only decide *candidate order*. `allocateStockAware`
(`:551`) is the single place that turns candidates into stock-bounded allocations.

```
strategy ──► candidate list (ordered)  ──► allocateStockAware ──► [ALLOCATED rows | FAILED backorder]
   RULE_BASED      rule's nodeIds            greedy, stock-checked
   AI_OPTIMIZED    top-3 by score            per node, per SKU
   fallback        all ACTIVE nodes          shared-pool netted
```

### 3.1 RULE_BASED — rules now order candidates, the allocator bounds them

`ruleBasedAllocation` (`:330`) evaluates rules and, for the first one whose conditions match, turns
its `actions.nodeIds` into an **ordered candidate list**:

```java
for (NxRoutingRule rule : rules) {
    if (!conditionEvaluator.evaluate(rule, order)) continue;
    JsonNode actions = conditionEvaluator.parseJson(rule.getActions());
    if (actions == null || !actions.has("nodeIds")) continue;

    List<Candidate> candidates = new ArrayList<>();
    actions.get("nodeIds").forEach(n -> {
        UUID nodeId = UUID.fromString(n.asText());
        warehouseRepository.findById(nodeId)
                // Rule actions are tenant data; never let a rule reach another tenant's node.
                .filter(wh -> tenantId.equals(wh.getTenantId()))
                .ifPresent(wh -> candidates.add(new Candidate(wh, rule)));
    });

    if (!candidates.isEmpty()) {
        return allocateStockAware(order, tenantId, candidates, "RULE_BASED").allocations();
    }
}
```

**T-17 Bug A is fixed.** Previously this loop called `buildAllocation` once per node, and
`buildAllocation` assigned the *full* order quantity to each — so a rule listing three warehouses
triplicated the order and blew up at reservation time. Rules now produce a priority order; the
shared allocator decides how much each node actually gets.

Two further properties are worth calling out, because they are easy to regress:

- **Tenant containment.** `.filter(wh -> tenantId.equals(wh.getTenantId()))` is load-bearing. Rule
  `actions` is tenant-authored data holding raw UUIDs; without it a rule could nominate another
  tenant's node.
- **First match wins.** Rules are evaluated in repository order and the first match returns. There
  is no priority column, no scoring between matching rules, and no fall-through to a lower-ranked
  rule if the winning rule's nodes turn out to be unstocked. HotWax has the same first-match
  character at the *rule* level, but recovers better because its node-level filter is so much wider.

**A rule can still only express three conditions** — `RuleConditionEvaluator.java:40-72`:
`maxTotal`/`minTotal`, `regions`, and `priority` (read from `order.metadata.priority` JSON). That
remains the single largest breadth gap.

### 3.2 The shared allocator — `allocateStockAware` (`:551`)

This is the heart of the T-17 work and the only place quantities are decided.

```java
Map<String, Integer> remaining = new LinkedHashMap<>(collectOrderDemand(order));
Map<String, Integer> sharedDrawn = new HashMap<>();
int priority = 1;

for (Candidate candidate : candidates) {
    if (remaining.isEmpty()) break;

    Warehouse wh = candidate.warehouse();
    Map<String, Integer> take = new LinkedHashMap<>();
    Map<String, Boolean> drawnFromShared = new HashMap<>();
    int nodeTotal = 0;

    for (Map.Entry<String, Integer> entry : remaining.entrySet()) {
        int outstanding = entry.getValue();
        if (outstanding <= 0) continue;

        InventoryService.StockPosition position =
                inventoryService.resolveStock(tenantId, entry.getKey(), wh.getId());
        drawnFromShared.put(entry.getKey(), position.fromSharedPool());

        int effective = position.available();
        if (position.fromSharedPool()) {
            // Net out what earlier candidates already drew from this same shared row.
            effective = Math.max(0, effective - sharedDrawn.getOrDefault(entry.getKey(), 0));
        }

        int grant = Math.min(outstanding, effective);
        if (grant <= 0) continue;
        take.put(entry.getKey(), grant);
        nodeTotal += grant;
    }

    if (nodeTotal <= 0) continue;   // emit no row rather than a 0-qty allocation

    take.forEach((sku, qty) -> {
        remaining.merge(sku, -qty, Integer::sum);
        if (Boolean.TRUE.equals(drawnFromShared.get(sku))) {
            sharedDrawn.merge(sku, qty, Integer::sum);
        }
    });

    plan.allocations.add(buildAllocation(order, tenantId, wh, candidate.rule(), strategy,
            nodeTotal, take, priority++, "ALLOCATED"));
}

remaining.values().removeIf(q -> q <= 0);
if (!remaining.isEmpty()) {
    plan.unallocated.putAll(remaining);
    plan.allocations.add(backorderAllocation(order, tenantId, strategy, remaining,
            "No node can supply " + describeDemand(remaining)));
}
```

Four properties, each of which is a bug that used to exist:

**Greedy, stock-bounded, in candidate order.** Each node takes as much of the still-outstanding
demand as it can *actually source*; the next node gets the remainder. `grant = min(outstanding,
effective)` means no node can ever be allocated more than it holds.

**`nodeTotal <= 0` → no row at all.** A node that can source nothing gets no allocation row, rather
than a zero-quantity `ALLOCATED` row. This is the invariant that reservation depends on.

**Shared-pool netting is the subtle one.** `InventoryService.resolveStock` (`InventoryService.java:132`)
resolves node-specific inventory first and *falls back to the tenant+SKU shared row* when a node has
no dedicated row — reporting `fromSharedPool=true`. That means several nodes can resolve to the
**same** physical row. Without `sharedDrawn`, node #1 and node #2 would each see the full shared
quantity and each plan to commit it, double-counting the pool. HotWAX has the same problem domain
and solves it with the `@fpuim` map. Netting the draws as candidates are walked is the equivalent.

**The backorder carries only the true remainder.** `backorderAllocation` (`:647`) writes
`quantityRequested = totalDemand(unmet)` and `quantityAllocated = 0`, where `unmet` is what is left
*after* successful candidates ran. Recomputing full order demand here would report 100 units
backordered on an order that was 60% covered.

### 3.3 Fallback — `createDefaultAllocation` (`:620`) considers every node

```java
if (warehouses.isEmpty()) {
    return List.of(backorderAllocation(order, tenantId, "RULE_BASED",
            collectOrderDemand(order), "No active warehouses available for routing"));
}

List<Candidate> candidates = warehouses.stream()
        .map(wh -> new Candidate(wh, null))
        .toList();

return allocateStockAware(order, tenantId, candidates, "RULE_BASED").allocations();
```

**T-17 Bug C is fixed.** The old code was
`.filter(canFulfill).findFirst().orElse(warehouses.get(0))` — and since `canFulfill` (`:702`)
`allMatch`es, a genuinely split order (no single node holds everything) matched *nothing* and fell
through to a warehouse just proven incapable. Every ACTIVE node is now a candidate, the allocator
splits across as many as it needs, and a true shortfall becomes a backorder.

### 3.4 AI_OPTIMIZED — scores order candidates, no longer invents quantities

`aiOptimizedAllocation` (`:366`) still contains **no model, no inference, no external call**. It is a
hand-tuned score. What changed is what the score is allowed to decide:

```java
List<Warehouse> scored = warehouses.stream()
        .map(wh -> {
            boolean fulfillsAll = canFulfill(order, wh, demanded);
            BigDecimal base = scoreNodeForOrder(wh, order, shipRegion);
            if (fulfillsAll)        base = base.add(BigDecimal.valueOf(40));
            else if (canFulfillAny) base = base.add(BigDecimal.valueOf(10));
            else                    base = base.subtract(BigDecimal.valueOf(50));
            return new AbstractMap.SimpleEntry<>(wh, base);
        })
        .sorted((a, b) -> b.getValue().compareTo(a.getValue()))
        .limit(3)
        .map(Map.Entry::getKey)
        .toList();

return allocateStockAware(order, tenantId, candidates, "AI_OPTIMIZED").allocations();
```

`scoreNodeForOrder` (`:714`) is unchanged and still magic numbers:

| Signal | Points |
|--------|--------|
| Base | 50 |
| Warehouse state == ship-to state | +30 |
| Both US (different state) | +15 |
| Utilization < 85% | +10 |
| Utilization > 95% | −20 |
| Status ACTIVE | +10 |

**T-17 Bug B is fixed.** The old code split the remaining quantity arithmetically
(`Math.max(1, remaining / (scored.size() - i))`) *after* scoring, so a node that had just failed
`canFulfill` still received an `ALLOCATED` row. The score now only orders candidates; the allocator
caps every node at real availability.

**Remaining weakness — `.limit(3)`.** The candidate pool is silently truncated to three nodes
before allocation. A split order needing four facilities will be backordered at the fourth even if
that facility is fully stocked and in-region. This is now the highest-value *breadth* fix in the
router, and it is a one-line change plus a test.

### 3.5 HYBRID — coverage is now compared before confidence

`selectBestAllocations` (`:861`) runs both strategies and picks a plan:

```java
int ruleCovered = sumAllocated(ruleAllocs);
int aiCovered   = sumAllocated(aiAllocs);
if (ruleCovered != aiCovered) {
    return ruleCovered > aiCovered ? ruleAllocs : aiAllocs;   // coverage wins outright
}

BigDecimal ruleConfidence = averageConfidence(ruleAllocs);
BigDecimal aiConfidence   = averageConfidence(aiAllocs);

if (aiConfidence.compareTo(threshold) >= 0
        && aiConfidence.compareTo(ruleConfidence) >= 0) {
    return aiAllocs;
}
return ruleAllocs;
```

**The ordering here is the fix.** Previously the winner was chosen on confidence alone, so a plan that
could source 2 of 3 items could beat a plan that could source all 3 — on a routing score. A shorter
plan is never the better outcome when a fuller one exists, so coverage is now the primary key and
confidence is only the tiebreaker between equally-covering plans.

`averageConfidence` (`:1045`) averages only `ALLOCATED` rows, excluding backorder rows so that a plan
is not penalised for the very shortfall it honestly reported.

**Still weak:** the threshold comes from `NxRoutingConfig.aiConfidenceThreshold`, defaulting to
`0.7000` (`entity/NxRoutingConfig.java:68`) — that part is properly tenant-configurable. But the
quantity being compared is
now the **mean per-node `deliveryPromiseConfidence`**, i.e. the §3.6 heuristic — not the order-level
score. HYBRID is choosing between two synthetic numbers, and the tiebreak is only as meaningful as
the score underneath it. The coverage key above is the part of this fix that genuinely improved
behaviour; the threshold comparison is untouched by the confidence refactor.

### 3.6 The fabricated measurements — still fabricated

T-17 did **not** touch these. They are the largest remaining honesty problem in the router, because
they are persisted to the allocation row and shown to users as if they were measurements.

| Field | Implementation | Line |
|-------|----------------|------|
| `estimateDistance` → `distanceKm` | `50.00` if same state, else `500.00` | `:762` |
| `estimateShippingCost` → `costEstimated` | `$5.00 + 5% of order total`, × quantity | `:753` |
| `calculateNodeConfidence` → `deliveryPromiseConfidence` | `0.7500` base, `+0.10` if ACTIVE, `−0.15` if util > 90% | `:893` |

None of these consult the carrier, a real distance, or the allocator's own state. A 40 km node and a
400 km node both report `500.00`. `costEstimated` is a flat rate that does not reflect zone, weight,
service level, or the split it is being charged against.

**Partially resolved — the order-level score.** This paragraph originally read that
`confidenceScore` was a hardcoded per-strategy constant (`0.8500` for `AI_OPTIMIZED`) blended
through `calculateHybridConfidence` at a fixed 40/60 weighting. That is no longer true, and the
original text is kept here deliberately so the gap is not mistaken for an open one.

| | Before | Now |
|---|---|---|
| Order-level `confidenceScore` | Per-strategy constant, or a 40/60 blend of a fixed prior with a *previously returned* plan's node average | `derivePlanConfidence` (`:795`) — coverage, backorder cap, quantity-weighted node health, promise slack |
| Is it a measurement? | No — but it also carried no information | **Still no.** It is a triage indicator derived from allocation state, not carrier performance or historical on-time rates |

So the *shape* of the lie changed, not the fact of it. `order.allocationConfidence` is now
defensible as "how risky is this plan, given what we know about the plan" and is **not** defensible
as "how likely is this to arrive on time." The two distance and cost fields above remain entirely
uninformed by reality and are the sharper problem.

**Still synthetic at the node level:** `calculateNodeConfidence` (`:893`) continues to populate
`deliveryPromiseConfidence` on every allocation row. Its `0.7500` base is a constant, and the
`−0.15` saturation term duplicates a capacity check that `derivePlanConfidence` now also performs
(it uses `isSaturated`, `:875`, with a `0.15` weight). The order-level score no longer trusts this
number, but it is still persisted per node and shown in the UI.

### 3.7 What is missing entirely vs HotWax

The first two rows are the ones T-17 closed; the rest are the real remaining gap.

| Capability | HotWax | Nexus |
|------------|--------|-------|
| Inventory decrement / reservation | Yes (`@fpuim` mutation) | **Yes** — `reserveInventory` per node per SKU, with rollback (`:169-186`) |
| Backorder flag | Yes | **Yes** — explicit `FAILED` remainder row (`:647`) |
| Over-allocation guard | implicit | **Yes** — invariant throws if allocated > ordered (`:140-147`) |
| Product substitutes | Yes | No |
| Facility daily order cap | Yes (`@fom`) | No |
| Manual facility exclusion | Yes | No |
| Facility groups / brokering groups | Yes | No |
| Real geo distance | `ST_Distance_Sphere` | No — constant |
| Week of supply / sales velocity | Ranking inputs | No |
| Broken style | Handled | No |
| Promise-date cutoff | Item-level routing | No |
| `ORA_SINGLE` / `ORA_MULTI` | Explicit | Split is automatic; no explicit single/multi control |
| Tenant-scoped rule targets | via facility security | Yes (`.filter(tenantId)`, `ruleBasedAllocation`) |

**Two honest-state consequences worth knowing.** `resolveOrderStatus` (`:200`) sets
`PARTIALLY_ALLOCATED` for a short order and `FAILED` when nothing could be sourced. Writing the
state is only half the fix: a consumer that enumerates statuses would otherwise drop both and the
shortfall would still vanish. T-17 closed the two in-repo surfaces that did exactly that —
`DashboardService.getOrderStatusDistribution` now enumerates both states as their own
"Partially Allocated" / "Failed Allocation" buckets (deliberately *not* folded into "In Progress",
which would re-hide the shortfall), and the frontend `OrderStatus` union and `OrdersPage.STATUS_ORDER`
carry both so the Orders list does not mis-sort them. Anything keyed on `ALLOCATED` outside this
repo still under-reports by design. And item-level allocation
(`order_item.allocated_node_id` / `allocated_qty`) is written **only when a single node covers the
whole order** (`:207`); for a genuine split, the per-SKU truth lives in the allocation's
`skuQuantities` metadata instead, because claiming one node holds every item would be false.

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

Same scenario: 3 line items, A stocks all 3, B stocks item 1 only, rule routes to `[A, B]`.

1. `allocateOrder` (`:73`) requires `PENDING` or `CONFIRMED`, then dispatches on strategy.
2. **RULE_BASED:** `ruleBasedAllocation` (`:330`) walks active rules, the first whose three conditions match wins. Its `actions.nodeIds` is `[A, B]`; both are kept, **in that order**, after the tenant filter.
3. `allocateStockAware` (`:551`) walks that candidate order. It resolves A's position for each SKU and finds A stocks all three.
4. Demand is exhausted at the first candidate, so **`B` is never reached** — `remaining` is empty and the loop breaks.
5. Result: **one allocation row.** A receives all 3 items, `quantityAllocated` equals the true per-SKU demand, `status="ALLOCATED"`. This is T-17 Bug A fixed — before the fix, A *and* B were each built with the full order quantity.
6. The invariant at `:140–147` passes cleanly: allocated equals ordered, so no backorder is required.
7. `reserveInventory` (`:169–186`) reserves A's stock for all 3 SKUs. Nothing to roll back.
8. `distanceKm` is still `500.00` for A, and `costEstimated` is still `$5.00 + 5%` — the measurements in §3.6 are untouched by T-17.

**Where Nexus still diverges from HotWax on this same order.** HotWax sorts candidates by configured policy and puts **B (40 km) first**, splitting into B(item 1) + A(items 2,3). Nexus takes A alone, because the rule's node order is authoritative and distance never influences `RULE_BASED`. That is a legitimate product difference — a merchandising rule outranking a heuristic — but it is a difference, and it means Nexus has no way to express "prefer the nearest capable node" short of hand-ordering `nodeIds`.

**If the rule were `[B, A]`,** the split would happen correctly: B contributes item 1 (its only stocked line), A takes items 2 and 3, each row carries the real `skuQuantities` that node was assigned, and the order lands as a genuine two-node split. That is T-17 Bug C fixed — the old `.findFirst()` fallback could not produce this.

**Under `AI_OPTIMIZED`:** A and B are still scored by the same hand-tuned table, sorted descending, and truncated with `.limit(3)` (`:393`). What changed is what happens next — the scored list is handed to `allocateStockAware` as candidate *order*, and quantities come from real stock. B may now be allocated item 1 instead of a `remaining / (3 − i)` guess, and any true shortfall becomes a `FAILED` backorder row rather than being silently rounded away.

---

## 5. Shopify Integration

### Nexus — the strongest connector work in the codebase

1,637 LOC across 10 files, and it is **real**:

| File | LOC | Role |
|------|-----|------|
| `service/shopify/ShopifyOrderImportService.java` | 350 | Inbound order import |
| `service/shopify/ShopifyProductSyncService.java` | 238 | Catalog push/pull |
| `service/shopify/ShopifyFulfillmentPushService.java` | 200 | Fulfillment → Shopify |
| `service/shopify/ShopifyInventorySyncService.java` | 199 | Level → Shopify |
| `service/shopify/ShopifyClient.java` | 182 | REST client |
| `service/shopify/ShopifyRefundSyncService.java` | 131 | Refund sync |
| `service/shopify/ShopifyWebhookService.java` | 115 | Webhook registration |
| `controller/ShopifyController.java` | 98 | HTTP surface |
| `service/shopify/ShopifyTokenService.java` | 78 | Token lifecycle |
| `entity/NxShopifyWebhook.java` | 46 | Webhook persistence |

> `ShopifyInventorySyncService.java` is 199 LOC on this branch (171 at `da7bb5e`) — the T-17
> second pass added the `sellableToStorefront` helper and its Javadoc. An earlier draft of this
> table claimed 1,817 LOC total; the measured figure is 1,637.

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

1. **T-17 closed the correctness gap; the depth gap remains.** All three strategies now route through one stock-aware allocator (`allocateStockAware`, `:551`). The over-allocation bug, the AI arithmetic-split bug, and the incapable-fallback bug are fixed, quantities are drawn from real `StockPosition`s, and an invariant guard makes over-allocation impossible rather than merely unlikely.
2. **Allocation is now honest, and that has visible consequences.** Short orders produce an explicit `FAILED` backorder row and `PARTIALLY_ALLOCATED`; unsourceable orders produce `FAILED`. Writing the state is only half the job — a consumer that enumerates statuses drops whatever it does not list, so T-17 also closed the two in-repo surfaces that would have hidden the shortfall anyway. `DashboardService.getOrderStatusDistribution` enumerates both states as their own "Partially Allocated" / "Failed Allocation" buckets rather than folding them into "In Progress", and the frontend `OrderStatus` union and `STATUS_ORDER` carry both so the Orders list does not mis-sort them. Both downstream gaps have since been closed rather than left implicit. `BigCommerceOrderStatusPushService` now enumerates `PARTIALLY_ALLOCATED` / `FAILED` explicitly as unmapped and logs the skip with a pointer to this document, instead of relying on the `default -> null` branch; the storefront still never learns of either state, but the omission is now greppable and observable rather than an accident — and mapping either one onto an existing `status_id` would be worse than silence, since `FAILED → CANCELLED (5)` would cancel a live customer order. `AiAgentService` splits the two states out of `PENDING_STATUSES` into a named `NEEDS_ATTENTION_STATUSES` set, includes them in `getRoutingQueue`, and adds `status` / `needsAttention` / `attentionReason` to each queue entry, so a short order appears in the agent queue flagged rather than vanishing from it; the same set feeds `isPending` so the briefing's open-order count includes them. The frontend `RoutingQueueItem` type carries the three fields and `AiOrderRoutingPage` renders a Partial / Alloc failed badge off `AlertTriangle`. The same reasoning drove two upstream fixes: `OrderService.confirmOrder` no longer stomps a routing-persisted `PARTIALLY_ALLOCATED` / `FAILED` back to `CONFIRMED` on the main confirmation path, and `BrokeringService` accepts both states at `enqueueOrder` and records a partial queue entry as `FAILED` rather than either stamping it green or re-running `allocateOrder` — which would double-reserve, because routing only persists item-level `allocatedNodeId` / `allocatedQty` for orders that land entirely in one node. External BI keyed on `ALLOCATED` still under-reports until revisited.
3. **The measurements are still the weakest link.** Distance, cost, and confidence remain hardcoded heuristics (§3.6) and are persisted to the allocation row as though they were measurements. This is now the top remaining routing item, ahead of any new feature — `HYBRID` still arbitrates between synthetic numbers, and the `.limit(3)` candidate truncation still bounds how many facilities the AI path can consider.
4. **HotWax's allocator remains structurally deeper.** Nexus has a single stock-bounded greedy pass driven by tenant-authored node order; HotWax has a policy-sorted candidate set with per-facility ATP, facility caps, substitutes, brokering gates, and item-level routing across facilities. Nexus cannot express "prefer the nearest *capable* node" or "exclude facility X" without a code change.
5. **NetSuite remains a P0 gap.** A registry slot, a comment about a past bug, and conflict-resolution logic with no connector behind them. Any "we support NetSuite" claim is unsupportable.
6. **Shopify is the counter-example.** It proves the team can build real integrations when scoped to one system — the connector framework works and is used (`WebhookIntegrationController`, `IntegrationHubController`, `BatchJobService`). The remaining routing gap is about measurement honesty and policy depth, not architectural incapacity.
