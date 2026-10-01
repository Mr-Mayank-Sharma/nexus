# Nexus Market-Gap Tickets — Round 3 (T-14 → T-18)

> **Purpose:** Rounds 1 and 2 (`nexus-market-gap-tickets.md` T-01→T-07, `nexus-market-gap-tickets-2.md` T-08→T-13) were mined from **transcripts** — what customers said they needed. This round is mined from **source code** — what the two implementations actually do when you read them line by line. Five findings, four of them P0.
>
> **Evidence base:** [`nexus-order-routing-comparison.md`](./nexus-order-routing-comparison.md) — full side-by-side with file:line citations.
>
> **HotWax source:** `/Users/mayanksharma/Desktop/hotwaxoms/moqui2/runtime/component/order-routing`
> **Nexus source:** `/Users/mayanksharma/Desktop/nexus/nexus-oms-backend/src/main/java/com/nexus/oms`

---

## How These Tickets Map to the Plan

| Ticket | Gap | Priority | Evidence |
|--------|-----|----------|----------|
| T-14 | Stock-aware multi-warehouse allocation engine | **P0** | `OrderRoutingService.java:404-640` vs `InventorySourceSelector.sql.ftl` |
| T-15 | NetSuite ERP connector (does not exist) | **P0** | `ConnectorFactoryConfiguration.java:64` is a placeholder only |
| T-16 | Wire orphaned carrier connectors into rates + labels | **P0** | `FedExConnector` registered at `:93`, never called |
| T-17 | Allocation correctness bugs (N× over-allocation, unchecked AI split, incapable-node fallback) | **P0** | `OrderRoutingService.java:232`, `:272`, `:418-420` |
| T-18 | Routing rule expressiveness (3 conditions → 11+) | P1 | `RuleConditionEvaluator.java:40-72` |

> **Why these matter:** T-14, T-15, T-16, and T-17 are P0 because they are not "missing features" — they are **actively incorrect or entirely absent** in code that already exists and returns `200 OK`. A customer splitting an order across two warehouses today gets double-allocated inventory. A customer told they have NetSuite integration has a registry entry and nothing else. T-18 is P1 because it is a depth gap, not a correctness one.

