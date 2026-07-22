-- ============================================================
-- Import History & Processing Logs
-- V18 migration
-- ============================================================

CREATE TABLE IF NOT EXISTS import_history (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    tenant_id UUID NOT NULL,
    file_name VARCHAR(500) NOT NULL,
    original_file_name VARCHAR(500) NOT NULL,
    import_type VARCHAR(50) NOT NULL,
    file_format VARCHAR(10) NOT NULL,
    import_mode VARCHAR(30) NOT NULL DEFAULT 'CONTINUE_ON_ERROR',
    status VARCHAR(30) NOT NULL DEFAULT 'PENDING',
    total_records INT NOT NULL DEFAULT 0,
    success_count INT NOT NULL DEFAULT 0,
    failed_count INT NOT NULL DEFAULT 0,
    duplicate_count INT NOT NULL DEFAULT 0,
    processing_time_ms BIGINT NOT NULL DEFAULT 0,
    file_size_bytes BIGINT NOT NULL DEFAULT 0,
    stored_file_path VARCHAR(1000),
    processed_file_path VARCHAR(1000),
    error_file_path VARCHAR(1000),
    started_at TIMESTAMP,
    completed_at TIMESTAMP,
    created_at TIMESTAMP NOT NULL DEFAULT now(),
    updated_at TIMESTAMP NOT NULL DEFAULT now()
);

CREATE INDEX IF NOT EXISTS idx_import_history_tenant ON import_history(tenant_id);
CREATE INDEX IF NOT EXISTS idx_import_history_status ON import_history(status);
CREATE INDEX IF NOT EXISTS idx_import_history_type ON import_history(import_type);
CREATE INDEX IF NOT EXISTS idx_import_history_created ON import_history(created_at DESC);

CREATE TABLE IF NOT EXISTS import_record_log (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    import_history_id UUID NOT NULL REFERENCES import_history(id) ON DELETE CASCADE,
    tenant_id UUID NOT NULL,
    row_number INT NOT NULL,
    status VARCHAR(20) NOT NULL DEFAULT 'PENDING',
    error_code VARCHAR(50),
    error_message TEXT,
    suggested_resolution TEXT,
    original_data JSONB,
    processed_data JSONB,
    stage VARCHAR(50),
    created_at TIMESTAMP NOT NULL DEFAULT now()
);

CREATE INDEX IF NOT EXISTS idx_import_record_log_history ON import_record_log(import_history_id);
CREATE INDEX IF NOT EXISTS idx_import_record_log_status ON import_record_log(status);
CREATE INDEX IF NOT EXISTS idx_import_record_log_row ON import_record_log(import_history_id, row_number);
