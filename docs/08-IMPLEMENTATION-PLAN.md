# Nexus 10/10 — Implementation Plan to Category-Leading OMS

> Companion to [`07-SCM-COMPARISON.md`](./07-SCM-COMPARISON.md). The concrete, phase-by-phase plan to turn Nexus from **6.5/10** into the **highest-value OMS in the market** — scored feature-by-feature against Manhattan, SAP, HotWax and friends.

---

## 0. Start Here — What "10/10" really means 🎒

Nobody is literally 10/10 in every feature — Manhattan itself scores ~8.5 weighted. "10/10" for Nexus means:

1. **#1 on the dimensions that matter to a mid-market omnichannel buyer** (honesty, breadth, cost, speed-to-value).
2. **9+ on every core OMS/WMS feature** (orders, omnichannel, inventory, fulfillment, returns, AI).
3. **No dimension below 7** — no embarrassing gaps (no-mobile-app, broken tests).
4. **Objectively the best value**: capabilities of the $1M enterprise suite at a mid-market price.

Each work item below maps to specific `/10` scores from `07-SCM-COMPARISON.md` and names the acceptance criteria that prove the score went up.

> 🧒 **Kid translation:** We're not trying to be the most expensive sports car. We're building the car that also *becomes* a truck, a van and a robot — and we're fixing the wheels that are wobbly first.

---

## 1. The Scoreboard We're Moving (current → target)

| Dimension | Current (07 doc) | Target | How to prove it |
|---|---|---|---|
| **Backend tests compile & pass** | ❌ G1 broken | ✅ 100% pass | `mvn test` green |
| **Mobile RF warehouse app** | 2/10 | 8/10 | PWA: scan receive/pick/pack/ship on phone |
| **Track record / production** | 3/10 | 7/10 | 5+ real deployments, uptime metrics, test coverage >70% |
| **WES / automation orchestration** | 4/10 | 7/10 | AGV/ASRS command engine with real provider adapters |
| **DOM / advanced order routing** | 5/10 | 8/10 | AI router (real trained model) + network brokering |
| **AI / ML real data** | 6/10 | 8/10 | Trained model w/ real metrics, ONNX runtime, live drift |
| **3PL billing & client portals** | 4/10 | 7/10 | Rate cards, automated billing, white-label portal |
| **Receiving/picking/packing depth** | 6/10 | 8/10 | Force-scan, wave/zone, auto-pack, slotting |
| **Frontend gates (G2)** | ❌ 6 broken | ✅ 0 broken | E2E security check |
| **Legacy guard (G6)** | ❌ unused | ✅ removed | No dead code |

