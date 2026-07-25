-- V43: Trailer Lifecycle Tracking
-- Manhattan parity: trailer check-in/out, dwell time tracking, event history

CREATE TABLE IF NOT EXISTS nx_trailer_events (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    tenant_id UUID NOT NULL,
    warehouse_id UUID NOT NULL,
    trailer_number VARCHAR(50) NOT NULL,
    event_type VARCHAR(30) NOT NULL, -- CHECKED_IN, CHECKED_OUT, DOCKED, UNDOCKED, RELOCATED, INSPECTION
    dock_door_id UUID,
    yard_location_id UUID,
    appointment_id UUID,
    carrier_code VARCHAR(30),
    driver_name VARCHAR(100),
    seal_number VARCHAR(50),
    license_plate VARCHAR(30),
    loaded BOOLEAN DEFAULT FALSE,
    pallet_count INT DEFAULT 0,
    weight_kg DECIMAL(10,2),
    condition_notes TEXT,
    performed_by VARCHAR(100),
    event_time TIMESTAMP NOT NULL DEFAULT NOW(),
    created_at TIMESTAMP NOT NULL DEFAULT NOW()
);

CREATE INDEX IF NOT EXISTS idx_trailer_events_trailer ON nx_trailer_events(trailer_number);
CREATE INDEX IF NOT EXISTS idx_trailer_events_warehouse ON nx_trailer_events(warehouse_id, event_time);
CREATE INDEX IF NOT EXISTS idx_trailer_events_type ON nx_trailer_events(event_type);

-- Track current trailer state
CREATE TABLE IF NOT EXISTS nx_trailers (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    tenant_id UUID NOT NULL,
    warehouse_id UUID NOT NULL,
    trailer_number VARCHAR(50) NOT NULL,
    carrier_code VARCHAR(30),
    status VARCHAR(20) NOT NULL DEFAULT 'IN_YARD', -- IN_YARD, AT_DOCK, DEPARTED, MISSING
    current_dock_door_id UUID,
    current_yard_location_id UUID,
    checked_in_at TIMESTAMP,
    checked_out_at TIMESTAMP,
    docked_at TIMESTAMP,
    last_event_at TIMESTAMP,
    loaded BOOLEAN DEFAULT FALSE,
    pallet_count INT DEFAULT 0,
    seal_number VARCHAR(50),
    license_plate VARCHAR(30),
    dwelled_minutes INT DEFAULT 0,
    notes TEXT,
    created_at TIMESTAMP NOT NULL DEFAULT NOW(),
    updated_at TIMESTAMP NOT NULL DEFAULT NOW(),
    UNIQUE(tenant_id, warehouse_id, trailer_number)
);

CREATE INDEX IF NOT EXISTS idx_trailers_warehouse ON nx_trailers(warehouse_id, status);
