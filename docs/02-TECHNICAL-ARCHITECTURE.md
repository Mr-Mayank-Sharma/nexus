# Nexus OMS — Technical Architecture

> Companion: [`01-CURRENT-STATE.md`](./01-CURRENT-STATE.md) · [`03-ER-DIAGRAM.md`](./03-ER-DIAGRAM.md)

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

### 2.1 Backend (`nexus-oms-backend`)

| Concern | Technology |
|---|---|
| Runtime | Java **17**, Spring Boot 3 (spring-boot-starter-parent) |
| Web | spring-boot-starter-web (REST), spring-boot-starter-websocket (STOMP) |
| Data | spring-boot-starter-data-jpa, PostgreSQL (`postgresql`), Flyway (`flyway-core` + `flyway-database-postgresql`) |
| Security | spring-boot-starter-security, **jjwt** (api/impl/jackson) JWT |
| Messaging | **spring-kafka** (event bus / async integrations) |
| Caching | spring-boot-starter-data-redis + spring-boot-starter-cache (60s RBAC permission cache) |
| Cross-cutting | spring-boot-starter-aop (aspects), spring-boot-starter-validation, Resilience4j (`resilience4j-spring-boot3`) circuit breakers |
| AI/Docs | springdoc-openapi (Swagger UI), jackson-databind + jsr310 |
| Files/Reporting | Apache POI (`poi-ooxml` Excel), PDFBox (PDF), iText/SAAJ (`saaj-impl` SOAP) |
| Observability | actuator, micrometer-tracing-bridge-brave, micrometer-registry-prometheus, logstash-logback-encoder |
| Boilerplate | Lombok |

### 2.2 Frontend (`nexus-oms-frontend`)

| Concern | Technology |
|---|---|
| Framework | React 18 + TypeScript, Vite build (`tsc && vite build`), port 3000 |
| Routing | react-router-dom v6, **hash-based** router, lazy-loaded routes |
| Data | axios, @tanstack/react-query v5 |
| Realtime | @stomp/stompjs + sockjs-client (STOMP over SockJS) |
| UI | Tailwind + tailwind-merge + tailwindcss-animate, Radix primitives (dialog, dropdown, select, tabs), class-variance-authority + clsx, lucide-react icons |
| Charts | recharts |
| Sanitization | dompurify |
| Tests | vitest (unit + coverage scripts) |

### 2.3 Infrastructure (docker-compose)

| Service | Image/Purpose |
|---|---|
| `postgres` | Primary OLTP database (volumes: postgres-data) |
| `redis` | Cache + rate-limit backing store (redis-data) |
| `kafka` | Async event bus / integration ingestion (kafka-data) |
| `backend` | Spring Boot app (compiled from `nexus-oms-backend`) |
| `frontend` | Served React SPA |
| `ai-ops` | Operational AI service (training, rules, automation) |
| `ai-intel` | Intelligence/briefing AI service |
| `prometheus` / `grafana` | Metrics collection + dashboards (prometheus-data, grafana-data) |
| Network | `nexus-network` bridge for all services |

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

### 3.1 Cross-cutting services

| Service | Responsibility |
|---|---|
| `PermissionService` | 39 `PATH_TO_RESOURCE` mappings; role-permission lookup from `nx_role_permissions` (tenant+role), 60s cache, **allow-by-default** semantics |
| `PermissionAuthorizationFilter` | Pre-authorization filter resolving path→resource→permission before controllers |
| `AuthService` | Login, JWT issuance, `ALLOWED_ROLES` (14 roles) |
| `ImportTokenService` | Signed, expiring tokens for bulk import endpoints (idempotency + tamper-evidence) |
| `GenericImportService` / `ImportExportEngine` | CSV/Excel/JSON import + export pipeline |
| `EventBus` | In-process + Kafka-backed domain event publishing |
| `CredentialVault` | Encrypted storage of external-connector credentials |

---

## 4. Integration Hub (iPaaS-lite)

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

**Design principles**
- One `Connector` interface; adapters translate protocol differences (REST/SOAP/GraphQL/EDI).
- Credentials never leave `CredentialVault`; secrets are encrypted at rest.
- `EventBus` decouples ingestion (webhook/poll/batch) from processing (Kafka).
- Every connector exposes `ConnectorHealth` for the Integration Hub dashboard.

---

## 5. AI Platform Architecture

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

## 6. Security Architecture

```
 Request → PermissionAuthorizationFilter (path→resource→permission)
         → JWT auth (jjwt, stateless)
         → Controller (tenant scoping enforced in services)
                    │
  Frontend: ProtectedRoute (auth) → PermissionGate (resource+action) → page
```

- **RBAC**: 14 roles (`ALLOWED_ROLES`), role↔permission rows in `nx_role_permissions`, tenant-scoped.
- **Authorization model**: `PermissionService.PATH_TO_RESOURCE` (39 mappings) maps URL paths to `resource:action` pairs; first-prefix-match wins; `*:*` wildcard for ADMIN; **allow-by-default** when no mapping matches (documented trade-off, see `06-BUSINESS-FLOW-RBAC.md`).
- **Multi-tenancy**: every entity carries tenant scope; queries filter by tenant.
- **Import security**: signed import tokens with expiry; idempotency keys.
- **External credentials**: encrypted `CredentialVault`; Okta SSO connector; MFA-ready.

---

## 7. Data & Messaging

| Store | Use |
|---|---|
| PostgreSQL | System of record; Flyway migrations `V1…V52`; JPA-managed |
| Redis | Cache (RBAC permissions 60s, hot lookups), session-adjacent state |
| Kafka | Domain events, integration ingestion, async automation (resilient via Resilience4j) |
| In-memory/WS | STOMP push of live order/shipment/notification updates |

### 7.1 Concurrency & idempotency
- Version/optimistic-locking patterns on mutable aggregates (orders, training jobs).
- Import token + idempotency keys deduplicate bulk loads.
- Retry with backoff for Kafka consumers and connector calls (circuit breakers).

---

## 8. Frontend Architecture

- **Entry** `src/main.tsx` → `App.tsx` defines HashRouter + lazy `Route` elements wrapped in `ProtectedRoute` + `AppLayout`.
- **Guards**: `ProtectedRoute` (authenticated), `PermissionGate` (resource+action; **caveat**: 6 pages pass a non-functional `permission` prop — see G2 in `01-CURRENT-STATE.md`), legacy unused `RoleProtectedRoute`.
- **API layer**: typed clients per domain in `src/api/`; `src/types/index.ts` mirrors backend DTOs.
- **State**: React Query server-state caching; STOMP subscription for realtime updates.
- **Charts/UX**: recharts for analytics; Radix + Tailwind for components; LaunchPad groups navigation by role.

---

## 9. Deployment Topology

- **docker-compose** single-file topology (dev/self-hosted): all services on `nexus-network`.
- Health: Spring Actuator `/actuator/health`, Prometheus scrape, Grafana dashboards.
- **Frontend build**: `tsc && vite build` (type-check enforced at build).
- **Backend build**: Java 17 required (`JAVA_HOME=/usr/lib/jvm/java-17-openjdk-amd64`); tests currently blocked (see §6 of CURRENT-STATE).

---

*Next: [`03-ER-DIAGRAM.md`](./03-ER-DIAGRAM.md) — the entity/relationship map, and [`features/`](./features/) for per-module deep dives.*
