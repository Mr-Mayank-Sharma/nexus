# Nexus OMS — Data Flow

> End-to-end data movement across the platform: ingestion, orchestration, fulfillment, integration and AI. Mermaid `sequenceDiagram`/`flowchart` notation. See [`02-TECHNICAL-ARCHITECTURE.md`](./02-TECHNICAL-ARCHITECTURE.md) for infrastructure and [`features/`](./features/) for per-module flows.

---

## 1. The Big Picture (order → dispatch → analytics)

```mermaid
flowchart LR
    CH[Sales Channel] -->|webhook / poll / import| ING[Ingestion]
    ING -->|IntegrationMessage| EVT[EventBus / Kafka]
    EVT --> ORD[Order Orchestration]
    ORD --> ROUT[Routing & Broker]
    ROUT -->|allocatable| ATP[ATP Check]
    ROUT -->|unfulfillable| PARK[Parked / Brokering Queue]
    ATP --> WAVE[Wave Planning]
    WAVE --> PICK[Picklist]
    PICK --> PACK[Packing]
    PACK --> SHIP[Shipment & Carrier]
    SHIP --> TRACK[Tracking Events]
    SHIP --> INV[Invoices & Finance]
    ORD -.-> AI[AI Platform: forecast / rules / briefings]
    AI -.->|suggestions| ROUT
    AI -.->|drift fallback| ROUT
```

---

## 2. Order Intake Flow

```mermaid
sequenceDiagram
    participant CH as Channel (Shopify/BC/Amazon)
    participant HUB as Integration Hub
    participant MB as EventBus/Kafka
    participant SVC as OrderService
    participant DB as Postgres

    CH->>HUB: webhook / poll payload
    HUB->>HUB: normalize → IntegrationMessage (direction=IN)
    HUB->>MB: publish order.created
    MB->>SVC: consume event
    SVC->>DB: insert NxOrder + NxOrderItem
    SVC->>SVC: apply routing rules / ATP
    SVC->>DB: NxOrderAllocation / NxParkedOrder
    SVC-->>HUB: ack / sync result (idempotent by channel_order_id)
    HUB-->>CH: channel confirmation
```

**Idempotency:** orders keyed by `channel_order_id` + store; duplicate webhooks are skipped.

---

## 3. Fulfillment Flow (wave → ship)

```mermaid
flowchart TD
    A[Ready orders] --> B[Wave rule evaluation]
    B --> C{NxWave created}
    C --> D[Picklists generated]
    D --> E[Picker assignment]
    E --> F[Pick confirm → NxPicklistItem]
    F --> G[Stage & pack → NxPackage]
    G --> H[Rate shopping → carrier]
    H --> I[Label → NxShipment]
    I --> J[Manifest + load]
    J --> K[Tracking events / POD]
    G -.->|exception| X[NxFulfillmentException]
    X -->|resolve| A
```

---

## 4. Integration Hub Data Flow (iPaaS)

```mermaid
flowchart LR
    EXT[External systems] -->|connector| FACT[ConnectorFactory]
    FACT --> MAP[DataMapper transform]
    MAP --> VAL[Validation rules]
    VAL -->|pass| MSG[IntegrationMessage]
    VAL -->|fail| DLQ[IntegrationDLQ]
    MSG --> EVT[EventBus]
    EVT --> CONS[Consumers: orders, inventory, shipments]
    CONS --> DB[(Postgres)]
    DB --> EXP[Export jobs / EDI / webhooks]
    EXP --> EXT
```

**Supporting stores:** `NxIntegrationFlow` + steps (transform/validation), `NxIntegrationSyncConfig`, `NxIntegrationAuditLog`, `IntegrationCDCEvent` for change-data-capture.

---

## 5. AI Platform Data Flow

```mermaid
flowchart LR
    SRC[Orders, inventory, labor, shipments] --> FEAT[Feature values]
    FEAT --> DS[AiDataset]
    DS --> TJ[AiTrainingJob]
    TJ -->|real metrics| MV[AiModelVersion]
    TJ -->|no metrics| NM[NO_METRICS flag]
    MV --> DEP[AiDeployment]
    DEP --> GATE[AiGatewayRoute]
    GATE --> INF[AiInferenceLog]
    RULES[AiRuleEngineService] -->|deterministic fallback| DEC[Decision]
    GATE --> DEC
    DEC -->|outcome| FB[AiRuleFallback / calibrations]
    DEC -.-> COST[AiCostLog]
```

**Honesty guarantees (Phase 2.5):**
- Training metrics are stored only when real (`metricsSource=REAL`), else `NO_METRICS` + null metrics.
- Rule evaluation is deterministic from config + order input.
- Automation results carry real `elapsed_ms` and explicit `simulated` flags.

---

## 6. Returns Data Flow

```mermaid
flowchart LR
    RQ[Return request] --> RM[NxReturn + items]
    RM --> AUTH[Authorization / RMA]
    AUTH --> RC[Receipt & inspection]
    RC --> DSP{Disposition}
    DSP -->|restock| INV[Inventory +]
    DSP -->|destroy/donate| WR[Write-off]
    DSP -->|reject| REJ[Rejection reason]
    RC --> REF[Refund / credit memo]
```

---

## 7. Procurement Data Flow

```mermaid
flowchart LR
    REQ[Replenishment suggestion / demand] --> PR[NxPurchaseRequest]
    PR --> AP[Approval rule]
    AP --> PO[NxPurchaseOrder]
    PO --> REC[NxInventoryReceipt]
    REC --> ATP[ATP updated]
    REQ -.-> RFQ[RFQ + responses]
    RFQ -->|award| PO
```

---

## 8. Yard & Shipping Data Flow

```mermaid
flowchart LR
    TR[Trailer arrives] --> YARD[NxYardLocation assign]
    YARD --> EV[NxTrailerEvent]
    EV --> DOOR[Dock door assignment]
    DOOR --> LOAD[Loading]
    LOAD --> OUT[Trailer departs]
    OUT --> DWELL[Dwell / audit]
    SHIP[NxShipment] --> RATE[Rate shopping log]
    RATE --> CAR[Carrier account]
    CAR --> TRACK[Tracking events]
```

---

## 9. Cross-cutting concerns in every flow

| Concern | Mechanism |
|---|---|
| **Tenancy** | Every entity query scoped by `tenant_id` |
| **Authorization** | `PermissionAuthorizationFilter` path→resource→permission (39 mappings) |
| **Audit** | `NxAuditLog` writes on sensitive actions; `IntegrationAuditLog` on syncs |
| **Idempotency** | `channel_order_id` keys, import tokens, sync-state tracking |
| **Resilience** | Kafka retries + Resilience4j circuit breakers |
| **Observability** | Micrometer/Prometheus counters per flow stage; logstash JSON |

---

*Next: [`06-BUSINESS-FLOW-RBAC.md`](./06-BUSINESS-FLOW-RBAC.md) — who can touch what at each stage, and [`features/`](./features/) for per-module data flows.*
