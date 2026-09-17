# Nexus OMS — Documentation Index

AI-native, multi-tenant Order Management System unifying **commerce → inventory → fulfillment → shipping → returns → procurement → finance → AI**.

> **Quick answers:**
> - "**I'm new here — how do I learn the system?**" → [**MASTER-ONBOARDING-GUIDE.md**](./MASTER-ONBOARDING-GUIDE.md) ⭐
> - "**How do I set up, connect, deploy, and run everything in production?**" → [**NEXUS-COMPLETE-GUIDE.md**](./NEXUS-COMPLETE-GUIDE.md) ⭐
> - "What is this / what's the dream?" → [00-VISION-DREAM.md](./00-VISION-DREAM.md)
> - "What works today?" → [01-CURRENT-STATE.md](./01-CURRENT-STATE.md)
> - "What's the tech stack?" → [02-TECHNICAL-ARCHITECTURE.md](./02-TECHNICAL-ARCHITECTURE.md)
> - "What tables/entities exist?" → [03-ER-DIAGRAM.md](./03-ER-DIAGRAM.md)
> - "What can a user do?" → [04-USE-CASES.md](./04-USE-CASES.md)
> - "How does data move?" → [05-DATA-FLOW.md](./05-DATA-FLOW.md)
> - "Who can access what?" → [06-BUSINESS-FLOW-RBAC.md](./06-BUSINESS-FLOW-RBAC.md)
> - "How do we compare vs Manhattan, SAP, HotWax?" → [07-SCM-COMPARISON.md](./07-SCM-COMPARISON.md)
> - "How do I run the whole thing today?" → [08-SELF-HOST-BOOT.md](./08-SELF-HOST-BOOT.md)
> - "What does it cost?" → [09-COST-TCO.md](./09-COST-TCO.md)

## Library map

| Doc | Purpose | Audience |
|---|---|---|
| [`MASTER-ONBOARDING-GUIDE.md`](./MASTER-ONBOARDING-GUIDE.md) | **⭐ Onboarding guide** — learn the system: architecture, order lifecycle, code walkthroughs, exercises | New hires, engineering, all |
| [`NEXUS-COMPLETE-GUIDE.md`](./NEXUS-COMPLETE-GUIDE.md) | **⭐ Complete platform guide** — setup, connect, deploy, operate in production | All, ops, buyers |
| [`00-VISION-DREAM.md`](./00-VISION-DREAM.md) | Vision, dream product, **market numbers** (OMS/WMS/AI), roadmap, moat | Founders, investors, exec |
| [`01-CURRENT-STATE.md`](./01-CURRENT-STATE.md) | Honest snapshot: what works, known gaps (G1–G7), Phase 2.5 hardening | Engineering, PM |
| [`02-TECHNICAL-ARCHITECTURE.md`](./02-TECHNICAL-ARCHITECTURE.md) | Stack, layers, integration hub, AI platform, security, deployment | Engineering |
| [`03-ER-DIAGRAM.md`](./03-ER-DIAGRAM.md) | ~157 entities: domain catalog + per-domain Mermaid ER diagrams | Engineering, DBA |
| [`04-USE-CASES.md`](./04-USE-CASES.md) | UC-01…UC-42 actor/precondition/flow/result catalogue | PM, QA, Engineering |
| [`05-DATA-FLOW.md`](./05-DATA-FLOW.md) | End-to-end data movement (intake, fulfillment, integrations, AI, returns, procurement, yard) | Engineering, QA |
| [`06-BUSINESS-FLOW-RBAC.md`](./06-BUSINESS-FLOW-RBAC.md) | **Who can access what**: 14 roles, 39 path→resource mappings, seeded permission matrix, flow-stage access tables | All |
| [`07-SCM-COMPARISON.md`](./07-SCM-COMPARISON.md) | Deep-research comparison vs **12 SCM/OMS/WMS systems** (Manhattan, HotWax, SAP, Oracle, Blue Yonder…), feature scores /10, weighted rankings, Nexus gap analysis | All, exec, investors |
| [`08-SELF-HOST-BOOT.md`](./08-SELF-HOST-BOOT.md) | **Production in under 1 hour** — step-by-step boot of the full stack, monitoring, teardown | Ops, buyers, evaluation teams |
| [`09-COST-TCO.md`](./09-COST-TCO.md) | **Cost & TCO** — $0 license, self-host math (~$76–110/mo), vs enterprise vendors | Buyers, CFOs, investors |
| [`features/`](./features/) | Per-feature deep dives (overview, process, use cases, data flow, ER subset, access) | All |

