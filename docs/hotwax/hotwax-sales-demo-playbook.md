# HotWax Sales & Demo Playbook — Reverse-Engineered

> How HotWax runs demo calls, finds clients, and closes deals — extracted from real meeting transcripts. This is the playbook Nexus should study, then beat.

---

## 1. The Core Sales Insight

**HotWax sells demos, not documents.** Every deal in the transcripts (SIMKHAI, Dollskill, Lovers, gorjana, Rails) starts with a **targeted product demo** that shows a specific workflow solving the prospect's specific pain. There is no evidence of long RFP processes, slide decks, or feature-matrix selling. It's: *"watch this workflow solve your exact problem, then let's talk price."*

---

## 2. The Demo Call Anatomy

### 2.1 Pre-Demo: Tailored Script

Each demo is built around **one buyer persona and one workflow**:

| Prospect | Demo focus | Buyer pain it solves |
|----------|-----------|---------------------|
| **SIMKHAI** | Transfer-order receiving + cycle counting | Stores can't operate in NetSuite; need store-friendly receiving |
| **Dollskill** | Preorder + RFID | Preorder-provider contract expiring; need serialized tracking |
| **Lovers** | BOPIS + DoorDash unified screen | Store associates juggling two systems |
| **gorjana** | Ship-to-me + warehouse brokering | Store-rejected pickups need to convert to ship-to-me |
| **Pick Wave demo** | Wave picking | Warehouse picking efficiency |

**Lesson for Nexus:** Build **repeatable demo scripts per persona** (retail store ops, warehouse ops, DTC brand, 3PL). Each demo shows ONE workflow end-to-end with real integrations.

### 2.2 During Demo: Show Real Integrations

HotWax demos show **live NetSuite + Shopify sync**, not mockups. The SIMKHAI demo covered:
- Transfer-order receiving with **real-time Shopify inventory updates**
- Cycle counting with **NetSuite + Shopify synchronization**
- Discrepancy handling, tracking, partial shipments

**Lesson for Nexus:** Demos must show **real data flowing between systems** (Shopify↔OMS↔NetSuite/ERP). A demo with fake data or no live integration won't close a deal.

### 2.3 Post-Demo: Scope + Price

After the demo:
1. **Split scope into P0 (must-have) / P1 (nice-to-have)** — Lovers kickoff did exactly this.
2. **Quote per-order pricing** based on projected volume — SIMKHAI: $750/mo >2,500 orders.
3. **Quote customizations separately** — $10k for try-and-buy/consignment.
4. **Fast turnaround** — P0 targeted for "current week," testing handoff "early next week."

**Lesson for Nexus:** Have a **pricing calculator** ready (per-order tiers) and a **P0/P1 scoping template** to move from demo → signed deal quickly.

---

## 3. How They Find Clients

### 3.1 The Referral/Partnership Engine

**Jonathan Sew Hoy** is the clearest example — he's a consultant/partner who:
- Brings clients (Dollskill, Malbon, RFID prospects)
- Schedules demos ("Jonathan will schedule the Dollskill demo")
- Provides feedback on prospects ("Jonathan will provide feedback on Malbon after speaking with its operations director")
- Is building a **paid consulting arm** ("Jonathan will consider a workable model for providing implementation coordination or project-management support")

**Lesson for Nexus:** Build a **partner/consultant channel**. Consultants who implement OMS for retailers are a direct source of qualified leads. Offer them a referral fee or white-label implementation.

### 3.2 The Shopify Ecosystem

All HotWax clients are **Shopify merchants**. HotWax positions as the **Shopify→NetSuite bridge** — the missing fulfillment/inventory layer. This is a massive, well-defined TAM.

**Lesson for Nexus:** Target **Shopify merchants with NetSuite (or similar ERP)** who have outgrown Shopify's native fulfillment. This is the exact wedge HotWax uses.

### 3.3 Existing Account Expansion

HotWax grows by **expanding existing accounts**:
- **Rails** — rolling out *additional* Shopify shops (multi-shop rollout)
- **gorjana** — expanding scope (ship-to-me, exchanges, store credit)
- **SIMKHAI** — adding RFID, try-and-buy

**Lesson for Nexus:** Design for **multi-store/multi-shop** from day one. Land with one store, expand to many. This is a cheaper acquisition than net-new logos.

### 3.4 Consulting-Led Growth

HotWax is building a **consulting arm** (routing, RFID, implementation coordination) that naturally leads to product sales. The RFID meeting explicitly discusses "paid coordination and consulting support, including deeper order-routing guidance."

**Lesson for Nexus:** Offer **paid implementation/consulting services** as a lead-gen and revenue stream. A retailer who pays for routing consulting is a warm product lead.

---

## 4. The Sales Funnel (Step by Step)

