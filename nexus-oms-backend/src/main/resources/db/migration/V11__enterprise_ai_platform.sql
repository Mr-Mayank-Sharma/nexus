-- ============================================================
-- Enterprise AI Platform
-- Model Registry, Feature Store, Training Pipelines,
-- Inference Gateway, Monitoring, Rule Engine, RAG
-- ============================================================

-- 1. MODEL REGISTRY
CREATE TABLE IF NOT EXISTS ai_models (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    tenant_id UUID,
    name VARCHAR(255) NOT NULL,
    display_name VARCHAR(255),
    description TEXT,
    model_type VARCHAR(50) NOT NULL,
    category VARCHAR(30) NOT NULL CHECK (category IN ('GLOBAL', 'TENANT', 'HYBRID')),
    base_model_id UUID REFERENCES ai_models(id),
    status VARCHAR(20) DEFAULT 'DRAFT' CHECK (status IN ('DRAFT','ACTIVE','TRAINING','WARNING','ERROR','DISABLED','ARCHIVED')),
    current_version VARCHAR(50),
    input_schema JSONB,
    output_schema JSONB,
    config JSONB,
    tags TEXT[],
    is_active BOOLEAN DEFAULT true,
    created_by VARCHAR(255),
    created_at TIMESTAMP DEFAULT now(),
    updated_at TIMESTAMP DEFAULT now()
);
CREATE INDEX idx_ai_models_tenant ON ai_models(tenant_id);
CREATE INDEX idx_ai_models_type ON ai_models(model_type);
CREATE INDEX idx_ai_models_status ON ai_models(status);

-- 2. MODEL VERSIONS
CREATE TABLE IF NOT EXISTS ai_model_versions (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    model_id UUID NOT NULL REFERENCES ai_models(id) ON DELETE CASCADE,
    version VARCHAR(50) NOT NULL,
    model_file_url TEXT,
    model_size_bytes BIGINT,
    framework VARCHAR(100),
    framework_version VARCHAR(50),
    accuracy DECIMAL(5,2),
    precision DECIMAL(5,2),
    recall DECIMAL(5,2),
    f1_score DECIMAL(5,2),
    latency_ms DECIMAL(10,2),
    training_dataset_id UUID,
    validation_dataset_id UUID,
    test_dataset_id UUID,
    training_job_id UUID,
    metrics JSONB,
    parameters JSONB,
    commit_message TEXT,
    status VARCHAR(20) DEFAULT 'STAGED' CHECK (status IN ('STAGED','VALIDATING','VALIDATED','DEPLOYED','ROLLED_BACK','FAILED','ARCHIVED')),
    validated_by VARCHAR(255),
    validated_at TIMESTAMP,
    deployed_by VARCHAR(255),
    deployed_at TIMESTAMP,
    created_by VARCHAR(255),
    created_at TIMESTAMP DEFAULT now(),
    UNIQUE(model_id, version)
);
CREATE INDEX idx_ai_model_versions_model ON ai_model_versions(model_id);
CREATE INDEX idx_ai_model_versions_status ON ai_model_versions(status);

-- 3. DEPLOYMENTS (per tenant, per model)
CREATE TABLE IF NOT EXISTS ai_deployments (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    tenant_id UUID NOT NULL,
    model_id UUID NOT NULL REFERENCES ai_models(id) ON DELETE CASCADE,
    version_id UUID NOT NULL REFERENCES ai_model_versions(id) ON DELETE CASCADE,
    environment VARCHAR(20) DEFAULT 'PRODUCTION' CHECK (environment IN ('DEVELOPMENT','STAGING','PRODUCTION','CANARY')),
    traffic_weight DECIMAL(3,2) DEFAULT 1.00,
    endpoint_url TEXT,
    config_overrides JSONB,
    status VARCHAR(20) DEFAULT 'PENDING' CHECK (status IN ('PENDING','DEPLOYING','ACTIVE','FAILED','ROLLING_BACK','ROLLED_BACK')),
    deployed_by VARCHAR(255),
    deployed_at TIMESTAMP DEFAULT now(),
    created_at TIMESTAMP DEFAULT now(),
    updated_at TIMESTAMP DEFAULT now(),
    UNIQUE(tenant_id, model_id, environment)
);
CREATE INDEX idx_ai_deployments_tenant ON ai_deployments(tenant_id);
CREATE INDEX idx_ai_deployments_active ON ai_deployments(tenant_id, status);

