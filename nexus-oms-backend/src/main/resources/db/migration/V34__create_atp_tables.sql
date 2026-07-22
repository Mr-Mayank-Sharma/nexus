-- V34: Create ATP Calculation Engine tables

CREATE TABLE nx_atp_rules (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    tenant_id UUID NOT NULL,
    name VARCHAR(255),
    rule_type VARCHAR(50) NOT NULL,
    priority INTEGER NOT NULL DEFAULT 10,
    safety_stock_pct DECIMAL(5, 2),
    reserve_window_hours INTEGER,
    active BOOLEAN NOT NULL DEFAULT TRUE,
    created_at TIMESTAMP NOT NULL DEFAULT NOW(),
    updated_at TIMESTAMP NOT NULL DEFAULT NOW()
);

CREATE INDEX idx_atp_rule_tenant ON nx_atp_rules(tenant_id);
CREATE INDEX idx_atp_rule_type ON nx_atp_rules(rule_type);

CREATE TABLE nx_atp_snapshots (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    tenant_id UUID NOT NULL,
    node_id UUID NOT NULL,
    sku VARCHAR(100) NOT NULL,
    physical_stock INTEGER NOT NULL,
    reserved_stock INTEGER NOT NULL DEFAULT 0,
    safety_stock INTEGER NOT NULL DEFAULT 0,
    allocated_stock INTEGER NOT NULL DEFAULT 0,
    atp_quantity INTEGER NOT NULL DEFAULT 0,
    total_demand INTEGER NOT NULL DEFAULT 0,
    net_atp INTEGER NOT NULL DEFAULT 0,
    snapshot_date TIMESTAMP NOT NULL DEFAULT NOW(),
    created_at TIMESTAMP NOT NULL DEFAULT NOW()
);

CREATE INDEX idx_atp_snap_tenant ON nx_atp_snapshots(tenant_id);
CREATE INDEX idx_atp_snap_node_sku ON nx_atp_snapshots(node_id, sku);
CREATE INDEX idx_atp_snap_date ON nx_atp_snapshots(snapshot_date);
