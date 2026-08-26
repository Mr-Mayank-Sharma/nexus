# AI Integration Options — Model Serving Architecture

**Date:** 2026-08-26
**Status:** Verified against live system (E2E test passed same day)

## Current State (Verified Working)

The inference path is a **three-tier waterfall** inside `AiInferenceService`:

1. **ONNX artifact (in-process)** — `OnnxRuntimeService` loads the trained artifact registered to a model version. Real trained model, zero network hop.
2. **LLM prediction** — used only when no ONNX artifact is served and the chat service is enabled.
3. **Rule engine fallback** — deterministic heuristics (`MOVING_AVERAGE` for demand, `GENERIC_RULE` otherwise). Always available.

Every tier transition is instrumented (`nexus.ai.inference.onnx_success|onnx_fallback|llm_success|llm_fallback` counters), SHAP-style explanations layer on top regardless of tier, and responses self-describe via `onnxPowered` / `llmPowered` / `fallbackReason`.

The deployment path is protected by `AiDeploymentGateService`: challenger versions must beat absolute WAPE ceiling (0.60), beat the seasonal-naive MASE baseline, and improve on the active champion by the configured margin. Blocks return HTTP 422 with verbatim failure reasons; `?force=true` overrides and stamps `validatedBy`/`validatedAt` on the version plus a WARN log line.

**E2E verification (2026-08-26):** create → champion deploy → challenger blocked (422) → forced override → ramp ladder → rollback → predict — all 12 checks passed. Fallback probes confirmed graceful degradation on unknown model types, empty input, and malformed input (no 500s; rule engine absorbs everything).

## Option A — In-Process ONNX Serving (current primary path)

`register_model.py` chains: backtest metrics → version registration → ONNX artifact upload → gated deploy → gateway route. The JVM executes the artifact directly.

**Pros**
- Already built and E2E-verified; no new infrastructure
- Lowest latency and no network failure mode
- Gate, registry, and routes all enforce quality before traffic shifts

**Cons**
- Preprocessing must be duplicated in Java (training does it in pandas)
- Artifact swap requires careful lifecycle handling in the JVM
- JVM heap shared with OMS workload

**Fit:** right default for P1.x. Demand forecasting (model5) is the proven case.

## Option B — Python Sidecar Inference Service

Promote `supply_chain_ai2/api/api_demand_inventory.py` (FastAPI + Dockerfile already present) to a first-class serving tier behind the gateway's route table (`AiGatewayRouteRepository`).

**Pros**
- Preprocessing parity with training — eliminates the dual-implementation drift risk
- Independent scaling, deploys, and dependency versions
- Route table makes it a configuration change, not a code change

**Cons**
- One more service to run/monitor locally and in prod
- Adds a network hop before the existing rule-engine safety net

**Fit:** adopt when a model needs heavy feature engineering (model6 inventory optimization is the likely candidate) or when artifact hot-swapping matters.

## Option C — Managed Cloud Endpoint (SageMaker / Vertex / Bedrock)

**Pros:** autoscaling, managed MLOps, no serving code.
**Cons:** cost, vendor lock-in, data egress, cold starts; disproportionate at current scale.

**Fit:** revisit only for bursty external workloads or enterprise deployments that mandate it.

## Recommendation

**Stay on Option A. Keep Option B warm.** The registry/gate/route abstractions mean migrating a given model type to the sidecar is additive — register the route, keep the same gate contract. Do not introduce Option C until a customer requirement forces it.

### Follow-ups (small, worth doing)

1. **Gate override flag** — forced deploys are indistinguishable from clean passes in the DB (`validatedBy` is set identically). Add an explicit `gateOverride` column or persist gate failures into the version's `config` JSON so audits don't depend on log retention.
2. **Traffic weight enforcement** — ramp ladder bookkeeping exists (5%→10%→25%→50%→100%) but the gateway serves the single ACTIVE deployment regardless of weight. Wire weighted serving when real traffic splitting lands (post-P1.6).
3. **Sidecar smoke route** — add a health probe entry for the FastAPI service to the integration hub once Option B is activated for any model type.
