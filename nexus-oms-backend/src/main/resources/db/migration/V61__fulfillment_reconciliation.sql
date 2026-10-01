-- ============================================================
-- T-09: Shopify→OMS Fulfillment Reconciliation
-- Ledger for cross-system fulfillment truth-acceptance & repair.
-- ============================================================

CREATE TABLE IF NOT EXISTS nx_fulfillment_reconciliation (
    id                      UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    tenant_id               UUID NOT NULL,
    order_id                UUID NOT NULL REFERENCES nx_orders(id),
    order_number            VARCHAR(64) NOT NULL,
    shopify_fulfillment_id  VARCHAR(64),
    match_type              VARCHAR(20) NOT NULL,   -- EXACT|PARTIAL|LOCATION_CHANGE|QUANTITY_DELTA|ANOMALY
    fulfilled_location      VARCHAR(64),
    routed_location         VARCHAR(64),
    fulfilled_items         JSONB,
    remaining_items         JSONB,
    resolution              VARCHAR(20) NOT NULL,   -- ACCEPTED|PARTIAL_ACCEPTED|FLAGGED|REPAIRED
    netsuite_sync_status    VARCHAR(20) DEFAULT 'PENDING',
    inventory_delta         INTEGER,
    notes                   TEXT,
    created_at              TIMESTAMP NOT NULL DEFAULT now(),
    updated_at              TIMESTAMP NOT NULL DEFAULT now()
);

CREATE INDEX IF NOT EXISTS idx_fulfill_recon_order  ON nx_fulfillment_reconciliation(tenant_id, order_id);
CREATE INDEX IF NOT EXISTS idx_fulfill_recon_status ON nx_fulfillment_reconciliation(netsuite_sync_status);

-- Track per-line fulfillment progress without destroying the original quantity.
ALTER TABLE nx_order_items ADD COLUMN IF NOT EXISTS fulfilled_qty INTEGER DEFAULT 0;