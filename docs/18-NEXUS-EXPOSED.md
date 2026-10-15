# 18 — NEXUS EXPOSED

> **What this is.** The ground truth about Nexus: how it is built, every defect the audit found
> (with its status), what was verified by actually running it end to end, and what the product is
> honestly worth today. It replaces optimism with evidence.
>
> **Verified against:** `main` at the commit that contains this file, 2026-10-14. Every number
> below was produced by running something (the backend suite, the frontend suite, the checker in
> `scripts/verify_docs.py`, or the live lifecycle script), not recalled.
>
> **Evidence labels.** **[code]** read in source · **[test]** covered by an automated test that
> runs in CI · **[live]** observed against the running stack (Postgres, Redis, the real API) ·
> **[unverified]** written but never exercised against the real external system it targets.
>
> **Read first:** [§7 the scorecard](#part-7--the-scorecard) and [§8 what is still open](#part-8--still-open-and-who-owns-it).
> Market context (competitors, pricing, analysts) is in [19-MARKET-BENCHMARKS.md](./19-MARKET-BENCHMARKS.md).

---

# PART 1 — ARCHITECTURE

## 1.1 What Nexus Is

A **multi-tenant SaaS suite** that ships as three editions — **OMS**, **WMS**, or **SUITE** — with
three paid add-ons (**3PL**, **EDI**, **AI**). Licensing is enforced *in the request path*, not just
in the UI.

| Layer | Technology | Scale |
|---|---|---|
| Backend | Spring Boot 3 / Java 17 | 84 controllers · 133 services · 174 entities · 70 migration files (V1–V78) · 76 permission-mapped API prefixes |
| Frontend | React 18 + Vite + TypeScript | 103 routes · 66 API modules · RF handheld app |
| Database | PostgreSQL 16 + pgvector | Row-Level Security per tenant |
| Cache / Locks | Redis 7 | cache, rate-limit buckets, ShedLock |
| Messaging | Kafka 3.7.2 (KRaft) | transactional outbox |
| AI | 2× Flask services + in-process ONNX | ai-ops :5000 · ai-intel :5001 |
| Observability | Prometheus · Grafana · Loki · Promtail · Alertmanager | — |

## 1.2 System Context (C4 Level 1)

```
                    ┌──────────────────────────────────────────────┐
                    │                 ACTORS                        │
                    │  Ops staff · Warehouse (RF) · 3PL partners    │
                    │  B2B buyers · Client-portal users · AI agents │
                    └───────────────────────┬──────────────────────┘
                                            │ HTTPS / WSS
                    ┌───────────────────────▼──────────────────────┐
                    │              NEXUS PLATFORM                   │
                    │        (multi-tenant SaaS OMS + WMS)          │
                    └──┬──────────────┬───────────────┬────────────┘
                       │              │               │
             ┌─────────▼───┐  ┌───────▼──────┐  ┌─────▼─────────┐
             │  CHANNELS   │  │  CARRIERS    │  │  ERP / FINANCE│
             │ Shopify     │  │ FedEx        │  │ SAP           │
             │ Amazon      │  │ UPS / DHL    │  │ NetSuite      │
             │ BigCommerce │  │ project44    │  │ QuickBooks    │
             │ Magento     │  │ FourKites    │  │ Xero / Sage   │
             │ eBay/Walmart│  │              │  │               │
             └─────────────┘  └──────────────┘  └───────────────┘
```

## 1.3 Container Diagram (C4 Level 2)

```
┌───────────────────────────────────────────────────────────────────────────┐
│                            nexus-network (docker)                           │
│                                                                             │
│  ┌────────────┐  REST + STOMP  ┌──────────────────┐   JDBC    ┌──────────┐  │
│  │  Frontend  │───────────────▶│     Backend      │──────────▶│ Postgres │  │
│  │ React+Vite │◀───────────────│  Spring Boot 3   │           │ 16+vec   │  │
│  │   :80      │   WebSocket    │      :8080       │           │  :5432   │  │
│  └────────────┘                └──┬────┬──────┬───┘           └──────────┘  │
│                                   │    │      │                             │
│                     ┌─────────────┘    │      └────────────┐                │
│                     │                  │                   │                │
│               ┌─────▼─────┐     ┌──────▼──────┐     ┌──────▼───────┐        │
│               │   Redis   │     │    Kafka    │     │  AI (Flask)  │        │
│               │ cache /   │     │  outbox +   │     │ ai-ops  :5000│        │
│               │ locks /   │     │  events     │     │ ai-intel:5001│        │
│               │ rate-limit│     │             │     │              │        │
│               └───────────┘     └─────────────┘     └──────────────┘        │
│                                                                             │
│  ┌────────────┐  ┌──────────┐  ┌────────────┐  ┌────────────┐              │
│  │ Prometheus │  │ Grafana  │  │    Loki    │  │ Alertmgr   │              │
│  └────────────┘  └──────────┘  └────────────┘  └────────────┘              │
└───────────────────────────────────────────────────────────────────────────┘
```

## 1.4 Backend Layered Architecture

```
┌─────────────────────────────────────────────────────────────────────┐
│  controller/  (76)      REST endpoints · DTO in/out                  │
├─────────────────────────────────────────────────────────────────────┤
│  filter/ + security/ + entitlement/   ← cross-cutting request path   │
├─────────────────────────────────────────────────────────────────────┤
│  service/  (79)         business logic                               │
│    ├─ service/ai (27)      ├─ service/twin (digital twin)            │
│    ├─ service/auth         ├─ service/shopify                        │
│    ├─ service/bigcommerce  └─ …                                      │
├─────────────────────────────────────────────────────────────────────┤
│  integration/           protocol adapters + connectors               │
│    ├─ protocol/  EDI · GraphQL · REST · SOAP                         │
│    └─ connector/ ecommerce · erp · crm · wms · payment · shipping …  │
│  kafka/                 outbox writer · relay · producer · consumer  │
│  scheduler/             sync jobs + ShedLock                         │
├─────────────────────────────────────────────────────────────────────┤
│  repository/ (156)      Spring Data JPA                              │
├─────────────────────────────────────────────────────────────────────┤
│  entity/ (156)          JPA entities                                 │
├─────────────────────────────────────────────────────────────────────┤
│  PostgreSQL 16 + pgvector   (RLS per tenant)                         │
└─────────────────────────────────────────────────────────────────────┘
```

## 1.5 Request Lifecycle (the filter chain)

```
HTTP request
   │
   ▼  CorrelationIdFilter ──────────── assign trace id
   ▼  SecurityHeadersFilter / CspNonceFilter
   ▼  ImportTokenAuthenticationFilter ─ (import endpoints only)
   ▼  JwtAuthenticationFilter ──────── validate JWT → set TenantContext
   ▼  RateLimitingFilter ───────────── Redis token bucket
   ▼  IdempotencyFilter ────────────── dedupe by Idempotency-Key
   ▼  PermissionAuthorizationFilter ── RBAC check
   ▼  ModuleEntitlementFilter ──────── edition/module gate (OMS/WMS/3PL/EDI/AI)
   ▼
Controller → Service → Repository → Postgres (RLS)
```

The **`ModuleEntitlementFilter`** is the licensing boundary: it maps API prefixes → `ProductModule`
(longest-match, e.g. `/twin/orders`→OMS, other `/twin`→PLATFORM) and rejects calls the tenant's
edition doesn't own.

## 1.6 Data Layer & Multi-Tenancy

```
Request → JWT → TenantContext (ThreadLocal)
                    │
        ┌───────────┼────────────────┬──────────────────┐
        ▼           ▼                ▼                  ▼
TenantAwareDataSource   TenantScopedCacheManager   Postgres RLS
 (sets app.tenant_id)     (namespaced cache keys)   (USING policies)
```

- **RLS** on tenant tables (migration V69) — the DB itself refuses cross-tenant reads.
- **Optimistic locking** (V73), **ShedLock** (V71), **outbox** (V72).
- No hardcoded tenant IDs in the request path.

## 1.7 Event Flow (transactional outbox)

```
Business transaction (single DB txn)
   │
   ├─ write business row
   └─ OutboxWriter.enqueue(topic, key, payload) → nx_outbox (PENDING)
   │
   ▼  COMMIT  (atomic — event can't be lost or phantom)
   │
OutboxRelay  (@Scheduled every 1s + ShedLock PT2M)
   │  SELECT … WHERE status='PENDING' ORDER BY created_at, id LIMIT 200
   ▼
KafkaTemplate.send(topic, key, payload).get(10s)   [idempotent producer]
   │
   ├─ success → status='SENT', sent_at=now
   └─ fail    → attempts++ ; attempts ≥ 10 → status='FAILED' (parked)
                else break (preserve per-key order)
   │
   ▼  purgeSent() @ 02:15 daily → DELETE status='SENT' AND sent_at < now-7d
   │
KafkaConsumerService  (@KafkaListener per topic)
   │  EventRecord.fromJson(message)   ⚠ FAILS on bare-UUID payloads
   ▼
   ├─ ok   → NxAuditLog(entityId, entityType, eventType)
   └─ fail → kafkaTemplate.send("<topic>.dlq", message)
                → @KafkaListener(".*\.dlq") → log.warn → dropped
```

## 1.8 Integration Layer

```
                    ┌──────────────────────────────────────┐
                    │        ConnectorRegistry              │
                    │  (ConnectorFactoryConfiguration)      │
                    └───────────────┬──────────────────────┘
                                    │
        ┌───────────────────────────┼───────────────────────────┐
        ▼                           ▼                           ▼
  DEDICATED (11)              GENERIC (~28)              PROTOCOLS
  Shopify  Magento            eBay  Walmart  Etsy        EDI
  Amazon   BigCommerce        Alibaba  NetSuite          GraphQL
  SAP      Salesforce         Oracle ERP  Dynamics       REST
  FedEx    Stripe             HubSpot  Zoho  Manhattan   SOAP
  QuickBooks  Twilio          Blue Yonder  Oracle WMS
  Okta                        project44  FourKites  UPS
                              DHL  PayPal  Adyen  Xero
                              Sage  Square  Lightspeed
                              SendGrid  Slack  Auth0
                              Power BI  Tableau  Looker
```

## 1.9 AI Subsystem

```
Backend  service/ai  (27 services)
   │
   ├─ AiGatewayService ─────── routing + rule engine + inference
   ├─ AiInferenceService ───── ONNX Runtime (in-process, 1–10 ms)
   ├─ AiModelRegistryService / AiDeploymentGateService
   ├─ DemandForecastService / HybridSearchService (pgvector RAG)
   ├─ LlmChatService (OpenAI) / SemanticCacheService
   └─ McpToolExecutor ──────── exposes OMS tools to AI agents (RBAC + budget)
   │
   ▼ HTTP
Flask AI services
   ├─ ai-ops   :5000  (routing, carrier, box, pick-pack)
   └─ ai-intel :5001  (demand, inventory)
   │
   ▼
sklearn / xgboost models → ONNX artifacts
```

## 1.10 Deployment Topology

```
docker-compose (local/dev)          deploy/local (macOS)
  postgres · redis · kafka            launchd plists:
  backend · frontend                  com.nexus.oms.backend.plist
  ai-ops · ai-intel                   com.nexus.oms.frontend.plist
  prometheus · grafana                install.sh · start-backend.sh
  (+ loki · promtail · alertmanager   .env
   via monitoring/)
```

---

# PART 2 — FINDINGS

## 2.1 Severity Summary

| ID | Severity | Area | Issue | Status |
|---|---|---|---|---|
| ISS-01 | 🔴 Critical | Kafka | Consumer can't deserialize any published event → all order events dead-lettered | ✅ **Fixed** `e6d2d30`: producers publish the envelope; round-trip test **[test]** |
| ISS-02 | 🔴 High | Kafka | `FAILED` outbox rows never purged, never alerted | ✅ **Fixed**: 30-day purge, `nexus.outbox.failed` / `.pending` gauges **[test]**. The alert rule itself is not yet written |
| ISS-03 | 🔴 High | Kafka | Kafka disabled by default; docker-compose never enables it | 🟡 **Partly**: the dev compose now enables it; the application default stays `false` by design |
| ISS-04 | 🟠 Medium | Kafka | `publishEvent()` (the correct API) is dead code | ✅ **Fixed**: all 11 call sites use it; a test fails if any service publishes a raw payload **[test]** |
| ISS-05 | 🟠 Medium | Kafka | Dynamic `order.{status}` emits up to 16 topics, 11 undeclared | ✅ **Fixed**: all 16 declared **[test]** |
| ISS-06 | 🟠 Medium | Kafka | 8 consumers with no producer (dead listeners) | 🟡 Open by choice: the listeners are harmless and will work when shipment, inventory, payment and return producers are added |
| ISS-07 | 🟠 Medium | Kafka | DLQ: 3 uncoordinated mechanisms; Spring recoverer is a no-op | ✅ **Fixed**: the consumer no longer swallows errors and sends to the DLQ itself; the container's `DeadLetterPublishingRecoverer` is the one consuming-side path **[test]**. The outbox `FAILED` state is the separate producing-side path |
| ISS-08 | 🟠 Medium | Kafka | DLQ topics missing for 4 consumed topics | ✅ **Fixed**: one per consumed topic **[test]** |
| ISS-09 | 🟠 Medium | Kafka | ShedLock `lockAtMostFor` (2 min) < worst-case batch (~33 min) | ✅ **Fixed**: batch 100 × 3 s = 5 min inside a 10 min lock; an invariant test guards it **[test]** |
| ISS-10 | 🟠 Medium | Integration | Connector depth gap: 11 dedicated vs ~28 generic stubs | 🟡 **Made honest**: generic reads and writes now answer `NOT_SUPPORTED` instead of a fake success **[test]**. The depth gap itself remains |
| ISS-11 | 🟡 Low | Kafka | Consumer doesn't dedupe despite at-least-once delivery | ✅ **Fixed**: V79 `nx_processed_events`; event id claimed before processing, released if processing fails; 14-day purge **[test]** |
| ISS-12 | 🟡 Low | Kafka | Global head-of-line blocking on a poison event | ✅ **Fixed**: unreadable messages are rejected as non-retryable and dead-lettered at once; transient failures retry 3 × 1 s **[test]**. Not run against a real broker |
| ISS-13 | 🟡 Low | Kafka | Replication factor hardcoded to 1 | ✅ **Fixed**: `nexus.kafka.replication` (set 3 in production) |
| ISS-14 | 🟡 Low | Frontend | `AiAuditTrailPage` is a "not yet available" placeholder | 🔴 Open: honest placeholder; a real log needs AI decisions persisted on the routing path |
| ISS-15 | 🟡 Low | Backend | 2 stub markers (webhook, transform) | 🟡 The webhook default now says plainly it is unsupported; the transform stub is open |
| ISS-16 | 🟡 Low | Repo | Uncommitted `GlobalExceptionHandler` 404 fix in working tree | ✅ **Fixed and committed** `5533dfe` **[test]** |

---

## 2.2 Critical & High

### ISS-01 — Consumer can't deserialize any published event 🔴 **[outbox]**

**The single most important finding.** The producer and consumer disagree on the payload shape.

| Side | Code | Payload on the wire |
|---|---|---|
| Producer | `OrderService.publish("order.created", order.getId().toString())` | `3fa85f64-5717-4562-b3fc-2c963f66afa6` (bare UUID) |
| Consumer | `EventRecord.fromJson(message)` | expects `{"eventId":…,"eventType":…,"tenantId":…}` |

`EventRecord.fromJson` is `MAPPER.readValue(json, EventRecord.class)`. Given a bare UUID, Jackson
throws `JsonParseException` → wrapped in `RuntimeException` → caught by the consumer's `catch` →
**`kafkaTemplate.send(Topics.dlq(topic), message)`**.

**Impact chain:**
```
OrderService.publish("order.created", "<uuid>")
  → outbox row (payload = bare uuid)
  → relay sends raw uuid to Kafka
  → consumer: EventRecord.fromJson("<uuid>")  ✗ throws
  → catch → send to order.created.dlq
  → @KafkaListener(".*\.dlq") → log.warn → dropped
```

**Net effect:** when Kafka is enabled, **every order lifecycle event is dead-lettered and no audit
trail is ever written.** The bus is effectively write-only.

**Root cause:** the correct API — `publishEvent(topic, eventType, data)`, which wraps the payload in
an `EventRecord` — is **never called** (see ISS-04). Every real caller uses the raw `publish`.

**Fix:** callers switch to `publishEvent(...)` — see Part 3.

---

### ISS-02 — `FAILED` outbox rows never purged, never alerted 🔴 **[outbox]**

| Aspect | Detail |
|---|---|
| Where | `OutboxRelay` — after `attempts ≥ MAX_ATTEMPTS (10)`, sets `status = 'FAILED'` |
| Purge | `purgeSent()` deletes only `status='SENT'` older than 7 days — **`FAILED` rows are never deleted** |
| Alert | Only `log.error(...)`; **no metric, no alert rule** |
| Consequence | Poison events accumulate silently; nobody knows the bus is dropping events |

**Correction to earlier note:** SENT rows *are* purged (7-day retention). The gap is specifically
that FAILED rows have no lifecycle and no observability.

---

### ISS-03 — Kafka disabled by default 🔴 **[outbox]**

| Aspect | Detail |
|---|---|
| Default | `nexus.kafka.enabled=false` → `NoOpKafkaProducerService` is wired |
| docker-compose | Starts a Kafka container but **never sets `NEXUS_KAFKA_ENABLED=true`** on the backend |
| Consequence | In the default stack, **nothing is ever published** — the outbox is inert |
| Compounding | ISS-01 only manifests when Kafka *is* enabled — so the bug hides until someone turns it on |

---

## 2.3 Medium

### ISS-04 — `publishEvent()` is dead code 🟠 **[outbox]**

| API | Payload | Callers |
|---|---|---|
| `publish(topic, key, message)` | raw string | OrderService (×9), ShippingLabelService, FulfillmentReconciliationService |
| `publishEvent(topic, eventType, data)` | `EventRecord` JSON | **nobody** |

The one API that would make the consumer work is the one nobody calls. Directly enables ISS-01.

> **Note:** `EventBus.publish(IntegrationEvent)` is a *separate, correct* path — it calls
> `producer.publish(topic, eventId, event.toJson())`, wrapping in `EventRecord` JSON. But it targets
> `integration.*` topics that have **no declared topics and no consumers**, so those events also go
> nowhere.

### ISS-05 — Dynamic `order.{status}` topic sprawl 🟠 **[outbox]**

`OrderService.updateStatus()` line 211:
```java
kafkaProducerService.publish("order." + status.toLowerCase(), order.getId().toString());
```
`OrderStatusPolicy` defines **16 statuses** → this one line can emit 16 topics. Only 5
(`confirmed`, `allocated`, `shipped`, `delivered`, `cancelled`) are declared/consumed. The other 11
(`pending`, `partially_allocated`, `failed`, `picking`, `packing`, `in_transit`, `fulfilled`,
`returned`, `exception`, `merged`, `split`) are **undeclared and unconsumed** — invisible to
`Topics.java`.

### ISS-06 — 8 consumers with no producer 🟠 **[outbox]**

`order.updated`, `shipment.created`, `shipment.updated`, `inventory.adjusted`, `payment.received`,
`payment.refunded`, `return.created`, `return.approved` are **declared, created, and consumed — but
nothing publishes them.** The Python AI services have zero Kafka references. Either reserved for
external producers or aspirational stubs; today they're dead listeners.

### ISS-07 — DLQ: 3 uncoordinated mechanisms 🟠 **[outbox]**

| Mechanism | Behaviour |
|---|---|
| Consumer manual send | `kafkaTemplate.send(Topics.dlq(topic), message)` on catch |
| Spring `DefaultErrorHandler` recoverer | configured but **no-op** |
| `@KafkaListener(".*\.dlq")` | logs and drops |

Three paths, no single owner; the Spring recoverer does nothing.

### ISS-08 — DLQ topics missing for 4 consumed topics 🟠 **[outbox]**

`KafkaTopicConfig.omsTopics()` creates DLQ topics for only **10 of 14** consumed topics. Missing:
`order.confirmed.dlq`, `order.allocated.dlq`, `order.shipped.dlq`, `order.delivered.dlq` → relies on
broker auto-create (fails in prod).

### ISS-09 — ShedLock window < worst-case batch 🟠 **[outbox]**

`@SchedulerLock(lockAtMostFor = "PT2M")` but worst case is `BATCH_SIZE (200) × SEND_TIMEOUT_MS (10s)`
= **~33 min**. A second relay instance can acquire the lock mid-batch → **duplicate sends**.

### ISS-10 — Connector depth gap 🟠 **[code]**

| Tier | Count | Examples |
|---|---|---|
| Dedicated implementations | **11** | Shopify, Magento, Amazon, BigCommerce, SAP, Salesforce, FedEx, Stripe, QuickBooks, Twilio, Okta |
| Generic stubs (`createGenericFactory`) | **~28** | eBay, Walmart, Etsy, Alibaba, NetSuite, Oracle ERP, Dynamics 365, HubSpot, Zoho, Manhattan, Blue Yonder, Oracle WMS, project44, FourKites, UPS, DHL, PayPal, Adyen, Xero, Sage, Square, Lightspeed, SendGrid, Slack, Auth0, Power BI, Tableau, Looker |

The marketplace advertises ~39 platforms; only ~11 have real protocol logic. See **Part 4** for the
full deep dive.

---

## 2.4 Low

| ID | Issue | Detail |
|---|---|---|
| ISS-11 | No consumer dedupe | At-least-once delivery, but no `eventId` dedupe table → reprocessing on redelivery |
| ISS-12 | Head-of-line blocking | One poison event blocks the whole partition batch |
| ISS-13 | Replication factor = 1 | `KafkaTopicConfig.REPLICATION = 1` hardcoded → no HA in prod |
| ISS-14 | `AiAuditTrailPage` placeholder | Renders "Audit trail not yet available" |
| ISS-15 | 2 stub markers | `BaseApiConnector.registerWebhooks` ("not implemented"), `TransformationEngine` unsupported transform |
| ISS-16 | Uncommitted fix | `GlobalExceptionHandler` + test — unknown URLs return 404 not 500 (+12 lines, coherent) |

---

## 2.5 Resolved Since the 2026-09-05 Audit ✅

Re-verified against current source; **do not treat these as open**:

| Audit finding | Status |
|---|---|
| BigCommerce registered with `ShopifyConnector.Factory` | ✅ Fixed — now `BigCommerceConnector.Factory()` (`ConnectorFactoryConfiguration:50`) |
| Shopify marked "coming soon" in marketplace UI | ✅ Fixed — no longer present |
| `SyncConflictService.recoveryJob` "No tenant context" | ✅ Fixed — now sets `TenantContext` (`SyncConflictService:190`) |
| `GenericHttpConnector` → `docs.example.com` stub | ✅ Fixed |
| `WarehouseDashboardPage` hardcoded KPIs | ✅ Fixed — "Nothing is hardcoded" |

---

# PART 3 — FIX PLAN: ISS-01 / ISS-04 (the critical event-contract bug)

> **Status: implemented in `e6d2d30`.** Two deliberate differences from the plan below: (1) the call
> sites use a helper, `publishEntityEvent(topic, entityId)`, that derives the event type from the
> topic (`order.shipped` becomes `ORDER_SHIPPED`) rather than 11 hand-written strings; (2) the Kafka
> key is the **entity id**, not the random event id, so all events for one order share a partition
> and stay in order (the plan would have lost per-order ordering). The consumer was not loosened.

## 3.1 Root Cause (one sentence)

`KafkaProducerService.publish(topic, message)` sends the **raw** message, while
`KafkaConsumerService` parses every message as an **`EventRecord` JSON envelope** — and every real
caller uses `publish`, never `publishEvent`.

## 3.2 The Two APIs

```java
// KafkaProducerService.java
public void publish(String topic, String key, String message) {          // ← raw, used by everyone
    UUID id = outbox.enqueue(topic, key, message);
}

public void publishEvent(String topic, String eventType, String data) {  // ← correct, zero callers
    EventRecord event = EventRecord.create(eventType, topic, data, TenantContext.getCurrentTenantId());
    publish(topic, event.eventId(), event.toJson());                     // ← wraps in envelope
}
```

## 3.3 Change Set — switch callers to `publishEvent`

| # | File | Line | Before | After |
|---|---|---|---|---|
| 1 | `service/OrderService.java` | 154 | `publish("order.created", id.toString())` | `publishEvent("order.created", "ORDER_CREATED", id.toString())` |
| 2 | `service/OrderService.java` | 211 | `publish("order." + status.toLowerCase(), id.toString())` | `publishEvent("order." + status.toLowerCase(), "ORDER_" + status.toUpperCase(), id.toString())` |
| 3 | `service/OrderService.java` | 270 | `publish("order.allocated", …)` | `publishEvent("order.allocated", "ORDER_ALLOCATED", …)` |
| 4 | `service/OrderService.java` | 327 | `publish("order.confirmed", …)` | `publishEvent("order.confirmed", "ORDER_CONFIRMED", …)` |
| 5 | `service/OrderService.java` | 352 | `publish("order.shipped", …)` | `publishEvent("order.shipped", "ORDER_SHIPPED", …)` |
| 6 | `service/OrderService.java` | 424 | `publish("order.modified", …)` | `publishEvent("order.modified", "ORDER_MODIFIED", …)` |
| 7 | `service/OrderService.java` | 504 | `publish("order.split", …)` | `publishEvent("order.split", "ORDER_SPLIT", …)` |
| 8 | `service/OrderService.java` | 559 | `publish("order.merged", …)` | `publishEvent("order.merged", "ORDER_MERGED", …)` |
| 9 | `service/OrderService.java` | 590 | `publish("order.cancelled", …)` | `publishEvent("order.cancelled", "ORDER_CANCELLED", …)` |
| 10 | `service/ShippingLabelService.java` | 214 | `publish("order.label_generated", …)` | `publishEvent("order.label_generated", "ORDER_LABEL_GENERATED", …)` |
| 11 | `service/FulfillmentReconciliationService.java` | 282 | `publish(ORDER_FULFILLED_TOPIC, …)` | `publishEvent(ORDER_FULFILLED_TOPIC, "ORDER_FULFILLED", …)` |

**Why this is safe:** `publishEvent` internally calls `publish`, so the outbox path, keying, and
retry semantics are unchanged. Only the payload becomes an envelope.

**Consumer compatibility:** the consumer reads `event.data()` as the entity id and does
`UUID.fromString(event.data())` — which now receives the UUID string. ✅ No consumer change needed.

## 3.4 Companion Fixes (do in the same PR)

| Fix | Why | Change |
|---|---|---|
| **ISS-08** — add 4 missing DLQ topics | After ISS-01, `order.confirmed/allocated/shipped/delivered` will actually be consumed; if they fail, the DLQ send must not fail | Add `dlqTopic(...)` for those 4 in `KafkaTopicConfig.omsTopics()` |
| **ISS-05** — stop topic sprawl | `order.{status}` still emits undeclared topics | Either declare the 11 missing topics, or route all status changes through one `order.status_changed` topic with the status in the payload |
| **ISS-02** — FAILED lifecycle | Parked rows accumulate invisibly | Add a purge for `status='FAILED'` (e.g. 30-day retention) + a gauge metric `nexus_outbox_failed_total` + alert |
| **ISS-09** — lock window | Duplicate sends | Raise `lockAtMostFor` to `PT45M` (or lower `BATCH_SIZE`/`SEND_TIMEOUT_MS`) |

## 3.5 Test Plan

| Test | Type | Assertion |
|---|---|---|
| `publishEvent` writes an `EventRecord` JSON to the outbox | Unit | `EventRecord.fromJson(payload)` round-trips; `data` == UUID |
| `OrderService.create` enqueues an envelope | Unit | Outbox row payload parses as `EventRecord` |
| Consumer handles an envelope | Unit | `NxAuditLog` row written with correct `entityId` |
| Consumer handles a **bare UUID** (regression guard) | Unit | Currently throws → after fix, no caller produces this; keep a test documenting the contract |
| End-to-end: create order → audit log | Integration (Testcontainers Kafka) | Audit row exists within N seconds |
| DLQ: force a consumer failure | Integration | Message lands on `<topic>.dlq` and the topic exists |

## 3.6 Rollout

1. Land the caller changes + companion fixes behind the existing `nexus.kafka.enabled` flag.
2. Enable Kafka in a staging compose (`NEXUS_KAFKA_ENABLED=true`) and run the E2E test.
3. Add the `nexus_outbox_failed_total` metric + alert **before** enabling in prod.
4. Enable in prod; watch the DLQ topic and the FAILED gauge for 24h.

---

# PART 4 — DEEP DIVE: INTEGRATION & CONNECTOR LAYER

## 4.1 Class Hierarchy

```
AbstractConnector
   └─ BaseApiConnector                     ← default impls (mostly no-ops)
        ├─ GenericHttpConnector            ← ~28 platforms (createGenericFactory)
        │     ├─ eBay, Walmart, Etsy, Alibaba
        │     ├─ NetSuite, Oracle ERP, Dynamics 365
        │     ├─ HubSpot, Zoho, Manhattan, Blue Yonder, Oracle WMS
        │     ├─ project44, FourKites, UPS, DHL
        │     ├─ PayPal, Adyen, Xero, Sage, Square, Lightspeed
        │     ├─ SendGrid, Slack, Auth0
        │     └─ Power BI, Tableau, Looker
        └─ Dedicated (11)
              ├─ ecommerce: Shopify, Magento, Amazon, BigCommerce
              ├─ erp:       SAP
              ├─ crm:       Salesforce
              ├─ shipping:  FedEx
              ├─ payment:   Stripe
              ├─ accounting:QuickBooks
              ├─ communication: Twilio
              └─ identity:  Okta
```

## 4.2 What Actually Works vs. What Is a No-Op

| Method | `BaseApiConnector` (default) | `GenericHttpConnector` | Dedicated |
|---|---|---|---|
| `syncOrders` | ❌ returns 0, logs only | ✅ real `restClient.paginatedGet` | ✅ platform-specific |
| `syncProducts` | ❌ returns 0, logs only | ✅ real `restClient.get` | ✅ platform-specific |
| `pushInventory` | ❌ returns 0, logs only | ❌ returns 0, logs only | ✅ (where implemented) |
| `pushFulfillments` | ❌ returns 0, logs only | ❌ (inherited) | ✅ (where implemented) |
| `pushRefunds` | ❌ returns 0, logs only | ❌ (inherited) | ✅ (where implemented) |
| `registerWebhooks` | ❌ `log.info("Webhooks not implemented")` | ❌ (inherited) | ✅ (where implemented) |
| `testConnection` | ⚠️ `GET baseUrl + "/"` (root) | ⚠️ `GET health_path` (default `/`) | ✅ platform-specific |

> **Update (this audit):** the table above is the state *found*. A generic connector's "pull" fetched
> records, counted them as `itemsSucceeded`, and **discarded them** (nothing maps them into Nexus
> orders), and its writes answered `COMPLETED` with zero items. Both now answer `NOT_SUPPORTED`
> with a warning that says what was and was not done **[test]**. The depth gap is unchanged.

**Read this carefully:** a generic connector can **pull orders and products** (real REST), but
**cannot push inventory, fulfillments, or refunds** — those return `itemsSucceeded(0)` and only log.
So the marketplace's "INVENTORY_PUSH / FULFILLMENT_PUSH / REFUND_PUSH" capabilities are advertised
in `ConnectorMetadata.supportedSyncTypes` but are **not implemented** for generic platforms.

## 4.3 The Gap, Quantified

| Dimension | Reality |
|---|---|
| Platforms advertised | ~39 |
| Platforms with real order/product pull | ~11 dedicated + ~28 generic = ~39 (generic does real GETs) |
| Platforms with real inventory/fulfillment/refund push | **~11** (dedicated only) |
| Platforms with webhook registration | **~11** (dedicated only) |
| Platforms with a real connection test | **~11** (dedicated only; generic hits `/`) |

**The honest framing:** generic connectors are *read-capable* but *write-incapable*. For a
marketplace that promises two-way sync, ~28 of ~39 integrations are half-implemented.

## 4.4 Risks

| Risk | Impact |
|---|---|
| `testConnection` hits `/` for generic platforms | False "connected" or false "failed" depending on the vendor's root response |
| `pushInventory` silently returns success (0 items) | Callers believe inventory synced; it didn't |
| `registerWebhooks` no-op | No inbound webhooks for generic platforms → polling-only |
| `supportedSyncTypes` overstates capability | UI/marketplace misleads users |
| `forPlatform` builds `https://<platform>.com/docs` | Docs URL is a guess, not a real link |

## 4.5 Recommendations

1. **Make capability honest.** Split `supportedSyncTypes` into `implemented` vs `planned`, and have
   the marketplace render "read-only" badges for generic platforms.
2. **Fail loud, not silent.** `pushInventory`/`pushFulfillments`/`pushRefunds` should return
   `Status.NOT_SUPPORTED` (or throw) rather than `COMPLETED` with 0 items.
3. **Fix `testConnection`.** Require a configured `health_path`; if absent, return "unknown" rather
   than probing `/`.
4. **Prioritise the top platforms.** Promote the highest-demand generic platforms (UPS, DHL,
   NetSuite, Walmart, eBay) to dedicated connectors with real push + webhooks.
5. **Add a connector conformance test.** A shared test suite every connector must pass (auth,
   pull, push, webhook, error mapping) — this is how incumbents certify integrations.

---

# PART 5 — WHAT'S BEST FOR NEXUS: A PRIORITIZED ROADMAP

> **Thesis.** Nexus does not have a *feature* problem — it has a **credibility** problem. The core
> orchestration loop (events) is broken, and roughly 28 of ~39 advertised integrations are
> read-only while presenting as two-way. Fixing what already exists beats adding anything new.
>
> This part ranks the work by leverage, not by novelty. Every item below traces back to a finding
> in Part 2 and, where a fix is already designed, to Part 3 or Part 4.

## 5.1 The Priority Stack

| Rank | Workstream | Findings | Why it ranks here | Effort | Impact |
|---|---|---|---|---|---|
| **P0** | Fix the event backbone | ISS-01, ISS-04 (+ ISS-05, ISS-08) | Every downstream capability — notifications, analytics, workflows, AI triggers, audit — hangs off Kafka events. Today they **all dead-letter**. | S–M | 🔴 Unblocks everything |
| **P1** | Make integrations honest, then real | ISS-10 | The product is sold on ~39 integrations; ~28 **silently no-op writes**. Trust-destroying. | M–L | 🔴 Revenue / retention |
| **P2** | Close the customer onboarding gap | *(unverified — see §5.4)* | For a self-serve / mid-market product, onboarding **is** the product. | M | 🟠 Activation |
| **P3** | Operational hardening | ISS-02, ISS-03, ISS-07, ISS-09, ISS-11, ISS-12, ISS-13 | Blind spots and latent duplicate-send / HA risks. | S–M | 🟡 Reliability |

**Effort key:** S = days · M = 1–2 weeks · L = 3+ weeks.

## 5.2 P0 — Fix the event backbone (ISS-01 + ISS-04)

**The problem in one line:** producers publish a bare UUID; the consumer parses an `EventRecord`
envelope; every order event dead-letters; the one correct API (`publishEvent`) has **zero callers**.

**Why this is #1 and not #2:** the entire value proposition is "one brain that orchestrates the
order." If the brain cannot emit events, the orchestration is a fiction — and every feature built
on top (notifications, analytics, AI triggers, audit trail) is silently dead. Nothing else you
build matters until this works.

**Definition of done:**

- [ ] All **11 call sites** switched to `publishEvent(...)` — see Part 3 §3.3.
- [ ] Companion fixes landed in the same PR: **ISS-08** (4 missing DLQ topics), **ISS-05** (topic
      sprawl), **ISS-02** (FAILED lifecycle + metric + alert), **ISS-09** (lock window ≥ worst-case batch).
- [ ] E2E test passes: create order → `NxAuditLog` row written within N seconds (Testcontainers Kafka).
- [ ] Kafka enabled in staging (`NEXUS_KAFKA_ENABLED=true`) — note **ISS-03**: it is off by default,
      which is *precisely why this bug hides* until someone turns it on.

**Trap to avoid:** do **not** "fix" this by making the consumer tolerate bare UUIDs. That entrenches
the wrong contract and leaves the envelope path dead. Fix the **producer**, not the consumer.

**Observability prerequisite:** add `nexus_outbox_failed_total` (gauge) + an alert rule **before**
enabling in prod — otherwise you fix the bus but stay blind to the next failure (ISS-02).

## 5.3 P1 — Make integrations honest, then real (ISS-10)

Do this in **two ordered steps**. Honesty is cheap and immediate; depth is expensive and staged.

### Step 1 — Honesty (do first)

| Change | Where | Why |
|---|---|---|
| Split `supportedSyncTypes` into `implemented` vs `planned` | `ConnectorMetadata` | The marketplace currently overstates capability |
| Render "read-only" badges for generic platforms | Marketplace UI | Sets correct expectations before a customer connects |
| `pushInventory` / `pushFulfillments` / `pushRefunds` → `NOT_SUPPORTED` (or throw) | `BaseApiConnector` / `GenericHttpConnector` | A silent `COMPLETED` with 0 items is worse than an explicit refusal |
| `testConnection` requires a configured `health_path`; else return "unknown" | `BaseApiConnector` | Stop probing `/` and reporting false connected/failed |

### Step 2 — Depth (do second)

- Promote the highest-demand generic platforms to **dedicated** connectors with real push + webhooks.
  Candidate order: **UPS, DHL, NetSuite, Walmart, eBay**.
- Add a **connector conformance test** every connector must pass: auth, pull, push, webhook, error
  mapping. This is how incumbents certify integrations — and how you stop the gap from reopening.

**Why honesty first:** a silent no-op that reports success destroys trust the first time a customer's
inventory drifts. An explicit "not supported" costs a feature but keeps the customer.

## 5.4 P2 — Close the customer onboarding gap

**Status: unverified.** The system *has* the machinery to onboard a tenant — connectors, credential
vault, and `ModuleEntitlementFilter` (editions/add-ons enforced in the request path) — but the
**customer-facing flow** (integrate → pull/load data → configure → go-live) has not been verified
against source in this audit.

**What the code implies the onboarding must be:**

| Stage | Mechanism (verified) | Gap |
|---|---|---|
| **Integrate** | `ConnectorFactory` + credential vault; 11 dedicated, ~28 generic | Generic connectors cannot push (ISS-10) |
| **Pull / load data** | Generic connectors do real order/product pulls via REST | Inventory/fulfillment/refund pushes are no-ops |
| **Configure** | `ModuleEntitlementFilter` maps API prefix → `ProductModule` | — |
| **Go-live** | Gated by the event backbone | **ISS-01** blocks it until P0 lands |

**Definition of done:** a documented, code-verified onboarding runbook — every step traceable to a
real endpoint or service, with no aspirational steps. **Do not ship onboarding docs that describe a
flow the code does not implement.**

## 5.5 P3 — Operational hardening

| Finding | Fix | Why it matters |
|---|---|---|
| **ISS-02** — FAILED rows never purged/alarmed | 30-day purge + `nexus_outbox_failed_total` gauge + alert | You are currently blind to dropped events |
| **ISS-03** — Kafka off by default | Set `NEXUS_KAFKA_ENABLED=true` in the standard compose | The default stack publishes nothing |
| **ISS-07** — 3 uncoordinated DLQ mechanisms | Pick one owner; make the Spring recoverer real or remove it | No single source of truth for failures |
| **ISS-09** — lock window < batch | Raise `lockAtMostFor` to `PT45M` (or shrink batch/timeout) | Prevents duplicate sends |
| **ISS-11** — no consumer dedupe | `eventId` dedupe table | At-least-once → reprocessing |
| **ISS-12** — head-of-line blocking | Per-message error handling / retry topic | One poison event stalls a partition |
| **ISS-13** — replication factor = 1 | Make RF configurable (≥3 in prod) | No HA |

## 5.6 Sequencing & Dependencies

```
P0 (event backbone) ──▶ P1 (integrations) ──▶ P2 (onboarding) ──▶ P3 (hardening)
        │
        └─ ISS-03 (enable Kafka) is a prerequisite to *observing* P0 in any environment
```

- **P0 before P1:** integrations that push inventory depend on events flowing.
- **P0 before P2:** onboarding "go-live" is gated by the event bus.
- **P3 is parallelizable** with P1/P2 once P0 lands — but the ISS-02 metric must ship *with* P0.

## 5.7 Success Metrics

| Workstream | Metric | Target |
|---|---|---|
| P0 | Order events reaching `NxAuditLog` | 100% (from ~0%) |
| P0 | `nexus_outbox_failed_total` | Flat / zero in steady state |
| P1 | Connectors advertising two-way sync that actually push | 11 → 16+ |
| P1 | Silent no-op writes | 0 (all return `NOT_SUPPORTED`) |
| P2 | Time-to-first-order for a new tenant | Measured, documented, decreasing |
| P3 | DLQ depth + FAILED outbox rows | Alerted, bounded |

## 5.8 Anti-Priorities (what NOT to do)

- **Don't add new connectors** before the existing ~28 are honest.
- **Don't build new AI features** while the event bus is write-only — they have no events to react to.
- **Don't "fix" ISS-01** by loosening the consumer contract; fix the producer.
- **Don't ship onboarding docs** that describe a flow the code does not implement.
- **Don't chase enterprise logos** (full ERP/WMS/POS) — that is explicitly out of scope; the ICP is
  small/mid-market brands, multi-channel retailers, 3PLs, and EDI-heavy B2B.

---

# PART 6 — WHAT RUNNING IT END TO END EXPOSED

Everything in Parts 2 to 5 came from reading code. This part is what turned up when the system was
**driven the way a user drives it**: a 39-step lifecycle script against the live API (product, stock,
order, allocate, pick, pack, ship, deliver, return, refund, receive, count, transfer, ledger), a sweep
of all 188 parameterless `GET` endpoints, a browser pass over every screen, and a read of every `catch`
block that shows a success message.

The headline: **the unit tests were green and the pages loaded, and yet core flows were wrong.** None
of the defects below were visible to either.

## 6.1 The findings

| # | Area | What was wrong | How it showed | Status |
|---|---|---|---|---|
| L-01 | Inventory | **Shipping never took stock off the books.** Status changed; on-hand stayed overstated forever and the allocation was never cleared | Live script: after shipping 3 units, on-hand was still 50 | ✅ `1b25976` **[live][test]** |
| L-02 | Inventory | **Transfer orders moved no stock.** Ship and receive only changed status fields | No inventory call anywhere in `TransferOrderService` | ✅ `1b25976` **[live][test]** |
| L-03 | OMS | **Allocation failed for any tenant whose warehouses are `OPERATIONAL`** ("No active warehouse nodes found"); every caller asked for exactly `ACTIVE` | Live: manual allocate returned 400 on the demo tenant | ✅ `1b25976` **[live][test]** |
| L-04 | OMS | **Every partial allocation failed to record its backorder exception.** The allocation strategy name was written into `resolution_strategy`, which a CHECK constraint limits to eight other values; the brokering job then errored on every cycle | Server log: `violates check constraint "..._resolution_strategy_check"` | ✅ `1b25976`; a test pins the Java list to the migration's constraint **[test]** |
| L-05 | Inventory | A manual adjustment of a SKU held in several locations changed **whichever row the database returned first** | Code read after V70 made stock per-location | ✅ must now name the location **[test]** |
| L-06 | Inventory | Cancelling a *shipped* order would release its allocation again, freeing another order's reservation | Follows from L-01 | ✅ releases only what is still reserved **[test]** |
| L-07 | Inventory | **There was no ledger.** No history of on-hand, allocated or reserved changes, so nothing could be reconciled | No movement table | ✅ V78: append-only, enforced by a trigger **[live][test]**; see [17](./17-INVENTORY-LEDGER.md) |
| L-08 | Shopify | The webhook event-id was recorded **before** the signature was checked (a forged request could burn an id so the real delivery was dropped as a "duplicate"); the check-then-set was not atomic | Code read | ✅ `71a42ac` **[live][test]** |
| L-09 | Shopify | The signed body was rebuilt line by line (altering the bytes); only one global secret was accepted | Code read | ✅ raw bytes, per-shop secret first **[test]** |
| L-10 | Security | **Integration secrets (access tokens, client secrets) were stored in plaintext and returned by `GET /integration-stores/{id}/settings`** | Live: the API returned the token | ✅ sealed (AES-GCM), masked in the API, never cached in Redis **[live][test]** |
| L-11 | Shopify | Inventory push **summed every warehouse and wrote the total to whichever Shopify location came back first**, falling back to location `1`; read only the first 250 products; swallowed errors | Code read | ✅ replaced by location-aware publishing with dry run and a reconciliation report `75d3b88` **[test]**; **[unverified]** against a live shop |
| L-12 | Shopify | No handling of HTTP 429 or transient errors; API version pinned past Shopify's support window | Code read | ✅ `Retry-After` and backoff; version configurable **[test]** |
| L-13 | Integrations | **The Amazon, eBay and Walmart connector API simulated everything:** "sync" counted local orders and wrote a `SUCCESS` log; `authorize` marked the connector connected without checking and, with no token, stored the literal word "authorized"; "marketplace inventory" and "listings" were Nexus's own data | Live: `POST /connectors/walmart/sync` returned "Walmart sync completed"; status said `connected: true` with `hasCredentials: false` | ✅ `c3e1390`: never "connected", sync answers 501, secrets sealed, responses labelled `NEXUS_RECORDS` **[live][test]** |
| L-14 | Integrations | Generic connectors reported `COMPLETED` for pushes they cannot do, and "imported" N orders that they downloaded and discarded | Code read | ✅ `NOT_SUPPORTED` with a warning **[test]** |
| L-15 | AI | **`POST /ai/chat` with no model configured (the default) answered from a script:** an invented order number, "sales are up 18%", made-up top customers with dollar amounts, "Top-moving SKU is NEXUS-PRO-X1 with 1,247 units" | Live: asked about stock, got an invented SKU | ✅ `5533dfe`: refuses with a 503 that says how to enable it **[live][test]** |
| L-16 | Frontend | The AI panel stored the whole `{success, content}` response as the message text, so answers could not render and errors never reached the user | Component test | ✅ `5533dfe` **[test]** |
| L-17 | API | Any unknown URL, malformed JSON body, wrong method or wrong content type returned **500** | Live probes | ✅ 404 / 400 / 405 / 415 **[live][test]** |
| L-18 | API | Settings page 500 for tenants created by sign-up; analytics NPE; six yard endpoints 500 on a missing warehouse; EDI uploads failed on Flyway databases and acknowledged the wrong control numbers; picker creation 500 | GET sweep + live | ✅ `59734e9`, `1b6f592`, `5783091` **[live][test]** |
| L-19 | Frontend | **Type checking was a no-op.** The root `tsconfig.json` checks no files, so `tsc` and `npm run build` checked nothing; the real config had 389 errors | Running the real config | ✅ per-file ratchet in CI; **389 → 140** |
| L-20 | Frontend | `useToast()` had no `toast()`, yet six screens call it: live notifications and four pages threw after every action | Component test | ✅ `4339b41` **[test]** |
| L-21 | Frontend | The admin role switcher always granted **zero** permissions (read `.data.data` of an array) | Type error | ✅ **[code]** |
| L-22 | Frontend | The packer screen requested `status=[object Object]` | Type error | ✅ **[code]** |
| L-23 | Frontend | The Orders list showed **blank Carrier, Destination and Promised Delivery for every order** (it read names the API never sends) | Compared a live order with the columns | ✅ one normaliser for list and detail `08e2211` **[test][live]** |
| L-24 | Frontend | **One dev tenant's warehouse id was hard-coded into six screens**; their dropdowns offered "East DC" and "West DC" that exist nowhere | grep | ✅ real warehouses, with an empty state `08e2211` **[live]** |
| L-25 | Frontend | Multi-Node Inventory showed **"$12.4M", 23 / 156 / 34, an invented "Mumbai Central WH" at 42% capacity**, a search that could never return a result, and a **"Transfer Created" toast that never called the API** | Reading the page | ✅ rebuilt on real data with tested arithmetic `08e2211` **[live][test]** |
| L-26 | Frontend | Labor showed literal 142 / 96% / 88%, a fabricated efficiency-by-weekday chart, `NaN%`, and crashed when its Assign modal opened (`waves` undefined) | Reading the page | ✅ derived from productivity logs; modal fixed **[test]** |
| L-27 | Frontend | Amazon, eBay and Walmart pages showed **"sync completed: 23 orders imported, 2 errors" when the call failed**; a Save that was a `setTimeout`; invented KPIs | Reading `catch` blocks | ✅ `c3e1390` **[live]** |
| L-28 | Frontend | Automation Systems: seven handlers claimed "(simulated)" success and added fabricated rows when the real call failed | grep `simulated` | ✅ failures are reported and nothing changes **[code]** |
| L-29 | Frontend | Shared components silently dropped what callers passed (KPI colours and labels, grid `header`, breadcrumb `href`) | Type errors | ✅ **[test]** |
| L-31 | Security | **Equipment API keys were stored in plaintext and returned by `GET /automation/systems`** | Found while building the inbound path | ✅ the key is write-only in JSON **[live]**; still stored unsealed in the database |
| L-32 | Kafka | The consumer dropped the event's tenant id when writing the audit row | Code read | ✅ **[test]** |
| L-30 | CI | Backend CI red for days: a test schema excluded by `.gitignore`; code-scanning and Sonar jobs failing because unconfigured; a CVE in a binary the image never uses | CI logs | ✅ |

## 6.2 The patterns, so they can be searched for

1. **Fake success in a `catch`.** Code that, when the real call fails, shows or returns success with invented
   numbers. Search for success toasts and `COMPLETED` results outside the happy path.
2. **A constant where a lookup belongs.** Warehouse ids, KPI values, "East DC". Search for UUID literals and
   numeric literals next to labels.
3. **A contract that two sides disagree about** (event envelope, order field names, CHECK-constraint values).
   Each needs a test that goes round the whole loop, not one that checks each half.
4. **A check that checks nothing** (`tsc` over zero files; a stale incremental compile; a skipped test suite
   when Docker is down). Prove a gate can fail before trusting that it passes.
5. **An operation that changes a status but not the thing the status is about** (ship, transfer, restock).
   Assert the stock, not the status.

## 6.3 What found them

None of the unit tests, none of the page-load audit, and none of the type checks. What found them:
`scripts/e2e/lifecycle-smoke.py` (now in the repo, 39 checks, passes 39/39), a sweep of every `GET`, reading
the code behind every success message, and comparing a real API response with what the screen reads.
**The lifecycle script is not yet run by CI.** That is the single cheapest improvement to the safety net.

---

## 6.4 Equipment automation: the inbound path (new)

A separate review found there was **no inbound path from equipment**: commands went out, nothing came
back, and a command stayed `SENT` unless the synchronous reply happened to say more. Webhooks were
commerce-only and the twin is read-only. Now present **[live][test]**:

- `POST /webhooks/equipment/{tenantId}/{systemId}/heartbeat`: equipment is alive (never revives a system an operator switched off or put in maintenance; `ERROR` and `DEGRADED` are reportable).
- `POST /webhooks/equipment/{tenantId}/{systemId}/commands/{id}/status`: `ACKNOWLEDGED`, `EXECUTING`, `COMPLETED`, `FAILED`, with a result or an error. Authenticated by the system's shared secret in `X-Equipment-Key` (constant-time compare); wrong key 401, no secret configured 403, wrong tenant or another system's command 404, repeat of the same status accepted, any move out of a finished state or backwards 409.

A watchdog (`EquipmentWatchdog`, every minute, one pass per tenant, under a scheduler lock) closes the loop:

- A command still `SENT`, `ACKNOWLEDGED` or `EXECUTING` past its `timeoutMs` (default 300 s) is failed with a reason and a `COMMAND_TIMEOUT` alert **[test]**.
- Equipment that has sent a heartbeat before and then goes quiet (default 300 s, `nexus.equipment.heartbeat-timeout-seconds`) is set to `ERROR` with one `CRITICAL` `HEARTBEAT_MISSED` alert; the alert closes itself when it reports again **[live][test]**. Equipment that never reported is not watched, and neither is anything an operator set to `OFFLINE` or `MAINTENANCE`.

Three more behaviours close the remaining gaps **[test]**:

- **Automatic retry, only where it is safe.** A command whose call to the equipment *errored* (it never received the job) is re-sent with backoff of 30 s, 60 s, 120 s and so on up to its `maxRetries`, once each. A command that **timed out** or that the equipment **reported failed** is never retried automatically, because the work may have happened and re-sending could do it twice; those raise an alert for a person.
- **Production never fakes equipment.** `nexus.automation.emulator.enabled` is `true` locally and `false` in the `prod` profile (`NEXUS_AUTOMATION_EMULATOR_ENABLED` overrides). With it off, a command to a system with no endpoint stays `SENT` until the equipment calls back or the watchdog times it out, and a command to a switched-off system fails instead of "completing".
- **Accepted is not done.** If the equipment's reply is `{"async":true}` or a status of `ACCEPTED`, `QUEUED` or `PENDING`, the command stays `ACKNOWLEDGED` and waits for the callback. Previously any 2xx reply marked it `COMPLETED`.

**Still not robotics-ready.** The callback is plain HTTPS JSON, with no industrial protocol (OPC-UA, MQTT,
vendor APIs); the command timeout and the retry were verified by unit tests, not live; and nothing real
has ever called these endpoints. Treat it as the contract a controller integration would build on.

---

## 6.5 Point-of-sale stock (new)

There was no POS support at all. What exists now is the **vendor-neutral contract**, not a vendor
integration **[live][test]**:

1. An operator registers a POS source for a store node: `POST /pos/sources {nodeId, name, vendor}`. The response carries a secret **once**; only its SHA-256 hash is stored. `POST /pos/sources/{id}/rotate-key` replaces it.
2. The store's POS, or middleware in front of it, pushes counts: `POST /webhooks/pos/{tenantId}/{sourceId}/inventory` with header `X-Pos-Key` and body `{"reportedAt": "<ISO-8601 with offset>", "items": [{"sku": "...", "quantity": 12}]}`. Wrong key 401, disabled source 403, more than 5,000 items or a future timestamp 400. Invalid items are listed and the valid ones kept; a report older than the one already held is ignored.
3. `GET /pos/inventory/aggregate?nodeId=` compares the POS count with Nexus on-hand at that node, per SKU: `MATCH`, `DRIFT`, `STALE` (the POS has gone quiet for longer than the source's `staleAfterMinutes`, 120 by default), `NO_POS_DATA`, or `NO_NEXUS_ROW`.
4. `POST /pos/inventory/reconcile {nodeId, skus?, apply}` is a **dry run unless `apply` is true**. It adjusts Nexus on-hand to the POS count for fresh `DRIFT` rows only, writes inventory-ledger lines, and refuses (`BLOCKED`) a correction that would leave on-hand below what is already allocated to orders. Stale, unknown and unreported SKUs are skipped with a reason.

Because Shopify publishing reads node on-hand ([16](./16-SHOPIFY-INVENTORY-PUBLISHING.md)), a reconciled store count flows to the channel on the next publish.

**Screen:** *Store Operations* (`/store-operations`, under Order Management) lets an operator connect a POS to the selected location (the key is displayed once, with a warning), see the till-versus-Nexus table worst difference first with plain-language statuses, preview corrections, and apply them only after a preview. It is covered by component and rule tests and builds; **it has not been looked at in a browser against the live stack.**

**Not built:** an adapter that *pulls* from a specific vendor (Square, Lightspeed, Shopify POS, Clover): each needs that vendor's credentials to verify, so none is claimed. POS *sales* events (decrementing as items sell between snapshots) are not ingested; the contract is periodic full or partial counts. Reconciling assumes the POS count is right; where the till and a cycle count disagree, a person decides.

---

# PART 7 — THE SCORECARD

## 7.1 How the numbers are given

Scores are out of 10 and measure **production readiness (NR)**: what a stranger could verify today by
cloning the repo and running it. They do not reward plans, and they are **not** feature counts.

| Band | Meaning |
|---|---|
| 9–10 | Exercised against the real external system (where one exists), automated tests in CI, monitored, survived a pilot |
| 7–8 | Works end to end in tests **and live** on the local stack; limits documented; not yet exercised externally |
| 5–6 | The main path works; material gaps, or the external side is untested |
| 3–4 | Partly built, or a facade that is honest about being one |
| 1–2 | Largely absent |

**Cap:** a dimension that depends on an external system that has never been exercised cannot exceed 6.

## 7.2 Production readiness by dimension

| Dimension | Score | Evidence | Why not higher |
|---|---|---|---|
| Order lifecycle and state machine | **7.5** | Illegal transitions refused with 409 **[live]**; optimistic locking **[test]**; 39-step lifecycle **[live]**; `OrderStatusPolicy` table **[test]** | Stock consumption covers single-node allocations only; partial and split shipments are treated as whole; no payment or fraud step |
| Inventory integrity | **7.0** | Append-only ledger enforced by a trigger **[live]**; ledger-vs-stock check reconciles every row **[live]**; consume-on-ship, transfers and returns move stock **[test]** | Not lot-level; damaged stock is a field nothing maintains; in-transit and on-order are not ledgered |
| Warehouse operations | **6.0** | Receiving, counting, transfers, pick, pack, ship all pass the live script | Carrier labels are not exercised against a real carrier; no directed putaway; the RF app is not exercised with real scanners |
| Tenant isolation and data security | **8.5** | Row-level security forced on every tenant table, proven on real PostgreSQL **[test]**; tenant-scoped cache keys; integration secrets sealed | A second tenant has not been red-teamed; the vault key falls back to a built-in value when none is set (production validation warns) |
| Authentication and access control | **8.0** | TOTP MFA with recovery codes verified end to end; JWKS ID-token checking; RBAC with deny-by-default **[test]** | Integration screens are admin-only (no other role has the permission group); no SSO tested against a real IdP |
| Integrations | **4.0** | Shopify path is real code with a dry-run, reconciliation and honest errors **[test]**; every simulated connector was removed or made honest | **No integration has been run against a real external system.** The POS push contract exists but no vendor adapter does. Generic connectors cannot write |
| Event backbone | **6.5** | Envelope contract fixed and tested; every topic and DLQ declared; consumer de-duplicates and has one dead-letter path **[test]** | Never run against a real Kafka in this audit; off by default; no alert rules |
| AI | **3.5** | Predict endpoints refuse to fabricate **[live]**; the chat refuses instead of inventing **[live]**; forecasting is a real Holt model **[live]** | Forecast accuracy on real data has not been measured in this audit; no curated dataset or deployed model; chat needs an OpenAI key; the two AI services were not running here |
| Frontend integrity and UX | **5.5** | 99 screens load with no crash, failing call or dead button **[live]**; ~30 fakes removed; type-check ratchet in CI | **140 type errors remain**; each sweep still found new fakes, so more are likely; mobile and accessibility were not audited |
| Quality engineering | **6.5** | 979 backend + 457 frontend tests, none skipped; real-PostgreSQL integration suite; a coverage ratchet; a lifecycle script | The lifecycle script is not in CI; **CI cannot currently run at all** (GitHub Actions billing); no load or soak test |
| Operability and deployment | **4.5** | Compose, Helm, Prometheus, Grafana and Loki configs exist; outbox metrics exist | The staging deploy has **never succeeded** (secrets absent); no backup/restore drill; no alert rules for the new metrics |
| **Overall readiness (mean)** | **6.1 / 10** | | |

**Context.** [12](./12-BENCHMARK-AND-DEVELOPMENT-PLAN.md) recorded an overall readiness of **5.7** at the
start of this engagement. The gain is **+0.4**, deliberately small: the score rewards behaviour that has
been *verified*, and each fix exposed more that had not been. A rise should mostly come from
exercising the system against real Shopify, real carriers and a real deployment, not from more code.
Treat the score as ±0.5.

## 7.3 The other two numbers

| Measure | Value | What it means |
|---|---|---|
| **Feature presence (NP)** | **≈ 7.5** (judgment; `07` self-scores 8.4) | `07` counted several features as present that were facades (marketplace sync, the AI assistant, POS aggregation and store operations were not built). Removing those lowers it; it is an estimate, not a measurement |
| **Market validation** | **1 / 10** | $0 revenue, 0 customers, no analyst coverage, no peer reviews ([19](./19-MARKET-BENCHMARKS.md) §9). This is the row that only real customers can move |

## 7.4 Verdict

**Technically real, commercially unproven, and narrowly pilot-ready.**

*Defensible pilot configuration today:* one tenant, SUITE or WMS edition, one warehouse, orders allocated
from a single location, Shopify as the only channel (after a dry-run on a development store), AI off.

*Not ready:* multi-location split allocation, point-of-sale aggregation, any marketplace other than
Shopify, AI features, unattended production operation.

*What would move the score most:* (1) a real Shopify development-store run, (2) a successful staging
deploy behind HTTPS, (3) the lifecycle script in CI with CI running again, (4) a first pilot customer.

---

# PART 8 — STILL OPEN, AND WHO OWNS IT

## 8.1 Blocked on you (accounts, credentials, decisions)

| Item | Why it blocks |
|---|---|
| **GitHub Actions billing** ("recent account payments have failed") | Every CI job ends in 2 seconds with no steps, so nothing is gated until this is resolved. Everything was verified locally with the identical commands |
| **`STAGING_HOST`, `STAGING_USER`, `STAGING_SSH_KEY`** in the `staging` environment | The deploy step fails with "missing server host"; images build, scan and publish |
| A public **HTTPS** address for the staging server | Shopify only delivers webhooks to HTTPS |
| A **Shopify development store** and app credentials | The only way to verify the Shopify integration; [16](./16-SHOPIFY-INVENTORY-PUBLISHING.md) has the runbook |
| An **OpenAI key**, or a decision to run a local model | The assistant refuses until one exists |
| Whether non-admin roles should manage integrations | No role other than ADMIN holds the `integration` permission group |

## 8.2 Engineering backlog, in the order that moves the score

1. Run `scripts/e2e/lifecycle-smoke.py` in CI against the Compose stack.
2. Vendor POS adapters that pull counts and sales (the push contract exists, see 6.5); each needs vendor credentials.
3. Store-operations screen, distinct from the integration marketplace.
4. Stock consumption for split allocations and partial shipments; lot-level movements.
5. Producers for the shipment, inventory, payment and return topics (their listeners exist); one real controller integration for equipment (and the protocol adapter it needs).
6. Alert rules for `nexus.outbox.failed` and the DLQ depth.
7. Promote UPS, DHL, NetSuite, Walmart and eBay from generic to dedicated connectors; add a connector conformance suite.
8. Burn down the **140** type errors (the ratchet only stops it growing).
9. A grounded assistant: answer from Nexus's own data when no model is configured, labelled as such.
10. Mobile layout and accessibility audit; a load test; a backup and restore drill.

---

## Appendix — Verification Commands

```bash
# ISS-01/04: producer vs consumer contract
grep -rnE '\.publish\(|\.publishEvent\(' nexus-oms-backend/src/main/java --include='*.java'

# ISS-08: DLQ topic coverage
grep -nE 'dlqTopic|topic\(' nexus-oms-backend/src/main/java/com/nexus/oms/config/KafkaTopicConfig.java

# ISS-10: dedicated vs generic connectors
grep -nE 'registerFactory' nexus-oms-backend/src/main/java/com/nexus/oms/integration/core/ConnectorFactoryConfiguration.java

# ISS-02/09: outbox lifecycle + lock window
grep -nE 'MAX_ATTEMPTS|lockAtMostFor|purgeSent|FAILED' nexus-oms-backend/src/main/java/com/nexus/oms/kafka/OutboxRelay.java
```
