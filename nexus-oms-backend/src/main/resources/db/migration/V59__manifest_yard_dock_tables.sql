-- Manifest, yard, dock door, and shift schedule tables (entities existed without migrations)

CREATE TABLE IF NOT EXISTS nx_manifests (
    id UUID PRIMARY KEY,
    tenant_id UUID,
    carrier VARCHAR(255),
    manifest_date VARCHAR(255),
    bol_number VARCHAR(255),
    total_weight NUMERIC(38, 2),
    total_cost NUMERIC(38, 2),
    status VARCHAR(255) NOT NULL,
    created_at TIMESTAMP NOT NULL,
    updated_at TIMESTAMP NOT NULL
);

CREATE INDEX IF NOT EXISTS idx_manifests_tenant ON nx_manifests (tenant_id);
CREATE INDEX IF NOT EXISTS idx_manifests_status ON nx_manifests (status);

CREATE TABLE IF NOT EXISTS nx_manifest_shipments (
    id UUID PRIMARY KEY,
    manifest_id UUID NOT NULL,
    tenant_id UUID,
    order_id VARCHAR(255),
    tracking_number VARCHAR(255),
    service VARCHAR(255),
    status VARCHAR(255),
    weight NUMERIC(38, 2),
    cost NUMERIC(38, 2),
    destination VARCHAR(255)
);

CREATE INDEX IF NOT EXISTS idx_manifest_shipments_manifest ON nx_manifest_shipments (manifest_id);
CREATE INDEX IF NOT EXISTS idx_manifest_shipments_tenant ON nx_manifest_shipments (tenant_id);

CREATE TABLE IF NOT EXISTS nx_dock_doors (
    id UUID PRIMARY KEY,
    tenant_id UUID,
    warehouse_id UUID,
    door_number VARCHAR(255) NOT NULL,
    door_type VARCHAR(255),
    status VARCHAR(255) NOT NULL,
    dock_height VARCHAR(255),
    has_leveler BOOLEAN,
    has_seal BOOLEAN,
    max_width_cm INTEGER,
    max_height_cm INTEGER,
    max_weight_kg DOUBLE PRECISION,
    current_vehicle_id UUID,
    current_appointment_id UUID,
    zone_id UUID,
    notes VARCHAR(255),
    metadata JSONB,
    created_at TIMESTAMP NOT NULL,
    updated_at TIMESTAMP NOT NULL
);

CREATE INDEX IF NOT EXISTS idx_dock_doors_tenant ON nx_dock_doors (tenant_id);
CREATE INDEX IF NOT EXISTS idx_dock_doors_warehouse ON nx_dock_doors (warehouse_id);
CREATE INDEX IF NOT EXISTS idx_dock_doors_status ON nx_dock_doors (status);

CREATE TABLE IF NOT EXISTS nx_yard_locations (
    id UUID PRIMARY KEY,
    tenant_id UUID,
    warehouse_id UUID,
    location_code VARCHAR(255) NOT NULL,
    location_type VARCHAR(255),
    status VARCHAR(255) NOT NULL,
    capacity INTEGER,
    current_occupancy INTEGER,
    zone VARCHAR(255),
    notes VARCHAR(255),
    metadata JSONB,
    created_at TIMESTAMP NOT NULL,
    updated_at TIMESTAMP NOT NULL
);

CREATE INDEX IF NOT EXISTS idx_yard_locations_tenant ON nx_yard_locations (tenant_id);
CREATE INDEX IF NOT EXISTS idx_yard_locations_warehouse ON nx_yard_locations (warehouse_id);
CREATE INDEX IF NOT EXISTS idx_yard_locations_status ON nx_yard_locations (status);

CREATE TABLE IF NOT EXISTS nx_shift_schedules (
    id UUID PRIMARY KEY,
    tenant_id UUID NOT NULL,
    warehouse_id UUID NOT NULL,
    staff_id UUID NOT NULL,
    employee_code VARCHAR(255),
    first_name VARCHAR(255),
    last_name VARCHAR(255),
    shift_date DATE NOT NULL,
    shift_type VARCHAR(255) NOT NULL,
    scheduled_start TIME NOT NULL,
    scheduled_end TIME NOT NULL,
    actual_start TIME,
    actual_end TIME,
    status VARCHAR(255),
    notes VARCHAR(255),
    created_at TIMESTAMP NOT NULL,
    updated_at TIMESTAMP NOT NULL
);

CREATE INDEX IF NOT EXISTS idx_shift_schedules_tenant ON nx_shift_schedules (tenant_id);
CREATE INDEX IF NOT EXISTS idx_shift_schedules_staff ON nx_shift_schedules (staff_id);
CREATE INDEX IF NOT EXISTS idx_shift_schedules_date ON nx_shift_schedules (shift_date);
