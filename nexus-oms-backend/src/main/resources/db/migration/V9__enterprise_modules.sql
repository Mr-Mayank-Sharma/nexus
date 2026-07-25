-- ============================================================
-- Enterprise OMS — All Modules
-- Warehouse, Procurement, Invoicing, Notifications,
-- Workflow Builder, Document Management, RBAC
-- ============================================================

-- 1. WAREHOUSE MANAGEMENT
CREATE TABLE IF NOT EXISTS nx_warehouses (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    tenant_id UUID NOT NULL,
    code VARCHAR(50) NOT NULL,
    name VARCHAR(255) NOT NULL,
    type VARCHAR(30) DEFAULT 'WAREHOUSE',
    status VARCHAR(20) DEFAULT 'ACTIVE',
    address_line1 VARCHAR(255),
    address_line2 VARCHAR(255),
    city VARCHAR(100),
    state VARCHAR(100),
    zip_code VARCHAR(20),
    country VARCHAR(100),
    latitude DECIMAL(10,7),
    longitude DECIMAL(10,7),
    total_capacity_sqm DECIMAL(12,2),
    used_capacity_sqm DECIMAL(12,2) DEFAULT 0,
    total_capacity_cbm DECIMAL(12,2),
    used_capacity_cbm DECIMAL(12,2) DEFAULT 0,
    dock_count INT DEFAULT 0,
    is_active BOOLEAN DEFAULT true,
    operating_hours JSONB,
    contact_name VARCHAR(255),
    contact_phone VARCHAR(50),
    contact_email VARCHAR(255),
    metadata JSONB,
    created_at TIMESTAMP NOT NULL DEFAULT now(),
    updated_at TIMESTAMP NOT NULL DEFAULT now(),
    UNIQUE(tenant_id, code)
);

CREATE TABLE IF NOT EXISTS nx_warehouse_zones (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    tenant_id UUID NOT NULL,
    warehouse_id UUID NOT NULL REFERENCES nx_warehouses(id) ON DELETE CASCADE,
    code VARCHAR(50) NOT NULL,
    name VARCHAR(255) NOT NULL,
    zone_type VARCHAR(30) NOT NULL DEFAULT 'STORAGE',
    zone_category VARCHAR(30) DEFAULT 'DRY',
    temperature_min DECIMAL(6,2),
    temperature_max DECIMAL(6,2),
    humidity_min DECIMAL(5,2),
    humidity_max DECIMAL(5,2),
    capacity_sqm DECIMAL(12,2),
    used_sqm DECIMAL(12,2) DEFAULT 0,
    is_active BOOLEAN DEFAULT true,
    is_hazardous BOOLEAN DEFAULT false,
    security_level VARCHAR(20) DEFAULT 'STANDARD',
    metadata JSONB,
    created_at TIMESTAMP NOT NULL DEFAULT now(),
    updated_at TIMESTAMP NOT NULL DEFAULT now(),
    UNIQUE(warehouse_id, code)
);

CREATE TABLE IF NOT EXISTS nx_warehouse_bins (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    tenant_id UUID NOT NULL,
    warehouse_id UUID NOT NULL REFERENCES nx_warehouses(id) ON DELETE CASCADE,
    zone_id UUID REFERENCES nx_warehouse_zones(id) ON DELETE SET NULL,
    code VARCHAR(50) NOT NULL,
    bin_type VARCHAR(30) DEFAULT 'RACK',
    bin_class VARCHAR(20) DEFAULT 'A',
    max_weight_kg DECIMAL(10,2),
    max_volume_cbm DECIMAL(10,2),
    current_weight_kg DECIMAL(10,2) DEFAULT 0,
    current_volume_cbm DECIMAL(10,2) DEFAULT 0,
    is_empty BOOLEAN DEFAULT true,
    is_active BOOLEAN DEFAULT true,
    is_reserved BOOLEAN DEFAULT false,
    last_picked_at TIMESTAMP,
    last_counted_at TIMESTAMP,
    x_coord INT,
    y_coord INT,
    z_coord INT,
    metadata JSONB,
    created_at TIMESTAMP NOT NULL DEFAULT now(),
    updated_at TIMESTAMP NOT NULL DEFAULT now(),
    UNIQUE(warehouse_id, code)
);

