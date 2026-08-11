ALTER TABLE ai_training_jobs
    ADD COLUMN IF NOT EXISTS metrics_source TEXT;
