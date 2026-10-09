# 11 — System Design: Structural Views, Sequences, State Machines and Topology

> **What this document is.** The diagram companion to the Nexus documentation set.
> Every diagram here is derived from the source tree — CI job IDs, servlet filter
> ordering, Kafka topic names, status literals and container ports were read out of
> the code, not recalled from memory. The exact commands to re-derive each one are
> in [Appendix Re Deriving Diagrams](#appendix-re-deriving-diagrams).
>
> **What this document is not.** It is not another prose guide. For stack and
> component descriptions see [02 Technical Architecture](./02-TECHNICAL-ARCHITECTURE.md)
> and [ARCHITECTURE](./ARCHITECTURE.md); for entity structure see
> [03 ER Diagram](./03-ER-DIAGRAM.md); for end-to-end data movement see
> [05 Data Flow](./05-DATA-FLOW.md); for current verified counts see
> [01 Current State](./01-CURRENT-STATE.md). This document deliberately restates
> **no** hard numbers, so it can never drift out of sync with the CI claim checks.

## Contents

- [Scope](#scope)
- [Structural Views](#structural-views)
  - [C4 Context](#c4-context)
  - [C4 Container](#c4-container)
  - [C4 Component Backend](#c4-component-backend)
  - [Deployment Topology](#deployment-topology)
- [Delivery Pipeline](#delivery-pipeline)
  - [CI Pipeline](#ci-pipeline)
  - [CD Pipeline](#cd-pipeline)
- [Request Paths](#request-paths)
  - [Authentication and Authorization](#authentication-and-authorization)
  - [Webhook Ingestion](#webhook-ingestion)
  - [Generic Import Engine](#generic-import-engine)
- [State Machines](#state-machines)
  - [Order Lifecycle](#order-lifecycle)
  - [Fulfillment Lifecycle](#fulfillment-lifecycle)
  - [Shipment Lifecycle](#shipment-lifecycle)
  - [Return Lifecycle](#return-lifecycle)
  - [Procurement Lifecycle](#procurement-lifecycle)
- [Event Topology](#event-topology)
- [Frontend Architecture](#frontend-architecture)
- [Security Topology](#security-topology)
- [Appendix Re Deriving Diagrams](#appendix-re-deriving-diagrams)

## Scope

| Section | Question it answers | Diagram type |
|---|---|---|
| Structural Views | What exists and how does it fit together? | C4 context, container, component, deployment |
| Delivery Pipeline | How does a change reach production? | CI/CD graph |
| Request Paths | What happens to one request? | Sequence |
| State Machines | What values may an object hold and in what order? | stateDiagram-v2 |
| Event Topology | Which events exist and where do they go? | Flow graph |
| Frontend Architecture | How is the SPA organised? | Flow graph |
| Security Topology | Where are the trust boundaries? | Flow graph |

> 🧒 **In plain words:** if the architecture docs explain *what the boxes are*,
> this document draws *the boxes, how they talk, and what state they can be in*.

## Structural Views

### C4 Context

Nexus sits between the people who buy things, the channels that sell them, and the
physical supply chain that fulfils them.

```mermaid
flowchart LR
    buyer["Buyer / Customer<br/>storefront, POS, B2B portal"]
    ops["Operations staff<br/>warehouse, planning, finance"]
    chan["Sales channels<br/>Shopify, BigCommerce"]
    carrier["Carriers"]
    supplier["Suppliers"]
    edi["EDI VAN / trading partners"]
    email["Inbound email inbox"]

    subgraph nexus["Nexus OMS"]
        oms["Nexus Order Management<br/>and Supply Chain Platform"]
    end

    buyer -->|"places order, tracks parcel"| oms
    ops -->|"picks, packs, resolves exceptions"| oms
    chan -->|"order and fulfilment webhooks"| oms
    email -->|"parsed purchase orders"| oms
    edi -->|"850 / 856 / 810 transactions"| oms
    oms -->|"status, cancellation, confirmation"| chan
    oms -->|"rate shop, label, track"| carrier
    oms -->|"purchase orders, ASN"| supplier
    oms -->|"despatch advice, invoices"| edi
```

> 🧒 **In plain words:** Nexus takes orders from many doors — websites, email,
> EDI, staff — and pushes work out to warehouses, carriers and suppliers, then
> tells the original door what happened.

### C4 Container

Nine containers are defined in `docker-compose.yml`, plus the browser. Port
mappings below are the literal host-to-container bindings from that file.

```mermaid
flowchart TB
    browser["Browser SPA<br/>React + Vite + TypeScript"]

    subgraph compose["docker compose network: nexus-network"]
        frontend["frontend<br/>host 80 -> container 80<br/>Nginx serving built SPA"]
        backend["backend<br/>host 8080 -> container 8080<br/>Spring Boot 3, Java 17"]
        aiops["ai-ops<br/>host 5000 -> container 5000<br/>FastAPI operational AI"]
        aiintel["ai-intel<br/>host 5001 -> container 5001<br/>FastAPI intelligence AI"]
        postgres["postgres<br/>host 5433 -> container 5432<br/>PostgreSQL 16"]
        redis["redis<br/>host 6379<br/>cache, rate limit, idempotency"]
        kafka["kafka<br/>host 9092<br/>14 topics plus dead-letter"]
        prometheus["prometheus<br/>host 9090<br/>scrape backend /actuator"]
        grafana["grafana<br/>host 3001 -> container 3000<br/>dashboards"]
    end

    browser -->|"HTTP /api/v1"| backend
    browser -->|"/"| frontend
    frontend -->|"proxies /api/v1"| backend
    backend -->|"JDBC, Flyway migrations"| postgres
    backend -->|"cache and throttle"| redis
    backend -->|"publish and consume"| kafka
    backend -->|"predictions and forecasts"| aiops
    backend -->|"briefings and experiments"| aiintel
    backend -->|"/actuator metrics"| prometheus
    prometheus -->|"scrape dashboards"| grafana
```

Storage volumes `postgres-data`, `redis-data`, `kafka-data`, `prometheus-data` and
`grafana-data` survive `docker compose down` and are only removed by `down -v`.

> 🧒 **In plain words:** one machine runs everything. The browser talks to Nginx
> and the API directly; the API is the only container allowed to touch the
> database.

### C4 Component Backend

The backend package layout as found under `com.nexus.oms`:

```mermaid
flowchart TB
    subgraph inbound["Inbound edge"]
        controller["controller/<br/>REST controllers"]
        filter["filter/<br/>correlation, CSP nonce,<br/>security headers, idempotency,<br/>rate limit, webhook auth"]
        security["security/<br/>JWT, RBAC, tenant context,<br/>refresh, blacklist, SSO"]
        health["health/"]
    end

    subgraph core["Domain core"]
        service["service/<br/>business logic and workflows"]
        scheduler["scheduler/<br/>brokering, fulfilment limits,<br/>pre-order release"]
        integration["integration/<br/>webhooks, Shopify, BigCommerce,<br/>EDI, email parsing"]
        importexp["ImportExportEngine<br/>GenericImportService"]
        kafka["kafka/<br/>KafkaProducerService,<br/>KafkaConsumerService, Topics"]
        ai["ai/"]
    end

    subgraph persistence["Persistence"]
        repository["repository/<br/>Spring Data JPA"]
        entity["entity/<br/>JPA entities"]
        db[("PostgreSQL<br/>Flyway migrations")]
    end

    subgraph shared["Shared and config"]
        dto["dto/"]
        exception["exception/"]
        config["config/<br/>CORS, Kafka, Redis,<br/>tenant datasource, WebSocket, SSO"]
    end

    controller --> service
    filter --> security
    security --> service
    scheduler --> service
    integration --> service
    importexp --> service
    ai --> service
    service --> repository
    service --> kafka
    service --> ai
    repository --> entity
    entity --> db
    service -.->|"uses"| dto
    service -.->|"throws"| exception
    config -.->|"wires"| service
```

> 🧒 **In plain words:** controllers take the request, services do the thinking,
> repositories talk to the database. Filters and security sit in front of all of
> it; schedulers, integrations and Kafka producers sit behind it and call into
> the same services.

### Deployment Topology

Single-host production layout. TLS terminates at Nginx; only the edge ports are
reachable from outside the host.

```mermaid
flowchart TB
    internet((Internet))

    subgraph host["Production host"]
        fw["Firewall / cloud security group<br/>allow 80, 443, SSH from allowlist only"]
        nginx["Nginx<br/>TLS termination, reverse proxy,<br/>static SPA assets"]

        subgraph app["Application tier"]
            backend["Backend :8080"]
            aiops["ai-ops :5000"]
            aiintel["ai-intel :5001"]
        end

        subgraph data["Data tier, not exposed"]
            postgres["PostgreSQL"]
            redis["Redis"]
            kafka["Kafka"]
        end

        subgraph obs["Observability, internal only"]
            prometheus["Prometheus :9090"]
            grafana["Grafana :3001"]
        end
    end

    internet -->|"443 HTTPS"| fw
    fw --> nginx
    nginx -->|"/"| frontend["Frontend static bundle"]
    nginx -->|"/api/v1"| backend
    nginx -->|"/api/ai/*"| aiops
    backend --> postgres
    backend --> redis
    backend --> kafka
    backend --> aiops
    backend --> aiintel
    backend --> prometheus
    prometheus --> grafana
```

Stand-alone firewall rules are listed in
[03 Deployment Guide section 4](../DEPLOYMENT_GUIDE.md) and sizing tiers in
section 7 of the same document.

## Delivery Pipeline

### CI Pipeline

Workflow `.github/workflows/ci.yml`, triggered on pushes and pull requests to
`main` and `develop`. Job IDs are the literal YAML keys.

```mermaid
flowchart LR
    trigger["push or pull request<br/>to main or develop"]

    trigger --> docs["docs<br/>Docs Consistency<br/>verify_docs.py"]
    trigger --> backend["backend<br/>Backend Java 17<br/>JUnit, JaCoCo, OWASP"]
    trigger --> frontend["frontend<br/>Frontend Node 20<br/>vitest, npm audit"]
    trigger --> python_ai["python-ai<br/>AI Service 1"]
    trigger --> python_ai2["python-ai2<br/>AI Service 2"]
    trigger --> codeql["codeql<br/>CodeQL SAST"]
    trigger --> sonarqube["sonarqube<br/>SonarQube Analysis"]

    backend --> docker["docker<br/>Docker Build and Container Scan"]
    frontend --> docker
```

Notes worth knowing:

- `docker` declares `needs: [backend, frontend]`, so a red backend or frontend
  job blocks the image scan.
- The `docs` job runs `python3 scripts/verify_docs.py --no-surefire`, which
  recomputes every published count from the source tree. See
  [Appendix Re Deriving Diagrams](#appendix-re-deriving-diagrams).
- The `env.` context is not available when `jobs.<id>.name` is evaluated, so the
  workflow uses literal names; referencing it there is a fatal parse error.

> 🧒 **In plain words:** every push runs documentation truth-checking, the two
> real test suites, both AI services, and two security scanners in parallel. Only
> when the backend and frontend both pass does the container get built and
> scanned.

### CD Pipeline

Workflow `.github/workflows/cd.yml`, triggered by version tags `v*`, by pushes
to `main`, and by manual `workflow_dispatch`.

```mermaid
flowchart LR
    trig["trigger<br/>tag v*, main, or workflow_dispatch"]

    trig --> prepare["prepare<br/>Prepare deployment"]
    prepare --> build["build-images<br/>Build and Push Images<br/>matrix: backend, frontend<br/>push to GHCR"]
    build --> trivy["Trivy image scan<br/>CRITICAL and HIGH gate"]
    trivy --> staging["deploy-staging<br/>Deploy to Staging<br/>SSH to staging host"]
    staging --> prod["deploy-production<br/>Deploy to Production<br/>GitHub release and notify"]

    trig -.->|"manual gate"| staging
```

The staging and production steps are separate jobs rather than environment
gates, so a failed staging deploy stops the chain before production is reached.

## Request Paths

### Authentication and Authorization

Two layers of filters run on every request. Servlet-registered filters carry
`@Order` annotations and run first; the Spring Security chain is assembled
explicitly in `SecurityConfig`.

```mermaid
flowchart TB
    subgraph servlet["Servlet filter layer, @Order"]
        corr["CorrelationIdFilter @Order 0"]
        csp["CspNonceFilter @Order 0"]
        hdr["SecurityHeadersFilter @Order 0"]
        whauth["WebhookAuthenticationFilter @Order 2"]
    end

    subgraph chain["Spring Security filter chain"]
        imp["ImportTokenAuthenticationFilter<br/>before JwtAuthenticationFilter"]
        jwt["JwtAuthenticationFilter<br/>before UsernamePasswordAuthenticationFilter"]
        rate["RateLimitingFilter<br/>after Jwt"]
        idem["IdempotencyFilter<br/>after Jwt"]
        perm["PermissionAuthorizationFilter<br/>after Jwt"]
        upw["UsernamePasswordAuthenticationFilter"]
    end

    request["Inbound request"] --> corr --> csp --> hdr --> whauth
    whauth --> imp --> jwt --> rate --> idem --> perm --> upw
    upw --> controller["Controller method"]
```

Permit-all and restricted matchers, transcribed from `SecurityConfig`:

| Matcher | Access |
|---|---|
| `/auth/**` | permit all |
| `/swagger-ui/**`, `/v3/api-docs/**` | permit all |
| `/actuator/health` | permit all |
| `/ws/**` | permit all |
| `/webhooks/**` | permit all |
| `/assets/**`, `*.html`, `*.js`, `*.css`, `/` | permit all |
| `/actuator/**` | role `ADMIN` only |
| `POST /import/**` | authenticated |
| any other request | authenticated |

```mermaid
sequenceDiagram
    participant C as Client
    participant J as JwtAuthenticationFilter
    participant R as RateLimitingFilter
    participant I as IdempotencyFilter
    participant P as PermissionAuthorizationFilter
    participant S as Service

    C->>J: GET /api/v1/orders with Bearer token
    J->>J: validate signature, expiry, blacklist
    alt token invalid or blacklisted
        J-->>C: 401 Unauthorized
    else token valid
        J->>R: authenticated principal
        alt Redis throttle exceeded
            R-->>C: 429 Too Many Requests
        else within budget
            R->>I: continue
            alt Idempotency-Key already seen
                I-->>C: replay stored response
            else new key
                I->>P: continue
                alt permission path not mapped
                    P-->>C: 403 Forbidden
                else allowed for tenant
                    P->>S: invoke controller
                    S-->>C: 200 OK
                end
            end
        end
    end
```

> 🧒 **In plain words:** identify the caller, check they are not being rate
> flooded, make sure the same request is not applied twice, then check their
> role is allowed to touch this specific endpoint. Only then does business code
> run.

### Webhook Ingestion

External channels push events into `/webhooks/**`, which is reachable without a
bearer token — so signature verification happens in the filter chain instead.

```mermaid
sequenceDiagram
    participant CH as Shopify / BigCommerce
    participant WH as WebhookAuthenticationFilter
    participant SEC as WebhookSecurityService
    participant DED as WebhookDedupLedgerService
    participant C as WebhookIntegrationController
    participant SVC as Order or Fulfilment service
    participant K as Kafka

    CH->>WH: POST /webhooks/... + HMAC signature header
    WH->>SEC: verify shared secret / signature
    alt signature mismatch
        SEC-->>WH: reject
        WH-->>CH: 401 Unauthorized
    else signature valid
        SEC-->>WH: verified
        WH->>DED: check event id
        alt event already processed
            DED-->>WH: duplicate
            WH-->>CH: 200 OK, ignored
        else first delivery
            DED-->>WH: new
            WH->>C: pass through
            C->>SVC: upsert order / shipment state
            SVC->>K: publish order.updated
            SVC-->>CH: 200 OK
        end
    end
```

Replay protection is what the dedup ledger buys: channels retry aggressively,
and a ledger lookup is cheaper than a second write.

### Generic Import Engine

Uploads reach `POST /import/{entityType}`. Two authentication paths are
accepted — a normal JWT, or a short-lived `X-Import-Token` issued by
`ImportTokenService` for machine-to-machine uploads.

```mermaid
sequenceDiagram
    participant U as Uploader
    participant IT as ImportTokenAuthenticationFilter
    participant J as JwtAuthenticationFilter
    participant C as Import controller
    participant G as GenericImportService
    participant E as ImportExportEngine
    participant H as ImportHistory / ImportRecordLog
    participant DB as PostgreSQL

    U->>C: POST /import/orders -F file=orders.csv
    alt X-Import-Token present
        U->>IT: token header
        IT->>C: principal from import token
    else Bearer JWT
        U->>J: Authorization header
        J->>C: principal from JWT
    end
    C->>G: parse by entityType and format
    G->>E: csv | json | xml | edi | xlsx
    E-->>G: rows plus per-row errors
    G->>DB: persist accepted rows
    G->>H: write history and record log
    G-->>C: totals, successCount, errorCount, warnings
    C-->>U: ImportResult summary
```

Row-level errors are reported rather than aborting the whole file, so a single
bad record does not block a bulk load.

## State Machines

> ⚠️ **Read this before trusting an edge.** Status fields in Nexus are plain
> `String` columns, not Java enums or check constraints. `NxOrder.status`
> defaults to `PENDING` via a null guard, and any service may write any literal
> it chooses. The **values** below are verified from `setStatus(...)` call
> sites; the **edges** show the observed stage owners and the conventional
> happy path. Nothing in the database will stop a transition the diagrams do
> not show.

### Order Lifecycle

Statuses written by services that operate on `NxOrder`. Plausible-looking
neighbours — `PICKED`, `PACKED`, `LABELED`, `REJECTED`, `ESCALATED`,
`PENDING_APPROVAL`, `EXPIRED` — are **not** order statuses; they belong to the
pick item, package, shipment, exception, procurement and brokering-queue
entities shown in the diagrams below.

```mermaid
stateDiagram-v2
    direction TB
    [*] --> PENDING : null guard in NxOrder
    PENDING --> CONFIRMED : confirmOrder
    PENDING --> ALLOCATED : allocateOrder, wave release
    CONFIRMED --> ALLOCATED : allocateOrder
    PENDING --> PARTIALLY_ALLOCATED : resolveOrderStatus
    CONFIRMED --> PARTIALLY_ALLOCATED : resolveOrderStatus
    PENDING --> FAILED : resolveOrderStatus
    CONFIRMED --> FAILED : resolveOrderStatus
    ALLOCATED --> SHIPPED : shipOrder, shipPackage
    SHIPPED --> FULFILLED : acceptFulfillment
    PENDING --> SPLIT : splitOrder
    CONFIRMED --> SPLIT : splitOrder
    CONFIRMED --> MERGED : mergeOrders
    PENDING --> CANCELLED : cancelOrder
    CONFIRMED --> CANCELLED : cancelOrder
    ALLOCATED --> CANCELLED : cancelOrder
    PARTIALLY_ALLOCATED --> CANCELLED : cancelOrder
    FAILED --> CANCELLED : cancelOrder
    FULFILLED --> [*]
    MERGED --> [*]
    SPLIT --> [*]
    CANCELLED --> [*]
```

Verified details behind the edges:

- **Allocation outcomes.** `OrderRoutingService.resolveOrderStatus` maps the
  raw numbers: zero demand or full coverage → `ALLOCATED`; any shortfall →
  `PARTIALLY_ALLOCATED`, except a total miss against a backorder row →
  `FAILED`. `confirmOrder` skips its own `CONFIRMED` write when routing
  reported `PARTIALLY_ALLOCATED`/`FAILED` (T-17), so a short order survives
  confirmation in its honest state.
- **Who can split or merge.** `splitOrder` accepts only `PENDING` or
  `CONFIRMED` sources. `mergeOrders` rejects sources already `SHIPPED`,
  `DELIVERED` or `CANCELLED`. `MERGED` and `SPLIT` are terminal for the
  source — the work moves to the merged or split child record.
- **Cancellation.** `cancelOrder` has no status guard: it releases any
  reserved inventory, writes `CANCELLED`, pushes the status to BigCommerce
  and publishes `order.cancelled`.
- **The API escape hatch.** `PUT /orders/{id}/status`
  (`OrderService.updateStatus`) writes *any* string it is handed with no
  validation. That is the only way `DELIVERED`, `ON_HOLD`, `PICKING`,
  `PACKING` and `PACKED` ever land on an order, even though dashboard
  queries count them and no service `setStatus` call produces them. It is
  also how `APPROVED`/`RELEASED` — named by `WaveService.PICKABLE_STATES`
  as wave-eligible order statuses — can appear despite having no writer.

### Fulfillment Lifecycle

Three cooperating objects drive a confirmed order through the warehouse. Each
has its own status column, its own owning service and its own vocabulary.
They are deliberately **three diagrams, not one**: a single merged graph would
fuse their identically-named states (`IN_PROGRESS`, `COMPLETED`,
`CANCELLED`) into shared nodes and invent edges between objects that never
touch each other's status.

**Wave** — `nx_waves`, written only by `WaveService`:

```mermaid
stateDiagram-v2
    direction LR
    [*] --> DRAFT : createWave
    DRAFT --> PLANNED : planWave
    PLANNED --> IN_PROGRESS : releaseWave
    IN_PROGRESS --> RELEASING_PAUSED : pauseWave
    RELEASING_PAUSED --> IN_PROGRESS : resumeWave
    IN_PROGRESS --> COMPLETED : completeWave
    DRAFT --> CANCELLED : cancelWave
    PLANNED --> CANCELLED : cancelWave
    RELEASING_PAUSED --> CANCELLED : cancelWave
    IN_PROGRESS --> CANCELLED : cancelWave
    COMPLETED --> [*]
```

Every hop is guarded: `planWave` requires `DRAFT`, `releaseWave` requires
`PLANNED`, `pauseWave` and `completeWave` require `IN_PROGRESS`, `resumeWave`
requires `RELEASING_PAUSED`. `cancelWave` requires membership in
`ACTIVE_STATES` = `DRAFT`, `PLANNED`, `RELEASING`, `RELEASING_PAUSED`,
`RELEASED`, `IN_PROGRESS` — note that set names `RELEASING`/`RELEASED`,
values no writer ever produces.

**Pick list** — `nx_picklists`, written by `PickingService` and `WaveService`:

```mermaid
stateDiagram-v2
    direction LR
    [*] --> OPEN : createPicklistFromOrder, wave seeding
    OPEN --> IN_PROGRESS : startPicking, assignPicker
    IN_PROGRESS --> COMPLETED : completePicklist, pickItem (last item)
    OPEN --> CANCELLED : cancelPicklist
    IN_PROGRESS --> CANCELLED : cancelPicklist
    COMPLETED --> [*]
```

Pick **items** carry a separate three-value vocabulary written by the same
service: `PENDING` → `PICKED` (`pickItem`, `pickAllItems`,
`completePicklist`) or `PENDING` → `CANCELLED` (`cancelPicklist`, for items
not yet picked). `pickAllItems` leaves the list `IN_PROGRESS`; only
`completePicklist` or the auto-finish inside `pickItem` when the last item is
picked closes it. Neither the auto-finish nor `cancelPicklist` checks the
current list status.

**Package** — `nx_packages`, written only by `PackingService`:

```mermaid
stateDiagram-v2
    direction LR
    [*] --> PENDING_PACK : createPackage
    PENDING_PACK --> PACKING : startPacking
    PACKING --> PACKED : completePacking
    PACKED --> LABELED : generateLabel
    LABELED --> SHIPPED : shipPackage
    PACKING --> VOIDED : voidPackage
    SHIPPED --> [*]
    VOIDED --> [*]
```

`voidPackage` performs no status check — it will run from any package state;
only the `PACKING` source is drawn. `shipPackage` additionally flips the
parent order's status to `SHIPPED` when the package is linked to an order.

Wave release writes `ALLOCATED` **on the orders** it seeds into the picklist
(`WaveService.seedItemsIntoPicklist`) — an order status, not a wave stage.

### Shipment Lifecycle

```mermaid
stateDiagram-v2
    [*] --> PENDING
    PENDING --> LABELED
    LABELED --> SHIPPED
    SHIPPED --> DELIVERED
    PENDING --> VOIDED
    LABELED --> VOIDED
    SHIPPED --> VOIDED
    DELIVERED --> [*]
    VOIDED --> [*]
```

Carrier status webhooks land on `DELIVERED` and on the `VOIDED` path when a
label is cancelled before tender.

### Return Lifecycle

```mermaid
stateDiagram-v2
    [*] --> PENDING : default in NxReturn
    PENDING --> REQUESTED : ReturnService
    REQUESTED --> APPROVED : ReturnService
    REQUESTED --> REJECTED : ReturnService
    APPROVED --> RECEIVED : ReturnService
    RECEIVED --> INSPECTED : ReturnService
    RECEIVED --> REJECTED : ReturnService
    INSPECTED --> REFUNDED : ReturnService
    INSPECTED --> DISPOSED : ReturnService
    REFUNDED --> [*]
    DISPOSED --> [*]
    REJECTED --> [*]
```

`REFUNDED` hands off to `ReturnFinanceService`, which settles against the
original payment method.

### Procurement Lifecycle

Procurement spans **three separate entities**, each defaulting to `DRAFT` via
a null guard and each written only by `ProcurementService`. Collapsing them
into one chain is the classic mistake this section exists to prevent — and
note `APPROVED` is shared by two *different* entities, so it must not appear
as a single node in a merged graph.

**PurchaseRequest**:

```mermaid
stateDiagram-v2
    direction LR
    [*] --> DRAFT : null guard in PurchaseRequest
    DRAFT --> PENDING_APPROVAL : submitForApproval
    PENDING_APPROVAL --> APPROVED : approveRequest
    APPROVED --> [*]
```

**Rfq**:

```mermaid
stateDiagram-v2
    direction LR
    [*] --> DRAFT : null guard in Rfq
    DRAFT --> SUBMITTED : submitRfq
    SUBMITTED --> [*]
```

**PurchaseOrder**:

```mermaid
stateDiagram-v2
    direction LR
    [*] --> DRAFT : null guard in PurchaseOrder
    DRAFT --> APPROVED : approvePurchaseOrder
    APPROVED --> [*]
```

| Entity | Default status | Transitions found in source |
|---|---|---|
| `PurchaseRequest` | `DRAFT` | `PENDING_APPROVAL`, `APPROVED` |
| `Rfq` | `DRAFT` | `SUBMITTED` |
| `PurchaseOrder` | `DRAFT` | `APPROVED` |

That is the complete set: a repository-wide grep finds no other `setStatus`
call targeting these three entities — no reject, cancel, award or receive
transitions exist yet.

## Event Topology

All topic names are the literal constants in `kafka/Topics.java`. Every topic
has a paired dead-letter topic produced by `Topics.dlq(topic)`.

```mermaid
flowchart LR
    subgraph producers["Producers"]
        orderSvc["Order services"]
        invSvc["Inventory service"]
        paySvc["Billing and payments"]
        retSvc["Return service"]
        shipSvc["Shipment service"]
    end

    subgraph topics["Topics"]
        t1["order.created"]
        t2["order.confirmed"]
        t3["order.allocated"]
        t4["order.shipped"]
        t5["order.delivered"]
        t6["order.updated"]
        t7["order.cancelled"]
        t8["shipment.created"]
        t9["shipment.updated"]
        t10["inventory.adjusted"]
        t11["payment.received"]
        t12["payment.refunded"]
        t13["return.created"]
        t14["return.approved"]
    end

    subgraph dlq["Dead letters"]
        d1["*.dlq via Topics.dlq"]
    end

    orderSvc --> t1
    orderSvc --> t2
    orderSvc --> t3
    orderSvc --> t4
    orderSvc --> t5
    orderSvc --> t6
    orderSvc --> t7
    shipSvc --> t8
    shipSvc --> t9
    invSvc --> t10
    paySvc --> t11
    paySvc --> t12
    retSvc --> t13
    retSvc --> t14

    t1 -.->|"handler failure"| d1
    t2 -.->|"handler failure"| d1
    t6 -.->|"handler failure"| d1
    t10 -.->|"handler failure"| d1

    t1 -.-> cons["KafkaConsumerService"]
    t6 -.-> cons
    t9 -.-> cons
```

Consumption is funnelling through `KafkaConsumerService`; a `NoOpKafkaProducerService`
exists so the platform can run with Kafka disabled for local development.

> 🧒 **In plain words:** services do not call each other directly for
> order lifecycle work — they announce facts on topics, and anything that cannot
> handle the announcement drops it into a dead-letter topic instead of losing it.

## Frontend Architecture

```mermaid
flowchart TB
    main["main.tsx<br/>Vite entry point"]
    app["App.tsx<br/>router and layout shell"]

    subgraph src["nexus-oms-frontend/src"]
        pages["pages/<br/>one component per route"]
        components["components/<br/>shared UI"]
        api["api/<br/>module per resource"]
        hooks["hooks/<br/>data and effect hooks"]
        context["context/<br/>auth and tenant context"]
        types["types/"]
        utils["utils/"]
        styles["styles/, index.css"]
        rf["rf/"]
        test["test/"]
    end

    browser["Browser"] --> main --> app
    app --> pages
    pages --> components
    pages --> hooks
    hooks --> api
    context -.->|"current tenant and user"| app
    api -->|"fetch /api/v1"| backend["Backend API"]
```

Routes are declared in `App.tsx` and each page pulls data through exactly one
`api/` module — there is no second HTTP client. React is pinned to 18.3.1 across
the repo; see [README](../README.md) for the version table.

## Security Topology

Trust boundaries, outermost first:

```mermaid
flowchart TB
    subgraph untrusted["Untrusted"]
        internet((Internet))
        browser["Browser"]
    end

    subgraph edge["Edge, authenticated by TLS"]
        nginx["Nginx :80 / :443<br/>TLS, static assets"]
        hsec["SecurityHeadersFilter<br/>CSP with nonce, frame and referrer policy"]
        corr["CorrelationIdFilter<br/>request id on every log line"]
    end

    subgraph api["API, authenticated by token"]
        jwt["JwtAuthenticationFilter"]
        rate["RateLimitingFilter backed by Redis"]
        idem["IdempotencyFilter"]
        perm["PermissionAuthorizationFilter<br/>74 mapped permission paths"]
        wh["WebhookAuthenticationFilter<br/>HMAC verification"]
        imp["ImportTokenAuthenticationFilter<br/>X-Import-Token"]
        tenant["TenantContext<br/>row-level tenant scoping"]
    end

    subgraph internal["Internal, never exposed"]
        postgres[("PostgreSQL")]
        redis[("Redis")]
        kafka[("Kafka")]
        grafana["Grafana :3001"]
        prometheus["Prometheus :9090"]
    end

    internet --> nginx
    browser --> nginx
    nginx --> hsec --> corr --> jwt
    jwt --> rate --> idem --> perm --> tenant
    wh -.->|"signed, no bearer token"| jwt
    imp -.->|"import token"| jwt
    tenant --> postgres
    rate --> redis
    tenant -.->|"audit and trace"| kafka
    perm -.->|"authoritative allow / deny"| redis
```

Control notes:

- CORS is configured in `CorsConfig`; WebSocket endpoints are handled by
  `WebSocketConfig`.
- `TokenBlacklistService` lets a logout invalidate a token before its expiry.
- `TenantAwareDataSource` and `TenantContext` keep one tenant's rows invisible to
  another; the scoping happens below the service layer.
- Admin-only surfaces are the `/actuator/**` family, gated to role `ADMIN`.

## Appendix Re Deriving Diagrams

Every claim in this document can be regenerated. Run these from the repository
root.

```bash
# Status literals used by order-lifecycle services
grep -rhoE 'setStatus\("[A-Z_]+"\)' nexus-oms-backend/src/main/java \
  --include='*.java' | sed 's/setStatus("//;s/")//' | sort -u

# Statuses per owning service
for f in OrderService OrderRoutingService BrokeringService WaveService \
         PickingService PackingService ReturnService ProcurementService; do
  printf '%-24s ' "$f"
  grep -hoE 'setStatus\("[A-Z_]+"\)' \
    nexus-oms-backend/src/main/java/com/nexus/oms/service/$f.java \
    | sed 's/setStatus("//;s/")//' | tr '\n' ' '
  echo
done

# Kafka topic constants
grep -E 'public static final String' \
  nexus-oms-backend/src/main/java/com/nexus/oms/kafka/Topics.java

# Servlet filter ordering
grep -n '@Order\|class ' nexus-oms-backend/src/main/java/com/nexus/oms/filter/*.java

# Security chain assembly
grep -n 'addFilter\|requestMatchers' \
  nexus-oms-backend/src/main/java/com/nexus/oms/security/SecurityConfig.java

# Container service and port map
awk '/^  [a-z][a-z0-9_-]*:$/{svc=$1} /- "[0-9]+:[0-9]+"$/{print svc, $0}' \
  docker-compose.yml

# CI and CD job IDs
awk '/^jobs:/{f=1} f && /^  [a-z][a-z0-9_-]*:$/{print $1}' .github/workflows/ci.yml
awk '/^jobs:/{f=1} f && /^  [a-z][a-z0-9_-]*:$/{print $1}' .github/workflows/cd.yml

# Documentation truth gate, same command the docs CI job runs
python3 scripts/verify_docs.py --no-surefire
```

If any of these return something different from the diagrams above, the diagram
is stale and this document should be updated in the same commit as the source
change.

---

*Companion to the Nexus documentation set — see [docs index](./README.md).*
