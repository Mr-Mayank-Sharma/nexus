# Nexus OMS — Real Market Business Process Alignment

> **The core value of the HotWax transcripts is not feature lists — it's the actual operational business processes clients run every day.** This document extracts those real-world flows (step-by-step, with all their nuance) from the transcripts and maps each one to Nexus's current state: **where Nexus already covers it, where it's partial, and where it's missing.**

*Source: 11 Read AI transcripts (SIMKHAI, SoC, gorjana, Lovers, Rails, Chelan, Agape, RFID, international-shopify-routing, shopify-fulfillment-netsuite, mcp-graphql-shopify-oms-integration).*

---

## How to Read This

Each business process below is a **real operational flow** a HotWax client runs. For each:
- **The flow** — the actual step-by-step process (from the transcript)
- **Nexus status** — ✅ covered / 🟡 partial / ❌ missing
- **What to build** — the concrete Nexus gap to close

---

## 1. Transfer Order Receiving (SIMKHAI)

**The real flow (from SIMKHAI demo):**
1. Warehouse fulfills a transfer order (TO) → it becomes **pending receipt at the store**
2. Store sees the TO with the **same TO number** the warehouse used (shared language)
3. **Tracking code synced in** from the carrier (NetSuite-integrated)
4. Store **scans the shipping-label barcode** → auto-opens the right TO
5. Store receives **in parts** (partial receiving) — each session saves as an item receipt to NetSuite
6. **Everything received updates Shopify in real time** (omni-fulfillment visibility)
7. **Short-receive discrepancy:** store received 30+10=40 but got 10 less → clicks "I acknowledge this discrepancy"
8. HotWax **auto-reconciles in NetSuite**: closes the TO with full receipt + corrects store inventory with an adjustment
9. **Discrepancy report** — real-time alert + trend data (which products misshipped, which stores are outliers)
10. **Over-receive** also supported (positive discrepancy); unannounced items auto-add to receiving
11. **Live on-hand inventory shown during receiving** — store manager sees shelf is empty → sets aside stock for the shelf instead of back room (avoids second process)
12. **Audit trail** — who received what, which session

**Nexus status:** 🟡 **Partial.** Nexus has transfer orders and receiving, but needs:
- **Discrepancy acknowledgment workflow** (short/over-receive with auto-reconcile + adjustment)
- **Discrepancy trend reporting** (misship/outlier analytics)
- **Live on-hand surfacing during receiving** (shelf-empty awareness)
- **Tracking-code barcode scan → auto-open TO**

---

## 2. Transfer Order Fulfillment (SIMKHAI)

**The real flow:**
1. TOs assigned from NetSuite to stores **auto-appear in the store's fulfillment task list**
2. TO can be to another store, or **return-to-warehouse** (end of season)
3. Stores can **create their own TO** (autonomy to ship to a requesting store)
4. Store **cannot change items** on an assigned TO (reduced scope vs. receiving)
5. **Partial fulfillment** supported
6. **Live label generation** — integrates with carrier (FedEx/UPS) or aggregator (ShipHock/ShipStation); store sees live rates or the predetermined shipping method's rate; prints label from the app (no carrier website)
7. **Close items** — when done, store closes remaining quantity → cancels unfulfilled qty in NetSuite → report of how much was off → cycle count to zero it

**Nexus status:** 🟡 **Partial.** Nexus has transfer orders + fulfillment, but needs:
- **Close-items/cancel-remainder** workflow with NetSuite reconciliation
- **Live label generation from carrier/aggregator** in the fulfillment app
- **Store-created TOs** (autonomy)
- **Return-to-warehouse** TO type

---

## 3. Cycle Counting (SIMKHAI)

**The real flow:**
1. Triggered by discrepancy reports or scheduled
2. Store counts items → **discrepancy drives inventory adjustment**
3. Used to **shore up inventory** at store or warehouse
4. **RFID-assisted counting** (emerging)

