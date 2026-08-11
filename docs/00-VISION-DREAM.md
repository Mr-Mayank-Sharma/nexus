# Nexus OMS — Vision, Dream & Market

> *"One platform. Every order. Zero waste."*

**Document status:** Living strategy document  
**Audience:** Founders, investors, executive team, product & engineering  
**Last updated:** August 2026

---

## 1. The Idea

**Nexus OMS is an AI-native, multi-tenant Order Management System (OMS) that unifies the entire commerce-to-dispatch pipeline** — orders, inventory, fulfillment, shipping, returns, procurement, warehouse automation, finance and analytics — into a single, real-time platform for retailers, brands, 3PLs and omnichannel merchants.

Most mid-market businesses run their commerce on **4–7 disconnected systems**: an ecommerce platform (Shopify/BigCommerce), a marketplace feed (Amazon/eBay/Walmart), an order manager, a WMS, a carrier dashboard, a spreadsheet for inventory and an accountant's inbox for returns. Every hand-off is a reconciliation problem. Every reconciliation is a cost. Every cost is a delay. Every delay is a lost customer.

Nexus collapses that fragmentation into one product with **three convictions**:

1. **Honest data, always.** No mock numbers, no fabricated metrics, no pretend AI. Every number on screen traces back to a real event or a clearly-labelled rule. (See the "fabricated randomness removal" hardening in `FIX_LOG.md`, Phase 2.5.)
2. **AI where it pays, rules where it's deterministic.** Demand forecasting, order routing, slotting, packing, truck loading and briefing get ML; everything else gets deterministic, auditable rules.
3. **Omnichannel by design.** Store, warehouse, BOPIS, endless aisle and marketplace are first-class citizens — not bolt-ons.

---

## 2. The Dream

The long-term dream: **an operating system for the modern retail supply chain** that any business — from a 3-person brand to a multi-national 3PL — can switch on in an afternoon and run like a Fortune-500 operation.

### 2.1 North-star scenarios (what "dream achieved" looks like)

| Scenario | Today's reality | Dream state |
|---|---|---|
| A customer orders at 9:04 pm | Order sits until morning review | AI routing allocates stock in <1s; order is in a pick wave by 9:10 pm |
| Stock goes negative in a store | Detected on next month's recount | Real-time ATP + cycle counting triggers a transfer before the shelf is empty |
| A carrier misses a pickup | Nobody knows until a customer asks | Yard/trailer visibility + exception alerting escalate in minutes |
| A wave is mis-batched | Pickers walk the same aisle 3× | AI wave planning + slotting optimizes batch → fewer km walked per order |
| A return arrives damaged | Support re-opens a 6-tab investigation | AI disposition suggests refund-vs-reject with confidence + audit trail |
| CEO asks "why did costs spike?" | Finance spends 2 weeks building a report | AI briefing answers with data, drill-downs and anomalies |
| New sales channel launches | 6 weeks of integration work | Connector hub + iPaaS flow configures it in a day |

### 2.2 The 2030 dream product

- **Intelligent order orchestration** — every order is routed, brokered and fulfilled by the cheapest/fastest path the network can promise (ATP-aware).
- **Self-optimizing warehouse** — slotting, wave planning, labor balancing and automation (ASRS/AMR/conveyor) continuously re-tune themselves.
- **A governed AI platform** — model registry, training pipeline, experiments, monitoring, drift detection, rule fallbacks and a full audit trail; the customer controls every decision.
- **One-touch commerce integrations** — Shopify, BigCommerce, Amazon, eBay, Walmart, EDI, email parsing, plus a self-service iPaaS (flows, transforms, validation, DLQ).
- **Trustworthy by default** — real metrics or none, signed imports, tenant isolation, RBAC with 14 roles, MFA/SSO.

---

## 3. The Market — By the Numbers

> Figures below are compiled from public market research (sources cited inline). All are **approximate** and for planning purposes only.

### 3.1 Order Management Systems (our core market)

- The global **Order Management market** is projected at **≈ $3.8–6.2 B in 2025**, reaching **≈ $6.8–9.1 B by 2030–2034**, growing at **≈ 10–13% CAGR**. *(Dataintelo: $3.8B 2025 → $9.1B 2034 @ 10.2% CAGR; Virtue Market Research: $2.9B 2024 → $6.1B 2030 @ 13.2% CAGR; Netguru: $6.2B 2025.)*
- **Multichannel order management** (our exact wedge) was **$2.95 B in 2022 → $6.86 B by 2030**, **11.6% CAGR**. *(Netguru.)*
- Retail is the **top end-user at ~29%** of OMS revenue; cloud deployment is the fastest-growing segment (**13.6% CAGR**) and will be **>74% of revenue by 2034**. *(Dataintelo.)*
- **Asia-Pacific is the fastest-growing region** at **~13% CAGR**; North America leads revenue at **~38%**. *(Dataintelo, Marketintelo.)*

### 3.2 Warehouse Management (adjacent, expanded TAM)

- **WMS market: $4.57 B in 2025 → $10.04 B by 2030** at **17.1% CAGR**. *(MarketsandMarkets.)*
- Broader estimates range **$3.4–4.6 B in 2025 → $10–20 B by 2030–2035** at **14–22% CAGR**. *(MarketsandMarkets, Grand View, EMR.)*
- Order fulfillment & distribution is the **fastest-growing application**; 3PL is the dominant end-user. *(MarketsandMarkets.)*

