-- V49: Replace ivfflat with HNSW index for pgvector embeddings + webhook dedup ledger

-- ============================================================
-- 1. HNSW index for ai_rag_documents (replaces ivfflat)
-- ============================================================
-- ivfflat requires periodic REINDEX and degrades as data grows.
-- HNSW provides higher recall, better query throughput, and no reindexing.
-- Guard: only run if table exists (V48 may have failed in dev environments)
DO $$
BEGIN
    IF EXISTS (SELECT 1 FROM information_schema.tables WHERE table_name = 'ai_rag_documents') THEN
        DROP INDEX IF EXISTS idx_rag_documents_embedding;
        CREATE INDEX idx_rag_documents_embedding ON ai_rag_documents
            USING hnsw (embedding vector_cosine_ops) WITH (m = 16, ef_construction = 64);
    END IF;
END $$;

-- ============================================================
-- 2. Webhook deduplication ledger
-- ============================================================
-- Ensures idempotent processing of inbound Shopify/BigCommerce webhooks.
-- External order ID serves as the unique constraint to prevent double-allocation.
CREATE TABLE IF NOT EXISTS webhook_dedup_ledger (
    id              UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    tenant_id       UUID NOT NULL,
    source          VARCHAR(50) NOT NULL,           -- SHOPIFY, BIGCOMMERCE
    external_id     VARCHAR(100) NOT NULL,          -- shopify_order_id, bc_order_id, etc.
    event_id        VARCHAR(200),                   -- webhook event ID for dedup
    status          VARCHAR(20) DEFAULT 'PROCESSED', -- PROCESSED, SKIPPED_DUPLICATE
    processed_at    TIMESTAMP NOT NULL DEFAULT NOW(),
    UNIQUE (source, external_id)
);

CREATE INDEX IF NOT EXISTS idx_webhook_dedup_source_external ON webhook_dedup_ledger(source, external_id);
CREATE INDEX IF NOT EXISTS idx_webhook_dedup_tenant ON webhook_dedup_ledger(tenant_id, processed_at DESC);
