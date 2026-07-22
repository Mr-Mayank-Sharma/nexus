-- V39: Create Picker Role & Assignment tables

CREATE TABLE nx_pickers (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    tenant_id UUID NOT NULL,
    node_id UUID NOT NULL,
    user_id UUID NOT NULL,
    name VARCHAR(255) NOT NULL,
    employee_id VARCHAR(50),
    status VARCHAR(50) NOT NULL DEFAULT 'AVAILABLE',
    current_order_id UUID,
    max_concurrent_orders INTEGER NOT NULL DEFAULT 3,
    orders_completed_today INTEGER NOT NULL DEFAULT 0,
    items_picked_today INTEGER NOT NULL DEFAULT 0,
    last_active_at TIMESTAMP,
    shift_start TIMESTAMP,
    shift_end TIMESTAMP,
    active BOOLEAN NOT NULL DEFAULT TRUE,
    created_at TIMESTAMP NOT NULL DEFAULT NOW(),
    updated_at TIMESTAMP NOT NULL DEFAULT NOW()
);

CREATE INDEX idx_picker_tenant ON nx_pickers(tenant_id);
CREATE INDEX idx_picker_node ON nx_pickers(node_id);
CREATE INDEX idx_picker_user ON nx_pickers(user_id);
CREATE INDEX idx_picker_status ON nx_pickers(status);

CREATE TABLE nx_picker_assignments (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    tenant_id UUID NOT NULL,
    picker_id UUID NOT NULL,
    pickup_order_id UUID NOT NULL,
    order_number VARCHAR(50) NOT NULL,
    node_id UUID NOT NULL,
    status VARCHAR(50) NOT NULL DEFAULT 'ASSIGNED',
    assigned_at TIMESTAMP NOT NULL DEFAULT NOW(),
    started_at TIMESTAMP,
    completed_at TIMESTAMP,
    priority INTEGER NOT NULL DEFAULT 10,
    created_at TIMESTAMP NOT NULL DEFAULT NOW(),
    updated_at TIMESTAMP NOT NULL DEFAULT NOW()
);

CREATE INDEX idx_picker_assign_picker ON nx_picker_assignments(picker_id);
CREATE INDEX idx_picker_assign_node ON nx_picker_assignments(node_id);
CREATE INDEX idx_picker_assign_status ON nx_picker_assignments(status);
CREATE INDEX idx_picker_assign_order ON nx_picker_assignments(pickup_order_id);
