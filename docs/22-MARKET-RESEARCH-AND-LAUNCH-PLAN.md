# 22 — Market research, Nexus today, and the launch plan

> **Date:** 2026-10-17. **What this is:** what was read, what it says, what Nexus is today in code, what
> follows for features and positioning, and a launch plan. **What this is not:** a claim that the market
> numbers are precise. Where a number is a vendor claim or second-hand, it says so.

## 0. What was actually read (and what was not)

**Read in full:** two NRF 2026 and one Shoptalk 2026 recap (Omni Talk), the Constellation and Sitoo NRF
recaps, a Shopify Summer '26 Editions write-up, Blue Yonder's and HotWax's own positioning posts, a
fabric/Total Retail piece on AI order orchestration, a CMU news story and two abstracts for the
ship-from-store pick-failure paper, arXiv 2506.05357 (inventory record inaccuracy), the SupChain-Bench
abstract (arXiv 2602.07342), the RL-fulfilment abstract (arXiv 2311.16171), Shopify Community threads,
and pricing/listing pages for six competitors.

**Not possible, so not done:** watching video or listening to audio (I can only read text); most podcasts
publish no transcript, only a summary; the full text of the pick-failure paper (the PDF could not be
parsed here, so its numbers come from the CMU story); paywalled analyst reports (Gartner, Forrester, IDC);
Reddit. "Every paper and every podcast" is not achievable; this is a broad, honest sample.

## 1. Nexus today, from the code

Size: about 69K lines of Java and 70K of TypeScript, 78 controllers, 159 entities, 252 commits, 99 screens.
Tests: 1,065 backend, 497 frontend. No TODO/FIXME markers.

**Works, with tests, and (where stated) run live on the local stack**
- Order lifecycle and state machine; row-level tenant isolation; RBAC with MFA; sealed integration secrets.
- Stock ledger enforced by a database trigger; reserve/release/consume; transfers; cycle counts; lots.
- Routing: rules, strategies, stock-aware allocator with a backorder invariant; **store rejection that
  re-routes the whole order and holds the units out of availability** (live on Postgres).
- Shopify: order and product import (**live, totals matched to the cent**), per-location stock publish with
  dry run, drift detection and a **one-SKU real write read back from Shopify**; BigCommerce product import (live).
- BOPIS, ship-from-store, endless aisle, wave planning, pick/pack/ship, 3PL billing, EDI 850/856/810/940.

**Built, but never run against the real thing (unverified)**
- Webhooks from Shopify and BigCommerce; BigCommerce order import (the dev store has no orders).
- Carrier labels with real accounts; the AI assistant (no model key); Kafka against a real broker.
- Full-catalogue stock publish; fulfilment push; refund sync.

**Not built**
- A NetSuite or SAP inventory connector (both are connection tests only); a vendor POS adapter;
  Shopify POS transfer receiving; package-level item tracking; address validation; seasonal routing
  profiles; a batch what-if simulator; failure-aware routing; an onboarding stock import.

**Known bugs and risks that are still open**
1. **Onboarding gap:** Nexus has stock rows for almost none of a merchant's SKUs after connecting a store,
   so a publish skips them (correctly) and a new customer sees nothing happen. It needs a baseline import.
2. Open pick lists are not cancelled when an order is re-routed (picklists are not linked to orders by a column).
3. 139 TypeScript errors remain; the new screens have not been looked at in a browser.
4. The credential vault falls back to a key hard-coded in the source when the environment key is unset.
5. CI is off (billing); staging deploy has never succeeded; no backup/restore drill; no alert rules.
6. Product sync reports products as "processed" but variants as "succeeded".
7. Shopify lists only 60 days of orders without the `read_all_orders` scope (8 older orders were missing).

**Fixed this week (each with a test):** BigCommerce token stored in plain text; new connections enabling
scheduled writes; a manual/scheduled product-sync race; a full publish that would have set every untracked
SKU to zero; `reallocateOrder` failing for allocated orders and leaking reservations; allocation failing for
five warehouse types; a rejection that trusted a caller-supplied order; `processRejection` claiming an
adjustment it did not make.

## 2. What the research says

