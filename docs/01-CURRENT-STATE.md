# Nexus OMS — Current State (Where We Are Today)

> Companion to [`00-VISION-DREAM.md`](./00-VISION-DREAM.md). This document is the **honest engineering snapshot** of the platform as of **August 2026**.

---

## 1. Snapshot

| Dimension | State |
|---|---|
| **Codebase** | Monorepo: `nexus-oms-backend` (Java/Spring Boot) + `nexus-oms-frontend` (React/Vite) |
| **Git history** | 65 commits on `main`, single-tracked remote (`github.com/Mr-Mayank-Sharma/nexus`) |
| **Migrations** | 44 Flyway migrations (`V1`…`V52`) — schema, seed data, AI `metrics_source` column |
| **Backend surface** | **~190 JPA entities**, **63 controllers**, 50+ services, 14 RBAC roles |
| **Frontend surface** | **86 pages**, hash-routed, role/resource-gated, React Query + WebSockets |
| **Infra (docker-compose)** | Postgres, Redis, Kafka, backend, frontend, **ai-ops**, **ai-intel**, Prometheus, Grafana |
| **Testing** | Frontend: vitest present. **Backend test suite currently does NOT compile** (see §6) |

---

## 2. What Works Today (feature-complete surface)

The platform implements a **broad end-to-end commerce-to-dispatch pipeline**:

### 2.1 Commerce & orders
- Orders from **Shopify, BigCommerce, Amazon, eBay, Walmart, Magento** (connectors in `integration/connector/ecommerce/`) + manual/import
- **BOPIS** (buy online pick up in store), **endless aisle**, store transfers, marketplace listings
- Order import/export engine, bulk CSV/Excel (`poi-ooxml`, `pdfbox` for docs), email order parsing
- Pickup/pay-in-store flows, store & warehouse inventory reservation

### 2.2 Inventory
- Multi-warehouse, multi-store inventory; reservations, transfers, cycle counts, stock adjustments
- **Available-to-promise (ATP)** logic, inbound (PO receiving) and outbound processing

### 2.3 Fulfillment & logistics
- Wave planning, **picking**, packing, loading, shipment, carrier manifests, tracking
- **Yard & trailer management**, dock doors, carriers, lanes, freight quotes
- Print label generation, box/pack configuration

### 2.4 Returns (RMA)
- Return requests, inspections, dispositions (restock/destroy/donat/refund), refund workflows

### 2.5 Procurement & warehouse ops
- Purchase orders, vendors, receiving; warehouse zones/aisles/bins, labor tasks, automation config (AGV/ASRS), IoT/telemetry hooks

### 2.6 Finance & analytics
- Invoices, payments (Stripe connector), QuickBooks connector, KPIs/analytics dashboards, reporting

### 2.7 Integrations hub (iPaaS-lite)
- `integration/core`: `ConnectorFactory`, `EventBus`, `CredentialVault`, `DataMapper`, `ConnectorHealth`, batch jobs, webhooks
- **Protocol adapters**: REST, SOAP, GraphQL, EDI (X12/EDIFACT)
- **Connectors**: Shopify, BigCommerce, Magento, Amazon, Salesforce, SAP, QuickBooks, Stripe, Twilio, FedEx, Okta, OpenAI + generic HTTP
- **Import/Export engine + signed import tokens** (`ImportTokenService`, `GenericImportService`), `ImportExportEngine`

### 2.8 AI platform (foundation built)
- `ai/`: `AiService`, `AiHealthCheck`, rule engine (`AiRuleEngineService`, `RuleConditionEvaluator`), training pipeline (`AiTrainingPipelineService`), drift detection, model registry, experiments, forecasting, briefings
- Dedicated services in the stack: **ai-ops** (operational intelligence) and **ai-intel** (Intel-style analytics/briefing) containers
- Configurable formulas/thresholds with deterministic fallback (Phase 2.5 hardening)

### 2.9 Security & access
- JWT auth, 14-role RBAC with 39 path→resource mappings, tenant-scoped data everywhere
- MFA/SSO-ready (Okta connector), credential vault for external system keys

---

## 3. What Is Known-Broken / Incomplete

This is the **honesty section** — the gap between "has a screen + endpoint" and "trustworthy".