### Feature docs

| # | Feature | Doc |
|---|---|---|
| 1 | Order Management & Omnichannel | [`01-order-management.md`](./features/01-order-management.md) |
| 2 | Inventory & Available-to-Promise | [`02-inventory-atp.md`](./features/02-inventory-atp.md) |
| 3 | Fulfillment (Wave → Pick → Pack → Ship) | [`03-fulfillment-wave-pick-pack-ship.md`](./features/03-fulfillment-wave-pick-pack-ship.md) |
| 4 | Shipping, Carriers & Yard | [`04-shipping-carriers-yard.md`](./features/04-shipping-carriers-yard.md) |
| 5 | Returns & Refunds (RMA) | [`05-returns-refunds.md`](./features/05-returns-refunds.md) |
| 6 | Procurement & Suppliers | [`06-procurement-suppliers.md`](./features/06-procurement-suppliers.md) |
| 7 | Warehouse & Automation | [`07-warehouse-automation.md`](./features/07-warehouse-automation.md) |
| 8 | Finance, Billing & Payments | [`08-finance-billing-payments.md`](./features/08-finance-billing-payments.md) |
| 9 | Analytics & Reporting | [`09-analytics-reporting.md`](./features/09-analytics-reporting.md) |
| 10 | Integrations Hub, EDI & iPaaS | [`10-integrations-edi-ipaas.md`](./features/10-integrations-edi-ipaas.md) |
| 11 | AI Platform | [`11-ai-platform.md`](./features/11-ai-platform.md) |
| 12 | Import / Export Engine | [`12-import-export-engine.md`](./features/12-import-export-engine.md) |
| 13 | Auth, RBAC & Security | [`13-auth-rbac-security.md`](./features/13-auth-rbac-security.md) |
| 14 | Omnichannel (BOPIS, Pickup, Endless Aisle) | [`14-omnichannel-bopis-endless-aisle.md`](./features/14-omnichannel-bopis-endless-aisle.md) |

## Pre-existing docs (kept)

| Doc | Topic |
|---|---|
| [`business-process.md`](./business-process.md) | Original 14-role business process narrative |
| [`ai-business-process.md`](./ai-business-process.md) | AI-driven processes |
| [`implementation.md`](./implementation.md) | Implementation notes |
| [`ENTERPRISE-AI-ARCHITECTURE.md`](./ENTERPRISE-AI-ARCHITECTURE.md) | Deep AI architecture |
| [`API.md`](./API.md) | API reference |
| [`DEPLOYMENT.md`](./DEPLOYMENT.md) | Deployment guide (compose + Kubernetes/Helm + backup/PITR) |
| [`GAP_CLOSURE_PLAN.md`](./GAP_CLOSURE_PLAN.md) | Gap closure plan |
| [`testing-guide.md`](./testing-guide.md) | Testing guide |
| [`FIX_LOG.md`](../FIX_LOG.md) | Change/hardening log (incl. Phase 2.5 honesty sweep) |

## Project artifacts & reports

Historical planning documents, analysis reports, and non-code assets — consolidated out of the repo root.

| Path | Contents |
|---|---|
| [`reports/`](./reports/) | Implementation report, repo analysis (`nexus-analysis.md`), DB migration guide (`MIGRATION.md`), legacy deployment guide (`DEPLOYMENT-legacy.md` — superseded by [`DEPLOYMENT.md`](./DEPLOYMENT.md)) |
| [`blueprints/`](./blueprints/) | Original product blueprints: `NexusShip_Complete_Master_Blueprint.md`, `nexus-10out10-prompt.md` |
| [`assets/`](./assets/) | UI screenshots (`UI.png`) |
| [`samples/sample_import_files/`](./samples/sample_import_files/) | CSV import fixtures for all 15 entity types incl. malformed-file test cases; generated by `scripts/generate_sample_data.py`, consumed by `scripts/run_imports.sh` |

## Legend used in these docs
- **Access table** values: 👁 view · ✍ create · ✎ edit · ✖ delete · * = ADMIN wildcard · – = no permission.
- **ER diagrams** are logical (FK-by-UUID-column); most entities relate via plain `UUID` FK fields, not JPA object graphs.