-- 4. FEATURE DEFINITIONS
CREATE TABLE IF NOT EXISTS ai_feature_definitions (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    tenant_id UUID,
    name VARCHAR(255) NOT NULL,
    display_name VARCHAR(255),
    description TEXT,
    feature_group VARCHAR(100) NOT NULL,
    data_type VARCHAR(50) NOT NULL,
    entity_type VARCHAR(100),
    source_type VARCHAR(30) CHECK (source_type IN ('OMS_TABLE','KAFKA_STREAM','EXTERNAL_API','DERIVED','MANUAL')),
    source_config JSONB,
    transformation_sql TEXT,
    is_categorical BOOLEAN DEFAULT false,
    cardinality INT,
    default_value VARCHAR(255),
    is_active BOOLEAN DEFAULT true,
    version INT DEFAULT 1,
    metadata JSONB,
    created_by VARCHAR(255),
    created_at TIMESTAMP DEFAULT now(),
    updated_at TIMESTAMP DEFAULT now(),
    UNIQUE(tenant_id, name)
);
CREATE INDEX idx_ai_features_group ON ai_feature_definitions(feature_group);
CREATE INDEX idx_ai_features_entity ON ai_feature_definitions(entity_type);

-- 5. FEATURE VALUES (materialized feature store)
CREATE TABLE IF NOT EXISTS ai_feature_values (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    tenant_id UUID NOT NULL,
    feature_id UUID NOT NULL REFERENCES ai_feature_definitions(id) ON DELETE CASCADE,
    entity_id VARCHAR(255) NOT NULL,
    entity_type VARCHAR(100),
    value TEXT,
    numeric_value DECIMAL(20,6),
    bool_value BOOLEAN,
    json_value JSONB,
    timestamp_value TIMESTAMP,
    as_of_date DATE NOT NULL,
    created_at TIMESTAMP DEFAULT now()
);
CREATE INDEX idx_ai_feature_values_lookup ON ai_feature_values(tenant_id, feature_id, entity_id, as_of_date);
CREATE INDEX idx_ai_feature_values_date ON ai_feature_values(as_of_date);

-- 6. TRAINING JOBS
CREATE TABLE IF NOT EXISTS ai_training_jobs (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    tenant_id UUID,
    model_id UUID NOT NULL REFERENCES ai_models(id) ON DELETE CASCADE,
    name VARCHAR(255),
    version VARCHAR(50),
    status VARCHAR(20) DEFAULT 'PENDING' CHECK (status IN ('PENDING','RUNNING','VALIDATING','COMPLETED','FAILED','ROLLED_BACK','CANCELLED')),
    job_type VARCHAR(30) DEFAULT 'SCHEDULED' CHECK (job_type IN ('MANUAL','SCHEDULED','TRIGGERED','BACKFILL')),
    trigger_reason TEXT,
    config JSONB,
    hyperparameters JSONB,
    training_dataset_id UUID,
    validation_dataset_id UUID,
    accuracy DECIMAL(5,2),
    precision DECIMAL(5,2),
    recall DECIMAL(5,2),
    f1_score DECIMAL(5,2),
    loss DECIMAL(10,6),
    drift_score DECIMAL(5,2),
    epochs INT,
    dataset_size INT,
    duration_seconds INT,
    model_size_bytes BIGINT,
    error_message TEXT,
    logs TEXT,
    started_at TIMESTAMP,
    completed_at TIMESTAMP,
    created_by VARCHAR(255),
    created_at TIMESTAMP DEFAULT now(),
    updated_at TIMESTAMP DEFAULT now()
);
CREATE INDEX idx_ai_training_model ON ai_training_jobs(model_id);
CREATE INDEX idx_ai_training_status ON ai_training_jobs(status);
CREATE INDEX idx_ai_training_tenant ON ai_training_jobs(tenant_id);

