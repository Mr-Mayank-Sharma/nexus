# Market Capability Matrix — Real-Market Needs vs. Nexus OMS

This matrix maps the **operational capabilities** that real HotWax customers actually use (from the 11 meetings) against what **Nexus OMS** currently provides.

**Legend:** ✅ Nexus covers today · 🟡 Partial / needs work · ❌ Not in Nexus today

---

## Order Management

| Real-market capability | Seen in | Nexus status | Notes |
|------------------------|---------|--------------|-------|
| Order lifecycle (created → confirmed → allocated → shipped → delivered) | All | ✅ | Nexus has Kafka events for the full lifecycle |
| Order CRUD & status sync | All | ✅ | `/orders/**` |
| Multi-channel order import (Shopify, POS, marketplace) | gorjana, Rails, Lovers, Shopify/NetSuite | 🟡 | Import engine handles files; live Shopify/POS sync is integration work |
| BOPIS (buy online, pick up in store) orders | Lovers, gorjana | 🟡 | No dedicated BOPIS screen/flow in Nexus |
| DoorDash / on-demand fulfillment orders | Lovers | ❌ | No on-demand/3P delivery order type |
| Store-rejected pickup → ship-to-me conversion | gorjana | ❌ | Requires fulfillment-type switching on an order |
| Partial fulfillment / partial shipment | SIMKHAI, SoC, Shopify/NetSuite | 🟡 | Order model supports lines; partial-shipment workflow not explicit |
| Partial payments | Shopify/NetSuite | ❌ | Payment-split/partial-payment handling absent |
| Pre-orders | SIMKHAI, RFID (Dollskill) | 🟡 | Mentioned but not a first-class Nexus flow |
| Try-and-buy / consignment | SIMKHAI | ❌ | Customization quoted at $10k — not in Nexus |

---

## Inventory

| Real-market capability | Seen in | Nexus status | Notes |
|------------------------|---------|--------------|-------|
| Inventory management & adjustments | All | ✅ | `/inventory/**` |
| Real-time inventory sync to channels (Shopify) | gorjana, Rails, SIMKHAI, Shopify/NetSuite | 🟡 | Needs connector work |
| Cycle counting (hard, directed, dynamic) | SIMKHAI, RFID | 🟡 | Counting workflows partly generic; no dedicated cycle-count module |
| Transfer orders (receiving, fulfillment, partial, over-receipt) | SIMKHAI, RFID | 🟡 | Purchase-order entity exists; store transfer-order flow not explicit |
| Serialized / RFID inventory (EPC tracking) | RFID, SIMKHAI | 🟡 | Nexus tracks items, not serialized EPCs |
| Inventory variance / discrepancy handling | SIMKHAI, gorjana | 🟡 | Adjustments exist; variance approval workflow not explicit |
| Replenishment (drop-off suggestions, SKU-location) | SoC | ❌ | No replenishment engine |
| Availability-to-promise (ATP) / online ATP | SoC | 🟡 | No explicit ATP calc surfaced |
| Consignment / consigned shipment | Chelan, RFID | ❌ | Consignment-shipment API specifically missing |

---

## Fulfillment & Shipping

| Real-market capability | Seen in | Nexus status | Notes |
|------------------------|---------|--------------|-------|
| Shipment tracking | All | ✅ | `/shipments/**` |
| Carrier rate shopping | (Nexus core) | ✅ | Advertised capability |
| Shipping-label generation (carrier integrations) | SoC (Jitsu), SIMKHAI | 🟡 | Label generation is connector work |
| Pick waves & shipping-priority filters | SIMKHAI | ❌ | No wave/pick planning module |
| Pick tickets & customer receipts (mobile/Epson) | Lovers, SIMKHAI | ❌ | No printing/receipt subsystem |
| Fulfillment routing / order brokering | gorjana, Rails, RFID, SoC | ❌ | **Biggest gap** — no routing engine |
| Rejected-order rerouting | gorjana, Rails | ❌ | No automated reroute on rejection |
| Fulfillment status counts (pending/queued/completed) | Shopify/NetSuite | ❌ | No fulfillment sync dashboard |
| Days-in-transit / transit-time filtering | SoC | ❌ | No transit-time model |

---

## Returns & Exchanges

| Real-market capability | Seen in | Nexus status | Notes |
|------------------------|---------|--------------|-------|
| Returns & RMA | (Nexus core) | ✅ | `/returns/**` |
| Exchanges with price/tax differences | gorjana | 🟡 | Return model exists; multi-outcome claims not explicit |
| Store-credit exchanges | gorjana | ❌ | No store-credit ledger |
| Split outcomes within one return claim | gorjana | ❌ | One claim → multiple outcomes not supported |
| Return sync to NetSuite | Shopify/NetSuite | 🟡 | Integration work |

---

## Integrations & Data

| Real-market capability | Seen in | Nexus status | Notes |
|------------------------|---------|--------------|-------|
| Generic file import (CSV/JSON/XML/EDI/XLSX) | (Nexus core) | ✅ | Strong differentiator |
| EDI (850/856/810) | (Nexus core) | ✅ | X12 parsing |
| Shopify connector | gorjana, Rails, Lovers, SIMKHAI | 🟡 | Needs live sync connector |
| NetSuite sync | Rails, SIMKHAI, Shopify/NetSuite | 🟡 | Integration work |
| OFBiz / legacy OMS sync | Chelan, Agape | 🟡 | Bidirectional sync + conflict handling |
| Webhooks / external events | (Nexus core) | ✅ | `/webhooks/**` |
| MCP / GraphQL AI-agent access | MCP/GraphQL | 🟡 | Huge opportunity — see recommendations |
| Carrier postal-route mapping | SoC | ❌ | No transit-time data model |

---

## Analytics & AI

| Real-market capability | Seen in | Nexus status | Notes |
|------------------------|---------|--------------|-------|
| Business analytics | (Nexus core) | ✅ | `/analytics/**` |
| AI predictions | (Nexus core) | ✅ | `/ai/**`, Python ML pipeline |
| Picker-performance reporting | SoC | ❌ | No labor/productivity analytics |
| Fulfillment discrepancy reporting | Shopify/NetSuite | ❌ | No reconciliation reports |
| AI-agent order debugging & reconciliation | MCP/GraphQL | 🟡 | Opportunity via MCP tooling |

---

## Summary

**Nexus strengths:** order lifecycle, inventory, shipments, returns, generic import engine, EDI, webhooks, analytics, AI.

**Biggest real-market gaps (in order of market demand):**
1. **Fulfillment routing / order brokering** — appears in nearly every meeting; it's the heart of an OMS.
2. **Store operations (BOPIS, DoorDash, pick tickets, store sessions)** — Lovers, gorjana.
3. **Bidirectional channel sync (Shopify/NetSuite) with conflict handling** — Rails, Shopify/NetSuite, Chelan.
4. **Transfer orders & cycle counting** — SIMKHAI, RFID.
5. **Replenishment & ATP** — SoC.
