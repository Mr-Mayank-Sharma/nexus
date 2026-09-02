# Nexus OMS — Complete Platform Guide

**The single source of truth for setting up, connecting, deploying, and operating the Nexus Order Management System (OMS) & Supply Chain Platform in production.**

> **Version:** 1.0.0 · **Last updated:** 2026-09-02
> **Stack:** Spring Boot 3 (Java 17) · React 19 (Vite + TypeScript) · PostgreSQL 16 (pgvector) · Redis 7 · Apache Kafka 3.7 (KRaft) · Flyway

---

## Table of Contents

1. [Platform Overview](#1-platform-overview)
2. [Architecture](#2-architecture)
3. [Repository Layout](#3-repository-layout)
4. [Prerequisites](#4-prerequisites)
5. [Quick Start (Local Development)](#5-quick-start-local-development)
6. [Environment Configuration](#6-environment-configuration)
7. [Database & Migrations](#7-database--migrations)
8. [Connecting Services](#8-connecting-services)
9. [Building the Application](#9-building-the-application)
10. [Production Deployment](#10-production-deployment)
11. [Kubernetes / Helm Deployment](#11-kubernetes--helm-deployment)
12. [Cloud Deployment (Render)](#12-cloud-deployment-render)
13. [Monitoring & Observability](#13-monitoring--observability)
14. [Backup & Disaster Recovery](#14-backup--disaster-recovery)
15. [Security](#15-security)
16. [API Reference](#16-api-reference)
17. [AI / ML Services](#17-ai--ml-services)
18. [Operations & Troubleshooting](#18-operations--troubleshooting)
19. [Schema Validation (ddl-auto=validate)](#19-schema-validation)
20. [FAQ](#20-faq)

---

## 1. Platform Overview

Nexus is a **full-stack Order Management System (OMS) and supply chain platform** covering:

- **Order Management** — order lifecycle, allocation, fulfillment
- **Warehouse Management** — inventory, picklists, packages, dock appointments, yard management
- **Carrier Rate Shopping** — FedEx, UPS, USPS, DHL rate comparison
- **Returns Management** — RMA, return items, financial reconciliation
- **B2B Portal** — customer-facing ordering
- **EDI Automation** — X12 document ingestion (850, 856, 810)
- **Generic File Import Engine** — CSV, JSON, XML, XLSX, EDI
- **RFID / Serialized Inventory** — item-level tracking
- **AI / ML** — demand forecasting, endless aisle, intelligent order routing
- **MCP Server Tools** — AI agent integration
- **Bidirectional Sync** — Shopify, BigCommerce connectors

### Key Design Principles

- **Schema-first:** `spring.jpa.hibernate.ddl-auto=validate` in production — the database schema is the source of truth, and Flyway migrations are the only way it changes.
- **Tenant isolation:** Row-Level Security (RLS) on all tenant-scoped tables.
- **Resilience:** Resilience4j circuit breakers and retries on all external calls.
- **Security:** JWT auth, MFA, SSO (Google/Microsoft/Okta/Auth0), security headers, no anonymous signups in prod.

---

## 2. Architecture

```
                        ┌─────────────────────────────────────────────┐
                        │              Frontend (React SPA)           │
                        │         Nginx :80/:443 → /api → backend     │
                        └──────────────────────┬──────────────────────┘
                                               │ HTTPS /api/v1
                        ┌──────────────────────▼──────────────────────┐
                        │            Backend (Spring Boot)            │
                        │  Java 17 · :8080 · context-path /api/v1     │
                        │  JWT Auth · RBAC · RLS · Resilience4j       │
                        └───┬──────────┬──────────┬──────────┬────────┘
                            │          │          │          │
                    ┌───────▼──┐ ┌─────▼────┐ ┌───▼────┐ ┌───▼─────────┐
                    │PostgreSQL│ │  Redis   │ │ Kafka  │ │  AI / ML    │
                    │ 16+pgvec │ │  7 cache │ │ 3.7    │ │ Python APIs │
                    │  :5432   │ │  :6379   │ │ :9092  │ │ :5000/:5001 │
                    └──────────┘ └──────────┘ └────────┘ └─────────────┘
```

### Service Matrix

| Service | Image / Runtime | Port | Purpose |
|---------|----------------|------|---------|
| **postgres** | `pgvector/pgvector:pg16` | 5432 (host 5433 in dev) | Primary DB + vector embeddings |
| **redis** | `redis:7-alpine` | 6379 | Cache, sessions, rate limiting |
| **kafka** | `apache/kafka:3.7.2` (KRaft) | 9092 | Event streaming, order lifecycle |
| **backend** | Spring Boot fat JAR | 8080 | REST API (`/api/v1`) |
| **frontend** | Nginx serving React build | 80/443 | SPA + PWA |
| **ai-ops** | Python (FastAPI) | 5000 | Demand forecasting / ops AI |
| **ai-intel** | Python (FastAPI) | 5001 | Business intelligence AI |

---

## 3. Repository Layout

```
nexus/
├── nexus-oms-backend/          # Spring Boot 3 + Java 17 backend
│   ├── src/main/java/com/nexus/oms/
│   │   ├── controller/         # REST controllers
│   │   ├── service/            # Business logic
│   │   ├── repository/         # Spring Data JPA repositories
│   │   ├── entity/             # JPA entities
│   │   ├── dto/                # Request/response DTOs
│   │   ├── security/           # JWT auth, tenant context
│   │   └── config/             # App configuration
│   ├── src/main/resources/
│   │   ├── application.properties      # Base config (env-var driven)
│   │   ├── application-prod.properties # Prod overrides
│   │   ├── application-dev.properties  # Dev overrides
│   │   ├── application-ai.yml          # AI service config
│   │   └── db/migration/               # Flyway migrations (V1–V67)
│   └── target/oms-1.0.0.jar   # Compiled fat JAR
├── nexus-oms-frontend/         # React 19 + Vite + TypeScript
│   ├── src/pages/              # Page components
│   ├── src/components/         # Reusable UI components
│   ├── src/api/                # API client modules
│   └── nginx.conf              # Nginx reverse proxy config
├── supply_chain_ai/            # Python ML pipeline (ops AI)
├── supply_chain_ai2/           # Additional AI modules (intel AI)
├── scripts/                    # Automation & setup scripts
├── deploy/
│   ├── docker-compose.prod.yml # Production compose
│   ├── helm/nexus/             # Kubernetes Helm chart
│   ├── grafana/                # Grafana dashboards + datasources
│   ├── prometheus/             # Prometheus config
│   └── local/                  # macOS launchd plists
├── docs/                       # Documentation & blueprints
├── docker-compose.yml          # Dev compose (infra only)
├── Dockerfile                  # Backend container
├── render.yaml                 # Render.com cloud config
└── .env.template               # Environment variable template
```

---

## 4. Prerequisites

| Tool | Version | Notes |
|------|---------|-------|
| **Java** | 17 | `JAVA_HOME` must point to JDK 17 |
| **Maven** | 3.9+ | Backend build |
| **Node.js** | 18+ | Frontend build |
| **Docker** | 24+ | Infra + prod containers |
| **Docker Compose** | v2 | Orchestration |
| **PostgreSQL** | 16 (or 14) | With **pgvector** extension |
| **Redis** | 7 | Cache/sessions |
| **Kafka** | 3.x | KRaft mode |

> **pgvector note:** The `pgvector/pgvector:pg16` Docker image bundles the extension. For a **native** Postgres install, you must install pgvector matching your exact Postgres major version (see §7).

---

## 5. Quick Start (Local Development)

### Option A — Full Docker (easiest)

```bash
# 1. Configure environment
cp .env.template .env
# Edit .env — set DB_PASSWORD, JWT_SECRET (required)

# 2. Start infrastructure (Postgres, Redis, Kafka)
docker compose up -d postgres redis kafka

# 3. Start backend (from repo root)
cd nexus-oms-backend
mvn clean package -DskipTests
java -jar target/oms-1.0.0.jar \
  --spring.profiles.active=dev \
  --server.port=8080

# 4. Start frontend (separate terminal)
cd nexus-oms-frontend
npm install
npm run dev   # serves on :3000 (Vite)
```

### Option B — Native services (macOS Homebrew)

```bash
# PostgreSQL 14 + pgvector (see §7 for pgvector install)
brew services start postgresql@14
createdb -h localhost -p 5432 -U nexus nexus_oms

# Redis
brew services start redis

# Kafka (KRaft) — or use docker compose up -d kafka
docker compose up -d kafka

# Backend
cd nexus-oms-backend
mvn clean package -DskipTests
NEXUS_JWT_SECRET="<32+ char secret>" \
NEXUS_DB_URL="jdbc:postgresql://localhost:5432/nexus_oms" \
NEXUS_DB_USERNAME="nexus" \
NEXUS_DB_PASSWORD="nexus" \
NEXUS_DDL_AUTO="validate" \
NEXUS_KAFKA_ENABLED="false" \
NEXUS_KAFKA_BOOTSTRAP="localhost:9092" \
NEXUS_REDIS_HEALTH_ENABLED="false" \
NEXUS_CACHE_ENABLED="false" \
java -jar target/oms-1.0.0.jar --server.port=8080
```

### Verify It's Running

```bash
# Backend health (Spring Boot Actuator)
curl http://localhost:8080/api/v1/actuator/health
# → {"status":"UP", ...}

# Frontend
open http://localhost:3000

# Login — admin user provisioned via NEXUS_ADMIN_USER / NEXUS_ADMIN_PASSWORD
# (or register a new account in dev; registration is disabled in prod)
```

---

## 6. Environment Configuration

All configuration is **environment-variable driven**. Copy `.env.template` → `.env` and fill in required values. **Never commit secrets.**

### Required Variables (no defaults)

| Variable | Description | Generate With |
|----------|-------------|---------------|
| `DB_PASSWORD` | Postgres password | `openssl rand -base64 24` |
| `JWT_SECRET` | JWT signing key (≥32 chars) | `openssl rand -base64 48` |
| `REDIS_PASSWORD` | Redis password (prod) | `openssl rand -base64 24` |
| `NEXUS_CORS_ORIGINS` | Comma-separated allowed origins | — |
| `NEXUS_FRONTEND_URL` | Frontend public URL | — |

### Backend Environment Variables

| Variable | Default | Description |
|----------|---------|-------------|
| `NEXUS_DB_URL` | `jdbc:postgresql://localhost:5432/nexus_oms` | JDBC URL |
| `NEXUS_DB_USERNAME` | `nexus` | DB user |
| `NEXUS_DB_PASSWORD` | `nexus` | DB password |
| `NEXUS_DDL_AUTO` | `validate` | Hibernate DDL mode (**keep `validate` in prod**) |
| `NEXUS_JWT_SECRET` | — | JWT signing key |
| `NEXUS_JWT_EXPIRATION_MS` | `900000` | Access token TTL (15 min) |
| `NEXUS_KAFKA_ENABLED` | `false` | Enable Kafka event publishing |
| `NEXUS_KAFKA_BOOTSTRAP` | `localhost:9092` | Kafka broker list |
| `NEXUS_KAFKA_LISTENER_AUTO_START` | `true` | Auto-start Kafka consumers |
| `NEXUS_KAFKA_SECURITY_PROTOCOL` | `PLAINTEXT` | Kafka security protocol |
| `NEXUS_KAFKA_SASL_MECHANISM` | `PLAIN` | SASL mechanism |
| `NEXUS_KAFKA_SASL_JAAS_CONFIG` | — | SASL JAAS config |
| `NEXUS_REDIS_HOST` | `localhost` | Redis host |
| `NEXUS_REDIS_PORT` | `6379` | Redis port |
| `NEXUS_REDIS_HEALTH_ENABLED` | `false` | Include Redis in health checks |
| `NEXUS_CACHE_ENABLED` | `false` | Enable Redis-backed caching |
| `NEXUS_S3_ENABLED` | `false` | Enable S3/MinIO storage |
| `NEXUS_S3_ENDPOINT` | `http://localhost:9000` | S3 endpoint |
| `NEXUS_S3_ACCESS_KEY` | `minioadmin` | S3 access key |
| `NEXUS_S3_SECRET_KEY` | `minioadmin` | S3 secret key |
| `NEXUS_S3_BUCKET` | `nexus-shipping-labels` | S3 bucket |
| `NEXUS_DB_POOL_MAX` | `30` (prod) | Hikari max pool size |
| `NEXUS_DB_POOL_MIN` | `10` (prod) | Hikari min idle |
| `NEXUS_AI_ENABLED` | `false` | Enable AI features |
| `NEXUS_LOG_LEVEL` | `INFO` | Log level |
| `NEXUS_AUTOCONFIG_EXCLUDE` | Kafka auto-config | Excluded auto-configs |

### Carrier Credentials (for shipping)

| Variable | Provider |
|----------|----------|
| `FEDEX_CLIENT_ID` / `FEDEX_CLIENT_SECRET` / `FEDEX_ACCOUNT_NUMBER` | FedEx |
| `UPS_CLIENT_ID` / `UPS_CLIENT_SECRET` / `UPS_ACCOUNT_NUMBER` | UPS |
| `USPS_USER_ID` / `USPS_PASSWORD` | USPS |
| `DHL_API_KEY` / `DHL_API_SECRET` / `DHL_ACCOUNT_NUMBER` | DHL |

### SSO Providers (optional)

Google, Microsoft, Okta, Auth0 — configure via `sso.providers.*` properties (see `application.properties`).

---

## 7. Database & Migrations

### PostgreSQL + pgvector

The platform requires the **pgvector** extension for AI/embedding features (V48 migration).

**Docker (recommended):** Use `pgvector/pgvector:pg16` — extension pre-installed.

**Native install (macOS Homebrew, Postgres 14):**
```bash
# pgvector Homebrew bottle only ships @17/@18 — build from source for @14
git clone --branch v0.8.6 --depth 1 https://github.com/pgvector/pgvector.git /tmp/pgvector
cd /tmp/pgvector
make PG_CONFIG=/opt/homebrew/opt/postgresql@14/bin/pg_config install
# Verify
psql -U nexus -d nexus_oms -c "CREATE EXTENSION IF NOT EXISTS vector;"
psql -U nexus -d nexus_oms -c "SELECT '[1,2,3]'::vector;"
```

### Flyway Migrations

Migrations live in `nexus-oms-backend/src/main/resources/db/migration/` and run **automatically on startup**.

- **Current version:** V67 (`V67__schema_drift_fix.sql`)
- **Total migrations:** 57 (V1–V67, some version gaps are intentional)
- **Baseline:** `spring.flyway.baseline-on-migrate=true`, `baseline-version=0`

**Key migrations:**

| Version | Purpose |
|---------|---------|
| V26 | Row-Level Security enablement (tenant isolation) |
| V42 | Labor productivity tracking tables |
| V45 | Freight invoice tables (`nxfreight_*`) |
| V48 | pgvector + RAG tables (`ai_models`, `ai_prompts`, embeddings) |
| V58 | ASN ↔ dock appointment link |
| V66 | Sync conflict handling |
| V67 | **Schema drift fix** — reconciles entities with DB for `ddl-auto=validate` |

### Schema Validation

Production runs with `spring.jpa.hibernate.ddl-auto=validate`. This means Hibernate **verifies** the DB schema matches the entities on every startup and **fails fast** if there's drift. **Never** use `create`/`update` in production.

V67 was specifically built and tested to make validation pass across three DB states:
1. **Fresh DB** — all migrations from scratch
2. **Existing dev DB** — Hibernate-created schema on top of Flyway
3. **Existing prod DB** — V45 applied, no Hibernate additions

---

## 8. Connecting Services

### Backend ↔ PostgreSQL

```
URL:      jdbc:postgresql://<host>:<port>/<db>
Driver:   org.postgresql.Driver
Dialect:  org.hibernate.dialect.PostgreSQLDialect
Pool:     HikariCP (max 20 default, 30 prod)
```

### Backend ↔ Redis

```
Host:     NEXUS_REDIS_HOST (default localhost)
Port:     NEXUS_REDIS_PORT (default 6379)
Password: SPRING_DATA_REDIS_PASSWORD (prod)
```

Redis is used for caching, sessions, and rate limiting. The app **degrades gracefully** if Redis is down (health indicator disabled by default).

### Backend ↔ Kafka

```
Bootstrap: NEXUS_KAFKA_BOOTSTRAP (default localhost:9092)
Group:     nexus-oms-group
```

**Order lifecycle topics:** `order.created`, `order.confirmed`, `order.allocated`, `order.shipped`, `order.delivered`

Kafka can be **disabled** entirely with `NEXUS_KAFKA_ENABLED=false` (and `NEXUS_AUTOCONFIG_EXCLUDE` for the auto-config) — useful for lightweight deployments.

### Frontend ↔ Backend

The frontend calls `/api/v1` (relative path). In production, **Nginx** proxies `/api/` → `backend:8080`. In dev, Vite proxies to the backend.

### Backend ↔ AI Services

```
AI Ops API:   http://localhost:5000  (nexus.ai.base-url-ops)
AI Intel API: http://localhost:5001  (nexus.ai.base-url-intel)
OpenAI:       OPENAI_API_KEY, model gpt-4o, embeddings text-embedding-3-small
```

---

## 9. Building the Application

### Backend

```bash
cd nexus-oms-backend
mvn clean package -DskipTests
# Output: target/oms-1.0.0.jar
```

> **Fast iteration tip:** If only migration SQL changed (no Java), use `mvn -q package -DskipTests` (no `clean`) — much faster.

### Frontend

```bash
cd nexus-oms-frontend
npm ci
npm run build
# Output: dist/ (served by Nginx)
```

### Docker Images

```bash
# Backend
docker build -t nexus-backend ./nexus-oms-backend

# Frontend
docker build -t nexus-frontend ./nexus-oms-frontend
```

---

## 10. Production Deployment

### Docker Compose (Production)

```bash
# 1. Configure secrets
cp .env.template .env
# Set DB_PASSWORD, JWT_SECRET, REDIS_PASSWORD, NEXUS_CORS_ORIGINS, NEXUS_FRONTEND_URL

# 2. Deploy
docker compose -f docker-compose.prod.yml up -d --build

# 3. Verify
curl -f http://localhost/api/v1/actuator/health
curl -f http://localhost/manifest.json
```

**`docker-compose.prod.yml` runs with `SPRING_PROFILES_ACTIVE=prod`**, which enables:
- Larger Hikari pool (30 max / 10 min)
- `ddl-auto=validate` (schema locked)
- Flyway `validate-on-migrate=true`, `clean-disabled=true`
- Graceful shutdown (20s timeout)
- Health probes enabled
- Redis-backed caching
- **No anonymous signups** (`app.registration.enabled=false`)
- CORS locked to `NEXUS_CORS_ORIGINS`

### Production Service Ports

| Service | Port | Exposure |
|---------|------|----------|
| frontend | 80/443 | Public (Nginx) |
| backend | 8080 | Internal only (`expose`) |
| postgres | 5432 | Internal only |
| redis | 6379 | Internal only |
| kafka | 9092 | Internal only |

> In production, only the **frontend** ports are exposed to the internet. The backend, DB, Redis, and Kafka are on an internal Docker network.

### Production Checklist

- [ ] All secrets set (no defaults)
- [ ] `SPRING_PROFILES_ACTIVE=prod`
- [ ] `ddl-auto=validate` (never `create`/`update`)
- [ ] Redis password set
- [ ] CORS origins locked to your domain
- [ ] Registration disabled (admins provision users)
- [ ] TLS/HTTPS terminated at Nginx or load balancer
- [ ] Backups configured (see §14)
- [ ] Monitoring configured (see §13)

---

## 11. Kubernetes / Helm Deployment

A Helm chart is provided at `deploy/helm/nexus/`.

```bash
helm install nexus deploy/helm/nexus \
  --set postgres.password="$DB_PASSWORD" \
  --set redis.password="$REDIS_PASSWORD" \
  --set backend.jwtSecret="$JWT_SECRET" \
  --set ingress.host="app.yourdomain.com"
```

**Chart contents:**
- `backend.yaml` — Spring Boot deployment + service
- `frontend.yaml` — Nginx SPA deployment + service
- `postgres.yaml` — PostgreSQL StatefulSet
- `redis.yaml` — Redis deployment
- `kafka.yaml` — Kafka (KRaft) StatefulSet
- `ingress.yaml` — Ingress with TLS
- `secret.yaml` — Kubernetes secrets
- `backup.yaml` — CronJob for DB backups

---

## 12. Cloud Deployment (Render)

A `render.yaml` blueprint is provided for Render.com:

```yaml
# Backend web service (Java)
# - Builds with Maven
# - Health check: /api/v1/actuator/health
# - Kafka/Redis auto-config excluded (lightweight)
# - Frontend URL: https://nexus-silk-five-61.vercel.app
```

**Render setup:**
1. Create a Postgres instance on Render, copy the connection string.
2. Deploy the backend from the `render.yaml` blueprint.
3. Set `NEXUS_DB_URL`, `NEXUS_DB_USERNAME`, `NEXUS_DB_PASSWORD`, `NEXUS_JWT_SECRET` as sync:false env vars.
4. Deploy the frontend to Vercel/Netlify, set `APP_FRONTEND_URL` and `APP_CORS_ALLOWED_ORIGINS`.

---

## 13. Monitoring & Observability

### Actuator Endpoints

Exposed: `health`, `info`, `prometheus`, `metrics`

```bash
curl http://localhost:8080/api/v1/actuator/health
curl http://localhost:8080/api/v1/actuator/metrics
curl http://localhost:8080/api/v1/actuator/prometheus   # Prometheus scrape target
```

### Prometheus

Config at `deploy/prometheus/`. Scrape the backend's `/actuator/prometheus` endpoint.

### Grafana

Config at `deploy/grafana/`:
- `datasources/datasources.yml` — Prometheus datasource
- `dashboards/oms-dashboard.json` — OMS dashboard

```bash
# Start Grafana + Prometheus (see deploy/ for compose)
docker compose -f deploy/grafana/docker-compose.yml up -d
```

### Logging

- Structured JSON via `logback-spring.xml` in prod
- Log level: `NEXUS_LOG_LEVEL` (default INFO)
- Graceful shutdown with 20s drain

---

## 14. Backup & Disaster Recovery

### Database Backups (primary)

```bash
# Full logical backup (custom format)
pg_dump -U nexus -h <db-host> -F c -d nexus_oms -f nexus_backup_$(date +%Y%m%d_%H%M).dump

# Restore
createdb -U nexus -h <db-host> nexus_oms_restore
pg_restore -U nexus -h <db-host> -d nexus_oms_restore -j 4 --no-owner --role=nexus nexus_backup.dump
```

### Retention Schedule

| Cadence | Retention | Purpose |
|---------|-----------|---------|
| Daily 02:00 | 14 days | RPO ≤ 24h |
| Weekly (Sun 03:00) | 8 weeks | Audit window |
| Monthly | 12 months | Compliance |

**Always copy backups off-site** (S3, Azure Blob) and encrypt at rest.

### Redis

```bash
redis-cli -a $REDIS_PASSWORD BGSAVE   # then copy RDB from volume
```
Redis is a cache — a flush is safe (cold start).

### Kafka

Kafka topics are ephemeral (order events). No backup needed; consumers re-read from earliest offset on restart if needed.

---

## 15. Security

### Authentication
- **JWT** access tokens (15 min TTL) + refresh tokens
- **MFA** (TOTP) support
- **SSO** — Google, Microsoft, Okta, Auth0 (optional)
- **No anonymous signups in prod** — admins provision users

### Authorization
- **RBAC** — role-based access control (`ROLE_ORDER_MANAGER`, etc.)
- **Tenant isolation** — Row-Level Security (RLS) on all tenant tables

### Security Headers (set by `SecurityHeadersFilter`)

| Header | Value |
|--------|-------|
| `Strict-Transport-Security` | `max-age=31536000; includeSubDomains; preload` |
| `X-Content-Type-Options` | `nosniff` |
| `X-Frame-Options` | `DENY` |
| `Referrer-Policy` | `strict-origin-when-cross-origin` |
| `Permissions-Policy` | `camera=(), microphone=(), geolocation=()` |
| `Content-Security-Policy` | Configured via `SecurityConfig` |

### Production Hardening
- `ddl-auto=validate` — schema locked
- Flyway `clean-disabled=true` — no destructive migration
- `server.error.include-message=never` — no error details leaked
- CORS locked to explicit origins
- Secrets via env vars / secrets manager — never committed

---

## 16. API Reference

**Base URL:** `https://your-domain.com/api/v1`
**Auth:** `Authorization: Bearer <token>` (unless marked public)

### Authentication
| Method | Endpoint | Description |
|--------|----------|-------------|
| POST | `/auth/login` | Login → JWT tokens |
| POST | `/auth/refresh` | Refresh access token |
| POST | `/auth/mfa/verify` | Verify TOTP MFA |
| POST | `/auth/register` | Register (disabled in prod) |

### Core Modules
| Module | Endpoints | Description |
|--------|-----------|-------------|
| Orders | `/orders/**` | Order CRUD & lifecycle |
| Inventory | `/inventory/**` | Inventory management |
| Shipments | `/shipments/**` | Shipment tracking |
| Returns | `/returns/**` | Returns & RMA |
| Analytics | `/analytics/**` | Business analytics |
| Webhooks | `/webhooks/**` | External webhooks |
| AI | `/ai/**` | AI predictions |
| Connectors | `/connectors/**` | Shopify/BigCommerce sync |
| Promotions | `/promotions/**` | Promotions & pricing |
| Endless Aisle | `/endless-aisle/**` | AI endless aisle |

### Generic Import Engine
| Method | Endpoint | Description |
|--------|----------|-------------|
| GET | `/import/entity-types` | Supported entity types |
| GET | `/import/formats` | Supported formats |
| POST | `/import/{entityType}` | Import a file |

**Entity types:** `products`, `orders`, `inventory`, `customers`, `shipments`, `returns`, `suppliers`, `purchase-orders`, `invoices`, `warehouses`
**Formats:** `csv`, `json`, `xml`, `edi` (X12), `xlsx`

---

## 17. AI / ML Services

### Python Services
| Service | Port | Purpose |
|---------|------|---------|
| `supply_chain_ai` | 5000 | Demand forecasting, ops AI |
| `supply_chain_ai2` | 5001 | Business intelligence AI |

### OpenAI Integration
- **Model:** `gpt-4o` (configurable via `OPENAI_MODEL`)
- **Embeddings:** `text-embedding-3-small` (for RAG / vector search)
- **pgvector** stores embeddings in Postgres (V48)

### AI Features
- Demand forecasting
- Endless aisle (product recommendations)
- Intelligent order routing
- MCP server tools (AI agent integration)
- RAG over documents (pgvector)

### Enable AI
```bash
export NEXUS_AI_ENABLED=true
export OPENAI_API_KEY="sk-..."
# Start Python services
cd supply_chain_ai && uvicorn main:app --port 5000 &
cd supply_chain_ai2 && uvicorn main:app --port 5001 &
```

---

## 18. Operations & Troubleshooting

### Startup Sequence
1. Flyway runs migrations (V1–V67)
2. Hibernate validates schema (`ddl-auto=validate`)
3. App starts, health probes become ready
4. Kafka consumers start (if enabled)

### Common Issues

| Symptom | Cause | Fix |
|---------|-------|-----|
| `Schema-validation: missing column` | DB drift | Apply latest migration; run V67 |
| `Migration V67 failed` | Freight table collision | V67 handles both rename & drop cases |
| `relation "vector" does not exist` | pgvector not installed | `CREATE EXTENSION vector;` |
| `Application run failed` | Missing `NEXUS_JWT_SECRET` | Set JWT secret (≥32 chars) |
| Kafka connection refused | Kafka down | `docker compose up -d kafka` or `NEXUS_KAFKA_ENABLED=false` |
| Redis connection refused | Redis down | App degrades gracefully; or start Redis |
| `ddl-auto=validate` fails on fresh DB | Migration gap | Ensure all migrations present |

### Logs
```bash
# Docker
docker logs -f nexus-backend

# Native
tail -f /tmp/nexus_boot.log
```

### Health Checks
```bash
curl http://localhost:8080/api/v1/actuator/health
# readiness/liveness probes enabled in prod
```

---

## 19. Schema Validation (ddl-auto=validate)

This is the **most important operational guarantee** in the platform.

### Why It Matters
`spring.jpa.hibernate.ddl-auto=validate` makes Hibernate **verify** the DB schema matches the JPA entities on every startup. If there's any drift, the app **fails to start** — preventing silent data corruption from schema mismatches.

### The Three DB States V67 Handles
1. **Fresh DB** — all 57 migrations run from scratch
2. **Existing dev DB** — Hibernate previously created schema (`update`), drift present
3. **Existing prod DB** — V45 applied, no Hibernate additions

### What V67 Fixes
- `ai_models.tags` / `ai_prompts.tags`: `text[]` → `varchar(255)` (entities map as `String`)
- `nx_edi_documents.partner_id`: added as `varchar(255)` (was `uuid`)
- `nx_edi_documents.validation_errors`: added as `jsonb`
- `nx_inventory.version`, `nx_fulfillment_exceptions`, `nx_order_items.image_url`, `nx_orders` address columns
- Freight table renames (`nxfreight_*` → `nx_freight_*`) — handles both rename (data-preserving) and drop-orphan cases
- RLS policies on canonical `nx_freight_*` tables

### How to Verify
```bash
# Boot with validate — should print "Started NexusOmsApplication"
NEXUS_DDL_AUTO=validate java -jar target/oms-1.0.0.jar
```

---

## 20. FAQ

**Q: Can I run without Kafka?**
Yes. Set `NEXUS_KAFKA_ENABLED=false` and `NEXUS_AUTOCONFIG_EXCLUDE=org.springframework.boot.autoconfigure.kafka.KafkaAutoConfiguration`. Order events won't be published, but core functionality works.

**Q: Can I run without Redis?**
Yes. The app degrades gracefully. Set `NEXUS_CACHE_ENABLED=false` and `NEXUS_REDIS_HEALTH_ENABLED=false`.

**Q: How do I reset the database?**
```bash
docker compose down -v   # removes volumes (DESTRUCTIVE)
# or
dropdb -U nexus nexus_oms && createdb -U nexus nexus_oms
# Flyway re-runs all migrations on next startup
```

**Q: How do I add a new migration?**
Create `V68__description.sql` in `nexus-oms-backend/src/main/resources/db/migration/`. Use `IF NOT EXISTS` / `IF EXISTS` guards for idempotency. Rebuild and restart.

**Q: How do I create an admin user?**
In dev, seed via Flyway or the `NEXUS_ADMIN_USER`/`NEXUS_ADMIN_PASSWORD` env vars. In prod, admins provision users (registration disabled).

**Q: What Java version is required?**
Java 17. Set `JAVA_HOME` to a JDK 17 install.

**Q: Where is the fat JAR?**
`nexus-oms-backend/target/oms-1.0.0.jar`

**Q: How do I enable AI features?**
Set `NEXUS_AI_ENABLED=true`, `OPENAI_API_KEY`, start the Python services on :5000/:5001.

---

*This document is maintained as the authoritative operational reference for the Nexus OMS platform. For detailed API schemas see `docs/API.md`; for the deployment deep-dive see `docs/DEPLOYMENT.md`; for architecture see `docs/02-TECHNICAL-ARCHITECTURE.md`.*