CREATE TABLE IF NOT EXISTS nx_warehouse_staff (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    tenant_id UUID NOT NULL,
    warehouse_id UUID NOT NULL REFERENCES nx_warehouses(id) ON DELETE CASCADE,
    user_id UUID,
    employee_code VARCHAR(50) NOT NULL,
    first_name VARCHAR(100) NOT NULL,
    last_name VARCHAR(100) NOT NULL,
    role VARCHAR(50) DEFAULT 'PICKER',
    shift VARCHAR(20) DEFAULT 'DAY',
    skills JSONB,
    productivity_score DECIMAL(5,2) DEFAULT 0,
    items_picked_today INT DEFAULT 0,
    items_packed_today INT DEFAULT 0,
    is_active BOOLEAN DEFAULT true,
    cert_expires_at TIMESTAMP,
    created_at TIMESTAMP NOT NULL DEFAULT now(),
    updated_at TIMESTAMP NOT NULL DEFAULT now(),
    UNIQUE(warehouse_id, employee_code)
);

CREATE TABLE IF NOT EXISTS nx_warehouse_equipment (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    tenant_id UUID NOT NULL,
    warehouse_id UUID NOT NULL REFERENCES nx_warehouses(id) ON DELETE CASCADE,
    code VARCHAR(50) NOT NULL,
    equipment_type VARCHAR(50) NOT NULL,
    model VARCHAR(100),
    status VARCHAR(20) DEFAULT 'AVAILABLE',
    battery_level INT DEFAULT 100,
    last_maintenance_at TIMESTAMP,
    next_maintenance_at TIMESTAMP,
    assigned_to UUID,
    is_active BOOLEAN DEFAULT true,
    created_at TIMESTAMP NOT NULL DEFAULT now(),
    updated_at TIMESTAMP NOT NULL DEFAULT now()
);

-- 2. PROCUREMENT & SUPPLIERS
CREATE TABLE IF NOT EXISTS nx_suppliers (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    tenant_id UUID NOT NULL,
    supplier_code VARCHAR(50) NOT NULL,
    company_name VARCHAR(255) NOT NULL,
    trading_name VARCHAR(255),
    tax_id VARCHAR(50),
    registration_number VARCHAR(50),
    supplier_type VARCHAR(30) DEFAULT 'MANUFACTURER',
    status VARCHAR(20) DEFAULT 'ACTIVE',
    payment_terms VARCHAR(50) DEFAULT 'NET30',
    currency VARCHAR(3) DEFAULT 'USD',
    credit_limit DECIMAL(15,2),
    rating INT DEFAULT 0,
    address_line1 VARCHAR(255),
    address_line2 VARCHAR(255),
    city VARCHAR(100),
    state VARCHAR(100),
    zip_code VARCHAR(20),
    country VARCHAR(100),
    phone VARCHAR(50),
    email VARCHAR(255),
    website VARCHAR(255),
    is_active BOOLEAN DEFAULT true,
    metadata JSONB,
    created_at TIMESTAMP NOT NULL DEFAULT now(),
    updated_at TIMESTAMP NOT NULL DEFAULT now(),
    UNIQUE(tenant_id, supplier_code)
);

CREATE TABLE IF NOT EXISTS nx_supplier_contacts (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    supplier_id UUID NOT NULL REFERENCES nx_suppliers(id) ON DELETE CASCADE,
    first_name VARCHAR(100) NOT NULL,
    last_name VARCHAR(100) NOT NULL,
    job_title VARCHAR(100),
    email VARCHAR(255),
    phone VARCHAR(50),
    mobile VARCHAR(50),
    is_primary BOOLEAN DEFAULT false,
    department VARCHAR(100),
    notes TEXT,
    created_at TIMESTAMP NOT NULL DEFAULT now()
);

CREATE TABLE IF NOT EXISTS nx_supplier_contracts (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    tenant_id UUID NOT NULL,
    supplier_id UUID NOT NULL REFERENCES nx_suppliers(id) ON DELETE CASCADE,
    contract_number VARCHAR(50) NOT NULL,
    contract_type VARCHAR(30) DEFAULT 'MASTER',
    start_date DATE NOT NULL,
    end_date DATE,
    terms TEXT,
    pricing_terms JSONB,
    discount_percent DECIMAL(5,2) DEFAULT 0,
    status VARCHAR(20) DEFAULT 'ACTIVE',
    auto_renew BOOLEAN DEFAULT false,
    file_url VARCHAR(512),
    created_at TIMESTAMP NOT NULL DEFAULT now(),
    updated_at TIMESTAMP NOT NULL DEFAULT now(),
    UNIQUE(supplier_id, contract_number)
);

