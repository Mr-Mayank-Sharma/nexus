# Nexus P1 Implementation Specs — T-08, T-10, T-11, T-13

> **Purpose:** Turn the four **P1** market-gap tickets from [`nexus-market-gap-tickets-2.md`](./nexus-market-gap-tickets-2.md) into **buildable implementation specs** grounded in the actual Nexus codebase. These are the differentiator tickets (RFID, returns finance, MCP server, bidirectional sync) that close the biggest remaining feature gaps vs. HotWax.
>
> **Scope:** T-08 (RFID/serialized inventory), T-10 (returns/exchange financial reconciliation), T-11 (MCP server), T-13 (bidirectional sync & conflict handling). The two P0 tickets (T-09, T-12) are in [`nexus-p0-implementation-specs.md`](./nexus-p0-implementation-specs.md).
>
> **Codebase grounding (verified):**
> - Backend: Spring Boot 3 + Java 17, package `com.nexus.oms`
> - Latest Flyway migration: **V60** → new migrations start at **V61**
> - **T-08:** existing `NxInventory`, `NxInventoryReceipt`, `NxCycleCount` (V5) — need a serialized/EPC registry
> - **T-10:** existing `NxReturn`, `NxReturnItem` (V13), `CreditMemo` — `CreditMemo` has a **single `invoice_id`** (the exact limitation the transcript describes: one memo applied to multiple invoices breaks accounting)
> - **T-11:** rich AI platform exists (`ai_models`, `ai_deployments`, `AiCostLog`, `AiInferenceLog`, `AiGatewayRoute`) — MCP server reuses these
> - **T-13:** integration hub exists (`integration/connector/erp`, `ecommerce/ShopifyConnector`) — need conflict detection/versioning

---

## T-08: RFID / Serialized Inventory (EPC Registry) — **P1**

### Problem (from transcript `rfid-inventory.json`)

HotWax is actively scoping RFID with a partner. The entire call is a design discussion on how to handle RFID guns, EPC uniqueness, dedup, and serialized cycle counting. HotWax explicitly says RFID is **"perpetually not smooth"** and wants to make it "widely available" — a clear differentiator Nexus can own.

**Nexus entry point:** start with the **input-adapter layer** (pure software) and defer hardware/printing. The existing `NxInventoryReceipt` and `NxCycleCount` entities are the natural integration points.

### Design

Two layers:
1. **EPC registry** — a `serialized_inventory` table keyed by EPC (unique serial), with status/location/SKU.
2. **RFID input adapter** — a middleware layer between the RFID gun and existing inventory apps (receiving, cycle counting, fulfillment). Handles: read, dedup, decode, encode, reject-foreign.

### Flyway migration — `V63__rfid_serialized_inventory.sql`

