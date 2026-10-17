-- A store rejection now names the store, and records what happened to the order afterwards.
ALTER TABLE nx_order_rejections ADD COLUMN IF NOT EXISTS node_id UUID;
ALTER TABLE nx_order_rejections ADD COLUMN IF NOT EXISTS reroute_outcome VARCHAR(40);
ALTER TABLE nx_order_rejections ADD COLUMN IF NOT EXISTS reroute_note TEXT;
CREATE INDEX IF NOT EXISTS idx_rejection_node ON nx_order_rejections(node_id);

-- A re-route is its own allocation strategy; recording it as RULE_BASED or MANUAL would misstate how the node was chosen.
ALTER TABLE nx_order_allocations DROP CONSTRAINT IF EXISTS nx_order_allocations_allocation_strategy_check;
ALTER TABLE nx_order_allocations ADD CONSTRAINT nx_order_allocations_allocation_strategy_check
    CHECK (allocation_strategy IN ('RULE_BASED','AI_OPTIMIZED','HYBRID','MANUAL','REROUTE_AFTER_REJECTION'));
