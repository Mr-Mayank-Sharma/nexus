# 12 — Verified Benchmark, Improvement List & Development Plan

> **Date:** 2026-10-09 · **Basis:** a full pass over `docs/` plus a code-level audit of
> `nexus-oms-backend`, `nexus-oms-frontend`, `supply_chain_ai*`, `deploy/` and CI, with
> the test suites actually run on this date. Competitor positions were refreshed against
> public 2026 sources (§9).
>
> **Why this document exists.** [`07-SCM-COMPARISON.md`](./07-SCM-COMPARISON.md) scores Nexus
> **8.4/10, tied with Manhattan**. That score rates *feature presence*: whether code for a
> feature exists. Competitor scores rate *shipping, credible and used widely*. This document
> re-scores Nexus on the **same rubric as competitors** (production readiness), shows the
> evidence for every number, and turns the gaps into an ordered plan.

---

## 0. The answer in one screen

| | Value |
|---|---|
| **Nexus score, feature presence (07 doc)** | 8.4 / 10 |
| **Nexus score, production readiness (this doc, same rubric as competitors)** | **5.7 / 10**, ranked 12 of 12 |
| **Gap that matters most** | Security: 3 critical auth/tenancy defects (§3.1) |
| **What Nexus really leads on** | Cost and time to value (9/10), breadth in one codebase, honest `simulated:true` labelling |
| **Projected after the plan (§7)** | 6.1 → 6.5 → 6.9 → **7.1** with pilot customers (≈ NetSuite/Körber tier) |
| **Verified engineering health** | 789/789 backend tests ✅, 403/403 frontend tests ✅, `tsc` 0 errors ✅, build ✅. **Line coverage 31.5%** ⚠️ |

> 🧒 **In plain words:** Nexus has *rooms for everything*, more than most houses. But several
> doors don't lock yet, some rooms are painted cardboard, and nobody has lived in it yet.
> Competitors are scored as lived-in houses, so Nexus has to be scored the same way.

---

## 1. Methodology

1. **Docs read:** 00–11 series, `GAP_CLOSURE_PLAN.md`, `hotwax/*` (gap tickets T-1…T-18,
   routing comparison, honest scorecard), `thoughts/shared/*` (audit, go-live roadmap,
   tenant provisioning design), `DEPLOYMENT_GUIDE.md`.
2. **Code audited:** pattern sweeps across all 63k Java lines, then line-level reads of
   every hit in security, tenancy, caching, routing, carriers, connectors, schedulers,
   Kafka, migrations and AI.
3. **Executed:** `mvn -B test` (with JaCoCo), `vitest run`, `tsc --noEmit`, `vite build`.
4. **Scoring rubric:** identical to doc 07 (0–10 per feature; 8+ = *shipping, credible,
   used widely*), with the same weights: OM 25%, Inventory and Fulfillment 30%,
   Integrations/3PL/EDI/AI 25%, Security/Trust/Cost/Track 20%. Competitor scores are kept
   from doc 07 (dated Aug 2026), with 2026 market facts noted in §2.3.
5. **Not done (yet):** a live load test, because Docker was not running. §6 defines the
   performance benchmarks to capture with the k6 install already on this machine.

---

## 2. Verified engineering benchmarks (measured 2026-10-09)

### 2.1 Codebase

| Metric | Measured |
|---|---|
| Java (backend) | 63,196 lines · 167 entities · 82 controllers · 124 services · 167 repositories · **812 endpoints** |
| TypeScript (frontend) | 64,358 lines · 260 files · 94 page components |
| Python AI | ~1,700 lines in 6 model scripts plus 2 Flask APIs |
| Flyway migrations | 59 (V1–V67) · 182 `CREATE TABLE` · 327 indexes |
| Git | 204 commits on `main` |

### 2.2 Quality gates

| Gate | Result | Verdict |
|---|---|---|
| Backend tests | **789 run, 0 failed, 0 errors, 0 skipped** | ✅ claim confirmed |
| Frontend tests | **403 passed / 31 files** | ✅ claim confirmed |
| TypeScript typecheck | 0 errors | ✅ |
| Production build | OK in 11 s | ✅ |
| **Backend coverage (JaCoCo)** | **line 31.5% · branch 20.2% · instruction 28.6% · method 29.1%** | ⚠️ low |
| Coverage, `integration.connector.ecommerce` (Shopify/Amazon/BC/Magento) | **0.9%** | 🟥 |
| Coverage, `integration.protocol` (REST/GraphQL/EDI/AS2) | 4.2% | 🟥 |
| Coverage, `service.shopify` / `service.bigcommerce` | 10.1% / 9.5% | 🟥 |
| Coverage, `service.ai` | 16.6% | 🟥 |
| Coverage, `security` / `filter` | 77.0% / 73.5% | ✅ |
| Integration tests on real Postgres (Testcontainers) | **none**; tests run on H2 | ⚠️ RLS, JSONB, pgvector untested |
| CI security tooling | CodeQL, OWASP dependency-check, Trivy, npm/pip audit, SonarQube | ✅ strong |

