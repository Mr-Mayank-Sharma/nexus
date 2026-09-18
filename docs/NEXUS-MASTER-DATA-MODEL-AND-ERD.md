# Nexus OMS & Supply Chain Platform — Master Data Model & ERD Reference

> **The Definitive Data Architecture & Entity Relationship Catalog for Nexus OMS.**  
> Covers all database tables, logical foreign keys, primary keys, schemas, and Mermaid diagrams across all business subsystems.

---

## Table of Contents
1. [Architecture & Database Overview](#1-architecture--database-overview)
2. [Domain Subsystem Architecture](#2-domain-subsystem-architecture)
3. [Subsystem 1: Core Commerce, Customers & Catalog](#3-subsystem-1-core-commerce-customers--catalog)
4. [Subsystem 2: Orders, Brokering & Routing (DOM)](#4-subsystem-2-orders-brokering--routing-dom)
5. [Subsystem 3: Inventory, ATP & Warehouse Network](#5-subsystem-3-inventory-atp--warehouse-network)
6. [Subsystem 4: Fulfillment, WMS, Waves, Picking & Packing](#6-subsystem-4-fulfillment-wms-waves-picking--packing)
7. [Subsystem 5: Carriers, Rate Shopping, Shipping & Yard Management](#7-subsystem-5-carriers-rate-shopping-shipping--yard-management)
8. [Subsystem 6: Reverse Logistics (Returns & RMA) & Financial Reconciliation](#8-subsystem-6-reverse-logistics-returns--rma--financial-reconciliation)
9. [Subsystem 7: Procurement, Suppliers & Inbound ASN](#9-subsystem-7-procurement-suppliers--inbound-asn)
10. [Subsystem 8: 3PL Multi-Client Billing & Rate Cards](#10-subsystem-8-3pl-multi-client-billing--rate-cards)
11. [Subsystem 9: Integration Hub, Marketplaces, EDI & File Ingestion](#11-subsystem-9-integration-hub-marketplaces-edi--file-ingestion)
12. [Subsystem 10: AI Platform, Vector Search & MCP Infrastructure](#12-subsystem-10-ai-platform-vector-search--mcp-infrastructure)

---

## 1. Architecture & Database Overview

The Nexus platform uses **PostgreSQL 16** with the following core architectural standards:
- **Primary Keys**: `UUID` across all tables, generated via Hibernate `GenerationType.UUID` or Postgres `gen_random_uuid()`.
- **Multi-Tenancy Isolation**: Every tenant-scoped entity carries a `tenant_id` (`varchar(64)` or `UUID`), enforced via Spring Security tenant context filters and PostgreSQL Row-Level Security (RLS) policies.
- **Audit Columns**: Standard audit columns `created_at` (`timestamp with time zone`), `updated_at`, and `created_by` / `updated_by`.
- **Flexible Payloads**: Structured business data resides in strict columns; flexible configurations and raw partner payloads utilize PostgreSQL `jsonb` columns.
- **AI Vector Search**: Embeddings stored using the `pgvector` extension (`vector(1536)` / `vector(768)`).

---

## 2. Domain Subsystem Architecture

```
┌────────────────────────────────────────────────────────────────────────────────────────┐
│                                 NEXUS DATA ARCHITECTURE                                │
├─────────────────────────┬───────────────────────────────┬──────────────────────────────┤
│ 🛒 Commerce & Orders    │ 📦 Inventory, Nodes & ATP     │ 🚚 WMS, Waves & Fulfillment  │
│ • nx_customers          │ • nx_inventory                │ • nx_waves                   │
│ • nx_orders             │ • nx_warehouses               │ • nx_picklists & items       │
│ • nx_order_items        │ • nx_warehouse_bins           │ • nx_packages                │
│ • nx_order_allocations  │ • nx_atp_snapshots            │ • nx_shipments               │
│ • nx_pickup_orders      │ • nx_transfer_orders          │ • nx_shipping_labels         │
├─────────────────────────┼───────────────────────────────┼──────────────────────────────┤
│ 🚢 Carriers & Yard      │ 🔄 Returns & Finance          │ 🔌 Hub, EDI & AI Platform    │
│ • nx_carriers & rates   │ • nx_returns & items          │ • nx_integration_stores      │
│ • nx_dock_doors         │ • nx_invoices & payments      │ • nx_edi_documents           │
│ • nx_appointments       │ • nx_credit_memos             │ • ai_models & deployments    │
│ • nx_trailers & events  │ • nx_billing_statements (3PL) │ • nx_mcp_tools               │
└─────────────────────────┴───────────────────────────────┴──────────────────────────────┘
```

---

## 3. Subsystem 1: Core Commerce, Customers & Catalog

### Mermaid ER Diagram

```mermaid
erDiagram
    NX_CUSTOMERS ||--o{ NX_ORDERS : places
    NX_CUSTOMERS ||--o{ NX_ADDRESSES : has_address
    NX_CUSTOMERS ||--o{ NX_CONTACTS : has_contact
    PRODUCTS ||--o{ NX_PRODUCT_MAPPINGS : channel_sku_mapped
    NX_ORDERS ||--o{ NX_PROMOTION_USAGE : uses_promo
    NX_PROMOTIONS ||--o{ NX_PROMOTION_USAGE : recorded_in

    NX_CUSTOMERS {
        UUID id PK
        string tenant_id
        string customer_code UK
        string first_name
        string last_name
        string email UK
        string phone
        string company_name
        string customer_type "B2C|B2B"
        timestamp created_at
    }

    NX_ADDRESSES {
        UUID id PK
        string tenant_id
        UUID customer_id FK
        string address_type "BILLING|SHIPPING"
        string street1
        string street2
        string city
        string state_province
        string postal_code
        string country_code
        boolean is_default
    }

    PRODUCTS {
        UUID id PK
        string tenant_id
        string sku UK
        string upc
        string name
        string description
        string category
        decimal unit_price
        decimal cost_price
        decimal weight_kg
        decimal length_cm
        decimal width_cm
        decimal height_cm
        boolean is_active
    }

    NX_PRODUCT_MAPPINGS {
        UUID id PK
        string tenant_id
        UUID internal_product_id FK
        string channel_type "SHOPIFY|AMAZON|WALMART|EBAY"
        string channel_sku
        string channel_product_id
    }
```

---

## 4. Subsystem 2: Orders, Brokering & Routing (DOM)

### Mermaid ER Diagram

```mermaid
erDiagram
    NX_ORDERS ||--|{ NX_ORDER_ITEMS : contains
    NX_ORDERS ||--o{ NX_ORDER_ALLOCATIONS : splits_and_allocates
    NX_ORDERS ||--o{ NX_ORDER_APPROVALS : approval_workflow
    NX_ORDERS ||--o{ NX_ORDER_REJECTIONS : rejection_history
    NX_ORDERS ||--o{ NX_PARKED_ORDERS : parked_reason
    NX_ORDERS ||--o{ NX_BROKERING_QUEUE : routes_through
    NX_ORDERS ||--o{ NX_PICKUP_ORDERS : bopis_fulfillment
    NX_ORDERS ||--o{ NX_ENDLESS_AISLE_ORDERS : store_fulfillment

    NX_ORDERS {
        UUID id PK
        string tenant_id
        string order_number UK
        UUID customer_id FK
        string channel "SHOPIFY|AMAZON|MANUAL|EDI|B2B"
        string status "PENDING|APPROVED|ALLOCATED|PICKING|SHIPPED|DELIVERED|CANCELLED|PARKED"
        string fulfillment_type "SHIP|BOPIS|PICKUP|TRANSFER"
        decimal subtotal
        decimal discount_amount
        decimal tax_amount
        decimal shipping_amount
        decimal total_amount
        string currency
        timestamp order_date
        jsonb shipping_address
        jsonb billing_address
    }

    NX_ORDER_ITEMS {
        UUID id PK
        string tenant_id
        UUID order_id FK
        UUID product_id FK
        string sku
        string product_name
        int quantity
        decimal unit_price
        decimal total_price
        string status "PENDING|ALLOCATED|FULFILLED|CANCELLED"
    }

    NX_ORDER_ALLOCATIONS {
        UUID id PK
        string tenant_id
        UUID order_id FK
        UUID order_item_id FK
        UUID warehouse_id FK
        int allocated_quantity
        string status "ALLOCATED|FULFILLED|RELEASED"
        timestamp allocated_at
    }

    NX_BROKERING_QUEUE {
        UUID id PK
        string tenant_id
        UUID order_id FK
        int priority
        string routing_strategy "LEAST_COST|FASTEST_DELIVERY|LEAST_SPLIT|PROXIMITY"
        int attempt_count
        string status "QUEUED|PROCESSING|ROUTED|FAILED"
    }

    NX_PICKUP_ORDERS {
        UUID id PK
        string tenant_id
        UUID order_id FK
        UUID store_node_id FK
        string pickup_status "READY_FOR_PICK|PICKED|READY_FOR_HANDOFF|COLLECTED|CANCELLED"
        string customer_name
        string customer_phone
        timestamp ready_at
        timestamp collected_at
    }
```

---

## 5. Subsystem 3: Inventory, ATP & Warehouse Network

### Mermaid ER Diagram

```mermaid
erDiagram
    NX_WAREHOUSES ||--o{ NX_WAREHOUSE_ZONES : partitioned_into
    NX_WAREHOUSE_ZONES ||--o{ NX_WAREHOUSE_BINS : contains
    NX_WAREHOUSE_BINS ||--o{ NX_INVENTORY : holds_stock
    PRODUCTS ||--o{ NX_INVENTORY : physical_units
    PRODUCTS ||--o{ NX_ATP_SNAPSHOTS : calculated_atp
    NX_WAREHOUSES ||--o{ NX_TRANSFER_ORDERS : source_transfer
    NX_WAREHOUSES ||--o{ NX_TRANSFER_ORDERS : destination_transfer
    NX_TRANSFER_ORDERS ||--|{ NX_TRANSFER_ORDER_ITEMS : transfer_lines

    NX_WAREHOUSES {
        UUID id PK
        string tenant_id
        string code UK
        string name
        string type "FULFILLMENT_CENTER|RETAIL_STORE|3PL_FACILITY"
        string address
        decimal latitude
        decimal longitude
        boolean active
    }

    NX_WAREHOUSE_BINS {
        UUID id PK
        string tenant_id
        UUID warehouse_id FK
        UUID zone_id FK
        string bin_code UK
        string aisle
        string rack
        string shelf
        string bin_type "PICKING|STORAGE|RECEIVING|STAGING|RETURNS"
        decimal max_weight_kg
        decimal max_volume_m3
    }

    NX_INVENTORY {
        UUID id PK
        string tenant_id
        UUID warehouse_id FK
        UUID bin_id FK
        UUID product_id FK
        string sku
        int quantity_on_hand
        int quantity_allocated
        int quantity_reserved
        int quantity_available
        int safety_stock
        string lot_number
        timestamp expiry_date
    }

    NX_ATP_SNAPSHOTS {
        UUID id PK
        string tenant_id
        UUID product_id FK
        UUID node_id FK
        int total_on_hand
        int total_allocated
        int total_reserved
        int total_safety_stock
        int atp_quantity
        timestamp calculated_at
    }
```

---

## 6. Subsystem 4: Fulfillment, WMS, Waves, Picking & Packing

### Mermaid ER Diagram

```mermaid
erDiagram
    NX_WAVES ||--o{ NX_PICKLISTS : dispatches
    NX_PICKLISTS ||--|{ NX_PICKLIST_ITEMS : item_lines
    NX_PICKERS ||--o{ NX_PICKER_ASSIGNMENTS : assigned_to
    NX_PICKLISTS ||--o{ NX_PACKAGES : packed_as
    NX_BOX_TEMPLATES ||--o{ NX_PACKAGES : uses_box
    NX_PACKAGES ||--o{ NX_SHIPMENTS : shipped_in
    NX_SHIPMENTS ||--o{ NX_SHIPPING_LABELS : prints

    NX_WAVES {
        UUID id PK
        string tenant_id
        string wave_number UK
        UUID warehouse_id FK
        string wave_strategy "BATCH|ZONE|CLUSTER|SINGLE_ORDER"
        string status "PLANNED|RELEASED|IN_PROGRESS|COMPLETED"
        int total_orders
        int total_items
        timestamp created_at
    }

    NX_PICKLISTS {
        UUID id PK
        string tenant_id
        UUID wave_id FK
        UUID assigned_picker_id FK
        string picklist_number UK
        string status "ASSIGNED|PICKING|PICKED|EXCEPTION"
        timestamp started_at
        timestamp completed_at
    }

    NX_PICKLIST_ITEMS {
        UUID id PK
        string tenant_id
        UUID picklist_id FK
        UUID order_item_id FK
        UUID product_id FK
        UUID source_bin_id FK
        int quantity_to_pick
        int quantity_picked
        int quantity_short
        string status "PENDING|PICKED|SHORT"
    }

    NX_PACKAGES {
        UUID id PK
        string tenant_id
        string package_number UK
        UUID order_id FK
        UUID box_template_id FK
        decimal weight_kg
        decimal length_cm
        decimal width_cm
        decimal height_cm
        string packed_by
    }

    NX_SHIPMENTS {
        UUID id PK
        string tenant_id
        string shipment_number UK
        string tracking_number UK
        UUID order_id FK
        UUID carrier_id FK
        string carrier_service_code
        decimal shipping_cost
        string status "MANIFESTED|PICKED_UP|IN_TRANSIT|OUT_FOR_DELIVERY|DELIVERED"
        timestamp shipped_at
        timestamp estimated_delivery
    }
```

---

## 7. Subsystem 5: Carriers, Rate Shopping, Shipping & Yard Management

### Mermaid ER Diagram

```mermaid
erDiagram
    NX_CARRIERS ||--o{ NX_CARRIER_ACCOUNTS : credentials
    NX_CARRIERS ||--o{ NX_CARRIER_RATES : rate_tables
    NX_CARRIERS ||--o{ NX_SHIPMENTS : fulfills
    NX_DOCK_DOORS ||--o{ NX_APPOINTMENTS : schedules
    NX_TRAILERS ||--o{ NX_TRAILER_EVENTS : lifecycle_events
    NX_TRAILERS ||--o{ NX_APPOINTMENTS : arrives_for

    NX_CARRIERS {
        UUID id PK
        string tenant_id
        string name
        string carrier_code UK
        string service_level "GROUND|EXPRESS|OVERNIGHT|PRIORITY"
        boolean is_active
    }

    NX_CARRIER_RATES {
        UUID id PK
        string tenant_id
        UUID carrier_id FK
        string origin_zone
        string destination_zone
        decimal base_rate
        decimal per_kg_rate
        int estimated_days
    }

    NX_DOCK_DOORS {
        UUID id PK
        string tenant_id
        UUID warehouse_id FK
        string door_number
        string door_type "INBOUND|OUTBOUND|UNIVERSAL"
        string status "AVAILABLE|OCCUPIED|RESERVED|MAINTENANCE"
    }

    NX_TRAILERS {
        UUID id PK
        string tenant_id
        string trailer_number UK
        string carrier_code
        string trailer_type "DRY_VAN|REEFER|FLATBED"
        string status "IN_YARD|AT_DOCK|CHECKED_OUT"
        UUID current_dock_door_id FK
    }

    NX_APPOINTMENTS {
        UUID id PK
        string tenant_id
        UUID warehouse_id FK
        UUID dock_door_id FK
        UUID trailer_id FK
        string appointment_type "INBOUND_RECEIVING|OUTBOUND_DISPATCH"
        timestamp scheduled_start
        timestamp scheduled_end
        timestamp actual_arrival
        timestamp actual_departure
        string status "REQUESTED|CONFIRMED|CHECKED_IN|COMPLETED|NO_SHOW"
    }
```

---

## 8. Subsystem 6: Reverse Logistics (Returns & RMA) & Financial Reconciliation

### Mermaid ER Diagram

```mermaid
erDiagram
    NX_ORDERS ||--o{ NX_RETURNS : requested_for
    NX_RETURNS ||--|{ NX_RETURN_ITEMS : returned_lines
    NX_RETURNS ||--o{ NX_CREDIT_MEMOS : produces_credit
    NX_INVOICES ||--o{ NX_PAYMENTS : paid_by
    NX_CREDIT_MEMOS ||--o{ NX_CREDIT_MEMO_INVOICE_APPLICATIONS : applied_against
    NX_INVOICES ||--o{ NX_CREDIT_MEMO_INVOICE_APPLICATIONS : reduced_by

    NX_RETURNS {
        UUID id PK
        string tenant_id
        string rma_number UK
        UUID order_id FK
        UUID customer_id FK
        string status "REQUESTED|APPROVED|RECEIVED|INSPECTED|REFUNDED|CANCELLED"
        string return_tracking_number
        decimal estimated_refund
        decimal actual_refund
        timestamp created_at
    }

    NX_RETURN_ITEMS {
        UUID id PK
        string tenant_id
        UUID return_id FK
        UUID order_item_id FK
        UUID product_id FK
        int quantity
        string return_reason "DEFECTIVE|WRONG_ITEM|NOT_AS_DESCRIBED|BUYERS_REMORSE"
        string inspection_grade "GRADE_A_RESTOCK|GRADE_B_OPEN_BOX|GRADE_C_REFURB|GRADE_D_SCRAP"
        string disposition "RESTOCK_INVENTORY|REFURBISH|DONATE|SCRAP"
        UUID restock_bin_id FK
    }

    NX_CREDIT_MEMOS {
        UUID id PK
        string tenant_id
        string memo_number UK
        UUID return_id FK
        UUID customer_id FK
        decimal total_amount
        decimal remaining_balance
        string status "OPEN|PARTIALLY_APPLIED|FULLY_APPLIED|REFUNDED_TO_CARD"
        string payment_gateway_refund_id
    }
```

---

## 9. Subsystem 7: Procurement, Suppliers & Inbound ASN

### Mermaid ER Diagram

```mermaid
erDiagram
    NX_SUPPLIERS ||--o{ NX_PURCHASE_ORDERS : supplies
    NX_PURCHASE_ORDERS ||--|{ NX_PURCHASE_ORDER_ITEMS : po_lines
    NX_PURCHASE_ORDERS ||--o{ NX_ASNS : shipped_via
    NX_ASNS ||--|{ NX_ASN_LINES : asn_manifest_lines
    NX_ASNS ||--o{ NX_INVENTORY_RECEIPTS : received_into_stock

    NX_SUPPLIERS {
        UUID id PK
        string tenant_id
        string supplier_code UK
        string name
        string email
        string phone
        string tax_identifier
        string payment_terms "NET_30|NET_60|PREPAID"
    }

    NX_PURCHASE_ORDERS {
        UUID id PK
        string tenant_id
        string po_number UK
        UUID supplier_id FK
        UUID destination_warehouse_id FK
        string status "DRAFT|SUBMITTED|CONFIRMED|PARTIALLY_RECEIVED|COMPLETED|CANCELLED"
        decimal subtotal
        decimal tax_amount
        decimal total_amount
        timestamp order_date
        timestamp expected_delivery_date
    }

    NX_ASNS {
        UUID id PK
        string tenant_id
        string asn_number UK
        UUID purchase_order_id FK
        UUID supplier_id FK
        UUID destination_warehouse_id FK
        string status "EXPECTED|RECEIVING|COMPLETED"
        timestamp estimated_arrival
        string bol_number "Bill of Lading"
    }

    NX_INVENTORY_RECEIPTS {
        UUID id PK
        string tenant_id
        UUID asn_id FK
        UUID purchase_order_id FK
        UUID product_id FK
        UUID destination_bin_id FK
        int quantity_received
        int quantity_damaged
        string lot_number
        string received_by
        timestamp received_at
    }
```

---

## 10. Subsystem 8: 3PL Multi-Client Billing & Rate Cards

### Mermaid ER Diagram

```mermaid
erDiagram
    NX_RATE_CARDS ||--o{ NX_BILLING_STATEMENTS : calculates_fees
    NX_BILLING_STATEMENTS ||--|{ NX_BILLING_STATEMENT_LINES : itemized_lines

    NX_RATE_CARDS {
        UUID id PK
        string tenant_id
        string name
        UUID client_tenant_id FK
        decimal base_order_fee
        decimal per_unit_pick_fee
        decimal per_unit_pack_fee
        decimal storage_fee_per_pallet_monthly
        decimal return_handling_fee
        boolean is_active
    }

    NX_BILLING_STATEMENTS {
        UUID id PK
        string tenant_id
        string statement_number UK
        UUID client_tenant_id FK
        timestamp billing_period_start
        timestamp billing_period_end
        decimal total_orders_fee
        decimal total_picking_fee
        decimal total_storage_fee
        decimal total_shipping_markup
        decimal total_statement_amount
        string status "DRAFT|ISSUED|PAID|OVERDUE"
    }
```

---

## 11. Subsystem 9: Integration Hub, Marketplaces, EDI & File Ingestion

### Mermaid ER Diagram

```mermaid
erDiagram
    NX_INTEGRATION_STORES ||--o{ NX_SYNC_LOGS : records_activity
    NX_INTEGRATION_STORES ||--o{ NX_SYNC_CONFLICTS : logs_discrepancies
    NX_EDI_PARTNERS ||--o{ NX_EDI_DOCUMENTS : exchanges
    IMPORT_HISTORY ||--o{ IMPORT_RECORD_LOG : row_by_row_status

    NX_INTEGRATION_STORES {
        UUID id PK
        string tenant_id
        string store_name
        string channel_type "SHOPIFY|BIGCOMMERCE|AMAZON|EBAY|WALMART"
        string api_url
        string auth_token_encrypted
        boolean auto_sync_inventory
        boolean auto_sync_orders
    }

    NX_EDI_DOCUMENTS {
        UUID id PK
        string tenant_id
        UUID partner_id FK
        string document_type "850_PO|856_ASN|810_INVOICE|997_ACK"
        string direction "INBOUND|OUTBOUND"
        string control_number UK
        string raw_payload
        string status "RECEIVED|PARSED|FAILED|TRANSMITTED"
    }

    IMPORT_HISTORY {
        UUID id PK
        string tenant_id
        string file_name
        string entity_type "PRODUCTS|ORDERS|INVENTORY|CUSTOMERS"
        string file_format "CSV|JSON|XML|EDI|XLSX"
        int total_rows
        int success_count
        int error_count
        string status "PROCESSING|COMPLETED|FAILED"
        int processing_time_ms
    }
```

---

## 12. Subsystem 10: AI Platform, Vector Search & MCP Infrastructure

### Mermaid ER Diagram

```mermaid
erDiagram
    AI_MODELS ||--o{ AI_MODEL_VERSIONS : releases
    AI_MODELS ||--o{ AI_DEPLOYMENTS : deployed_as
    AI_DATASETS ||--o{ AI_TRAINING_JOBS : trains
    AI_KNOWLEDGE_BASES ||--o{ AI_KNOWLEDGE_DOCUMENTS : indexes_vectors
    NX_MCP_TOOLS ||--o{ NX_MCP_AGENT_BUDGETS : limits_usage

    AI_MODELS {
        UUID id PK
        string tenant_id
        string model_name UK
        string model_type "DEMAND_FORECAST|ORDER_ROUTING|FRAUD_SCORING|BOX_RECOMMENDER"
        string framework "ONNX|PYTORCH|SCIKIT_LEARN"
        string status "ACTIVE|DEPRECATED"
    }

    AI_KNOWLEDGE_DOCUMENTS {
        UUID id PK
        string tenant_id
        UUID knowledge_base_id FK
        string document_title
        string text_content
        string vector_embedding "vector(1536) / pgvector index"
        jsonb metadata
    }

    NX_MCP_TOOLS {
        UUID id PK
        string tenant_id
        string tool_name UK
        string description
        string http_endpoint
        string http_method "GET|POST"
        jsonb input_schema
        boolean is_autonomous_allowed
    }
```
