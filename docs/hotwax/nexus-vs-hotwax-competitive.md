# Nexus OMS vs. HotWax Commerce — Competitive Positioning

> Head-to-head comparison of Nexus OMS against HotWax Commerce, based on the HotWax company analysis and Nexus's current state. This is the "how we win" document.

---

## 1. The Battlefield

Both Nexus and HotWax target the same buyer: **a Shopify merchant (or multi-store brand) with an ERP (NetSuite/QuickBooks) that has outgrown Shopify's native fulfillment** and needs an OMS/WMS layer for multi-location inventory, routing, and store operations.

**HotWax's wedge:** "We're the Shopify→NetSuite bridge, built on battle-tested OFBiz."
**Nexus's wedge:** "We're the modern, honest, self-serve OMS — predictable onboarding, fast import, no sync conflicts."

---

## 2. Head-to-Head Capability Matrix

**Legend:** ✅ Strong · 🟡 Partial · ❌ Weak/Absent

| Capability | HotWax | Nexus | Advantage |
|-----------|--------|-------|-----------|
| **Fulfillment routing / brokering** | ✅ (but can't reroute on Shopify stockout) | ✅ DOM routing | **Nexus** (stronger reroute) |
| **BOPIS** | ✅ | ✅ | Tie |
| **DoorDash / on-demand** | ✅ | 🟡 (planned) | HotWax (for now) |
| **Store printing (receipts/pick tickets)** | ✅ | 🟡 (planned) | HotWax |
| **Transfer orders** | ✅ | ✅ | Tie |
| **Cycle counting** | ✅ | ✅ | Tie |
| **Replenishment (system-directed)** | ✅ | 🟡 (planned) | HotWax |
| **Picker-performance analytics** | ✅ | ❌ (planned) | HotWax |
| **Transit-time model** | ✅ | ❌ (planned) | HotWax |
| **ATP / online-ATP** | ✅ | ✅ | Tie |
| **Serialized/RFID** | ✅ | 🟡 (planned) | HotWax |
| **Bidirectional sync + conflict handling** | 🟡 (sync conflicts, overwrites) | 🟡 (webhooks/Kafka, no conflict framework) | **Nexus** (cleaner data model) |
| **Returns/exchange complexity** | ✅ | 🟡 | HotWax |
| **Import engine** | ❌ (2–3 min/order) | ✅ (fast) | **Nexus** (huge) |
| **EDI** | 🟡 | ✅ (850/856/810/855/997) | **Nexus** |
| **3PL billing + client portal** | 🟡 | ✅ | **Nexus** |
| **AI platform** | 🟡 (Moki emerging) | ✅ (forecasting, drift, model registry) | **Nexus** |
| **MCP/AI-agent access** | ✅ (Moki) | 🟡 (planned) | HotWax (for now) |
| **Mobile RF app** | ✅ | ✅ | Tie |
| **Onboarding / go-live** | ❌ (reactive, no checklist) | 🟡 (planned self-serve) | **Nexus** (planned) |
| **Honest data / deterministic AI** | 🟡 | ✅ | **Nexus** |

---

## 3. Where Nexus Wins (lead with these)

### 3.1 Fulfillment Routing (DOM)
Nexus's DOM routing is **stronger** than HotWax's — HotWax explicitly can't reroute when Shopify's final location can't fulfill (Rails meeting). Nexus's `canFulfill` demand checks + auto-allocation + hybrid AI routing is a genuine advantage.

**Sales message:** "Nexus routes every order to the best location automatically — and reroutes when a location can't fulfill. HotWax needs manual ops for that."

### 3.2 Fast Import Engine
HotWax's BlackBrick import takes **2–3 min/order** (Agape). Nexus's import engine is fast and handles CSV/JSON/XML/EDI/XLSX.

**Sales message:** "Nexus imports orders in milliseconds, not minutes. Your peak-season import won't be a bottleneck."

### 3.3 Honest Data & Deterministic AI
Nexus's **deterministic AI, honest metrics, no fake data** is a core differentiator. HotWax has sync conflicts, overwrites, and reactive fixes.

**Sales message:** "Nexus gives you honest, auditable data. Every number is real or clearly labelled. No silent overwrites, no sync conflicts."

### 3.4 EDI
Nexus has real X12 EDI (850/856/810/855/997). HotWax's EDI is less prominent.

**Sales message:** "Nexus speaks EDI natively — 850 purchase orders, 856 ASNs, 810 invoices, 855/997 acknowledgments."

### 3.5 3PL Billing & Client Portal
Nexus has rate cards, billing statements, and a client portal. This is a 3PL differentiator (Agape-style).

### 3.6 Predictable Onboarding (planned)
Nexus will win on **self-serve onboarding + go-live checklist** — HotWax's #1 known weakness.

---

## 4. Where HotWax Wins (close these)

### 4.1 Store Ops Depth (DoorDash, printing)
HotWax has live store ops (Lovers, gorjana). Nexus needs the on-demand order type + printing. **Close this in Track A2.**

### 4.2 WMS Depth (replenishment, picker analytics, transit-time)
HotWax's SoC project shows production WMS depth. Nexus needs these. **Close this in Track A1.**

### 4.3 Returns/Exchange Complexity
HotWax handles multi-outcome claims, store credit. Nexus needs this. **Close this in Track A3.**

### 4.4 Serialized/RFID
HotWax has RFID. Nexus needs serialized tracking. **Close this in Track A4.**

### 4.5 MCP/AI-Agent Access
HotWax's Moki is live. Nexus needs an MCP server. **Close this in Track A4.**

---

## 5. The Winning Strategy

**Don't fight HotWax on their turf (store ops, RFID, WMS depth). Win on your turf (routing, import, honest data, EDI, 3PL, predictability) while closing the gaps.**

### Phase 1 — Win on Strengths (now)
Lead every demo with:
1. **DOM routing** (reroute on stockout — HotWax can't)
2. **Fast import** (ms vs. minutes)
3. **Honest data** (deterministic AI, no conflicts)
4. **EDI** (native X12)
5. **3PL billing + client portal**

### Phase 2 — Close the Gaps (8–12 weeks)
Build the P0/P1 gaps from the market-readiness plan:
- WMS depth (replenishment, picker analytics, transit-time)
- Store ops (DoorDash, printing)
- Returns/exchange complexity
- Serialized/RFID
- MCP server

### Phase 3 — Win on Predictability (ongoing)
- Self-serve onboarding
- Go-live checklist
- Roll-off process
- Transparent per-order pricing

---

## 6. The Pitch (30-second version)

> "Nexus is the modern OMS for Shopify brands with an ERP. It routes every order to the best location automatically — and reroutes when a location can't fulfill, which HotWax can't do. It imports orders in milliseconds, not minutes. It gives you honest, auditable data with no sync conflicts. And you can onboard yourself in a day, with a clear go-live checklist — no reactive consulting required. One platform. Every order. Zero waste."

---

## 7. Bottom Line

Nexus is **technically competitive or superior** on the core OMS capabilities (routing, import, EDI, 3PL, honest data). HotWax leads on **store ops depth, WMS depth, returns complexity, RFID, and MCP**. 

**The path to market-ready:** close the HotWax-leading gaps (Tracks A1–A4), build the sales motion (Track B), and win on predictability (Track C). Lead with routing + honest data + fast import. **Nexus can win.**