```sql
-- T-08: Serialized inventory (EPC registry) for RFID.
-- One row per unique EPC (serialized tag). Supports two modes:
--   FULL_REGISTRY  = one-for-one serialized tracking
--   DECODE_TO_UPC  = traditional quantity uptick (EPC decoded to UPC, no registry)

CREATE TABLE nx_serialized_inventory (
    id              UUID PRIMARY KEY,
    tenant_id       UUID NOT NULL,
    location_id     UUID,               -- store / warehouse location
    epc             VARCHAR(128) NOT NULL,  -- unique EPC (serialized tag)
    sku             VARCHAR(100),           -- decoded UPC/SKU
    status          VARCHAR(20) NOT NULL DEFAULT 'ACTIVE',
                    -- ACTIVE | DAMAGED | ON_HOLD | MISSING | RETURNED
    mode            VARCHAR(20) NOT NULL DEFAULT 'FULL_REGISTRY',
                    -- FULL_REGISTRY | DECODE_TO_UPC
    received_at     TIMESTAMP,
    last_seen_at    TIMESTAMP,
    created_at      TIMESTAMP NOT NULL DEFAULT now(),
    updated_at      TIMESTAMP NOT NULL DEFAULT now(),
    UNIQUE (tenant_id, epc)
);

CREATE INDEX idx_serialized_inv_location ON nx_serialized_inventory(tenant_id, location_id, status);
CREATE INDEX idx_serialized_inv_sku ON nx_serialized_inventory(tenant_id, sku);

-- RFID scan session: batches EPC reads from a wand, dedups in-session.
CREATE TABLE nx_rfid_scan_session (
    id              UUID PRIMARY KEY,
    tenant_id       UUID NOT NULL,
    location_id     UUID,
    session_type    VARCHAR(20) NOT NULL,  -- RECEIVING | CYCLE_COUNT | FULFILLMENT
    mode            VARCHAR(20) NOT NULL DEFAULT 'FULL_REGISTRY',
    started_by      UUID,
    started_at      TIMESTAMP NOT NULL DEFAULT now(),
    completed_at    TIMESTAMP,
    status          VARCHAR(20) NOT NULL DEFAULT 'OPEN',  -- OPEN | COMPLETED | CANCELLED
    -- JSON: EPCs seen in this session (deduped). Foreign tags rejected here.
    seen_epcs       JSONB,
    rejected_epcs   JSONB
);
```

### Backend — new services

Package: `com.nexus.oms.service`

```java
@Service
public class RfidIngestionService {
    // 1. HIGH-THROUGHPUT INGESTION
    // Batch/stream EPC reads over Bluetooth. Dedup by EPC within a session
    // (same tag read 100x while wand held in place -> count once).
    // Idempotent writes keyed on (tenant, epc).
    @Transactional
    public RfidScanResult ingestEpcs(UUID sessionId, List<String> epcs) { ... }

    // 2. EPC -> UPC DECODE
    // Parse EPC string on the fly to extract UPC/SKU + serial.
    // Support SGTIN-96 and common encodings. Handle pre-encoded labels
    // from other sources (not just self-encoded).
    public DecodedEpc decodeEpc(String epc) { ... }

    // 3. REJECT FOREIGN TAGS
    // Wands pick up tags from the store next door, shoes, clothing.
    // Decode the EPC and reject tags that aren't this store's inventory.
    public boolean isForeignTag(String epc, UUID locationId) { ... }

    // 4. RECEIVING
    // Register each EPC at the location (FULL_REGISTRY) or uptick quantity (DECODE_TO_UPC).
    @Transactional
    public void receive(UUID sessionId, UUID locationId, List<String> epcs) { ... }

    // 5. SERIALIZED CYCLE COUNT
    // Reconcile a full-store scan against the registry:
    //   - EPCs that existed but are now MISSING
    //   - new EPCs never received
    //   - EPCs in DAMAGED / ON_HOLD status (counted separately, not toward active)
    @Transactional
    public CycleCountResult runSerializedCycleCount(UUID sessionId, UUID locationId) { ... }

    // 6. RETAG
    // Re-encode + print a new tag for missing tags and returns.
    // (Printer calibration is a known pain point - abstract it.)
    public RetagResult retag(String oldEpc, String newEpc, UUID locationId) { ... }
}
```

### Backend — REST endpoints (`RfidController`)

| Method | Path | Purpose |
|--------|------|---------|
| `POST` | `/api/v1/rfid/sessions` | Open a scan session (receiving / cycle count / fulfillment) |
| `POST` | `/api/v1/rfid/sessions/{id}/epcs` | Ingest a batch of EPC reads (dedup, reject foreign) |
| `POST` | `/api/v1/rfid/sessions/{id}/complete` | Complete the session (run cycle count if applicable) |
| `GET` | `/api/v1/rfid/inventory` | Query serialized inventory by location/status/SKU |
| `POST` | `/api/v1/rfid/retag` | Re-encode + print a new tag |
| `GET` | `/api/v1/rfid/decode/{epc}` | Decode an EPC to UPC/SKU |

