-- V38: Create Rejection Handling tables

CREATE TABLE nx_rejection_reasons (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    tenant_id UUID NOT NULL,
    code VARCHAR(50) NOT NULL,
    label VARCHAR(255) NOT NULL,
    description TEXT,
    category VARCHAR(50) NOT NULL,
    inventory_impact VARCHAR(50) NOT NULL,
    requires_photo BOOLEAN NOT NULL DEFAULT FALSE,
    requires_notes BOOLEAN NOT NULL DEFAULT FALSE,
    active BOOLEAN NOT NULL DEFAULT TRUE,
    sort_order INTEGER DEFAULT 0,
    created_at TIMESTAMP NOT NULL DEFAULT NOW(),
    updated_at TIMESTAMP NOT NULL DEFAULT NOW(),
    UNIQUE(tenant_id, code)
);

CREATE INDEX idx_rejection_reason_tenant ON nx_rejection_reasons(tenant_id);

CREATE TABLE nx_order_rejections (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    tenant_id UUID NOT NULL,
    order_id UUID NOT NULL,
    order_number VARCHAR(50) NOT NULL,
    order_item_id UUID,
    sku VARCHAR(100) NOT NULL,
    rejection_reason_id UUID NOT NULL,
    rejection_code VARCHAR(50) NOT NULL,
    quantity INTEGER NOT NULL,
    rejected_by VARCHAR(255) NOT NULL,
    notes TEXT,
    photo_path VARCHAR(500),
    inventory_action VARCHAR(50) NOT NULL,
    inventory_adjusted BOOLEAN NOT NULL DEFAULT FALSE,
    status VARCHAR(50) NOT NULL DEFAULT 'PENDING',
    processed_at TIMESTAMP,
    created_at TIMESTAMP NOT NULL DEFAULT NOW(),
    updated_at TIMESTAMP NOT NULL DEFAULT NOW()
);

CREATE INDEX idx_rejection_tenant ON nx_order_rejections(tenant_id);
CREATE INDEX idx_rejection_order ON nx_order_rejections(order_id);
CREATE INDEX idx_rejection_status ON nx_order_rejections(status);
CREATE INDEX idx_rejection_code ON nx_order_rejections(rejection_code);

-- Seed default rejection reasons
INSERT INTO nx_rejection_reasons (tenant_id, code, label, category, inventory_impact, requires_photo, requires_notes, sort_order)
VALUES
    ('00000000-0000-0000-0000-000000000001', 'QUALITY_DEFECT', 'Quality Defect', 'QUALITY', 'QUARANTINE', true, false, 1),
    ('00000000-0000-0000-0000-000000000001', 'DAMAGED_PACKAGING', 'Damaged Packaging', 'DAMAGED', 'DAMAGE_WRITE_OFF', true, false, 2),
    ('00000000-0000-0000-0000-000000000001', 'DAMAGED_PRODUCT', 'Damaged Product', 'DAMAGED', 'DAMAGE_WRITE_OFF', true, true, 3),
    ('00000000-0000-0000-0000-000000000001', 'WRONG_ITEM', 'Wrong Item Received', 'WRONG_ITEM', 'RESTOCK', false, true, 4),
    ('00000000-0000-0000-0000-000000000001', 'EXPIRED', 'Expired Product', 'QUALITY', 'DAMAGE_WRITE_OFF', false, false, 5),
    ('00000000-0000-0000-0000-000000000001', 'CUSTOMER_REFUSED', 'Customer Refused', 'CUSTOMER', 'RESTOCK', false, true, 6),
    ('00000000-0000-0000-0000-000000000001', 'INVENTORY_SHORT', 'Inventory Shortage', 'INVENTORY', 'RESTOCK', false, true, 7),
    ('00000000-0000-0000-0000-000000000001', 'OTHER', 'Other', 'OTHER', 'RESTOCK', false, true, 99);
