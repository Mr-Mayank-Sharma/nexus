-- V67: Fix Hibernate↔Flyway schema drift
-- Fixes: missing columns, type mismatches, table renames, EDI document schema, freight table naming
-- All statements use IF EXISTS/IF NOT EXISTS guards for safe re-entrancy
-- Works on: fresh DB (all migrations), existing dev DB (Hibernate-created), existing prod DB (V45 manually applied)

-- ============================================================
-- 1. EDI DOCUMENTS - Fix V8→V16 column drift (V16 CREATE TABLE IF NOT EXISTS was no-op)
-- ============================================================

DO $$
BEGIN
    IF EXISTS (SELECT 1 FROM information_schema.tables WHERE table_name = 'nx_edi_documents') THEN
        -- Add columns that entity expects but V8 didn't create
        IF NOT EXISTS (SELECT 1 FROM information_schema.columns WHERE table_name = 'nx_edi_documents' AND column_name = 'doc_type') THEN
            ALTER TABLE nx_edi_documents ADD COLUMN doc_type VARCHAR(50);
            UPDATE nx_edi_documents SET doc_type = document_type WHERE doc_type IS NULL AND document_type IS NOT NULL;
        END IF;
        IF NOT EXISTS (SELECT 1 FROM information_schema.columns WHERE table_name = 'nx_edi_documents' AND column_name = 'filename') THEN
            ALTER TABLE nx_edi_documents ADD COLUMN filename VARCHAR(255);
        END IF;
        IF NOT EXISTS (SELECT 1 FROM information_schema.columns WHERE table_name = 'nx_edi_documents' AND column_name = 'parsed_status') THEN
            ALTER TABLE nx_edi_documents ADD COLUMN parsed_status VARCHAR(20) DEFAULT 'UNPARSED';
        END IF;
        IF NOT EXISTS (SELECT 1 FROM information_schema.columns WHERE table_name = 'nx_edi_documents' AND column_name = 'parsed_data') THEN
            ALTER TABLE nx_edi_documents ADD COLUMN parsed_data jsonb;
            UPDATE nx_edi_documents SET parsed_data = parsed_json WHERE parsed_data IS NULL AND parsed_json IS NOT NULL;
        END IF;
        IF NOT EXISTS (SELECT 1 FROM information_schema.columns WHERE table_name = 'nx_edi_documents' AND column_name = 'validation_errors') THEN
            ALTER TABLE nx_edi_documents ADD COLUMN validation_errors jsonb;
        END IF;
        IF NOT EXISTS (SELECT 1 FROM information_schema.columns WHERE table_name = 'nx_edi_documents' AND column_name = 'error_message') THEN
            ALTER TABLE nx_edi_documents ADD COLUMN error_message text;
        END IF;
        IF NOT EXISTS (SELECT 1 FROM information_schema.columns WHERE table_name = 'nx_edi_documents' AND column_name = 'updated_at') THEN
            ALTER TABLE nx_edi_documents ADD COLUMN updated_at timestamp;
        END IF;
        IF NOT EXISTS (SELECT 1 FROM information_schema.columns WHERE table_name = 'nx_edi_documents' AND column_name = 'partner_id') THEN
            ALTER TABLE nx_edi_documents ADD COLUMN partner_id varchar(255);
        ELSE
            -- Coerce existing uuid-typed partner_id to varchar(255) to match entity (String)
            IF EXISTS (SELECT 1 FROM information_schema.columns WHERE table_name = 'nx_edi_documents' AND column_name = 'partner_id' AND udt_name = 'uuid') THEN
                ALTER TABLE nx_edi_documents ALTER COLUMN partner_id TYPE varchar(255) USING partner_id::text;
            END IF;
        END IF;
        IF NOT EXISTS (SELECT 1 FROM information_schema.columns WHERE table_name = 'nx_edi_documents' AND column_name = 'partner_name') THEN
            ALTER TABLE nx_edi_documents ADD COLUMN partner_name varchar(100);
        END IF;
        IF NOT EXISTS (SELECT 1 FROM information_schema.columns WHERE table_name = 'nx_edi_documents' AND column_name = 'test_indicator') THEN
            ALTER TABLE nx_edi_documents ADD COLUMN test_indicator boolean DEFAULT false;
        END IF;
        IF NOT EXISTS (SELECT 1 FROM information_schema.columns WHERE table_name = 'nx_edi_documents' AND column_name = 'interchange_control_number') THEN
            ALTER TABLE nx_edi_documents ADD COLUMN interchange_control_number varchar(50);
        END IF;
        IF NOT EXISTS (SELECT 1 FROM information_schema.columns WHERE table_name = 'nx_edi_documents' AND column_name = 'group_control_number') THEN
            ALTER TABLE nx_edi_documents ADD COLUMN group_control_number varchar(50);
        END IF;
        IF NOT EXISTS (SELECT 1 FROM information_schema.columns WHERE table_name = 'nx_edi_documents' AND column_name = 'shipment_id') THEN
            ALTER TABLE nx_edi_documents ADD COLUMN shipment_id uuid;
        END IF;
        IF NOT EXISTS (SELECT 1 FROM information_schema.columns WHERE table_name = 'nx_edi_documents' AND column_name = 'invoice_id') THEN
            ALTER TABLE nx_edi_documents ADD COLUMN invoice_id uuid;
        END IF;
        -- Drop V8-only columns that entity doesn't have
        IF EXISTS (SELECT 1 FROM information_schema.columns WHERE table_name = 'nx_edi_documents' AND column_name = 'connector_instance_id') THEN
            ALTER TABLE nx_edi_documents DROP COLUMN connector_instance_id;
        END IF;
        IF EXISTS (SELECT 1 FROM information_schema.columns WHERE table_name = 'nx_edi_documents' AND column_name = 'direction') THEN
            ALTER TABLE nx_edi_documents DROP COLUMN direction;
        END IF;
        IF EXISTS (SELECT 1 FROM information_schema.columns WHERE table_name = 'nx_edi_documents' AND column_name = 'standard') THEN
            ALTER TABLE nx_edi_documents DROP COLUMN standard;
        END IF;
        IF EXISTS (SELECT 1 FROM information_schema.columns WHERE table_name = 'nx_edi_documents' AND column_name = 'version') THEN
            ALTER TABLE nx_edi_documents DROP COLUMN version;
        END IF;
        IF EXISTS (SELECT 1 FROM information_schema.columns WHERE table_name = 'nx_edi_documents' AND column_name = 'sender_id') THEN
            ALTER TABLE nx_edi_documents DROP COLUMN sender_id;
        END IF;
        IF EXISTS (SELECT 1 FROM information_schema.columns WHERE table_name = 'nx_edi_documents' AND column_name = 'receiver_id') THEN
            ALTER TABLE nx_edi_documents DROP COLUMN receiver_id;
        END IF;
        IF EXISTS (SELECT 1 FROM information_schema.columns WHERE table_name = 'nx_edi_documents' AND column_name = 'processing_notes') THEN
            ALTER TABLE nx_edi_documents DROP COLUMN processing_notes;
        END IF;
        IF EXISTS (SELECT 1 FROM information_schema.columns WHERE table_name = 'nx_edi_documents' AND column_name = 'received_at') THEN
            ALTER TABLE nx_edi_documents DROP COLUMN received_at;
        END IF;
    END IF;