| # | Gap | Detail | Fix status |
|---|---|---|---|
| G1 | **Backend tests don't compile** | `PickingServiceTest` (line 40) and `DashboardServiceTest` (lines 40, 81) reference stale constructor/method signatures | **Blocked on design** — tests must be updated to current service APIs |
| G2 | **6 frontend gates use a broken `permission` prop** | `PermissionGate` uses `resource`+`action`; these pages pass a `permission` prop that is ignored → gate can mis-authorize | Needs UX/security review; list of pages captured in exploration |
| G3 | **AI training path lacks real production data** | `completeJob` now honestly marks jobs `NO_METRICS` when no real metrics exist; no real dataset yet, so no model version is produced | Awaits real warehouse/order history or curated dataset |
| G4 | **Several endpoints are stubs/simulated** | Automation `sendCommand`/`retryCommand` return `simulated:true` results; some connector calls are mocked at the wire level | Intentional for now; must be wired to real providers before GA |
| G5 | **Email order parsing is rule-based** | Works for known formats; arbitrary email orders need the ML layer | Phase 2 (real training) |
| G6 | **`RoleProtectedRoute` legacy guard is unused** | Frontend has two guard systems; dead code should be removed to avoid confusion | Cleanup task |
| G7 | **ER/relationship documentation absent** | No entity-relationship diagram existed → this doc set adds it (`03-ER-DIAGRAM.md`) | Done as part of this documentation initiative |

---

## 4. Verified Hardening Completed (Phase 2.5 — the honesty sweep)

Reference: `FIX_LOG.md` Phase 2.5. All changes committed (`cbb26f6`) and pushed.

- **`AiRuleEngineService`** — removed `java.util.Random`; formula/threshold evaluation is now **fully deterministic** (config-driven, documented defaults, rounded to 2dp), risk scores in `[0,1]` derived from real order input.
- **`AiTrainingPipelineService.completeJob`** — stores **null metrics + `metricsSource="NO_METRICS"`** when the job has no real results; only creates a model version when genuine metrics exist; durations/epochs only recorded when provided.
- **`AutomationService`** — removed random ACK delays and fabricated execution times; results are real `Duration` elapsed, and simulated paths are **explicitly flagged** `"simulated":true`.
- **`AiTrainingJob`** — new `metricsSource` column (`V52` migration) so every training job records where its metrics came from.
- **Kept intentionally**: `SampleDataGenerator` (explicit demo generator) and `ShippingLabelService` (opaque tracking-number suffix).

---

## 5. Engineering Rigor in Place

- **Determinism**: rule engine + automation are reproducible and auditable (no hidden randomness).
- **Data integrity**: Flyway-versioned schema; tenant-scoped queries; JSONB where flexible payloads needed.
- **Observability**: Actuator + Micrometer + Prometheus + Grafana compose stack; logstash JSON logging; tracing (Brave).
- **Resilience**: Resilience4j circuit breakers, Kafka event bus for async integration, Redis caching.
- **API docs**: springdoc OpenAPI (Swagger UI) generated from controllers.
- **Security posture**: JWT with jjwt, path→resource authorization filter, signed import tokens, credential vault.

---

## 6. Known Test Blockers (detail)

```
mvn test
  ERROR: [ERROR] /.../PickingServiceTest.java:[40]  — constructor no longer matches PickingService
  ERROR: [ERROR] /.../DashboardServiceTest.java:[40,81] — getOrderVelocity() no longer takes 0 args / DashboardService constructor drift
```
Verified **pre-existing**: identical failures occur on the base commit before Phase 2.5 (`git stash` proof). These are stale tests, not regressions from the honesty sweep.

---

## 7. Frontend Access Landscape (verified)

- Hash-router with lazy routes; `ProtectedRoute` (auth) wrapping an `AppLayout` shell; `PermissionGate` (resource+action) for fine-grained UI.
- **86 pages** across: LaunchPad, Orders, Fulfillment (picking/packing/loading/shipment), Inventory & Warehouses, Returns, Procurement, Vendors, Carriers, Yard/Trailers, Integrations, Import/Export, Finance, Analytics, AI (training, rules, experiments, briefings), Admin (users/roles/tenants), Support.
- 14 roles enforced server-side; UI gates mirror resource mappings (with the G2 caveat above).

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