-- 7. INFERENCE LOGS (audit trail)
CREATE TABLE IF NOT EXISTS ai_inference_logs (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    tenant_id UUID NOT NULL,
    model_id UUID NOT NULL REFERENCES ai_models(id) ON DELETE CASCADE,
    version_id UUID REFERENCES ai_model_versions(id),
    deployment_id UUID REFERENCES ai_deployments(id),
    request_id VARCHAR(255),
    input_data JSONB,
    output_data JSONB,
    confidence DECIMAL(5,2),
    latency_ms DECIMAL(10,2),
    status VARCHAR(20) DEFAULT 'SUCCESS' CHECK (status IN ('SUCCESS','PARTIAL','FAILED','FALLBACK')),
    fallback_used BOOLEAN DEFAULT false,
    fallback_reason VARCHAR(50),
    rule_engine_used BOOLEAN DEFAULT false,
    user_overridden BOOLEAN DEFAULT false,
    user_override_value JSONB,
    user_id VARCHAR(255),
    source_service VARCHAR(100),
    cost DECIMAL(10,6),
    tokens_used INT,
    created_at TIMESTAMP DEFAULT now()
);
CREATE INDEX idx_ai_inference_tenant ON ai_inference_logs(tenant_id);
CREATE INDEX idx_ai_inference_model ON ai_inference_logs(model_id);
CREATE INDEX idx_ai_inference_time ON ai_inference_logs(created_at);
CREATE INDEX idx_ai_inference_status ON ai_inference_logs(status);

-- 8. DATASETS
CREATE TABLE IF NOT EXISTS ai_datasets (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    tenant_id UUID,
    name VARCHAR(255) NOT NULL,
    description TEXT,
    dataset_type VARCHAR(30) CHECK (dataset_type IN ('TRAINING','VALIDATION','TEST','EVALUATION','BACKFILL')),
    source_query TEXT,
    record_count INT,
    size_bytes BIGINT,
    storage_url TEXT,
    schema_def JSONB,
    statistics JSONB,
    status VARCHAR(20) DEFAULT 'PENDING' CHECK (status IN ('PENDING','GENERATING','READY','FAILED','ARCHIVED')),
    is_active BOOLEAN DEFAULT true,
    created_by VARCHAR(255),
    created_at TIMESTAMP DEFAULT now(),
    updated_at TIMESTAMP DEFAULT now()
);
CREATE INDEX idx_ai_datasets_tenant ON ai_datasets(tenant_id);
CREATE INDEX idx_ai_datasets_type ON ai_datasets(dataset_type);

-- 9. EXPERIMENTS (A/B testing)
CREATE TABLE IF NOT EXISTS ai_experiments (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    tenant_id UUID NOT NULL,
    model_id UUID NOT NULL REFERENCES ai_models(id) ON DELETE CASCADE,
    name VARCHAR(255) NOT NULL,
    description TEXT,
    experiment_type VARCHAR(30) CHECK (experiment_type IN ('A_B_TEST','CHAMPION_CHALLENGER','MULTIVARIATE','CANARY')),
    champion_version_id UUID REFERENCES ai_model_versions(id),
    challenger_version_id UUID REFERENCES ai_model_versions(id),
    traffic_split DECIMAL(3,2) DEFAULT 0.50,
    success_metric VARCHAR(100),
    status VARCHAR(20) DEFAULT 'DRAFT' CHECK (status IN ('DRAFT','RUNNING','COMPLETED','ROLLED_BACK','FAILED')),
    winner_version_id UUID REFERENCES ai_model_versions(id),
    start_date TIMESTAMP,
    end_date TIMESTAMP,
    results JSONB,
    created_by VARCHAR(255),
    created_at TIMESTAMP DEFAULT now(),
    updated_at TIMESTAMP DEFAULT now()
);
CREATE INDEX idx_ai_experiments_tenant ON ai_experiments(tenant_id);