### 2.3 Frontend bundle (gzip)

| Chunk | gzip |
|---|---|
| `index` (app shell) | 83.6 kB |
| `vendor-react` | 54.0 kB |
| `vendor-recharts` | 108.0 kB |
| `ScannerOverlay` (ZXing, lazy) | 118.7 kB |
| CSS | 24.0 kB |

Initial load is about 160 kB gzip JS: acceptable, under a 200 kB budget. The build reports
mixed static/dynamic imports on connector pages (Amazon, Walmart), so their lazy loading
doesn't take effect.

### 2.4 Integration reality

| Category | Count |
|---|---|
| Connector types registered | 41 |
| **Real implementations** | **13**: Shopify, Amazon, BigCommerce, Magento, SAP, Salesforce, FedEx, Stripe, QuickBooks, Twilio, Okta, OpenAI + generic EDI/AS2 |
| **Generic stubs** (`GenericHttpConnector`, base URL `https://docs.example.com/…`) | **28**: eBay, Walmart, Etsy, NetSuite, Dynamics 365, Oracle ERP, HubSpot, UPS, DHL, PayPal, Adyen, Xero, Square, … |
| Shopify in the UI | still marked `coming_soon` (`IntegrationMarketplacePage.tsx:68`) despite a real backend |
| FedEx connector | real code, **no callers** in rate shopping or labels (T-16 still open) |
| Rate shopping | reads seeded `carrier_rates` rows, never a live carrier quote |
| Label default | `labelSource = "SIMULATED"` unless a carrier adapter (Jitsu/Sapi) is configured |

### 2.5 AI reality

| Item | Finding |
|---|---|
| Training data | `supply_chain_ai/data/training_data.py` **generates synthetic data** (`np.random.seed(42)`, 10k rows); model 6 randomises `current_stock` |
| Only stored forecast metric | `metrics.selftest.json`: **WAPE 1.00** (error equal to total demand), MASE 0.50, **1 SKU** |
| Forecasting in Java | Holt smoothing + grid search (sound baseline, not ML) |
| Verdict | The pipeline is real; no model has been trained or validated on real demand |

---

## 3. Findings from the code audit

Severity: 🔴 critical (fix before any customer or demo with real data) · 🟠 high · 🟡 medium.

### 3.1 🔴 Critical: security and tenancy

| # | Finding | Evidence | Impact |
|---|---|---|---|
| **S-1** | **SSO ID tokens are never signature-verified.** `extractEmailFromIdToken` base64-decodes the JWT payload and trusts its `email`; malformed tokens fall back to a random `sso_*` user instead of failing. | `service/AuthService.java:415-435`; endpoint `/auth/sso/{provider}` is `permitAll` | Authentication bypass: a forged token can sign in as an existing user, including admins. |
| **S-2** | **SSO maps users by email local-part.** `username = email.split("@")[0]`, then `findByUsername`. | `AuthService.java:155-156` | `john@a.com` and `john@b.com` resolve to the same account (cross-tenant account merge). |
| **S-3** | **MFA is not real TOTP.** The code is `(username.hashCode() + time/30000) % 1e6`: no per-user secret, no attempt limit. | `AuthService.java:133, 437-440` | MFA is fully predictable from a username, so the "SSO/MFA shipped" claim does not hold. |
| **S-4** | **Cross-tenant cache leaks.** Cache keys without a tenant: `products` `'list'`, `warehouses` `'page:N:S'`, `warehouses` `'all-summary'`. The first tenant's result is served to every tenant for the TTL (products: 10 min). | `ProductService.java:23`, `WarehouseService.java:44, 256` | Tenant A sees tenant B's catalogue and warehouses. |
| **S-5** | **ID lookups without a tenant check, also cached by bare id:** `getOrder(UUID)`, `getWarehouse(UUID)`. | `OrderService.java:154-158`, `WarehouseService.java:49-53` | IDOR. RLS doesn't stop it (S-6), and cache hits skip the DB entirely. |
| **S-6** | **RLS is not an effective backstop.** (a) The app connects as `nexus`, the `POSTGRES_USER` **superuser**, and superusers bypass RLS even with `FORCE`. (b) The policy allows all rows when `app.current_tenant_id IS NULL`. (c) **~68 of 75 tables created after V26** (transfers, brokering, ATP, BOPIS, billing, WES, ASN, RFID, MCP…) have no policy. | `V26__*.sql`, `docker-compose*.yml`, migrations V28–V67 | Tenant isolation depends entirely on every query being written correctly. |
| **S-7** | **Password reset doesn't work and isn't cluster-safe.** Tokens sit in a non-thread-safe `HashMap`, are never emailed, are lost on restart, and don't survive 2 replicas. `forgotPassword` scans **all users** (`findAll()`). | `AuthService.java:58, 174-188` | Broken user flow, plus an O(N) scan on a public endpoint. |
| **S-8** | Webhook HMAC compared with `String.equals` (not constant-time), and `getBytes()` uses the platform charset. | `WebhookSecurityService.java:43, 62` | Timing side-channel; possible signature mismatch on non-ASCII payloads. |
| **S-9** | JWT and refresh tokens stored in `localStorage`. | `LoginPage.tsx:74-76` | Any XSS steals sessions. The CSP `script-src 'self'` reduces but doesn't remove the risk. |
| **S-10** | Insecure defaults in base config: DB password `nexus`, S3 secret `minioadmin`. The file header says "no defaults for secrets". | `application.properties:10, 52` | A prod deployment that forgets the env vars boots with known credentials. `StartupValidator` should refuse. |

