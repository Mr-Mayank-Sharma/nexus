-- ============================================================
-- Phase 0+1: Real demand forecasting artifacts
-- 1. Store ONNX model artifacts + feature contract on versions
-- 2. Per-tenant/per-entity calibration factors for cold-start
-- ============================================================

-- 1. Artifact columns on model versions
ALTER TABLE ai_model_versions
    ADD COLUMN IF NOT EXISTS artifact_format VARCHAR(20),
    ADD COLUMN IF NOT EXISTS artifact_checksum VARCHAR(64),
    ADD COLUMN IF NOT EXISTS feature_columns JSONB,
    ADD COLUMN IF NOT EXISTS calibration_type VARCHAR(20) DEFAULT 'MULTIPLICATIVE';

-- 2. Calibration factors (global model + per-tenant adjustment)
CREATE TABLE IF NOT EXISTS ai_calibrations (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    tenant_id UUID NOT NULL,
    model_id UUID NOT NULL REFERENCES ai_models(id) ON DELETE CASCADE,
    version_id UUID REFERENCES ai_model_versions(id) ON DELETE CASCADE,
    entity_id VARCHAR(255) NOT NULL,
    entity_type VARCHAR(50) DEFAULT 'SKU',
    scale_factor DECIMAL(20,6) DEFAULT 1.000000,
    bias DECIMAL(20,6) DEFAULT 0.000000,
    sample_count INT DEFAULT 0,
    weight DECIMAL(20,6) DEFAULT 1.000000,
    updated_at TIMESTAMP DEFAULT now(),
    created_at TIMESTAMP DEFAULT now(),
    UNIQUE(tenant_id, model_id, entity_id)
);
CREATE INDEX IF NOT EXISTS idx_ai_calibrations_lookup ON ai_calibrations(tenant_id, model_id, entity_id);
CREATE INDEX IF NOT EXISTS idx_ai_calibrations_version ON ai_calibrations(version_id);
