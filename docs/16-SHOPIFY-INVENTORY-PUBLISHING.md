# 16 — Shopify inventory publishing (location-aware)

How Nexus decides what quantity to offer on Shopify, how to set it up, and how to run the first
real test safely. Code: `service/shopify/InventoryPublishPlanner`, `ShopifyInventorySyncService`,
`InventoryChannelMappingService`; API: `ShopifyInventoryController`; schema: `V77`.

## The rules

Nothing is published for a store until you **map Nexus locations to Shopify locations**. With no
mapping the run is `BLOCKED` and Shopify is never called.

For each SKU and each Shopify location, the quantity is the sum, over the Nexus nodes (warehouses
or stores) mapped to that location, of:

> **on hand − allocated − reserved − damaged − safety stock − the mapping's buffer**, using only
> lots that have not expired, floored at zero **per node**, then capped by the mapping's
> contribution cap if one is set.

- A node maps to **one** Shopify location per store (a database constraint), so no unit is offered twice.
- Several nodes may share one Shopify location (a pooled online location).
- Inactive nodes and disabled mappings contribute nothing; unmapped nodes are never published.
- Flooring per node means an oversold store cannot cancel out another store's real stock.
- A SKU with no stock publishes **0**, so Shopify stops selling it.
- This is never more generous than the allocator (`InventoryService.available`): it additionally
  excludes damaged units and expired lots. (The allocator itself does not yet exclude damaged
  units; nothing maintains `quantity_damaged` today, so the two agree in practice.)

Nexus is authoritative for what it publishes. If the Shopify quantity differs from what Nexus
last set, it is overwritten and the line is flagged **drift** (someone edited it in Shopify).

## In the app

**Integrations → Shopify → select the store → Inventory publishing.** Add a row per Nexus
location, pick its Shopify location, optionally a buffer and a cap, and save. *Preview (no
changes)* runs a dry run and opens the reconciliation report; each line expands to show the
per-node arithmetic. *Publish to Shopify* asks for confirmation first. Preview and publish are
disabled while there are unsaved or invalid changes. (Integration screens are visible to
administrators only: no other role holds the `integration` permission group yet.)

## API (`/api/v1/shopify/stores/{storeId}/inventory`)

| Call | Purpose |
|---|---|
| `GET /shopify-locations` | Live Shopify locations (id, name, active) for the mapping screen |
| `GET` / `PUT /location-mappings` | Read / replace the mappings: `[{nodeId, externalLocationId, enabled, bufferUnits, contributionCap}]` |
| `POST /publish?dryRun=true` | Calculate, record, write nothing (**default**). `dryRun=false` writes to Shopify |
| `GET /runs` | History of runs, newest first |
| `GET /runs/{runId}/lines` | The reconciliation report: per SKU and location, the per-node arithmetic, the quantity Shopify held, what was published, any error |

Outcomes per line: `PLANNED` (dry run, would change), `PUBLISHED`, `UNCHANGED`, `SKIPPED` (no
Shopify variant for the SKU, or the location is gone/deactivated), `FAILED` (that line only; the
rest continue). A run is `COMPLETED`, `PARTIAL`, `FAILED` or `BLOCKED`.

The scheduler runs a real publish for every enabled store (default every 15 minutes). A scheduled
pass that changes nothing leaves no history row; runs older than 60 days are removed. Only one
run per store can be in flight (enforced by a unique index; a stale one is expired after 15 min).

## First real test, step by step

1. **Create a Shopify development store** and a custom app with `read_products`,
   `read_inventory`, `write_inventory`, `read_orders`, `write_orders`, `read_locations`, and
   create at least two locations there.
2. In Nexus, add the Shopify integration store with `shop_domain`, `access_token` (or
   `client_id` + `client_secret`) and `externalDomain`. Secrets are stored encrypted.
3. Set the webhook secret: Shopify signs webhooks with the app's **client secret**; store it as
   `client_secret`. Register webhooks (`POST /shopify/stores/{id}/webhooks/register?baseUrl=https://…`);
   the result lists each topic as `REGISTERED`, `EXISTS` or `FAILED: reason`. Shopify requires a
   public HTTPS URL, so the AWS test server needs a certificate (or a tunnel) first.
4. Run a **product sync** so Nexus SKUs are linked to Shopify variants.
5. `PUT /location-mappings` (or use `GET /shopify-locations` to pick ids).
6. `POST /publish?dryRun=true`, then read `/runs/{id}/lines`. Check every number against the
   per-node arithmetic in `calculation`.
7. Only then `POST /publish?dryRun=false`, and compare with the Shopify admin.
8. Change a quantity in the Shopify admin, publish again, and confirm the line shows `drift`.
9. Send an `app/uninstalled` webhook (uninstall the app) and confirm the store becomes `DISCONNECTED`.

## Not verified yet

- Nothing here has run against a **real** Shopify store: there are no credentials in this
  environment. The REST Admin API version is `app.shopify.api-version` (default `2026-07`);
  confirm that version, and that `inventory_levels/set` and `/connect` behave as expected, in step 6.
- Shopify's REST Admin API is legacy for new public apps; a GraphQL client
  (`inventorySetQuantities` with a compare-quantity) would also remove the read-then-write race
  with edits made in the Shopify admin. Not built.
- Product mapping (`nx_product_mappings`) has no store column, so one SKU maps to one Shopify
  variant per tenant; two Shopify stores in one tenant would collide.
- The OAuth install flow (authorization-code grant) is not implemented; credentials are entered.
- POS inventory pulls are not implemented (see the POS adapter contract, when added).