### 3.2 🟠 High: correctness and scale

| # | Finding | Evidence | Impact |
|---|---|---|---|
| **C-1** | **14 `@Scheduled` jobs with no distributed lock** (no ShedLock), while Helm ships `replicaCount: 2`. | `config/BrokeringScheduler.java` (every 2 and 5 min), `SyncSchedulerService` (30 s), `BatchJobService` (60 s), reconciliation, pre-order release… | Every job runs on every pod: double brokering passes, double syncs, double pre-order releases. |
| **C-2** | **No transactional outbox.** `KafkaProducerService` sends fire-and-forget inside DB transactions. | `kafka/KafkaProducerService.java:31, 46` | Event lost on Kafka failure, or event published for a rolled-back transaction. |
| **C-3** | **Routing intelligence is hardcoded.** Distance is `50 km` if same state, else `500 km`. Cost is `$5 + 5% of order total × qty`. | `OrderRoutingService.java:750-767` | The "cost/distance-optimal" strategies can't beat a coin toss on real geography. |
| **C-4** | Order and fulfilment statuses are free `String`s, `cancelOrder` is unguarded, `PUT /orders/{id}/status` allows any jump. `WaveService.PICKABLE_STATES` lists `RELEASED`/`APPROVED`, which nothing writes. | doc 11 §State Machines (verified) | Illegal transitions possible; dead states in the logic. |
| **C-5** | 12 `findAll()` then in-memory filter calls on request paths: `ProductService`, `CustomerController.getCustomers` (unpaged), `ReturnFinanceService` ×2, `YardService.generateAppointmentNumber`, `ShopifyInventorySyncService`, `FulfillmentReconciliationService`… | grep `\.findAll()` | O(table) per request across all tenants; appointment numbers can collide under concurrency. |
| **C-6** | Optimistic locking (`@Version`) on 1 entity only (`NxInventory`). Orders, waves, picklists and POs have none. | grep `@Version` | Lost updates on concurrent edits (two pickers, UI + webhook). |
| **C-7** | `AuthResponse.expiresIn = 86400000` while the JWT lives for 900000 ms. | `AuthService.java:402` | Frontend refresh timing is off by 96×. |
| **C-8** | Rate limiter fails open when Redis is down. | `RateLimitingFilter.java:67` | Login brute-force protection disappears during a Redis outage. |

### 3.3 🟡 Medium: product depth

- **Procurement:** only 4 transitions (`DRAFT→PENDING_APPROVAL→APPROVED→SUBMITTED`). No reject, cancel, award or close.
- **Returns finance:** "Placeholder rate model" (`ReturnFinanceService.java:207`).
- **MCP tools:** placeholder per-query cost and placeholder bridge (`McpToolExecutor.java:166, 209`).
- **RFID:** placeholder EPC validation (`RfidIngestionService.java:149`).
- **AI Platform controller:** an endpoint returns `"Not implemented"` (`AiPlatformController.java:251`).
- **WES:** emulator only. Every command result is `simulated:true`.
- **3PL client portal:** read-only.

---

## 4. Competitor benchmark: re-scored on one rubric

Competitor columns are unchanged from doc 07 (public research, Aug 2026). **Nexus (P)** =
feature-presence score from doc 07. **Nexus (R)** = production-readiness score from this
audit. Only R is comparable with competitors.

### 4.1 Order management & omnichannel (25%)

| Feature | Manh. | HotWax | BY | Oracle | SAP | Körber | Softeon | NetSuite | Cin7 | Extensiv | Brightpearl | Nex (P) | **Nex (R)** | Why R |
|---|---|---|---|---|---|---|---|---|---|---|---|---|---|---|
| Multi-channel intake | 10 | 9 | 9 | 8 | 8 | 7 | 6 | 8 | 9 | 7 | 8 | 9 | **6** | 4 real channels, Shopify hidden in UI, eBay/Walmart stubs, connectors 0.9% tested |
| BOPIS | 9 | 10 | 9 | 7 | 6 | 6 | 5 | 6 | 6 | 4 | 6 | 10 | **7** | Full lifecycle; no store hours yet |
| Ship-from-store / endless aisle | 10 | 9 | 9 | 6 | 6 | 5 | 4 | 5 | 6 | 4 | 5 | 8 | **6** | Services exist; tables lack RLS |
| DOM / routing | 10 | 8 | 9 | 8 | 7 | 6 | 6 | 7 | 6 | 5 | 6 | 9 | **5** | C-3 hardcoded distance/cost; no substitutes or brokering groups |
| Lifecycle / holds | 9 | 8 | 9 | 8 | 8 | 7 | 6 | 8 | 7 | 6 | 8 | 9 | **6** | C-4 unguarded strings |
| Returns / RMA | 10 | 6 | 7 | 7 | 7 | 7 | 6 | 7 | 6 | 6 | 7 | 10 | **7** | Placeholder finance rates |
| **Bucket** | 9.3 | 8.3 | 8.7 | 7.3 | 7.0 | 6.3 | 5.5 | 6.8 | 6.7 | 5.3 | 6.7 | 9.0 | **6.2** | |

