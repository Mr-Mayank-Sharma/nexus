# 13 — Master Plan: 10/10, Separate OMS & WMS Products, Enterprise UI

> **Date:** 2026-10-09 · **Builds on:** [12 — Verified Benchmark](./12-BENCHMARK-AND-DEVELOPMENT-PLAN.md)
> (scores, findings, phases) · **Reference studied:** HotWax OMS source (Moqui components
> `oms`, `order-routing`, `shopify-connector`, `netsuite-connector`; Apache-2.0) and the
> HotWax app repos listed by the owner · **Design reference:** "WareTrack" 3D warehouse UI
> (React Three Fiber).

---

## 0. What "10 on 10" means, and the one honest limit

Doc 12 scores Nexus two ways:

| Score | Meaning | Today | Target |
|---|---|---|---|
| **NP** (feature presence) | Code for the capability exists | 8.4 | **10.0** on every row |
| **NR** (production readiness) | It works on real data and real providers, is secure, tested and operable | 5.7 | **10.0 on every row code can move** |

Twenty-four of the 25 rows can reach 10 through engineering. The 25th, **track record /
production deployments**, is only earned by customers running Nexus in production over time.
This plan gets every code-controlled row to 10 and runs a pilot programme to move the last row.
With track record at 5 (pilots), weighted NR is **≈ 9.4**; with 12 months of production
references it reaches **≈ 9.8–10**. Treat any claim of "10/10 NR" before that point as marketing.

---

## 1. Progress log

| Date | Item | Result |
|---|---|---|
| 2026-10-09 | Doc 12 benchmark | NP 8.4 / NR 5.7 baseline, verified by running the suites |
| 2026-10-09 | **Phase 0 security** (commit `749562d`) | SSO JWKS verification, real TOTP, DB-backed one-time tokens, RLS forced on 163/163 tenant tables + `nexus_app` role (proven on Postgres 16), tenant-scoped caches, constant-time HMAC, working prod secret checks. Tests 816 ✅ / 403 ✅ |
| 2026-10-09 | **Inventory per location** (`f1ad8b3`) | V20's UNIQUE (tenant, sku) blocked multi-location stock on every Flyway-built DB; V70 keys on (tenant, sku, node, lot); the import is location-aware |
| 2026-10-09 | **Warehouse Twin** (`f8285f7`) | `/warehouse-twin`: 3D site from real docks, appointments, trailers, equipment, stock and shipments; verified in a browser on Postgres with RLS on |
| 2026-10-09 | **Order Flow Twin** | `/order-twin`: channels → nodes → regions on a 3D Sankey board with live flows, routing explanation per order, lifecycle, queue and exceptions; 1h/6h/24h/7d windows. Tests 828 ✅ / 414 ✅ |
| 2026-10-09 | **Phase A hardening** | ShedLock on all 15 jobs (V71); UTC end to end with `Z` timestamps; transactional outbox for Kafka (V72); `@Version` on 7 aggregates with 409s (V73); order transition table (fixes cancel-after-ship, shipping cancelled orders, arbitrary status jumps); Testcontainers Postgres suite proving tenant isolation through the API; Flyway always runs unscoped; coverage gate ratcheted to 43%/31% (gated packages) and 33%/22% (whole codebase). Tests 866 ✅. **Open:** Spring Boot 4 migration (removes the two time-boxed Spring CVEs before 2026-12-31). |
| 2026-10-09 | **Phase B (in progress)** | Account & security page with authenticator-app MFA (verified end to end); sign-in without invented figures. Route audit (`scripts/e2e/route-audit.mjs`): 99 pages, 0 crashes, 0 failing calls, 0 dead buttons. Hardcoded Warehouse Dashboard rebuilt on real data; fake KPI trends removed; marketplace statuses made truthful; Add Picker, New Transfer, packer Find and CSV exports wired. Bugs found and fixed: pickers could not be created (V74), EDI uploads always failed and 997s echoed wrong control numbers (V75), Settings 500 for new tenants (sign-up now creates settings), analytics NPE, bad-input/state errors returned 500. GET sweep: 188 endpoints, 0 server errors. Schema guard in CI: every required column must be written by its entity. Tests 871 ✅ / 415 ✅. |
| 2026-10-10 | **Phase C — editions** | OMS, WMS and SUITE editions plus THREE_PL / EDI / AI add-ons (V76 `nx_tenant_subscriptions`). Enforced on every API call (403 `MODULE_NOT_LICENSED`, all roles); UI hides unlicensed sections, tiles and actions and shows "not in your plan" for direct links. Guards: every controller and every UI route must be classified. Verified live with a WMS+EDI tenant. Also: CI was red since the outbox commit (`.gitignore` excluded the test schema); code-scanning/Sonar now skip cleanly until configured — `main` CI fully green. See [15](./15-EDITIONS-AND-ENTITLEMENTS.md). Tests 879 ✅ / 417 ✅. |

