# Nexus OMS — The 10/10 Prompt

---

```
You are a staff-level full-stack engineer and architect. Your job is to take the Nexus OMS repository (https://github.com/Mr-Mayank-Sharma/nexus) from its current state to a production-ready, market-leading 10/10 Order Management System that beats Manhattan Associates, IBM Sterling, HotWax Commerce, Zoho Inventory, and ShipBob.

The repo is: Spring Boot 3.2.5 (Java 17) backend, React 19 + Vite + TypeScript frontend, PostgreSQL 16, Redis 7, Kafka 3.7.2, Python ML services (scikit-learn), Docker Compose with 9 services.

Work through every phase below IN ORDER. Do not skip phases. Do not skip steps. Commit after each step with a clear message. Run all verifications before moving to the next phase.

═══════════════════════════════════════════════════════════════════════
PHASE 1: MAKE IT COMPILE AND RUN (CRITICAL — NOTHING ELSE MATTERS UNTIL THIS IS DONE)
═══════════════════════════════════════════════════════════════════════

### 1.1 Backend compilation
- Install Maven (or add the Maven Wrapper: `mvn -N wrapper:wrapper`)
- Run `mvn clean compile` in nexus-oms-backend/
- Fix EVERY compilation error:
  - Missing imports
  - Wrong method signatures
  - Type mismatches
  - Missing dependencies in pom.xml
  - Broken Spring annotations
  - Missing bean definitions
  - Circular dependencies
- Run `mvn clean package -DskipTests` — must produce a runnable JAR
- Run `mvn clean package` (with tests) — note which tests pass/fail
- Document every error found and the fix applied in a CHANGES.md file

### 1.2 Frontend verification
- Run `npm install` in nexus-oms-frontend/
- Run `npx tsc --noEmit` — fix any type errors
- Run `npm run build` — must succeed
- Run `npm test` — note results
- Fix any broken imports, missing components, type errors

### 1.3 Docker Compose boot
- Run `docker compose build` — all 9 services must build
- Run `docker compose up` — all 9 services must start without crashing
- Verify:
  - PostgreSQL accepts connections on 5433
  - Redis responds to ping
  - Kafka broker is reachable
  - Backend starts on 8080 and `/actuator/health` returns 200
  - Frontend loads on port 80
  - AI services start on their ports
  - Prometheus scrapes the backend
  - Grafana loads on 3001
- Fix any startup failures, port conflicts, missing configs

### 1.4 End-to-end smoke test
- Create a tenant via API
- Create a user with ADMIN role
- Log in, get JWT
- Create an order
- View it in the frontend
- If any of this fails, fix it

═══════════════════════════════════════════════════════════════════════
PHASE 2: SECURITY HARDENING (CRITICAL)
═══════════════════════════════════════════════════════════════════════

### 2.1 Remove all hardcoded secrets
In docker-compose.yml:
- Change `${JWT_SECRET:-NexusShipOMSSuperSecretKeyForJWTTokenGeneration2024!}` to `${JWT_SECRET:?ERROR: JWT_SECRET is required. Set it in .env}`
- Change `${DB_PASSWORD:-nexus_secret}` to `${DB_PASSWORD:?ERROR: DB_PASSWORD is required. Set it in .env}`
- Change Grafana `GF_SECURITY_ADMIN_PASSWORD=nexus_admin_2026` to `GF_SECURITY_ADMIN_PASSWORD=${GRAFANA_ADMIN_PASSWORD:?ERROR: GRAFANA_ADMIN_PASSWORD is required}`
- Search the ENTIRE codebase for any other hardcoded secrets, API keys, passwords. Remove all of them.
- Add a startup check in the backend: if JWT_SECRET is missing or shorter than 32 bytes, refuse to start with a clear error message.

### 2.2 PostgreSQL Row-Level Security
- Create Flyway migration V26__enable_row_level_security.sql:
  - Enable RLS on every table that has a tenant_id column
  - Create a policy for each table: `CREATE POLICY tenant_isolation ON nx_<table> USING (tenant_id = current_setting('app.current_tenant_id', true)::uuid);`
  - Force the tenant ID to be set: `ALTER TABLE nx_<table> FORCE ROW LEVEL SECURITY;`
- In the backend:
  - Create a `TenantConnectionInterceptor` (Hibernate `StatementInspector` or Spring aspect) that executes `SET LOCAL app.current_tenant_id = '<uuid>'` at the start of every transaction, reading from `TenantContext`
  - Create a `TenantContextFilter` that extracts tenant ID from JWT claims and sets it in `TenantContext`
  - Add a test: insert a row as tenant A, query as tenant B, verify zero rows returned (both via app AND via direct SQL)
- Create a `TenantIsolationIntegrationTest` that:
  - Creates two tenants
  - Creates orders for both
  - Verifies tenant A cannot see tenant B's orders through any API endpoint
  - Verifies tenant A cannot see tenant B's orders through direct repository queries

### 2.3 Webhook security
- Audit every webhook endpoint (Shopify, BigCommerce, carrier webhooks)
- Every webhook MUST verify HMAC-SHA256 signatures:
  - Shopify: verify `X-Shopify-Hmac-Sha256` header using the app secret
  - BigCommerce: verify `X-Bc-Webhook-Signature` header
  - Carrier webhooks: verify per-carrier signature scheme
- Reject any webhook with an invalid signature with 401 Unauthorized
- Log all webhook attempts (valid and invalid) for audit
- Add replay attack protection: store processed webhook event IDs in Redis with 24h TTL, reject duplicates

### 2.4 Rate limiting enhancement
- The existing `RateLimitingFilter` needs to be per-tenant, not just per-IP
- Add tenant-aware rate limiting: each tenant gets configurable limits
- Add endpoint-specific limits: auth endpoints ( stricter), import endpoints (burst limits), API endpoints (standard)
- Return proper 429 responses with `Retry-After` header and `X-RateLimit-*` headers

### 2.5 Input validation
- Add `@Valid` annotations on ALL request body DTOs
- Add `@NotBlank`, `@NotNull`, `@Size`, `@Pattern`, `@Email`, `@Positive` constraints
- Add a global `@ControllerAdvice` exception handler that returns proper 400 errors with field-level validation messages
- Sanitize all string inputs to prevent SQL injection (parameterized queries should already handle this, but audit every native query)
- Add XSS protection: sanitize all user-provided strings before storing and before returning to frontend

### 2.6 CORS hardening
- Remove the wildcard CORS configuration
- Only allow origins from the configured `APP_CORS_ALLOWED_ORIGINS`
- Add `X-Content-Type-Options: nosniff`, `X-Frame-Options: DENY`, `Strict-Transport-Security` headers
- Add a `SecurityHeadersFilter` that sets all security headers

### 2.7 JWT improvements
- Shorten access token TTL to 15 minutes
- Add refresh token with 7-day TTL, stored in Redis (revocable)
- Add token rotation: each refresh generates a new refresh token, invalidating the old one
- Add `jti` (JWT ID) claim and track used tokens to prevent replay
- Add logout endpoint that blacklists the current token in Redis until expiry

═══════════════════════════════════════════════════════════════════════
PHASE 3: TEST COVERAGE (CRITICAL FOR 10/10)
═══════════════════════════════════════════════════════════════════════

### 3.1 Backend unit tests (target: 200+ tests)
Write comprehensive unit tests for every service. Minimum coverage:

**Order Service (20 tests):**
- createOrder: valid order, invalid tenant, missing required fields, duplicate order number
- confirmOrder: valid, already confirmed, cancelled order
- allocateOrder: sufficient inventory, insufficient inventory, partial allocation
- splitOrder: valid split, invalid split ratio, split on shipped order
- mergeOrders: valid merge, different tenants, different statuses
- cancelOrder: valid cancel, already shipped, partial cancel
- getOrder: exists, not found, cross-tenant access denied

**Inventory Service (15 tests):**
- getATP: correct computation, with allocations, with reservations, with holds
- adjustInventory: positive adjustment, negative adjustment, below zero prevented
- lowStockCheck: triggers at threshold, doesn't trigger above threshold
- cycleCount: create, execute, reconcile, variance detection
- transferInventory: valid transfer, insufficient stock, cross-warehouse

**Shipment Service (12 tests):**
- createShipment, trackShipment, voidShipment
- generateLabel: valid carrier, invalid carrier, API failure
- rateShop: single carrier, multiple carriers, best rate selection
- shipmentKPIs: on-time, late, in-transit counts

**Routing Service (10 tests):**
- allocate: nearest node, lowest cost, fastest
- simulate: valid simulation, invalid inputs
- reallocate: valid, already shipped

**Auth Service (15 tests):**
- login: valid credentials, invalid password, non-existent user, locked account
- MFA: valid code, invalid code, expired code
- token refresh: valid refresh, expired refresh, revoked refresh
- SSO: valid SSO callback, invalid SSO, user mapping
- logout: token blacklisted

**Import Token Service (10 tests):**
- generateToken: valid generation, correct scope, correct TTL
- validateToken: valid token, expired token, wrong scope, invalid signature, replay attack
- token format: HMAC-SHA256, constant-time comparison

**Permission Service (12 tests):**
- hasPermission: ADMIN wildcard, specific role+resource+action, denied
- permission caching: cache hit, cache miss, cache invalidation
- role hierarchy: proper inheritance
- VIEWER: read-only enforcement

**Carrier Service (10 tests):**
- rateRequest: valid, invalid address, API timeout
- labelGeneration: valid, invalid weight, invalid dimensions
- tracking: valid, not found, delivered
- voidShipment: valid, already delivered

**Returns Service (8 tests):**
- createRMA, approveRMA, rejectRMA, receiveReturn, inspectReturn, processRefund

**Procurement Service (10 tests):**
- createPO, approvePO, receivePO, closePO
- createRFQ, submitQuote, acceptQuote, rejectQuote

### 3.2 Backend integration tests (target: 50+ tests)
- Multi-tenant isolation tests (every endpoint tested with two tenants)
- Full order lifecycle: create → confirm → allocate → pick → pack → ship → deliver
- Full return lifecycle: RMA → approve → receive → inspect → refund
- Full procurement lifecycle: RFQ → quote → PO → receive → close
- Import flow: generate token → upload CSV → verify data → check audit log
- Webhook flow: simulate Shopify webhook → verify order created
- Kafka event flow: create order → verify event published → verify consumer processed

### 3.3 Frontend tests (target: 50+ tests)
- Component tests for EnterpriseDataGrid, EnterpriseKPICard, PermissionGate
- Page tests for OrdersPage, InventoryPage, DashboardPage, ShipmentPage
- Auth flow: login → redirect → logout
- Permission gating: ADMIN sees all, VIEWER sees limited, PICKER sees only picklist
- API integration: mock backend, verify all API calls are correct
- Import flow: token generation → file upload → success/error display

### 3.4 Test infrastructure
- Add JaCoCo plugin for coverage reporting
- Configure 80% minimum coverage gate (line + branch) for backend
- Add frontend coverage with vitest/coverage
- Add `mvn verify` to run all tests + integration tests
- CI must fail if coverage drops below threshold

═══════════════════════════════════════════════════════════════════════
PHASE 4: PRODUCTION READINESS (HIGH PRIORITY)
═══════════════════════════════════════════════════════════════════════

### 4.1 Distributed permission cache
- Replace in-memory permission cache with Redis-backed Spring Cache
- Cache key: `perm:{tenantId}:{role}:{resource}:{action}`
- TTL: 60 seconds
- Add cache invalidation via Redis pub/sub: when permissions change, publish invalidation event to all instances
- All instances subscribe and clear their local caches on invalidation event

### 4.2 Circuit breakers for all external calls
- Add Resilience4j to pom.xml
- Apply @CircuitBreaker to:
  - All carrier API calls (FedEx, UPS, DHL, USPS, EasyPost)
  - All e-commerce platform API calls (Shopify, BigCommerce)
  - All AI service calls
  - All ERP connector calls
- Configuration: failure rate 50%, wait 30s, ring buffer 20 calls, timeout 3s
- Fallback methods: return cached data, return error response, queue for retry
- Add @Retry for transient failures: 3 attempts, exponential backoff

### 4.3 Carrier rate caching
- Create `RateCacheService` backed by Redis
- Cache key: SHA-256(origin_zip + dest_zip + weight + dims + carrier_id + service_type)
- TTL: 15 minutes (configurable per carrier)
- Check cache before calling carrier API
- Store full rate response as JSON
- Add cache statistics endpoint: hit rate, miss rate, size
- Target: 60%+ hit rate in steady state

### 4.4 Idempotency for all mutating endpoints
- Create `IdempotencyFilter` that checks `Idempotency-Key` header
- Store in Redis: `idem:{sha256(key)}` → response body, TTL 24h
- If key exists, return cached response (same status code + body)
- Apply to: POST /orders, POST /shipments, POST /returns, POST /import/*, POST /procurement/*
- If no Idempotency-Key header is provided on order creation, generate one from request hash

### 4.5 Kafka event system
- Create Kafka topics:
  - `nexus.order.events` (created, confirmed, allocated, picked, packed, shipped, delivered, cancelled, returned)
  - `nexus.inventory.events` (adjusted, transferred, low-stock, cycle-count-completed)
  - `nexus.shipment.events` (created, labeled, dispatched, in-transit, delivered, voided)
  - `nexus.integration.events` (sync-started, sync-completed, sync-failed, webhook-received)
  - `nexus.audit.events` (entity-created, entity-updated, entity-deleted, permission-changed)
- Create producers in each service that emit events on state changes
- Create consumers:
  - Analytics consumer: aggregates events into real-time KPIs
  - Notification consumer: sends alerts (low stock, shipment delays, order exceptions)
  - Audit consumer: persists audit trail
  - Search consumer: updates search index
- Use Avro or JSON schemas for event payloads
- Add Dead Letter Queue for failed event processing
- Add Kafka health check to actuator

### 4.6 S3-compatible object storage for labels
- Add AWS SDK (or MinIO client for self-hosted) to pom.xml
- Create `StorageService` interface with `upload`, `download`, `getPresignedUrl`, `delete`
- Ship all shipping labels to S3/MinIO, not local disk or DB
- Generate presigned URLs with 24h TTL for label download
- Add S3/MinIO service to docker-compose.yml for local development
- Migrate any existing label storage to S3

### 4.7 Backend health checks
- Add Spring Boot Actuator with health, info, metrics, prometheus endpoints
- Custom health indicators:
  - Database health (can connect + run query)
  - Redis health (can connect + ping)
  - Kafka health (can connect + describe topics)
  - AI services health (can reach both AI service ports)
  - Carrier API health (can reach each carrier's sandbox)
- Add health check to docker-compose.yml for backend service
- Make `depends_on` use `condition: service_healthy` for backend-dependent services

### 4.8 Proper logging
- Add structured JSON logging (Logback with JSON encoder)
- Add correlation ID (MDC) propagated through every request, including Kafka messages
- Add request/response logging for all API calls (with PII redaction)
- Add log aggregation config for ELK or Loki
- Configure log levels via environment variables
- Add audit log for all sensitive operations (permission changes, data exports, config changes)

### 4.9 Configuration management
- Expand .env.template with ALL required variables (see full list in analysis doc)
- Add Spring Cloud Config or just externalize all config to env vars
- Add config validation on startup: if any required config is missing, fail with clear error
- Add profile support: dev, staging, prod with different defaults
- Add secrets management: never log secrets, never return them in API responses

═══════════════════════════════════════════════════════════════════════
PHASE 5: API AND DOCUMENTATION (HIGH PRIORITY)
═══════════════════════════════════════════════════════════════════════

### 5.1 OpenAPI 3.1 specification
- Add springdoc-openapi dependency
- Generate spec for all 37 controllers
- Add @Operation, @ApiResponse, @Schema annotations to every controller method
- Add @Tag for controller grouping
- Export spec to /v3/api-docs and swagger-ui
- Protect swagger-ui in production (require ADMIN role or disable)
- Add examples for every request/response
- Add error response schemas for 400, 401, 403, 404, 409, 429, 500

### 5.2 API versioning
- Move all endpoints under `/api/v1/`
- Add Spring Boot API versioning (header or path-based)
- Update all 38 frontend API client modules to use `/api/v1/` prefix
- Add deprecation headers for any old paths

### 5.3 API documentation page
- Create a frontend page at /docs that renders the OpenAPI spec
- Add interactive "Try it out" functionality
- Add code examples in curl, JavaScript, Python, Java

### 5.4 Postman collection
- Generate a Postman collection from the OpenAPI spec
- Include environment variables for base URL, JWT token, tenant ID
- Include example requests for every endpoint
- Add to repo under /docs/postman/

═══════════════════════════════════════════════════════════════════════
PHASE 6: CLOSE COMPETITOR GAPS (HIGH PRIORITY — THIS IS WHERE 10/10 IS EARNED)
═══════════════════════════════════════════════════════════════════════

### 6.1 Carrier expansion (beat ShipBob + Manhattan)
Current: 4 carriers (FedEx, UPS, DHL, USPS) + EasyPost
Target: 15+ direct carriers + EasyPost + ShipStation as aggregators

Add direct connectors for:
- **Canada Post** — OAuth 2.0, REST API
- **Australia Post** — API Key, REST API
- **Royal Mail** — OAuth 2.0, REST API
- **Deutsche Post DHL (Germany domestic)** — API Key, REST API
- **FedEx Ground/Freight** — separate LTL API
- **Old Dominion (LTL)** — API Key, REST API
- **XPO Logistics (LTL)** — OAuth 2.0, REST API
- **Saia (LTL)** — API Key, REST API
- **OnTrac** — API Key, REST API
- **LSO** — API Key, REST API
- **UDS** — API Key, REST API
- **International carriers**: TNT, Aramex, China Post, Japan Post

Each connector must implement:
- `getRates(shipmentRequest)` → RateQuote[]
- `createLabel(shipmentRequest)` → LabelResponse
- `trackShipment(trackingNumber)` → TrackingResponse
- `voidShipment(shipmentId)` → VoidResponse
- `validateAddress(address)` → AddressValidationResponse

Add a carrier registry: admin can enable/disable carriers per tenant, set carrier priorities, configure markup/discounts.

### 6.2 E-commerce platform expansion (beat HotWax + Zoho)
Current: Shopify, BigCommerce
Target: 10+ platforms

Add connectors for:
- **Amazon Seller Central** — SP-API, OAuth 2.0
  - Order import, product sync, inventory sync, shipment confirmation, refund sync
- **Walmart Marketplace** — API Key, REST API
  - Order import, product sync, inventory sync, shipment confirmation
- **eBay** — OAuth 2.0, REST API
  - Order import, product sync, inventory sync
- **Magento 2 (Adobe Commerce)** — OAuth 1.0a, REST API
  - Full bi-directional sync
- **WooCommerce** — API Key (WWA), REST API
  - Full bi-directional sync
- **Etsy** — OAuth 2.0, REST API
  - Order import, product sync
- **Squarespace Commerce** — API Key, REST API
  - Order import
- **Salesforce Commerce Cloud (B2C)** — OAuth 2.0 (Open Commerce API)
  - Full bi-directional sync

Each connector must implement:
- `syncOrders()` — pull new orders since last sync
- `syncProducts()` — push product catalog updates
- `syncInventory()` — push inventory levels
- `pushShipment(orderId, trackingNumber, carrier)` — confirm fulfillment
- `syncRefunds()` — pull refund notifications
- `registerWebhooks()` — register for real-time notifications
- `handleWebhook(payload, signature)` — process incoming webhook
- Full webhook signature verification per platform spec

### 6.3 ERP connector implementation (beat Manhattan + IBM Sterling)
Current: SAP, Oracle listed as framework entries only
Target: 4 fully functional ERP connectors

**SAP S/4HANA Connector:**
- Auth: OAuth 2.0 (SAP Cloud Platform) or Basic Auth (on-premise)
- Order sync: SAP sales orders ↔ Nexus orders (BAPI_SALESORDER_CREATEFROMDAT2 or OData API_SALES_ORDER)
- Inventory sync: SAP material stock ↔ Nexus inventory (API_MATERIAL_STOCK)
- shipment sync: SAP outbound delivery ↔ Nexus shipment (API_OUTBOUND_DELIVERY_SRV)
- IDoc support: DESADV (dispatch advice), SHPMNT (shipment)
- Real-time: OData service consumption
- Batch: IDoc flat file exchange

**Oracle Fusion / EBS Connector:**
- Auth: OAuth 2.0 (Fusion Cloud) / SOAP session token (EBS 12.2.x)
- Order sync: Oracle Order Management Cloud REST API
- Inventory sync: Oracle Inventory Management Cloud
- Shipment sync: Oracle Shipping Execution (WSH)
- SOAP gateway for EBS on-premise

**NetSuite Connector:**
- Auth: OAuth 2.0 (NetSuite TBA) or Token-Based Authentication
- REST/SOAP API access
- Order sync, inventory sync, fulfillment sync
- SuiteScript webhook integration

**Microsoft Dynamics 365 Connector:**
- Auth: OAuth 2.0 (Azure AD)
- OData v4 API (D365 Supply Chain Management)
- Sales order sync, inventory sync, shipment sync
- Data Entity access for custom integrations

Each ERP connector must implement:
- `syncOrders(direction)` — bi-directional order sync
- `syncInventory(direction)` — bi-directional inventory sync
- `syncShipments(direction)` — bi-directional shipment sync
- `syncMasterData()` — sync products, customers, suppliers
- Error handling with retry queue
- Field mapping configuration (admin can map custom fields)
- Sync status dashboard with last sync time, record count, errors

### 6.4 Mobile app for warehouse staff (beat HotWax BOPIS)
Build a React Native app for pickers, packers, and BOPIS staff:

**Picker features:**
- View assigned picklists
- Scan barcode to confirm pick
- Mark item as not found / damaged
- Complete picklist
- View next picklist (auto-assigned by AI routing)

**Packer features:**
- View assigned packages
- Scan items to verify contents
- Print shipping label (via Bluetooth printer or cloud print)
- Confirm package sealed and ready
- Photo capture for proof of pack

**BOPIS staff features:**
- View pending BOPIS orders
- Mark order ready for pickup
- Scan customer QR code to verify pickup
- Process in-store returns
- View store inventory levels

**Loader features:**
- Scan packages to load onto truck
- Confirm carrier pickup
- View loading manifest

**App infrastructure:**
- React Native (Expo) for iOS + Android
- Same JWT auth as web app
- Offline support: queue scans when offline, sync when reconnected
- Push notifications for new assignments
- Barcode/QR scanning via camera
- Bluetooth label printer support

### 6.5 Advanced WMS features (beat Manhattan WMS)
Current: WMS-Lite
Target: Full WMS capabilities

- **Bin/location management**: hierarchical warehouse layout (warehouse → zone → aisle → rack → shelf → bin)
- **Put-away logic**: AI-recommended put-away locations based on velocity, size, weight, compatibility
- **Pick path optimization**: Traveling Salesman Problem solver for optimal pick sequence
- **Wave picking**: group orders into waves for batch picking
- **Cluster picking**: pick multiple orders simultaneously with cart
- **Zone picking**: assign pickers to zones, pass tote between zones
- **Receiving**: ASN matching, quality inspection, put-away
- **Cross-docking**: bypass storage for urgent orders
- **Cycle counting**: ABC analysis-based count frequency, zero-count freeze, variance investigation
- **Lot/serial tracking**: full lot and serial number traceability
- **Expiration date tracking**: FEFO (First Expired First Out) picking
- **Hazardous materials handling**: hazmat flags, segregation rules
- **Temperature control**: cold chain tracking for pharma/food
- **Kitting/assembly**: build kits from components, track component consumption

### 6.6 Transportation Management (beat Manhattan TMS)
- **Freight rate management**: LTL and TL rate negotiation, rate sheets, fuel surcharge calculation
- **Freight tendering**: tender loads to carriers, accept/reject, re-tender
- **Load planning**: consolidate shipments into loads, optimize load sequence
- **Route optimization**: multi-stop route planning with traffic awareness
- **Freight bill audit**: match invoices to rated shipments, flag discrepancies
- **Carrier scorecard**: on-time pickup, on-time delivery, damage rate, cost per mile
- **Proof of delivery**: electronic POD capture, image upload
- **Freight claims management**: file, track, and resolve freight claims

### 6.7 Distributed Order Management (beat IBM Sterling DOM)
- **Node inventory aggregation**: real-time inventory across all nodes (warehouses, stores, 3PLs)
- **Smart routing rules engine**:
  - Proximity: ship from nearest node to customer
  - Inventory: ship from node with most stock
  - Cost: ship from node with lowest landed cost
  - Speed: ship from node with fastest delivery promise
  - Capacity: respect node capacity limits
  - Custom rules: tenant-configurable rules
- **What-if simulation**: simulate routing changes and see impact before applying
- **Order split optimization**: when to split across nodes vs single node
- **Store fulfillment (SFS)**: enable stores as fulfillment nodes with inventory reservations
- **BOPIS**: online order → store pickup with real-time store inventory
- **Endless aisle**: ship-from-store when warehouse is OOS
- **Pre-order management**: backorder with promise date, auto-convert when inventory arrives
- **Order exception management**: flag and resolve stuck orders, partial shipments, address issues

### 6.8 Advanced analytics and reporting (beat Manhattan Active Inventory)
- **Real-time dashboards**:
  - Order velocity (orders/hour by channel)
  - Fulfillment SLA tracking (on-time % by carrier, by node)
  - Inventory turnover by SKU, by category, by node
  - Gross margin by order, by channel, by SKU
  - Carrier performance scorecard
  - Return rate by SKU, by reason, by channel
  - Warehouse productivity (picks/hour, packs/hour, accuracy %)
- **Historical reporting**:
  - Custom report builder (select metrics, dimensions, filters, date range)
  - Scheduled reports (email/Slack delivery)
  - Export to CSV, Excel, PDF
- **Predictive analytics**:
  - Demand forecast (per SKU, per node, per week)
  - Inventory optimization recommendations (reorder points, safety stock)
  - Carrier performance prediction
  - Return probability per order
- **Custom KPI engine**: admin can define custom KPIs with formula builder

### 6.9 Customer portal (beat Zoho customer self-service)
- **Order tracking**: real-time status, tracking link, estimated delivery
- **Order history**: full history with filters and search
- **Return initiation**: self-service return requests with reason codes
- **Invoice download**: PDF invoices, statements
- **Address management**: saved addresses, default shipping address
- **Saved payment methods**: tokenized cards (PCI-DSS compliant via Stripe/Braintree)
- **Communication**: order updates via email, SMS, push notification
- **Wishlist/favorites**: save items for re-order
- **Reorder**: one-click reorder from history

### 6.10 EDI expansion (beat IBM Sterling EDI)
Current: X12 parsing
Target: Full EDI VAN + direct trading partner connections

- **Inbound EDI**: 850 (Purchase Order), 852 (Product Activity), 859 (Application Advice), 861 (Receiving Advice), 862 (Shipping Schedule), 846 (Inventory Inquiry)
- **Outbound EDI**: 810 (Invoice), 855 (Purchase Order Ack), 856 (ASN/Advance Ship Notice), 812 (Credit/Debit Adjustment), 870 (Order Status Report)
- **EDI VAN connectivity**: connect to major VANs (SPS Commerce, TrueCommerce, Cleo, IBM Sterling)
- **Trading partner management**: manage partner EDI IDs, partner-specific maps, test/production flags
- **EDI translation maps**: configurable maps between EDI segments and internal data model
- **EDI validation**: syntactic validation, partner-specific requirements, GS1 compliance
- **EDI acknowledgment**: generate 997 (Functional Acknowledgment) for all inbound, process inbound 997s
- **AS2 connectivity**: support AS2 for direct partner connections
- **Batch processing**: scheduled batch pickup and delivery
- **HIPAA compliance** (if healthcare customers): 837, 835, 270/271, 276/277, 278

═══════════════════════════════════════════════════════════════════════
PHASE 7: AI/ML UPGRADE (THE DIFFERENTIATOR)
═══════════════════════════════════════════════════════════════════════

### 7.1 Upgrade from scikit-learn to enterprise-grade ML
Current: 6 scikit-learn models
Target: 9 production ML models with proper MLOps

**Model 1: Order Routing Optimizer (XGBoost Ranker)**
- Replace current scikit-learn model with XGBoost Ranker
- Features: distance, inventory level, node capacity, historical fulfillment time, carrier cost, carrier on-time rate, order priority, order value
- Pairwise ranking: for each (order, node) pair, predict fulfillment quality score
- Retrain weekly with last 90 days of fulfillment data
- Shadow mode: run new model alongside old model for 7 days before promotion
- Fallback: if model is unavailable, use rules-based routing (distance → inventory → cost)

**Model 2: Demand Forecaster (Temporal Fusion Transformer)**
- Replace current scikit-learn model with TFT (PyTorch or TensorFlow)
- Multi-horizon forecasting: 1 day, 7 days, 14 days, 30 days, 90 days
- Features: historical sales, seasonality, holidays, promotions, price changes, weather, economic indicators
- Per-SKU, per-node forecasts
- Prediction intervals (P10, P50, P90)
- Retrain daily with incremental learning
- Drift detection: if actuals deviate >20% from forecast for 3 consecutive days, alert

**Model 3: Fraud Detection (Isolation Forest + XGBoost)**
- Anomaly detection on order patterns: unusual order value, unusual shipping address, unusual payment method, velocity anomalies
- Ensemble: Isolation Forest for anomaly detection + XGBoost for fraud classification
- Real-time scoring: <100ms per order
- Human-in-the-loop: high-risk orders flagged for manual review
- Feedback loop: human decisions fed back into retraining

**Model 4: Carrier Performance Predictor (Random Forest + survival analysis)**
- Predict on-time delivery probability per carrier per shipment
- Features: carrier, service level, origin, destination, package specs, day of week, season, weather forecast
- Kaplan-Meier survival analysis for delivery time distribution
- Used in rate shopping: recommend carrier with best on-time probability for price

**Model 5: Return Predictor (BERT + structured features)**
- Text analysis of order comments, return reasons, customer reviews (BERT fine-tuned)
- Structured features: product category, price, size category, historical return rate, customer return history
- Predict return probability at order time
- Preventive action: high return risk → offer size guide, add care instructions, suggest alternative

**Model 6: Inventory Optimizer (Tabular Neural Network)**
- Compute optimal reorder points and safety stock per SKU per node
- Features: demand forecast, demand variability, lead time, lead time variability, service level target, holding cost, stockout cost
- Multi-objective optimization: minimize total cost while hitting service level target
- What-if analysis: show impact of changing service level target

**Model 7: Box Optimizer (3D bin packing with ML guidance)**
- Given items (dimensions, weight, fragility), select optimal box from available box sizes
- 3D bin packing algorithm (guillotine or shelf-based) with ML-priority guidance
- Minimize: dimensional weight, number of packages, void space
- Output: box selection, item arrangement, packing instructions

**Model 8: Pick/Pack Time Estimator (Gradient Boosted Trees)**
- Predict pick time per picklist (travel time + pick time + verification time)
- Predict pack time per package (item count + complexity + label time)
- Features: picklist size, item locations, warehouse layout, picker experience, item characteristics
- Used for: labor planning, SLA estimation, wave scheduling

**Model 9: Dynamic Pricing Engine (Reinforcement Learning)**
- Real-time price optimization for shipping services
- Features: demand, capacity, competitor pricing, historical acceptance rate, margin target
- Bandit algorithm: explore vs exploit pricing
- A/B testing framework: test pricing strategies on subset of orders

### 7.2 MLOps infrastructure
- **Feature store**: implement Feast or Tecton for feature management
  - Online features (Redis): real-time serving
  - Offline features (S3/Parquet): training
  - Feature versioning and lineage
- **Model registry**: MLflow or custom
  - Model versioning, stage transitions (staging → production)
  - Model artifacts storage
  - Model cards (documentation, training data, metrics, limitations)
- **Training pipeline**:
  - Automated retraining schedules (daily for demand, weekly for routing)
  - Data validation before training
  - Hyperparameter tuning (Optuna or Ray Tune)
  - Distributed training for large models
  - Training metrics logging and alerting
- **Inference pipeline**:
  - Model serving via FastAPI (Python) with batching
  - A/B testing: route X% of traffic to new model
  - Shadow deployment: run new model in parallel, compare results
  - Canary deployment: gradually increase traffic to new model
  - Automatic rollback: if metrics degrade >5%, revert to previous model
- **Monitoring**:
  - Prediction drift detection (KS test on score distributions)
  - Feature drift detection
  - Latency monitoring (P50, P95, P99)
  - Model accuracy monitoring (when ground truth available)
  - Alerting on drift, latency, accuracy degradation

### 7.3 LLM-powered features
- **AI Assistant (already started — enhance it)**:
  - Natural language query: "Show me all orders from Amazon that are delayed"
  - Natural language report: "Generate a report of carrier performance last month"
  - Anomaly explanation: "Why was this order flagged as high return risk?"
  - Recommendations: "What should I reorder this week?"
  - Conversation memory: remember context within a session
- **Smart order parsing**:
  - Parse unstructured email orders (already started — enhance accuracy)
  - Parse PDF purchase orders from customers
  - Parse EDI-like text from legacy systems
  - Extract: customer, items, quantities, prices, shipping address, payment terms
- **Intelligent routing explanations**:
  - When AI routes an order, generate a human-readable explanation
  - "This order was routed to the Dallas DC because it has 500 units in stock, is 15 miles from the customer, and FedEx Ground offers 2-day delivery at $8.50"
- **Return reason analysis**:
  - Categorize free-text return reasons into structured categories
  - Identify trending return issues (e.g., "runs small" spike for a product)
  - Alert merchandising team when return reason rate exceeds threshold

═══════════════════════════════════════════════════════════════════════
PHASE 8: SCALABILITY AND PERFORMANCE
═══════════════════════════════════════════════════════════════════════

### 8.1 Database optimization
- Add indexes on all frequently queried columns (tenant_id + status, tenant_id + created_at, tenant_id + order_number)
- Add composite indexes for common query patterns
- Add partial indexes for status-based queries (e.g., WHERE status = 'PENDING')
- Implement read replicas for reporting queries
- Add query performance monitoring (slow query log > 100ms)
- Add connection pooling (HikariCP — configure properly: max pool size, min idle, timeout)
- Partition large tables by date (orders, shipments, audit_logs) for archival
- Add Citus or TimescaleDB for horizontal scaling of high-volume tables

### 8.2 Caching strategy
- Redis caching layers:
  - L1: Hibernate L2 cache (entity cache) for reference data (products, customers, carriers)
  - L2: Application cache for computed data (ATP, rate quotes, KPIs)
  - L3: HTTP response cache for static API responses (carrier list, warehouse list)
- Cache invalidation strategies:
  - TTL-based for time-sensitive data
  - Event-based for entity changes (Kafka event → cache eviction)
  - Manual invalidation for admin-triggered changes

### 8.3 Async processing
- Move all non-critical-path work to async:
  - Audit logging → async via Kafka
  - Notification sending → async via message queue
  - Analytics aggregation → async via scheduled job
  - Report generation → async with email delivery
  - Carrier rate shopping → parallel calls via CompletableFuture
- Add a job queue (Redis or Kafka-based) for background tasks
- Add job status tracking and retry logic

### 8.4 Frontend performance
- Code splitting: lazy-load all 39+ pages
- Bundle optimization: tree-shaking, minification, compression
- Image optimization: WebP, lazy loading, responsive sizes
- API response caching: React Query or SWR with stale-while-revalidate
- Virtual scrolling for large lists (orders, inventory items)
- Debounced search inputs
- Prefetch likely-next pages on hover

### 8.5 Horizontal scaling
- Make the backend stateless: move all session state to Redis
- Add Spring Session with Redis backing
- Configure load balancing (nginx or AWS ALB)
- Add sticky sessions for WebSocket connections (or use Redis pub/sub for WebSocket fan-out)
- Add auto-scaling configuration (HPA for Kubernetes, or document AWS Auto Scaling setup)
- Add graceful shutdown: drain connections on SIGTERM

═══════════════════════════════════════════════════════════════════════
PHASE 9: CI/CD AND DEPLOYMENT
═══════════════════════════════════════════════════════════════════════

### 9.1 GitHub Actions CI pipeline
Create `.github/workflows/ci.yml`:
- Trigger: push to main, pull request
- Jobs:
  - backend-compile: `mvn clean compile` (JDK 17)
  - backend-test: `mvn test` with PostgreSQL and Redis services
  - backend-coverage: run JaCoCo, check 80% minimum
  - frontend-build: `npm ci && npm run build`
  - frontend-typecheck: `npx tsc --noEmit`
  - frontend-test: `npm test` with coverage
  - frontend-lint: `npm run lint`
  - docker-build: build all Docker images (don't push)
  - security-scan: run Trivy on Docker images, run npm audit, run mvn dependency-check
- Fail the pipeline if any job fails
- Cache Maven and npm dependencies between runs

### 9.2 GitHub Actions CD pipeline
Create `.github/workflows/deploy.yml`:
- Trigger: push to main (after CI passes) or manual dispatch
- Build and push Docker images to registry (GitHub Container Registry or Docker Hub)
- Tag images: `latest` for main, `v<version>` for tags
- Deploy to staging automatically
- Deploy to production on manual approval (environment protection rule)
- Run smoke tests after deployment
- Rollback on smoke test failure

### 9.3 Kubernetes manifests
Create `/deploy/kubernetes/` with:
- Namespace definition
- Deployments for: backend (3 replicas), frontend (2 replicas), ai-ops (2 replicas), ai-intel (2 replicas)
- StatefulSets for: PostgreSQL (1 replica with PVC), Redis (1 replica with PVC), Kafka (3 replicas)
- Services for all deployments
- Ingress with TLS termination
- HorizontalPodAutoscalers for backend and AI services
- ConfigMaps and Secrets (externalized)
- PodDisruptionBudgets for HA
- NetworkPolicies for service-to-service security
- readiness and liveness probes for all services

### 9.4 Infrastructure as Code
Create `/deploy/terraform/` with:
- VPC, subnets, security groups
- RDS PostgreSQL (Multi-AZ)
- ElastiCache Redis (Multi-AZ)
- MSK Kafka (3 broker)
- EKS cluster with node groups
- S3 buckets for labels, exports, backups
- IAM roles and policies
- Route53 DNS
- CloudWatch alarms

### 9.5 Monitoring and alerting
- Prometheus: scrape all services with custom metrics
- Grafana dashboards:
  - System overview (request rate, error rate, latency P50/P95/P99)
  - Business metrics (orders/hour, fulfillment rate, inventory value)
  - Infrastructure (CPU, memory, disk, network)
  - Kafka (lag, throughput, partition health)
  - Database (connections, slow queries, replication lag)
  - AI services (model latency, prediction distribution, drift)
- Alert rules:
  - Error rate > 1% → PagerDuty
  - P95 latency > 500ms → Slack
  - Kafka consumer lag > 1000 → Slack
  - Database connections > 80% → PagerDuty
  - Disk usage > 80% → PagerDuty
  - Model drift detected → Slack
- Distributed tracing with Jaeger or Zipkin

═══════════════════════════════════════════════════════════════════════
PHASE 10: COMPLIANCE AND CERTIFICATIONS
═══════════════════════════════════════════════════════════════════════

### 10.1 SOC 2 Type II readiness
- Access controls: RBAC (done), MFA (done), session management, password policies
- Audit logging: every data access, every config change, every permission change (partially done — complete it)
- Data encryption: at rest (AES-256 via PostgreSQL TDE or disk encryption), in transit (TLS 1.3)
- Key management: AWS KMS or HashiCorp Vault for encryption keys
- Incident response: create incident response plan, alerting, post-mortem templates
- Change management: all changes via PR, approval required, deployment audit trail
- Vendor management: document all third-party services, their security posture
- Data retention: configurable retention policies per data type, automated deletion
- Breach notification: automated detection and notification pipeline

### 10.2 GDPR compliance
- Data subject rights:
  - Right to access: export all personal data for a customer
  - Right to erasure: delete all personal data (with order retention for legal compliance)
  - Right to rectification: customer can update their data
  - Right to portability: export in machine-readable format
  - Right to object: opt-out of processing
- Consent management: track consent for data processing, marketing
- Data Processing Agreement template for customers
- EU data residency option: deploy in eu-west-1, no cross-region replication
- Privacy by design: minimize data collection, pseudonymize where possible
- Data Protection Impact Assessment template

### 10.3 CCPA compliance
- "Do Not Sell My Personal Information" mechanism
- Consumer rights: know, delete, opt-out
- Privacy policy updates
- Data inventory and mapping

### 10.4 PCI DSS (for payment processing)
- Never store full credit card numbers (tokenize via Stripe/Braintree)
- SAQ-A scope (redirect to hosted payment page)
- Quarterly security scans
- Vulnerability management program

### 10.5 HIPAA (optional — for healthcare customers)
- BAA (Business Associate Agreement) support
- PHI encryption at rest and in transit
- Audit logging for all PHI access
- Breach notification within 60 days
- Secure data disposal

═══════════════════════════════════════════════════════════════════════
PHASE 11: INTEGRATIONS ECOSYSTEM
═══════════════════════════════════════════════════════════════════════

### 11.1 Payment integrations
- Stripe: payment processing, tokenized cards, refunds, disputes
- PayPal: checkout, refunds
- Braintree: payment processing
- Adyen: multi-currency, global payment processing
- QuickBooks Online: invoice sync, payment sync
- Xero: invoice sync, payment sync
- NetSuite (financials): AR sync, invoice sync

### 11.2 Communication integrations
- Twilio: SMS notifications (order shipped, delivery, exceptions)
- SendGrid: transactional email (order confirmations, shipping notifications, returns)
- Mailchimp: marketing email sync (customer segments)
- Slack: internal notifications (low stock alerts, order exceptions, daily summaries)
- Microsoft Teams: internal notifications (same as Slack)
- WhatsApp Business: customer notifications (international markets)

### 11.3 Identity integrations
- Okta: SSO via SAML 2.0 / OIDC
- Azure AD: SSO via OIDC
- Google Workspace: SSO via OIDC
- Auth0: SSO via OIDC
- OneLogin: SSO via SAML 2.0

### 11.4 Marketing/CRM integrations
- Salesforce CRM: customer sync, order sync
- HubSpot: customer sync, deal sync
- Klaviyo: customer segment sync, event streaming
- Segment: event streaming for analytics

### 11.5 Data/analytics integrations
- Snowflake: data warehouse export
- Google BigQuery: data warehouse export
- Amazon Redshift: data warehouse export
- Looker: BI connector
- Tableau: BI connector

### 11.6 Integration framework
Build a universal integration framework so any new integration can be added with minimal code:
- `Connector` interface with standard methods: `authenticate()`, `testConnection()`, `sync()`, `handleWebhook()`
- Connector registry: dynamic registration, enable/disable per tenant
- Connection management: per-tenant credential storage (encrypted)
- Sync engine: scheduled syncs, on-demand syncs, webhook-triggered syncs
- Error handling: retry queue, dead letter queue, alert on repeated failures
- Sync monitoring: dashboard showing last sync time, records processed, errors
- Field mapping: configurable mapping between external and internal data models
- Webhook management: register, verify, process, replay webhooks

═══════════════════════════════════════════════════════════════════════
PHASE 12: UX POLISH (MAKE IT BEAUTIFUL)
═══════════════════════════════════════════════════════════════════════

### 12.1 Design system
- Create a formal design system documentation
- Define color palette, typography, spacing, shadows, borders
- Create reusable component library with Storybook
- Ensure WCAG 2.1 AA accessibility compliance
- Add keyboard navigation for all interactive elements
- Add screen reader support (ARIA labels, live regions)
- Add dark mode and light mode parity (both fully functional)
- Add responsive design: desktop, tablet, mobile

### 12.2 Dashboard improvements
- Customizable dashboard: drag-and-drop widgets, save layouts per user
- Real-time updates: WebSocket for live order/inventory updates
- Global search: search across orders, products, customers, shipments with fuzzy matching
- Quick actions: keyboard shortcuts for common actions (N = new order, S = search, / = focus search)
- Notification center: in-app notifications for alerts, mentions, assignments
- Onboarding wizard: first-time user setup, role-based walkthroughs
- Empty states: helpful empty state messages with CTAs
- Loading states: skeleton screens, progress indicators
- Error states: helpful error messages with recovery actions

### 12.3 Bulk operations
- Bulk order actions: select multiple orders → cancel, merge, assign to wave, export
- Bulk inventory adjustments: CSV upload for mass adjustments
- Bulk product updates: CSV upload for price/attribute changes
- Bulk shipping: select orders → batch generate labels → batch print

### 12.4 Data export
- Export any list view to CSV/Excel
- Export filtered and sorted data
- Scheduled exports (daily/weekly/monthly) emailed to recipients
- Custom export builder: select fields, format, delivery method

═══════════════════════════════════════════════════════════════════════
PHASE 13: DOCUMENTATION ALIGNMENT
═══════════════════════════════════════════════════════════════════════

### 13.1 Update blueprint to match reality
- Add "Implementation Status" badges to every section: ✅ Implemented, 🚧 In Progress, 📋 Planned
- Change Java 21 → Java 17 (or upgrade to 21 and document)
- Change React 18 → React 19
- Remove or mark as "Roadmap Q3 2026" any technology not in the current stack
- Add a "Current Architecture" section that accurately describes what's built
- Add a "Migration Path" section showing how to get from current to target architecture

### 13.2 Create developer documentation
- Architecture overview with diagrams (Mermaid or PlantUML)
- Development setup guide (step-by-step for new developers)
- API reference (auto-generated from OpenAPI)
- Database schema documentation (auto-generated from Flyway migrations)
- Deployment guide (Docker Compose for dev, Kubernetes for prod)
- Contributing guide (code style, PR process, testing requirements)
- ADR (Architecture Decision Records) for major decisions

### 13.3 Create user documentation
- Admin guide: tenant setup, user management, role configuration, carrier setup, integration setup
- Operations guide: order management, inventory management, shipment processing, returns
- Warehouse guide: receiving, put-away, picking, packing, loading, cycle counting
- Integration guide: connecting Shopify, BigCommerce, Amazon, SAP, etc.
- API guide: authentication, pagination, error handling, rate limits, webhooks

### 13.4 Create a demo
- Seed data script: creates a demo tenant with realistic data (1000 orders, 500 products, 5 warehouses, 10 carriers)
- Demo walkthrough script: step-by-step demo showing the full order lifecycle
- Screen recording of the demo
- Live demo deployment (on a free tier — Railway, Render, or Fly.io)

═══════════════════════════════════════════════════════════════════════
PHASE 14: FINAL VERIFICATION (THE 10/10 CHECKLIST)
═══════════════════════════════════════════════════════════════════════

Run through this checklist. Every item must pass:

### Build
- [ ] `mvn clean package` produces a JAR with 0 errors
- [ ] `npm run build` produces a production bundle with 0 errors
- [ ] `npx tsc --noEmit` passes with 0 errors
- [ ] `npm run lint` passes with 0 errors
- [ ] `docker compose build` builds all services
- [ ] `docker compose up` starts all services without errors
- [ ] All services pass health checks within 60 seconds

### Tests
- [ ] `mvn test` runs 200+ tests, all passing
- [ ] `npm test` runs 50+ tests, all passing
- [ ] Backend line coverage ≥ 80%
- [ ] Frontend line coverage ≥ 70%
- [ ] Multi-tenant isolation tests pass (no cross-tenant data access via API or DB)
- [ ] End-to-end smoke test passes (create tenant → login → create order → ship → deliver)

### Security
- [ ] No hardcoded secrets anywhere in the codebase (grep -ri "password\|secret\|key" --exclude-dir=node_modules)
- [ ] JWT secret is ≥ 256 bits and loaded from env
- [ ] DB password loaded from env, no defaults
- [ ] PostgreSQL RLS enabled on all multi-tenant tables
- [ ] All webhook endpoints verify HMAC signatures
- [ ] All API endpoints require authentication (except login and health)
- [ ] CORS is restricted to configured origins only
- [ ] Rate limiting is per-tenant and per-endpoint
- [ ] All inputs validated (no unvalidated request bodies)
- [ ] Security headers set on all responses
- [ ] OWASP Top 10 audit passes (run OWASP ZAP)

### Production Readiness
- [ ] Permission cache is Redis-backed with pub/sub invalidation
- [ ] Circuit breakers on all external API calls
- [ ] Carrier rate caching with 60%+ hit rate
- [ ] Idempotency keys on all mutating endpoints
- [ ] Kafka events flowing for all critical state changes
- [ ] Shipping labels stored in S3/MinIO, not local disk
- [ ] Backend health check in docker-compose
- [ ] Structured JSON logging with correlation IDs
- [ ] All required env vars in .env.template
- [ ] Config validation on startup

### API
- [ ] OpenAPI 3.1 spec generated and accessible
- [ ] All endpoints under /api/v1/
- [ ] Swagger UI available (protected in prod)
- [ ] Postman collection generated
- [ ] API documentation page in frontend

### Competitor Features
- [ ] 15+ carrier connectors functional
- [ ] 10+ e-commerce platform connectors functional
- [ ] 4 ERP connectors functional (SAP, Oracle, NetSuite, D365)
- [ ] React Native mobile app for warehouse staff
- [ ] Full WMS features (bins, put-away, pick optimization, cycle counting, lot tracking)
- [ ] TMS features (freight rate, tendering, load planning, route optimization)
- [ ] Advanced DOM (multi-node inventory, smart routing, SFS, BOPIS)
- [ ] Real-time analytics dashboards
- [ ] Customer self-service portal
- [ ] Full EDI (inbound + outbound, VAN, AS2, trading partner management)

### AI/ML
- [ ] 9 ML models deployed with proper MLOps
- [ ] Feature store (Feast or equivalent)
- [ ] Model registry (MLflow or equivalent)
- [ ] Automated retraining pipelines
- [ ] A/B testing and canary deployment for models
- [ ] Drift detection and alerting
- [ ] LLM-powered AI assistant with natural language queries
- [ ] Smart order parsing (email, PDF, text)
- [ ] Routing explanations in human-readable text

### Scalability
- [ ] Backend is stateless (all session state in Redis)
- [ ] Database has proper indexes on all query paths
- [ ] Read replicas configured for reporting
- [ ] Connection pooling properly configured
- [ ] Frontend uses code splitting and lazy loading
- [ ] API response caching implemented
- [ ] Async processing for all non-critical-path work
- [ ] Horizontal scaling documented and tested

### CI/CD
- [ ] GitHub Actions CI runs on every PR
- [ ] CI includes: compile, test, coverage gate, lint, security scan
- [ ] GitHub Actions CD deploys to staging on merge
- [ ] Production deployment requires manual approval
- [ ] Docker images tagged and pushed to registry
- [ ] Kubernetes manifests in repo
- [ ] Terraform infrastructure as code

### Compliance
- [ ] SOC 2 controls implemented and documented
- [ ] GDPR data subject rights functional
- [ ] CCPA opt-out mechanism functional
- [ ] PCI DSS scope minimized (no card data stored)
- [ ] Data retention policies configurable
- [ ] Audit trail complete for all sensitive operations

### Documentation
- [ ] Blueprint updated with implementation status badges
- [ ] Developer documentation complete (setup, architecture, API, schema)
- [ ] User documentation complete (admin, ops, warehouse, integration guides)
- [ ] ADRs written for major architectural decisions
- [ ] Demo seed data and walkthrough script
- [ ] Live demo deployed and accessible

### Monitoring
- [ ] Prometheus scraping all services
- [ ] Grafana dashboards for system, business, infrastructure
- [ ] Alert rules configured with PagerDuty/Slack routing
- [ ] Distributed tracing with Jaeger/Zipkin
- [ ] Error tracking (Sentry or equivalent)
- [ ] Uptime monitoring (external probe)

═══════════════════════════════════════════════════════════════════════
EXECUTION INSTRUCTIONS
═══════════════════════════════════════════════════════════════════════

1. Work through phases 1-14 IN ORDER. Do not skip.
2. After each step, commit with format: `phase<#>: <step> - <description>`
3. After each phase, run the verification checklist for that phase
4. If a step reveals issues in a previous phase, go back and fix them
5. Update CHANGES.md after every phase with what was done
6. After all phases, run the Phase 14 final verification checklist
7. If any checklist item fails, fix it before declaring done
8. The project is 10/10 only when EVERY checklist item passes

IMPORTANT NOTES:
- This is a real production system. No shortcuts. No mocks in production code. No TODO comments left unimplemented.
- Every feature must have tests. Every test must pass. Every endpoint must be documented.
- Security is not optional. Every vulnerability in the analysis must be fixed.
- The blueprint describes the target. Your job is to make the code match the blueprint.
- If something in the blueprint is impractical, document why and implement the best practical alternative.
- Do not break existing functionality. If you must change an API, add the new version alongside the old.
- Optimize for correctness first, then performance, then elegance.
```