### 4.2 Inventory & fulfilment (30%)

| Feature | Manh. | HotWax | BY | Oracle | SAP | Körber | Softeon | NetSuite | Cin7 | Extensiv | Brightpearl | Nex (P) | **Nex (R)** | Why R |
|---|---|---|---|---|---|---|---|---|---|---|---|---|---|---|
| Multi-location inventory | 10 | 8 | 9 | 9 | 9 | 8 | 8 | 8 | 8 | 8 | 7 | 9 | **7** | Atomic row-level reserve ✅ |
| ATP / allocation | 9 | 8 | 9 | 8 | 8 | 7 | 6 | 8 | 7 | 6 | 7 | 8 | **7** | Engine real; ATP tables lack RLS |
| Receiving / ASN | 9 | 5 | 8 | 9 | 9 | 9 | 9 | 8 | 6 | 8 | 5 | 9 | **7** | 856→ASN real; no production use |
| Picking / slotting / labor | 10 | 5 | 8 | 9 | 9 | 9 | 9 | 8 | 6 | 8 | 5 | 8 | **6** | Dead wave states (C-4) |
| Packing / kitting | 8 | 5 | 7 | 8 | 9 | 8 | 8 | 7 | 6 | 7 | 5 | 8 | **6** | Box templates real |
| Shipping: rates, labels | 9 | 6 | 8 | 9 | 9 | 8 | 8 | 8 | 7 | 7 | 7 | 8 | **4** | Seeded rates, `SIMULATED` labels, FedEx has no callers |
| Yard / dock | 8 | 2 | 8 | 8 | 9 | 7 | 7 | 5 | 3 | 6 | 2 | 8 | **6** | Real CRUD; appointment-number race |
| WES / automation | 9 | 3 | 8 | 8 | 8 | 9 | 10 | 5 | 4 | 6 | 5 | 8 | **3** | Emulator only |
| Mobile RF | 9 | 4 | 8 | 8 | 8 | 9 | 9 | 9 | 6 | 8 | 5 | 8 | **6** | PWA + offline queue; no DC hours |
| **Bucket** | 9.0 | 5.1 | 8.1 | 8.4 | 8.7 | 8.2 | 8.2 | 7.3 | 5.9 | 7.1 | 5.3 | 8.2 | **5.8** | |

### 4.3 Procurement, finance, 3PL, EDI, integrations, AI (25%)

| Feature | Manh. | HotWax | BY | Oracle | SAP | Körber | Softeon | NetSuite | Cin7 | Extensiv | Brightpearl | Nex (P) | **Nex (R)** | Why R |
|---|---|---|---|---|---|---|---|---|---|---|---|---|---|---|
| Procurement | 7 | 5 | 8 | 9 | 10 | 6 | 5 | 8 | 7 | 5 | 8 | 9 | **6** | 4 transitions, no reject/cancel |
| Finance | 6 | 4 | 7 | 9 | 10 | 5 | 4 | 9 | 7 | 6 | 9 | 9 | **6** | QuickBooks real; AR aging |
| 3PL billing / portal | 8 | 6 | 7 | 8 | 8 | 8 | 8 | 7 | 8 | 10 | 6 | 8 | **6** | Portal read-only |
| EDI | 9 | 5 | 9 | 9 | 9 | 8 | 8 | 9 | 8 | 7 | 6 | 9 | **7** | 850/855/856/810/940/997 + AS2; protocol layer 4% tested |
| Integration hub | 9 | 7 | 8 | 9 | 8 | 8 | 7 | 8 | 9 | 8 | 7 | 9 | **5** | 13 real / 28 stubs |
| AI / ML | 8 | 3 | 9 | 7 | 8 | 6 | 7 | 6 | 7 | 5 | 7 | 8 | **4** | Synthetic data; WAPE 1.0 |
| **Bucket** | 7.8 | 5.0 | 8.0 | 8.5 | 8.8 | 6.8 | 6.5 | 7.8 | 7.7 | 6.8 | 7.2 | 8.5 | **5.7** | |

### 4.4 Security, trust, cost, delivery (20%)

