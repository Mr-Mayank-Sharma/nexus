# Nexus OMS — Market-Readiness Implementation Plan

> **Goal:** Make Nexus OMS **market-ready** to compete with HotWax Commerce for Shopify-merchant-with-ERP OMS deals. This plan synthesizes the HotWax company analysis, the SoC WMS reference, and Nexus's current state into a **prioritized, executable roadmap** — covering product gaps, sales/demo readiness, commercial model, and competitive positioning.

*Companion docs: [`hotwax-company-analysis.md`](./hotwax-company-analysis.md), [`hotwax-sales-demo-playbook.md`](./hotwax-sales-demo-playbook.md), [`soc-wms-project.md`](./soc-wms-project.md), [`nexus-vs-hotwax-competitive.md`](./nexus-vs-hotwax-competitive.md). Also see `../GAP_CLOSURE_PLAN.md` (technical gap-by-gap) and `../08-IMPLEMENTATION-PLAN.md` (10/10 scorecard).*

---

## 0. Executive Summary

Nexus is **technically strong** — it already has DOM routing, BOPIS, ATP, transfer orders, cycle counting, mobile RF, EDI, AI, 3PL billing, and a client portal. The gap to market-readiness is **not raw capability**; it's:

1. **Completing the last-mile workflows** HotWax's customers actually use (replenishment drop-off, picker analytics, transit-time model, store ops depth).
2. **Packaging Nexus for sale** — demo scripts, pricing model, onboarding, go-live checklist.
3. **Winning on HotWax's weaknesses** — predictability, self-serve onboarding, honest data, fast import.

**The plan has 4 tracks:**
- **Track A — Product Gaps (P0/P1/P2):** close the market-defining gaps
- **Track B — Sales & Demo Readiness:** build the demo playbook and pricing
- **Track C — Onboarding & Go-Live:** self-serve onboarding, go-live checklist, roll-off
- **Track D — Competitive Positioning:** lead with routing, honest data, fast import

**Timeline:** 8–12 weeks to market-ready. **North-star metric:** close a real HotWax-style deal (Shopify merchant with ERP) using the demo playbook.

---

## 1. Current State Assessment

### 1.1 Nexus Strengths (lead with these)