### Frontend — React

New page `RfidScanPage.tsx`:
- **Scan session view** — live EPC count, dedup indicator, foreign-tag rejections
- **Receiving flow** — register EPCs at a location (or decode-to-UPC)
- **Cycle count result** — missing / new / damaged / on-hold breakdown
- **Mode toggle** — per-location: FULL_REGISTRY vs DECODE_TO_UPC

### Acceptance test

1. A store associate receives a box via RFID gun → all EPCs registered at the store (FULL_REGISTRY).
2. A full-store cycle count detects: (a) an EPC received but now missing, (b) a foreign tag rejected, (c) an EPC in damaged status counted separately.
3. No double-counting when the wand is held in place (dedup in-session).
4. The same flow works in DECODE_TO_UPC mode (quantities only, no EPC registry).
5. Retag re-encodes a missing tag and updates the registry.

### Notes
- **Start with the input-adapter layer** (pure software); defer hardware/printing.
- **This is a differentiator** — HotWax says RFID is "perpetually not smooth"; Nexus owning the EPC registry + input adapter is a wedge.
- **Reuse `NxInventoryReceipt` and `NxCycleCount`** as the integration points for receiving and cycle counting.

---

## T-10: Returns / Exchange Financial Reconciliation — **P1**

### Problem (from transcript `gorjana-weekly.json`)

A live account needs **split credit memos** for multi-transaction returns, **cross-location exchanges with tax differences**, and **store-credit vs. like-for-like** handling. HotWax currently creates a **single credit memo** and applies multiple invoices to it, which breaks accounting.

**Current state:** Nexus's `CreditMemo` entity has a **single `invoice_id`** — the exact limitation. A return with two transactions needs **two separate credit memos**, each with its own invoice application.

### Design

Extend the returns/finance model to support:
1. **Credit-memo splitting** — a return claim generates multiple credit memos (one per transaction), each with its own invoice application.
2. **Outcome model** — per-return-outcome: store credit, like-for-like exchange, refund; with split capability and a shared sales-return identifier.
3. **Tax-delta handling** — compute and apply tax differences for cross-location exchanges.
4. **Payment reconciliation** — correctly classify customer-deposit balancing payments.

### Flyway migration — `V64__returns_financial_reconciliation.sql`

```sql
-- T-10: Returns / exchange financial reconciliation.
-- Fixes the single-credit-memo limitation: a return claim can now generate
-- MULTIPLE credit memos (one per transaction), each with its own invoice.

-- 1. Link credit memos to a shared sales-return claim (the parent).
ALTER TABLE nx_credit_memos ADD COLUMN IF NOT EXISTS sales_return_id UUID;
ALTER TABLE nx_credit_memos ADD COLUMN IF NOT EXISTS return_id UUID REFERENCES nx_returns(id);
ALTER TABLE nx_credit_memos ADD COLUMN IF NOT EXISTS outcome VARCHAR(20) DEFAULT 'REFUND';
    -- REFUND | STORE_CREDIT | LIKE_FOR_LIKE_EXCHANGE | LESSER_VALUE_EXCHANGE
ALTER TABLE nx_credit_memos ADD COLUMN IF NOT EXISTS tax_delta DECIMAL(12,2) DEFAULT 0.00;
ALTER TABLE nx_credit_memos ADD COLUMN IF NOT EXISTS source_location_id UUID;
ALTER TABLE nx_credit_memos ADD COLUMN IF NOT EXISTS target_location_id UUID;
ALTER TABLE nx_credit_memos ADD COLUMN IF NOT EXISTS customer_deposit_applied DECIMAL(12,2) DEFAULT 0.00;

-- 2. Invoice application: a credit memo can apply to multiple invoices
--    (replaces the single invoice_id assumption).
CREATE TABLE nx_credit_memo_invoice_applications (
    id              UUID PRIMARY KEY,
    tenant_id       UUID NOT NULL,
    credit_memo_id  UUID NOT NULL REFERENCES nx_credit_memos(id) ON DELETE CASCADE,
    invoice_id      UUID NOT NULL,
    amount          DECIMAL(12,2) NOT NULL,
    tax_amount      DECIMAL(12,2) DEFAULT 0.00,
    created_at      TIMESTAMP NOT NULL DEFAULT now(),
    UNIQUE (credit_memo_id, invoice_id)
);
CREATE INDEX idx_cm_invoice_app_cm ON nx_credit_memo_invoice_applications(credit_memo_id);
```

