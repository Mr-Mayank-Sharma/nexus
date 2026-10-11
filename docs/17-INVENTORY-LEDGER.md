# 17 — Inventory ledger and stock movement

How stock changes in Nexus, and how to prove the books are right. Code: `InventoryService`,
`InventoryLedger`, `OrderStockCommit`, `TransferOrderService`; schema `V78`.

## The ledger

Every change to a stock row's **on-hand**, **allocated** or **reserved** quantity writes one line to
`nx_inventory_movements`, in the same transaction as the change. Lines hold deltas (not balances),
so they can be summed and compared with the rows. The table is **append-only**: a database trigger
refuses `UPDATE` and `DELETE`.

| Type | Written when | Changes |
|---|---|---|
| `ADJUSTMENT` | manual adjust, opening balance on create, bulk import, baseline | on-hand (and allocated for imports) |
| `RECEIPT` | an inventory receipt is received | on-hand + |
| `CYCLE_COUNT` | a count is recorded | on-hand ± |
| `RESERVE` / `RELEASE` | an order is allocated / cancelled | allocated + / − |
| `SHIPMENT` | an order ships | on-hand −, allocated − |
| `TRANSFER_OUT` / `TRANSFER_IN` | a transfer ships / is received | on-hand − at source / + at destination |
| `RETURN` | a returned item is restocked | on-hand + |

Each line carries the SKU, location, a reference (`ORDER`, `RECEIPT`, `TRANSFER`, …, plus the
document id), who did it, and an optional reason (for example "shipped 5 but only 2 were on hand").

**API:** `GET /inventory/movements?sku=&nodeId=&limit=` (history), `GET /inventory/ledger-check`
(every stock row against the sum of its ledger lines; non-zero `unexplained` is a change nobody
recorded), `POST /inventory/ledger/baseline` (gives stock that predates the ledger an opening
balance, tagged `BASELINE`).

## Rules this enforces

- **Shipping takes stock off the books.** Until V78 shipping changed only an order's status:
  on-hand stayed overstated and the allocation was never cleared. Now every path that ships an
  order (manual ship, a status change to shipped/delivered, packing, a shipment marked shipped,
  an external Shopify fulfilment) calls `OrderStockCommit`, which is idempotent per order line
  (`consumed_qty`), so shipping twice deducts once.
- **Cancelling releases only what is still reserved**, never units that already shipped.
- **A transfer moves stock.** Shipping takes it out of the source (only stock not allocated to
  orders), holds it as in-transit at the destination, and receiving adds it on hand there. Before,
  transfers changed only their status.
- **A manual adjustment of a SKU held at several locations must say which** (`nodeId`); it used to
  change an arbitrary row.
- **A return is restocked where the order shipped from**, else the SKU's only location, else the
  location holding the most (a return is never refused over ambiguity).
- **A warehouse is usable if it is `ACTIVE` or `OPERATIONAL` and not switched off.** Allocation
  required exactly `ACTIVE`, so a tenant whose warehouses were `OPERATIONAL` could not allocate.

## Known limits

- Consumption on shipment works from the per-line allocation, which is recorded when **one node
  covers the whole order**. A split allocation keeps its per-SKU truth in the allocation metadata,
  so those lines are skipped (and logged), not guessed.
- Partial shipments are treated as the whole order shipping.
- The ledger tracks on-hand, allocated and reserved. In-transit and on-order quantities change
  but are not ledgered.
- Lot/serial/expiry are carried on the stock row but movements are not lot-level.
- Damaged stock (`quantity_damaged`) is a field nothing yet maintains.
- Stock written by direct SQL, or by code paths not listed above, will show up as `unexplained`
  in the ledger check, which is its purpose.
