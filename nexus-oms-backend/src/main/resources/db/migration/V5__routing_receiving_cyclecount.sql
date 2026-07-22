CREATE TABLE nx_routing_rules (
    id UUID PRIMARY KEY DEFAULT uuid_generate_v4(),
    tenant_id UUID,
    name VARCHAR(255) NOT NULL,
    description TEXT,
    priority INT NOT NULL,
    rule_type VARCHAR(50) NOT NULL,
    conditions JSONB,
    actions JSONB,
    is_active BOOLEAN DEFAULT TRUE,
    created_at TIMESTAMP NOT NULL DEFAULT NOW(),
    updated_at TIMESTAMP NOT NULL DEFAULT NOW()
);

CREATE TABLE nx_inventory_receipts (
    id UUID PRIMARY KEY DEFAULT uuid_generate_v4(),
    tenant_id UUID,
    node_id UUID,
    receipt_type VARCHAR(50) NOT NULL,
    reference_number VARCHAR(255),
    sku VARCHAR(100) NOT NULL,
    product_name VARCHAR(255),
    quantity INT NOT NULL,
    unit_cost DECIMAL(12,2),
    lot_number VARCHAR(100),
    expiry_date TIMESTAMP,
    status VARCHAR(50) NOT NULL DEFAULT 'PENDING',
    received_by VARCHAR(255),
    created_at TIMESTAMP NOT NULL DEFAULT NOW(),
    received_at TIMESTAMP
);

CREATE TABLE nx_cycle_counts (
    id UUID PRIMARY KEY DEFAULT uuid_generate_v4(),
    tenant_id UUID,
    node_id UUID,
    sku VARCHAR(100) NOT NULL,
    product_name VARCHAR(255),
    expected_qty INT NOT NULL,
    counted_qty INT,
    status VARCHAR(50) NOT NULL DEFAULT 'PENDING',
    counted_by VARCHAR(255),
    notes TEXT,
    created_at TIMESTAMP NOT NULL DEFAULT NOW(),
    counted_at TIMESTAMP,
    updated_at TIMESTAMP NOT NULL DEFAULT NOW()
);

CREATE INDEX idx_routing_rules_tenant ON nx_routing_rules(tenant_id, is_active);
CREATE INDEX idx_inventory_receipts_tenant ON nx_inventory_receipts(tenant_id, status);
CREATE INDEX idx_cycle_counts_tenant ON nx_cycle_counts(tenant_id, status);
