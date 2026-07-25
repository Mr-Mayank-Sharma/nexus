-- ============================================================
-- Email Order Parsing: PDF/CSV ingestion → order creation
-- V17 migration
-- ============================================================

CREATE TABLE IF NOT EXISTS nx_email_ingestion_config (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    tenant_id UUID NOT NULL UNIQUE,
    protocol VARCHAR(10) DEFAULT 'IMAP' CHECK (protocol IN ('IMAP','POP3','GRAPH_API')),
    host VARCHAR(255),
    port INT DEFAULT 993,
    username VARCHAR(255),
    encrypted_password TEXT,
    use_ssl BOOLEAN DEFAULT true,
    inbox_folder VARCHAR(100) DEFAULT 'INBOX',
    processed_folder VARCHAR(100) DEFAULT 'Processed',
    failed_folder VARCHAR(100) DEFAULT 'Failed',
    polling_interval_sec INT DEFAULT 300,
    allowed_senders TEXT,
    subject_filter VARCHAR(255),
    auto_create_orders BOOLEAN DEFAULT false,
    send_confirmation BOOLEAN DEFAULT true,
    is_active BOOLEAN DEFAULT false,
    last_polled_at TIMESTAMP,
    created_at TIMESTAMP DEFAULT now(),
    updated_at TIMESTAMP DEFAULT now()
);

CREATE TABLE IF NOT EXISTS nx_email_parsed_orders (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    tenant_id UUID NOT NULL,
    email_message_id VARCHAR(255),
    email_subject VARCHAR(512),
    email_from VARCHAR(255),
    email_to VARCHAR(255),
    email_received_at TIMESTAMP,
    attachment_filename VARCHAR(255),
    attachment_type VARCHAR(30) CHECK (attachment_type IN ('PDF','CSV','HTML','TEXT','NONE')),
    raw_body TEXT,
    parsed_data JSONB,
    order_id UUID REFERENCES nx_orders(id) ON DELETE SET NULL,
    status VARCHAR(30) DEFAULT 'NEW' CHECK (status IN ('NEW','PARSED','PENDING_REVIEW','APPROVED','REJECTED','DUPLICATE','FAILED')),
    confidence_score DECIMAL(5,4) DEFAULT 0.0,
    customer_name VARCHAR(255),
    customer_email VARCHAR(255),
    customer_phone VARCHAR(50),
    order_total DECIMAL(12,2),
    item_count INT DEFAULT 0,
    shipping_address JSONB,
    rejection_reason TEXT,
    matched_customer_id UUID,
    created_at TIMESTAMP DEFAULT now(),
    processed_at TIMESTAMP
);
CREATE INDEX idx_nx_email_parsed_orders_tenant ON nx_email_parsed_orders(tenant_id);
CREATE INDEX idx_nx_email_parsed_orders_status ON nx_email_parsed_orders(status);
CREATE INDEX idx_nx_email_parsed_orders_message ON nx_email_parsed_orders(email_message_id);