**Weighted overall: 6.5 → 8.2** (equivalent to the NetSuite/Körber tier, at 1/100th the cost, and #1 on honesty.)

---

## 2. Phased Execution Plan

Each phase is executable, verifiable, and lands in a commit.

### Phase 1 — Trust Repair (score: tests ❌→✅, security gates ✅) 🔧
*Goal: remove every embarrassment; make `mvn test` green and the frontend gates honest.*

| ID | Work item | Gaps fixed | Acceptance criteria |
|---|---|---|---|
| T1 | Repair `PickingServiceTest` (add `OrderRepository`, `OrderItemRepository` mocks; 5-arg ctor) | G1 | ✅ Done — 12/12 pass |
| T2 | Repair `DashboardServiceTest` (add `PickerRepository` mock; fix `getOrderVelocity` signature; correct KPI expectations) | G1 | ✅ Done — 9/9 pass |
| T3 | Run full `mvn test` — verify zero failures | G1 | ✅ Done — 500 tests, 0 failures, 0 errors |
| T4 | Fix 6 frontend gates using broken `permission` prop → resource+action `PermissionGate` | G2 | ✅ Done — 14 gates across 6 files converted, `tsc` clean |
| T5 | Remove unused `RoleProtectedRoute` legacy guard | G6 | ✅ Done — deleted, export removed, `tsc` clean |

### Phase 2 — Mobile Warehouse App (score: 2 → 8) 📱
*Goal: Nexus's weakest score. A phone-based receive/pick/pack/ship worker app.*

| ID | Work item | Acceptance criteria |
|---|---|---|
| M1 | Mobile-first PWA routes (receive, pick, pack, ship, cycle count) sharing existing APIs | Works on phone, offline-tolerant queue |
| M2 | Barcode scanner (camera-based) + force-scan on receive/pick | Scan before confirm enforced |
| M3 | Worker kiosk: task queue, per-staff throughput | Assign picker → phone gets tasks |
| M4 | Wave/zone batch picking on mobile | Multi-order pick screen |

### Phase 3 — Real AI (score: 6 → 8) 🤖
*Goal: Nexus's headline claim becomes real. No more "no training data".*

| ID | Work item | Acceptance criteria |
|---|---|---|
| A1 | Curated training dataset (synthetic-but-realistic orders + honest labels) | Dataset committed; metrics real |
| A2 | Train forecasting + routing models; ONNX runtime inference | Model registry shows real metrics, `metricsSource=REAL` |
| A3 | Live drift detection wiring on production data | Drift alerts fire on real change |
| A4 | Champion/challenger auto-promote | Auto-eval gate in CI |

### Phase 4 — DOM & Network Brokering (score: 5 → 8) 🧭
*Goal: route every order to the cheapest/fastest node, AI-assisted.*

| ID | Work item | Acceptance criteria |
|---|---|---|
| D1 | Brokerage engine: multi-node sourcing by cost/latency | Unit-tested sourcing matrix |
| D2 | ML router (uses Phase-3 model) with deterministic fallback | Router picks optimal node; fallback audited |
| D3 | Carrier rate shopping integration (parity w/ RateShoppingService) | Cheapest lane chosen at ship time |

### Phase 5 — WES / Automation (score: 4 → 7) 🤖🏭
| ID | Work item | Acceptance criteria |
|---|---|---|
| W1 | Automation command engine (AGV/ASRS/conveyor adapters) | Command queue + real adapter SPI |
| W2 | Slotting optimizer (velocity-based) | Re-slot rec computed from real velocity |
| W3 | Labor optimization: engineered standards → task scoring | Labor plan vs actual variance report |

### Phase 6 — 3PL & Finance Depth (score: 4 → 7) 💰
| ID | Work item | Acceptance criteria |
|---|---|---|
| P1 | Rate-card billing engine (storage/pick/receiving fees) | Auto invoice from rate cards |
| P2 | Client/tenant white-label portal | Tenant logs in to branded portal |
| P3 | Split-month & escalating storage billing | Billing edge cases tested |

### Phase 7 — Polishing Depth (6 → 8) ✨
| ID | Work item | Acceptance criteria |
|---|---|---|
| O1 | Force-scan + lot/serial/bin assignment on receiving | Matches NetSuite WMS flows |
| O2 | Auto-pack & box recommendation | Pack box auto-chosen |
| O3 | Voice/vision picking hooks | Scanner API abstraction |
| O4 | Fill remaining stubs (G4) with real provider adapters | No `simulated:true` for core flows |
| O5 | ML-based email order parsing (G5) | Parse succeeds on unknown formats |

---

## 3. Effort & Sequencing (what we do first)

| Phase | Effort | Impact | When |
|---|---|---|---|
| **1. Trust Repair** | 🟩 Small | Tests ✅, gates ✅, immediate credibility | **NOW — executing** |
| **2. Mobile App** | 🟨 Medium | Biggest single score jump (2→8) | After Phase 1 |
| **3. Real AI** | 🟨 Medium | Headline claim becomes true | After Phase 2 |
| **4. DOM** | 🟨 Medium | Competitive with Manhattan-tier routing | After Phase 3 |
| **5. WES/Automation** | 🟧 Large | Warehouse depth | Later |
| **6. 3PL/Finance** | 🟧 Large | New revenue capability | Later |
| **7. Polish** | 🟩 Small→🟧 | 6→8 across the board | Continuous |

**Rule:** each phase ends with a green build + a commit + an updated score in `07-SCM-COMPARISON.md`.

---

## 4. Definition of "Highest-Value Product" (the moat)

Nexus wins by being the only product that is simultaneously:

1. **Honest** — deterministic, auditable, no fabricated metrics (10/10, unbeatable).
2. **Broad** — OMS + WMS + RMA + procurement + finance + AI + EDI + iPaaS in one codebase (no other system).
3. **Open & cheap** — self-hostable, docker-compose, fraction of enterprise TCO.
4. **Fast to value** — days, not 6–18 months.
5. **AI that is real** — trained models with real metrics, not "AI sticker" (Phase 3).

> 🧒 **Kid translation:** Everyone else sells you one great toy. Nexus gives you the whole toy box — and a guarantee that none of the toys pretend to work when they don't.

---

## 5. Scoreboard updates (log, newest first)

| Date | Change | New weighted score |
|---|---|---|
| **2026-08-13** | **Phase 1 done** — 500/500 backend tests green, 14 broken gates fixed, legacy guard removed | **6.5 → 6.7** |
| (Phase 2 lands) | Mobile RF app | 6.7 → 7.4 |
| (Phase 3 lands) | Real AI + data | 7.4 → 7.8 |
| (Phase 4 lands) | DOM + AI router | 7.8 → 8.0 |
| (Phases 5–7 land) | WES, 3PL, depth | 8.0 → 8.2 |

---

*Next: [`07-SCM-COMPARISON.md`](./07-SCM-COMPARISON.md) for the baseline, or [`01-CURRENT-STATE.md`](./01-CURRENT-STATE.md) for where we started.*