CREATE TABLE IF NOT EXISTS nx_purchase_requests (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    tenant_id UUID NOT NULL,
    request_number VARCHAR(50) NOT NULL,
    title VARCHAR(255) NOT NULL,
    description TEXT,
    status VARCHAR(30) DEFAULT 'DRAFT',
    priority VARCHAR(20) DEFAULT 'MEDIUM',
    requested_by UUID,
    approved_by UUID,
    approved_at TIMESTAMP,
    notes TEXT,
    created_at TIMESTAMP NOT NULL DEFAULT now(),
    updated_at TIMESTAMP NOT NULL DEFAULT now(),
    UNIQUE(tenant_id, request_number)
);

CREATE TABLE IF NOT EXISTS nx_purchase_request_items (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    request_id UUID NOT NULL REFERENCES nx_purchase_requests(id) ON DELETE CASCADE,
    sku VARCHAR(100),
    product_name VARCHAR(255) NOT NULL,
    quantity INT NOT NULL,
    unit VARCHAR(20) DEFAULT 'EA',
    estimated_unit_price DECIMAL(15,4),
    requested_date DATE,
    notes TEXT,
    created_at TIMESTAMP NOT NULL DEFAULT now()
);

CREATE TABLE IF NOT EXISTS nx_rfqs (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    tenant_id UUID NOT NULL,
    rfq_number VARCHAR(50) NOT NULL,
    title VARCHAR(255) NOT NULL,
    description TEXT,
    status VARCHAR(30) DEFAULT 'DRAFT',
    request_id UUID REFERENCES nx_purchase_requests(id),
    due_date DATE,
    supplier_ids JSONB,
    terms TEXT,
    currency VARCHAR(3) DEFAULT 'USD',
    created_by UUID,
    approved_by UUID,
    created_at TIMESTAMP NOT NULL DEFAULT now(),
    updated_at TIMESTAMP NOT NULL DEFAULT now(),
    UNIQUE(tenant_id, rfq_number)
);

CREATE TABLE IF NOT EXISTS nx_rfq_responses (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    rfq_id UUID NOT NULL REFERENCES nx_rfqs(id) ON DELETE CASCADE,
    supplier_id UUID NOT NULL REFERENCES nx_suppliers(id),
    total_amount DECIMAL(15,2),
    currency VARCHAR(3) DEFAULT 'USD',
    delivery_days INT,
    valid_until DATE,
    notes TEXT,
    status VARCHAR(20) DEFAULT 'PENDING',
    submitted_at TIMESTAMP NOT NULL DEFAULT now()
);

CREATE TABLE IF NOT EXISTS nx_purchase_orders (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    tenant_id UUID NOT NULL,
    po_number VARCHAR(50) NOT NULL,
    supplier_id UUID NOT NULL REFERENCES nx_suppliers(id),
    rfq_id UUID REFERENCES nx_rfqs(id),
    status VARCHAR(30) DEFAULT 'DRAFT',
    order_date DATE NOT NULL DEFAULT CURRENT_DATE,
    expected_delivery_date DATE,
    delivered_date DATE,
    shipping_method VARCHAR(100),
    payment_terms VARCHAR(50),
    currency VARCHAR(3) DEFAULT 'USD',
    subtotal DECIMAL(15,2) DEFAULT 0,
    tax_amount DECIMAL(15,2) DEFAULT 0,
    shipping_cost DECIMAL(15,2) DEFAULT 0,
    total_amount DECIMAL(15,2) DEFAULT 0,
    notes TEXT,
    terms TEXT,
    created_by UUID,
    approved_by UUID,
    approved_at TIMESTAMP,
    is_fully_received BOOLEAN DEFAULT false,
    metadata JSONB,
    created_at TIMESTAMP NOT NULL DEFAULT now(),
    updated_at TIMESTAMP NOT NULL DEFAULT now(),
    UNIQUE(tenant_id, po_number)
);