| Capability | Status | Market relevance |
|-----------|--------|------------------|
| **DOM routing / order brokering** | ✅ | The #1 OMS differentiator — HotWax's core value |
| **BOPIS lifecycle** | ✅ | Lovers/gorjana need this |
| **ATP engine** | ✅ | SoC needs online-ATP |
| **Transfer orders** | ✅ | SIMKHAI needs this |
| **Cycle counting** | ✅ | SIMKHAI/RFID need this |
| **Mobile RF app (PWA)** | ✅ | Warehouse ops |
| **EDI (850/856/810/855/997)** | ✅ | Strong differentiator |
| **Import engine** | ✅ | Fast (vs. HotWax's 2–3 min/order) |
| **3PL billing + client portal** | ✅ | Agape/3PL need this |
| **AI platform** | ✅ | Forward-looking (Moki equivalent) |
| **Webhooks/Kafka events** | ✅ | Integration backbone |

### 1.2 Nexus Gaps (close these)

| Gap | Market evidence | Priority |
|-----|-----------------|----------|
| **Replenishment drop-off (system-directed)** | SoC | P0 |
| **Picker-performance / labor analytics** | SoC | P0 |
| **Transit-time model (ship-by/ship-after/days-in-transit)** | SoC | P0 |
| **Store ops depth (DoorDash on-demand, printing, associate tracking)** | Lovers, gorjana | P0 |
| **Bidirectional sync with conflict handling** | Chelan, Rails, Shopify/NetSuite | P1 |
| **Returns/exchange complexity (multi-outcome, store credit)** | gorjana | P1 |
| **Serialized/RFID tracking** | RFID, SIMKHAI | P1 |
| **MCP server for AI-agent access** | MCP/GraphQL | P1 (strategic) |
| **Go-live checklist + self-serve onboarding** | HotWax weakness | P0 (commercial) |
| **Pricing/commercial model** | SIMKHAI | P0 (commercial) |

---

## 2. Track A — Product Gaps

### Phase A1: WMS Depth (Weeks 1–3) — P0

*Goal: match the SoC WMS reference so Nexus can win warehouse/DTC deals.*

#### A1.1 System-Directed Replenishment Drop-Off
**Market evidence:** SoC — system-directed drop-off locations, flexible SKU-to-location, locked-source fallback, skip-item reason enforcement.

**Scope:**
- Replenishment task generation with **system-directed drop-off location** suggestion
- **Flexible SKU-to-location** matching (any flavor accepted when inventory is zero)
- **Locked-source-location handling:** notify → cancel affected tasks → search unlocked locations → system comment on auto-cancel
- **Skip-item reason enforcement** (required reason)
- Replenishment dashboard + UAT flow

**Acceptance:** A replenisher can complete a drop-off with system-suggested locations; locked sources auto-fallback with audit trail; skip requires a reason.

#### A1.2 Picker-Performance / Labor Analytics
**Market evidence:** SoC — picker identity, product category, location, duration.

**Scope:**
- Capture picker identity on every pick task (link to RF app user)
- Track product category, location/area, picking duration
- **Labor analytics dashboard:** who picked what category, how long
- Exportable reports (Looker-style)

**Acceptance:** A manager can see per-picker throughput by category/location/duration.

#### A1.3 Transit-Time Model & Holiday Planning
**Market evidence:** SoC — ship-by/ship-after/days-in-transit filtering, destination-specific transit.

**Scope:**
- **Carrier postal-route mapping** → destination-specific days-in-transit
- **Holiday planning screen:** filter orders by ship-by date, ship-after date, days-in-transit
- **Manual pick waiver** workflow (release selected orders without changing peak profiles)
- **Peak-season module** (packaged, repeatable)

**Acceptance:** A planner can filter holiday orders by transit window and release them via pick waivers.

### Phase A2: Store Ops Depth (Weeks 3–5) — P0

*Goal: win Lovers/gorjana-style store deals.*

#### A2.1 On-Demand (DoorDash/Uber) Order Type
**Market evidence:** Lovers — DoorDash all-or-nothing cancellation, priority sorting.

**Scope:**
- On-demand order type with **all-or-nothing cancellation** (sync to Shopify/DoorDash)
- **Priority sorting** for on-demand orders
- **Associate fulfillment tracking** + productivity reporting
- Unified store screen (BOPIS + on-demand)

#### A2.2 Store Printing (Receipts & Pick Tickets)
**Market evidence:** Lovers — Epson mobile printers, customer receipts.

**Scope:**
- Customer receipt + pick ticket printing (mobile/Epson)
- Configurable paper size
- Combined vs. separate receipt/pick-ticket decision

### Phase A3: Integration & Data Integrity (Weeks 5–7) — P1

*Goal: prevent the data-integrity pain every integration account hits.*

#### A3.1 Bidirectional Sync with Conflict Handling
**Market evidence:** Chelan (OFBiz sync conflicts), Rails/NetSuite (fulfillment mismatches).

**Scope:**
- Sync engine with **conflict detection** (stale flags, versioning, locks)
- **Idempotent, retryable sync jobs** + recovery job
- **Reconciliation reporting** (pending/queued/completed counts)
- **Field-level mapping control** (gross vs. rate)
- Sync-status surfacing on order pages

#### A3.2 Returns/Exchange Complexity
**Market evidence:** gorjana — multi-outcome claims, store credit, price/tax differences.

**Scope:**
- **Multi-outcome return claims** (split while retaining claim ID)
- **Store-credit ledger**
- Exchange price/tax difference handling
- NetSuite discount-item mapping

### Phase A4: Strategic Differentiators (Weeks 7–9) — P1

#### A4.1 Serialized/RFID Tracking
**Market evidence:** RFID, SIMKHAI — EPC tracking, middleware, readers.

**Scope:**
- Serialized item tracking (EPC in backend, UPC derived)
- RFID reader middleware (dedup, decode, encode)
- Handheld (receiving/cycle count) + fixed (checkout) reader support
- RFID printer integration

#### A4.2 MCP Server for AI-Agent Access
**Market evidence:** MCP/GraphQL meeting — publish OMS tools as MCP for AI agents.

**Scope:**
- Expose Nexus REST/GraphQL as **MCP tools**
- Query permissions + cost limits
- Order debugging, reconciliation, test validation via AI agents
- API-key (internal) + OAuth/SSO (client-facing)

---

## 3. Track B — Sales & Demo Readiness

*Goal: Nexus can run a HotWax-style demo call and close a deal.*

### B1. Demo Scripts (per persona) — Week 1–2
Build **4 repeatable demo scripts** (from the sales playbook):
1. **Retail Store Ops** (Lovers/gorjana) — BOPIS + DoorDash unified screen
2. **Warehouse Ops** (SoC/SIMKHAI) — wave picking, replenishment, cycle counts
3. **DTC Brand with ERP** (gorjana/Rails) — routing, sync, returns
4. **3PL** (Agape) — rate cards, client portal, WES

Each demo: **one workflow, real integrations, addresses specific pain.**

### B2. Pricing Model — Week 2–3
Adopt a **per-order commercial model** (HotWax-proven):
- **Recurring:** per-order tiers (e.g., $X/mo for N orders)
- **Implementation:** free/self-serve onboarding (differentiator)
- **Customization:** tiered config vs. custom
- **Consulting:** paid implementation/consulting arm
- **Dev work:** fixed-scope SOWs

### B3. Demo Environment — Week 2–4
- **Live sandbox** with real Shopify + ERP (NetSuite/QuickBooks) connectors
- **Seed data** per persona (retail, warehouse, DTC, 3PL)
- **One-click demo reset**

---

## 4. Track C — Onboarding & Go-Live

*Goal: beat HotWax's #1 weakness — reactive, non-standard implementation.*

### C1. Self-Serve Onboarding — Week 3–5
- **Guided setup wizard** (tenant, warehouse, locations, channels)
- **Connector setup** (Shopify, ERP) with test mode
- **Import sample data** to validate
- **Documentation + video walkthroughs**

### C2. Go-Live Checklist — Week 4–6
Build the **standard go-live checklist** HotWax lacks:
- [ ] Tenant + warehouse + locations configured
- [ ] Channels connected (Shopify, ERP) + test orders flow
- [ ] Inventory synced + ATP verified
- [ ] Routing rules configured + tested
- [ ] Store ops (BOPIS/DoorDash) tested
- [ ] Returns/exchanges tested
- [ ] UAT with representative orders
- [ ] Cutover plan (config-based, no downtime)
- [ ] Hypercare plan + roll-off

### C3. Roll-Off / Self-Sufficiency — Week 6–8
- **Hypercare → self-sufficiency transition** (HotWax's known gap)
- **Client admin training** + documentation
- **Support tiers** (self-serve → paid support)

---

## 5. Track D — Competitive Positioning

*Goal: win on HotWax's weaknesses.*

### D1. Lead with Routing
Nexus's **DOM routing** is stronger than HotWax's (which can't reroute on Shopify stockout). Lead every demo with routing.

### D2. Lead with Honest Data
Nexus's **deterministic AI, honest metrics, no fake data** is a differentiator vs. HotWax's sync conflicts and reactive fixes.

### D3. Lead with Fast Import
Nexus's **fast import engine** beats HotWax's 2–3 min/order. Show a live import benchmark.

### D4. Lead with Predictability
Nexus's **go-live checklist + self-serve onboarding** beats HotWax's reactive implementation.

---

## 6. Prioritized Roadmap (8–12 weeks)

| Week | Track A (Product) | Track B (Sales) | Track C (Onboarding) |
|------|-------------------|-----------------|----------------------|
| 1–2 | Replenishment drop-off, Picker analytics | Demo scripts (4 personas) | — |
| 3–4 | Transit-time model, Store ops (DoorDash) | Pricing model, Demo env | Self-serve onboarding |
| 5–6 | Store printing, Bidirectional sync | — | Go-live checklist |
| 7–8 | Returns/exchange, Serialized/RFID | — | Roll-off process |
| 9–12 | MCP server, polish | Refine demos from feedback | Full onboarding flow |

---

## 7. Success Metrics

| Metric | Target |
|--------|--------|
| **Close a real HotWax-style deal** (Shopify + ERP merchant) | 1 within 12 weeks |
| **Demo-to-deal conversion** | >20% |
| **Onboarding time** (signup → first order) | <1 day self-serve |
| **Go-live time** (from kickoff) | <2 weeks |
| **Replenishment drop-off completion** | >95% |
| **Picker analytics accuracy** | >99% |
| **Transit-time accuracy** | >95% |
| **Sync conflict resolution** | 100% no silent overwrites |
| **Import throughput** | >100 orders/min (vs. HotWax 2–3 min/order) |

---

## 8. Risks & Mitigation

| Risk | Impact | Mitigation |
|------|--------|------------|
| Scope creep in product gaps | High | P0/P1 discipline; ship P0 first |
| Demo environment not realistic | High | Live connectors + seed data per persona |
| Pricing misalignment | Medium | Per-order model validated against HotWax |
| Onboarding too complex | Medium | Guided wizard + docs + video |
| Competitive response from HotWax | Medium | Move fast; win on predictability/honesty |

---

## 9. Conclusion

Nexus is **one step from market-ready**. The product already has the core OMS/WMS capabilities HotWax's customers use. What's missing is:
1. **Last-mile WMS workflows** (replenishment, picker analytics, transit-time) — from the SoC reference.
2. **Store ops depth** (DoorDash, printing) — from Lovers/gorjana.
3. **A sales motion** (demo scripts, pricing, demo env) — from HotWax's playbook.
4. **Predictable onboarding** (go-live checklist, self-serve) — beating HotWax's #1 weakness.

**Execute Tracks A–D in parallel, lead with routing + honest data + fast import, and close a real deal within 12 weeks.**
