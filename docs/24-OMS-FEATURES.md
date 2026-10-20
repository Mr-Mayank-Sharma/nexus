# 24. Nexus OMS: features

What the Nexus OMS gives a client, by area. Built on the OFBiz-aligned model ([doc 23](23-DATA-MODEL-OFBIZ-ALIGNED.md)).
Warehouse operations are Nexus WMS, built later; the OMS talks to any warehouse system through the OMS-WMS contract.

Status: **Works** built and tested today, moves to the new model · **Partly** exists but incomplete or never run on a
real account · **New** comes with the rebuild. Partner A references (A1-A15) are the anonymised design-partner
requirements in [doc 21](21-DESIGN-PARTNER-REQUIREMENTS.md).

## 1. Orders

| Feature | What it does | Status |
|---|---|---|
| Order list and search | All channels in one list; filters by status, channel, date, location; fast paging | Works |
| Order detail | Lines with pictures, customer, address, payments, recorded timeline of every change and who made it | Works |
| Ship groups | One order shipping to several addresses or from several locations, each tracked separately | New |
| Line-level status | Cancel, reject or ship part of an order, line by line | New |
| Edit order | Change address or items, split, merge, cancel with a reason | Partly |
| Manual and B2B orders | Phone and wholesale orders | Works |
| Holds and approvals | Hold for payment, risk or review; approve or release | Partly |
| Pre-orders and backorders | Released automatically when stock arrives | Partly |
| B2B release by ship date | Wholesale orders held until shortly before the requested ship date | Works |
| Adjustments per line | Discounts, tax and shipping per line, for exact refunds | New |
| Several payments per order | Card plus gift card or store credit | New |

## 2. Routing, reallocation and split control

| Feature | What it does | Status |
|---|---|---|
| Routing groups and rules | Built on screen: which orders, which locations, how to sort, what to do when nothing fits | New |
| Whole-order first | Prefer one location that ships everything; split only when allowed (per store setting) | Partly (A3) |
| Reject without splitting | When a store rejects one unit, move the whole order to a location that holds all of it, never back to a store that rejected it | Works (A1) |
| Reject-reason inventory impact | A rejection reason holds the stock online without touching ERP on-hand until a manager confirms a count | Works (A2) |
| Rejection profile | Per store: how many partial rejections are allowed before the whole order moves | New (A3) |
| Reallocate on demand | Move an order or a ship group to another location from the order screen, releasing the old reservation first | Partly |
| Reallocate in bulk | Move all open orders off a store that closes, is overloaded or is out of stock | New |
| Scheduled reallocation | A job retries unfillable and held orders when stock arrives or capacity frees up | Partly |
| Seasonal routing profiles | Black Friday (cheapest, single package) and Christmas (guaranteed date, nearest) profiles, switched on by date, without IT | New (A4) |
| Routing simulation | Run a rule set on a snapshot of real orders and stock; compare split shipments, unfillable rate and distance; approve before applying | Partly (A5) |
| Capacity controls | Daily order limit per location; a manager can stop new routing to a store without hiding its stock online; manual assignment still possible | New (A6) |
| Substitutes | Ship an equivalent product when the ordered one is short | New |
| Location groups | Route to a region or store cluster in priority order | New |
| Sort by sales velocity and weeks of supply | Protect fast-selling stores | New |
| Broken size runs | Prefer stores whose size run is incomplete, even when farther | New (A12) |
| Routing explanation | Why each location was chosen | Works |

## 3. Inventory

| Feature | What it does | Status |
|---|---|---|
| Stock per location | On hand, reserved, available to promise, safety stock | Works |
| Stock history | Every change with its reason | Works |
| Reservations | Which stock is held for which line | New |
| OMS owns online availability | Buffers, safety stock and what is published, per channel and location; ERP supplies on-hand | Partly (A7) |
| Event-by-event channel sync | Each stock change queued to the channel, never dropped on rate limits, with an audit view of every event and its delay | Partly (A8) |
| Stock from ERP and POS | SAP file first, then two-way; NetSuite; POS | Partly (A14) |
| Transfers | Store to store, warehouse to store; received in Shopify POS and sellable on arrival; split transfers | Partly (A13) |
| Store counts | Simple counts and adjustments in stores | New |
| Lots, expiry, serial numbers | Where needed | New |