CREATE TABLE IF NOT EXISTS nx_purchase_order_items (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    po_id UUID NOT NULL REFERENCES nx_purchase_orders(id) ON DELETE CASCADE,
    sku VARCHAR(100),
    product_name VARCHAR(255) NOT NULL,
    quantity_ordered INT NOT NULL,
    quantity_received INT DEFAULT 0,
    quantity_cancelled INT DEFAULT 0,
    unit_price DECIMAL(15,4) NOT NULL,
    total_price DECIMAL(15,2) NOT NULL,
    tax_rate DECIMAL(5,2) DEFAULT 0,
    discount_percent DECIMAL(5,2) DEFAULT 0,
    unit VARCHAR(20) DEFAULT 'EA',
    expected_date DATE,
    notes TEXT,
    created_at TIMESTAMP NOT NULL DEFAULT now()
);

-- 3. INVOICING & PAYMENTS
CREATE TABLE IF NOT EXISTS nx_invoices (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    tenant_id UUID NOT NULL,
    invoice_number VARCHAR(50) NOT NULL,
    invoice_type VARCHAR(20) DEFAULT 'SALES',
    order_id UUID,
    customer_id UUID,
    supplier_id UUID REFERENCES nx_suppliers(id),
    status VARCHAR(30) DEFAULT 'DRAFT',
    invoice_date DATE NOT NULL DEFAULT CURRENT_DATE,
    due_date DATE,
    paid_date DATE,
    currency VARCHAR(3) DEFAULT 'USD',
    subtotal DECIMAL(15,2) DEFAULT 0,
    tax_amount DECIMAL(15,2) DEFAULT 0,
    discount_amount DECIMAL(15,2) DEFAULT 0,
    shipping_cost DECIMAL(15,2) DEFAULT 0,
    total_amount DECIMAL(15,2) DEFAULT 0,
    amount_paid DECIMAL(15,2) DEFAULT 0,
    balance_due DECIMAL(15,2) DEFAULT 0,
    payment_terms VARCHAR(50),
    notes TEXT,
    billing_address JSONB,
    shipping_address JSONB,
    metadata JSONB,
    created_at TIMESTAMP NOT NULL DEFAULT now(),
    updated_at TIMESTAMP NOT NULL DEFAULT now(),
    UNIQUE(tenant_id, invoice_number)
);

CREATE TABLE IF NOT EXISTS nx_invoice_items (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    invoice_id UUID NOT NULL REFERENCES nx_invoices(id) ON DELETE CASCADE,
    sku VARCHAR(100),
    description VARCHAR(255) NOT NULL,
    quantity INT NOT NULL,
    unit_price DECIMAL(15,4) NOT NULL,
    total_price DECIMAL(15,2) NOT NULL,
    tax_rate DECIMAL(5,2) DEFAULT 0,
    discount_percent DECIMAL(5,2) DEFAULT 0,
    created_at TIMESTAMP NOT NULL DEFAULT now()
);

CREATE TABLE IF NOT EXISTS nx_payments (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    tenant_id UUID NOT NULL,
    payment_number VARCHAR(50) NOT NULL,
    payment_type VARCHAR(30) NOT NULL DEFAULT 'INCOMING',
    invoice_id UUID REFERENCES nx_invoices(id),
    customer_id UUID,
    supplier_id UUID REFERENCES nx_suppliers(id),
    amount DECIMAL(15,2) NOT NULL,
    currency VARCHAR(3) DEFAULT 'USD',
    payment_method VARCHAR(50) NOT NULL,
    payment_reference VARCHAR(255),
    status VARCHAR(20) DEFAULT 'PENDING',
    transaction_id VARCHAR(255),
    gateway_response JSONB,
    paid_at TIMESTAMP,
    notes TEXT,
    created_at TIMESTAMP NOT NULL DEFAULT now(),
    updated_at TIMESTAMP NOT NULL DEFAULT now(),
    UNIQUE(tenant_id, payment_number)
);

CREATE TABLE IF NOT EXISTS nx_credit_memos (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    tenant_id UUID NOT NULL,
    memo_number VARCHAR(50) NOT NULL,
    invoice_id UUID REFERENCES nx_invoices(id),
    order_id UUID,
    customer_id UUID,
    supplier_id UUID REFERENCES nx_suppliers(id),
    memo_type VARCHAR(20) DEFAULT 'REFUND',
    reason VARCHAR(255),
    amount DECIMAL(15,2) NOT NULL,
    currency VARCHAR(3) DEFAULT 'USD',
    status VARCHAR(20) DEFAULT 'DRAFT',
    notes TEXT,
    created_at TIMESTAMP NOT NULL DEFAULT now(),
    updated_at TIMESTAMP NOT NULL DEFAULT now(),
    UNIQUE(tenant_id, memo_number)
);

