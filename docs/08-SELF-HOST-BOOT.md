# NexusShip OMS — Self-Host Boot: Production in Under 1 Hour

**Target: a mid-market buyer evaluating NexusShip against Manhattan/SAP
has the full stack — Postgres, Redis, Kafka, backend, frontend,
monitoring — running and taking live orders inside 60 minutes.**

Everything here is real: one command per step, using the checked-in
artifacts (`docker-compose.prod.yml`, `deploy/`, `docs/DEPLOYMENT.md`).

---

## The 60-minute path

### 00:00–05:00 — Preflight

```bash
# Requires: git, docker, docker compose plugin (2.20+), ~8GB RAM free
docker --version && docker compose version
git clone <your-nexus-repo> && cd <your-nexus-repo>
```

### 05:00–20:00 — Secrets & config

Create `.env` at repo root (template below). Generate strong secrets:

```bash
openssl rand -hex 32            # → DB_PASSWORD
openssl rand -hex 32            # → REDIS_PASSWORD
openssl rand -hex 32            # → JWT_SECRET
```

`.env`:

```bash
DB_PASSWORD=<from-openssl>
REDIS_PASSWORD=<from-openssl>
JWT_SECRET=<from-openssl>
NEXUS_CORS_ORIGINS=http://localhost
NEXUS_FRONTEND_URL=http://localhost
NEXUS_DB_POOL_MAX=30
NEXUS_DB_POOL_MIN=10
```

### 20:00–40:00 — Boot the stack

```bash
docker compose -f docker-compose.prod.yml up -d
docker compose -f docker-compose.prod.yml ps --format "table {{.Name}}\t{{.Status}}"
```

Wait for `healthy` on postgres/redis/backend (probes built in). Expect:

```
nexus-prod-postgres   Up (healthy)
nexus-prod-redis      Up (healthy)
nexus-prod-kafka      Up
nexus-prod-backend    Up (healthy)
nexus-prod-frontend   Up
```

### 40:00–45:00 — Verify

```bash
curl -s http://localhost/api/v1/actuator/health | jq .status   # "UP"
curl -s http://localhost/manifest.json | head -c 120           # PWA manifest
curl -s http://localhost:9090/-/healthy                        # Prometheus
curl -s http://localhost:3000/api/health                       # Grafana
```

### 45:00–50:00 — First admin user

```bash
curl -s -X POST http://localhost/api/v1/auth/register \
  -H "Content-Type: application/json" \
  -d '{"username":"admin","password":"<strong>","email":"admin@example.com"}' | jq .success   # true
```

### 50:00–60:00 — Create a product and an order

```bash
TOKEN=$(curl -s -X POST http://localhost/api/v1/auth/login \
  -H "Content-Type: application/json" \
  -d '{"username":"admin","password":"<strong>"}' | jq -r .data.accessToken)

# Seed a SKU
curl -s -X POST http://localhost/api/v1/products \
  -H "Authorization: Bearer $TOKEN" -H "Content-Type: application/json" \
  -d '{"sku":"SKU-0001","name":"Demo Widget","price":19.99}' | jq .success

# Place an order
curl -s -X POST http://localhost/api/v1/orders \
  -H "Authorization: Bearer $TOKEN" -H "Content-Type: application/json" \
  -d '{"channel":"web","customerName":"Demo Customer","items":[{"sku":"SKU-0001","quantity":1,"unitPrice":19.99}]}' | jq .success
```

**Done.** Live order → inventory → fulfillment pipeline running.
Total wall-clock: ~50 minutes including image pulls.

---

## Monitoring (bundled, no extra setup)

| Stack | Endpoint | Credentials |
|---|---|---|
| Prometheus | `http://localhost:9090` | none (localhost) |
| Grafana | `http://localhost:3000` | `admin` / Grafana-issued (first login) |
| Dashboards | `deploy/grafana/dashboards/` | import on first login |

Alerts in `deploy/prometheus/alerts.yml` (backend error rate, latency,
JVM memory) fire to Grafana OnCall (see `docs/DEPLOYMENT.md` §5).

---

## Tear-down / re-boot

```bash
docker compose -f docker-compose.prod.yml down         # stop, keep volumes
docker compose -f docker-compose.prod.yml down -v      # wipe data too
docker compose -f docker-compose.prod.yml up -d        # re-boot with data
```

Backup/restore: `docs/DEPLOYMENT.md` §4.

---

## Going further

- **Kubernetes**: `deploy/helm/nexus/` + `docs/DEPLOYMENT.md` §11
  (same stack, rolling upgrades, PVC persistence, backup CronJob).
- **Custom domain / TLS**: `docs/DEPLOYMENT.md` §8 (Nginx config).
- **SSO/MFA hardening**: see Auth + RBAC feature doc and security
  checklist (`docs/DEPLOYMENT.md` §7).

## Cost summary

| Item | Cost |
|---|---|
| Software | **$0** — open source, self-hosted |
| Compute (single node, 4 vCPU / 8GB) | ~$60/mo cloud, or free on an existing box |
| Managed Postgres (optional) | ~$15–50/mo |
| Total all-in | **≈ $0–110/mo** for a full OMS+WMS+3PL+EDI+AI stack |

Compare: Manhattan/SAP/Oracle license + implementation is 6–7 figures
and 6–18 months (see `07-SCM-COMPARISON.md` §5.1). See
`09-COST-TCO.md` for the full TCO model.
