-- Job manager: jobs a customer defines and schedules, each run with its counts, log, output file and the
-- individual rows that failed or need attention (with the reason), so a person can download them and see why.
CREATE TABLE IF NOT EXISTS nx_jobs (
    id            UUID PRIMARY KEY,
    tenant_id     UUID         NOT NULL,
    name          VARCHAR(120) NOT NULL,
    job_type      VARCHAR(60)  NOT NULL,
    params        TEXT         NOT NULL DEFAULT '{}',
    cron          VARCHAR(80),                       -- 6-field Spring cron; null means run only on demand
    timezone      VARCHAR(60)  NOT NULL DEFAULT 'UTC',
    enabled       BOOLEAN      NOT NULL DEFAULT FALSE,
    next_run_at   TIMESTAMP,
    last_run_at   TIMESTAMP,
    last_status   VARCHAR(20),
    created_by    VARCHAR(120),
    created_at    TIMESTAMP    NOT NULL DEFAULT now(),
    updated_at    TIMESTAMP    NOT NULL DEFAULT now(),
    CONSTRAINT uq_nx_jobs_name UNIQUE (tenant_id, name)
);
CREATE INDEX IF NOT EXISTS idx_nx_jobs_due ON nx_jobs (enabled, next_run_at);

CREATE TABLE IF NOT EXISTS nx_job_runs (
    id            UUID PRIMARY KEY,
    tenant_id     UUID         NOT NULL,
    job_id        UUID         NOT NULL,
    status        VARCHAR(20)  NOT NULL,             -- RUNNING, COMPLETED, PARTIAL, FAILED
    trigger_kind  VARCHAR(12)  NOT NULL,             -- MANUAL or SCHEDULE
    triggered_by  VARCHAR(120),
    started_at    TIMESTAMP    NOT NULL DEFAULT now(),
    finished_at   TIMESTAMP,
    total         INTEGER      NOT NULL DEFAULT 0,
    succeeded     INTEGER      NOT NULL DEFAULT 0,
    failed        INTEGER      NOT NULL DEFAULT 0,
    issue_count   INTEGER      NOT NULL DEFAULT 0,
    message       TEXT,
    log_text      TEXT,
    output_name   VARCHAR(200),
    output_csv    TEXT
);
CREATE INDEX IF NOT EXISTS idx_nx_job_runs_job ON nx_job_runs (tenant_id, job_id, started_at DESC);

CREATE TABLE IF NOT EXISTS nx_job_run_issues (
    id         UUID PRIMARY KEY,
    tenant_id  UUID         NOT NULL,
    run_id     UUID         NOT NULL,
    row_ref    VARCHAR(300),
    reason     TEXT         NOT NULL,
    raw        TEXT
);
CREATE INDEX IF NOT EXISTS idx_nx_job_run_issues_run ON nx_job_run_issues (tenant_id, run_id);

DO $$
DECLARE tbl TEXT;
BEGIN
    FOREACH tbl IN ARRAY ARRAY['nx_jobs', 'nx_job_runs', 'nx_job_run_issues'] LOOP
        EXECUTE format('ALTER TABLE %I ENABLE ROW LEVEL SECURITY', tbl);
        EXECUTE format('ALTER TABLE %I FORCE ROW LEVEL SECURITY', tbl);
        EXECUTE format('DROP POLICY IF EXISTS tenant_isolation ON %I', tbl);
        EXECUTE format($p$
            CREATE POLICY tenant_isolation ON %I
            USING (
                tenant_id IS NULL
                OR nullif(current_setting('app.current_tenant_id', true), '') IS NULL
                OR tenant_id = nullif(current_setting('app.current_tenant_id', true), '')::uuid
            )
            WITH CHECK (
                tenant_id IS NULL
                OR nullif(current_setting('app.current_tenant_id', true), '') IS NULL
                OR tenant_id = nullif(current_setting('app.current_tenant_id', true), '')::uuid
            )$p$, tbl);
    END LOOP;
END;
$$;
