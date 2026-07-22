-- ============================================================
-- EDI Automation: 850 PO / 856 ASN / 810 Invoice processing
-- V16 migration
-- ============================================================

CREATE TABLE IF NOT EXISTS nx_edi_documents (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    tenant_id UUID NOT NULL,
    doc_type VARCHAR(10) NOT NULL CHECK (doc_type IN ('850','856','810')),
    filename VARCHAR(512),
    raw_content TEXT,
    parsed_status VARCHAR(30) DEFAULT 'PENDING' CHECK (parsed_status IN ('PENDING','PARSED','FAILED','VALIDATED','ERROR')),
    parsed_data JSONB,
    validation_errors JSONB,
    order_id UUID REFERENCES nx_orders(id) ON DELETE SET NULL,
    shipment_id UUID,
    invoice_id UUID,
    partner_id VARCHAR(100),
    partner_name VARCHAR(255),
    control_number VARCHAR(50),
    interchange_control_number VARCHAR(50),
    group_control_number VARCHAR(50),
    test_indicator BOOLEAN DEFAULT false,
    processed_at TIMESTAMP,
    error_message TEXT,
    created_at TIMESTAMP DEFAULT now(),
    updated_at TIMESTAMP DEFAULT now()
);
CREATE INDEX idx_nx_edi_documents_tenant ON nx_edi_documents(tenant_id);
CREATE INDEX idx_nx_edi_documents_type ON nx_edi_documents(doc_type);
CREATE INDEX idx_nx_edi_documents_status ON nx_edi_documents(parsed_status);
CREATE INDEX idx_nx_edi_documents_order ON nx_edi_documents(order_id);

CREATE TABLE IF NOT EXISTS nx_edi_partners (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    tenant_id UUID NOT NULL,
    partner_code VARCHAR(50) NOT NULL,
    partner_name VARCHAR(255) NOT NULL,
    qualifier VARCHAR(10) DEFAULT 'ZZ',
    interchange_id VARCHAR(100),
    is_active BOOLEAN DEFAULT true,
    supported_docs JSONB,
    created_at TIMESTAMP DEFAULT now(),
    updated_at TIMESTAMP DEFAULT now()
);
CREATE INDEX idx_nx_edi_partners_tenant ON nx_edi_partners(tenant_id);
CREATE UNIQUE INDEX idx_nx_edi_partners_code_tenant ON nx_edi_partners(tenant_id, partner_code);
