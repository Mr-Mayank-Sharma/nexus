-- WES: waves, wave rules, automation systems/commands/alerts/logs, slotting, equipment.
-- These tables previously existed only via ddl-auto=update; this migration makes prod validate safe.

CREATE TABLE IF NOT EXISTS nx_waves (
    id UUID PRIMARY KEY,
    tenant_id UUID NOT NULL,
    name VARCHAR(255) NOT NULL,
    warehouse_id UUID NOT NULL,
    status VARCHAR(50) NOT NULL,
    priority VARCHAR(50) NOT NULL,
    wave_type VARCHAR(50) NOT NULL,
    order_count INTEGER,
    total_line_items INTEGER,
    released_line_items INTEGER,
    completed_line_items INTEGER,
    zone_filter VARCHAR(255),
    target_completion_at TIMESTAMP,
    released_at TIMESTAMP,
    completed_at TIMESTAMP,
    released_by VARCHAR(255),
    completed_by VARCHAR(255),
    optimization_score INTEGER,
    notes TEXT,
    metadata JSONB,
    created_at TIMESTAMP NOT NULL,
    updated_at TIMESTAMP NOT NULL
);

CREATE INDEX IF NOT EXISTS idx_waves_tenant_status ON nx_waves (tenant_id, status);

CREATE TABLE IF NOT EXISTS nx_wave_rules (
    id UUID PRIMARY KEY,
    tenant_id UUID NOT NULL,
    wave_id UUID NOT NULL,
    rule_type VARCHAR(100) NOT NULL,
    operator VARCHAR(50) NOT NULL,
    value VARCHAR(255) NOT NULL,
    sequence INTEGER NOT NULL,
    is_active BOOLEAN NOT NULL,
    created_at TIMESTAMP NOT NULL,
    updated_at TIMESTAMP NOT NULL
);

CREATE INDEX IF NOT EXISTS idx_wave_rules_wave ON nx_wave_rules (wave_id);

CREATE TABLE IF NOT EXISTS nx_automation_systems (
    id UUID PRIMARY KEY,
    tenant_id UUID NOT NULL,
    warehouse_id UUID NOT NULL,
    system_name VARCHAR(255) NOT NULL,
    system_type VARCHAR(100) NOT NULL,
    vendor VARCHAR(255),
    model VARCHAR(255),
    protocol VARCHAR(100),
    endpoint_url VARCHAR(500),
    api_key TEXT,
    status VARCHAR(50) NOT NULL,
    health_check_url VARCHAR(500),
    last_health_check_at TIMESTAMP,
    health_check_interval_sec INTEGER,
    capabilities JSONB,
    connection_config JSONB,
    is_active BOOLEAN NOT NULL,
    last_connected_at TIMESTAMP,
    error_message TEXT,
    metadata JSONB,
    created_at TIMESTAMP NOT NULL,
    updated_at TIMESTAMP NOT NULL
);

CREATE INDEX IF NOT EXISTS idx_automation_systems_tenant ON nx_automation_systems (tenant_id, system_type);

CREATE TABLE IF NOT EXISTS nx_automation_commands (
    id UUID PRIMARY KEY,
    tenant_id UUID NOT NULL,
    system_id UUID NOT NULL,
    command_type VARCHAR(100) NOT NULL,
    status VARCHAR(50) NOT NULL,
    parameters JSONB,
    result JSONB,
    priority INTEGER NOT NULL,
    timeout_ms INTEGER,
    retry_count INTEGER,
    max_retries INTEGER,
    assigned_by VARCHAR(255),
    order_id UUID,
    picklist_id UUID,
    wave_id UUID,
    error_message TEXT,
    sent_at TIMESTAMP,
    acknowledged_at TIMESTAMP,
    completed_at TIMESTAMP,
    execution_time_ms BIGINT,
    metadata JSONB,
    created_at TIMESTAMP NOT NULL,
    updated_at TIMESTAMP NOT NULL
);

CREATE INDEX IF NOT EXISTS idx_automation_commands_system_status ON nx_automation_commands (system_id, status);

CREATE TABLE IF NOT EXISTS nx_automation_alerts (
    id UUID PRIMARY KEY,
    tenant_id UUID NOT NULL,
    system_id UUID NOT NULL,
    alert_type VARCHAR(100) NOT NULL,
    severity VARCHAR(50) NOT NULL,
    status VARCHAR(50) NOT NULL,
    title VARCHAR(255) NOT NULL,
    description TEXT,
    threshold_value DOUBLE PRECISION,
    current_value DOUBLE PRECISION,
    unit VARCHAR(50),
    acknowledged_by VARCHAR(255),
    acknowledged_at TIMESTAMP,
    resolved_at TIMESTAMP,
    resolution_notes TEXT,
    auto_resolve BOOLEAN,
    metadata JSONB,
    created_at TIMESTAMP NOT NULL,
    updated_at TIMESTAMP NOT NULL
);

