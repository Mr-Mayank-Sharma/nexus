-- V26: Row-Level Security enforcement for multi-tenant isolation
-- Supersedes V23 with corrected table names and FORCE ROW LEVEL SECURITY.
--
-- Every table that carries a tenant_id column gets a policy that
-- restricts visibility to rows matching the session variable
--   app.current_tenant_id
-- set by the Java backend after JWT authentication.
--
-- Tables WITHOUT tenant_id (child/detail tables joined via FK to a
-- tenant-scoped parent) are deliberately excluded; their isolation
-- is inherited through the parent's RLS policy.
--
-- The entire block is idempotent: re-running it is safe.

DO $$
DECLARE
    tbl TEXT;
    tenant_tables TEXT[] := ARRAY[
        -- ── core OMS ──────────────────────────────────────────────
        'nx_orders',
        'nx_order_allocations',
        'nx_customers',
        'nx_users',
        'nx_shipments',
        'nx_inventory',
        'nx_inventory_receipts',
        'nx_nodes',
        'nx_returns',
        'nx_return_items',
        'nx_picklists',
        'nx_picklist_items',
        'nx_packages',
        'nx_fulfillment_exceptions',
        'nx_cycle_counts',

        -- ── finance ───────────────────────────────────────────────
        'nx_invoices',
        'nx_payments',
        'nx_credit_memos',

        -- ── carriers / routing ────────────────────────────────────
        'nx_carriers',
        'nx_carrier_accounts',
        'nx_carrier_rates',
        'nx_carrier_zones',
        'nx_routing_rules',
        'nx_routing_configs',
        'nx_routing_log',
        'nx_rate_shopping_logs',

        -- ── EDI / email ingestion ─────────────────────────────────
        'nx_edi_documents',
        'nx_edi_partners',
        'nx_email_parsed_orders',
        'nx_email_ingestion_config',

        -- ── suppliers / procurement ───────────────────────────────
        'nx_suppliers',
        'nx_supplier_contracts',
        'nx_purchase_orders',
        'nx_purchase_requests',
        'nx_rfqs',

        -- ── products / mappings ───────────────────────────────────
        'products',
        'nx_product_mappings',

        -- ── warehouses ────────────────────────────────────────────
        'nx_warehouses',
        'warehouse_zones',
        'warehouse_bins',
        'nx_warehouse_staff',
        'nx_warehouse_equipment',

        -- ── documents / contacts / addresses ──────────────────────
        'nx_documents',
        'nx_contacts',
        'nx_addresses',

        -- ── workflows ─────────────────────────────────────────────
        'nx_workflows',
        'nx_workflow_executions',

        -- ── notifications / alerts ────────────────────────────────
        'nx_notification_templates',
        'nx_notification_logs',
        'nx_alert_rules',

        -- ── audit / sync / import ─────────────────────────────────
        'nx_audit_log',
        'nx_sync_logs',
        'import_history',
        'import_record_log',

        -- ── integration hub ───────────────────────────────────────
        'nx_integration_stores',
        'nx_integration_flows',
        'nx_integration_endpoints',
        'nx_integration_messages',
        'nx_integration_cdc_events',
        'nx_integration_audit_log',
        'nx_integration_dlq',
        'nx_integration_import_jobs',
        'nx_integration_export_jobs',
        'nx_integration_validation_rules',

        -- ── platform connectors ───────────────────────────────────
        'nx_bigcommerce_config',
        'nx_bigcommerce_webhooks',
        'nx_shopify_webhooks',

        -- ── roles / teams / settings ──────────────────────────────
        'nx_role_permissions',
        'nx_user_roles',
        'nx_teams',
        'nx_company_settings',

        -- ── AI / ML platform ──────────────────────────────────────
        'ai_models',
        'ai_model_metrics',
        'ai_deployments',
        'ai_experiments',
        'ai_training_jobs',
        'ai_datasets',
        'ai_feature_definitions',
        'ai_feature_values',
        'ai_inference_logs',
        'ai_cost_logs',
        'ai_knowledge_bases',
        'ai_knowledge_documents',
        'ai_prompts',
        'ai_compute_resources',
        'ai_gateway_routes',
        'ai_rule_fallbacks'
    ];
BEGIN
    FOREACH tbl IN ARRAY tenant_tables LOOP
        -- Enable RLS (no-op if already enabled)
        EXECUTE format('ALTER TABLE %I ENABLE ROW LEVEL SECURITY', tbl);

        -- Drop any previous tenant_isolation policy so we always
        -- re-create with the definitive definition.
        EXECUTE format('DROP POLICY IF EXISTS tenant_isolation ON %I', tbl);

        -- Create the isolation policy.
        -- Access is allowed when:
        --   (a) the row's tenant_id matches the session variable, OR
        --   (b) the session variable is empty (system / migration context).
        --
        -- nullif(..., '') converts the empty string to NULL so the ::uuid
        -- cast never receives an invalid literal.  When the setting is
        -- absent, the IS NULL branch grants full access; when it is set,
        -- the equality check enforces tenant isolation.
        EXECUTE format(
            'CREATE POLICY tenant_isolation ON %I
             USING (
                 nullif(current_setting(''app.current_tenant_id'', true), '''')::uuid IS NULL
                 OR tenant_id = nullif(current_setting(''app.current_tenant_id'', true), '''')::uuid
             )',
            tbl
        );

        -- FORCE RLS even for table owners / superuser.
        EXECUTE format('ALTER TABLE %I FORCE ROW LEVEL SECURITY', tbl);
    END LOOP;

    RAISE NOTICE 'V26: RLS policies applied to % tenant-scoped tables',
                 array_length(tenant_tables, 1);
END;
$$;

-- ──────────────────────────────────────────────────────────────────
-- Tables WITHOUT a tenant_id column are intentionally excluded.
-- They are child/detail tables whose rows are accessed only through
-- joins to a tenant-scoped parent that already has RLS enforced:
--
--   nx_order_items        → via nx_orders
--   nx_tracking_events    → via nx_shipments
--   nx_workflow_steps     → via nx_workflows
--   nx_document_versions  → via nx_documents
--   nx_invoice_items      → via nx_invoices
--   nx_rfq_responses      → via nx_rfqs
--   nx_purchase_order_items → via nx_purchase_orders
--   nx_purchase_request_items → via nx_purchase_requests
--   nx_supplier_contacts  → via nx_suppliers
--   nx_integration_flow_steps → via nx_integration_flows
--   nx_integration_store_settings → via nx_integration_stores
--   nx_integration_sync_configs → via nx_integration_stores
--   ai_model_versions     → via ai_models
--
-- If application code ever queries these tables independently (without
-- joining to the parent), the code must add an explicit WHERE clause
-- filtering by the parent's tenant_id.  RLS cannot protect a column-
-- less table without introducing a sub-query policy, which would
-- penalise every query.
-- ──────────────────────────────────────────────────────────────────
