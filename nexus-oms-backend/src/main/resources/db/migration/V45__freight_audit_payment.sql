-- V45: Freight Audit & Payment
-- Manhattan parity: freight invoice matching, audit trails, cost reconciliation

CREATE TABLE IF NOT EXISTS nxFreight_invoices (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    tenant_id UUID NOT NULL,
    warehouse_id UUID,
    invoice_number VARCHAR(50) NOT NULL,
    carrier_code VARCHAR(30) NOT NULL,
    carrier_name VARCHAR(100),
    invoice_date DATE NOT NULL,
    period_start DATE,
    period_end DATE,
    total_amount DECIMAL(12,2) NOT NULL,
    total_tax DECIMAL(12,2) DEFAULT 0,
    total_discount DECIMAL(12,2) DEFAULT 0,
    total_net DECIMAL(12,2) NOT NULL,
    currency VARCHAR(3) DEFAULT 'USD',
    status VARCHAR(20) NOT NULL DEFAULT 'RECEIVED', -- RECEIVED, AUDITING, MATCHED, DISPUTED, APPROVED, PAID, REJECTED
    matched_percentage DECIMAL(5,2) DEFAULT 0,
    dispute_reason TEXT,
    approved_by VARCHAR(100),
    paid_at TIMESTAMP,
    file_reference VARCHAR(200),
    notes TEXT,
    created_at TIMESTAMP NOT NULL DEFAULT NOW(),
    updated_at TIMESTAMP NOT NULL DEFAULT NOW(),
    UNIQUE(tenant_id, invoice_number)
);

CREATE TABLE IF NOT EXISTS nxFreight_invoice_lines (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    tenant_id UUID NOT NULL,
    invoice_id UUID NOT NULL,
    line_number INT NOT NULL,
    shipment_id UUID,
    tracking_number VARCHAR(50),
    service_level VARCHAR(50),
    weight_kg DECIMAL(10,2),
    dimensions_json TEXT,
    origin_zip VARCHAR(20),
    destination_zip VARCHAR(20),
    billed_amount DECIMAL(12,2) NOT NULL,
    expected_amount DECIMAL(12,2),
    variance_amount DECIMAL(12,2) DEFAULT 0,
    status VARCHAR(20) NOT NULL DEFAULT 'PENDING', -- PENDING, MATCHED, DISPUTED, RESOLVED
    dispute_notes TEXT,
    created_at TIMESTAMP NOT NULL DEFAULT NOW()
);

CREATE TABLE IF NOT EXISTS nxFreight_audit_logs (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    tenant_id UUID NOT NULL,
    invoice_id UUID NOT NULL,
    action VARCHAR(30) NOT NULL, -- RECEIVED, VALIDATED, MATCHED, DISPUTED, APPROVED, PAID, REJECTED
    performed_by VARCHAR(100),
    from_status VARCHAR(20),
    to_status VARCHAR(20),
    notes TEXT,
    created_at TIMESTAMP NOT NULL DEFAULT NOW()
);

CREATE INDEX IF NOT EXISTS idx_freight_invoices_status ON nxFreight_invoices(tenant_id, status);
CREATE INDEX IF NOT EXISTS idx_freight_invoice_lines_invoice ON nxFreight_invoice_lines(invoice_id);
CREATE INDEX IF NOT EXISTS idx_freight_audit_logs_invoice ON nxFreight_audit_logs(invoice_id);
