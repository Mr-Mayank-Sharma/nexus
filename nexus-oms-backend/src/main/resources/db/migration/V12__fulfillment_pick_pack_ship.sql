-- ============================================================
-- Enterprise Fulfillment: Picking, Packing, Shipping
-- V12 migration
-- ============================================================

-- 1. PICKLISTS (waves/batches of picks)
CREATE TABLE IF NOT EXISTS nx_picklists (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    tenant_id UUID NOT NULL,
    name VARCHAR(255) NOT NULL,
    wave_type VARCHAR(30) DEFAULT 'SINGLE_ORDER' CHECK (wave_type IN ('SINGLE_ORDER','BATCH','WAVE','ZONE')),
    priority VARCHAR(10) DEFAULT 'NORMAL' CHECK (priority IN ('LOW','NORMAL','HIGH','URGENT')),
    status VARCHAR(30) DEFAULT 'OPEN' CHECK (status IN ('OPEN','IN_PROGRESS','COMPLETED','CANCELLED')),
    assignee_id UUID,
    total_items INT DEFAULT 0,
    picked_items INT DEFAULT 0,
    order_ids UUID[],
    notes TEXT,
    started_at TIMESTAMP,
    completed_at TIMESTAMP,
    created_by VARCHAR(255),
    created_at TIMESTAMP DEFAULT now(),
    updated_at TIMESTAMP DEFAULT now()
);
CREATE INDEX idx_nx_picklists_tenant ON nx_picklists(tenant_id);
CREATE INDEX idx_nx_picklists_status ON nx_picklists(status);
CREATE INDEX idx_nx_picklists_assignee ON nx_picklists(assignee_id);

-- 2. PICKLIST ITEMS
CREATE TABLE IF NOT EXISTS nx_picklist_items (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    picklist_id UUID NOT NULL REFERENCES nx_picklists(id) ON DELETE CASCADE,
    tenant_id UUID NOT NULL,
    order_id UUID NOT NULL,
    order_item_id UUID,
    sku VARCHAR(255) NOT NULL,
    product_name VARCHAR(512),
    quantity INT NOT NULL,
    picked_quantity INT DEFAULT 0,
    from_bin_id UUID,
    from_location VARCHAR(255),
    status VARCHAR(20) DEFAULT 'PENDING' CHECK (status IN ('PENDING','PICKING','PICKED','SKIPPED','CANCELLED')),
    picked_at TIMESTAMP,
    picked_by UUID,
    notes TEXT,
    created_at TIMESTAMP DEFAULT now()
);
CREATE INDEX idx_nx_picklist_items_picklist ON nx_picklist_items(picklist_id);
CREATE INDEX idx_nx_picklist_items_status ON nx_picklist_items(status);
CREATE INDEX idx_nx_picklist_items_order ON nx_picklist_items(order_id);

-- 3. PACKAGES (packing sessions / shipping units)
CREATE TABLE IF NOT EXISTS nx_packages (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    tenant_id UUID NOT NULL,
    order_id UUID NOT NULL,
    picklist_id UUID REFERENCES nx_picklists(id),
    package_type VARCHAR(30) DEFAULT 'BOX' CHECK (package_type IN ('BOX','PALLET','CRATE','ENVELOPE','TUBE','BAG')),
    box_name VARCHAR(255),
    weight_lbs DECIMAL(8,2),
    width_in DECIMAL(6,1),
    height_in DECIMAL(6,1),
    depth_in DECIMAL(6,1),
    items JSONB,
    item_count INT DEFAULT 0,
    tracking_number VARCHAR(255),
    carrier_id UUID,
    carrier_name VARCHAR(255),
    service_level VARCHAR(100),
    label_url TEXT,
    label_format VARCHAR(10) DEFAULT 'PDF',
    shipping_cost DECIMAL(10,2),
    status VARCHAR(20) DEFAULT 'PENDING_PACK' CHECK (status IN ('PENDING_PACK','PACKING','PACKED','LABELED','SHIPPED','VOIDED')),
    notes TEXT,
    packed_by VARCHAR(255),
    packed_at TIMESTAMP,
    shipped_at TIMESTAMP,
    created_at TIMESTAMP DEFAULT now(),
    updated_at TIMESTAMP DEFAULT now()
);
CREATE INDEX idx_nx_packages_tenant ON nx_packages(tenant_id);
CREATE INDEX idx_nx_packages_order ON nx_packages(order_id);
CREATE INDEX idx_nx_packages_status ON nx_packages(status);
CREATE INDEX idx_nx_packages_carrier ON nx_packages(carrier_id);