-- 10. PROMPT MANAGEMENT (for LLM features)
CREATE TABLE IF NOT EXISTS ai_prompts (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    tenant_id UUID,
    name VARCHAR(255) NOT NULL,
    description TEXT,
    model_id UUID REFERENCES ai_models(id),
    prompt_template TEXT NOT NULL,
    variables JSONB,
    response_schema JSONB,
    temperature DECIMAL(3,2) DEFAULT 0.7,
    max_tokens INT DEFAULT 1024,
    version INT DEFAULT 1,
    status VARCHAR(20) DEFAULT 'ACTIVE' CHECK (status IN ('ACTIVE','DRAFT','DEPRECATED','ARCHIVED')),
    tags TEXT[],
    created_by VARCHAR(255),
    created_at TIMESTAMP DEFAULT now(),
    updated_at TIMESTAMP DEFAULT now(),
    UNIQUE(tenant_id, name, version)
);
CREATE INDEX idx_ai_prompts_model ON ai_prompts(model_id);

-- 11. KNOWLEDGE BASES (RAG)
CREATE TABLE IF NOT EXISTS ai_knowledge_bases (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    tenant_id UUID,
    name VARCHAR(255) NOT NULL,
    description TEXT,
    embedding_model VARCHAR(255),
    chunking_strategy VARCHAR(50) DEFAULT 'RECURSIVE',
    chunk_size INT DEFAULT 512,
    chunk_overlap INT DEFAULT 64,
    status VARCHAR(20) DEFAULT 'ACTIVE' CHECK (status IN ('ACTIVE','INDEXING','FAILED','ARCHIVED')),
    document_count INT DEFAULT 0,
    vector_size INT,
    config JSONB,
    created_by VARCHAR(255),
    created_at TIMESTAMP DEFAULT now(),
    updated_at TIMESTAMP DEFAULT now(),
    UNIQUE(tenant_id, name)
);
CREATE INDEX idx_ai_kb_tenant ON ai_knowledge_bases(tenant_id);

-- 12. KNOWLEDGE DOCUMENTS
CREATE TABLE IF NOT EXISTS ai_knowledge_documents (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    knowledge_base_id UUID NOT NULL REFERENCES ai_knowledge_bases(id) ON DELETE CASCADE,
    tenant_id UUID,
    title VARCHAR(512),
    content TEXT,
    content_type VARCHAR(50),
    source_url TEXT,
    file_path TEXT,
    file_size_bytes BIGINT,
    chunk_count INT,
    embedding_status VARCHAR(20) DEFAULT 'PENDING' CHECK (embedding_status IN ('PENDING','PROCESSING','EMBEDDED','FAILED')),
    metadata JSONB,
    checksum VARCHAR(64),
    created_by VARCHAR(255),
    created_at TIMESTAMP DEFAULT now(),
    updated_at TIMESTAMP DEFAULT now()
);
CREATE INDEX idx_ai_kb_docs_base ON ai_knowledge_documents(knowledge_base_id);
CREATE INDEX idx_ai_kb_docs_status ON ai_knowledge_documents(embedding_status);

-- 13. RULE ENGINE FALLBACKS
CREATE TABLE IF NOT EXISTS ai_rule_fallbacks (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    tenant_id UUID,
    model_id UUID NOT NULL REFERENCES ai_models(id) ON DELETE CASCADE,
    name VARCHAR(255) NOT NULL,
    description TEXT,
    priority INT DEFAULT 100,
    condition_expression TEXT,
    action_expression TEXT,
    action_type VARCHAR(50) CHECK (action_type IN ('STATIC_VALUE','FORMULA','SERVICE_CALL','LOOKUP_TABLE','THRESHOLD_RULE')),
    action_config JSONB,
    confidence_threshold_low DECIMAL(5,2) DEFAULT 0.0,
    confidence_threshold_high DECIMAL(5,2) DEFAULT 0.80,
    is_active BOOLEAN DEFAULT true,
    created_by VARCHAR(255),
    created_at TIMESTAMP DEFAULT now(),
    updated_at TIMESTAMP DEFAULT now()
);
CREATE INDEX idx_ai_fallbacks_model ON ai_rule_fallbacks(model_id);

