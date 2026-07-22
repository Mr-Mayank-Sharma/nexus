-- V36: Create Parked Orders table for Pre-order/Backorder parking

CREATE TABLE nx_parked_orders (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    tenant_id UUID NOT NULL,
    order_id UUID NOT NULL,
    order_number VARCHAR(50) NOT NULL,
    reason VARCHAR(50) NOT NULL,
    priority INTEGER DEFAULT 10,
    sku VARCHAR(100),
    product_name VARCHAR(255),
    quantity INTEGER DEFAULT 1,
    customer_email VARCHAR(255),
    expected_date TIMESTAMP,
    notes TEXT,
    status VARCHAR(50) NOT NULL DEFAULT 'PARKED',
    parked_at TIMESTAMP NOT NULL DEFAULT NOW(),
    released_at TIMESTAMP,
    created_at TIMESTAMP NOT NULL DEFAULT NOW(),
    updated_at TIMESTAMP NOT NULL DEFAULT NOW()
);

CREATE INDEX idx_parked_tenant ON nx_parked_orders(tenant_id);
CREATE INDEX idx_parked_order_id ON nx_parked_orders(order_id);
CREATE INDEX idx_parked_status ON nx_parked_orders(status);
CREATE INDEX idx_parked_reason ON nx_parked_orders(reason);
CREATE INDEX idx_parked_sku ON nx_parked_orders(sku);
