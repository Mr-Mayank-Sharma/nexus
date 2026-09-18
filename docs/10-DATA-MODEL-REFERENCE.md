# Nexus OMS — Data Model Reference (Field-by-Field)

> The exhaustive, field-level catalog of every table in the Nexus OMS database — all **177 tables**, every column, every constraint, plus the data flows that connect them.
>
> **Companion docs:** [03-ER-DIAGRAM.md](./03-ER-DIAGRAM.md) (entity relationships at a glance) · [05-DATA-FLOW.md](./05-DATA-FLOW.md) (how data moves through the system) · [02-TECHNICAL-ARCHITECTURE.md](./02-TECHNICAL-ARCHITECTURE.md) (services & infra)

---

## 🧒 Start Here (the kid-friendly version)

Imagine a giant toy warehouse that sells hoodies online.

- **A "table"** is like a big box of index cards. Each card is one **row** (one order, one hoodie, one warehouse).
- **A "column"** is one thing written on every card — like the order number, the customer's name, or how many hoodies fit in a box.
- **A "primary key" (`id`)** is the unique number on every card so you never mix two orders up.
- **`tenant_id`** is the name of the *store* the card belongs to. Nexus is a multi-tenant system — one database, many stores, and every row knows which store it belongs to.
- **A "foreign key"** is a card that says "see card #123" — e.g., every order item points back to its order.

This document lists **every card box** (table) in the warehouse, **every field** written on the cards, and then walks through the **journeys** the cards take (order → pick → pack → ship → invoice).

---

## 📖 How to Read This Document

Each table is documented as:

```
### table_name — what this table does
- `column` TYPE constraints — what the column means
```

**Type legend**

| Type | Meaning |
|---|---|
| `UUID` | 128-bit globally-unique ID (PostgreSQL `gen_random_uuid()` / `uuid_generate_v4()`) |
| `VARCHAR(n)` | Text up to `n` characters |
| `TEXT` | Unlimited text |
| `INT` / `BIGINT` | Whole number (32-bit / 64-bit) |
| `DECIMAL(p,s)` | Exact number, `p` digits total, `s` after the decimal (money/weights) |
| `BOOLEAN` | `true` / `false` |
| `TIMESTAMP` | Date + time (UTC) |
| `DATE` | Date only |
| `JSONB` | Flexible JSON document (PostgreSQL binary JSON) |
| `SERIAL` / `BIGSERIAL` | Auto-incrementing integer |

**Constraint legend**

| Constraint | Meaning |
|---|---|
| `PK` | Primary key — uniquely identifies the row |
| `NOT NULL` | The column must always have a value |
| `DEFAULT x` | If you don't provide a value, `x` is used |
| `REFERENCES t(c)` | Foreign key → column `c` of table `t` |
| `ON DELETE CASCADE` | Deleting the parent row deletes this row too |
| `CHECK (...)` | Value must satisfy the rule (usually an enum) |
| `UNIQUE` | No two rows may share this value (table-level `UNIQUE:` lines are listed under the table) |

---

## 🗺️ Domain Catalog (177 tables)

