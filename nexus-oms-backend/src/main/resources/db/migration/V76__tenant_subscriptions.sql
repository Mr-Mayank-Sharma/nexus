-- Sellable editions (OMS, WMS, SUITE) and add-ons (THREE_PL, EDI, AI) per tenant.
-- No row = SUITE with every add-on, so existing tenants keep everything they have.
CREATE TABLE IF NOT EXISTS nx_tenant_subscriptions (
    tenant_id   UUID PRIMARY KEY,
    edition     VARCHAR(16) NOT NULL CHECK (edition IN ('OMS', 'WMS', 'SUITE')),
    add_ons     VARCHAR(255),
    valid_until TIMESTAMP,
    updated_at  TIMESTAMP DEFAULT now()
);

-- Same isolation as every other tenant table (V69).
ALTER TABLE nx_tenant_subscriptions ENABLE ROW LEVEL SECURITY;
ALTER TABLE nx_tenant_subscriptions FORCE ROW LEVEL SECURITY;
DROP POLICY IF EXISTS tenant_isolation ON nx_tenant_subscriptions;
CREATE POLICY tenant_isolation ON nx_tenant_subscriptions
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
