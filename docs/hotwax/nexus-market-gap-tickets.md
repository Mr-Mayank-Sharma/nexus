# Nexus OMS — P0 Market-Gap Implementation Tickets

> **Purpose:** Turn the real market business processes (from `nexus-business-process-alignment.md`) into **concrete, buildable implementation tickets** for the P0 gaps. Each ticket uses the actual client workflow as its acceptance criteria — so Nexus builds exactly what the market runs, not a theoretical feature.

*Each ticket is sized for a single implementer and follows Nexus's existing patterns (Spring Boot backend + React frontend + Flyway migrations + tests). Priority: P0 = blocks market-readiness for a HotWax-style deal.*

---

## How These Tickets Map to the Plan

These tickets implement **Track A (Product Gaps)** from `nexus-market-readiness-plan.md`, Phase A1 (WMS Depth) and Phase A2 (Store Ops). They are the **market-defining gaps** — the ones HotWax's customers actually run that Nexus is missing.

| Ticket | Market Flow (from alignment doc) | Priority |
|--------|----------------------------------|----------|
| T-01 | Replenishment — system-directed drop-off (#4) | P0 |
| T-02 | Picker-performance / labor analytics (#5) | P0 |
| T-03 | Holiday planning / transit-time model (#7) | P0 |
| T-04 | BOPIS + DoorDash store ops (#8) | P0 |
| T-05 | Transfer order receiving — discrepancy workflow (#1) | P0 |
| T-06 | Transfer order fulfillment — close-items + live labels (#2) | P0 |
| T-07 | Packing — mid-pack box change concurrency (#6) | P0 |

---

## T-01: Replenishment — System-Directed Drop-Off

**Market evidence:** SoC — the most nuanced process in the corpus.

### Real workflow (acceptance criteria)
1. Replenishment moves items from **bulk to pick location**.
2. **Category-to-location association:** each category (soup/sides/dessert) maps to a set of locations (main 1–21, sides 1–10, dessert 1–10).
3. System **suggests candidate drop-off locations** (a set, not a single fixed one) — worker picks from the set.
4. **Rules constrain the suggestion:**
   - One LPN per location
   - One flavor (SKU) per location
   - One lot per location
   - Location must be empty (on-hand = 0) to accept
5. **Locked-source-location handling:** notify → cancel affected tasks → search unlocked locations → system comment on auto-cancel.
6. **Skip-item reason enforcement** (required reason).
7. **Flexible SKU-to-location** matching (any flavor accepted when inventory is zero).
8. **Override feature** — worker may change the suggested location.
9. **Deployment caution:** don't block operations during peak season.

### Build scope
- New entities: `LocationCategory`, `LocationCategoryAssignment`, `ReplenishmentTask` (with suggested-locations, source-lock, skip-reason fields)
- Replenishment rules engine (LPN/flavor/lot/empty constraints)
- Candidate-location suggestion algorithm
- Locked-source fallback + auto-cancel with audit trail
- Skip-reason enforcement (required field)
- Override workflow
- Replenishment dashboard + UAT flow

### Acceptance test
A replenisher can complete a drop-off with system-suggested locations; locked sources auto-fallback with audit trail; skip requires a reason; a location with existing on-hand is not suggested.

---

## T-02: Picker-Performance / Labor Analytics

**Market evidence:** SoC — picker identity, category, location, duration.

### Real workflow (acceptance criteria)
1. **Picker identity** captured on every pick task (who picked what).
2. **Product category** (frozen vs. dry), **location/area**, **duration** tracked.
3. **Data model** lets any SQL identify which pickers picked which items in a day.
4. **Reporting dashboard:** who picked frozen, how long; who picked dry, how long.
5. **Timeframes:** hourly / daily / weekly / monthly / yearly.
6. **Click-minimization UX:** don't add a per-item click (rejected by workers — 45–50 items = 45–50 clicks). Use a two-flow option with minimum clicks.

### Build scope
- Capture picker identity on pick tasks (link to RF app user)
- Track product category, location/area, picking duration
- **Labor analytics dashboard** (per-picker throughput by category/location/duration)
- **Exportable reports** (Looker-style)
- Click-minimized pick flow (minimum additional clicks)

### Acceptance test
A manager can see per-picker throughput by category/location/duration for hourly/daily/weekly/monthly/yearly windows and export it.

---

## T-03: Holiday Planning / Transit-Time Model

**Market evidence:** SoC — ship-by/ship-after/days-in-transit, destination-specific transit.

### Real workflow (acceptance criteria)
1. **Ship-by / ship-after / days-in-transit** filtering of orders.
2. **Destination-specific transit** (carrier postal-route mapping).
3. **Holiday planning screen** — filter orders by transit window.
4. **Manual pick waiver** — release selected orders without changing peak profiles.
5. **Peak-season module** (packaged, repeatable).
6. **50–100 order UAT** before peak.

### Build scope
- Carrier postal-route mapping → destination-specific days-in-transit
- Ship-by/ship-after/transit filtering
- Holiday planning screen
- Manual pick waiver workflow
- Peak-season module (configuration, not code change)

### Acceptance test
A planner can filter holiday orders by transit window and release them via pick waivers without altering peak profiles.

---

## T-04: BOPIS + DoorDash Store Ops

**Market evidence:** Lovers — unified screen, receipts, all-or-nothing DoorDash cancellation.

### Real workflow (acceptance criteria)
1. **Import only BOPIS and DoorDash orders** from Shopify (existing tags + sales-channel identifiers).
2. **Unified screen** showing both order types.
3. **Enhanced product details:** name, size, color, SKU.
4. **Customer receipts** (Epson-style mobile printing, configurable paper size).
5. **Extended / non-expiring store sessions.**
6. **DoorDash cancellations are all-or-nothing** (whole order) — may originate in HotWax, DoorDash, or Shopify; **synced with Shopify**.
7. **BOPIS orders may be partially fulfilled.**
8. **P1:** DoorDash prioritization, associate fulfillment tracking, cancellation/refund sync.
9. **DoorDash needs no direct agreement** — access via Shopify.

### Build scope
- On-demand (DoorDash) order type with all-or-nothing cancellation
- Unified BOPIS + on-demand screen
- Customer receipt + pick ticket printing (Epson mobile)
- Associate fulfillment tracking + productivity
- DoorDash prioritization (P1)
- Shopify sync for cancellation/refund (P1)

### Acceptance test
A store associate can see BOPIS + DoorDash orders on one screen, print a customer receipt, and process a whole-order DoorDash cancellation synced to Shopify.

---

## T-05: Transfer Order Receiving — Discrepancy Workflow

**Market evidence:** SIMKHAI — short/over-receive, discrepancy acknowledgment, trend reporting.

### Real workflow (acceptance criteria)
1. Warehouse fulfills TO → **pending receipt at store** (same TO number).
2. **Tracking code synced in** from carrier.
3. Store **scans shipping-label barcode** → auto-opens the right TO.
4. Store receives **in parts** (partial receiving) — each session saves as an item receipt.
5. **Everything received updates Shopify in real time.**
6. **Short-receive discrepancy:** store clicks "I acknowledge this discrepancy" → auto-reconcile (close TO with full receipt + correct store inventory with adjustment).
7. **Discrepancy report** — real-time alert + trend data (misship/outlier analytics).
8. **Over-receive** supported; unannounced items auto-add to receiving.
9. **Live on-hand shown during receiving** (shelf-empty awareness).
10. **Audit trail** — who received what, which session.

### Build scope
- Discrepancy acknowledgment workflow (short/over-receive with auto-reconcile + adjustment)
- Discrepancy trend reporting (misship/outlier analytics)
- Live on-hand surfacing during receiving
- Tracking-code barcode scan → auto-open TO
- Partial receiving with per-session item receipts

### Acceptance test
A store can receive in parts, report a short-receive discrepancy that auto-reconciles inventory, and see the discrepancy in a trend report.

---

## T-06: Transfer Order Fulfillment — Close-Items + Live Labels

**Market evidence:** SIMKHAI — close-items, live label generation, store-created TOs.

### Real workflow (acceptance criteria)
1. TOs assigned from NetSuite to stores **auto-appear in the store's fulfillment task list**.
2. TO can be to another store, or **return-to-warehouse** (end of season).
3. Stores can **create their own TO** (autonomy).
4. Store **cannot change items** on an assigned TO.
5. **Partial fulfillment** supported.
6. **Live label generation** — integrates with carrier (FedEx/UPS) or aggregator (ShipHock/ShipStation); store sees live rates or predetermined shipping method; prints label from the app.
7. **Close items** — store closes remaining quantity → cancels unfulfilled qty → report of how much was off → cycle count to zero it.

### Build scope
- Close-items/cancel-remainder workflow with reconciliation
- Live label generation from carrier/aggregator in the fulfillment app
- Store-created TOs (autonomy)
- Return-to-warehouse TO type
- Assigned-TO item immutability

### Acceptance test
A store can fulfill a TO partially, generate a live shipping label, and close remaining items to cancel unfulfilled qty with a reconciliation report.

---

## T-07: Packing — Mid-Pack Box Change Concurrency

**Market evidence:** SoC — deadlock when packers change shipment box mid-pack.

### Real workflow (acceptance criteria)
1. Packers **change the shipment box during packing** (e.g., wrong size).
2. **Concurrency bug:** parallel transactions lock on an entity → deadlock → **labels not printing properly**.
3. Fix: handle the deadlock (two resources depending on each other in the same transaction).
4. **Standby review:** team on standby to review logs when re-enabled.

### Build scope
- Transaction concurrency/deadlock hardening for mid-pack box changes
- Label regeneration on box change
- Log-based verification workflow

### Acceptance test
A packer can change the shipment box mid-pack without deadlock; labels regenerate correctly; logs confirm no lock contention.

---

## Sequencing & Dependencies

| Order | Ticket | Depends on |
|-------|--------|-----------|
| 1 | T-07 (concurrency hardening) | — (foundation) |
| 2 | T-01 (replenishment) | T-07 (transaction safety) |
| 3 | T-02 (picker analytics) | — (data capture) |
| 4 | T-03 (transit-time) | — |
| 5 | T-04 (store ops) | — |
| 6 | T-05 (TO receiving) | — |
| 7 | T-06 (TO fulfillment) | T-05 (shared TO model) |

**Parallel tracks:**
- **WMS track:** T-07 → T-01 → T-02 (warehouse depth)
- **Store track:** T-04 → T-05 → T-06 (store ops)
- **Planning track:** T-03 (holiday planning, can run in parallel)

---

## Definition of Done (per ticket)

- [ ] Backend: Flyway migration + entity + service + controller (following Nexus patterns)
- [ ] Frontend: page + API client (React/Vite/TS)
- [ ] Tests: backend unit + frontend vitest (green)
- [ ] Acceptance criteria from the real market flow verified
- [ ] RBAC: role/permission mapping updated
- [ ] Kafka event emitted for relevant state changes
- [ ] Documented in the relevant `docs/features/` doc

---

## Next Step

These are the **P0 product tickets**. The natural follow-ons are:
1. **P1 tickets** (from alignment doc #10–14): RFID, bidirectional sync/conflict handling, returns/exchange, MCP server.
2. **Commercial tickets** (Track B/C): demo scripts, pricing model, self-serve onboarding, go-live checklist.

Want me to expand any ticket into a full implementation spec, or produce the P1 + commercial ticket set next?