END $$;

-- ============================================================
-- 2. FREIGHT TABLE RENAMES - V45 created nxFreight_* but entity expects nx_freight_*
-- ============================================================

DO $$
BEGIN
    -- If old nxfreight_* exists and new nx_freight_* does NOT exist -> rename (preserves data)
    -- If old nxfreight_* exists and new nx_freight_* DOES exist -> Hibernate-created table is
    --   authoritative (matches entities); drop the orphaned old table to avoid duplicate/conflict.
    IF EXISTS (SELECT 1 FROM information_schema.tables WHERE table_name = 'nxfreight_invoices') THEN
        IF NOT EXISTS (SELECT 1 FROM information_schema.tables WHERE table_name = 'nx_freight_invoices') THEN
            ALTER TABLE nxfreight_invoices RENAME TO nx_freight_invoices;
        ELSE
            DROP TABLE IF EXISTS nxfreight_invoices;
        END IF;
    END IF;
    IF EXISTS (SELECT 1 FROM information_schema.tables WHERE table_name = 'nxfreight_invoice_lines') THEN
        IF NOT EXISTS (SELECT 1 FROM information_schema.tables WHERE table_name = 'nx_freight_invoice_lines') THEN
            ALTER TABLE nxfreight_invoice_lines RENAME TO nx_freight_invoice_lines;
        ELSE
            DROP TABLE IF EXISTS nxfreight_invoice_lines;
        END IF;
    END IF;
    IF EXISTS (SELECT 1 FROM information_schema.tables WHERE table_name = 'nxfreight_audit_logs') THEN
        IF NOT EXISTS (SELECT 1 FROM information_schema.tables WHERE table_name = 'nx_freight_audit_logs') THEN
            ALTER TABLE nxfreight_audit_logs RENAME TO nx_freight_audit_logs;
        ELSE
            DROP TABLE IF EXISTS nxfreight_audit_logs;
        END IF;
    END IF;
END $$;

