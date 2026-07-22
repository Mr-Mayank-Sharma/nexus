-- ============================================================
-- Enterprise Integration Platform
-- Integration Hub (MuleSoft/Boomi-style), CDC, DLQ, Audit
-- ============================================================

-- 1. INTEGRATION ENDPOINTS
CREATE TABLE IF NOT EXISTS nx_integration_endpoints (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    tenant_id UUID NOT NULL,
    name VARCHAR(255) NOT NULL,
    description TEXT,
    endpoint_type VARCHAR(50) NOT NULL,
    protocol VARCHAR(30) NOT NULL,
    host VARCHAR(255),
    port INT,
    path VARCHAR(500),
    method VARCHAR(10),
    headers JSONB,
    query_params JSONB,
    auth_type VARCHAR(50),
    auth_config JSONB,
    ssl_enabled BOOLEAN DEFAULT false,
    timeout_ms INT DEFAULT 30000,
    retry_count INT DEFAULT 3,
    retry_delay_ms INT DEFAULT 1000,
    circuit_breaker_enabled BOOLEAN DEFAULT true,
    circuit_breaker_threshold INT DEFAULT 5,
    circuit_breaker_timeout_ms INT DEFAULT 30000,
    rate_limit_enabled BOOLEAN DEFAULT false,
    rate_limit_max INT DEFAULT 100,
    rate_limit_window_ms INT DEFAULT 1000,
    is_active BOOLEAN DEFAULT true,
    metadata JSONB,
    created_at TIMESTAMP NOT NULL DEFAULT now(),
    updated_at TIMESTAMP NOT NULL DEFAULT now(),
    UNIQUE(tenant_id, name)
);

-- 2. INTEGRATION FLOWS
CREATE TABLE IF NOT EXISTS nx_integration_flows (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    tenant_id UUID NOT NULL,
    name VARCHAR(255) NOT NULL,
    description TEXT,
    flow_type VARCHAR(30) NOT NULL,
    status VARCHAR(20) DEFAULT 'DRAFT',
    source_endpoint_id UUID REFERENCES nx_integration_endpoints(id),
    target_endpoint_id UUID REFERENCES nx_integration_endpoints(id),
    trigger_type VARCHAR(30) NOT NULL,
    trigger_config JSONB,
    schedule_cron VARCHAR(100),
    priority INT DEFAULT 5,
    max_retries INT DEFAULT 3,
    retry_delay_seconds INT DEFAULT 60,
    batch_size INT DEFAULT 1000,
    throttle_rate INT DEFAULT 0,
    processing_timeout_minutes INT DEFAULT 60,
    error_handling VARCHAR(30) DEFAULT 'STOP',
    is_active BOOLEAN DEFAULT false,
    metadata JSONB,
    created_at TIMESTAMP NOT NULL DEFAULT now(),
    updated_at TIMESTAMP NOT NULL DEFAULT now(),
    UNIQUE(tenant_id, name)
);

-- 3. INTEGRATION FLOW STEPS
CREATE TABLE IF NOT EXISTS nx_integration_flow_steps (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    flow_id UUID NOT NULL REFERENCES nx_integration_flows(id) ON DELETE CASCADE,
    step_order INT NOT NULL,
    step_type VARCHAR(50) NOT NULL,
    transformer_type VARCHAR(50),
    config JSONB NOT NULL,
    condition_expression TEXT,
    on_error VARCHAR(20) DEFAULT 'STOP',
    timeout_seconds INT DEFAULT 300,
    created_at TIMESTAMP NOT NULL DEFAULT now()
);

-- 4. INTEGRATION MESSAGES
CREATE TABLE IF NOT EXISTS nx_integration_messages (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    tenant_id UUID NOT NULL,
    flow_id UUID REFERENCES nx_integration_flows(id),
    message_id VARCHAR(255) NOT NULL,
    correlation_id VARCHAR(255),
    source VARCHAR(100),
    message_type VARCHAR(50) NOT NULL,
    format VARCHAR(30),
    payload TEXT,
    payload_size BIGINT,
    headers JSONB,
    status VARCHAR(20) NOT NULL DEFAULT 'PENDING',
    error_message TEXT,
    error_code VARCHAR(100),
    retry_count INT DEFAULT 0,
    max_retries INT DEFAULT 3,
    processed_at TIMESTAMP,
    created_at TIMESTAMP NOT NULL DEFAULT now(),
    UNIQUE(tenant_id, message_id)
);

-- 5. DEAD LETTER QUEUE
CREATE TABLE IF NOT EXISTS nx_integration_dlq (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    tenant_id UUID NOT NULL,
    flow_id UUID REFERENCES nx_integration_flows(id),
    endpoint_id UUID REFERENCES nx_integration_endpoints(id),
    message_id VARCHAR(255),
    original_payload TEXT,
    error_message TEXT,
    error_stacktrace TEXT,
    error_category VARCHAR(50),
    retry_count INT DEFAULT 0,
    max_retries INT DEFAULT 3,
    last_retry_at TIMESTAMP,
    status VARCHAR(20) DEFAULT 'FAILED',
    created_at TIMESTAMP NOT NULL DEFAULT now(),
    updated_at TIMESTAMP NOT NULL DEFAULT now()
);

