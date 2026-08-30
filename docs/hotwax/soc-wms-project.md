# Spoonful of Comfort (SoC) — The WMS Project Deep-Dive

> SoC is HotWax's **flagship WMS/fulfillment engagement** — a DTC comfort-food brand (soups) with seasonal/holiday peaks. This transcript (`soc-hotwax.json`) reveals the full depth of a production WMS: replenishment, picker analytics, holiday planning, shipping labels, and peak-season readiness. This is the reference for what a **market-ready Nexus WMS** must handle.

---

## 1. The Business

- **Spoonful of Comfort** — DTC comfort-food brand selling soups/gift sets
- **Seasonal/holiday peaks** — the business spikes dramatically around holidays (Thanksgiving, Christmas)
- **Warehouse fulfillment** — single/multi-warehouse, pick-pack-ship
- **Platform:** Shopify front-end, HotWax OMS/WMS, Jitsu shipping labels, Looker analytics

---

## 2. The WMS Workflows in Play

### 2.1 Fulfillment & Packing

- **Packing-label fix** — changing shipment boxes *during packing* caused a failure. Root cause: **parallel transactions and possible deadlocks** (not carrier behavior). This is a **transaction-concurrency** issue — a real WMS must handle box changes mid-pack without deadlock.
- **"Start picking" interaction** — ticket SOC 1039: reducing clicks by removing an extra "start picking" step. **UX efficiency matters** in a WMS.

### 2.2 Picker-Performance Reporting (Labor Analytics)

- **What they want:** picker identity, product category, location/area, picking duration
- **Use case:** "show who picked frozen and other product categories and how long the work took"
- **Tooling:** Looker dashboards, custom SQL queries
- **Nexus relevance:** This is **labor/productivity analytics** — a WMS differentiator. Nexus has `/analytics/**` but no picker-performance module.

### 2.3 Replenishment (System-Directed)

- **Goal:** system-directed replenishment drop-off locations (vs. worker-selected)
- **Flexible SKU-to-location:** initially broad location categories + flexible suggestions; "soup locations may accept any soup flavor when inventory is zero"
- **Locked-source-location handling:** when a source location is locked → notify user, cancel affected tasks, next scheduled job searches unlocked locations; auto-canceled tasks get a system comment identifying the locked source
- **Skip-item reason enforcement:** users appear able to skip items without entering a reason (a **bug** — reason should be required)
- **Nexus relevance:** Nexus has replenishment rules + suggestions, but not the **system-directed drop-off** workflow or **locked-source fallback**.

### 2.4 Shipping-Label Integration (Jitsu)

- **Jitsu** integration: credentials, shipment creation, label generation
- **PDF format** consistent with B-home config
- **ZPL → printable** conversion demonstrated
- **SAPI endpoints** + production requirements outstanding
- **Timeline risk:** Sept 9 delivery vs. Sept 11 operational / Sept 14 test shipments — **integration timeline management** is critical

### 2.5 Holiday Order Planning (Peak Season)

- **Filter by:** ship-by date, ship-after date, **days in transit** (destination-specific)
- **Transit duration** derived from carrier postal-route mapping after routing; varies by destination despite same ground service
- **Process:** manual pick waivers → payment creation (no peak-profile change)
- **UAT:** 50–100 representative orders (import, filtering, pick-waiving) before peak
- **Nexus relevance:** Nexus has no **transit-time model** or **ship-by/ship-after filtering** — this is a peak-season capability gap.

### 2.6 ATP / Availability

- **Zero-online-ATP investigation** — a product showing zero online ATP needs investigation
- **Nexus relevance:** Nexus has ATP engine, but needs the **online-ATP surfacing** and investigation workflow.

---

## 3. The WMS Capability Checklist (from SoC)

This is the **minimum WMS feature set** a market-ready Nexus must match:

| Capability | SoC requirement | Nexus status |
|-----------|-----------------|--------------|
| Pick-pack-ship with box changes mid-pack (no deadlock) | ✅ | 🟡 transaction concurrency |
| Picker-performance / labor analytics | ✅ | ❌ no labor module |
| System-directed replenishment drop-off | ✅ | 🟡 rules exist, no drop-off |
| Flexible SKU-to-location suggestions | ✅ | 🟡 |
| Locked-source-location fallback | ✅ | ❌ |
| Skip-item reason enforcement | ✅ | ❌ |
| Shipping-label integration (Jitsu, PDF/ZPL) | ✅ | 🟡 label gen exists |
| Holiday planning: ship-by/ship-after/days-in-transit | ✅ | ❌ no transit model |
| ATP / online-ATP surfacing | ✅ | 🟡 ATP exists |
| UAT with representative orders before peak | ✅ | ✅ testing exists |
| Inventory configuration screen (product/facility/location) | ✅ | 🟡 |

---

## 4. The Peak-Season Playbook (what Nexus should copy)

SoC's holiday readiness process is a **repeatable playbook**:

1. **Define the release window** — filter orders by ship-by date, ship-after date, days-in-transit
2. **Build a transit-time model** — carrier postal-route mapping, destination-specific
3. **Manual pick waivers** — release selected holiday orders without changing peak profiles
4. **UAT with representative orders** — 50–100 orders covering import, filtering, pick-waiving
5. **Monitor for recurring issues** — real-time system behavior, performance metrics

**Nexus opportunity:** Build this as a **"Peak Season" module** — a packaged, repeatable workflow that any DTC brand can turn on before the holidays. This is a **saleable, differentiated feature**.

---

## 5. Key Lessons for Nexus

1. **Transaction concurrency matters** — a WMS must handle mid-operation changes (box swap during packing) without deadlock. Nexus should stress-test this.
2. **Labor analytics is a differentiator** — picker performance reporting is a real, requested feature. Build it.
3. **System-directed replenishment** — not just suggestions; the system should direct drop-off and handle locked sources gracefully.
4. **Transit-time model** — destination-specific days-in-transit is essential for peak planning. Build a carrier postal-route mapping.
5. **Peak-season readiness is a product** — package the holiday workflow as a repeatable module.
6. **Integration timeline management** — shipping-label integrations (Jitsu) have hard deadlines; Nexus should make connector setup fast and predictable.

---

## 6. SoC as the Nexus WMS Reference

SoC is the clearest example of a **production WMS** in the HotWax corpus. It shows:
- The **depth** a real WMS needs (replenishment, labor, peak planning, labels)
- The **operational edge cases** (box changes, locked locations, skip reasons, zero-ATP)
- The **peak-season pressure** that separates a toy from a production system

**If Nexus can match the SoC WMS feature set and add the predictability HotWax lacks, it has a market-ready WMS.**
