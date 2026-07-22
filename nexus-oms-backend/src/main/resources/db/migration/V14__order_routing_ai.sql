-- ============================================================
-- AI Order Routing: Allocations & Fulfillment Exceptions
-- V14 migration
-- ============================================================

-- 1. ORDER ALLOCATIONS (per-order fulfillment node assignments)
CREATE TABLE IF NOT EXISTS nx_order_allocations (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    order_id UUID NOT NULL REFERENCES nx_orders(id) ON DELETE CASCADE,
    tenant_id UUID NOT NULL,
    node_id UUID,
    node_name VARCHAR(255),
    node_type VARCHAR(50) DEFAULT 'WAREHOUSE' CHECK (node_type IN ('WAREHOUSE','DC','CROSS_DOCK','DROP_SHIP','STORE')),
    priority INT DEFAULT 0,
    quantity_allocated INT DEFAULT 0,
    quantity_requested INT DEFAULT 0,
    status VARCHAR(30) DEFAULT 'PENDING' CHECK (status IN ('PENDING','ALLOCATED','PARTIALLY_ALLOCATED','FAILED','CANCELLED')),
    delivery_promise_date TIMESTAMP,
    delivery_promise_confidence DECIMAL(5,4) DEFAULT 0.0,
    allocation_strategy VARCHAR(30) DEFAULT 'RULE_BASED' CHECK (allocation_strategy IN ('RULE_BASED','AI_OPTIMIZED','HYBRID','MANUAL')),
    rule_id UUID,
    rule_name VARCHAR(255),
    cost_estimated DECIMAL(12,2) DEFAULT 0.00,
    distance_km DECIMAL(10,2),
    metadata JSONB,
    allocated_at TIMESTAMP DEFAULT now(),
    allocated_by UUID,
    created_at TIMESTAMP DEFAULT now(),
    updated_at TIMESTAMP DEFAULT now()
);
CREATE INDEX idx_nx_order_allocations_order ON nx_order_allocations(order_id);
CREATE INDEX idx_nx_order_allocations_tenant ON nx_order_allocations(tenant_id);
CREATE INDEX idx_nx_order_allocations_status ON nx_order_allocations(status);
CREATE INDEX idx_nx_order_allocations_node ON nx_order_allocations(node_id);

-- 2. FULFILLMENT EXCEPTIONS (issues detected during routing/fulfillment)
CREATE TABLE IF NOT EXISTS nx_fulfillment_exceptions (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    order_id UUID NOT NULL REFERENCES nx_orders(id) ON DELETE CASCADE,
    allocation_id UUID REFERENCES nx_order_allocations(id) ON DELETE SET NULL,
    tenant_id UUID NOT NULL,
    type VARCHAR(50) NOT NULL CHECK (type IN (
        'INVENTORY_SHORTAGE','CARRIER_FAILURE','CAPACITY_EXCEEDED','WORKER_ABSENT',
        'WEATHER_DELAY','SHIPPING_ADDRESS_ISSUE','PAYMENT_HOLD','CREDIT_HOLD',
        'FRAUD_FLAG','CUSTOMER_REQUEST','SYSTEM_ERROR','OTHER'
    )),
    severity VARCHAR(20) NOT NULL DEFAULT 'MEDIUM' CHECK (severity IN ('LOW','MEDIUM','HIGH','CRITICAL')),
    status VARCHAR(30) NOT NULL DEFAULT 'OPEN' CHECK (status IN ('OPEN','IN_PROGRESS','RESOLVED','ESCALATED','CLOSED')),
    title VARCHAR(255) NOT NULL,
    description TEXT,
    resolution TEXT,
    suggested_action TEXT,
    auto_resolvable BOOLEAN DEFAULT false,
    resolution_strategy VARCHAR(50) CHECK (resolution_strategy IN (
        'REALLOCATE','OVERRIDE','CONTACT_CUSTOMER','ESCALATE_MANAGER',
        'SPLIT_ORDER','BACKORDER','SUBSTITUTE','CANCEL_ORDER'
    )),
    detected_at TIMESTAMP DEFAULT now(),
    resolved_at TIMESTAMP,
    resolved_by UUID,
    assigned_to UUID,
    escalated_at TIMESTAMP,
    metadata JSONB,
    created_at TIMESTAMP DEFAULT now(),
    updated_at TIMESTAMP DEFAULT now()
);
CREATE INDEX idx_nx_fulfillment_exceptions_order ON nx_fulfillment_exceptions(order_id);
CREATE INDEX idx_nx_fulfillment_exceptions_tenant ON nx_fulfillment_exceptions(tenant_id);
CREATE INDEX idx_nx_fulfillment_exceptions_status ON nx_fulfillment_exceptions(status);
CREATE INDEX idx_nx_fulfillment_exceptions_type ON nx_fulfillment_exceptions(type);
CREATE INDEX idx_nx_fulfillment_exceptions_severity ON nx_fulfillment_exceptions(severity);

-- 3. ROUTING STRATEGY CONFIG (tenant-level AI routing settings)
CREATE TABLE IF NOT EXISTS nx_routing_config (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    tenant_id UUID NOT NULL UNIQUE,
    default_strategy VARCHAR(30) DEFAULT 'HYBRID' CHECK (default_strategy IN ('RULE_BASED','AI_OPTIMIZED','HYBRID')),
    ai_confidence_threshold DECIMAL(5,4) DEFAULT 0.7000,
    enable_auto_allocation BOOLEAN DEFAULT true,
    enable_exception_detection BOOLEAN DEFAULT true,
    enable_auto_resolution BOOLEAN DEFAULT false,
    max_splits INT DEFAULT 3,
    cost_optimization_weight DECIMAL(3,2) DEFAULT 0.40,
    speed_optimization_weight DECIMAL(3,2) DEFAULT 0.40,
    accuracy_optimization_weight DECIMAL(3,2) DEFAULT 0.20,
    preferred_carriers JSONB,
    blacklisted_nodes JSONB,
    created_at TIMESTAMP DEFAULT now(),
    updated_at TIMESTAMP DEFAULT now()
);
CREATE INDEX idx_nx_routing_config_tenant ON nx_routing_config(tenant_id);

-- 4. ROUTING LOG (audit trail for every allocation decision)
CREATE TABLE IF NOT EXISTS nx_routing_log (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    order_id UUID NOT NULL REFERENCES nx_orders(id) ON DELETE CASCADE,
    tenant_id UUID NOT NULL,
    strategy VARCHAR(30) NOT NULL,
    input_snapshot JSONB,
    rules_evaluated JSONB,
    candidates JSONB,
    selected_node_id UUID,
    confidence_score DECIMAL(5,4),
    delivery_promise_estimate TIMESTAMP,
    cost_estimate DECIMAL(12,2),
    exceptions_detected JSONB,
    execution_time_ms INT,
    status VARCHAR(30) DEFAULT 'SUCCESS' CHECK (status IN ('SUCCESS','FAILED','PARTIAL')),
    error_message TEXT,
    created_at TIMESTAMP DEFAULT now()
);
CREATE INDEX idx_nx_routing_log_order ON nx_routing_log(order_id);
CREATE INDEX idx_nx_routing_log_tenant ON nx_routing_log(tenant_id);