### Backend — new service `ReturnFinanceService`

Package: `com.nexus.oms.service`

```java
@Service
public class ReturnFinanceService {

    // 1. SPLIT CREDIT MEMOS
    // A return with two different transactions in the same claim must create
    // TWO separate credit memos, not one combined. Each memo gets its own
    // invoice application(s).
    @Transactional
    public List<CreditMemo> splitCreditMemos(NxReturn returnClaim) { ... }

    // 2. OUTCOME MODEL
    // Per-return-outcome: store credit, like-for-like exchange, refund.
    // Block multi-select of outcomes per return, but keep the ability to SPLIT
    // into separate transactions (store credit + like-for-like) with a shared
    // sales-return identifier.
    public void validateOutcomeSelection(NxReturn returnClaim) { ... }

    // 3. LESSER-VALUE EXCHANGE
    // Customer exchanges for a lower-cost item; difference refunded as store credit.
    // Produce a single credit memo with two invoices (exchange + store credit)
    // applied correctly.
    @Transactional
    public CreditMemo lesserValueExchange(NxReturn returnClaim, BigDecimal exchangeValue) { ... }

    // 4. CROSS-LOCATION TAX DELTA
    // Exchange from a lower-tax location to a higher-tax location (e.g., Long Beach);
    // compute and apply the tax delta in NetSuite.
    public BigDecimal computeTaxDelta(UUID sourceLocationId, UUID targetLocationId, BigDecimal amount) { ... }

    // 5. CUSTOMER-DEPOSIT BALANCING
    // Some transactions apply a customer deposit to balance the invoice;
    // classify it so it doesn't corrupt accounting.
    public void applyCustomerDeposit(CreditMemo memo, BigDecimal deposit) { ... }

    // 6. IMPACT REPORT
    // How many orders are affected by the current single-memo behavior,
    // to size the migration.
    public ImpactReport getSingleMemoImpact(UUID tenantId) { ... }
}
```

### Backend — REST endpoints (`ReturnFinanceController`)

| Method | Path | Purpose |
|--------|------|---------|
| `POST` | `/api/v1/returns/{id}/credit-memos/split` | Split a return claim into multiple credit memos |
| `POST` | `/api/v1/returns/{id}/exchange` | Process a like-for-like / lesser-value exchange |
| `GET` | `/api/v1/returns/{id}/credit-memos` | List credit memos for a return |
| `GET` | `/api/v1/finance/returns-impact` | Single-memo impact report |

### Frontend — React

Extend the returns page (`ReturnsPage` / RMA flow):
- **Outcome selector** — store credit / like-for-like / refund, with split capability (block multi-select per return, allow split into transactions)
- **Credit memo list** — show all memos for a return, each with its invoice applications
- **Tax delta display** — show the tax delta for cross-location exchanges

### Acceptance test

1. A return with two transactions generates **two separate credit memos**.
2. A lesser-value exchange produces **one credit memo with two correctly-applied invoices** (exchange + store credit).
3. A cross-location exchange applies the correct **tax delta** in NetSuite.
4. Customer-deposit balancing payments are classified correctly (no accounting corruption).
5. No accounting corruption across all scenarios.

