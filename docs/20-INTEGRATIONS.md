# 20 — Integrations: what is real, how it is shown, how to keep it true

> The rule for this product: **no screen, API answer or log line may claim a connection, sync or result that did not happen.**
> This document records how that rule is enforced for integrations, what was found wrong when it was first checked, and what is still not done.
> Companion: [18 — Nexus Exposed](./18-NEXUS-EXPOSED.md) (audit) · [16 — Shopify publishing](./16-SHOPIFY-INVENTORY-PUBLISHING.md).

## 1. The three kinds of integration

| Tier | Meaning | Where |
|---|---|---|
| **Built into Nexus** | Part of the product: own screens, storage, scheduling. Shopify, BigCommerce, POS stock feed, carrier labels, EDI, SSO, the assistant, warehouse equipment. | Directory → *Built into Nexus* |
| **Connector** | A connector class exists for the platform (Stripe, FedEx, QuickBooks, Twilio, Magento, Amazon, Salesforce, Okta, SAP). It covers only the capabilities listed as working; most only *read a preview*. | Directory → *Connector* |
| **Custom REST only** | No connector. You supply an address and a key; Nexus can test that it answers and count records from paths you configure. Nothing is imported, nothing is written. About 30 platforms. | Directory → *Custom REST only* |

## 2. Evidence, per capability

`GET /integration-hub/catalog` (rendered at **Integrations → Directory**) lists every provider and, for each capability, one of:

- **Tested**: automated tests run the logic with the outside system mocked.
- **Written, not tested**: the code exists; nothing checks it.
- **Not built**: Nexus does nothing with it.

