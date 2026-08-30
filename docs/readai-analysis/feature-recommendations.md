# Feature Recommendations for Nexus OMS

Prioritized build recommendations derived from real-market operations. Each recommendation names the capability, the market evidence, and a suggested scope. This is **design-level** — implementation details belong in the plan.

---

## P0 — Must Build (Market-Defining)

### 1. Fulfillment Routing / Order Brokering Engine
**Market evidence:** gorjana, Rails, SoC, RFID all depend on routing.
**Scope:**
- Location selection by available inventory + capacity limits
- Fallback / capacity-override rules
- Ship-from-store vs. ship-from-warehouse decisions
- Automatic reroute on rejection or stockout
- International / multi-location routing (country-aware)
- Routing audit trail

### 2. Store Operations Layer
**Market evidence:** Lovers (BOPIS + DoorDash), gorjana (ship-to-me).
**Scope:**
- Store-facing order screen (unified BOPIS + on-demand)
- On-demand order type (DoorDash/Uber) with all-or-nothing cancellation
- BOPIS partial fulfillment
- Customer receipt + pick ticket printing (mobile/Epson)
- Long-lived store sessions
- Associate fulfillment tracking + productivity reporting

---

## P1 — High Value

### 3. Bidirectional Channel Sync Framework
**Market evidence:** Chelan (OFBiz sync conflicts), Shopify/NetSuite (fulfillment mismatches), Rails (mapping).
**Scope:**
- Sync engine with conflict detection (stale flags, versioning, locks)
- Idempotent retryable jobs + recovery job
- Reconciliation reporting (pending/queued/completed counts)
- Field-level mapping control
- Sync-status surfacing on order pages

### 4. Transfer Orders & Cycle Counting
**Market evidence:** SIMKHAI, RFID.
**Scope:**
- Store transfer-order receiving (partial, over-receipt, unexpected items, tracking scan)
- Transfer-order fulfillment (partial, labels, pick tickets, store-created)
- Cycle counting (hard/directed/dynamic, concurrent, manager approval, variance approval)
- Serialized/RFID item tracking (EPC)

### 5. MCP Server for AI-Agent Access
**Market evidence:** MCP/GraphQL meeting (gorjana pilot, then Lovers/ADOC/Mephisto).
**Scope:**
- Expose Nexus REST/GraphQL as MCP tools
- Query permissions + cost limits
- Order debugging, reconciliation, test validation via AI agents
- API-key for internal, OAuth/SSO for client-facing

---

## P2 — Valuable

### 6. Replenishment Engine
**Market evidence:** SoC.
**Scope:**
- System-directed drop-off locations
- Flexible SKU-to-location suggestions
- Locked-source-location handling (cancel/notify/fallback)
- ATP/availability calculation surfaced

### 7. Returns / Exchange Complexity
**Market evidence:** gorjana.
**Scope:**
- Multi-outcome return claims (split while retaining claim ID)
- Store-credit ledger
- Exchange price/tax difference handling
- NetSuite discount-item mapping

---

## P3 — Nice to Have

### 8. Import Engine Performance & Migration Tooling
**Market evidence:** Agape (2–3 min/order import), Chelan (production reset).
**Scope:**
- Optimize bulk import throughput
- Deployment/migration runbooks

---

## Suggested Sequencing

1. **Routing engine** first — it's the core OMS differentiator and unblocks most accounts.
2. **Store ops** next — Lovers is a live, paying kickoff; gorjana is live.
3. **Bidirectional sync** — needed to prevent the data-integrity pain every integration account hits.
4. **Transfer orders + cycle counting** — SIMKHAI/RFID are actively demoing.
5. **MCP server** — low effort, high strategic value, differentiates Nexus from competitors.
