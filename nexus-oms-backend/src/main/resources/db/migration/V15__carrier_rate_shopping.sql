-- ============================================================
-- Carrier Rate Shopping Engine
-- V15 migration
-- ============================================================

-- 1. CARRIER RATES (zone/weight-based pricing per carrier service)
CREATE TABLE IF NOT EXISTS nx_carrier_rates (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    tenant_id UUID NOT NULL,
    carrier_code VARCHAR(50) NOT NULL,
    carrier_name VARCHAR(255) NOT NULL,
    service_level VARCHAR(50) NOT NULL CHECK (service_level IN ('GROUND','EXPRESS','OVERNIGHT','TWO_DAY','THREE_DAY','FREIGHT_LTL','FREIGHT_FTL','INTERNATIONAL_ECONOMY','INTERNATIONAL_EXPRESS','SAME_DAY')),
    service_name VARCHAR(255) NOT NULL,
    zone VARCHAR(10),
    weight_min_kg DECIMAL(10,3) DEFAULT 0,
    weight_max_kg DECIMAL(10,3) DEFAULT 99999,
    base_rate DECIMAL(12,2) NOT NULL,
    per_kg_rate DECIMAL(12,4) DEFAULT 0,
    fuel_surcharge_pct DECIMAL(5,2) DEFAULT 0,
    residential_surcharge DECIMAL(12,2) DEFAULT 0,
    transit_days_min INT DEFAULT 1,
    transit_days_max INT DEFAULT 10,
    is_active BOOLEAN DEFAULT true,
    effective_date DATE,
    expiry_date DATE,
    metadata JSONB,
    created_at TIMESTAMP DEFAULT now(),
    updated_at TIMESTAMP DEFAULT now()
);
CREATE INDEX idx_nx_carrier_rates_tenant ON nx_carrier_rates(tenant_id);
CREATE INDEX idx_nx_carrier_rates_carrier ON nx_carrier_rates(carrier_code);
CREATE INDEX idx_nx_carrier_rates_service ON nx_carrier_rates(service_level);
CREATE INDEX idx_nx_carrier_rates_active ON nx_carrier_rates(tenant_id, is_active);

-- 2. CARRIER ZONES (origin/destination zone mappings)
CREATE TABLE IF NOT EXISTS nx_carrier_zones (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    tenant_id UUID NOT NULL,
    carrier_code VARCHAR(50) NOT NULL,
    zone_code VARCHAR(10) NOT NULL,
    zip_prefix VARCHAR(10) NOT NULL,
    country VARCHAR(10) DEFAULT 'US',
    is_origin BOOLEAN DEFAULT false,
    is_destination BOOLEAN DEFAULT true,
    created_at TIMESTAMP DEFAULT now()
);
CREATE INDEX idx_nx_carrier_zones_carrier ON nx_carrier_zones(carrier_code);
CREATE INDEX idx_nx_carrier_zones_zip ON nx_carrier_zones(zip_prefix);

-- 3. RATE SHOPPING LOG (audit trail)
CREATE TABLE IF NOT EXISTS nx_rate_shopping_log (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    order_id UUID,
    tenant_id UUID NOT NULL,
    from_zip VARCHAR(20),
    to_zip VARCHAR(20),
    to_country VARCHAR(10),
    total_weight_kg DECIMAL(10,3),
    declared_value DECIMAL(12,2),
    num_packages INT DEFAULT 1,
    results JSONB,
    selected_carrier_code VARCHAR(50),
    selected_service VARCHAR(50),
    total_cost DECIMAL(12,2),
    estimated_delivery_days INT,
    execution_time_ms INT,
    created_at TIMESTAMP DEFAULT now()
);
CREATE INDEX idx_nx_rate_shopping_log_tenant ON nx_rate_shopping_log(tenant_id);
CREATE INDEX idx_nx_rate_shopping_log_order ON nx_rate_shopping_log(order_id);