Every entry also carries `liveVerified`. **It is true for five capabilities only** (see [§7](#7-live-verification-log)) and false for everything else, because nothing else has been run against a real platform with real credentials. The Directory says so at the top of the page and marks each checked capability. A test fails if anyone sets it without recording the evidence in `IntegrationCatalog.LIVE_EVIDENCE`.

## 3. How the Hub works now

- A **connection** is an integration store (`nx_integration_stores`): tenant-scoped by row-level security *and* an explicit check, persistent, with secrets sealed (AES-GCM) and masked in the API. A connector object is built from the stored settings for each request and keeps no state.
- **Saving is not verifying.** Creating a connection runs a real connection test and returns `connectionVerified`. The last test result (time, outcome, message) is stored. The UI shows *Verified*, *Failing* or *Never tested*; the sidebar shows a red count when any connection's last test failed.
- **Operations** are split in two: *actions* (change something on the platform; need confirmation) and *previews* (ask the platform for records and report the count; always answered `NOT_SUPPORTED` with "returned N … nothing was imported into Nexus").
- An operation a connector does not implement answers **501** (or `NOT_SUPPORTED`), never `COMPLETED`.
- **Webhooks:** no connector registers webhooks automatically. Shopify and BigCommerce register theirs from their own pages, and only against a public `https://` address.

## 4. What was found wrong (and fixed) when this was audited

| Finding | Fix |
|---|---|
| The Hub kept every connector in **one global in-memory map**: every organisation listed everyone's connections, and all of them (with credentials) vanished on restart. | Connections are integration stores. Tested for cross-tenant isolation. |
| Pushes (inventory, fulfilment, refund) in Shopify, BigCommerce, Magento, Amazon and **all of SAP** logged a sentence and answered `COMPLETED` with 0 items. | Removed. They answer `NOT_SUPPORTED`. A source guard fails the build if the pattern returns. |
| Reads ("Imported 124 orders") fetched records, counted them and **discarded them**. | Reported as previews: nothing imported. |
| Four connectors "registered webhooks" by posting to **Nexus's own address** (a parameter shadowed the platform's); Stripe overwrote the same form key six times; QuickBooks called an endpoint that does not exist. | Removed. The base class refuses and says to set it up in the platform's console. |
| `/integration/webhooks/*` received events unauthenticated and **discarded** them. | Deleted. |
| SAP `testConnection` returned `true` without testing anything. | Returns false unless the SOAP probe passes; SAP advertises nothing. |
| Twilio defaulted the sender to Twilio's magic **test number** and the text to "Your order has been shipped!". | No defaults: to, body and sender are required. |
| Generic platforms advertised imports, pushes and webhook support and a made-up `docs.example.com` link. | Advertise only a connection test and a read preview; no docs link. |
| Every store on **any** platform got five enabled sync configs; the scheduler then marked them `FAILED` every 30 seconds. | Defaults only for Shopify and BigCommerce; the scheduler skips the rest. |
| Shipping labels got a **fabricated tracking number** by default, and a simulated adapter's output was stored as a `CARRIER` purchase. | Production refuses to invent one (`nexus.carrier.simulation.enabled=false`); demo numbers are `SIM-` prefixed; a typed-in number is `MANUAL`. |
| The Hub screen said "connected" after any save and reported success for failed calls (its API wrapper swallowed errors). | Rewritten. A failed test is shown as failed, with the platform's reason. |
| The Store Connections page's BigCommerce syncs **always threw** (`import { default: bc }` of a module with no default export); "Register webhooks" reported success for BigCommerce without calling anything. | Fixed; webhook results are reported per topic; https is required. |
| Navigation had dead groups, two overlapping integration sections, a "Stores" link that opened the integration-stores page, and no section headings. | Seven labelled sections; Integrations / Sales Channels / Data Exchange under *Connect*; a test fails on any dead link, duplicate id or role typo. |

## 5. Rules for adding or changing an integration

1. Add the entry to `IntegrationCatalog` **first**, with the honest evidence for each capability. `IntegrationCatalogTest` compares it to the code.
2. A connector may only advertise (`supportedSyncTypes`) what changes something for real; read-only checks go in `previewSyncTypes`. `ConnectorTruthTest` checks that every advertised type can be dispatched.
3. Never return `COMPLETED` for work not done, and never log a line that stands in for an API call. Use `notSupported(...)` or `previewOnly(...)`.
4. Never default a credential, sender, recipient or message.
5. A UI handler must pass an API wrapper's result through `ensureOk` before reporting success.
6. Do not set `liveVerified` until the capability has run against the real platform; record how in `LIVE_EVIDENCE`.

## 6. Still not true, in order of importance

1. **Almost nothing has been run against a real platform.** Only the four read paths and one single-SKU stock write in §7 have. No other write to a real store has been run (full publish, fulfilment push, refund sync, webhook registration), and no BigCommerce order has ever been imported (the dev store has none). The Shopify order, product, fulfilment and refund services and most BigCommerce ones still have no automated tests.
2. Connectors cannot import into Nexus. Only Shopify and BigCommerce create Nexus orders.
3. No payment webhooks, no carrier tracking feed, no live carrier quotes, no EDI transport (AS2/SFTP/VAN), no SCIM.
4. `integration` permission is held by ADMIN only; other roles cannot manage connections.
5. About 30 "Custom REST only" platforms are listed for completeness; none does anything beyond a connection test and a preview.
6. The new Directory, Connections and sidebar are covered by component tests and a live API run, but **have not been looked at in a browser** (the browser tool was unavailable when they were built).
7. The legacy marketplace pages (Amazon, eBay, Walmart) show Nexus's own records; they are labelled as such.

## 7. Live verification log

**2026-10-15, Shopify dev shop `nexus-ship-ufb7gevv` and BigCommerce dev store `mhccbjdmoi`, read-only.** Credentials were supplied by the owner, saved through the API (sealed), and revoked afterwards by the owner. Nothing was written to either store. The single exception is the one-SKU stock canary in the table below.

| Check | Result |
|---|---|
| Shopify connection, scopes, locations | Passed. 23 locations found. |
| Shopify order import | **Matches.** 5,052 orders imported; the API lists 5,052. Order total 3,679,289.59 and 9,445 line items are identical on both sides. Shopify's count endpoint says 5,060: the 8 extra are older than 60 days and need the `read_all_orders` scope. |
| Shopify product sync | **Matches.** 4,935 products (9,996 variants), equal to Shopify's count. |
| Shopify stock publish | Full dry run planned 6,934 changes (all zeros, see defect 5). **Canary, real write, one SKU (100001) to `NEXUS-WH-001`:** published 7, Shopify read back 7; a repeat run said "already correct" and wrote nothing. Restored afterwards: the item was disconnected from that location and the shop is back to its original state (checked). Full-catalogue publish not run. |
| BigCommerce product sync | **Matches.** 13 products, 68 variants. |
| BigCommerce order import | Ran, imported 0. The store has 0 orders, so this proves the connection, not the mapping. |

Defects the live run found and fixed:

1. **BigCommerce access token was stored in plain text and returned by `GET /integrations/bigcommerce/config`.** It is now sealed at rest (AES-GCM, legacy values still read and sealed on the next save) and write-only in JSON. Tests: `SealedStringConverterTest`.
2. **A new Shopify or BigCommerce connection switched on scheduled stock, fulfilment and refund writes.** Only the order and product imports start enabled now. Test: `NewConnectionReadOnlyTest`.
3. **The manual Shopify product sync ignored the lock the scheduler uses**, so a manual run and a scheduled run collided, and one duplicate-SKU error poisoned a single long transaction and answered 500 after 3.5 minutes. The endpoint takes the lock, rows now save independently, and failed rows are counted and named.
4. `CredentialVault` falls back to a hard-coded key when `APP_CREDENTIAL_ENCRYPTION_KEY` is unset (a warning is logged). **Set it in every real environment.** Not changed here.
5. **A real stock publish would have emptied the shop.** Nexus holds stock for only 13 demo SKUs, none of them in the Shopify shop, so the "6,934 changes planned" were all *set to 0*. A SKU Nexus has no stock record for is now skipped (unknown is not zero), and the publish endpoint accepts `skus=` so the first real write can be a one-SKU canary. Tests: `ShopifyInventoryPublishTest`.
6. Cosmetic, not fixed: the product sync reports products as `processed` but variants as `succeeded`.

## 8. Scheduled jobs (the job manager)

A customer can create, schedule and watch their own jobs at **Connect → Data Exchange → Scheduled Jobs**
(`/jobs`), without an engineer. A job is a type, its parameters and an optional schedule; a new job is switched
off until someone turns it on. Every run records its counts, a log, an output file if the job makes one, and
**each row that failed or needs attention with the reason**, downloadable as a CSV.

| Job type | What it does | Writes to |
|---|---|---|
| Shopify: import orders | Reads orders into Nexus | Nexus only |
| Shopify: sync products | Reads the catalogue | Nexus only |
| Shopify: bring in current stock | Physical on-hand per mapped location; dry run unless "write" is on | Nexus only |
| NetSuite: bring in stock counts | SuiteQL inventory balances (not yet run against a real account) | Nexus only |
| Export stock (CSV) | Stock per SKU and location, with what can be sold | a file |
| Export store pick-failure rates (CSV) | The rates routing uses | a file |

Rules the code enforces (and tests pin): a job may not run more often than every 5 minutes; a job cannot be
switched on without a schedule; one run at a time per job; a stale run is closed out after 3 hours; schedules
fire under a database lock so two instances cannot double-start one; a store parameter must belong to the
tenant; CSV cells that look like spreadsheet formulas are neutralised; output is capped at 5 million characters
and 10,000 kept rows per run. Not built yet: scheduled file pickup from SFTP or S3, retrying only the failed
rows, failure notifications.

