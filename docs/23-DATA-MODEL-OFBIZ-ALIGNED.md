# 23. Nexus data model, aligned with OFBiz

Status: target design, approved direction (2026-10-20). Replaces the current 196-table model step by step.

Sources studied:
- Apache OFBiz data model book (32 pages, 2017 edition): common, party, product, store, facility, inventory, order, shipment, return, accounting, work effort, service and integration entities.
- Moqui framework entities (CC0): basic (enumeration, status, UOM, geo), security, service jobs, system messages, entity audit and sequences.
- HotWax's Apache-2.0 components (`ofbiz-oms-udm`, `oms`, `order-routing`, `shopify-connector`, `netsuite-connector`): which OFBiz entities an OMS keeps, and the fields and entities HotWax added after moving OFBiz to Moqui.

Nothing is copied from components without a licence (`poorti`, `ai`, `maarg-util`, `gql`, client folders).

## 1. Decisions

| Decision | Choice | Why |
|---|---|---|
| Tenancy | One installation and database per client. No `tenant_id`, no row-level security. | Clients can take their whole database at any time; no cross-client risk; simpler queries. |
| Databases | PostgreSQL and MySQL 8, both supported and tested. | OFBiz's model uses only portable types. |
| Keys | OFBiz style: text ids (`VARCHAR(40)`) from a sequence table; detail rows keyed by parent id plus a sequence id (`order_item_seq_id = '00001'`). | Readable ids, works on both databases, the same shape as OFBiz and HotWax. |
| Naming | OFBiz entity and field names, as snake_case tables and columns: `OrderHeader.orderId` becomes `order_header.order_id`. | Anyone who knows OFBiz or HotWax can read the schema. |
| Foreign keys | Every relationship is a real foreign key, except where OFBiz itself marks it "no FK" (for example `createdByUserLogin`). | No orphaned rows. |
| Types and statuses | Type tables with `parent_type_id`; `status_item` plus `status_flow_transition` for allowed changes; a status history table for every object with a status. | Admins add types and change flows as data; every status change is kept. |
| Dated links | Associations and memberships carry `from_date` and `thru_date`, part of the key. | History is kept, and changes can be scheduled. |
| Standard columns | Every table has `last_updated_stamp`, `last_updated_tx_stamp`, `created_stamp`, `created_tx_stamp`. | The OFBiz/Moqui convention; used for sync and change feeds. |
| History | Written by the application (an entity-change listener), not by database triggers. | Triggers differ between PostgreSQL and MySQL. |

### Field types (OFBiz type to SQL)

| OFBiz type | PostgreSQL | MySQL 8 |
|---|---|---|
| `id` | `VARCHAR(40)` | `VARCHAR(40)` |
| `id-long` | `VARCHAR(255)` | `VARCHAR(255)` |
| `id-vlong` | `VARCHAR(4000)` | `TEXT` |
| `indicator` / `text-indicator` | `CHAR(1)` | `CHAR(1)` |
| `text-short` | `VARCHAR(63)` | `VARCHAR(63)` |
| `text-medium` / `name` / `description` | `VARCHAR(255)` | `VARCHAR(255)` |
| `text-intermediate` | `VARCHAR(1023)` | `VARCHAR(1023)` |
| `text-long` / `comment` | `VARCHAR(4000)` | `TEXT` |
| `text-very-long` | `TEXT` | `LONGTEXT` |
| `date-time` | `TIMESTAMP(3)` | `DATETIME(3)` |
| `date` / `time` | `DATE` / `TIME` | `DATE` / `TIME` |
| `currency-amount` | `NUMERIC(24,4)` | `DECIMAL(24,4)` |
| `currency-precise` | `NUMERIC(25,5)` | `DECIMAL(25,5)` |
| `number-decimal` / `fixed-point` | `NUMERIC(26,6)` | `DECIMAL(26,6)` |
| `number-integer` | `BIGINT` | `BIGINT` |
| `number-float` | `DOUBLE PRECISION` | `DOUBLE` |
| `binary-very-long` | `BYTEA` | `LONGBLOB` |

## 2. Layers

```
Framework (Moqui-style)   enumeration, status, uom, geo, sequence, system property, user account and groups,
                          service jobs, system messages, data manager, entity audit log
Common business (OFBiz)   party, contact mech, product, category, feature, price, product store,
                          facility, inventory, order, shipment, picklist, return, invoice, payment, work effort
OMS extensions (HotWax)   order and facility extras, brokering fields, routing, facility order count,
                          excluded order facility, product store settings, identifications
Nexus additions           store integrations, channel stock publishing, AI, warehouse operations,
                          procurement, replenishment, using the same conventions
```