Security row NR: **2 → 7**

**New findings while building (added to the backlog):**
- **Time zones:** timestamps are stored without a zone and written in the JVM's local zone. Pin the JVM and DB to UTC (`-Duser.timezone=UTC`) and migrate to `timestamptz` (Phase A).
- **Login page claims:** the sign-in screen shows "50K+ orders daily", "99.9% uptime SLA", "150+ integrations" and "40% cost reduction", none of which is backed by data. Remove them in the shell rebuild (Phase B).
- **Spring CVE-2026-47884 and CVE-2026-47890 (CRITICAL, spring-webmvc XsltView and SSE view rendering):** fixed only in Spring Framework 7. Both are time-boxed in `.trivyignore.yaml` until 2026-12-31 because Nexus uses neither XSLT views nor SSE. Migrate to Spring Boot 4 in Phase A.
- **Test blind spot:** H2 without Flyway hid the V20 inventory bug, which is one more reason for Testcontainers (Phase A). (the remaining points come from P0-10 HttpOnly refresh cookie, a pen-test and SOC 2 controls in §6).

---

## 2. Product packaging: sell OMS and WMS separately

### 2.1 Editions

| Edition | For | Includes |
|---|---|---|
| **Nexus OMS** | Brands and retailers (Shopify, Amazon, multi-store) | Orders, order flow twin, routing/brokering, ATP, BOPIS and ship-from-store, pre-orders, returns, customers, channel connectors, promotions |
| **Nexus WMS** | DCs, 3PLs, brands running their own warehouses | Receiving/ASN, putaway, slotting, waves, picking, packing, shipping, labels, yard and dock, cycle counts, RF app, labor, **warehouse twin** |
| **Nexus Suite** | Both | OMS + WMS + cross-module automation (routing that knows live warehouse capacity) |

**Add-ons** (any edition): 3PL Billing & Client Portal · EDI (X12/EDIFACT/AS2) · AI (forecasting,
replenishment, assistant) · Automation/WES · Advanced Analytics · Premium connectors (ERP: NetSuite,
SAP, Dynamics).

**Indicative pricing** (to validate with pilots; anchors from doc 12 §4.6). Cin7 Core is
$349–999/month, while HotWax and Extensiv are quote-only.

| | Starter | Growth | Enterprise |
|---|---|---|---|
| OMS | $299/mo, 2k orders | $899/mo, 20k orders | custom |
| WMS | $399/mo, 1 site | $1,199/mo, 3 sites | custom |
| Suite | $599/mo | $1,799/mo | custom |
| Self-hosted | Free community core (OMS-lite) + paid licence key for editions/add-ons | | |

### 2.2 How the split works technically

**One codebase, hard module boundaries, runtime entitlements.** Separate repos would double
maintenance for no buyer benefit. What buyers need is (a) to pay only for what they use, and (b)
to run WMS under a non-Nexus OMS (Shopify, HotWax, SAP) or OMS over a non-Nexus warehouse
(a 3PL, NetSuite). Both are solved by contracts, not repos.