| # | Finding | Source / confidence |
|---|---|---|
| E1 | US returns are forecast at **$849.9B (15.8%)**, **19.3%** for online sales; **9%** of returns are fraudulent; 85% of retailers use AI against return fraud | NRF via Retail TouchPoints. High |
| E2 | Modelling the **probability that a store pick fails**, and ordering store attempts by it, saved retailers **"as much as 22 percent"**; recommendation: put pick-failure probabilities into the OMS | Das, Ravi, Sridhar, M&SOM 2023 (CMU/Onera), tested on real order data of several top US retailers. Figure from the CMU story; full text not read. High |
| E3 | **65%** of 370,000 inventory records at one 37-store retailer were inaccurate; variation is mostly by **product category (26.4%)**, hardly by store (2.7%) | DeHoratius & Raman, Management Science 2008. High, but one retailer and old |
| E4 | Record errors are **predictable** (a 2026 model catches genuine errors about 9 times in 10, from 1.3M audit observations); an inventory audit gave an **11% store-wide sales lift**, all from items where the system showed more than was on the shelf | Cardiff/ECR press release (2026), arXiv 2506.05357. Medium: press release and abstract |
| E5 | LLM agents show **"substantial gaps in execution reliability"** on supply-chain tool tasks; an early study found LLM forecasts do not beat standard algorithms | SupChain-Bench (ACL Findings 2026); AMCIS 2026 preliminary. Medium |
| E6 | At NRF 2026 one retailer's AI **hallucinated a business review from fake data**; its fix was to audit AI with AI. Sam's Club cut assortment work from weeks to 20 minutes | Omni Talk NRF recap. Anecdotal |
| E7 | Agentic AI is blocked by **data trust**: "inventory and OMS trust" is the foundation; most retailers are not ready; "Frankenstack" sprawl; stores are "growth engines"; **"a 90% solution now beats a 100% solution late"** | NRF 2026 recaps (Sitoo, Constellation, Omni Talk). Qualitative |
| E8 | Shopify Summer '26: **UCP on by default** and auto-syndication to ChatGPT, Copilot, Gemini; POS refunds as shop credit; Scripts retired 30 Jun 2026. POS 10.20 added **native transfer fulfil/receive**. Stocky shut down 31 Aug 2026. Native routing has four simple rules | Third-party write-ups and Shopify changelog. Verify before relying |
| E9 | Merchants report **overselling at the top-priority location** because Shopify's oversell check uses **total** stock, not per-location; and overselling when a fulfilment partner syncs once a day | Shopify Community (staff replies). Real, recurring |
| E10 | Agent-ready commerce needs availability **by region and fulfilment method**, refreshed **in minutes**, with reservation on commit | UCP explainers (secondary). Medium |
| E11 | Blue Yonder argues agents on fragmented OMS data inherit the fragmentation ("thirty seconds stale"; promise and sourcing on different data). Manhattan sells OMS/Store/Contact-centre agents as an add-on. IDC's 2026 "AI-enabled order orchestration" Leaders include SAP, Blue Yonder, Infios, Kibo | Vendor posts and press releases. Self-interested |
| E12 | Pricing anchors: a routing app with a **Dry Run** mode at **$55.99/mo**; HotWax **$11K/yr + $0.75/order** over 1,000; Fluent and Manhattan quote-only; ShipHero about $1.85–2.1K/mo; Loop $155–340/mo | Public listings. Medium |
| E13 | DOM market estimates for 2026 range **$1.85B to $6.9B**; Gartner expects agentic-AI supply-chain software to grow from under $2B (2025) to **$53B (2030)** and cites >40% of agentic projects cancelled by 2027 | Market-report sites (inconsistent); Gartner press release. Low / Medium |

**Not found:** a reliable industry rate for store pick failures or rejections; Shopify merchant counts;
any published result for AI-driven routing beyond vendor claims.

### How competitors market (campaigns, and why)

| Who | What they did | Why it works |
|---|---|---|
| **HotWax** | A podcast of retailer guests; thought-leadership with a metric ("shipment value matters more than split rate") and a gap message ("Shopify's rule has no minimum-value check"); a public price on the Shopify App Store | Borrows credibility from named retailers; teaches a framework only they implement; removes price friction |
| **Fluent** | Gated ebook costing split shipments ("1.8 split rate nearly doubles shipping, $5 to $9"); analyst-leader PR | Quantifies the pain, collects leads, adds analyst trust |
| **Kibo, SAP, Infios, Blue Yonder** | A wave of IDC MarketScape "Leader" announcements in Jul–Aug 2026 | Analyst badge as the buying shortcut for enterprise |
| **Blue Yonder** | Counter-narrative: "embedding AI agents won't fix your OMS — connected intelligence will" | Turns the AI hype against rivals' architecture |
| **Shopify / Google / Stripe** | UCP on by default, native transfers, Sidekick | The platform is absorbing basic OMS functions: a threat and a standard to build on |

## 3. What follows: fix, and build

