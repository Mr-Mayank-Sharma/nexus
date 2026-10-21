# Nexus vs HotWax — Honest Feature Scorecard

**Purpose:** A single, straight answer to "who is better, feature by feature", scored out of 10 for **code** (what actually exists and works) and separately for **business process** (how completely the workflow is covered).

**Audit date:** 2026-10-03 · **Nexus commit:** `67d2753` (scores last re-litigated at `45f389d`; since then only `83fcc18` permissions, `08741b3` docs and the `0b04f19`/`67d2753` doc guard landed — **no capability change, so no score moved**) · **HotWax:** `moqui2` runtime snapshot, 159 `*Services.xml` across 14 components

**Method — read this before the numbers:**

- Every Nexus score below was **verified against source in this repo at `45f389d`, spot-re-verified at `67d2753`**, not copied from an existing doc. Where code contradicts a prior doc, I say so in §5.
- **Changed since the `10a092f` audit (2026-10-01 → 2026-10-02):** T-17 (allocation correctness), T-09 (fulfillment reconciliation), and T-12 (real carrier label purchase) have landed on `main` via merge `45f389d`. Rows 1, 2, 3 and process row 2 are re-scored below; every other score is unchanged and was **not** re-litigated.
- HotWax scores are grounded in the verified source audits already recorded in [`nexus-vs-hotwax-technical.md`](./nexus-vs-hotwax-technical.md) (service counts, `EligibleOrdersQuery.sql.ftl` / `InventorySourceSelector.sql.ftl`, reconciliation contract, funnel services).
- **Score = code, not marketing.** A `POST /mcp/execute` that returns a descriptor instead of calling the service is not a working feature, so it scores low no matter how good the design is. A placeholder scores as a placeholder.
- HotWax is scored **as the open-source `moqui2` runtime snapshot**, not as a vendor's full commercial product. HotWax Commerce the *company* sells things not present here (Moki AI, Manhattan-scale add-ons). Scoring the company higher would be dishonest without the code to prove it, and scoring the code lower would be equally dishonest. Same for Nexus: no production uptime history, so track record scores low regardless of feature count.

**Verdict up front: HotWax wins on depth and production reality. Nexus wins on architecture, import speed, EDI, 3PL billing, cost, and honesty. Neither wins outright.**

---

## 1. Code scorecard — what actually exists

