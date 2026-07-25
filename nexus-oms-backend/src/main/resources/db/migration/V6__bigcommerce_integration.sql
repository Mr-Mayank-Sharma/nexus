CREATE TABLE nx_bigcommerce_config (
    id UUID PRIMARY KEY DEFAULT uuid_generate_v4(),
    tenant_id UUID NOT NULL,
    store_hash VARCHAR(255) NOT NULL,
    access_token VARCHAR(512) NOT NULL,
    client_id VARCHAR(255),
    api_path VARCHAR(255) DEFAULT 'https://api.bigcommerce.com',
    is_active BOOLEAN DEFAULT TRUE,
    auto_sync_orders BOOLEAN DEFAULT FALSE,
    auto_sync_inventory BOOLEAN DEFAULT FALSE,
    sync_interval_minutes INT DEFAULT 15,
    last_order_sync_at TIMESTAMP,
    last_product_sync_at TIMESTAMP,
    last_inventory_sync_at TIMESTAMP,
    created_at TIMESTAMP NOT NULL DEFAULT NOW(),
    updated_at TIMESTAMP NOT NULL DEFAULT NOW()
);

CREATE TABLE nx_sync_logs (
    id UUID PRIMARY KEY DEFAULT uuid_generate_v4(),
    tenant_id UUID NOT NULL,
    integration_type VARCHAR(50) NOT NULL,
    sync_type VARCHAR(50) NOT NULL,
    status VARCHAR(20) NOT NULL,
    started_at TIMESTAMP NOT NULL DEFAULT NOW(),
    completed_at TIMESTAMP,
    items_processed INT DEFAULT 0,
    items_succeeded INT DEFAULT 0,
    items_failed INT DEFAULT 0,
    error_message TEXT,
    details JSONB,
    created_at TIMESTAMP NOT NULL DEFAULT NOW()
);

CREATE TABLE nx_bigcommerce_webhooks (
    id UUID PRIMARY KEY DEFAULT uuid_generate_v4(),
    tenant_id UUID NOT NULL,
    webhook_id INTEGER NOT NULL,
    scope VARCHAR(255) NOT NULL,
    destination VARCHAR(512) NOT NULL,
    is_active BOOLEAN DEFAULT TRUE,
    created_at TIMESTAMP NOT NULL DEFAULT NOW()
);

CREATE TABLE nx_product_mappings (
    id UUID PRIMARY KEY DEFAULT uuid_generate_v4(),
    tenant_id UUID NOT NULL,
    bc_product_id INTEGER NOT NULL,
    bc_variant_id INTEGER,
    bc_sku VARCHAR(100),
    nexus_sku VARCHAR(100) NOT NULL,
    nexus_product_name VARCHAR(255),
    last_synced_at TIMESTAMP,
    created_at TIMESTAMP NOT NULL DEFAULT NOW(),
    updated_at TIMESTAMP NOT NULL DEFAULT NOW()
);

CREATE INDEX idx_bc_config_tenant ON nx_bigcommerce_config(tenant_id);
CREATE INDEX idx_sync_logs_tenant ON nx_sync_logs(tenant_id, integration_type, created_at DESC);
CREATE INDEX idx_product_mappings_tenant ON nx_product_mappings(tenant_id);
CREATE INDEX idx_product_mappings_sku ON nx_product_mappings(bc_sku);