### Notes
- **This is a finance/accounting gap** — high-stakes, low-tolerance for error.
- **Nexus's finance module** (feature 08) is the home for this.
- **The `CreditMemo` entity's single `invoice_id` is the exact limitation** — the `nx_credit_memo_invoice_applications` table fixes it.

---

## T-11: MCP Server for OMS (AI-Agent Debugging Surface) — **P1**

### Problem (from transcript `mcp-graphql-shopify-oms-integration.json`)

HotWax is actively building an **MCP server** to expose OMS tools to AI agents for **production debugging**. The goal: an AI agent can query the production system directly, with **query permissions and cost limits**, so support can debug issues without manual DB spelunking.

**Nexus advantage:** Nexus already has a rich AI platform (`ai_models`, `ai_deployments`, `AiCostLog`, `AiInferenceLog`, `AiGatewayRoute`) and Kafka events. This is a natural extension — and Nexus can productize it as a **customer-facing AI support surface** (an AI agent that answers "where's my order" and "why is this stuck").

### Design

A Spring Boot **MCP server** exposing OMS tools, with:
1. **Tool registry** — which OMS operations are exposed, with read/write classification.
2. **Permission mapping** — map MCP tools to the existing RBAC/permission model (per-tenant, per-entity).
3. **Cost / rate limiting** — per-agent query budget and rate limits; logging of agent queries.
4. **GraphQL/REST bridge** — reuse the existing GraphQL/REST layer as the backend for MCP tools (single source of truth for auth).

### Flyway migration — `V65__mcp_server_tools.sql`

```sql
-- T-11: MCP server tool registry + agent budgets.
-- Defines which OMS operations are exposed as MCP tools, their read/write
-- classification, and per-agent query budgets.

CREATE TABLE nx_mcp_tools (
    id              UUID PRIMARY KEY,
    tenant_id       UUID NOT NULL,
    tool_name       VARCHAR(128) NOT NULL,   -- e.g. "get_order_status", "get_inventory"
    description     TEXT,
    access          VARCHAR(10) NOT NULL,    -- READ | WRITE
    -- JSON: the REST/GraphQL endpoint this tool maps to (single source of truth for auth)
    endpoint        JSONB,
    is_active       BOOLEAN NOT NULL DEFAULT TRUE,
    created_at      TIMESTAMP NOT NULL DEFAULT now(),
    UNIQUE (tenant_id, tool_name)
);

CREATE TABLE nx_mcp_agent_budgets (
    id              UUID PRIMARY KEY,
    tenant_id       UUID NOT NULL,
    agent_name      VARCHAR(128) NOT NULL,
    -- Per-agent query budget (cost/volume) so an agent can't run away
    -- with expensive queries.
    query_budget    INTEGER NOT NULL DEFAULT 100,   -- max queries per period
    cost_budget     DECIMAL(12,2) NOT NULL DEFAULT 100.00,
    period          VARCHAR(20) NOT NULL DEFAULT 'DAILY',  -- DAILY | WEEKLY | MONTHLY
    is_active       BOOLEAN NOT NULL DEFAULT TRUE,
    created_at      TIMESTAMP NOT NULL DEFAULT now()
);
```

### Backend — MCP server

Package: `com.nexus.oms.integration.mcp`

```java
// Spring Boot MCP server exposing OMS tools.
// Reuses the existing REST/GraphQL layer as the backend (single source of truth for auth).

@Configuration
public class McpServerConfig {
    // Register MCP tools from nx_mcp_tools
}

@Component
public class McpToolExecutor {
    // 1. Resolve the tool from nx_mcp_tools
    // 2. Enforce RBAC: the agent's permissions map to the same per-tenant,
    //    per-entity model as the REST/GraphQL layer
    // 3. Enforce budget: check nx_mcp_agent_budgets (query + cost), log to AiCostLog
    // 4. Execute via the existing REST/GraphQL bridge
    // 5. Return the result to the agent
    public McpToolResult execute(String toolName, Map<String, Object> args, AgentContext ctx) { ... }
}

@Component
public class McpBudgetGuard {
    // Per-agent query budget and rate limits.
    // Logs every agent query to AiCostLog / AiInferenceLog.
    public boolean allowQuery(AgentContext ctx) { ... }
}
```

