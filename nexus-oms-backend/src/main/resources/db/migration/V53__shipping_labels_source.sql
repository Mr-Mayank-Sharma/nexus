-- Shipping labels: mark how the label was produced.
-- SIMULATED = generated locally without a real carrier API call.
-- CARRIER  = returned by a real carrier/connector purchase.
ALTER TABLE nx_shipping_labels ADD COLUMN IF NOT EXISTS label_source VARCHAR(20) DEFAULT 'SIMULATED';
