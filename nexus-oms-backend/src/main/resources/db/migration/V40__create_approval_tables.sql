-- V40: Create Order Approval Engine tables

CREATE TABLE nx_approval_rules (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    tenant_id UUID NOT NULL,
    name VARCHAR(255),
    rule_type VARCHAR(50) NOT NULL,
    action VARCHAR(50) NOT NULL,
    threshold_value DECIMAL(12, 2),
    threshold_string VARCHAR(255),
    priority INTEGER NOT NULL DEFAULT 10,
    active BOOLEAN NOT NULL DEFAULT TRUE,
    created_at TIMESTAMP NOT NULL DEFAULT NOW(),
    updated_at TIMESTAMP NOT NULL DEFAULT NOW()
);

CREATE INDEX idx_approval_rule_tenant ON nx_approval_rules(tenant_id);

CREATE TABLE nx_order_approvals (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    tenant_id UUID NOT NULL,
    order_id UUID NOT NULL,
    order_number VARCHAR(50) NOT NULL,
    order_total DECIMAL(12, 2),
    customer_id UUID,
    risk_score DECIMAL(5, 2),
    status VARCHAR(50) NOT NULL DEFAULT 'PENDING',
    matched_rules TEXT,
    reviewed_by VARCHAR(255),
    review_notes TEXT,
    decided_at TIMESTAMP,
    created_at TIMESTAMP NOT NULL DEFAULT NOW(),
    updated_at TIMESTAMP NOT NULL DEFAULT NOW()
);

CREATE INDEX idx_approval_tenant ON nx_order_approvals(tenant_id);
CREATE INDEX idx_approval_order ON nx_order_approvals(order_id);
CREATE INDEX idx_approval_status ON nx_order_approvals(status);
CREATE INDEX idx_approval_customer ON nx_order_approvals(customer_id);

-- Seed default approval rules
INSERT INTO nx_approval_rules (tenant_id, name, rule_type, action, threshold_value, priority)
VALUES
    ('00000000-0000-0000-0000-000000000001', 'High Value Order', 'AMOUNT_THRESHOLD', 'HOLD_FOR_REVIEW', 500.00, 1),
    ('00000000-0000-0000-0000-000000000001', 'Very High Value Order', 'AMOUNT_THRESHOLD', 'REJECT', 5000.00, 2),
    ('00000000-0000-0000-0000-000000000001', 'First Time Customer', 'REPEAT_CUSTOMER', 'HOLD_FOR_REVIEW', NULL, 3);
