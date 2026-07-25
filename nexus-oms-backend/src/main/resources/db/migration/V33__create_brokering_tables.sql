-- V33: Create Brokering Queue tables

CREATE TABLE nx_brokering_queue (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    tenant_id UUID NOT NULL,
    order_id UUID NOT NULL,
    priority VARCHAR(20) NOT NULL DEFAULT 'NORMAL',
    status VARCHAR(50) NOT NULL DEFAULT 'WAITING',
    attempts INTEGER NOT NULL DEFAULT 0,
    max_attempts INTEGER NOT NULL DEFAULT 3,
    last_attempt_at TIMESTAMP,
    next_run_at TIMESTAMP,
    allocated_node_id UUID,
    failure_reason TEXT,
    metadata JSONB,
    entered_at TIMESTAMP NOT NULL DEFAULT NOW(),
    exited_at TIMESTAMP,
    created_at TIMESTAMP NOT NULL DEFAULT NOW(),
    updated_at TIMESTAMP NOT NULL DEFAULT NOW()
);

CREATE INDEX idx_brokering_tenant ON nx_brokering_queue(tenant_id);
CREATE INDEX idx_brokering_status ON nx_brokering_queue(status);
CREATE INDEX idx_brokering_priority ON nx_brokering_queue(priority);
CREATE INDEX idx_brokering_order ON nx_brokering_queue(order_id);
CREATE INDEX idx_brokering_next_run ON nx_brokering_queue(next_run_at);

CREATE TABLE nx_brokering_runs (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    tenant_id UUID NOT NULL,
    run_type VARCHAR(50) NOT NULL,
    started_at TIMESTAMP NOT NULL,
    completed_at TIMESTAMP,
    orders_processed INTEGER DEFAULT 0,
    orders_allocated INTEGER DEFAULT 0,
    orders_failed INTEGER DEFAULT 0,
    execution_time_ms INTEGER,
    status VARCHAR(50) NOT NULL DEFAULT 'RUNNING',
    triggered_by UUID,
    metadata JSONB,
    created_at TIMESTAMP NOT NULL DEFAULT NOW()
);

CREATE INDEX idx_brokering_run_tenant ON nx_brokering_runs(tenant_id);
CREATE INDEX idx_brokering_run_status ON nx_brokering_runs(status);
