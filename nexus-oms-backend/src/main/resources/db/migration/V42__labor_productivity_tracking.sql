-- V42: Enhanced Labor Management - productivity tracking, workload balancing, performance standards enforcement
-- Manhattan parity: labor productivity tracking, performance standards, workload balancing

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
