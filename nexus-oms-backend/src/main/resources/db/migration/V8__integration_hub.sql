-- Integration Hub tables
-- Extends the OFBiz-style store model with connector instances, credentials, mappings, EDI, webhooks

-- Connector instances (one per store/platform connection)
CREATE TABLE IF NOT EXISTS nx_connector_instances (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    tenant_id UUID NOT NULL,
    store_id UUID REFERENCES nx_integration_stores(id),
    platform_type VARCHAR(50) NOT NULL,
    connector_id VARCHAR(100) NOT NULL,
    connector_name VARCHAR(255),
    api_version VARCHAR(50) DEFAULT 'v1',
    environment VARCHAR(20) DEFAULT 'production',
    base_url VARCHAR(512),
    status VARCHAR(20) DEFAULT 'INITIALIZED',
    is_active BOOLEAN DEFAULT true,
    max_retries INT DEFAULT 3,
    timeout_seconds INT DEFAULT 30,
    batch_size INT DEFAULT 100,
    webhooks_enabled BOOLEAN DEFAULT true,
    auto_sync_enabled BOOLEAN DEFAULT false,
    sync_interval_minutes INT DEFAULT 15,
    config_json JSONB,
    metadata JSONB,
    last_sync_at TIMESTAMP,
    last_health_check_at TIMESTAMP,
    created_at TIMESTAMP NOT NULL DEFAULT now(),
    updated_at TIMESTAMP NOT NULL DEFAULT now(),
    UNIQUE(tenant_id, connector_id)
);

-- Encrypted credentials (AES-256-GCM)
CREATE TABLE IF NOT EXISTS nx_connector_credentials (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    connector_instance_id UUID NOT NULL REFERENCES nx_connector_instances(id) ON DELETE CASCADE,
    credential_key VARCHAR(100) NOT NULL,
    credential_value TEXT NOT NULL,
    is_encrypted BOOLEAN DEFAULT true,
    expires_at TIMESTAMP,
    created_at TIMESTAMP NOT NULL DEFAULT now(),
    updated_at TIMESTAMP NOT NULL DEFAULT now(),
    UNIQUE(connector_instance_id, credential_key)
);

-- Health check records
CREATE TABLE IF NOT EXISTS nx_connector_health (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    connector_instance_id UUID NOT NULL REFERENCES nx_connector_instances(id) ON DELETE CASCADE,
    status VARCHAR(20) NOT NULL DEFAULT 'UNKNOWN',
    message TEXT,
    latency_ms BIGINT,
    consecutive_failures INT DEFAULT 0,
    is_webhooks_registered BOOLEAN DEFAULT false,
    last_success_at TIMESTAMP,
    last_error_at TIMESTAMP,
    checked_at TIMESTAMP NOT NULL DEFAULT now()
);

-- Data field mappings (external ↔ OMS fields)
CREATE TABLE IF NOT EXISTS nx_data_mappings (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    tenant_id UUID NOT NULL,
    connector_instance_id UUID REFERENCES nx_connector_instances(id) ON DELETE CASCADE,
    mapping_name VARCHAR(100) NOT NULL,
    direction VARCHAR(20) NOT NULL DEFAULT 'BOTH',
    source_field VARCHAR(255) NOT NULL,
    target_field VARCHAR(255) NOT NULL,
    transformation_type VARCHAR(50),
    transformation_rule TEXT,
    default_value TEXT,
    is_required BOOLEAN DEFAULT false,
    is_active BOOLEAN DEFAULT true,
    created_at TIMESTAMP NOT NULL DEFAULT now(),
    updated_at TIMESTAMP NOT NULL DEFAULT now()
);

-- Batch job tracking
CREATE TABLE IF NOT EXISTS nx_batch_jobs (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    tenant_id UUID NOT NULL,
    connector_instance_id UUID REFERENCES nx_connector_instances(id),
    job_type VARCHAR(50) NOT NULL,
    sync_type VARCHAR(50),
    status VARCHAR(20) NOT NULL DEFAULT 'PENDING',
    total_items INT DEFAULT 0,
    items_succeeded INT DEFAULT 0,
    items_failed INT DEFAULT 0,
    items_skipped INT DEFAULT 0,
    error_message TEXT,
    params JSONB,
    result JSONB,
    started_at TIMESTAMP NOT NULL DEFAULT now(),
    completed_at TIMESTAMP,
    duration_ms BIGINT,
    created_at TIMESTAMP NOT NULL DEFAULT now()
);

CREATE INDEX idx_batch_jobs_tenant ON nx_batch_jobs(tenant_id);
CREATE INDEX idx_batch_jobs_connector ON nx_batch_jobs(connector_instance_id);
CREATE INDEX idx_batch_jobs_status ON nx_batch_jobs(status);

-- EDI document tracking (X12 / EDIFACT)
CREATE TABLE IF NOT EXISTS nx_edi_documents (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    tenant_id UUID NOT NULL,
    connector_instance_id UUID REFERENCES nx_connector_instances(id),
    direction VARCHAR(10) NOT NULL DEFAULT 'INBOUND',
    standard VARCHAR(10) NOT NULL DEFAULT 'X12',
    document_type VARCHAR(10) NOT NULL,
    version VARCHAR(10) DEFAULT '004010',
    sender_id VARCHAR(100),
    receiver_id VARCHAR(100),
    control_number VARCHAR(50),
    raw_content TEXT,
    parsed_json JSONB,
    status VARCHAR(20) DEFAULT 'RECEIVED',
    processing_notes TEXT,
    order_id UUID,
    received_at TIMESTAMP NOT NULL DEFAULT now(),
    processed_at TIMESTAMP,
    created_at TIMESTAMP NOT NULL DEFAULT now()
);

CREATE INDEX idx_edi_documents_tenant ON nx_edi_documents(tenant_id);
CREATE INDEX idx_edi_documents_type ON nx_edi_documents(document_type);
CREATE INDEX idx_edi_documents_status ON nx_edi_documents(status);

-- Webhook endpoint tracking
CREATE TABLE IF NOT EXISTS nx_webhook_endpoints (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    tenant_id UUID NOT NULL,
    connector_instance_id UUID REFERENCES nx_connector_instances(id) ON DELETE CASCADE,
    platform VARCHAR(50) NOT NULL,
    topic VARCHAR(100) NOT NULL,
    callback_url VARCHAR(512) NOT NULL,
    is_registered BOOLEAN DEFAULT false,
    external_id VARCHAR(255),
    last_triggered_at TIMESTAMP,
    last_response_code INT,
    registered_at TIMESTAMP,
    created_at TIMESTAMP NOT NULL DEFAULT now(),
    updated_at TIMESTAMP NOT NULL DEFAULT now(),
    UNIQUE(connector_instance_id, topic)
);