## 4. Store fulfilment

| Feature | What it does | Status |
|---|---|---|
| Store task queues | Each store sees its orders | Works |
| Pick, pack, ship | Picklists, packing, box suggestion, labels | Works |
| Bulk pick with scan-to-sort | Pick many orders at once; one scan sorts or backs out an item | New (A9) |
| Item to package to tracking | Each item recorded in its package, so a loss in transit names the item | New (A9) |
| Buy online, pick up in store | Ready for pickup, collection, handover, with customer emails | Works |
| Reject with reason | Feeds routing and inventory | Works |
| Store dashboard | Assigned orders, fill rate, rejection levels, picker leaderboard | Partly (A11) |
| Endless aisle | Sell stock held elsewhere | Partly |

## 5. Nexus Gateway: shipping and customer messages

A separate shared service, in the spirit of HotWax's Unigate. Every client installation calls one API; carrier and
messaging credentials live in the gateway, per client and per store, never in the client's OMS database.

| Feature | What it does | Status |
|---|---|---|
| One carrier API | Rates, labels, void, return labels and tracking for every carrier through one call | New |
| Carrier accounts per store | Each store uses its own carrier account (for example per-store FedEx) | New (A10) |
| Address validation | Flag a bad address before picking starts | New (A10) |
| Labels early and again | Pre-generate while picking; regenerate after an error | New (A10) |
| Tracking back to the channel | Tracking number and status pushed to Shopify and the customer | Partly |
| No per-label SaaS fee | Direct carrier accounts instead of an aggregator charging per label | New (A10) |
| Customer messages | Pickup ready, rejection, cancellation, completion; email templates per store; Klaviyo events | New |
| Add a carrier once | A new carrier added to the gateway is available to every client and store | New |
| Gateway health | Every request with its result and timing | New |

## 6. Returns

| Feature | What it does | Status |
|---|---|---|
| Returns and exchanges | By line, with reasons and return window | Works |
| Receive and restock | At a store or warehouse, stock updated | Partly |
| Exact refunds | Per line with discounts and tax | New |
| Return labels | Through the gateway | New |

## 7. Channels and integrations

| Feature | What it does | Status |
|---|---|---|
| Shopify and Shopify POS | Orders, products and stock in; stock, fulfilment and tracking out | Partly (only reads verified) |
| BigCommerce | Same | Partly (only reads verified) |
| Amazon, eBay, Walmart, Magento | Connectors exist | Partly (never run on a real account) |
| SAP, NetSuite and other ERPs | Stock, orders, shrink and finance reconciliation | Partly / New (A14) |
| Any JSON API | Import and export configured without code | Works |
| External WMS and 3PL | Through the OMS-WMS contract | New |
| Integration health | Every message in and out, failures, retries, replay | Works |

## 8. Setup and administration

| Feature | What it does | Status |
|---|---|---|
| Product stores | One per channel or brand, with settings | New |
| Locations | Address, hours, timezone, daily limit, groups, carrier accounts | Partly |
| Products | Variants, sizes and colours, pictures, barcodes, categories, prices | Partly |
| Customers | People and companies, addresses, B2B buyers | Partly |
| Users and permissions | Roles, single sign-on, two-factor sign-in | Works |
| Scheduled jobs | See, run, pause, change; full run history | Works |
| Settings, statuses and types | Editable as data | New |
| Phased roll-out | Start with one brand without stores, then add stores | Works (A15) |

## 9. Insight and AI

| Feature | What it does | Status |
|---|---|---|
| Dashboard and reports | Orders, fill rate, late orders, store performance; custom reports | Partly |
| AI suggestions on orders | Grounded in real stock and payment | Works |
| Routing by plain-language instruction | Describe a routing change; it becomes a draft rule set to simulate and approve | New (A4) |
| Forecasting and alerts | Demand per location; unusual orders | Partly |

## Order of delivery

1. Orders, routing (with reallocation and split control), inventory, store fulfilment: areas 1-4.
2. Nexus Gateway for shipping and customer messages: area 5.
3. Shopify and Shopify POS proven on a real store, then the SAP stock file.
4. Returns, the rest of integrations, insight.