```
nexus-platform (shared kernel)
  tenancy · auth/SSO/MFA · RBAC · entitlements · audit · products/catalog · facilities
  inventory ledger (positions, reservations, ATP) · customers · events/outbox · integrations core
        │                                   │
  nexus-oms                           nexus-wms
  orders · routing/brokering ·        receiving · putaway · waves · picking · packing
  ATP rules · BOPIS · returns ·       shipping/labels · yard/dock · counts · labor · RF
  pre-orders · promotions             slotting · automation/WES
        │      Fulfillment contract (events + REST)      │
        └──── FulfillmentRequested / Accepted / Rejected / Shipped / Cancelled ────┘
  add-ons: nexus-3pl · nexus-edi · nexus-ai · nexus-connectors-*
  nexus-app: Spring Boot assembly (Suite) — or OMS-only / WMS-only images
```

**Steps:**

1. **Spring Modulith first** (no file moves): declare `@ApplicationModule`s over the existing
   packages, add `ApplicationModules.verify()` to CI so illegal cross-module calls fail the build.
   Fix the violations it reports.
2. **Entitlements:** `nx_tenant_subscriptions(tenant_id, edition, add_ons[], limits jsonb,
   valid_until)`. A `@RequiresModule("WMS")` annotation with a filter that answers **402 Payment
   Required**. `GET /me/entitlements` drives the frontend: nav, routes and command palette show
   only licensed modules. Self-hosted builds verify a signed licence key (Ed25519) offline.
3. **Fulfillment contract:** OMS never calls WMS services directly. It emits
   `FulfillmentRequested` (through the outbox). A *facility adapter* fulfils it: the Nexus WMS
   adapter for Suite tenants, or a 3PL/EDI 940/NetSuite/Shopify-location adapter for OMS-only
   tenants. WMS emits `Shipped`/`Rejected` back. This is HotWax's model (OMS brokers to
   facilities), made pluggable.
4. **Maven multi-module split** once Modulith is green: `nexus-platform`, `nexus-oms`,
   `nexus-wms`, the add-ons, and `nexus-app`. Three Docker images (`suite`, `oms`, `wms`) are
   built from the same modules.
5. **Frontend module registry** (§4.3): each module ships its routes, nav entries, command-palette
   actions and dashboard widgets. The shell assembles whatever the tenant is entitled to.

**Mapping today's packages and pages:** OMS takes Orders, Create/Find Order, Order Detail,
Approvals, Routing Rules, Order Routing, Brokering Queue, ATP Rules, Fulfillment Limits, BOPIS
(3 pages), Endless Aisle, Pre-Orders, Rejections, Returns (3), Customers, Promotions, Payments,
Transfers, channel integrations, Email Order Parsing. WMS takes Warehouse, Warehouse Dashboard,
Inventory Receiving, Cycle Count, Wave Planning, Picking, Pickers, Packing, Packer Screen,
Shipping, Label Printing, Manifest, Carriers, Rate Cards, Rate Shopping, Freight Audit, Yard/Dock,
Slotting, Labor, Task Queues, RFID, RF app, Automation Systems. Platform keeps Dashboard,
Products, Inventory, Users, Settings, Audit, Notifications, Import/Export, Documents, Workflows,
Report Builder, Integration Hub/Marketplace/Stores. Add-ons take Billing Statements, Client
Portal, Invoicing, B2B Portal (3PL); EDI Automation (EDI); all AI pages and MCP Server (AI).

---

## 3. HotWax as reference: match it, then beat it 100×

What the HotWax source shows (order-routing component). Routing is a hierarchy:
**OrderRoutingGroup** (scheduled job per product store) → **OrderRouting** (filtered, sorted
order batch) → **OrderRoutingRule** (inventory conditions + actions) → **OrderRoutingRuleAction**
(next rule / move to queue / partial-allocation policy), with **OrderFilterCondition**,
**OrderRoutingRuleInvCond** (facility group, distance, brokering-safety-stock, shipping-limit
checks), **OrderRoutingRun/BatchRun/JobRun** history, and an **OrderItemsQueue**. Rules compile
to SQL (`get#OrderRoutingRuleSql`). Separate apps cover picking, receiving, BOPIS, transfers,
inventory count, pre-orders, users and NetSuite.