-- 4. SEED SAMPLE CARRIER RATES (tenant-agnostic defaults)
INSERT INTO nx_carrier_rates (tenant_id, carrier_code, carrier_name, service_level, service_name, zone, weight_min_kg, weight_max_kg, base_rate, per_kg_rate, transit_days_min, transit_days_max)
VALUES
    ('00000000-0000-0000-0000-000000000000', 'UPS', 'UPS', 'GROUND', 'Ground', NULL, 0, 5, 6.99, 0.45, 2, 5),
    ('00000000-0000-0000-0000-000000000000', 'UPS', 'UPS', 'GROUND', 'Ground', NULL, 5, 20, 10.99, 0.35, 2, 5),
    ('00000000-0000-0000-0000-000000000000', 'UPS', 'UPS', 'GROUND', 'Ground', NULL, 20, 50, 18.99, 0.25, 2, 5),
    ('00000000-0000-0000-0000-000000000000', 'UPS', 'UPS', 'EXPRESS', 'Express 3-Day', NULL, 0, 5, 12.99, 0.85, 3, 3),
    ('00000000-0000-0000-0000-000000000000', 'UPS', 'UPS', 'EXPRESS', 'Express 3-Day', NULL, 5, 20, 19.99, 0.65, 3, 3),
    ('00000000-0000-0000-0000-000000000000', 'UPS', 'UPS', 'EXPRESS', 'Express 3-Day', NULL, 20, 50, 32.99, 0.50, 3, 3),
    ('00000000-0000-0000-0000-000000000000', 'UPS', 'UPS', 'TWO_DAY', 'Express 2-Day', NULL, 0, 5, 17.99, 1.20, 2, 2),
    ('00000000-0000-0000-0000-000000000000', 'UPS', 'UPS', 'TWO_DAY', 'Express 2-Day', NULL, 5, 20, 28.99, 0.95, 2, 2),
    ('00000000-0000-0000-0000-000000000000', 'UPS', 'UPS', 'TWO_DAY', 'Express 2-Day', NULL, 20, 50, 45.99, 0.75, 2, 2),
    ('00000000-0000-0000-0000-000000000000', 'UPS', 'UPS', 'OVERNIGHT', 'Next Day Air', NULL, 0, 5, 29.99, 2.50, 1, 1),
    ('00000000-0000-0000-0000-000000000000', 'UPS', 'UPS', 'OVERNIGHT', 'Next Day Air', NULL, 5, 20, 45.99, 1.80, 1, 1),
    ('00000000-0000-0000-0000-000000000000', 'UPS', 'UPS', 'OVERNIGHT', 'Next Day Air', NULL, 20, 50, 72.99, 1.20, 1, 1),
    ('00000000-0000-0000-0000-000000000000', 'FEDEX', 'FedEx', 'GROUND', 'Ground', NULL, 0, 5, 7.49, 0.42, 1, 5),
    ('00000000-0000-0000-0000-000000000000', 'FEDEX', 'FedEx', 'GROUND', 'Ground', NULL, 5, 20, 11.49, 0.32, 1, 5),
    ('00000000-0000-0000-0000-000000000000', 'FEDEX', 'FedEx', 'TWO_DAY', 'Express 2-Day', NULL, 0, 5, 18.99, 1.10, 2, 2),
    ('00000000-0000-0000-0000-000000000000', 'FEDEX', 'FedEx', 'TWO_DAY', 'Express 2-Day', NULL, 5, 20, 29.99, 0.85, 2, 2),
    ('00000000-0000-0000-0000-000000000000', 'FEDEX', 'FedEx', 'OVERNIGHT', 'Priority Overnight', NULL, 0, 5, 32.99, 2.75, 1, 1),
    ('00000000-0000-0000-0000-000000000000', 'FEDEX', 'FedEx', 'OVERNIGHT', 'Priority Overnight', NULL, 5, 20, 48.99, 1.90, 1, 1),
    ('00000000-0000-0000-0000-000000000000', 'USPS', 'USPS', 'GROUND', 'Parcel Select Ground', NULL, 0, 5, 5.99, 0.35, 2, 8),
    ('00000000-0000-0000-0000-000000000000', 'USPS', 'USPS', 'GROUND', 'Parcel Select Ground', NULL, 5, 20, 9.99, 0.25, 2, 8),
    ('00000000-0000-0000-0000-000000000000', 'USPS', 'USPS', 'EXPRESS', 'Priority Mail Express', NULL, 0, 5, 26.99, 1.50, 1, 2),
    ('00000000-0000-0000-0000-000000000000', 'USPS', 'USPS', 'EXPRESS', 'Priority Mail Express', NULL, 5, 20, 39.99, 1.20, 1, 2)
ON CONFLICT DO NOTHING;