| Feature | Manh. | HotWax | BY | Oracle | SAP | Körber | Softeon | NetSuite | Cin7 | Extensiv | Brightpearl | Nex (P) | **Nex (R)** | Why R |
|---|---|---|---|---|---|---|---|---|---|---|---|---|---|---|
| Security / tenancy / SSO / MFA | 9 | 8 | 9 | 9 | 9 | 8 | 8 | 8 | 7 | 7 | 8 | 9 | **2** | S-1…S-6 |
| Honesty / auditability | 7 | 7 | 7 | 7 | 7 | 7 | 7 | 7 | 6 | 6 | 6 | 10 | **7** | `simulated:true` is honest; self-scores inflated |
| Deployment | 7 | 9 | 6 | 6 | 5 | 7 | 8 | 8 | 8 | 8 | 8 | 8 | **6** | Helm + compose good; C-1, C-2 break multi-replica |
| Cost / time to value | 2 | 9 | 2 | 2 | 2 | 5 | 5 | 6 | 7 | 7 | 7 | 9 | **9** | $0 license, ~1-hour boot ✅ |
| Track record | 10 | 8 | 10 | 10 | 10 | 9 | 8 | 9 | 8 | 8 | 8 | 3 | **2** | No production tenant |
| **Bucket** | 7.0 | 8.2 | 6.8 | 6.8 | 6.6 | 7.2 | 7.2 | 7.6 | 7.2 | 7.2 | 7.4 | 7.8 | **5.2** | |

### 4.5 Weighted ranking (same weights as doc 07)

| Rank | System | OM 25% | Inv 30% | Int 25% | Sec 20% | **Weighted** |
|---|---|---|---|---|---|---|
| 1 | Manhattan Active | 9.3 | 9.0 | 7.8 | 7.0 | **8.4** |
| 2 | Blue Yonder | 8.7 | 8.1 | 8.0 | 6.8 | **8.0** |
| 3 | SAP S/4HANA + EWM | 7.0 | 8.7 | 8.8 | 6.6 | **7.9** |
| 3 | Oracle SCM / WMS | 7.3 | 8.4 | 8.5 | 6.8 | **7.9** |
| 5 | NetSuite WMS | 6.8 | 7.3 | 7.8 | 7.6 | **7.4** |
| 6 | Körber / Infios | 6.3 | 8.2 | 6.8 | 7.2 | **7.2** |
| 7 | Softeon (IFS) | 5.5 | 8.2 | 6.5 | 7.2 | **6.9** |
| 8 | Cin7 Omni | 6.7 | 5.9 | 7.7 | 7.2 | **6.8** |
| 9 | Extensiv 3PL WM | 5.3 | 7.1 | 6.8 | 7.2 | **6.6** |
| 10 | Brightpearl | 6.7 | 5.3 | 7.2 | 7.4 | **6.5** |
| 10 | HotWax | 8.3 | 5.1 | 5.0 | 8.2 | **6.5** |
| **12** | **Nexus (readiness)** | **6.2** | **5.8** | **5.7** | **5.2** | **5.7** |
| — | *Nexus (feature presence, doc 07)* | *9.0* | *8.2* | *8.5* | *7.8* | *8.4* |

**Reading it honestly:** the 2.7-point gap between P and R is the size of the job. Nexus has
built the *surface* of a leader and the *depth* of a newcomer. Most of the gap can be closed by
code (security, real carriers, routing math, tests). The last part, track record, can only
be closed by customers.

### 4.6 Market facts refreshed (2026)

- **Gartner MQ for WMS (published Apr 2026), Leaders:** Manhattan, Blue Yonder, SAP, Oracle,
  Infios (Körber + KKR joint venture, formerly Körber WMS) and Infor. Softeon is a
  **Visionary** and was **acquired by IFS in March 2026**.
- **DOM:** no 2026 Magic Quadrant. Gartner's latest is the *Market Guide for DOM* (June 2025).
  Buyers short-list from that guide plus peer reviews, so a public reference customer
  matters more than an analyst quadrant for Nexus's segment.
- **HotWax routing** (the direct comparison for DOM) supports queue, shipping-method,
  priority and channel filters, rules on order total, SKU, category, facility type,
  operating hours and capacity, store capacity limits, and a split-prevention threshold.
  Nexus's routing lacks hours, the split threshold, real distance and real cost.
- **Price anchors:** Cin7 Core publishes **$349 / $599 / $999 per month** (top tier possibly
  $1.2k since Aug 2026). Extensiv and HotWax are quote-only. Nexus's ~$76–110/month
  self-host TCO (doc 09) is a real, defensible lead.

---

## 5. Improvement list (prioritised)

