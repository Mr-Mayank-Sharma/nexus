# Nexus OMS — Technical Architecture

> Companion: [`01-CURRENT-STATE.md`](./01-CURRENT-STATE.md) · [`03-ER-DIAGRAM.md`](./03-ER-DIAGRAM.md)

---

## 0. Start Here — Plain-English map of the machine 🏗️

Think of Nexus as a **restaurant with 4 floors**:

- **Floor 1 — The counter (Frontend):** where customers and staff see everything. In a restaurant, this is the menu, the order tickets, the kitchen screens.
- **Floor 2 — The kitchen (Backend):** where all the real cooking happens. The chefs are the 63 "controllers" and 50+ "services". They receive a ticket (request) and produce the food (data).
- **Floor 3 — The pantry (Database & caches):** where all ingredients live — Postgres is the big fridge (everything saved permanently), Redis is the counter-side tray (things we grab fast, like today's special), Kafka is the kitchen bell system (announcements that different teams listen to).
- **Floor 4 — The robot friends (AI services):** `ai-ops` cooks smarter (optimizes operations) and `ai-intel` writes the daily summary for the boss.
- **Security guard at the door (RBAC):** checks your ID (JWT) and your badge (role) before letting you in — and only into the rooms you're allowed.

Every floor talks to the others using agreed "languages" (HTTP/JSON for requests, STOMP/WebSocket for live updates). Everything is monitored by **cameras** (Prometheus + Grafana).

> 🧒 **Kid translation:** Frontend = the screens. Backend = the brain. Database = the memory. Kafka = the walkie-talkie. Redis = the speed rack. AI = the smart helpers. RBAC = the security guard.

---

## 1. System Overview

Nexus OMS is a **cloud-native, multi-tenant** commerce-to-dispatch platform with a Java/Spring Boot backend, a React SPA frontend, and a compose-deployed infrastructure of PostgreSQL, Redis, Kafka, Prometheus and Grafana, plus two purpose-built AI services (`ai-ops`, `ai-intel`).

```
┌─────────────────────────────┐        ┌──────────────────────────────┐
│         FRONTEND            │        │       EXTERNAL WORLD         │
│  React 18 + Vite (SPA)     │        │  Shopify · BigCommerce · Amazon│
│  HashRouter · React Query   │        │  eBay · Walmart · Magento     │
│  WebSockets (STOMP/SockJS)  │        │  Stripe · FedEx · QuickBooks  │
│  Tailwind + Radix + Recharts│        │  Salesforce · SAP · Twilio    │
└─────────────┬───────────────┘        │  Okta · OpenAI · Generic HTTP │
              │  REST (axios, JWT)     └──────────────┬───────────────┘
              │  WS (stomp)                           │ connectors
┌─────────────▼───────────────┐        ┌──────────────▼───────────────┐
│         BACKEND             │        │        INTEGRATION HUB        │
│  Spring Boot 3 · Java 17    │◄──────►│  ConnectorFactory · EventBus  │
│  Spring Web · Security · JPA│        │  CredentialVault · DataMapper │
│  Validation · Cache · AOP   │        │  REST / SOAP / GraphQL / EDI  │
│  WebSocket (STOMP)          │        │  Webhooks · Batch · iPaaS-lite│
│  springdoc OpenAPI          │        └───────────────────────────────┘
└───────┬──────────┬──────────┘
        │ JPA       │ async
┌───────▼───┐  ┌────▼──────┐   ┌─────────┐   ┌─────────┐   ┌──────────┐
│ Postgres  │  │  Kafka    │   │  Redis  │   │  ai-ops │   │  ai-intel│
│ (Flyway)  │  │ (events)  │   │ (cache) │   │ (ops AI)│   │ (intel)  │
└───────────┘  └───────────┘   └─────────┘   └─────────┘   └──────────┘
                        ┌──────────────────────────────────────┐
                        │  Observability                       │
                        │  Actuator · Micrometer · Prometheus  │
                        │  Grafana · Logstash JSON · Brave     │
                        └──────────────────────────────────────┘
```

---

## 2. Technology Stack

> 🧒 **Kid translation of "stack":** The pile of tools we used to build everything — like the wood, nails, paint and robot arms used to build the clubhouse.

### 2.1 Backend (`nexus-oms-backend`)

| Concern | Technology | Plain-English job |
|---|---|---|
| Runtime | Java 17, Spring Boot 3 | The engine that runs the kitchen |
| Web | spring-boot-starter-web, spring-boot-starter-websocket | The doorbell + the intercom (REST + live chat) |
| Data | spring-boot-starter-data-jpa, PostgreSQL, Flyway | The fridge + recipe book that records every schema change |
| Security | spring-boot-starter-security, jjwt | The guard + ID cards (JWT) |
| Messaging | spring-kafka | The walkie-talkie between teams |
| Caching | spring-boot-starter-data-redis + cache | The speed rack for things fetched often |
| Cross-cutting | AOP, Validation, Resilience4j | The toolbox: seatbelts, rules, checks |
| AI/Docs | springdoc-openapi, jackson | The map book (API docs) + the translator (JSON) |
| Files/Reports | Apache POI, PDFBox, SAAJ | The printer (Excel, PDF, SOAP) |
| Observability | actuator, micrometer, logstash, brave | The cameras and CCTV |
| Boilerplate | Lombok | The robot that writes boring repetitive code for us |

### 2.2 Frontend (`nexus-oms-frontend`)

| Concern | Technology | Plain-English job |
|---|---|---|
| Framework | React 18 + TypeScript, Vite | The screens + paint, Vite = fast paint brush |
| Routing | react-router-dom v6 (hash) | The signposts between pages |
| Data | axios, @tanstack/react-query | The courier + smart inbox (caches answers) |
| Realtime | @stomp/stompjs + sockjs | Live "ticket arrived" updates |
| UI | Tailwind, Radix, cva, clsx, lucide | The furniture and decorations |
| Charts | recharts | The graphs |
| Sanitization | dompurify | The filter that blocks dangerous messages |
| Tests | vitest | The practice quizzes |

### 2.3 Infrastructure (docker-compose)

| Service | Plain-English job |
|---|---|
| `postgres` | The big permanent fridge |
| `redis` | The fast counter tray |
| `kafka` | The announcement speaker system |
| `backend` | The main kitchen |
| `frontend` | The screens at the counter |
| `ai-ops` | Robot that optimizes operations |
| `ai-intel` | Robot that writes boss briefings |
| `prometheus`/`grafana` | Cameras + control-room dashboards |

---

## 3. Backend Layering

```
┌─────────────────────────────────────────────────────────────┐
│ CONTROLLER LAYER   — 63 controllers, REST + WebSocket        │
│   /api/** · /ws/** · openapi at /v3/api-docs, /swagger-ui    │
├─────────────────────────────────────────────────────────────┤
│ APPLICATION LAYER — 50+ services                            │
│   Order orchestration · Inventory · Fulfillment · Shipping   │
│   Returns · Procurement · Warehouse/Yard · Finance ·       │
│   AI platform · Integrations · Import/Export · Auth/RBAC     │
├─────────────────────────────────────────────────────────────┤
│ DOMAIN LAYER      — ~190 JPA entities (tenant-scoped)        │
│   @ManyToOne / @OneToMany / @OneToOne / @ElementCollection   │
│   jsonb columns for flexible payloads                        │
├─────────────────────────────────────────────────────────────┤
│ INFRASTRUCTURE    — Postgres · Redis · Kafka · Flyway        │
│   Resilience4j · Cache · AOP aspects · EventBus · WebSocket   │
└─────────────────────────────────────────────────────────────┘
```

> 🍕 **Real life analogy — ordering a pizza through the layers:**
> 1. **Controller** = the waiter who takes your order at the counter ("I'll have a pepperoni").
> 2. **Service** = the head chef who decides *how* to make it ("grab dough, add sauce, add cheese, bake 8 min").
> 3. **Domain (entities)** = the actual ingredients — dough, sauce, cheese, pizza box — each with a name tag.
> 4. **Infrastructure** = the fridge (Postgres), the speed rack (Redis), and the bell to call the delivery team (Kafka).

### 3.1 Cross-cutting services

| Service | Responsibility | Plain-English |
|---|---|---|
| `PermissionService` | 39 path→resource mappings; role-permission lookup, 60s cache, allow-by-default | The list that says *which key opens which door* |
| `PermissionAuthorizationFilter` | Resolves path→resource→permission before controllers | The guard checking keys before you enter |
| `AuthService` | Login, JWT issuance, `ALLOWED_ROLES` (14 roles) | The ID-card office |
| `ImportTokenService` | Signed, expiring tokens for bulk import | The signed permission slip for big deliveries |
| `GenericImportService`/`ImportExportEngine` | CSV/Excel/JSON import + export | The bulk-copy machine |
| `EventBus` | In-process + Kafka-backed domain events | The announcement system |
| `CredentialVault` | Encrypted storage of external-connector credentials | The locked safe for passwords to other systems |

---

## 4. Integration Hub (iPaaS-lite) 🔌

Path: `integration/`

```
integration/
├── core/
│   ├── ConnectorFactory.java        # registry + instantiation
│   ├── ConnectorFactoryConfiguration.java
│   ├── AbstractConnector.java       # base lifecycle
│   ├── BaseApiConnector.java
│   ├── GenericHttpConnector.java
│   ├── DataMapper.java              # external ↔ internal DTO mapping
│   ├── CredentialVault.java
│   ├── EventBus.java
│   ├── ConnectorHealth.java
│   └── IntegrationHubController.java
├── connector/
│   ├── ecommerce/  Shopify · BigCommerce · Magento · Amazon
│   ├── accounting/ QuickBooks
│   ├── shipping/   FedEx
│   ├── payment/    Stripe
│   ├── crm/        Salesforce
│   ├── erp/        Sap
│   ├── identity/   Okta
│   ├── communication/ Twilio
│   └── ai/         OpenAi
├── protocol/
│   ├── RestProtocolAdapter · SoapProtocolAdapter
│   ├── GraphqlProtocolAdapter · EdiProtocolAdapter
├── webhook/        WebhookIntegrationController
├── batch/          BatchJobService
└── dto/            ConnectorConfig · SyncResult · IntegrationEvent
```

> 🧒 **Kid translation:** The Integration Hub is like a **universal plug adapter**. Each store (Shopify, Amazon…) speaks its own "language" (REST, SOAP, EDI…). Nexus has one plug per language and one plug per store — so any store can connect without us rebuilding the kitchen.

**Design principles**
- One `Connector` interface; adapters translate protocol differences.
- Credentials never leave `CredentialVault`; secrets encrypted at rest.
- `EventBus` decouples ingestion (webhook/poll/batch) from processing (Kafka).
- Every connector exposes `ConnectorHealth` for the hub dashboard (like a doctor's check-up per plug).

---

## 5. AI Platform Architecture 🤖

```
                    ┌──────────────────────────────────────────┐
                    │              AI PLATFORM                 │
   Order/inventory  │  AiService (orchestrator)                │
   signals ────────►│   ├─ ForecastingEngine                   │
                    │   ├─ DemandForecastService               │
                    │   ├─ BriefingEngine / AiBriefingService  │
                    │   ├─ RuleEngine: AiRuleEngineService     │
                    │   │    └─ RuleConditionEvaluator         │
                    │   ├─ Training: AiTrainingPipelineService │
                    │   │    └─ AiTrainingJob (metricsSource)  │
                    │   ├─ Drift: DriftDetectionService        │
                    │   ├─ Models: ModelRegistryService        │
                    │   ├─ Experiments: ExperimentService      │
                    │   └─ Health: AiHealthCheck               │
                    │   External: OpenAiConnector (optional)   │
                    └──────────────────────────────────────────┘
```

> 🎓 **Real life example — teaching a robot to guess sales:**
> 1. **Collect** (features/dataset): "Last 12 months, sold 100 hoodies in Jan, 40 in Feb, 130 in Dec…"
> 2. **Train** (`AiTrainingJob`): the robot studies the pattern (winter = more hoodies).
> 3. **Evaluate**: we measure how close the robot's guesses are. **If we have no real test data, we say `NO_METRICS` — we never invent the score.** ✅ (This is the Phase 2.5 honesty promise.)
> 4. **Register/Deploy**: the approved robot goes to work.
> 5. **Monitor**: if the real world stops matching ("everyone now buys crop tops"), drift detection switches to **safe rules** until retrained.

### 5.1 Honesty model (Phase 2.5)
- **`AiTrainingJob.metricsSource`** (`V52`): `REAL` / `NO_METRICS` — no fabricated training metrics.
- **Deterministic fallbacks**: `AiRuleEngineService` computes formula values and thresholds from real order input + config with documented defaults (no `Random`).
- **Automation** returns real elapsed durations; simulated paths carry `"simulated":true`.
- **Model versioning**: a new model version is created only when the training job produces real metrics.

### 5.2 Model lifecycle (target)
```
collect → prepare → train (job) → evaluate (real metrics) → register (model registry)
  → deploy champion → serve online → monitor drift → retrain / fallback to rules
```

---

## 6. Security Architecture 🔐

```
 Request → PermissionAuthorizationFilter (path→resource→permission)
         → JWT auth (jjwt, stateless)
         → Controller (tenant scoping enforced in services)
                    │
  Frontend: ProtectedRoute (auth) → PermissionGate (resource+action) → page
```

> 🧒 **Kid translation:** It's like a school with a **security guard**.
> 1. You show your ID card (JWT) — *proves who you are*.
> 2. The guard checks his big list — *which rooms may you enter?* (role → resource → action)
> 3. You enter only allowed rooms (data is also filtered to *your* school only — tenant scoping).

- **RBAC**: 14 roles, role↔permission rows in `nx_role_permissions`, tenant-scoped.
- **Authorization model**: `PermissionService.PATH_TO_RESOURCE` (39 mappings), first-prefix-match, `*:*` for ADMIN, **allow-by-default** (documented trade-off — see `06-BUSINESS-FLOW-RBAC.md`).
- **Multi-tenancy**: every entity carries tenant scope.
- **Import security**: signed import tokens with expiry; idempotency keys.
- **External credentials**: encrypted `CredentialVault`; Okta SSO; MFA-ready.

---

## 7. Data & Messaging

| Store | Use | Plain-English |
|---|---|---|
| PostgreSQL | System of record; Flyway `V1…V52`; JPA | The permanent fridge |
| Redis | Cache (RBAC 60s, hot lookups) | The speed rack |
| Kafka | Domain events, integration ingestion, async automation | The walkie-talkie network |
| In-memory/WS | STOMP push of live updates | Live "order just arrived" screen refreshes |

### 7.1 Concurrency & idempotency
- Optimistic locking on mutable aggregates (orders, training jobs).
- Import token + idempotency keys deduplicate bulk loads.
- Retry with backoff for Kafka consumers and connector calls (circuit breakers).

> 🧒 **Kid translation of idempotency:** If the delivery person rings the bell twice, the kitchen should not cook the pizza twice. Idempotency = "same message twice = only one pizza." 🍕

---

## 8. Frontend Architecture

- **Entry** `src/main.tsx` → `App.tsx` defines HashRouter + lazy `Route` elements wrapped in `ProtectedRoute` + `AppLayout`.
- **Guards**: `ProtectedRoute` (authenticated), `PermissionGate` (resource+action; **caveat G2**: 6 pages pass a non-functional `permission` prop), legacy unused `RoleProtectedRoute`.
- **API layer**: typed clients per domain in `src/api/`; `src/types/index.ts` mirrors backend DTOs.
- **State**: React Query server-state caching; STOMP subscription for realtime.
- **Charts/UX**: recharts; Radix + Tailwind; LaunchPad groups navigation by role.

---

## 9. Deployment Topology 🚀

- **docker-compose** single-file topology: all services on `nexus-network`.
- Health: Actuator `/actuator/health`, Prometheus scrape, Grafana dashboards.
- **Frontend build**: `tsc && vite build` (type-check enforced).
- **Backend build**: Java 17 required; tests currently blocked (see §6 of CURRENT-STATE).

---

*Next: [`03-ER-DIAGRAM.md`](./03-ER-DIAGRAM.md) — the entity/relationship map, and [`features/`](./features/) for per-module deep dives.*
