# Release notes

## v0.1.0-rc3 (2026-10-18)

Adds, on top of rc2:
- **Routing what-if (order replay).** Upload your own orders, stock and (optionally) locations with their pick-failure
  rates as CSV, or load invented sample data, and see how three routing rules would have handled them: shipments,
  orders split, units not filled, expected failed picks and an adjustable cost estimate, with the orders that would
  ship differently and CSV downloads. It is a simulation to compare rules on the same orders, not a forecast, and the
  page says so beside the result. Nothing is stored and nothing in Nexus changes.
- **Fixed: the Integration Directory and Connections screens crashed in a real browser** (they passed their unit
  tests only because the tests mocked the API). Found by viewing the screens for the first time; now covered by a test
  that runs the real API client against a fake server.
- Scheduled Jobs shows as cards on a phone instead of a cut-off table; "1 shipments" fixed.

Every screen added in rc1 to rc3 has now been looked at in a real browser (desktop and phone width). The rest of the
app has not.

## v0.1.0-rc2 (2026-10-18)

Adds, on top of rc1:
- **Scheduled jobs.** Customers create and schedule their own jobs, and every run shows its counts, a log, the
  file it produced, and each failed row with its reason as a downloadable CSV. Six job types (Shopify orders,
  products and stock; NetSuite stock; stock and pick-failure exports). Details in `docs/20-INTEGRATIONS.md`, section 8.
- **A re-routed order's pick work is cancelled.** When a store rejects part of an order and the whole order goes
  to another store, the first store's open pick tasks for it are cancelled, and the store is told how many lines
  were already picked and must be put back.
- Shopify product sync now reports products honestly (it counted products as processed but variants as succeeded).
- `/jobs` is registered for permissions (the `import` group) and for editions (platform).

Upgrading from rc1: migration V83 creates the job tables; nothing else changes.

## v0.1.0-rc1: private pilot release candidate (2026-10-18)

**What this is.** The first version meant to be run for a real retailer under supervision: an order-orchestration
core centred on Shopify, with stores and warehouses as fulfilment locations. It is a release *candidate* for a
private pilot with a small number of founding customers. It is not general availability.

**Who it is for.** Shopify retailers with a handful to about a hundred locations that sell online and fulfil
from stores and warehouses, and want honest stock, fewer split shipments, and routing they can test before
changing.

### Verified against real systems (dev stores, dated in `docs/20-INTEGRATIONS.md`)
- Shopify: order import (5,052 orders; totals and line items equal Shopify's), product import (4,935 products),
  one real stock write read back from Shopify, and a **stock baseline import** from Shopify's physical on-hand
  (6,923 stock rows, ledger balanced).
- BigCommerce: product import (13 products, 68 variants).

### Verified by automated tests and a real PostgreSQL (1,100+ backend and 497 frontend tests)
- Order lifecycle, row-level tenant isolation, role-based access with MFA, sealed integration secrets.
- Stock ledger, reservations, transfers, cycle counts.
- Routing with rules and strategies; **a store rejection re-routes the whole order to a node that holds all of
  it and holds the rejected units out of availability**; **routing weighs each store's pick-failure rate**.

### Built, but not yet run against a real system
NetSuite stock import (token-based authentication and SuiteQL, built from NetSuite's published API); Shopify and
BigCommerce webhooks; BigCommerce order import; carrier labels with real accounts; the AI assistant (needs a
model key); a full-catalogue stock publish; fulfilment push and refund sync.

### Not included
SAP connector; a vendor POS adapter; Shopify POS transfer receiving; package-level item tracking; address
validation; seasonal routing profiles; a what-if order replay; a returns portal. See `docs/22`.

### Changes that matter when upgrading
- **Database:** migration V82 adds rejection fields; no data is rewritten.
- **Secrets:** `APP_CREDENTIAL_ENCRYPTION_KEY` is now required in production, and the encryption key is derived
  from the whole value (earlier versions used its first 16 characters). Secrets sealed by earlier versions are
  still readable and are re-sealed on their next save. Keep a copy of the key; losing it loses stored credentials.
- **New connections are read-only.** Scheduled stock, fulfilment and refund writes start switched off and must be
  turned on deliberately.
- **A stock publish skips any SKU Nexus has no stock record for**, and never sets it to zero.

### Conditions for a pilot
Run the stock baseline import (dry run first) before connecting a store to routing; map each location; keep
write syncs off until the dry runs look right; keep the encryption key and database backups somewhere safe;
use a hosted environment with HTTPS (webhooks need it) rather than a laptop.

### How to run
`docs/08-SELF-HOST-BOOT.md` (production compose), `scripts/verify-all.sh` (every check that CI would run).