| ID | Improvement | Fixes | Sev. | Effort | Score lift |
|---|---|---|---|---|---|
| **P0-1** | Verify SSO ID tokens via provider JWKS (`spring-security-oauth2-jose` `NimbusJwtDecoder`), checking `iss`, `aud`, `exp` and `email_verified`. Reject on any failure; remove the random-user fallback. | S-1 | 🔴 | 2 d | Security +2 |
| **P0-2** | Link SSO identities by `(provider, sub)` in a new `nx_user_identities` table. Never by email local-part. No auto-provision into a caller-supplied `tenantId`. | S-2 | 🔴 | 2 d | (in P0-1) |
| **P0-3** | Real RFC 6238 TOTP: per-user secret (encrypted at rest), enrolment QR, ±1 step window, 5-attempt lockout, recovery codes. | S-3 | 🔴 | 3 d | Security +1 |
| **P0-4** | Tenant-prefixed cache keys everywhere (custom `KeyGenerator` that prepends `TenantContext`). Fix the 3 leaking keys. Add a cross-tenant cache regression test. | S-4 | 🔴 | 1 d | Security +1 |
| **P0-5** | Replace `findById` on tenant data with `findByIdAndTenantId`. Add an ArchUnit rule banning bare `findById`/`findAll` in services. | S-5, C-5 | 🔴 | 3 d | Security +1 |
| **P0-6** | RLS hardening: a non-superuser `nexus_app` role (no `BYPASSRLS`), policy without the `IS NULL` open path (a separate `nexus_jobs` role for system jobs), and migration V68 applying RLS + FORCE to every `tenant_id` table. Add a CI check that fails if a `tenant_id` table lacks a policy. | S-6 | 🔴 | 3 d | Security +1 |
| **P0-7** | Password reset: hashed tokens in a DB/Redis table with TTL, email delivery, an indexed `findByEmail`, rate limit. | S-7 | 🔴 | 1.5 d | — |
| **P0-8** | Constant-time HMAC (`MessageDigest.isEqual`), UTF-8 everywhere. | S-8 | 🟠 | 0.5 d | — |
| **P0-9** | `StartupValidator` refuses to start in `prod` with default DB/S3/JWT secrets. | S-10 | 🟠 | 0.5 d | — |
| **P0-10** | Move refresh token to an `HttpOnly; Secure; SameSite=Strict` cookie; keep the access token in memory only. | S-9 | 🟠 | 2 d | — |
| **P1-1** | ShedLock (JDBC provider) on all 14 `@Scheduled` jobs. | C-1 | 🟠 | 1 d | Deploy +1 |
| **P1-2** | Transactional outbox (`nx_outbox` + relay, or Debezium) for all domain events. Consumers made idempotent by event id. | C-2 | 🟠 | 4 d | Deploy +1 |
| **P1-3** | State machines as code: an enum + transition table per entity (Order, Wave, Picklist, Package, PR/RFQ/PO, Return). Guard `cancelOrder`; restrict `PUT /status` to admins with an audit reason. Remove dead `RELEASED`/`APPROVED`. | C-4 | 🟠 | 4 d | Lifecycle +1, Picking +1 |
| **P1-4** | `@Version` on Order, Wave, Picklist, Package, PO, Return, Shipment. Map conflicts to HTTP 409. | C-6 | 🟠 | 1.5 d | — |
| **P1-5** | Testcontainers (Postgres 16 + pgvector, Redis, Kafka) for integration tests. Add a tenant-isolation suite that runs every list/get endpoint as tenant B against tenant A data. | S-4…S-6 | 🟠 | 4 d | Security trust |
| **P1-6** | Coverage gates: JaCoCo `check` at a 50% line floor now, 70% by end of Phase 3. Connector packages need ≥60% via WireMock contract tests. | §2.2 | 🟠 | ongoing | Integration +1 |
| **P1-7** | Fix C-7 (`expiresIn`) and C-8 (fail-closed limiter on `/auth/**`, local Bucket4j fallback). | C-7, C-8 | 🟡 | 0.5 d | — |
| **P2-1** | **Live carriers:** wire `FedExConnector` into `RateShoppingService` + `ShippingLabelService`; add UPS (REST OAuth), USPS and an aggregator (EasyPost or Shippo) so many carriers come through one adapter. Keep seeded rates as a stale-marked fallback. | T-16 | 🟠 | 8 d | Shipping 4→7 |
| **P2-2** | **Routing math:** geocode warehouses + ship-to (cached), haversine distance, carrier zone/rate lookup from P2-1 as cost. Add substitutes, brokering groups, operating hours and split threshold (HotWax parity). | C-3, T-14, T-18 | 🟠 | 10 d | DOM 5→7 |
| **P2-3** | **Connector honesty:** hide the 28 stubs from the marketplace (or badge them "Preview"). Un-hide Shopify. Build real **eBay**, **Walmart** and **NetSuite** connectors (T-15). | §2.4 | 🟠 | 15 d | Intake 6→8, Hub 5→7 |
| **P2-4** | Connector contract tests (WireMock fixtures recorded from sandbox APIs) for the 13 real connectors. | §2.2 | 🟠 | 5 d | — |
| **P2-5** | Finance: replace the placeholder return-rate model with per-location configured rates; add Xero (real). | §3.3 | 🟡 | 4 d | Finance 6→7 |
| **P3-1** | Procurement: reject, cancel, award and close transitions; 3-way match (PO ↔ receipt ↔ invoice). | §3.3 | 🟡 | 5 d | Procurement 6→7 |
| **P3-2** | Picking depth: real wave release states, batch/cluster pick, slotting rules engine using recorded pick velocity. | §4.2 | 🟡 | 10 d | Picking 6→8 |
| **P3-3** | 3PL portal self-serve: ASN submission, document upload, order approvals, invoice payment. | G9 | 🟡 | 8 d | 3PL 6→8 |
| **P3-4** | **AI on real data:** onboard a pilot tenant's 12+ months of history, rolling-origin backtests per SKU, publish only models that beat seasonal-naive (MASE < 1 *and* WAPE < 0.35 on A-class SKUs). Retire synthetic-data models from the product UI. | G3, §2.5 | 🟠 | 10 d | AI 4→6 |
| **P3-5** | Replace placeholders: RFID EPC validation (GS1 SGTIN-96), MCP cost metering, AI-platform "Not implemented" endpoint. | §3.3 | 🟡 | 4 d | — |
| **P3-6** | Frontend: fix mixed static/dynamic connector imports; route-level code splitting check; Lighthouse CI budget (LCP < 2.5 s, JS < 200 kB gzip initial). | §2.3 | 🟡 | 2 d | — |
| **P4-1** | **Pilot programme:** 2–3 design-partner tenants (one Shopify brand, one 3PL). SLOs + status page, uptime history, published case study. | Track record | 🟠 | 8–12 wk | Track 2→5 |
| **P4-2** | Real WES: one real integration (e.g. Locus or a PLC/OPC-UA bridge) behind the existing command model. | G8 | 🟡 | 15 d | WES 3→6 |
| **P4-3** | Docs honesty: re-score doc 07 using this rubric; have `verify_docs.py` fail if a doc claims a score above the readiness score. | Honesty | 🟡 | 1 d | Honesty 7→9 |

