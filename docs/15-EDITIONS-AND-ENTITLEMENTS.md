# 15 — Editions & entitlements (selling OMS and WMS separately)

> **Superseded (2026-10-21).** The OMS and WMS are now separate products in separate modules (see
> [doc 26](26-EXECUTION-PLAN.md), W1). The editions and entitlements switch described here was removed from the OMS;
> it no longer contains WMS code to switch off.

Nexus is sold as three editions plus add-ons. Each tenant's plan is enforced by the server on
every API call. The UI also hides what the plan doesn't include.

| Edition | Includes |
|---|---|
| `OMS` | Platform + Order Management |
| `WMS` | Platform + Warehouse Management |
| `SUITE` | Platform + OMS + WMS |

| Add-on | Includes |
|---|---|
| `THREE_PL` | 3PL billing statements, rate cards, client portal |
| `EDI` | EDI 850/856/810/997 automation |
| `AI` | AI control centre, forecasting, assistant, MCP server |

**Platform (every edition):** sites and warehouses (master data), inventory, products,
customers, transfers, replenishment, procurement, invoices and payments, integrations and
marketplaces, analytics and reports, users and roles, settings, audit, workflows.

| Module | What it covers |
|---|---|
| **OMS** | Orders, order flow twin, routing and brokering, approvals, rejections, returns, BOPIS and store fulfilment, ATP, pre-orders, endless aisle, promotions, fulfilment limits, reconciliation, B2B portal, e-mail order parser. |
| **WMS** | Fulfilment execution (picking, packing, shipping, waves, pickers, packer/loader views, labels, manifests), receiving, cycle counts, RFID, warehouse twin, yard and dock, slotting, labour, automation, task queues, carriers, rate shopping, freight audit, RF handheld. |

## Where it lives

- **API path to module:** `ProductModule.API_PREFIXES` (backend).
  - The longest prefix wins.
  - Unlisted paths are Platform.
  - `EntitlementTest.everyControllerIsClassified` fails CI if a new controller is neither assigned to a module nor listed as Platform.
- **Enforcement:** `ModuleEntitlementFilter` returns `403` with this body:
  ```json
  {"error":"MODULE_NOT_LICENSED","module":"WMS"}
  ```
  It applies to every role, administrators included.
- **Plan lookup:** `EntitlementService`.
  - It caches each tenant's plan for 60 s, so a plan change applies within a minute.
- **UI:**
  - **Route map:** `features/entitlements/modules.ts` maps UI routes to modules. Its test fails if a new route is unclassified.
  - **Plan hook:** `useEntitlements` loads `GET /entitlements`.
  - **What the UI hides:** the sidebar sections, Launch Pad tiles, *Find Order* and *AI Assistant* the plan doesn't include. The logo subtitle names the edition.
  - **Direct links** to a page outside the plan show *"… isn't in your plan"*.

## Assigning a plan

A tenant **without** a subscription row has `SUITE` with every add-on. Tenants that existed
before licensing therefore lose nothing.

```sql
-- WMS edition with EDI, valid for a year
INSERT INTO nx_tenant_subscriptions (tenant_id, edition, add_ons, valid_until)
VALUES ('<tenant uuid>', 'WMS', 'EDI', now() + interval '1 year')
ON CONFLICT (tenant_id) DO UPDATE
   SET edition = EXCLUDED.edition, add_ons = EXCLUDED.add_ons, valid_until = EXCLUDED.valid_until;

-- Upgrade to the suite with every add-on
UPDATE nx_tenant_subscriptions SET edition = 'SUITE', add_ons = 'THREE_PL,EDI,AI' WHERE tenant_id = '<tenant uuid>';
```

**When `valid_until` passes,** only Platform remains. Customers keep access to their own master
data and can export it.

**Not built yet:**
- A self-service billing page, and an operator console for plans; today plans are changed with SQL as above.
- Sign-up always creates a `SUITE` trial.
