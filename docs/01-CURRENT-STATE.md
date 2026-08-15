# Nexus OMS — Current State (Where We Are Today)

> Companion to [`00-VISION-DREAM.md`](./00-VISION-DREAM.md). The honest engineering snapshot as of **August 2026**.

---

## 0. Start Here — In plain English 🎒

Imagine a school where you're building a **super clubhouse** with 20 rooms. You've built *all* the rooms — kitchen, games room, library, craft room, storage room. Every room has a door, a sign, and furniture — and the doors all lock now, and the clubhouse even runs its own little **canteen** (billing) with **name-badges for guests** (client portal).

What's still to do:

- **Furniture that wobbles** (a few features are "simulated" — they look real but aren't wired to real providers yet).
- **No real ingredients for the kitchen** (the AI hasn't been given real data to learn from yet).

The **skeleton is complete, the locks work, and the practice exams pass (593/593)**. The job now is **trust**: giving the AI real food to cook with and wiring the last simulated doors to the real world.

That is exactly where Nexus stands: **broad, real surface + a short, honest list of gaps.**

---

## 1. Snapshot

| Dimension | State |
|---|---|
| **Codebase** | Monorepo: `nexus-oms-backend` (Java/Spring Boot) + `nexus-oms-frontend` (React/Vite) |
| **Git history** | 72 commits on `main`, tracked at `github.com/Mr-Mayank-Sharma/nexus` |
| **Migrations** | 56 Flyway migrations (`V1`…`V56`) — schema, seed data, AI `metrics_source`, shipping-label source, multi-client billing, box/kitting catalog, WES tables |
| **Backend surface** | **~136 JPA entities**, **66 controllers**, 50+ services, 14 RBAC roles |
| **Frontend surface** | **89 pages**, hash-routed, role/resource-gated, React Query + WebSockets |
| **Infra (docker-compose)** | Postgres, Redis, Kafka, backend, frontend, **ai-ops**, **ai-intel**, Prometheus, Grafana |
| **Testing** | Backend: **593 unit tests, 0 failures, 0 errors** (`mvn test` green). Frontend: vitest 396 passing (4 pre-existing failures in `newBackendApi.test.ts`/`AuthContext.test.tsx`); Playwright smoke-tested on live dev stack |

> 🧒 **Kid translation of the table:** "Entities" are the *things* we keep track of (orders, boxes, warehouses, cars). "Controllers" are the *doors* people knock on. "Pages" are the *rooms* you see on screen. We built ~136 things, ~66 doors, and ~89 rooms — and the practice exams all pass.

---

## 2. What Works Today (feature-complete surface)

The platform implements a **broad end-to-end commerce-to-dispatch pipeline** — from "customer clicks Buy" to "truck leaves the dock."

### 2.1 Commerce & orders 🛒
- Orders from **Shopify, BigCommerce, Amazon, eBay, Walmart, Magento** + manual + import
- **BOPIS** (buy online, pick up in store) with a full pickup lifecycle — assign picker → pick items (with substitution/short handling) → pack → ready-for-handoff → handoff → **POD collection**; BOPIS shipping labels generated server-side
- **Endless aisle**, store transfers, marketplace listings
- Order import/export engine, bulk CSV/Excel, email order parsing

> 🛍️ **Real life example:** A customer on Amazon buys a blue hoodie. The hoodie is at the downtown store, not the warehouse. Nexus sees the store has 3 blue hoodies, creates the order, and — because the customer chooses store pickup — reserves one hoodie at the store, walks the picker through picking it, and prints a BOPIS handoff label when it's ready. All automatic.

### 2.2 Inventory 📦
- Multi-warehouse, multi-store inventory; reservations, transfers, cycle counts, stock adjustments
- **Available-to-promise (ATP)** — rule-driven engine (safety-stock %, reservation limits), per-node snapshots, reserve/release, `GET /api/v1/atp/nodes?min=50` queries — unit-tested
- Inbound (PO receiving with **10% over-receipt tolerance control**, then **putaway recommendation** via slotting: existing bin → available storage bin → empty bin → any bin, with PUTAWAY audit) and outbound processing — unit-tested

### 2.3 Fulfillment & logistics 🚚
- **Wave planning & release** (order grouping, picklist/task generation), **picking**, packing, loading, shipment, carrier manifests, tracking
- **Distributed order management (DOM)**: deterministic rule-based, AI-assisted and hybrid order routing with `canFulfill` demand checks against fulfillment nodes; orders auto-allocate on confirmation; `fulfillmentType` (SHIP/BOPIS) honored end-to-end — unit-tested
- **Packing**: box templates + recommendation by fit/weight/cost, **kit templates + explosion on order create**, pack validation (`validatePack`) — unit-tested
- **Yard & trailer management** — dock-door assignment, appointments (request → confirm → check-in → check-out), dock utilization & appointment stats — unit-tested
- **WES**: task queue + automation commands (`move`, `putaway`, …) with honest `simulated:true` flags for non-wired equipment, full WES schema (V56: waves, picklist lines, tasks, automation, bin locations) — task-queue PATCH tested
- **Shipping labels** — server-side PDF/ZPL label generation for standard and BOPIS orders, per-label source tracking
- **Mobile RF app (PWA)** — Pick/Pack/Receive/Ship/Count/Scan screens, camera barcode scanning (native + ZXing fallback), server-side scan search, and an **offline-first pick queue** (actions buffered to localStorage, flushed on reconnect) — unit-tested

### 2.4 Returns (RMA) 🔄
- Return requests, inspections, dispositions (restock/destroy/donate/refund), refund workflows

### 2.5 Procurement & warehouse ops 🏗️
- Purchase orders (subtotal/tax/total computation), vendors (supplier codes, tax IDs, status), replenishment rules + replenishment suggestions (engine + controller + rules/suggestions entities); warehouse zones/aisles/bins, labor tasks, automation config (AGV/ASRS), IoT/telemetry hooks
- PO receiving enforces a configurable **over-receipt tolerance** (default 10%) and hands off to **putaway recommendation** — unit-tested

### 2.6 Finance, billing & 3PL 💰
- **Invoicing**: invoice creation (subtotal/tax/discount/shipping → total), payments (`recordPayment` with settlement + over-payment rejection), **refunds + credit memos**, invoice summary (outstanding / overdue / paid-this-month), and an **AR aging report** (`GET /api/v1/invoicing/reports/aging` — current / 1-30 / 31-60 / 61-90 / 90+ buckets) — unit-tested
- **3PL billing**: per-client **rate cards** (per-order / per-line / picking / storage pricing), **billing statement generation** (itemized lines, totals, outstanding/collected KPIs) — live-verified via REST
- **Client portal**: per-client overview (open/fulfilled orders, units, open invoices, outstanding $), orders-by-status, recent orders; client selector in the UI
- Invoices, payments (Stripe connector), QuickBooks connector, KPIs/analytics dashboards, reporting

### 2.7 Integrations hub (iPaaS-lite) 🔌
- `ConnectorFactory`, `EventBus`, `CredentialVault`, `DataMapper`, `ConnectorHealth`, batch jobs, webhooks
- **Protocol adapters**: REST (with connect/read timeouts so unreachable partners fail fast), SOAP, GraphQL, EDI (X12/EDIFACT)
- **EDI 850/856/810** — real X12 parsing (BEG/N1/PO1, BSN/HL/MAN/TD3/TD5, BIG/IT1/TDS), validation, control-number extraction, dry-run, partner management, and **PO → order creation**; parser hardened (field mapping + regex DoS fixes) — unit-tested
- **Outbound dispatch** — `POST /api/v1/integration-platform/endpoints/{id}/dispatch` with exponential-backoff retries, **`Idempotency-Key` dedup**, and **DLQ parking** on final failure (never silently dropped) — unit-tested
- **Connectors**: Shopify, BigCommerce, Magento, Amazon, Salesforce, SAP, QuickBooks, Stripe, Twilio, FedEx, Okta, OpenAI + generic HTTP
- **Import/Export engine + signed import tokens**, `ImportExportEngine`

### 2.8 AI platform (foundation built) 🤖
- `AiService`, `AiHealthCheck`, rule engine, training pipeline, drift detection, model registry, experiments, forecasting, briefings
- **Demand forecasting** — Holt linear + Holt-Winters smoothing with grid-search alpha/beta/gamma, next-7 and next-30 horizons, **WAPE** accuracy and p90 safety-stock view (`GET /api/ai/forecast` + `/evaluate`) — unit-tested
- Dedicated services: **ai-ops** and **ai-intel** containers
- Configurable formulas/thresholds with deterministic fallback (Phase 2.5 hardening)

### 2.9 Security & access 🔐
- JWT auth, 14-role RBAC with resource→action permission gates (deny-by-default), tenant-scoped data everywhere
- MFA/SSO-ready (Okta connector), credential vault for external system keys

---

## 3. What Is Known-Broken / Incomplete (the honesty section)

This is the "wobbly furniture" list. Every gap is named, tracked, and fixable. **G1, G2, G6 and G7 from the baseline are closed; what remains:**

| # | Gap | Plain-English meaning | Fix status |
|---|---|---|---|
| G3 | **AI training path lacks real production data** | The robot has a classroom but **no real homework to learn from** yet | Awaits real data or curated dataset |
| G4 | **Several endpoints are stubs/simulated** | Some doors lead to a *play* room, not the real room (commands say `simulated:true`) | Intentional now; wire to real providers before GA |
| G5 | **Email order parsing is rule-based** | The email reader works for known formats only; strange emails need the ML layer | Phase 2 (real training) |
| G8 | **Automation orchestration is WES-lite** | Task queue + commands exist, but AGV/ASRS/robotic orchestration isn't closed-loop | Needs equipment/emulator + slotting |
| G9 | **Client portal is read-only** | 3PL clients can view KPIs/orders but not yet self-serve (doc upload, approvals) | Roadmap |

> 🧒 **Kid translation:** G3 is the most important to understand. AI is like a chess player. You can build the board, the clock, and the rulebook (that's done ✅), but the player only becomes a *master* after playing **thousands of real games** (that's the missing real data ⏳). We refuse to fake the games — that's the "honesty" promise.

---

## 4. Verified Hardening Completed (Phase 2.5 + the 3PL/WES/EDI sweep)

Reference: `FIX_LOG.md` Phase 2.5 plus the Aug 2026 fulfillment/billing/EDI work. All committed and pushed to `main`.

- **`AiRuleEngineService`** — removed `java.util.Random`; formula/threshold evaluation is now **fully deterministic** (config-driven, documented defaults, rounded to 2dp), risk scores in `[0,1]` derived from real order input.
- **`AiTrainingPipelineService.completeJob`** — stores **null metrics + `metricsSource="NO_METRICS"`** when the job has no real results; only creates a model version when genuine metrics exist.
- **`AutomationService`** — removed random ACK delays and fabricated execution times; results are real `Duration` elapsed, and simulated paths are **explicitly flagged** `"simulated":true`.
- **`RestProtocolAdapter`** — REST calls now enforce connect/read timeouts so an unreachable partner fails in ~3s instead of hanging.
- **`EdiAutomationService`** — corrected X12 field mapping (off-by-one in BEG/N1/BSN/TD3/PO1/IT1 indexing) and replaced a catastrophic-backtracking regex that could hang on malformed input; 850 now creates real orders end-to-end.
- **Backend test suite** — expanded from 501 → **593 green tests** with new suites for ATP, yard/dock, procurement (incl. over-receipt tolerance + putaway), invoicing (incl. AR aging), EDI (incl. dry-run/upload), BOPIS pickup lifecycle, billing, rate cards, waves, task-queue, DOM order routing, box recommendation, kitting, AI demand forecast and integration outbound.

> 🎯 **Real life example of why this matters:**
> Before: an AI "safety score" for an order was partly decided by **rolling dice** (`Random`). So the *same* order could score 80% one second and 55% the next.
> After: the *same* order ALWAYS gets the *same* score — because it's computed from the order's real data using a fixed formula. You can replay any decision and get the identical answer. That's what "deterministic" means, and it's what makes a system *auditable* and *trustworthy*.

---

## 5. Engineering Rigor in Place 🏗️

- **Determinism**: rule engine + automation are reproducible and auditable (no hidden randomness).
- **Data integrity**: Flyway-versioned schema (V54); tenant-scoped queries; JSONB for flexible payloads.
- **Observability**: Actuator + Micrometer + Prometheus + Grafana; logstash JSON logging; tracing (Brave).
- **Resilience**: Resilience4j circuit breakers, Kafka event bus for async integration, Redis caching, integration timeouts.
- **API docs**: springdoc OpenAPI (Swagger UI) generated from controllers.
- **Security posture**: JWT (jjwt), resource→action authorization filter, signed import tokens, credential vault.
- **Testing**: 593 backend unit tests green; frontend vitest 396 passing (4 pre-existing failures in `newBackendApi.test.ts` / `AuthContext.test.tsx`); Playwright smoke suite on the live dev stack (rate cards, billing statements, client portal) with 0 console errors.

> 🧒 **Kid translation:** We have **safety rails**: a seatbelt (circuit breakers so one broken part doesn't crash everything), a speedometer (Prometheus/Grafana so we see what's happening), and a map book (OpenAPI docs so developers know every door).

---

## 6. Frontend Access Landscape (verified) 🖥️

- Hash-router with lazy routes; `ProtectedRoute` (auth) wrapping an `AppLayout` shell; `PermissionGate` (resource+action) for fine-grained UI.
- **89 pages** across: LaunchPad, Orders, Fulfillment, Inventory & Warehouses, Returns, Procurement, Vendors, Carriers, Yard/Trailers, Integrations, Import/Export, Finance, Analytics, AI, Admin, Support — plus the **3PL module** (Rate Cards, Billing Statements, Client Portal).
- 14 roles enforced server-side; UI gates mirror resource mappings.

---

## 7. Where the Dream Meets Reality (vs `00-VISION-DREAM.md`)

| Dream (2030) | Current | Missing |
|---|---|---|
| AI routes orders in <1s | Deterministic + AI/hybrid DOM routing with `canFulfill`; unit-tested | Real trained models + online inference |
| Self-optimizing warehouse | Wave plan/release + task queue + WES schema (V56) + putaway recommendation | Closed-loop slotting/labor optimization (G8) |
| Trustworthy AI platform | Model registry, experiments, drift, fallbacks, audit; demand forecast (Holt + WAPE) shipped | Real training data → **no production model yet** (G3) |
| One-touch integrations | 15+ connectors + EDI 850/856/810 + iPaaS-lite + outbound retry/idempotency/DLQ | Depth (auth refresh, ASN/856 → inbound) |
| 3PL-grade billing | Rate cards + statements + read-only client portal; AR aging report | Self-serve portal, credit/payment workflows (G9) |
| GA quality | 56 migrations, 66+ controllers, 89 pages, 593 backend tests green | Stub→real provider wiring (G4), real ML data (G3) |

**Bottom line:** the *architecture* and *surface* of the dream product exist, the locks work (RBAC gates), and the practice exams all pass. The remaining distance is **trust**: real data for ML, real provider wiring, and closed-loop automation.

---

*Next: [`02-TECHNICAL-ARCHITECTURE.md`](./02-TECHNICAL-ARCHITECTURE.md) — the full stack and module map.*