-- 14. MODEL METRICS HISTORY
CREATE TABLE IF NOT EXISTS ai_model_metrics (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    tenant_id UUID,
    model_id UUID NOT NULL REFERENCES ai_models(id) ON DELETE CASCADE,
    version_id UUID REFERENCES ai_model_versions(id),
    metric_name VARCHAR(100) NOT NULL,
    metric_value DECIMAL(20,6) NOT NULL,
    recorded_at TIMESTAMP DEFAULT now(),
    metadata JSONB
);
CREATE INDEX idx_ai_metrics_model ON ai_model_metrics(model_id);
CREATE INDEX idx_ai_metrics_time ON ai_model_metrics(recorded_at);

-- 15. AI GATEWAY ROUTES
CREATE TABLE IF NOT EXISTS ai_gateway_routes (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    tenant_id UUID,
    name VARCHAR(255) NOT NULL,
    model_type VARCHAR(50) NOT NULL,
    route_pattern VARCHAR(255),
    target_endpoint TEXT,
    fallback_strategy VARCHAR(30) DEFAULT 'RULE_ENGINE' CHECK (fallback_strategy IN ('RULE_ENGINE','CACHE','ALTERNATE_MODEL','ERROR')),
    fallback_model_id UUID REFERENCES ai_models(id),
    rate_limit_per_minute INT DEFAULT 1000,
    timeout_ms INT DEFAULT 5000,
    retry_count INT DEFAULT 2,
    is_active BOOLEAN DEFAULT true,
    metadata JSONB,
    created_by VARCHAR(255),
    created_at TIMESTAMP DEFAULT now(),
    updated_at TIMESTAMP DEFAULT now(),
    UNIQUE(tenant_id, model_type)
);
CREATE INDEX idx_ai_gateway_tenant ON ai_gateway_routes(tenant_id);

-- 16. COMPUTE RESOURCE ALLOCATION
CREATE TABLE IF NOT EXISTS ai_compute_resources (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    tenant_id UUID,
    name VARCHAR(255) NOT NULL,
    resource_type VARCHAR(30) CHECK (resource_type IN ('CPU','GPU','TPU','MEMORY','STORAGE')),
    provider VARCHAR(50) DEFAULT 'KUBERNETES',
    config JSONB,
    allocated_units DECIMAL(10,2),
    used_units DECIMAL(10,2) DEFAULT 0,
    cost_per_unit DECIMAL(10,6),
    is_active BOOLEAN DEFAULT true,
    created_at TIMESTAMP DEFAULT now(),
    updated_at TIMESTAMP DEFAULT now()
);

-- 17. MODEL COST LOGS
CREATE TABLE IF NOT EXISTS ai_cost_logs (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    tenant_id UUID NOT NULL,
    model_id UUID NOT NULL REFERENCES ai_models(id) ON DELETE CASCADE,
    version_id UUID REFERENCES ai_model_versions(id),
    cost_type VARCHAR(30) CHECK (cost_type IN ('TRAINING','INFERENCE','STORAGE','COMPUTE')),
    amount DECIMAL(12,6) NOT NULL,
    currency VARCHAR(3) DEFAULT 'USD',
    tokens_used INT,
    compute_hours DECIMAL(10,4),
    recorded_at TIMESTAMP DEFAULT now(),
    description TEXT
);
CREATE INDEX idx_ai_costs_tenant ON ai_cost_logs(tenant_id);
CREATE INDEX idx_ai_costs_model ON ai_cost_logs(model_id);
CREATE INDEX idx_ai_costs_month ON ai_cost_logs(date_trunc('month', recorded_at));

-- Seed default AI model definitions
INSERT INTO ai_models (name, display_name, description, model_type, category, status, input_schema, output_schema) VALUES
('demand_forecaster', 'Demand Forecaster', 'Predicts future product demand per warehouse based on historical orders, promotions, holidays, weather, and marketing campaigns.', 'DEMAND_FORECAST', 'TENANT', 'DRAFT',
 '{"type":"object","properties":{"productId":{"type":"string"},"warehouseId":{"type":"string"},"date":{"type":"string","format":"date"},"lookbackDays":{"type":"integer","default":365}}}',
 '{"type":"object","properties":{"predictedOrders":{"type":"number"},"confidence":{"type":"number"},"lowerBound":{"type":"number"},"upperBound":{"type":"number"},"unit":{"type":"string"}}}'),
