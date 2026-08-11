# Nexus OMS — Current State (Where We Are Today)

> Companion to [`00-VISION-DREAM.md`](./00-VISION-DREAM.md). The honest engineering snapshot as of **August 2026**.

---

## 0. Start Here — In plain English 🎒

Imagine a school where you're building a **super clubhouse** with 20 rooms. You've already built *all* the rooms — there's a kitchen, a games room, a library, a craft room, a storage room. Every room has a door, a sign, and furniture.

But some rooms still have:

- **Furniture that wobbles** (some features are "simulated" — they look real but aren't wired to the real world yet).
- **A few doors that don't lock properly** (some security gates need fixing).
- **No real ingredients** for the kitchen (the AI hasn't been given real data to learn from yet).

The **skeleton is complete** — every room exists. The job now is **trust**: making every door lock, every light real, and giving the AI real food to cook with.

That is exactly where Nexus stands: **broad, real surface + a short list of honest gaps.**

---

## 1. Snapshot

| Dimension | State |
|---|---|
| **Codebase** | Monorepo: `nexus-oms-backend` (Java/Spring Boot) + `nexus-oms-frontend` (React/Vite) |
| **Git history** | 65 commits on `main`, tracked at `github.com/Mr-Mayank-Sharma/nexus` |
| **Migrations** | 44 Flyway migrations (`V1`…`V52`) — schema, seed data, AI `metrics_source` column |
| **Backend surface** | **~190 JPA entities**, **63 controllers**, 50+ services, 14 RBAC roles |
| **Frontend surface** | **86 pages**, hash-routed, role/resource-gated, React Query + WebSockets |
| **Infra (docker-compose)** | Postgres, Redis, Kafka, backend, frontend, **ai-ops**, **ai-intel**, Prometheus, Grafana |
| **Testing** | Frontend: vitest present. **Backend test suite currently does NOT compile** (see §6) |

> 🧒 **Kid translation of the table:** "Entities" are the *things* we keep track of (orders, boxes, warehouses, cars). "Controllers" are the *doors* people knock on. "Pages" are the *rooms* you see on screen. We built ~190 things, ~63 doors, and ~86 rooms.

---

## 2. What Works Today (feature-complete surface)

The platform implements a **broad end-to-end commerce-to-dispatch pipeline** — from "customer clicks Buy" to "truck leaves the dock."

### 2.1 Commerce & orders 🛒
- Orders from **Shopify, BigCommerce, Amazon, eBay, Walmart, Magento** + manual + import
- **BOPIS** (buy online, pick up in store), **endless aisle**, store transfers, marketplace listings
- Order import/export engine, bulk CSV/Excel, email order parsing
- Pickup/pay-in-store flows, store & warehouse inventory reservation

> 🛍️ **Real life example:** A customer on Amazon buys a blue hoodie. The hoodie is at the downtown store, not the warehouse. Nexus sees the store has 3 blue hoodies, creates the order, and — because the customer chooses store pickup — reserves one hoodie at the store and shows "ready for pickup in 2 hours." All automatically.

### 2.2 Inventory 📦
- Multi-warehouse, multi-store inventory; reservations, transfers, cycle counts, stock adjustments
- **Available-to-promise (ATP)**, inbound (PO receiving) and outbound processing

### 2.3 Fulfillment & logistics 🚚
- Wave planning, **picking**, packing, loading, shipment, carrier manifests, tracking
- **Yard & trailer management**, dock doors, carriers, lanes, freight quotes
- Print label generation, box/pack configuration

### 2.4 Returns (RMA) 🔄
- Return requests, inspections, dispositions (restock/destroy/donate/refund), refund workflows

### 2.5 Procurement & warehouse ops 🏗️
- Purchase orders, vendors, receiving; warehouse zones/aisles/bins, labor tasks, automation config (AGV/ASRS), IoT/telemetry hooks

### 2.6 Finance & analytics 💰
- Invoices, payments (Stripe connector), QuickBooks connector, KPIs/analytics dashboards, reporting

### 2.7 Integrations hub (iPaaS-lite) 🔌
- `ConnectorFactory`, `EventBus`, `CredentialVault`, `DataMapper`, `ConnectorHealth`, batch jobs, webhooks
- **Protocol adapters**: REST, SOAP, GraphQL, EDI (X12/EDIFACT)
- **Connectors**: Shopify, BigCommerce, Magento, Amazon, Salesforce, SAP, QuickBooks, Stripe, Twilio, FedEx, Okta, OpenAI + generic HTTP
- **Import/Export engine + signed import tokens**, `ImportExportEngine`

### 2.8 AI platform (foundation built) 🤖
- `AiService`, `AiHealthCheck`, rule engine, training pipeline, drift detection, model registry, experiments, forecasting, briefings
- Dedicated services: **ai-ops** and **ai-intel** containers
- Configurable formulas/thresholds with deterministic fallback (Phase 2.5 hardening)

### 2.9 Security & access 🔐
- JWT auth, 14-role RBAC with 39 path→resource mappings, tenant-scoped data everywhere
- MFA/SSO-ready (Okta connector), credential vault for external system keys

---

## 3. What Is Known-Broken / Incomplete (the honesty section)

This is the "wobbly furniture" list. Every gap is named, tracked, and fixable.

| # | Gap | Plain-English meaning | Fix status |
|---|---|---|---|
| G1 | **Backend tests don't compile** | The *practice quizzes* for our code are outdated and fail to even start | **Blocked on design** — tests must be updated to current code |
| G2 | **6 frontend gates use a broken `permission` prop** | 6 doors check the wrong lock; the server still protects, but the UI sign could mislead | Needs UX/security review |
| G3 | **AI training path lacks real production data** | The robot has a classroom but **no real homework to learn from** yet | Awaits real data or curated dataset |
| G4 | **Several endpoints are stubs/simulated** | Some doors lead to a *play* room, not the real room (commands say `simulated:true`) | Intentional now; wire to real providers before GA |
| G5 | **Email order parsing is rule-based** | The email reader works for known formats only; strange emails need the ML layer | Phase 2 (real training) |
| G6 | **`RoleProtectedRoute` legacy guard is unused** | An old key that nobody uses anymore should be thrown away | Cleanup task |
| G7 | **ER/relationship documentation absent** | No map of how all the rooms connect existed → this doc set adds it | ✅ Done in this documentation initiative |

> 🧒 **Kid translation:** G3 is the most important to understand. AI is like a chess player. You can build the board, the clock, and the rulebook (that's done ✅), but the player only becomes a *master* after playing **thousands of real games** (that's the missing real data ⏳). We refuse to fake the games — that's the "honesty" promise.

---

## 4. Verified Hardening Completed (Phase 2.5 — the honesty sweep)

Reference: `FIX_LOG.md` Phase 2.5. All changes committed (`cbb26f6`) and pushed.

- **`AiRuleEngineService`** — removed `java.util.Random`; formula/threshold evaluation is now **fully deterministic** (config-driven, documented defaults, rounded to 2dp), risk scores in `[0,1]` derived from real order input.
- **`AiTrainingPipelineService.completeJob`** — stores **null metrics + `metricsSource="NO_METRICS"`** when the job has no real results; only creates a model version when genuine metrics exist; durations/epochs only recorded when provided.
- **`AutomationService`** — removed random ACK delays and fabricated execution times; results are real `Duration` elapsed, and simulated paths are **explicitly flagged** `"simulated":true`.
- **`AiTrainingJob`** — new `metricsSource` column (`V52` migration) so every training job records where its metrics came from.
- **Kept intentionally**: `SampleDataGenerator` (explicit demo generator) and `ShippingLabelService` (opaque tracking-number suffix).

> 🎯 **Real life example of why this matters:**
> Before: an AI "safety score" for an order was partly decided by **rolling dice** (`Random`). So the *same* order could score 80% one second and 55% the next. A human auditing decisions couldn't reproduce why. 
> After: the *same* order ALWAYS gets the *same* score — because it's computed from the order's real data (price, size, quantity) using a fixed formula. You can replay any decision and get the identical answer. That's what "deterministic" means, and it's what makes a system *auditable* and *trustworthy*.

---

## 5. Engineering Rigor in Place 🏗️

- **Determinism**: rule engine + automation are reproducible and auditable (no hidden randomness).
- **Data integrity**: Flyway-versioned schema; tenant-scoped queries; JSONB for flexible payloads.
- **Observability**: Actuator + Micrometer + Prometheus + Grafana; logstash JSON logging; tracing (Brave).
- **Resilience**: Resilience4j circuit breakers, Kafka event bus for async integration, Redis caching.
- **API docs**: springdoc OpenAPI (Swagger UI) generated from controllers.
- **Security posture**: JWT (jjwt), path→resource authorization filter, signed import tokens, credential vault.

> 🧒 **Kid translation:** We have **safety rails**: a seatbelt (circuit breakers so one broken part doesn't crash everything), a speedometer (Prometheus/Grafana so we see what's happening), and a map book (OpenAPI docs so developers know every door).

---

## 6. Known Test Blockers (detail)

```
mvn test
  ERROR: PickingServiceTest.java:[40]  — constructor no longer matches PickingService
  ERROR: DashboardServiceTest.java:[40,81] — getOrderVelocity() no longer takes 0 args / DashboardService constructor drift
```
Verified **pre-existing**: identical failures occur on the base commit before Phase 2.5 (`git stash` proof). These are stale tests, not regressions.

> 🧒 **Kid translation:** The code got upgraded (like a bicycle getting new gears), but two old instruction manuals still describe the old bicycle. Fixing the manuals is a small, known job.

---

## 7. Frontend Access Landscape (verified) 🖥️

- Hash-router with lazy routes; `ProtectedRoute` (auth) wrapping an `AppLayout` shell; `PermissionGate` (resource+action) for fine-grained UI.
- **86 pages** across: LaunchPad, Orders, Fulfillment, Inventory & Warehouses, Returns, Procurement, Vendors, Carriers, Yard/Trailers, Integrations, Import/Export, Finance, Analytics, AI, Admin, Support.
- 14 roles enforced server-side; UI gates mirror resource mappings (with the G2 caveat).

---

## 8. Where the Dream Meets Reality (vs `00-VISION-DREAM.md`)

| Dream (2030) | Current | Missing |
|---|---|---|
| AI routes orders in <1s | Deterministic routing rules exist; ML router pending | Real trained models + online inference |
| Self-optimizing warehouse | Wave planning + automation config exist | Closed-loop slotting/labor optimization |
| Trustworthy AI platform | Model registry, experiments, drift, fallbacks, audit | Real training data → **no production model yet** |
| One-touch integrations | 14 connectors + EDI/email + iPaaS-lite | Depth (auth refresh, idempotent sync state) |
| GA quality | 44 migrations, 63 controllers, 86 pages | Test suite repair, stub→real provider wiring, G2 gates |

**Bottom line:** the *architecture* and *surface* of the dream product exist. The remaining distance is **trust**: real data for ML, real provider wiring, repaired tests, and closed security gates.

---

*Next: [`02-TECHNICAL-ARCHITECTURE.md`](./02-TECHNICAL-ARCHITECTURE.md) — the full stack and module map.*
