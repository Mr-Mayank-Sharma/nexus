-- Row-Level Security for tenant isolation
-- This is a defense-in-depth layer: even if an application-level query
-- misses a tenant_id filter, the database will enforce isolation.

-- Helper function: extracts tenant_id from a session-level setting.
-- The Java backend sets this via SET app.current_tenant = '<uuid>' after JWT auth.
CREATE OR REPLACE FUNCTION app.current_tenant_id()
RETURNS UUID
LANGUAGE SQL STABLE
AS $$ SELECT NULLIF(current_setting('app.current_tenant', TRUE), '')::UUID $$;

-- Apply RLS to every tenant-scoped table.
-- The USING clause allows access only when the row's tenant_id matches
-- the session-level setting, or when no setting is present (system-level).
DO $$
DECLARE
    tbl TEXT;
BEGIN
    FOR tbl IN
        SELECT unnest(ARRAY[
            'nx_orders', 'nx_order_items', 'nx_customers',
            'nx_shipments', 'nx_tracking_events',
            'nx_inventory', 'nx_inventory_receipts',
            'nx_returns',
            'nx_picklists', 'nx_picklist_items',
            'nx_packages',
            'nx_invoices', 'nx_invoice_items', 'nx_payments', 'nx_credit_memos',
            'nx_carriers', 'nx_carrier_accounts', 'nx_carrier_rates', 'nx_carrier_zones',
            'nx_routing_rules', 'nx_routing_configs', 'nx_routing_logs',
            'nx_order_allocations',
            'nx_fulfillment_exceptions',
            'nx_rate_shopping_logs',
            'nx_purchase_orders', 'nx_purchase_order_items',
            'nx_purchase_requests', 'nx_purchase_request_items',
            'nx_suppliers', 'nx_supplier_contacts', 'nx_supplier_contracts',
            'nx_rfqs', 'nx_rfq_responses',
            'nx_workflows', 'nx_workflow_steps', 'nx_workflow_executions',
            'nx_notification_templates', 'nx_notification_logs', 'nx_alert_rules',
            'nx_documents', 'nx_document_versions',
            'nx_audit_log',
            'nx_cycle_counts',
            'nx_edi_documents', 'nx_edi_partners',
            'nx_email_parsed_orders',
            'nx_import_history', 'nx_import_record_logs',
            'nx_integration_stores', 'nx_integration_store_settings', 'nx_integration_sync_configs',
            'nx_integration_flows', 'nx_integration_flow_steps',
            'nx_integration_endpoints', 'nx_integration_messages',
            'nx_integration_cdc_events', 'nx_integration_audit_logs',
            'nx_integration_dlq',
            'nx_integration_import_jobs', 'nx_integration_export_jobs',
            'nx_integration_transform_mappings', 'nx_integration_validation_rules',
            'nx_bigcommerce_config', 'nx_bigcommerce_webhooks',
            'nx_shopify_webhooks', 'nx_sync_logs',
            'nx_product_mappings',
            'nx_role_permissions', 'nx_user_roles',
            'nx_teams',
            'nx_company_settings',
            'nx_addresses', 'nx_contacts',
            'warehouses', 'warehouse_zones', 'warehouse_bins', 'warehouse_staff', 'warehouse_equipment',
            'products',
            'nx_nodes',
            'nx_ai_models', 'nx_ai_model_versions', 'nx_ai_model_metrics',
            'nx_ai_deployments', 'nx_ai_experiments',
            'nx_ai_training_jobs', 'nx_ai_datasets',
            'nx_ai_feature_definitions', 'nx_ai_feature_values',
            'nx_ai_inference_logs', 'nx_ai_cost_logs',
            'nx_ai_gateway_routes', 'nx_ai_prompts',
            'nx_ai_knowledge_bases', 'nx_ai_knowledge_documents',
            'nx_ai_compute_resources', 'nx_ai_rule_fallbacks',
            'nx_users'
        ])
    LOOP
        EXECUTE format('ALTER TABLE %I ENABLE ROW LEVEL SECURITY', tbl);
        EXECUTE format(
            'DROP POLICY IF EXISTS tenant_isolation ON %I',
            tbl
        );
        EXECUTE format(
            'CREATE POLICY tenant_isolation ON %I
             USING (tenant_id = app.current_tenant_id() OR app.current_tenant_id() IS NULL)',
            tbl
        );
    END LOOP;
END;
$$;

-- The nx_users table has a nullable tenant_id; superuser/admin access bypasses RLS.
ALTER TABLE nx_users ENABLE ROW LEVEL SECURITY;
DROP POLICY IF EXISTS tenant_isolation ON nx_users;
CREATE POLICY tenant_isolation ON nx_users
    USING (tenant_id = app.current_tenant_id() OR app.current_tenant_id() IS NULL);

-- Note: Tables without a tenant_id column (e.g. flyway_schema_history) are
-- excluded. System-level tables remain accessible to all authenticated users.
