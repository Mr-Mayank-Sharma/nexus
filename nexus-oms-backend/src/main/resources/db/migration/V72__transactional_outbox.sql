-- V72: transactional outbox for domain events.
--
-- KafkaProducerService used to send to Kafka synchronously (30 s timeout) inside business
-- transactions: an event could be lost when Kafka was down, published for a transaction
-- that later rolled back, and a Kafka outage stalled order writes for 30 s each. Events
-- are now written here in the same transaction and relayed by OutboxRelay.

CREATE TABLE IF NOT EXISTS nx_outbox (
    id          UUID         PRIMARY KEY DEFAULT gen_random_uuid(),
    tenant_id   UUID,
    topic       VARCHAR(200) NOT NULL,
    msg_key     VARCHAR(200),
    payload     TEXT         NOT NULL,
    status      VARCHAR(16)  NOT NULL DEFAULT 'PENDING',
    attempts    INTEGER      NOT NULL DEFAULT 0,
    last_error  TEXT,
    created_at  TIMESTAMP    NOT NULL DEFAULT now(),
    sent_at     TIMESTAMP
);
CREATE INDEX IF NOT EXISTS ix_outbox_pending ON nx_outbox (status, created_at);

-- Same tenant policy as V69 (rows written by tenant requests stay tenant-scoped; the relay
-- runs without a tenant and sees all rows).
ALTER TABLE nx_outbox ENABLE ROW LEVEL SECURITY;
ALTER TABLE nx_outbox FORCE ROW LEVEL SECURITY;
DROP POLICY IF EXISTS tenant_isolation ON nx_outbox;
CREATE POLICY tenant_isolation ON nx_outbox
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

DO $$
BEGIN
    IF EXISTS (SELECT 1 FROM pg_roles WHERE rolname = 'nexus_app') THEN
        GRANT SELECT, INSERT, UPDATE, DELETE ON nx_outbox TO nexus_app;
    END IF;
END;
$$;
