-- ============================================================
-- T-08: RFID / Serialized Inventory (EPC Registry)
-- One row per unique EPC (serialized tag). Supports two modes:
--   FULL_REGISTRY  = one-for-one serialized tracking
--   DECODE_TO_UPC  = traditional quantity uptick (EPC decoded to UPC)
-- ============================================================

-- 1. SERIALIZED INVENTORY (EPC registry).
CREATE TABLE IF NOT EXISTS nx_serialized_inventory (
    id              UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    tenant_id       UUID NOT NULL,
    location_id     UUID,               -- store / warehouse location
    epc             VARCHAR(128) NOT NULL,  -- unique EPC (serialized tag)
    sku             VARCHAR(100),           -- decoded UPC/SKU
    status          VARCHAR(20) NOT NULL DEFAULT 'ACTIVE',
                    -- ACTIVE | DAMAGED | ON_HOLD | MISSING | RETURNED
    mode            VARCHAR(20) NOT NULL DEFAULT 'FULL_REGISTRY',
                    -- FULL_REGISTRY | DECODE_TO_UPC
    received_at     TIMESTAMP,
    last_seen_at    TIMESTAMP,
    created_at      TIMESTAMP NOT NULL DEFAULT now(),
    updated_at      TIMESTAMP NOT NULL DEFAULT now(),
    UNIQUE (tenant_id, epc)
);
CREATE INDEX IF NOT EXISTS idx_serialized_inv_location ON nx_serialized_inventory(tenant_id, location_id, status);
CREATE INDEX IF NOT EXISTS idx_serialized_inv_sku ON nx_serialized_inventory(tenant_id, sku);

-- 2. RFID SCAN SESSION: batches EPC reads from a wand, dedups in-session.
CREATE TABLE IF NOT EXISTS nx_rfid_scan_session (
    id              UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    tenant_id       UUID NOT NULL,
    location_id     UUID,
    session_type    VARCHAR(20) NOT NULL,  -- RECEIVING | CYCLE_COUNT | FULFILLMENT
    mode            VARCHAR(20) NOT NULL DEFAULT 'FULL_REGISTRY',
    started_by      UUID,
    started_at      TIMESTAMP NOT NULL DEFAULT now(),
    completed_at    TIMESTAMP,
    status          VARCHAR(20) NOT NULL DEFAULT 'OPEN',  -- OPEN | COMPLETED | CANCELLED
    -- JSON: EPCs seen in this session (deduped). Foreign tags rejected here.
    seen_epcs       JSONB,
    rejected_epcs   JSONB
);
CREATE INDEX IF NOT EXISTS idx_rfid_session_tenant ON nx_rfid_scan_session(tenant_id, status);