('smart_allocator', 'Smart Allocator', 'Selects optimal warehouse for order fulfillment based on inventory, capacity, customer location, SLA, and shipping cost.', 'SMART_ALLOCATOR', 'TENANT', 'DRAFT',
 '{"type":"object","properties":{"orderId":{"type":"string"},"items":{"type":"array"},"customerZip":{"type":"string"},"customerLat":{"type":"number"},"customerLng":{"type":"number"},"sla":{"type":"string"}}}',
 '{"type":"object","properties":{"warehouseId":{"type":"string"},"warehouseName":{"type":"string"},"shippingCost":{"type":"number"},"estimatedDays":{"type":"number"},"confidence":{"type":"number"},"alternatives":{"type":"array"}}}'),
('carrier_optimizer', 'Carrier Optimizer', 'Chooses optimal shipping carrier using global reliability data and tenant-specific pricing/SLA contracts.', 'CARRIER_OPTIMIZER', 'HYBRID', 'DRAFT',
 '{"type":"object","properties":{"originZip":{"type":"string"},"destinationZip":{"type":"string"},"weight":{"type":"number"},"dimensions":{"type":"object"},"sla":{"type":"string"},"declaredValue":{"type":"number"}}}',
 '{"type":"object","properties":{"carrier":{"type":"string"},"serviceLevel":{"type":"string"},"cost":{"type":"number"},"estimatedDelivery":{"type":"string"},"confidence":{"type":"number"},"alternatives":{"type":"array"}}}'),
('returns_predictor', 'Returns Predictor', 'Predicts probability of order/item return based on product category, customer history, season, and channel.', 'RETURNS_PREDICTOR', 'TENANT', 'DRAFT',
 '{"type":"object","properties":{"productId":{"type":"string"},"category":{"type":"string"},"customerId":{"type":"string"},"orderValue":{"type":"number"},"channel":{"type":"string"},"season":{"type":"string"}}}',
 '{"type":"object","properties":{"returnProbability":{"type":"number"},"expectedReturnDate":{"type":"string"},"confidence":{"type":"number"},"topReasons":{"type":"array"}}}'),
('inventory_optimizer', 'Inventory Optimizer', 'Recommends reorder points, safety stock levels, and replenishment quantities per SKU per warehouse.', 'INVENTORY_OPTIMIZER', 'TENANT', 'DRAFT',
 '{"type":"object","properties":{"sku":{"type":"string"},"warehouseId":{"type":"string"},"leadTime":{"type":"number"},"moq":{"type":"number"},"holdingCost":{"type":"number"},"stockoutCost":{"type":"number"}}}',
 '{"type":"object","properties":{"reorderPoint":{"type":"number"},"safetyStock":{"type":"number"},"reorderQty":{"type":"number"},"confidence":{"type":"number"},"riskLevel":{"type":"string"}}}'),
('anomaly_detector', 'Anomaly Detector', 'Detects fraudulent orders, unusual system behavior, and data anomalies using global fraud patterns and tenant-specific buying behavior.', 'ANOMALY_DETECTOR', 'HYBRID', 'DRAFT',
 '{"type":"object","properties":{"entityType":{"type":"string"},"entityId":{"type":"string"},"features":{"type":"object"}}}',
 '{"type":"object","properties":{"isAnomaly":{"type":"boolean"},"anomalyScore":{"type":"number"},"confidence":{"type":"number"},"reasons":{"type":"array"},"severity":{"type":"string"}}}'),
('ai_assistant', 'AI Assistant', 'Natural language assistant for OMS operations using RAG with tenant-specific knowledge base and shared foundation model.', 'AI_ASSISTANT', 'HYBRID', 'DRAFT',
 '{"type":"object","properties":{"query":{"type":"string"},"context":{"type":"object"},"conversationId":{"type":"string"}}}',
 '{"type":"object","properties":{"response":{"type":"string"},"sources":{"type":"array"},"confidence":{"type":"number"},"suggestedActions":{"type":"array"}}}'),
