# 20 — Supply Chain & Integration Gap Analysis

> **What this is.** The honest answer to three questions the other docs circle but never answer head-on:
> **(1)** Nexus is sold as a *supply chain platform* — how much of the supply chain does it actually cover?
> **(2)** Of the ~39 advertised integrations, which are **real** and which are **demo**?
> **(3)** Which shipped features are **worthless** (or worse than absent), and what does the external
> evidence say actually decides whether an OMS integration program succeeds?
>
> **What this is not.** It is not a competitor scorecard ([`07`](./07-SCM-COMPARISON.md)), not an internal
> defect audit ([`18`](./18-NEXUS-EXPOSED.md)), and not a market-size report ([`19`](./19-MARKET-BENCHMARKS.md)).
> It is the **synthesis**: internal ground truth from `18` + external integration/SCM research, read together.
>
> **Evidence labels.** **[code]** read in source · **[test]** covered by CI · **[live]** observed running ·
> **[research]** external source (see §8) · **[unverified]** written but never exercised against the real system.
>
> **Companions:** [`18-NEXUS-EXPOSED.md`](./18-NEXUS-EXPOSED.md) (defects + roadmap) ·
> [`19-MARKET-BENCHMARKS.md`](./19-MARKET-BENCHMARKS.md) (market reality) ·
> [`07-SCM-COMPARISON.md`](./07-SCM-COMPARISON.md) (feature scores) ·
> [`features/10-integrations-edi-ipaas.md`](./features/10-integrations-edi-ipaas.md) (integration feature doc).

---

## 0. The one-line finding

Nexus is a **strong OMS + WMS with a tactical procurement module** — not a supply chain platform. The
**downstream half** of the chain (order → inventory → fulfillment → shipping → returns) is real and
partly live-verified. The **upstream half** (demand planning, supply planning, sourcing, supplier
collaboration, multi-echelon optimization) is **absent**. And the integration layer that would connect
either half is **~28 of ~39 connectors read-only while advertising two-way sync** — a gap that external
research says is *the* thing that decides whether OMS programs succeed or fail.

Three claims, each defensible:

1. **Scope:** Nexus covers the **execution** supply chain, not the **planning** supply chain. Calling it a "supply chain platform" overstates the upstream.
2. **Integrations:** The marketplace advertises ~39 platforms; **~11 are real, ~28 are read-capable but write-incapable**, and the event bus that would carry writes is **write-only** (every event dead-letters).
3. **Leverage:** The highest-value work is **not** new features — it is making the existing integrations honest and fixing the event backbone. External evidence (integration = 40–60% of OMS cost; most large programs miss budget) says this is where the money and the risk actually are.

---

## 1. What Nexus actually is vs what "supply chain platform" implies

The README calls it "an OMS & supply chain platform." That framing invites a buyer to expect the full
chain. Here is the chain, and where Nexus sits on it.

### 1.1 The supply chain, split into two halves

| Half | Stage | Nexus coverage | Evidence |
|---|---|---|---|
| **Upstream (plan & source)** | Demand planning / S&OP | ❌ Absent | no planning engine in the entity model |
| | Supply planning / MRP | ❌ Absent | replenishment is rule-based, not MRP |
| | Sourcing / supplier collaboration | 🟡 Thin | vendor records + POs, no supplier portal/RFQ/scorecards |
| | Multi-echelon inventory optimization | ❌ Absent | single-tier stock positions only |
| | Global trade / customs / landed cost | ❌ Absent | — |
| **Downstream (execute & deliver)** | Order management / DOM | ✅ Real | [`features/01`](./features/01-order-management.md) |
| | Inventory & ATP | ✅ Real | [`features/02`](./features/02-inventory-atp.md) |
| | Fulfillment (wave→pick→pack→ship) | ✅ Real | [`features/03`](./features/03-fulfillment-wave-pick-pack-ship.md) |
| | Shipping / carriers / yard | ✅ Real | [`features/04`](./features/04-shipping-carriers-yard.md) |
| | Returns / RMA | ✅ Real | [`features/05`](./features/05-returns-refunds.md) |
| | Procurement (tactical) | ✅ Real | [`features/06`](./features/06-procurement-suppliers.md) |
| | Finance / billing / 3PL | ✅ Real | [`features/08`](./features/08-finance-billing-payments.md) |

