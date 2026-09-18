# Nexus OMS — Operations Guide

This guide covers **day-2 operations** for the Nexus platform: monitoring,
backups, upgrades, scaling, incident response, and ongoing maintenance.

---

## Table of Contents

1. [Operational Overview](#1-operational-overview)
2. [Monitoring & Alerting](#2-monitoring--alerting)
3. [Health Checks](#3-health-checks)
4. [Backup & Restore](#4-backup--restore)
5. [Upgrades & Rollbacks](#5-upgrades--rollbacks)
6. [Scaling](#6-scaling)
7. [Incident Response](#7-incident-response)
8. [Log Management](#8-log-management)
9. [Security Operations](#9-security-operations)
10. [Runbooks](#10-runbooks)

---

## 1. Operational Overview

The Nexus stack runs as Docker containers. Day-2 operations involve:

- **Monitoring** — Prometheus + Grafana for metrics, healthchecks for liveness.
- **Backups** — PostgreSQL dumps + volume snapshots.
- **Upgrades** — rebuild images, migrate DB, rollback if needed.
- **Scaling** — vertical (bigger host) or horizontal (more replicas).
- **Incidents** — detect, diagnose, resolve, learn.

---

## 2. Monitoring & Alerting

### 2.1 Grafana

Access: `http://localhost:3001` (or your host).

Default login: `admin` / `GRAFANA_ADMIN_PASSWORD` from `.env`.

**Dashboards:**
- System overview (CPU, RAM, disk per container)
- Backend health (JVM, request rate, error rate)
- Database (connections, slow queries)
- Kafka (broker, consumer lag)
- Redis (memory, hit rate)

### 2.2 Prometheus

Access: `http://localhost:9090`.

**Note on scrape targets:** The compose ships with a clean Prometheus config.
The backend `/actuator/prometheus` endpoint requires ADMIN auth, and
kafka/redis/postgres need dedicated exporters. See
`monitoring/prometheus/prometheus.yml` for commented templates to enable them.

### 2.3 Alerting

`monitoring/prometheus/alerts.yml` ships with a `ServiceDown` alert. Extend it:

```yaml
groups:
  - name: nexus
    rules:
      - alert: ServiceDown
        expr: up == 0
        for: 5m
        labels:
          severity: critical
        annotations:
          summary: "{{ $labels.job }} is down"

      - alert: HighErrorRate
        expr: rate(http_requests_total{status=~"5.."}[5m]) > 0.05
        for: 10m
        labels:
          severity: warning
        annotations:
          summary: "High 5xx error rate"
```

---

## 3. Health Checks

### 3.1 Quick healthcheck

```bash
bash scripts/healthcheck.sh
```

Checks:
- Backend: `http://localhost:8080/api/v1/actuator/health`
- Frontend: `http://localhost/`
- AI Ops: `http://localhost:5000/api/health`
- AI Intel: `http://localhost:5001/api/health-extended`
- Postgres, Redis, Kafka, Prometheus, Grafana: container status

### 3.2 Manual checks

```bash
# All containers
docker compose ps

# Backend health
curl http://localhost:8080/api/v1/actuator/health

# Container logs
docker logs nexus-backend --tail 50
docker logs nexus-frontend --tail 50
```

---

## 4. Backup & Restore

### 4.1 PostgreSQL backup (daily)

```bash
# Manual
docker exec nexus-postgres pg_dump -U nexus -d nexus_oms > backup.sql

# Cron (daily 2am)
0 2 * * * docker exec nexus-postgres pg_dump -U nexus -d nexus_oms | gzip > /backups/nexus_$(date +\%F).sql.gz
```

### 4.2 Volume backup

```bash
docker run --rm -v nexus_postgres-data:/data -v /backups:/backup \
  alpine tar czf /backup/postgres-data.tar.gz -C /data .
```

### 4.3 Restore

```bash
# Restore DB
cat backup.sql | docker exec -i nexus-postgres psql -U nexus -d nexus_oms

# Restore volume
docker run --rm -v nexus_postgres-data:/data -v /backups:/backup \
  alpine tar xzf /backup/postgres-data.tar.gz -C /data
```

### 4.4 Backup retention policy

| Environment | Frequency | Retention |
|-------------|-----------|-----------|
| Dev | None (throwaway) | — |
| UAT | Weekly | 4 weeks |
| Prod | Daily | 30 days + monthly archive |

---

## 5. Upgrades & Rollbacks

### 5.1 Upgrade

```bash
# 1. Pull latest code
git pull origin main

# 2. Backup first (see Section 4)

# 3. Rebuild & restart
docker compose up -d --build

# 4. Verify
bash scripts/healthcheck.sh
```

### 5.2 Rollback

```bash
# 1. Checkout previous commit
git log --oneline -5
git checkout <previous-commit>

# 2. Restart
docker compose up -d --build

# 3. Verify
bash scripts/healthcheck.sh
```

### 5.3 DB migration safety

- Backend uses **Flyway** for migrations (`FLYWAY_BASELINE_ON_MIGRATE=true`).
- Always backup before an upgrade that includes DB migrations.
- Test the upgrade on UAT first.

---

## 6. Scaling

### 6.1 Vertical (simplest)

Resize the host (more vCPU/RAM/disk), then:

```bash
docker compose up -d
```

### 6.2 Horizontal (backend + AI)

```bash
# docker-compose.override.yml
services:
  backend:
    deploy:
      replicas: 2
```

### 6.3 Split DB to managed service

For high volume, move Postgres to RDS/Aurora:

```bash
# .env
NEXUS_DB_URL=jdbc:postgresql://YOUR_RDS_HOST:5432/nexus_oms
```

---

## 7. Incident Response

### 7.1 Incident severity

| Severity | Definition | Response time |
|----------|------------|---------------|
| **SEV-1** | Total outage | Immediate |
| **SEV-2** | Major degradation | 1 hour |
| **SEV-3** | Minor issue | 1 day |
| **SEV-4** | Cosmetic | Next release |

### 7.2 Incident workflow

1. **Detect** — alert fires or user reports.
2. **Triage** — assess severity, assign owner.
3. **Diagnose** — check logs, metrics, healthchecks.
4. **Resolve** — apply fix (restart, rollback, scale).
5. **Verify** — confirm recovery.
6. **Learn** — postmortem, update runbooks.

### 7.3 Common incidents

| Symptom | Likely cause | Fix |
|---------|--------------|-----|
| Backend unhealthy | DB down / stale volume | `docker compose down -v && up` |
| Frontend unhealthy | Bad build / healthcheck | Rebuild frontend |
| High CPU | Peak load / AI training | Scale up / off-peak training |
| DB full | No retention | Add disk, prune data |
| Kafka lag | Consumer down | Restart consumer, check logs |

---

## 8. Log Management

### 8.1 View logs

```bash
docker logs nexus-backend --tail 100
docker logs nexus-frontend --tail 100
docker logs nexus-ai-ops --tail 100
docker logs nexus-ai-intel --tail 100
```

### 8.2 Log levels

Set `NEXUS_LOG_LEVEL` in `.env`:
- `DEBUG` — verbose (dev)
- `INFO` — normal (default)
- `WARN` — reduced (prod)

### 8.3 Centralized logging (optional)

For production, ship logs to a central store (ELK, Loki, CloudWatch):

```yaml
# docker-compose.override.yml
services:
  backend:
    logging:
      driver: json-file
      options:
        max-size: "10m"
        max-file: "3"
```

---

## 9. Security Operations

### 9.1 Secret rotation

Rotate `DB_PASSWORD`, `JWT_SECRET`, `GRAFANA_ADMIN_PASSWORD` regularly:

```bash
# 1. Generate new secrets
openssl rand -base64 48

# 2. Update .env
# 3. Restart
docker compose up -d
```

### 9.2 Image updates

Rebuild images regularly to pick up security patches:

```bash
docker compose build --pull
docker compose up -d
```

### 9.3 Access control

- Use RBAC in the app (Admin, Ops, Warehouse, Finance, Viewer).
- Restrict SSH access to the host.
- Don't expose admin ports (8080, 3001, 9090) publicly.

---

## 10. Runbooks

### Runbook 1 — Backend down

```bash
# 1. Check status
docker compose ps backend

# 2. Check logs
docker logs nexus-backend --tail 50

# 3. If DB auth error → reset volume
docker compose down -v && docker compose up -d --build

# 4. Verify
curl http://localhost:8080/api/v1/actuator/health
```

### Runbook 2 — Frontend down

```bash
# 1. Check status
docker compose ps frontend

# 2. Check logs
docker logs nexus-frontend --tail 50

# 3. Rebuild
docker compose build frontend && docker compose up -d frontend

# 4. Verify
curl http://localhost/
```

### Runbook 3 — Database full

```bash
# 1. Check disk
df -h

# 2. Check DB size
docker exec nexus-postgres psql -U nexus -d nexus_oms -c \
  "SELECT pg_size_pretty(pg_database_size('nexus_oms'));"

# 3. Prune old data / add disk
# 4. Verify
```

### Runbook 4 — Kafka lag

```bash
# 1. Check consumer lag
docker exec nexus-kafka kafka-consumer-groups --bootstrap-server localhost:9092 \
  --describe --all-groups

# 2. Restart consumer (backend)
docker compose restart backend

# 3. Verify lag returns to 0
```

### Runbook 5 — Full reset

```bash
# WARNING: deletes ALL data
docker compose down -v
docker compose up -d --build
```

---

*See also: [Deployment Guide](DEPLOYMENT.md) · [Onboarding Guide](ONBOARDING.md) ·
[Sizing Guide](SIZING.md) · [Environment Guide](ENVIRONMENTS.md).*