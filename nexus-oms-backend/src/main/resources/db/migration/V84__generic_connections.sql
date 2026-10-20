-- Generic connections: any system Nexus talks to over a plain REST/JSON API, described by configuration
-- (endpoint, auth, paging, field mapping) instead of a hand-written connector. Used by the GENERIC_IMPORT and
-- GENERIC_EXPORT job types. The secret (token, password or API key) is sealed by the application before it is saved.
CREATE TABLE IF NOT EXISTS nx_generic_connections (
    id           UUID PRIMARY KEY,
    tenant_id    UUID         NOT NULL,
    name         VARCHAR(120) NOT NULL,
    direction    VARCHAR(10)  NOT NULL,            -- IMPORT or EXPORT
    entity_type  VARCHAR(30)  NOT NULL,            -- orders, inventory (import); orders, shipments, inventory (export)
    channel      VARCHAR(60),                      -- the channel name orders are tagged with, e.g. WOOCOMMERCE
    config       TEXT         NOT NULL DEFAULT '{}',
    secret       TEXT,
    watermark    TIMESTAMP,                        -- the last point fully imported/exported; incremental runs start here
    created_at   TIMESTAMP    NOT NULL DEFAULT now(),
    updated_at   TIMESTAMP    NOT NULL DEFAULT now(),
    CONSTRAINT uq_nx_generic_connections_name UNIQUE (tenant_id, name),
    CONSTRAINT ck_nx_generic_connections_direction CHECK (direction IN ('IMPORT', 'EXPORT'))
);

ALTER TABLE nx_generic_connections ENABLE ROW LEVEL SECURITY;
ALTER TABLE nx_generic_connections FORCE ROW LEVEL SECURITY;
DROP POLICY IF EXISTS tenant_isolation ON nx_generic_connections;
CREATE POLICY tenant_isolation ON nx_generic_connections
    USING (
        tenant_id IS NULL
        OR nullif(current_setting('app.current_tenant_id', true), '') IS NULL
        OR tenant_id = nullif(current_setting('app.current_tenant_id', true), '')::uuid
    )
    WITH CHECK (
        tenant_id IS NULL
        OR nullif(current_setting('app.current_tenant_id', true), '') IS NULL
        OR tenant_id = nullif(current_setting('app.current_tenant_id', true), '')::uuid
    );
