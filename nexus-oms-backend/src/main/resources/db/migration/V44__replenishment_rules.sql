-- V44: Replenishment Rules Engine
-- Manhattan parity: dynamic replenishment rules, demand-driven suggestions, priority scoring

CREATE TABLE IF NOT EXISTS nx_replenishment_rules (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    tenant_id UUID NOT NULL,
    warehouse_id UUID NOT NULL,
    rule_name VARCHAR(100) NOT NULL,
    rule_type VARCHAR(30) NOT NULL, -- MIN_MAX, EOQ, DEMAND_DRIVEN, TIME_PHASED, REORDER_POINT
    item_category VARCHAR(50),
    item_class VARCHAR(50),
    reorder_point DECIMAL(12,2) DEFAULT 0,
    reorder_qty DECIMAL(12,2) DEFAULT 0,
    safety_stock DECIMAL(12,2) DEFAULT 0,
    max_stock DECIMAL(12,2) DEFAULT 0,
    lead_time_days INT DEFAULT 7,
    demand_window_days INT DEFAULT 30,
    priority INT DEFAULT 50,
    is_active BOOLEAN DEFAULT TRUE,
    notes TEXT,
    created_at TIMESTAMP NOT NULL DEFAULT NOW(),
    updated_at TIMESTAMP NOT NULL DEFAULT NOW()
);

CREATE TABLE IF NOT EXISTS nx_replenishment_suggestions (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    tenant_id UUID NOT NULL,
    warehouse_id UUID NOT NULL,
    inventory_item_id UUID,
    sku VARCHAR(50),
    product_name VARCHAR(200),
    rule_id UUID,
    rule_type VARCHAR(30) NOT NULL,
    current_qty INT DEFAULT 0,
    reorder_point INT DEFAULT 0,
    suggested_qty INT NOT NULL,
    priority VARCHAR(10) NOT NULL, -- CRITICAL, HIGH, MEDIUM, LOW
    estimated_cost DECIMAL(12,2),
    estimated_delivery_days INT,
    status VARCHAR(20) NOT NULL DEFAULT 'PENDING', -- PENDING, APPROVED, REJECTED, ORDERED, RECEIVED
    approved_by VARCHAR(100),
    notes TEXT,
    created_at TIMESTAMP NOT NULL DEFAULT NOW(),
    updated_at TIMESTAMP NOT NULL DEFAULT NOW()
);

CREATE INDEX IF NOT EXISTS idx_replenishment_suggestions_status ON nx_replenishment_suggestions(warehouse_id, status);
CREATE INDEX IF NOT EXISTS idx_replenishment_suggestions_priority ON nx_replenishment_suggestions(priority);
