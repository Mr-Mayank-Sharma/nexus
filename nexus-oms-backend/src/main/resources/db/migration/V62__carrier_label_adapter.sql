-- ============================================================
-- T-12: Carrier label adapter config.
-- Per-tenant carrier label settings: which adapter, label format,
-- required fields. Adding a carrier = a config row + adapter bean,
-- not a code change.
-- ============================================================

CREATE TABLE IF NOT EXISTS nx_carrier_label_config (
    id              UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    tenant_id       UUID NOT NULL,
    carrier_code    VARCHAR(32) NOT NULL,   -- JITSU, SAPI, VHO, FEDEX, UPS...
    adapter_name    VARCHAR(64) NOT NULL,   -- JitsuCarrierAdapter, SapiCarrierAdapter...
    label_format    VARCHAR(8)  NOT NULL DEFAULT 'PDF',  -- ZPL | PDF
    -- JSON: required fields that must appear on the label (ship-from, ship-to,
    -- service, tracking, weight, dimensions). Validated before print.
    required_fields JSONB,
    is_active       BOOLEAN NOT NULL DEFAULT TRUE,
    created_at      TIMESTAMP NOT NULL DEFAULT now(),
    updated_at      TIMESTAMP NOT NULL DEFAULT now(),
    UNIQUE (tenant_id, carrier_code)
);
CREATE INDEX IF NOT EXISTS idx_carrier_label_config_tenant ON nx_carrier_label_config(tenant_id, is_active);

-- Extend shipping labels with the adapter + format used to produce them.
ALTER TABLE nx_shipping_labels ADD COLUMN IF NOT EXISTS label_format VARCHAR(8) DEFAULT 'PDF';
ALTER TABLE nx_shipping_labels ADD COLUMN IF NOT EXISTS adapter_name VARCHAR(64);