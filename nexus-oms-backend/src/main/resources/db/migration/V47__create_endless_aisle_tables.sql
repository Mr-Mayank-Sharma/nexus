-- Endless Aisle: store-initiated orders for out-of-stock items
CREATE TABLE nx_endless_aisle_orders (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    tenant_id UUID NOT NULL,
    store_id UUID NOT NULL,
    customer_id UUID,
    customer_name VARCHAR(255),
    customer_email VARCHAR(255),
    customer_phone VARCHAR(50),
    product_sku VARCHAR(100) NOT NULL,
    product_name VARCHAR(255) NOT NULL,
    quantity INTEGER NOT NULL,
    unit_price DECIMAL(12,2) NOT NULL,
    total_amount DECIMAL(12,2) NOT NULL,
    fulfillment_type VARCHAR(50) NOT NULL, -- SHIP_TO_CUSTOMER, SHIP_TO_STORE
    ship_to_address TEXT,
    status VARCHAR(50) NOT NULL DEFAULT 'PENDING', -- PENDING, CONFIRMED, PROCESSING, SHIPPED, DELIVERED, CANCELLED
    linked_order_id UUID,
    notes TEXT,
    created_by VARCHAR(100),
    created_at TIMESTAMP NOT NULL DEFAULT NOW(),
    updated_at TIMESTAMP NOT NULL DEFAULT NOW()
);

CREATE INDEX idx_endless_aisle_tenant ON nx_endless_aisle_orders(tenant_id);
CREATE INDEX idx_endless_aisle_store ON nx_endless_aisle_orders(store_id);
CREATE INDEX idx_endless_aisle_status ON nx_endless_aisle_orders(tenant_id, status);
CREATE INDEX idx_endless_aisle_customer ON nx_endless_aisle_orders(customer_id);
