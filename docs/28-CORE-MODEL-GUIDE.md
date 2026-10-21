# 28. Working with the core model

How the OFBiz-aligned core ([doc 23](23-DATA-MODEL-OFBIZ-ALIGNED.md)) is built, and how to add to it. Written at
W3 (foundations); each later step (W4-W9) adds entities the same way.

## Where things are

| What | Where |
|---|---|
| The model, as data | `scripts/core_model_spec.py` (entities, fields, keys, foreign keys, indexes, audited fields, migrations) |
| Seed rows | `scripts/core_seed_spec.py` (statuses and allowed changes, coded lists, units, currencies, countries and states) |
| Generator | `scripts/gen_core_model.py` |
| Generated schema | `nexus-oms-backend/src/main/resources/db/core/{postgresql,mysql,common}/V<n>__*.sql` |
| Generated entities | `nexus-oms-backend/src/main/java/com/nexus/core/model/<area>/` |
| Hand-written core code | `com.nexus.core.service`, `com.nexus.core.audit`, `com.nexus.core.config`, `com.nexus.core.model.CoreEntity` |

The core is a package, not yet a separate jar: Docker images, CD and Render all build from `nexus-oms-backend`, and
there is no second consumer until Nexus WMS returns. `CoreBoundaryTest` keeps it separable: core code may never
import `com.nexus.oms`.

## Adding to the model

1. Add the entity to `core_model_spec.py` with OFBiz names and types, and add its name to a **new** entry in
   `MIGRATIONS` (released migrations never change).
2. Add any seed rows to `core_seed_spec.py`, in a new `SEED_MIGRATIONS` entry.
3. Run `python3 scripts/gen_core_model.py`. It writes the PostgreSQL and MySQL scripts and the entity classes, and
   refuses to change a released migration.
4. Write the service code and tests. `CoreModelUpToDateTest` fails if a generated file was edited by hand or not
   regenerated.

Field types and their SQL:

| Type | PostgreSQL | MySQL | Java |
|---|---|---|---|
| `id` | `VARCHAR(40)` | `VARCHAR(40)` | `String` |
| `id-long` | `VARCHAR(255)` | `VARCHAR(255)` | `String` |
| `indicator` | `CHAR(1)` (`Y`/`N`) | `CHAR(1)` | `Boolean` (YesNoConverter) |
| `text-short` / `text-medium` / `text-intermediate` | `VARCHAR(63/255/1023)` | same | `String` |
| `text-long` | `VARCHAR(4000)` | `VARCHAR(4000)` | `String` |
| `text-very-long` | `TEXT` | `LONGTEXT` | `String` |
| `date-time` | `TIMESTAMP(3)` | `DATETIME(3)` | `LocalDateTime` (UTC) |
| `number-integer` | `BIGINT` | `BIGINT` | `Long` |
| `number-decimal` | `NUMERIC(26,6)` | `DECIMAL(26,6)` | `BigDecimal` |
| `currency-amount` / `currency-precise` | `NUMERIC(24,4)` / `NUMERIC(25,5)` | `DECIMAL` same | `BigDecimal` |

`text-long` is `VARCHAR(4000)` on both databases (doc 23 first said `TEXT` for MySQL); one type keeps Hibernate's
schema validation identical on both. MySQL tables use `utf8mb4` with a binary collation, so ids compare exactly as on
PostgreSQL.

## Migrations

The core has its own Flyway history table, `flyway_core_history`, separate from the legacy migrations. At start-up the
legacy migrations run first, then the core ones (`CoreSchemaConfig`), then Hibernate validates every entity. Turn the
core off with `nexus.core.schema.enabled=false`. The core builds an empty PostgreSQL or MySQL database on its own
(`CoreSchemaMigrator`).

## Framework services

| Service | Use it for |
|---|---|
| `SequenceService.nextId("OrderHeader")` | New ids: readable numbers from 10000, reserved in banks of 50 (`nexus.sequence.bank-size`) so servers never collide. `SequenceService.seqId(1)` gives `"00001"` for detail rows. |
| `StatusService.requireAllowed(flow, from, to)` | Before every status change. Allowed changes are data (`status_flow_transition`); the error message names the allowed next statuses. `initialStatus(flow, type)` gives the starting status. |
| `EnumerationService.require(type, id)` | Validating a coded value (sales channel, reason); the error lists valid values. |
| `SystemPropertyService` | Settings stored as data, with typed reads and readable errors. |
| `PartyService` | People and organisations with roles, outside ids, and dated contacts (address, phone, email) tagged with purposes. |
| `ProductService` (`coreProductService`) | Products with ids of every kind (SKU, barcode, channel ids, dated), variants, substitutes and kit components, features (size, colour), categories, and dated prices per currency and store group. |
| `ProductStoreService` | Stores and their settings (coded `PRODUCT_STORE_SETTING` values). |
| `FacilityService` (`coreFacilityService`) | Stores, warehouses and 3PLs with outside ids (POS, Shopify location), brokering groups in order, the stores they serve, and the daily order limit: `takeOrder(facility, day)` is false when the facility is full that day. |
| `InventoryService` (`coreInventoryService`) | Stock: every change is an `inventory_item_detail` row with a reason. `reserve` lowers ATP, `ship` lowers on hand, `recordCount` keeps the variance and its reason, transfers go request, approve (holds stock), ship, receive. `atp`, `atpByFacility`, and `product_facility` per product and facility for routing. |
| `OrderService` (`coreOrderService`) | Orders: `create` (roles, contacts, ship groups, items, adjustments, payments, outside ids), `changeStatus` (cascades to items, gives stock back on cancel), `cancelItem` (partial), `reserve` and `releaseReservations`, `findByIdentification("SHOPIFY_ORD_ID", id)`. Every status change is an `order_status` row. |
| `CoreClock.now()` | The time for dated keys; never the same millisecond twice, so dated primary keys don't collide. |
| `GeoService` | Free-text country and state ("US", "New Jersey") to geo ids. |
| `@AuditedField` | Mark a field in the spec with `audit`; every change goes to `entity_audit_log` with old and new value and the user. |