**The honest read:** Nexus is an **execution platform with a procurement tail**, not a planning platform.
That is a perfectly good product — but it is the *bottom* of the chain, and the top is where
"supply chain" buyers (and Gartner's SCM category) spend their planning budget.

### 1.2 The upstream void, stated plainly

- **No forecasting that drives replenishment.** There is an AI demand-forecast service
  (`Holt smoothing + grid search`, real metrics) **[code]**, but it does not yet *close the loop* into
  purchasing decisions — a human still reads a number and raises a PO.
- **No supplier collaboration surface.** Vendors are records you attach to a PO. There is no supplier
  portal, no RFQ/quote comparison, no supplier scorecard, no ASN *from the supplier's side*.
- **No network/multi-echelon view.** Stock is per-location. There is no "where should I hold this
  across the network" optimization.

> **Why this matters commercially:** the mid-market OMS competitors Nexus is measured against
> (Cin7, Brightpearl, NetSuite) also stop at execution + light planning. The **planning** tier
> (Blue Yonder, o9, Kinaxis) is a different product and a different buyer. Nexus should stop implying
> it plays there — see §6.4.

---

## 2. Integration reality: demo vs real

This is the section the marketplace page will not tell you. Grounded in
[`18` Part 4](./18-NEXUS-EXPOSED.md) **[code][test]**.

### 2.1 The three tiers

Counted directly from `ConnectorFactoryConfiguration.registerFactories()` **[code]** — **42 registered
factories** = **39 business connectors + 3 AI-platform connectors**.

| Tier | Count | What it is |
|---|---|---|
| **Dedicated** | **12** | Hand-written protocol logic per platform |
| **Generic** | **30** | One `GenericHttpConnector` with a platform name + a **placeholder** docs URL |
| **Protocols** | 4 | EDI · GraphQL · REST · SOAP (transport, not per-platform) |

**Dedicated (12):** Shopify · Magento · Amazon · BigCommerce · SAP · Salesforce · FedEx · Stripe ·
QuickBooks · Twilio · Okta · **OpenAI**

**Generic (30):** eBay · Walmart · Etsy · Alibaba · NetSuite · Oracle ERP · Dynamics 365 · HubSpot ·
Zoho · Manhattan · Blue Yonder · Oracle WMS · project44 · FourKites · UPS · DHL · PayPal · Adyen ·
Xero · Sage · Square · Lightspeed · SendGrid · Slack · Auth0 · Power BI · Tableau · Looker ·
**Anthropic · AWS Bedrock**

> **Reconciling with [`18`](./18-NEXUS-EXPOSED.md):** `18` says "~39, 11 dedicated, ~28 generic." That
> count **excludes the 3 AI-platform connectors** (OpenAI dedicated; Anthropic + Bedrock generic).
> Both are correct: **39 business connectors** (11 dedicated / 28 generic) **+ 3 AI connectors = 42**.
> This doc uses the source-exact **42 / 12 / 30** so the numbers reconcile with the registry.

### 2.2 The demo-vs-real matrix

| Capability | Dedicated (12) | Generic (30) |
|---|---|---|
| **Pull orders** | ✅ real, platform-specific | ✅ real REST GET — **but records are counted and discarded** (no mapping into Nexus orders) |
| **Pull products** | ✅ real | ✅ real REST GET — same discard caveat |
| **Push inventory** | ✅ where implemented | ❌ **no-op** (returns `NOT_SUPPORTED` after the fix) |
| **Push fulfillments** | ✅ where implemented | ❌ **no-op** |
| **Push refunds** | ✅ where implemented | ❌ **no-op** |
| **Register webhooks** | ✅ where implemented | ❌ **no-op** → polling-only |
| **Test connection** | ✅ platform-specific | ⚠️ probes `/` → false connected/failed |

**Before the fix, this was worse:** generic writes returned `COMPLETED` with **0 items** — a silent
success. And the **Amazon / eBay / Walmart** connectors *simulated everything*: "sync" counted local
orders and wrote a `SUCCESS` log; `authorize` marked the connector connected **without checking** and,
with no token, stored the literal word `"authorized"` **[live]**. Both were corrected (`c3e1390`,
`NOT_SUPPORTED` fix) — but the depth gap itself is unchanged.

### 2.2b The lie is in the metadata, not just the marketplace

The generic factory's own `ConnectorMetadata` **[code]** declares:

- `supportedSyncTypes = [ORDER_IMPORT, PRODUCT_SYNC, INVENTORY_PUSH]` — advertises **inventory push**
- `supportsWebhooks(true)` — advertises **inbound webhooks**

…while the `GenericHttpConnector` it builds **returns `NOT_SUPPORTED` for push and no-ops
`registerWebhooks`**. So the dishonesty is not confined to a marketing page — it is baked into the
**object the platform uses to describe itself**, and any UI that reads `supportsWebhooks` will render a
green "webhooks supported" badge that is false. The connector's docs URL is also a **placeholder**:
`https://docs.example.com/<platform>`.

**This is the §4 pattern again:** the code's *default* is to assert a capability rather than to check
for it. Fixing the marketplace copy without fixing the metadata leaves the lie in the API.

### 2.3 The number that matters

| Claim | Reality |
|---|---|
| "~39 integrations" | **42 registered factories** (39 business + 3 AI) |
| "Two-way sync" | **12 can actually push.** 30 are **read-only** |
| Generic `ConnectorMetadata.supportedSyncTypes` | Advertises `ORDER_IMPORT / PRODUCT_SYNC / **INVENTORY_PUSH**` — push **not implemented** |
| Generic `ConnectorMetadata.supportsWebhooks` | `true` — but `registerWebhooks` is a **no-op** |

**The honest framing:** for a product sold on two-way sync, **30 of 42 connectors are half-implemented**
(and the 3 AI connectors are a different class entirely — inference, not commerce sync). A read-only
connector is not worthless — it can pull orders — but presenting it as two-way destroys trust the first
time a customer's inventory drifts and nobody notices.

### 2.4 The event backbone is write-only

Even the *real* integrations depend on events to react. Per [`18` ISS-01](./18-NEXUS-EXPOSED.md):

- Producers publish a **bare UUID**; the consumer parses an **`EventRecord` envelope** → **every order event dead-letters**.
- The one correct API (`publishEvent`) had **zero callers**.
- Kafka is **off by default** (`nexus.kafka.enabled=false`) — so the bug hides until someone enables it.

**Net effect:** notifications, analytics, workflow triggers, audit trail, and AI triggers that hang off
Kafka events are **silently dead** until P0 lands. The integration layer's "reactive" half does not
function end to end.

---

## 3. The integration patterns that actually decide success

This is the external research **[research]** — what actually goes wrong in OMS/ERP/WMS integration
programs, independent of vendor. It is the lens to read §2 through.

### 3.1 Push vs poll — the latency that breaches SLAs

- **Event-driven / webhook push** is the only pattern that supports intraday promises. Polling introduces
  a lag window in which the OMS believes stock exists that a warehouse has already consumed.
- **Legacy constraint:** some platforms (and most EDI-only partners) offer **only** batch (SFTP/EDI) —
  no REST, no webhooks. That latency is not a bug; it is a design constraint the dispatch layer must
  *plan around*, not wish away.
- **Nexus today:** dedicated connectors can register webhooks; generic connectors **cannot** → they are
  **polling-only**, and the marketplace does not say so.

### 3.2 Idempotency — the difference between "retry" and "double-ship"

- Every integration that can retry **must** be idempotent, or a network blip becomes a duplicate order,
  a double refund, or a double inventory decrement.
- **Nexus today:** has an `IdempotencyFilter` (dedupe by `Idempotency-Key`) **[code]**, a
  **QuickBooks refund idempotency** path **[test]**, and **integration outbound retry + idempotency +
  DLQ parking** **[test]**. This is genuinely good — but it is **not universal** across connectors, and
  consumer-side event dedupe (`nx_processed_events`, V79) is recent.

### 3.3 Master data & crosswalk — the #1 reason integrations fail

- Integration projects fail most often on **data**, not APIs: **duplicate SKUs, missing GTINs, mismatched
  customer IDs**, units-of-measure mismatches, and no canonical identifier map between systems.
- **Nexus today:** a rich internal data model (179 tables) **[code]**, but **no visible crosswalk /
  external-ID mapping service** for reconciling "their SKU" with "our SKU." This is a **silent gap** —
  it will not show up in a demo and it will show up in every real deployment.

### 3.4 Error handling — DLQ, retries, and the runbook nobody writes

- The failure mode is not "an error happened"; it is **"an error happened and nobody knew."**
  Mature programs have: retries with backoff, a dead-letter queue with an owner, **alerting**, and a
  **runbook** for replay.
- **Nexus today:** retries + DLQ parking exist **[test]**, but per [`18` ISS-02](./18-NEXUS-EXPOSED.md)
  the outbox `FAILED` state had **no purge, no metric, no alert** — you were **blind** to dropped events.
  ISS-07: **three uncoordinated DLQ mechanisms**, one of which (the Spring recoverer) was a **no-op**.

### 3.5 The operational edges that bite in production

| Edge | Why it bites | Nexus status |
|---|---|---|
| **OAuth token lifecycle** | Tokens expire; refresh must be atomic or the connector dies mid-sync | partial **[code]** |
| **Rate limits (HTTP 429)** | A bulk sync without `Retry-After` handling gets throttled or banned | Shopify ✅ **[test]**; generic ⚠️ |
| **Business logic in the wrong layer** | Logic duplicated in the integration layer instead of the domain → drift | risk **[code]** |
| **Undefined scope** | "Integrate with their ERP" with no field-level mapping agreed → endless change orders | process gap |
| **No ownership** | Nobody owns the integration after go-live | process gap |

### 3.6 The composable/MACH reality check

Nexus is positioned as the "composable" alternative. The external evidence is more nuanced than the
MACH marketing **[research]**:

- **Adoption is real** — composable/MACH adoption is majority-of-enterprise.
- **But there is a backlash:** mid-market firms are **reverting** to suites because the **integration
  tax** (20–35% of engineering capacity) and vendor-sprawl outweigh the flexibility.
- **The lesson Nexus should take:** "composable" is a promise to **own the integration burden**, not to
  hand it to the customer. A composable product that ships **read-only connectors and a write-only event
  bus** is *the worst of both worlds* — the customer pays the integration tax **and** gets broken sync.
- **"Smallest credible architecture"** is the emerging guidance: ship the few integrations that are
  **fully real** (push + webhook + idempotency + conformance-tested), not the many that are half-real.

---

## 4. Which features are worthless (or worse than absent)

"Worthless" here means: **a user cannot rely on it, and its presence actively misleads.** Ranked by how
dangerous the gap is.

| # | Feature | Why it's worse than absent | Status |
|---|---|---|---|
| 1 | **Generic connector "writes"** | Returned `COMPLETED` with 0 items → customer believes inventory synced when it didn't | ✅ now `NOT_SUPPORTED` **[test]** |
| 2 | **Amazon/eBay/Walmart "sync"** | Counted local orders and logged `SUCCESS`; `authorize` stored the word `"authorized"` with no token | ✅ fixed (`c3e1390`) **[live]** |
| 3 | **`/ai/chat` with no model** | Invented order numbers, "sales up 18%", fake SKUs and dollar amounts | ✅ now 503 with instructions **[live]** |
| 4 | **`AiAuditTrailPage`** | Renders "Audit trail not yet available" — a page that exists to imply a capability that isn't wired | 🔴 **Open** |
| 5 | **Generic `testConnection`** | Probes `/` → reports connected/failed on nothing meaningful | 🟡 open |
| 6 | **Marketplace `supportedSyncTypes`** | Advertises push capabilities that don't exist for ~28 platforms | 🟡 partially honest |
| 7 | **`registerWebhooks` no-op** | No inbound webhooks for generic platforms → silent polling-only | 🟡 open |
| 8 | **Multi-Node Inventory page (pre-fix)** | Showed `$12.4M`, invented "Mumbai Central WH", a search that never returns, a toast that never calls the API | ✅ rebuilt on real data **[live][test]** |

**The pattern:** every one of these was a **confident lie** — a success message, a number, or a page
that asserted something untrue. That is the specific failure mode Nexus's honesty thesis exists to
prevent, and it recurred **eight times**. The lesson is not "we fixed them"; it is **"the default
behaviour of this codebase, under time pressure, is to fabricate a plausible success."** The
conformance-test culture in §6.2 is the structural fix.

---

## 5. What's needed vs what we have

The gap matrix. "Needed" = required for a credible mid-market OMS integration story, per §3.

| Capability | Needed for | Nexus has | Gap |
|---|---|---|---|
| **Real two-way connectors (top platforms)** | Any two-way sync promise | 11 real / ~28 read-only | **Promote UPS, DHL, NetSuite, Walmart, eBay** |
| **Connector conformance test** | Stop the gap reopening | none | **Build it** (auth/pull/push/webhook/error) |
| **Master-data crosswalk** | Every real deployment | not visible | **Build external-ID mapping** |
| **Event backbone that works** | Notifications, analytics, AI, audit | write-only (ISS-01) | **Fix producer → envelope** |
| **Webhook inbound for generic** | Low-latency inbound | no-op | Wire or badge "polling-only" |
| **FAILED-event observability** | Knowing when sync drops | no metric/alert (ISS-02) | **Metric + alert + purge** |
| **Capacity to push inventory (generic)** | The #1 OMS job | no-op | Implement or refuse loudly |
| **Demand → replenishment loop** | "Supply chain" claim | forecast exists, not closed | **Close the loop** |
| **Supplier collaboration** | Upstream credibility | absent | Out of scope — stop implying it |
| **Multi-echelon / network optimization** | Enterprise supply chain | absent | Out of scope — different product |

**Read the two "out of scope" rows carefully.** They are not gaps to close; they are **claims to stop
making.** Nexus's honest position is execution + tactical procurement. That is a large, real market.

---

## 6. Research-backed recommendations

Ordered by leverage. This deliberately **overlaps and reinforces** [`18` Part 5](./18-NEXUS-EXPOSED.md) —
where it agrees, that is corroboration; where it adds, it is the external evidence.

### 6.1 P0 — Fix the event backbone (unblocks everything)

Every reactive capability — notifications, analytics, AI triggers, audit — hangs off Kafka events.
Today they **all dead-letter**. This is [`18` P0](./18-NEXUS-EXPOSED.md) and §2.4 agrees.

- Switch the **11 call sites** to `publishEvent(...)`; fix the **producer**, never loosen the consumer.
- Ship the **`nexus_outbox_failed_total` gauge + alert** *with* P0, or you fix the bus and stay blind.
- Enable Kafka in the standard compose (`NEXUS_KAFKA_ENABLED=true`) so the fix is observable.

### 6.2 P1 — Make integrations honest, then real

**Step 1 — Honesty (cheap, immediate):**
- Split `supportedSyncTypes` into **implemented vs planned**; render **"read-only" badges**.
- Keep the `NOT_SUPPORTED` behaviour for generic writes (already done) — never a silent `COMPLETED`.
- Fix `testConnection` to require a configured `health_path`; else return "unknown."

**Step 2 — Depth (staged):**
- Promote the top-5 generic platforms to **dedicated** (UPS, DHL, NetSuite, Walmart, eBay).
- Add a **connector conformance test** every connector must pass — this is how incumbents certify
  integrations, and the only structural defence against §4's "fabricate a plausible success" pattern.

### 6.3 P1.5 — Build the master-data crosswalk (the silent gap)

External research is unambiguous: **data, not APIs, is why integrations fail.** Nexus has no visible
external-ID mapping. Build a **crosswalk service** (their SKU ↔ our SKU, their customer ↔ ours, UoM
normalization) **before** promoting more connectors — otherwise you scale the failure, not the feature.

### 6.4 P2 — Correct the positioning (free, high-impact)

- **Stop calling it a "supply chain platform."** Call it what it is: an **execution OMS+WMS with
  procurement**, the composable alternative for mid-market. The upstream (planning, sourcing,
  multi-echelon) is a different product and a different buyer.
- **Own the integration burden.** "Composable" must mean *Nexus* absorbs the integration tax, not the
  customer. A composable product with read-only connectors inverts the promise.
- **Lead with the two things that are externally defensible:** **$0 license / ~1-hour boot** (cost) and
  **honesty** (no fabricated data). Both are corroborated in [`19`](./19-MARKET-BENCHMARKS.md).

### 6.5 P3 — Operational hardening

Per [`18` P3](./18-NEXUS-EXPOSED.md): FAILED-event purge + alert, single DLQ owner, lock window ≥
worst-case batch, consumer dedupe, replication factor ≥ 3 in prod. These are the "error happened and
nobody knew" fixes §3.4 says decide whether a program survives contact with production.

### 6.6 Anti-priorities (what NOT to do)

- **Don't add connectors** before the existing ~28 are honest.
- **Don't build upstream/planning features** to justify the "supply chain" label — that is a new product.
- **Don't build new AI features** while the event bus is write-only — they have no events to react to.
- **Don't "fix" the event bug** by loosening the consumer contract.
- **Don't ship onboarding docs** describing a flow the code does not implement ([`18` P2](./18-NEXUS-EXPOSED.md)).

### 6.7 Success metrics

| Workstream | Metric | Target |
|---|---|---|
| P0 | Order events reaching `NxAuditLog` | 100% (from ~0%) |
| P1 | Connectors advertising two-way that actually push | 11 → 16+ |
| P1 | Silent no-op writes | 0 |
| P1.5 | Entities with a crosswalk mapping | SKU + customer first |
| P2 | Docs that call it a "supply chain platform" | 0 |
| P3 | DLQ depth + FAILED outbox rows | Alerted, bounded |

---

## 7. Open questions & data gaps

Recorded so a future pass knows where the holes are.

1. **Is there any external-ID mapping today?** Not found in this pass — needs a source-level check before §6.3 is scoped.
2. **How many connectors have real push, exactly?** `18` says ~11 dedicated; the *per-connector* push matrix is not enumerated.
3. **Does the AI forecast feed replenishment at all?** Forecast service exists **[code]**; the loop into POs is unverified.
4. **Rate-limit / OAuth-refresh coverage across the 11 dedicated connectors** — only Shopify is confirmed hardened **[test]**.
5. **The composable-backlash sources** are directional **[research]**; a future pass should pin named studies before quoting externally.

---

## 8. Sources

**Internal (ground truth):**
- [`18-NEXUS-EXPOSED.md`](./18-NEXUS-EXPOSED.md) — Parts 2 (findings), 4 (connector deep dive), 5 (roadmap), 6 (live findings)
- [`19-MARKET-BENCHMARKS.md`](./19-MARKET-BENCHMARKS.md) — market reality, TCO, published pricing
- [`07-SCM-COMPARISON.md`](./07-SCM-COMPARISON.md) — feature scores (capability presence, not market position)
- [`features/10-integrations-edi-ipaas.md`](./features/10-integrations-edi-ipaas.md), [`features/06-procurement-suppliers.md`](./features/06-procurement-suppliers.md)
- `ConnectorFactoryConfiguration.java` **[code]** — the dedicated/generic split

**External [research]:**
- OMS/ERP/WMS integration practice — idempotency, webhook-vs-poll, OAuth lifecycle, rate limits, DLQ, canonical data models, master-data crosswalk, EDI–API bridging.
- Practitioner post-mortems on why integration projects fail — data mapping, undefined scope, business logic in the wrong layer, ownership, master-data failures.
- Composable/MACH adoption **and backlash** — integration tax as a share of engineering capacity; mid-market reversion; "smallest credible architecture."
- OMS market & TCO — analyst Leader sets; implementation ≈ half of project cost; services ≈ license cost; integration 40–60% of cost; buy-don't-build; staged rollout.

> ⚠️ **[research] items are directional and were gathered via web search in this session.** Before any
> external/investor/customer use, pin each to a named, dated source (same discipline as [`19` §11](./19-MARKET-BENCHMARKS.md)).

---

*Related: [`18-NEXUS-EXPOSED.md`](./18-NEXUS-EXPOSED.md) (defects + roadmap) ·
[`19-MARKET-BENCHMARKS.md`](./19-MARKET-BENCHMARKS.md) (market reality) ·
[`07-SCM-COMPARISON.md`](./07-SCM-COMPARISON.md) (feature scores) · [`README.md`](./README.md) (doc index)*