-- 6. TRANSFORM MAPPINGS
CREATE TABLE IF NOT EXISTS nx_integration_transform_mappings (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    tenant_id UUID NOT NULL,
    name VARCHAR(255) NOT NULL,
    source_format VARCHAR(30) NOT NULL,
    target_format VARCHAR(30) NOT NULL,
    mapping_definition JSONB NOT NULL,
    version INT DEFAULT 1,
    is_active BOOLEAN DEFAULT true,
    created_at TIMESTAMP NOT NULL DEFAULT now(),
    updated_at TIMESTAMP NOT NULL DEFAULT now(),
    UNIQUE(tenant_id, name)
);

-- 7. VALIDATION RULES
CREATE TABLE IF NOT EXISTS nx_integration_validation_rules (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    tenant_id UUID NOT NULL,
    name VARCHAR(255) NOT NULL,
    rule_type VARCHAR(50) NOT NULL,
    entity_type VARCHAR(100) NOT NULL,
    field_path VARCHAR(255),
    operator VARCHAR(30),
    value TEXT,
    error_message VARCHAR(500),
    severity VARCHAR(20) DEFAULT 'ERROR',
    is_active BOOLEAN DEFAULT true,
    created_at TIMESTAMP NOT NULL DEFAULT now(),
    updated_at TIMESTAMP NOT NULL DEFAULT now()
);

-- 8. IMPORT JOBS
CREATE TABLE IF NOT EXISTS nx_integration_import_jobs (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    tenant_id UUID NOT NULL,
    flow_id UUID REFERENCES nx_integration_flows(id),
    job_name VARCHAR(255) NOT NULL,
    source_type VARCHAR(50) NOT NULL,
    source_config JSONB,
    target_type VARCHAR(50) NOT NULL,
    target_config JSONB,
    file_name VARCHAR(255),
    file_size BIGINT,
    file_hash VARCHAR(64),
    record_count INT DEFAULT 0,
    success_count INT DEFAULT 0,
    error_count INT DEFAULT 0,
    status VARCHAR(30) NOT NULL DEFAULT 'PENDING',
    error_summary TEXT,
    processing_time_ms BIGINT,
    started_at TIMESTAMP,
    completed_at TIMESTAMP,
    created_by UUID,
    created_at TIMESTAMP NOT NULL DEFAULT now(),
    updated_at TIMESTAMP NOT NULL DEFAULT now()
);

-- 9. EXPORT JOBS
CREATE TABLE IF NOT EXISTS nx_integration_export_jobs (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    tenant_id UUID NOT NULL,
    flow_id UUID REFERENCES nx_integration_flows(id),
    job_name VARCHAR(255) NOT NULL,
    export_type VARCHAR(50) NOT NULL,
    export_config JSONB,
    query_criteria JSONB,
    entity_type VARCHAR(100) NOT NULL,
    format VARCHAR(30) NOT NULL,
    compression VARCHAR(20),
    encryption VARCHAR(30),
    file_name VARCHAR(255),
    file_size BIGINT,
    record_count INT DEFAULT 0,
    status VARCHAR(30) NOT NULL DEFAULT 'PENDING',
    error_message TEXT,
    processing_time_ms BIGINT,
    started_at TIMESTAMP,
    completed_at TIMESTAMP,
    created_by UUID,
    created_at TIMESTAMP NOT NULL DEFAULT now(),
    updated_at TIMESTAMP NOT NULL DEFAULT now()
);

-- 10. CHANGE DATA CAPTURE EVENTS
CREATE TABLE IF NOT EXISTS nx_integration_cdc_events (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    tenant_id UUID NOT NULL,
    source VARCHAR(100) NOT NULL,
    entity_type VARCHAR(100) NOT NULL,
    entity_id UUID NOT NULL,
    event_type VARCHAR(30) NOT NULL,
    before_snapshot JSONB,
    after_snapshot JSONB,
    change_summary JSONB,
    transaction_id VARCHAR(255),
    sequence BIGINT,
    processed BOOLEAN DEFAULT false,
    processed_at TIMESTAMP,
    created_at TIMESTAMP NOT NULL DEFAULT now()
);

-- 11. INTEGRATION AUDIT LOG
CREATE TABLE IF NOT EXISTS nx_integration_audit_log (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    tenant_id UUID NOT NULL,
    flow_id UUID REFERENCES nx_integration_flows(id),
    message_id UUID REFERENCES nx_integration_messages(id),
    entity_type VARCHAR(100),
    entity_id VARCHAR(255),
    action VARCHAR(50) NOT NULL,
    status VARCHAR(20) NOT NULL,
    request_payload TEXT,
    response_payload TEXT,
    source_system VARCHAR(255),
    target_system VARCHAR(255),
    processing_time_ms BIGINT,
    error_message TEXT,
    ip_address VARCHAR(45),
    user_agent VARCHAR(500),
    created_by UUID,
    created_at TIMESTAMP NOT NULL DEFAULT now()
);

