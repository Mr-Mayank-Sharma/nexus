-- V28: Create Transfer Order tables

CREATE TABLE nx_transfer_orders (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    tenant_id UUID NOT NULL,
    transfer_number VARCHAR(50) NOT NULL UNIQUE,
    transfer_type VARCHAR(50) NOT NULL,
    source_node_id UUID NOT NULL,
    destination_node_id UUID NOT NULL,
    status VARCHAR(50) NOT NULL DEFAULT 'DRAFT',
    priority VARCHAR(20) NOT NULL DEFAULT 'NORMAL',
    requested_by UUID,
    approved_by UUID,
    expected_arrival TIMESTAMP,
    actual_arrival TIMESTAMP,
    notes TEXT,
    metadata JSONB,
    created_at TIMESTAMP NOT NULL DEFAULT NOW(),
    updated_at TIMESTAMP NOT NULL DEFAULT NOW()
);

CREATE INDEX idx_transfer_tenant ON nx_transfer_orders(tenant_id);
CREATE INDEX idx_transfer_status ON nx_transfer_orders(status);
CREATE INDEX idx_transfer_source ON nx_transfer_orders(source_node_id);
CREATE INDEX idx_transfer_dest ON nx_transfer_orders(destination_node_id);
CREATE INDEX idx_transfer_number ON nx_transfer_orders(transfer_number);

CREATE TABLE nx_transfer_order_items (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    transfer_order_id UUID NOT NULL REFERENCES nx_transfer_orders(id) ON DELETE CASCADE,
    tenant_id UUID NOT NULL,
    sku VARCHAR(100) NOT NULL,
    product_name VARCHAR(255),
    quantity_requested INTEGER NOT NULL,
    quantity_shipped INTEGER,
    quantity_received INTEGER,
    unit_cost DECIMAL(12, 2),
    status VARCHAR(50) NOT NULL DEFAULT 'PENDING',
    created_at TIMESTAMP NOT NULL DEFAULT NOW(),
    updated_at TIMESTAMP NOT NULL DEFAULT NOW()
);

CREATE INDEX idx_transfer_item_order ON nx_transfer_order_items(transfer_order_id);
CREATE INDEX idx_transfer_item_sku ON nx_transfer_order_items(sku);
