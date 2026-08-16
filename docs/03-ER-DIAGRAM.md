# Nexus OMS — Entity Relationship Diagram (ER)

> The authoritative catalog of the ~157 JPA entities. Diagrams use **Mermaid** (`erDiagram`). Relationships are **logical FKs** — most associations are plain `UUID` columns (e.g. `customerId`, `warehouseId`, `tenantId`) rather than JPA object graphs (only ~6 JPA associations exist).

---

## 0. Start Here — What is an "ER diagram"? 🧩

Imagine your school's **storage room**. It has:
- **Shelves** — each shelf holds ONE kind of thing. We call each shelf a **TABLE** (like `nx_orders` = the "orders shelf").
- **Boxes on each shelf** — each box is one **ROW** (like ONE specific order for one customer).
- **Name tags** — each box has a **PRIMARY KEY (PK)**, a unique ID (like a locker number).
- **Cross-reference cards** — a card on the order box says *"customer #77"*. That's a **FOREIGN KEY (FK)** — a reference to a box on another shelf (the customers shelf).

An **ER diagram** is just the **floor plan** of the storage room: which shelf connects to which shelf, and how.

> 🧒 **Kid translation:**
> - **Table** = a drawer (one type of thing).
> - **Row** = one thing in the drawer.
> - **PK** = the thing's unique tag.
> - **FK** = "this belongs to that" (e.g. *this order belongs to customer #77*).
> - **tenant_id** = "which school this belongs to" (multi-tenant safety).

### Real-life example — one order touches many shelves
> 🛍️ **Mila orders 2 blue hoodies:**
> - `nx_orders` gets a row: "Order #1001, customer Mila" (FK: customer_id)
> - `nx_order_items` gets 1 row: "hoodie, qty 2" (FK: order_id, product_id)
> - `nx_inventory` loses 2 hoodies (FK: product_id, node_id)
> - `nx_waves`/`nx_picklists` get rows when it's time to pick
> - `nx_shipments` gets a row when it ships (FK: carrier_id)
> - `nx_invoices` gets a row for payment (FK: order_id)
>
> The ER diagram is the map that shows ALL these shelves and their links.

### Reading Guide
- `PK` — `UUID` (Hibernate `GenerationType.UUID`) unless noted.
- `tenant_id` — multi-tenancy scope column (present on virtually every table).
- Timestamps `LocalDateTime`; money `BigDecimal`; flexible payloads `jsonb`.
- Three tables use legacy casing `nxFreight_*` (kept; **do not rename without a migration**).
- Naming: `nx_<domain>`, `ai_*` (AI), `import_*` (import/export).

---

## 1. Domain Catalog (all ~157 tables)

### Core commerce & customers
`nx_customers` · `nx_addresses` · `nx_contacts` · `products` · `nx_product_mappings` · `nx_promotions` · `nx_promotion_usage` · `nx_payments` · `nx_invoices` · `nx_invoice_items` · `nx_credit_memos`

### Orders & order lifecycle
`nx_orders` · `nx_order_items` · `nx_order_allocations` · `nx_order_approvals` · `nx_order_rejections` · `nx_rejection_reasons` · `nx_parked_orders` · `nx_brokering_queue` · `nx_brokering_runs` · `nx_routing_config` · `nx_routing_rules` · `nx_routing_log` · `nx_endless_aisle_orders` · `nx_pickup_orders` · `nx_pickup_order_items`

### Inventory & network
`nx_inventory` · `nx_inventory_receipts` · `nx_nodes` · `nx_warehouses` · `nx_warehouse_zones` · `nx_warehouse_bins` · `nx_warehouse_equipment` · `nx_warehouse_staff` · `nx_cycle_counts` · `nx_atp_rules` · `nx_atp_snapshots` · `nx_transfer_orders` · `nx_transfer_order_items` · `nx_replenishment_rules` · `nx_replenishment_suggestions`

### Fulfillment (wave → pick → pack → ship)
`nx_waves` · `nx_wave_rules` · `nx_picklists` · `nx_picklist_items` · `nx_pickers` · `nx_picker_assignments` · `nx_packages` · `nx_shipments` · `nx_shipping_labels` · `nx_manifests` · `nx_manifest_shipments` · `nx_tracking_events` · `nx_proof_of_delivery` · `nx_fulfillment_exceptions` · `nx_fulfillment_limits` · `nx_fulfillment_capacity_log`

### Slotting, engineering & labor
`nx_slotting_rules` · `nx_slotting_assignments` · `nx_slotting_audits` · `nx_engineered_standards` · `nx_workload_rules` · `nx_labor_entries` · `nx_productivity_log` · `nx_shift_schedules`

### Shipping, carriers & yard
`nx_carriers` · `nx_carrier_accounts` · `nx_carrier_rates` · `nx_carrier_zones` · `nx_rate_shopping_log` · `nx_trailers` · `nx_trailer_events` · `nx_yard_locations` · `nx_dock_doors` · `nx_appointments` · `nxFreight_invoices` · `nxFreight_invoice_lines` · `nxFreight_audit_logs`

### Returns (RMA)
`nx_returns` · `nx_return_items`

### Procurement & suppliers
`nx_purchase_requests` · `nx_purchase_request_items` · `nx_purchase_orders` · `nx_purchase_order_items` · `nx_rfqs` · `nx_rfq_responses` · `nx_suppliers` · `nx_supplier_contacts` · `nx_supplier_contracts` · `nx_approval_rules` · `nx_order_approvals`

### Automation & alerting
`nx_automation_systems` · `nx_automation_commands` · `nx_automation_logs` · `nx_automation_alerts` · `nx_alert_rules`

### Integrations hub
`nx_integration_stores` · `nx_integration_store_settings` · `nx_integration_flows` · `nx_integration_flow_steps` · `nx_integration_messages` · `nx_integration_endpoints` · `nx_integration_import_jobs` · `nx_integration_export_jobs` · `nx_integration_sync_configs` · `nx_integration_dlq` · `nx_integration_cdc_events` · `nx_integration_audit_log` · `nx_integration_transform_mappings` · `nx_integration_validation_rules` · `nx_sync_logs` · `nx_shopify_webhooks` · `nx_bigcommerce_config` · `nx_bigcommerce_webhooks` · `nx_email_ingestion_config` · `nx_email_parsed_orders`

### EDI & bulk import/export
`nx_edi_partners` · `nx_edi_documents` · `import_history` · `import_record_log`

### AI platform
`ai_models` · `ai_model_versions` · `ai_model_metrics` · `ai_training_jobs` · `ai_datasets` · `ai_feature_definitions` · `ai_feature_values` · `ai_experiments` · `ai_deployments` · `ai_gateway_routes` · `ai_inference_logs` · `ai_prompts` · `ai_knowledge_bases` · `ai_knowledge_documents` · `ai_rule_fallbacks` · `ai_calibrations` · `ai_cost_logs` · `ai_compute_resources`

### Identity, RBAC & tenancy
`nx_users` · `nx_user_roles` · `nx_role_permissions` · `nx_teams` · `nx_company_settings` · `nx_audit_log`

### Platform & workflow
`nx_workflows` · `nx_workflow_steps` · `nx_workflow_executions` · `nx_notification_templates` · `nx_notification_logs` · `nx_documents` · `nx_document_versions`

---

## 2. Core Commerce ER

```mermaid
erDiagram
    NX_CUSTOMERS ||--o{ NX_ORDERS : places
    NX_CUSTOMERS ||--o{ NX_ADDRESSES : has
    NX_ORDERS ||--o{ NX_ORDER_ITEMS : contains
    NX_ORDERS ||--o{ NX_ORDER_ALLOCATIONS : allocates
    NX_ORDERS ||--o{ NX_PAYMENTS : paid_by
    NX_ORDERS ||--o{ NX_INVOICES : billed_by
    NX_ORDERS ||--o{ NX_ORDER_APPROVALS : approves
    NX_ORDERS ||--o{ NX_ORDER_REJECTIONS : may_reject
    NX_INVOICES ||--o{ NX_INVOICE_ITEMS : contains
    NX_PROMOTIONS ||--o{ NX_PROMOTION_USAGE : consumed_by
    PRODUCTS ||--o{ NX_ORDER_ITEMS : line_items
    PRODUCTS ||--o{ NX_PRODUCT_MAPPINGS : mapped_to_channels

    NX_CUSTOMERS {
        uuid id PK
        uuid tenant_id
        string email
        string status
    }
    NX_ORDERS {
        uuid id PK
        uuid tenant_id
        string external_id
        string channel
        string channel_order_id
        uuid customer_id FK
        string status
        string sub_status
        string fulfillment_type
        string ship_from
        uuid ship_to_address_id FK
    }
    NX_ORDER_ITEMS {
        uuid id PK
        uuid order_id FK
        uuid product_id FK
        int quantity
        money unit_price
        string status
    }
    NX_ORDER_ALLOCATIONS {
        uuid id PK
        uuid order_id FK
        uuid inventory_id FK
        int qty_allocated
    }
```

> 🍩 **Real life example — the doughnut shop:**
> - `nx_customers` = the "who" (Mia, Leo…)
> - `nx_orders` = "Mia wants 6 glazed + 2 chocolate"
> - `nx_order_items` = one row per flavor with quantity
> - `nx_order_allocations` = "reserve 6 glazed from Shelf B"
> - `nx_invoices` = the receipt
> - `nx_payments` = the coins handed over
> - `nx_promotions` = the "buy 5 get 1 free" sign; `nx_promotion_usage` = how many times it was used

**Key columns (logical FKs):** `NxOrder.customerId`, `NxOrder.shipToAddressId` (JPA `@ManyToOne` → `Address`), `NxOrderItem.orderId`, `NxOrderItem.productId`, `NxOrderAllocation.orderId` + `inventoryId`, `NxInvoice.orderId`.

---

## 3. Inventory & Network ER

```mermaid
erDiagram
    NX_WAREHOUSES ||--o{ NX_WAREHOUSE_ZONES : contains
    NX_WAREHOUSE_ZONES ||--o{ NX_WAREHOUSE_BINS : contains
    NX_NODES ||--o{ NX_INVENTORY : holds
    NX_WAREHOUSES ||--o{ NX_NODES : may_be_nodes
    PRODUCTS ||--o{ NX_INVENTORY : stocked_as
    NX_ORDERS ||--o{ NX_TRANSFER_ORDERS : triggers
    NX_TRANSFER_ORDERS ||--o{ NX_TRANSFER_ORDER_ITEMS : contains
    NX_ATP_RULES ||--o{ NX_ATP_SNAPSHOTS : evaluated
    NX_WAREHOUSES ||--o{ NX_CYCLE_COUNTS : counted
    NX_REPLENISHMENT_RULES ||--o{ NX_REPLENISHMENT_SUGGESTIONS : suggests

    NX_WAREHOUSES {
        uuid id PK
        uuid tenant_id
        string code
        string type
        string status
    }
    NX_WAREHOUSE_BINS {
        uuid id PK
        uuid zone_id FK
        string code
        string pick_face
        int capacity
    }
    NX_INVENTORY {
        uuid id PK
        uuid tenant_id
        uuid product_id FK
        uuid node_id FK
        uuid bin_id FK
        int on_hand
        int reserved
        int available
        string lot
    }
    NX_TRANSFER_ORDERS {
        uuid id PK
        uuid tenant_id
        uuid from_node_id FK
        uuid to_node_id FK
        string status
    }
```

> 🎒 **Real life analogy — your school bag:**
> - `nx_warehouses` = your bag (holds everything)
> - `nx_warehouse_zones` = the big pockets (books pocket, lunch pocket)
> - `nx_warehouse_bins` = the little zip pockets (this is *exactly* where the pencil lives)
> - `nx_inventory` = "3 pencils, 2 are reserved for tomorrow, 1 available"
> - `nx_transfer_orders` = moving pencils from home bag to school bag
> - `nx_atp_snapshots` = a photo of "what can I promise today"

**Key columns:** `NxInventory.productId`, `NxInventory.nodeId`, `NxInventory.binId`, `NxWarehouseBin.zoneId`, `NxTransferOrder.fromNodeId`/`toNodeId`, `NxTransferOrderItem.transferOrderId`.

---

## 4. Fulfillment ER (Wave → Ship)

```mermaid
erDiagram
    NX_ORDERS ||--o{ NX_WAVES : batched_into
    NX_WAVE_RULES ||--o{ NX_WAVES : generated_by
    NX_WAVES ||--o{ NX_PICKLISTS : produces
    NX_PICKLISTS ||--o{ NX_PICKLIST_ITEMS : contains
    NX_PICKLISTS ||--o{ NX_PICKERS : assigned_to
    NX_PICKERS ||--o{ NX_PICKER_ASSIGNMENTS : covers
    NX_PICKLISTS ||--o{ NX_PACKAGES : packed_into
    NX_PACKAGES ||--o{ NX_SHIPMENTS : ships_via
    NX_SHIPMENTS ||--o{ NX_SHIPPING_LABELS : has
    NX_SHIPMENTS ||--o{ NX_TRACKING_EVENTS : tracked_by
    NX_MANIFESTS ||--o{ NX_MANIFEST_SHIPMENTS : includes
    NX_SHIPMENTS ||--o{ NX_PROOF_OF_DELIVERY : delivered_with

    NX_WAVES {
        uuid id PK
        uuid tenant_id
        string status
        string strategy
        datetime created_at
    }
    NX_PICKLISTS {
        uuid id PK
        uuid wave_id FK
        uuid picker_id FK
        string status
    }
    NX_PICKLIST_ITEMS {
        uuid id PK
        uuid picklist_id FK
        uuid order_item_id FK
        uuid bin_id FK
        int qty
    }
    NX_PACKAGES {
        uuid id PK
        uuid picklist_id FK
        string box_type
        money declared_weight
    }
    NX_SHIPMENTS {
        uuid id PK
        uuid tenant_id
        uuid carrier_id FK
        string tracking_number
        string status
    }
```

> 📚 **Real life analogy — homework baskets:**
> - `nx_wave_rules` = the rule "group all math homework together"
> - `nx_waves` = one basket of grouped homework
> - `nx_picklists` = a checklist for one student "collect these 4 books"
> - `nx_picklist_items` = each item on the checklist with its shelf (bin)
> - `nx_packages` = the bag you carry them in
> - `nx_shipments` = handing the bag to the bus driver; `nx_tracking_events` = the bus stops

**Key columns:** `NxWave` status/strategy; `NxPicklist.waveId`, `NxPicklist.pickerId`; `NxPicklistItem.picklistId`/`orderItemId`/`binId`; `NxPackage.picklistId`; `NxShipment.carrierId`; `NxManifestShipment.manifestId`/`shipmentId`.

---

## 5. Shipping, Carriers & Yard ER

```mermaid
erDiagram
    NX_CARRIERS ||--o{ NX_CARRIER_ACCOUNTS : has_accounts
    NX_CARRIERS ||--o{ NX_CARRIER_RATES : priced_by
    NX_CARRIERS ||--o{ NX_CARRIER_ZONES : serves
    NX_CARRIERS ||--o{ NX_RATE_SHOPPING_LOG : quoted_in
    NX_TRAILERS ||--o{ NX_TRAILER_EVENTS : logged
    NX_YARD_LOCATIONS ||--o{ NX_TRAILERS : parked_at
    NX_WAREHOUSES ||--o{ NX_DOCK_DOORS : has_doors
    NX_WAREHOUSES ||--o{ NX_APPOINTMENTS : schedules

    NX_CARRIERS {
        uuid id PK
        uuid tenant_id
        string name
        string scac_code
    }
    NX_CARRIER_RATES {
        uuid id PK
        uuid carrier_id FK
        string service
        string zone
        money base_rate
    }
    NX_TRAILERS {
        uuid id PK
        uuid tenant_id
        string trailer_number
        string status
        uuid yard_location_id FK
    }
```

> 🚚 **Real life analogy — the delivery yard:**
> - `nx_carriers` = the delivery companies (Blue Truck Co., Fast Van Ltd.)
> - `nx_carrier_rates` = their price lists ("Zone 2 = $8")
> - `nx_rate_shopping_log` = the record of "we compared 3, chose Fast Van = $8"
> - `nx_trailers` = the trailers parked outside; `nx_yard_locations` = parking spots
> - `nx_trailer_events` = "arrived at 8am, moved to dock 3 at 9am"

**Key columns:** `NxCarrierRate.carrierId`, `NxTrailer.yardLocationId`, `NxTrailerEvent.trailerId`, `NxManifestShipment.shipmentId`, `NxFreightInvoiceLine.freightInvoiceId`.

---

## 6. Returns (RMA) ER

```mermaid
erDiagram
    NX_ORDERS ||--o{ NX_RETURNS : returned_from
    NX_RETURNS ||--o{ NX_RETURN_ITEMS : contains
    NX_ORDER_ITEMS ||--o{ NX_RETURN_ITEMS : references
    NX_RETURNS ||--o{ NX_REJECTION_REASONS : rejected_with

    NX_RETURNS {
        uuid id PK
        uuid tenant_id
        uuid order_id FK
        string status
        string rma_number
        string disposition
    }
    NX_RETURN_ITEMS {
        uuid id PK
        uuid return_id FK
        uuid order_item_id FK
        string condition
        money refund_amount
    }
```

> 🎁 **Real life analogy — returning a gift:**
> - `nx_returns` = the return slip ("Grandma's sweater came back")
> - `nx_return_items` = which item, what condition, refund amount
> - `nx_rejection_reasons` = "no, because it's clearly worn" (evidence recorded)

---

## 7. Procurement & Suppliers ER

```mermaid
erDiagram
    NX_SUPPLIERS ||--o{ NX_SUPPLIER_CONTACTS : has
    NX_SUPPLIERS ||--o{ NX_SUPPLIER_CONTRACTS : signed
    NX_PURCHASE_REQUESTS ||--o{ NX_PURCHASE_REQUEST_ITEMS : contains
    NX_PURCHASE_REQUESTS ||--o{ NX_PURCHASE_ORDERS : becomes
    NX_PURCHASE_ORDERS ||--o{ NX_PURCHASE_ORDER_ITEMS : contains
    NX_SUPPLIERS ||--o{ NX_PURCHASE_ORDERS : fulfills
    NX_RFQS ||--o{ NX_RFQ_RESPONSES : receives_bids
    NX_APPROVAL_RULES ||--o{ NX_ORDER_APPROVALS : drives

    NX_PURCHASE_ORDERS {
        uuid id PK
        uuid tenant_id
        uuid supplier_id FK
        string status
        date expected_date
    }
    NX_PURCHASE_ORDER_ITEMS {
        uuid id PK
        uuid po_id FK
        uuid product_id FK
        int qty
        money unit_cost
    }
```

> 🏗️ **Real life analogy — buying bricks for the clubhouse:**
> - `nx_purchase_requests` = "we need 50 bricks" (the ask)
> - `nx_approval_rules` = "asks over $500 need the boss's OK"
> - `nx_purchase_orders` = the official order form to the brick shop
> - `nx_rfqs` = "how much do 3 shops want for 50 bricks?" + `nx_rfq_responses` = their bids
> - `nx_suppliers` = the brick shop's profile + contacts + contract

---

## 8. Automation & Alerting ER

```mermaid
erDiagram
    NX_AUTOMATION_SYSTEMS ||--o{ NX_AUTOMATION_COMMANDS : receives
    NX_AUTOMATION_COMMANDS ||--o{ NX_AUTOMATION_LOGS : logs
    NX_AUTOMATION_SYSTEMS ||--o{ NX_AUTOMATION_ALERTS : raises
    NX_ALERT_RULES ||--o{ NX_AUTOMATION_ALERTS : triggers
    NX_AUTOMATION_LOGS ||--o{ NX_NOTIFICATION_LOGS : notifies

    NX_AUTOMATION_SYSTEMS {
        uuid id PK
        uuid tenant_id
        string system_type
        string name
        string status
    }
    NX_AUTOMATION_COMMANDS {
        uuid id PK
        uuid system_id FK
        string command_type
        string status
        jsonb payload
        bigint elapsed_ms
        boolean simulated
    }
```

> 🤖 **Real life analogy — the conveyor belt:**
> - `nx_automation_systems` = the conveyor machine
> - `nx_automation_commands` = "start belt, speed 2" (with real time taken + `simulated` flag)
> - `nx_automation_logs` = what happened
> - `nx_alert_rules` + `nx_automation_alerts` = "belt stopped twice in an hour → page the manager"

**Honesty note:** `NxAutomationCommand` carries `elapsed_ms` (real `Duration` since Phase 2.5) and an explicit `simulated` flag.

---

## 9. Integrations Hub ER

```mermaid
erDiagram
    NX_INTEGRATION_STORES ||--o{ NX_INTEGRATION_STORE_SETTINGS : configured
    NX_INTEGRATION_STORES ||--o{ NX_INTEGRATION_MESSAGES : ingests
    NX_INTEGRATION_STORES ||--o{ NX_INTEGRATION_IMPORT_JOBS : imports
    NX_INTEGRATION_STORES ||--o{ NX_INTEGRATION_EXPORT_JOBS : exports
    NX_INTEGRATION_FLOWS ||--o{ NX_INTEGRATION_FLOW_STEPS : composed_of
    NX_INTEGRATION_STORES ||--o{ NX_INTEGRATION_SYNC_CONFIGS : syncs
    NX_INTEGRATION_MESSAGES ||--o{ NX_INTEGRATION_DLQ : deadletters
    NX_INTEGRATION_STORES ||--o{ NX_SHOPIFY_WEBHOOKS : receives
    NX_INTEGRATION_STORES ||--o{ NX_BIGCOMMERCE_WEBHOOKS : receives

    NX_INTEGRATION_STORES {
        uuid id PK
        uuid tenant_id
        string channel
        string platform
        string store_name
        string status
    }
    NX_INTEGRATION_MESSAGES {
        uuid id PK
        uuid store_id FK
        string direction
        string status
        jsonb payload
    }
    NX_INTEGRATION_FLOW_STEPS {
        uuid id PK
        uuid flow_id FK
        int step_order
        string type
        string config
    }
```

> 🔌 **Real life analogy — the mail room:**
> - `nx_integration_stores` = each mailbox (Shopify box, Amazon box)
> - `nx_integration_messages` = each letter with a stamp (payload)
> - `nx_integration_flow_steps` = the sorting instructions ("open → translate → check → deliver")
> - `nx_integration_dlq` = the "can't read this" bin (never thrown away — quarantined)

---

## 10. AI Platform ER

```mermaid
erDiagram
    AI_MODELS ||--o{ AI_MODEL_VERSIONS : versioned
    AI_MODEL_VERSIONS ||--o{ AI_MODEL_METRICS : measured
    AI_DATASETS ||--o{ AI_TRAINING_JOBS : trains
    AI_MODELS ||--o{ AI_DEPLOYMENTS : deployed
    AI_MODELS ||--o{ AI_GATEWAY_ROUTES : served
    AI_MODELS ||--o{ AI_INFERENCE_LOGS : logged
    AI_EXPERIMENTS ||--o{ AI_MODEL_VERSIONS : compared
    AI_FEATURE_DEFINITIONS ||--o{ AI_FEATURE_VALUES : produces
    AI_KNOWLEDGE_BASES ||--o{ AI_KNOWLEDGE_DOCUMENTS : contains
    AI_RULE_FALLBACKS ||--o{ AI_MODELS : guards
    AI_TRAINING_JOBS ||--o{ AI_COST_LOGS : costs

    AI_TRAINING_JOBS {
        uuid id PK
        uuid tenant_id
        string status
        string metrics_source
        jsonb metrics
        int epochs
        int dataset_size
        bigint duration_seconds
    }
    AI_MODEL_VERSIONS {
        uuid id PK
        uuid model_id FK
        string version
        string status
        uuid training_job_id FK
    }
```

> 🎓 **Real life analogy — robot report card:**
> - `ai_models` = the robot (named "Sales-Guesser")
> - `ai_model_versions` = robot v1, robot v2 (improved)
> - `ai_training_jobs` = a training session; `metrics_source` says **REAL** or **NO_METRICS**
> - `ai_model_metrics` = the report card (accuracy etc. — real only)
> - `ai_inference_logs` = every guess the robot made, logged
> - `ai_rule_fallbacks` = the safety rulebook used if the robot drifts

**Honesty note:** `AiTrainingJob.metricsSource` (`REAL` / `NO_METRICS`) added in migration `V52` — a model version is created only when the job produces real metrics.

---

## 11. Identity, RBAC & Tenancy ER

```mermaid
erDiagram
    NX_TEAMS ||--o{ NX_USERS : belongs_to
    NX_USERS ||--o{ NX_USER_ROLES : assigned
    NX_USER_ROLES ||--o{ NX_ROLE_PERMISSIONS : grants
    NX_USERS ||--o{ NX_AUDIT_LOG : performs
    NX_COMPANY_SETTINGS ||--o{ NX_USERS : configures

    NX_USERS {
        uuid id PK
        uuid tenant_id
        string username
        string email
        string password_hash
        string status
    }
    NX_USER_ROLES {
        uuid id PK
        uuid user_id FK
        string role
    }
    NX_ROLE_PERMISSIONS {
        uuid id PK
        uuid tenant_id
        string role
        string resource
        string action
    }
```

> 🪪 **Real life analogy — school badges:**
> - `nx_users` = every student/teacher (with photo ID)
> - `nx_user_roles` = "Mia = Class Monitor" badge
> - `nx_role_permissions` = the list "Class Monitors may open the supply cupboard, not the staff room"
> - `nx_audit_log` = the visitor logbook ("Mia opened the cupboard at 10:02")

---

## 12. Workflow & Platform ER

```mermaid
erDiagram
    NX_WORKFLOWS ||--o{ NX_WORKFLOW_STEPS : contains
    NX_WORKFLOW_STEPS ||--o{ NX_WORKFLOW_EXECUTIONS : runs
    NX_NOTIFICATION_TEMPLATES ||--o{ NX_NOTIFICATION_LOGS : sent_as
    NX_DOCUMENTS ||--o{ NX_DOCUMENT_VERSIONS : revisioned

    NX_WORKFLOWS {
        uuid id PK
        uuid tenant_id
        string name
        string type
        boolean active
    }
    NX_WORKFLOW_STEPS {
        uuid id PK
        uuid workflow_id FK
        int step_order
        string step_type
        string config
    }
```

> 📋 **Real life analogy — the permission slip process:**
> `nx_workflows` = "Field trip permission" · `nx_workflow_steps` = "1. print form, 2. get signature, 3. collect" · `nx_workflow_executions` = one student's progress through the steps.

---

## 13. Entity Health Notes

| Aspect | Status | Plain-English |
|---|---|---|
| JPA object-graph associations | **Minimal** (6 uses) — most links are UUID columns | Most "links" are just ID tags on boxes |
| `@JoinColumn` insertable/updatable=false | Read-only navigation (e.g. `NxOrder → Address`) | A reference card you can read but not change |
| jsonb columns | Workflow/config/integration payload tables | Flexible pockets for "anything goes" data |
| Tenant scoping | `tenant_id` on all tenant tables; enforced in services | Every box is labelled with its school |
| Schema migrations | 58 Flyway files `V1…V58` (idempotent) | Every drawer change is recorded in history |
| Legacy naming | `nxFreight_*` (3 tables) — requires migration to normalize | Old labels on 3 drawers; renaming needs care |

---

*Next: [`04-USE-CASES.md`](./04-USE-CASES.md) — the complete use-case catalogue, and [`features/`](./features/) for per-module ER + flows.*
