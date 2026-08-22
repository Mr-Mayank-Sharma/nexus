-- Box/carton catalog + kit templates (packing & kitting upgrade)

CREATE TABLE IF NOT EXISTS nx_box_templates (
    id UUID PRIMARY KEY,
    tenant_id UUID NOT NULL,
    name VARCHAR(120) NOT NULL,
    width_in DOUBLE PRECISION NOT NULL,
    height_in DOUBLE PRECISION NOT NULL,
    depth_in DOUBLE PRECISION NOT NULL,
    volume_capacity_in3 DOUBLE PRECISION NOT NULL,
    max_weight_lbs DOUBLE PRECISION NOT NULL,
    max_item_count INTEGER NOT NULL DEFAULT 0,
    is_active BOOLEAN NOT NULL DEFAULT TRUE,
    created_at TIMESTAMP NOT NULL,
    updated_at TIMESTAMP NOT NULL
);

CREATE INDEX IF NOT EXISTS idx_box_templates_tenant ON nx_box_templates (tenant_id, is_active);

CREATE TABLE IF NOT EXISTS nx_kit_templates (
    id UUID PRIMARY KEY,
    tenant_id UUID NOT NULL,
    kit_sku VARCHAR(120) NOT NULL,
    name VARCHAR(200) NOT NULL,
    components JSONB NOT NULL,
    is_active BOOLEAN NOT NULL DEFAULT TRUE,
    created_at TIMESTAMP NOT NULL,
    updated_at TIMESTAMP NOT NULL
);

CREATE UNIQUE INDEX IF NOT EXISTS uq_kit_templates_tenant_sku ON nx_kit_templates (tenant_id, kit_sku);

-- Seed default box catalog for the dev tenant
INSERT INTO nx_box_templates (id, tenant_id, name, width_in, height_in, depth_in, volume_capacity_in3, max_weight_lbs, max_item_count, is_active, created_at, updated_at)
SELECT * FROM (VALUES
    ('00000000-0000-0000-0000-00000000a101'::uuid, 'f226ca95-8668-4118-86f1-8e1da072d5da'::uuid, 'SM-MAILER', 11, 3, 8, 264, 5, 3, TRUE, NOW(), NOW()),
    ('00000000-0000-0000-0000-00000000a102'::uuid, 'f226ca95-8668-4118-86f1-8e1da072d5da'::uuid, 'SM-BOX', 12, 6, 9, 648, 15, 6, TRUE, NOW(), NOW()),
    ('00000000-0000-0000-0000-00000000a103'::uuid, 'f226ca95-8668-4118-86f1-8e1da072d5da'::uuid, 'MD-BOX', 16, 8, 12, 1536, 30, 12, TRUE, NOW(), NOW()),
    ('00000000-0000-0000-0000-00000000a104'::uuid, 'f226ca95-8668-4118-86f1-8e1da072d5da'::uuid, 'LG-BOX', 20, 10, 16, 3200, 50, 20, TRUE, NOW(), NOW()),
    ('00000000-0000-0000-0000-00000000a105'::uuid, 'f226ca95-8668-4118-86f1-8e1da072d5da'::uuid, 'XL-BOX', 24, 14, 20, 6720, 80, 40, TRUE, NOW(), NOW())
) AS seed(id, tenant_id, name, width_in, height_in, depth_in, volume_capacity_in3, max_weight_lbs, max_item_count, is_active, created_at, updated_at)
WHERE NOT EXISTS (SELECT 1 FROM nx_box_templates);
