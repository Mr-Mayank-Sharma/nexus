-- V37: Create BOPIS Pickup tables

CREATE TABLE nx_pickup_orders (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    tenant_id UUID NOT NULL,
    order_id UUID NOT NULL,
    order_number VARCHAR(50) NOT NULL,
    node_id UUID NOT NULL,
    picker_id UUID,
    picker_name VARCHAR(255),
    status VARCHAR(50) NOT NULL DEFAULT 'PENDING',
    pickup_type VARCHAR(20) NOT NULL DEFAULT 'BOPIS',
    customer_name VARCHAR(255),
    customer_email VARCHAR(255),
    customer_phone VARCHAR(50),
    pickup_code VARCHAR(20),
    estimated_ready_at TIMESTAMP,
    picked_at TIMESTAMP,
    packed_at TIMESTAMP,
    ready_at TIMESTAMP,
    handed_off_at TIMESTAMP,
    collected_at TIMESTAMP,
    created_at TIMESTAMP NOT NULL DEFAULT NOW(),
    updated_at TIMESTAMP NOT NULL DEFAULT NOW()
);

CREATE INDEX idx_pickup_tenant ON nx_pickup_orders(tenant_id);
CREATE INDEX idx_pickup_node ON nx_pickup_orders(node_id);
CREATE INDEX idx_pickup_status ON nx_pickup_orders(status);
CREATE INDEX idx_pickup_picker ON nx_pickup_orders(picker_id);
CREATE INDEX idx_pickup_code ON nx_pickup_orders(pickup_code);

CREATE TABLE nx_pickup_order_items (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    pickup_order_id UUID NOT NULL,
    sku VARCHAR(100) NOT NULL,
    product_name VARCHAR(255),
    quantity INTEGER NOT NULL,
    picked_quantity INTEGER DEFAULT 0,
    location VARCHAR(100),
    status VARCHAR(50) NOT NULL DEFAULT 'PENDING',
    substituted_sku VARCHAR(100),
    notes TEXT,
    created_at TIMESTAMP NOT NULL DEFAULT NOW()
);

CREATE INDEX idx_pickup_item_order ON nx_pickup_order_items(pickup_order_id);

CREATE TABLE nx_proof_of_delivery (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    tenant_id UUID NOT NULL,
    pickup_order_id UUID NOT NULL,
    order_number VARCHAR(50) NOT NULL,
    collected_by_name VARCHAR(255) NOT NULL,
    collected_by_id_doc VARCHAR(255),
    collector_signature TEXT NOT NULL,
    associate_name VARCHAR(255) NOT NULL,
    associate_signature TEXT,
    collection_notes TEXT,
    items_handed_over INTEGER NOT NULL,
    photo_path VARCHAR(500),
    collected_at TIMESTAMP NOT NULL DEFAULT NOW(),
    created_at TIMESTAMP NOT NULL DEFAULT NOW()
);

CREATE INDEX idx_pod_order ON nx_proof_of_delivery(pickup_order_id);
