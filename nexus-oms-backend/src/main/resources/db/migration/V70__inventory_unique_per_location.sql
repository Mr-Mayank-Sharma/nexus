-- V70: Inventory uniqueness is per location, not per tenant
--
-- V20 added UNIQUE (tenant_id, sku), which allowed a SKU to be stocked in only ONE place per
-- tenant. That contradicts multi-location inventory: the allocator reserves per (sku, node) and
-- InventoryRepository looks rows up by (tenant, sku, node). It went unnoticed because the test
-- suite runs on H2 without Flyway. The natural key is (tenant, sku, location, lot).

DROP INDEX IF EXISTS idx_inventory_tenant_sku_unique;

CREATE UNIQUE INDEX IF NOT EXISTS idx_inventory_tenant_sku_node_lot_unique
    ON nx_inventory (tenant_id, sku,
                     COALESCE(node_id, '00000000-0000-0000-0000-000000000000'::uuid),
                     COALESCE(lot_number, ''));
