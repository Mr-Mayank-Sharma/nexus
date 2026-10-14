# 18 — NEXUS EXPOSED

> **What this is.** A ground-truth system design of Nexus plus every issue found in the
> audit, with the critical event-contract bug fixed in detail and a deep dive into the
> integration layer. Every claim is verified against source at the commit noted below.
>
> **Verified against:** working tree, branch `main` (uncommitted `GlobalExceptionHandler` fix present).
> **Method:** source reads + `grep`/`find` sweeps. Findings marked **[outbox]** were verified in the
> Kafka deep-dive; **[code]** were verified against current source. The 2026-09-05 audit's bugs were
> re-checked and are **fixed** (see §2.5) — they are not carried forward as open.

---

# PART 1 — ARCHITECTURE

## 1.1 What Nexus Is

A **multi-tenant SaaS suite** that ships as three editions — **OMS**, **WMS**, or **SUITE** — with
three paid add-ons (**3PL**, **EDI**, **AI**). Licensing is enforced *in the request path*, not just
in the UI.

| Layer | Technology | Scale |
|---|---|---|
| Backend | Spring Boot 3 / Java 17 | 76 controllers · 79 services · 156 entities · 156 repositories · 70 migrations |
| Frontend | React 18 + Vite + TypeScript | ~95 routed pages · 299 TS/TSX files · RF handheld app |
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

| ID | Severity | Area | Issue |
|---|---|---|---|
| ISS-01 | 🔴 Critical | Kafka | Consumer can't deserialize any published event → all order events dead-lettered |
| ISS-02 | 🔴 High | Kafka | `FAILED` outbox rows never purged, never alerted |
| ISS-03 | 🔴 High | Kafka | Kafka disabled by default; docker-compose never enables it |
| ISS-04 | 🟠 Medium | Kafka | `publishEvent()` (the correct API) is dead code |
| ISS-05 | 🟠 Medium | Kafka | Dynamic `order.{status}` emits up to 16 topics, 11 undeclared |
| ISS-06 | 🟠 Medium | Kafka | 8 consumers with no producer (dead listeners) |
| ISS-07 | 🟠 Medium | Kafka | DLQ: 3 uncoordinated mechanisms; Spring recoverer is a no-op |
| ISS-08 | 🟠 Medium | Kafka | DLQ topics missing for 4 consumed topics |
| ISS-09 | 🟠 Medium | Kafka | ShedLock `lockAtMostFor` (2 min) < worst-case batch (~33 min) |
| ISS-10 | 🟠 Medium | Integration | Connector depth gap: 11 dedicated vs ~28 generic stubs |
| ISS-11 | 🟡 Low | Kafka | Consumer doesn't dedupe despite at-least-once delivery |
| ISS-12 | 🟡 Low | Kafka | Global head-of-line blocking on a poison event |
| ISS-13 | 🟡 Low | Kafka | Replication factor hardcoded to 1 |
| ISS-14 | 🟡 Low | Frontend | `AiAuditTrailPage` is a "not yet available" placeholder |
| ISS-15 | 🟡 Low | Backend | 2 stub markers (webhook, transform) |
| ISS-16 | 🟡 Low | Repo | Uncommitted `GlobalExceptionHandler` 404 fix in working tree |

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
