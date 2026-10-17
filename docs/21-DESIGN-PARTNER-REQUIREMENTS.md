# 21 — Design partner A: requirements, and where Nexus stands

> **Source.** Two discovery conversations with a prospective design partner, summarised and anonymised.
> No recordings, transcripts, names or company identifiers are kept in this repository. The partner is
> referred to as "Partner A", and the incumbent systems are named only by product category and vendor.
>
> **Status labels** — **Done** built and covered by a test · **Partial** exists but does not meet the
> need · **Missing** not built · **Unverified** code exists, never run against the real system.
> Status was checked against the code on the date of this file, not recalled.

## 1. Who Partner A is

- A multi-brand footwear and accessories retailer with **about 300 stores plus distribution centres**,
  e-commerce above $100M a year, moving all brands to **Shopify** and **Shopify POS** (store roll-out first;
  the OMS layer follows, planned for 2027).
- Current stack: Shopify (customer transactions) · an enterprise OMS (store pick/pack) · a legacy POS being
  retired · **SAP** (inventory movements, finance; pushes inventory to Shopify nightly) · a label and
  tracking aggregator · carrier accounts that differ by store.
- Open to a vendor without a pre-built SAP connector if the vendor will build it with them.
- Peak: about 50,000 orders on Black Friday.

## 2. What they said they need, in priority order

The second call ends with the customer stating the order: **the core fulfilment problem first; transfers
and receiving second; SAP constraints decide fit.**

| ID | Requirement (their words, condensed) | Nexus today |
|---|---|---|
| A1 | **Reject a pick on a multi-unit order without creating a split shipment.** If a store rejects one unit, check whether another store holds the *whole* quantity and send the entire order there in one package. | **Done, live on a local database.** `POST /rejections` re-routes the order in the same transaction (`OrderRoutingService.rerouteAfterRejection`): releases the rejecting store's reservation, cancels its allocation, sends the whole order to the best other node that holds all of it, never back to a store that already rejected it. If no single node holds the whole order, the store keeps its lines, only the rejected units are released, and an open shortfall exception is raised. Tests: `RejectionRerouteTest`. Not yet done: the store's open pick list for that order is not cancelled automatically (picklists are not linked to orders by a column). |
| A2 | **Rejection reasons change available-to-promise, not ERP on-hand.** A rejection reason code sets the inventory impact in the OMS; the ERP stays untouched until a manager approves a cycle count. | **Done, live on a local database.** Unless the reason is `RESTOCK`, the rejected units move into the held bucket at that store at once (online availability drops, on-hand unchanged). A manager's *process* step writes off or returns to vendor for real, with a ledger line; `QUARANTINE` stays held for a count. `inventoryAdjusted` is true only when on-hand really changed (it used to be set with nothing moved). |
| A3 | **Reduce split shipments; limit partial rejection** when another node could fulfil the whole shipment (configurable profile). | **Partial.** Routing prefers fewer-node plans; no rejection profile. |
| A4 | **Business users change sourcing priorities by season** (Black Friday: cheapest single package, ignore speed; Christmas: guaranteed date, proximity first), without an IT ticket. Eventually by plain-language instruction to an agent, QA'd by IT. | **Partial.** Rule engine (`NxRoutingRule`, ordered, conditions/actions) and strategies exist. Node-scoring weights are constants in code. No scheduled/seasonal profiles. No inventory-health factor. |
| A5 | **Routing simulation**: compare rule sets on a snapshot of live orders and inventory by split shipments, unfillable rate and shipping distance; sandbox with checkpoints and approval before applying. | **Partial.** `simulateAllocation` dry-runs one order. No batch/what-if comparison, no approval workflow. |
| A6 | **Capacity controls**: manager can set a store's fulfilment capacity to zero (rejecting assigned orders) separately from online availability; caps stop new routing, manual assignment still possible. | **Unverified.** A capacity log exists; the separate switches were not checked. |
| A7 | **OMS owns online ATP, buffers and what is published**; SAP supplies on-hand in phase 1, the OMS becomes the ledger later. | **Partial.** Per-location publish with buffers and caps is live-tested for one SKU. No SAP feed. |
| A8 | **Event-by-event inventory sync into Shopify** with a queue (never drop a call on rate limits), separate app lanes for orders and inventory, an audit view of every event and its latency, mapped to Shopify reason codes. | **Partial.** Ledger and publish exist; a full-catalogue publish is not event-driven, and has not been run live. |
| A9 | **Store tool**: wave picking, **bulk pick with single-scan sort/back-out**, one label per package, **item-to-package-to-tracking** so a loss in transit names the item. | **Partial / Missing.** Waves exist; package-level item tracking is missing. |
| A10 | **Labels**: pre-generate while picking, regenerate after errors, address validation flagged before picking, per-store FedEx accounts, tracking pushed to Shopify; drop EasyPost's per-label SaaS fee. | **Partial.** Carrier adapters exist, unverified against real accounts. No address validation. |
| A11 | **Store dashboard**: assigned orders, fill rate, rejection levels, picker leaderboard. | **Partial.** Stats exist; no leaderboard. |
| A12 | **Broken size runs**: prefer stores whose size family is incomplete (orphaned or returned items), even when farther. | **Missing.** |
| A13 | **Transfers received natively in Shopify POS**, sellable on arrival; ASN/shipment data arrives late from SAP; transfers may be split. | **Partial.** Transfer orders exist; Shopify POS receiving not built. |
| A14 | **SAP integration**: take the inventory file first, then two-way; send what SAP needs to reconcile (shrink, finance). | **Missing.** No SAP connector (the catalog lists generic platforms only). |
| A15 | **Phased pilot**: a brand without stores first, then add stores. | Process, supported by per-store connections. |
| — | 30 store price lists in POS | Not an OMS function (Shopify POS handles it). Out of scope. |

## 3. What Nexus builds first, and why

1. **A1 + A2** — rejection → inventory impact → whole-order re-route. **Built** (see the table above). It is the pain point the customer
   called "a dream" and says nobody solves, and two parts of it are broken today in a way that would fail a
   proof of concept.
2. **A4 + A5** — seasonal routing profiles and a batch what-if comparison. This is the demo that wins
   against a Fluent-style "open a ticket" experience.
3. **A9, A10, A11** — store tool depth.
4. **A14** — SAP inventory import, designed with the customer.
5. **A13** — Shopify POS transfer receiving.

Everything else stays on the list with its label until it has a test and, where it touches a real
system, a dated live run recorded in [20-INTEGRATIONS.md](./20-INTEGRATIONS.md).

## 4. Other accounts that shape the same product

Recurring themes from other retail operations: Shopify-to-ERP fulfilment loops, store transfers and cycle
counts, BOPIS and on-demand delivery, returns through a third-party portal, inventory feeds to marketing
channels. These are treated as market patterns, not as another company's material.
