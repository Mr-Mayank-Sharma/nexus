CREATE EXTENSION IF NOT EXISTS "uuid-ossp";

CREATE TABLE nx_users (
    id UUID PRIMARY KEY DEFAULT uuid_generate_v4(),
    username VARCHAR(100) NOT NULL UNIQUE,
    password_hash VARCHAR(255) NOT NULL,
    email VARCHAR(255),
    role VARCHAR(20) NOT NULL DEFAULT 'VIEWER',
    tenant_id UUID,
    created_at TIMESTAMP NOT NULL DEFAULT NOW(),
    updated_at TIMESTAMP NOT NULL DEFAULT NOW()
);

CREATE TABLE nx_customers (
    id UUID PRIMARY KEY DEFAULT uuid_generate_v4(),
    tenant_id UUID,
    external_id VARCHAR(255),
    name VARCHAR(255) NOT NULL,
    email VARCHAR(255),
    phone VARCHAR(50),
    address JSONB,
    created_at TIMESTAMP NOT NULL DEFAULT NOW()
);

CREATE TABLE nx_orders (
    id UUID PRIMARY KEY DEFAULT uuid_generate_v4(),
    tenant_id UUID,
    external_id VARCHAR(255),
    channel VARCHAR(50),
    channel_order_id VARCHAR(255),
    customer_id UUID REFERENCES nx_customers(id),
    status VARCHAR(50) NOT NULL DEFAULT 'PENDING',
    sub_status VARCHAR(50),
    fulfillment_type VARCHAR(50),
    ship_from VARCHAR(255),
    ship_to JSONB,
    billing_address JSONB,
    currency VARCHAR(3) DEFAULT 'INR',
    subtotal DECIMAL(12,2) DEFAULT 0.00,
    shipping_cost DECIMAL(12,2) DEFAULT 0.00,
    tax_amount DECIMAL(12,2) DEFAULT 0.00,
    total DECIMAL(12,2) DEFAULT 0.00,
    payment_status VARCHAR(50),
    payment_reference VARCHAR(255),
    allocated_node UUID,
    allocation_rule VARCHAR(50),
    allocation_confidence DECIMAL(5,4),
    carrier_id VARCHAR(100),
    tracking_number VARCHAR(255),
    label_url TEXT,
    promised_delivery TIMESTAMP,
    created_at TIMESTAMP NOT NULL DEFAULT NOW(),
    updated_at TIMESTAMP NOT NULL DEFAULT NOW(),
    shipped_at TIMESTAMP,
    delivered_at TIMESTAMP,
    metadata JSONB
);

CREATE TABLE nx_order_items (
    id UUID PRIMARY KEY DEFAULT uuid_generate_v4(),
    order_id UUID NOT NULL REFERENCES nx_orders(id) ON DELETE CASCADE,
    sku VARCHAR(100) NOT NULL,
    product_name VARCHAR(255),
    quantity INT NOT NULL DEFAULT 1,
    unit_price DECIMAL(12,2) NOT NULL DEFAULT 0.00,
    total_price DECIMAL(12,2) NOT NULL DEFAULT 0.00,
    allocated_node_id UUID,
    allocated_qty INT DEFAULT 0
);

CREATE TABLE nx_shipments (
    id UUID PRIMARY KEY DEFAULT uuid_generate_v4(),
    order_id UUID NOT NULL REFERENCES nx_orders(id),
    tenant_id UUID,
    carrier_id VARCHAR(100),
    service_level VARCHAR(50),
    tracking_number VARCHAR(255),
    label_url TEXT,
    label_format VARCHAR(10),
    voided BOOLEAN DEFAULT FALSE,
    rate JSONB,
    cost_components JSONB,
    origin_node_id UUID,
    estimated_delivery TIMESTAMP,
    actual_delivery TIMESTAMP,
    status VARCHAR(50) NOT NULL DEFAULT 'PENDING',
    created_at TIMESTAMP NOT NULL DEFAULT NOW(),
    shipped_at TIMESTAMP,
    manifest_closed_at TIMESTAMP
);

