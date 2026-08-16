# NexusShip OMS — Cost & TCO

**The honest math for a mid-market omnichannel retailer comparing
NexusShip to Manhattan, SAP, Oracle, Blue Yonder.**

| | NexusShip (self-host) | Manhattan Active OM/WMS | SAP S/4HANA + EWM | Blue Yonder |
|---|---|---|---|---|
| License / SaaS (yr) | **$0** | ~$500k–2M+ | ~$1M+ | ~$1M+ |
| Implementation | **$0 (DIY, ~1h boot)** | 6–18 mo, consultants, 6–7 figures | 6–18 mo, SI team | 6–18 mo |
| Annual maintenance / infra | **$0–1.3k** (self-hosted VM) | 22% of license | 20–25% of license | 20–25% |
| First order live | **~1 hour** | 6–18 months | 12–24 months | 6–18 months |
| Lock-in | none (open source, your cloud) | high | very high | high |

> Source: vendor pricing guidance + public benchmarks; competitor rows
> are rounded directional ranges, not quotes. NexusShip numbers are
> actual artifacts in this repo.

## Self-host TCO model

Assumes a mid-market deployment (2 replicas backend, 1 node).

| Line item | Monthly |
|---|---|
| Compute (4 vCPU / 8GB, cloud VM) | ~$60 |
| Managed Postgres (optional) | ~$15–50 |
| Object storage (backups, ~10GB) | ~$0.50 |
| **Total, self-hosted** | **≈ $76–110/mo** |

### What the $76–110/mo buys

- Full OMS: omnichannel intake (Shopify/Amazon/Magento/BigCommerce
  webhooks), order lifecycle (approval/parked/rejection), BOPIS pickup,
  ship-from-store / endless aisle.
- Fulfillment: waves → pick → pack → ship, yard/dock appointments,
  carriers, rate shopping, freight audit, trailers.
- Inventory: multi-location, ATP engine, transfer orders.
- RMA: inspect → disposition → refund (idempotent QuickBooks sync).
- Procurement, invoicing/AR, 3PL billing + client portal.
- EDI (850/856/855/810/940/997), integration hub (13 connector types),
  AI (deterministic + ML forecast, trained on real data).
- Monitoring: Prometheus + Grafana + alerting included.

## Cloud-hosted option

| Option | Monthly | Notes |
|---|---|---|
| DIY on your cloud | ~$80–300 | Full control, cheapest |
| Managed (we host for you) | TBD (planned) | Managed Postgres + TLS + backups |

## Elasticity / scale

- Backend is stateless (JWT): scale horizontally by adding replicas.
- Postgres handles read replicas for analytics.
- Redis cluster mode beyond ~10k concurrent sessions.
- See `docs/DEPLOYMENT.md` §9 (scaling) and §11 (Kubernetes).

## Why this is honest

The **track record / production deployments** row is 3/10 in the
scorecard (`07-SCM-COMPARISON.md`) — we do not claim deployments we
don't have. What we do claim is **code-verified**: every capability
listed above ships in this repository with tests. The cost advantage is
structural (open source, self-hosted) and reproducible in ~1 hour:
see `08-SELF-HOST-BOOT.md`.