### Backend — REST endpoints (`McpController`)

| Method | Path | Purpose |
|--------|------|---------|
| `GET` | `/api/v1/mcp/tools` | List exposed MCP tools (read/write classification) |
| `POST` | `/api/v1/mcp/tools` | Register a tool (admin) |
| `GET/POST` | `/api/v1/mcp/agents/budgets` | Manage per-agent query/cost budgets |
| `GET` | `/api/v1/mcp/agents/{name}/usage` | Agent query/cost usage log |

### Frontend — React

New page `McpAdminPage.tsx`:
- **Tool registry** — which OMS operations are exposed, read/write classification
- **Agent budgets** — per-agent query/cost limits
- **Usage log** — agent queries, cost, latency

### Acceptance test

1. An AI agent (via MCP) can query an order's fulfillment status, inventory at a location, and shipment state — with the **same permissions as the calling user**.
2. A write-capable tool is **blocked for a read-only agent**.
3. Query cost/volume is **tracked and limited** (budget enforced).
4. The agent can debug a "stuck order" without DB access.
5. **No tenant isolation or RBAC bypass** — MCP tools never bypass the permission model.

### Notes
- **This is a differentiator** — HotWax builds it for internal debugging; Nexus can productize it as a **customer-facing AI support surface**.
- **Nexus already has the AI platform** (feature 11) and Kafka events — this is a natural extension.
- **Security is paramount** — MCP tools must never bypass tenant isolation or RBAC.

---

## T-13: Bidirectional Sync & Conflict Handling — **P1**

### Problem (from transcripts `chelan-weekly.json`, `rails-hotwax.json`, `gorjana-weekly.json`)

Sync conflicts are the **#1 integration pain across accounts**. A consigned order shipped in OFBiz got **undone** when NetSuite fed back stale data ("one overrides the other and changes what OFBiz had"). This is a silent data-corruption bug that erodes trust in the OMS.

**Current state:** Nexus has an integration hub (`integration/connector/erp`, `ecommerce/ShopifyConnector`) but needs **conflict detection/versioning** to prevent stale-overwrite.

### Design

