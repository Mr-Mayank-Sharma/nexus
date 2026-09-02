-- V42: Enhanced Labor Management - productivity tracking, workload balancing, performance standards enforcement
-- Manhattan parity: labor productivity tracking, performance standards, workload balancing

-- Base tables for labor tracking.  These are JPA entities (NxLaborEntry,
-- NxEngineeredStandard) that were previously created only by Hibernate
-- ddl-auto.  In production (ddl-auto=validate, Flyway-only schema) they
-- must exist before the ALTERs below, so we create them here.
CREATE TABLE IF NOT EXISTS nx_labor_entries (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    tenant_id UUID NOT NULL,
    warehouse_id UUID NOT NULL,
    staff_id UUID NOT NULL,
    employee_code VARCHAR(255),
    first_name VARCHAR(255),
    last_name VARCHAR(255),
    task_type VARCHAR(255),
    status VARCHAR(255),
    shift VARCHAR(255),
    clocked_in_at TIMESTAMP,
    clocked_out_at TIMESTAMP,
    break_started_at TIMESTAMP,
    break_ended_at TIMESTAMP,
    total_work_minutes INT,
    total_break_minutes INT,
    lines_picked INT,
    lines_packed INT,
    units_received INT,
    units_shipped INT,
    error_count INT,
    productivity_score DOUBLE PRECISION,
    efficiency_rating VARCHAR(255),
    current_task VARCHAR(255),
    current_wave_id UUID,
    notes TEXT,
    metadata JSONB,
    created_at TIMESTAMP NOT NULL DEFAULT NOW(),
    updated_at TIMESTAMP NOT NULL DEFAULT NOW()
);

CREATE TABLE IF NOT EXISTS nx_engineered_standards (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    tenant_id UUID NOT NULL,
    warehouse_id UUID NOT NULL,
    task_type VARCHAR(255) NOT NULL,
    uom VARCHAR(255) NOT NULL,
    standard_value DOUBLE PRECISION NOT NULL,
    category VARCHAR(255),
    complexity_level VARCHAR(255),
    is_active BOOLEAN DEFAULT TRUE,
    effective_from DATE,
    effective_to DATE,
    notes TEXT,
    created_at TIMESTAMP NOT NULL DEFAULT NOW(),
    updated_at TIMESTAMP NOT NULL DEFAULT NOW()
);

-- Add workload balancing columns to labor entries
ALTER TABLE nx_labor_entries ADD COLUMN IF NOT EXISTS zone_assignment VARCHAR(50);
ALTER TABLE nx_labor_entries ADD COLUMN IF NOT EXISTS workload_weight DECIMAL(5,2) DEFAULT 0;
ALTER TABLE nx_labor_entries ADD COLUMN IF NOT EXISTS items_per_hour DECIMAL(8,2) DEFAULT 0;
ALTER TABLE nx_labor_entries ADD COLUMN IF NOT EXISTS quality_score DECIMAL(5,2) DEFAULT 100.0;
ALTER TABLE nx_labor_entries ADD COLUMN IF NOT EXISTS assigned_area VARCHAR(100);

-- Add performance standard columns
ALTER TABLE nx_engineered_standards ADD COLUMN IF NOT EXISTS target_uph DECIMAL(8,2) DEFAULT 0; -- units per hour target
ALTER TABLE nx_engineered_standards ADD COLUMN IF NOT EXISTS target_lines_per_hour DECIMAL(8,2) DEFAULT 0;
ALTER TABLE nx_engineered_standards ADD COLUMN IF NOT EXISTS error_tolerance_pct DECIMAL(5,2) DEFAULT 2.0;
ALTER TABLE nx_engineered_standards ADD COLUMN IF NOT EXISTS break_frequency_minutes INT DEFAULT 120;
ALTER TABLE nx_engineered_standards ADD COLUMN IF NOT EXISTS shift_type VARCHAR(20) DEFAULT 'ALL';

-- Workload balancing rules
CREATE TABLE IF NOT EXISTS nx_workload_rules (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    tenant_id UUID NOT NULL,
    warehouse_id UUID NOT NULL,
    rule_name VARCHAR(100) NOT NULL,
    task_type VARCHAR(50) NOT NULL,
    max_workload_weight DECIMAL(6,2) DEFAULT 10.0,
    priority_weight DECIMAL(5,2) DEFAULT 1.0,
    skill_required VARCHAR(50),
    is_active BOOLEAN DEFAULT TRUE,
    notes TEXT,
    created_at TIMESTAMP NOT NULL DEFAULT NOW(),
    updated_at TIMESTAMP NOT NULL DEFAULT NOW()
);

-- Productivity log for historical tracking
CREATE TABLE IF NOT EXISTS nx_productivity_log (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    tenant_id UUID NOT NULL,
    warehouse_id UUID NOT NULL,
    staff_id UUID NOT NULL,
    labor_entry_id UUID,
    task_type VARCHAR(50) NOT NULL,
    items_completed INT DEFAULT 0,
    time_spent_minutes INT DEFAULT 0,
    items_per_hour DECIMAL(8,2) DEFAULT 0,
    quality_score DECIMAL(5,2) DEFAULT 100.0,
    vs_standard_pct DECIMAL(8,2) DEFAULT 0,
    logged_at TIMESTAMP NOT NULL DEFAULT NOW()
);

CREATE INDEX IF NOT EXISTS idx_productivity_log_staff ON nx_productivity_log(staff_id);
CREATE INDEX IF NOT EXISTS idx_productivity_log_warehouse ON nx_productivity_log(warehouse_id, logged_at);

-- Row-Level Security for the tenant-scoped tables created by this migration.
-- V26 (which normally applies RLS) runs BEFORE this migration, so these new
-- tables would otherwise be unprotected.  Same policy as V26.
DO $$
DECLARE tbl TEXT;
BEGIN
    FOREACH tbl IN ARRAY ARRAY['nx_labor_entries', 'nx_engineered_standards'] LOOP
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
