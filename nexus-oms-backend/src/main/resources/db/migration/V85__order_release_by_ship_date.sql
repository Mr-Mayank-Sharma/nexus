-- Fulfilment starts at the right time: an order with a requested ship date far away (typically B2B, placed weeks or
-- months ahead) is not allocated, so it reserves no stock, until its release time = ship date minus a window. Until
-- then the same stock keeps serving the orders that ship sooner.
ALTER TABLE nx_orders ADD COLUMN IF NOT EXISTS order_type VARCHAR(10) NOT NULL DEFAULT 'B2C';
ALTER TABLE nx_orders ADD COLUMN IF NOT EXISTS requested_ship_date TIMESTAMP;
ALTER TABLE nx_orders ADD COLUMN IF NOT EXISTS release_at TIMESTAMP;      -- when fulfilment may start; null = no hold
ALTER TABLE nx_orders ADD COLUMN IF NOT EXISTS released_at TIMESTAMP;     -- when the hold ended (on time or by hand)
DO $$ BEGIN
    ALTER TABLE nx_orders ADD CONSTRAINT ck_nx_orders_order_type CHECK (order_type IN ('B2C', 'B2B'));
EXCEPTION WHEN duplicate_object THEN NULL; END $$;
CREATE INDEX IF NOT EXISTS idx_nx_orders_release_due ON nx_orders (release_at) WHERE released_at IS NULL AND release_at IS NOT NULL;

-- the window per order type, in days before the requested ship date
ALTER TABLE nx_routing_config ADD COLUMN IF NOT EXISTS release_days_b2b INTEGER NOT NULL DEFAULT 3;
ALTER TABLE nx_routing_config ADD COLUMN IF NOT EXISTS release_days_b2c INTEGER NOT NULL DEFAULT 1;
