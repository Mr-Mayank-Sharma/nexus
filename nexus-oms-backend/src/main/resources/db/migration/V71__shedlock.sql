-- V71: lock table for ShedLock (one execution per @Scheduled job across replicas).
CREATE TABLE IF NOT EXISTS shedlock (
    name       VARCHAR(64)  NOT NULL PRIMARY KEY,
    lock_until TIMESTAMP    NOT NULL,
    locked_at  TIMESTAMP    NOT NULL,
    locked_by  VARCHAR(255) NOT NULL
);

-- Tenant-scoped connections run as nexus_app (V69); V69's default privileges cover tables
-- created later by the migration owner, but grant explicitly in case they were not applied.
DO $$
BEGIN
    IF EXISTS (SELECT 1 FROM pg_roles WHERE rolname = 'nexus_app') THEN
        GRANT SELECT, INSERT, UPDATE, DELETE ON shedlock TO nexus_app;
    END IF;
END;
$$;