**Fix first (a customer would hit these in week one)**
1. Onboarding **stock baseline import** from Shopify (and an ERP file), with a reconciliation report.
2. Link pick lists to orders so a re-route cancels them.
3. Run webhooks, fulfilment push and refunds against the dev stores; record dated evidence.
4. Kill the hard-coded vault key fallback outside local; add alert rules; do a backup/restore drill.
5. Look at every new screen in a browser; get the type errors down.

**Build (ordered by evidence, not by feature count)**

| # | Feature | Evidence | Why Nexus |
|---|---|---|---|
| 1 | **Failure-aware routing:** per store and SKU pick-failure rate from our own rejection records, used to order store attempts and to haircut availability | E2 (up to 22%), E3, E4 | The data now exists in Nexus; few small vendors use it |
| 2 | **Order replay simulator:** a prospect's 90 days of orders in; splits, cost and failure impact out, current rules vs new | E2, E12 (routing apps already offer a dry run), E7 (execution gap) | It is the sales demo and the product |
| 3 | **Location-aware availability and oversell guard** with event-driven stock sync and an audit trail | E9, E10, E7 | Directly answers a recurring merchant complaint |
| 4 | **Phantom-inventory flags → directed counts** from rejections and sales | E3, E4 | A cheap, defensible form of the Cardiff result |
| 5 | **Split economics:** a minimum shipment value or margin before a split is allowed | E12 (HotWax's own idea, so implement our own way), Fluent ebook | Table stakes for the segment |
| 6 | **AI that proposes, the engine proves, a human approves:** plain-language policy → rule set → replay → approval → audit | E5, E6, E11 | Honest, and unreliable LLMs never act alone |
| 7 | **Agent-ready availability and promise API** | E8, E10 | UCP makes accuracy a distribution channel |
| 8 | Shopify POS transfers: sync, do not rebuild | E8 | Shopify now has the workflow |

**Do not build now:** a returns portal (integrate), a bigger AI platform, more connector listings, WMS depth
beyond pick/pack/ship, digital twins, RFID, yard/dock, editions and pricing modules.

## 4. What to call Nexus

- **Category (use the analysts' word):** *order orchestration for Shopify retailers with stores.*
- **One sentence:** "Nexus keeps every location's stock honest, routes orders around stores that fail picks,
  and proves every rule change on your own orders before it goes live."
- **Three claims you can defend:** (1) stock truth per location; (2) failure-aware routing; (3) simulate
  before apply, with AI that proposes but never acts alone.
- **Not:** "OMS + WMS + AI + EDI + 3PL for everyone", "best in the industry", or "joint-first with Manhattan".

**Ideal first customers:** Shopify (Plus) retailers with **5–100 stores**, roughly **$20M–$200M** revenue,
on Shopify POS, with an ERP (NetSuite or SAP). Triggers: overselling at peak, store pick failures, rising
split cost, outgrowing a routing app's limits (10 locations on lower plans), leaving a legacy POS or OMS.
Buyer: VP/Director of e-commerce or operations. **Not yet:** 300-store enterprises running RFIs (a 2027
timeline, a competitive process, an ERP-heavy build); single-warehouse DTC brands (Shopify is enough).

## 5. Pricing (a hypothesis to test with five prospects, not a decision)

| Tier | Price | Limit |
|---|---|---|
| Design partner | free for 90 days, then 50% off year one | data, weekly feedback, a case study |
| Starter | about $499/mo | up to 5 locations |
| Growth | about $1,499/mo | up to 50 locations |
| Scale | from about $3,500/mo | more locations, ERP connector, SLA |

Anchors: HotWax about $1,000/mo plus $0.75/order, a routing app at $56/mo, enterprise suites in the
hundreds of thousands. Publish prices; HotWax and the app stores do, and it removes the first objection.

## 6. Launch plan: private pilot first, not general availability

**Pilot-ready gates (all must be true):** baseline stock import works; one real carrier; one ERP feed;
webhooks and fulfilment verified on dev stores; alerts, backups and a runbook; every screen checked in a
browser; a signed data-handling agreement; a rollback plan.

| When | Do |
|---|---|
| Weeks 1–2 | Settle the HotWax/prospect legal question; rotate tokens; build the replay simulator and failure-aware routing; stock baseline import; look at every screen |
| Weeks 3–4 | NetSuite connector (needs sandbox credentials); one real carrier; webhooks live behind a tunnel; location-aware availability; record a 3-minute demo of the full flow |
| Weeks 5–8 | Show the demo to 10 retailers; offer the free 90-day **order replay** report to each; start 2–3 design-partner pilots; publish three pieces of content (below) |
| Weeks 9–12 | Convert pilots to paid; write the first case study; decide on a Shopify App Store listing and hosting |

**Content that fits the evidence (and costs nothing):** a calculator and post on "what a store pick failure
costs you" (E2, E4); a post on why Shopify oversells at the top-priority location (E9); a one-page "how to
test AI in your OMS without trusting it" (E5, E6). Offer them as the free order replay.

**Channels:** founder-led outreach to 100 named retailers; Shopify Plus agencies and implementers; Shopify
and retail communities; an interview series with retailer ops leads; events (Shoptalk, NRF 2027) for
meetings, not booths.

## 7. Risks

Legal and confidentiality exposure from third-party call recordings (see the cleanup in `772923f`);
Shopify absorbing basic OMS functions; AI-hype backlash and unreliable agents (E5, E6); a solo team's
capacity; security questionnaires (SOC 2) from larger buyers; support load; hosting for a pilot.

## 8. Sources

- NRF returns forecast: <https://www.retailtouchpoints.com/news/nrf-forecasts-nearly-850-billion-in-returns-in-2025-slight-decrease-from-2024>
- CMU, ship-from-store pick failure: <https://www.cmu.edu/tepper/news/stories/2023/january/e-commerce-store-pickup-research.html> · MIT DSL abstract: <https://dsl.mit.edu/?p=378> · DOI 10.1287/msom.2022.1164
- Inventory record inaccuracy (Management Science 2008): <https://ideas.repec.org/a/inm/ormnsc/v54y2008i4p627-641.html> · Chicago Booth: <https://www.chicagobooth.edu/review/first-measure-then-manage>
- Cardiff/ECR 2026: <https://www.cardiff.ac.uk/news/view/3073980-cardiff-university-research-shows-retailers-can-dramatically-improve-stock-accuracy-and-sales-by-counting-smarter> · arXiv 2506.05357: <https://arxiv.org/abs/2506.05357>
- SupChain-Bench: <https://arxiv.org/abs/2602.07342v1> · RL fulfilment: <https://arxiv.org/abs/2311.16171>
- NRF 2026 recaps: <https://omnitalk.blog/2026/01/23/the-death-of-optimization-first-retail-four-lessons-from-nrf-2026/> · <https://sitoo.com/resources/top-trends-and-takeaways-from-nrf-2026> · <https://www.constellationr.com/insights/news/nrf-2026-agentic-ai-commerce-frontline-workers-customer-experiences>
- Shoptalk 2026: <https://omnitalk.blog/2026/03/27/shoptalk-spring-2026-the-biggest-takeaways-on-ai-agentic-commerce-and-the-future-of-retail/>
- Shopify Summer '26: <https://shopappy.com/ecommerce/shopify-editions-summer-26-what-shopify-actually-announced> · POS transfers: <https://changelog.shopify.com/posts/fulfilling-transfers-with-shopify-pos> · routing limits: <https://ecommercefastlane.com/shopify-order-management-systems/>
- Shopify Community on overselling: <https://community.shopify.com/t/products-overselling-multi-location-orders/227730>
- UCP explainers: <https://racklify.com/encyclopedia/what-is-the-universal-commerce-protocol-ucp-a-practical-overview/>
- Competitor positioning: <https://www.hotwax.co/blog/why-shipment-value-matters-more-than-your-split-rate> · <https://fluentcommerce.com/wp-content/uploads/10-ways-to-optimize-split-shipments.pdf> · <https://zh-hant.blueyonder.com/blog/2026/embedding-ai-agents-wont-fix-your-oms-connected-intelligence-will> · <https://www.mytotalretail.com/article/artificial-intelligence-and-order-orchestration-together-an-unbeatable-team/>
- IDC MarketScape 2026 coverage: <https://news.sap.com/2026/08/sap-order-management-services-a-leader-idc-marketscape-worldwide-ai-enabled-order-orchestration-fulfillment-applications-retail-b2c-2026-vendor-assessment/>
- Pricing: <https://apps.shopify.com/location-priority> · <https://www.capterra.com/p/166930/ShipHero/pricing> · <https://apps.shopify.com/loop-returns>
- Market size and Gartner: <https://marketintelo.com/report/distributed-order-management-systems-market> · <https://www.gartner.com/en/newsroom/press-releases/2026-04-07-gartner-forecasts-supply-chain-management-software-with-agentic-ai-will-grow-to-53-billion-in-spend-by-2030>
- Go-to-market: <https://uncorkcapital.com/blog/how-to-find-and-select-your-first-10-customers-c681d78a16b1> · <https://hub.causo.ai/guides/h1-2026-founder-led-sales-report>
