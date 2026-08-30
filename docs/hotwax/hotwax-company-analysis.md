# HotWax Commerce — Company Deep-Dive

> Reverse-engineered from 11 real customer/partner meetings (Read AI transcripts). This is the "know your enemy" document — how HotWax operates as a business, their architecture, management, sales motion, and where they're vulnerable.

---

## 1. What HotWax Is

**HotWax Commerce** is a **Shopify-first OMS** (Order Management System) built on **Apache OFBiz** (an open-source ERP/CRM/eCommerce platform). It positions itself as the fulfillment/order-management layer between **Shopify (front-end commerce)** and **NetSuite (back-end ERP/accounting)**.

**Core value proposition (from the meetings):**
- Connect Shopify stores to NetSuite without custom code per store
- Manage **multi-location inventory** (warehouse + stores) with real-time sync
- **Route orders** to the best fulfillment location (warehouse vs. store)
- Handle **store operations** (BOPIS, DoorDash, transfers, cycle counts) that Shopify can't
- Provide **serialized/RFID** inventory tracking

**Their stack (inferred from transcripts):**
- **Apache OFBiz** — the underlying OMS engine (Chelan's "OFBiz↔end-to-end sync" confirms OFBiz is the core)
- **Shopify** — front-end commerce (all accounts are Shopify-based)
- **NetSuite** — back-end ERP/accounting (fulfillment, item fulfillment, inventory sync)
- **"end-to-end"** — HotWax's own OMS layer that syncs with OFBiz
- **Moki** — their AI/GraphQL/SSO/MCP access layer (from the Shopify/NetSuite integration meeting)
- **Jitsu** — shipping-label integration (SoC)
- **Looker** — analytics/dashboards (SoC)
- **AfterShip** — customer shipping communications (gorjana)

---

## 2. Business Model & Pricing

### 2.1 Pricing Model (from SIMKHAI meeting — the most explicit)

| Component | Price | Notes |
|-----------|-------|-------|
| **Recurring subscription** | **$750/mo** for >2,500 orders | Per-order fee model, not revenue-based |
| **Implementation fee** | **$1,000** one-time | Onboarding |
| **Customization fee** | **$10,000** one-time | e.g., try-and-buy / consignment |
| **Consulting** | Paid, per-scope | Routing guidance, RFID, implementation coordination (RFID meeting) |
| **Shopify dev work** | **$150/hr** | gorjana SOW (~24 hrs estimated) |

**Key pricing insights:**
- **Per-order, not per-revenue** — "pricing projections should use order counts rather than revenue because the commercial model is based on a per-order fee."
- **Scaled pricing** is tied to projected Shopify order volume (Aditya prepares scaled pricing based on volume).
- **Customizations are a separate revenue stream** ($10k for try-and-buy/consignment).
- **Consulting is a growing revenue stream** — HotWax is actively exploring paid implementation coordination, routing consulting, and RFID consulting (RFID meeting).

### 2.2 Revenue Streams

1. **Recurring SaaS** — per-order subscription (the core)
2. **Implementation/onboarding** — one-time fees
3. **Custom development** — SOWs at $150/hr, $10k customizations
4. **Consulting** — routing, RFID, implementation coordination (emerging)
5. **Hardware/partner resale** — RFID hardware via vendor partnerships (emerging)

---

## 3. Architecture (Inferred)

```
┌─────────────────────────────────────────────────────────┐
│                    CHANNELS (front-end)                 │
│   Shopify (multi-store) · POS · Marketplaces · DoorDash │
└──────────────────────────┬──────────────────────────────┘
                           │ Shopify API / webhooks
┌──────────────────────────▼──────────────────────────────┐
│                  HOTWAX OMS ("end-to-end")              │
│  ┌─────────────┐ ┌──────────────┐ ┌──────────────────┐  │
│  │ Order Mgmt  │ │ Routing/     │ │ Store Ops        │  │
│  │ (approve,   │ │ Brokering    │ │ (BOPIS, DoorDash,│  │
│  │ park, reject│ │ (location    │ │  transfers,      │  │
│  │  ship-to-me)│ │  selection)  │ │  cycle counts)   │  │
│  └─────────────┘ └──────────────┘ └──────────────────┘  │
│  ┌─────────────┐ ┌──────────────┐ ┌──────────────────┐  │
│  │ Inventory   │ │ Fulfillment  │ │ Serialized/RFID  │  │
│  │ (ATP, sync) │ │ (waves, pick,│ │ (EPC tracking)   │  │
│  │             │ │  pack, ship) │ │                  │  │
│  └─────────────┘ └──────────────┘ └──────────────────┘  │
│  ┌─────────────┐ ┌──────────────┐ ┌──────────────────┐  │
│  │ Sync Engine │ │ Moki (AI/    │ │ Replenishment    │  │
│  │ (bidirect,  │ │  GraphQL/    │ │ (drop-off, ATP)  │  │
│  │  conflict)  │ │  SSO/MCP)    │ │                  │  │
│  └─────────────┘ └──────────────┘ └──────────────────┘  │
└──────────┬──────────────────────────────┬───────────────┘
           │ OFBiz sync (5-min)           │ NetSuite API
┌──────────▼──────────────┐   ┌───────────▼───────────────┐
│   OFBiz (legacy OMS)    │   │   NetSuite (ERP/accounting)│
│   Chelan, Agape         │   │   fulfillment, inventory   │
└─────────────────────────┘   └───────────────────────────┘
```

**Architecture characteristics:**
- **OFBiz at the core** — HotWax is essentially a managed/commercial layer on Apache OFBiz. This is both a strength (mature, battle-tested) and a weakness (legacy complexity, sync conflicts).
- **Bidirectional sync is the connective tissue** — OFBiz↔end-to-end every 5 min, OFBiz→end-to-end async ~seconds, 15-min recovery job (Chelan).
- **Moki is their AI/access layer** — GraphQL, SSO, MCP API access, chat, production debugging (Shopify/NetSuite meeting). This is their forward-looking AI-agent play.
- **Serialized/RFID** — EPC in backend, UPC derived for inventory ops, middleware layer for readers (RFID meeting).

---

## 4. Management & Operations

### 4.1 Key People (from transcripts)

| Person | Role (inferred) | Accounts |
|--------|-----------------|----------|
| **Aditya Patel** | Sales/Account Executive, Product | gorjana, Rails, SIMKHAI, Outfitters, Shipsi, ADOC, Mephisto, Krewe |
| **Patrick Gibbons** | Engineering/Program lead | Chelan, SoC, Agape, ARES, internal |
| **Ajinkya Moghe** | Account/Integration | ADOC, Mephisto, Krewe, Shipsi, Ashley |
| **Mohammad Kathawala** | Deployment/DevOps | Agape, Winning |
| **Devanshu Vyas** | Fulfillment/WMS engineer | SoC |
| **Yash Jain** | Demo/Implementation | SIMKHAI, Lovers, Dollskill |
| **Jonathan Sew Hoy** | Consulting/Partnerships | RFID, Rails, Malbon, Dollskill |
| **Gurveen Kaur Bagga** | Integration/Config | Rails, international |
| **Banibrata Manna** | Architecture (MCP/GraphQL) | Internal |
| **Harsh Vijaywargiya** | Engineering | ARES (Able Aerospace) |
| **Mridul Pathak** | Engineering/QA | Chelan, Winning |
| **Mike Bates** | Engineering | SoC, Chelan, Agape |

### 4.2 Operating Rhythm

- **Weekly account check-ins** — every account has a recurring weekly call (gorjana weekly, ADOC weekly, Krewe weekly, Mephisto weekly, Chelan weekly, Agape weekly, ARES stand-ups).
- **Demo-driven delivery** — features are demonstrated before rollout (SIMKHAI transfer-order demo, Dollskill demo, Pick Wave demo).
- **UAT before production** — clients test in UAT with representative orders (SoC: 50–100 orders before peak).
- **Phased rollouts** — P0/P1 prioritization, cutover planning, no-downtime config-based cutovers (Rails, Lovers).
- **Production resets for big changes** — Chelan production deploy = reset + resync (a weakness).

### 4.3 Known Weaknesses (from their own meetings)

1. **Reactive configuration** — "weaknesses in implementation project management, including reactive configuration and the absence of a standard go-live checklist" (RFID meeting).
2. **No standard go-live checklist** — "A lightweight checklist may be more effective than creating Jira tasks for every item."
3. **Weak client roll-off** — "a stronger transition from post-rollout hypercare to long-term client self-sufficiency" needed.
4. **Import performance** — BlackBrick import-orders takes 2–3 min/order vs. ms for direct REST (Agape).
5. **Sync conflicts** — OFBiz↔end-to-end overwrites, missing consignment-shipment API, FRTTRA unsupported (Chelan).
6. **Shopify routing limitations** — Shopify can't reroute on final-location stockout (Rails).
7. **Knowledge-transfer risk** — staff departure leaves gaps (Agape).
8. **Replenishment skip-reason bypass** — users can skip items without a reason in production (SoC).

---

## 5. Client Acquisition & Sales Motion

### 5.1 How They Find Clients

From the transcripts, HotWax's client acquisition is **relationship + demo driven**:

- **Referrals/partnerships** — Jonathan Sew Hoy (consultant) brings clients (Dollskill, Malbon, RFID). He "will consider a workable model for providing implementation coordination or project-management support."
- **Shopify ecosystem** — all clients are Shopify merchants; HotWax positions as the Shopify→NetSuite bridge.
- **Targeted demos** — each prospect gets a tailored demo (SIMKHAI transfer-order demo, Dollskill demo, Pick Wave demo).
- **Consulting-led** — HotWax is building a consulting arm (routing, RFID) that naturally leads to product sales.
- **Existing account expansion** — Rails is rolling out *additional* Shopify shops; gorjana is expanding scope (ship-to-me, exchanges).

### 5.2 The Demo Call (the core sales motion)

From SIMKHAI, RFID, and Lovers meetings, the demo call pattern is:

1. **Targeted demo script** — a specific workflow demo (transfer orders, cycle counting, routing) tailored to the prospect's business.
2. **Show real integrations** — NetSuite + Shopify sync demonstrated live.
3. **Address the prospect's specific pain** — SIMKHAI: store receiving without NetSuite; Lovers: BOPIS + DoorDash on one screen.
4. **Follow up with pricing** — scaled pricing based on projected order volume.
5. **P0/P1 prioritization** — after the demo, scope is split into must-have (P0) and nice-to-have (P1).
6. **Fast turnaround** — P0 targeted for "current week," testing handoff "early next week."

### 5.3 The Sales Funnel (inferred)

```
Prospect identified (referral/Shopify ecosystem)
        │
        ▼
Tailored demo (targeted workflow, real integrations)
        │
        ▼
Scope + pricing (per-order model, P0/P1, customizations)
        │
        ▼
Kickoff (Lovers kickoff: scope, priorities, testing, launch)
        │
        ▼
Phased rollout (P0 first, UAT, cutover, no downtime)
        │
        ▼
Weekly check-in + expansion (new shops, new features, consulting)
```

---

## 6. What the Market Actually Buys (from all accounts)

Synthesizing across all 11 meetings, the market buys these capabilities:

| Capability | Accounts | Why they buy it |
|-----------|----------|-----------------|
| **Fulfillment routing / brokering** | gorjana, Rails, SoC, RFID | Route to best location, fallback, ship-from-store |
| **Store operations (BOPIS/DoorDash)** | Lovers, gorjana | Unified store screen, on-demand fulfillment |
| **Bidirectional sync (Shopify↔OMS↔NetSuite)** | Rails, Chelan, Shopify/NetSuite | Data integrity across systems |
| **Transfer orders & cycle counting** | SIMKHAI, RFID | Store inventory management without NetSuite |
| **Replenishment & ATP** | SoC | Keep shelves stocked, promise availability |
| **Returns/exchanges complexity** | gorjana | Multi-outcome claims, store credit |
| **Serialized/RFID** | RFID, SIMKHAI | Item-level tracking |
| **AI-agent access (MCP/GraphQL)** | MCP/GraphQL | Safe AI debugging/reconciliation |

---

## 7. HotWax's Strategic Direction (forward-looking)

From the meetings, HotWax is moving toward:

1. **Consulting services** — paid implementation coordination, routing consulting, RFID consulting (Jonathan Sew Hoy, Aditya).
2. **AI-agent integration (Moki)** — MCP/GraphQL tools for AI agents, SSO, production debugging. Pilot gorjana → Lovers, ADOC, Mephisto.
3. **RFID as a product** — serialized tracking, hardware partnerships, iPad POS integration.
4. **International expansion** — Rails international routing (EU/UK/Canada), but *cautiously* (only add routing when needed).
5. **Standardized implementation** — they *know* they need a go-live checklist and better roll-off; this is a known gap they're working on.

---

## 8. Bottom Line for Nexus

**HotWax's strengths:** mature OFBiz core, live multi-account base, demo-driven sales, per-order pricing, consulting upsells, real integrations.

**HotWax's vulnerabilities (Nexus's openings):**
1. **Reactive, non-standard implementation** — no go-live checklist, weak roll-off → Nexus can win on **predictable, self-serve onboarding**.
2. **Legacy OFBiz complexity** — sync conflicts, production resets → Nexus's **modern, honest data model** is a differentiator.
3. **Import performance** — 2–3 min/order is a blocker → Nexus's **fast import engine** wins.
4. **Shopify routing gaps** — can't reroute on stockout → Nexus's **DOM routing** is stronger.
5. **Consulting dependency** — HotWax needs consultants to implement → Nexus can be **self-serve**.
6. **Per-order pricing complexity** — Nexus can offer **simpler, transparent pricing**.
