# Nexus OMS — AI Business Process Document

## 1. Executive Summary

Nexus AI transforms from *analytics dashboards* to *decision engines* that run inside every fulfillment workflow. The core principle, backed by the M5 forecasting competition, the 2025/2026 retail-forecasting benchmark (arXiv:2506.05941), and Gartner's 2026 agentic-AI guidance:

> **Rules guarantee feasibility → ML scores candidates → optimizer assigns → agents recommend → humans approve → audit logs everything.**

Autonomy grows in controlled steps. Phases 1–3 ship with **recommend + human approves**; the autonomy dial (Phase 4) lets each tenant escalate to *auto-execute low-risk actions* only. Every AI output is explainable, fallback-safe, and tenant-isolated.

**What the operator sees changes:** instead of opening a dashboard to read numbers, operators now open an *approval queue* and review concrete recommended actions with reasons.

**Target load:** 10,000–100,000 orders/day across many complex managed stores, served by one global model per capability with per-tenant calibration for cold-start and isolation.

---

## 2. Business Roles & Their Responsibilities

| Role | Today | After AI implementation |
|------|-------|------------------------|
| **ADMIN** | System configuration, user management, tenant setup | Tunes the **autonomy dial** and model versions per tenant |
| **CEO** | High-level analytics, audit logs, strategic oversight | Reviews AI value dashboards (savings, accuracy, cost per inference) |
| **OPS_MANAGER** | Day-to-day order, inventory, and fulfillment management | Reviews the **routing & exception approval queue**; overrides AI recommendations |
| **WAREHOUSE_MANAGER** | Picking, packing, shipping, cycle counts, receiving | Acts on **slotting & replenishment recommendations**; approves moves and reorders |
| **PICKER / PACKER / LOADER** | Physical fulfillment work | Follows AI-optimized pack/loading guidance; outcomes feed model feedback |
| **STORE_MANAGER** | BOPIS orders, store inventory | Sees AI-suggested store fulfillments (ship-from-store) and confirms allocation |
| **PROCUREMENT_MANAGER** | Supplier management, purchase orders, RFQs | Approves AI-generated **reorder recommendations**; PO drafts |
| **LOGISTICS_MANAGER** | Carrier management, rate shopping, manifesting | Validates **carrier-optimizer** recommendations and promised dates |
| **CUSTOMER_SUPPORT** | Order lookups, returns processing | Uses AI briefings for proactive exception handling |
| **FINANCE** | Invoicing, payment reconciliation, refunds | Reviews returns-predictor and cost-impact summaries |
| **SYSTEM/ML ENGINEER** | Manages registry & infra | Owns **training runs, calibration, drift, shadow deployments, experiments** |
| **Nexus platform** | Hosts all tenants | Runs **global models + per-tenant calibration** |

---

## 3. End-to-End Business Processes

### 3.1 BP-1: Demand Forecasting & Replenishment (highest ROI)

**Trigger:** Daily at a configurable scheduled time per tenant; on-demand via the forecasting page; weekly retraining.

**Process flow:**

```
Order History → Aggregator Job → Feature Store → [LightGBM Quantile Training → ONNX]
                                                                   │
                                              Model Registry (versioned, staged, promoted)
                                                                   │
Forecast Request → Gateway → ONNX Runtime (in-process) → per-tenant calibration → Quantiles (P10/P50/P90)
                                                                   │
                                       ┌───────────────────────────┴────────────────────────────┐
                                       ▼                                                        ▼
                              Safety Stock / Reorder (BP-2)                         Order Promising / ATP (BP-3)
```

1. **Aggregator job** rolls up order history into daily **SKU × warehouse × channel** demand records plus features (day-of-week, month, promotions, price, price change) and writes them to the feature store (`AiFeatureValue`).
2. **Training** runs locally on CPU (Python, LightGBM) as **quantile regression** (P10/P50/P90) with **hierarchical reconciliation** (SKU → warehouse → channel → tenant). The result is exported to an **ONNX artifact**, registered and versioned in the model registry.
3. **Serving** (`POST /api/v1/ai/predict/DEMAND_FORECAST`) routes through the AI gateway into in-process ONNX Runtime (~1–10 ms), then applies the **per-tenant calibration factor**.
4. **Outputs:** forecast series with **confidence bands (P50/P90)**; surfaced on the forecasting page.
5. **Consumers:**
   - Safety stock / reorder points (BP-2) are computed from **P90**, not a point estimate.
   - Order promising / ATP (BP-3) uses **P50** forecast + current inventory + carrier ETA.
