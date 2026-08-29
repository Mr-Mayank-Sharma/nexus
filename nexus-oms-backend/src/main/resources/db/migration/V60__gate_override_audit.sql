-- V60: Gate-override audit columns on ai_model_versions.
-- Forced deploys now carry explicit metadata so auditors can distinguish
-- clean passes from admin overrides without relying on log retention.

ALTER TABLE ai_model_versions
    ADD COLUMN IF NOT EXISTS gate_override BOOLEAN DEFAULT FALSE,
    ADD COLUMN IF NOT EXISTS gate_failures TEXT;
