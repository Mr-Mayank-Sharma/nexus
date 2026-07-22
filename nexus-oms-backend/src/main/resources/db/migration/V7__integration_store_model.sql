-- OFBiz-style generic integration store model
-- A store represents a connected sales channel (BigCommerce, Shopify, etc.)

CREATE TABLE nx_integration_stores (
    id UUID PRIMARY KEY DEFAULT uuid_generate_v4(),
    tenant_id UUID NOT NULL,
    store_code VARCHAR(100) NOT NULL,
    store_name VARCHAR(255) NOT NULL,
    platform VARCHAR(50) NOT NULL,
    platform_type VARCHAR(50),
    status VARCHAR(20) NOT NULL DEFAULT 'ACTIVE',
    currency VARCHAR(3) DEFAULT 'USD',
    default_locale VARCHAR(10) DEFAULT 'en_US',
    timezone VARCHAR(50) DEFAULT 'UTC',
    external_store_id VARCHAR(255),
    external_domain VARCHAR(255),
    is_active BOOLEAN DEFAULT TRUE,
    config_json JSONB,
    metadata JSONB,
    created_at TIMESTAMP NOT NULL DEFAULT NOW(),
    updated_at TIMESTAMP NOT NULL DEFAULT NOW()
);

CREATE TABLE nx_integration_store_settings (
    id UUID PRIMARY KEY DEFAULT uuid_generate_v4(),
    store_id UUID NOT NULL REFERENCES nx_integration_stores(id) ON DELETE CASCADE,
    setting_type VARCHAR(100) NOT NULL,
    setting_value TEXT,
    description VARCHAR(255),
    is_encrypted BOOLEAN DEFAULT FALSE,
    created_at TIMESTAMP NOT NULL DEFAULT NOW(),
    updated_at TIMESTAMP NOT NULL DEFAULT NOW()
);

CREATE TABLE nx_integration_sync_configs (
    id UUID PRIMARY KEY DEFAULT uuid_generate_v4(),
    store_id UUID NOT NULL REFERENCES nx_integration_stores(id) ON DELETE CASCADE,
    sync_type VARCHAR(50) NOT NULL,
    enabled BOOLEAN DEFAULT TRUE,
    interval_minutes INT DEFAULT 15,
    last_sync_at TIMESTAMP,
    last_sync_status VARCHAR(20),
    last_sync_message TEXT,
    config_json JSONB,
    created_at TIMESTAMP NOT NULL DEFAULT NOW(),
    updated_at TIMESTAMP NOT NULL DEFAULT NOW()
);

CREATE TABLE nx_shopify_webhooks (
    id UUID PRIMARY KEY DEFAULT uuid_generate_v4(),
    tenant_id UUID NOT NULL,
    store_id UUID NOT NULL REFERENCES nx_integration_stores(id) ON DELETE CASCADE,
    shopify_webhook_id BIGINT NOT NULL,
    topic VARCHAR(255) NOT NULL,
    address VARCHAR(512) NOT NULL,
    is_active BOOLEAN DEFAULT TRUE,
    created_at TIMESTAMP NOT NULL DEFAULT NOW()
);

CREATE INDEX idx_int_stores_tenant ON nx_integration_stores(tenant_id, platform);
CREATE INDEX idx_int_stores_code ON nx_integration_stores(tenant_id, store_code);
CREATE INDEX idx_int_store_settings ON nx_integration_store_settings(store_id);
CREATE INDEX idx_int_sync_configs ON nx_integration_sync_configs(store_id);
CREATE INDEX idx_shopify_webhooks_store ON nx_shopify_webhooks(store_id);