---

## 6. Performance benchmarks to capture (targets + method)

Not measured on 2026-10-09 because Docker was down. Run these before Phase 2 ends and
record the results in this section.

| Benchmark | Target (mid-market SLO) | Method |
|---|---|---|
| Order create API (p95) | < 250 ms at 50 rps | k6 against `POST /orders`, seeded 100k orders |
| Order list (p95, paged 50) | < 150 ms at 100 rps | k6 `GET /orders?page=` |
| Routing / allocation per order (p95) | < 300 ms, 3 nodes × 20 lines | k6 + `nx_routing_executions.exec_time_ms` |
| ATP lookup (p95, cached) | < 20 ms | k6 `GET /inventory/atp` |
| Webhook ingest throughput | ≥ 200 orders/s sustained, 0 loss | k6 burst on `/webhooks/shopify` + outbox lag |
| Kafka end-to-end lag (p99) | < 2 s | consumer-lag metric in Prometheus |
| Tenant isolation | 0 cross-tenant rows across every endpoint | P1-5 suite |
| Pick throughput (RF) | ≥ 120 lines/hour/picker at pilot DC | RF task timestamps |
| Forecast accuracy (A-class SKUs) | WAPE < 0.35, MASE < 1 | P3-4 backtest |
| Frontend LCP (4G) | < 2.5 s | Lighthouse CI |
| Availability | 99.5% pilot → 99.9% GA | status page + Alertmanager |

> Add `scripts/perf/*.js` (k6) and a manually triggered `perf` CI job against an ephemeral
> compose stack, so the numbers can be re-measured on demand.

---

## 7. Development plan

Effort assumes **one full-time developer**; with two, phases 2 and 3 can run in parallel.

### Phase 0 — Security emergency (1.5 weeks) · gate: no external demo before this ships

| Week | Items | Exit criteria |
|---|---|---|
| W1 | P0-1, P0-2, P0-4, P0-5, P0-8, P0-9 | Forged/unsigned SSO token returns 401 in tests. Cross-tenant cache test is green. ArchUnit rule in CI. |
| W2 (½) | P0-3, P0-6, P0-7, P0-10 | TOTP passes RFC 6238 test vectors. App runs as non-superuser. Every `tenant_id` table has a FORCE RLS policy (CI check). Reset email delivered in staging. |

**Score after:** Security 2→8, overall **5.7 → ~6.0**.

### Phase 1 — Platform hardening (2.5 weeks)

| Week | Items | Exit criteria |
|---|---|---|
| W3 | P1-1, P1-4, P1-7 | 2-replica run shows each job firing once (ShedLock table). 409 on concurrent edits. |
| W4 | P1-2, P1-3 | Kill-Kafka chaos test: 0 lost events, 0 phantom events. Illegal transitions return 422. |
| W5 (½) | P1-5, P1-6 | Testcontainers suite in CI. Coverage ≥ 50% line, gate enforced. |

**Score after:** Deployment 6→7, Lifecycle 6→7, Honesty 7→8, overall **~6.1**.

### Phase 2 — Make it real: carriers, routing, connectors (6 weeks)

| Weeks | Items | Exit criteria |
|---|---|---|
| W6–7 | P2-1 | Real FedEx/UPS sandbox labels as PDF/ZPL. Live rates with fallback and staleness badge. |
| W8–9 | P2-2 | Routing picks the geographically and cost-optimal node in 10 scripted scenarios, matching HotWax's documented rule types. |
| W10–11 | P2-3, P2-4, P2-5 | Shopify visible. eBay, Walmart and NetSuite sandbox round-trip. Stubs hidden or badged. Connector coverage ≥ 60%. |
| W11 | §6 benchmarks run | Results table filled in; p95 targets met or tickets raised. |