1. **Conflict detection engine** — versioning / stale-flag / optimistic-lock on synced entities; detect when an inbound update would overwrite a newer local state; reject or queue the conflict.
2. **Idempotent sync jobs** — make all sync jobs idempotent and retryable; add a recovery job (15-min) that retries failed/pending records.
3. **Reconciliation reporting** — pending / queued / completed counts per sync direction; daily reconciliation summary.
4. **Field-level mapping control** — per-field direction/priority mapping (which side wins per field).
5. **Sync-status surfacing** — order-page sync status indicator (in-sync / pending / conflict / out-of-sync).
6. **Transaction-type handling** — graceful handling of unsupported transaction types (skip + flag, don't break the pipeline).

### Flyway migration — `V66__sync_conflict_handling.sql`

```sql
-- T-13: Bidirectional sync conflict handling.
-- Versioning + conflict ledger for synced entities, so stale inbound data
-- never silently overwrites newer local state.

-- 1. Versioning on synced entities (optimistic lock).
--    Add a version column to the core synced entities (orders, inventory,
--    shipments). Inbound updates carry the version they were based on; if the
--    local version is newer, it's a conflict.
ALTER TABLE nx_orders ADD COLUMN IF NOT EXISTS sync_version BIGINT DEFAULT 0;
ALTER TABLE nx_inventory ADD COLUMN IF NOT EXISTS sync_version BIGINT DEFAULT 0;
ALTER TABLE nx_shipments ADD COLUMN IF NOT EXISTS sync_version BIGINT DEFAULT 0;

-- 2. Conflict ledger.
CREATE TABLE nx_sync_conflicts (
    id              UUID PRIMARY KEY,
    tenant_id       UUID NOT NULL,
    entity_type     VARCHAR(50) NOT NULL,   -- ORDER | INVENTORY | SHIPMENT | ...
    entity_id       UUID NOT NULL,
    direction       VARCHAR(20) NOT NULL,   -- INBOUND | OUTBOUND
    source_system   VARCHAR(50) NOT NULL,   -- NETSUITE | SHOPIFY | OFBIZ | ...
    -- The version the inbound update was based on vs. the local version
    inbound_version BIGINT,
    local_version   BIGINT,
    -- JSON: the conflicting fields (field -> {inbound, local})
    conflicting_fields JSONB,
    resolution      VARCHAR(20) DEFAULT 'PENDING',
                    -- PENDING | LOCAL_WINS | INBOUND_WINS | MERGED | MANUAL
    status          VARCHAR(20) DEFAULT 'OPEN',  -- OPEN | RESOLVED | IGNORED
    created_at      TIMESTAMP NOT NULL DEFAULT now(),
    resolved_at     TIMESTAMP,
    resolved_by     UUID
);
CREATE INDEX idx_sync_conflicts_tenant ON nx_sync_conflicts(tenant_id, status);
CREATE INDEX idx_sync_conflicts_entity ON nx_sync_conflicts(entity_type, entity_id);

-- 3. Field-level mapping control (which side wins per field).
CREATE TABLE nx_sync_field_mappings (
    id              UUID PRIMARY KEY,
    tenant_id       UUID NOT NULL,
    entity_type     VARCHAR(50) NOT NULL,
    field_name      VARCHAR(100) NOT NULL,
    direction       VARCHAR(20) NOT NULL,   -- INBOUND | OUTBOUND
    winner          VARCHAR(20) NOT NULL,   -- LOCAL | REMOTE
    is_active       BOOLEAN NOT NULL DEFAULT TRUE,
    created_at      TIMESTAMP NOT NULL DEFAULT now(),
    UNIQUE (tenant_id, entity_type, field_name, direction)
);

-- 4. Sync job status (pending / queued / completed / failed) for reconciliation.
CREATE TABLE nx_sync_job_status (
    id              UUID PRIMARY KEY,
    tenant_id       UUID NOT NULL,
    job_name        VARCHAR(128) NOT NULL,
    direction       VARCHAR(20) NOT NULL,
    status          VARCHAR(20) NOT NULL,   -- PENDING | QUEUED | COMPLETED | FAILED
    records_total   INTEGER DEFAULT 0,
    records_processed INTEGER DEFAULT 0,
    records_failed  INTEGER DEFAULT 0,
    last_run_at     TIMESTAMP,
    next_run_at     TIMESTAMP,
    created_at      TIMESTAMP NOT NULL DEFAULT now(),
    updated_at      TIMESTAMP NOT NULL DEFAULT now()
);
```

### Backend — new service `SyncConflictService`

Package: `com.nexus.oms.integration`

```java
@Service
public class SyncConflictService {

    // 1. CONFLICT DETECTION
    // When an inbound update arrives, compare its base version against the
    // local version. If local is newer, it's a conflict: reject or queue it,
    // never silently overwrite.
    public ConflictDecision detectConflict(String entityType, UUID entityId,
                                           long inboundVersion, long localVersion) { ... }

    // 2. RESOLUTION
    // Apply the field-level mapping (nx_sync_field_mappings) to decide which
    // side wins per field. Default: LOCAL_WINS (preserve newer local state).
    @Transactional
    public SyncConflict resolveConflict(UUID conflictId, String resolution) { ... }

    // 3. IDEMPOTENT, RETRYABLE SYNC JOBS
    // Make all sync jobs idempotent (keyed on external id + version) and
    // retryable. Recovery job (15-min) retries failed/pending records.
    @Scheduled(cron = "${nexus.sync.recovery.cron:0 */15 * * * *}")
    public void recoveryJob() { ... }

    // 4. RECONCILIATION REPORTING
    // Pending / queued / completed counts per sync direction; daily summary.
    public SyncReconciliationReport getReconciliationReport(UUID tenantId) { ... }

    // 5. TRANSACTION-TYPE HANDLING
    // Unsupported NetSuite transaction types (e.g., FRTTRA) are skipped + flagged,
    // not fatal to the pipeline. Missing consignment APIs handled gracefully.
    public void handleUnsupportedTransaction(String txType, String payload) { ... }
}
```

### Backend — REST endpoints (`SyncController`)

| Method | Path | Purpose |
|--------|------|---------|
| `GET` | `/api/v1/sync/conflicts` | List open sync conflicts |
| `POST` | `/api/v1/sync/conflicts/{id}/resolve` | Resolve a conflict (local/inbound/merge/manual) |
| `GET` | `/api/v1/sync/reconciliation` | Reconciliation report (pending/queued/completed per direction) |
| `GET/POST` | `/api/v1/sync/field-mappings` | Manage per-field winner mapping |
| `POST` | `/api/v1/sync/recovery` | Trigger the recovery job on demand |

### Frontend — React

Extend the order page + new `SyncAdminPage.tsx`:
- **Order-page sync status indicator** — in-sync / pending / conflict / out-of-sync
- **Conflict queue** — list of conflicts with the conflicting fields, resolve action
- **Field mapping editor** — per-field winner (local vs remote)
- **Reconciliation report** — pending/queued/completed per direction

### Acceptance test

1. A consigned order is shipped in the OMS; NetSuite feeds back a stale (pre-ship) version. The OMS **detects the conflict and preserves the shipped state** instead of undoing it.
2. The conflict is surfaced on the order page and in the reconciliation report.
3. A sync job retried after a failure is **idempotent** (no duplicate records).
4. Field-level mapping lets an operator set "gross wins" for a specific field.
5. Unsupported transaction types (FRTTRA) are skipped + flagged, not fatal.

### Notes
- **This is the #1 integration pain** across all HotWax accounts — fixing it is a direct market win and serves Nexus's "honest data" value.
- **Complements T-09** (fulfillment reconciliation): T-09 handles the Shopify→OMS direction; T-13 handles the general OMS↔ERP bidirectional conflict problem.
- **Nexus's integration hub** (feature 10) and Kafka eventing are the home for this.

---

## Sequencing & Dependencies

| Order | Ticket | Why this order |
|-------|--------|----------------|
| 1 | **T-13** (bidirectional sync) | #1 integration pain; builds on T-09's reconciliation ledger (same "truth" principle). |
| 2 | **T-10** (returns finance) | High-visibility accounting gap; `CreditMemo` single-invoice limitation is a known defect. |
| 3 | **T-08** (RFID) | Differentiator; start with input-adapter layer (pure software). |
| 4 | **T-11** (MCP server) | Differentiator; reuses existing AI platform + RBAC. |

**Shared infrastructure:** T-13 and T-09 both use the integration hub and the "truth" principle. Build the sync-versioning abstraction first so both share it. T-11 reuses the AI platform's `AiCostLog`/`AiInferenceLog` for agent budget tracking.

---

## Definition of Done (per ticket)

- [ ] Flyway migration (V63–V66) applied cleanly on a fresh DB
- [ ] Backend service + controller with tenant-scoped RBAC (no cross-tenant leakage)
- [ ] Kafka event emitted on the key state change
- [ ] React UI page/extension with the workflow
- [ ] Acceptance tests pass (the scenarios above)
- [ ] Idempotency verified (T-08: dedup EPCs; T-13: retry sync jobs)
- [ ] Security verified (T-11: MCP never bypasses RBAC/tenant isolation)