-- INDEXES
CREATE INDEX idx_int_endpoints_tenant ON nx_integration_endpoints(tenant_id);
CREATE INDEX idx_int_endpoints_type ON nx_integration_endpoints(endpoint_type);
CREATE INDEX idx_int_endpoints_active ON nx_integration_endpoints(tenant_id, is_active);

CREATE INDEX idx_int_flows_tenant ON nx_integration_flows(tenant_id);
CREATE INDEX idx_int_flows_status ON nx_integration_flows(status);
CREATE INDEX idx_int_flows_type ON nx_integration_flows(flow_type);
CREATE INDEX idx_int_flows_source ON nx_integration_flows(source_endpoint_id);
CREATE INDEX idx_int_flows_target ON nx_integration_flows(target_endpoint_id);
CREATE INDEX idx_int_flows_tenant_active ON nx_integration_flows(tenant_id, is_active);

CREATE INDEX idx_int_flow_steps_flow ON nx_integration_flow_steps(flow_id);
CREATE INDEX idx_int_flow_steps_order ON nx_integration_flow_steps(flow_id, step_order);

CREATE INDEX idx_int_messages_tenant ON nx_integration_messages(tenant_id);
CREATE INDEX idx_int_messages_flow ON nx_integration_messages(flow_id);
CREATE INDEX idx_int_messages_status ON nx_integration_messages(status);
CREATE INDEX idx_int_messages_correlation ON nx_integration_messages(correlation_id);
CREATE INDEX idx_int_messages_tenant_status ON nx_integration_messages(tenant_id, status);
CREATE INDEX idx_int_messages_created ON nx_integration_messages(tenant_id, created_at);

CREATE INDEX idx_int_dlq_tenant ON nx_integration_dlq(tenant_id);
CREATE INDEX idx_int_dlq_flow ON nx_integration_dlq(flow_id);
CREATE INDEX idx_int_dlq_status ON nx_integration_dlq(status);
CREATE INDEX idx_int_dlq_category ON nx_integration_dlq(error_category);
CREATE INDEX idx_int_dlq_tenant_status ON nx_integration_dlq(tenant_id, status);

CREATE INDEX idx_int_mappings_tenant ON nx_integration_transform_mappings(tenant_id);
CREATE INDEX idx_int_mappings_formats ON nx_integration_transform_mappings(source_format, target_format);

CREATE INDEX idx_int_validation_tenant ON nx_integration_validation_rules(tenant_id);
CREATE INDEX idx_int_validation_entity ON nx_integration_validation_rules(entity_type);
CREATE INDEX idx_int_validation_rule_type ON nx_integration_validation_rules(rule_type);

CREATE INDEX idx_int_import_tenant ON nx_integration_import_jobs(tenant_id);
CREATE INDEX idx_int_import_flow ON nx_integration_import_jobs(flow_id);
CREATE INDEX idx_int_import_status ON nx_integration_import_jobs(status);
CREATE INDEX idx_int_import_tenant_status ON nx_integration_import_jobs(tenant_id, status);

CREATE INDEX idx_int_export_tenant ON nx_integration_export_jobs(tenant_id);
CREATE INDEX idx_int_export_flow ON nx_integration_export_jobs(flow_id);
CREATE INDEX idx_int_export_status ON nx_integration_export_jobs(status);
CREATE INDEX idx_int_export_tenant_status ON nx_integration_export_jobs(tenant_id, status);

CREATE INDEX idx_int_cdc_tenant ON nx_integration_cdc_events(tenant_id);
CREATE INDEX idx_int_cdc_source ON nx_integration_cdc_events(tenant_id, source, entity_type, processed);
CREATE INDEX idx_int_cdc_entity ON nx_integration_cdc_events(entity_type, entity_id);
CREATE INDEX idx_int_cdc_processed ON nx_integration_cdc_events(processed);
CREATE INDEX idx_int_cdc_transaction ON nx_integration_cdc_events(transaction_id);

CREATE INDEX idx_int_audit_tenant ON nx_integration_audit_log(tenant_id);
CREATE INDEX idx_int_audit_flow ON nx_integration_audit_log(flow_id);
CREATE INDEX idx_int_audit_entity ON nx_integration_audit_log(entity_type, entity_id);
CREATE INDEX idx_int_audit_action ON nx_integration_audit_log(action);
CREATE INDEX idx_int_audit_tenant_lookup ON nx_integration_audit_log(tenant_id, flow_id, entity_type, action, created_at);
CREATE INDEX idx_int_audit_created ON nx_integration_audit_log(created_at);
