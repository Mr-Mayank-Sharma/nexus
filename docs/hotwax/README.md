# HotWax Commerce — Deep Analysis & Nexus Market-Readiness

> **Purpose:** Reverse-engineer HotWax Commerce from real meeting transcripts (Read AI) to understand *how they operate as a company* — architecture, management, sales process, demo calls, client acquisition, and pricing — then use that intelligence to build a **market-readiness implementation plan for Nexus OMS**.

*Source: 11 real HotWax customer/partner meetings transcribed via Read AI (see `../readai-analysis/transcripts/`). All analysis is read-only; nothing was posted back to Read AI.*

---

## Why This Matters

HotWax Commerce is a **real, revenue-generating OMS** with a live customer base (gorjana, Rails, SIMKHAI, Chelan Fresh, Spoonful of Comfort, Lovers, ADOC, Mephisto, Krewe, Agape, Winning, Outfitters, Able Aerospace, Dollskill, Malbon). Their meetings reveal:

1. **What the market actually buys** — the exact workflows, integrations, and pain points that close deals.
2. **How they sell** — demo-driven sales, per-order pricing, consulting upsells, phased rollouts.
3. **Their weaknesses** — reactive configuration, no standard go-live checklist, weak roll-off, import performance, sync conflicts.
4. **Where Nexus can win** — the gaps HotWax leaves open are Nexus's market entry points.

---

## Document Index

| Document | What it covers |
|----------|----------------|
| [`hotwax-company-analysis.md`](./hotwax-company-analysis.md) | Deep dive: HotWax business model, architecture, management, operations, pricing, weaknesses |
| [`hotwax-sales-demo-playbook.md`](./hotwax-sales-demo-playbook.md) | How HotWax runs demo calls, finds clients, and closes deals — the sales playbook |
| [`hotwax-marketing-strategy.md`](./hotwax-marketing-strategy.md) | How HotWax markets itself (NRF strategy, content engine, partner channel, pricing) — the marketing playbook |
| [`soc-wms-project.md`](./soc-wms-project.md) | Deep dive on the Spoonful of Comfort (SoC) WMS project — the flagship fulfillment/WMS engagement |
| [`nexus-market-readiness-plan.md`](./nexus-market-readiness-plan.md) | **The main deliverable** — detailed implementation plan to make Nexus market-ready |
| [`nexus-vs-hotwax-competitive.md`](./nexus-vs-hotwax-competitive.md) | Head-to-head competitive positioning and where Nexus wins/loses |
| [`nexus-business-process-alignment.md`](./nexus-business-process-alignment.md) | **The real market flows** — 16 actual operational business processes extracted from the transcripts, mapped to Nexus's current state (covered / partial / missing) |
| [`nexus-market-gap-tickets.md`](./nexus-market-gap-tickets.md) | **P0 implementation tickets (Round 1)** — the 7 market-defining gaps (replenishment, picker analytics, transit-time, store ops, TO receiving/fulfillment, packing concurrency) turned into buildable tickets with real-flow acceptance criteria |
| [`nexus-market-gap-tickets-2.md`](./nexus-market-gap-tickets-2.md) | **Implementation tickets (Round 2)** — 6 more gaps from the RFID, Shopify↔NetSuite, bidirectional-sync, returns/exchange, MCP, and shipping-label transcripts (T-08 → T-13) + T-03 expansion, with detailed plans |
| [`nexus-p0-implementation-specs.md`](./nexus-p0-implementation-specs.md) | **P0 build specs** — full engineering detail for T-09 (fulfillment reconciliation) and T-12 (carrier labels): Flyway V61/V62, Spring Boot services, REST endpoints, React UI, acceptance tests |
| [`nexus-p1-implementation-specs.md`](./nexus-p1-implementation-specs.md) | **P1 build specs** — full engineering detail for T-08 (RFID/serialized inventory), T-10 (returns/exchange finance), T-11 (MCP server), T-13 (bidirectional sync & conflict handling): Flyway V63–V66, Spring Boot services, REST endpoints, React UI, acceptance tests |

---

## TL;DR — The 5 Things That Matter Most

1. **HotWax sells demos, not documents.** Every deal (SIMKHAI, Dollskill, Lovers, gorjana) starts with a targeted product demo. Nexus must build a **repeatable demo script** per buyer persona.

2. **HotWax's pricing is per-order + consulting.** ~$750/mo for >2,500 orders, $1k implementation, $10k customizations, plus paid consulting (routing, RFID, implementation coordination). Nexus needs a **commercial model**, not just a product.

3. **Routing is the heart of the OMS.** Fulfillment routing / order brokering appears in *every* account. Nexus has DOM routing — this is the #1 differentiator to lead with.

4. **HotWax's weaknesses are Nexus's openings.** Reactive config, no go-live checklist, weak client roll-off, import performance, sync conflicts. Nexus can win on **predictability, self-serve onboarding, and honest data**.

5. **SoC is the WMS reference.** The Spoonful of Comfort project shows the full WMS depth (replenishment, picker analytics, holiday planning, shipping labels) that a market-ready OMS/WMS needs.

---

## Method

1. Pulled full transcripts (summary, action items, topics) from Read AI for 11 key meetings.
2. Extracted HotWax's **business model, sales process, architecture, management, and operational patterns**.
3. Mapped each against **Nexus OMS** current capabilities (Spring Boot OMS: orders, inventory, shipments, returns, import engine, analytics, AI, Kafka events, webhooks, DOM routing, BOPIS, ATP, mobile RF).
4. Produced this market-readiness analysis and implementation plan.
