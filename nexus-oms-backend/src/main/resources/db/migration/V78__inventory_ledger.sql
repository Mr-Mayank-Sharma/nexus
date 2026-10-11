-- Append-only inventory ledger: every change to a stock row's on-hand, allocated or reserved
-- quantity writes one row here, in the same transaction as the change. Deltas, not balances, so the
-- ledger can be summed and compared with the current rows to prove nothing moved off the books.
CREATE TABLE IF NOT EXISTS nx_inventory_movements (
    id              UUID PRIMARY KEY,
    tenant_id       UUID         NOT NULL,
    inventory_id    UUID,
    sku             VARCHAR(128) NOT NULL,
    node_id         UUID,
    movement_type   VARCHAR(24)  NOT NULL,   -- RECEIPT, SHIPMENT, ADJUSTMENT, CYCLE_COUNT, RESERVE, RELEASE
    on_hand_delta   INTEGER      NOT NULL DEFAULT 0,
    allocated_delta INTEGER      NOT NULL DEFAULT 0,
    reserved_delta  INTEGER      NOT NULL DEFAULT 0,
    reference_type  VARCHAR(32),             -- ORDER, RECEIPT, CYCLE_COUNT, MANUAL ...
    reference_id    VARCHAR(64),
    reason          TEXT,
    created_by      VARCHAR(128),
    created_at      TIMESTAMP    NOT NULL DEFAULT now()
);
CREATE INDEX IF NOT EXISTS idx_inv_mov_sku  ON nx_inventory_movements (tenant_id, sku, created_at DESC);
CREATE INDEX IF NOT EXISTS idx_inv_mov_time ON nx_inventory_movements (tenant_id, created_at DESC);
CREATE INDEX IF NOT EXISTS idx_inv_mov_ref  ON nx_inventory_movements (reference_type, reference_id);

-- A ledger that can be edited is not a ledger.
CREATE OR REPLACE FUNCTION nx_inventory_movements_immutable() RETURNS trigger AS $$
BEGIN
    RAISE EXCEPTION 'nx_inventory_movements is append-only (% not allowed)', TG_OP;
END;
$$ LANGUAGE plpgsql;
DROP TRIGGER IF EXISTS nx_inventory_movements_no_change ON nx_inventory_movements;
CREATE TRIGGER nx_inventory_movements_no_change
    BEFORE UPDATE OR DELETE ON nx_inventory_movements
    FOR EACH ROW EXECUTE FUNCTION nx_inventory_movements_immutable();

ALTER TABLE nx_inventory_movements ENABLE ROW LEVEL SECURITY;
ALTER TABLE nx_inventory_movements FORCE ROW LEVEL SECURITY;
DROP POLICY IF EXISTS tenant_isolation ON nx_inventory_movements;
CREATE POLICY tenant_isolation ON nx_inventory_movements
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

-- How many units of each order line have already been taken off stock (idempotency marker for shipping).
ALTER TABLE nx_order_items ADD COLUMN IF NOT EXISTS consumed_qty INTEGER NOT NULL DEFAULT 0;