## 3. Framework entities

| Entity | Purpose | Replaces in Nexus today |
|---|---|---|
| `enumeration_type`, `enumeration` (+ `enum_group`, `enum_group_member`) | Every coded list: sales channels, reasons, condition types, setting types | text values checked in code |
| `status_type`, `status_item`, `status_flow`, `status_flow_item`, `status_flow_transition` | Every status and which changes are allowed (per flow, so a store can have its own order flow) | `OrderStatusPolicy` in code |
| `uom_type`, `uom`, `uom_conversion` | Currency, weight, length, time | hard-coded currency and units |
| `geo`, `geo_assoc`, `geo_point` | Countries, states, cities, postal codes, coordinates | free-text country/state |
| `sequence_value_item` | Id generation, banked per server | UUIDs |
| `system_property` | Settings by resource and property id | `application.properties`, `nx_company_settings` |
| `user_account`, `user_group`, `user_group_member`, `user_permission`, `user_group_permission`, `user_login_history`, `user_password_history`, `user_preference`, `user_login_key` | Sign-in and permissions; `user_account.party_id` links a user to a person | `nx_users`, `nx_user_roles`, `nx_role_permissions`, `nx_auth_tokens` |
| `user_auth_identity` (Nexus) | Google, Microsoft, Okta sign-in links | `nx_user_identities` |
| `service_job`, `service_job_parameter`, `service_job_run`, `service_job_run_lock`, `service_job_user` | Scheduled jobs: cron, parameters, every run with results and errors, a lock so only one server runs a job | `nx_jobs`, `nx_job_runs`, `nx_job_run_issues`, `shedlock` |
| `system_message_type`, `system_message_remote`, `system_message`, `system_message_error` | Every message to or from another system (webhooks in, feeds out, EDI), with status, retries and errors | `nx_integration_messages`, `nx_outbox`, `nx_integration_dlq`, webhook tables |
| `data_manager_config`, `data_manager_log`, `data_manager_parameter` (HotWax) | File and API imports/exports: one config per feed, one log per run with record and failure counts | `import_history`, `import_record_log`, generic connection runs |
| `entity_audit_log` | Field-level change history for audited fields | `nx_audit_log` |
| `note_data` | Notes attached to orders, returns, parties | ad hoc note columns |

## 4. Party and contacts

`party` (type, status, external id), `person`, `party_group`, `role_type`, `party_role`, `party_relationship` (+ type), `party_identification` (+ type), `party_attribute`, `party_classification` (+ group, type), `party_status`, `party_note`, `party_carrier_account`.