-- 4. NOTIFICATIONS
CREATE TABLE IF NOT EXISTS nx_notification_templates (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    tenant_id UUID NOT NULL,
    template_code VARCHAR(50) NOT NULL,
    name VARCHAR(255) NOT NULL,
    channel VARCHAR(20) NOT NULL DEFAULT 'EMAIL',
    subject VARCHAR(255),
    body TEXT NOT NULL,
    variables JSONB,
    is_active BOOLEAN DEFAULT true,
    created_at TIMESTAMP NOT NULL DEFAULT now(),
    updated_at TIMESTAMP NOT NULL DEFAULT now(),
    UNIQUE(tenant_id, template_code, channel)
);

CREATE TABLE IF NOT EXISTS nx_notification_logs (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    tenant_id UUID NOT NULL,
    channel VARCHAR(20) NOT NULL,
    recipient VARCHAR(255) NOT NULL,
    subject VARCHAR(255),
    body TEXT,
    status VARCHAR(20) DEFAULT 'PENDING',
    reference_type VARCHAR(50),
    reference_id UUID,
    error_message TEXT,
    sent_at TIMESTAMP,
    read_at TIMESTAMP,
    created_at TIMESTAMP NOT NULL DEFAULT now()
);

CREATE INDEX idx_notif_logs_tenant ON nx_notification_logs(tenant_id);
CREATE INDEX idx_notif_logs_status ON nx_notification_logs(status);
CREATE INDEX idx_notif_logs_reference ON nx_notification_logs(reference_type, reference_id);

CREATE TABLE IF NOT EXISTS nx_alert_rules (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    tenant_id UUID NOT NULL,
    name VARCHAR(255) NOT NULL,
    description TEXT,
    event_type VARCHAR(100) NOT NULL,
    condition_expression TEXT,
    severity VARCHAR(20) DEFAULT 'WARNING',
    channel VARCHAR(20) DEFAULT 'EMAIL',
    recipient_list JSONB,
    is_active BOOLEAN DEFAULT true,
    throttle_minutes INT DEFAULT 0,
    created_at TIMESTAMP NOT NULL DEFAULT now(),
    updated_at TIMESTAMP NOT NULL DEFAULT now()
);

-- 5. WORKFLOW BUILDER
CREATE TABLE IF NOT EXISTS nx_workflows (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    tenant_id UUID NOT NULL,
    name VARCHAR(255) NOT NULL,
    description TEXT,
    category VARCHAR(50) NOT NULL DEFAULT 'ORDER',
    trigger_type VARCHAR(50) NOT NULL,
    trigger_config JSONB,
    status VARCHAR(20) DEFAULT 'DRAFT',
    is_active BOOLEAN DEFAULT false,
    version INT DEFAULT 1,
    created_by UUID,
    created_at TIMESTAMP NOT NULL DEFAULT now(),
    updated_at TIMESTAMP NOT NULL DEFAULT now()
);

CREATE TABLE IF NOT EXISTS nx_workflow_steps (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    workflow_id UUID NOT NULL REFERENCES nx_workflows(id) ON DELETE CASCADE,
    step_order INT NOT NULL,
    step_type VARCHAR(50) NOT NULL,
    step_name VARCHAR(255) NOT NULL,
    config JSONB NOT NULL,
    condition_expression TEXT,
    on_failure VARCHAR(30) DEFAULT 'STOP',
    timeout_seconds INT DEFAULT 300,
    created_at TIMESTAMP NOT NULL DEFAULT now()
);

CREATE TABLE IF NOT EXISTS nx_workflow_executions (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    tenant_id UUID NOT NULL,
    workflow_id UUID NOT NULL REFERENCES nx_workflows(id),
    trigger_entity_type VARCHAR(50),
    trigger_entity_id UUID,
    status VARCHAR(20) DEFAULT 'RUNNING',
    current_step INT DEFAULT 0,
    total_steps INT DEFAULT 0,
    input_data JSONB,
    output_data JSONB,
    error_message TEXT,
    started_at TIMESTAMP NOT NULL DEFAULT now(),
    completed_at TIMESTAMP,
    duration_ms BIGINT,
    created_at TIMESTAMP NOT NULL DEFAULT now()
);

CREATE INDEX idx_wf_exec_tenant ON nx_workflow_executions(tenant_id);
CREATE INDEX idx_wf_exec_status ON nx_workflow_executions(status);

