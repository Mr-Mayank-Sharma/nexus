-- Location-aware inventory publishing to sales channels (Shopify first).
--
-- A Nexus node (warehouse or store) feeds at most ONE channel location per integration store, so
-- its stock cannot be published twice. Several nodes may feed the same channel location (a pooled
-- online location). Nothing is published for a store that has no mapping.

CREATE TABLE IF NOT EXISTS nx_channel_location_mappings (
    id                     UUID PRIMARY KEY,
    tenant_id              UUID         NOT NULL,
    integration_store_id   UUID         NOT NULL,
    node_id                UUID         NOT NULL,
    external_location_id   VARCHAR(64)  NOT NULL,
    external_location_name VARCHAR(255),
    enabled                BOOLEAN      NOT NULL DEFAULT TRUE,
    buffer_units           INTEGER      NOT NULL DEFAULT 0 CHECK (buffer_units >= 0),
    contribution_cap       INTEGER CHECK (contribution_cap IS NULL OR contribution_cap >= 0),
    created_at             TIMESTAMP    NOT NULL DEFAULT now(),
    updated_at             TIMESTAMP    NOT NULL DEFAULT now(),
    CONSTRAINT uq_channel_mapping_node UNIQUE (integration_store_id, node_id)
);
CREATE INDEX IF NOT EXISTS idx_channel_mapping_store ON nx_channel_location_mappings (integration_store_id);

-- One row per publish attempt: the reconciliation header.
CREATE TABLE IF NOT EXISTS nx_inventory_publish_runs (
    id                   UUID PRIMARY KEY,
    tenant_id            UUID        NOT NULL,
    integration_store_id UUID        NOT NULL,
    status               VARCHAR(16) NOT NULL,      -- RUNNING, COMPLETED, PARTIAL, FAILED, BLOCKED
    dry_run              BOOLEAN     NOT NULL DEFAULT FALSE,
    triggered_by         VARCHAR(64),
    started_at           TIMESTAMP   NOT NULL DEFAULT now(),
    completed_at         TIMESTAMP,
    items_planned        INTEGER     NOT NULL DEFAULT 0,
    items_published      INTEGER     NOT NULL DEFAULT 0,
    items_unchanged      INTEGER     NOT NULL DEFAULT 0,
    items_failed         INTEGER     NOT NULL DEFAULT 0,
    items_skipped        INTEGER     NOT NULL DEFAULT 0,
    message              TEXT
);
CREATE INDEX IF NOT EXISTS idx_publish_runs_store ON nx_inventory_publish_runs (integration_store_id, started_at DESC);
-- At most one run in flight per store, enforced by the database rather than by hope.
CREATE UNIQUE INDEX IF NOT EXISTS uq_publish_runs_one_running
    ON nx_inventory_publish_runs (integration_store_id) WHERE status = 'RUNNING';

-- One row per SKU and channel location: what was calculated, from what, and what happened.
CREATE TABLE IF NOT EXISTS nx_inventory_publish_lines (
    id                   UUID PRIMARY KEY,
    tenant_id            UUID         NOT NULL,
    run_id               UUID         NOT NULL REFERENCES nx_inventory_publish_runs (id) ON DELETE CASCADE,
    sku                  VARCHAR(128) NOT NULL,
    external_location_id VARCHAR(64)  NOT NULL,
    inventory_item_id    BIGINT,
    calculated_qty       INTEGER      NOT NULL,
    channel_qty_before   INTEGER,
    published_qty        INTEGER,
    outcome              VARCHAR(16)  NOT NULL,     -- PLANNED, PUBLISHED, UNCHANGED, SKIPPED, FAILED
    drift                BOOLEAN      NOT NULL DEFAULT FALSE,
    calculation          TEXT,                      -- JSON: per-node contributions and exclusions
    error                TEXT,
    created_at           TIMESTAMP    NOT NULL DEFAULT now()
);
CREATE INDEX IF NOT EXISTS idx_publish_lines_run ON nx_inventory_publish_lines (run_id);

DO $$
DECLARE tbl TEXT;
BEGIN
    FOREACH tbl IN ARRAY ARRAY['nx_channel_location_mappings', 'nx_inventory_publish_runs', 'nx_inventory_publish_lines'] LOOP
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
