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

## 4b. Store network: how stores work with each other

Stores never talk directly; every request goes through the OMS, which holds one stock record per store.

| Feature | What it fixes | Status |
|---|---|---|
| Stock lookup across stores, with a confidence level per store | Promising stock another store can't find | Partly (confidence is new) |
| Reserve at another store for pickup | Customer travels only when the item is held | New |
| Endless aisle, order routed to the best store | Saves the sale when the item isn't on the shelf | Partly |
| Transfer lifecycle: request, approve, sending stock reduced at once, pick, ship, in transit, receive, settle differences | Phone requests, double-selling, lost transfers | Partly |
| In transit as its own location | Stock always has an owner; nobody reorders what is already on the way | New |
| Receive by scan with automatic settlement under a tolerance | Small differences no longer take days | New |
| Sales credit split between selling and fulfilling store | Managers ignoring requests that earn their store nothing | New |
| Rebalancing suggestions before markdown, within store clusters, only where margin beats transfer cost | Markdowns that a transfer would have avoided | New |
| Transfers received in Shopify POS | Partner A (A13) | Partly |

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

## 10. AI (how each works: [doc 25](25-AI-ARCHITECTURE.md))

| Feature | What it does | Status |
|---|---|---|
| Agent-ready order API (ACP, UCP) | AI shopping assistants (ChatGPT, Gemini) check real stock and dates, order, and get updates | New |
| Availability and promise service | One answer to "can you deliver this, here, by then?" from live stock, capacity and cut-offs | New |
| Agent-safe changes after purchase | A customer's agent can cancel, re-address or return within store policy | New |
| Delivery-date prediction | Real delivery range from history, not fixed rules | New |
| Cost-to-serve sourcing | Full cost per location: shipping, labour, split, rejection and markdown risk | New |
| Split prediction and prevention | Predict and avoid split shipments | New |
| Phantom stock detection | Find items stores show but don't have; route away and ask for a targeted count | New |
| Store reliability score | Each store's chance of rejecting or failing, feeding routing | Partly (failure-aware routing) |
| Return-risk prediction | Likely returns flagged at order time; size advice; repeat-return patterns | New |
| Order risk scoring | Fraud and payment risk, automatic hold | New |
| Dynamic safety stock | Online buffers from predicted demand and record accuracy | New |
| Learning routing | Lookahead routing, tested in simulation first | New (experiment) |
| Exception agent | Finds stuck orders, explains the cause, proposes the fix, applies after approval | New |
| Plain-language routing | Describe a change; it becomes a rule set, simulated and approved | New |
| Operations assistant | Questions about orders, stores and stock answered from live data | Partly |
| Where-is-my-order agent | Status and delivery answers; changes within policy | New |
| Proactive re-promise | Carrier delay detected, new date, customer told, re-route if needed | New |
| Store associate assistant | Guided picking and reject reasons | New |
| Explain, log, undo | Every AI decision traceable and reversible, with approval levels and limits | Partly |
| AI scorecard | AI against plain rules on the client's own orders | Partly (order replay) |

## 11. Client pain points and how the OMS answers them

Collected from industry surveys, retailer reports, vendor and consultant write-ups and practitioner posts
(2016-2026; sources listed below). Vendor figures are marked as such.