> ## ⚠️ Status re-verified 2026-10-01
>
> **T-17 is still an open P0 on `main` — and a verified fix now exists on a branch.** Since this doc was written, partial stock-awareness and queue plumbing landed (`0b91a0a`, `90bde84`) along with `BrokeringServiceTest` (`5619719`). The defects catalogued below remain unresolved in `OrderRoutingService` **on `main`** (two verbatim-live, one mitigated but ungated), and `OrderService.confirmOrder` (`:286`) still accepts its corrupt output.
>
> **A complete fix is already built and verified on `fix/t17-allocation-correctness`** (worktree `nexus-t17`, commits `a7ea7d2` + `cac85c8`). It makes `OrderRoutingService` stock-aware, caps every allocation at real availability, routes shortfalls to `PARTIALLY_ALLOCATED`/`FAILED` instead of `CONFIRMED`, and has a full regression suite. Verified: **768 tests, 0 failures** (`mvn clean test`, JDK 17).
>
> **Recommended action: merge `fix/t17-allocation-correctness` — do not re-implement.** The fix belongs *in the router* because `OrderRoutingService` is the **only** allocator: `BrokeringService` is a persisted work queue whose every allocation is a call to `orderRoutingService.allocateOrder(...)`, so there is no alternative path to route through and no call ordering that avoids the defect. **Defect-by-defect status: [`nexus-order-routing-comparison.md` §8](./nexus-order-routing-comparison.md#8-t-17-status--re-audited-2026-10-01).**
>
> **T-14 and T-18 remain genuinely open.** Do not read the brokering queue work as progress on T-14 — `OrderRoutingService` still does not implement HotWax's allocation dimensions.

---

## T-14: Stock-Aware Multi-Warehouse Allocation Engine — **P0**

**Market evidence:** Source-level. `createDefaultAllocation` selects `.findFirst()` (`:418`); `aiOptimizedAllocation` is a constant-scoring function named "AI" that contains no model (`:245-303`); `scoreNodeForOrder` is magic numbers (`:474-505`). HotWax runs a real greedy allocator in `InventorySourceSelector.sql.ftl`.

### What's actually broken today

| Behavior | Nexus today | HotWax |
|----------|--------------|--------|
| Pick best node | `.findFirst()` on repository order | Sorted by admin-configured policy |
| Split across nodes | Arithmetic `remaining / (n - i)` | Greedy, decrements real inventory |
| Reserve inventory | **Nothing** | `@fpuim` map mutation |
| Distance | `50.00` or `500.00` (`:514`) | `ST_Distance_Sphere` on lat/long |
| Cost | `$5.00 + 5% of total` (`:508`) | Real rate-shopping result |
| Confidence | Hardcoded `0.65` / `0.75` / `0.85` | Derived from allocator state |
| Backorder | No | Yes |
| Split order needing 2+ nodes | Falls to a node proven incapable (`.orElse(warehouses.get(0))`) | Splits correctly |

### Real workflow (acceptance criteria)
1. **Rank every eligible facility** against the order using a configurable, admin-editable policy — the HotWax analogue of `inventorySortByList`.
2. **Allocate greedily against real availability.** Decrement stock as each node is assigned; stop when the order is fully covered.
3. **Split across nodes when required.** An order that no single facility can fill allocates partially to each capable facility, with the remainder tracked.
4. **Flag backorder** when coverage is incomplete, rather than silently marking `ALLOCATED`.
5. **Report true distance** from facility lat/long to ship-to, in the tenant's configured unit.
6. **Report true cost** from the rate-shopping result, not a formula on the order total.
7. **Derive confidence** from allocator state — coverage ratio, distance, capacity — not a constant.
8. **Never mark `ALLOCATED` without a stock check** on any path.

### Build scope
- **Allocation engine** — replace the three-branch strategy with a ranked-candidate greedy allocator; keep `RULE_BASED` as a pre-filter that narrows the candidate set rather than short-circuiting to a final answer.
- **Inventory reservation** — decrement or reserve on allocation; release on cancel/reassign. This is the missing primitive that makes `ALLOCATION_RESERVATIONS` in T-03+ coherent.
- **Real distance** — store facility lat/long; add a distance service (haversine in Java, or `ST_Distance_Sphere` via PostGIS).
- **Real cost** — plumb the rate-shopping result into `costEstimated`; T-16 provides the carrier side.
- **Configurable ranking policy** — persisted, tenant-scoped, admin-editable; orderable list of ranking factors.
- **Rename `AI_OPTIMIZED`** — either implement a real model behind the name or remove the strategy. A strategy named for AI that calls no model is a liability in a sales conversation.

### Acceptance test
An order for 3 SKUs where Warehouse A holds all 3 (120 km away) and Warehouse B holds SKU 1 only (40 km away), with a ranking policy of distance-first. Assert: SKU 1 → B, SKUs 2–3 → A, total allocated equals ordered quantity **exactly once**, no negative inventory, `distanceKm` reflects real coordinates, and `costEstimated` matches the carrier rate. Repeat with the policy reversed and assert allocation follows.

### Notes
- T-14 subsumes most of the "orchestration" ambition in Round 1's T-01. It is a rewrite of the decision core of `OrderRoutingService`, not an additive feature.
- Keep `BrokeringService` (386 LOC) out of scope — it is a **work queue**, not an allocator. Do not let it absorb allocation responsibility.

---

## T-15: NetSuite ERP Connector — **P0**

**Market evidence:** `grep -ril netsuite` across the Nexus source tree returns **two files**, neither an implementation. `ConnectorFactoryConfiguration.java:64` registers a generic HTTP factory under the name `NETSUITE`. `SyncConflictService.java:32,290` mentions NetSuite in **comments only** — describing a past bug and an `FRTTRA` transaction type.

### What's actually missing

There is no `NetSuiteConnector`, no `NetSuiteClient`, no token exchange, no entity mapping, no repository, no service. Meanwhile `SyncConflictService.java` is **345 lines of real conflict logic** — `detectConflict` (`:73`), `resolveFieldWinners` (`:110`), `recoveryJob` (`:179`), `getReconciliationReport` (`:255`), `handleUnsupportedTransaction` (`:293`).

**The conflict-resolution layer was built for a NetSuite workload that was never connected.** Because the factory slot resolves, a NETSUITE connector would instantiate today and do nothing.

### Real workflow (acceptance criteria)
1. **Authenticate** — SuiteTalk REST OAuth / token exchange, token refresh, per-tenant credential storage.
2. **Push sales orders** with correct entity mapping, including custom fields and custom document generation (the capability HotWax's component provides).
3. **Pull fulfillment and inventory** back from NetSuite.
4. **Map NetSuite transaction types**, including unsupported ones — `SyncConflictService.handleUnsupportedTransaction` already expects `FRTTRA`.
5. **Feed the existing conflict layer.** `detectConflict` must receive real NetSuite timestamps; the "NetSuite fed back stale data" bug described at `:32` must be structurally impossible.
6. **Run the reconciliation report** against live NetSuite data.

### Build scope
- **`NetSuiteConnector`** implementing the existing connector abstraction, following `SapConnector` (191 LOC) as the structural template.
- **SuiteTalk REST client** — auth, retry, rate-limit handling, pagination.
- **Entity + field mapping** — configurable per tenant, including custom document generation.
- **Credential storage** — encrypted per-tenant NetSuite secrets (check how SAP/QuickBooks credentials are stored; do not invent a new mechanism).
- **Replace the generic factory entry** at `ConnectorFactoryConfiguration.java:64` with the real factory.
- **Wire outbound sync** into the existing batch/sync job infrastructure (`BatchJobService`).

### Acceptance test
A tenant with NetSuite credentials pushes a sales order; the order appears in NetSuite with correct custom fields. NetSuite returns a fulfillment; the OMS updates and `detectConflict` correctly identifies a stale-write attempt on an older `lastModified`. `getReconciliationReport` shows matched and diverged entities.

### Notes
- **P0 on honesty grounds.** T-13 (Round 2) shipped bidirectional sync + conflict handling, and this transcript claims reference a NetSuite integration that does not exist. Either build it or stop implying it exists.
- Delete the generic factory placeholder once real — a resolvable placeholder is worse than an absent one.

---

## T-16: Wire Orphaned Carrier Connectors into Rates + Labels — **P0**

**Market evidence:** `FedExConnector.java` (182 LOC) contains real endpoints — `https://apis.fedex.com/oauth/token`, `https://apis.fedex.com`, plus the sandbox URL — and is registered at `ConnectorFactoryConfiguration.java:93`. No caller was found in the inspected label or rate-shopping services. Meanwhile `ShippingLabelService.generateLabel` (`:28-48`) fabricates labels.

### What's actually broken today

**`ShippingLabelService.generateLabel` (`:28`):**
```java
.labelSource("SIMULATED")
.trackingNumber("SIM" + System.currentTimeMillis() + new Random().nextInt(10000))
.labelUrl("/api/v1/labels/" + id + "/download")
```
Real endpoints, simulated output.

**`RateShoppingService.shopRates` (`:45`):** reads `rateCache`, falls back to `carrierRateRepository.findEligibleRates(...)`. Seeded database rows, never a carrier quote.

**`FedExConnector`:** real code, zero callers. The framework *is* consumed elsewhere (`WebhookIntegrationController`, `IntegrationHubController`, `BatchJobService`) — this connector specifically is orphaned.

### Real workflow (acceptance criteria)
1. **Live rate shopping** — `RateShoppingService` calls a carrier through the connector abstraction; cached rates remain a fallback with a visible staleness indicator.
2. **Real label purchase** — `ShippingLabelService` creates a shipment with the carrier and stores the returned label, not a synthetic one.
3. **Label void and retry** — mirror HotWax's `void#ShippingLabel` and `retry#ShippingLabel`, including batch retry with per-order status.
4. **Void on shipment reinitialization** — `FulfillmentServices.xml:354` `reinitialize#Shipment` voids the existing label when tracking number and `carrierServiceStatusId` are present. Nexus has no equivalent.
5. **Label artifacts** — produce the HotWax document set: `Label.pdf`, `ReturnLabel.pdf`, `PackingSlipAndLabel.pdf`, `Manifest.pdf`.
6. **Carrier error surfacing** — carrier failures must raise `NxFulfillmentException`, not silently return a fake label.
7. **ZPL and PDF rendering** per T-12.

### Build scope
- **Connector registry resolution** — inject the connector into `ShippingLabelService` and `RateShoppingService`; remove the direct-instantiation pattern.
- **Carrier adapter interface** — pluggable per carrier (Jitsu, SAPI, VHO, FedEx, UPS) so adding one is configuration, not a code change.
- **Label lifecycle** — purchase, store, void, retry; persist the carrier's own shipment and tracking IDs.
- **Manifest + return label** generators.
- **Credential management** per carrier, per tenant.
- **Reopen T-12** — this ticket is the enabling half of the Round 2 P0 carrier ticket.

### Acceptance test
A confirmed order with carrier `FEDEX` produces a rating request against FedEx's live sandbox, a purchased label with a real tracking number, a retrievable PDF, and — on shipment reinitialization — a void call to FedEx with the original tracking number. A carrier 5xx raises a fulfillment exception; it does not return `SIM` tracking.

### Notes
- `ShippingLabelService` currently returns a plausible response for a request that has done no work. That is worse than a 501: it will not appear in an error-rate dashboard.
- Coordinate with T-14 — real cost feeds `costEstimated`, and T-14's acceptance test depends on T-16's rate output.

---

## T-17: Allocation Correctness Bugs — **P0**

**Market evidence:** Two independent defects in `OrderRoutingService.java` that produce wrong inventory commitments **today**, on code paths that return success.

### Bug A — N× over-allocation on the rule path (`:232`)

`buildAllocation` (`:643`) builds every node with the **full order quantity**:

```java
int qty = estimateOrderQuantity(order);
.quantityAllocated(qty)
.quantityRequested(qty)
```

`ruleBasedAllocation` calls it once per UUID in `actions.nodeIds` (`:229-234`). A rule listing 3 nodes allocates the entire order to all three. **`canFulfill` is never called on this path** — the node is marked `ALLOCATED` with no stock check whatsoever.

### Bug B — unchecked split on the AI path (`:272`)

```java
int allocQty = (i == scored.size() - 1) ? remaining
              : Math.max(1, remaining / (scored.size() - i));
```

Quantity is split by arithmetic *after* scoring, bypassing the `canFulfill` result that `scoreNodeForOrder` just used. A node that failed the stock check can still receive quantity and be marked `ALLOCATED`.

### Bug C — incapable-node fallback (`:418-420`)

```java
.orElse(warehouses.get(0));
```

`canFulfill` uses `allMatch` (`:462`). An order needing a genuine split matches nothing, so allocation falls to `warehouses.get(0)` — a warehouse **just proven incapable** — and is marked `ALLOCATED`.

### Real workflow (acceptance criteria)
1. **Total allocated == total ordered, always.** Invariant enforced in one place, asserted at the persistence boundary.
2. **No `ALLOCATED` status without a successful stock check**, on any strategy.
3. **Split orders route to capable nodes** or flag backorder — never to an arbitrary fallback.
4. **Invariant test suite** over the property-based combinations: 0-node, 1-node, exact-fit, split-required, oversubscribed, no-capable-node.

### Build scope
- Fix `buildAllocation` to take an explicit allocated quantity and a stock-check result.
- Fix `aiOptimizedAllocation` to allocate against remaining availability rather than dividing by node count.
- Fix `createDefaultAllocation` to return a backorder/exception instead of `.orElse(warehouses.get(0))`.
- **Enforce the invariant in code**, not by convention — a guard that rejects any allocation whose sum exceeds demand.
- **Regression tests** for all three, using the T-14 acceptance fixture.

### Acceptance test
Property-based: for any order and any inventory/facility configuration, `sum(quantityAllocated) == sum(demand)` or a backorder is raised. The T-14 scenario (A has 3 SKUs, B has 1, rule lists `[A, B]`) must produce one allocation of the correct size per node — not two full-order allocations.

### Notes
- These are **bugs, not gaps** — they should be fixed on their own schedule, ahead of and independent of the T-14 rewrite. Shipping T-14 without fixing T-17 leaves the same defect class in new code.
- Consider an integration guard that fails loudly if `status="ALLOCATED"` is ever persisted without a corresponding reservation.

---

## T-18: Routing Rule Expressiveness — P1

**Market evidence:** `RuleConditionEvaluator.evaluate` (`:40-72`) supports exactly three condition types: `maxTotal`/`minTotal`, `regions`, and `priority` (read from `order.metadata.priority`). HotWax expresses ~11+ dimensions through admin-editable filter rows.

### Missing dimensions

| Dimension | HotWax | Nexus |
|-----------|--------|-------|
| Facility group / brokering group | `FACILITY_GROUP_MEMBER`, `BROKERING_GROUP` | No |
| Per-facility brokering opt-in | `ALLOW_BROKERING='Y'` | No |
| Product substitutes | `PRODUCT_SUBSTITUTE` via `product_assoc` | No |
| Facility daily order cap | `facility_order_count` + `maximum_order_limit` | No |
| Manual facility exclusion | `excluded_order_facility` | No |
| Real distance threshold | `ST_Distance_Sphere` | No |
| Week of supply | Ranking input | No |
| Sales velocity | Ranking input | No |
| Broken style | Handled | No |
| Promise-date cutoff | Item-level routing | No |
| Ship-by / ship-after date | Ordering filters | No |

### Real workflow (acceptance criteria)
1. **Facility group rules** — restrict candidate facilities to a group.
2. **Per-facility brokering opt-in** — honor `ALLOW_BROKERING`.
3. **Substitute-aware availability** — count substitutable SKUs as available stock.
4. **Facility order caps** — daily per-facility maximum, with reset.
5. **Manual exclusions** — an operator can pin an order away from a facility.
6. **Distance threshold** — rules can express "within 50 km".
7. **Configurable ranking** — factors and their order are admin-editable, not hardcoded.
8. **Ties are deterministic** — final tiebreaker on a stable key, mirroring HotWax's `facility_id` comment (*"to avoid random sorting"*).

### Build scope
- Extend the condition schema and `RuleConditionEvaluator` to the missing dimensions.
- Introduce facility groups, brokering flags, and order caps as first-class entities.
- Persist ranking policy separately from filter conditions (HotWax splits `ENTCT_FILTER` from `ENTCT_SORT_BY` — same idea).
- Wire each new condition into the T-14 allocator as a candidate-generation filter, not as a post-hoc check.

### Acceptance test
A rule configured with "facility group = WEST, within 75 km, exclude facility F3, substitute SKU-X allowed" filters candidates accordingly. Two facilities with identical scores resolve to the same node across 100 runs.

### Notes
- Keep filters and ranking separate. Conflating them is how HotWax's design got expressive, and how a naive design becomes unmaintainable.
- Sequencing note: T-18's conditions are only meaningful once T-14 has a real candidate-generation stage. **T-14 → T-18.**

---

## Sequencing & Dependencies

```
T-17 (bugs)  ─────────────────────────────►  ship independently, FIRST
T-16 (carrier wiring) ──┐
                        ├──► T-14 (allocation engine) ──► T-18 (rule expressiveness)
T-15 (NetSuite) ────────┘   (needs real cost for costEstimated)
```

| Order | Ticket | Why here |
|-------|--------|----------|
| 1 | **T-17** | Active data corruption. Independent of everything else. |
| 2 | **T-16** | Unblocks T-14's real cost; also the enabler for Round 2's T-12. |
| 3 | **T-15** | Independent of routing. Ship in parallel with T-16. |
| 4 | **T-14** | Core rewrite; depends on T-16 for cost, T-17 to avoid re-embedding the bug class. |
| 5 | **T-18** | Meaningless until T-14 has a candidate-generation stage. |

---

## Definition of Done (per ticket)

- Every allocation path is covered by a test asserting `sum(quantityAllocated) == sum(demand)` or an explicit backorder.
- No code path sets `status="ALLOCATED"` without a stock check.
- No reported metric (distance, cost, confidence) is a hardcoded constant presented as a measurement.
- Integration behavior is verified against a real sandbox endpoint, not a mock returning a fixture.
- `grep -ril` for a claimed integration returns an implementation, not a comment or a factory slot.
- Ticket acceptance test is automated and runs in CI.

---

## Next Step

Start with **T-17**. It is a small, self-contained diff that stops the system from double-allocating inventory today, and it does not depend on any other ticket landing first.