## Tests

| Test | Proves |
|---|---|
| `CoreSchemaMigrationTest` | The core builds and seeds an empty PostgreSQL database, beside legacy tables, idempotently, with foreign keys enforced; the MySQL scripts run on H2 in MySQL mode. A real MySQL run is enabled with `-Dnexus.test.mysql=true` (pulls `mysql:8.4`). |
| `CoreOnPostgresAppTest` | The whole app starts on PostgreSQL with legacy and core migrations and validates every entity. |
| `CoreServicesTest` | Ids (including 400 concurrent), status rules, enumerations, settings and the audit trail. |
| `FacilityInventoryServiceTest` | Receive, reserve, ship and count with full history; ATP less safety stock; transfers and cancelling; the daily order limit; groups in order. |
| `OrderServiceTest` | Totals with item and order adjustments; approve, reserve, partial cancel, cancel with stock given back; the flow enforced; orders copied in a given status. |
| `CoreBoundaryTest`, `CoreModelUpToDateTest` | The core stays separate; generated files match the spec. |

## Copying legacy data

Run the copies in this order: customers, products, stock, orders. Each later copy links to what the earlier ones
copied. `CoreMirror` runs them every minute for companies whose orders changed, and follows status and tracking
changes (`nexus.core.mirror.enabled`, `nexus.core.mirror.interval-ms`); its watermark is the system property
`nexus.core / mirror.lastRun`.

`POST /core-migration/orders` (admin) copies orders with items, shipping and tax as adjustments, payment status, the
channel order id, the customer's address as ship-to, and the old status and fulfilment type as order attributes. When
an old total differs from its parts, the difference is an adjustment, so grand totals match. It copied 5,224 dev
orders in 54 seconds.

`POST /core-migration/stock` (admin; run the product copy first) copies fulfilment nodes and warehouses into facilities and stock rows
into inventory items, one per lot, each with one `IID_MIGRATION` detail row. Available is on hand less allocated and
reserved, so held stock stays held; safety stock and reorder levels go to `product_facility`; damaged counts are noted
on the item. Rows without a location or with an unknown SKU are counted and skipped. On the dev data it copied 5 facilities and
6,929 items in 14 seconds, with totals matching the legacy tables.

`POST /core-migration/products` (admin) copies connected stores into product stores and products into the product
model (SKU, old id, name, picture, weight, price and cost in the company currency, category). It copied 6,962 dev
products in 15 seconds.

`POST /core-migration/customers` (admin) copies the caller's company's customers and addresses into the party model.
Each copy carries a `NEXUS_LEGACY_ID` identification with the old id, so running it again copies nothing twice. On the
development database it copied 4,934 customers.

## Conventions learned the hard way

- Name core beans `@Service("coreXxx")`: the OMS has classes with the same simple names (`ProductService`).
  `CoreBoundaryTest` enforces it.
- Four legacy entities clash with OFBiz entity names; they are named `LegacyProduct`, `LegacyInvoice`,
  `LegacyInvoiceItem` and `LegacyPayment` in JPQL. No table changed.
- `text-very-long` columns bind as plain text; PostgreSQL's driver rejects Hibernate's long-text type for nulls.
  `CoreOnPostgresAppTest` writes through the core on real PostgreSQL to catch this kind of difference, which H2 hides.
- Inventory detail rows take their id from the `InventoryItemDetail` sequence, not a per-item counter: counting
  detail rows needs a flush, and copy jobs run with COMMIT flush mode.
- Tests run outside `main()`, which sets the JVM to UTC, so a timestamp read with plain JDBC in a test is shifted
  by the machine's zone. Read timestamps through JPA in tests.
- With `hibernate.order_inserts`, a copy that flushes once per chunk has its inserts grouped by entity type, not in
  the order they were made. A row that a later entity points at (a guest party, a new address) needs an `em.flush()`
  right after it is created, or PostgreSQL rejects the foreign key. H2 in the tests did not catch this; the dev run did.
- Bulk copies read what exists once, keep lookups in memory, and flush once per chunk; per-row queries made the
  product copy 30 times slower.

## Not yet

- Real MySQL has not been run (no image locally); the scripts are checked on H2 in MySQL mode.
- The legacy model still has `tenant_id` and row-level security; they go as each area moves to the core (W4-W10).
- `user_account`, `service_job` and `system_message` exist but are not used yet: sign-in moves to `user_account` in a
  supervised session, jobs and messages in W10.
- Orders still point at `nx_customers`; they move to parties with the order model (W7).
