# Nexus Market-Gap Tickets — Round 2 (T-08 → T-13)

> **Purpose:** The first round of tickets (`nexus-market-gap-tickets.md`, T-01 → T-07) covered the WMS/store/planning gaps surfaced from the SoC, SIMKHAI, and gorjana transcripts. This round mines the **remaining transcripts** — RFID, Shopify↔NetSuite fulfillment reconciliation, bidirectional sync/conflict, gorjana returns/exchange, MCP/GraphQL, and the SoC shipping-label work — to surface **6 more distinct, market-defining gaps** and turn them into buildable implementation tickets with real-flow acceptance criteria.
>
> **Source transcripts:** `rfid-inventory.json`, `shopify-fulfillment-netsuite-integration.json`, `chelan-weekly.json`, `rails-hotwax.json`, `gorjana-weekly.json`, `mcp-graphql-shopify-oms-integration.json`, `soc-hotwax.json`.
>
> **Companion docs:** [`nexus-business-process-alignment.md`](./nexus-business-process-alignment.md) (16 real flows) · [`nexus-market-gap-tickets.md`](./nexus-market-gap-tickets.md) (T-01 → T-07).

---

## How These Tickets Map to the Plan

| Ticket | Gap | Priority | Source transcript |
|--------|-----|----------|-------------------|
| T-08 | RFID / serialized inventory (EPC registry) | P1 | `rfid-inventory.json` |
| T-09 | Shopify→OMS fulfillment reconciliation (partial / cross-location) | **P0** | `shopify-fulfillment-netsuite-integration.json` |
| T-10 | Returns / exchange financial reconciliation (split credit memos, cross-location tax) | P1 | `gorjana-weekly.json` |
| T-11 | MCP server for OMS (AI-agent debugging surface) | P1 | `mcp-graphql-shopify-oms-integration.json` |
| T-12 | Carrier shipping-label integration (Jitsu/SAPI, ZPL/PDF) | **P0** | `soc-hotwax.json` |
| T-13 | Bidirectional sync & conflict handling (stale-overwrite prevention) | P1 | `chelan-weekly.json`, `rails-hotwax.json`, `gorjana-weekly.json` |
| T-03+ | **Expansion:** ship-by / ship-after filter + manual pick-wave release | P0 | `soc-hotwax.json` |

> **Why these matter:** T-09, T-12, and T-03+ are **P0** because they are *data-integrity and peak-season* issues that affect every live account and are time-boxed by real deadlines (SoC peak: integration ready Sept 11, test shipments Sept 14, full volume Oct 28). T-08, T-10, T-11, T-13 are **P1** differentiators that close the biggest remaining feature gaps vs. HotWax — including the #1 integration pain (sync conflicts, T-13).

---

## T-08: RFID / Serialized Inventory (EPC Registry)

**Market evidence:** `rfid-inventory.json` — HotWax is actively scoping RFID with a partner (Jonathan Sew Hoy). The entire call is a design discussion on how to handle RFID guns, EPC uniqueness, dedup, and serialized cycle counting. This is a **roadmap item HotWax wants to make "widely available"** — a clear differentiator Nexus can own.

### Real workflow (acceptance criteria)
1. **RFID gun receiving** — scan a box of goods with a wand; the system registers each **EPC** (unique serial) at the store/location. High event throughput over Bluetooth (many tags scanned at once).
2. **EPC → UPC decode** — decode the EPC string on the fly to extract the UPC/SKU, while also storing the full EPC. Handles **pre-encoded labels from other sources** (not just self-encoded).
3. **Dedup in-session** — the same tag is read 100× while the wand is held in place; the system must not double-count. Record the EPC in the backend but **don't show it to the user**.
4. **Reject foreign tags** — wands pick up tags from the store next door, shoes, clothing; decode the EPC and **reject tags that aren't this store's inventory**.
5. **Serialized cycle counting** — full store scan: EPCs that existed but are now missing, new EPCs never received, and EPCs in **damaged / on-hold status** (counted separately, not toward the active count).
6. **Two modes** — (a) full EPC registry (one-for-one serialized tracking), or (b) decode-to-UPC (traditional quantity uptick). Both must be supported.
7. **Retagging** — for missing tags or returns, print/re-encode a new tag (printer calibration is a known pain point).