| Area | HotWax does | Nexus will do (the "100× better" bar) |
|---|---|---|
| **Routing** | Groups → routings → rules → actions, SQL-compiled, scheduled batches, run history | Same hierarchy **plus** (1) a *simulation sandbox*: replay last week's orders against a draft rule set and diff cost, SLA and split rate before publishing; (2) per-order *decision explanation* ("chose DC-2: in stock, 212 km, $6.40, capacity 61%; rejected Store-9: below safety stock"); (3) real geodistance and carrier-rate cost; (4) live capacity from WMS (Suite) instead of static limits; (5) versioned rule sets with rollback |
| **Inventory/ATP** | Safety stock, brokering thresholds, online ATP by channel | Same **plus** an event-sourced inventory ledger (every unit movement auditable), channel-level ATP with reservations TTL, forecast-aware safety stock (AI add-on) |
| **BOPIS** | Strong store app | Same **plus** curbside geofence check-in, customer live status page, SLA timers in the order twin |
| **Picking/Receiving/Count apps** | Separate Ionic apps | One offline-first RF PWA with scan-first UX, voice, batch/cluster pick, photo proof, and the same design system |
| **Transfers** | Transfer orders app | Transfers planned by the replenishment engine, with in-transit visible on the twins |
| **NetSuite** | Mature connector | Real NetSuite connector (T-15) with field-mapping UI, replay and DLQ console |
| **Ops visibility** | Lists and dashboards | **Warehouse Twin** and **Order Flow Twin** (§5): spatial, live, game-like control rooms |
| **Admin** | Moqui screens | Modern admin: SSO/MFA, SCIM, audit search, rule-set diffing, entitlement-aware UI |
| **Extensibility** | Moqui services | Webhooks + typed public API + event stream + MCP tools for AI agents |

**Licensing:** HotWax OMS components are Apache-2.0, so ideas, data models and algorithms can be
used freely. Any copied code keeps its licence header and goes into `NOTICE`. `poorti`, `ai`
and `lovers` have no clear open licence, so do not copy from them.

---

## 4. Frontend rebuild: enterprise, interactive, modern-minimal

### 4.1 Principles

1. **Every screen works or it doesn't ship.** No mock data, no "coming soon", no dead buttons.
   Each feature has an E2E test against the real API (Playwright) in CI.
2. **Calm by default, dense on demand.** White space, one accent colour, comfortable/compact
   density toggle, and data tables that scale to 100k rows.
3. **Keyboard-first:** ⌘K command palette (navigate, search any entity, run actions), `/` to
   search, `g o` for orders and `g w` for warehouse, `?` for the shortcut sheet.
4. **Live:** WebSocket push into React Query caches, with optimistic updates and undo toasts.
5. **Every state designed:** loading skeletons, empty states with a next action, error states
   with retry and correlation id, permission-denied, offline.
6. **Accessible:** WCAG 2.2 AA, focus rings, reduced-motion, colour-blind-safe status palette.

### 4.2 Stack decisions

| Concern | Choice | Why |
|---|---|---|
| Framework | React 18 + Vite + TS (keep) | Already in place; no rewrite risk |
| Design system | Radix primitives + Tailwind tokens, as an in-repo `@nexus/ui` package (shadcn-style, owned code) | Accessible, themeable, no vendor lock |
| Data grid | TanStack Table + TanStack Virtual | Saved views, column pinning, bulk actions, 100k rows |
| Forms | react-hook-form + zod, schemas generated from OpenAPI | One source of truth for validation |
| API client | OpenAPI-generated typed client + React Query | Removes ~65 hand-written API modules and their drift |
| State | React Query (server), Zustand (UI) | Already used, minimal |
| Charts | Recharts → visx for custom | Keep the bundle sane |
| 3D | three + @react-three/fiber 8 + drei 9 | WareTrack-class twins |
| Routing | React Router 6 data routers, lazy per module | Code-split per edition |
| Testing | Vitest + Testing Library, Playwright E2E, Storybook + visual regression | "100% working" proof |