CREATE TABLE nx_inventory (
    id UUID PRIMARY KEY DEFAULT uuid_generate_v4(),
    tenant_id UUID,
    sku VARCHAR(100) NOT NULL,
    node_id UUID,
    quantity_on_hand INT DEFAULT 0,
    quantity_allocated INT DEFAULT 0,
    quantity_reserved INT DEFAULT 0,
    quantity_in_transit INT DEFAULT 0,
    quantity_on_order INT DEFAULT 0,
    quantity_damaged INT DEFAULT 0,
    safety_stock INT DEFAULT 0,
    reorder_point INT DEFAULT 0,
    reorder_qty INT DEFAULT 0,
    lot_number VARCHAR(100),
    expiry_date DATE,
    created_at TIMESTAMP NOT NULL DEFAULT NOW(),
    updated_at TIMESTAMP NOT NULL DEFAULT NOW()
);

CREATE TABLE nx_nodes (
    id UUID PRIMARY KEY DEFAULT uuid_generate_v4(),
    tenant_id UUID,
    name VARCHAR(255) NOT NULL,
    type VARCHAR(20) NOT NULL,
    address JSONB,
    latitude DECIMAL(10,7),
    longitude DECIMAL(10,7),
    is_active BOOLEAN DEFAULT TRUE,
    capacity_daily INT,
    cut_off_time TIME,
    carrier_config JSONB
);

CREATE TABLE nx_carrier_accounts (
    id UUID PRIMARY KEY DEFAULT uuid_generate_v4(),
    tenant_id UUID,
    carrier_id VARCHAR(100) NOT NULL,
    account_number VARCHAR(255),
    api_key_encrypted TEXT,
    api_secret_encrypted TEXT,
    negotiated_discount DECIMAL(5,4) DEFAULT 0.0000,
    contract_effective DATE,
    contract_expiry DATE,
    is_active BOOLEAN DEFAULT TRUE,
    node_id UUID REFERENCES nx_nodes(id)
);

CREATE TABLE nx_returns (
    id UUID PRIMARY KEY DEFAULT uuid_generate_v4(),
    tenant_id UUID,
    order_id UUID NOT NULL REFERENCES nx_orders(id),
    customer_id UUID,
    reason TEXT,
    grade VARCHAR(10),
    disposition VARCHAR(50),
    carrier_id VARCHAR(100),
    tracking_number VARCHAR(255),
    label_url TEXT,
    refund_amount DECIMAL(12,2) DEFAULT 0.00,
    refund_reference VARCHAR(255),
    status VARCHAR(50) NOT NULL DEFAULT 'PENDING',
    inspected_at TIMESTAMP,
    created_at TIMESTAMP NOT NULL DEFAULT NOW(),
    updated_at TIMESTAMP NOT NULL DEFAULT NOW()
);

CREATE TABLE nx_tracking_events (
    id UUID PRIMARY KEY DEFAULT uuid_generate_v4(),
    shipment_id UUID NOT NULL REFERENCES nx_shipments(id),
    event_type VARCHAR(100),
    location VARCHAR(255),
    timestamp TIMESTAMP,
    description TEXT,
    raw_data JSONB
);

CREATE TABLE nx_audit_log (
    id UUID PRIMARY KEY DEFAULT uuid_generate_v4(),
    tenant_id UUID,
    entity_type VARCHAR(100),
    entity_id UUID,
    event_type VARCHAR(100),
    actor_id UUID,
    actor_type VARCHAR(50),
    data JSONB,
    created_at TIMESTAMP NOT NULL DEFAULT NOW()
);

CREATE INDEX idx_orders_tenant_status ON nx_orders(tenant_id, status);
CREATE INDEX idx_orders_channel_order_id ON nx_orders(channel_order_id);
CREATE INDEX idx_orders_tracking ON nx_orders(tracking_number);
CREATE INDEX idx_orders_customer ON nx_orders(customer_id);
CREATE INDEX idx_order_items_order ON nx_order_items(order_id);
CREATE INDEX idx_shipments_tracking ON nx_shipments(tracking_number);
CREATE INDEX idx_shipments_order ON nx_shipments(order_id);
CREATE INDEX idx_inventory_sku ON nx_inventory(sku);
CREATE INDEX idx_inventory_node ON nx_inventory(node_id);
CREATE INDEX idx_inventory_tenant_sku ON nx_inventory(tenant_id, sku);
CREATE INDEX idx_nodes_tenant_active ON nx_nodes(tenant_id, is_active);
CREATE INDEX idx_tracking_shipment ON nx_tracking_events(shipment_id);
CREATE INDEX idx_returns_order ON nx_returns(order_id);
CREATE INDEX idx_audit_entity ON nx_audit_log(entity_type, entity_id);
CREATE INDEX idx_audit_tenant ON nx_audit_log(tenant_id, created_at);