6. **Human loop:** the planner compares forecast vs. actuals; deviations feed the **feedback dataset** used at the next retrain.
7. **Fallback:** if no deployment, no artifact, or confidence < 0.80, the gateway falls back to the existing rule/LLM path — the forecast never hard-fails.

**Decision points:** Which quantile to plan against (P50 for cost-optimal, P90 for service level) — per-tenant setting.

**KPIs:** WAPE / RMSE per tenant, forecast-vs-actual bias, stockout rate, excess-inventory days.

---

### 3.2 BP-2: Inventory Optimization (reorder + safety stock)

**Trigger:** Daily after the forecast; on-demand per SKU.

1. For each SKU × warehouse: fetch **P50/P90 forecast**, current stock, open POs, and **learned lead time**.
2. `POST /api/v1/ai/predict/INVENTORY_OPTIMIZER` returns **reorder point, safety stock, reorder qty, and risk level** (CRITICAL / HIGH / MEDIUM / LOW).
3. Recommendations are created and appear in the **approval queue** ("Reorder 120 units of SKU-X at WH-A").
4. **Approve** → a purchase order draft is auto-created. **Override** → edits quantity/source. **Reject** → logged with reason (feeds the feedback dataset).

**KPIs:** service level (fill rate), stockout incidents, inventory turns, aging/excess stock.

---

### 3.3 BP-3: Intelligent Order Routing, Allocation & ATP (every order)

**Trigger:** Every inbound order (`POST /orders`), immediately.

1. **Order ingestion** → order lines are resolved against inventory and forecast.
2. **Candidate scoring:** the routing scorer (LightGBM ranker) scores each candidate **node** (warehouses + managed stores with stock) on *fulfillment cost, ETA, on-hand inventory, carrier reliability, and shipping zone*.
3. **Feasibility guardrails (hard rules, always win):** out-of-stock nodes are excluded; legal/shipping constraints are enforced; an infeasible order can never be produced by the LLM or scorer.
4. **Optimizer (CP-SAT, batch):** for waves/large orders, assigns across nodes jointly to minimize cost + time; for single orders, picks the top score in **<50 ms**.
5. **Recommendation:** `POST /api/v1/ai/routing` creates a recommendation → operator **approves / overrides / rejects** in the order-routing page.
6. **Approved** → allocation locked, **ATP / promised date** computed (forecast P50 + carrier ETA + node capacity), order committed to WMS.
7. **Multi-warehouse / multi-store:** optional consensus-seeking agent resolves conflicting allocations (Phase 4).
8. **Fallback:** if the scorer/optimizer errors, static routing rules apply — an order is never blocked.

**KPIs:** cost per order, promise-date accuracy, ship-cutoff hit rate, split-shipment rate, node utilization.

---

### 3.4 BP-4: Warehouse Operations — Packing, Loading, Slotting

- **Packing (`/ai/packing`):** per-order packing recommendation (box selection, pack density) from item dimensions/weight; reviewed in the packing page.
- **Loading (`/ai/loading`):** loading-bay sequence recommendation (destinations, weights, cutoffs) from the order wave; reviewed in the loading page.
- **Slotting (`slotting-optimization`):** velocity ABC + affinity-clustering analysis → **move recommendations** ("move SKU-X from aisle 3 → 1 for +2.1% pick efficiency"). The warehouse manager approves moves; placement updates are recorded for the next analysis cycle.

**KPIs:** picks/hour, pick-distance reduction, cube utilization, dwell time.

---

### 3.5 BP-5: Exception & Anomaly Management (proactive ops)

**Trigger:** Continuous event scan (orders, inventory, carrier events, forecasts).

1. The anomaly model scores events → **flagged orders** (late risk, stockout risk, carrier deviation, fraud signals) with severity, confidence, and reasons (`ANOMALY_DETECTOR`).
2. **AI briefing (`/ai/briefing`)** summarizes the day's exceptions into prioritized actions.
3. Exceptions enter the approval queue with an **LLM explanation** (reuse the chat service) and a **suggested corrective action** (reroute, expedite, notify customer).
4. **Outcome recorded** (approved / overridden / rejected) → feeds agent learning and drift monitoring.

**KPIs:** mean time to resolve exceptions, % auto-detected before customer impact, escalation accuracy.

---

### 3.6 BP-6: Agentic Layer — Autonomy Dial & Audit (Phase 4)

- Each tenant sets an **autonomy level per decision type**:
  - `RECOMMEND` — agents propose, humans approve (default).
  - `AUTO_LOW_RISK` — agents auto-execute only low-risk actions (e.g., routing below a cost threshold, routine reorders) within guardrails; everything above the threshold escalates.
