-- ============================================================
-- Enterprise Returns Enhancement: Return Items & RMA Lifecycle
-- V13 migration
-- ============================================================

-- 1. RETURN ITEMS (individual line items in a return)
CREATE TABLE IF NOT EXISTS nx_return_items (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    return_id UUID NOT NULL REFERENCES nx_returns(id) ON DELETE CASCADE,
    tenant_id UUID NOT NULL,
    order_item_id UUID,
    sku VARCHAR(255) NOT NULL,
    product_name VARCHAR(512),
    quantity INT NOT NULL DEFAULT 1,
    return_reason VARCHAR(100),
    return_reason_detail TEXT,
    condition VARCHAR(30) DEFAULT 'UNKNOWN' CHECK (condition IN ('LIKE_NEW','GOOD','FAIR','DAMAGED','DEFECTIVE','UNKNOWN')),
    condition_notes TEXT,
    grade VARCHAR(10),
    disposition VARCHAR(50) DEFAULT 'PENDING' CHECK (disposition IN ('PENDING','RESTOCK','REFURBISH','RETURN_TO_VENDOR','DISPOSE','DONATE')),
    refund_amount DECIMAL(12,2) DEFAULT 0.00,
    status VARCHAR(30) DEFAULT 'PENDING' CHECK (status IN ('PENDING','APPROVED','RECEIVED','INSPECTED','REFUNDED','REJECTED')),
    inspected_at TIMESTAMP,
    inspected_by UUID,
    created_at TIMESTAMP DEFAULT now(),
    updated_at TIMESTAMP DEFAULT now()
);
CREATE INDEX idx_nx_return_items_return ON nx_return_items(return_id);
CREATE INDEX idx_nx_return_items_status ON nx_return_items(status);
CREATE INDEX idx_nx_return_items_tenant ON nx_return_items(tenant_id);

-- 2. Add RMA number column to nx_returns (for unique RMA tracking)
ALTER TABLE nx_returns ADD COLUMN IF NOT EXISTS rma_number VARCHAR(50) UNIQUE;
ALTER TABLE nx_returns ADD COLUMN IF NOT EXISTS rma_type VARCHAR(30) DEFAULT 'RETURN' CHECK (rma_type IN ('RETURN','EXCHANGE','REPLACEMENT','CANCEL'));
ALTER TABLE nx_returns ADD COLUMN IF NOT EXISTS return_channel VARCHAR(30) DEFAULT 'MANUAL' CHECK (return_channel IN ('MANUAL','PORTAL','EMAIL','EDI','API'));
ALTER TABLE nx_returns ADD COLUMN IF NOT EXISTS approved_at TIMESTAMP;
ALTER TABLE nx_returns ADD COLUMN IF NOT EXISTS approved_by UUID;
ALTER TABLE nx_returns ADD COLUMN IF NOT EXISTS received_at TIMESTAMP;
ALTER TABLE nx_returns ADD COLUMN IF NOT EXISTS received_by UUID;
ALTER TABLE nx_returns ADD COLUMN IF NOT EXISTS refunded_at TIMESTAMP;
ALTER TABLE nx_returns ADD COLUMN IF NOT EXISTS rejected_reason TEXT;
ALTER TABLE nx_returns ADD COLUMN IF NOT EXISTS exchange_order_id UUID;
ALTER TABLE nx_returns ADD COLUMN IF NOT EXISTS return_label_url TEXT;
ALTER TABLE nx_returns ADD COLUMN IF NOT EXISTS return_tracking_number VARCHAR(255);

-- 3. RETURN REASONS taxonomy (configurable per tenant)
CREATE TABLE IF NOT EXISTS nx_return_reasons (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    tenant_id UUID NOT NULL,
    code VARCHAR(50) NOT NULL,
    label VARCHAR(255) NOT NULL,
    category VARCHAR(50) DEFAULT 'OTHER' CHECK (category IN ('PRODUCT','QUALITY','SHIPPING','CUSTOMER','OTHER')),
    requires_detail BOOLEAN DEFAULT false,
    is_active BOOLEAN DEFAULT true,
    sort_order INT DEFAULT 0,
    created_at TIMESTAMP DEFAULT now()
);
CREATE INDEX idx_nx_return_reasons_tenant ON nx_return_reasons(tenant_id);
CREATE UNIQUE INDEX idx_nx_return_reasons_code_tenant ON nx_return_reasons(tenant_id, code);

-- Seed default return reasons
INSERT INTO nx_return_reasons (tenant_id, code, label, category, requires_detail, sort_order) VALUES
    ('00000000-0000-0000-0000-000000000000', 'DEFECTIVE', 'Defective / Not Working', 'QUALITY', true, 1),
    ('00000000-0000-0000-0000-000000000000', 'WRONG_ITEM', 'Wrong Item Shipped', 'SHIPPING', true, 2),
    ('00000000-0000-0000-0000-000000000000', 'DAMAGED', 'Damaged in Transit', 'SHIPPING', true, 3),
    ('00000000-0000-0000-0000-000000000000', 'NOT_AS_DESC', 'Not as Described', 'PRODUCT', true, 4),
    ('00000000-0000-0000-0000-000000000000', 'SIZE_FIT', 'Size / Fit Issue', 'PRODUCT', false, 5),
    ('00000000-0000-0000-0000-000000000000', 'CHANGED_MIND', 'Changed Mind', 'CUSTOMER', false, 6),
    ('00000000-0000-0000-0000-000000000000', 'DUPLICATE', 'Duplicate Order', 'CUSTOMER', false, 7),
    ('00000000-0000-0000-0000-000000000000', 'LATE_DELIVERY', 'Late Delivery', 'SHIPPING', false, 8),
    ('00000000-0000-0000-0000-000000000000', 'BETTER_PRICE', 'Found Better Price', 'CUSTOMER', false, 9),
    ('00000000-0000-0000-0000-000000000000', 'OTHER', 'Other Reason', 'OTHER', true, 10)
ON CONFLICT DO NOTHING;