### Build scope
- **EPC registry entity** — `serialized_inventory` table keyed by EPC, with status (active / damaged / on-hold / missing), location, SKU (decoded UPC), received-at.
- **RFID input adapter** — a middleware layer that sits between the RFID gun and existing inventory apps (receiving, cycle counting, fulfillment). Handles: read, dedup, decode, encode, reject-foreign.
- **High-throughput ingestion** — batch/stream EPC reads over Bluetooth; dedup by EPC within a session; idempotent writes.
- **EPC decode service** — parse EPC → UPC + serial; support SGTIN-96 and common encodings; handle pre-encoded labels.
- **Serialized cycle count** — reconcile full-store scan against the EPC registry; produce missing / new / status-flagged results.
- **Retag / print** — re-encode + print a new tag for missing tags and returns.
- **Two-mode toggle** — per-location config: full EPC registry vs. decode-to-UPC.

### Acceptance test
A store associate receives a box via RFID gun → all EPCs registered at the store. A full-store cycle count detects: (a) an EPC that was received but is now missing, (b) a foreign tag that is rejected, (c) an EPC in damaged status counted separately. No double-counting when the wand is held in place. The same flow works in decode-to-UPC mode (quantities only, no EPC registry).

### Notes / open questions
- **Partner vs. in-house:** HotWax is weighing white-labeling an RFID vendor vs. building in-house. Nexus can start with the **input-adapter layer** (which is pure software) and defer hardware/printing.
- **This is a differentiator:** HotWax explicitly says RFID is "perpetually not smooth" and wants a better solution. Nexus owning the EPC registry + input adapter is a wedge.

---

## T-09: Shopify→OMS Fulfillment Reconciliation (Partial / Cross-Location) — **P0**

**Market evidence:** `shopify-fulfillment-netsuite-integration.json` — a live account (gorjana) is hitting a **data-integrity failure**: HotWax's strict ship-group matching breaks when Shopify fulfills partially or at a different location, causing order state to diverge across **Shopify / OMS / NetSuite** and directly corrupting inventory.

