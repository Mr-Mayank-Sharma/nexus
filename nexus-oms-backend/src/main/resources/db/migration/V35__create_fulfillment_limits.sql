-- V35: Create Fulfillment Limits tables

CREATE TABLE nx_fulfillment_limits (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    tenant_id UUID NOT NULL,
    node_id UUID NOT NULL,
    max_orders_per_day INTEGER,
    max_orders_per_week INTEGER,
    max_items_per_day INTEGER,
    current_orders_today INTEGER DEFAULT 0,
    current_orders_this_week INTEGER DEFAULT 0,
    current_items_today INTEGER DEFAULT 0,
    fulfillment_enabled BOOLEAN NOT NULL DEFAULT TRUE,
    alert_threshold DECIMAL(5, 2) DEFAULT 0.80,
    last_reset_at TIMESTAMP,
    created_at TIMESTAMP NOT NULL DEFAULT NOW(),
    updated_at TIMESTAMP NOT NULL DEFAULT NOW(),
    UNIQUE(tenant_id, node_id)
);

CREATE INDEX idx_fulfillment_tenant ON nx_fulfillment_limits(tenant_id);
CREATE INDEX idx_fulfillment_node ON nx_fulfillment_limits(node_id);

CREATE TABLE nx_fulfillment_capacity_log (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    tenant_id UUID NOT NULL,
    node_id UUID NOT NULL,
    order_id UUID,
    action VARCHAR(50) NOT NULL,
    orders_before INTEGER,
    orders_after INTEGER,
    capacity_percentage DECIMAL(5, 2),
    created_at TIMESTAMP NOT NULL DEFAULT NOW()
);

CREATE INDEX idx_capacity_log_tenant ON nx_fulfillment_capacity_log(tenant_id);
CREATE INDEX idx_capacity_log_node ON nx_fulfillment_capacity_log(node_id);
CREATE INDEX idx_capacity_log_created ON nx_fulfillment_capacity_log(created_at);
