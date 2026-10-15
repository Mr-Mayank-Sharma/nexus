-- Point-of-sale sources. A store's POS (or middleware in front of it) pushes the stock it counts; Nexus keeps
-- the latest reported quantity per SKU, compares it with its own on-hand for that store node, and only changes
-- Nexus stock when an operator reconciles. The shared secret is stored as a SHA-256 hash and shown once.
CREATE TABLE IF NOT EXISTS nx_pos_sources (
    id                  UUID PRIMARY KEY,
    tenant_id           UUID         NOT NULL,
    node_id             UUID         NOT NULL,
    name                VARCHAR(120) NOT NULL,
    vendor              VARCHAR(40)  NOT NULL DEFAULT 'CUSTOM',
    key_hash            VARCHAR(64)  NOT NULL,
    stale_after_minutes INTEGER      NOT NULL DEFAULT 120 CHECK (stale_after_minutes > 0),
    enabled             BOOLEAN      NOT NULL DEFAULT TRUE,
    last_snapshot_at    TIMESTAMP,
    created_at          TIMESTAMP    NOT NULL DEFAULT now(),
    CONSTRAINT uq_pos_source_node_name UNIQUE (node_id, name)
);
CREATE INDEX IF NOT EXISTS idx_pos_sources_node ON nx_pos_sources (tenant_id, node_id);

CREATE TABLE IF NOT EXISTS nx_pos_inventory_snapshots (
    id          UUID PRIMARY KEY,
    tenant_id   UUID         NOT NULL,
    source_id   UUID         NOT NULL,
    node_id     UUID         NOT NULL,
    sku         VARCHAR(128) NOT NULL,
    quantity    INTEGER      NOT NULL CHECK (quantity >= 0),
    reported_at TIMESTAMP    NOT NULL,
    received_at TIMESTAMP    NOT NULL DEFAULT now(),
    CONSTRAINT uq_pos_snapshot UNIQUE (source_id, sku)
);
CREATE INDEX IF NOT EXISTS idx_pos_snapshots_node ON nx_pos_inventory_snapshots (tenant_id, node_id);

DO $$
DECLARE tbl TEXT;
BEGIN
    FOREACH tbl IN ARRAY ARRAY['nx_pos_sources', 'nx_pos_inventory_snapshots'] LOOP
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
