-- Carrier master data and KPI tracking

CREATE TABLE IF NOT EXISTS nx_carriers (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    tenant_id UUID NOT NULL,
    name VARCHAR(255) NOT NULL,
    code VARCHAR(50) NOT NULL,
    type VARCHAR(50) NOT NULL DEFAULT 'SHIPPING',
    status VARCHAR(20) NOT NULL DEFAULT 'ACTIVE',
    account_number VARCHAR(100),
    api_key_encrypted TEXT,
    api_secret_encrypted TEXT,
    otd_rate NUMERIC(5,2) DEFAULT 0,
    avg_cost NUMERIC(10,2) DEFAULT 0,
    total_shipments BIGINT DEFAULT 0,
    damage_rate NUMERIC(5,2) DEFAULT 0,
    metadata JSONB,
    is_active BOOLEAN DEFAULT true,
    created_at TIMESTAMP DEFAULT now(),
    updated_at TIMESTAMP DEFAULT now()
);

CREATE INDEX idx_nx_carriers_tenant ON nx_carriers(tenant_id);
CREATE INDEX idx_nx_carriers_code ON nx_carriers(code);
CREATE INDEX idx_nx_carriers_status ON nx_carriers(status);
