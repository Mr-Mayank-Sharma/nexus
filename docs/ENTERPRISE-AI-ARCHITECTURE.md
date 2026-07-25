# NexusShip Enterprise AI Architecture — Complete Design Document

> **Version:** 1.0  
> **Date:** July 2026  
> **Status:** Design Phase  
> **Benchmark Tier:** Manhattan Active Omni / Blue Yonder Luminate / SAP IBP

---

## Table of Contents

1. [Executive Summary](#1-executive-summary)
2. [Current State Analysis](#2-current-state-analysis)
3. [Industry Benchmark Analysis](#3-industry-benchmark-analysis)
4. [Target Architecture](#4-target-architecture)
5. [AI Orchestrator Redesign — ChatGPT Integration](#5-ai-orchestrator-redesign--chatgpt-integration)
6. [Multi-Agent Architecture](#6-multi-agent-architecture)
7. [ML Model Strategy](#7-ml-model-strategy)
8. [RAG & Knowledge Architecture](#8-rag--knowledge-architecture)
9. [Governance, Explainability & Observability](#9-governance-explainability--observability)
10. [Data Architecture](#10-data-architecture)
11. [Integration Patterns](#11-integration-patterns)
12. [Algorithm Selection Matrix](#12-algorithm-selection-matrix)
13. [Phased Implementation Roadmap](#13-phased-implementation-roadmap)
14. [Infrastructure Requirements](#14-infrastructure-requirements)

---

## 1. Executive Summary

### The Problem

NexusShip's AI layer currently operates as **simulated intelligence**. All 11 backend AI services contain hardcoded switch-case logic, random number generators, and static response strings. The 6 Python ML models generate synthetic training data and produce mock predictions. Nothing connects to real data, real optimization, or real LLM reasoning.

### The Vision

Transform NexusShip into an **enterprise-grade AI-powered supply chain platform** comparable to Manhattan Active Omni, Blue Yonder Luminate, and SAP IBP — while preserving the existing codebase structure and adding **ChatGPT (GPT-4o)** as the reasoning backbone for all AI orchestrators.

### Key Design Decisions

| Decision | Choice | Rationale |
|----------|--------|-----------|
| LLM Provider | OpenAI GPT-4o | User requirement; best-in-class reasoning |
| Orchestrator Pattern | Multi-Agent Supervisor | Industry standard (Manhattan Agent Foundry™, Blue Yonder Luminate) |
| ML Framework | XGBoost + LightGBM | 20-30% accuracy lift over current random/synthetic |
| Optimization Solver | Google OR-Tools (MILP) | Open-source, scales to enterprise; Gurobi for premium |
| Vector Database | pgvector (PostgreSQL) | Leverages existing PostgreSQL 16; sufficient for <1M docs |
| Knowledge Architecture | Hybrid RAG (dense + sparse + reranking) | ~80% hallucination reduction vs base LLM |
| Explainability | SHAP + LLM-generated explanations | Glass-box approach (Blue Yonder standard) |
| Governance | Multi-tenant audit trails + confidence thresholds | Manhattan Active compliance tier |

### Expected Outcomes

| Metric | Current | Target | Industry Benchmark |
|--------|---------|--------|-------------------|
| Forecast Accuracy | N/A (synthetic) | 85-92% MAPE | SAP IBP: 20-30% improvement |
| Decision Latency | <1ms (hardcoded) | <2s (GPT-4o + ML) | Manhattan: <3s |
| Fallback Rate | 100% (all simulated) | <5% | Blue Yonder: <3% |
| Explainability | None | SHAP + natural language | SAP: embedded XAI |
| Cost per Prediction | $0 (fake) | $0.002-0.01 (GPT-4o) | Enterprise: $0.001-0.05 |

---

## 2. Current State Analysis

### 2.1 Complete AI Codebase Inventory

#### Backend Java AI Services (11 files, 1,868 LOC)

| File | LOC | Classification | Current Implementation | Issues |
|------|-----|---------------|----------------------|--------|
| `AiInferenceService.java` | 452 | EXPERIMENTAL | Hardcoded switch-case per model type (DEMAND_FORECAST, SMART_ALLOCATOR, CARRIER_OPTIMIZER, RETURNS_PREDICTOR, INVENTORY_OPTIMIZER, ANOMALY_DETECTOR, AI_ASSISTANT, DOCUMENT_AI) | No ML, no LLM, deterministic math only |
| `AiRuleEngineService.java` | 211 | EXPERIMENTAL | Priority-based fallback rules with random generators | `random.nextInt(500)` for forecasts |
| `AiOrderActionService.java` | 166 | EXPERIMENTAL | Hardcoded status→action mapping (PENDING→CONFIRM, CONFIRMED→ALLOCATE, etc.) | No AI reasoning, static confidence values |
| `AiModelRegistryService.java` | 165 | EXPERIMENTAL | CRUD for AI models/versions with tenant scoping | Schema is solid, logic is mock |
| `AiGatewayService.java` | 164 | REAL | API gateway with route lookup, deployment check, fallback chain | **Best service** — real routing, real inference logging |
| `AiTrainingPipelineService.java` | 163 | EXPERIMENTAL | Simulated training jobs with Thread.sleep | No real training |
| `AiMonitoringService.java` | 135 | REAL | Model health, drift detection, dashboard summary | Framework is solid, needs real metrics |
| `AiExperimentService.java` | 117 | EXPERIMENTAL | A/B test experiments with random traffic split | Schema good, logic mock |
| `LlmChatService.java` | 108 | **REAL** | OpenAI GPT-4o integration via REST | **Only real AI service** — reads `${nexus.ai.openai.api-key}` |
| `AiFeatureStoreService.java` | 94 | EXPERIMENTAL | Feature group/value management | No real feature computation |
| `AiAnalyticsService.java` | 93 | EXPERIMENTAL | Tenant dashboard with cost breakdown | Framework solid, data is zero |

#### Backend AI Repositories (17 files)

All JPA CRUD repositories — schema is enterprise-grade:

| Repository | Purpose | Key Queries |
|-----------|---------|-------------|
| `AiModelRepository` | Model registry | `findAvailableForTenant(tenantId, modelType, pageable)` |
| `AiModelVersionRepository` | Version management | Standard CRUD |
| `AiModelMetricRepository` | Metrics tracking | `avgMetricsSince(modelId, since)` |
| `AiDeploymentRepository` | Deployments | `findByTenantIdAndModelIdAndEnvironment()` |
| `AiInferenceLogRepository` | Audit trail | `avgLatencyByModelSince()`, `totalCostByTenantSince()` |
| `AiTrainingJobRepository` | Training jobs | `avgAccuracyByModel()` |
| `AiDatasetRepository` | Datasets | Standard CRUD |
| `AiFeatureDefinitionRepository` | Feature definitions | Standard CRUD |
| `AiFeatureValueRepository` | Feature values | Standard CRUD |
| `AiKnowledgeBaseRepository` | Knowledge bases | Standard CRUD |
| `AiKnowledgeDocumentRepository` | Knowledge docs | Standard CRUD |
| `AiGatewayRouteRepository` | Gateway routing | `findByTenantIdAndModelType()` |
| `AiPromptRepository` | Prompt templates | Standard CRUD |
| `AiRuleFallbackRepository` | Fallback rules | `findByModelIdAndIsActiveTrueOrderByPriorityAsc()` |
| `AiExperimentRepository` | A/B experiments | Standard CRUD |
| `AiCostLogRepository` | Cost tracking | `sumByTenantAndTypeSince()` |
| `AiComputeResourceRepository` | Compute resources | Standard CRUD |

#### Backend AI Entities (17 files in `entity/ai/`)

Enterprise-grade schemas with tenant scoping, versioning, and audit fields:

- `AiModel` — name, displayName, modelType, category, status, currentVersion, tenantId
- `AiModelVersion` — version, modelId, status, metrics (accuracy, precision, recall, f1)
- `AiModelMetric` — modelId, metricName, metricValue, recordedAt
- `AiDeployment` — modelId, versionId, environment, status, trafficWeight
- `AiInferenceLog` — tenantId, modelId, versionId, inputData, outputData, confidence, latencyMs, status, fallbackUsed
- `AiTrainingJob` — modelId, status, accuracy, trainingData
- `AiDataset` — name, description, recordCount, tenantId
- `AiFeatureDefinition` — name, type, description
- `AiFeatureValue` — featureId, value, entityId
- `AiKnowledgeBase` — name, description, documentCount
- `AiKnowledgeDocument` — knowledgeBaseId, content, metadata
- `AiGatewayRoute` — modelType, endpoint, timeoutMs, fallbackStrategy
- `AiPrompt` — name, template, modelType
- `AiRuleFallback` — modelId, name, actionType, actionConfig, priority
- `AiExperiment` — name, variants, trafficSplit
- `AiCostLog` — tenantId, costType, amount
- `AiComputeResource` — name, type, capacity

#### Backend AI Controllers (3 files)

| Controller | Endpoints | Purpose |
|-----------|-----------|---------|
| `AiPlatformController` | 30+ at `/api/ai` | Models, features, training, monitoring, experiments, knowledge, gateway |
| `AiChatController` | `/api/ai/chat` | Chat interface via LlmChatService |
| `AiController` | `/api/v1/ai` | Legacy predictions endpoint |

#### Frontend AI Pages (9 files)

| Page | Lines | Purpose |
|------|-------|---------|
| `AiPlatformPage.tsx` | ~800 | Main AI platform dashboard |
| `AiOrderRoutingPage.tsx` | ~400 | Order routing decisions |
| `AiPackingPage.tsx` | ~350 | Packing optimization |
| `AiForecastingPage.tsx` | ~450 | Demand forecasting |
| `AiExperimentsPage.tsx` | ~300 | A/B experiments |
| `AiAuditTrailPage.tsx` | ~350 | Inference audit logs |
| `AiBriefingPage.tsx` | ~300 | AI briefing dashboard |
| `AiLoadingPage.tsx` | ~150 | AI loading states |
| `AiPage.tsx` | ~200 | Legacy AI page |

#### Frontend AI API Modules (4 files)

| Module | Endpoints | Purpose |
|--------|-----------|---------|
| `aiPlatform.ts` | 30+ | Models, features, training, monitoring, knowledge, gateway |
| `aiAgents.ts` | 10+ | Agent management, briefings, insights |
| `aiOrders.ts` | 5+ | Order AI actions, suggestions |
| `ai.ts` | 5+ | Legacy AI predictions |

#### Python ML Models (6 models, 915 LOC)

| File | LOC | Algorithm | Training Data |
|------|-----|-----------|--------------|
| `model1_order_routing.py` | 75 | XGBoost classifier | Synthetic (500 samples) |
| `model2_shipping_aggregator.py` | 75 | XGBoost classifier | Synthetic (500 samples) |
| `model3_box_optimizer.py` | 76 | XGBoost classifier | Synthetic (500 samples) |
| `model4_pick_pack_ship.py` | 76 | XGBoost classifier | Synthetic (500 samples) |
| `model5_demand_forecasting.py` | 78 | XGBoost regressor | Synthetic (500 samples) |
| `model6_inventory_optimization.py` | 79 | XGBoost regressor | Synthetic (500 samples) |

#### Python API Servers (2 files)

| File | LOC | Purpose |
|------|-----|---------|
| `api_server.py` | 223 | Models 1-4 REST API |
| `api_demand_inventory.py` | 139 | Models 5-6 REST API |

### 2.2 Infrastructure

| Component | Current | Purpose |
|-----------|---------|---------|
| PostgreSQL 16 | `docker-compose.yml` | Primary database + pgvector |
| Redis 7 | `docker-compose.yml` | Caching, session, semantic cache |
| Kafka 3.7.2 | `docker-compose.yml` | Event streaming, AI event bus |
| JWT + RBAC | `security/` | Multi-tenant authentication |
| WebSocket (STOMP) | Frontend | Real-time AI updates |
| OpenAI API Key | `.env`, `application.properties`, `docker-compose.yml` | GPT-4o access |

### 2.3 What Works vs What's Fake

| Component | Status | Evidence |
|-----------|--------|----------|
| Multi-tenant auth | ✅ REAL | JWT tokens, TenantContext, RBAC |
| Model registry schema | ✅ REAL | JPA entities with versioning, scoping |
| Gateway routing | ✅ REAL | Route lookup, deployment check, fallback chain |
| Inference logging | ✅ REAL | Full audit trail with latency, cost, confidence |
| Drift detection | ✅ REAL | `detectDrift()` checks accuracy < 0.70 |
| Cost tracking | ✅ REAL | `AiCostLogRepository.sumByTenantAndTypeSince()` |
| OpenAI integration | ✅ REAL | `LlmChatService` calls GPT-4o API |
| Prediction logic | ❌ FAKE | Hardcoded switch-case in `AiInferenceService` |
| ML models | ❌ FAKE | Synthetic training data, mock predictions |
| Training pipeline | ❌ FAKE | `Thread.sleep()` simulation |
| Feature computation | ❌ FAKE | Static mock values |
| A/B experiments | ❌ FAKE | Random traffic split |
| Knowledge base | ❌ FAKE | Schema only, no RAG |

---

## 3. Industry Benchmark Analysis

### 3.1 Manhattan Active Omni

**Architecture Pattern:** Cloud-native microservices with continuous delivery (updates every 90 days)

**AI Capabilities:**
- **Agentic AI with Agent Foundry™** — Pre-built AI agents for fulfillment, inventory, labor, store operations
- **Unified ML Pipeline Studio** — Single platform for model training, validation, deployment
- **Data Pipeline Studio** — ETL and data preparation at scale
- **Google Cloud Platform** partnership for infrastructure
- **Store-aware AI** — In-store ML for labor optimization, store clustering by traffic patterns

**Key Patterns We Adopt:**
1. Agent Foundry™ → NexusShip Agent Orchestrator (see Section 6)
2. Unified ML Pipeline → NexusShip Training Pipeline (see Section 7)
3. Fallback chains with confidence thresholds → Already in `AiGatewayService`
4. Tenant-scoped model deployments → Already in schema

### 3.2 Blue Yonder Luminate

**Architecture Pattern:** AI engine "Fusion" — unified demand + supply planning

**AI Capabilities:**
- **"Sense, Analyze, Act" loop** — Sense signals → Analyze with AI/GenAI → Act with confidence scoring
- **Lumi AI Brain** — Generative AI assistants, digital twins for scenario planning
- **20+ billion ML predictions daily** at enterprise scale
- **Glass box approach** — Explainable AI with confidence scores and reasoning
- **Planning agents** — Inventory ops agent, shelf ops agent, replenishment agent

**Key Patterns We Adopt:**
1. Sense-Analyze-Act loop → NexusShip AI Pipeline (see Section 5)
2. Glass box explainability → SHAP + LLM explanations (see Section 9)
3. Confidence-driven fallbacks → Already in `AiGatewayService` (threshold 0.80)
4. Domain-specific agents → NexusShip specialized agents (see Section 6)

### 3.3 SAP IBP (Integrated Business Planning)

**Architecture Pattern:** SAP HANA in-memory + cloud-native on BTP

**AI Capabilities:**
- **Hybrid Gradient Boosting** for demand sensing
- **SAP Joule** — AI copilot for natural language queries
- **Embedded explainable AI** — Every prediction includes reasoning
- **SAP Data Intelligence** — ML pipeline orchestration
- **SAP AI Core** — Model deployment and serving
- **AutoML** for model selection
- **50-70% reduction** in planning cycles, **20-30% forecast accuracy** improvement

**Key Patterns We Adopt:**
1. Joule (AI copilot) → NexusShip AI Assistant via GPT-4o (see Section 5)
2. AutoML → NexusShip Model Selection Service (see Section 7)
3. Explainability → SHAP + natural language (see Section 9)
4. Fallback chains → Already in `AiGatewayService`

### 3.4 Enterprise Optimization (Walmart/Cognizant)

**Key Patterns:**
- **LP/MILP solvers** (Gurobi, OR-Tools, CP-SAT) for large-scale supply chain
- **Predict-then-optimize** workflows: ML predictions → optimization solvers
- **Decomposition, heuristics, metaheuristics** for scale

**We Adopt:** Google OR-Tools for carrier routing, warehouse allocation, box optimization (see Section 12)

### 3.5 Multi-Agent Architecture (LangChain/LangGraph)

**Key Patterns:**
- **LangGraph** — Stateful orchestration with cycles, persistence, human-in-the-loop
- **Subagents pattern** — Supervisor delegates to specialized agents
- **ReAct agents** — Reasoning + Acting with tool use
- **Enterprise requirements** — Audit trails, RBAC, observability

**We Adopt:** Supervisor + specialized agents pattern (see Section 6)

### 3.6 Enterprise RAG

**Key Patterns (5-Layer Architecture):**
1. **Ingestion** — Document parsing, chunking, embedding
2. **Chunking/Embedding** — Semantic chunking, OpenAI embeddings
3. **Vector Storage** — pgvector for <1M docs, Pinecone/Weaviate for scale
4. **Retrieval/Reranking** — Hybrid search (dense + sparse + reranking)
5. **LLM Generation** — Context-augmented responses

**Key Stats:**
- ~80% hallucination reduction vs base LLM
- Hybrid search + reranking outperforms pure vector search
- Semantic caching reduces latency 60-80% for repeated queries
- HNSW indexes on pgvector for sub-second retrieval

**We Adopt:** pgvector + hybrid search (see Section 8)

---

## 4. Target Architecture

### 4.1 High-Level Architecture Diagram

```
┌─────────────────────────────────────────────────────────────────────────┐
│                        NexusShip AI Platform                            │
├─────────────────────────────────────────────────────────────────────────┤
│                                                                         │
│  ┌──────────────┐    ┌──────────────┐    ┌──────────────┐              │
│  │   Frontend    │    │   Frontend    │    │   Frontend    │              │
│  │  AI Platform  │    │  AI Chat     │    │  AI Briefing  │              │
│  │    Page       │    │  Interface   │    │    Page       │              │
│  └──────┬───────┘    └──────┬───────┘    └──────┬───────┘              │
│         │                   │                   │                        │
│         └───────────────────┼───────────────────┘                        │
│                             │ WebSocket + REST                           │
│  ┌──────────────────────────┴──────────────────────────────────────┐    │
│  │                     AI Gateway Layer                            │    │
│  │  ┌─────────────┐  ┌─────────────┐  ┌─────────────┐            │    │
│  │  │   Route      │  │  Rate       │  │  Fallback   │            │    │
│  │  │   Resolver   │  │  Limiter    │  │  Chain      │            │    │
│  │  └─────────────┘  └─────────────┘  └─────────────┘            │    │
│  └──────────────────────────┬──────────────────────────────────────┘    │
│                             │                                            │
│  ┌──────────────────────────┴──────────────────────────────────────┐    │
│  │                   AI Orchestrator Layer                         │    │
│  │                                                                  │    │
│  │  ┌─────────────────────────────────────────────────────────┐    │    │
│  │  │              Supervisor Agent (GPT-4o)                  │    │    │
│  │  │  • Intent classification    • Task decomposition        │    │    │
│  │  │  • Agent routing            • Result synthesis          │    │    │
│  │  └─────────────┬───────────────────────────┬───────────────┘    │    │
│  │                │                           │                     │    │
│  │  ┌─────────────┴──────┐  ┌────────────────┴──────────────┐    │    │
│  │  │  Specialized Agents │  │  Tool-Using Agents            │    │    │
│  │  │                     │  │                                │    │    │
│  │  │  • Demand Forester  │  │  • OR-Tools Solver             │    │    │
│  │  │  • Inventory Oracle │  │  • SQL Executor                │    │    │
│  │  │  • Carrier Optimizer│  │  • API Caller                  │    │    │
│  │  │  • Returns Predictor│  │  • Document Reader             │    │    │
│  │  │  • Anomaly Hunter   │  │  • Knowledge Search (RAG)     │    │    │
│  │  │  • Document Parser  │  │                                │    │    │
│  │  └─────────────────────┘  └────────────────────────────────┘    │    │
│  └──────────────────────────┬──────────────────────────────────────┘    │
│                             │                                            │
│  ┌──────────────────────────┴──────────────────────────────────────┐    │
│  │                    ML Model Layer                               │    │
│  │                                                                  │    │
│  │  ┌─────────────┐  ┌─────────────┐  ┌─────────────┐            │    │
│  │  │  XGBoost    │  │  LightGBM   │  │  OR-Tools   │            │    │
│  │  │  Models 1-6 │  │  Ensemble   │  │  MILP       │            │    │
│  │  └─────────────┘  └─────────────┘  └─────────────┘            │    │
│  │                                                                  │    │
│  │  ┌─────────────┐  ┌─────────────┐  ┌─────────────┐            │    │
│  │  │  Feature    │  │  Model      │  │  A/B         │            │    │
│  │  │  Store      │  │  Registry   │  │  Experiments │            │    │
│  │  └─────────────┘  └─────────────┘  └─────────────┘            │    │
│  └──────────────────────────┬──────────────────────────────────────┘    │
│                             │                                            │
│  ┌──────────────────────────┴──────────────────────────────────────┐    │
│  │                  Knowledge & RAG Layer                          │    │
│  │                                                                  │    │
│  │  ┌─────────────┐  ┌─────────────┐  ┌─────────────┐            │    │
│  │  │  pgvector   │  │  Hybrid     │  │  Semantic   │            │    │
│  │  │  Embeddings │  │  Search     │  │  Cache      │            │    │
│  │  └─────────────┘  └─────────────┘  └─────────────┘            │    │
│  └──────────────────────────┬──────────────────────────────────────┘    │
│                             │                                            │
│  ┌──────────────────────────┴──────────────────────────────────────┐    │
│  │               Governance & Observability Layer                  │    │
│  │                                                                  │    │
│  │  ┌─────────────┐  ┌─────────────┐  ┌─────────────┐            │    │
│  │  │  SHAP       │  │  Audit      │  │  Cost       │            │    │
│  │  │  Explainability│ │  Trails    │  │  Tracking   │            │    │
│  │  └─────────────┘  └─────────────┘  └─────────────┘            │    │
│  │                                                                  │    │
│  │  ┌─────────────┐  ┌─────────────┐  ┌─────────────┐            │    │
│  │  │  Drift      │  │  Confidence │  │  Human-in-  │            │    │
│  │  │  Detection  │  │  Thresholds │  │  the-Loop   │            │    │
│  │  └─────────────┘  └─────────────┘  └─────────────┘            │    │
│  └─────────────────────────────────────────────────────────────────┘    │
│                                                                         │
├─────────────────────────────────────────────────────────────────────────┤
│                      Data Layer                                         │
│  ┌─────────────┐  ┌─────────────┐  ┌─────────────┐  ┌─────────────┐  │
│  │ PostgreSQL 16│  │ Redis 7     │  │ Kafka 3.7   │  │ OpenAI API  │  │
│  │ + pgvector   │  │ Cache       │  │ Event Bus   │  │ GPT-4o      │  │
│  └─────────────┘  └─────────────┘  └─────────────┘  └─────────────┘  │
└─────────────────────────────────────────────────────────────────────────┘
```

### 4.2 Request Flow — End to End

```
User Request (e.g., "Optimize order OMS-2024-5821")
    │
    ▼
┌─────────────────┐
│  AI Gateway      │  Route lookup, rate limit, auth
│  (AiGatewayService) │
└────────┬────────┘
         │
         ▼
┌─────────────────┐
│  Supervisor Agent │  GPT-4o classifies intent, decomposes task
│  (GPT-4o)        │  "This is an order optimization request"
└────────┬────────┘
         │
         ├──► "Need order details" ──► SQL Executor Tool
         ├──► "Need inventory data" ──► Inventory Oracle Agent
         ├──► "Need carrier rates" ──► Carrier Optimizer Agent
         └──► "Need historical patterns" ──► RAG Knowledge Search
         │
         ▼
┌─────────────────┐
│  Synthesis Agent  │  GPT-4o combines all results
│  (GPT-4o)        │  Generates actionable recommendation
└────────┬────────┘
         │
         ▼
┌─────────────────┐
│  SHAP Explainer   │  Feature importance for ML predictions
│  + LLM Summary    │  Natural language explanation
└────────┬────────┘
         │
         ▼
┌─────────────────┐
│  Audit Logger     │  Full trace: input, reasoning, output, cost
│  (AiInferenceLog) │  Confidence score, latency, fallback used
└─────────────────┘
```

---

## 5. AI Orchestrator Redesign — ChatGPT Integration

### 5.1 Current State

`LlmChatService.java` (108 lines) is the **only real AI service**. It:
- Reads `nexus.ai.openai.api-key` from Spring properties
- Calls `https://api.openai.com/v1/chat/completions` with GPT-4o
- Falls back to keyword-matched static responses when API key is missing
- Used by `AiChatController` for chat interface

**Problem:** Only used for chat. The 10 other AI services don't use it at all.

### 5.2 Target State — GPT-4o as Reasoning Backbone

Every AI orchestrator will use GPT-4o for reasoning while keeping existing ML models for numerical predictions:

```
┌─────────────────────────────────────────────────────────────┐
│                    LlmChatService (Enhanced)                 │
│                                                              │
│  • Structured output (JSON mode)                            │
│  • Function calling for tool use                            │
│  • Streaming responses (SSE)                                │
│  • Token counting and cost tracking                         │
│  • Semantic caching (Redis)                                 │
│  • Retry with exponential backoff                           │
│  • Circuit breaker (resilience4j)                           │
└─────────────────────────────────────────────────────────────┘
         │
         ├──► AiInferenceService (GPT-4o reasons over ML outputs)
         ├──► AiRuleEngineService (GPT-4o generates dynamic rules)
         ├──► AiOrderActionService (GPT-4o decides actions)
         ├──► AiGatewayService (GPT-4o for complex routing)
         └──► AiAssistantService (GPT-4o for natural language)
```

### 5.3 Service-by-Service Redesign

#### AiInferenceService → AI Inference Orchestrator

**Current:** 452-line switch-case with hardcoded math for 8 model types.

**Target:** GPT-4o reasons over input data, selects appropriate ML model, interprets results.

```java
// BEFORE (current — line 58-411)
switch (modelType) {
    case "DEMAND_FORECAST": {
        double seasonalityMultiplier;
        switch (seasonality) {
            case "HIGH": seasonalityMultiplier = 1.3; break;
            // ... hardcoded forever
        }
    }
}

// AFTER (target)
public Map<String, Object> execute(UUID modelId, UUID versionId, Map<String, Object> input) {
    AiModel model = modelRepository.findById(modelId).orElseThrow();
    
    // Step 1: GPT-4o analyzes input and selects approach
    String analysisPrompt = buildAnalysisPrompt(model, input);
    String analysis = llmChatService.chatStructured(analysisPrompt, buildInputContext(input));
    
    // Step 2: Run appropriate ML model based on GPT-4o's selection
    Map<String, Object> mlResult = runSelectedModel(analysis, input);
    
    // Step 3: GPT-4o interprets ML result and generates explanation
    String explanation = llmChatService.chatStructured(
        "Interpret this prediction and explain it to a supply chain manager:",
        Map.of("prediction", mlResult, "context", input)
    );
    
    // Step 4: Log everything
    logInference(model, input, mlResult, explanation);
    
    return combineResults(mlResult, explanation);
}
```

#### AiRuleEngineService → AI Dynamic Rule Engine

**Current:** 211 lines of static formulas and random generators.

**Target:** GPT-4o generates context-aware rules dynamically.

```java
// BEFORE (current — line 105-210)
case "DEMAND_FORECAST":
    result.put("predictedOrders", (int) Math.round(historicalAvg * seasonalMultiplier));

// AFTER (target)
public Map<String, Object> generateDynamicRule(UUID tenantId, String modelType, 
                                                 Map<String, Object> context) {
    String rulePrompt = String.format("""
        You are a supply chain rule engine. Given this context:
        %s
        
        Generate a fallback rule for %s that:
        1. Uses historical patterns from the context
        2. Applies industry-standard formulas
        3. Returns JSON with prediction, confidence, and reasoning
        
        Rule types available: FORMULA, LOOKUP_TABLE, THRESHOLD_RULE, DYNAMIC
        """, objectMapper.writeValueAsString(context), modelType);
    
    return llmChatService.chatStructured(rulePrompt);
}
```

#### AiOrderActionService → AI Order Decision Agent

**Current:** 166 lines of hardcoded status→action mapping.

**Target:** GPT-4o reasons about order state and recommends actions.

```java
// BEFORE (current — line 33-77)
switch (order.getStatus()) {
    case "PENDING" -> suggestions.add(... "Confirm Order" ... confidence(0.92));
}

// AFTER (target)
public List<AiSuggestionDto> getSuggestions(UUID orderId) {
    OrderResponse order = orderService.getOrder(orderId);
    List<AiSuggestionDto> suggestions = new ArrayList<>();
    
    // GPT-4o reasons about the order
    String reasoning = llmChatService.chatStructured("""
        Analyze this order and recommend actions:
        Order: %s
        Status: %s
        Items: %s
        Customer history: %s
        
        Consider: payment status, inventory availability, shipping deadlines,
        customer loyalty, return risk, fraud indicators.
        
        Return JSON array of suggestions with actionType, label, description,
        confidence (0-1), and reasoning.
        """, order);
    
    return parseSuggestions(reasoning);
}
```

### 5.4 Enhanced LlmChatService

```java
@Service
public class LlmChatService {
    
    // Existing fields + new ones
    private final RedisTemplate<String, String> redisTemplate;
    private final CircuitBreaker circuitBreaker;
    private final MeterRegistry meterRegistry;
    
    // New: Structured output with JSON mode
    public Map<String, Object> chatStructured(String systemPrompt, 
                                               Map<String, Object> context) {
        String cacheKey = "llm:" + hashingUtil.hash(systemPrompt + context);
        
        // Semantic cache check
        String cached = redisTemplate.opsForValue().get(cacheKey);
        if (cached != null) {
            meterRegistry.counter("llm.cache.hit").increment();
            return objectMapper.readValue(cached, Map.class);
        }
        
        // Circuit breaker protected call
        return circuitBreaker.executeSupplier(() -> {
            long start = System.currentTimeMillis();
            
            ObjectNode body = objectMapper.createObjectNode();
            body.put("model", model);
            body.put("response_format", Map.of("type", "json_object")); // JSON mode
            body.put("temperature", 0.3); // Low for consistency
            
            // ... build messages, call API, parse response
            
            // Track cost and latency
            trackMetrics(response, start);
            
            // Cache result
            redisTemplate.opsForValue().set(cacheKey, result, Duration.ofMinutes(5));
            
            return result;
        });
    }
    
    // New: Function calling for tool use
    public List<ToolCall> chatWithTools(String prompt, List<ToolDefinition> tools) {
        // GPT-4o function calling to select and invoke tools
    }
    
    // New: Streaming for real-time UI updates
    public Flux<String> chatStream(String prompt) {
        // SSE streaming for chat interface
    }
}
```

---

## 6. Multi-Agent Architecture

### 6.1 Agent Topology

```
                    ┌─────────────────────┐
                    │   Supervisor Agent   │
                    │      (GPT-4o)       │
                    │                      │
                    │  • Intent parsing    │
                    │  • Task routing      │
                    │  • Result merging    │
                    └──────────┬──────────┘
                               │
           ┌───────────────────┼───────────────────┐
           │                   │                   │
    ┌──────┴──────┐    ┌──────┴──────┐    ┌──────┴──────┐
    │   Demand     │    │  Inventory  │    │  Carrier    │
    │   Forester   │    │   Oracle    │    │  Optimizer  │
    │              │    │             │    │             │
    │ XGBoost +   │    │ XGBoost +  │    │ OR-Tools   │
    │ GPT-4o      │    │ GPT-4o     │    │ MILP       │
    │ reasoning   │    │ reasoning  │    │ + GPT-4o   │
    └─────────────┘    └────────────┘    └─────────────┘
    
    ┌─────────────┐    ┌────────────┐    ┌─────────────┐
    │  Returns    │    │  Anomaly   │    │  Document   │
    │  Predictor  │    │  Hunter    │    │  Parser     │
    │             │    │            │    │             │
    │ XGBoost +  │    │ Isolation  │    │ GPT-4o     │
    │ GPT-4o     │    │ Forest +   │    │ Vision     │
    │ reasoning  │    │ GPT-4o     │    │            │
    └─────────────┘    └────────────┘    └─────────────┘
```

### 6.2 Agent Definitions

| Agent | Model | Tools | Responsibility |
|-------|-------|-------|---------------|
| **Supervisor** | GPT-4o | All tools | Intent classification, task decomposition, result synthesis |
| **Demand Forester** | XGBoost + GPT-4o | SQL, Feature Store | Demand forecasting with natural language explanations |
| **Inventory Oracle** | XGBoost + GPT-4o | SQL, OR-Tools | Reorder points, safety stock, risk assessment |
| **Carrier Optimizer** | OR-Tools MILP + GPT-4o | Rate APIs, OR-Tools | Multi-carrier routing, cost optimization |
| **Returns Predictor** | XGBoost + GPT-4o | SQL, Customer DB | Return probability, reason prediction |
| **Anomaly Hunter** | Isolation Forest + GPT-4o | SQL, Pattern DB | Fraud detection, anomaly explanation |
| **Document Parser** | GPT-4o Vision | File System | Invoice/packing slip extraction |

### 6.3 Tool Definitions

| Tool | Implementation | Purpose |
|------|---------------|---------|
| `sql_executor` | JDBC via Spring | Query order/inventory/customer data |
| `feature_store` | AiFeatureStoreService | Retrieve computed features |
| `or_tools_solver` | Python subprocess | MILP optimization |
| `knowledge_search` | pgvector RAG | Search knowledge base |
| `api_caller` | RestTemplate | External API calls (carrier rates, etc.) |
| `audit_logger` | AiInferenceLogRepository | Log all actions |

### 6.4 Agent Communication Pattern

```java
@Component
public class SupervisorAgent {
    
    private final LlmChatService llmChatService;
    private final Map<String, Agent> agents;
    private final Map<String, Tool> tools;
    
    public AgentResponse processRequest(UserRequest request) {
        // Step 1: Classify intent
        Intent intent = classifyIntent(request);
        
        // Step 2: Decompose into subtasks
        List<Subtask> subtasks = decomposeTask(intent);
        
        // Step 3: Execute subtasks (parallel where possible)
        List<SubtaskResult> results = executeSubtasks(subtasks);
        
        // Step 4: Synthesize final response
        return synthesizeResults(request, results);
    }
    
    private Intent classifyIntent(UserRequest request) {
        String response = llmChatService.chatStructured("""
            Classify this supply chain request into one of:
            DEMAND_FORECAST, INVENTORY_OPTIMIZATION, CARRIER_SELECTION,
            RETURNS_ANALYSIS, ANOMALY_DETECTION, ORDER_OPTIMIZATION,
            DOCUMENT_PROCESSING, GENERAL_INQUIRY
            
            Request: %s
            
            Return JSON: {"intent": "...", "confidence": 0.0-1.0, 
                         "subtasks": ["..."], "required_agents": ["..."]}
            """, request.getText());
        
        return objectMapper.readValue(response, Intent.class);
    }
    
    private List<SubtaskResult> executeSubtasks(List<Subtask> subtasks) {
        // Parallel execution for independent subtasks
        return subtasks.parallelStream()
            .map(subtask -> {
                Agent agent = agents.get(subtask.getRequiredAgent());
                return agent.execute(subtask);
            })
            .collect(Collectors.toList());
    }
}
```

---

## 7. ML Model Strategy

### 7.1 Current → Target Model Mapping

| Current Model | LOC | Current Algorithm | Target Algorithm | Expected Improvement |
|--------------|-----|-------------------|-----------------|---------------------|
| `model1_order_routing.py` | 75 | XGBoost (synthetic data) | **LightGBM** + real order data | 15-25% accuracy |
| `model2_shipping_aggregator.py` | 75 | XGBoost (synthetic data) | **XGBoost** + carrier rate data | 10-20% cost reduction |
| `model3_box_optimizer.py` | 76 | XGBoost (synthetic data) | **OR-Tools 3D Bin Packing** | 20-30% space utilization |
| `model4_pick_pack_ship.py` | 76 | XGBoost (synthetic data) | **LightGBM** + warehouse data | 15-20% efficiency |
| `model5_demand_forecasting.py` | 78 | XGBoost regressor (synthetic) | **LightGBM** + historical sales | 20-30% MAPE improvement |
| `model6_inventory_optimization.py` | 79 | XGBoost regressor (synthetic) | **XGBoost** + inventory data | 15-25% stockout reduction |

### 7.2 New Models to Add

| Model | Algorithm | Purpose | Data Source |
|-------|-----------|---------|-------------|
| `model7_fraud_detection.py` | Isolation Forest + XGBoost | Order fraud scoring | Transaction data |
| `model8_carrier_selection.py` | LightGBM + OR-Tools | Multi-carrier optimization | Carrier APIs |
| `model9_delivery_prediction.py` | LightGBM | Delivery time prediction | Historical deliveries |
| `model10_price_optimization.py` | XGBoost | Dynamic pricing | Market data |

### 7.3 Training Pipeline

```
┌─────────────────────────────────────────────────────────────┐
│                    Training Pipeline                         │
│                                                              │
│  ┌─────────────┐    ┌─────────────┐    ┌─────────────┐     │
│  │  Data        │    │  Feature    │    │  Model      │     │
│  │  Ingestion   │───►│  Engineering│───►│  Training   │     │
│  │  (Kafka)     │    │  (Spark)    │    │  (XGBoost)  │     │
│  └─────────────┘    └─────────────┘    └─────────────┘     │
│         │                   │                   │            │
│         ▼                   ▼                   ▼            │
│  ┌─────────────┐    ┌─────────────┐    ┌─────────────┐     │
│  │  Data        │    │  Feature    │    │  Model      │     │
│  │  Validation  │    │  Store      │    │  Registry   │     │
│  │  (Great      │    │  (Redis)    │    │  (JPA)      │     │
│  │   Expect.)   │    │             │    │             │     │
│  └─────────────┘    └─────────────┘    └─────────────┘     │
│                                              │               │
│                                              ▼               │
│                                     ┌─────────────┐         │
│                                     │  A/B         │         │
│                                     │  Experiments │         │
│                                     └─────────────┘         │
└─────────────────────────────────────────────────────────────┘
```

### 7.4 Feature Store Design

```sql
-- Feature definitions
CREATE TABLE ai_feature_definitions (
    id UUID PRIMARY KEY,
    name VARCHAR(100) UNIQUE NOT NULL,
    feature_type VARCHAR(20) NOT NULL,  -- NUMERIC, CATEGORICAL, EMBEDDING
    description TEXT,
    computation_sql TEXT,  -- SQL to compute feature
    refresh_interval INTERVAL DEFAULT '1 hour',
    tenant_id UUID NOT NULL
);

-- Feature values (computed)
CREATE TABLE ai_feature_values (
    id UUID PRIMARY KEY,
    feature_id UUID REFERENCES ai_feature_definitions(id),
    entity_type VARCHAR(50) NOT NULL,  -- ORDER, PRODUCT, CUSTOMER
    entity_id UUID NOT NULL,
    feature_value JSONB NOT NULL,
    computed_at TIMESTAMP NOT NULL,
    expires_at TIMESTAMP,
    tenant_id UUID NOT NULL
);

-- Key features per model type
-- DEMAND_FORECAST: historical_avg_30d, seasonality_index, trend_slope, promotional_impact
-- CARRIER_OPTIMIZER: weight_tier, zone_distance, carrier_reliability_score, cost_per_mile
-- INVENTORY_OPTIMIZER: days_of_supply, reorder_point, safety_stock, demand_variability
```

---

## 8. RAG & Knowledge Architecture

### 8.1 5-Layer RAG Architecture

```
┌─────────────────────────────────────────────────────────────┐
│  Layer 5: LLM Generation                                    │
│  ┌─────────────────────────────────────────────────────┐   │
│  │  GPT-4o with retrieved context                      │   │
│  │  "Based on our knowledge base, the answer is..."    │   │
│  └─────────────────────────────────────────────────────┘   │
│                          ▲                                  │
│  Layer 4: Retrieval + Reranking                            │
│  ┌─────────────────────────────────────────────────────┐   │
│  │  Hybrid: Dense (cosine) + Sparse (BM25) + Reranker │   │
│  │  Top-K: 10 candidates → Reranked to 3-5            │   │
│  └─────────────────────────────────────────────────────┘   │
│                          ▲                                  │
│  Layer 3: Vector Storage                                    │
│  ┌─────────────────────────────────────────────────────┐   │
│  │  pgvector with HNSW index                          │   │
│  │  Tables: ai_knowledge_documents (embedding VECTOR) │   │
│  │  Distance: cosine similarity                       │   │
│  └─────────────────────────────────────────────────────┘   │
│                          ▲                                  │
│  Layer 2: Chunking + Embedding                             │
│  ┌─────────────────────────────────────────────────────┐   │
│  │  Semantic chunking (500 tokens, 50 overlap)        │   │
│  │  Embedding: text-embedding-3-small (1536 dim)      │   │
│  │  Metadata: source, category, tenant_id             │   │
│  └─────────────────────────────────────────────────────┘   │
│                          ▲                                  │
│  Layer 1: Ingestion                                         │
│  ┌─────────────────────────────────────────────────────┐   │
│  │  PDF, DOCX, TXT, CSV, HTML → parsed text          │   │
│  │  Sources: SOPs, carrier guides, product catalogs   │   │
│  └─────────────────────────────────────────────────────┘   │
└─────────────────────────────────────────────────────────────┘
```

### 8.2 Database Schema for RAG

```sql
-- Enable pgvector extension
CREATE EXTENSION IF NOT EXISTS vector;

-- Knowledge bases
CREATE TABLE ai_knowledge_bases (
    id UUID PRIMARY KEY,
    name VARCHAR(200) NOT NULL,
    description TEXT,
    document_count INTEGER DEFAULT 0,
    tenant_id UUID NOT NULL,
    created_at TIMESTAMP DEFAULT NOW(),
    updated_at TIMESTAMP DEFAULT NOW()
);

-- Knowledge documents with embeddings
CREATE TABLE ai_knowledge_documents (
    id UUID PRIMARY KEY,
    knowledge_base_id UUID REFERENCES ai_knowledge_bases(id),
    title VARCHAR(500) NOT NULL,
    content TEXT NOT NULL,
    chunk_index INTEGER NOT NULL,
    chunk_total INTEGER NOT NULL,
    embedding VECTOR(1536),  -- text-embedding-3-small dimensions
    metadata JSONB DEFAULT '{}',
    source VARCHAR(500),
    category VARCHAR(100),
    tenant_id UUID NOT NULL,
    created_at TIMESTAMP DEFAULT NOW()
);

-- HNSW index for fast similarity search
CREATE INDEX idx_knowledge_embedding_hnsw 
    ON ai_knowledge_documents 
    USING hnsw (embedding vector_cosine_ops)
    WITH (m = 16, ef_construction = 64);

-- BM25 index for sparse search (using pg_trgm)
CREATE INDEX idx_knowledge_content_trgm 
    ON ai_knowledge_documents 
    USING gin (content gin_trgm_ops);

-- Semantic cache for repeated queries
CREATE TABLE ai_semantic_cache (
    id UUID PRIMARY KEY,
    query_hash VARCHAR(64) NOT NULL,
    query_embedding VECTOR(1536),
    response JSONB NOT NULL,
    hit_count INTEGER DEFAULT 1,
    created_at TIMESTAMP DEFAULT NOW(),
    expires_at TIMESTAMP NOT NULL
);
```

### 8.3 Hybrid Search Implementation

```java
@Service
public class HybridSearchService {
    
    private final JdbcTemplate jdbcTemplate;
    private final LlmChatService llmChatService;
    
    public List<SearchResult> hybridSearch(String query, UUID tenantId, int topK) {
        // Step 1: Generate query embedding
        float[] queryEmbedding = llmChatService.getEmbedding(query);
        
        // Step 2: Dense search (cosine similarity)
        List<SearchResult> denseResults = denseSearch(queryEmbedding, tenantId, topK * 2);
        
        // Step 3: Sparse search (BM25-like with pg_trgm)
        List<SearchResult> sparseResults = sparseSearch(query, tenantId, topK * 2);
        
        // Step 4: Reciprocal Rank Fusion
        List<SearchResult> fusedResults = reciprocalRankFusion(denseResults, sparseResults, topK);
        
        // Step 5: Rerank with cross-encoder (optional, for precision)
        return rerank(query, fusedResults, topK);
    }
    
    private List<SearchResult> denseSearch(float[] embedding, UUID tenantId, int limit) {
        String sql = """
            SELECT id, title, content, category,
                   1 - (embedding <=> ?::vector) as similarity
            FROM ai_knowledge_documents
            WHERE tenant_id = ?
            ORDER BY embedding <=> ?::vector
            LIMIT ?
            """;
        return jdbcTemplate.query(sql, new Object[]{embedding, tenantId, embedding, limit}, ...);
    }
    
    private List<SearchResult> sparseSearch(String query, UUID tenantId, int limit) {
        String sql = """
            SELECT id, title, content, category,
                   similarity(content, ?) as similarity
            FROM ai_knowledge_documents
            WHERE tenant_id = ? AND similarity(content, ?) > 0.1
            ORDER BY similarity DESC
            LIMIT ?
            """;
        return jdbcTemplate.query(sql, new Object[]{query, tenantId, query, limit}, ...);
    }
    
    private List<SearchResult> reciprocalRankFusion(
            List<SearchResult> dense, List<SearchResult> sparse, int topK) {
        Map<String, Double> scores = new HashMap<>();
        Map<String, SearchResult> resultMap = new HashMap<>();
        
        double k = 60.0; // RRF constant
        for (int i = 0; i < dense.size(); i++) {
            String id = dense.get(i).getId();
            scores.merge(id, 1.0 / (k + i + 1), Double::sum);
            resultMap.put(id, dense.get(i));
        }
        for (int i = 0; i < sparse.size(); i++) {
            String id = sparse.get(i).getId();
            scores.merge(id, 1.0 / (k + i + 1), Double::sum);
            resultMap.putIfAbsent(id, sparse.get(i));
        }
        
        return scores.entrySet().stream()
            .sorted(Map.Entry.<String, Double>comparingByValue().reversed())
            .limit(topK)
            .map(e -> resultMap.get(e.getKey()))
            .collect(Collectors.toList());
    }
}
```

### 8.4 Semantic Caching

```java
@Service
public class SemanticCacheService {
    
    private final RedisTemplate<String, String> redisTemplate;
    private final JdbcTemplate jdbcTemplate;
    
    public Optional<String> getCached(String query, UUID tenantId) {
        float[] queryEmbedding = getEmbedding(query);
        
        // Find similar cached queries (cosine similarity > 0.95)
        String sql = """
            SELECT response, hit_count
            FROM ai_semantic_cache
            WHERE tenant_id = ? 
              AND expires_at > NOW()
              AND (query_embedding <=> ?::vector) < 0.05
            ORDER BY (query_embedding <=> ?::vector)
            LIMIT 1
            """;
        
        return jdbcTemplate.queryForObject(sql, new Object[]{tenantId, queryEmbedding, queryEmbedding}, 
            (rs, rowNum) -> {
                // Increment hit count
                jdbcTemplate.update("UPDATE ai_semantic_cache SET hit_count = hit_count + 1 WHERE id = ?",
                    rs.getObject("id"));
                return rs.getString("response");
            });
    }
    
    public void cache(String query, String response, UUID tenantId) {
        float[] queryEmbedding = getEmbedding(query);
        
        jdbcTemplate.update("""
            INSERT INTO ai_semantic_cache (id, query_hash, query_embedding, response, tenant_id, expires_at)
            VALUES (gen_random_uuid(), ?, ?, ?::jsonb, ?, NOW() + INTERVAL '5 minutes')
            """, hash(query), queryEmbedding, response, tenantId);
    }
}
```

---

## 9. Governance, Explainability & Observability

### 9.1 Explainability Stack

```
┌─────────────────────────────────────────────────────────────┐
│                    Explainability Layers                      │
│                                                              │
│  Layer 1: SHAP Values                                       │
│  ┌─────────────────────────────────────────────────────┐   │
│  │  Feature importance for ML predictions              │   │
│  │  "weight_kg contributed 35% to carrier selection"   │   │
│  └─────────────────────────────────────────────────────┘   │
│                          │                                  │
│  Layer 2: LLM Natural Language                             │
│  ┌─────────────────────────────────────────────────────┐   │
│  │  GPT-4o generates human-readable explanation        │   │
│  │  "Based on the package weight and destination..."   │   │
│  └─────────────────────────────────────────────────────┘   │
│                          │                                  │
│  Layer 3: Visual Explanation                               │
│  ┌─────────────────────────────────────────────────────┐   │
│  │  Frontend: confidence bars, feature charts          │   │
│  │  "This decision was 85% confident"                  │   │
│  └─────────────────────────────────────────────────────┘   │
│                          │                                  │
│  Layer 4: Audit Trail                                      │
│  ┌─────────────────────────────────────────────────────┐   │
│  │  Full trace: input → reasoning → output → cost      │   │
│  │  Stored in ai_inference_logs for compliance         │   │
│  └─────────────────────────────────────────────────────┘   │
└─────────────────────────────────────────────────────────────┘
```

### 9.2 SHAP Integration

```java
@Service
public class ShapExplainerService {
    
    private final LlmChatService llmChatService;
    
    public Explanation explainPrediction(Map<String, Object> input, 
                                          Map<String, Object> prediction,
                                          String modelType) {
        // Step 1: Compute SHAP values (via Python subprocess)
        Map<String, Double> shapValues = computeShapValues(input, prediction, modelType);
        
        // Step 2: Sort by absolute importance
        List<Map.Entry<String, Double>> sorted = shapValues.entrySet().stream()
            .sorted(Map.Entry.<String, Double>comparingByValue().reversed())
            .collect(Collectors.toList());
        
        // Step 3: GPT-4o generates natural language explanation
        String nlExplanation = llmChatService.chatStructured("""
            Explain this supply chain prediction in plain English:
            
            Model: %s
            Top factors: %s
            Prediction: %s
            
            Be specific, actionable, and concise.
            """, modelType, sorted.subList(0, 5), prediction);
        
        return Explanation.builder()
            .shapValues(shapValues)
            .naturalLanguage(nlExplanation)
            .confidence(extractConfidence(prediction))
            .keyFactors(sorted.subList(0, 3))
            .build();
    }
}
```

### 9.3 Human-in-the-Loop

```java
@Service
public class HumanInTheLoopService {
    
    // Confidence thresholds for auto-execute vs human review
    private static final Map<String, Double> THRESHOLDS = Map.of(
        "DEMAND_FORECAST", 0.85,    // High confidence: auto-execute
        "SMART_ALLOCATOR", 0.80,    // Medium: auto-execute
        "CARRIER_OPTIMIZER", 0.90,  // High: auto-execute (cost impact)
        "RETURNS_PREDICTOR", 0.75,  // Lower threshold (advisory)
        "ANOMALY_DETECTOR", 0.70,   // Flag for review
        "ORDER_ACTION", 0.95        // Very high: auto-execute
    );
    
    public DecisionResult processDecision(String modelType, 
                                           Map<String, Object> prediction) {
        double confidence = extractConfidence(prediction);
        double threshold = THRESHOLDS.getOrDefault(modelType, 0.80);
        
        if (confidence >= threshold) {
            // Auto-execute with audit trail
            return DecisionResult.autoExecute(prediction);
        } else {
            // Queue for human review
            return DecisionResult.requiresReview(prediction, 
                String.format("Confidence %.1f%% below threshold %.1f%%",
                    confidence * 100, threshold * 100));
        }
    }
}
```

### 9.4 Cost Tracking

```java
@Service
public class AiCostTrackingService {
    
    private final AiCostLogRepository costLogRepository;
    private final MeterRegistry meterRegistry;
    
    public void trackOpenAiCost(String model, int promptTokens, 
                                  int completionTokens, UUID tenantId) {
        // GPT-4o pricing (as of 2026)
        double costPerInputToken = 0.0025 / 1000;   // $2.50 per 1M input tokens
        double costPerOutputToken = 0.01 / 1000;     // $10.00 per 1M output tokens
        
        double inputCost = promptTokens * costPerInputToken;
        double outputCost = completionTokens * costPerOutputToken;
        double totalCost = inputCost + outputCost;
        
        costLogRepository.save(AiCostLog.builder()
            .tenantId(tenantId)
            .costType("INFERENCE")
            .amount(BigDecimal.valueOf(totalCost))
            .metadata(Map.of(
                "model", model,
                "promptTokens", promptTokens,
                "completionTokens", completionTokens
            ))
            .build());
        
        // Prometheus metrics
        meterRegistry.gauge("ai.cost.total", totalCost);
        meterRegistry.counter("ai.tokens.input").increment(promptTokens);
        meterRegistry.counter("ai.tokens.output").increment(completionTokens);
    }
}
```

### 9.5 Drift Detection

```java
@Service
public class DriftDetectionService {
    
    private final AiModelMetricRepository metricRepository;
    private final LlmChatService llmChatService;
    
    public DriftReport detectDrift(UUID modelId) {
        // Step 1: Check accuracy drift
        BigDecimal currentAccuracy = metricRepository.avgAccuracySince(
            modelId, LocalDateTime.now().minusDays(7));
        BigDecimal baselineAccuracy = metricRepository.avgAccuracySince(
            modelId, LocalDateTime.now().minusDays(30), LocalDateTime.now().minusDays(7));
        
        double driftPct = baselineAccuracy != null && currentAccuracy != null ?
            (baselineAccuracy.doubleValue() - currentAccuracy.doubleValue()) / baselineAccuracy.doubleValue() : 0;
        
        // Step 2: Check latency drift
        BigDecimal currentLatency = metricRepository.avgLatencySince(
            modelId, LocalDateTime.now().minusHours(1));
        BigDecimal baselineLatency = metricRepository.avgLatencySince(
            modelId, LocalDateTime.now().minusDays(7));
        
        // Step 3: Check confidence drift
        BigDecimal currentConfidence = metricRepository.avgConfidenceSince(
            modelId, LocalDateTime.now().minusHours(1));
        
        boolean isDrifting = driftPct > 0.10 || // 10% accuracy drop
                            currentLatency.compareTo(baselineLatency.multiply(new BigDecimal("2"))) > 0;
        
        if (isDrifting) {
            // GPT-4o generates drift analysis
            String analysis = llmChatService.chatStructured("""
                Analyze this model drift report and recommend actions:
                Model: %s
                Accuracy drift: %.1f%%
                Latency change: %sms → %sms
                Confidence: %.2f
                
                Recommend: retrain, rollback, or investigate?
                """, modelId, driftPct * 100, 
                    baselineLatency, currentLatency, currentConfidence);
            
            return DriftReport.alerting(driftPct, analysis);
        }
        
        return DriftReport.healthy();
    }
}
```

---

## 10. Data Architecture

### 10.1 Database Schema Enhancements

```sql
-- 1. Enable pgvector
CREATE EXTENSION IF NOT EXISTS vector;
CREATE EXTENSION IF NOT EXISTS pg_trgm;

-- 2. AI Model Registry (existing, enhanced)
ALTER TABLE ai_models ADD COLUMN IF NOT EXISTS 
    training_data_source VARCHAR(200),
    feature_set JSONB DEFAULT '[]',
    hyperparameters JSONB DEFAULT '{}',
    shap_enabled BOOLEAN DEFAULT true;

-- 3. Inference Log (existing, enhanced)
ALTER TABLE ai_inference_logs ADD COLUMN IF NOT EXISTS
    shap_values JSONB,
    explanation TEXT,
    tokens_used INTEGER,
    cost_usd DECIMAL(10,6),
    model_provider VARCHAR(50) DEFAULT 'INTERNAL';

-- 4. Knowledge Base with Embeddings
CREATE TABLE IF NOT EXISTS ai_knowledge_documents (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    knowledge_base_id UUID REFERENCES ai_knowledge_bases(id),
    title VARCHAR(500) NOT NULL,
    content TEXT NOT NULL,
    chunk_index INTEGER NOT NULL,
    chunk_total INTEGER NOT NULL,
    embedding VECTOR(1536),
    metadata JSONB DEFAULT '{}',
    source VARCHAR(500),
    category VARCHAR(100),
    tenant_id UUID NOT NULL,
    created_at TIMESTAMP DEFAULT NOW()
);

-- 5. Semantic Cache
CREATE TABLE IF NOT EXISTS ai_semantic_cache (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    query_hash VARCHAR(64) NOT NULL,
    query_embedding VECTOR(1536),
    response JSONB NOT NULL,
    hit_count INTEGER DEFAULT 1,
    tenant_id UUID NOT NULL,
    created_at TIMESTAMP DEFAULT NOW(),
    expires_at TIMESTAMP NOT NULL
);

-- 6. Agent Execution Log
CREATE TABLE IF NOT EXISTS ai_agent_executions (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    tenant_id UUID NOT NULL,
    supervisor_request_id UUID,
    agent_name VARCHAR(100) NOT NULL,
    agent_type VARCHAR(50) NOT NULL,  -- SUPERVISOR, SPECIALIST, TOOL
    input_data JSONB,
    output_data JSONB,
    reasoning TEXT,
    confidence DECIMAL(5,4),
    latency_ms INTEGER,
    tokens_used INTEGER,
    cost_usd DECIMAL(10,6),
    status VARCHAR(20) NOT NULL,  -- SUCCESS, FAILED, TIMEOUT, FALLBACK
    parent_execution_id UUID,
    created_at TIMESTAMP DEFAULT NOW()
);

-- Indexes
CREATE INDEX idx_knowledge_embedding_hnsw 
    ON ai_knowledge_documents USING hnsw (embedding vector_cosine_ops);
CREATE INDEX idx_knowledge_content_trgm 
    ON ai_knowledge_documents USING gin (content gin_trgm_ops);
CREATE INDEX idx_agent_executions_tenant 
    ON ai_agent_executions(tenant_id, created_at DESC);
CREATE INDEX idx_agent_executions_supervisor 
    ON ai_agent_executions(supervisor_request_id);
```

### 10.2 Event Architecture

```
┌─────────────────────────────────────────────────────────────┐
│                    Kafka Event Bus                           │
│                                                              │
│  Topics:                                                     │
│  ┌─────────────────────────────────────────────────────┐   │
│  │  ai.prediction.requested    — Inference requested    │   │
│  │  ai.prediction.completed    — Inference completed    │   │
│  │  ai.prediction.failed       — Inference failed       │   │
│  │  ai.model.retrained         — Model retrained        │   │
│  │  ai.model.deployed          — Model deployed         │   │
│  │  ai.drift.detected          — Drift detected         │   │
│  │  ai.agent.executed          — Agent action logged    │   │
│  │  ai.cost.incurred           — Cost tracked           │   │
│  └─────────────────────────────────────────────────────┘   │
│                                                              │
│  Consumers:                                                  │
│  • AiMonitoringService — Real-time dashboards               │
│  • AiCostTrackingService — Cost aggregation                 │
│  • DriftDetectionService — Continuous monitoring            │
│  • AuditService — Compliance logging                        │
└─────────────────────────────────────────────────────────────┘
```

---

## 11. Integration Patterns

### 11.1 External System Integration

```
┌─────────────────────────────────────────────────────────────┐
│                    Integration Hub                           │
│                                                              │
│  ┌─────────────┐  ┌─────────────┐  ┌─────────────┐        │
│  │  Carrier    │  │  Warehouse  │  │  Payment    │        │
│  │  APIs       │  │  Systems    │  │  Gateways   │        │
│  │             │  │             │  │             │        │
│  │  FedEx      │  │  WMS        │  │  Stripe     │        │
│  │  UPS        │  │  Manhattan  │  │  PayPal     │        │
│  │  USPS       │  │  SAP EWM   │  │  Adyen      │        │
│  └──────┬──────┘  └──────┬──────┘  └──────┬──────┘        │
│         │                │                │                  │
│         └────────────────┼────────────────┘                  │
│                          │                                    │
│  ┌───────────────────────┴──────────────────────────────┐   │
│  │              OpenAiConnector (Existing)               │   │
│  │  • Tenant-scoped credentials                         │   │
│  │  • Circuit breaker protection                        │   │
│  │  • Request/response logging                          │   │
│  └──────────────────────────────────────────────────────┘   │
│                                                              │
│  ┌──────────────────────────────────────────────────────┐   │
│  │              New: CarrierRateConnector                │   │
│  │  • Real-time rate shopping                           │   │
│  │  • Multi-carrier comparison                          │   │
│  │  • Cached rates (5-min TTL)                          │   │
│  └──────────────────────────────────────────────────────┘   │
│                                                              │
│  ┌──────────────────────────────────────────────────────┐   │
│  │              New: InventorySyncConnector              │   │
│  │  • Real-time stock levels                            │   │
│  │  • Multi-warehouse aggregation                       │   │
│  │  • Kafka event-driven sync                           │   │
│  └──────────────────────────────────────────────────────┘   │
└─────────────────────────────────────────────────────────────┘
```

### 11.2 API Design

```
Base URL: /api/v1/ai

# Gateway
POST   /predict/{modelType}          — Unified prediction endpoint
POST   /predict/batch                — Batch predictions
GET    /health                       — System health

# Agents
POST   /agents/execute               — Execute agent task
GET    /agents/{id}/status           — Agent execution status
GET    /agents/{id}/reasoning        — Get agent reasoning trace

# Knowledge
POST   /knowledge/search             — Hybrid RAG search
POST   /knowledge/ingest             — Ingest documents
GET    /knowledge/bases              — List knowledge bases

# Governance
GET    /explain/{inferenceId}        — Get SHAP explanation
GET    /cost/summary                 — Cost breakdown
GET    /drift/{modelId}              — Drift report

# Models
GET    /models                       — List models
POST   /models/{id}/train           — Trigger training
POST   /models/{id}/deploy          — Deploy model
GET    /models/{id}/metrics         — Model metrics

# Experiments
POST   /experiments                  — Create A/B test
GET    /experiments/{id}/results    — Get results
POST   /experiments/{id}/conclude   — Conclude experiment
```

---

## 12. Algorithm Selection Matrix

| Problem Domain | Primary Algorithm | Secondary | When to Use | Expected Accuracy |
|---------------|-------------------|-----------|-------------|-------------------|
| **Demand Forecasting** | LightGBM | XGBoost, Prophet | Historical sales + seasonality | 85-92% MAPE |
| **Order Routing** | LightGBM | XGBoost, Neural Net | Multi-warehouse allocation | 80-88% accuracy |
| **Carrier Selection** | OR-Tools MILP | LightGBM | Cost + time optimization | 15-25% cost reduction |
| **Box Optimization** | OR-Tools 3D Bin Packing | Greedy heuristic | Space utilization | 20-30% improvement |
| **Inventory Optimization** | XGBoost | Linear Programming | Reorder point + safety stock | 15-25% stockout reduction |
| **Returns Prediction** | XGBoost | Logistic Regression | Return probability | 75-85% AUC |
| **Anomaly Detection** | Isolation Forest | XGBoost, Autoencoder | Fraud detection | 80-90% recall |
| **Delivery Prediction** | LightGBM | XGBoost | Time prediction | 82-88% accuracy |
| **Price Optimization** | XGBoost + LP | Reinforcement Learning | Dynamic pricing | 10-20% margin improvement |
| **Document Parsing** | GPT-4o Vision | Textract | Invoice/packing slip | 90-95% field extraction |
| **NLP Queries** | GPT-4o | Fine-tuned BERT | Natural language to SQL | 85-92% accuracy |
| **Knowledge Search** | pgvector + BM25 | Pinecone | RAG retrieval | 80-90% recall@5 |

### Algorithm Decision Tree

```
Input arrives at AI Gateway
    │
    ├── Is it a numerical prediction?
    │   ├── Yes → Run ML model (XGBoost/LightGBM)
    │   │   └── Is explanation needed?
    │   │       ├── Yes → SHAP + GPT-4o natural language
    │   │       └── No → Return ML result
    │   └── No → Continue
    │
    ├── Is it an optimization problem?
    │   ├── Yes → OR-Tools MILP/LP
    │   │   └── Is it combinatorial?
    │   │       ├── Yes → CP-SAT solver
    │   │       └── No → LP solver
    │   └── No → Continue
    │
    ├── Is it a natural language query?
    │   ├── Yes → GPT-4o with RAG
    │   │   ├── Has knowledge base? → Hybrid search → GPT-4o
    │   │   └── No knowledge base? → GPT-4o direct
    │   └── No → Continue
    │
    ├── Is it a document to parse?
    │   ├── Yes → GPT-4o Vision
    │   └── No → Continue
    │
    └── Default → GPT-4o reasoning
```

---

## 13. Phased Implementation Roadmap

### Phase 1: Foundation (Weeks 1-3)
**Goal:** GPT-4o integration into existing orchestrators

| Task | Files | LOC Change | Priority |
|------|-------|------------|----------|
| Enhance LlmChatService | `LlmChatService.java` | +150 | HIGH |
| Add Redis semantic cache | New `SemanticCacheService.java` | +80 | HIGH |
| Add circuit breaker (resilience4j) | `LlmChatService.java` | +40 | HIGH |
| Wire GPT-4o into AiInferenceService | `AiInferenceService.java` | +200 | HIGH |
| Wire GPT-4o into AiRuleEngineService | `AiRuleEngineService.java` | +150 | HIGH |
| Wire GPT-4o into AiOrderActionService | `AiOrderActionService.java` | +100 | HIGH |
| Add SHAP explainability | New `ShapExplainerService.java` | +120 | MEDIUM |
| Add cost tracking | `AiCostTrackingService.java` | +80 | HIGH |
| Add pgvector extension | Docker Compose + SQL | +20 | HIGH |
| Update frontend for real AI responses | 3 frontend pages | +200 | MEDIUM |

**Deliverable:** All 10 AI services use GPT-4o for reasoning, with cost tracking and caching.

### Phase 2: ML Models (Weeks 4-6)
**Goal:** Real ML models with real data

| Task | Files | LOC Change | Priority |
|------|-------|------------|----------|
| Replace synthetic training data | 6 Python models | -500, +800 | HIGH |
| Add real feature computation | New `FeatureComputeService.java` | +200 | HIGH |
| Implement feature store queries | `AiFeatureStoreService.java` | +100 | HIGH |
| Add model training pipeline | `AiTrainingPipelineService.java` | +150 | HIGH |
| Add model validation metrics | `AiMonitoringService.java` | +80 | HIGH |
| Add A/B experiment framework | `AiExperimentService.java` | +100 | MEDIUM |
| Add 4 new ML models | 4 new Python files | +300 | MEDIUM |
| Add data ingestion from Kafka | New `DataIngestionService.java` | +150 | HIGH |

**Deliverable:** All ML models trained on real data, with validation and monitoring.

### Phase 3: RAG & Knowledge (Weeks 7-9)
**Goal:** Enterprise knowledge base with hybrid search

| Task | Files | LOC Change | Priority |
|------|-------|------------|----------|
| Implement document ingestion | New `DocumentIngestionService.java` | +200 | HIGH |
| Implement semantic chunking | New `ChunkingService.java` | +150 | HIGH |
| Implement embedding generation | `LlmChatService.java` | +50 | HIGH |
| Implement hybrid search | New `HybridSearchService.java` | +200 | HIGH |
| Implement reranking | New `RerankerService.java` | +100 | MEDIUM |
| Add knowledge base management UI | `AiPlatformPage.tsx` | +300 | MEDIUM |
| Add RAG to agent queries | Agent files | +100 | HIGH |
| Add document parsing (GPT-4o Vision) | New `DocumentParserAgent.java` | +150 | MEDIUM |

**Deliverable:** Full RAG pipeline with hybrid search and document parsing.

### Phase 4: Multi-Agent (Weeks 10-12)
**Goal:** Supervisor + specialized agents

| Task | Files | LOC Change | Priority |
|------|-------|------------|----------|
| Implement SupervisorAgent | New `SupervisorAgent.java` | +250 | HIGH |
| Implement DemandForesterAgent | New `DemandForesterAgent.java` | +150 | HIGH |
| Implement InventoryOracleAgent | New `InventoryOracleAgent.java` | +150 | HIGH |
| Implement CarrierOptimizerAgent | New `CarrierOptimizerAgent.java` | +150 | HIGH |
| Implement ReturnsPredictorAgent | New `ReturnsPredictorAgent.java` | +120 | MEDIUM |
| Implement AnomalyHunterAgent | New `AnomalyHunterAgent.java` | +120 | MEDIUM |
| Implement DocumentParserAgent | New `DocumentParserAgent.java` | +150 | MEDIUM |
| Implement tool definitions | New `AgentTools.java` | +200 | HIGH |
| Add agent execution logging | `AiInferenceLogRepository` | +80 | HIGH |
| Add human-in-the-loop UI | `AiPlatformPage.tsx` | +250 | MEDIUM |

**Deliverable:** Full multi-agent system with supervisor pattern.

### Phase 5: Governance & Polish (Weeks 13-15)
**Goal:** Enterprise compliance and monitoring

| Task | Files | LOC Change | Priority |
|------|-------|------------|----------|
| Implement drift detection | `DriftDetectionService.java` | +120 | HIGH |
| Implement confidence thresholds | `HumanInTheLoopService.java` | +100 | HIGH |
| Add Prometheus metrics | All services | +200 | HIGH |
| Add Grafana dashboards | Config files | +150 | MEDIUM |
| Add OpenTelemetry tracing | All services | +100 | HIGH |
| Add compliance audit trails | All services | +80 | HIGH |
| Performance testing | Test files | +300 | MEDIUM |
| Documentation | MD files | +500 | LOW |

**Deliverable:** Enterprise-grade governance, observability, and compliance.

---

## 14. Infrastructure Requirements

### 14.1 Docker Compose Additions

```yaml
# docker-compose.yml additions
services:
  # Existing
  postgres:
    image: pgvector/pgvector:pg16
    # Add pgvector extension on startup
    
  redis:
    image: redis:7-alpine
    # Used for semantic caching
    
  kafka:
    image: confluentinc/cp-kafka:3.7.2
    # AI event bus
    
  # New
  prometheus:
    image: prom/prometheus:latest
    volumes:
      - ./deploy/prometheus.yml:/etc/prometheus/prometheus.yml
    ports:
      - "9090:9090"
    
  grafana:
    image: grafana/grafana:latest
    environment:
      - GF_SECURITY_ADMIN_PASSWORD=admin
    ports:
      - "3001:3000"
    
  jaeger:
    image: jaegertracing/all-in-one:latest
    ports:
      - "16686:16686"
      - "14268:14268"
```

### 14.2 Maven Dependencies

```xml
<!-- pom.xml additions -->
<dependencies>
    <!-- Resilience4j Circuit Breaker -->
    <dependency>
        <groupId>io.github.resilience4j</groupId>
        <artifactId>resilience4j-spring-boot3</artifactId>
        <version>2.1.0</version>
    </dependency>
    
    <!-- Redis -->
    <dependency>
        <groupId>org.springframework.boot</groupId>
        <artifactId>spring-boot-starter-data-redis</artifactId>
    </dependency>
    
    <!-- pgvector -->
    <dependency>
        <groupId>com.pgvector</groupId>
        <artifactId>pgvector</artifactId>
        <version>0.1.6</version>
    </dependency>
    
    <!-- Prometheus -->
    <dependency>
        <groupId>io.micrometer</groupId>
        <artifactId>micrometer-registry-prometheus</artifactId>
    </dependency>
    
    <!-- OpenTelemetry -->
    <dependency>
        <groupId>io.opentelemetry</groupId>
        <artifactId>opentelemetry-api</artifactId>
        <version>1.35.0</version>
    </dependency>
</dependencies>
```

### 14.3 Environment Variables

```bash
# .env additions
OPENAI_API_KEY=sk-proj-lYH6...
OPENAI_MODEL=gpt-4o
OPENAI_EMBEDDING_MODEL=text-embedding-3-small
OPENAI_MAX_TOKENS=4096
OPENAI_TEMPERATURE=0.3

# Circuit Breaker
RESILIENCE4J_CIRCUITBREAKER_FAILURES_THRESHOLD=5
RESILIENCE4J_CIRCUITBREAKER_WAIT_DURATION_IN_OPEN_STATE=30s

# Semantic Cache
REDIS_CACHE_TTL=300
REDIS_SIMILARITY_THRESHOLD=0.95

# Cost Limits
AI_MONTHLY_BUDGET_USD=1000
AI_MAX_COST_PER_PREDICTION=0.05
```

---

## Appendix A: File Reference

| Category | Path | Purpose |
|----------|------|---------|
| AI Services | `nexus-oms-backend/src/main/java/com/nexus/oms/service/ai/` | All 11 AI services |
| AI Repos | `nexus-oms-backend/src/main/java/com/nexus/oms/repository/ai/` | All 17 AI repositories |
| AI Entities | `nexus-oms-backend/src/main/java/com/nexus/oms/entity/ai/` | All 17 AI entities |
| AI DTOs | `nexus-oms-backend/src/main/java/com/nexus/oms/dto/ai/` | 3 AI DTOs |
| AI Controllers | `nexus-oms-backend/src/main/java/com/nexus/oms/controller/ai/` | 3 AI controllers |
| Python ML | `supply_chain_ai/` | Models 1-4 |
| Python ML | `supply_chain_ai2/` | Models 5-6 |
| Frontend AI | `nexus-oms-frontend/src/pages/Ai*.tsx` | 9 AI pages |
| Frontend API | `nexus-oms-frontend/src/api/ai*.ts` | 4 AI API modules |
| Config | `.env`, `application.properties`, `docker-compose.yml` | Environment |

---

## Appendix B: Glossary

| Term | Definition |
|------|-----------|
| **MILP** | Mixed-Integer Linear Programming |
| **RAG** | Retrieval-Augmented Generation |
| **SHAP** | SHapley Additive exPlanations |
| **HNSW** | Hierarchical Navigable Small World (vector index) |
| **BM25** | Best Matching 25 (sparse retrieval algorithm) |
| **RRF** | Reciprocal Rank Fusion |
| **MAPE** | Mean Absolute Percentage Error |
| **AUC** | Area Under the Curve (ROC) |
| **Agent Foundry™** | Manhattan's pre-built AI agent platform |
| **Lumi AI Brain** | Blue Yonder's generative AI engine |
| **SAP Joule** | SAP's AI copilot |
| **Sense-Analyze-Act** | Blue Yonder's AI loop pattern |

---

*Document generated by Sisyphus — NexusShip Enterprise AI Architecture Design*
