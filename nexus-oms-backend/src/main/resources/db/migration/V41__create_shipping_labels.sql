-- V41: Create Shipping Label tables for Advance Label Generation

CREATE TABLE nx_shipping_labels (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    tenant_id UUID NOT NULL,
    order_id UUID NOT NULL,
    order_number VARCHAR(50) NOT NULL,
    pickup_order_id UUID,
    carrier VARCHAR(50) NOT NULL,
    service_type VARCHAR(50) NOT NULL,
    tracking_number VARCHAR(100),
    label_url VARCHAR(500),
    label_base64 TEXT,
    status VARCHAR(50) NOT NULL DEFAULT 'GENERATED',
    generated_at TIMESTAMP NOT NULL DEFAULT NOW(),
    printed_at TIMESTAMP,
    attached_at TIMESTAMP,
    from_name VARCHAR(255),
    from_address TEXT,
    to_name VARCHAR(255),
    to_address TEXT,
    weight DOUBLE PRECISION,
    dimensions VARCHAR(100),
    created_at TIMESTAMP NOT NULL DEFAULT NOW(),
    updated_at TIMESTAMP NOT NULL DEFAULT NOW()
);

CREATE INDEX idx_label_tenant ON nx_shipping_labels(tenant_id);
CREATE INDEX idx_label_order ON nx_shipping_labels(order_id);
CREATE INDEX idx_label_pickup ON nx_shipping_labels(pickup_order_id);
CREATE INDEX idx_label_tracking ON nx_shipping_labels(tracking_number);
CREATE INDEX idx_label_status ON nx_shipping_labels(status);
