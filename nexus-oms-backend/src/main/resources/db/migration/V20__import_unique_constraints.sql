-- Add unique constraints needed by GenericImportService UPSERT mode

-- nx_inventory: one SKU per tenant should be unique
CREATE UNIQUE INDEX IF NOT EXISTS idx_inventory_tenant_sku_unique ON nx_inventory(tenant_id, sku);

-- nx_customers: one email per tenant should be unique for dedup
CREATE UNIQUE INDEX IF NOT EXISTS idx_customers_tenant_email ON nx_customers(tenant_id, email);

-- nx_suppliers: supplier code per tenant should be unique
CREATE UNIQUE INDEX IF NOT EXISTS idx_suppliers_tenant_code ON nx_suppliers(tenant_id, supplier_code);