| # | Domain | Tables | What it does |
|---|---|---|---|
| 1 | [AI Platform](#ai-platform) | 22 | ML models, training, inference, RAG, cost tracking |
| 2 | [Import / Freight](#import--freight) | 5 | Bulk import jobs + freight invoice audit |
| 3 | [Core Commerce & Customers](#core-commerce--customers) | 13 | Products, customers, promotions, payments, invoices, billing |
| 4 | [Orders & Order Lifecycle](#orders--order-lifecycle) | 16 | Orders, items, allocations, approvals, routing, brokering |
| 5 | [Inventory & Network](#inventory--network) | 17 | Stock, warehouses, zones, bins, ATP, transfers, replenishment |
| 6 | [Fulfillment](#fulfillment) | 16 | Waves, picklists, pickers, packages, shipments, tracking |
| 7 | [Slotting, Engineering & Labor](#slotting-engineering--labor) | 6 | Slotting rules, engineered standards, labor, productivity |
| 8 | [Shipping, Carriers & Yard](#shipping-carriers--yard) | 13 | Carriers, rate shopping, trailers, dock doors, appointments |
| 9 | [Returns (RMA)](#returns-rma) | 4 | Returns, return items, return reasons, proof of delivery |
| 10 | [Procurement & Suppliers](#procurement--suppliers) | 11 | Purchase requests/orders, RFQs, suppliers, ASNs, approvals |
| 11 | [Automation & Alerting](#automation--alerting) | 5 | Automation systems/commands, alerts |
| 12 | [Integrations Hub](#integrations-hub) | 30 | Connectors, flows, sync, webhooks, email ingestion, DLQ |
| 13 | [EDI & Bulk Import/Export](#edi--bulk-importexport) | 3 | EDI partners/documents, webhook dedup ledger |
| 14 | [Identity, RBAC & Tenancy](#identity-rbac--tenancy) | 6 | Users, roles, permissions, teams, company settings, audit |
| 15 | [Platform & Workflow](#platform--workflow) | 10 | Workflows, notifications, documents, MCP tools |

> **Note on table count:** the ER diagram (`03-ER-DIAGRAM.md`) documents ~157 JPA entities. This reference covers **177 tables** — the delta is tables added since the ER doc was written (e.g., `ai_*` platform tables, `nx_integration_*` hub tables, `nx_slotting_*`, `nx_workflow_*`, `nx_mcp_*`).

---

## 🧱 Conventions & Shared Patterns

Every table in Nexus follows the same house style:

1. **UUID primary keys.** `id UUID PRIMARY KEY DEFAULT gen_random_uuid()` (older tables use `uuid_generate_v4()`). No auto-increment IDs anywhere in the core domain.
2. **Multi-tenancy via `tenant_id`.** Nearly every table carries `tenant_id UUID NOT NULL`. All queries are scoped by tenant. Indexes almost always lead with `tenant_id`.
3. **Logical foreign keys.** Most relationships are plain `UUID` columns (e.g., `order_id UUID REFERENCES nx_orders(id)`) rather than JPA object graphs — only ~6 real JPA associations exist (see ER doc). This keeps the schema flat and join-friendly.
4. **`created_at` / `updated_at`.** Standard audit timestamps on virtually every table (`TIMESTAMP DEFAULT now()`).
5. **Enums as `CHECK` constraints.** Status/type fields use `VARCHAR(n) CHECK (col IN ('A','B',...))` rather than Postgres `ENUM` types — easy to extend, easy to migrate.
6. **Money & weights are `DECIMAL`.** `DECIMAL(20,6)` for prices/amounts, `DECIMAL(10,3)`/`DECIMAL(12,3)` for weights — never `FLOAT`.
7. **Flexible config in `JSONB`.** Connector settings, flow configs, rule payloads, and webhook payloads are stored as `JSONB` documents.
8. **Idempotency keys.** Integration tables carry `(source, external_id)` UNIQUE pairs so replays never double-create records.
9. **Soft state machines.** Status columns (`PENDING → PROCESSING → COMPLETED/FAILED`) with `error_message`/`retry_count` on job-like tables.

### ⚠️ Schema quirks to know

- **`nx_edi_documents` appears twice** in the extracted schema — an older variant (with `doc_type` CHECK `850/856/810` and `parsed_status`) and a newer variant (with `standard`/`sender_id`/`receiver_id`/`direction`). The newer variant is the live one; the older is a legacy definition.
- **`nx_warehouse_equipment` appears twice** — a legacy variant (`id UUID PRIMARY KEY` without default) and the current variant (`gen_random_uuid()`). The current variant is live.
- **`products`** (lowercase, no `nx_` prefix) is the catalog table — a legacy name kept for compatibility.
- **`import_history` / `import_record_log`** are the generic bulk-import tables used by the legacy import pipeline.
- **`webhook_dedup_ledger`** is the idempotency ledger for inbound webhooks.

---

<!-- ============ AUTO-GENERATED FIELD-BY-FIELD CATALOG ============ -->
<!-- Generated by gen_schema_doc.py from Flyway migrations. Do not edit by hand. -->


---

## AI Platform


### ai_models — ML model registry (classification/forecast models)

| Column | Definition |
|---|---|
| `id` | `UUID PRIMARY KEY DEFAULT gen_random_uuid()` |
| `tenant_id` | `UUID` |
| `name` | `VARCHAR(255) NOT NULL` |
| `display_name` | `VARCHAR(255)` |
| `description` | `TEXT` |
| `model_type` | `VARCHAR(50) NOT NULL` |
| `category` | `VARCHAR(30) NOT NULL CHECK (category IN ('GLOBAL', 'TENANT', 'HYBRID'))` |
| `base_model_id` | `UUID REFERENCES ai_models(id)` |
| `status` | `VARCHAR(20) DEFAULT 'DRAFT' CHECK (status IN ('DRAFT','ACTIVE','TRAINING','WARNING','ERROR','DISABLED','ARCHIVED'))` |
| `current_version` | `VARCHAR(50)` |
| `input_schema` | `JSONB` |
| `output_schema` | `JSONB` |
| `config` | `JSONB` |
| `tags` | `TEXT[]` |
| `is_active` | `BOOLEAN DEFAULT true` |
| `created_by` | `VARCHAR(255)` |
| `created_at` | `TIMESTAMP DEFAULT now()` |
| `updated_at` | `TIMESTAMP DEFAULT now()` |

**Indexes:**
- `idx_ai_models_tenant (tenant_id)`
- `idx_ai_models_type (model_type)`
- `idx_ai_models_status (status)`


### ai_model_versions — Versioned snapshots of a model

| Column | Definition |
|---|---|
| `id` | `UUID PRIMARY KEY DEFAULT gen_random_uuid()` |
| `model_id` | `UUID NOT NULL REFERENCES ai_models(id) ON DELETE CASCADE` |
| `version` | `VARCHAR(50) NOT NULL` |
| `model_file_url` | `TEXT` |
| `model_size_bytes` | `BIGINT` |
| `framework` | `VARCHAR(100)` |
| `framework_version` | `VARCHAR(50)` |
| `accuracy` | `DECIMAL(5,2)` |
| `precision` | `DECIMAL(5,2)` |
| `recall` | `DECIMAL(5,2)` |
| `f1_score` | `DECIMAL(5,2)` |
| `latency_ms` | `DECIMAL(10,2)` |
| `training_dataset_id` | `UUID` |
| `validation_dataset_id` | `UUID` |
| `test_dataset_id` | `UUID` |
| `training_job_id` | `UUID` |
| `metrics` | `JSONB` |
| `parameters` | `JSONB` |
| `commit_message` | `TEXT` |
| `status` | `VARCHAR(20) DEFAULT 'STAGED' CHECK (status IN ('STAGED','VALIDATING','VALIDATED','DEPLOYED','ROLLED_BACK','FAILED','ARCHIVED'))` |
| `validated_by` | `VARCHAR(255)` |
| `validated_at` | `TIMESTAMP` |
| `deployed_by` | `VARCHAR(255)` |
| `deployed_at` | `TIMESTAMP` |
| `created_by` | `VARCHAR(255)` |
| `created_at` | `TIMESTAMP DEFAULT now()` |

**Table-level constraints:**
- `UNIQUE(model_id, version)`

**Indexes:**
- `idx_ai_model_versions_model (model_id)`
- `idx_ai_model_versions_status (status)`


### ai_training_jobs — Training job runs

| Column | Definition |
|---|---|
| `id` | `UUID PRIMARY KEY DEFAULT gen_random_uuid()` |
| `tenant_id` | `UUID` |
| `model_id` | `UUID NOT NULL REFERENCES ai_models(id) ON DELETE CASCADE` |
| `name` | `VARCHAR(255)` |
| `version` | `VARCHAR(50)` |
| `status` | `VARCHAR(20) DEFAULT 'PENDING' CHECK (status IN ('PENDING','RUNNING','VALIDATING','COMPLETED','FAILED','ROLLED_BACK','CANCELLED'))` |
| `job_type` | `VARCHAR(30) DEFAULT 'SCHEDULED' CHECK (job_type IN ('MANUAL','SCHEDULED','TRIGGERED','BACKFILL'))` |
| `trigger_reason` | `TEXT` |
| `config` | `JSONB` |
| `hyperparameters` | `JSONB` |
| `training_dataset_id` | `UUID` |
| `validation_dataset_id` | `UUID` |
| `accuracy` | `DECIMAL(5,2)` |
| `precision` | `DECIMAL(5,2)` |
| `recall` | `DECIMAL(5,2)` |
| `f1_score` | `DECIMAL(5,2)` |
| `loss` | `DECIMAL(10,6)` |
| `drift_score` | `DECIMAL(5,2)` |
| `epochs` | `INT` |
| `dataset_size` | `INT` |
| `duration_seconds` | `INT` |
| `model_size_bytes` | `BIGINT` |
| `error_message` | `TEXT` |
| `logs` | `TEXT` |
| `started_at` | `TIMESTAMP` |
| `completed_at` | `TIMESTAMP` |
| `created_by` | `VARCHAR(255)` |
| `created_at` | `TIMESTAMP DEFAULT now()` |
| `updated_at` | `TIMESTAMP DEFAULT now()` |

**Indexes:**
- `idx_ai_training_model (model_id)`
- `idx_ai_training_status (status)`
- `idx_ai_training_tenant (tenant_id)`


### ai_datasets — Datasets used for training/eval

| Column | Definition |
|---|---|
| `id` | `UUID PRIMARY KEY DEFAULT gen_random_uuid()` |
| `tenant_id` | `UUID` |
| `name` | `VARCHAR(255) NOT NULL` |
| `description` | `TEXT` |
| `dataset_type` | `VARCHAR(30) CHECK (dataset_type IN ('TRAINING','VALIDATION','TEST','EVALUATION','BACKFILL'))` |
| `source_query` | `TEXT` |
| `record_count` | `INT` |
| `size_bytes` | `BIGINT` |
| `storage_url` | `TEXT` |
| `schema_def` | `JSONB` |
| `statistics` | `JSONB` |
| `status` | `VARCHAR(20) DEFAULT 'PENDING' CHECK (status IN ('PENDING','GENERATING','READY','FAILED','ARCHIVED'))` |
| `is_active` | `BOOLEAN DEFAULT true` |
| `created_by` | `VARCHAR(255)` |
| `created_at` | `TIMESTAMP DEFAULT now()` |
| `updated_at` | `TIMESTAMP DEFAULT now()` |

**Indexes:**
- `idx_ai_datasets_tenant (tenant_id)`
- `idx_ai_datasets_type (dataset_type)`


### ai_experiments — ML experiment tracking

| Column | Definition |
|---|---|
| `id` | `UUID PRIMARY KEY DEFAULT gen_random_uuid()` |
| `tenant_id` | `UUID NOT NULL` |
| `model_id` | `UUID NOT NULL REFERENCES ai_models(id) ON DELETE CASCADE` |
| `name` | `VARCHAR(255) NOT NULL` |
| `description` | `TEXT` |
| `experiment_type` | `VARCHAR(30) CHECK (experiment_type IN ('A_B_TEST','CHAMPION_CHALLENGER','MULTIVARIATE','CANARY'))` |
| `champion_version_id` | `UUID REFERENCES ai_model_versions(id)` |
| `challenger_version_id` | `UUID REFERENCES ai_model_versions(id)` |
| `traffic_split` | `DECIMAL(3,2) DEFAULT 0.50` |
| `success_metric` | `VARCHAR(100)` |
| `status` | `VARCHAR(20) DEFAULT 'DRAFT' CHECK (status IN ('DRAFT','RUNNING','COMPLETED','ROLLED_BACK','FAILED'))` |
| `winner_version_id` | `UUID REFERENCES ai_model_versions(id)` |
| `start_date` | `TIMESTAMP` |
| `end_date` | `TIMESTAMP` |
| `results` | `JSONB` |
| `created_by` | `VARCHAR(255)` |
| `created_at` | `TIMESTAMP DEFAULT now()` |
| `updated_at` | `TIMESTAMP DEFAULT now()` |

**Indexes:**
- `idx_ai_experiments_tenant (tenant_id)`


### ai_deployments — Model deployments to serving endpoints

| Column | Definition |
|---|---|
| `id` | `UUID PRIMARY KEY DEFAULT gen_random_uuid()` |
| `tenant_id` | `UUID NOT NULL` |
| `model_id` | `UUID NOT NULL REFERENCES ai_models(id) ON DELETE CASCADE` |
| `version_id` | `UUID NOT NULL REFERENCES ai_model_versions(id) ON DELETE CASCADE` |
| `environment` | `VARCHAR(20) DEFAULT 'PRODUCTION' CHECK (environment IN ('DEVELOPMENT','STAGING','PRODUCTION','CANARY'))` |
| `traffic_weight` | `DECIMAL(3,2) DEFAULT 1.00` |
| `endpoint_url` | `TEXT` |
| `config_overrides` | `JSONB` |
| `status` | `VARCHAR(20) DEFAULT 'PENDING' CHECK (status IN ('PENDING','DEPLOYING','ACTIVE','FAILED','ROLLING_BACK','ROLLED_BACK'))` |
| `deployed_by` | `VARCHAR(255)` |
| `deployed_at` | `TIMESTAMP DEFAULT now()` |
| `created_at` | `TIMESTAMP DEFAULT now()` |
| `updated_at` | `TIMESTAMP DEFAULT now()` |

**Table-level constraints:**
- `UNIQUE(tenant_id, model_id, environment)`

**Indexes:**
- `idx_ai_deployments_tenant (tenant_id)`
- `idx_ai_deployments_active (tenant_id, status)`


### ai_inference_logs — Inference request/response logs

| Column | Definition |
|---|---|
| `id` | `UUID PRIMARY KEY DEFAULT gen_random_uuid()` |
| `tenant_id` | `UUID NOT NULL` |
| `model_id` | `UUID NOT NULL REFERENCES ai_models(id) ON DELETE CASCADE` |
| `version_id` | `UUID REFERENCES ai_model_versions(id)` |
| `deployment_id` | `UUID REFERENCES ai_deployments(id)` |
| `request_id` | `VARCHAR(255)` |
| `input_data` | `JSONB` |
| `output_data` | `JSONB` |
| `confidence` | `DECIMAL(5,2)` |
| `latency_ms` | `DECIMAL(10,2)` |
| `status` | `VARCHAR(20) DEFAULT 'SUCCESS' CHECK (status IN ('SUCCESS','PARTIAL','FAILED','FALLBACK'))` |
| `fallback_used` | `BOOLEAN DEFAULT false` |
| `fallback_reason` | `VARCHAR(50)` |
| `rule_engine_used` | `BOOLEAN DEFAULT false` |
| `user_overridden` | `BOOLEAN DEFAULT false` |
| `user_override_value` | `JSONB` |
| `user_id` | `VARCHAR(255)` |
| `source_service` | `VARCHAR(100)` |
| `cost` | `DECIMAL(10,6)` |
| `tokens_used` | `INT` |
| `created_at` | `TIMESTAMP DEFAULT now()` |

**Indexes:**
- `idx_ai_inference_tenant (tenant_id)`
- `idx_ai_inference_model (model_id)`
- `idx_ai_inference_time (created_at)`
- `idx_ai_inference_status (status)`


### ai_rag_documents — Documents ingested for RAG

| Column | Definition |
|---|---|
| `id` | `UUID PRIMARY KEY DEFAULT gen_random_uuid()` |
| `tenant_id` | `UUID NOT NULL` |
| `source_type` | `VARCHAR(50) NOT NULL` |
| `source_id` | `VARCHAR(100)` |
| `title` | `VARCHAR(500) NOT NULL` |
| `content` | `TEXT NOT NULL` |
| `embedding` | `vector(1536)` |
| `metadata_json` | `JSONB DEFAULT '{}'` |
| `chunk_index` | `INT DEFAULT 0` |
| `token_count` | `INT DEFAULT 0` |
| `created_at` | `TIMESTAMP NOT NULL DEFAULT NOW()` |
| `updated_at` | `TIMESTAMP NOT NULL DEFAULT NOW()` |

**Indexes:**
- `idx_rag_documents_unique_source (tenant_id, source_type, source_id) UNIQUE`
- `idx_rag_documents_tenant (tenant_id)`
- `idx_rag_documents_embedding (embedding vector_cosine_ops)`
- `idx_rag_documents_embedding (embedding vector_cosine_ops)`


### ai_cost_tracking — AI cost/usage tracking

| Column | Definition |
|---|---|
| `id` | `UUID PRIMARY KEY DEFAULT gen_random_uuid()` |
| `tenant_id` | `UUID NOT NULL` |
| `model_name` | `VARCHAR(100) NOT NULL` |
| `operation_type` | `VARCHAR(50) NOT NULL` |
| `input_tokens` | `INT NOT NULL DEFAULT 0` |
| `output_tokens` | `INT NOT NULL DEFAULT 0` |
| `total_tokens` | `INT NOT NULL DEFAULT 0` |
| `cost_usd` | `DECIMAL(12,6) NOT NULL DEFAULT 0` |
| `request_id` | `VARCHAR(100)` |
| `endpoint` | `VARCHAR(200)` |
| `latency_ms` | `INT DEFAULT 0` |
| `cached` | `BOOLEAN DEFAULT FALSE` |
| `created_at` | `TIMESTAMP NOT NULL DEFAULT NOW()` |

**Indexes:**
- `idx_cost_tracking_tenant (tenant_id, created_at DESC)`
- `idx_cost_tracking_model (model_name, created_at DESC)`


### ai_cost_logs — Raw AI cost log entries

| Column | Definition |
|---|---|
| `id` | `UUID PRIMARY KEY DEFAULT gen_random_uuid()` |
| `tenant_id` | `UUID NOT NULL` |
| `model_id` | `UUID NOT NULL REFERENCES ai_models(id) ON DELETE CASCADE` |
| `version_id` | `UUID REFERENCES ai_model_versions(id)` |
| `cost_type` | `VARCHAR(30) CHECK (cost_type IN ('TRAINING','INFERENCE','STORAGE','COMPUTE'))` |
| `amount` | `DECIMAL(12,6) NOT NULL` |
| `currency` | `VARCHAR(3) DEFAULT 'USD'` |
| `tokens_used` | `INT` |
| `compute_hours` | `DECIMAL(10,4)` |
| `recorded_at` | `TIMESTAMP DEFAULT now()` |
| `description` | `TEXT` |

**Indexes:**
- `idx_ai_costs_tenant (tenant_id)`
- `idx_ai_costs_model (model_id)`
- `idx_ai_costs_month (date_trunc('month', recorded_at)`


### ai_calibrations — Model calibration records

| Column | Definition |
|---|---|
| `id` | `UUID PRIMARY KEY DEFAULT gen_random_uuid()` |
| `tenant_id` | `UUID NOT NULL` |
| `model_id` | `UUID NOT NULL REFERENCES ai_models(id) ON DELETE CASCADE` |
| `version_id` | `UUID REFERENCES ai_model_versions(id) ON DELETE CASCADE` |
| `entity_id` | `VARCHAR(255) NOT NULL` |
| `entity_type` | `VARCHAR(50) DEFAULT 'SKU'` |
| `scale_factor` | `DECIMAL(20,6) DEFAULT 1.000000` |
| `bias` | `DECIMAL(20,6) DEFAULT 0.000000` |
| `sample_count` | `INT DEFAULT 0` |
| `weight` | `DECIMAL(20,6) DEFAULT 1.000000` |
| `updated_at` | `TIMESTAMP DEFAULT now()` |
| `created_at` | `TIMESTAMP DEFAULT now()` |

**Table-level constraints:**
- `UNIQUE(tenant_id, model_id, entity_id)`

**Indexes:**
- `idx_ai_calibrations_lookup (tenant_id, model_id, entity_id)`
- `idx_ai_calibrations_version (version_id)`


### ai_prompts — Prompt definitions/versions

| Column | Definition |
|---|---|
| `id` | `UUID PRIMARY KEY DEFAULT gen_random_uuid()` |
| `tenant_id` | `UUID` |
| `name` | `VARCHAR(255) NOT NULL` |
| `description` | `TEXT` |
| `model_id` | `UUID REFERENCES ai_models(id)` |
| `prompt_template` | `TEXT NOT NULL` |
| `variables` | `JSONB` |
| `response_schema` | `JSONB` |
| `temperature` | `DECIMAL(3,2) DEFAULT 0.7` |
| `max_tokens` | `INT DEFAULT 1024` |
| `version` | `INT DEFAULT 1` |
| `status` | `VARCHAR(20) DEFAULT 'ACTIVE' CHECK (status IN ('ACTIVE','DRAFT','DEPRECATED','ARCHIVED'))` |
| `tags` | `TEXT[]` |
| `created_by` | `VARCHAR(255)` |
| `created_at` | `TIMESTAMP DEFAULT now()` |
| `updated_at` | `TIMESTAMP DEFAULT now()` |

**Table-level constraints:**
- `UNIQUE(tenant_id, name, version)`

**Indexes:**
- `idx_ai_prompts_model (model_id)`


### ai_knowledge_bases — RAG knowledge base definitions

| Column | Definition |
|---|---|
| `id` | `UUID PRIMARY KEY DEFAULT gen_random_uuid()` |
| `tenant_id` | `UUID` |
| `name` | `VARCHAR(255) NOT NULL` |
| `description` | `TEXT` |
| `embedding_model` | `VARCHAR(255)` |
| `chunking_strategy` | `VARCHAR(50) DEFAULT 'RECURSIVE'` |
| `chunk_size` | `INT DEFAULT 512` |
| `chunk_overlap` | `INT DEFAULT 64` |
| `status` | `VARCHAR(20) DEFAULT 'ACTIVE' CHECK (status IN ('ACTIVE','INDEXING','FAILED','ARCHIVED'))` |
| `document_count` | `INT DEFAULT 0` |
| `vector_size` | `INT` |
| `config` | `JSONB` |
| `created_by` | `VARCHAR(255)` |
| `created_at` | `TIMESTAMP DEFAULT now()` |
| `updated_at` | `TIMESTAMP DEFAULT now()` |

**Table-level constraints:**
- `UNIQUE(tenant_id, name)`

**Indexes:**
- `idx_ai_kb_tenant (tenant_id)`


### ai_knowledge_documents — Documents in a knowledge base

| Column | Definition |
|---|---|
| `id` | `UUID PRIMARY KEY DEFAULT gen_random_uuid()` |
| `knowledge_base_id` | `UUID NOT NULL REFERENCES ai_knowledge_bases(id) ON DELETE CASCADE` |
| `tenant_id` | `UUID` |
| `title` | `VARCHAR(512)` |
| `content` | `TEXT` |
| `content_type` | `VARCHAR(50)` |
| `source_url` | `TEXT` |
| `file_path` | `TEXT` |
| `file_size_bytes` | `BIGINT` |
| `chunk_count` | `INT` |
| `embedding_status` | `VARCHAR(20) DEFAULT 'PENDING' CHECK (embedding_status IN ('PENDING','PROCESSING','EMBEDDED','FAILED'))` |
| `metadata` | `JSONB` |
| `checksum` | `VARCHAR(64)` |
| `created_by` | `VARCHAR(255)` |
| `created_at` | `TIMESTAMP DEFAULT now()` |
| `updated_at` | `TIMESTAMP DEFAULT now()` |

**Indexes:**
- `idx_ai_kb_docs_base (knowledge_base_id)`
- `idx_ai_kb_docs_status (embedding_status)`


### ai_feature_definitions — ML feature definitions

| Column | Definition |
|---|---|
| `id` | `UUID PRIMARY KEY DEFAULT gen_random_uuid()` |
| `tenant_id` | `UUID` |
| `name` | `VARCHAR(255) NOT NULL` |
| `display_name` | `VARCHAR(255)` |
| `description` | `TEXT` |
| `feature_group` | `VARCHAR(100) NOT NULL` |
| `data_type` | `VARCHAR(50) NOT NULL` |
| `entity_type` | `VARCHAR(100)` |
| `source_type` | `VARCHAR(30) CHECK (source_type IN ('OMS_TABLE','KAFKA_STREAM','EXTERNAL_API','DERIVED','MANUAL'))` |
| `source_config` | `JSONB` |
| `transformation_sql` | `TEXT` |
| `is_categorical` | `BOOLEAN DEFAULT false` |
| `cardinality` | `INT` |
| `default_value` | `VARCHAR(255)` |
| `is_active` | `BOOLEAN DEFAULT true` |
| `version` | `INT DEFAULT 1` |
| `metadata` | `JSONB` |
| `created_by` | `VARCHAR(255)` |
| `created_at` | `TIMESTAMP DEFAULT now()` |
| `updated_at` | `TIMESTAMP DEFAULT now()` |

**Table-level constraints:**
- `UNIQUE(tenant_id, name)`

**Indexes:**
- `idx_ai_features_group (feature_group)`
- `idx_ai_features_entity (entity_type)`


### ai_feature_values — Computed feature values

| Column | Definition |
|---|---|
| `id` | `UUID PRIMARY KEY DEFAULT gen_random_uuid()` |
| `tenant_id` | `UUID NOT NULL` |
| `feature_id` | `UUID NOT NULL REFERENCES ai_feature_definitions(id) ON DELETE CASCADE` |
| `entity_id` | `VARCHAR(255) NOT NULL` |
| `entity_type` | `VARCHAR(100)` |
| `value` | `TEXT` |
| `numeric_value` | `DECIMAL(20,6)` |
| `bool_value` | `BOOLEAN` |
| `json_value` | `JSONB` |
| `timestamp_value` | `TIMESTAMP` |
| `as_of_date` | `DATE NOT NULL` |
| `created_at` | `TIMESTAMP DEFAULT now()` |

**Indexes:**
- `idx_ai_feature_values_lookup (tenant_id, feature_id, entity_id, as_of_date)`
- `idx_ai_feature_values_date (as_of_date)`


### ai_model_metrics — Model evaluation metrics

| Column | Definition |
|---|---|
| `id` | `UUID PRIMARY KEY DEFAULT gen_random_uuid()` |
| `tenant_id` | `UUID` |
| `model_id` | `UUID NOT NULL REFERENCES ai_models(id) ON DELETE CASCADE` |
| `version_id` | `UUID REFERENCES ai_model_versions(id)` |
| `metric_name` | `VARCHAR(100) NOT NULL` |
| `metric_value` | `DECIMAL(20,6) NOT NULL` |
| `recorded_at` | `TIMESTAMP DEFAULT now()` |
| `metadata` | `JSONB` |

**Indexes:**
- `idx_ai_metrics_model (model_id)`
- `idx_ai_metrics_time (recorded_at)`


### ai_gateway_routes — AI gateway routing rules

| Column | Definition |
|---|---|
| `id` | `UUID PRIMARY KEY DEFAULT gen_random_uuid()` |
| `tenant_id` | `UUID` |
| `name` | `VARCHAR(255) NOT NULL` |
| `model_type` | `VARCHAR(50) NOT NULL` |
| `route_pattern` | `VARCHAR(255)` |
| `target_endpoint` | `TEXT` |
| `fallback_strategy` | `VARCHAR(30) DEFAULT 'RULE_ENGINE' CHECK (fallback_strategy IN ('RULE_ENGINE','CACHE','ALTERNATE_MODEL','ERROR'))` |
| `fallback_model_id` | `UUID REFERENCES ai_models(id)` |
| `rate_limit_per_minute` | `INT DEFAULT 1000` |
| `timeout_ms` | `INT DEFAULT 5000` |
| `retry_count` | `INT DEFAULT 2` |
| `is_active` | `BOOLEAN DEFAULT true` |
| `metadata` | `JSONB` |
| `created_by` | `VARCHAR(255)` |
| `created_at` | `TIMESTAMP DEFAULT now()` |
| `updated_at` | `TIMESTAMP DEFAULT now()` |

**Table-level constraints:**
- `UNIQUE(tenant_id, model_type)`

**Indexes:**
- `idx_ai_gateway_tenant (tenant_id)`


### ai_compute_resources — AI compute resource inventory

| Column | Definition |
|---|---|
| `id` | `UUID PRIMARY KEY DEFAULT gen_random_uuid()` |
| `tenant_id` | `UUID` |
| `name` | `VARCHAR(255) NOT NULL` |
| `resource_type` | `VARCHAR(30) CHECK (resource_type IN ('CPU','GPU','TPU','MEMORY','STORAGE'))` |
| `provider` | `VARCHAR(50) DEFAULT 'KUBERNETES'` |
| `config` | `JSONB` |
| `allocated_units` | `DECIMAL(10,2)` |
| `used_units` | `DECIMAL(10,2) DEFAULT 0` |
| `cost_per_unit` | `DECIMAL(10,6)` |
| `is_active` | `BOOLEAN DEFAULT true` |
| `created_at` | `TIMESTAMP DEFAULT now()` |
| `updated_at` | `TIMESTAMP DEFAULT now()` |


### ai_rule_fallbacks — Fallback rules when AI is unavailable

| Column | Definition |
|---|---|
| `id` | `UUID PRIMARY KEY DEFAULT gen_random_uuid()` |
| `tenant_id` | `UUID` |
| `model_id` | `UUID NOT NULL REFERENCES ai_models(id) ON DELETE CASCADE` |
| `name` | `VARCHAR(255) NOT NULL` |
| `description` | `TEXT` |
| `priority` | `INT DEFAULT 100` |
| `condition_expression` | `TEXT` |
| `action_expression` | `TEXT` |
| `action_type` | `VARCHAR(50) CHECK (action_type IN ('STATIC_VALUE','FORMULA','SERVICE_CALL','LOOKUP_TABLE','THRESHOLD_RULE'))` |
| `action_config` | `JSONB` |
| `confidence_threshold_low` | `DECIMAL(5,2) DEFAULT 0.0` |
| `confidence_threshold_high` | `DECIMAL(5,2) DEFAULT 0.80` |
| `is_active` | `BOOLEAN DEFAULT true` |
| `created_by` | `VARCHAR(255)` |
| `created_at` | `TIMESTAMP DEFAULT now()` |
| `updated_at` | `TIMESTAMP DEFAULT now()` |

**Indexes:**
- `idx_ai_fallbacks_model (model_id)`


### ai_semantic_cache — Semantic cache for AI queries

| Column | Definition |
|---|---|
| `id` | `UUID PRIMARY KEY DEFAULT gen_random_uuid()` |
| `tenant_id` | `UUID NOT NULL` |
| `prompt_hash` | `VARCHAR(64) NOT NULL` |
| `prompt_text` | `TEXT` |
| `response_text` | `TEXT NOT NULL` |
| `model_name` | `VARCHAR(100) NOT NULL` |
| `hit_count` | `INT DEFAULT 1` |
| `cost_saved_usd` | `DECIMAL(12,6) DEFAULT 0` |
| `expires_at` | `TIMESTAMP NOT NULL` |
| `created_at` | `TIMESTAMP NOT NULL DEFAULT NOW()` |
| `last_hit_at` | `TIMESTAMP NOT NULL DEFAULT NOW()` |

**Indexes:**
- `idx_semantic_cache_hash (tenant_id, prompt_hash) UNIQUE`
- `idx_semantic_cache_expires (expires_at)`


### ai_shap_explanations — SHAP explainability records

| Column | Definition |
|---|---|
| `id` | `UUID PRIMARY KEY DEFAULT gen_random_uuid()` |
| `tenant_id` | `UUID NOT NULL` |
| `model_type` | `VARCHAR(50) NOT NULL` |
| `prediction_id` | `VARCHAR(100)` |
| `input_json` | `JSONB NOT NULL` |
| `output_json` | `JSONB NOT NULL` |
| `shap_values` | `JSONB` |
| `explanation` | `TEXT` |
| `confidence` | `DECIMAL(5,4)` |
| `created_at` | `TIMESTAMP NOT NULL DEFAULT NOW()` |

**Indexes:**
- `idx_shap_explanations_tenant (tenant_id, model_type)`


---

## Import / Freight


### import_history — Bulk import job history

| Column | Definition |
|---|---|
| `id` | `UUID PRIMARY KEY DEFAULT gen_random_uuid()` |
| `tenant_id` | `UUID NOT NULL` |
| `file_name` | `VARCHAR(500) NOT NULL` |
| `original_file_name` | `VARCHAR(500) NOT NULL` |
| `import_type` | `VARCHAR(50) NOT NULL` |
| `file_format` | `VARCHAR(10) NOT NULL` |
| `import_mode` | `VARCHAR(30) NOT NULL DEFAULT 'CONTINUE_ON_ERROR'` |
| `status` | `VARCHAR(30) NOT NULL DEFAULT 'PENDING'` |
| `total_records` | `INT NOT NULL DEFAULT 0` |
| `success_count` | `INT NOT NULL DEFAULT 0` |
| `failed_count` | `INT NOT NULL DEFAULT 0` |
| `duplicate_count` | `INT NOT NULL DEFAULT 0` |
| `processing_time_ms` | `BIGINT NOT NULL DEFAULT 0` |
| `file_size_bytes` | `BIGINT NOT NULL DEFAULT 0` |
| `stored_file_path` | `VARCHAR(1000)` |
| `processed_file_path` | `VARCHAR(1000)` |
| `error_file_path` | `VARCHAR(1000)` |
| `started_at` | `TIMESTAMP` |
| `completed_at` | `TIMESTAMP` |
| `created_at` | `TIMESTAMP NOT NULL DEFAULT now()` |
| `updated_at` | `TIMESTAMP NOT NULL DEFAULT now()` |

**Indexes:**
- `idx_import_history_tenant (tenant_id)`
- `idx_import_history_status (status)`
- `idx_import_history_type (import_type)`
- `idx_import_history_created (created_at DESC)`


### import_record_log — Per-record import log

| Column | Definition |
|---|---|
| `id` | `UUID PRIMARY KEY DEFAULT gen_random_uuid()` |
| `import_history_id` | `UUID NOT NULL REFERENCES import_history(id) ON DELETE CASCADE` |
| `tenant_id` | `UUID NOT NULL` |
| `row_number` | `INT NOT NULL` |
| `status` | `VARCHAR(20) NOT NULL DEFAULT 'PENDING'` |
| `error_code` | `VARCHAR(50)` |
| `error_message` | `TEXT` |
| `suggested_resolution` | `TEXT` |
| `original_data` | `JSONB` |
| `processed_data` | `JSONB` |
| `stage` | `VARCHAR(50)` |
| `created_at` | `TIMESTAMP NOT NULL DEFAULT now()` |

**Indexes:**
- `idx_import_record_log_history (import_history_id)`
- `idx_import_record_log_status (status)`
- `idx_import_record_log_row (import_history_id, row_number)`


### nxfreight_invoices — Freight invoices

| Column | Definition |
|---|---|
| `id` | `UUID PRIMARY KEY DEFAULT gen_random_uuid()` |
| `tenant_id` | `UUID NOT NULL` |
| `warehouse_id` | `UUID` |
| `invoice_number` | `VARCHAR(50) NOT NULL` |
| `carrier_code` | `VARCHAR(30) NOT NULL` |
| `carrier_name` | `VARCHAR(100)` |
| `invoice_date` | `DATE NOT NULL` |
| `period_start` | `DATE` |
| `period_end` | `DATE` |
| `total_amount` | `DECIMAL(12,2) NOT NULL` |
| `total_tax` | `DECIMAL(12,2) DEFAULT 0` |
| `total_discount` | `DECIMAL(12,2) DEFAULT 0` |
| `total_net` | `DECIMAL(12,2) NOT NULL` |
| `currency` | `VARCHAR(3) DEFAULT 'USD'` |
| `status` | `VARCHAR(20) NOT NULL DEFAULT 'RECEIVED'` |
| `matched_percentage` | `DECIMAL(5,2) DEFAULT 0` |
| `dispute_reason` | `TEXT` |
| `approved_by` | `VARCHAR(100)` |
| `paid_at` | `TIMESTAMP` |
| `file_reference` | `VARCHAR(200)` |
| `notes` | `TEXT` |
| `created_at` | `TIMESTAMP NOT NULL DEFAULT NOW()` |
| `updated_at` | `TIMESTAMP NOT NULL DEFAULT NOW()` |

**Table-level constraints:**
- `UNIQUE(tenant_id, invoice_number)`

**Indexes:**
- `idx_freight_invoices_status (tenant_id, status)`


### nxfreight_invoice_lines — Freight invoice line items

| Column | Definition |
|---|---|
| `id` | `UUID PRIMARY KEY DEFAULT gen_random_uuid()` |
| `tenant_id` | `UUID NOT NULL` |
| `invoice_id` | `UUID NOT NULL` |
| `line_number` | `INT NOT NULL` |
| `shipment_id` | `UUID` |
| `tracking_number` | `VARCHAR(50)` |
| `service_level` | `VARCHAR(50)` |
| `weight_kg` | `DECIMAL(10,2)` |
| `dimensions_json` | `TEXT` |
| `origin_zip` | `VARCHAR(20)` |
| `destination_zip` | `VARCHAR(20)` |
| `billed_amount` | `DECIMAL(12,2) NOT NULL` |
| `expected_amount` | `DECIMAL(12,2)` |
| `variance_amount` | `DECIMAL(12,2) DEFAULT 0` |
| `status` | `VARCHAR(20) NOT NULL DEFAULT 'PENDING'` |
| `dispute_notes` | `TEXT` |
| `created_at` | `TIMESTAMP NOT NULL DEFAULT NOW()` |

**Indexes:**
- `idx_freight_invoice_lines_invoice (invoice_id)`


### nxfreight_audit_logs — Freight invoice audit records

| Column | Definition |
|---|---|
| `id` | `UUID PRIMARY KEY DEFAULT gen_random_uuid()` |
| `tenant_id` | `UUID NOT NULL` |
| `invoice_id` | `UUID NOT NULL` |
| `action` | `VARCHAR(30) NOT NULL` |
| `performed_by` | `VARCHAR(100)` |
| `from_status` | `VARCHAR(20)` |
| `to_status` | `VARCHAR(20)` |
| `notes` | `TEXT` |
| `created_at` | `TIMESTAMP NOT NULL DEFAULT NOW()` |

**Indexes:**
- `idx_freight_audit_logs_invoice (invoice_id)`


---

## Core Commerce & Customers


### products — Product catalog (legacy name, no nx_ prefix)

| Column | Definition |
|---|---|
| `id` | `UUID PRIMARY KEY DEFAULT uuid_generate_v4()` |
| `tenant_id` | `UUID NOT NULL` |
| `sku` | `VARCHAR(100) NOT NULL` |
| `product_name` | `VARCHAR(255) NOT NULL` |
| `description` | `TEXT` |
| `category` | `VARCHAR(100)` |
| `unit_price` | `DECIMAL(12,2) NOT NULL DEFAULT 0` |
| `cost_price` | `DECIMAL(12,2)` |
| `weight` | `DECIMAL(10,2)` |
| `is_active` | `BOOLEAN NOT NULL DEFAULT TRUE` |
| `created_at` | `TIMESTAMP NOT NULL DEFAULT NOW()` |
| `updated_at` | `TIMESTAMP NOT NULL DEFAULT NOW()` |

**Indexes:**
- `idx_products_tenant (tenant_id)`
- `idx_products_sku (sku)`
- `idx_products_tenant_sku (tenant_id, sku) UNIQUE`


### nx_customers — Customers

| Column | Definition |
|---|---|
| `id` | `UUID PRIMARY KEY DEFAULT uuid_generate_v4()` |
| `tenant_id` | `UUID` |
| `external_id` | `VARCHAR(255)` |
| `name` | `VARCHAR(255) NOT NULL` |
| `email` | `VARCHAR(255)` |
| `phone` | `VARCHAR(50)` |
| `address` | `JSONB` |
| `created_at` | `TIMESTAMP NOT NULL DEFAULT NOW()` |

**Indexes:**
- `idx_customers_tenant_email (tenant_id, email) UNIQUE`
- `idx_nx_customers_address (address_id)`


### nx_addresses — Addresses (customer/ship-to/bill-to)

| Column | Definition |
|---|---|
| `id` | `UUID PRIMARY KEY` |
| `tenant_id` | `UUID` |
| `address_line1` | `VARCHAR(255)` |
| `address_line2` | `VARCHAR(255)` |
| `city` | `VARCHAR(100)` |
| `state` | `VARCHAR(100)` |
| `postal_code` | `VARCHAR(20)` |
| `country` | `VARCHAR(100) DEFAULT 'IN'` |
| `address_type` | `VARCHAR(50)` |
| `full_name` | `VARCHAR(255)` |
| `company` | `VARCHAR(255)` |
| `phone` | `VARCHAR(50)` |
| `is_residential` | `BOOLEAN DEFAULT FALSE` |
| `created_at` | `TIMESTAMP NOT NULL DEFAULT NOW()` |
| `updated_at` | `TIMESTAMP NOT NULL DEFAULT NOW()` |

**Indexes:**
- `idx_nx_addresses_tenant (tenant_id)`


### nx_contacts — Contacts (customer/supplier personnel)

| Column | Definition |
|---|---|
| `id` | `UUID PRIMARY KEY` |
| `tenant_id` | `UUID` |
| `full_name` | `VARCHAR(255)` |
| `email` | `VARCHAR(255)` |
| `phone` | `VARCHAR(50)` |
| `position` | `VARCHAR(255)` |
| `department` | `VARCHAR(255)` |
| `is_primary` | `BOOLEAN DEFAULT FALSE` |
| `notes` | `TEXT` |
| `created_at` | `TIMESTAMP NOT NULL DEFAULT NOW()` |
| `updated_at` | `TIMESTAMP NOT NULL DEFAULT NOW()` |

**Indexes:**
- `idx_nx_contacts_tenant (tenant_id)`


### nx_promotions — Promotions/discounts

| Column | Definition |
|---|---|
| `id` | `UUID PRIMARY KEY DEFAULT gen_random_uuid()` |
| `tenant_id` | `UUID NOT NULL` |
| `name` | `VARCHAR(255) NOT NULL` |
| `description` | `TEXT` |
| `promotion_type` | `VARCHAR(50) NOT NULL` |
| `discount_value` | `DECIMAL(12,2) NOT NULL` |
| `min_order_amount` | `DECIMAL(12,2)` |
| `min_quantity` | `INTEGER` |
| `max_uses_total` | `INTEGER` |
| `max_uses_per_customer` | `INTEGER` |
| `current_uses` | `INTEGER NOT NULL DEFAULT 0` |
| `coupon_code` | `VARCHAR(100) UNIQUE` |
| `start_date` | `TIMESTAMP NOT NULL` |
| `end_date` | `TIMESTAMP NOT NULL` |
| `applicable_channels` | `VARCHAR(255)` |
| `applicable_product_ids` | `TEXT` |
| `applicable_category_ids` | `TEXT` |
| `stackable` | `BOOLEAN NOT NULL DEFAULT FALSE` |
| `priority` | `INTEGER NOT NULL DEFAULT 10` |
| `active` | `BOOLEAN NOT NULL DEFAULT TRUE` |
| `created_at` | `TIMESTAMP NOT NULL DEFAULT NOW()` |
| `updated_at` | `TIMESTAMP NOT NULL DEFAULT NOW()` |

**Indexes:**
- `idx_promotions_tenant (tenant_id)`
- `idx_promotions_coupon (coupon_code)`
- `idx_promotions_active (tenant_id, active, start_date, end_date)`


### nx_promotion_usage — Promotion usage records

| Column | Definition |
|---|---|
| `id` | `UUID PRIMARY KEY DEFAULT gen_random_uuid()` |
| `tenant_id` | `UUID NOT NULL` |
| `promotion_id` | `UUID NOT NULL REFERENCES nx_promotions(id)` |
| `order_id` | `UUID` |
| `customer_id` | `UUID` |
| `coupon_code` | `VARCHAR(100)` |
| `discount_amount` | `DECIMAL(12,2) NOT NULL` |
| `order_total` | `DECIMAL(12,2)` |
| `used_at` | `TIMESTAMP NOT NULL DEFAULT NOW()` |

**Indexes:**
- `idx_promotion_usage_tenant (tenant_id)`
- `idx_promotion_usage_promotion (promotion_id)`
- `idx_promotion_usage_customer (customer_id)`


### nx_payments — Payments

| Column | Definition |
|---|---|
| `id` | `UUID PRIMARY KEY DEFAULT gen_random_uuid()` |
| `tenant_id` | `UUID NOT NULL` |
| `payment_number` | `VARCHAR(50) NOT NULL` |
| `payment_type` | `VARCHAR(30) NOT NULL DEFAULT 'INCOMING'` |
| `invoice_id` | `UUID REFERENCES nx_invoices(id)` |
| `customer_id` | `UUID` |
| `supplier_id` | `UUID REFERENCES nx_suppliers(id)` |
| `amount` | `DECIMAL(15,2) NOT NULL` |
| `currency` | `VARCHAR(3) DEFAULT 'USD'` |
| `payment_method` | `VARCHAR(50) NOT NULL` |
| `payment_reference` | `VARCHAR(255)` |
| `status` | `VARCHAR(20) DEFAULT 'PENDING'` |
| `transaction_id` | `VARCHAR(255)` |
| `gateway_response` | `JSONB` |
| `paid_at` | `TIMESTAMP` |
| `notes` | `TEXT` |
| `created_at` | `TIMESTAMP NOT NULL DEFAULT now()` |
| `updated_at` | `TIMESTAMP NOT NULL DEFAULT now()` |

**Table-level constraints:**
- `UNIQUE(tenant_id, payment_number)`


### nx_invoices — Invoices

| Column | Definition |
|---|---|
| `id` | `UUID PRIMARY KEY DEFAULT gen_random_uuid()` |
| `tenant_id` | `UUID NOT NULL` |
| `invoice_number` | `VARCHAR(50) NOT NULL` |
| `invoice_type` | `VARCHAR(20) DEFAULT 'SALES'` |
| `order_id` | `UUID` |
| `customer_id` | `UUID` |
| `supplier_id` | `UUID REFERENCES nx_suppliers(id)` |
| `status` | `VARCHAR(30) DEFAULT 'DRAFT'` |
| `invoice_date` | `DATE NOT NULL DEFAULT CURRENT_DATE` |
| `due_date` | `DATE` |
| `paid_date` | `DATE` |
| `currency` | `VARCHAR(3) DEFAULT 'USD'` |
| `subtotal` | `DECIMAL(15,2) DEFAULT 0` |
| `tax_amount` | `DECIMAL(15,2) DEFAULT 0` |
| `discount_amount` | `DECIMAL(15,2) DEFAULT 0` |
| `shipping_cost` | `DECIMAL(15,2) DEFAULT 0` |
| `total_amount` | `DECIMAL(15,2) DEFAULT 0` |
| `amount_paid` | `DECIMAL(15,2) DEFAULT 0` |
| `balance_due` | `DECIMAL(15,2) DEFAULT 0` |
| `payment_terms` | `VARCHAR(50)` |
| `notes` | `TEXT` |
| `billing_address` | `JSONB` |
| `shipping_address` | `JSONB` |
| `metadata` | `JSONB` |
| `created_at` | `TIMESTAMP NOT NULL DEFAULT now()` |
| `updated_at` | `TIMESTAMP NOT NULL DEFAULT now()` |

**Table-level constraints:**
- `UNIQUE(tenant_id, invoice_number)`


### nx_invoice_items — Invoice line items

| Column | Definition |
|---|---|
| `id` | `UUID PRIMARY KEY DEFAULT gen_random_uuid()` |
| `invoice_id` | `UUID NOT NULL REFERENCES nx_invoices(id) ON DELETE CASCADE` |
| `sku` | `VARCHAR(100)` |
| `description` | `VARCHAR(255) NOT NULL` |
| `quantity` | `INT NOT NULL` |
| `unit_price` | `DECIMAL(15,4) NOT NULL` |
| `total_price` | `DECIMAL(15,2) NOT NULL` |
| `tax_rate` | `DECIMAL(5,2) DEFAULT 0` |
| `discount_percent` | `DECIMAL(5,2) DEFAULT 0` |
| `created_at` | `TIMESTAMP NOT NULL DEFAULT now()` |


### nx_credit_memos — Credit memos

| Column | Definition |
|---|---|
| `id` | `UUID PRIMARY KEY DEFAULT gen_random_uuid()` |
| `tenant_id` | `UUID NOT NULL` |
| `memo_number` | `VARCHAR(50) NOT NULL` |
| `invoice_id` | `UUID REFERENCES nx_invoices(id)` |
| `order_id` | `UUID` |
| `customer_id` | `UUID` |
| `supplier_id` | `UUID REFERENCES nx_suppliers(id)` |
| `memo_type` | `VARCHAR(20) DEFAULT 'REFUND'` |
| `reason` | `VARCHAR(255)` |
| `amount` | `DECIMAL(15,2) NOT NULL` |
| `currency` | `VARCHAR(3) DEFAULT 'USD'` |
| `status` | `VARCHAR(20) DEFAULT 'DRAFT'` |
| `notes` | `TEXT` |
| `created_at` | `TIMESTAMP NOT NULL DEFAULT now()` |
| `updated_at` | `TIMESTAMP NOT NULL DEFAULT now()` |

**Table-level constraints:**
- `UNIQUE(tenant_id, memo_number)`


### nx_credit_memo_invoice_applications — Credit memo → invoice applications

| Column | Definition |
|---|---|
| `id` | `UUID PRIMARY KEY DEFAULT gen_random_uuid()` |
| `tenant_id` | `UUID NOT NULL` |
| `credit_memo_id` | `UUID NOT NULL REFERENCES nx_credit_memos(id) ON DELETE CASCADE` |
| `invoice_id` | `UUID NOT NULL` |
| `amount` | `DECIMAL(12,2) NOT NULL` |
| `tax_amount` | `DECIMAL(12,2) DEFAULT 0.00` |
| `created_at` | `TIMESTAMP NOT NULL DEFAULT now()` |

**Table-level constraints:**
- `UNIQUE (credit_memo_id, invoice_id)`

**Indexes:**
- `idx_cm_invoice_app_cm (credit_memo_id)`
- `idx_cm_invoice_app_invoice (invoice_id)`


### nx_billing_statements — Billing statements

| Column | Definition |
|---|---|
| `id` | `UUID PRIMARY KEY` |
| `tenant_id` | `UUID NOT NULL` |
| `client_id` | `UUID NOT NULL` |
| `client_name` | `VARCHAR(255) NOT NULL` |
| `currency` | `VARCHAR(8) NOT NULL DEFAULT 'USD'` |
| `period_start` | `DATE NOT NULL` |
| `period_end` | `DATE NOT NULL` |
| `status` | `VARCHAR(20) NOT NULL DEFAULT 'DRAFT'` |
| `order_count` | `INTEGER NOT NULL DEFAULT 0` |
| `line_count` | `INTEGER NOT NULL DEFAULT 0` |
| `picked_lines` | `INTEGER NOT NULL DEFAULT 0` |
| `units_handled` | `INTEGER NOT NULL DEFAULT 0` |
| `subtotal` | `NUMERIC(14,2) NOT NULL DEFAULT 0` |
| `total` | `NUMERIC(14,2) NOT NULL DEFAULT 0` |
| `notes` | `TEXT` |
| `created_at` | `TIMESTAMP NOT NULL DEFAULT NOW()` |
| `updated_at` | `TIMESTAMP NOT NULL DEFAULT NOW()` |

**Indexes:**
- `idx_billing_statements_tenant (tenant_id)`
- `idx_billing_statements_client (client_id)`


### nx_billing_statement_lines — Billing statement line items

| Column | Definition |
|---|---|
| `id` | `UUID PRIMARY KEY` |
| `statement_id` | `UUID NOT NULL REFERENCES nx_billing_statements(id) ON DELETE CASCADE` |
| `rate_type` | `VARCHAR(30) NOT NULL` |
| `description` | `VARCHAR(500)` |
| `quantity` | `INTEGER NOT NULL DEFAULT 0` |
| `unit_price` | `NUMERIC(12,2) NOT NULL DEFAULT 0` |
| `amount` | `NUMERIC(14,2) NOT NULL DEFAULT 0` |
| `created_at` | `TIMESTAMP NOT NULL DEFAULT NOW()` |

**Indexes:**
- `idx_billing_statement_lines_stmt (statement_id)`


---

## Orders & Order Lifecycle


### nx_orders — Orders

| Column | Definition |
|---|---|
| `id` | `UUID PRIMARY KEY DEFAULT uuid_generate_v4()` |
| `tenant_id` | `UUID` |
| `external_id` | `VARCHAR(255)` |
| `channel` | `VARCHAR(50)` |
| `channel_order_id` | `VARCHAR(255)` |
| `customer_id` | `UUID REFERENCES nx_customers(id)` |
| `status` | `VARCHAR(50) NOT NULL DEFAULT 'PENDING'` |
| `sub_status` | `VARCHAR(50)` |
| `fulfillment_type` | `VARCHAR(50)` |
| `ship_from` | `VARCHAR(255)` |
| `ship_to` | `JSONB` |
| `billing_address` | `JSONB` |
| `currency` | `VARCHAR(3) DEFAULT 'INR'` |
| `subtotal` | `DECIMAL(12,2) DEFAULT 0.00` |
| `shipping_cost` | `DECIMAL(12,2) DEFAULT 0.00` |
| `tax_amount` | `DECIMAL(12,2) DEFAULT 0.00` |
| `total` | `DECIMAL(12,2) DEFAULT 0.00` |
| `payment_status` | `VARCHAR(50)` |
| `payment_reference` | `VARCHAR(255)` |
| `allocated_node` | `UUID` |
| `allocation_rule` | `VARCHAR(50)` |
| `allocation_confidence` | `DECIMAL(5,4)` |
| `carrier_id` | `VARCHAR(100)` |
| `tracking_number` | `VARCHAR(255)` |
| `label_url` | `TEXT` |
| `promised_delivery` | `TIMESTAMP` |
| `created_at` | `TIMESTAMP NOT NULL DEFAULT NOW()` |
| `updated_at` | `TIMESTAMP NOT NULL DEFAULT NOW()` |
| `shipped_at` | `TIMESTAMP` |
| `delivered_at` | `TIMESTAMP` |
| `metadata` | `JSONB` |

**Indexes:**
- `idx_orders_tenant_status (tenant_id, status)`
- `idx_orders_channel_order_id (channel_order_id)`
- `idx_orders_tracking (tracking_number)`
- `idx_orders_customer (customer_id)`
- `idx_nx_orders_ship_to_address (ship_to_address_id)`
- `idx_nx_orders_billing_address (billing_address_id)`


### nx_order_items — Order line items

| Column | Definition |
|---|---|
| `id` | `UUID PRIMARY KEY DEFAULT uuid_generate_v4()` |
| `order_id` | `UUID NOT NULL REFERENCES nx_orders(id) ON DELETE CASCADE` |
| `sku` | `VARCHAR(100) NOT NULL` |
| `product_name` | `VARCHAR(255)` |
| `quantity` | `INT NOT NULL DEFAULT 1` |
| `unit_price` | `DECIMAL(12,2) NOT NULL DEFAULT 0.00` |
| `total_price` | `DECIMAL(12,2) NOT NULL DEFAULT 0.00` |
| `allocated_node_id` | `UUID` |
| `allocated_qty` | `INT DEFAULT 0` |

**Indexes:**
- `idx_order_items_order (order_id)`


### nx_order_allocations — Allocation of items to nodes

| Column | Definition |
|---|---|
| `id` | `UUID PRIMARY KEY DEFAULT gen_random_uuid()` |
| `order_id` | `UUID NOT NULL REFERENCES nx_orders(id) ON DELETE CASCADE` |
| `tenant_id` | `UUID NOT NULL` |
| `node_id` | `UUID` |
| `node_name` | `VARCHAR(255)` |
| `node_type` | `VARCHAR(50) DEFAULT 'WAREHOUSE' CHECK (node_type IN ('WAREHOUSE','DC','CROSS_DOCK','DROP_SHIP','STORE'))` |
| `priority` | `INT DEFAULT 0` |
| `quantity_allocated` | `INT DEFAULT 0` |
| `quantity_requested` | `INT DEFAULT 0` |
| `status` | `VARCHAR(30) DEFAULT 'PENDING' CHECK (status IN ('PENDING','ALLOCATED','PARTIALLY_ALLOCATED','FAILED','CANCELLED'))` |
| `delivery_promise_date` | `TIMESTAMP` |
| `delivery_promise_confidence` | `DECIMAL(5,4) DEFAULT 0.0` |
| `allocation_strategy` | `VARCHAR(30) DEFAULT 'RULE_BASED' CHECK (allocation_strategy IN ('RULE_BASED','AI_OPTIMIZED','HYBRID','MANUAL'))` |
| `rule_id` | `UUID` |
| `rule_name` | `VARCHAR(255)` |
| `cost_estimated` | `DECIMAL(12,2) DEFAULT 0.00` |
| `distance_km` | `DECIMAL(10,2)` |
| `metadata` | `JSONB` |
| `allocated_at` | `TIMESTAMP DEFAULT now()` |
| `allocated_by` | `UUID` |
| `created_at` | `TIMESTAMP DEFAULT now()` |
| `updated_at` | `TIMESTAMP DEFAULT now()` |

**Indexes:**
- `idx_nx_order_allocations_order (order_id)`
- `idx_nx_order_allocations_tenant (tenant_id)`
- `idx_nx_order_allocations_status (status)`
- `idx_nx_order_allocations_node (node_id)`


### nx_order_approvals — Order approval requests

| Column | Definition |
|---|---|
| `id` | `UUID PRIMARY KEY DEFAULT gen_random_uuid()` |
| `tenant_id` | `UUID NOT NULL` |
| `order_id` | `UUID NOT NULL` |
| `order_number` | `VARCHAR(50) NOT NULL` |
| `order_total` | `DECIMAL(12, 2)` |
| `customer_id` | `UUID` |
| `risk_score` | `DECIMAL(5, 2)` |
| `status` | `VARCHAR(50) NOT NULL DEFAULT 'PENDING'` |
| `matched_rules` | `TEXT` |
| `reviewed_by` | `VARCHAR(255)` |
| `review_notes` | `TEXT` |
| `decided_at` | `TIMESTAMP` |
| `created_at` | `TIMESTAMP NOT NULL DEFAULT NOW()` |
| `updated_at` | `TIMESTAMP NOT NULL DEFAULT NOW()` |

**Indexes:**
- `idx_approval_tenant (tenant_id)`
- `idx_approval_order (order_id)`
- `idx_approval_status (status)`
- `idx_approval_customer (customer_id)`


### nx_approval_rules — Approval rule definitions

| Column | Definition |
|---|---|
| `id` | `UUID PRIMARY KEY DEFAULT gen_random_uuid()` |
| `tenant_id` | `UUID NOT NULL` |
| `name` | `VARCHAR(255)` |
| `rule_type` | `VARCHAR(50) NOT NULL` |
| `action` | `VARCHAR(50) NOT NULL` |
| `threshold_value` | `DECIMAL(12, 2)` |
| `threshold_string` | `VARCHAR(255)` |
| `priority` | `INTEGER NOT NULL DEFAULT 10` |
| `active` | `BOOLEAN NOT NULL DEFAULT TRUE` |
| `created_at` | `TIMESTAMP NOT NULL DEFAULT NOW()` |
| `updated_at` | `TIMESTAMP NOT NULL DEFAULT NOW()` |

**Indexes:**
- `idx_approval_rule_tenant (tenant_id)`


### nx_order_rejections — Order rejection records

| Column | Definition |
|---|---|
| `id` | `UUID PRIMARY KEY DEFAULT gen_random_uuid()` |
| `tenant_id` | `UUID NOT NULL` |
| `order_id` | `UUID NOT NULL` |
| `order_number` | `VARCHAR(50) NOT NULL` |
| `order_item_id` | `UUID` |
| `sku` | `VARCHAR(100) NOT NULL` |
| `rejection_reason_id` | `UUID NOT NULL` |
| `rejection_code` | `VARCHAR(50) NOT NULL` |
| `quantity` | `INTEGER NOT NULL` |
| `rejected_by` | `VARCHAR(255) NOT NULL` |
| `notes` | `TEXT` |
| `photo_path` | `VARCHAR(500)` |
| `inventory_action` | `VARCHAR(50) NOT NULL` |
| `inventory_adjusted` | `BOOLEAN NOT NULL DEFAULT FALSE` |
| `status` | `VARCHAR(50) NOT NULL DEFAULT 'PENDING'` |
| `processed_at` | `TIMESTAMP` |
| `created_at` | `TIMESTAMP NOT NULL DEFAULT NOW()` |
| `updated_at` | `TIMESTAMP NOT NULL DEFAULT NOW()` |

**Indexes:**
- `idx_rejection_tenant (tenant_id)`
- `idx_rejection_order (order_id)`
- `idx_rejection_status (status)`
- `idx_rejection_code (rejection_code)`


### nx_rejection_reasons — Rejection reason codes

| Column | Definition |
|---|---|
| `id` | `UUID PRIMARY KEY DEFAULT gen_random_uuid()` |
| `tenant_id` | `UUID NOT NULL` |
| `code` | `VARCHAR(50) NOT NULL` |
| `label` | `VARCHAR(255) NOT NULL` |
| `description` | `TEXT` |
| `category` | `VARCHAR(50) NOT NULL` |
| `inventory_impact` | `VARCHAR(50) NOT NULL` |
| `requires_photo` | `BOOLEAN NOT NULL DEFAULT FALSE` |
| `requires_notes` | `BOOLEAN NOT NULL DEFAULT FALSE` |
| `active` | `BOOLEAN NOT NULL DEFAULT TRUE` |
| `sort_order` | `INTEGER DEFAULT 0` |
| `created_at` | `TIMESTAMP NOT NULL DEFAULT NOW()` |
| `updated_at` | `TIMESTAMP NOT NULL DEFAULT NOW()` |

**Table-level constraints:**
- `UNIQUE(tenant_id, code)`

**Indexes:**
- `idx_rejection_reason_tenant (tenant_id)`


### nx_parked_orders — Parked orders awaiting resolution

| Column | Definition |
|---|---|
| `id` | `UUID PRIMARY KEY DEFAULT gen_random_uuid()` |
| `tenant_id` | `UUID NOT NULL` |
| `order_id` | `UUID NOT NULL` |
| `order_number` | `VARCHAR(50) NOT NULL` |
| `reason` | `VARCHAR(50) NOT NULL` |
| `priority` | `INTEGER DEFAULT 10` |
| `sku` | `VARCHAR(100)` |
| `product_name` | `VARCHAR(255)` |
| `quantity` | `INTEGER DEFAULT 1` |
| `customer_email` | `VARCHAR(255)` |
| `expected_date` | `TIMESTAMP` |
| `notes` | `TEXT` |
| `status` | `VARCHAR(50) NOT NULL DEFAULT 'PARKED'` |
| `parked_at` | `TIMESTAMP NOT NULL DEFAULT NOW()` |
| `released_at` | `TIMESTAMP` |
| `created_at` | `TIMESTAMP NOT NULL DEFAULT NOW()` |
| `updated_at` | `TIMESTAMP NOT NULL DEFAULT NOW()` |

**Indexes:**
- `idx_parked_tenant (tenant_id)`
- `idx_parked_order_id (order_id)`
- `idx_parked_status (status)`
- `idx_parked_reason (reason)`
- `idx_parked_sku (sku)`


### nx_pickup_orders — BOPIS/pickup orders

| Column | Definition |
|---|---|
| `id` | `UUID PRIMARY KEY DEFAULT gen_random_uuid()` |
| `tenant_id` | `UUID NOT NULL` |
| `order_id` | `UUID NOT NULL` |
| `order_number` | `VARCHAR(50) NOT NULL` |
| `node_id` | `UUID NOT NULL` |
| `picker_id` | `UUID` |
| `picker_name` | `VARCHAR(255)` |
| `status` | `VARCHAR(50) NOT NULL DEFAULT 'PENDING'` |
| `pickup_type` | `VARCHAR(20) NOT NULL DEFAULT 'BOPIS'` |
| `customer_name` | `VARCHAR(255)` |
| `customer_email` | `VARCHAR(255)` |
| `customer_phone` | `VARCHAR(50)` |
| `pickup_code` | `VARCHAR(20)` |
| `estimated_ready_at` | `TIMESTAMP` |
| `picked_at` | `TIMESTAMP` |
| `packed_at` | `TIMESTAMP` |
| `ready_at` | `TIMESTAMP` |
| `handed_off_at` | `TIMESTAMP` |
| `collected_at` | `TIMESTAMP` |
| `created_at` | `TIMESTAMP NOT NULL DEFAULT NOW()` |
| `updated_at` | `TIMESTAMP NOT NULL DEFAULT NOW()` |

**Indexes:**
- `idx_pickup_tenant (tenant_id)`
- `idx_pickup_node (node_id)`
- `idx_pickup_status (status)`
- `idx_pickup_picker (picker_id)`
- `idx_pickup_code (pickup_code)`


### nx_pickup_order_items — Pickup order line items

| Column | Definition |
|---|---|
| `id` | `UUID PRIMARY KEY DEFAULT gen_random_uuid()` |
| `pickup_order_id` | `UUID NOT NULL` |
| `sku` | `VARCHAR(100) NOT NULL` |
| `product_name` | `VARCHAR(255)` |
| `quantity` | `INTEGER NOT NULL` |
| `picked_quantity` | `INTEGER DEFAULT 0` |
| `location` | `VARCHAR(100)` |
| `status` | `VARCHAR(50) NOT NULL DEFAULT 'PENDING'` |
| `substituted_sku` | `VARCHAR(100)` |
| `notes` | `TEXT` |
| `created_at` | `TIMESTAMP NOT NULL DEFAULT NOW()` |

**Indexes:**
- `idx_pickup_item_order (pickup_order_id)`


### nx_endless_aisle_orders — Endless-aisle (ship-from-store) orders

| Column | Definition |
|---|---|
| `id` | `UUID PRIMARY KEY DEFAULT gen_random_uuid()` |
| `tenant_id` | `UUID NOT NULL` |
| `store_id` | `UUID NOT NULL` |
| `customer_id` | `UUID` |
| `customer_name` | `VARCHAR(255)` |
| `customer_email` | `VARCHAR(255)` |
| `customer_phone` | `VARCHAR(50)` |
| `product_sku` | `VARCHAR(100) NOT NULL` |
| `product_name` | `VARCHAR(255) NOT NULL` |
| `quantity` | `INTEGER NOT NULL` |
| `unit_price` | `DECIMAL(12,2) NOT NULL` |
| `total_amount` | `DECIMAL(12,2) NOT NULL` |
| `fulfillment_type` | `VARCHAR(50) NOT NULL` |
| `ship_to_address` | `TEXT` |
| `status` | `VARCHAR(50) NOT NULL DEFAULT 'PENDING'` |
| `linked_order_id` | `UUID` |
| `notes` | `TEXT` |
| `created_by` | `VARCHAR(100)` |
| `created_at` | `TIMESTAMP NOT NULL DEFAULT NOW()` |
| `updated_at` | `TIMESTAMP NOT NULL DEFAULT NOW()` |

**Indexes:**
- `idx_endless_aisle_tenant (tenant_id)`
- `idx_endless_aisle_store (store_id)`
- `idx_endless_aisle_status (tenant_id, status)`
- `idx_endless_aisle_customer (customer_id)`


### nx_brokering_runs — Brokering job runs

| Column | Definition |
|---|---|
| `id` | `UUID PRIMARY KEY DEFAULT gen_random_uuid()` |
| `tenant_id` | `UUID NOT NULL` |
| `run_type` | `VARCHAR(50) NOT NULL` |
| `started_at` | `TIMESTAMP NOT NULL` |
| `completed_at` | `TIMESTAMP` |
| `orders_processed` | `INTEGER DEFAULT 0` |
| `orders_allocated` | `INTEGER DEFAULT 0` |
| `orders_failed` | `INTEGER DEFAULT 0` |
| `execution_time_ms` | `INTEGER` |
| `status` | `VARCHAR(50) NOT NULL DEFAULT 'RUNNING'` |
| `triggered_by` | `UUID` |
| `metadata` | `JSONB` |
| `created_at` | `TIMESTAMP NOT NULL DEFAULT NOW()` |

**Indexes:**
- `idx_brokering_run_tenant (tenant_id)`
- `idx_brokering_run_status (status)`


### nx_brokering_queue — Orders queued for brokering

| Column | Definition |
|---|---|
| `id` | `UUID PRIMARY KEY DEFAULT gen_random_uuid()` |
| `tenant_id` | `UUID NOT NULL` |
| `order_id` | `UUID NOT NULL` |
| `priority` | `VARCHAR(20) NOT NULL DEFAULT 'NORMAL'` |
| `status` | `VARCHAR(50) NOT NULL DEFAULT 'WAITING'` |
| `attempts` | `INTEGER NOT NULL DEFAULT 0` |
| `max_attempts` | `INTEGER NOT NULL DEFAULT 3` |
| `last_attempt_at` | `TIMESTAMP` |
| `next_run_at` | `TIMESTAMP` |
| `allocated_node_id` | `UUID` |
| `failure_reason` | `TEXT` |
| `metadata` | `JSONB` |
| `entered_at` | `TIMESTAMP NOT NULL DEFAULT NOW()` |
| `exited_at` | `TIMESTAMP` |
| `created_at` | `TIMESTAMP NOT NULL DEFAULT NOW()` |
| `updated_at` | `TIMESTAMP NOT NULL DEFAULT NOW()` |

**Indexes:**
- `idx_brokering_tenant (tenant_id)`
- `idx_brokering_status (status)`
- `idx_brokering_priority (priority)`
- `idx_brokering_order (order_id)`
- `idx_brokering_next_run (next_run_at)`


### nx_routing_rules — Order routing rules

| Column | Definition |
|---|---|
| `id` | `UUID PRIMARY KEY DEFAULT uuid_generate_v4()` |
| `tenant_id` | `UUID` |
| `name` | `VARCHAR(255) NOT NULL` |
| `description` | `TEXT` |
| `priority` | `INT NOT NULL` |
| `rule_type` | `VARCHAR(50) NOT NULL` |
| `conditions` | `JSONB` |
| `actions` | `JSONB` |
| `is_active` | `BOOLEAN DEFAULT TRUE` |
| `created_at` | `TIMESTAMP NOT NULL DEFAULT NOW()` |
| `updated_at` | `TIMESTAMP NOT NULL DEFAULT NOW()` |

**Indexes:**
- `idx_routing_rules_tenant (tenant_id, is_active)`


### nx_routing_config — Routing configuration

| Column | Definition |
|---|---|
| `id` | `UUID PRIMARY KEY DEFAULT gen_random_uuid()` |
| `tenant_id` | `UUID NOT NULL UNIQUE` |
| `default_strategy` | `VARCHAR(30) DEFAULT 'HYBRID' CHECK (default_strategy IN ('RULE_BASED','AI_OPTIMIZED','HYBRID'))` |
| `ai_confidence_threshold` | `DECIMAL(5,4) DEFAULT 0.7000` |
| `enable_auto_allocation` | `BOOLEAN DEFAULT true` |
| `enable_exception_detection` | `BOOLEAN DEFAULT true` |
| `enable_auto_resolution` | `BOOLEAN DEFAULT false` |
| `max_splits` | `INT DEFAULT 3` |
| `cost_optimization_weight` | `DECIMAL(3,2) DEFAULT 0.40` |
| `speed_optimization_weight` | `DECIMAL(3,2) DEFAULT 0.40` |
| `accuracy_optimization_weight` | `DECIMAL(3,2) DEFAULT 0.20` |
| `preferred_carriers` | `JSONB` |
| `blacklisted_nodes` | `JSONB` |
| `created_at` | `TIMESTAMP DEFAULT now()` |
| `updated_at` | `TIMESTAMP DEFAULT now()` |

**Indexes:**
- `idx_nx_routing_config_tenant (tenant_id)`


### nx_routing_log — Routing decision log

| Column | Definition |
|---|---|
| `id` | `UUID PRIMARY KEY DEFAULT gen_random_uuid()` |
| `order_id` | `UUID NOT NULL REFERENCES nx_orders(id) ON DELETE CASCADE` |
| `tenant_id` | `UUID NOT NULL` |
| `strategy` | `VARCHAR(30) NOT NULL` |
| `input_snapshot` | `JSONB` |
| `rules_evaluated` | `JSONB` |
| `candidates` | `JSONB` |
| `selected_node_id` | `UUID` |
| `confidence_score` | `DECIMAL(5,4)` |
| `delivery_promise_estimate` | `TIMESTAMP` |
| `cost_estimate` | `DECIMAL(12,2)` |
| `exceptions_detected` | `JSONB` |
| `execution_time_ms` | `INT` |
| `status` | `VARCHAR(30) DEFAULT 'SUCCESS' CHECK (status IN ('SUCCESS','FAILED','PARTIAL'))` |
| `error_message` | `TEXT` |
| `created_at` | `TIMESTAMP DEFAULT now()` |

**Indexes:**
- `idx_nx_routing_log_order (order_id)`
- `idx_nx_routing_log_tenant (tenant_id)`


---

## Inventory & Network


### nx_inventory — Inventory stock levels

| Column | Definition |
|---|---|
| `id` | `UUID PRIMARY KEY DEFAULT uuid_generate_v4()` |
| `tenant_id` | `UUID` |
| `sku` | `VARCHAR(100) NOT NULL` |
| `node_id` | `UUID` |
| `quantity_on_hand` | `INT DEFAULT 0` |
| `quantity_allocated` | `INT DEFAULT 0` |
| `quantity_reserved` | `INT DEFAULT 0` |
| `quantity_in_transit` | `INT DEFAULT 0` |
| `quantity_on_order` | `INT DEFAULT 0` |
| `quantity_damaged` | `INT DEFAULT 0` |
| `safety_stock` | `INT DEFAULT 0` |
| `reorder_point` | `INT DEFAULT 0` |
| `reorder_qty` | `INT DEFAULT 0` |
| `lot_number` | `VARCHAR(100)` |
| `expiry_date` | `DATE` |
| `created_at` | `TIMESTAMP NOT NULL DEFAULT NOW()` |
| `updated_at` | `TIMESTAMP NOT NULL DEFAULT NOW()` |

**Indexes:**
- `idx_inventory_sku (sku)`
- `idx_inventory_node (node_id)`
- `idx_inventory_tenant_sku (tenant_id, sku)`
- `idx_inventory_tenant_sku_unique (tenant_id, sku) UNIQUE`


### nx_inventory_receipts — Inbound inventory receipts

| Column | Definition |
|---|---|
| `id` | `UUID PRIMARY KEY DEFAULT uuid_generate_v4()` |
| `tenant_id` | `UUID` |
| `node_id` | `UUID` |
| `receipt_type` | `VARCHAR(50) NOT NULL` |
| `reference_number` | `VARCHAR(255)` |
| `sku` | `VARCHAR(100) NOT NULL` |
| `product_name` | `VARCHAR(255)` |
| `quantity` | `INT NOT NULL` |
| `unit_cost` | `DECIMAL(12,2)` |
| `lot_number` | `VARCHAR(100)` |
| `expiry_date` | `TIMESTAMP` |
| `status` | `VARCHAR(50) NOT NULL DEFAULT 'PENDING'` |
| `received_by` | `VARCHAR(255)` |
| `created_at` | `TIMESTAMP NOT NULL DEFAULT NOW()` |
| `received_at` | `TIMESTAMP` |

**Indexes:**
- `idx_inventory_receipts_tenant (tenant_id, status)`


### nx_transfer_orders — Transfer orders (current)

| Column | Definition |
|---|---|
| `id` | `UUID PRIMARY KEY DEFAULT gen_random_uuid()` |
| `tenant_id` | `UUID NOT NULL` |
| `transfer_number` | `VARCHAR(50) NOT NULL UNIQUE` |
| `transfer_type` | `VARCHAR(50) NOT NULL` |
| `source_node_id` | `UUID NOT NULL` |
| `destination_node_id` | `UUID NOT NULL` |
| `status` | `VARCHAR(50) NOT NULL DEFAULT 'DRAFT'` |
| `priority` | `VARCHAR(20) NOT NULL DEFAULT 'NORMAL'` |
| `requested_by` | `UUID` |
| `approved_by` | `UUID` |
| `expected_arrival` | `TIMESTAMP` |
| `actual_arrival` | `TIMESTAMP` |
| `notes` | `TEXT` |
| `metadata` | `JSONB` |
| `created_at` | `TIMESTAMP NOT NULL DEFAULT NOW()` |
| `updated_at` | `TIMESTAMP NOT NULL DEFAULT NOW()` |

**Indexes:**
- `idx_transfer_tenant (tenant_id)`
- `idx_transfer_status (status)`
- `idx_transfer_source (source_node_id)`
- `idx_transfer_dest (destination_node_id)`
- `idx_transfer_number (transfer_number)`


### nx_transfer_order_items — Transfer order line items (current)

| Column | Definition |
|---|---|
| `id` | `UUID PRIMARY KEY DEFAULT gen_random_uuid()` |
| `transfer_order_id` | `UUID NOT NULL REFERENCES nx_transfer_orders(id) ON DELETE CASCADE` |
| `tenant_id` | `UUID NOT NULL` |
| `sku` | `VARCHAR(100) NOT NULL` |
| `product_name` | `VARCHAR(255)` |
| `quantity_requested` | `INTEGER NOT NULL` |
| `quantity_shipped` | `INTEGER` |
| `quantity_received` | `INTEGER` |
| `unit_cost` | `DECIMAL(12, 2)` |
| `status` | `VARCHAR(50) NOT NULL DEFAULT 'PENDING'` |
| `created_at` | `TIMESTAMP NOT NULL DEFAULT NOW()` |
| `updated_at` | `TIMESTAMP NOT NULL DEFAULT NOW()` |

**Indexes:**
- `idx_transfer_item_order (transfer_order_id)`
- `idx_transfer_item_sku (sku)`


### nx_warehouses — Warehouses

| Column | Definition |
|---|---|
| `id` | `UUID PRIMARY KEY DEFAULT gen_random_uuid()` |
| `tenant_id` | `UUID NOT NULL` |
| `code` | `VARCHAR(50) NOT NULL` |
| `name` | `VARCHAR(255) NOT NULL` |
| `type` | `VARCHAR(30) DEFAULT 'WAREHOUSE'` |
| `status` | `VARCHAR(20) DEFAULT 'ACTIVE'` |
| `address_line1` | `VARCHAR(255)` |
| `address_line2` | `VARCHAR(255)` |
| `city` | `VARCHAR(100)` |
| `state` | `VARCHAR(100)` |
| `zip_code` | `VARCHAR(20)` |
| `country` | `VARCHAR(100)` |
| `latitude` | `DECIMAL(10,7)` |
| `longitude` | `DECIMAL(10,7)` |
| `total_capacity_sqm` | `DECIMAL(12,2)` |
| `used_capacity_sqm` | `DECIMAL(12,2) DEFAULT 0` |
| `total_capacity_cbm` | `DECIMAL(12,2)` |
| `used_capacity_cbm` | `DECIMAL(12,2) DEFAULT 0` |
| `dock_count` | `INT DEFAULT 0` |
| `is_active` | `BOOLEAN DEFAULT true` |
| `operating_hours` | `JSONB` |
| `contact_name` | `VARCHAR(255)` |
| `contact_phone` | `VARCHAR(50)` |
| `contact_email` | `VARCHAR(255)` |
| `metadata` | `JSONB` |
| `created_at` | `TIMESTAMP NOT NULL DEFAULT now()` |
| `updated_at` | `TIMESTAMP NOT NULL DEFAULT now()` |

**Table-level constraints:**
- `UNIQUE(tenant_id, code)`


### nx_warehouse_zones — Warehouse zones

| Column | Definition |
|---|---|
| `id` | `UUID PRIMARY KEY DEFAULT gen_random_uuid()` |
| `tenant_id` | `UUID NOT NULL` |
| `warehouse_id` | `UUID NOT NULL REFERENCES nx_warehouses(id) ON DELETE CASCADE` |
| `code` | `VARCHAR(50) NOT NULL` |
| `name` | `VARCHAR(255) NOT NULL` |
| `zone_type` | `VARCHAR(30) NOT NULL DEFAULT 'STORAGE'` |
| `zone_category` | `VARCHAR(30) DEFAULT 'DRY'` |
| `temperature_min` | `DECIMAL(6,2)` |
| `temperature_max` | `DECIMAL(6,2)` |
| `humidity_min` | `DECIMAL(5,2)` |
| `humidity_max` | `DECIMAL(5,2)` |
| `capacity_sqm` | `DECIMAL(12,2)` |
| `used_sqm` | `DECIMAL(12,2) DEFAULT 0` |
| `is_active` | `BOOLEAN DEFAULT true` |
| `is_hazardous` | `BOOLEAN DEFAULT false` |
| `security_level` | `VARCHAR(20) DEFAULT 'STANDARD'` |
| `metadata` | `JSONB` |
| `created_at` | `TIMESTAMP NOT NULL DEFAULT now()` |
| `updated_at` | `TIMESTAMP NOT NULL DEFAULT now()` |

**Table-level constraints:**
- `UNIQUE(warehouse_id, code)`


### nx_warehouse_bins — Storage bins

| Column | Definition |
|---|---|
| `id` | `UUID PRIMARY KEY DEFAULT gen_random_uuid()` |
| `tenant_id` | `UUID NOT NULL` |
| `warehouse_id` | `UUID NOT NULL REFERENCES nx_warehouses(id) ON DELETE CASCADE` |
| `zone_id` | `UUID REFERENCES nx_warehouse_zones(id) ON DELETE SET NULL` |
| `code` | `VARCHAR(50) NOT NULL` |
| `bin_type` | `VARCHAR(30) DEFAULT 'RACK'` |
| `bin_class` | `VARCHAR(20) DEFAULT 'A'` |
| `max_weight_kg` | `DECIMAL(10,2)` |
| `max_volume_cbm` | `DECIMAL(10,2)` |
| `current_weight_kg` | `DECIMAL(10,2) DEFAULT 0` |
| `current_volume_cbm` | `DECIMAL(10,2) DEFAULT 0` |
| `is_empty` | `BOOLEAN DEFAULT true` |
| `is_active` | `BOOLEAN DEFAULT true` |
| `is_reserved` | `BOOLEAN DEFAULT false` |
| `last_picked_at` | `TIMESTAMP` |
| `last_counted_at` | `TIMESTAMP` |
| `x_coord` | `INT` |
| `y_coord` | `INT` |
| `z_coord` | `INT` |
| `metadata` | `JSONB` |
| `created_at` | `TIMESTAMP NOT NULL DEFAULT now()` |
| `updated_at` | `TIMESTAMP NOT NULL DEFAULT now()` |

**Table-level constraints:**
- `UNIQUE(warehouse_id, code)`


### nx_warehouse_equipment — Warehouse equipment

| Column | Definition |
|---|---|
| `id` | `UUID PRIMARY KEY` |
| `tenant_id` | `UUID` |
| `warehouse_id` | `UUID NOT NULL` |
| `code` | `VARCHAR(120) NOT NULL` |
| `equipment_type` | `VARCHAR(100) NOT NULL` |
| `model` | `VARCHAR(255)` |
| `status` | `VARCHAR(50) NOT NULL` |
| `battery_level` | `INTEGER` |
| `last_maintenance_at` | `TIMESTAMP` |
| `next_maintenance_at` | `TIMESTAMP` |
| `assigned_to` | `UUID` |
| `is_active` | `BOOLEAN` |
| `created_at` | `TIMESTAMP NOT NULL` |
| `updated_at` | `TIMESTAMP NOT NULL` |
| `id` | `UUID PRIMARY KEY DEFAULT gen_random_uuid()` |
| `tenant_id` | `UUID NOT NULL` |
| `warehouse_id` | `UUID NOT NULL REFERENCES nx_warehouses(id) ON DELETE CASCADE` |
| `code` | `VARCHAR(50) NOT NULL` |
| `equipment_type` | `VARCHAR(50) NOT NULL` |
| `model` | `VARCHAR(100)` |
| `status` | `VARCHAR(20) DEFAULT 'AVAILABLE'` |
| `battery_level` | `INT DEFAULT 100` |
| `last_maintenance_at` | `TIMESTAMP` |
| `next_maintenance_at` | `TIMESTAMP` |
| `assigned_to` | `UUID` |
| `is_active` | `BOOLEAN DEFAULT true` |
| `created_at` | `TIMESTAMP NOT NULL DEFAULT now()` |
| `updated_at` | `TIMESTAMP NOT NULL DEFAULT now()` |

**Indexes:**
- `idx_equipment_wh_status (warehouse_id, status)`


### nx_warehouse_staff — Warehouse staff

| Column | Definition |
|---|---|
| `id` | `UUID PRIMARY KEY DEFAULT gen_random_uuid()` |
| `tenant_id` | `UUID NOT NULL` |
| `warehouse_id` | `UUID NOT NULL REFERENCES nx_warehouses(id) ON DELETE CASCADE` |
| `user_id` | `UUID` |
| `employee_code` | `VARCHAR(50) NOT NULL` |
| `first_name` | `VARCHAR(100) NOT NULL` |
| `last_name` | `VARCHAR(100) NOT NULL` |
| `role` | `VARCHAR(50) DEFAULT 'PICKER'` |
| `shift` | `VARCHAR(20) DEFAULT 'DAY'` |
| `skills` | `JSONB` |
| `productivity_score` | `DECIMAL(5,2) DEFAULT 0` |
| `items_picked_today` | `INT DEFAULT 0` |
| `items_packed_today` | `INT DEFAULT 0` |
| `is_active` | `BOOLEAN DEFAULT true` |
| `cert_expires_at` | `TIMESTAMP` |
| `created_at` | `TIMESTAMP NOT NULL DEFAULT now()` |
| `updated_at` | `TIMESTAMP NOT NULL DEFAULT now()` |

**Table-level constraints:**
- `UNIQUE(warehouse_id, employee_code)`


### nx_nodes — Fulfillment network nodes

| Column | Definition |
|---|---|
| `id` | `UUID PRIMARY KEY DEFAULT uuid_generate_v4()` |
| `tenant_id` | `UUID` |
| `name` | `VARCHAR(255) NOT NULL` |
| `type` | `VARCHAR(20) NOT NULL` |
| `address` | `JSONB` |
| `latitude` | `DECIMAL(10,7)` |
| `longitude` | `DECIMAL(10,7)` |
| `is_active` | `BOOLEAN DEFAULT TRUE` |
| `capacity_daily` | `INT` |
| `cut_off_time` | `TIME` |
| `carrier_config` | `JSONB` |

**Indexes:**
- `idx_nodes_tenant_active (tenant_id, is_active)`


### nx_atp_rules — Available-to-promise rules

| Column | Definition |
|---|---|
| `id` | `UUID PRIMARY KEY DEFAULT gen_random_uuid()` |
| `tenant_id` | `UUID NOT NULL` |
| `name` | `VARCHAR(255)` |
| `rule_type` | `VARCHAR(50) NOT NULL` |
| `priority` | `INTEGER NOT NULL DEFAULT 10` |
| `safety_stock_pct` | `DECIMAL(5, 2)` |
| `reserve_window_hours` | `INTEGER` |
| `active` | `BOOLEAN NOT NULL DEFAULT TRUE` |
| `created_at` | `TIMESTAMP NOT NULL DEFAULT NOW()` |
| `updated_at` | `TIMESTAMP NOT NULL DEFAULT NOW()` |

**Indexes:**
- `idx_atp_rule_tenant (tenant_id)`
- `idx_atp_rule_type (rule_type)`


### nx_atp_snapshots — ATP snapshot calculations

| Column | Definition |
|---|---|
| `id` | `UUID PRIMARY KEY DEFAULT gen_random_uuid()` |
| `tenant_id` | `UUID NOT NULL` |
| `node_id` | `UUID NOT NULL` |
| `sku` | `VARCHAR(100) NOT NULL` |
| `physical_stock` | `INTEGER NOT NULL` |
| `reserved_stock` | `INTEGER NOT NULL DEFAULT 0` |
| `safety_stock` | `INTEGER NOT NULL DEFAULT 0` |
| `allocated_stock` | `INTEGER NOT NULL DEFAULT 0` |
| `atp_quantity` | `INTEGER NOT NULL DEFAULT 0` |
| `total_demand` | `INTEGER NOT NULL DEFAULT 0` |
| `net_atp` | `INTEGER NOT NULL DEFAULT 0` |
| `snapshot_date` | `TIMESTAMP NOT NULL DEFAULT NOW()` |
| `created_at` | `TIMESTAMP NOT NULL DEFAULT NOW()` |

**Indexes:**
- `idx_atp_snap_tenant (tenant_id)`
- `idx_atp_snap_node_sku (node_id, sku)`
- `idx_atp_snap_date (snapshot_date)`


### nx_replenishment_rules — Replenishment rules

| Column | Definition |
|---|---|
| `id` | `UUID PRIMARY KEY DEFAULT gen_random_uuid()` |
| `tenant_id` | `UUID NOT NULL` |
| `warehouse_id` | `UUID NOT NULL` |
| `rule_name` | `VARCHAR(100) NOT NULL` |
| `rule_type` | `VARCHAR(30) NOT NULL` |
| `item_category` | `VARCHAR(50)` |
| `item_class` | `VARCHAR(50)` |
| `reorder_point` | `DECIMAL(12,2) DEFAULT 0` |
| `reorder_qty` | `DECIMAL(12,2) DEFAULT 0` |
| `safety_stock` | `DECIMAL(12,2) DEFAULT 0` |
| `max_stock` | `DECIMAL(12,2) DEFAULT 0` |
| `lead_time_days` | `INT DEFAULT 7` |
| `demand_window_days` | `INT DEFAULT 30` |
| `priority` | `INT DEFAULT 50` |
| `is_active` | `BOOLEAN DEFAULT TRUE` |
| `notes` | `TEXT` |
| `created_at` | `TIMESTAMP NOT NULL DEFAULT NOW()` |
| `updated_at` | `TIMESTAMP NOT NULL DEFAULT NOW()` |


### nx_replenishment_suggestions — Replenishment suggestions

| Column | Definition |
|---|---|
| `id` | `UUID PRIMARY KEY DEFAULT gen_random_uuid()` |
| `tenant_id` | `UUID NOT NULL` |
| `warehouse_id` | `UUID NOT NULL` |
| `inventory_item_id` | `UUID` |
| `sku` | `VARCHAR(50)` |
| `product_name` | `VARCHAR(200)` |
| `rule_id` | `UUID` |
| `rule_type` | `VARCHAR(30) NOT NULL` |
| `current_qty` | `INT DEFAULT 0` |
| `reorder_point` | `INT DEFAULT 0` |
| `suggested_qty` | `INT NOT NULL` |
| `priority` | `VARCHAR(10) NOT NULL` |
| `estimated_cost` | `DECIMAL(12,2)` |
| `estimated_delivery_days` | `INT` |
| `status` | `VARCHAR(20) NOT NULL DEFAULT 'PENDING'` |
| `approved_by` | `VARCHAR(100)` |
| `notes` | `TEXT` |
| `created_at` | `TIMESTAMP NOT NULL DEFAULT NOW()` |
| `updated_at` | `TIMESTAMP NOT NULL DEFAULT NOW()` |

**Indexes:**
- `idx_replenishment_suggestions_status (warehouse_id, status)`
- `idx_replenishment_suggestions_priority (priority)`


### nx_cycle_counts — Cycle count jobs

| Column | Definition |
|---|---|
| `id` | `UUID PRIMARY KEY DEFAULT uuid_generate_v4()` |
| `tenant_id` | `UUID` |
| `node_id` | `UUID` |
| `sku` | `VARCHAR(100) NOT NULL` |
| `product_name` | `VARCHAR(255)` |
| `expected_qty` | `INT NOT NULL` |
| `counted_qty` | `INT` |
| `status` | `VARCHAR(50) NOT NULL DEFAULT 'PENDING'` |
| `counted_by` | `VARCHAR(255)` |
| `notes` | `TEXT` |
| `created_at` | `TIMESTAMP NOT NULL DEFAULT NOW()` |
| `counted_at` | `TIMESTAMP` |
| `updated_at` | `TIMESTAMP NOT NULL DEFAULT NOW()` |

**Indexes:**
- `idx_cycle_counts_tenant (tenant_id, status)`


### nx_serialized_inventory — Serialized/RFID inventory

| Column | Definition |
|---|---|
| `id` | `UUID PRIMARY KEY DEFAULT gen_random_uuid()` |
| `tenant_id` | `UUID NOT NULL` |
| `location_id` | `UUID` |
| `epc` | `VARCHAR(128) NOT NULL` |
| `sku` | `VARCHAR(100)` |
| `status` | `VARCHAR(20) NOT NULL DEFAULT 'ACTIVE'` |
| `mode` | `VARCHAR(20) NOT NULL DEFAULT 'FULL_REGISTRY'` |
| `received_at` | `TIMESTAMP` |
| `last_seen_at` | `TIMESTAMP` |
| `created_at` | `TIMESTAMP NOT NULL DEFAULT now()` |
| `updated_at` | `TIMESTAMP NOT NULL DEFAULT now()` |

**Table-level constraints:**
- `UNIQUE (tenant_id, epc)`

**Indexes:**
- `idx_serialized_inv_location (tenant_id, location_id, status)`
- `idx_serialized_inv_sku (tenant_id, sku)`


### nx_rfid_scan_session — RFID scan sessions

| Column | Definition |
|---|---|
| `id` | `UUID PRIMARY KEY DEFAULT gen_random_uuid()` |
| `tenant_id` | `UUID NOT NULL` |
| `location_id` | `UUID` |
| `session_type` | `VARCHAR(20) NOT NULL` |
| `mode` | `VARCHAR(20) NOT NULL DEFAULT 'FULL_REGISTRY'` |
| `started_by` | `UUID` |
| `started_at` | `TIMESTAMP NOT NULL DEFAULT now()` |
| `completed_at` | `TIMESTAMP` |
| `status` | `VARCHAR(20) NOT NULL DEFAULT 'OPEN'` |
| `seen_epcs` | `JSONB` |
| `rejected_epcs` | `JSONB` |

**Indexes:**
- `idx_rfid_session_tenant (tenant_id, status)`


---

## Fulfillment


### nx_waves — Fulfillment waves

| Column | Definition |
|---|---|
| `id` | `UUID PRIMARY KEY` |
| `tenant_id` | `UUID NOT NULL` |
| `name` | `VARCHAR(255) NOT NULL` |
| `warehouse_id` | `UUID NOT NULL` |
| `status` | `VARCHAR(50) NOT NULL` |
| `priority` | `VARCHAR(50) NOT NULL` |
| `wave_type` | `VARCHAR(50) NOT NULL` |
| `order_count` | `INTEGER` |
| `total_line_items` | `INTEGER` |
| `released_line_items` | `INTEGER` |
| `completed_line_items` | `INTEGER` |
| `zone_filter` | `VARCHAR(255)` |
| `target_completion_at` | `TIMESTAMP` |
| `released_at` | `TIMESTAMP` |
| `completed_at` | `TIMESTAMP` |
| `released_by` | `VARCHAR(255)` |
| `completed_by` | `VARCHAR(255)` |
| `optimization_score` | `INTEGER` |
| `notes` | `TEXT` |
| `metadata` | `JSONB` |
| `created_at` | `TIMESTAMP NOT NULL` |
| `updated_at` | `TIMESTAMP NOT NULL` |

**Indexes:**
- `idx_waves_tenant_status (tenant_id, status)`


### nx_wave_rules — Wave building rules

| Column | Definition |
|---|---|
| `id` | `UUID PRIMARY KEY` |
| `tenant_id` | `UUID NOT NULL` |
| `wave_id` | `UUID NOT NULL` |
| `rule_type` | `VARCHAR(100) NOT NULL` |
| `operator` | `VARCHAR(50) NOT NULL` |
| `value` | `VARCHAR(255) NOT NULL` |
| `sequence` | `INTEGER NOT NULL` |
| `is_active` | `BOOLEAN NOT NULL` |
| `created_at` | `TIMESTAMP NOT NULL` |
| `updated_at` | `TIMESTAMP NOT NULL` |

**Indexes:**
- `idx_wave_rules_wave (wave_id)`


### nx_picklists — Picklists

| Column | Definition |
|---|---|
| `id` | `UUID PRIMARY KEY DEFAULT gen_random_uuid()` |
| `tenant_id` | `UUID NOT NULL` |
| `name` | `VARCHAR(255) NOT NULL` |
| `wave_type` | `VARCHAR(30) DEFAULT 'SINGLE_ORDER' CHECK (wave_type IN ('SINGLE_ORDER','BATCH','WAVE','ZONE'))` |
| `priority` | `VARCHAR(10) DEFAULT 'NORMAL' CHECK (priority IN ('LOW','NORMAL','HIGH','URGENT'))` |
| `status` | `VARCHAR(30) DEFAULT 'OPEN' CHECK (status IN ('OPEN','IN_PROGRESS','COMPLETED','CANCELLED'))` |
| `assignee_id` | `UUID` |
| `total_items` | `INT DEFAULT 0` |
| `picked_items` | `INT DEFAULT 0` |
| `order_ids` | `UUID[]` |
| `notes` | `TEXT` |
| `started_at` | `TIMESTAMP` |
| `completed_at` | `TIMESTAMP` |
| `created_by` | `VARCHAR(255)` |
| `created_at` | `TIMESTAMP DEFAULT now()` |
| `updated_at` | `TIMESTAMP DEFAULT now()` |

**Indexes:**
- `idx_nx_picklists_tenant (tenant_id)`
- `idx_nx_picklists_status (status)`
- `idx_nx_picklists_assignee (assignee_id)`


### nx_picklist_items — Picklist line items

| Column | Definition |
|---|---|
| `id` | `UUID PRIMARY KEY DEFAULT gen_random_uuid()` |
| `picklist_id` | `UUID NOT NULL REFERENCES nx_picklists(id) ON DELETE CASCADE` |
| `tenant_id` | `UUID NOT NULL` |
| `order_id` | `UUID NOT NULL` |
| `order_item_id` | `UUID` |
| `sku` | `VARCHAR(255) NOT NULL` |
| `product_name` | `VARCHAR(512)` |
| `quantity` | `INT NOT NULL` |
| `picked_quantity` | `INT DEFAULT 0` |
| `from_bin_id` | `UUID` |
| `from_location` | `VARCHAR(255)` |
| `status` | `VARCHAR(20) DEFAULT 'PENDING' CHECK (status IN ('PENDING','PICKING','PICKED','SKIPPED','CANCELLED'))` |
| `picked_at` | `TIMESTAMP` |
| `picked_by` | `UUID` |
| `notes` | `TEXT` |
| `created_at` | `TIMESTAMP DEFAULT now()` |

**Indexes:**
- `idx_nx_picklist_items_picklist (picklist_id)`
- `idx_nx_picklist_items_status (status)`
- `idx_nx_picklist_items_order (order_id)`


### nx_pickers — Picker assignments

| Column | Definition |
|---|---|
| `id` | `UUID PRIMARY KEY DEFAULT gen_random_uuid()` |
| `tenant_id` | `UUID NOT NULL` |
| `node_id` | `UUID NOT NULL` |
| `user_id` | `UUID NOT NULL` |
| `name` | `VARCHAR(255) NOT NULL` |
| `employee_id` | `VARCHAR(50)` |
| `status` | `VARCHAR(50) NOT NULL DEFAULT 'AVAILABLE'` |
| `current_order_id` | `UUID` |
| `max_concurrent_orders` | `INTEGER NOT NULL DEFAULT 3` |
| `orders_completed_today` | `INTEGER NOT NULL DEFAULT 0` |
| `items_picked_today` | `INTEGER NOT NULL DEFAULT 0` |
| `last_active_at` | `TIMESTAMP` |
| `shift_start` | `TIMESTAMP` |
| `shift_end` | `TIMESTAMP` |
| `active` | `BOOLEAN NOT NULL DEFAULT TRUE` |
| `created_at` | `TIMESTAMP NOT NULL DEFAULT NOW()` |
| `updated_at` | `TIMESTAMP NOT NULL DEFAULT NOW()` |

**Indexes:**
- `idx_picker_tenant (tenant_id)`
- `idx_picker_node (node_id)`
- `idx_picker_user (user_id)`
- `idx_picker_status (status)`


### nx_picker_assignments — Picker → task assignments

| Column | Definition |
|---|---|
| `id` | `UUID PRIMARY KEY DEFAULT gen_random_uuid()` |
| `tenant_id` | `UUID NOT NULL` |
| `picker_id` | `UUID NOT NULL` |
| `pickup_order_id` | `UUID NOT NULL` |
| `order_number` | `VARCHAR(50) NOT NULL` |
| `node_id` | `UUID NOT NULL` |
| `status` | `VARCHAR(50) NOT NULL DEFAULT 'ASSIGNED'` |
| `assigned_at` | `TIMESTAMP NOT NULL DEFAULT NOW()` |
| `started_at` | `TIMESTAMP` |
| `completed_at` | `TIMESTAMP` |
| `priority` | `INTEGER NOT NULL DEFAULT 10` |
| `created_at` | `TIMESTAMP NOT NULL DEFAULT NOW()` |
| `updated_at` | `TIMESTAMP NOT NULL DEFAULT NOW()` |

**Indexes:**
- `idx_picker_assign_picker (picker_id)`
- `idx_picker_assign_node (node_id)`
- `idx_picker_assign_status (status)`
- `idx_picker_assign_order (pickup_order_id)`


### nx_packages — Packages

| Column | Definition |
|---|---|
| `id` | `UUID PRIMARY KEY DEFAULT gen_random_uuid()` |
| `tenant_id` | `UUID NOT NULL` |
| `order_id` | `UUID NOT NULL` |
| `picklist_id` | `UUID REFERENCES nx_picklists(id)` |
| `package_type` | `VARCHAR(30) DEFAULT 'BOX' CHECK (package_type IN ('BOX','PALLET','CRATE','ENVELOPE','TUBE','BAG'))` |
| `box_name` | `VARCHAR(255)` |
| `weight_lbs` | `DECIMAL(8,2)` |
| `width_in` | `DECIMAL(6,1)` |
| `height_in` | `DECIMAL(6,1)` |
| `depth_in` | `DECIMAL(6,1)` |
| `items` | `JSONB` |
| `item_count` | `INT DEFAULT 0` |
| `tracking_number` | `VARCHAR(255)` |
| `carrier_id` | `UUID` |
| `carrier_name` | `VARCHAR(255)` |
| `service_level` | `VARCHAR(100)` |
| `label_url` | `TEXT` |
| `label_format` | `VARCHAR(10) DEFAULT 'PDF'` |
| `shipping_cost` | `DECIMAL(10,2)` |
| `status` | `VARCHAR(20) DEFAULT 'PENDING_PACK' CHECK (status IN ('PENDING_PACK','PACKING','PACKED','LABELED','SHIPPED','VOIDED'))` |
| `notes` | `TEXT` |
| `packed_by` | `VARCHAR(255)` |
| `packed_at` | `TIMESTAMP` |
| `shipped_at` | `TIMESTAMP` |
| `created_at` | `TIMESTAMP DEFAULT now()` |
| `updated_at` | `TIMESTAMP DEFAULT now()` |

**Indexes:**
- `idx_nx_packages_tenant (tenant_id)`
- `idx_nx_packages_order (order_id)`
- `idx_nx_packages_status (status)`
- `idx_nx_packages_carrier (carrier_id)`


### nx_shipments — Shipments

| Column | Definition |
|---|---|
| `id` | `UUID PRIMARY KEY DEFAULT uuid_generate_v4()` |
| `order_id` | `UUID NOT NULL REFERENCES nx_orders(id)` |
| `tenant_id` | `UUID` |
| `carrier_id` | `VARCHAR(100)` |
| `service_level` | `VARCHAR(50)` |
| `tracking_number` | `VARCHAR(255)` |
| `label_url` | `TEXT` |
| `label_format` | `VARCHAR(10)` |
| `voided` | `BOOLEAN DEFAULT FALSE` |
| `rate` | `JSONB` |
| `cost_components` | `JSONB` |
| `origin_node_id` | `UUID` |
| `estimated_delivery` | `TIMESTAMP` |
| `actual_delivery` | `TIMESTAMP` |
| `status` | `VARCHAR(50) NOT NULL DEFAULT 'PENDING'` |
| `created_at` | `TIMESTAMP NOT NULL DEFAULT NOW()` |
| `shipped_at` | `TIMESTAMP` |
| `manifest_closed_at` | `TIMESTAMP` |

**Indexes:**
- `idx_shipments_tracking (tracking_number)`
- `idx_shipments_order (order_id)`


### nx_tracking_events — Tracking events

| Column | Definition |
|---|---|
| `id` | `UUID PRIMARY KEY DEFAULT uuid_generate_v4()` |
| `shipment_id` | `UUID NOT NULL REFERENCES nx_shipments(id)` |
| `event_type` | `VARCHAR(100)` |
| `location` | `VARCHAR(255)` |
| `timestamp` | `TIMESTAMP` |
| `description` | `TEXT` |
| `raw_data` | `JSONB` |

**Indexes:**
- `idx_tracking_shipment (shipment_id)`


### nx_shipping_labels — Shipping labels

| Column | Definition |
|---|---|
| `id` | `UUID PRIMARY KEY DEFAULT gen_random_uuid()` |
| `tenant_id` | `UUID NOT NULL` |
| `order_id` | `UUID NOT NULL` |
| `order_number` | `VARCHAR(50) NOT NULL` |
| `pickup_order_id` | `UUID` |
| `carrier` | `VARCHAR(50) NOT NULL` |
| `service_type` | `VARCHAR(50) NOT NULL` |
| `tracking_number` | `VARCHAR(100)` |
| `label_url` | `VARCHAR(500)` |
| `label_base64` | `TEXT` |
| `status` | `VARCHAR(50) NOT NULL DEFAULT 'GENERATED'` |
| `generated_at` | `TIMESTAMP NOT NULL DEFAULT NOW()` |
| `printed_at` | `TIMESTAMP` |
| `attached_at` | `TIMESTAMP` |
| `from_name` | `VARCHAR(255)` |
| `from_address` | `TEXT` |
| `to_name` | `VARCHAR(255)` |
| `to_address` | `TEXT` |
| `weight` | `DOUBLE PRECISION` |
| `dimensions` | `VARCHAR(100)` |
| `created_at` | `TIMESTAMP NOT NULL DEFAULT NOW()` |
| `updated_at` | `TIMESTAMP NOT NULL DEFAULT NOW()` |

**Indexes:**
- `idx_label_tenant (tenant_id)`
- `idx_label_order (order_id)`
- `idx_label_pickup (pickup_order_id)`
- `idx_label_tracking (tracking_number)`
- `idx_label_status (status)`


### nx_fulfillment_exceptions — Fulfillment exceptions

| Column | Definition |
|---|---|
| `id` | `UUID PRIMARY KEY DEFAULT gen_random_uuid()` |
| `order_id` | `UUID NOT NULL REFERENCES nx_orders(id) ON DELETE CASCADE` |
| `allocation_id` | `UUID REFERENCES nx_order_allocations(id) ON DELETE SET NULL` |
| `tenant_id` | `UUID NOT NULL` |
| `type` | `VARCHAR(50) NOT NULL CHECK (type IN (
        'INVENTORY_SHORTAGE','CARRIER_FAILURE','CAPACITY_EXCEEDED','WORKER_ABSENT',
        'WEATHER_DELAY','SHIPPING_ADDRESS_ISSUE','PAYMENT_HOLD','CREDIT_HOLD',
        'FRAUD_FLAG','CUSTOMER_REQUEST','SYSTEM_ERROR','OTHER'
    ))` |
| `severity` | `VARCHAR(20) NOT NULL DEFAULT 'MEDIUM' CHECK (severity IN ('LOW','MEDIUM','HIGH','CRITICAL'))` |
| `status` | `VARCHAR(30) NOT NULL DEFAULT 'OPEN' CHECK (status IN ('OPEN','IN_PROGRESS','RESOLVED','ESCALATED','CLOSED'))` |
| `title` | `VARCHAR(255) NOT NULL` |
| `description` | `TEXT` |
| `resolution` | `TEXT` |
| `suggested_action` | `TEXT` |
| `auto_resolvable` | `BOOLEAN DEFAULT false` |
| `resolution_strategy` | `VARCHAR(50) CHECK (resolution_strategy IN (
        'REALLOCATE','OVERRIDE','CONTACT_CUSTOMER','ESCALATE_MANAGER',
        'SPLIT_ORDER','BACKORDER','SUBSTITUTE','CANCEL_ORDER'
    ))` |
| `detected_at` | `TIMESTAMP DEFAULT now()` |
| `resolved_at` | `TIMESTAMP` |
| `resolved_by` | `UUID` |
| `assigned_to` | `UUID` |
| `escalated_at` | `TIMESTAMP` |
| `metadata` | `JSONB` |
| `created_at` | `TIMESTAMP DEFAULT now()` |
| `updated_at` | `TIMESTAMP DEFAULT now()` |

**Indexes:**
- `idx_nx_fulfillment_exceptions_order (order_id)`
- `idx_nx_fulfillment_exceptions_tenant (tenant_id)`
- `idx_nx_fulfillment_exceptions_status (status)`
- `idx_nx_fulfillment_exceptions_type (type)`
- `idx_nx_fulfillment_exceptions_severity (severity)`


### nx_fulfillment_limits — Fulfillment capacity limits

| Column | Definition |
|---|---|
| `id` | `UUID PRIMARY KEY DEFAULT gen_random_uuid()` |
| `tenant_id` | `UUID NOT NULL` |
| `node_id` | `UUID NOT NULL` |
| `max_orders_per_day` | `INTEGER` |
| `max_orders_per_week` | `INTEGER` |
| `max_items_per_day` | `INTEGER` |
| `current_orders_today` | `INTEGER DEFAULT 0` |
| `current_orders_this_week` | `INTEGER DEFAULT 0` |
| `current_items_today` | `INTEGER DEFAULT 0` |
| `fulfillment_enabled` | `BOOLEAN NOT NULL DEFAULT TRUE` |
| `alert_threshold` | `DECIMAL(5, 2) DEFAULT 0.80` |
| `last_reset_at` | `TIMESTAMP` |
| `created_at` | `TIMESTAMP NOT NULL DEFAULT NOW()` |
| `updated_at` | `TIMESTAMP NOT NULL DEFAULT NOW()` |

**Table-level constraints:**
- `UNIQUE(tenant_id, node_id)`

**Indexes:**
- `idx_fulfillment_tenant (tenant_id)`
- `idx_fulfillment_node (node_id)`


### nx_fulfillment_capacity_log — Fulfillment capacity usage log

| Column | Definition |
|---|---|
| `id` | `UUID PRIMARY KEY DEFAULT gen_random_uuid()` |
| `tenant_id` | `UUID NOT NULL` |
| `node_id` | `UUID NOT NULL` |
| `order_id` | `UUID` |
| `action` | `VARCHAR(50) NOT NULL` |
| `orders_before` | `INTEGER` |
| `orders_after` | `INTEGER` |
| `capacity_percentage` | `DECIMAL(5, 2)` |
| `created_at` | `TIMESTAMP NOT NULL DEFAULT NOW()` |

**Indexes:**
- `idx_capacity_log_tenant (tenant_id)`
- `idx_capacity_log_node (node_id)`
- `idx_capacity_log_created (created_at)`


### nx_productivity_log — Picker/worker productivity log

| Column | Definition |
|---|---|
| `id` | `UUID PRIMARY KEY DEFAULT gen_random_uuid()` |
| `tenant_id` | `UUID NOT NULL` |
| `warehouse_id` | `UUID NOT NULL` |
| `staff_id` | `UUID NOT NULL` |
| `labor_entry_id` | `UUID` |
| `task_type` | `VARCHAR(50) NOT NULL` |
| `items_completed` | `INT DEFAULT 0` |
| `time_spent_minutes` | `INT DEFAULT 0` |
| `items_per_hour` | `DECIMAL(8,2) DEFAULT 0` |
| `quality_score` | `DECIMAL(5,2) DEFAULT 100.0` |
| `vs_standard_pct` | `DECIMAL(8,2) DEFAULT 0` |
| `logged_at` | `TIMESTAMP NOT NULL DEFAULT NOW()` |

**Indexes:**
- `idx_productivity_log_staff (staff_id)`
- `idx_productivity_log_warehouse (warehouse_id, logged_at)`


### nx_box_templates — Box/package templates

| Column | Definition |
|---|---|
| `id` | `UUID PRIMARY KEY` |
| `tenant_id` | `UUID NOT NULL` |
| `name` | `VARCHAR(120) NOT NULL` |
| `width_in` | `DOUBLE PRECISION NOT NULL` |
| `height_in` | `DOUBLE PRECISION NOT NULL` |
| `depth_in` | `DOUBLE PRECISION NOT NULL` |
| `volume_capacity_in3` | `DOUBLE PRECISION NOT NULL` |
| `max_weight_lbs` | `DOUBLE PRECISION NOT NULL` |
| `max_item_count` | `INTEGER NOT NULL DEFAULT 0` |
| `is_active` | `BOOLEAN NOT NULL DEFAULT TRUE` |
| `created_at` | `TIMESTAMP NOT NULL` |
| `updated_at` | `TIMESTAMP NOT NULL` |

**Indexes:**
- `idx_box_templates_tenant (tenant_id, is_active)`


### nx_kit_templates — Kit/BOM templates

| Column | Definition |
|---|---|
| `id` | `UUID PRIMARY KEY` |
| `tenant_id` | `UUID NOT NULL` |
| `kit_sku` | `VARCHAR(120) NOT NULL` |
| `name` | `VARCHAR(200) NOT NULL` |
| `components` | `JSONB NOT NULL` |
| `is_active` | `BOOLEAN NOT NULL DEFAULT TRUE` |
| `created_at` | `TIMESTAMP NOT NULL` |
| `updated_at` | `TIMESTAMP NOT NULL` |

**Indexes:**
- `uq_kit_templates_tenant_sku (tenant_id, kit_sku) UNIQUE`


---

## Slotting, Engineering & Labor


### nx_slotting_rules — Slotting rules

| Column | Definition |
|---|---|
| `id` | `UUID PRIMARY KEY` |
| `tenant_id` | `UUID` |
| `warehouse_id` | `UUID NOT NULL` |
| `rule_name` | `VARCHAR(255) NOT NULL` |
| `rule_type` | `VARCHAR(100) NOT NULL` |
| `criteria` | `JSONB` |
| `target_zone_id` | `UUID` |
| `target_bin_class` | `VARCHAR(100)` |
| `priority` | `INTEGER NOT NULL` |
| `is_active` | `BOOLEAN` |
| `effectiveness` | `DOUBLE PRECISION NOT NULL` |
| `last_applied_at` | `TIMESTAMP` |
| `apply_count` | `INTEGER` |
| `notes` | `TEXT` |
| `metadata` | `JSONB` |
| `created_at` | `TIMESTAMP NOT NULL` |
| `updated_at` | `TIMESTAMP NOT NULL` |

**Indexes:**
- `idx_slotting_rules_warehouse (warehouse_id, rule_type)`


### nx_slotting_assignments — Slotting assignments

| Column | Definition |
|---|---|
| `id` | `UUID PRIMARY KEY` |
| `tenant_id` | `UUID` |
| `warehouse_id` | `UUID NOT NULL` |
| `sku` | `VARCHAR(120) NOT NULL` |
| `product_name` | `VARCHAR(255)` |
| `bin_id` | `UUID NOT NULL` |
| `zone_id` | `UUID` |
| `assigned_quantity` | `INTEGER` |
| `velocity_class` | `VARCHAR(50)` |
| `last_picked_at` | `TIMESTAMP` |
| `pick_frequency` | `INTEGER` |
| `last_slotting_at` | `TIMESTAMP` |
| `slotting_score` | `DOUBLE PRECISION` |
| `assigned_by` | `VARCHAR(255)` |
| `rule_id` | `UUID` |
| `notes` | `TEXT` |
| `metadata` | `JSONB` |
| `created_at` | `TIMESTAMP NOT NULL` |
| `updated_at` | `TIMESTAMP NOT NULL` |

**Indexes:**
- `uq_slotting_assignments_wh_bin_sku (warehouse_id, bin_id, sku) UNIQUE`


### nx_slotting_audits — Slotting audit trail

| Column | Definition |
|---|---|
| `id` | `UUID PRIMARY KEY` |
| `tenant_id` | `UUID` |
| `warehouse_id` | `UUID NOT NULL` |
| `sku` | `VARCHAR(120) NOT NULL` |
| `product_name` | `VARCHAR(255)` |
| `from_bin_id` | `UUID` |
| `from_bin_code` | `VARCHAR(100)` |
| `to_bin_id` | `UUID` |
| `to_bin_code` | `VARCHAR(100)` |
| `from_zone_id` | `UUID` |
| `to_zone_id` | `UUID` |
| `reason` | `VARCHAR(255)` |
| `action` | `VARCHAR(100)` |
| `moved_quantity` | `INTEGER` |
| `performed_by` | `VARCHAR(255)` |
| `notes` | `TEXT` |
| `created_at` | `TIMESTAMP NOT NULL` |

**Indexes:**
- `idx_slotting_audits_wh_time (warehouse_id, created_at)`


### nx_engineered_standards — Engineered labor standards

| Column | Definition |
|---|---|
| `id` | `UUID PRIMARY KEY DEFAULT gen_random_uuid()` |
| `tenant_id` | `UUID NOT NULL` |
| `warehouse_id` | `UUID NOT NULL` |
| `task_type` | `VARCHAR(255) NOT NULL` |
| `uom` | `VARCHAR(255) NOT NULL` |
| `standard_value` | `DOUBLE PRECISION NOT NULL` |
| `category` | `VARCHAR(255)` |
| `complexity_level` | `VARCHAR(255)` |
| `is_active` | `BOOLEAN DEFAULT TRUE` |
| `effective_from` | `DATE` |
| `effective_to` | `DATE` |
| `notes` | `TEXT` |
| `created_at` | `TIMESTAMP NOT NULL DEFAULT NOW()` |
| `updated_at` | `TIMESTAMP NOT NULL DEFAULT NOW()` |


### nx_labor_entries — Labor time entries

| Column | Definition |
|---|---|
| `id` | `UUID PRIMARY KEY DEFAULT gen_random_uuid()` |
| `tenant_id` | `UUID NOT NULL` |
| `warehouse_id` | `UUID NOT NULL` |
| `staff_id` | `UUID NOT NULL` |
| `employee_code` | `VARCHAR(255)` |
| `first_name` | `VARCHAR(255)` |
| `last_name` | `VARCHAR(255)` |
| `task_type` | `VARCHAR(255)` |
| `status` | `VARCHAR(255)` |
| `shift` | `VARCHAR(255)` |
| `clocked_in_at` | `TIMESTAMP` |
| `clocked_out_at` | `TIMESTAMP` |
| `break_started_at` | `TIMESTAMP` |
| `break_ended_at` | `TIMESTAMP` |
| `total_work_minutes` | `INT` |
| `total_break_minutes` | `INT` |
| `lines_picked` | `INT` |
| `lines_packed` | `INT` |
| `units_received` | `INT` |
| `units_shipped` | `INT` |
| `error_count` | `INT` |
| `productivity_score` | `DOUBLE PRECISION` |
| `efficiency_rating` | `VARCHAR(255)` |
| `current_task` | `VARCHAR(255)` |
| `current_wave_id` | `UUID` |
| `notes` | `TEXT` |
| `metadata` | `JSONB` |
| `created_at` | `TIMESTAMP NOT NULL DEFAULT NOW()` |
| `updated_at` | `TIMESTAMP NOT NULL DEFAULT NOW()` |


### nx_shift_schedules — Labor shift schedules

| Column | Definition |
|---|---|
| `id` | `UUID PRIMARY KEY` |
| `tenant_id` | `UUID NOT NULL` |
| `warehouse_id` | `UUID NOT NULL` |
| `staff_id` | `UUID NOT NULL` |
| `employee_code` | `VARCHAR(255)` |
| `first_name` | `VARCHAR(255)` |
| `last_name` | `VARCHAR(255)` |
| `shift_date` | `DATE NOT NULL` |
| `shift_type` | `VARCHAR(255) NOT NULL` |
| `scheduled_start` | `TIME NOT NULL` |
| `scheduled_end` | `TIME NOT NULL` |
| `actual_start` | `TIME` |
| `actual_end` | `TIME` |
| `status` | `VARCHAR(255)` |
| `notes` | `VARCHAR(255)` |
| `created_at` | `TIMESTAMP NOT NULL` |
| `updated_at` | `TIMESTAMP NOT NULL` |

**Indexes:**
- `idx_shift_schedules_tenant (tenant_id)`
- `idx_shift_schedules_staff (staff_id)`
- `idx_shift_schedules_date (shift_date)`


---

## Shipping, Carriers & Yard


### nx_carriers — Carriers

| Column | Definition |
|---|---|
| `id` | `UUID PRIMARY KEY DEFAULT gen_random_uuid()` |
| `tenant_id` | `UUID NOT NULL` |
| `name` | `VARCHAR(255) NOT NULL` |
| `code` | `VARCHAR(50) NOT NULL` |
| `type` | `VARCHAR(50) NOT NULL DEFAULT 'SHIPPING'` |
| `status` | `VARCHAR(20) NOT NULL DEFAULT 'ACTIVE'` |
| `account_number` | `VARCHAR(100)` |
| `api_key_encrypted` | `TEXT` |
| `api_secret_encrypted` | `TEXT` |
| `otd_rate` | `NUMERIC(5,2) DEFAULT 0` |
| `avg_cost` | `NUMERIC(10,2) DEFAULT 0` |
| `total_shipments` | `BIGINT DEFAULT 0` |
| `damage_rate` | `NUMERIC(5,2) DEFAULT 0` |
| `metadata` | `JSONB` |
| `is_active` | `BOOLEAN DEFAULT true` |
| `created_at` | `TIMESTAMP DEFAULT now()` |
| `updated_at` | `TIMESTAMP DEFAULT now()` |

**Indexes:**
- `idx_nx_carriers_tenant (tenant_id)`
- `idx_nx_carriers_code (code)`
- `idx_nx_carriers_status (status)`


### nx_carrier_accounts — Carrier accounts

| Column | Definition |
|---|---|
| `id` | `UUID PRIMARY KEY DEFAULT uuid_generate_v4()` |
| `tenant_id` | `UUID` |
| `carrier_id` | `VARCHAR(100) NOT NULL` |
| `account_number` | `VARCHAR(255)` |
| `api_key_encrypted` | `TEXT` |
| `api_secret_encrypted` | `TEXT` |
| `negotiated_discount` | `DECIMAL(5,4) DEFAULT 0.0000` |
| `contract_effective` | `DATE` |
| `contract_expiry` | `DATE` |
| `is_active` | `BOOLEAN DEFAULT TRUE` |
| `node_id` | `UUID REFERENCES nx_nodes(id)` |


### nx_carrier_rates — Carrier rates

| Column | Definition |
|---|---|
| `id` | `UUID PRIMARY KEY DEFAULT gen_random_uuid()` |
| `tenant_id` | `UUID NOT NULL` |
| `carrier_code` | `VARCHAR(50) NOT NULL` |
| `carrier_name` | `VARCHAR(255) NOT NULL` |
| `service_level` | `VARCHAR(50) NOT NULL CHECK (service_level IN ('GROUND','EXPRESS','OVERNIGHT','TWO_DAY','THREE_DAY','FREIGHT_LTL','FREIGHT_FTL','INTERNATIONAL_ECONOMY','INTERNATIONAL_EXPRESS','SAME_DAY'))` |
| `service_name` | `VARCHAR(255) NOT NULL` |
| `zone` | `VARCHAR(10)` |
| `weight_min_kg` | `DECIMAL(10,3) DEFAULT 0` |
| `weight_max_kg` | `DECIMAL(10,3) DEFAULT 99999` |
| `base_rate` | `DECIMAL(12,2) NOT NULL` |
| `per_kg_rate` | `DECIMAL(12,4) DEFAULT 0` |
| `fuel_surcharge_pct` | `DECIMAL(5,2) DEFAULT 0` |
| `residential_surcharge` | `DECIMAL(12,2) DEFAULT 0` |
| `transit_days_min` | `INT DEFAULT 1` |
| `transit_days_max` | `INT DEFAULT 10` |
| `is_active` | `BOOLEAN DEFAULT true` |
| `effective_date` | `DATE` |
| `expiry_date` | `DATE` |
| `metadata` | `JSONB` |
| `created_at` | `TIMESTAMP DEFAULT now()` |
| `updated_at` | `TIMESTAMP DEFAULT now()` |

**Indexes:**
- `idx_nx_carrier_rates_tenant (tenant_id)`
- `idx_nx_carrier_rates_carrier (carrier_code)`
- `idx_nx_carrier_rates_service (service_level)`
- `idx_nx_carrier_rates_active (tenant_id, is_active)`


### nx_carrier_zones — Carrier zones

| Column | Definition |
|---|---|
| `id` | `UUID PRIMARY KEY DEFAULT gen_random_uuid()` |
| `tenant_id` | `UUID NOT NULL` |
| `carrier_code` | `VARCHAR(50) NOT NULL` |
| `zone_code` | `VARCHAR(10) NOT NULL` |
| `zip_prefix` | `VARCHAR(10) NOT NULL` |
| `country` | `VARCHAR(10) DEFAULT 'US'` |
| `is_origin` | `BOOLEAN DEFAULT false` |
| `is_destination` | `BOOLEAN DEFAULT true` |
| `created_at` | `TIMESTAMP DEFAULT now()` |

**Indexes:**
- `idx_nx_carrier_zones_carrier (carrier_code)`
- `idx_nx_carrier_zones_zip (zip_prefix)`


### nx_rate_cards — Rate cards

| Column | Definition |
|---|---|
| `id` | `UUID PRIMARY KEY` |
| `tenant_id` | `UUID NOT NULL` |
| `client_id` | `UUID` |
| `client_name` | `VARCHAR(255)` |
| `currency` | `VARCHAR(8) NOT NULL DEFAULT 'USD'` |
| `per_order_fee` | `NUMERIC(12,2) NOT NULL DEFAULT 0` |
| `per_line_fee` | `NUMERIC(12,2) NOT NULL DEFAULT 0` |
| `picking_fee_per_line` | `NUMERIC(12,2) NOT NULL DEFAULT 0` |
| `storage_fee_per_unit` | `NUMERIC(12,2) NOT NULL DEFAULT 0` |
| `description` | `TEXT` |
| `effective_from` | `TIMESTAMP` |
| `effective_to` | `TIMESTAMP` |
| `is_active` | `BOOLEAN NOT NULL DEFAULT TRUE` |
| `created_at` | `TIMESTAMP NOT NULL DEFAULT NOW()` |
| `updated_at` | `TIMESTAMP NOT NULL DEFAULT NOW()` |

**Indexes:**
- `idx_rate_cards_tenant (tenant_id)`
- `idx_rate_cards_client (client_id)`


### nx_rate_shopping_log — Rate shopping log

| Column | Definition |
|---|---|
| `id` | `UUID PRIMARY KEY DEFAULT gen_random_uuid()` |
| `order_id` | `UUID` |
| `tenant_id` | `UUID NOT NULL` |
| `from_zip` | `VARCHAR(20)` |
| `to_zip` | `VARCHAR(20)` |
| `to_country` | `VARCHAR(10)` |
| `total_weight_kg` | `DECIMAL(10,3)` |
| `declared_value` | `DECIMAL(12,2)` |
| `num_packages` | `INT DEFAULT 1` |
| `results` | `JSONB` |
| `selected_carrier_code` | `VARCHAR(50)` |
| `selected_service` | `VARCHAR(50)` |
| `total_cost` | `DECIMAL(12,2)` |
| `estimated_delivery_days` | `INT` |
| `execution_time_ms` | `INT` |
| `created_at` | `TIMESTAMP DEFAULT now()` |

**Indexes:**
- `idx_nx_rate_shopping_log_tenant (tenant_id)`
- `idx_nx_rate_shopping_log_order (order_id)`


### nx_trailers — Trailers

| Column | Definition |
|---|---|
| `id` | `UUID PRIMARY KEY DEFAULT gen_random_uuid()` |
| `tenant_id` | `UUID NOT NULL` |
| `warehouse_id` | `UUID NOT NULL` |
| `trailer_number` | `VARCHAR(50) NOT NULL` |
| `carrier_code` | `VARCHAR(30)` |
| `status` | `VARCHAR(20) NOT NULL DEFAULT 'IN_YARD'` |
| `current_dock_door_id` | `UUID` |
| `current_yard_location_id` | `UUID` |
| `checked_in_at` | `TIMESTAMP` |
| `checked_out_at` | `TIMESTAMP` |
| `docked_at` | `TIMESTAMP` |
| `last_event_at` | `TIMESTAMP` |
| `loaded` | `BOOLEAN DEFAULT FALSE` |
| `pallet_count` | `INT DEFAULT 0` |
| `seal_number` | `VARCHAR(50)` |
| `license_plate` | `VARCHAR(30)` |
| `dwelled_minutes` | `INT DEFAULT 0` |
| `notes` | `TEXT` |
| `created_at` | `TIMESTAMP NOT NULL DEFAULT NOW()` |
| `updated_at` | `TIMESTAMP NOT NULL DEFAULT NOW()` |

**Table-level constraints:**
- `UNIQUE(tenant_id, warehouse_id, trailer_number)`

**Indexes:**
- `idx_trailers_warehouse (warehouse_id, status)`


### nx_trailer_events — Trailer lifecycle events

| Column | Definition |
|---|---|
| `id` | `UUID PRIMARY KEY DEFAULT gen_random_uuid()` |
| `tenant_id` | `UUID NOT NULL` |
| `warehouse_id` | `UUID NOT NULL` |
| `trailer_number` | `VARCHAR(50) NOT NULL` |
| `event_type` | `VARCHAR(30) NOT NULL` |
| `dock_door_id` | `UUID` |
| `yard_location_id` | `UUID` |
| `appointment_id` | `UUID` |
| `carrier_code` | `VARCHAR(30)` |
| `driver_name` | `VARCHAR(100)` |
| `seal_number` | `VARCHAR(50)` |
| `license_plate` | `VARCHAR(30)` |
| `loaded` | `BOOLEAN DEFAULT FALSE` |
| `pallet_count` | `INT DEFAULT 0` |
| `weight_kg` | `DECIMAL(10,2)` |
| `condition_notes` | `TEXT` |
| `performed_by` | `VARCHAR(100)` |
| `event_time` | `TIMESTAMP NOT NULL DEFAULT NOW()` |
| `created_at` | `TIMESTAMP NOT NULL DEFAULT NOW()` |

**Indexes:**
- `idx_trailer_events_trailer (trailer_number)`
- `idx_trailer_events_warehouse (warehouse_id, event_time)`
- `idx_trailer_events_type (event_type)`


### nx_dock_doors — Dock doors

| Column | Definition |
|---|---|
| `id` | `UUID PRIMARY KEY` |
| `tenant_id` | `UUID` |
| `warehouse_id` | `UUID` |
| `door_number` | `VARCHAR(255) NOT NULL` |
| `door_type` | `VARCHAR(255)` |
| `status` | `VARCHAR(255) NOT NULL` |
| `dock_height` | `VARCHAR(255)` |
| `has_leveler` | `BOOLEAN` |
| `has_seal` | `BOOLEAN` |
| `max_width_cm` | `INTEGER` |
| `max_height_cm` | `INTEGER` |
| `max_weight_kg` | `DOUBLE PRECISION` |
| `current_vehicle_id` | `UUID` |
| `current_appointment_id` | `UUID` |
| `zone_id` | `UUID` |
| `notes` | `VARCHAR(255)` |
| `metadata` | `JSONB` |
| `created_at` | `TIMESTAMP NOT NULL` |
| `updated_at` | `TIMESTAMP NOT NULL` |

**Indexes:**
- `idx_dock_doors_tenant (tenant_id)`
- `idx_dock_doors_warehouse (warehouse_id)`
- `idx_dock_doors_status (status)`


### nx_appointments — Dock/delivery appointments

| Column | Definition |
|---|---|
| `id` | `UUID PRIMARY KEY DEFAULT gen_random_uuid()` |
| `tenant_id` | `UUID` |
| `warehouse_id` | `UUID` |
| `appointment_number` | `VARCHAR(255) NOT NULL UNIQUE` |
| `type` | `VARCHAR(255) NOT NULL` |
| `status` | `VARCHAR(255) NOT NULL` |
| `carrier_name` | `VARCHAR(255)` |
| `carrier_code` | `VARCHAR(255)` |
| `trailer_number` | `VARCHAR(255)` |
| `vehicle_license_plate` | `VARCHAR(255)` |
| `driver_name` | `VARCHAR(255)` |
| `driver_phone` | `VARCHAR(255)` |
| `dock_door_id` | `UUID` |
| `yard_location_id` | `UUID` |
| `asn_id` | `UUID` |
| `edi_document_id` | `UUID` |
| `estimated_arrival` | `TIMESTAMP` |
| `actual_arrival` | `TIMESTAMP` |
| `estimated_departure` | `TIMESTAMP` |
| `actual_departure` | `TIMESTAMP` |
| `appointment_window` | `VARCHAR(255)` |
| `po_numbers` | `TEXT` |
| `order_ids` | `TEXT` |
| `load_count` | `INT` |
| `pallet_count` | `INT` |
| `piece_count` | `INT` |
| `weight_kg` | `DOUBLE PRECISION` |
| `temperature_required` | `BOOLEAN` |
| `temperature_min` | `DOUBLE PRECISION` |
| `temperature_max` | `DOUBLE PRECISION` |
| `special_instructions` | `TEXT` |
| `checked_in_by` | `VARCHAR(255)` |
| `completed_by` | `VARCHAR(255)` |
| `notes` | `TEXT` |
| `metadata` | `JSONB` |
| `created_at` | `TIMESTAMP NOT NULL DEFAULT NOW()` |
| `updated_at` | `TIMESTAMP NOT NULL DEFAULT NOW()` |

**Indexes:**
- `idx_nx_appointments_asn_id (asn_id)`
- `idx_nx_appointments_edi_document_id (edi_document_id)`


### nx_yard_locations — Yard locations

| Column | Definition |
|---|---|
| `id` | `UUID PRIMARY KEY` |
| `tenant_id` | `UUID` |
| `warehouse_id` | `UUID` |
| `location_code` | `VARCHAR(255) NOT NULL` |
| `location_type` | `VARCHAR(255)` |
| `status` | `VARCHAR(255) NOT NULL` |
| `capacity` | `INTEGER` |
| `current_occupancy` | `INTEGER` |
| `zone` | `VARCHAR(255)` |
| `notes` | `VARCHAR(255)` |
| `metadata` | `JSONB` |
| `created_at` | `TIMESTAMP NOT NULL` |
| `updated_at` | `TIMESTAMP NOT NULL` |

**Indexes:**
- `idx_yard_locations_tenant (tenant_id)`
- `idx_yard_locations_warehouse (warehouse_id)`
- `idx_yard_locations_status (status)`


### nx_manifests — Shipment manifests

| Column | Definition |
|---|---|
| `id` | `UUID PRIMARY KEY` |
| `tenant_id` | `UUID` |
| `carrier` | `VARCHAR(255)` |
| `manifest_date` | `VARCHAR(255)` |
| `bol_number` | `VARCHAR(255)` |
| `total_weight` | `NUMERIC(38, 2)` |
| `total_cost` | `NUMERIC(38, 2)` |
| `status` | `VARCHAR(255) NOT NULL` |
| `created_at` | `TIMESTAMP NOT NULL` |
| `updated_at` | `TIMESTAMP NOT NULL` |

**Indexes:**
- `idx_manifests_tenant (tenant_id)`
- `idx_manifests_status (status)`


### nx_manifest_shipments — Manifest → shipment links

| Column | Definition |
|---|---|
| `id` | `UUID PRIMARY KEY` |
| `manifest_id` | `UUID NOT NULL` |
| `tenant_id` | `UUID` |
| `order_id` | `VARCHAR(255)` |
| `tracking_number` | `VARCHAR(255)` |
| `service` | `VARCHAR(255)` |
| `status` | `VARCHAR(255)` |
| `weight` | `NUMERIC(38, 2)` |
| `cost` | `NUMERIC(38, 2)` |
| `destination` | `VARCHAR(255)` |

**Indexes:**
- `idx_manifest_shipments_manifest (manifest_id)`
- `idx_manifest_shipments_tenant (tenant_id)`


---

## Returns (RMA)


### nx_returns — Returns/RMAs

| Column | Definition |
|---|---|
| `id` | `UUID PRIMARY KEY DEFAULT uuid_generate_v4()` |
| `tenant_id` | `UUID` |
| `order_id` | `UUID NOT NULL REFERENCES nx_orders(id)` |
| `customer_id` | `UUID` |
| `reason` | `TEXT` |
| `grade` | `VARCHAR(10)` |
| `disposition` | `VARCHAR(50)` |
| `carrier_id` | `VARCHAR(100)` |
| `tracking_number` | `VARCHAR(255)` |
| `label_url` | `TEXT` |
| `refund_amount` | `DECIMAL(12,2) DEFAULT 0.00` |
| `refund_reference` | `VARCHAR(255)` |
| `status` | `VARCHAR(50) NOT NULL DEFAULT 'PENDING'` |
| `inspected_at` | `TIMESTAMP` |
| `created_at` | `TIMESTAMP NOT NULL DEFAULT NOW()` |
| `updated_at` | `TIMESTAMP NOT NULL DEFAULT NOW()` |

**Indexes:**
- `idx_returns_order (order_id)`


### nx_return_items — Return line items

| Column | Definition |
|---|---|
| `id` | `UUID PRIMARY KEY DEFAULT gen_random_uuid()` |
| `return_id` | `UUID NOT NULL REFERENCES nx_returns(id) ON DELETE CASCADE` |
| `tenant_id` | `UUID NOT NULL` |
| `order_item_id` | `UUID` |
| `sku` | `VARCHAR(255) NOT NULL` |
| `product_name` | `VARCHAR(512)` |
| `quantity` | `INT NOT NULL DEFAULT 1` |
| `return_reason` | `VARCHAR(100)` |
| `return_reason_detail` | `TEXT` |
| `condition` | `VARCHAR(30) DEFAULT 'UNKNOWN' CHECK (condition IN ('LIKE_NEW','GOOD','FAIR','DAMAGED','DEFECTIVE','UNKNOWN'))` |
| `condition_notes` | `TEXT` |
| `grade` | `VARCHAR(10)` |
| `disposition` | `VARCHAR(50) DEFAULT 'PENDING' CHECK (disposition IN ('PENDING','RESTOCK','REFURBISH','RETURN_TO_VENDOR','DISPOSE','DONATE'))` |
| `refund_amount` | `DECIMAL(12,2) DEFAULT 0.00` |
| `status` | `VARCHAR(30) DEFAULT 'PENDING' CHECK (status IN ('PENDING','APPROVED','RECEIVED','INSPECTED','REFUNDED','REJECTED'))` |
| `inspected_at` | `TIMESTAMP` |
| `inspected_by` | `UUID` |
| `created_at` | `TIMESTAMP DEFAULT now()` |
| `updated_at` | `TIMESTAMP DEFAULT now()` |

**Indexes:**
- `idx_nx_return_items_return (return_id)`
- `idx_nx_return_items_status (status)`
- `idx_nx_return_items_tenant (tenant_id)`


### nx_return_reasons — Return reason codes

| Column | Definition |
|---|---|
| `id` | `UUID PRIMARY KEY DEFAULT gen_random_uuid()` |
| `tenant_id` | `UUID NOT NULL` |
| `code` | `VARCHAR(50) NOT NULL` |
| `label` | `VARCHAR(255) NOT NULL` |
| `category` | `VARCHAR(50) DEFAULT 'OTHER' CHECK (category IN ('PRODUCT','QUALITY','SHIPPING','CUSTOMER','OTHER'))` |
| `requires_detail` | `BOOLEAN DEFAULT false` |
| `is_active` | `BOOLEAN DEFAULT true` |
| `sort_order` | `INT DEFAULT 0` |
| `created_at` | `TIMESTAMP DEFAULT now()` |

**Indexes:**
- `idx_nx_return_reasons_tenant (tenant_id)`
- `idx_nx_return_reasons_code_tenant (tenant_id, code) UNIQUE`


### nx_proof_of_delivery — Proof of delivery records

| Column | Definition |
|---|---|
| `id` | `UUID PRIMARY KEY DEFAULT gen_random_uuid()` |
| `tenant_id` | `UUID NOT NULL` |
| `pickup_order_id` | `UUID NOT NULL` |
| `order_number` | `VARCHAR(50) NOT NULL` |
| `collected_by_name` | `VARCHAR(255) NOT NULL` |
| `collected_by_id_doc` | `VARCHAR(255)` |
| `collector_signature` | `TEXT NOT NULL` |
| `associate_name` | `VARCHAR(255) NOT NULL` |
| `associate_signature` | `TEXT` |
| `collection_notes` | `TEXT` |
| `items_handed_over` | `INTEGER NOT NULL` |
| `photo_path` | `VARCHAR(500)` |
| `collected_at` | `TIMESTAMP NOT NULL DEFAULT NOW()` |
| `created_at` | `TIMESTAMP NOT NULL DEFAULT NOW()` |

**Indexes:**
- `idx_pod_order (pickup_order_id)`


---

## Procurement & Suppliers


### nx_suppliers — Suppliers

| Column | Definition |
|---|---|
| `id` | `UUID PRIMARY KEY DEFAULT gen_random_uuid()` |
| `tenant_id` | `UUID NOT NULL` |
| `supplier_code` | `VARCHAR(50) NOT NULL` |
| `company_name` | `VARCHAR(255) NOT NULL` |
| `trading_name` | `VARCHAR(255)` |
| `tax_id` | `VARCHAR(50)` |
| `registration_number` | `VARCHAR(50)` |
| `supplier_type` | `VARCHAR(30) DEFAULT 'MANUFACTURER'` |
| `status` | `VARCHAR(20) DEFAULT 'ACTIVE'` |
| `payment_terms` | `VARCHAR(50) DEFAULT 'NET30'` |
| `currency` | `VARCHAR(3) DEFAULT 'USD'` |
| `credit_limit` | `DECIMAL(15,2)` |
| `rating` | `INT DEFAULT 0` |
| `address_line1` | `VARCHAR(255)` |
| `address_line2` | `VARCHAR(255)` |
| `city` | `VARCHAR(100)` |
| `state` | `VARCHAR(100)` |
| `zip_code` | `VARCHAR(20)` |
| `country` | `VARCHAR(100)` |
| `phone` | `VARCHAR(50)` |
| `email` | `VARCHAR(255)` |
| `website` | `VARCHAR(255)` |
| `is_active` | `BOOLEAN DEFAULT true` |
| `metadata` | `JSONB` |
| `created_at` | `TIMESTAMP NOT NULL DEFAULT now()` |
| `updated_at` | `TIMESTAMP NOT NULL DEFAULT now()` |

**Table-level constraints:**
- `UNIQUE(tenant_id, supplier_code)`

**Indexes:**
- `idx_suppliers_tenant_code (tenant_id, supplier_code) UNIQUE`


### nx_supplier_contacts — Supplier contacts

| Column | Definition |
|---|---|
| `id` | `UUID PRIMARY KEY DEFAULT gen_random_uuid()` |
| `supplier_id` | `UUID NOT NULL REFERENCES nx_suppliers(id) ON DELETE CASCADE` |
| `first_name` | `VARCHAR(100) NOT NULL` |
| `last_name` | `VARCHAR(100) NOT NULL` |
| `job_title` | `VARCHAR(100)` |
| `email` | `VARCHAR(255)` |
| `phone` | `VARCHAR(50)` |
| `mobile` | `VARCHAR(50)` |
| `is_primary` | `BOOLEAN DEFAULT false` |
| `department` | `VARCHAR(100)` |
| `notes` | `TEXT` |
| `created_at` | `TIMESTAMP NOT NULL DEFAULT now()` |


### nx_supplier_contracts — Supplier contracts

| Column | Definition |
|---|---|
| `id` | `UUID PRIMARY KEY DEFAULT gen_random_uuid()` |
| `tenant_id` | `UUID NOT NULL` |
| `supplier_id` | `UUID NOT NULL REFERENCES nx_suppliers(id) ON DELETE CASCADE` |
| `contract_number` | `VARCHAR(50) NOT NULL` |
| `contract_type` | `VARCHAR(30) DEFAULT 'MASTER'` |
| `start_date` | `DATE NOT NULL` |
| `end_date` | `DATE` |
| `terms` | `TEXT` |
| `pricing_terms` | `JSONB` |
| `discount_percent` | `DECIMAL(5,2) DEFAULT 0` |
| `status` | `VARCHAR(20) DEFAULT 'ACTIVE'` |
| `auto_renew` | `BOOLEAN DEFAULT false` |
| `file_url` | `VARCHAR(512)` |
| `created_at` | `TIMESTAMP NOT NULL DEFAULT now()` |
| `updated_at` | `TIMESTAMP NOT NULL DEFAULT now()` |

**Table-level constraints:**
- `UNIQUE(supplier_id, contract_number)`


### nx_purchase_requests — Purchase requests

| Column | Definition |
|---|---|
| `id` | `UUID PRIMARY KEY DEFAULT gen_random_uuid()` |
| `tenant_id` | `UUID NOT NULL` |
| `request_number` | `VARCHAR(50) NOT NULL` |
| `title` | `VARCHAR(255) NOT NULL` |
| `description` | `TEXT` |
| `status` | `VARCHAR(30) DEFAULT 'DRAFT'` |
| `priority` | `VARCHAR(20) DEFAULT 'MEDIUM'` |
| `requested_by` | `UUID` |
| `approved_by` | `UUID` |
| `approved_at` | `TIMESTAMP` |
| `notes` | `TEXT` |
| `created_at` | `TIMESTAMP NOT NULL DEFAULT now()` |
| `updated_at` | `TIMESTAMP NOT NULL DEFAULT now()` |

**Table-level constraints:**
- `UNIQUE(tenant_id, request_number)`


### nx_purchase_request_items — Purchase request line items

| Column | Definition |
|---|---|
| `id` | `UUID PRIMARY KEY DEFAULT gen_random_uuid()` |
| `request_id` | `UUID NOT NULL REFERENCES nx_purchase_requests(id) ON DELETE CASCADE` |
| `sku` | `VARCHAR(100)` |
| `product_name` | `VARCHAR(255) NOT NULL` |
| `quantity` | `INT NOT NULL` |
| `unit` | `VARCHAR(20) DEFAULT 'EA'` |
| `estimated_unit_price` | `DECIMAL(15,4)` |
| `requested_date` | `DATE` |
| `notes` | `TEXT` |
| `created_at` | `TIMESTAMP NOT NULL DEFAULT now()` |


### nx_purchase_orders — Purchase orders

| Column | Definition |
|---|---|
| `id` | `UUID PRIMARY KEY DEFAULT gen_random_uuid()` |
| `tenant_id` | `UUID NOT NULL` |
| `po_number` | `VARCHAR(50) NOT NULL` |
| `supplier_id` | `UUID NOT NULL REFERENCES nx_suppliers(id)` |
| `rfq_id` | `UUID REFERENCES nx_rfqs(id)` |
| `status` | `VARCHAR(30) DEFAULT 'DRAFT'` |
| `order_date` | `DATE NOT NULL DEFAULT CURRENT_DATE` |
| `expected_delivery_date` | `DATE` |
| `delivered_date` | `DATE` |
| `shipping_method` | `VARCHAR(100)` |
| `payment_terms` | `VARCHAR(50)` |
| `currency` | `VARCHAR(3) DEFAULT 'USD'` |
| `subtotal` | `DECIMAL(15,2) DEFAULT 0` |
| `tax_amount` | `DECIMAL(15,2) DEFAULT 0` |
| `shipping_cost` | `DECIMAL(15,2) DEFAULT 0` |
| `total_amount` | `DECIMAL(15,2) DEFAULT 0` |
| `notes` | `TEXT` |
| `terms` | `TEXT` |
| `created_by` | `UUID` |
| `approved_by` | `UUID` |
| `approved_at` | `TIMESTAMP` |
| `is_fully_received` | `BOOLEAN DEFAULT false` |
| `metadata` | `JSONB` |
| `created_at` | `TIMESTAMP NOT NULL DEFAULT now()` |
| `updated_at` | `TIMESTAMP NOT NULL DEFAULT now()` |

**Table-level constraints:**
- `UNIQUE(tenant_id, po_number)`


### nx_purchase_order_items — Purchase order line items

| Column | Definition |
|---|---|
| `id` | `UUID PRIMARY KEY DEFAULT gen_random_uuid()` |
| `po_id` | `UUID NOT NULL REFERENCES nx_purchase_orders(id) ON DELETE CASCADE` |
| `sku` | `VARCHAR(100)` |
| `product_name` | `VARCHAR(255) NOT NULL` |
| `quantity_ordered` | `INT NOT NULL` |
| `quantity_received` | `INT DEFAULT 0` |
| `quantity_cancelled` | `INT DEFAULT 0` |
| `unit_price` | `DECIMAL(15,4) NOT NULL` |
| `total_price` | `DECIMAL(15,2) NOT NULL` |
| `tax_rate` | `DECIMAL(5,2) DEFAULT 0` |
| `discount_percent` | `DECIMAL(5,2) DEFAULT 0` |
| `unit` | `VARCHAR(20) DEFAULT 'EA'` |
| `expected_date` | `DATE` |
| `notes` | `TEXT` |
| `created_at` | `TIMESTAMP NOT NULL DEFAULT now()` |


### nx_rfqs — Requests for quotation

| Column | Definition |
|---|---|
| `id` | `UUID PRIMARY KEY DEFAULT gen_random_uuid()` |
| `tenant_id` | `UUID NOT NULL` |
| `rfq_number` | `VARCHAR(50) NOT NULL` |
| `title` | `VARCHAR(255) NOT NULL` |
| `description` | `TEXT` |
| `status` | `VARCHAR(30) DEFAULT 'DRAFT'` |
| `request_id` | `UUID REFERENCES nx_purchase_requests(id)` |
| `due_date` | `DATE` |
| `supplier_ids` | `JSONB` |
| `terms` | `TEXT` |
| `currency` | `VARCHAR(3) DEFAULT 'USD'` |
| `created_by` | `UUID` |
| `approved_by` | `UUID` |
| `created_at` | `TIMESTAMP NOT NULL DEFAULT now()` |
| `updated_at` | `TIMESTAMP NOT NULL DEFAULT now()` |

**Table-level constraints:**
- `UNIQUE(tenant_id, rfq_number)`


### nx_rfq_responses — RFQ responses

| Column | Definition |
|---|---|
| `id` | `UUID PRIMARY KEY DEFAULT gen_random_uuid()` |
| `rfq_id` | `UUID NOT NULL REFERENCES nx_rfqs(id) ON DELETE CASCADE` |
| `supplier_id` | `UUID NOT NULL REFERENCES nx_suppliers(id)` |
| `total_amount` | `DECIMAL(15,2)` |
| `currency` | `VARCHAR(3) DEFAULT 'USD'` |
| `delivery_days` | `INT` |
| `valid_until` | `DATE` |
| `notes` | `TEXT` |
| `status` | `VARCHAR(20) DEFAULT 'PENDING'` |
| `submitted_at` | `TIMESTAMP NOT NULL DEFAULT now()` |


### nx_asns — Advanced shipping notices (ASN)

| Column | Definition |
|---|---|
| `id` | `UUID PRIMARY KEY` |
| `tenant_id` | `UUID NOT NULL` |
| `asn_number` | `VARCHAR(255) NOT NULL` |
| `node_id` | `UUID` |
| `status` | `VARCHAR(50) NOT NULL` |
| `source` | `VARCHAR(50) NOT NULL` |
| `supplier_name` | `VARCHAR(255)` |
| `carrier_code` | `VARCHAR(100)` |
| `tracking_number` | `VARCHAR(255)` |
| `purchase_order_number` | `VARCHAR(255)` |
| `ship_date` | `TIMESTAMP` |
| `expected_arrival_date` | `TIMESTAMP` |
| `edi_document_id` | `UUID` |
| `received_by` | `VARCHAR(255)` |
| `received_at` | `TIMESTAMP` |
| `created_at` | `TIMESTAMP NOT NULL` |
| `updated_at` | `TIMESTAMP NOT NULL` |

**Indexes:**
- `idx_asns_tenant_status (tenant_id, status)`
- `idx_asns_tenant_number (tenant_id, asn_number) UNIQUE`


### nx_asn_lines — ASN line items

| Column | Definition |
|---|---|
| `id` | `UUID PRIMARY KEY` |
| `asn_id` | `UUID NOT NULL` |
| `tenant_id` | `UUID NOT NULL` |
| `sku` | `VARCHAR(255) NOT NULL` |
| `product_name` | `VARCHAR(255)` |
| `expected_qty` | `INTEGER NOT NULL` |
| `received_qty` | `INTEGER NOT NULL DEFAULT 0` |
| `lot_number` | `VARCHAR(255)` |
| `expiry_date` | `TIMESTAMP` |
| `status` | `VARCHAR(50) NOT NULL` |
| `created_at` | `TIMESTAMP NOT NULL` |

**Table-level constraints:**
- `CONSTRAINT fk_asn_lines_asn FOREIGN KEY (asn_id) REFERENCES nx_asns(id) ON DELETE CASCADE`

**Indexes:**
- `idx_asn_lines_asn (asn_id)`


---

## Automation & Alerting


### nx_automation_systems — Automation systems (WES/robots)

| Column | Definition |
|---|---|
| `id` | `UUID PRIMARY KEY` |
| `tenant_id` | `UUID NOT NULL` |
| `warehouse_id` | `UUID NOT NULL` |
| `system_name` | `VARCHAR(255) NOT NULL` |
| `system_type` | `VARCHAR(100) NOT NULL` |
| `vendor` | `VARCHAR(255)` |
| `model` | `VARCHAR(255)` |
| `protocol` | `VARCHAR(100)` |
| `endpoint_url` | `VARCHAR(500)` |
| `api_key` | `TEXT` |
| `status` | `VARCHAR(50) NOT NULL` |
| `health_check_url` | `VARCHAR(500)` |
| `last_health_check_at` | `TIMESTAMP` |
| `health_check_interval_sec` | `INTEGER` |
| `capabilities` | `JSONB` |
| `connection_config` | `JSONB` |
| `is_active` | `BOOLEAN NOT NULL` |
| `last_connected_at` | `TIMESTAMP` |
| `error_message` | `TEXT` |
| `metadata` | `JSONB` |
| `created_at` | `TIMESTAMP NOT NULL` |
| `updated_at` | `TIMESTAMP NOT NULL` |

**Indexes:**
- `idx_automation_systems_tenant (tenant_id, system_type)`


### nx_automation_commands — Automation commands

| Column | Definition |
|---|---|
| `id` | `UUID PRIMARY KEY` |
| `tenant_id` | `UUID NOT NULL` |
| `system_id` | `UUID NOT NULL` |
| `command_type` | `VARCHAR(100) NOT NULL` |
| `status` | `VARCHAR(50) NOT NULL` |
| `parameters` | `JSONB` |
| `result` | `JSONB` |
| `priority` | `INTEGER NOT NULL` |
| `timeout_ms` | `INTEGER` |
| `retry_count` | `INTEGER` |
| `max_retries` | `INTEGER` |
| `assigned_by` | `VARCHAR(255)` |
| `order_id` | `UUID` |
| `picklist_id` | `UUID` |
| `wave_id` | `UUID` |
| `error_message` | `TEXT` |
| `sent_at` | `TIMESTAMP` |
| `acknowledged_at` | `TIMESTAMP` |
| `completed_at` | `TIMESTAMP` |
| `execution_time_ms` | `BIGINT` |
| `metadata` | `JSONB` |
| `created_at` | `TIMESTAMP NOT NULL` |
| `updated_at` | `TIMESTAMP NOT NULL` |

**Indexes:**
- `idx_automation_commands_system_status (system_id, status)`


### nx_automation_alerts — Automation system alerts

| Column | Definition |
|---|---|
| `id` | `UUID PRIMARY KEY` |
| `tenant_id` | `UUID NOT NULL` |
| `system_id` | `UUID NOT NULL` |
| `alert_type` | `VARCHAR(100) NOT NULL` |
| `severity` | `VARCHAR(50) NOT NULL` |
| `status` | `VARCHAR(50) NOT NULL` |
| `title` | `VARCHAR(255) NOT NULL` |
| `description` | `TEXT` |
| `threshold_value` | `DOUBLE PRECISION` |
| `current_value` | `DOUBLE PRECISION` |
| `unit` | `VARCHAR(50)` |
| `acknowledged_by` | `VARCHAR(255)` |
| `acknowledged_at` | `TIMESTAMP` |
| `resolved_at` | `TIMESTAMP` |
| `resolution_notes` | `TEXT` |
| `auto_resolve` | `BOOLEAN` |
| `metadata` | `JSONB` |
| `created_at` | `TIMESTAMP NOT NULL` |
| `updated_at` | `TIMESTAMP NOT NULL` |

**Indexes:**
- `idx_automation_alerts_system_status (system_id, status)`


### nx_automation_logs — Automation command/event logs

| Column | Definition |
|---|---|
| `id` | `UUID PRIMARY KEY` |
| `tenant_id` | `UUID NOT NULL` |
| `system_id` | `UUID NOT NULL` |
| `command_id` | `UUID` |
| `log_level` | `VARCHAR(50) NOT NULL` |
| `event` | `VARCHAR(255) NOT NULL` |
| `message` | `TEXT` |
| `data` | `JSONB` |
| `duration_ms` | `BIGINT` |
| `created_at` | `TIMESTAMP NOT NULL` |

**Indexes:**
- `idx_automation_logs_system_time (system_id, created_at)`


### nx_alert_rules — Alert rule definitions

| Column | Definition |
|---|---|
| `id` | `UUID PRIMARY KEY DEFAULT gen_random_uuid()` |
| `tenant_id` | `UUID NOT NULL` |
| `name` | `VARCHAR(255) NOT NULL` |
| `description` | `TEXT` |
| `event_type` | `VARCHAR(100) NOT NULL` |
| `condition_expression` | `TEXT` |
| `severity` | `VARCHAR(20) DEFAULT 'WARNING'` |
| `channel` | `VARCHAR(20) DEFAULT 'EMAIL'` |
| `recipient_list` | `JSONB` |
| `is_active` | `BOOLEAN DEFAULT true` |
| `throttle_minutes` | `INT DEFAULT 0` |
| `created_at` | `TIMESTAMP NOT NULL DEFAULT now()` |
| `updated_at` | `TIMESTAMP NOT NULL DEFAULT now()` |


---

## Integrations Hub


### nx_integration_flows — Integration flows

| Column | Definition |
|---|---|
| `id` | `UUID PRIMARY KEY DEFAULT gen_random_uuid()` |
| `tenant_id` | `UUID NOT NULL` |
| `name` | `VARCHAR(255) NOT NULL` |
| `description` | `TEXT` |
| `flow_type` | `VARCHAR(30) NOT NULL` |
| `status` | `VARCHAR(20) DEFAULT 'DRAFT'` |
| `source_endpoint_id` | `UUID REFERENCES nx_integration_endpoints(id)` |
| `target_endpoint_id` | `UUID REFERENCES nx_integration_endpoints(id)` |
| `trigger_type` | `VARCHAR(30) NOT NULL` |
| `trigger_config` | `JSONB` |
| `schedule_cron` | `VARCHAR(100)` |
| `priority` | `INT DEFAULT 5` |
| `max_retries` | `INT DEFAULT 3` |
| `retry_delay_seconds` | `INT DEFAULT 60` |
| `batch_size` | `INT DEFAULT 1000` |
| `throttle_rate` | `INT DEFAULT 0` |
| `processing_timeout_minutes` | `INT DEFAULT 60` |
| `error_handling` | `VARCHAR(30) DEFAULT 'STOP'` |
| `is_active` | `BOOLEAN DEFAULT false` |
| `metadata` | `JSONB` |
| `created_at` | `TIMESTAMP NOT NULL DEFAULT now()` |
| `updated_at` | `TIMESTAMP NOT NULL DEFAULT now()` |

**Table-level constraints:**
- `UNIQUE(tenant_id, name)`

**Indexes:**
- `idx_int_flows_tenant (tenant_id)`
- `idx_int_flows_status (status)`
- `idx_int_flows_type (flow_type)`
- `idx_int_flows_source (source_endpoint_id)`
- `idx_int_flows_target (target_endpoint_id)`
- `idx_int_flows_tenant_active (tenant_id, is_active)`


### nx_integration_flow_steps — Flow steps

| Column | Definition |
|---|---|
| `id` | `UUID PRIMARY KEY DEFAULT gen_random_uuid()` |
| `flow_id` | `UUID NOT NULL REFERENCES nx_integration_flows(id) ON DELETE CASCADE` |
| `step_order` | `INT NOT NULL` |
| `step_type` | `VARCHAR(50) NOT NULL` |
| `transformer_type` | `VARCHAR(50)` |
| `config` | `JSONB NOT NULL` |
| `condition_expression` | `TEXT` |
| `on_error` | `VARCHAR(20) DEFAULT 'STOP'` |
| `timeout_seconds` | `INT DEFAULT 300` |
| `created_at` | `TIMESTAMP NOT NULL DEFAULT now()` |

**Indexes:**
- `idx_int_flow_steps_flow (flow_id)`
- `idx_int_flow_steps_order (flow_id, step_order)`


### nx_integration_audit_log — Integration audit log

| Column | Definition |
|---|---|
| `id` | `UUID PRIMARY KEY DEFAULT gen_random_uuid()` |
| `tenant_id` | `UUID NOT NULL` |
| `flow_id` | `UUID REFERENCES nx_integration_flows(id)` |
| `message_id` | `UUID REFERENCES nx_integration_messages(id)` |
| `entity_type` | `VARCHAR(100)` |
| `entity_id` | `VARCHAR(255)` |
| `action` | `VARCHAR(50) NOT NULL` |
| `status` | `VARCHAR(20) NOT NULL` |
| `request_payload` | `TEXT` |
| `response_payload` | `TEXT` |
| `source_system` | `VARCHAR(255)` |
| `target_system` | `VARCHAR(255)` |
| `processing_time_ms` | `BIGINT` |
| `error_message` | `TEXT` |
| `ip_address` | `VARCHAR(45)` |
| `user_agent` | `VARCHAR(500)` |
| `created_by` | `UUID` |
| `created_at` | `TIMESTAMP NOT NULL DEFAULT now()` |

**Indexes:**
- `idx_int_audit_tenant (tenant_id)`
- `idx_int_audit_flow (flow_id)`
- `idx_int_audit_entity (entity_type, entity_id)`
- `idx_int_audit_action (action)`
- `idx_int_audit_tenant_lookup (tenant_id, flow_id, entity_type, action, created_at)`
- `idx_int_audit_created (created_at)`


### nx_integration_endpoints — Integration endpoint definitions

| Column | Definition |
|---|---|
| `id` | `UUID PRIMARY KEY DEFAULT gen_random_uuid()` |
| `tenant_id` | `UUID NOT NULL` |
| `name` | `VARCHAR(255) NOT NULL` |
| `description` | `TEXT` |
| `endpoint_type` | `VARCHAR(50) NOT NULL` |
| `protocol` | `VARCHAR(30) NOT NULL` |
| `host` | `VARCHAR(255)` |
| `port` | `INT` |
| `path` | `VARCHAR(500)` |
| `method` | `VARCHAR(10)` |
| `headers` | `JSONB` |
| `query_params` | `JSONB` |
| `auth_type` | `VARCHAR(50)` |
| `auth_config` | `JSONB` |
| `ssl_enabled` | `BOOLEAN DEFAULT false` |
| `timeout_ms` | `INT DEFAULT 30000` |
| `retry_count` | `INT DEFAULT 3` |
| `retry_delay_ms` | `INT DEFAULT 1000` |
| `circuit_breaker_enabled` | `BOOLEAN DEFAULT true` |
| `circuit_breaker_threshold` | `INT DEFAULT 5` |
| `circuit_breaker_timeout_ms` | `INT DEFAULT 30000` |
| `rate_limit_enabled` | `BOOLEAN DEFAULT false` |
| `rate_limit_max` | `INT DEFAULT 100` |
| `rate_limit_window_ms` | `INT DEFAULT 1000` |
| `is_active` | `BOOLEAN DEFAULT true` |
| `metadata` | `JSONB` |
| `created_at` | `TIMESTAMP NOT NULL DEFAULT now()` |
| `updated_at` | `TIMESTAMP NOT NULL DEFAULT now()` |

**Table-level constraints:**
- `UNIQUE(tenant_id, name)`

**Indexes:**
- `idx_int_endpoints_tenant (tenant_id)`
- `idx_int_endpoints_type (endpoint_type)`
- `idx_int_endpoints_active (tenant_id, is_active)`


### nx_integration_messages — Integration message bus records

| Column | Definition |
|---|---|
| `id` | `UUID PRIMARY KEY DEFAULT gen_random_uuid()` |
| `tenant_id` | `UUID NOT NULL` |
| `flow_id` | `UUID REFERENCES nx_integration_flows(id)` |
| `message_id` | `VARCHAR(255) NOT NULL` |
| `correlation_id` | `VARCHAR(255)` |
| `source` | `VARCHAR(100)` |
| `message_type` | `VARCHAR(50) NOT NULL` |
| `format` | `VARCHAR(30)` |
| `payload` | `TEXT` |
| `payload_size` | `BIGINT` |
| `headers` | `JSONB` |
| `status` | `VARCHAR(20) NOT NULL DEFAULT 'PENDING'` |
| `error_message` | `TEXT` |
| `error_code` | `VARCHAR(100)` |
| `retry_count` | `INT DEFAULT 0` |
| `max_retries` | `INT DEFAULT 3` |
| `processed_at` | `TIMESTAMP` |
| `created_at` | `TIMESTAMP NOT NULL DEFAULT now()` |

**Table-level constraints:**
- `UNIQUE(tenant_id, message_id)`

**Indexes:**
- `idx_int_messages_tenant (tenant_id)`
- `idx_int_messages_flow (flow_id)`
- `idx_int_messages_status (status)`
- `idx_int_messages_correlation (correlation_id)`
- `idx_int_messages_tenant_status (tenant_id, status)`
- `idx_int_messages_created (tenant_id, created_at)`


### nx_integration_dlq — Integration dead-letter queue

| Column | Definition |
|---|---|
| `id` | `UUID PRIMARY KEY DEFAULT gen_random_uuid()` |
| `tenant_id` | `UUID NOT NULL` |
| `flow_id` | `UUID REFERENCES nx_integration_flows(id)` |
| `endpoint_id` | `UUID REFERENCES nx_integration_endpoints(id)` |
| `message_id` | `VARCHAR(255)` |
| `original_payload` | `TEXT` |
| `error_message` | `TEXT` |
| `error_stacktrace` | `TEXT` |
| `error_category` | `VARCHAR(50)` |
| `retry_count` | `INT DEFAULT 0` |
| `max_retries` | `INT DEFAULT 3` |
| `last_retry_at` | `TIMESTAMP` |
| `status` | `VARCHAR(20) DEFAULT 'FAILED'` |
| `created_at` | `TIMESTAMP NOT NULL DEFAULT now()` |
| `updated_at` | `TIMESTAMP NOT NULL DEFAULT now()` |

**Indexes:**
- `idx_int_dlq_tenant (tenant_id)`
- `idx_int_dlq_flow (flow_id)`
- `idx_int_dlq_status (status)`
- `idx_int_dlq_category (error_category)`
- `idx_int_dlq_tenant_status (tenant_id, status)`


### nx_integration_transform_mappings — Transformation mappings

| Column | Definition |
|---|---|
| `id` | `UUID PRIMARY KEY DEFAULT gen_random_uuid()` |
| `tenant_id` | `UUID NOT NULL` |
| `name` | `VARCHAR(255) NOT NULL` |
| `source_format` | `VARCHAR(30) NOT NULL` |
| `target_format` | `VARCHAR(30) NOT NULL` |
| `mapping_definition` | `JSONB NOT NULL` |
| `version` | `INT DEFAULT 1` |
| `is_active` | `BOOLEAN DEFAULT true` |
| `created_at` | `TIMESTAMP NOT NULL DEFAULT now()` |
| `updated_at` | `TIMESTAMP NOT NULL DEFAULT now()` |

**Table-level constraints:**
- `UNIQUE(tenant_id, name)`

**Indexes:**
- `idx_int_mappings_tenant (tenant_id)`
- `idx_int_mappings_formats (source_format, target_format)`


### nx_integration_validation_rules — Validation rules

| Column | Definition |
|---|---|
| `id` | `UUID PRIMARY KEY DEFAULT gen_random_uuid()` |
| `tenant_id` | `UUID NOT NULL` |
| `name` | `VARCHAR(255) NOT NULL` |
| `rule_type` | `VARCHAR(50) NOT NULL` |
| `entity_type` | `VARCHAR(100) NOT NULL` |
| `field_path` | `VARCHAR(255)` |
| `operator` | `VARCHAR(30)` |
| `value` | `TEXT` |
| `error_message` | `VARCHAR(500)` |
| `severity` | `VARCHAR(20) DEFAULT 'ERROR'` |
| `is_active` | `BOOLEAN DEFAULT true` |
| `created_at` | `TIMESTAMP NOT NULL DEFAULT now()` |
| `updated_at` | `TIMESTAMP NOT NULL DEFAULT now()` |

**Indexes:**
- `idx_int_validation_tenant (tenant_id)`
- `idx_int_validation_entity (entity_type)`
- `idx_int_validation_rule_type (rule_type)`


### nx_integration_import_jobs — Integration import jobs

| Column | Definition |
|---|---|
| `id` | `UUID PRIMARY KEY DEFAULT gen_random_uuid()` |
| `tenant_id` | `UUID NOT NULL` |
| `flow_id` | `UUID REFERENCES nx_integration_flows(id)` |
| `job_name` | `VARCHAR(255) NOT NULL` |
| `source_type` | `VARCHAR(50) NOT NULL` |
| `source_config` | `JSONB` |
| `target_type` | `VARCHAR(50) NOT NULL` |
| `target_config` | `JSONB` |
| `file_name` | `VARCHAR(255)` |
| `file_size` | `BIGINT` |
| `file_hash` | `VARCHAR(64)` |
| `record_count` | `INT DEFAULT 0` |
| `success_count` | `INT DEFAULT 0` |
| `error_count` | `INT DEFAULT 0` |
| `status` | `VARCHAR(30) NOT NULL DEFAULT 'PENDING'` |
| `error_summary` | `TEXT` |
| `processing_time_ms` | `BIGINT` |
| `started_at` | `TIMESTAMP` |
| `completed_at` | `TIMESTAMP` |
| `created_by` | `UUID` |
| `created_at` | `TIMESTAMP NOT NULL DEFAULT now()` |
| `updated_at` | `TIMESTAMP NOT NULL DEFAULT now()` |

**Indexes:**
- `idx_int_import_tenant (tenant_id)`
- `idx_int_import_flow (flow_id)`
- `idx_int_import_status (status)`
- `idx_int_import_tenant_status (tenant_id, status)`


### nx_integration_export_jobs — Integration export jobs

| Column | Definition |
|---|---|
| `id` | `UUID PRIMARY KEY DEFAULT gen_random_uuid()` |
| `tenant_id` | `UUID NOT NULL` |
| `flow_id` | `UUID REFERENCES nx_integration_flows(id)` |
| `job_name` | `VARCHAR(255) NOT NULL` |
| `export_type` | `VARCHAR(50) NOT NULL` |
| `export_config` | `JSONB` |
| `query_criteria` | `JSONB` |
| `entity_type` | `VARCHAR(100) NOT NULL` |
| `format` | `VARCHAR(30) NOT NULL` |
| `compression` | `VARCHAR(20)` |
| `encryption` | `VARCHAR(30)` |
| `file_name` | `VARCHAR(255)` |
| `file_size` | `BIGINT` |
| `record_count` | `INT DEFAULT 0` |
| `status` | `VARCHAR(30) NOT NULL DEFAULT 'PENDING'` |
| `error_message` | `TEXT` |
| `processing_time_ms` | `BIGINT` |
| `started_at` | `TIMESTAMP` |
| `completed_at` | `TIMESTAMP` |
| `created_by` | `UUID` |
| `created_at` | `TIMESTAMP NOT NULL DEFAULT now()` |
| `updated_at` | `TIMESTAMP NOT NULL DEFAULT now()` |

**Indexes:**
- `idx_int_export_tenant (tenant_id)`
- `idx_int_export_flow (flow_id)`
- `idx_int_export_status (status)`
- `idx_int_export_tenant_status (tenant_id, status)`


### nx_integration_cdc_events — Change-data-capture events

| Column | Definition |
|---|---|
| `id` | `UUID PRIMARY KEY DEFAULT gen_random_uuid()` |
| `tenant_id` | `UUID NOT NULL` |
| `source` | `VARCHAR(100) NOT NULL` |
| `entity_type` | `VARCHAR(100) NOT NULL` |
| `entity_id` | `UUID NOT NULL` |
| `event_type` | `VARCHAR(30) NOT NULL` |
| `before_snapshot` | `JSONB` |
| `after_snapshot` | `JSONB` |
| `change_summary` | `JSONB` |
| `transaction_id` | `VARCHAR(255)` |
| `sequence` | `BIGINT` |
| `processed` | `BOOLEAN DEFAULT false` |
| `processed_at` | `TIMESTAMP` |
| `created_at` | `TIMESTAMP NOT NULL DEFAULT now()` |

**Indexes:**
- `idx_int_cdc_tenant (tenant_id)`
- `idx_int_cdc_source (tenant_id, source, entity_type, processed)`
- `idx_int_cdc_entity (entity_type, entity_id)`
- `idx_int_cdc_processed (processed)`
- `idx_int_cdc_transaction (transaction_id)`


### nx_integration_stores — Connected store instances

| Column | Definition |
|---|---|
| `id` | `UUID PRIMARY KEY DEFAULT uuid_generate_v4()` |
| `tenant_id` | `UUID NOT NULL` |
| `store_code` | `VARCHAR(100) NOT NULL` |
| `store_name` | `VARCHAR(255) NOT NULL` |
| `platform` | `VARCHAR(50) NOT NULL` |
| `platform_type` | `VARCHAR(50)` |
| `status` | `VARCHAR(20) NOT NULL DEFAULT 'ACTIVE'` |
| `currency` | `VARCHAR(3) DEFAULT 'USD'` |
| `default_locale` | `VARCHAR(10) DEFAULT 'en_US'` |
| `timezone` | `VARCHAR(50) DEFAULT 'UTC'` |
| `external_store_id` | `VARCHAR(255)` |
| `external_domain` | `VARCHAR(255)` |
| `is_active` | `BOOLEAN DEFAULT TRUE` |
| `config_json` | `JSONB` |
| `metadata` | `JSONB` |
| `created_at` | `TIMESTAMP NOT NULL DEFAULT NOW()` |
| `updated_at` | `TIMESTAMP NOT NULL DEFAULT NOW()` |

**Indexes:**
- `idx_int_stores_tenant (tenant_id, platform)`
- `idx_int_stores_code (tenant_id, store_code)`


### nx_integration_store_settings — Per-store settings

| Column | Definition |
|---|---|
| `id` | `UUID PRIMARY KEY DEFAULT uuid_generate_v4()` |
| `store_id` | `UUID NOT NULL REFERENCES nx_integration_stores(id) ON DELETE CASCADE` |
| `setting_type` | `VARCHAR(100) NOT NULL` |
| `setting_value` | `TEXT` |
| `description` | `VARCHAR(255)` |
| `is_encrypted` | `BOOLEAN DEFAULT FALSE` |
| `created_at` | `TIMESTAMP NOT NULL DEFAULT NOW()` |
| `updated_at` | `TIMESTAMP NOT NULL DEFAULT NOW()` |

**Indexes:**
- `idx_int_store_settings (store_id)`


### nx_integration_sync_configs — Sync configuration

| Column | Definition |
|---|---|
| `id` | `UUID PRIMARY KEY DEFAULT uuid_generate_v4()` |
| `store_id` | `UUID NOT NULL REFERENCES nx_integration_stores(id) ON DELETE CASCADE` |
| `sync_type` | `VARCHAR(50) NOT NULL` |
| `enabled` | `BOOLEAN DEFAULT TRUE` |
| `interval_minutes` | `INT DEFAULT 15` |
| `last_sync_at` | `TIMESTAMP` |
| `last_sync_status` | `VARCHAR(20)` |
| `last_sync_message` | `TEXT` |
| `config_json` | `JSONB` |
| `created_at` | `TIMESTAMP NOT NULL DEFAULT NOW()` |
| `updated_at` | `TIMESTAMP NOT NULL DEFAULT NOW()` |

**Indexes:**
- `idx_int_sync_configs (store_id)`


### nx_connector_instances — Connector instances

| Column | Definition |
|---|---|
| `id` | `UUID PRIMARY KEY DEFAULT gen_random_uuid()` |
| `tenant_id` | `UUID NOT NULL` |
| `store_id` | `UUID REFERENCES nx_integration_stores(id)` |
| `platform_type` | `VARCHAR(50) NOT NULL` |
| `connector_id` | `VARCHAR(100) NOT NULL` |
| `connector_name` | `VARCHAR(255)` |
| `api_version` | `VARCHAR(50) DEFAULT 'v1'` |
| `environment` | `VARCHAR(20) DEFAULT 'production'` |
| `base_url` | `VARCHAR(512)` |
| `status` | `VARCHAR(20) DEFAULT 'INITIALIZED'` |
| `is_active` | `BOOLEAN DEFAULT true` |
| `max_retries` | `INT DEFAULT 3` |
| `timeout_seconds` | `INT DEFAULT 30` |
| `batch_size` | `INT DEFAULT 100` |
| `webhooks_enabled` | `BOOLEAN DEFAULT true` |
| `auto_sync_enabled` | `BOOLEAN DEFAULT false` |
| `sync_interval_minutes` | `INT DEFAULT 15` |
| `config_json` | `JSONB` |
| `metadata` | `JSONB` |
| `last_sync_at` | `TIMESTAMP` |
| `last_health_check_at` | `TIMESTAMP` |
| `created_at` | `TIMESTAMP NOT NULL DEFAULT now()` |
| `updated_at` | `TIMESTAMP NOT NULL DEFAULT now()` |

**Table-level constraints:**
- `UNIQUE(tenant_id, connector_id)`


### nx_connector_credentials — Connector credentials

| Column | Definition |
|---|---|
| `id` | `UUID PRIMARY KEY DEFAULT gen_random_uuid()` |
| `connector_instance_id` | `UUID NOT NULL REFERENCES nx_connector_instances(id) ON DELETE CASCADE` |
| `credential_key` | `VARCHAR(100) NOT NULL` |
| `credential_value` | `TEXT NOT NULL` |
| `is_encrypted` | `BOOLEAN DEFAULT true` |
| `expires_at` | `TIMESTAMP` |
| `created_at` | `TIMESTAMP NOT NULL DEFAULT now()` |
| `updated_at` | `TIMESTAMP NOT NULL DEFAULT now()` |

**Table-level constraints:**
- `UNIQUE(connector_instance_id, credential_key)`


### nx_connector_health — Connector health status

| Column | Definition |
|---|---|
| `id` | `UUID PRIMARY KEY DEFAULT gen_random_uuid()` |
| `connector_instance_id` | `UUID NOT NULL REFERENCES nx_connector_instances(id) ON DELETE CASCADE` |
| `status` | `VARCHAR(20) NOT NULL DEFAULT 'UNKNOWN'` |
| `message` | `TEXT` |
| `latency_ms` | `BIGINT` |
| `consecutive_failures` | `INT DEFAULT 0` |
| `is_webhooks_registered` | `BOOLEAN DEFAULT false` |
| `last_success_at` | `TIMESTAMP` |
| `last_error_at` | `TIMESTAMP` |
| `checked_at` | `TIMESTAMP NOT NULL DEFAULT now()` |


### nx_data_mappings — Data mapping definitions

| Column | Definition |
|---|---|
| `id` | `UUID PRIMARY KEY DEFAULT gen_random_uuid()` |
| `tenant_id` | `UUID NOT NULL` |
| `connector_instance_id` | `UUID REFERENCES nx_connector_instances(id) ON DELETE CASCADE` |
| `mapping_name` | `VARCHAR(100) NOT NULL` |
| `direction` | `VARCHAR(20) NOT NULL DEFAULT 'BOTH'` |
| `source_field` | `VARCHAR(255) NOT NULL` |
| `target_field` | `VARCHAR(255) NOT NULL` |
| `transformation_type` | `VARCHAR(50)` |
| `transformation_rule` | `TEXT` |
| `default_value` | `TEXT` |
| `is_required` | `BOOLEAN DEFAULT false` |
| `is_active` | `BOOLEAN DEFAULT true` |
| `created_at` | `TIMESTAMP NOT NULL DEFAULT now()` |
| `updated_at` | `TIMESTAMP NOT NULL DEFAULT now()` |


### nx_batch_jobs — Batch job definitions

| Column | Definition |
|---|---|
| `id` | `UUID PRIMARY KEY DEFAULT gen_random_uuid()` |
| `tenant_id` | `UUID NOT NULL` |
| `connector_instance_id` | `UUID REFERENCES nx_connector_instances(id)` |
| `job_type` | `VARCHAR(50) NOT NULL` |
| `sync_type` | `VARCHAR(50)` |
| `status` | `VARCHAR(20) NOT NULL DEFAULT 'PENDING'` |
| `total_items` | `INT DEFAULT 0` |
| `items_succeeded` | `INT DEFAULT 0` |
| `items_failed` | `INT DEFAULT 0` |
| `items_skipped` | `INT DEFAULT 0` |
| `error_message` | `TEXT` |
| `params` | `JSONB` |
| `result` | `JSONB` |
| `started_at` | `TIMESTAMP NOT NULL DEFAULT now()` |
| `completed_at` | `TIMESTAMP` |
| `duration_ms` | `BIGINT` |
| `created_at` | `TIMESTAMP NOT NULL DEFAULT now()` |

**Indexes:**
- `idx_batch_jobs_tenant (tenant_id)`
- `idx_batch_jobs_connector (connector_instance_id)`
- `idx_batch_jobs_status (status)`


### nx_webhook_endpoints — Webhook endpoint definitions

| Column | Definition |
|---|---|
| `id` | `UUID PRIMARY KEY DEFAULT gen_random_uuid()` |
| `tenant_id` | `UUID NOT NULL` |
| `connector_instance_id` | `UUID REFERENCES nx_connector_instances(id) ON DELETE CASCADE` |
| `platform` | `VARCHAR(50) NOT NULL` |
| `topic` | `VARCHAR(100) NOT NULL` |
| `callback_url` | `VARCHAR(512) NOT NULL` |
| `is_registered` | `BOOLEAN DEFAULT false` |
| `external_id` | `VARCHAR(255)` |
| `last_triggered_at` | `TIMESTAMP` |
| `last_response_code` | `INT` |
| `registered_at` | `TIMESTAMP` |
| `created_at` | `TIMESTAMP NOT NULL DEFAULT now()` |
| `updated_at` | `TIMESTAMP NOT NULL DEFAULT now()` |

**Table-level constraints:**
- `UNIQUE(connector_instance_id, topic)`


### nx_bigcommerce_config — BigCommerce connector config

| Column | Definition |
|---|---|
| `id` | `UUID PRIMARY KEY DEFAULT uuid_generate_v4()` |
| `tenant_id` | `UUID NOT NULL` |
| `store_hash` | `VARCHAR(255) NOT NULL` |
| `access_token` | `VARCHAR(512) NOT NULL` |
| `client_id` | `VARCHAR(255)` |
| `api_path` | `VARCHAR(255) DEFAULT 'https://api.bigcommerce.com'` |
| `is_active` | `BOOLEAN DEFAULT TRUE` |
| `auto_sync_orders` | `BOOLEAN DEFAULT FALSE` |
| `auto_sync_inventory` | `BOOLEAN DEFAULT FALSE` |
| `sync_interval_minutes` | `INT DEFAULT 15` |
| `last_order_sync_at` | `TIMESTAMP` |
| `last_product_sync_at` | `TIMESTAMP` |
| `last_inventory_sync_at` | `TIMESTAMP` |
| `created_at` | `TIMESTAMP NOT NULL DEFAULT NOW()` |
| `updated_at` | `TIMESTAMP NOT NULL DEFAULT NOW()` |

**Indexes:**
- `idx_bc_config_tenant (tenant_id)`


### nx_bigcommerce_webhooks — BigCommerce webhook registrations

| Column | Definition |
|---|---|
| `id` | `UUID PRIMARY KEY DEFAULT uuid_generate_v4()` |
| `tenant_id` | `UUID NOT NULL` |
| `webhook_id` | `INTEGER NOT NULL` |
| `scope` | `VARCHAR(255) NOT NULL` |
| `destination` | `VARCHAR(512) NOT NULL` |
| `is_active` | `BOOLEAN DEFAULT TRUE` |
| `created_at` | `TIMESTAMP NOT NULL DEFAULT NOW()` |


### nx_shopify_webhooks — Shopify webhook registrations

| Column | Definition |
|---|---|
| `id` | `UUID PRIMARY KEY DEFAULT uuid_generate_v4()` |
| `tenant_id` | `UUID NOT NULL` |
| `store_id` | `UUID NOT NULL REFERENCES nx_integration_stores(id) ON DELETE CASCADE` |
| `shopify_webhook_id` | `BIGINT NOT NULL` |
| `topic` | `VARCHAR(255) NOT NULL` |
| `address` | `VARCHAR(512) NOT NULL` |
| `is_active` | `BOOLEAN DEFAULT TRUE` |
| `created_at` | `TIMESTAMP NOT NULL DEFAULT NOW()` |

**Indexes:**
- `idx_shopify_webhooks_store (store_id)`


### nx_product_mappings — Channel → Nexus product mappings

| Column | Definition |
|---|---|
| `id` | `UUID PRIMARY KEY DEFAULT uuid_generate_v4()` |
| `tenant_id` | `UUID NOT NULL` |
| `bc_product_id` | `INTEGER NOT NULL` |
| `bc_variant_id` | `INTEGER` |
| `bc_sku` | `VARCHAR(100)` |
| `nexus_sku` | `VARCHAR(100) NOT NULL` |
| `nexus_product_name` | `VARCHAR(255)` |
| `last_synced_at` | `TIMESTAMP` |
| `created_at` | `TIMESTAMP NOT NULL DEFAULT NOW()` |
| `updated_at` | `TIMESTAMP NOT NULL DEFAULT NOW()` |

**Indexes:**
- `idx_product_mappings_tenant (tenant_id)`
- `idx_product_mappings_sku (bc_sku)`


### nx_email_ingestion_config — Email ingestion config

| Column | Definition |
|---|---|
| `id` | `UUID PRIMARY KEY DEFAULT gen_random_uuid()` |
| `tenant_id` | `UUID NOT NULL UNIQUE` |
| `protocol` | `VARCHAR(10) DEFAULT 'IMAP' CHECK (protocol IN ('IMAP','POP3','GRAPH_API'))` |
| `host` | `VARCHAR(255)` |
| `port` | `INT DEFAULT 993` |
| `username` | `VARCHAR(255)` |
| `encrypted_password` | `TEXT` |
| `use_ssl` | `BOOLEAN DEFAULT true` |
| `inbox_folder` | `VARCHAR(100) DEFAULT 'INBOX'` |
| `processed_folder` | `VARCHAR(100) DEFAULT 'Processed'` |
| `failed_folder` | `VARCHAR(100) DEFAULT 'Failed'` |
| `polling_interval_sec` | `INT DEFAULT 300` |
| `allowed_senders` | `TEXT` |
| `subject_filter` | `VARCHAR(255)` |
| `auto_create_orders` | `BOOLEAN DEFAULT false` |
| `send_confirmation` | `BOOLEAN DEFAULT true` |
| `is_active` | `BOOLEAN DEFAULT false` |
| `last_polled_at` | `TIMESTAMP` |
| `created_at` | `TIMESTAMP DEFAULT now()` |
| `updated_at` | `TIMESTAMP DEFAULT now()` |


### nx_email_parsed_orders — Orders parsed from email

| Column | Definition |
|---|---|
| `id` | `UUID PRIMARY KEY DEFAULT gen_random_uuid()` |
| `tenant_id` | `UUID NOT NULL` |
| `email_message_id` | `VARCHAR(255)` |
| `email_subject` | `VARCHAR(512)` |
| `email_from` | `VARCHAR(255)` |
| `email_to` | `VARCHAR(255)` |
| `email_received_at` | `TIMESTAMP` |
| `attachment_filename` | `VARCHAR(255)` |
| `attachment_type` | `VARCHAR(30) CHECK (attachment_type IN ('PDF','CSV','HTML','TEXT','NONE'))` |
| `raw_body` | `TEXT` |
| `parsed_data` | `JSONB` |
| `order_id` | `UUID REFERENCES nx_orders(id) ON DELETE SET NULL` |
| `status` | `VARCHAR(30) DEFAULT 'NEW' CHECK (status IN ('NEW','PARSED','PENDING_REVIEW','APPROVED','REJECTED','DUPLICATE','FAILED'))` |
| `confidence_score` | `DECIMAL(5,4) DEFAULT 0.0` |
| `customer_name` | `VARCHAR(255)` |
| `customer_email` | `VARCHAR(255)` |
| `customer_phone` | `VARCHAR(50)` |
| `order_total` | `DECIMAL(12,2)` |
| `item_count` | `INT DEFAULT 0` |
| `shipping_address` | `JSONB` |
| `rejection_reason` | `TEXT` |
| `matched_customer_id` | `UUID` |
| `created_at` | `TIMESTAMP DEFAULT now()` |
| `processed_at` | `TIMESTAMP` |

**Indexes:**
- `idx_nx_email_parsed_orders_tenant (tenant_id)`
- `idx_nx_email_parsed_orders_status (status)`
- `idx_nx_email_parsed_orders_message (email_message_id)`


### nx_sync_conflicts — Sync conflict records

| Column | Definition |
|---|---|
| `id` | `UUID PRIMARY KEY DEFAULT gen_random_uuid()` |
| `tenant_id` | `UUID NOT NULL` |
| `entity_type` | `VARCHAR(50) NOT NULL` |
| `entity_id` | `UUID NOT NULL` |
| `direction` | `VARCHAR(20) NOT NULL` |
| `source_system` | `VARCHAR(50) NOT NULL` |
| `inbound_version` | `BIGINT` |
| `local_version` | `BIGINT` |
| `conflicting_fields` | `JSONB` |
| `resolution` | `VARCHAR(20) DEFAULT 'PENDING'` |
| `status` | `VARCHAR(20) DEFAULT 'OPEN'` |
| `created_at` | `TIMESTAMP NOT NULL DEFAULT now()` |
| `resolved_at` | `TIMESTAMP` |
| `resolved_by` | `UUID` |

**Indexes:**
- `idx_sync_conflicts_tenant (tenant_id, status)`
- `idx_sync_conflicts_entity (entity_type, entity_id)`


### nx_sync_field_mappings — Sync field mappings

| Column | Definition |
|---|---|
| `id` | `UUID PRIMARY KEY DEFAULT gen_random_uuid()` |
| `tenant_id` | `UUID NOT NULL` |
| `entity_type` | `VARCHAR(50) NOT NULL` |
| `field_name` | `VARCHAR(100) NOT NULL` |
| `direction` | `VARCHAR(20) NOT NULL` |
| `winner` | `VARCHAR(20) NOT NULL` |
| `is_active` | `BOOLEAN NOT NULL DEFAULT TRUE` |
| `created_at` | `TIMESTAMP NOT NULL DEFAULT now()` |

**Table-level constraints:**
- `UNIQUE (tenant_id, entity_type, field_name, direction)`


### nx_sync_job_status — Sync job status

| Column | Definition |
|---|---|
| `id` | `UUID PRIMARY KEY DEFAULT gen_random_uuid()` |
| `tenant_id` | `UUID NOT NULL` |
| `job_name` | `VARCHAR(128) NOT NULL` |
| `direction` | `VARCHAR(20) NOT NULL` |
| `status` | `VARCHAR(20) NOT NULL` |
| `records_total` | `INTEGER DEFAULT 0` |
| `records_processed` | `INTEGER DEFAULT 0` |
| `records_failed` | `INTEGER DEFAULT 0` |
| `last_run_at` | `TIMESTAMP` |
| `next_run_at` | `TIMESTAMP` |
| `created_at` | `TIMESTAMP NOT NULL DEFAULT now()` |
| `updated_at` | `TIMESTAMP NOT NULL DEFAULT now()` |

**Indexes:**
- `idx_sync_job_status_tenant (tenant_id, status)`


### nx_sync_logs — Sync logs

| Column | Definition |
|---|---|
| `id` | `UUID PRIMARY KEY DEFAULT uuid_generate_v4()` |
| `tenant_id` | `UUID NOT NULL` |
| `integration_type` | `VARCHAR(50) NOT NULL` |
| `sync_type` | `VARCHAR(50) NOT NULL` |
| `status` | `VARCHAR(20) NOT NULL` |
| `started_at` | `TIMESTAMP NOT NULL DEFAULT NOW()` |
| `completed_at` | `TIMESTAMP` |
| `items_processed` | `INT DEFAULT 0` |
| `items_succeeded` | `INT DEFAULT 0` |
| `items_failed` | `INT DEFAULT 0` |
| `error_message` | `TEXT` |
| `details` | `JSONB` |
| `created_at` | `TIMESTAMP NOT NULL DEFAULT NOW()` |

**Indexes:**
- `idx_sync_logs_tenant (tenant_id, integration_type, created_at DESC)`


---

## EDI & Bulk Import/Export


### nx_edi_partners — EDI trading partners

| Column | Definition |
|---|---|
| `id` | `UUID PRIMARY KEY DEFAULT gen_random_uuid()` |
| `tenant_id` | `UUID NOT NULL` |
| `partner_code` | `VARCHAR(50) NOT NULL` |
| `partner_name` | `VARCHAR(255) NOT NULL` |
| `qualifier` | `VARCHAR(10) DEFAULT 'ZZ'` |
| `interchange_id` | `VARCHAR(100)` |
| `is_active` | `BOOLEAN DEFAULT true` |
| `supported_docs` | `JSONB` |
| `created_at` | `TIMESTAMP DEFAULT now()` |
| `updated_at` | `TIMESTAMP DEFAULT now()` |

**Indexes:**
- `idx_nx_edi_partners_tenant (tenant_id)`
- `idx_nx_edi_partners_code_tenant (tenant_id, partner_code) UNIQUE`


### nx_edi_documents — EDI documents (850/856/810)

| Column | Definition |
|---|---|
| `id` | `UUID PRIMARY KEY DEFAULT gen_random_uuid()` |
| `tenant_id` | `UUID NOT NULL` |
| `doc_type` | `VARCHAR(10) NOT NULL CHECK (doc_type IN ('850','856','810'))` |
| `filename` | `VARCHAR(512)` |
| `raw_content` | `TEXT` |
| `parsed_status` | `VARCHAR(30) DEFAULT 'PENDING' CHECK (parsed_status IN ('PENDING','PARSED','FAILED','VALIDATED','ERROR'))` |
| `parsed_data` | `JSONB` |
| `validation_errors` | `JSONB` |
| `order_id` | `UUID REFERENCES nx_orders(id) ON DELETE SET NULL` |
| `shipment_id` | `UUID` |
| `invoice_id` | `UUID` |
| `partner_id` | `VARCHAR(100)` |
| `partner_name` | `VARCHAR(255)` |
| `control_number` | `VARCHAR(50)` |
| `interchange_control_number` | `VARCHAR(50)` |
| `group_control_number` | `VARCHAR(50)` |
| `test_indicator` | `BOOLEAN DEFAULT false` |
| `processed_at` | `TIMESTAMP` |
| `error_message` | `TEXT` |
| `created_at` | `TIMESTAMP DEFAULT now()` |
| `updated_at` | `TIMESTAMP DEFAULT now()` |
| `id` | `UUID PRIMARY KEY DEFAULT gen_random_uuid()` |
| `tenant_id` | `UUID NOT NULL` |
| `connector_instance_id` | `UUID REFERENCES nx_connector_instances(id)` |
| `direction` | `VARCHAR(10) NOT NULL DEFAULT 'INBOUND'` |
| `standard` | `VARCHAR(10) NOT NULL DEFAULT 'X12'` |
| `document_type` | `VARCHAR(10) NOT NULL` |
| `version` | `VARCHAR(10) DEFAULT '004010'` |
| `sender_id` | `VARCHAR(100)` |
| `receiver_id` | `VARCHAR(100)` |
| `control_number` | `VARCHAR(50)` |
| `raw_content` | `TEXT` |
| `parsed_json` | `JSONB` |
| `status` | `VARCHAR(20) DEFAULT 'RECEIVED'` |
| `processing_notes` | `TEXT` |
| `order_id` | `UUID` |
| `received_at` | `TIMESTAMP NOT NULL DEFAULT now()` |
| `processed_at` | `TIMESTAMP` |
| `created_at` | `TIMESTAMP NOT NULL DEFAULT now()` |

**Indexes:**
- `idx_nx_edi_documents_type (document_type)`
- `idx_nx_edi_documents_type (doc_type)`
- `idx_nx_edi_documents_status (parsed_status)`
- `idx_nx_edi_documents_status (status)`
- `idx_nx_edi_documents_tenant (tenant_id)`
- `idx_nx_edi_documents_order (order_id)`
- `idx_edi_documents_tenant (tenant_id)`
- `idx_edi_documents_type (document_type)`
- `idx_edi_documents_status (status)`


### webhook_dedup_ledger — Inbound webhook dedup ledger

| Column | Definition |
|---|---|
| `id` | `UUID PRIMARY KEY DEFAULT gen_random_uuid()` |
| `tenant_id` | `UUID NOT NULL` |
| `source` | `VARCHAR(50) NOT NULL` |
| `external_id` | `VARCHAR(100) NOT NULL` |
| `event_id` | `VARCHAR(200)` |
| `status` | `VARCHAR(20) DEFAULT 'PROCESSED'` |
| `processed_at` | `TIMESTAMP NOT NULL DEFAULT NOW()` |

**Table-level constraints:**
- `UNIQUE (source, external_id)`

**Indexes:**
- `idx_webhook_dedup_source_external (source, external_id)`
- `idx_webhook_dedup_tenant (tenant_id, processed_at DESC)`


---

## Identity, RBAC & Tenancy


### nx_users — Users

| Column | Definition |
|---|---|
| `id` | `UUID PRIMARY KEY DEFAULT uuid_generate_v4()` |
| `username` | `VARCHAR(100) NOT NULL UNIQUE` |
| `password_hash` | `VARCHAR(255) NOT NULL` |
| `email` | `VARCHAR(255)` |
| `role` | `VARCHAR(20) NOT NULL DEFAULT 'VIEWER'` |
| `tenant_id` | `UUID` |
| `created_at` | `TIMESTAMP NOT NULL DEFAULT NOW()` |
| `updated_at` | `TIMESTAMP NOT NULL DEFAULT NOW()` |


### nx_role_permissions — Role-permission grants

| Column | Definition |
|---|---|
| `id` | `UUID PRIMARY KEY DEFAULT gen_random_uuid()` |
| `tenant_id` | `UUID` |
| `role` | `VARCHAR(50) NOT NULL` |
| `permission_group` | `VARCHAR(100) NOT NULL` |
| `permission_name` | `VARCHAR(100) NOT NULL` |
| `can_view` | `BOOLEAN DEFAULT false` |
| `can_create` | `BOOLEAN DEFAULT false` |
| `can_edit` | `BOOLEAN DEFAULT false` |
| `can_delete` | `BOOLEAN DEFAULT false` |
| `can_approve` | `BOOLEAN DEFAULT false` |
| `created_at` | `TIMESTAMP NOT NULL DEFAULT now()` |

**Table-level constraints:**
- `UNIQUE(tenant_id, role, permission_group, permission_name)`


### nx_user_roles — User-role grants

| Column | Definition |
|---|---|
| `id` | `UUID PRIMARY KEY DEFAULT gen_random_uuid()` |
| `tenant_id` | `UUID NOT NULL` |
| `user_id` | `UUID NOT NULL` |
| `role` | `VARCHAR(50) NOT NULL` |
| `team` | `VARCHAR(100)` |
| `department` | `VARCHAR(100)` |
| `is_active` | `BOOLEAN DEFAULT true` |
| `assigned_by` | `UUID` |
| `assigned_at` | `TIMESTAMP NOT NULL DEFAULT now()` |

**Table-level constraints:**
- `UNIQUE(tenant_id, user_id, role)`


### nx_teams — Teams

| Column | Definition |
|---|---|
| `id` | `UUID PRIMARY KEY DEFAULT gen_random_uuid()` |
| `tenant_id` | `UUID NOT NULL` |
| `name` | `VARCHAR(100) NOT NULL` |
| `description` | `TEXT` |
| `manager_id` | `UUID` |
| `is_active` | `BOOLEAN DEFAULT true` |
| `created_at` | `TIMESTAMP NOT NULL DEFAULT now()` |

**Table-level constraints:**
- `UNIQUE(tenant_id, name)`


### nx_company_settings — Company/tenant settings

| Column | Definition |
|---|---|
| `id` | `UUID PRIMARY KEY DEFAULT gen_random_uuid()` |
| `tenant_id` | `UUID NOT NULL UNIQUE` |
| `company_name` | `VARCHAR(255)` |
| `company_logo` | `VARCHAR(512)` |
| `tax_id` | `VARCHAR(50)` |
| `registration_number` | `VARCHAR(50)` |
| `default_currency` | `VARCHAR(3) DEFAULT 'USD'` |
| `default_language` | `VARCHAR(10) DEFAULT 'en'` |
| `default_timezone` | `VARCHAR(50) DEFAULT 'UTC'` |
| `date_format` | `VARCHAR(20) DEFAULT 'MM/DD/YYYY'` |
| `time_format` | `VARCHAR(10) DEFAULT '24h'` |
| `fiscal_year_start` | `VARCHAR(5) DEFAULT '01-01'` |
| `countries` | `JSONB` |
| `regions` | `JSONB` |
| `holidays` | `JSONB` |
| `feature_flags` | `JSONB` |
| `security_policy` | `JSONB` |
| `backup_config` | `JSONB` |
| `created_at` | `TIMESTAMP NOT NULL DEFAULT now()` |
| `updated_at` | `TIMESTAMP NOT NULL DEFAULT now()` |


### nx_audit_log — Audit log

| Column | Definition |
|---|---|
| `id` | `UUID PRIMARY KEY DEFAULT uuid_generate_v4()` |
| `tenant_id` | `UUID` |
| `entity_type` | `VARCHAR(100)` |
| `entity_id` | `UUID` |
| `event_type` | `VARCHAR(100)` |
| `actor_id` | `UUID` |
| `actor_type` | `VARCHAR(50)` |
| `data` | `JSONB` |
| `created_at` | `TIMESTAMP NOT NULL DEFAULT NOW()` |

**Indexes:**
- `idx_audit_entity (entity_type, entity_id)`
- `idx_audit_tenant (tenant_id, created_at)`


---

## Platform & Workflow


### nx_workflows — Workflow definitions

| Column | Definition |
|---|---|
| `id` | `UUID PRIMARY KEY DEFAULT gen_random_uuid()` |
| `tenant_id` | `UUID NOT NULL` |
| `name` | `VARCHAR(255) NOT NULL` |
| `description` | `TEXT` |
| `category` | `VARCHAR(50) NOT NULL DEFAULT 'ORDER'` |
| `trigger_type` | `VARCHAR(50) NOT NULL` |
| `trigger_config` | `JSONB` |
| `status` | `VARCHAR(20) DEFAULT 'DRAFT'` |
| `is_active` | `BOOLEAN DEFAULT false` |
| `version` | `INT DEFAULT 1` |
| `created_by` | `UUID` |
| `created_at` | `TIMESTAMP NOT NULL DEFAULT now()` |
| `updated_at` | `TIMESTAMP NOT NULL DEFAULT now()` |


### nx_workflow_steps — Workflow steps

| Column | Definition |
|---|---|
| `id` | `UUID PRIMARY KEY DEFAULT gen_random_uuid()` |
| `workflow_id` | `UUID NOT NULL REFERENCES nx_workflows(id) ON DELETE CASCADE` |
| `step_order` | `INT NOT NULL` |
| `step_type` | `VARCHAR(50) NOT NULL` |
| `step_name` | `VARCHAR(255) NOT NULL` |
| `config` | `JSONB NOT NULL` |
| `condition_expression` | `TEXT` |
| `on_failure` | `VARCHAR(30) DEFAULT 'STOP'` |
| `timeout_seconds` | `INT DEFAULT 300` |
| `created_at` | `TIMESTAMP NOT NULL DEFAULT now()` |


### nx_workflow_executions — Workflow executions

| Column | Definition |
|---|---|
| `id` | `UUID PRIMARY KEY DEFAULT gen_random_uuid()` |
| `tenant_id` | `UUID NOT NULL` |
| `workflow_id` | `UUID NOT NULL REFERENCES nx_workflows(id)` |
| `trigger_entity_type` | `VARCHAR(50)` |
| `trigger_entity_id` | `UUID` |
| `status` | `VARCHAR(20) DEFAULT 'RUNNING'` |
| `current_step` | `INT DEFAULT 0` |
| `total_steps` | `INT DEFAULT 0` |
| `input_data` | `JSONB` |
| `output_data` | `JSONB` |
| `error_message` | `TEXT` |
| `started_at` | `TIMESTAMP NOT NULL DEFAULT now()` |
| `completed_at` | `TIMESTAMP` |
| `duration_ms` | `BIGINT` |
| `created_at` | `TIMESTAMP NOT NULL DEFAULT now()` |

**Indexes:**
- `idx_wf_exec_tenant (tenant_id)`
- `idx_wf_exec_status (status)`


### nx_documents — Documents

| Column | Definition |
|---|---|
| `id` | `UUID PRIMARY KEY DEFAULT gen_random_uuid()` |
| `tenant_id` | `UUID NOT NULL` |
| `document_number` | `VARCHAR(50)` |
| `title` | `VARCHAR(255) NOT NULL` |
| `description` | `TEXT` |
| `document_type` | `VARCHAR(50) NOT NULL` |
| `category` | `VARCHAR(50)` |
| `file_name` | `VARCHAR(255) NOT NULL` |
| `file_size` | `BIGINT` |
| `mime_type` | `VARCHAR(100)` |
| `file_url` | `VARCHAR(512)` |
| `storage_path` | `VARCHAR(512)` |
| `file_hash` | `VARCHAR(64)` |
| `current_version` | `INT DEFAULT 1` |
| `entity_type` | `VARCHAR(50)` |
| `entity_id` | `UUID` |
| `is_public` | `BOOLEAN DEFAULT false` |
| `tags` | `JSONB` |
| `metadata` | `JSONB` |
| `uploaded_by` | `UUID` |
| `created_at` | `TIMESTAMP NOT NULL DEFAULT now()` |
| `updated_at` | `TIMESTAMP NOT NULL DEFAULT now()` |

**Indexes:**
- `idx_docs_tenant (tenant_id)`
- `idx_docs_entity (entity_type, entity_id)`
- `idx_docs_type (document_type)`


### nx_document_versions — Document versions

| Column | Definition |
|---|---|
| `id` | `UUID PRIMARY KEY DEFAULT gen_random_uuid()` |
| `document_id` | `UUID NOT NULL REFERENCES nx_documents(id) ON DELETE CASCADE` |
| `version_number` | `INT NOT NULL` |
| `file_name` | `VARCHAR(255) NOT NULL` |
| `file_size` | `BIGINT` |
| `file_url` | `VARCHAR(512)` |
| `storage_path` | `VARCHAR(512)` |
| `file_hash` | `VARCHAR(64)` |
| `change_notes` | `TEXT` |
| `uploaded_by` | `UUID` |
| `created_at` | `TIMESTAMP NOT NULL DEFAULT now()` |

**Table-level constraints:**
- `UNIQUE(document_id, version_number)`


### nx_notification_templates — Notification templates

| Column | Definition |
|---|---|
| `id` | `UUID PRIMARY KEY DEFAULT gen_random_uuid()` |
| `tenant_id` | `UUID NOT NULL` |
| `template_code` | `VARCHAR(50) NOT NULL` |
| `name` | `VARCHAR(255) NOT NULL` |
| `channel` | `VARCHAR(20) NOT NULL DEFAULT 'EMAIL'` |
| `subject` | `VARCHAR(255)` |
| `body` | `TEXT NOT NULL` |
| `variables` | `JSONB` |
| `is_active` | `BOOLEAN DEFAULT true` |
| `created_at` | `TIMESTAMP NOT NULL DEFAULT now()` |
| `updated_at` | `TIMESTAMP NOT NULL DEFAULT now()` |

**Table-level constraints:**
- `UNIQUE(tenant_id, template_code, channel)`


### nx_notification_logs — Notification delivery log

| Column | Definition |
|---|---|
| `id` | `UUID PRIMARY KEY DEFAULT gen_random_uuid()` |
| `tenant_id` | `UUID NOT NULL` |
| `channel` | `VARCHAR(20) NOT NULL` |
| `recipient` | `VARCHAR(255) NOT NULL` |
| `subject` | `VARCHAR(255)` |
| `body` | `TEXT` |
| `status` | `VARCHAR(20) DEFAULT 'PENDING'` |
| `reference_type` | `VARCHAR(50)` |
| `reference_id` | `UUID` |
| `error_message` | `TEXT` |
| `sent_at` | `TIMESTAMP` |
| `read_at` | `TIMESTAMP` |
| `created_at` | `TIMESTAMP NOT NULL DEFAULT now()` |

**Indexes:**
- `idx_notif_logs_tenant (tenant_id)`
- `idx_notif_logs_status (status)`
- `idx_notif_logs_reference (reference_type, reference_id)`


### nx_mcp_tools — MCP tool definitions

| Column | Definition |
|---|---|
| `id` | `UUID PRIMARY KEY DEFAULT gen_random_uuid()` |
| `tenant_id` | `UUID NOT NULL` |
| `tool_name` | `VARCHAR(128) NOT NULL` |
| `description` | `TEXT` |
| `access` | `VARCHAR(10) NOT NULL` |
| `endpoint` | `JSONB` |
| `is_active` | `BOOLEAN NOT NULL DEFAULT TRUE` |
| `created_at` | `TIMESTAMP NOT NULL DEFAULT now()` |

**Table-level constraints:**
- `UNIQUE (tenant_id, tool_name)`

**Indexes:**
- `idx_mcp_tools_tenant (tenant_id, is_active)`


### nx_mcp_agent_budgets — MCP agent budget limits

| Column | Definition |
|---|---|
| `id` | `UUID PRIMARY KEY DEFAULT gen_random_uuid()` |
| `tenant_id` | `UUID NOT NULL` |
| `agent_name` | `VARCHAR(128) NOT NULL` |
| `query_budget` | `INTEGER NOT NULL DEFAULT 100` |
| `cost_budget` | `DECIMAL(12,2) NOT NULL DEFAULT 100.00` |
| `period` | `VARCHAR(20) NOT NULL DEFAULT 'DAILY'` |
| `is_active` | `BOOLEAN NOT NULL DEFAULT TRUE` |
| `created_at` | `TIMESTAMP NOT NULL DEFAULT now()` |

**Indexes:**
- `idx_mcp_budgets_tenant (tenant_id, agent_name)`


### nx_workload_rules — Workload management rules

| Column | Definition |
|---|---|
| `id` | `UUID PRIMARY KEY DEFAULT gen_random_uuid()` |
| `tenant_id` | `UUID NOT NULL` |
| `warehouse_id` | `UUID NOT NULL` |
| `rule_name` | `VARCHAR(100) NOT NULL` |
| `task_type` | `VARCHAR(50) NOT NULL` |
| `max_workload_weight` | `DECIMAL(6,2) DEFAULT 10.0` |
| `priority_weight` | `DECIMAL(5,2) DEFAULT 1.0` |
| `skill_required` | `VARCHAR(50)` |
| `is_active` | `BOOLEAN DEFAULT TRUE` |
| `notes` | `TEXT` |
| `created_at` | `TIMESTAMP NOT NULL DEFAULT NOW()` |
| `updated_at` | `TIMESTAMP NOT NULL DEFAULT NOW()` |