**Nexus status:** ✅ **Covered.** Nexus has cycle counting. (Verify RFID-assisted counting — see #10.)

---

## 4. Replenishment — System-Directed Drop-Off (SoC)

**The real flow (the most nuanced process in the corpus):**
1. Replenishment moves items from **bulk to pick location**
2. **Category-to-location association:** each category (soup/sides/dessert) maps to a set of locations (main 1–21, sides 1–10, dessert 1–10)
3. **System suggests candidate drop-off locations** (not a single fixed one) — worker picks from the suggested set
4. **Rules constrain the suggestion:**
   - **One LPN per location**
   - **One flavor per location** (a location with chicken noodle can't take another soup)
   - **One lot per location**
   - **Location must be empty** (on-hand = 0) before it can accept replenishment
5. **Locked-source-location handling:** if a source location is locked → notify → cancel affected tasks → search unlocked locations → system comment on auto-cancel
6. **Skip-item reason enforcement** (required reason)
7. **Flexible SKU-to-location** matching (any flavor accepted when inventory is zero)
8. **Override feature** needed (worker may need to change suggested location)
9. **Deployment caution:** don't block operations during peak season; roll out when team is ready

**Nexus status:** ❌ **Missing.** This is a P0 gap. Nexus needs:
- **Category-to-location association model**
- **System-directed drop-off suggestion** (candidate set, not fixed)
- **Replenishment rules engine** (LPN/flavor/lot/empty-location constraints)
- **Locked-source fallback** + auto-cancel with audit trail
- **Skip-item reason enforcement**
- **Override workflow**

---

## 5. Picker-Performance / Labor Analytics (SoC)

**The real flow:**
1. **Picker identity** captured on every pick task (who picked what)
2. **Product category** (frozen vs. dry), **location/area**, **duration** tracked
3. **Data model enhanced** so any SQL can identify which pickers picked which items in a day
4. **Looker dashboard** reporting: who picked frozen, how long; who picked dry, how long
5. **Timeframes:** hourly / daily / weekly / monthly / yearly
6. **The click problem:** adding a "start picking" click per item was rejected (45–50 items = 45–50 clicks). Solution: **two proposed flows with minimum additional clicks** — worker preference decides

**Nexus status:** ❌ **Missing.** P0 gap. Nexus needs:
- **Picker identity on pick tasks** (link to RF app user)
- **Category/location/duration capture**
- **Labor analytics dashboard** (per-picker throughput by category/location/duration)
- **Exportable reports** (Looker-style)
- **Click-minimization UX principle** (don't add per-item clicks)

---

## 6. Packing — Mid-Pack Box Change & Concurrency (SoC)

**The real flow:**
1. Packers **change the shipment box during packing** (e.g., wrong size)
2. **Concurrency bug:** parallel transactions lock on an entity → deadlock → **labels not printing properly**
3. Fix: handle the deadlock (two resources depending on each other in the same transaction)
4. **Standby review:** HotWax team on standby to review logs when the change is re-enabled

**Nexus status:** 🟡 **Partial.** Nexus has packing, but needs:
- **Transaction concurrency/deadlock hardening** for mid-pack box changes
- **Label regeneration** on box change
- **Log-based verification** workflow

---

## 7. Holiday Planning / Transit-Time Model (SoC)

**The real flow (from SoC peak-season):**
1. **Ship-by / ship-after / days-in-transit** filtering of orders
2. **Destination-specific transit** (carrier postal-route mapping)
3. **Holiday planning screen** — filter orders by transit window
4. **Manual pick waiver** — release selected orders without changing peak profiles
5. **Peak-season module** (packaged, repeatable)
6. **50–100 order UAT** before peak

**Nexus status:** ❌ **Missing.** P0 gap. Nexus needs:
- **Carrier postal-route mapping** → destination-specific days-in-transit
- **Ship-by/ship-after/transit filtering**
- **Holiday planning screen**
- **Manual pick waiver** workflow
- **Peak-season module**

---

## 8. BOPIS + DoorDash Store Ops (Lovers)

**The real flow:**
1. **Import only BOPIS and DoorDash orders** from Shopify (using existing Shopify tags + sales-channel identifiers)
2. **Unified screen** showing both order types
3. **Enhanced product details:** name, size, color, SKU
4. **Customer receipts** (Epson-style mobile printing, configurable paper size)
5. **Extended / non-expiring store sessions**
6. **DoorDash cancellations are all-or-nothing** (whole order) — may originate in HotWax, DoorDash, or Shopify; **synced with Shopify**
7. **BOPIS orders may be partially fulfilled**
8. **P1:** DoorDash order prioritization, associate fulfillment tracking, cancellation/refund sync
9. **DoorDash needs no direct agreement** — access via Shopify

**Nexus status:** 🟡 **Partial.** Nexus has BOPIS, but needs:
- **On-demand (DoorDash) order type** with all-or-nothing cancellation
- **Unified BOPIS + on-demand screen**
- **Customer receipt + pick ticket printing** (Epson mobile)
- **Associate fulfillment tracking** + productivity
- **DoorDash prioritization**

---

## 9. Order Routing — Capacity Limits & Reroute (Rails)

**The real flow:**
1. **Priority rules (1, 2, 3):** look for any location not at capacity with inventory
2. **Final-resort rule ("Hail Mary"):** override facility order limits — if a location has inventory, it receives the order even over capacity
3. **Two-step Hail Mary option:** first run without limit override, second run with override
4. **The Shopify limitation:** if a store is the **last location with inventory, Shopify won't natively allow rerouting** → HotWax is building a workaround (flag order in Shopify POS → OMS picks it up → reroutes)
5. **Two approaches being evaluated:** (a) real-time API call from Shopify POS to OMS, or (b) meta-field flag synced in 10–15 min → OMS reads and reassigns

**Nexus status:** ✅ **Covered (and stronger).** Nexus's DOM routing handles capacity limits, priority rules, and **can reroute on stockout** — which HotWax explicitly cannot do natively. **This is Nexus's #1 competitive advantage.** 
- **Verify:** Nexus supports the "last location with inventory" reroute case (HotWax's gap).
- **Opportunity:** Nexus's `canFulfill` demand checks + auto-allocation already beat HotWax here.

---

## 10. Serialized / RFID Tracking (RFID, SIMKHAI)

**The real flow:**
1. **Serialized item tracking** (EPC in backend, UPC derived)
2. **RFID reader middleware** (dedup, decode, encode)
3. **Handheld readers** (receiving, cycle count) + **fixed readers** (checkout)
4. **RFID printer integration**
5. HotWax has support but **no full supply-chain commitment yet** (emerging)

**Nexus status:** ❌ **Missing.** P1 gap. Nexus needs:
- **Serialized item tracking** (EPC)
- **RFID reader middleware**
- **Handheld + fixed reader support**
- **RFID printer integration**

---

## 11. Bidirectional Sync & Conflict Handling (Chelan, Rails, gorjana)

**The real flow (and the pain):**
1. **Sync cadence:** NetSuite→OFBiz every 5 min; OFBiz→NetSuite async (few seconds) + 15-min recovery job
2. **The conflict problem:** "one overrides the other and changes what OFBiz had" — e.g., a consigned order shipped in OFBiz got **undone** when NetSuite fed back stale data
3. **Missing consignment API** (awaiting API for consign shipments)
4. **FRTTRA unsupported** (a NetSuite transaction type)
5. **Reconciliation runs daily** (pending/queued/completed counts)
6. **Field-level mapping control** (gross vs. rate)
7. **Sync-status surfacing** on order pages

**Nexus status:** 🟡 **Partial.** Nexus has webhooks/Kafka events, but needs:
- **Conflict detection** (stale flags, versioning, locks) — prevent silent overwrites
- **Idempotent, retryable sync jobs** + recovery job
- **Reconciliation reporting** (pending/queued/completed)
- **Field-level mapping control**
- **Sync-status surfacing** on order pages

---

## 12. Returns / Exchange Complexity (gorjana)

**The real flow:**
1. **Multi-outcome return claims** (split while retaining claim ID)
2. **Store-credit ledger**
3. **Exchange price/tax difference handling** (e.g., $15 → $16 item; NetSuite discount-item mapping)
4. **Inventory reduction on exchange orders** (bug: not happening 10th–25th Aug → retroactive fix, respecting cycle counts)
5. **NetSuite discount-item mapping** for price differences

**Nexus status:** 🟡 **Partial.** Nexus has returns, but needs:
- **Multi-outcome return claims** (split)
- **Store-credit ledger**
- **Exchange price/tax difference handling** + NetSuite discount-item mapping
- **Retroactive inventory adjustment** with cycle-count awareness

---

## 13. 3PL / WES Operations (Agape)

**The real flow:**
1. **BlackBrick screens** for order processing (3PL's front-end)
2. **MQTT-based** real-time updates (local topic subscription)
3. **Deploy/upgrade scripts** (stage vs. production) with DB migration first
4. **Local environment setup** for both versions (test workflows locally)
5. **Branch sync** between old/new versions
6. **Import performance pain:** BlackBrick import takes **2–3 min/order** (vs. ms REST)

**Nexus status:** ✅ **Covered (and stronger).** Nexus has 3PL billing, client portal, WES tables, and a **fast import engine** (beats HotWax's 2–3 min/order). 
- **Opportunity:** Nexus's fast import is a direct competitive win.

---

## 14. MCP / AI-Agent Access (MCP/GraphQL)

**The real flow:**
1. Publish OMS tools as **MCP** for AI agents
2. **Query permissions + cost limits**
3. Order debugging, reconciliation, test validation via AI agents
4. **API-key (internal)** + **OAuth/SSO (client-facing)**

**Nexus status:** 🟡 **Partial.** Nexus has an AI platform, but needs an **MCP server** exposing OMS tools. P1 strategic gap.

---

## 15. International Routing (international-shopify-routing)

**The real flow:**
1. **Keep Shopify routing unchanged** outside the US unless a specific fulfillment/inventory-sync issue requires config
2. **Don't create unnecessary locations/routing** — let the current setup run if Shopify routing works
3. Canada may need capacity increase (Labour Day sale volume)
4. **Unresolved:** whether non-US countries can use the simple Shopify→NetSuite flow without routing

**Nexus status:** ✅ **Covered.** Nexus's DOM routing handles multi-location/multi-country. No gap.

---

## 16. Shopify Fulfillment → NetSuite Integration (shopify-fulfillment-netsuite)

**The real flow:**
1. Shopify order → OMS → NetSuite fulfillment
2. **Fulfillment synced back to Shopify** (marked fulfilled)
3. **Ship-to-me / BOPIS brokering:** store rejects → customer selects ship-to-me → brokered to warehouse → NetSuite fulfills → OMS posts fulfillment to Shopify
4. **Cancellation + alternate pickup location** support
5. **24-hr SOW** for the BOPIS transition work ($150/hr)

**Nexus status:** ✅ **Covered.** Nexus has BOPIS lifecycle + fulfillment sync. Verify the **alternate-pickup-location** and **ship-to-me brokering** flows.

---

## Summary: Nexus Alignment Scorecard

| # | Business Process | Nexus Status | Priority |
|---|------------------|--------------|----------|
| 1 | Transfer order receiving (discrepancy workflow) | 🟡 Partial | P0 |
| 2 | Transfer order fulfillment (close-items, live labels) | 🟡 Partial | P0 |
| 3 | Cycle counting | ✅ Covered | — |
| 4 | **Replenishment system-directed drop-off** | ❌ Missing | **P0** |
| 5 | **Picker-performance / labor analytics** | ❌ Missing | **P0** |
| 6 | Packing mid-pack box change (concurrency) | 🟡 Partial | P0 |
| 7 | **Holiday planning / transit-time model** | ❌ Missing | **P0** |
| 8 | BOPIS + DoorDash store ops | 🟡 Partial | P0 |
| 9 | Order routing (capacity + reroute) | ✅ **Stronger** | — |
| 10 | Serialized / RFID | ❌ Missing | P1 |
| 11 | Bidirectional sync + conflict handling | 🟡 Partial | P1 |
| 12 | Returns / exchange complexity | 🟡 Partial | P1 |
| 13 | 3PL / WES operations | ✅ **Stronger** | — |
| 14 | MCP / AI-agent access | 🟡 Partial | P1 |
| 15 | International routing | ✅ Covered | — |
| 16 | Shopify fulfillment → NetSuite | ✅ Covered | — |

**Bottom line:** Nexus already covers or beats HotWax on **routing (9), 3PL (13), cycle counting (3), international routing (15), and Shopify→NetSuite (16)**. The market-defining gaps to close are the **WMS depth (4, 5, 7)**, **store ops (8)**, **receiving/fulfillment nuance (1, 2, 6)**, and the **integration/data-integrity layer (11)**.

---

## The "Ocean of Market Knowledge" — Key Insights

1. **Discrepancy handling is a differentiator.** HotWax built discrepancy acknowledgment + trend reporting into receiving because "other tools require closing the TO fully." This is a real pain point Nexus can match.

2. **The click problem is real.** Store/warehouse workers reject extra clicks. Any Nexus RF/fulfillment UX must minimize clicks per item.

3. **Replenishment is nuanced.** It's not "move item A to location B" — it's category-to-location association + LPN/flavor/lot/empty constraints + system-suggested candidate set + override + locked-source fallback. This is the hardest WMS feature to get right.

4. **Sync conflicts are the #1 integration pain.** Every integration account (Chelan, Rails, gorjana) hits "one overrides the other." Nexus's clean data model + webhooks is a foundation, but needs explicit conflict detection.

5. **Routing is the heart.** It appears in every account. Nexus's DOM routing (with stockout reroute) is genuinely stronger than HotWax. **Lead every demo with it.**

6. **Store ops is a distinct product surface.** BOPIS + DoorDash + receipts + associate tracking is a store-facing app, different from warehouse ops. Nexus needs this store surface.

7. **Peak season is a packaged module.** Holiday planning + transit-time + pick waivers is a repeatable, sellable module — not an afterthought.

8. **Import speed is a wedge.** HotWax's 2–3 min/order import is a known weakness. Nexus's fast import is a direct, demonstrable win.

---

## Recommended Next Step

Turn this alignment into **implementation tickets** for the P0 gaps (4, 5, 7, 8, 1, 2, 6) — each with the real business flow as the acceptance criteria. The `nexus-market-readiness-plan.md` Track A already sequences these; this document provides the **exact operational flows** to build against.