('document_ai', 'Document AI', 'Extracts structured data from invoices, POs, packing slips, and other documents using OCR and classification.', 'DOCUMENT_AI', 'GLOBAL', 'DRAFT',
 '{"type":"object","properties":{"documentType":{"type":"string"},"imageUrl":{"type":"string"},"base64Content":{"type":"string"}}}',
 '{"type":"object","properties":{"extractedFields":{"type":"object"},"confidence":{"type":"number"},"documentClass":{"type":"string"},"pages":{"type":"array"}}}');

-- Seed default gateway routes
INSERT INTO ai_gateway_routes (name, model_type, route_pattern, fallback_strategy, rate_limit_per_minute, timeout_ms, retry_count) VALUES
('Demand Forecast Route', 'DEMAND_FORECAST', '/api/ai/predict/demand', 'RULE_ENGINE', 500, 5000, 2),
('Smart Allocation Route', 'SMART_ALLOCATOR', '/api/ai/predict/allocate', 'RULE_ENGINE', 1000, 3000, 2),
('Carrier Optimizer Route', 'CARRIER_OPTIMIZER', '/api/ai/predict/carrier', 'RULE_ENGINE', 1000, 3000, 2),
('Returns Predictor Route', 'RETURNS_PREDICTOR', '/api/ai/predict/returns', 'RULE_ENGINE', 500, 5000, 2),
('Inventory Optimizer Route', 'INVENTORY_OPTIMIZER', '/api/ai/predict/inventory', 'RULE_ENGINE', 500, 5000, 2),
('Anomaly Detector Route', 'ANOMALY_DETECTOR', '/api/ai/predict/anomaly', 'RULE_ENGINE', 2000, 2000, 3),
('AI Assistant Route', 'AI_ASSISTANT', '/api/ai/assistant', 'ERROR', 200, 10000, 1),
('Document AI Route', 'DOCUMENT_AI', '/api/ai/document', 'ERROR', 100, 30000, 2);

-- Seed default rule fallbacks for key models
INSERT INTO ai_rule_fallbacks (model_id, name, description, priority, action_type, action_config, confidence_threshold_low)
SELECT id, 'Demand Fallback — Moving Average', 'Fallback to historical moving average when AI is unavailable or low confidence', 100, 'FORMULA',
 '{"formula":"moving_average","lookbackDays":90,"defaultValue":0}', 0.80
FROM ai_models WHERE name = 'demand_forecaster';

INSERT INTO ai_rule_fallbacks (model_id, name, description, priority, action_type, action_config, confidence_threshold_low)
SELECT id, 'Allocation Fallback — Nearest Warehouse', 'Fallback to nearest warehouse rule when AI is unavailable', 100, 'LOOKUP_TABLE',
 '{"strategy":"NEAREST_WAREHOUSE","fallbackField":"warehousePriority"}', 0.80
FROM ai_models WHERE name = 'smart_allocator';

INSERT INTO ai_rule_fallbacks (model_id, name, description, priority, action_type, action_config, confidence_threshold_low)
SELECT id, 'Carrier Fallback — Cheapest Carrier', 'Fallback to cheapest available carrier when AI is unavailable', 100, 'FORMULA',
 '{"strategy":"CHEAPEST_CARRIER","preferredCarriers":[]}', 0.80
FROM ai_models WHERE name = 'carrier_optimizer';

INSERT INTO ai_rule_fallbacks (model_id, name, description, priority, action_type, action_config, confidence_threshold_low)
SELECT id, 'Anomaly Fallback — Rule Engine', 'Fallback to threshold-based rule engine when AI is unavailable', 100, 'THRESHOLD_RULE',
 '{"rules":[{"field":"orderValue","operator":"gt","value":10000,"severity":"HIGH"},{"field":"newCustomer","operator":"eq","value":true,"severity":"MEDIUM"}]}', 0.80
FROM ai_models WHERE name = 'anomaly_detector';