### 3.3 AI in Supply Chain (our differentiation layer)

- **AI in supply chain: $13.93 B in 2025 → $50.41 B by 2032** at **20.2% CAGR**. *(MarketsandMarkets.)*
- **Smart warehousing: $31.21 B in 2025 → $46.42 B by 2030**, **8.3% CAGR** — AGVs, AMRs, AS/RS, sorters, AI + IoT are the growth vectors. *(MarketsandMarkets.)*
- **57% of logistics firms plan to adopt smart-warehouse solutions by 2030**; **>26% of warehouses will be automated by 2027**. *(EMR.)*

### 3.4 Why now (demand-side tailwinds)

- **Omnichannel complexity:** organizations now orchestrate **5+ simultaneous selling channels**, making order orchestration critical. *(Dataintelo.)*
- **B2B digitization:** B2B commerce processed **over $12.2 T in 2025**. *(Dataintelo.)*
- **Mobile-first commerce:** APAC does **8 of 10 digital transactions on mobile** vs ~65.7% global — driving OMS demand. *(Netguru.)*
- **Cloud + AI adoption:** cloud OMS share rising **54.7% (2025) → 74% (2034)**; AI/ML adoption in OMS is a named growth driver. *(Dataintelo.)*

### 3.5 Serviceable addressable market (SAMI-style framing)

| Segment | 2025 size | 2030 size | CAGR |
|---|---|---|---|
| Order Management (core OMS) | ~$3.8–6.2 B | ~$6.8–9.1 B | 10–13% |
| Multichannel OMS (wedge) | ~$3.4 B | ~$6.9 B | ~11.6% |
| WMS (upsell) | ~$4.6 B | ~$10 B | ~17% |
| AI in supply chain (differentiator) | ~$14 B | ~$50 B | ~20% |

**Combined planning TAM (2030): ~$70 B** across OMS + WMS + AI-in-SCM, with OMS+WMS alone ≈ **$19–20 B**.

---

## 4. How We Win (Positioning)

The incumbents (Manhattan, Blue Yonder, SAP, Oracle, Salesforce, Shopify/ShipStation, etc.) are either **enterprise-megasuite** (expensive, 6–18 month deployments, implementation armies) or **channel-specific point tools** (Shopify-only, marketplace-only). Nexus occupies the **white space**:

- **Mid-market price point** with enterprise capability (cloud-native, multi-tenant, self-serve).
- **AI-first** rather than "AI sticker" — real model registry, training, experiments, fallbacks and audit.
- **Omnichannel out of the box** — store/BOPIS/endless-aisle/warehouse/marketplace in one screen.
- **Honest by design** — no dark patterns, no fabricated metrics (a differentiator for trust-sensitive enterprises).

### 4.1 Business model

| Stream | Description |
|---|---|
| **SaaS subscriptions** | Tiered per-tenant pricing (SMB / Growth / Enterprise), monthly or annual |
| **Usage / throughput** | Fulfillment volume, AI inference, import rows |
| **Marketplace & iPaaS** | Connector packs; flow/transform seats |
| **Professional services** | Onboarding, data migration, custom flows |

### 4.2 Growth flywheel

```
More channels connected → richer order data → better AI forecasts/routing
        ↑                                                        ↓
Lower fulfillment cost per order ← smarter operations ← happier operations teams
        ↓
Reference accounts → referrals → more channels connected
```

---

## 5. Ten-Year Roadmap (Dream → Reality)

| Horizon | Theme | Deliverables |
|---|---|---|
| **Now → 12 mo** | Trust & breadth | Ship the platform hardening (done: honesty sweep), fix pre-existing test gaps, complete real ML training path, move stub endpoints to real services, polish UX on 6 permission-broken gates |
| **1–2 yr** | AI trust loop | Real training with ONNX runtime, live drift monitoring, champion/challenger auto-promote, decision audit trail for every AI action |
| **2–4 yr** | Network scale | Multi-warehouse network-level brokering, carrier rate negotiation, lane analytics, 3PL white-label tenant portals |
| **4–7 yr** | Self-driving warehouse | AMR/ASRS orchestration, autonomous wave planning, predictive maintenance, labor optimization loops |
| **7–10 yr** | Category-defining | "Supply chain copilot" — conversational OMS, autonomous exception handling, carbon-aware routing |

---

## 6. Principles That Protect the Dream

1. **Real data or nothing.** Never fabricate metrics, mock AI results, or invent numbers (enforced since Phase 2.5 hardening).
2. **Deterministic where possible, AI where valuable.** Auditable rules are the floor; ML is the ceiling.
3. **Tenant isolation is sacred.** Every query, every entity, every export is tenant-scoped.
4. **Security by default.** Signed import tokens, MFA/SSO, 14-role RBAC, rate limiting, idempotency.
5. **Progress over perfection.** Ship the honest core, then layer intelligence — never the reverse.

---

*Next: read [`01-CURRENT-STATE.md`](./01-CURRENT-STATE.md) for exactly where the platform stands today, or browse the [`features/`](./features/) catalogue.*