| # | Feature | HotWax | Nexus | Winner | Evidence / why |
|---|---------|:-------:|:-----:|:------:|----------------|
| 1 | **Order routing / brokering** | 9 | **5** | 🔴 HotWax | HotWax: stock-aware SQL allocator, facility caps, substitute-item resolution, distance ranking. Nexus `OrderRoutingService`: T-17's three allocation defects are **fixed on `main`** (a pre-existing partial order with an allocated node is now failed instead of stamped `ALLOCATED` and re-routed — 17 tests pin it). Still **no stock-aware SQL allocator (T-14)**, so allocation quality is basic-path only. |
| 2 | **Order reconciliation API** | 9 | **6** | 🔴 HotWax | ⚠️ **Raised 4 → 6 on T-09.** `FulfillmentReconciliationService` is now real: `EXACT` / `PARTIAL` / `QUANTITY_DELTA` / `LOCATION_CHANGE` / `ANOMALY` classification, idempotent `acceptFulfillment`, webhook-driven reconciliation, mismatch report + per-order history, `V61`, 9 tests. **Still not equivalent to HotWax:** this is Shopify-fulfillment-scoped matching, not a generic contract-based recon API with half-open `[from,thru)` windows and exchange awareness. `SyncController.java:53` `/sync/reconciliation` still returns job counts, not order records. |
| 3 | **Carrier labels / rating** | 9 | **6** | 🔴 HotWax | ⚠️ **Raised 4 → 6 on T-12.** A real adapter path now exists — `CarrierLabelConfigService.resolveAdapter` wires Jitsu/SAPI from tenant config and **throws** on missing/unknown adapter instead of faking success, with `SimulatedCarrierAdapter` opt-in only, idempotent on order+carrier, publishing `order.label_generated`. **Still throttled:** legacy `ShippingLabelService.java:44,69` still default `labelSource` to `SIMULATED` and synthesize a tracking number when the caller omits one, BOPIS is local-only (correctly), and **FedEx remains unwired**. |
| 4 | **Import engine** | 3 | **9** | 🟢 Nexus | HotWax ~2–3 min/order. Nexus fast bulk import. The one unambiguous, large Nexus win. |
| 5 | **EDI** | 5 | **9** | 🟢 Nexus | `EdiAutomationService.java` (714 lines): parse `850/856/810/940`, generate `855` + `997` acks, unit-tested. HotWax has EDI but no comparable self-serve parser surface. |
| 6 | **3PL billing + client portal** | 5 | **8** | 🟢 Nexus | `BillingService.java` real (statement generation, lines, status) + `ClientPortalPage.tsx`. HotWax core OSS has no billing engine. |
| 7 | **WES / automation** | 8 | 7 | 🟡 HotWax | Nexus `AutomationEmulatorService` is a genuine closed-loop emulator (SENT→EXECUTING→COMPLETED, real elapsed time, `simulated:true`, feeds slotting). Real, but **emulator — no robotics**. |
| 8 | **Replenishment** | 9 | **7** | 🔴 HotWax | ⚠️ **Docs said "Missing/planned" — that is stale.** `ReplenishmentService.java` (168 lines) has rule CRUD, suggestion generation, approve/reject, stats. It's manual-approve, not system-directed drop-off. |
| 9 | **Serialized / RFID** | 9 | **7** | 🔴 HotWax | ⚠️ **Also stale in docs.** `RfidIngestionService.java` (391 lines) does EPC decode, receive, serialized cycle count, retag, sessions. Real EPC support, thinner than HotWax's RFID story. |
| 10 | **Analytics / dashboards** | 9 | 5 | 🔴 HotWax | HotWax: 8 facility-level funnel services backed by view entities. Nexus `AnalyticsController`: `/exceptions` is real, but `/carrier-performance`, `/cost-breakdown`, `/lanes` return **hardcoded literals**. |
| 11 | **AI / forecasting** | 5 | **8** | 🟢 Nexus | Nexus: Holt smoothing + grid search, WAPE, p90, drift, model registry. Honest gap: routing "AI" is score-then-split, not a trained allocator. |
| 12 | **MCP / AI-agent execution** | 8 | 4 | 🔴 HotWax | Nexus `McpToolExecutor.java:208-216` — `executeViaBridge` is a **placeholder returning the endpoint descriptor**, own comment: "In production, call the underlying service". Registerable and budgeted, **not executable**. |
| 13 | **Sync conflict handling** | 4 | **8** | 🟢 Nexus | `SyncConflictService` — stale flags, versioning, locks, `GET /sync/conflicts` + resolve workflow. HotWax overwrites. Genuine differentiator. |
| 14 | **Idempotency / CDC / DLQ** | 5 | **8** | 🟢 Nexus | `IdempotencyService`, `WebhookDedupLedgerService`, `CDCProcessor`, retry + backoff + DLQ parking. Nexus engineering discipline. |
| 15 | **Schema governance** | 5 | **9** | 🟢 Nexus | 59 explicit Flyway migrations (was 57 at `10a092f`; T-09/T-12 added V61/V62) vs. implicit entity auto-create (`EntityAutoServiceRunner` is HotWax's zero-config strength but weak governance). |
| 16 | **Multi-identifier orders** | 9 | 4 | 🔴 HotWax | HotWax typed temporal `OrderIdentification` (composite PK, `thruDate`, history). Nexus single `channelOrderId`/`externalId` — last-write-wins, no history. |
| 17 | **Cycle counting** | 9 | 8 | 🟡 HotWax | Both real. Nexus adds serialized counting via RFID. |
| 18 | **Transfer orders** | 9 | 7 | 🔴 HotWax | Nexus has transfers + receiving; discrepancy workflow is partial. |
| 19 | **BOPIS** | 8 | 8 | ⚪ Tie | Nexus: backend service refs + 3 pages (`BOPISPage`, `BopisAppPage`, `BopisOwnerPage`). Comparable. |
| 20 | **Returns / exchanges** | 9 | 6 | 🔴 HotWax | Nexus has returns; exchange complexity is partial. |
| 21 | **Picker performance analytics** | 9 | 3 | 🔴 HotWax | Nexus `PickerController` = picker assignment/availability only. **No performance metrics service exists.** Doc correct here. |
| 22 | **Transit-time model** | 9 | 2 | 🔴 HotWax | **No `TransitTime` service in the codebase.** Absent. Doc correct here. |
| 23 | **Store printing (pick tickets)** | 9 | 2 | 🔴 HotWax | **No pick-ticket / packing-slip PDF generation in backend.** Absent. Doc correct here. |
| 24 | **DoorDash / on-demand** | 8 | 2 | 🔴 HotWax | **Zero `doordash`/`curbside` references in backend Java.** Absent. Doc correct here. |
| 25 | **Warehouse / WMS depth** | 9 | 6 | 🔴 HotWax | Nexus has waves, slots, receiving, putaway. HotWax has slotting rules engine, batch-wave, labor optimization. |
| 26 | **UI / operator experience** | 6 | **8** | 🟢 Nexus | React/TypeScript SPA, **94 pages** (was 93; T-09 added `FulfillmentReconciliationPage`) vs. Moqui XML screens. HotWax screens are functional, dated. |
| 27 | **API explicitness** | 5 | **9** | 🟢 Nexus | Self-documenting controllers, typed DTOs, OpenAPI-shaped surface vs. implicit entity CRUD. |
| 28 | **Infrastructure / deploy** | 6 | **8** | 🟢 Nexus | Helm chart, `docker-compose.prod.yml` with healthchecks, Postgres/Redis/Kafka, backup CronJob. |
| 29 | **Testing depth** | 7 | 7 | ⚪ Tie | Nexus 147 test files / 830 tests incl. `OrderRoutingServiceTest`, `AutomationEmulatorServiceTest`, `EdiAutomationServiceTest`, `FulfillmentReconciliationServiceTest`. Neither is at enterprise CI rigor — the count grew, the rigor didn't. |
| 30 | **Production track record** | **10** | **3** | 🔴 HotWax | Not fixable by code. Needs real customers + uptime history. Nexus has none. |
| 31 | **Honesty of data** | 7 | **10** | 🟢 Nexus | Nexus is the only one that marks simulated results `simulated:true` and refuses to invent metrics. Real differentiator. |
| 32 | **Cost & time-to-value** | 9 | **9** | ⚪ Tie | HotWax OSS $0 license. Nexus self-host ≈$76–110/mo all-in. Both beat enterprise vendors. |

**Code tally:** HotWax wins **18**, Nexus wins **11**, ties **3** — 32 rows, counted from the winner column above. (The previous revision of this doc claimed "18 / 12 / 4", which does not add up to 32; that was an arithmetic error, now corrected.)

---

## 2. Business process scorecard — how completely the workflow is covered

Scored on **process completeness** (is the whole loop closed?), independent of how pretty the code is.

| # | Business process | HotWax | Nexus | Winner | Nexus gap |
|---|------------------|:-------:|:-----:|:------:|------------|
| 1 | **Order-to-cash (happy path)** | 9 | **8** | 🟢 Nexus | Both close. Nexus import/EDI speed is the better small-order experience. |
| 2 | **Multi-node allocation** | 9 | **5** | 🔴 HotWax | T-17's correctness bugs are **fixed**; T-14 (stock-aware SQL allocator) is still the blocker. |
| 3 | **Inventory accuracy loop** | 9 | 6 | 🔴 HotWax | No system-directed replenishment drop-off; replenishment is manual-approve. |
| 4 | **Inbound → available-to-promise** | 9 | 7 | 🔴 HotWax | ASN/856 + receive exist; dock scheduling and bulk EDI depth differ. |
| 5 | **Wave → pick → pack → ship** | 9 | 7 | 🔴 HotWax | Missing pick-ticket printing; labor optimization absent. |
| 6 | **Warehouse labor management** | 9 | 3 | 🔴 HotWax | No picker-performance analytics, no transit-time planning. |
| 7 | **Returns & exchanges** | 9 | 6 | 🔴 HotWax | Exchange complexity partial. |
| 8 | **Serialized/RFID process** | 9 | 7 | 🔴 HotWax | EPC flow works; thinner scanning ecosystem. |
| 9 | **Store ops / omnichannel** | 9 | 4 | 🔴 HotWax | BOPIS yes; DoorDash and store printing absent. |
| 10 | **Client onboarding & self-serve** | 4 | **8** | 🟢 Nexus | Nexus onboarding is far more predictable; HotWax is reactive. |
| 11 | **3PL / client billing ops** | 5 | **8** | 🟢 Nexus | Per-client rate cards + statements + portal is a real Nexus advantage. |
| 12 | **Integration lifecycle mgmt** | 6 | **8** | 🟢 Nexus | Conflict detection, idempotency, DLQ — mature operational process. |
| 13 | **AI-assisted operations** | 6 | 7 | 🟡 HotWax | Nexus forecasting works; MCP agent execution is a placeholder, so the process loop isn't closed. |
| 14 | **Auditability & trust** | 6 | **9** | 🟢 Nexus | Sync audit, AI audit trail, explicit conflict records. |
| 15 | **Cost & ownership economics** | 9 | **9** | ⚪ Tie | Both self-hostable at $0 license. |

**Process tally:** HotWax wins **9**, Nexus wins **5**, ties **1** — 15 rows, counted from the winner column above. (Previously claimed "10 / 5", which does not add up to 15; corrected.)

---

## 3. The honest one-paragraph version

HotWax is the more **complete** system: it closes more warehouse loops, its routing allocator is genuinely stock-aware, and it has years of production reality behind it. Nexus is the better **engineered** system: clean typed APIs, 59 migrations, real EDI, fast import, conflict handling, 3PL billing, and a modern UI — at a fraction of the cost, with honest data labelling. **T-17 has removed the correctness blocker**: routing no longer silently corrupts partial orders on `main`, and the three allocation defects are pinned by tests. Nexus's single biggest remaining problem in order routing is **T-14** — the absence of a stock-aware SQL allocator, which is the entire reason brokering works in HotWax. Build T-14 and this comparison changes materially. The same honesty rule applies to the two newest wins: T-09's reconciliation is real but scoped to Shopify fulfillment rather than a generic exchange/contract API, and T-12's carrier purchase is real but coexists with a legacy `SIMULATED` default and no FedEx.

---

## 4. Where the existing docs are wrong or stale

Honesty requires flagging this — I verified against code and found prior docs out of date:

| Claim in existing docs | Reality in this repo at `45f389d` | Fix |
|---|---|---|
| Replenishment = "❌ Missing" / "planned" | `ReplenishmentService.java` exists (168 lines, rule CRUD + suggestion generation + approve/reject + stats) | Raise Nexus process score; call it "manual-approve, not system-directed" |
| Serialized/RFID = "❌ Missing (P1 planned)" | `RfidIngestionService.java` exists (391 lines: EPC decode, receive, serialized cycle count, retag, sessions) + `NxSerializedInventory`, `SerializedInventoryRepository`, `RfidController` | Raise Nexus score; stop calling it planned |
| ~~Fulfillment reconciliation controller exists only in `nexus-p0`~~ | **Resolved.** `FulfillmentReconciliationController.java` + `V61__fulfillment_reconciliation.sql` are on `main` via `45f389d` | Claim is now accurate; scope caveat no longer needed |
| "T-17 ships three allocation bugs on `main`" | **Resolved.** Defects fixed in `OrderRoutingService`; pinned by `BrokeringServiceTest` (17 tests) | Score row 1 raised 4 → 5; T-14 is the remaining gap |
| `/analytics/carrier-performance`, `/cost-breakdown`, `/lanes` "real" | All three return hardcoded literals in `AnalyticsController.java` | Existing docs already correct here — keep |
| `nexus-business-process-alignment.md` row 14: MCP = "✅ Covered" (`fa40aff`) | `executeViaBridge` (`McpToolExecutor.java:208`) returns a descriptor; comment at `:210` says "In production, call the underlying service" | **Corrected to 🟡 Partial** — registry/RBAC/budgets are real, execution is not. `fa40aff`'s T-13 half was correct and is retained |
| `nexus-business-process-alignment.md` rows 4 / 9 / 10: replenishment "❌ Missing", routing "⚠ Defective (T-14, T-17)", Serialized/RFID "❌ Missing" | `ReplenishmentService` (168 lines) exists but is manual-approve; T-17 is fixed on `main`; `RfidIngestionService` (391 lines) + `RfidController` + `NxSerializedInventory` + `SerializedInventoryRepository` all exist | **Corrected** to 🟡 Partial in all three rows; routing's only remaining gap is T-14 |
| `nexus-vs-hotwax-technical.md` evidence appendix: `McpToolExecutor` "executes OMS tools via REST/GraphQL layer" | Same placeholder bridge as above | **Corrected** — now states registry/authz/budgeting are real, the bridge is not |

---

## 5. Scoring legend

- **9–10** — production-grade, complete, no material gap
- **7–8** — genuinely working, minor or known gaps
- **5–6** — works for the basic path, thin on depth
- **3–4** — surface exists but core is placeholder/mock/incorrect
- **1–2** — effectively absent

Scores are **capability today on `main` / in the `moqui2` snapshot** — not roadmap, not architecture potential. A placeholder is not credited for the design it hints at.

## 6. Source files cited

All Nexus paths below are in **this** repo at `45f389d`, spot-re-verified at `67d2753` (not `nexus-p0` / `nexus-t17` worktrees — **both are fully merged into `main`; `5619719` and `fd054bc` already appear in this repo's history**).

**Re-scored this revision** (T-17 / T-09 / T-12 evidence):

- `service/OrderRoutingService.java` — T-17's three fixes: `resolveOrderStatus(...)` at `:286` (was a hardcoded `ALLOCATED`; a short order is now `PARTIALLY_ALLOCATED`, a fully-uncovered one `FAILED`), the single-node guard `isSingleNodeAllocation(allocations)` at `:215` + `:299` (per-item `allocatedNodeId`/`allocatedQty` are only stamped when one node truly covers the whole order), and the unconditional backorder raise at `:226` (a shortfall is a correctness signal, not gated on exception-detection config). Shared allocator `allocateStockAware` at `:559`.
- `controller/FulfillmentReconciliationController.java` + `service/FulfillmentReconciliationService.java` + `db/migration/V61__fulfillment_reconciliation.sql` — T-09, on `main`. Scoped to Shopify fulfillments.
- `service/ShippingLabelService.java` + `db/migration/V62__carrier_label_adapter.sql` — T-12, on `main`. Real carrier purchase, but the legacy `SIMULATED` default is still reachable and FedEx is not implemented.
- `test/java/com/nexus/oms/service/BrokeringServiceTest.java` — 17 tests pinning the allocation behaviour.

**Unchanged rows still rest on:** `McpToolExecutor.java` (bridge `executeViaBridge` is a descriptor placeholder), `AnalyticsController.java` (hardcoded `/carrier-performance`, `/cost-breakdown`, `/lanes`), `SyncController.java`, `EdiAutomationService.java`, `BillingService.java`, `ReplenishmentService.java`, `RfidIngestionService.java`, `AutomationEmulatorService.java`, `SyncConflictService.java`.

- HotWax: `oms/service/co/hotwax/oms/recon/{OrderReconServices,ReturnReconServices}.xml`, `order-routing/service/co/hotwax/order/routing/OrderRoutingServices.xml`, `order-routing/sql/{EligibleOrdersQuery,InventorySourceSelector}.sql.ftl`, `poorti/service/co/hotwax/poorti/FulfillmentServices.xml`, `OrderServices.xml` (42 services)
- Cross-references: [`nexus-vs-hotwax-technical.md`](./nexus-vs-hotwax-technical.md), [`nexus-vs-hotwax-competitive.md`](./nexus-vs-hotwax-competitive.md), [`nexus-business-process-alignment.md`](./nexus-business-process-alignment.md), [`nexus-order-routing-comparison.md`](./nexus-order-routing-comparison.md), [`../07-SCM-COMPARISON.md`](../07-SCM-COMPARISON.md)