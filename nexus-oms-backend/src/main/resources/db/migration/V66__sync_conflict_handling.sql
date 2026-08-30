-- ============================================================
-- T-13: Bidirectional Sync Conflict Handling
-- Versioning + conflict ledger for synced entities, so stale
-- inbound data never silently overwrites newer local state.
-- ============================================================

-- 1. VERSIONING on synced entities (optimistic lock).
--    Inbound updates carry the version they were based on; if the
--    local version is newer, it's a conflict.
ALTER TABLE nx_orders ADD COLUMN IF NOT EXISTS sync_version BIGINT DEFAULT 0;
ALTER TABLE nx_inventory ADD COLUMN IF NOT EXISTS sync_version BIGINT DEFAULT 0;
ALTER TABLE nx_shipments ADD COLUMN IF NOT EXISTS sync_version BIGINT DEFAULT 0;

-- 2. CONFLICT LEDGER.
CREATE TABLE IF NOT EXISTS nx_sync_conflicts (
    id                  UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    tenant_id           UUID NOT NULL,
    entity_type         VARCHAR(50) NOT NULL,   -- ORDER | INVENTORY | SHIPMENT | ...
    entity_id           UUID NOT NULL,
    direction           VARCHAR(20) NOT NULL,   -- INBOUND | OUTBOUND
    source_system       VARCHAR(50) NOT NULL,   -- NETSUITE | SHOPIFY | OFBIZ | ...
    inbound_version     BIGINT,
    local_version       BIGINT,
    conflicting_fields  JSONB,
    resolution          VARCHAR(20) DEFAULT 'PENDING',
                        -- PENDING | LOCAL_WINS | INBOUND_WINS | MERGED | MANUAL
    status              VARCHAR(20) DEFAULT 'OPEN',  -- OPEN | RESOLVED | IGNORED
    created_at          TIMESTAMP NOT NULL DEFAULT now(),
    resolved_at         TIMESTAMP,
    resolved_by         UUID
);
CREATE INDEX IF NOT EXISTS idx_sync_conflicts_tenant ON nx_sync_conflicts(tenant_id, status);
CREATE INDEX IF NOT EXISTS idx_sync_conflicts_entity ON nx_sync_conflicts(entity_type, entity_id);

-- 3. FIELD-LEVEL MAPPING CONTROL (which side wins per field).
CREATE TABLE IF NOT EXISTS nx_sync_field_mappings (
    id              UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    tenant_id       UUID NOT NULL,
    entity_type     VARCHAR(50) NOT NULL,
    field_name      VARCHAR(100) NOT NULL,
    direction       VARCHAR(20) NOT NULL,   -- INBOUND | OUTBOUND
    winner          VARCHAR(20) NOT NULL,   -- LOCAL | REMOTE
    is_active       BOOLEAN NOT NULL DEFAULT TRUE,
    created_at      TIMESTAMP NOT NULL DEFAULT now(),
    UNIQUE (tenant_id, entity_type, field_name, direction)
);

-- 4. SYNC JOB STATUS (pending / queued / completed / failed) for reconciliation.
CREATE TABLE IF NOT EXISTS nx_sync_job_status (
    id                  UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    tenant_id           UUID NOT NULL,
    job_name            VARCHAR(128) NOT NULL,
    direction           VARCHAR(20) NOT NULL,
    status              VARCHAR(20) NOT NULL,   -- PENDING | QUEUED | COMPLETED | FAILED
    records_total       INTEGER DEFAULT 0,
    records_processed   INTEGER DEFAULT 0,
    records_failed      INTEGER DEFAULT 0,
    last_run_at         TIMESTAMP,
    next_run_at         TIMESTAMP,
    created_at          TIMESTAMP NOT NULL DEFAULT now(),
    updated_at          TIMESTAMP NOT NULL DEFAULT now()
);
CREATE INDEX IF NOT EXISTS idx_sync_job_status_tenant ON nx_sync_job_status(tenant_id, status);