| # | Pain point | Evidence | OMS answer |
|---|---|---|---|
| P1 | Store stock is wrong, so store orders fail | Store accuracy around 60-65% (vendor figure); staff blind to accurate stock 30% of the time (Manhattan research); pick failures studied in MSOM 2023 | Phantom stock detection, store reliability score, reject-reason inventory impact, targeted counts, safety buffers |
| P2 | Orders cancelled for stock that wasn't there | 38.6% of retailers cancel at least 1 in 10 orders; only 6% cancel under 1% (Fluent, 2023) | Availability and promise service, reservations, event-by-event channel sync, dynamic safety stock |
| P3 | No single view of stock across locations | Only 36% of retailers say they have full visibility (Linnworks 2025) | Stock per location with history, ERP/POS feeds, integration health with latency per event |
| P4 | Systems overwrite each other's stock | One system overrides another's inventory status (Fluent) | OMS owns availability; every feed is an event with source and time; conflict rules and a sync audit view |
| P5 | Stores aren't warehouses | Store staff also serve shoppers (Fluent); BOPIS concerns: stock availability 57%, store capacity 56%, staff 54% (LaserShip 2021) | Capacity controls, daily limits, store reliability, store task queues with priorities, associate assistant |
| P6 | Pickup experience fails | 36% of UK BOPIS users had issues: no pickup area, waits, items not found (YouGov); 29% abandoned a BOPIS order (Forrester 2016) | Ready-for-pickup only after the item is found and staged, pickup reminders, abandoned-pickup handling that returns stock to sale |
| P7 | Split shipments eat margin | Vendor claims of up to 50% fewer splits with better sourcing | Whole-order-first, split prediction, consolidation, rejection profiles |
| P8 | Peak-season failure | Black Friday orders cancelled days later; next-day promises missed (retailer news reports) | Load-tested routing, capacity throttling, promise service, proactive re-promise, seasonal routing profiles |
| P9 | Returns are huge and slow | US returns about $850B in 2025, online about 19%; processing and carrier costs rising; 9% fraudulent (NRF 2025) | Exact refunds per line, receive-and-restock fast (return to sellable stock), return labels, return-risk and fraud signals |
| P10 | Manual work: spreadsheets, manual routing | Vendor surveys of teams updating stock and routing by hand | Rules on screen, exception agent, bulk actions, scheduled jobs |
| P11 | OMS projects fail on data and integration, not software | Failures from people, process and data readiness (Panorama); self-guided implementation blamed by about a quarter (Brightpearl, vendor) | Data checks before go-live, connection tests per integration, phased roll-out (one brand, then stores), routing simulation on the client's own history, guided setup |
| P12 | Slow time to value | Speed of implementation is the top upgrade motive for 69% (Nextuple/Retail Dive 2024) | Ready-made Shopify, POS and ERP connectors; templates for stores, rules and statuses; one installation per client |
| P13 | Fragmented channels and systems | Many channels, each on a different system (vendor reports) | One order model for every channel; generic connector for anything without one |

Sources: [Netguru: 13 order management challenges](https://www.netguru.com/blog/order-management-challenges) ·
[OneStock: order management challenges](https://www.onestock-retail.com/blog/order-management-challenges/) ·
[Kibo: OMS trends 2026](https://kibocommerce.com/blog/retail-order-management-trends/) ·
[Retail Dive: Nextuple survey](https://retaildive.com/press-release/20240110-survey-reveals-one-in-four-retailers-and-grocers-express-concerns-over-out-1) ·
[Pipe17: ship-from-store inventory accuracy](https://pipe17.com/blog/ship-from-store-inventory-accuracy/) ·
[MSOM 2023: pick failures in ship-from-store](https://ideas.repec.org/a/inm/ormsom/v25y2023i2p508-523.html) ·
[Oracle: five order management challenges](https://www.oracle.com/retail/omnichannel/products/tips-to-resolve-five-retail-order-management-challenges/) ·
[Panorama: lessons from an OMS failure](https://www.panorama-consulting.com/lessons-learned-from-an-oms-implementation-failure) ·
[Brightpearl: ERP and OMS implementation failure](https://www.brightpearl.com/blog/out-now-the-shocking-new-report-into-erp-and-oms-implementation-failure) ·
[RetailWire: is BOPIS getting better](https://retailwire.com/discussion/is-the-bopis-experience-getting-any-better/) ·
[Forrester: BOPIS abandonment](https://www.forrester.com/blogs/16-05-10-your_customers_have_abandonment_issues) ·
[NRF: returns 2025](https://nrf.com/media-center/press-releases/consumers-expected-to-return-nearly-850-billion-in-merchandise-in-2025) ·
[Radial: state of returns 2025](https://www.radial.com/wp-content/uploads/2025/10/State-of-Returns_Report_2025_Final.pdf) ·
[Fluent: inventory visibility and cancellations](https://www.sdcexec.com/warehousing/retail/news/22877096/fluent-commerce-findings-suggest-impaired-inventory-jeopardizes-trust) ·
[Linnworks: state of commerce ops 2025](https://www.retaildive.com/press-release/20250327-linnworks-releases-state-of-commerce-ops-2025-report-1) ·
[Business Wire: Manhattan inventory visibility research](https://www.businesswire.com/news/home/20231010302318/en/Mounting-Inventory-Visibility-Challenges-Add-Pressure-to-Strapped-Retailers/) ·
[Black Friday cancellations (news)](https://www.wcpo.com/news/our-community/holiday/online-retailers-overwhelmed-by-orders-this-year-many-shoppers-not-getting-holiday-buys) ·
[Argos late Black Friday deliveries](https://ciltuk.org.uk/News/Latest-News/ArtMID/6887/ArticleID/7788/Argos-faces-complaints-over-late-Black-Friday-deliveries)

## Order of delivery

1. Orders, routing (with reallocation and split control), inventory, store fulfilment: areas 1-4.
2. Nexus Gateway for shipping and customer messages: area 5.
3. Shopify and Shopify POS proven on a real store, then the SAP stock file.
4. Returns, the rest of integrations, insight.
5. AI in the order of [doc 25](25-AI-ARCHITECTURE.md) section 4, starting with its foundations alongside step 1.