**Score after:** Intake 8, DOM 7, Shipping 7, Hub 7, Finance 7, overall **~6.5**.

### Phase 3 — Depth & AI on real data (6 weeks, overlaps pilot onboarding)

| Weeks | Items | Exit criteria |
|---|---|---|
| W12–13 | P3-1, P3-5, P3-6 | PO reject/cancel/close; 3-way match. No placeholders left (grep is clean). |
| W14–15 | P3-2 | Batch pick and slotting from real velocity; wave states match the code. |
| W16–17 | P3-3, P3-4 | Client self-serve ASN + docs. A forecast model beats seasonal-naive on pilot data and is published with its backtest report. |

**Score after:** Picking 8, RF 7, Procurement 7, 3PL 8, AI 6, ATP 8, BOPIS 8, overall **~6.9**.

### Phase 4 — Pilots & track record (8–12 weeks, starts at W12)

- P4-1: 2–3 design partners live, SLOs published, weekly reliability review.
- P4-2: one real automation integration.
- P4-3: re-score docs 07 and 12 from production evidence; target re-score date **Feb 2027**
  (the 6-month review that doc 07 already commits to).

**Score after:** Track record 2→5, Honesty 9, Deployment 8, RF 8, Ship-from-store 7, overall
**~7.1**: the Körber/Infios and NetSuite tier, and the leader on price within it.

### 7.1 Score trajectory

| Milestone | Week | Weighted (readiness) | Rank among 12 |
|---|---|---|---|
| Today | 0 | **5.7** | 12 |
| Phase 0 done | 1.5 | 6.0 | 12 |
| Phase 1 done | 4 | 6.1 | 12 |
| Phase 2 done | 10 | 6.5 | 10 (= HotWax, Brightpearl) |
| Phase 3 done | 16 | 6.9 | 7 (= Softeon) |
| Pilots live | 20–24 | 7.1 | 7 |
| 12 months of production | — | 7.5+ | 5 (past NetSuite) |

### 7.2 What *not* to build now

- More connector *types*: 28 stubs already dilute the catalogue. Depth over breadth.
- Robotics beyond one reference integration: Softeon and Körber own that space; Nexus wins on OMS + 3PL at its price.
- New AI features before P3-4: no model should appear in the UI without a real backtest.

---

## 8. Positioning after the plan

> *"The open, self-hostable OMS + WMS + 3PL platform for mid-market brands and 3PLs: HotWax-class
> routing, Extensiv-class 3PL billing, honest AI, at a tenth of Cin7's price."*

That claim is defensible once Phase 2 is done. "Tied with Manhattan" isn't, and shouldn't be
used externally.

---

## 9. Sources

**Internal (verified this pass):** files and line numbers cited inline; test logs from `mvn -B test`
and `vitest run` on 2026-10-09; JaCoCo `target/site/jacoco/jacoco.csv`.

**External (2026):**
- [Oracle named a Leader, 2026 Gartner MQ for WMS](https://www.oracle.com/news/announcement/oracle-named-a-leader-in-2026-gartner-magic-quadrant-for-warehouse-management-systems-2026-05-07/)
- [Infor, Leader in 2026 Gartner MQ for WMS](https://www.infor.com/mea/news/infor-named-leader-2026-gartner-magic-quadrant-warehouse-management-systems)
- [FreightWaves: best warehouse management companies (2026 MQ placements)](https://www.freightwaves.com/checkpoint/best-warehouse-management-companies/)
- [Gartner: Market Guide for Distributed Order Management Systems](https://www.gartner.com/en/documents/6659334)
- [Gartner Peer Insights: DOM systems](https://www.gartner.com/reviews/market/distributed-order-management-systems)
- [HotWax docs: order routing parameters](https://docs.hotwax.co/documents/retail-operations/orders/brokering/routing-parameters)
- [HotWax docs: configurable routing](https://docs.hotwax.co/documents/retail-operations/orders/brokering/configurablerouting)
- [G2: HotWax Commerce pricing](https://www.g2.com/products/hotwax-commerce/pricing)
- [CostBench: Cin7 pricing 2026](https://costbench.com/software/inventory-management/cin7/)
- [CostBench: Cin7 pricing change, Aug 2026](https://costbench.com/changelog/cin7-price-increase-2026-08/)
- [ERP Research: Extensiv 3PL Warehouse Manager pricing](https://erpresearch.com/erp-add-ons/wms/extensiv-3pl-warehouse-manager/pricing)

*Companion docs: [07 SCM Comparison](./07-SCM-COMPARISON.md) (feature presence) ·
[01 Current State](./01-CURRENT-STATE.md) · [11 System Design](./11-SYSTEM-DESIGN.md) ·
[hotwax/nexus-market-gap-tickets-3.md](./hotwax/nexus-market-gap-tickets-3.md).*
