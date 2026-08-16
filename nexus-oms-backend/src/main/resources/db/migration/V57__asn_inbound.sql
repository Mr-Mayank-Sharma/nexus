-- ASN (advance ship notice) inbound: headers + lines, and the EDI 856 -> ASN link.

CREATE TABLE IF NOT EXISTS nx_asns (
    id UUID PRIMARY KEY,
    tenant_id UUID NOT NULL,
    asn_number VARCHAR(255) NOT NULL,
    node_id UUID,
    status VARCHAR(50) NOT NULL,
    source VARCHAR(50) NOT NULL,
    supplier_name VARCHAR(255),
    carrier_code VARCHAR(100),
    tracking_number VARCHAR(255),
    purchase_order_number VARCHAR(255),
    ship_date TIMESTAMP,
    expected_arrival_date TIMESTAMP,
    edi_document_id UUID,
    received_by VARCHAR(255),
    received_at TIMESTAMP,
    created_at TIMESTAMP NOT NULL,
    updated_at TIMESTAMP NOT NULL
);

CREATE INDEX IF NOT EXISTS idx_asns_tenant_status ON nx_asns (tenant_id, status);
CREATE UNIQUE INDEX IF NOT EXISTS idx_asns_tenant_number ON nx_asns (tenant_id, asn_number);

CREATE TABLE IF NOT EXISTS nx_asn_lines (
    id UUID PRIMARY KEY,
    asn_id UUID NOT NULL,
    tenant_id UUID NOT NULL,
    sku VARCHAR(255) NOT NULL,
    product_name VARCHAR(255),
    expected_qty INTEGER NOT NULL,
    received_qty INTEGER NOT NULL DEFAULT 0,
    lot_number VARCHAR(255),
    expiry_date TIMESTAMP,
    status VARCHAR(50) NOT NULL,
    created_at TIMESTAMP NOT NULL,
    CONSTRAINT fk_asn_lines_asn FOREIGN KEY (asn_id) REFERENCES nx_asns(id) ON DELETE CASCADE
);

CREATE INDEX IF NOT EXISTS idx_asn_lines_asn ON nx_asn_lines (asn_id);

-- Link EDI 856 documents to the ASN they produced.
ALTER TABLE nx_edi_documents ADD COLUMN IF NOT EXISTS asn_id UUID;
