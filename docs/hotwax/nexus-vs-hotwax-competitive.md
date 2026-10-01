# Nexus OMS vs. HotWax Commerce — Competitive Positioning

> Head-to-head comparison of Nexus OMS against HotWax Commerce, based on the HotWax company analysis and Nexus's current state. This is the "how we win" document.
>
> **Technical evidence base:** [`nexus-vs-hotwax-technical.md`](./nexus-vs-hotwax-technical.md) — source-verified comparison of the HotWax Moqui architecture (services, reconciliation API, order routing, carrier labels, data model) against Nexus's Spring Boot stack.
>
> **Routing evidence base:** [`nexus-order-routing-comparison.md`](./nexus-order-routing-comparison.md) + [`nexus-market-gap-tickets-3.md`](./nexus-market-gap-tickets-3.md) — source-verified routing read of `OrderRoutingService.java`, including the live defects tracked as T-14/T-17.
>
> ⚠️ **Claims in this document that were later disproven by a source read are marked `[WITHDRAWN]`. Do not reuse them in a pitch or demo.**

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
| **Fulfillment routing / brokering** | ✅ (stock-aware SQL allocator) | 🟡 (routing surface exists; **allocation is defective**) | **HotWax** — see [`nexus-order-routing-comparison.md`](./nexus-order-routing-comparison.md) |
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
| **Honest data / deterministic AI** | 🟡 | 🟡 (clean data model ✅, but routing "AI" is hardcoded — `AI_OPTIMIZED`, default confidence `0.65`) | **Tie — claim needs qualification** |

---

## 3. Where Nexus Wins (lead with these)

### 3.1 Fulfillment Routing (DOM) — `[WITHDRAWN]`, not a win today
> **Correction (source-verified).** An earlier version of this doc claimed Nexus routing was **stronger** than HotWax's because HotWax "can't reroute when Shopify's final location can't fulfill" (Rails meeting). **That claim is withdrawn.** A read of `OrderRoutingService.java` shows the comparison does not hold up: HotWax's allocator is a **stock-aware SQL** implementation with facility caps, substitute-item resolution, shipment-group brokering, and distance ranking, while Nexus's DOM path has **live allocation defects**.

**What the source actually shows in Nexus:**
- `ruleBasedAllocation` (`:229-234`) calls `buildAllocation` (`:643`) once per candidate UUID with the **full order quantity**, and never calls `canFulfill` on that path.
- The AI path (`:272`) splits quantity arithmetically after scoring, **ignoring the `canFulfill` result** — a node that failed the stock check can still receive units and be marked `ALLOCATED`.
- The fallback `.orElse(warehouses.get(0))` (`:418-420`) can pick a node just proven incapable, because `canFulfill` uses `allMatch` (`:462`).

**Interim position:** do not demo or lead with Nexus routing. Fix **T-17** (P0 allocation correctness) and **T-14** (P0 stock-aware multi-warehouse allocator) first; **T-18** (P1) covers rule expressiveness. Full read: [`nexus-order-routing-comparison.md`](./nexus-order-routing-comparison.md).

**No sales message for this item until T-14/T-17 are closed.**

### 3.2 Fast Import Engine
HotWax's BlackBrick import takes **2–3 min/order** (Agape). Nexus's import engine is fast and handles CSV/JSON/XML/EDI/XLSX.

**Sales message:** "Nexus imports orders in milliseconds, not minutes. Your peak-season import won't be a bottleneck."

### 3.3 Honest Data (keep) — Deterministic AI (qualify)
**Honest data holds up.** HotWax has real sync conflicts, overwrites, and reactive fixes; Nexus's data model and audit posture are a genuine differentiator.

**But the "deterministic AI" framing was overstated.** In the routing path, scoring is **hardcoded** — the `AI_OPTIMIZED` bucket and the default confidence value of `0.65` are constants in `OrderRoutingService.java`, not model output. Deterministic AI is defensible for the *platform* (forecasting, drift, model registry); it is **not** defensible as a claim about routing intelligence today.

**Sales message:** "Nexus gives you honest, auditable data. Every number is real or clearly labelled. No silent overwrites, no sync conflicts." *(Do not extend this into an AI-routing claim until T-14/T-17 land.)*

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

### 4.6 Fulfillment Routing / Brokering (Depth + Correctness) — *newly added*
HotWax wins here today, on two separate axes:

1. **Allocator depth.** HotWax's SQL allocator handles facility caps, substitute-item resolution, shipment-group brokering, and distance ranking. Nexus's DOM path has no equivalent.
2. **Allocation correctness.** On `main`, Nexus breaks allocation three ways: the DOM rule path multiplies full order quantity across candidate nodes, the AI path splits quantity arithmetically after scoring without re-checking stock, and a fallback allocates to a node `canFulfill` just rejected — and `OrderService.confirmOrder` accepts the corrupt result (T-17). **Fixed and merged into `main`** (commits `a7ea7d2` + `cac85c8`, 768 tests green).

Correctness is a **P0 blocker on `main` today** and clears on merge — it is not a reason to route around `OrderRoutingService`, since `BrokeringService` is a queue over that same engine. Depth (T-14) remains a genuine gap. **Merge T-17 and close T-14 in Track A1; T-18 (rule expressiveness) is P1.** See [`nexus-order-routing-comparison.md`](./nexus-order-routing-comparison.md).

---

## 5. The Winning Strategy

**Don't fight HotWax on their turf (routing, store ops, RFID, WMS depth). Win on your turf (fast import, EDI, 3PL, honest data, predictability) while closing the gaps — starting with routing correctness, which is a P0 blocker, not a talking point.**

### Phase 1 — Win on Strengths (now)
Lead every demo with:
1. **Fast import** (ms vs. minutes)
2. **Honest data** (auditable, no silent overwrites or sync conflicts)
3. **EDI** (native X12)
4. **3PL billing + client portal**
5. ~~DOM routing~~ — **held back.** Routing is not demo-ready, but the reason changed: correctness (T-17) has landed on `main`; what still holds it back is depth (T-14).

### Phase 2 — Close the Gaps (8–12 weeks)
Build the P0/P1 gaps from the market-readiness plan:
- **Routing correctness (T-17, P0) + stock-aware allocator (T-14, P0)** — blocking; see 4.6
- WMS depth (replenishment, picker analytics, transit-time)
- Store ops (DoorDash, printing)
- Returns/exchange complexity
- Serialized/RFID
- MCP server
- Routing rule expressiveness (T-18, P1)

### Phase 3 — Win on Predictability (ongoing)
- Self-serve onboarding
- Go-live checklist
- Roll-off process
- Transparent per-order pricing

---

## 6. The Pitch (30-second version)

> "Nexus is the modern OMS for Shopify brands with an ERP. It imports orders in milliseconds, not minutes. It gives you honest, auditable data with no sync conflicts. It speaks EDI natively. And you can onboard yourself in a day, with a clear go-live checklist — no reactive consulting required. One platform. Every order. Zero waste."
>
> *Routing is intentionally absent from this pitch until T-17/T-14 are closed.*

---

## 7. Bottom Line

Nexus is **technically competitive or superior** on the core OMS capabilities (import, EDI, 3PL, honest data). HotWax leads on **routing/brokering depth and correctness**, store ops depth, WMS depth, returns complexity, RFID, and MCP.

**The path to market-ready:** close the HotWax-leading gaps (Tracks A1–A4) — **routing correctness (T-17) and allocator depth (T-14) first**, because they are P0 blockers — then build the sales motion (Track B) and win on predictability (Track C). Lead with fast import + honest data + EDI. **Nexus can win.**
