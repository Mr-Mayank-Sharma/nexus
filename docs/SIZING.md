# Nexus OMS — Sizing & Capacity Planning Guide

This guide helps you size the Nexus deployment based on **record processing
volumes**. It covers multiple tiers, detailed case studies, and how to scale
each component.

---

## Table of Contents

1. [Understanding Record Volume](#1-understanding-record-volume)
2. [Sizing Tiers](#2-sizing-tiers)
3. [Detailed Case Studies](#3-detailed-case-studies)
4. [Component-Level Sizing](#4-component-level-sizing)
5. [Scaling Strategies](#5-scaling-strategies)
6. [Capacity Planning Worksheet](#6-capacity-planning-worksheet)

---

## 1. Understanding Record Volume

"Record volume" = the number of business records processed per day. For Nexus,
this includes:

| Record type | Example | Driver |
|-------------|---------|--------|
| **Orders** | Sales orders, B2B orders | Core workload |
| **Order items** | Line items per order | Multiplier (avg 2–5/order) |
| **Inventory** | Stock levels, movements | Write-heavy |
| **Shipments** | Packages, labels | Carrier calls |
| **Returns** | RMAs, refunds | Seasonal spikes |
| **Products** | SKUs | Static, but large |
| **Customers** | Accounts | Grows over time |
| **Events** | Kafka lifecycle events | Multiplier (5–10/order) |

**Rule of thumb:** total DB writes ≈ **orders × 10** (order + items + events +
inventory + shipment + notifications).

---

## 2. Sizing Tiers

| Tier | Daily Orders | Daily DB Writes | vCPU | RAM | Disk | Recommended Host |
|------|--------------|-----------------|------|-----|------|------------------|
| **S** (Demo/POC) | < 1,000 | < 10k | 2 | 8 GB | 30 GB | Laptop / t3.medium |
| **M** (Small prod) | 1k – 10k | 10k – 100k | 4 | 16 GB | 50 GB | t3.xlarge / 4 vCPU VPS |
| **L** (Medium prod) | 10k – 100k | 100k – 1M | 8 | 32 GB | 100 GB | t3.2xlarge / 8 vCPU |
| **XL** (Large prod) | 100k – 1M | 1M – 10M | 16 | 64 GB | 200 GB | Dedicated / ECS cluster |
| **XXL** (Enterprise) | 1M+ | 10M+ | 32+ | 128 GB+ | 500 GB+ | Kubernetes / ECS + scaling |

---

## 3. Detailed Case Studies

### Case A — Small merchant / demo (500 orders/day)

**Profile:** D2C brand, single Shopify store, US only, 2,000 SKUs.

| Component | Sizing |
|-----------|--------|
| Host | Laptop or t3.medium (2 vCPU / 8 GB) |
| Disk | 30 GB |
| Postgres | Single instance, default config |
| Redis | Single instance, no persistence needed |
| Kafka | Single broker, no replication |
| Backend | 1 replica |
| AI | Optional (can disable) |
| Monitoring | Prometheus + Grafana on same host |

**Config:**
```bash
# .env
SPRING_PROFILES_ACTIVE=prod
NEXUS_AI_ENABLED=false
NEXUS_CACHE_ENABLED=true
```

**Expected performance:** < 1s API latency, < 50% CPU at peak.

---

### Case B — Regional 3PL (5,000 orders/day)

**Profile:** Regional 3PL, 3 warehouses, Shopify + BigCommerce + Amazon US,
FedEx/UPS/USPS, 20,000 SKUs.

| Component | Sizing |
|-----------|--------|
| Host | t3.xlarge (4 vCPU / 16 GB) |
| Disk | 50 GB |
| Postgres | Single instance, tuned (shared_buffers=2GB) |
| Redis | Single instance, appendonly=yes |
| Kafka | Single broker, replication factor 1 |
| Backend | 1 replica |
| AI | Enabled (order routing, demand forecast) |
| Monitoring | Prometheus + Grafana |

**Config:**
```bash
# .env
SPRING_PROFILES_ACTIVE=prod
NEXUS_AI_ENABLED=true
NEXUS_CACHE_ENABLED=true
NEXUS_LOG_LEVEL=INFO
```

**Expected performance:** < 2s API latency, < 70% CPU at peak.

---

### Case C — National retailer (50,000 orders/day)

**Profile:** National retailer, 10 warehouses, multi-channel (Shopify, Amazon,
Walmart, eBay), 100,000 SKUs, 4 carriers.

| Component | Sizing |
|-----------|--------|
| Host | t3.2xlarge (8 vCPU / 32 GB) |
| Disk | 100 GB |
| Postgres | Tuned (shared_buffers=6GB, work_mem=64MB) |
| Redis | Persistence enabled, maxmemory 4GB |
| Kafka | 2 brokers, replication factor 2 |
| Backend | 2 replicas (behind LB) |
| AI | Enabled |
| Monitoring | Prometheus + Grafana, longer retention |

**Config:**
```bash
# .env
SPRING_PROFILES_ACTIVE=prod
NEXUS_AI_ENABLED=true
NEXUS_CACHE_ENABLED=true
NEXUS_LOG_LEVEL=WARN
```

**Expected performance:** < 3s API latency, < 80% CPU at peak. Consider
splitting DB to a separate host.

---

### Case D — Enterprise / multi-tenant (500,000+ orders/day)

**Profile:** Enterprise 3PL / marketplace, 50+ warehouses, global, multi-currency,
multi-tax, 1M+ SKUs, 10+ carriers.

| Component | Sizing |
|-----------|--------|
| Host | Dedicated cluster (16+ vCPU / 64 GB+ per node) |
| Disk | 200 GB+ |
| Postgres | Managed RDS/Aurora, read replicas |
| Redis | Managed (ElastiCache), cluster mode |
| Kafka | Managed (MSK), replication factor 3 |
| Backend | 4+ replicas behind load balancer |
| AI | Enabled, GPU for model training |
| Monitoring | Prometheus + Grafana, HA |

**Config:**
```bash
# .env
SPRING_PROFILES_ACTIVE=prod
NEXUS_AI_ENABLED=true
NEXUS_CACHE_ENABLED=true
NEXUS_LOG_LEVEL=WARN
```

**Expected performance:** < 5s API latency at peak, horizontal scaling.

---

## 4. Component-Level Sizing

### 4.1 PostgreSQL

| Daily Orders | shared_buffers | work_mem | max_connections |
|--------------|----------------|----------|-----------------|
| < 1k | 128 MB | 4 MB | 100 |
| 1k – 10k | 2 GB | 16 MB | 200 |
| 10k – 100k | 6 GB | 64 MB | 300 |
| 100k+ | 12 GB+ | 128 MB | 500+ |

### 4.2 Redis

| Daily Orders | maxmemory | Persistence |
|--------------|-----------|-------------|
| < 1k | 256 MB | off |
| 1k – 10k | 1 GB | appendonly |
| 10k – 100k | 4 GB | appendonly + RDB |
| 100k+ | 8 GB+ | cluster |

### 4.3 Kafka

| Daily Orders | Brokers | Replication | Retention |
|--------------|---------|-------------|-----------|
| < 1k | 1 | 1 | 7 days |
| 1k – 10k | 1 | 1 | 7 days |
| 10k – 100k | 2 | 2 | 14 days |
| 100k+ | 3+ | 3 | 30 days |

### 4.4 Backend (Java)

| Daily Orders | Replicas | Heap (Xmx) |
|--------------|----------|------------|
| < 1k | 1 | 1 GB |
| 1k – 10k | 1 | 2 GB |
| 10k – 100k | 2 | 4 GB |
| 100k+ | 4+ | 8 GB |

### 4.5 AI services

| Prediction freq | AI Ops | AI Intel |
|-----------------|--------|----------|
| Low (< 1k/day) | 1 vCPU / 2 GB | 1 vCPU / 2 GB |
| Medium (1k–10k) | 2 vCPU / 4 GB | 2 vCPU / 4 GB |
| High (10k+) | 4 vCPU / 8 GB | 4 vCPU / 8 GB |

---

## 5. Scaling Strategies

### 5.1 Vertical scaling (simplest)

Increase the host's vCPU/RAM/disk. Works up to ~100k orders/day.

```bash
# After resizing the host, just restart
docker compose up -d
```

### 5.2 Horizontal scaling (backend + AI)

Run multiple backend replicas behind a load balancer.

```bash
# docker-compose.override.yml
services:
  backend:
    deploy:
      replicas: 2
```

### 5.3 Split DB to a managed service

For high volume, move Postgres to RDS/Aurora and Redis to ElastiCache.

```bash
# .env
NEXUS_DB_URL=jdbc:postgresql://YOUR_RDS_HOST:5432/nexus_oms
NEXUS_REDIS_HOST=YOUR_ELASTICACHE_HOST
```

### 5.4 Managed Kafka (MSK)

For high volume, use AWS MSK instead of the in-compose Kafka.

```bash
# .env
NEXUS_KAFKA_BOOTSTRAP=YOUR_MSK_BOOTSTRAP
```

---

## 6. Capacity Planning Worksheet

Use this to size a new deployment:

```
1. Expected daily orders:        ______
2. Avg line items per order:     ______
3. Total SKUs:                   ______
4. Number of warehouses:         ______
5. Channels:                     ______
6. Carriers:                     ______
7. AI enabled?                   Y/N
8. Peak season multiplier:       ______ (e.g., 3x for holiday)

Estimated daily DB writes = orders × 10 = ______
Peak throughput = daily × multiplier = ______

Recommended tier:
  < 10k writes/day  → S (2 vCPU / 8 GB)
  10k–100k          → M (4 vCPU / 16 GB)
  100k–1M           → L (8 vCPU / 32 GB)
  1M–10M            → XL (16 vCPU / 64 GB)
  10M+              → XXL (32+ vCPU / 128 GB+)
```

---

*See also: [Deployment Guide](DEPLOYMENT.md) · [Onboarding Guide](ONBOARDING.md) ·
[Environment Guide](ENVIRONMENTS.md) · [Operations Guide](OPERATIONS.md).*