-- 6. DOCUMENT MANAGEMENT
CREATE TABLE IF NOT EXISTS nx_documents (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    tenant_id UUID NOT NULL,
    document_number VARCHAR(50),
    title VARCHAR(255) NOT NULL,
    description TEXT,
    document_type VARCHAR(50) NOT NULL,
    category VARCHAR(50),
    file_name VARCHAR(255) NOT NULL,
    file_size BIGINT,
    mime_type VARCHAR(100),
    file_url VARCHAR(512),
    storage_path VARCHAR(512),
    file_hash VARCHAR(64),
    current_version INT DEFAULT 1,
    entity_type VARCHAR(50),
    entity_id UUID,
    is_public BOOLEAN DEFAULT false,
    tags JSONB,
    metadata JSONB,
    uploaded_by UUID,
    created_at TIMESTAMP NOT NULL DEFAULT now(),
    updated_at TIMESTAMP NOT NULL DEFAULT now()
);

CREATE INDEX idx_docs_tenant ON nx_documents(tenant_id);
CREATE INDEX idx_docs_entity ON nx_documents(entity_type, entity_id);
CREATE INDEX idx_docs_type ON nx_documents(document_type);

CREATE TABLE IF NOT EXISTS nx_document_versions (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    document_id UUID NOT NULL REFERENCES nx_documents(id) ON DELETE CASCADE,
    version_number INT NOT NULL,
    file_name VARCHAR(255) NOT NULL,
    file_size BIGINT,
    file_url VARCHAR(512),
    storage_path VARCHAR(512),
    file_hash VARCHAR(64),
    change_notes TEXT,
    uploaded_by UUID,
    created_at TIMESTAMP NOT NULL DEFAULT now(),
    UNIQUE(document_id, version_number)
);

-- 7. ENHANCED RBAC
CREATE TABLE IF NOT EXISTS nx_role_permissions (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    tenant_id UUID NOT NULL,
    role VARCHAR(50) NOT NULL,
    permission_group VARCHAR(100) NOT NULL,
    permission_name VARCHAR(100) NOT NULL,
    can_view BOOLEAN DEFAULT false,
    can_create BOOLEAN DEFAULT false,
    can_edit BOOLEAN DEFAULT false,
    can_delete BOOLEAN DEFAULT false,
    can_approve BOOLEAN DEFAULT false,
    created_at TIMESTAMP NOT NULL DEFAULT now(),
    UNIQUE(tenant_id, role, permission_group, permission_name)
);

CREATE TABLE IF NOT EXISTS nx_user_roles (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    tenant_id UUID NOT NULL,
    user_id UUID NOT NULL,
    role VARCHAR(50) NOT NULL,
    team VARCHAR(100),
    department VARCHAR(100),
    is_active BOOLEAN DEFAULT true,
    assigned_by UUID,
    assigned_at TIMESTAMP NOT NULL DEFAULT now(),
    UNIQUE(tenant_id, user_id, role)
);

CREATE TABLE IF NOT EXISTS nx_teams (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    tenant_id UUID NOT NULL,
    name VARCHAR(100) NOT NULL,
    description TEXT,
    manager_id UUID,
    is_active BOOLEAN DEFAULT true,
    created_at TIMESTAMP NOT NULL DEFAULT now(),
    UNIQUE(tenant_id, name)
);

-- 8. COMPANY SETTINGS
CREATE TABLE IF NOT EXISTS nx_company_settings (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    tenant_id UUID NOT NULL UNIQUE,
    company_name VARCHAR(255),
    company_logo VARCHAR(512),
    tax_id VARCHAR(50),
    registration_number VARCHAR(50),
    default_currency VARCHAR(3) DEFAULT 'USD',
    default_language VARCHAR(10) DEFAULT 'en',
    default_timezone VARCHAR(50) DEFAULT 'UTC',
    date_format VARCHAR(20) DEFAULT 'MM/DD/YYYY',
    time_format VARCHAR(10) DEFAULT '24h',
    fiscal_year_start VARCHAR(5) DEFAULT '01-01',
    countries JSONB,
    regions JSONB,
    holidays JSONB,
    feature_flags JSONB,
    security_policy JSONB,
    backup_config JSONB,
    created_at TIMESTAMP NOT NULL DEFAULT now(),
    updated_at TIMESTAMP NOT NULL DEFAULT now()
);
