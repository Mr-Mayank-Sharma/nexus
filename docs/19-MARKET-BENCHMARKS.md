# Market Benchmarks — Externally-Verified OMS/WMS Competitive Data

> **What this is:** a **real, externally-sourced** market benchmark for the OMS/WMS competitor set. Every competitor figure below is tied to a **public source URL**, a **reporting period**, and a **unit**. Where sources disagree, both numbers are printed and the conflict is flagged rather than averaged away.
>
> **What this is not:** a feature scoring sheet. [`07-SCM-COMPARISON.md`](./07-SCM-COMPARISON.md) scores capability `/10` by self-assessment; this document supplies the **market reality** that those scores sit on top of. The two answer different questions — 07 asks *"how much can it do?"*, 19 asks *"what is it actually worth in the market, and who has bought it?"*
>
> **Freshness:** collected **2026-10-14** via Firecrawl web search + page extraction. Re-run before external reuse — see [Rerun Inputs](#rerun-inputs).
>
> **Companion docs:** [`07-SCM-COMPARISON.md`](./07-SCM-COMPARISON.md) (self-scored feature comparison) · [`09-COST-TCO.md`](./09-COST-TCO.md) (Nexus cost model) · [`18-NEXUS-EXPOSED.md`](./18-NEXUS-EXPOSED.md) (internal audit) · [`01-CURRENT-STATE.md`](./01-CURRENT-STATE.md) (honest baseline)

---

## 0. The one-line finding

On every dimension that a **third party can verify without reading our source code** — revenue, customer count, market share, analyst position — Nexus is a **pre-revenue, pre-customer project** and the incumbents are **billion-dollar, thousand-customer, decade-long Gartner Leaders**. Where Nexus genuinely, verifiably wins is **cost and time-to-value** ($0 license and a 1-hour boot vs. **$200K–$1M** implementations and **2–18 month** timelines).

`07`'s headline claim — *"Nexus **8.4/10, joint-first, tied with Manhattan**"* — is a **self-score on self-defined criteria**. No externally-observable dimension supports parity with Manhattan Associates. Both statements can be true at once only if you accept that `/10` scores measure **feature presence**, not **market position**, and 07 does not consistently say so.

---

## 1. Methodology

| Rule | How it was applied |
|---|---|
| Every metric carries **period + unit** | e.g. "FY2025 revenue, USD 1,081.4 million" — never bare "$1B" |
| Prefer **primary/filing** sources | SEC 10-K and company IR press releases outrank vendor marketing pages |
| **Estimates are labeled** | Any figure marked *est.* is third-party estimation, not a disclosed number |
| **Conflicts are printed, not resolved** | See [§7 Conflicting Data](#7-conflicting-data--caveats) |
| **Nexus figures are a different evidence class** | Competitor numbers come from filings/marketplace pages; Nexus numbers come from **our own repo docs**. They are not equivalent evidence and are marked 🔶 throughout |
| Nothing is scored `/10` here | Scoring is inherently judgment; this doc reports **observable quantities** |

### Evidence classes used

- **A — Primary/filing:** SEC 10-K, company IR press release, official vendor pricing page. Highest confidence.
- **B — Third-party analyst/reseller:** Gartner/peer-review aggregators, implementation cost guides, published pricing aggregators. Medium confidence; often ranges.
- **C — Unverified claim:** figures we could not trace to a primary source in this run. Explicitly marked and excluded from conclusions.

---

## 2. Market sizing

| Market | 2024 | 2025 | 2026 | 2030 | 2031/2034 | CAGR | Source |
|---|---|---|---|---|---|---|---|
| **Warehouse Management Systems (WMS)** | — | **USD 4.57 B** | — | **USD 10.04 B** | — | **17.1%** (2025–2030) | MarketsandMarkets *(class B)* |
| **Supply Chain Management (SCM), overall** | **USD 25.67 B** | **USD 28.37 B** | — | **USD 48.59 B** | — | **11.4%** (2025–2030) | Grand View Research *(class B)* |
| **Inventory management software** | — | **USD 2.51 B** | **USD 2.75 B** | — | **USD 5.52 B** (2034) | **9.13%** (2026–2034) | Fortune Business Insights *(class B)* |
| **Inventory management (broader)** | — | — | **USD 2.95 B** | — | **USD 4.14 B** (2031) | — | Mordor Intelligence *(class B)* |

**Regional note:** Asia-Pacific is projected to be the fastest-growing WMS region at **18.4% CAGR** (2025–2030), driven by e-commerce and supply-chain modernization (MarketsandMarkets).

> ⚠️ **Scope conflict:** a widely-circulated figure puts the **SCM software market at ~USD 63.77 B** — roughly **2×** Grand View's USD 28.37 B (2025). These are **different market definitions** (one is broader, likely including hardware/services/adjacent categories), not a correction of each other. **Do not mix them in one chart.** See [§7](#7-conflicting-data--caveats).

**Why this matters for Nexus:** the WMS segment we compete in is a **USD 4.57 B (2025) → USD 10.04 B (2030)** market growing at **17.1%/yr** — one of the fastest-growing sub-segments of supply chain software. At **0 customers / USD 0 revenue**, our current share is **0.0%** of that market.

---

## 3. Vendor scale — revenue, customers, ownership

| Vendor | Revenue (period, USD) | Cloud/SaaS detail | Customers | Ownership / listing | Class |
|---|---|---|---|---|---|
| **Manhattan Associates** | **$1,081.4 M** — FY2025 total revenue | Cloud subscriptions **$408.1 M (38%** of total, up from $337.2 M prior year); license 1%, maintenance 12%, services 47%, hardware 2% | **1,200+** | Public — **NASDAQ: MANH** | **A** |
| ↳ quarterly cadence | Q4 2025 **$270.4 M** (vs $255.8 M Q4 2024) · Q1 2026 **$282.2 M** · Q2 2026 **$297.8 M** (vs $272.4 M) | — | — | 2026 guidance **$1,133–1,153 M** (5–7% growth) | **A** |
| **Blue Yonder** | **$1.42 B** — FY2025 | SaaS revenue **+10.4% YoY** in FY2025 | **114 new customers** in FY2025 *(total not disclosed)* | **Panasonic** — $7.1 B acquisition completed **Sept 2021**; acquired One Network Enterprises **$839 M** (2024), Pledge Earth Technologies (2025) | **A/B** |
| **SAP** (SCM-related) | **~$4.3 B** *est.* — SCM software + services, 2025 | — | **20,000+** supply-chain customers | Public (NYSE/DB) | **B — est.** |
| **Oracle** (SCM-related) | **~$2.9 B** *est.* — SCM-related revenue, 2025 | Growth driven by cloud migration + NetSuite mid-market | Not disclosed | Public (NYSE) | **B — est.** |
| **HotWax Commerce** | Not disclosed (private) | — | Not disclosed | Private | C |
| **Brightpearl** | Not disclosed | — | Not disclosed | Owned by **Sage** (rebranded *Brightpearl by Sage*) | C |
| **NetSuite** | Module of Oracle's SCM revenue | — | — | **Oracle** subsidiary | B |
| **Cin7 · Extensiv · Softeon · Körber/Infios · Descartes** | Not disclosed / not found in this run | — | — | Körber branded **Infios**; Softeon owned by **IFS** | C |
| **Nexus (us)** 🔶 | **$0** revenue | Not applicable | **0 customers** | Private, self-hosted, not listed | 🔶 *self-reported* |

**Sources:** Manhattan FY2025 Form 10-K (SEC EDGAR) · Manhattan Q4 2025 / Q1 2026 IR press releases · manh.com *About Us* (1,200+ customers) · Blue Yonder FY2025 results (Yahoo Finance) · Dataintelo SCM competitive-outlook report (SAP/Oracle *estimates*) · FreightWaves 2026 WMS vendor table.

### Scale ratio, stated plainly

| | Manhattan (FY2025) | Nexus | Ratio |
|---|---|---|---|
| Revenue | $1,081.4 M | $0 | **undefined / ∞** |
| Cloud subscription revenue alone | $408.1 M | $0 | **∞** |
| Customers | 1,200+ | 0 | **∞** |
| Annual revenue added vs. prior year | ~$70 M+ (guidance midpoint vs FY2025) | $0 | — |
| License cost to a buyer | >$500 K/yr (frequently) | **$0** | **Nexus wins outright** |
| Time to first production run | 2–18 months | **~1 hour** | **Nexus wins outright** |

---

## 4. Gartner positioning (2026 Magic Quadrant for WMS)

Six vendors publicly announced **Leader** placement in the **2026 Gartner Magic Quadrant for Warehouse Management Systems**. Streaks as announced:

| Vendor | Product (as Gartner renders it) | 2026 position | Consecutive years as Leader | Class |
|---|---|---|---|---|
| **Manhattan Associates** | Manhattan Active Warehouse Management | **Leader** | **18th time** | B |
| **Blue Yonder** | Blue Yonder Warehouse Management | **Leader** | **18th consecutive** | B |
| **SAP** | SAP Extended Warehouse Management | **Leader** | **12 consecutive years** | B |
| **Oracle** | Oracle Fusion Cloud Warehouse Management | **Leader** | **11th consecutive** | B |
| **Infios (Körber)** | rendered by Gartner as *Infios (Körber)* | **Leader** | **7th consecutive** (2025 figure) | B |
| **6th Leader** | *not confirmed in this run* | Leader (announced) | — | C |

**Related Gartner facts gathered:**

- The 2026 MQ covers **21 vendors** (FreightWaves roundup).
- **Infios (Körber)** carries the **highest review volume** of any WMS on Gartner Peer Insights: **285 reviews, 4.5 rating**.
- **Softeon (IFS)**: `07` claims **Visionary** in the 2026 MQ — *we did not independently verify this in this run* (class C).
- Gartner's **distributed order management (DOM)** research is **in transition**: the market is moving to the **"Distributed Order Management Systems"** category name, with a **Market Guide for Distributed Order Management (doc 6659334)** published. The older *Magic Quadrant for OMS & WMS* that `07` cites in its §7 is being superseded — **`07` should be re-cited against the current Gartner product.**
- **Nexus is not evaluated in any Gartner WMS, OMS, or DOM research**, and has **no Gartner Peer Insights presence**.

> ⚠️ **Streak conflict:** earlier-gathered 2025-MQ figures recorded *"Blue Yonder 14th consecutive, Oracle 10th consecutive."* Those are **arithmetically incompatible** with the 2026 figures (BY 18th, Oracle 11th would imply 2025 = 17th and 10th). The 14/10 pair likely refers to a **different Gartner research product** or a mis-scraped page. **The 2026 numbers above are the ones backed by a named third-party table**; treat 14/10 as unverified. See [§7](#7-conflicting-data--caveats).

---

## 5. Real pricing (published or documented estimates)

Enterprise WMS vendors publish **no list pricing** — FreightWaves' 2026 comparison table marks **"Published Pricing: None"** for Manhattan, Blue Yonder, SAP, and Oracle. Mid-market vendors do publish.

| Vendor | Published / documented price | Implementation | Notes | Class |
|---|---|---|---|---|
| **Manhattan Active WM** | **No list price.** Deals **frequently exceed $500 K/year**; WMS license **>$200 K/yr** cited | **$200 K – $1 M+** (complexity-driven); $50 K–$1 M range across sources | Enterprise tier | A/B |
| **Blue Yonder** | **No list price** | Not disclosed | — | C |
| **SAP EWM** | **No list price** | **$50 K – $500 K**; typical basic implementation **$100 K – $200 K** | Strongest if you already run SAP | B |
| **Oracle** | **No list price** | Not disclosed | — | C |
| **Oracle NetSuite** | Base **$999/mo** + **$129–199 per user/mo**; **WMS module ≈ $1,000–2,000/mo** | Mid-market: **$50 K–$100 K/yr** license + **$70 K–$150 K** one-time | Add-on modules priced individually | B |
| **Cin7 Core** | **$349/mo** (Standard) · **$599/mo** (Pro) · **$999–1,199/mo** (Advanced/Omni) | Not disclosed | MRP/Advanced Mfg requires Pro; B2B portal extra | **A — official pricing page** |
| **Extensiv (3PL WH Manager)** | **≈$599+/mo** base; Integration Manager **$39 / $99 / $199/mo** tiers | **$5 K – $20 K+** (certified partner required) | EDI + integrations scale total cost | B |
| **HotWax Commerce OMS** | **OMS Starter: $1,000/mo or $11,000/yr** (8% saving) · **$0.75/order** after first 1,000/mo · **$3,000 optional setup** | Optional setup fee above | **Shopify App Store listing** | **A — official listing** |
| **Descartes** | **$1,000 – $2,050+/mo** (WMS only) | On top of license | — | B |
| **Brightpearl** | **Quote only** — no public price | Contact sales | Tied to business size | A (absence noted) |
| **Nexus** 🔶 | **$0 license** | **~1 hour** self-host boot | All-in self-host ≈ **$76–110/mo** | 🔶 *self-reported* |

**Sources:** cin7.com/pricing · Capterra & G2 Cin7 listings · packemwms.com 3PL pricing guide · Capterra 3PL Warehouse Manager · Extensiv help docs (Integration Manager) · Shopify App Store *HotWax Order Management* · brokenrubik.com & inscio.com NetSuite pricing guides · erpresearch.com Manhattan Active WMS · grasshopperlabs.io & LeverX implementation-cost analyses · FreightWaves 2026 vendor table (published-pricing column).

### ⚠️ Direct correction to `07`

`07` **stated** **HotWax is "Open source / free"** (§1 row 28) and scored its strengths as *“**free**, fast to deploy (9/10)”* (§4.2 line 145). **✅ Corrected in `07` on 2026-10-14.**

**The verified price is $11,000/year or $1,000/month, plus $0.75 per order beyond 1,000 orders/month, plus a $3,000 setup fee.**

What is free is the **Shopify App Store install** and the open-source **Moqui** base — **not the OMS subscription.** As written, 07 overstates HotWax's price advantage and, by making it "free," inflates HotWax's cost/time score relative to Nexus. This is the one competitor where 07's price claim is **factually wrong**, not merely unsourced.

---

## 6. Implementation cost & timeline — market benchmarks

| Deployment model | Monthly fees | 5-year TCO (mid-market) | Implementation time | Class |
|---|---|---|---|---|
| **Cloud / SaaS WMS** | **$1,500 – $50,000/mo** | **$300 K – $1.2 M** | **2 – 4 months** | B |
| **On-premise WMS** | $0 + annual maintenance | **$400 K – $2.5 M** | **6 – 18 months** | B |
| **Hybrid WMS** | **$2,000 – $20,000/mo** | **$500 K – $2 M** | **4 – 12 months** | B |

**By facility size (implementation & configuration, one-time):**

| Facility size | Implementation cost |
|---|---|
| Small (<50 K sq ft) | **$10 K – $30 K** |
| Mid-market (50–250 K sq ft) | **$50 K – $200 K** |
| Enterprise (250 K+ sq ft, 75–500+ users) | **$200 K – $1 M** |

**Corroborating ranges from independent 2026 cost guides:**

- **$25 K–$75 K** for a single small site (**3–6 months**); **$100 K–$350 K** for a mid-market company (ASC Software).
- Mid-market SaaS WMS: **$10 K–$15 K/mo = $120 K–$180 K/yr** (OrderPilot).
- Implementation **$10 K – $300 K+**, scaling with size and integrations (Rorix).
- Single-warehouse mid-market 3PL cutover: **8–12 weeks** end-to-end; multi-warehouse: **3–6 months** (SC CodeWorks).
- Enterprise WMS platforms generally: **$200 K+, 12 months to deploy** (Grasshopper Labs).

**Sources:** cpcongroup.com WMS Cost 2026 guide · ascsoftware.com WMS Cost · orderpilots.com WMS Implementation Cost Guide · sccodeworks.com WMS Implementation Guide 2026 · rorixtech.com WMS Cost Breakdown 2026 · grasshopperlabs.io.

> 🔶 **Nexus, for contrast:** `08-SELF-HOST-BOOT.md` documents production in **~1 hour**; `09-COST-TCO.md` models **$0 license + ≈$76–110/mo all-in**. Over 60 months that is roughly **$4.6 K–$6.6 K** total, against a market mid-market TCO band of **$300 K–$1.2 M** — a **~45×–180× difference** on the cost dimension. This is Nexus's single strongest externally-plausible claim.

---

## 7. Conflicting Data & Caveats

| # | Topic | Conflict | Handling |
|---|---|---|---|
| 1 | **SCM market size** | **USD 63.77 B** vs **USD 28.37 B (2025)** | Different market definitions/scope. Use **one** consistently; never blend. Grand View figure used in §2 as the tighter, documented scope. |
| 2 | **Blue Yonder market share** | **22.1%** (Futurum) vs **~12%** (LinkedIn-cited, alongside SAP 28% / Oracle 22%) | Two different research methodologies (category-specific vs broad SCM). **Neither is primary-disclosed** — Blue Yonder does not publish share. Treat as **class C**; excluded from conclusions. |
| 3 | **Gartner WMS Leader streaks** | 2025 capture: *BY 14th, Oracle 10th* vs 2026: *BY 18th, Oracle 11th* | Arithmetically incompatible. **2026 figures used** (named third-party table). 14/10 likely a different Gartner product or scrape error — **re-verify before citing externally.** |
| 4 | **Gartner OMS/WMS research product** | `07` cites *"Gartner Magic Quadrant for OMS & WMS"* | Gartner is **transitioning to "Distributed Order Management Systems"** naming with a **Market Guide for DOM (doc 6659334)**. `07`'s §7 citation should be refreshed to the current research product. |
| 5 | **Blue Yonder share of enterprise WMS** | Listed as a Leader **18 consecutive years**, yet also suffered a **Nov 2024 ransomware incident** (service restored Dec 2024) | Analyst position and operational-resilience are **different axes**; both are stated in §3/§4 rather than collapsed into one judgment. |
| 6 | **Nexus evidence class** 🔶 | Competitor figures = SEC filings / public marketplace pages. Nexus figures = **our own repo docs** | Not equivalent evidence. Marked 🔶. A self-reported $0 price and 1-hour boot are plausible and internally documented, but **unverified by any third party.** |
| 7 | **HotWax pricing** | `07`: *"free"* vs Shopify App Store: **$11,000/yr** | **Resolved in favor of the primary source** — see §5 correction. |
| 8 | **Nexus test count** 🔶 | `07` recorded **830 tests**; corrected to **961 tests / 0 failures** | **Resolved** — `07` updated to 961 on 2026-10-14. Not a market metric. |

---

## 8. Data gaps — what could **not** be verified

Recorded so a future run knows where the holes are, rather than leaving silence that reads as absence.

1. **Customer counts** for: Blue Yonder (total, only "114 new in FY2025" found), SAP (only "20,000+ supply-chain customers"), NetSuite, Cin7, Extensiv, Brightpearl, Softeon, Körber/Infios, Descartes, HotWax.
2. **Revenue** for all private/non-disclosed vendors: HotWax, Cin7, Extensiv, Brightpearl, Softeon, Körber/Infios, Descartes.
3. **The 6th 2026 Gartner WMS Leader** — six were announced; only five are identified in gathered sources.
4. **Softeon (IFS) 2026 MQ placement** — `07` says *Visionary*, unverified here.
5. **Market-share percentages** — only SAP/Oracle/Blue Yonder estimates exist, all class C and mutually inconsistent (see §7.2).
6. **Published pricing** for the enterprise tier — confirmed **absent** (all four majors publish none), but this means no like-for-like price comparison is possible for that tier; only implementation-cost ranges exist.
7. **DOM/OMS-specific market sizing** — no clean OMS-only market size was found; closest proxies are inventory-management software (USD 2.51 B, 2025) and the broader SCM market. Nexus's DOM feature therefore has **no separately-sized market** to be benchmarked against.

---

## 9. Nexus's verifiable baseline 🔶

Stated honestly, with its evidence class attached:

| Dimension | Nexus | Evidence |
|---|---|---|
| Revenue | **$0** | 🔶 no commercial operation exists |
| Customers in production | **0** | 🔶 self-reported |
| Market share | **0.0%** | 🔶 derived (0 ÷ $4.57 B WMS market) |
| Gartner evaluation | **None** (WMS, OMS, or DOM) | ✅ externally verifiable absence |
| Gartner Peer Insights presence | **None** | ✅ externally verifiable absence |
| Public analyst/reseller review presence | **None found** (G2/Capterra/Gartner) | ✅ externally verifiable absence |
| License price | **$0** | 🔶 self-hosted, source-available |
| Time to production | **~1 hour** (`08-SELF-HOST-BOOT.md`) | 🔶 self-reported, internally documented |
| All-in run cost | **≈$76–110/mo** (`09-COST-TCO.md`) | 🔶 self-reported model |
| Backend test suite | **961 run, 0 failures** | 🔶 self-reported (`07` now records 961) |
| Data model | **179 tables / 167 entities** (`10-DATA-MODEL-REFERENCE.md`) | 🔶 self-reported |

**The honest summary:** Nexus is **technically real, commercially unproven.** It has meaningful built surface (a 179-table schema and a green test suite are not nothing) and it has **zero** of the external market-validation signals that enterprise buyers use to shortlist vendors.

---

## 10. Where `07`'s self-scores diverge from external evidence

`07` (§3, §5) reports a weighted table ranking **Nexus 8.4 🥇 and Manhattan 8.4 🥇 joint-first**, with the kid-translation line: *“joint-first with Manhattan (8.4) ahead of Blue Yonder, SAP, Oracle and the whole mid-tier.”*

Below: what `07` claims, against what a third party can observe.

| `07` claim | External evidence | Verdict |
|---|---|---|
| Nexus **8.4 = Manhattan 8.4**, joint-first | Manhattan FY2025 revenue **$1,081.4 M**, **1,200+ customers**, **18× consecutive** Gartner Leader; Nexus **$0 / 0 customers / not evaluated** | **Not externally supportable.** Parity exists only on *self-defined feature criteria*. Present it as **"feature-presence parity"**, never as market parity. |
| Nexus *“ahead of Blue Yonder, SAP, Oracle”* on weighted basis | Blue Yonder FY25 **$1.42 B**; SAP *est.* **$4.3 B** SCM; Oracle *est.* **$2.9 B** SCM; all Gartner Leaders 11–18 consecutive years | **Same** — defensible only as a *capability-scoring* statement, not a market statement. |
| HotWax is **“Open source / free”**, *“free, fast to deploy (9/10)”* | **$11,000/yr or $1,000/mo + $0.75/order + $3,000 setup** (Shopify App Store) | ❌ **Was factually wrong** (§5). **✅ Corrected in `07` on 2026-10-14.** |
| Manhattan scores **7.0 on cost** / enterprise tier *“cost millions, takes a year”* | Impl **$200 K–$1 M+**, deals **>$500 K/yr**, timeline **2–18 months** | ✅ **Directionally correct** — external data *supports* 07 here. |
| Nexus cost/time-to-value **9/10**, *“beats every enterprise vendor by a mile”* | Market TCO **$300 K–$1.2 M** mid-market vs Nexus **~$4.6–6.6 K/60mo**; timelines 2–18 months vs ~1 hour | ✅ **Strongly supported** — this is 07's best-evidenced claim. |
| §7 **Sources (public, August 2026)** | All entries are **vendor marketing pages**: *"official product pages"*, *"platform materials"*, *"case studies"*. **No SEC filings, no pricing sources, no analyst market data.** | ⚠️ **Evidence quality gap.** 07's own freshness header concedes: *"re-run the scoring before reusing these numbers externally."* **This document is that re-run** — but for market data, not feature scores. |

### The reconciled position

Both documents can stand, as long as they are read as measuring different things:

- **`07` measures capability presence** — *what has been built.* Its 8.4 is a code-audit of feature completeness. It is internally honest (it explicitly flags DOM as "feature-presence, not demo-readiness"), but its **headline framing ("joint-first") invites a market reading it cannot support.**
- **`19` measures market reality** — *who buys it, what they pay, who analyst-firms rank.* On these axes Nexus is at the start line.

**Recommended reframing for `07`:** keep the feature scores, but replace *"joint-first with Manhattan"* with *"feature-presence parity with Manhattan on our self-defined criteria — with zero of its market validation"*, and fix the HotWax pricing line. A single blended `/10` that merges *cost* (where we win by 100×) with *track record* (where we lose by infinity) will always produce a number nobody can verify.

---

## 11. Sources

All URLs retrieved **2026-10-14** via Firecrawl.

### Tier 1 — primary / filings (class A)

| Source | URL | Extracted |
|---|---|---|
| Manhattan Associates FY2025 Form 10-K | `sec.gov/Archives/edgar/data/1056696/000119312526037138/manh-20251231.htm` | FY2025 revenue **$1,081.4 M**; cloud subs **$408.1 M (38%)** vs **$337.2 M** prior; mix cloud 38% / license 1% / maintenance 12% / services 47% / hardware 2% |
| Manhattan Q4 2025 results (IR) | `ir.manh.com/static-files/93d68ee6-30dc-46e4-a00d-2e363bbde907` | Q4 2025 revenue **$270.4 M**; 2026 guidance **$1,133–1,153 M (5–7%)** |
| Manhattan Q4 2025 press release | `manh.com/about-us/newsroom/press-releases/manhattan-associates-reports-fourth-quarter-results` | Q4 2025 **$270.4 M** vs Q4 2024 **$255.8 M** |
| Manhattan Q1 2026 results | `ir.manh.com/static-files/0f0eef03-5424-40fa-8ce3-ded182869fb4` | Q1 2026 revenue **$282.2 M** |
| Manhattan — About Us | `manh.com` (About Us) | **1,200+ customers** |
| Cin7 pricing (official) | `cin7.com/pricing/` | **$349 / $599 / $1,199 per month** tiers |
| HotWax Order Management (Shopify App Store) | `apps.shopify.com/hotwax-order-management` | OMS Starter **$1,000/mo or $11,000/yr** (save 8%) · **$0.75/order** after 1,000/mo · **$3,000** optional setup |
| Extensiv Integration Manager FAQ | `help.extensiv.com/365762-faq/1619860-...` | Integration Manager **$39 / $99 / $99 / $199 per month** tiers |
| Brightpearl (official) | `brightpearl.com/warehouse-inventory-management-software` | **Quote only** — no public price |

### Tier 2 — third-party analysis & market data (class B)

| Source | URL | Extracted |
|---|---|---|
| MarketsandMarkets — WMS Market | `marketsandmarkets.com/Market-Reports/warehouse-management-system-market-41614951.html` | **$4.57 B (2025) → $10.04 B (2030)**, **17.1% CAGR**; APAC fastest at **18.4%** |
| Grand View Research — SCM Market | `grandviewresearch.com/industry-analysis/supply-chain-management-market-report` | **$25.67 B (2024)** / **$28.37 B (2025)** → **$48.59 B (2030)**, **11.4% CAGR** |
| Fortune Business Insights — Inventory Mgmt Software | `fortunebusinessinsights.com/inventory-management-software-market-108589` | **$2.51 B (2025)** → **$2.75 B (2026)** → **$5.52 B (2034)**, **9.13% CAGR** |
| Mordor Intelligence — Inventory Management | `mordorintelligence.com/industry-reports/inventory-management-market` | **$2.95 B (2026) → $4.14 B (2031)** |
| Dataintelo — SCM Software Market | `dataintelo.com/report/supply-chain-management-software-market` | *est.* SAP **~$4.3 B** + **20,000+** SCM customers · Oracle **~$2.9 B** · Blue Yonder **~$1.8 B**; SAP 28% / Oracle 22% / BY 12% share *(class C — see §7.2)* |
| FreightWaves — Best WMS Companies 2026 | `freightwaves.com/checkpoint/best-warehouse-management-companies/` | **2026 Gartner Leaders + streaks**: Manhattan 18th, Blue Yonder 18th, SAP 12th, Oracle 11th · **published pricing: none** for all four · Blue Yonder Panasonic **$7.1 B** acq (Sept 2021), One Network **$839 M** (2024), **Nov 2024 ransomware** · Infios (Körber) **285 Peer Insights reviews, 4.5** · 6 announced Leaders |
| Gartner Peer Insights — WMS market | `gartner.com/reviews/market/warehouse-management-systems` | Current WMS reviews market page |
| Yahoo Finance — Blue Yonder FY2025 | *(via search)* | FY25 revenue **$1.42 B**, SaaS **+10.4% YoY**, **114 new customers** |
| ERP Research — Manhattan Active WMS | `erpresearch.com/erp-add-ons/wms/manhattan-active-wms` | WMS deals **frequently exceed $500,000/year** |
| Grasshopper Labs — WMS alternative | `grasshopperlabs.io/blog/manhattan-associates-descartes-wms-alternative/` | Manhattan impl **$200 K–$1 M+**; enterprise WMS **$200 K+, 12 months**; Descartes **$1,000–2,050+/mo**; mid-market impl **$50 K–$200 K, 6–12 months** |
| LeverX — SAP EWM vs Manhattan | `leverx.com/blog/sap-ewm-vs-manhattan-associates-wms` | SAP EWM impl **$50 K–$500 K**; basic **$100 K–$200 K** |
| CPCON Group — WMS Cost 2026 | `cpcongroup.com/insights/article/warehouse-management-system-cost-guide/` | SaaS **$1,500–50,000/mo**, TCO **$300 K–$1.2 M**, **2–4 mo** · On-prem TCO **$400 K–$2.5 M**, **6–18 mo** · Hybrid **$2,000–20,000/mo**, TCO **$500 K–$2 M**, **4–12 mo** · impl by size **$10–30 K / $50–200 K / $200 K–1 M** |
| ASC Software — WMS Cost | `ascsoftware.com/blog/wms-cost` | Impl **$25–75 K** (1 small site, 3–6 mo) · **$100–350 K** mid-market |
| OrderPilot — WMS Implementation Cost | `orderpilots.com/wms-implementation-cost-guide/` | Mid-market SaaS **$10–15 K/mo = $120–180 K/yr** |
| Rorix — WMS Cost Breakdown 2026 | `rorixtech.com/blogs/warehouse/wms-cost-breakdown-2026-pricing-guide` | Impl **$10 K – $300 K+** |
| SC CodeWorks — WMS Implementation 2026 | `sccodeworks.com/wms-implementation/` | Single-warehouse 3PL cutover **8–12 weeks**; multi-warehouse **3–6 months** |
| Broken Rubik — NetSuite Pricing 2026 | `brokenrubik.com/blog/netsuite-pricing-the-definitive-guide` | Base **$999/mo** + **$129–199/user/mo**; **WMS ~$1,000–2,000/mo** |
| Inscio — NetSuite Pricing Guide | `inscio.com/blog/netsuite-pricing-guide/` | Mid-market **$50–100 K/yr** license + **$70–150 K** implementation |
| Packem — 3PL Software Pricing 2026 | `packemwms.com/3pl-software-pricing/` | Extensiv **~$599+/mo**, impl **$5–20 K+** |
| Capterra — 3PL Warehouse Manager | `capterra.com/p/86175/3PL-Warehouse-Manager/` | **$599.00** list price, 4.2 rating (88 reviews) |
| Capterra / G2 — Cin7 Core pricing | `capterra.com/p/133038/Cin7-Core/pricing/` · `g2.com/products/cin7-core/pricing` | **$349 – $1,199/mo**, 4 plans |
| Gartner — DOM market transition | `gartner.com` reviews/research pages | Market moving to **"Distributed Order Management Systems"**; **Market Guide for DOM (doc 6659334)**; legacy *MQ for OMS & WMS* being superseded |

### Tier 3 — Nexus internal (class 🔶, not externally verified)

| Source | Extracted |
|---|---|
| [`01-CURRENT-STATE.md`](./01-CURRENT-STATE.md) | Honest baseline: what works, gaps G1–G7 |
| [`08-SELF-HOST-BOOT.md`](./08-SELF-HOST-BOOT.md) | Production in **~1 hour** |
| [`09-COST-TCO.md`](./09-COST-TCO.md) | **$0 license**, **≈$76–110/mo** all-in |
| [`10-DATA-MODEL-REFERENCE.md`](./10-DATA-MODEL-REFERENCE.md) | **179 tables** |
| [`03-ER-DIAGRAM.md`](./03-ER-DIAGRAM.md) | **167 entities** |
| [`07-SCM-COMPARISON.md`](./07-SCM-COMPARISON.md) | Self-scored feature comparison: Nexus **8.4 🥇**, Manhattan **8.4 🥇**; HotWax pricing corrected to **$11,000/yr**; §7 sources are vendor marketing pages; records **961 tests** |

---

## Rerun Inputs

Re-run this document before any external, investor, or customer-facing use.

```bash
# 1. Market sizing (expect year-over-year restatements)
firecrawl search "warehouse management system market size 2026 2031 forecast billion CAGR" --limit 5
firecrawl search "supply chain management market size 2026 2031 forecast CAGR" --limit 5
firecrawl search "order management system market size 2026 forecast CAGR" --limit 5

# 2. Manhattan — always re-pull from SEC before quoting (class A)
firecrawl search "Manhattan Associates annual report total revenue current fiscal year" --limit 4
# then fetch the 10-K / latest quarterly IR release directly

# 3. Blue Yonder FY results + ownership changes (Panasonic)
firecrawl search "Blue Yonder annual revenue results fiscal year SaaS growth customers" --limit 4

# 4. Gartner — streaks and Leader set change each cycle; verify against a NAMED table
firecrawl search "Gartner Magic Quadrant warehouse management systems leaders consecutive years" --limit 5
firecrawl search "Gartner distributed order management market guide category" --limit 4

# 5. Pricing — check for newly published list prices (none expected for the four majors)
firecrawl search "Cin7 pricing plans per month" --limit 3
firecrawl search "HotWax Commerce OMS pricing Shopify App Store" --limit 3
firecrawl search "Extensiv 3PL Warehouse Manager pricing" --limit 3
firecrawl search "NetSuite WMS pricing per month module" --limit 3

# 6. Implementation cost benchmarks (refresh yearly)
firecrawl search "WMS implementation cost TCO 2027 guide mid-market" --limit 5

# 7. Nexus internal figures (🔴 must be updated or they go stale — 07 now records 961 tests)
grep -rn "961\|tests / 0 failures" docs/01-CURRENT-STATE.md docs/09-COST-TCO.md
```

**Known items to re-verify first (highest staleness risk):**

1. **Gartner 2026 WMS Leader set and streaks** — annual cycle; the 6th Leader is still unidentified and the 2025-vs-2026 streak conflict (§7.3) is unresolved.
2. **Blue Yonder market share** — both available figures are class C; do not cite until a primary or single-methodology source is found.
3. **Manhattan FY revenue** — re-pull from the current 10-K; the $1,081.4 M figure is FY2025 and will be superseded by FY2026.
4. **Nexus internal counts** 🔶 — test count, table count, and TCO must be re-derived from the repo; `07` test count refreshed to 961.
5. **Data gaps in §8** — customer counts and revenue for private vendors remain open; if a future run closes them, the scale table in §3 becomes materially stronger.

---

*Related: [`07-SCM-COMPARISON.md`](./07-SCM-COMPARISON.md) (capability scores) · [`09-COST-TCO.md`](./09-COST-TCO.md) (cost model) · [`18-NEXUS-EXPOSED.md`](./18-NEXUS-EXPOSED.md) (internal audit) · [`README.md`](./README.md) (doc index)*