### 4.3 App shell

- **Top bar:** module switcher (OMS / WMS / Platform, entitlement-aware), global search with ⌘K,
  site/facility picker (as in WareTrack), live clock with connection state, notifications,
  user menu with theme and density.
- **Left rail:** module navigation, collapsible, with favourites and recent items.
- **Page pattern:** title + primary action → saved-view tabs → filter bar → grid → **detail
  drawer** (no page reload) → full detail page for deep work.
- **Activity and audit side panel** on every entity.

### 4.4 Feature inventory → rebuild order

All 94 pages are rebuilt on the design system in this order. A page counts as done only when
its E2E passes on real data:

1. **Shell + design system + login/MFA/SSO** (incl. MFA enrolment UI for the new endpoints)
2. **Twins:** Warehouse Twin, Order Flow Twin (§5): the flagship demo pages
3. **OMS core:** Orders (grid + drawer), Order Detail, Create Order, Routing (rule builder +
   simulator + run history), Brokering Queue, ATP, BOPIS, Returns
4. **WMS core:** Receiving/ASN, Waves, Picking, Packing, Shipping/Labels, Yard/Dock, Counts, RF app
5. **Platform:** Products, Inventory, Customers, Users/RBAC, Settings, Integrations, Import/Export, Audit
6. **Add-ons:** 3PL Billing + Client Portal, EDI, AI pages, Analytics/Report Builder
7. **Retire** duplicate or placeholder pages (e.g. `AnalyticsPage` vs `AnalyticsDashboardPage`,
   `InventoryPage` vs `InventoryEnhancedPage`, `ReturnsPage` vs `ReturnsEnhancedPage`, the
   hardcoded `WarehouseDashboardPage` KPIs)

---

## 5. The flagship pages: live digital twins

### 5.1 Warehouse Twin (WMS), modelled on WareTrack

**Layout** (light theme, glass panels over a 3D isometric scene):

| Region | Content | Data source |
|---|---|---|
| Top-left KPI tiles | Stock on hand (pallets/units, Δ today), Trucks on site (inbound/outbound), On-time delivery (30d) | `/inventory`, `/yard/*`, `/analytics/carrier-performance` |
| Top bar | Site switcher "WH-01 · Riverside Hub · 78% full · 1/2 docked", live clock, search "sites, trucks, forklifts, pallets, shipments" | `/warehouses`, capacity fields |
| 3D scene | Buildings sized from zones, dock doors from `nx_dock_doors`, yard slots, trucks at doors/in yard/en route, forklifts with task state, pallets/staged loads, pins on selected items | warehouses, zones, dock doors, yard vehicles, equipment, tasks, shipments |
| Right inspector | Selected entity: pallet (SKU, qty, weight, lot, location, received), truck (carrier, shipment, ETA, bay, cargo progress), site (stock vs capacity, bays busy, outbound today, put-aways today, top inventory with stock status, forklift fleet) | entity endpoints |
| Bottom-left | Shipment tracking stepper: Order Confirmed → Picked → Loading n/m → In Transit → Delivered, with ETAs | shipments + order timeline |
| Bottom-right | Tabs Docks / Forklifts / Trucks: status chips (Loading, Unloading, Docking, En route, At gate, Available) and progress bars | yard/dock, equipment, shipments |
| Map controls | Zoom, rotate, reset, home; click-to-focus with camera fly-to | — |

**Live behaviour:** STOMP topics `/topic/{tenant}/yard`, `/topic/{tenant}/tasks` and
`/topic/{tenant}/shipments` move trucks and forklifts. Animation interpolates between server
states and never invents positions. When a site has no equipment telemetry, forklifts are
placed by their current task's zone, and the UI says so.

