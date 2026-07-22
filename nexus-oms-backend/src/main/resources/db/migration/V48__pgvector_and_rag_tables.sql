-- V48: pgvector extension + RAG + semantic cache + cost tracking tables
-- Enterprise AI Architecture Phase 1

-- Enable pgvector extension
CREATE EXTENSION IF NOT EXISTS vector;

-- ============================================================
-- 1. AI RAG Documents — stored knowledge for retrieval
-- ============================================================
CREATE TABLE IF NOT EXISTS ai_rag_documents (
    id              UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    tenant_id       UUID NOT NULL,
    source_type     VARCHAR(50) NOT NULL,       -- ORDER, INVENTORY, PRODUCT, DOCUMENT, POLICY
    source_id       VARCHAR(100),               -- Reference to source entity
    title           VARCHAR(500) NOT NULL,
    content         TEXT NOT NULL,               -- Raw text content
    embedding       vector(1536),                -- OpenAI text-embedding-3-small dimension
    metadata_json   JSONB DEFAULT '{}',          -- Arbitrary metadata
    chunk_index     INT DEFAULT 0,               -- For multi-chunk documents
    token_count     INT DEFAULT 0,
    created_at      TIMESTAMP NOT NULL DEFAULT NOW(),
    updated_at      TIMESTAMP NOT NULL DEFAULT NOW()
);

CREATE UNIQUE INDEX IF NOT EXISTS idx_rag_documents_unique_source ON ai_rag_documents(tenant_id, source_type, source_id);
CREATE INDEX IF NOT EXISTS idx_rag_documents_tenant ON ai_rag_documents(tenant_id);
CREATE INDEX IF NOT EXISTS idx_rag_documents_embedding ON ai_rag_documents USING ivfflat (embedding vector_cosine_ops) WITH (lists = 100);

-- ============================================================
-- 2. AI Cost Tracking — per-request OpenAI cost ledger
-- ============================================================
CREATE TABLE IF NOT EXISTS ai_cost_tracking (
    id              UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    tenant_id       UUID NOT NULL,
    model_name      VARCHAR(100) NOT NULL,       -- gpt-4o, gpt-4o-mini, etc.
    operation_type  VARCHAR(50) NOT NULL,        -- CHAT, COMPLETION, EMBEDDING, INFERENCE
    input_tokens    INT NOT NULL DEFAULT 0,
    output_tokens   INT NOT NULL DEFAULT 0,
    total_tokens    INT NOT NULL DEFAULT 0,
    cost_usd        DECIMAL(12,6) NOT NULL DEFAULT 0,  -- Precise cost in USD
    request_id      VARCHAR(100),                -- Correlation ID
    endpoint        VARCHAR(200),                -- Which service called it
    latency_ms      INT DEFAULT 0,
    cached          BOOLEAN DEFAULT FALSE,       -- Was this served from cache?
    created_at      TIMESTAMP NOT NULL DEFAULT NOW()
);

CREATE INDEX IF NOT EXISTS idx_cost_tracking_tenant ON ai_cost_tracking(tenant_id, created_at DESC);
CREATE INDEX IF NOT EXISTS idx_cost_tracking_model ON ai_cost_tracking(model_name, created_at DESC);

-- ============================================================
-- 3. AI Semantic Cache — Redis-backed, DB-backed for persistence
-- ============================================================
CREATE TABLE IF NOT EXISTS ai_semantic_cache (
    id              UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    tenant_id       UUID NOT NULL,
    prompt_hash     VARCHAR(64) NOT NULL,         -- SHA-256 of normalized prompt
    prompt_text     TEXT,                         -- Original prompt (for debugging)
    response_text   TEXT NOT NULL,
    model_name      VARCHAR(100) NOT NULL,
    hit_count       INT DEFAULT 1,
    cost_saved_usd  DECIMAL(12,6) DEFAULT 0,     -- Estimated savings
    expires_at      TIMESTAMP NOT NULL,
    created_at      TIMESTAMP NOT NULL DEFAULT NOW(),
    last_hit_at     TIMESTAMP NOT NULL DEFAULT NOW()
);

CREATE UNIQUE INDEX IF NOT EXISTS idx_semantic_cache_hash ON ai_semantic_cache(tenant_id, prompt_hash);
CREATE INDEX IF NOT EXISTS idx_semantic_cache_expires ON ai_semantic_cache(expires_at);

-- ============================================================
-- 4. AI SHAP Explanations — LLM-generated natural-language explanations
-- ============================================================
CREATE TABLE IF NOT EXISTS ai_shap_explanations (
    id              UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    tenant_id       UUID NOT NULL,
    model_type      VARCHAR(50) NOT NULL,         -- DEMAND_FORECAST, CARRIER_OPTIMIZER, etc.
    prediction_id   VARCHAR(100),                 -- Reference to the prediction
    input_json      JSONB NOT NULL,               -- Features that went into prediction
    output_json     JSONB NOT NULL,               -- Prediction result
    shap_values     JSONB,                        -- Feature importance scores
    explanation     TEXT,                         -- Natural language explanation
    confidence      DECIMAL(5,4),
    created_at      TIMESTAMP NOT NULL DEFAULT NOW()
);

CREATE INDEX IF NOT EXISTS idx_shap_explanations_tenant ON ai_shap_explanations(tenant_id, model_type);
