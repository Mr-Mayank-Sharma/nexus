-- Normalize JSONB address fields into proper relational tables
-- Creates nx_addresses and nx_contacts tables, migrates existing data,
-- and adds foreign key references from nx_orders, nx_customers.

CREATE TABLE nx_addresses (
    id UUID PRIMARY KEY,
    tenant_id UUID,
    address_line1 VARCHAR(255),
    address_line2 VARCHAR(255),
    city VARCHAR(100),
    state VARCHAR(100),
    postal_code VARCHAR(20),
    country VARCHAR(100) DEFAULT 'IN',
    address_type VARCHAR(50),
    full_name VARCHAR(255),
    company VARCHAR(255),
    phone VARCHAR(50),
    is_residential BOOLEAN DEFAULT FALSE,
    created_at TIMESTAMP NOT NULL DEFAULT NOW(),
    updated_at TIMESTAMP NOT NULL DEFAULT NOW()
);

CREATE TABLE nx_contacts (
    id UUID PRIMARY KEY,
    tenant_id UUID,
    full_name VARCHAR(255),
    email VARCHAR(255),
    phone VARCHAR(50),
    position VARCHAR(255),
    department VARCHAR(255),
    is_primary BOOLEAN DEFAULT FALSE,
    notes TEXT,
    created_at TIMESTAMP NOT NULL DEFAULT NOW(),
    updated_at TIMESTAMP NOT NULL DEFAULT NOW()
);

-- Add foreign key columns to nx_orders
ALTER TABLE nx_orders ADD COLUMN IF NOT EXISTS ship_to_address_id UUID REFERENCES nx_addresses(id);
ALTER TABLE nx_orders ADD COLUMN IF NOT EXISTS billing_address_id UUID REFERENCES nx_addresses(id);

-- Add foreign key column to nx_customers
ALTER TABLE nx_customers ADD COLUMN IF NOT EXISTS address_id UUID REFERENCES nx_addresses(id);

-- Add foreign key columns to nx_invoices
ALTER TABLE nx_invoices ADD COLUMN IF NOT EXISTS billing_address_id UUID REFERENCES nx_addresses(id);
ALTER TABLE nx_invoices ADD COLUMN IF NOT EXISTS shipping_address_id UUID REFERENCES nx_addresses(id);

-- Migrate existing JSONB data from nx_orders.ship_to to nx_addresses
DO $$
DECLARE
    order_rec RECORD;
    addr_id UUID;
    addr_data JSONB;
BEGIN
    FOR order_rec IN SELECT id, tenant_id, ship_to, billing_address FROM nx_orders WHERE ship_to IS NOT NULL AND ship_to != 'null'::jsonb LOOP
        addr_id := gen_random_uuid();
        addr_data := order_rec.ship_to::jsonb;
        INSERT INTO nx_addresses (id, tenant_id, address_line1, address_line2, city, state, postal_code, country, address_type, created_at, updated_at)
        VALUES (
            addr_id,
            order_rec.tenant_id,
            addr_data->>'line1',
            addr_data->>'line2',
            addr_data->>'city',
            addr_data->>'state',
            COALESCE(addr_data->>'pincode', addr_data->>'zip', addr_data->>'postalCode'),
            COALESCE(addr_data->>'country', 'IN'),
            'SHIPPING',
            NOW(), NOW()
        );
        UPDATE nx_orders SET ship_to_address_id = addr_id WHERE id = order_rec.id;
    END LOOP;

    FOR order_rec IN SELECT id, tenant_id, billing_address FROM nx_orders WHERE billing_address IS NOT NULL AND billing_address != 'null'::jsonb LOOP
        addr_id := gen_random_uuid();
        addr_data := order_rec.billing_address::jsonb;
        INSERT INTO nx_addresses (id, tenant_id, address_line1, address_line2, city, state, postal_code, country, address_type, created_at, updated_at)
        VALUES (
            addr_id,
            order_rec.tenant_id,
            addr_data->>'line1',
            addr_data->>'line2',
            addr_data->>'city',
            addr_data->>'state',
            COALESCE(addr_data->>'pincode', addr_data->>'zip', addr_data->>'postalCode'),
            COALESCE(addr_data->>'country', 'IN'),
            'BILLING',
            NOW(), NOW()
        );
        UPDATE nx_orders SET billing_address_id = addr_id WHERE id = order_rec.id;
    END LOOP;
END $$;

-- Migrate existing JSONB data from nx_customers.address to nx_addresses
DO $$
DECLARE
    cust_rec RECORD;
    addr_id UUID;
    addr_data JSONB;
BEGIN
    FOR cust_rec IN SELECT id, tenant_id, address FROM nx_customers WHERE address IS NOT NULL AND address != 'null'::jsonb LOOP
        addr_id := gen_random_uuid();
        addr_data := cust_rec.address::jsonb;
        INSERT INTO nx_addresses (id, tenant_id, address_line1, address_line2, city, state, postal_code, country, address_type, created_at, updated_at)
        VALUES (
            addr_id,
            cust_rec.tenant_id,
            addr_data->>'line1',
            addr_data->>'line2',
            addr_data->>'city',
            addr_data->>'state',
            COALESCE(addr_data->>'pincode', addr_data->>'zip', addr_data->>'postalCode'),
            COALESCE(addr_data->>'country', 'IN'),
            'PRIMARY',
            NOW(), NOW()
        );
        UPDATE nx_customers SET address_id = addr_id WHERE id = cust_rec.id;
    END LOOP;
END $$;

-- Drop old JSONB columns after data migration
ALTER TABLE nx_orders DROP COLUMN IF EXISTS ship_to;
ALTER TABLE nx_orders DROP COLUMN IF EXISTS billing_address;
ALTER TABLE nx_customers DROP COLUMN IF EXISTS address;

-- Add indexes for performance
CREATE INDEX IF NOT EXISTS idx_nx_orders_ship_to_address ON nx_orders(ship_to_address_id);
CREATE INDEX IF NOT EXISTS idx_nx_orders_billing_address ON nx_orders(billing_address_id);
CREATE INDEX IF NOT EXISTS idx_nx_customers_address ON nx_customers(address_id);
CREATE INDEX IF NOT EXISTS idx_nx_addresses_tenant ON nx_addresses(tenant_id);
CREATE INDEX IF NOT EXISTS idx_nx_contacts_tenant ON nx_contacts(tenant_id);