CREATE INDEX IF NOT EXISTS idx_automation_alerts_system_status ON nx_automation_alerts (system_id, status);

CREATE TABLE IF NOT EXISTS nx_automation_logs (
    id UUID PRIMARY KEY,
    tenant_id UUID NOT NULL,
    system_id UUID NOT NULL,
    command_id UUID,
    log_level VARCHAR(50) NOT NULL,
    event VARCHAR(255) NOT NULL,
    message TEXT,
    data JSONB,
    duration_ms BIGINT,
    created_at TIMESTAMP NOT NULL
);

CREATE INDEX IF NOT EXISTS idx_automation_logs_system_time ON nx_automation_logs (system_id, created_at);

CREATE TABLE IF NOT EXISTS nx_slotting_rules (
    id UUID PRIMARY KEY,
    tenant_id UUID,
    warehouse_id UUID NOT NULL,
    rule_name VARCHAR(255) NOT NULL,
    rule_type VARCHAR(100) NOT NULL,
    criteria JSONB,
    target_zone_id UUID,
    target_bin_class VARCHAR(100),
    priority INTEGER NOT NULL,
    is_active BOOLEAN,
    effectiveness DOUBLE PRECISION NOT NULL,
    last_applied_at TIMESTAMP,
    apply_count INTEGER,
    notes TEXT,
    metadata JSONB,
    created_at TIMESTAMP NOT NULL,
    updated_at TIMESTAMP NOT NULL
);

CREATE INDEX IF NOT EXISTS idx_slotting_rules_warehouse ON nx_slotting_rules (warehouse_id, rule_type);

CREATE TABLE IF NOT EXISTS nx_slotting_assignments (
    id UUID PRIMARY KEY,
    tenant_id UUID,
    warehouse_id UUID NOT NULL,
    sku VARCHAR(120) NOT NULL,
    product_name VARCHAR(255),
    bin_id UUID NOT NULL,
    zone_id UUID,
    assigned_quantity INTEGER,
    velocity_class VARCHAR(50),
    last_picked_at TIMESTAMP,
    pick_frequency INTEGER,
    last_slotting_at TIMESTAMP,
    slotting_score DOUBLE PRECISION,
    assigned_by VARCHAR(255),
    rule_id UUID,
    notes TEXT,
    metadata JSONB,
    created_at TIMESTAMP NOT NULL,
    updated_at TIMESTAMP NOT NULL
);

CREATE UNIQUE INDEX IF NOT EXISTS uq_slotting_assignments_wh_bin_sku ON nx_slotting_assignments (warehouse_id, bin_id, sku);

CREATE TABLE IF NOT EXISTS nx_slotting_audits (
    id UUID PRIMARY KEY,
    tenant_id UUID,
    warehouse_id UUID NOT NULL,
    sku VARCHAR(120) NOT NULL,
    product_name VARCHAR(255),
    from_bin_id UUID,
    from_bin_code VARCHAR(100),
    to_bin_id UUID,
    to_bin_code VARCHAR(100),
    from_zone_id UUID,
    to_zone_id UUID,
    reason VARCHAR(255),
    action VARCHAR(100),
    moved_quantity INTEGER,
    performed_by VARCHAR(255),
    notes TEXT,
    created_at TIMESTAMP NOT NULL
);

CREATE INDEX IF NOT EXISTS idx_slotting_audits_wh_time ON nx_slotting_audits (warehouse_id, created_at);

CREATE TABLE IF NOT EXISTS nx_warehouse_equipment (
    id UUID PRIMARY KEY,
    tenant_id UUID,
    warehouse_id UUID NOT NULL,
    code VARCHAR(120) NOT NULL,
    equipment_type VARCHAR(100) NOT NULL,
    model VARCHAR(255),
    status VARCHAR(50) NOT NULL,
    battery_level INTEGER,
    last_maintenance_at TIMESTAMP,
    next_maintenance_at TIMESTAMP,
    assigned_to UUID,
    is_active BOOLEAN,
    created_at TIMESTAMP NOT NULL,
    updated_at TIMESTAMP NOT NULL
);

CREATE INDEX IF NOT EXISTS idx_equipment_wh_status ON nx_warehouse_equipment (warehouse_id, status);