**Performance budget:** instanced meshes, ≤ 150 draw calls, 60 fps on an M1 / 30 fps on an
integrated-GPU laptop. The 3D chunk is lazy-loaded and kept out of the main bundle. A 2D
fallback map serves low-power devices and `prefers-reduced-motion`.

### 5.2 Order Flow Twin (OMS): the same visual language for orders

- **Scene:** a stylised network map (2.5D) of the tenant's **nodes**: DCs, stores, 3PLs,
  drop-ship vendors. Arcs animate as orders are brokered from channel → node → customer region.
- **KPI tiles:** Orders today (Δ), Brokered within SLA %, Split rate, Backorders, Cost per order.
- **Inspector:** node (ATP by top SKUs, capacity used, fulfillment limit, today's queue) or order
  (timeline, routing decision explanation, allocation per line, shipments).
- **Bottom-left:** order lifecycle stepper (Created → Approved → Brokered → Allocated → Picked →
  Shipped → Delivered) with SLA timers.
- **Bottom-right tabs:** Brokering queue / Exceptions / Channels, with live counts.
- **Simulation mode:** toggle "What-if" to replay the last N orders under a draft rule set and
  watch the arcs re-route, with a cost/SLA diff (the routing simulator from §3).

### 5.3 Data and API work needed

- `GET /twin/warehouse/{id}`: one aggregate snapshot (site, zones, doors, yard, equipment,
  active tasks, staged loads, KPIs), so the scene loads in one request.
- `GET /twin/network`: nodes, capacity and today's flows for the OMS twin.
- WebSocket deltas on the topics above, published from the outbox (Phase 1).
- Optional layout config per site (`nx_site_layouts`: building footprints, door positions, yard
  slots). Without it, a layout is generated automatically from zones and doors.

---

## 6. Row-by-row path to 10

Weights and rows as in doc 12 §4. "→ 10" is the work that takes each row to 10.

| Row | NP | NR | → 10 (presence + readiness) |
|---|---|---|---|
| Multi-channel intake | 9 | 6 | Real eBay, Walmart, Etsy, TikTok Shop, WooCommerce; contract tests; Shopify visible; per-channel sync health |
| BOPIS | 10 | 7 | Store app on design system, curbside geofence, customer status page, pilot store hours |
| Ship-from-store / endless aisle | 8 | 6 | Store fulfilment workbench, store capacity in routing, endless-aisle checkout flow |
| DOM / routing | 9 | 5 | HotWax-parity hierarchy, real distance and cost, simulator, explanations, versioned rule sets |
| Lifecycle / holds | 9 | 6 | Enum state machines with guarded transitions, holds, fraud/risk hooks |
| Returns / RMA | 10 | 7 | Configured refund rates, carrier return labels, customer return portal |
| Multi-location inventory | 9 | 7 | Event-sourced ledger, per-location audit, reconciliation reports |
| ATP / allocation | 8 | 7 | Channel ATP, reservation TTL, safety-stock policies, forecast-aware buffers |
| Receiving / ASN | 9 | 7 | Mobile receive with photos, discrepancy workflow, carrier ASN via EDI/API |
| Picking / slotting / labor | 8 | 6 | Batch/cluster/zone picking, slotting from velocity, engineered labor standards |
| Packing / kitting | 8 | 6 | Cartonization, pack station with scale/printer integration (QZ Tray) |
| Shipping | 8 | 4 | Live FedEx/UPS/USPS/DHL + EasyPost/Shippo; ZPL printing; manifests; tracking webhooks |
| Yard / dock | 8 | 6 | Gate check-in app, appointment self-scheduling for carriers, twin integration |
| WES / automation | 8 | 3 | Real integration (e.g. Locus, OPC-UA bridge) behind the command model |
| Mobile RF | 8 | 6 | Rebuilt RF PWA; device management; production hours at pilots |
| Procurement | 9 | 6 | Full PO lifecycle, 3-way match, supplier portal |
| Finance | 9 | 6 | QuickBooks + Xero + NetSuite GL sync, tax engine hook (Avalara) |
| 3PL billing / portal | 8 | 6 | Self-serve client portal, usage-based billing runs, invoice payments |
| EDI | 9 | 7 | Partner onboarding UI, AS2/SFTP transports tested, 997 reconciliation |
| Integration hub | 9 | 5 | Remove stubs; 25 real connectors with contract tests; mapping UI; DLQ console |
| AI / ML | 8 | 4 | Real-data forecasting that beats seasonal-naive, published backtests, explainable replenishment |
| Security | 9 | **7** ✅ | Phase 0 done; next: HttpOnly refresh cookie, SCIM, pen-test, SOC 2 Type I |
| Honesty | 10 | 7 | Re-score docs from evidence; public status page; `simulated` flags surfaced in UI |
| Deployment | 8 | 6 | Outbox, ShedLock, Testcontainers, blue/green, one-click Helm, licence keys |
| Cost / TTV | 9 | 9 | 15-minute guided onboarding, sample-data tenant, importers from Shopify/HotWax/NetSuite |
| Track record | 3 | 2 | Pilots → paid customers → case studies (the only row code can't move) |

---

## 7. Roadmap

Estimates assume one full-time engineer plus Claude-assisted development, and run sequentially.
With two engineers, phases B/C and D/E overlap.

| Phase | Weeks | Scope | Exit gate |
|---|---|---|---|
| **0 Security** ✅ | done | §1 | 816/403 tests, RLS proven on Postgres |
| **A Platform hardening** | 1–3 | Outbox, ShedLock, enum state machines, `@Version`, Testcontainers + tenant-isolation E2E, coverage gate 50% | Chaos test passes; 2 replicas run jobs once |
| **B Design system + shell + twins** | 3–7 | `@nexus/ui`, app shell, ⌘K, login/MFA UI, **Warehouse Twin**, **Order Flow Twin**, twin APIs | Twins live on real data; Lighthouse ≥ 90 |
| **C Modularity + entitlements** | 6–9 | Spring Modulith boundaries, subscriptions + `@RequiresModule`, frontend module registry, fulfillment contract | OMS-only and WMS-only tenants work end to end |
| **D OMS to 10** | 9–15 | Routing hierarchy + simulator + explanations, real distance/cost, ATP ledger, OMS pages rebuilt | Routing beats HotWax checklist; OMS E2E green |
| **E WMS to 10** | 13–20 | Live carriers + labels, picking depth, pack station, yard apps, RF rebuild, WMS pages rebuilt | Real labels in sandbox; WMS E2E green |
| **F Integrations + add-ons** | 18–24 | Stubs removed, NetSuite/eBay/Walmart, 3PL portal, EDI onboarding, AI on real data | ≥ 25 real connectors with contract tests |
| **G Pilots** | 20+ | 2–3 design partners, SLOs, status page, case study | Track-record row ≥ 5 |

**Score trajectory (weighted):**

| After | NP | NR |
|---|---|---|
| Phase 0 (now) | 8.4 | ~6.0 |
| A | 8.5 | 6.3 |
| B | 8.8 | 6.6 |
| C | 9.0 | 6.9 |
| D | 9.5 | 7.8 |
| E | 9.8 | 8.6 |
| F | **10.0** | 9.1 |
| G (pilots) | 10.0 | **9.4** |
| + 12 months of production | 10.0 | **9.8–10** |

---

## 8. Definition of done (every feature)

1. Works on real data from the real API: no mock, seed-only or `simulated` path without a visible badge.
2. Tenant-isolation test: tenant B can't see or change it.
3. Unit + integration tests; module coverage ≥ 70%.
4. Playwright E2E for the main user journey, run in CI.
5. Designed loading/empty/error/permission states; keyboard accessible; WCAG AA.
6. Permission-gated (RBAC) and entitlement-gated (edition).
7. Audit-logged mutations; observable (metrics + trace id in errors).
8. Documented in the in-app help and the public API reference.