### Real workflow (acceptance criteria)
1. **Strict-match failure** — HotWax requires an *exact* match (same ship group, same number of items, exact facility) before marking a fulfillment. When Shopify fulfills **one of two items** at a store, or **changes the facility**, the OMS never marks it → order stuck, never synced to NetSuite.
2. **Cross-system state mismatch** — the order is fulfilled in Shopify but not in OMS/NetSuite → inventory (QH) is reduced in Shopify but not in NetSuite → **direct inventory corruption**.
3. **Partial fulfillment** — a store fulfills only one of two routed items; the OMS must accept the partial as truth and reconcile the remaining item.
4. **Location change** — a CSR changes the fulfillment location (e.g., due to no inventory, printer down, shop closed) and fulfills elsewhere; the OMS must accept the actual fulfillment as truth and move the order items accordingly.
5. **"Fulfillment is the truth"** — once Shopify says an item is fulfilled, that is a fact; the OMS must save it, adjust ship groups, and propagate to NetSuite — not reject it.
6. **Rejected-order metafield** — when an order is rejected in POS, save a metafield (like NetSuite's custom field) and sync it back via the order-updated webhook.

### Build scope
- **Reconciliation engine** — on Shopify fulfillment sync, detect partial / cross-location fulfillment and **accept it as truth**: mark the fulfilled items, adjust ship groups, and propagate the delta to NetSuite.
- **Relaxed matching** — replace strict exact-match with a **tolerant matcher** that accepts: partial line fulfillment, facility changes, and quantity deltas — while still flagging genuine anomalies for review.
- **Cross-system state sync** — a reconciliation job that detects orders fulfilled in Shopify but not in OMS/NetSuite, and repairs the state (including inventory QH).
- **Rejected-order metafield sync** — read the POS rejection metafield and sync it back via order-updated webhook.
- **Mismatch reporting** — a report of "completed on Shopify but not in OMS" orders, with the reason (ship-group mismatch, facility change, partial).

### Acceptance test
An order with 2 items routed to a store; the store fulfills only 1 item. The OMS marks the fulfilled item, keeps the other open, and syncs the partial to NetSuite (QH reduced correctly). A second order fulfilled at a different facility than routed is accepted as truth and re-ship-grouped. No order is left in a stuck state, and inventory stays consistent across Shopify/OMS/NetSuite.

### Notes
- **This is the #1 integration pain across accounts** (sync conflicts were flagged as HotWax's top weakness). Fixing it is a direct market win.
- **Nexus's "honest data" value** is directly served by this — the OMS should never silently diverge from the source of truth.

---

## T-10: Returns / Exchange Financial Reconciliation

**Market evidence:** `gorjana-weekly.json` — a live account needs **split credit memos** for multi-transaction returns, **cross-location exchanges with tax differences**, and **store-credit vs. like-for-like** handling. HotWax currently creates a single credit memo and applies multiple invoices to it, which breaks accounting.

### Real workflow (acceptance criteria)
1. **Split credit memos** — a return with two different transactions in the same claim (e.g., two HG-app transactions) must create **two separate credit memos**, not one combined.
2. **Block multi-select, keep split capability** — block employees from selecting more than one outcome per return, but keep the ability to **split** into separate transactions (store credit + like-for-like) with a shared identifier (the sales return).
3. **Lesser-value exchange** — customer exchanges for a lower-cost item; the difference is refunded as store credit. Must produce a single credit memo with two invoices (exchange + store credit) applied correctly.
4. **Cross-location exchange tax** — exchange from a lower-tax location to a higher-tax location (e.g., Long Beach); the tax delta must be handled correctly in NetSuite.
5. **Customer-deposit balancing** — some transactions apply a customer deposit to balance the invoice; this payment must not affect accounting incorrectly.

### Build scope
- **Credit-memo splitting** — allow a return claim to generate multiple credit memos (one per transaction), each with its own invoice application.
- **Outcome model** — per-return-outcome: store credit, like-for-like exchange, refund; with split capability and a shared sales-return identifier.
- **Tax-delta handling** — compute and apply tax differences for cross-location exchanges.
- **Payment reconciliation** — correctly classify customer-deposit balancing payments so they don't corrupt accounting.
- **Impact report** — a report of how many orders are affected by the current single-memo behavior, to size the migration.

### Acceptance test
A return with two transactions generates two separate credit memos. A lesser-value exchange produces one credit memo with two correctly-applied invoices (exchange + store credit). A cross-location exchange applies the correct tax delta in NetSuite. No accounting corruption.

### Notes
- **This is a finance/accounting gap** — high-stakes, low-tolerance for error. It's P1 because it affects a subset of orders, but the accounting impact makes it high-visibility.
- **Nexus's finance module** (feature 08) should be the home for this.

---

## T-11: MCP Server for OMS (AI-Agent Debugging Surface)

**Market evidence:** `mcp-graphql-shopify-oms-integration.json` — HotWax is actively building an **MCP server** to expose OMS tools to AI agents for **production debugging**. The goal: an AI agent can query the production system directly, with **query permissions and cost limits**, so support can debug issues without manual DB spelunking.

### Real workflow (acceptance criteria)
1. **MCP tool export** — expose OMS APIs (GraphQL/REST) as **MCP tools** so an AI agent can talk to the production system directly.
2. **Query permissioning** — control which queries an agent can run (read vs. write), and what data it can access (per-tenant, per-resource).
3. **Cost / rate limiting** — track and limit query cost/volume so an agent can't run away with expensive queries.
4. **Debugging use case** — a support agent can answer "my picklist isn't printing" or "my order is stuck" by querying the system directly, without a human digging through the DB.
5. **Auth inheritance** — MCP tools inherit the same auth/permission model as the REST/GraphQL layer (custom artifact auth, per-entity permissions).

### Build scope
- **MCP server** — a Spring Boot MCP server exposing OMS tools (order lookup, inventory, fulfillment status, shipment, returns) as MCP tools.
- **Tool registry** — define which OMS operations are exposed, with read/write classification.
- **Permission mapping** — map MCP tools to the existing RBAC/permission model (per-tenant, per-entity).
- **Cost / rate limiting** — per-agent query budget and rate limits; logging of agent queries.
- **GraphQL/REST bridge** — reuse the existing GraphQL/REST layer as the backend for MCP tools (single source of truth for auth).

### Acceptance test
An AI agent (via MCP) can query an order's fulfillment status, inventory at a location, and shipment state — with the same permissions as the calling user. A write-capable tool is blocked for a read-only agent. Query cost/volume is tracked and limited. The agent can debug a "stuck order" without DB access.

### Notes
- **This is a differentiator** — HotWax is building it for internal debugging; Nexus can productize it as a **customer-facing AI support surface** (an AI agent that answers "where's my order" and "why is this stuck").
- **Nexus already has an AI platform** (feature 11) and Kafka events — this is a natural extension.
- **Security is paramount** — MCP tools must never bypass tenant isolation or RBAC.

---

## T-12: Carrier Shipping-Label Integration (Jitsu/SAPI, ZPL/PDF) — **P0**

**Market evidence:** `soc-hotwax.json` — SoC is integrating a new carrier (Jitsu) for shipping labels, with a **hard peak-season deadline** (integration ready Sept 11, test shipments Sept 14, full volume Oct 28). The work covers label format (ZPL/PDF), carrier API integration, and label generation in the pick/pack flow.

### Real workflow (acceptance criteria)
1. **Carrier label generation** — generate a shipping label from the carrier API (Jitsu) during the pick/pack flow, using the carrier's standard service (e.g., ground).
2. **Label format** — support **ZPL and PDF** label formats; the UI must render/print the label correctly (ZPL data → printable label).
3. **Carrier API integration** — create a shipment in the carrier (Jitsu) with the right JSON schema; get the label back.
4. **Custom label (SAPI)** — on production, use the custom label from SAPI (like the existing VHO integration); needs SAPI endpoints and details.
5. **Label data correctness** — verify all required fields appear on the label (a known issue: some fields weren't showing on the last implementation).
6. **Peak-season readiness** — labels must be correct at volume; no changes needed when volume ramps (Sept 14 test → Oct 28 full volume).

### Build scope
- **Carrier adapter** — a pluggable carrier-label adapter (Jitsu, SAPI, VHO) that creates shipments and returns labels.
- **Label format renderer** — ZPL and PDF rendering; UI preview + print.
- **Label data validation** — verify all required fields (ship-from, ship-to, service, tracking, weight, dimensions) appear on the label.
- **Pick/pack integration** — generate the label in the pick/pack flow (after pick wave, before/at pack).
- **Peak-volume test** — a UAT run with 50–100 orders to validate labels at volume before peak.

### Acceptance test
A pick wave is created for an order with the Jitsu carrier; the system generates a shipping label (ZPL/PDF) with all required fields. The label prints correctly in the UI. The same flow works with the SAPI custom label on production. A 50–100 order UAT confirms labels are correct at volume.

### Notes
- **This is P0 because of the hard deadline** — SoC's peak season is time-boxed, and label correctness at volume is non-negotiable.
- **Nexus's shipping/carrier module** (feature 04) is the home for this. A **pluggable carrier adapter** is the key abstraction — it makes adding any carrier (Jitsu, SAPI, UPS, FedEx) a config change, not a code change.
- **This is a repeatable pattern** — every OMS account needs carrier label integration; a clean adapter is a market win.

---

## T-03+ (Expansion): Ship-by / Ship-After Filter + Manual Pick-Wave Release

**Market evidence:** `soc-hotwax.json` — beyond the transit-time model in T-03, SoC needs a **filter-based manual release** workflow for holiday buckets: a planner filters orders by **ship-by date, ship-after date, and days-in-transit**, then **manually releases** them via pick waivers — without changing peak profiles.

### Real workflow (acceptance criteria)
1. **Filter by ship-by / ship-after / days-in-transit** — a planner can filter orders by these fields on the find/order screens.
2. **Days-in-transit as a filter** — "show all one-day transit orders" → select them → release them manually.
3. **Manual pick-wave release** — release selected orders via pick waivers without altering peak profiles.
4. **Weekly cadence** — e.g., Monday ship all 2-day transit, Tuesday ship 3-day, Thu/Fri keep 1-day. The planner needs to identify transit days without doing math.
5. **UAT before peak** — a 50–100 order UAT on the UAT instance to validate the screens before peak season.

### Build scope (adds to T-03)
- **Ship-by / ship-after fields** on find/order screens (currently only on order view).
- **Days-in-transit filter** — derived from carrier postal-route mapping (already in T-03 scope).
- **Manual pick-wave release** — select filtered orders → create pick wave → release, without touching peak profiles.

### Acceptance test
A planner filters to "one-day transit, ship-by tomorrow" → sees the matching orders → selects them → creates a pick wave → releases them. Peak profiles are unchanged. The same works for 2-day and 3-day transit buckets.

### Notes
- **This is a refinement of T-03**, not a new ticket — fold it into T-03's scope. The key addition is the **filter-based manual release** workflow (vs. the transit-time model itself).

---

## T-13: Bidirectional Sync & Conflict Handling

**Market evidence:** `chelan-weekly.json`, `rails-hotwax.json`, `gorjana-weekly.json` — sync conflicts are the **#1 integration pain across accounts**. A consigned order shipped in OFBiz got **undone** when NetSuite fed back stale data ("one overrides the other and changes what OFBiz had"). This is a silent data-corruption bug that erodes trust in the OMS.

### Real workflow (acceptance criteria)
1. **Sync cadence** — NetSuite→OMS every 5 min; OMS→NetSuite async (few seconds) + a recovery job (15-min).
2. **Conflict detection** — when a consigned order ships in the OMS but NetSuite feeds back stale data, the OMS must **not** silently undo the shipment. Detect the conflict (stale flag / version / lock) and preserve the newer state.
3. **Idempotent, retryable sync jobs** — sync jobs must be idempotent and retryable, with a recovery job that reconciles pending/queued/completed records.
4. **Reconciliation reporting** — daily reconciliation runs with pending/queued/completed counts, surfaced to ops.
5. **Field-level mapping control** — per-field mapping control (e.g., gross vs. rate) so operators can govern which side wins per field.
6. **Sync-status surfacing** — show sync status on order pages so operators can see when an order is out of sync.
7. **Unsupported transaction types** — handle unsupported NetSuite transaction types (e.g., FRTTRA) without breaking the sync pipeline.
8. **Missing consignment API** — gracefully handle missing consignment APIs (awaiting API for consign shipments).

### Build scope
- **Conflict detection engine** — versioning / stale-flag / optimistic-lock on synced entities; detect when an inbound update would overwrite a newer local state; reject or queue the conflict.
- **Idempotent sync jobs** — make all sync jobs idempotent and retryable; add a recovery job (15-min) that retries failed/pending records.
- **Reconciliation reporting** — pending / queued / completed counts per sync direction; daily reconciliation summary.
- **Field-level mapping control** — per-field direction/priority mapping (which side wins per field).
- **Sync-status surfacing** — order-page sync status indicator (in-sync / pending / conflict / out-of-sync).
- **Transaction-type handling** — graceful handling of unsupported transaction types (skip + flag, don't break the pipeline).

### Acceptance test
A consigned order is shipped in the OMS; NetSuite feeds back a stale (pre-ship) version. The OMS detects the conflict and **preserves the shipped state** instead of undoing it. The conflict is surfaced on the order page and in the reconciliation report. A sync job retried after a failure is idempotent (no duplicate records). Field-level mapping lets an operator set "gross wins" for a specific field.

### Notes
- **This is the #1 integration pain** across all HotWax accounts — fixing it is a direct market win and serves Nexus's "honest data" value.
- **Complements T-09** (fulfillment reconciliation): T-09 handles the Shopify→OMS direction; T-13 handles the general OMS↔ERP bidirectional conflict problem.
- **Nexus's integration hub** (feature 10) and Kafka eventing are the home for this.

---

## Sequencing & Dependencies

| Order | Ticket | Depends on |
|-------|--------|-----------|
| 1 | T-09 (fulfillment reconciliation) | — (data integrity, highest priority) |
| 2 | T-12 (carrier labels) | — (peak-season deadline) |
| 3 | T-03+ (ship-by/ship-after filter) | T-03 (transit-time model) |
| 4 | T-10 (returns/exchange finance) | — |
| 5 | T-13 (bidirectional sync/conflict) | — |
| 6 | T-08 (RFID) | — (input-adapter layer first) |
| 7 | T-11 (MCP server) | — (reuses existing RBAC/GraphQL) |

- **Data-integrity track:** T-09 → T-13 → T-10 (all fix cross-system state corruption)
- **Peak-season track:** T-12 → T-03+ (both time-boxed by SoC peak)
- **Differentiator track:** T-08 → T-11 (RFID + AI-agent surface = market wedge)

---

## Definition of Done (per ticket)

- [ ] Real workflow captured as acceptance criteria (from transcript, not invented)
- [ ] Build scope maps to Nexus modules (Spring Boot backend + React frontend + Flyway migration + tests)
- [ ] Acceptance test is executable and verifiable
- [ ] Sized for a single implementer
- [ ] No cross-tenant data leakage (RBAC respected)
- [ ] Follows Nexus patterns (mindmodel_lookup before coding)

---

## Next Step

1. **P0 first:** T-09 (fulfillment reconciliation) and T-12 (carrier labels) — both are data-integrity / peak-season issues affecting live accounts. **Full implementation specs are in [`nexus-p0-implementation-specs.md`](./nexus-p0-implementation-specs.md)** (Flyway V61/V62, Spring Boot services, REST endpoints, React UI, acceptance tests).
2. **Then P1 differentiators:** T-13 (bidirectional sync/conflict), T-08 (RFID), T-10 (returns finance), T-11 (MCP server).
3. **Fold T-03+ into T-03** and expand its scope with the filter-based manual release workflow.
4. **Product gaps are now fully ticketed** — all 16 alignment-doc flows map to either a ticket (T-01 → T-13) or an existing Nexus strength. The remaining work is **commercial readiness** (Track B/C): demo scripts per persona, pricing model, self-serve onboarding, go-live checklist, roll-off process.
4. Optionally expand any ticket into a full implementation spec (e.g., T-09 reconciliation engine, T-12 carrier adapter).
