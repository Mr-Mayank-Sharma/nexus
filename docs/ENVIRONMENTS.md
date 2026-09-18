# Nexus OMS — Environment Setup Guide (Dev / UAT / Prod)

This guide covers setting up **multiple environments** — Dev, UAT, and Prod —
on **local machines and cloud platforms**. It includes multiple deployment
cases and best practices for environment isolation.

---

## Table of Contents

1. [Environment Overview](#1-environment-overview)
2. [Environment Isolation](#2-environment-isolation)
3. [Local Environments](#3-local-environments)
4. [Cloud Environments](#4-cloud-environments)
5. [Deployment Cases](#5-deployment-cases)
6. [Promoting Between Environments](#6-promoting-between-environments)
7. [Environment Checklist](#7-environment-checklist)

---

## 1. Environment Overview

| Environment | Purpose | Who uses it | Data | Stability |
|-------------|---------|-------------|------|-----------|
| **Dev** | Active development | Developers | Throwaway | Unstable |
| **UAT** | User acceptance testing | QA, stakeholders | Test data | Stable |
| **Prod** | Live production | End users | Real data | Critical |

### Recommended topology

```
┌──────────┐   ┌──────────┐   ┌──────────┐
│   DEV    │   │   UAT    │   │   PROD   │
│  local/  │   │  staging │   │  cloud/  │
│  shared  │   │  server  │   │  server  │
└──────────┘   └──────────┘   └──────────┘
     │              │              │
     └── promote ───┴── promote ───┘
```

---

## 2. Environment Isolation

Each environment must be **fully isolated**:

| Resource | Isolation |
|----------|-----------|
| `.env` | Separate file per environment (never shared) |
| Data volumes | Separate named volumes (or hosts) |
| Database | Separate DB (or separate schema) |
| Redis | Separate instance |
| Kafka | Separate broker |
| Ports | Different hosts, or different ports |
| CORS | Environment-specific origins |
| Secrets | Environment-specific (DB password, JWT, Grafana) |

**Never** point Dev at Prod data, or vice versa.

---

## 3. Local Environments

### 3.1 Local Dev

```bash
# Clone
git clone https://github.com/Mr-Mayank-Sharma/Nexus-docker.git
cd Nexus-docker

# Dev .env
cp .env.example .env.dev
# .env.dev:
#   SPRING_PROFILES_ACTIVE=dev
#   CORS_ORIGINS=http://localhost:5173,http://localhost:3000
#   NEXUS_AI_ENABLED=false
#   DB_PASSWORD=dev_password
#   JWT_SECRET=dev_secret_at_least_32_chars
#   GRAFANA_ADMIN_PASSWORD=dev_grafana

# Run with the dev env file
docker compose --env-file .env.dev up -d --build
```

### 3.2 Local UAT

```bash
# UAT .env
cp .env.example .env.uat
# .env.uat:
#   SPRING_PROFILES_ACTIVE=prod
#   CORS_ORIGINS=http://localhost:3000
#   NEXUS_AI_ENABLED=true
#   DB_PASSWORD=uat_password
#   JWT_SECRET=uat_secret_at_least_32_chars
#   GRAFANA_ADMIN_PASSWORD=uat_grafana

# Run with the UAT env file
docker compose --env-file .env.uat up -d --build
```

### 3.3 Local Prod (simulation)

```bash
# Prod .env
cp .env.example .env.prod
# .env.prod:
#   SPRING_PROFILES_ACTIVE=prod
#   CORS_ORIGINS=https://app.example.com
#   NEXUS_AI_ENABLED=true
#   NEXUS_LOG_LEVEL=WARN
#   DB_PASSWORD=prod_password
#   JWT_SECRET=prod_secret_at_least_32_chars
#   GRAFANA_ADMIN_PASSWORD=prod_grafana

# Run with the prod env file
docker compose --env-file .env.prod up -d --build
```

> ⚠️ **Never commit** `.env.dev`, `.env.uat`, or `.env.prod` — they contain
> secrets. Add them to `.gitignore` (already covered by `.env*` patterns).

---

## 4. Cloud Environments

### 4.1 Cloud Dev

Use a small shared VM (or a containerized dev environment).

```bash
# On the dev VM
git clone https://github.com/Mr-Mayank-Sharma/Nexus-docker.git
cd Nexus-docker
cp .env.example .env
# .env:
#   SPRING_PROFILES_ACTIVE=dev
#   CORS_ORIGINS=http://dev.example.com
#   NEXUS_AI_ENABLED=false
docker compose up -d --build
```

### 4.2 Cloud UAT

Use a staging server with test data.

```bash
# On the staging server
git clone https://github.com/Mr-Mayank-Sharma/Nexus-docker.git
cd Nexus-docker
cp .env.example .env
# .env:
#   SPRING_PROFILES_ACTIVE=prod
#   CORS_ORIGINS=https://uat.example.com
#   NEXUS_AI_ENABLED=true
docker compose up -d --build
```

### 4.3 Cloud Prod

Use a dedicated production server (or cluster).

```bash
# On the production server
git clone https://github.com/Mr-Mayank-Sharma/Nexus-docker.git
cd Nexus-docker
cp .env.example .env
# .env:
#   SPRING_PROFILES_ACTIVE=prod
#   CORS_ORIGINS=https://app.example.com
#   NEXUS_AI_ENABLED=true
#   NEXUS_LOG_LEVEL=WARN
docker compose up -d --build
```

---

## 5. Deployment Cases

### Case 1 — All environments on one machine (small team)

Run Dev, UAT, and Prod on the same machine using **different ports** and
**different env files**.

```bash
# Dev on ports 8081/81
docker compose --env-file .env.dev -p nexus-dev up -d --build

# UAT on ports 8082/82
docker compose --env-file .env.uat -p nexus-uat up -d --build

# Prod on ports 8080/80
docker compose --env-file .env.prod -p nexus-prod up -d --build
```

> ⚠️ Requires editing the compose ports per environment, or using an override
> file. Not recommended for real production isolation.

### Case 2 — Separate machines per environment (recommended)

| Environment | Host | Purpose |
|-------------|------|---------|
| Dev | Dev VM / laptop | Development |
| UAT | Staging server | Testing |
| Prod | Production server | Live |

Each host runs one environment with its own `.env` and data. This is the
**recommended** setup for isolation and safety.

### Case 3 — Cloud-managed environments (enterprise)

| Environment | Cloud service |
|-------------|---------------|
| Dev | Cloud dev container / ephemeral |
| UAT | Staging VM / container |
| Prod | Production cluster (ECS/K8s) |

Use CI/CD to promote images: Dev → UAT → Prod.

---

## 6. Promoting Between Environments

### 6.1 Promote code

```bash
# From the repo
git pull origin main
docker compose up -d --build
```

### 6.2 Promote data (Dev → UAT)

```bash
# Dump Dev DB
docker exec nexus-postgres pg_dump -U nexus -d nexus_oms > dev.sql

# Restore to UAT
cat dev.sql | docker exec -i nexus-postgres psql -U nexus -d nexus_oms
```

### 6.3 Promote config

Copy the `.env` from the source environment, update environment-specific values
(secrets, CORS, URLs), and apply.

---

## 7. Environment Checklist

### Dev
- [ ] Dev profile active
- [ ] Localhost CORS
- [ ] AI disabled (or mock)
- [ ] Throwaway data
- [ ] Fast iteration

### UAT
- [ ] Prod profile active
- [ ] Test CORS
- [ ] AI enabled
- [ ] Persistent test data
- [ ] Mirrors prod config

### Prod
- [ ] Prod profile active
- [ ] Production CORS
- [ ] AI enabled
- [ ] Real data
- [ ] Backups configured
- [ ] Monitoring active
- [ ] TLS in place
- [ ] Secrets rotated

---

*See also: [Deployment Guide](DEPLOYMENT.md) · [Onboarding Guide](ONBOARDING.md) ·
[Sizing Guide](SIZING.md) · [Operations Guide](OPERATIONS.md).*