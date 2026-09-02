-- V58: Link ASNs to dock appointments.
-- nx_appointments is a JPA entity (NxAppointment) that was previously
-- created only by Hibernate ddl-auto.  In production (ddl-auto=validate,
-- Flyway-only schema) it must exist before the ALTERs below.
CREATE TABLE IF NOT EXISTS nx_appointments (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    tenant_id UUID,
    warehouse_id UUID,
    appointment_number VARCHAR(255) NOT NULL UNIQUE,
    type VARCHAR(255) NOT NULL,
    status VARCHAR(255) NOT NULL,
    carrier_name VARCHAR(255),
    carrier_code VARCHAR(255),
    trailer_number VARCHAR(255),
    vehicle_license_plate VARCHAR(255),
    driver_name VARCHAR(255),
    driver_phone VARCHAR(255),
    dock_door_id UUID,
    yard_location_id UUID,
    asn_id UUID,
    edi_document_id UUID,
    estimated_arrival TIMESTAMP,
    actual_arrival TIMESTAMP,
    estimated_departure TIMESTAMP,
    actual_departure TIMESTAMP,
    appointment_window VARCHAR(255),
    po_numbers TEXT,
    order_ids TEXT,
    load_count INT,
    pallet_count INT,
    piece_count INT,
    weight_kg DOUBLE PRECISION,
    temperature_required BOOLEAN,
    temperature_min DOUBLE PRECISION,
    temperature_max DOUBLE PRECISION,
    special_instructions TEXT,
    checked_in_by VARCHAR(255),
    completed_by VARCHAR(255),
    notes TEXT,
    metadata JSONB,
    created_at TIMESTAMP NOT NULL DEFAULT NOW(),
    updated_at TIMESTAMP NOT NULL DEFAULT NOW()
);

ALTER TABLE nx_appointments ADD COLUMN IF NOT EXISTS asn_id UUID;
ALTER TABLE nx_appointments ADD COLUMN IF NOT EXISTS edi_document_id UUID;

CREATE INDEX IF NOT EXISTS idx_nx_appointments_asn_id ON nx_appointments (asn_id);
CREATE INDEX IF NOT EXISTS idx_nx_appointments_edi_document_id ON nx_appointments (edi_document_id);

-- Row-Level Security for nx_appointments (created by this migration, so V26
-- which normally applies RLS ran before it existed).  Same policy as V26.
DO $$
DECLARE tbl TEXT;
BEGIN
    FOREACH tbl IN ARRAY ARRAY['nx_appointments'] LOOP
        EXECUTE format('ALTER TABLE %I ENABLE ROW LEVEL SECURITY', tbl);
        EXECUTE format('DROP POLICY IF EXISTS tenant_isolation ON %I', tbl);
        EXECUTE format(
            'CREATE POLICY tenant_isolation ON %I
             USING (
                 nullif(current_setting(''app.current_tenant_id'', true), '''')::uuid IS NULL
                 OR tenant_id = nullif(current_setting(''app.current_tenant_id'', true), '''')::uuid
             )',
            tbl
        );
        EXECUTE format('ALTER TABLE %I FORCE ROW LEVEL SECURITY', tbl);
    END LOOP;
END;
$$;
