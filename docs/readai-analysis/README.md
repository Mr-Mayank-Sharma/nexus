# Read AI Meeting Analysis — Nexus OMS vs. Real Market Operations

This directory compares **real-market retail operations** (captured from 11 HotWax Commerce customer/partner meetings transcribed via Read AI) against the **Nexus OMS** platform's current capabilities.

The goal: identify where real-world OMS requirements map cleanly onto Nexus, where Nexus has gaps, and what to build or prioritize next.

## Source Transcripts

Raw transcripts live in [`transcripts/`](./transcripts/). Meetings analyzed:

| File | Account / Theme |
|------|-----------------|
| `gorjana-weekly.json` | gorjana (jewelry) — Shopify Opus, ship-to-me, exchanges |
| `rfid-inventory.json` | RFID inventory architecture & demos |
| `soc-hotwax.json` | Spoonful of Comfort (DTC food) — fulfillment, replenishment, holiday |
| `simkhai-hotwax.json` | SIMKHAI (fashion) — transfer orders, cycle counting, pricing |
| `rails-hotwax.json` | Rails (fashion) — multi-shop rollout, international routing |
| `chelan-weekly.json` | Chelan Fresh (produce) — OFBiz↔OMS sync, UAT |
| `agape-weekly.json` | Agape (automation/3PL) — production upgrade, POS mirroring |
| `lovers-kickoff.json` | Lovers (retail) — BOPIS + DoorDash kickoff |
| `international-shopify-routing.json` | International Shopify routing strategy |
| `shopify-fulfillment-netsuite-integration.json` | Shopify Fulfillment / NetSuite integration |
| `mcp-graphql-shopify-oms-integration.json` | MCP / GraphQL / Shopify OMS integration |

## Analysis Documents

| Document | What it answers |
|----------|-----------------|
| [`market-capability-matrix.md`](./market-capability-matrix.md) | Which real-market needs does Nexus already cover? |
| [`gap-analysis.md`](./gap-analysis.md) | Where do real-market operations exceed current Nexus capabilities? |
| [`feature-recommendations.md`](./feature-recommendations.md) | Prioritized build recommendations for Nexus |
| [`workflow-mappings.md`](./workflow-mappings.md) | Concrete operational workflows mapped to Nexus |
| [`per-account-summaries.md`](./per-account-summaries.md) | One-page operational summary per account |

## Method

1. Pulled full transcripts (summary, action items, topics, transcript) from Read AI for 11 key meetings — **read-only**, nothing posted back to Read AI.
2. Extracted real-market operational workflows, integrations, pain points, and requested features per account/theme.
3. Mapped each against Nexus OMS capabilities (Spring Boot OMS: orders, inventory, shipments, returns, import engine, analytics, AI, Kafka events, webhooks).
4. Produced this comparison set.