-- Fix RLS policies that reference old table names (V26)
-- Ensure the tenant-isolation policy exists on the canonical nx_freight_* tables
DO $$
BEGIN
    IF EXISTS (SELECT 1 FROM information_schema.tables WHERE table_name = 'nx_freight_invoices') THEN
        DROP POLICY IF EXISTS freight_invoice_tenant_isolation ON nx_freight_invoices;
        CREATE POLICY freight_invoice_tenant_isolation ON nx_freight_invoices
            USING (tenant_id = current_setting('app.current_tenant_id')::uuid);
    END IF;
    IF EXISTS (SELECT 1 FROM information_schema.tables WHERE table_name = 'nx_freight_audit_logs') THEN
        DROP POLICY IF EXISTS freight_audit_log_tenant_isolation ON nx_freight_audit_logs;
        CREATE POLICY freight_audit_log_tenant_isolation ON nx_freight_audit_logs
            USING (tenant_id = current_setting('app.current_tenant_id')::uuid);
    END IF;
END $$;

-- ============================================================
-- 3. MISSING COLUMNS
-- ============================================================

-- nx_inventory: add version
DO $$ BEGIN
    IF EXISTS (SELECT 1 FROM information_schema.tables WHERE table_name = 'nx_inventory')
       AND NOT EXISTS (SELECT 1 FROM information_schema.columns WHERE table_name = 'nx_inventory' AND column_name = 'version') THEN
        ALTER TABLE nx_inventory ADD COLUMN version bigint DEFAULT 0;
    END IF;
END $$;

-- nx_fulfillment_exceptions: add exception_type, message
DO $$ BEGIN
    IF EXISTS (SELECT 1 FROM information_schema.tables WHERE table_name = 'nx_fulfillment_exceptions') THEN
        IF NOT EXISTS (SELECT 1 FROM information_schema.columns WHERE table_name = 'nx_fulfillment_exceptions' AND column_name = 'exception_type') THEN
            ALTER TABLE nx_fulfillment_exceptions ADD COLUMN exception_type varchar(50);
        END IF;
        IF NOT EXISTS (SELECT 1 FROM information_schema.columns WHERE table_name = 'nx_fulfillment_exceptions' AND column_name = 'message') THEN
            ALTER TABLE nx_fulfillment_exceptions ADD COLUMN message text;
        END IF;
    END IF;
END $$;

-- nx_order_items: add image_url
DO $$ BEGIN
    IF EXISTS (SELECT 1 FROM information_schema.tables WHERE table_name = 'nx_order_items')
       AND NOT EXISTS (SELECT 1 FROM information_schema.columns WHERE table_name = 'nx_order_items' AND column_name = 'image_url') THEN
        ALTER TABLE nx_order_items ADD COLUMN image_url varchar(500);
    END IF;
END $$;

-- nx_orders: add customer_email, ship_to_address_id, billing_address_id
DO $$ BEGIN
    IF EXISTS (SELECT 1 FROM information_schema.tables WHERE table_name = 'nx_orders') THEN
        IF NOT EXISTS (SELECT 1 FROM information_schema.columns WHERE table_name = 'nx_orders' AND column_name = 'customer_email') THEN
            ALTER TABLE nx_orders ADD COLUMN customer_email varchar(255);
        END IF;
        IF NOT EXISTS (SELECT 1 FROM information_schema.columns WHERE table_name = 'nx_orders' AND column_name = 'ship_to_address_id') THEN
            ALTER TABLE nx_orders ADD COLUMN ship_to_address_id uuid;
        END IF;
        IF NOT EXISTS (SELECT 1 FROM information_schema.columns WHERE table_name = 'nx_orders' AND column_name = 'billing_address_id') THEN
            ALTER TABLE nx_orders ADD COLUMN billing_address_id uuid;
        END IF;
    END IF;
END $$;

-- nx_product_mappings: add image_url
DO $$ BEGIN
    IF EXISTS (SELECT 1 FROM information_schema.tables WHERE table_name = 'nx_product_mappings')
       AND NOT EXISTS (SELECT 1 FROM information_schema.columns WHERE table_name = 'nx_product_mappings' AND column_name = 'image_url') THEN
        ALTER TABLE nx_product_mappings ADD COLUMN image_url varchar(500);
    END IF;
END $$;