```
1. PROSPECT IDENTIFIED
   - Referral/partner (Jonathan Sew Hoy)
   - Shopify ecosystem (merchant with NetSuite)
   - Existing account expansion (new shop, new feature)

2. TAILORED DEMO
   - One workflow, one persona
   - Real integrations (NetSuite + Shopify live)
   - Address specific pain

3. SCOPE + PRICE
   - P0/P1 split
   - Per-order pricing (projected volume)
   - Customization quotes ($10k)
   - Fast turnaround

4. KICKOFF
   - Review scope, priorities, testing, launch (Lovers kickoff)
   - P0 completion target (current week)
   - Testing handoff (early next week)

5. PHASED ROLLOUT
   - P0 first, then P1
   - UAT with representative orders
   - Config-based cutover (no downtime)
   - Production reset only for big changes (weakness)

6. WEEKLY CHECK-IN + EXPANSION
   - Recurring weekly call per account
   - New shops, new features, consulting upsells
```

---

## 5. Pricing Playbook

| Element | HotWax approach | Nexus recommendation |
|---------|----------------|----------------------|
| **Recurring** | Per-order fee (~$750/mo >2,500 orders) | Per-order tiers, transparent |
| **Displacement pricing** | ~$1,500/mo during incumbent contract → ~$3,000–3,200/mo after (Malbon) | Aggressive to win, raise after term |
| **Implementation** | $1,000 one-time | Free/self-serve onboarding (differentiator) |
| **Customization** | $10,000 per feature | Tiered: config vs. custom |
| **Consulting** | Paid, per-scope | Paid implementation/consulting arm |
| **Dev work** | $150/hr SOW | Fixed-scope SOWs |

**Key insight:** HotWax's per-order model means **revenue scales with client success**. Nexus should adopt a similar model — it aligns incentives and is easy for buyers to understand.

**Displacement pricing (from the Tuihq meeting):** When replacing an incumbent (Celigo, Purple Dot), HotWax prices **aggressively during the remaining contract term** (~$1,500/mo for Rails-equivalent capabilities *excluding* inventory management, because the client already has an inventory tool), then raises to ~$3,000–3,200/mo after the term. **Sell the gap, not the whole platform** — don't bundle features the client already owns.

---

## 6. What Nexus Should Copy vs. Beat

### Copy (proven to work)
- ✅ **Demo-driven sales** — targeted workflow demos with real integrations
- ✅ **Per-order pricing** — simple, scalable, aligned
- ✅ **P0/P1 scoping** — fast path from demo to signed deal
- ✅ **Weekly account check-ins** — relationship retention
- ✅ **Multi-shop expansion** — land-and-expand
- ✅ **Consulting-led growth** — services as lead-gen

### Beat (HotWax's weaknesses)
- 🚫 **Reactive implementation** → Nexus: **standard go-live checklist, self-serve onboarding**
- 🚫 **No roll-off process** → Nexus: **clear hypercare → self-sufficiency transition**
- 🚫 **Import performance (2–3 min/order)** → Nexus: **fast import engine**
- 🚫 **Sync conflicts** → Nexus: **honest, conflict-free data model**
- 🚫 **Shopify routing gaps** → Nexus: **stronger DOM routing**
- 🚫 **Consulting dependency** → Nexus: **self-serve where possible**

---

## 7. The Demo Script Nexus Should Build

For each buyer persona, build a **10-minute demo script**:

### Persona A: Retail Store Ops (Lovers, gorjana)
- **Pain:** Store associates juggling BOPIS + DoorDash + pickups
- **Demo:** Unified store screen → pick BOPIS order → print receipt → DoorDash all-or-nothing cancel
- **Nexus capability:** BOPIS lifecycle, store ops, printing

### Persona B: Warehouse Ops (SoC, SIMKHAI)
- **Pain:** Picking efficiency, replenishment, cycle counts
- **Demo:** Wave picking → picker performance → replenishment drop-off → cycle count with variance approval
- **Nexus capability:** Waves, mobile RF, replenishment, cycle counts

### Persona C: DTC Brand with NetSuite (gorjana, Rails)
- **Pain:** Shopify↔NetSuite sync, routing, returns
- **Demo:** Order import → routing to best location → NetSuite fulfillment sync → return/exchange
- **Nexus capability:** DOM routing, bidirectional sync, returns

### Persona D: 3PL (Agape)
- **Pain:** Multi-client billing, client portal, automation
- **Demo:** Rate cards → billing statement → client portal → WES automation
- **Nexus capability:** 3PL billing, client portal, WES

---

## 8. Bottom Line

HotWax's sales motion is **simple and effective**: find a Shopify merchant with an ERP, demo one workflow that solves their pain, quote per-order pricing, and expand. Nexus can win by **copying the demo-driven motion** while **beating HotWax on predictability, self-serve onboarding, and honest data** — the exact weaknesses HotWax's own meetings reveal.
