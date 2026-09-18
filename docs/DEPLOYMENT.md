# Nexus OMS & Supply Chain Platform — Deployment Guide

This guide covers **every** way to deploy the Nexus platform — from a single
laptop to production cloud clusters. It is the canonical reference for getting
Nexus running anywhere.

---

## Table of Contents

1. [Architecture Overview](#1-architecture-overview)
2. [Prerequisites](#2-prerequisites)
3. [Local Deployment (laptop / workstation)](#3-local-deployment)
4. [Single-Server Deployment (EC2 / VPS / bare metal)](#4-single-server-deployment)
5. [Cloud Platform Deployment](#5-cloud-platform-deployment)
   - [AWS EC2](#51-aws-ec2)
   - [AWS ECS](#52-aws-ecs)
   - [Google Cloud (GCP)](#53-google-cloud-gcp)
   - [Azure](#54-azure)
   - [DigitalOcean / Linode / Hetzner](#55-digitalocean--linode--hetzner)
6. [Environment Matrix (Dev / UAT / Prod)](#6-environment-matrix)
7. [Sizing by Record Volume](#7-sizing-by-record-volume)
8. [Security Hardening](#8-security-hardening)
9. [TLS / Reverse Proxy](#9-tls--reverse-proxy)
10. [Backup & Disaster Recovery](#10-backup--disaster-recovery)
11. [Troubleshooting](#11-troubleshooting)

---

## 1. Architecture Overview

Nexus is a full-stack Order Management System (OMS) and supply chain platform.
The Docker stack consists of **9 services**:

| Service      | Container        | Port  | Description                                   |
|--------------|------------------|-------|-----------------------------------------------|
| PostgreSQL   | `nexus-postgres` | 5433  | Primary database (pgvector/pg16)              |
| Redis        | `nexus-redis`    | 6379  | Cache & sessions                              |
| Kafka        | `nexus-kafka`    | 9092  | Event streaming / order lifecycle             |
| Backend      | `nexus-backend`  | 8080  | Spring Boot 3 OMS API (`/api/v1`)             |
| Frontend     | `nexus-frontend` | 80    | React + Vite SPA (nginx)                      |
| AI Ops       | `nexus-ai-ops`   | 5000  | Python ML — order routing                     |
| AI Intel     | `nexus-ai-intel` | 5001  | Python ML — demand & inventory                |
| Prometheus   | `nexus-prometheus`| 9090 | Metrics scraping                              |
| Grafana      | `nexus-grafana`  | 3001  | Dashboards & alerting                         |

```
                    ┌─────────────────────────────────────────────┐
                    │              FRONTEND (nginx :80)           │
                    │         React SPA + /api proxy              │
                    └───────────────┬─────────────────────────────┘
                                    │ /api/v1
                    ┌───────────────▼─────────────────────────────┐
                    │              BACKEND (Spring :8080)         │
                    │  Auth · Orders · Inventory · Shipments      │
                    │  Returns · Analytics · Integrations · AI    │
                    └───┬────────┬────────┬──────────┬────────────┘
                        │        │        │          │
              ┌─────────▼──┐ ┌───▼──┐ ┌───▼─────┐ ┌──▼──────────┐
              │ POSTGRES   │ │REDIS │ │ KAFKA   │ │ AI OPS      │
              │ pgvector   │ │cache │ │ events  │ │ AI INTEL    │
              │ :5433      │ │:6379 │ │ :9092   │ │ :5000/:5001 │
              └────────────┘ └──────┘ └─────────┘ └─────────────┘
                    ┌───────────────┬─────────────────────────────┐
                    │  PROMETHEUS   │        GRAFANA              │
                    │  :9090        │        :3001                │
                    └───────────────┴─────────────────────────────┘
```

**Key design points:**
- All secrets come from `.env` (gitignored) — **never** hardcoded.
- Named volumes persist data across container restarts.
- `restart: unless-stopped` on every service for auto-recovery.
- Backend waits for postgres/redis healthy before starting (`depends_on`).
- `SPRING_PROFILES_ACTIVE=prod` enables hardened production config.

---

## 2. Prerequisites

| Requirement | Version | Notes |
|-------------|---------|-------|
| Docker Engine | 24+ | `docker --version` |
| Docker Compose | v2 | `docker compose version` |
| Git | any | `git --version` |
| CPU arch | x86_64 **or** arm64 | The compose supports both (Apple Silicon + Graviton) |
| RAM | 8 GB min, 16 GB recommended | Java + 2 Python ML + Kafka |
| Disk | 30 GB+ | Images + data volumes |

**Verify Docker:**
```bash
docker --version
docker compose version
docker info | grep -i architecture   # x86_64 or aarch64
```

---

## 3. Local Deployment

### 3.1 Quick start (laptop / workstation)

```bash
# 1. Clone
git clone https://github.com/Mr-Mayank-Sharma/Nexus-docker.git
cd Nexus-docker

# 2. Create .env
cp .env.example .env

# 3. Edit .env — set REQUIRED secrets
#    DB_PASSWORD, JWT_SECRET (>=32 chars), GRAFANA_ADMIN_PASSWORD
#    Generate:  openssl rand -base64 48

# 4. Build & start
docker compose up -d --build

# 5. Verify
docker compose ps
curl http://localhost:8080/api/v1/actuator/health
```

### 3.2 Access URLs (local)

| Service | URL |
|---------|-----|
| Frontend | http://localhost |
| Backend API | http://localhost:8080/api/v1 |
| Grafana | http://localhost:3001 |
| Prometheus | http://localhost:9090 |
| PostgreSQL | localhost:5433 |
| Redis | localhost:6379 |
| Kafka | localhost:9092 |

### 3.3 Stop / tear down

```bash
docker compose down        # stop (keep data)
docker compose down -v     # stop AND delete all data volumes
```

### 3.4 One-shot setup script

```bash
# Clones, creates .env, builds, starts — all in one
bash scripts/setup.sh
```

---

## 4. Single-Server Deployment

This is the **recommended starting point** for production — one server runs the
whole stack. Works on EC2, any VPS, or bare metal.

### 4.1 Provision the server

- **OS:** Ubuntu 22.04 LTS (or Amazon Linux 2023)
- **Arch:** x86_64 or ARM64 (Graviton)
- **Size:** 4 vCPU / 16 GB RAM minimum (see [Sizing](#7-sizing-by-record-volume))
- **Disk:** 30 GB+ SSD

### 4.2 Install Docker

```bash
# Ubuntu
sudo apt update
sudo apt install -y docker.io docker-compose-v2 git
sudo systemctl enable --now docker
sudo usermod -aG docker $USER
# log out and back in
```

### 4.3 Deploy

```bash
git clone https://github.com/Mr-Mayank-Sharma/Nexus-docker.git
cd Nexus-docker
cp .env.example .env
nano .env   # set DB_PASSWORD, JWT_SECRET, GRAFANA_ADMIN_PASSWORD, CORS_ORIGINS

docker compose up -d --build
```

### 4.4 Firewall / security group

Open inbound ports:
- `80` (HTTP — frontend) — **required**
- `22` (SSH) — required
- `8080`, `3001`, `9090` — **optional** (recommend keeping behind reverse proxy)

---

## 5. Cloud Platform Deployment

### 5.1 AWS EC2

**Step 1 — Create instance**
- AMI: Ubuntu 22.04 LTS
- Instance type: `t3.xlarge` (4 vCPU / 16 GB) minimum
- Storage: 30 GB+ SSD
- Security group: open `80`, `22` (and `8080`/`3001` if exposing directly)

**Step 2 — SSH & install**
```bash
ssh -i your-key.pem ubuntu@YOUR_EC2_IP
sudo apt update && sudo apt install -y docker.io docker-compose-v2 git
sudo systemctl enable --now docker
```

**Step 3 — Deploy**
```bash
git clone https://github.com/Mr-Mayank-Sharma/Nexus-docker.git
cd Nexus-docker
cp .env.example .env
nano .env
# IMPORTANT: set CORS_ORIGINS=http://YOUR_EC2_IP,http://YOUR_DOMAIN
docker compose up -d --build
```

**Step 4 — Access**
- Frontend: `http://YOUR_EC2_IP`
- Grafana: `http://YOUR_EC2_IP:3001`

> ⚠️ For production, put TLS in front (see [Section 9](#9-tls--reverse-proxy)).

### 5.2 AWS ECS

For horizontal scaling and managed orchestration.

**Step 1 — Push images to ECR**
```bash
aws ecr-public get-login-password | docker login --username AWS --password-stdin \
  YOUR_ACCOUNT.dkr.ecr.us-east-1.amazonaws.com

docker tag nexus-backend YOUR_ACCOUNT.dkr.ecr.us-east-1.amazonaws.com/nexus-backend:latest
docker push YOUR_ACCOUNT.dkr.ecr.us-east-1.amazonaws.com/nexus-backend:latest
# repeat for frontend, ai-ops, ai-intel
```

**Step 2 — Deploy with ECS Compose**
```bash
ecs-cli compose --project-name nexus up
```

**Step 3 — Or use ECS with individual services**
- Create a task definition per service
- Use the ECR images
- Wire up service discovery + load balancer

> ECS is more complex. **Start with EC2 + Compose** unless you need scaling.

### 5.3 Google Cloud (GCP)

**Option A — Compute Engine (single VM)** — same as EC2:
```bash
gcloud compute ssh --zone us-central1-a nexus-vm
sudo apt update && sudo apt install -y docker.io docker-compose-v2 git
git clone https://github.com/Mr-Mayank-Sharma/Nexus-docker.git
cd Nexus-docker && cp .env.example .env && nano .env
docker compose up -d --build
```

**Option B — Cloud Run / GKE** — push images to GCR:
```bash
gcloud auth configure-docker
docker tag nexus-backend gcr.io/YOUR_PROJECT/nexus-backend:latest
docker push gcr.io/YOUR_PROJECT/nexus-backend:latest
# deploy via Cloud Run service or GKE pod
```

### 5.4 Azure

**Option A — Azure VM** — same as EC2:
```bash
az vm ssh --resource-group nexus --name nexus-vm
sudo apt update && sudo apt install -y docker.io docker-compose-v2 git
git clone https://github.com/Mr-Mayank-Sharma/Nexus-docker.git
cd Nexus-docker && cp .env.example .env && nano .env
docker compose up -d --build
```

**Option B — Azure Container Apps / AKS** — push to ACR:
```bash
az acr login --name nexus
docker tag nexus-backend nexus.azurecr.io/nexus-backend:latest
docker push nexus.azurecr.io/nexus-backend:latest
```

### 5.5 DigitalOcean / Linode / Hetzner

All are single-VM providers — identical to the EC2 flow:

```bash
# SSH in, then:
sudo apt update && sudo apt install -y docker.io docker-compose-v2 git
git clone https://github.com/Mr-Mayank-Sharma/Nexus-docker.git
cd Nexus-docker && cp .env.example .env && nano .env
docker compose up -d --build
```

---

## 6. Environment Matrix

Run **separate instances** for Dev, UAT, and Prod. Each gets its own `.env`,
its own data volumes, and (ideally) its own host.

| Environment | Purpose | Host | Data | CORS | Profile |
|-------------|---------|------|------|------|---------|
| **Dev** | Active development | Local / shared VM | Throwaway (reset often) | `http://localhost:5173` | `dev` |
| **UAT** | User acceptance testing | Staging server | Persistent (test data) | `http://uat.example.com` | `prod` |
| **Prod** | Live production | Dedicated server | Persistent (real data) | `https://app.example.com` | `prod` |

### 6.1 Dev instance

```bash
# Local dev — use the dev profile, allow localhost CORS
cp .env.example .env
# .env:
#   SPRING_PROFILES_ACTIVE=dev
#   CORS_ORIGINS=http://localhost:5173,http://localhost:3000
#   NEXUS_AI_ENABLED=false
docker compose up -d --build
```

### 6.2 UAT instance

```bash
# Staging — prod profile but test data
cp .env.example .env
# .env:
#   SPRING_PROFILES_ACTIVE=prod
#   CORS_ORIGINS=https://uat.example.com
#   NEXUS_AI_ENABLED=true
docker compose up -d --build
```

### 6.3 Prod instance

```bash
# Production — hardened, real data
cp .env.example .env
# .env:
#   SPRING_PROFILES_ACTIVE=prod
#   CORS_ORIGINS=https://app.example.com
#   NEXUS_AI_ENABLED=true
#   NEXUS_LOG_LEVEL=WARN
docker compose up -d --build
```

---

## 7. Sizing by Record Volume

Nexus scales from a small demo to high-volume production. Use this table to
pick the right server size. "Records" = orders + inventory + shipments +
returns processed per day.

### 7.1 Sizing matrix

| Tier | Daily Records | vCPU | RAM | Disk | Recommended Host |
|------|---------------|------|-----|------|------------------|
| **S** (Demo/POC) | < 1,000 | 2 | 8 GB | 30 GB | Laptop / t3.medium |
| **M** (Small prod) | 1k – 10k | 4 | 16 GB | 50 GB | t3.xlarge / 4 vCPU VPS |
| **L** (Medium prod) | 10k – 100k | 8 | 32 GB | 100 GB | t3.2xlarge / 8 vCPU |
| **XL** (Large prod) | 100k – 1M | 16 | 64 GB | 200 GB | Dedicated / ECS cluster |
| **XXL** (Enterprise) | 1M+ | 32+ | 128 GB+ | 500 GB+ | Kubernetes / ECS + scaling |

### 7.2 What drives resource usage

| Component | Resource driver | Scales with |
|-----------|-----------------|-------------|
| **Backend (Java)** | CPU + RAM | Concurrent API calls, order volume |
| **PostgreSQL** | Disk + RAM | Total records, write throughput |
| **Redis** | RAM | Session count, cache size |
| **Kafka** | Disk + RAM | Event throughput (order lifecycle) |
| **AI Ops / AI Intel** | CPU + RAM | Prediction frequency, model size |
| **Prometheus / Grafana** | Disk | Metric retention |

### 7.3 Case studies

**Case A — Small merchant (500 orders/day)**
- 2 vCPU / 8 GB / 30 GB — works on a laptop or t3.medium
- Single node, all 9 services
- Kafka single broker, no replication

**Case B — Regional 3PL (5,000 orders/day)**
- 4 vCPU / 16 GB / 50 GB — t3.xlarge
- Single node, all 9 services
- Enable AI (order routing, demand forecast)
- Daily DB backup

**Case C — National retailer (50,000 orders/day)**
- 8 vCPU / 32 GB / 100 GB — t3.2xlarge
- Consider splitting: DB on one host, app+AI on another
- Kafka with replication, Redis with persistence
- Hourly DB backup + point-in-time recovery

**Case D — Enterprise / multi-tenant (500,000+ orders/day)**
- 16+ vCPU / 64 GB+ / 200 GB+ — dedicated cluster
- ECS/Kubernetes with horizontal scaling
- Separate DB cluster (RDS/Aurora), managed Kafka (MSK)
- Full HA: load balancer, multiple app replicas, DB replication

---

## 8. Security Hardening

The compose ships with production defaults, but harden further for live use:

1. **Strong secrets** — `openssl rand -base64 48` for `DB_PASSWORD`, `JWT_SECRET`, `GRAFANA_ADMIN_PASSWORD`.
2. **Never commit `.env`** — it's gitignored. Use a secrets manager in prod.
3. **TLS everywhere** — terminate at a reverse proxy (see below).
4. **Restrict CORS** — set `CORS_ORIGINS` to your exact domain(s).
5. **Don't expose admin ports** — keep 8080/3001/9090 behind the proxy.
6. **DB access** — postgres is on `5433`; don't expose it publicly.
7. **Kafka** — internal only; don't expose `9092` publicly.
8. **AI keys** — `OPENAI_API_KEY` etc. stay in `.env`, never in code.
9. **Logging** — set `NEXUS_LOG_LEVEL=WARN` in prod to reduce noise.
10. **Backups** — automate DB + volume backups (see Section 10).

---

## 9. TLS / Reverse Proxy

For production, put nginx (or Caddy) in front and terminate TLS.

### 9.1 nginx reverse proxy (with Let's Encrypt)

```nginx
# /etc/nginx/sites-available/nexus
server {
    listen 80;
    server_name app.example.com;
    return 301 https://$host$request_uri;
}

server {
    listen 443 ssl;
    server_name app.example.com;

    ssl_certificate     /etc/letsencrypt/live/app.example.com/fullchain.pem;
    ssl_certificate_key /etc/letsencrypt/live/app.example.com/privkey.pem;

    location / {
        proxy_pass http://localhost:80;          # frontend
        proxy_set_header Host $host;
        proxy_set_header X-Real-IP $remote_addr;
    }

    location /api/ {
        proxy_pass http://localhost:8080;        # backend
        proxy_set_header Host $host;
        proxy_set_header X-Real-IP $remote_addr;
    }
}
```

### 9.2 Get a cert

```bash
sudo apt install -y certbot python3-certbot-nginx
sudo certbot --nginx -d app.example.com
```

---

## 10. Backup & Disaster Recovery

### 10.1 PostgreSQL backup

```bash
# Manual
docker exec nexus-postgres pg_dump -U nexus -d nexus_oms > backup.sql

# Cron (daily at 2am)
0 2 * * * docker exec nexus-postgres pg_dump -U nexus -d nexus_oms | gzip > /backups/nexus_$(date +\%F).sql.gz
```

### 10.2 Volume backup

```bash
# Back up named volumes
docker run --rm -v nexus_postgres-data:/data -v /backups:/backup \
  alpine tar czf /backup/postgres-data.tar.gz -C /data .
```

### 10.3 Restore

```bash
# Restore DB
cat backup.sql | docker exec -i nexus-postgres psql -U nexus -d nexus_oms

# Restore volume
docker run --rm -v nexus_postgres-data:/data -v /backups:/backup \
  alpine tar xzf /backup/postgres-data.tar.gz -C /data
```

### 10.4 Disaster recovery checklist

1. Re-provision the server (same OS/arch).
2. Install Docker + Compose.
3. Clone the repo, restore `.env`.
4. `docker compose up -d --build`.
5. Restore DB + volumes.
6. Verify healthcheck.

---

## 11. Troubleshooting

### Backend won't start
```bash
docker logs nexus-backend | tail -50
```
- **`UnsatisfiedDependencyException` for `AmazonS3`** → set `NEXUS_S3_ENABLED=true` (the app requires the S3 bean).
- **`password authentication failed`** → stale postgres volume. Reset: `docker compose down -v && docker compose up -d`.

### Frontend unhealthy
```bash
docker logs nexus-frontend | tail -20
```
- The healthcheck uses `curl` (BusyBox `wget` returns exit 1). Rebuild: `docker compose build frontend`.

### CORS errors in browser
- Set `CORS_ORIGINS` in `.env` to your exact frontend origin and restart backend.

### Prometheus shows targets "down"
- Backend `/actuator/prometheus` requires ADMIN auth; kafka/redis/postgres need exporters. See `monitoring/prometheus/prometheus.yml` for how to enable.

### Reset everything
```bash
docker compose down -v && docker compose up -d --build
```

---

*Generated for the Nexus OMS & Supply Chain Platform. See also:
[Onboarding Guide](ONBOARDING.md) · [Sizing Guide](SIZING.md) ·
[Environment Guide](ENVIRONMENTS.md) · [Operations Guide](OPERATIONS.md).*