Contacts: `contact_mech` (+ type), `postal_address` (with HotWax's `latitude`, `longitude`), `telecom_number`, `contact_mech_purpose_type`, `party_contact_mech`, `party_contact_mech_purpose`.

Replaces `nx_customers`, `nx_contacts`, `nx_addresses`, `nx_suppliers`, `nx_supplier_contacts`. A customer is a party with role `CUSTOMER`; a supplier a party with role `SUPPLIER`; a carrier a party with role `CARRIER`; the company itself a party group with role `INTERNAL_ORGANIZATIO`.

## 5. Product

`product` (+ `product_type`; HotWax adds `primary_product_store_id`, thumbnail urls), `product_assoc` (+ type: `PRODUCT_VARIANT`, `PRODUCT_SUBSTITUTE`, `PRODUCT_COMPONENT` for kits), `good_identification` (+ type: SKU, UPCA, EAN, SHOPIFY_PROD_ID...), `product_attribute`, `product_keyword`, `product_content`.

Features: `product_feature` (+ type, category), `product_feature_appl` (+ type). Size and colour are features; a variant's features say which size it is, which is what "broken style" needs.

Catalogue: `prod_catalog`, `prod_catalog_category`, `product_category` (+ type), `product_category_member`, `product_category_rollup`, `product_store_catalog`.

Price: `product_price` (+ type, purpose), keyed by product, type, purpose, currency, store group and `from_date`. Promotions (`product_promo`...) come later, only if Nexus prices orders itself.

Replaces `products`, `nx_product_mappings` (becomes `good_identification`), `nx_kit_templates` (becomes component associations).

## 6. Product store

`product_store` (OFBiz fields plus HotWax's `enable_brokering`, `allow_split`, `days_to_return`, `auto_accept_return`, `product_identifier_enum_id`, `enable_pre_order_auto_releasing`), `product_store_group`, `product_store_facility`, `product_store_facility_group` (HotWax), `product_store_setting` (HotWax: one row per setting enum), `product_store_role`, `product_store_shipment_meth`, `product_store_email_setting`.

The store is the unit a sales channel connects to: one Shopify shop is one product store. Its settings replace `nx_routing_config`'s release days and auto-allocation switches.

## 7. Facility and inventory

`facility` (+ type with parent; HotWax adds `open_time`, `close_time`, `facility_time_zone`, `maximum_order_limit`), `facility_group` (+ type, rollup), `facility_group_member` (sequence, dated), `facility_contact_mech`, `facility_contact_mech_purpose`, `facility_location` (area, aisle, section, level, position), `facility_party`, `facility_identification` (HotWax), `facility_calendar`.

`product_facility`: per product and facility. OFBiz fields (`minimum_stock`, `reorder_quantity`, `days_to_ship`, `last_inventory_count`) plus HotWax's `allow_brokering`, `allow_pickup`, `sales_velocity`, `reorder_point`, `maximum_stock`, `computed_last_inventory_count`.

`inventory_item` (+ type, status): a quantity of one product at one facility and location, optionally a lot or serial number, with `quantity_on_hand_total` and `available_to_promise_total`. `inventory_item_detail`: every change (positive or negative), with the order, shipment, return, receipt or physical count that caused it. `inventory_item_variance` with `variance_reason`, `physical_inventory`, `lot`, `inventory_transfer`.

`facility_order_count` (HotWax): orders taken per facility per day, for the daily limit.

Replaces `nx_nodes` and `nx_warehouses` (one `facility` table), `nx_inventory` (becomes `inventory_item` plus `product_facility`), `nx_inventory_movements` (becomes `inventory_item_detail`), `nx_cycle_counts` (physical inventory and variance), `nx_serialized_inventory` (serialised inventory items), `nx_warehouse_zones` and `nx_warehouse_bins` (facility locations), `nx_inventory_receipts` (`shipment_receipt`).

## 8. Orders

`order_header` (type, sales channel enum, status, product store, origin facility, currency, grand total; HotWax adds `bill_to_party_id`, `presentment_currency_uom`, `auto_approve`, `status_flow_id`, risk level)

`order_item_ship_group`: address (`contact_mech_id`), carrier, shipment method, assigned `facility_id`, `ship_after_date`, `ship_by_date`, `may_split`; HotWax adds `order_facility_id`, carrier account and service.

`order_item`: product, quantity, cancel quantity, unit price, status, `ship_before_date`, `ship_after_date`, `estimated_ship_date`, `promised_datetime`; HotWax adds `ship_group_seq_id`, `return_till_date`, `requested_delivery_date`, `available_to_promise`.

Around them: `order_status` (every status change of the order, an item or a payment), `order_role` (customer, bill-to, ship-to, placing user), `order_contact_mech`, `order_adjustment` (discount, tax, shipping, per order, item or ship group), `order_payment_preference` (several per order), `order_attribute`, `order_item_attribute`, `order_identification` (HotWax: every external id), `order_header_note`, `order_item_group` with `order_item_group_assoc` (lines that ship together), `order_item_assoc`, `order_item_change`, `order_term`, `order_type`, `order_item_type`, `order_adjustment_type`.

Reservation: `order_item_ship_grp_inv_res`, which inventory item is reserved for which line, with `reserve_order_enum_id` (FIFO, LIFO), `quantity_not_available`, `promised_datetime`.

HotWax history: `order_facility_change` (every re-route with reason), `excluded_order_facility` (a facility that rejected a line, dated), `order_inv_promise_history`.

Replaces `nx_orders`, `nx_order_items`, `nx_order_allocations`, `nx_order_rejections`, `nx_routing_log`, `nx_order_approvals`, `nx_parked_orders` (status), `nx_pickup_orders` (ship group with a pickup shipment method), `nx_endless_aisle_orders` (order type), `nx_transfer_orders` (order type `TRANSFER_ORDER` or `inventory_transfer`), `nx_email_parsed_orders` (system messages).

B2B release by ship date maps directly: `order_item.ship_after_date` and `order_item_ship_group.ship_after_date`, with the release lead time in `product_store_setting`.

## 9. Routing (brokering)

From HotWax's `order-routing`: `order_routing_group` (per product store, linked to a `service_job` for its schedule), `order_routing` (filters and sorts in `order_filter_condition`), `order_routing_rule` (assignment single or multi), `order_routing_rule_inv_cond` (inventory conditions and sorts), `order_routing_rule_action` (what to do when a rule finds nothing), `order_routing_run`, `order_routing_batch`.

The brokering query is written fresh for both databases (see the queries agreed on 2026-10-20), on `order_item_ship_group`, `order_item`, `product_facility`, `product_assoc`, `facility_group_member`, `facility_order_count` and `excluded_order_facility`. The running allocation happens in Java, not in SQL session variables.

Replaces `nx_routing_config`, `nx_routing_rules`, `nx_brokering_queue`, `nx_brokering_runs`, `nx_atp_rules`.

## 10. Fulfilment and shipping

`picklist` (+ status history, role), `picklist_bin`, `picklist_item`; `shipment` (+ type, status), `shipment_item`, `shipment_package` (+ content, route segment), `shipment_route_segment` (carrier, tracking, label, costs; HotWax adds tracking url, carrier service), `shipment_box_type`, `carrier_shipment_method`, `carrier_shipment_box_type`, `shipment_method_type`, `item_issuance` (each unit leaving, linked to the reservation, line and shipment), `shipment_receipt` (each unit arriving), `shipment_gateway_config` (+ per carrier), `shipment_cost_estimate`.

Replaces `nx_picklists`, `nx_picklist_items`, `nx_packages`, `nx_shipments`, `nx_shipping_labels`, `nx_manifests`, `nx_manifest_shipments`, `nx_tracking_events` (route segment plus a Nexus tracking event table), `nx_carriers` (parties with role `CARRIER`), `nx_carrier_accounts` (`party_carrier_account`), `nx_carrier_rates`, `nx_box_templates`.

## 11. Returns

`return_header` (+ type, status), `return_item` (+ type, reason, response), `return_adjustment` (+ type), `return_status`, `return_contact_mech`, `return_item_billing`; HotWax adds `return_identification`, `return_type_reason` and reason groups.

Replaces `nx_returns`, `nx_return_items`, `nx_return_reasons`, `nx_credit_memos`.

## 12. Accounting (OMS scope)

`invoice` (+ item, status, role, contact mech), `payment` (+ application), `payment_method` (+ type), `payment_gateway_response`, `billing_account`, `fin_account` (gift cards, store credit). The general ledger stays in the client's ERP.

Replaces `nx_invoices`, `nx_invoice_items`, `nx_payments`, `nx_billing_statements` (Nexus's own billing to clients moves out of the client database).

## 13. Nexus additions (same conventions)

Kept because no OFBiz or HotWax entity covers them, renamed to the conventions above:
- Store integrations: `nexus_store_connection` (one per product store and platform), `nexus_store_location_map`, inventory publish runs and lines.
- AI: model registry, inference and cost logs, prompts.
- Warehouse operations: waves, picker assignments, slotting, labour, dock doors, yard.
- Procurement and replenishment: purchase orders use OFBiz order type `PURCHASE_ORDER`; requirements use `requirement`.

## 14. Migration plan

Nexus has no production clients, so the new schema starts from a clean baseline instead of transforming the old tables. Demo data is reloaded from seed files and store imports.

1. **Foundations.** Framework entities, id sequence, both databases in the build and tests, entity-change listener for history, seed data for types, statuses and enumerations.
2. **Party.** Party, contacts, user accounts and groups; sign-in moves to `user_account`.
3. **Product and store.** Product, associations, identifications, features, categories, prices, product store and settings.
4. **Facility and inventory.** Facility, groups, locations, product facility, inventory items and details, reservations.
5. **Orders.** Order header, ship groups, items, status, roles, adjustments, payments, identifications; Shopify and BigCommerce import write the new model.
6. **Fulfilment and returns.** Picklists, shipments, packages, issuance, receipts, returns.
7. **Routing.** Routing groups, routings, rules, conditions, actions, runs; the new brokering query.
8. **Jobs and messages.** Service jobs and system messages replace ShedLock schedulers and integration queues.
9. **Remove the old tables** and their code.

Each step ends with the app working, tests passing on PostgreSQL and MySQL, and the screens of that area checked in the browser.