- Every agent action (auto or approved) writes to the **AI audit trail** with: input features, model version, decision, confidence, fallback reason, actor.
- **Consensus-seeking** (multi-agent negotiation for allocation) is available as opt-in — research-backed (arXiv:2411.10184) — with a human override at the end.

**KPIs:** % decisions auto-executed, % overridden, error rate per autonomy level, audit completeness.

---

### 3.7 BP-7: Model Lifecycle, Calibration & Monitoring (the "AI of AI")

- **Global model + per-tenant calibration:** one shared LightGBM per capability; each tenant gets a **calibration factor** (`ai_calibration` table) computed from recent actuals — new stores cold-start from the global model until enough data accrues.
- **Retraining:** scheduled weekly; triggered by drift or manually from the platform page → training job → ONNX artifact → staged (VALIDATING) → shadow-tested → promoted.
- **Experiments:** A/B compare model versions on live traffic; deploy the winning version.
- **Monitoring:** real accuracy, drift score, latency, fallback rate, cost per inference, semantic-cache hit rate.
- **Governance:** registry audit, version rollback, per-tenant enable/disable, cost ceilings.

---

## 4. How AI Interacts with the Existing System (Architecture Map)

```
Order/Inventory DB ─► [Aggregator] ─► Feature Store ─► [LightGBM → ONNX] ─► Model Registry ─► Gateway
                                                                                                      │
Inbound Order ─► Rules (feasibility) ─► ML Scorer ─► Optimizer (CP-SAT) ─► Recommendation ─► [Approve/Override/Reject] ─► WMS / ATP
                                                                      │                          │
                                              anomaly / forecast / briefing ◄──── feedback dataset ─► retrain
```

- **Fallback chain (never blocks):** ML → rules → (optional) LLM. Confidence gate at 0.80.
- **Existing code reused:** `AiGatewayService`, `AiInferenceService`, `AiRuleEngineService`, model registry, feature store, semantic cache, cost tracking, experiments, SHAP explainer, chat service, recommendation approve/override/reject flow, audit trail.

---

## 5. Scale & Performance Commitments (10k–100k orders/day)

| Load | Behavior |
|------|----------|
| 100k orders/day (~1.2/s avg; 30–100/s peaks) | Routing decision <50 ms; forecast inference ~1–10 ms in-process; a single JVM suffices |
| Batch/wave optimization | CP-SAT per wave, async, non-blocking |
| Multi-tenant | Global model + per-tenant calibration; isolated approval queues & audit |
| Failures | Rules always serve as backstop; gateway timeout → fallback; no order is ever dropped |

---

## 6. Phased Rollout

| Phase | Capability | Shipped autonomy |
|-------|-----------|------------------|
| **0** | Data plumbing, real feature store, training-data export | — |
| **1** | Demand forecasting (quantiles + reconciliation + calibration) | Recommend |
| **2** | Intelligent routing, CP-SAT allocator, predictive ATP | Recommend |
| **3** | Slotting, replenishment/safety stock, anomaly detection | Recommend |
| **4** | Agentic layer (autonomy dial, LLM explanations, consensus-seeking) | Dial (RECOMMEND → AUTO_LOW_RISK) |
| **5** | MLOps hardening (drift, shadow deploys, experiments) | — |

Every phase keeps the rule/LLM fallback, so each is safe to ship incrementally.

---

## 7. References

- Makridakis et al., *The M5 Accuracy Competition: Results, Findings, and Conclusions*, International Journal of Forecasting 38(4):1346–1364, 2022.
- Hobor, Brcic, Polutnik, Kapetanovic, *Comparative Analysis of Modern ML Models for Retail Sales Forecasting*, arXiv:2506.05941, 2025 (rev. 2026).
- Jannelli, Schöpf, Bickel, Netland, Brintrup, *Agentic LLMs in the Supply Chain: Towards Autonomous Multi-Agent Consensus-Seeking*, arXiv:2411.10184, 2024.
- Tang, Yang, Wang, Chen, Zhao, *SOAR: Real-Time Joint Optimization of Order Allocation and Robot Scheduling in RMFS*, arXiv:2605.03842, 2026 (Beihang / Geekplus).
- Daios, Kladovasilakis, Kelemis, Kostavelis, *AI Applications in Supply Chain Management: A Survey*, Applied Sciences 15(5):2775, 2025.
- Gartner, *Predicts 60% of Supply Chain Disruptions Will Be Resolved Without Human Intervention by 2031*, March 2026.
- Blue Yonder, *AI & Machine Learning / AI Agents* (SADA loop, supply-chain knowledge graph), 2026.