-- nx_return_items: add disposed_at, disposed_by, disposition_notes, original_price
DO $$ BEGIN
    IF EXISTS (SELECT 1 FROM information_schema.tables WHERE table_name = 'nx_return_items') THEN
        IF NOT EXISTS (SELECT 1 FROM information_schema.columns WHERE table_name = 'nx_return_items' AND column_name = 'disposed_at') THEN
            ALTER TABLE nx_return_items ADD COLUMN disposed_at timestamp;
        END IF;
        IF NOT EXISTS (SELECT 1 FROM information_schema.columns WHERE table_name = 'nx_return_items' AND column_name = 'disposed_by') THEN
            ALTER TABLE nx_return_items ADD COLUMN disposed_by uuid;
        END IF;
        IF NOT EXISTS (SELECT 1 FROM information_schema.columns WHERE table_name = 'nx_return_items' AND column_name = 'disposition_notes') THEN
            ALTER TABLE nx_return_items ADD COLUMN disposition_notes text;
        END IF;
        IF NOT EXISTS (SELECT 1 FROM information_schema.columns WHERE table_name = 'nx_return_items' AND column_name = 'original_price') THEN
            ALTER TABLE nx_return_items ADD COLUMN original_price numeric(12,2);
        END IF;
    END IF;
END $$;

-- nx_user_roles: add created_at
DO $$ BEGIN
    IF EXISTS (SELECT 1 FROM information_schema.tables WHERE table_name = 'nx_user_roles')
       AND NOT EXISTS (SELECT 1 FROM information_schema.columns WHERE table_name = 'nx_user_roles' AND column_name = 'created_at') THEN
        ALTER TABLE nx_user_roles ADD COLUMN created_at timestamp DEFAULT now();
    END IF;
END $$;

-- products: add image_url
DO $$ BEGIN
    IF EXISTS (SELECT 1 FROM information_schema.tables WHERE table_name = 'products')
       AND NOT EXISTS (SELECT 1 FROM information_schema.columns WHERE table_name = 'products' AND column_name = 'image_url') THEN
        ALTER TABLE products ADD COLUMN image_url varchar(500);
    END IF;
END $$;

-- ============================================================
-- 4. TYPE CONVERSIONS
-- ============================================================

-- ai_models.tags: text[] -> varchar(255) (entity maps as String)
DO $$ BEGIN
    IF EXISTS (SELECT 1 FROM information_schema.columns WHERE table_name = 'ai_models' AND column_name = 'tags' AND udt_name = '_text') THEN
        ALTER TABLE ai_models ALTER COLUMN tags TYPE varchar(255) USING array_to_string(tags, ',');
    END IF;
END $$;

-- ai_prompts.tags: text[] -> varchar(255) (entity maps as String)
DO $$ BEGIN
    IF EXISTS (SELECT 1 FROM information_schema.columns WHERE table_name = 'ai_prompts' AND column_name = 'tags' AND udt_name = '_text') THEN
        ALTER TABLE ai_prompts ALTER COLUMN tags TYPE varchar(255) USING array_to_string(tags, ',');
    END IF;
END $$;

-- nx_picklists.order_ids: uuid[] -> text
DO $$ BEGIN
    IF EXISTS (SELECT 1 FROM information_schema.columns WHERE table_name = 'nx_picklists' AND column_name = 'order_ids' AND udt_name = '_uuid') THEN
        ALTER TABLE nx_picklists ALTER COLUMN order_ids TYPE text USING array_to_string(order_ids, ',');
    END IF;
END $$;

-- nx_purchase_orders.created_by: uuid -> varchar(100)
DO $$ BEGIN
    IF EXISTS (SELECT 1 FROM information_schema.columns WHERE table_name = 'nx_purchase_orders' AND column_name = 'created_by' AND udt_name = 'uuid') THEN
        ALTER TABLE nx_purchase_orders ALTER COLUMN created_by TYPE varchar(100) USING created_by::text;
    END IF;
END $$;

-- nx_purchase_requests.requested_by: uuid -> varchar(100)
DO $$ BEGIN
    IF EXISTS (SELECT 1 FROM information_schema.columns WHERE table_name = 'nx_purchase_requests' AND column_name = 'requested_by' AND udt_name = 'uuid') THEN
        ALTER TABLE nx_purchase_requests ALTER COLUMN requested_by TYPE varchar(100) USING requested_by::text;
    END IF;
END $$;

-- nx_rfqs.created_by: uuid -> varchar(100)
DO $$ BEGIN
    IF EXISTS (SELECT 1 FROM information_schema.columns WHERE table_name = 'nx_rfqs' AND column_name = 'created_by' AND udt_name = 'uuid') THEN
        ALTER TABLE nx_rfqs ALTER COLUMN created_by TYPE varchar(100) USING created_by::text;
    END IF;
END $$;

-- nx_workflow_executions.current_step: int4 -> varchar(20)
DO $$ BEGIN
    IF EXISTS (SELECT 1 FROM information_schema.columns WHERE table_name = 'nx_workflow_executions' AND column_name = 'current_step' AND data_type = 'integer') THEN
        ALTER TABLE nx_workflow_executions ALTER COLUMN current_step TYPE varchar(20) USING current_step::text;
    END IF;
END $$;
