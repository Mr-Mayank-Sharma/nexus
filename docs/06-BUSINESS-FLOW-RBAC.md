# Nexus OMS — Business Flow & Access Control (RBAC)

> **Who can touch what, at every stage of the business flow.** This document is the authoritative answer to "who has access to what" — derived from the 14-role model, the `PermissionService.PATH_TO_RESOURCE` map (39 mappings), and the seeded `nx_role_permissions` rows (`V24`/`V25`).

---

## 1. Roles (14)

Defined in `AuthService.ALLOWED_ROLES`:

| # | Role | Profile |
|---|---|---|
| 1 | `ADMIN` | Platform super-user; wildcard `*:*` |
| 2 | `CEO` | Strategic visibility; view-only on key modules |
| 3 | `OPS_MANAGER` | Full operations access (orders, inventory, fulfillment, AI, import) |
| 4 | `WAREHOUSE_MANAGER` | Full warehouse-facing access |
| 5 | `PICKER` | Operate picking; view inventory |
| 6 | `PACKER` | Operate packing; view inventory |
| 7 | `LOADER` | Operate shipping; view shipments |
| 8 | `STORE_MANAGER` | Full store-facing access (orders, inventory, BOPIS) |
| 9 | `BOPIS_OWNER` | Full BOPIS-facing access |
| 10 | `CUSTOMER_SUPPORT` | View/edit customers, orders, returns, products |
| 11 | `PROCUREMENT_MANAGER` | Full procurement access |
| 12 | `FINANCE` | View/edit invoices, payments, returns; analytics |
| 13 | `LOGISTICS_MANAGER` | Full logistics access (shipping, carriers, routing, yard) |
| 14 | `VIEWER` | Read-only dashboards |

---

## 2. How Authorization Works (two layers)

### 2.1 Server-side — path → resource → permission
`PermissionAuthorizationFilter` → `PermissionService.PATH_TO_RESOURCE` (39 mappings, first-prefix-match wins):

| URL prefix | Resource | URL prefix | Resource |
|---|---|---|---|
| `/orders/` | orders | `/routing-rules/` | routing-rules |
| `/inventory/` | inventory | `/order-routing/` | routing |
| `/inventory-receipts/` | inventory-receipts | `/procurement/` | procurement |
| `/products/` | products | `/invoices/`, `/invoicing/` | invoices |
| `/customers/` | customers | `/payments/` | payments |
| `/returns/` | returns | `/carriers/`, `/carrier/` | carriers |
| `/shipments/` | shipments | `/rbac/` | rbac |
| `/picking/` | picking | `/settings/` | settings |
| `/packing/` | packing | `/workflows/` | workflows |
| `/shipping/` | shipping | `/ai/`, `/api/ai/` | ai |
| `/warehouse/`, `/warehouses/` | warehouse | `/analytics/` | analytics |
| `/receiving/` | warehouse | `/documents/` | documents |
| `/routing/` | routing | `/notifications/` | notifications |
| `/webhooks/` | webhooks | `/edi/` | edi |
| `/cycle-counts/` | cycle-counts | `/rate-shopping/` | rate-shopping |
| `/import/` | import | `/audit/` | audit |
| `/integration/`, `/integrations/`, `/integration-platform/`, `/integration-stores/`, `/integration-hub/`, `/shopify/`, `/email-parser/` | integration | `/fulfillment/` | fulfillment |
| `/bopis/` | inventory | `/api/sample-data/` | settings |

**Semantics:** a role is authorized for a request only if it holds `can_<method>` on the matched resource (`view`/`create`/`edit`/`delete`). `ADMIN` matches the wildcard `*:*`. **Allow-by-default caveat:** requests whose path matches no prefix are permitted — see §6.

### 2.2 Frontend — UI gates
- `ProtectedRoute` (authenticated) + `AppLayout` shell.
- `PermissionGate` (`resource`+`action`) hides/disables UI that a role may not use.
- ⚠️ **Gap G2:** 6 pages pass a `permission` prop that `PermissionGate` ignores (only `resource`/`action` are honored). Server remains the enforcement point; UI must be corrected. Legacy `RoleProtectedRoute` is unused.

---

## 3. The Business Flow — Who Does What (end to end)

> **Legend:** 👁 view · ✍ create · ✎ edit · ✖ delete. Roles not listed have no access to that stage. `ADMIN` (wildcard) is implicit everywhere.

### 3.1 Commerce & order intake
| Stage | Role → access |
|---|---|
| Channel order sync (Shopify/BC/Amazon…) | System (connector). Human review: `OPS_MANAGER` ✍✎, `CUSTOMER_SUPPORT` ✍✎, `STORE_MANAGER` ✍✎, `CEO` 👁, `VIEWER` 👁 |
| Manual order entry | `CUSTOMER_SUPPORT` ✍, `OPS_MANAGER` ✍ |
| Bulk import orders | `OPS_MANAGER` ✍ (full), `WAREHOUSE_MANAGER`/`PROCUREMENT_MANAGER`/`FINANCE`/`LOGISTICS_MANAGER` ✍ (scoped), `STORE_MANAGER`/`CEO`/`VIEWER` 👁 |
| Email order parsing | Configure: `OPS_MANAGER`; confirm parsed orders: `CUSTOMER_SUPPORT` ✎ |
| Parked / brokering queue | `OPS_MANAGER` ✎, `LOGISTICS_MANAGER` ✎, `CUSTOMER_SUPPORT` 👁 |
| Routing rules & config | `OPS_MANAGER` ✎, `LOGISTICS_MANAGER` ✎, `CEO` 👁 |
| BOPIS / pickup orders | `BOPIS_OWNER` ✍✎, `STORE_MANAGER` ✍✎, `CUSTOMER_SUPPORT` ✎, `OPS_MANAGER` ✎ |
| Endless aisle / store inventory | `STORE_MANAGER` ✍✎, `BOPIS_OWNER` ✍✎ |

### 3.2 Inventory & network
| Stage | Role → access |
|---|---|
| Inventory view/ATP | `OPS_MANAGER` 👁, `WAREHOUSE_MANAGER` 👁, `STORE_MANAGER` 👁, `BOPIS_OWNER` 👁, `VIEWER` 👁 |
| Receive inventory (PO/transfer) | `WAREHOUSE_MANAGER` ✍✎, `LOADER` (ship/load side), `STORE_MANAGER` ✎ |
| Cycle counts | `WAREHOUSE_MANAGER` ✍✎ |
| Transfer orders (node↔store) | `WAREHOUSE_MANAGER` ✍✎, `STORE_MANAGER` ✍✎ |
| Replenishment suggestions | Approve: `PROCUREMENT_MANAGER` ✎, `WAREHOUSE_MANAGER` ✎; view: `CEO` 👁 |
| Inventory import | `WAREHOUSE_MANAGER` ✍, `OPS_MANAGER` ✍ |
| Warehouse structure (zones/bins/nodes) | `WAREHOUSE_MANAGER` ✍✎✖, `OPS_MANAGER` ✍✎ |

### 3.3 Fulfillment (wave → pick → pack → load)
| Stage | Role → access |
|---|---|
| Wave planning | `OPS_MANAGER` ✍✎, `WAREHOUSE_MANAGER` ✍✎, `LOGISTICS_MANAGER` ✎ |
| Picklists | `OPS_MANAGER`/`WAREHOUSE_MANAGER` ✍✎; execute: `PICKER` ✍✎; view: `LOGISTICS_MANAGER` 👁 |
| Packing | `OPS_MANAGER`/`WAREHOUSE_MANAGER` ✍✎; execute: `PACKER` ✍✎ |
| Loading & dispatch | `OPS_MANAGER`/`WAREHOUSE_MANAGER`/`LOGISTICS_MANAGER` ✍✎; execute: `LOADER` ✍✎ |
| Shipments / tracking | `OPS_MANAGER` 👁✎, `WAREHOUSE_MANAGER` 👁✎, `LOGISTICS_MANAGER` 👁✎, `LOADER` 👁, `CUSTOMER_SUPPORT` 👁, `VIEWER` 👁 |
| Fulfillment exceptions | `WAREHOUSE_MANAGER` ✎, `OPS_MANAGER` ✎ |
| Fulfillment limits/capacity | `OPS_MANAGER` ✍✎ |

### 3.4 Shipping, carriers & yard
| Stage | Role → access |
|---|---|
| Carrier accounts/rates/zones | `LOGISTICS_MANAGER` ✍✎✖, `OPS_MANAGER` ✎ |
| Rate shopping | `LOGISTICS_MANAGER` ✎, `OPS_MANAGER` 👁, `FINANCE` 👁 |
| Trailers & yard locations | `LOGISTICS_MANAGER` ✍✎, `LOADER` ✎, `WAREHOUSE_MANAGER` 👁 |
| Dock doors & appointments | `LOGISTICS_MANAGER` ✍✎ |
| Freight invoices & audit | `FINANCE` ✍✎👁, `LOGISTICS_MANAGER` 👁 |
| Shipment/carrier import | `LOGISTICS_MANAGER` ✍ |

### 3.5 Returns (RMA)
| Stage | Role → access |
|---|---|
| Return requests | `CUSTOMER_SUPPORT` ✍✎, `OPS_MANAGER` ✎, `FINANCE` ✎ |
| Inspection & disposition | `WAREHOUSE_MANAGER` ✎, `OPS_MANAGER` ✎ |
| Refund / credit memo | `FINANCE` ✍✎ |
| Return analytics | `CEO` 👁, `OPS_MANAGER` 👁 |

### 3.6 Procurement & suppliers
| Stage | Role → access |
|---|---|
| Purchase requests / approval rules | `PROCUREMENT_MANAGER` ✍✎✖, `OPS_MANAGER` ✎ |
| Purchase orders | `PROCUREMENT_MANAGER` ✍✎✖, `WAREHOUSE_MANAGER` ✎ (receiving) |
| RFQ & responses | `PROCUREMENT_MANAGER` ✍✎ |
| Suppliers / contacts / contracts | `PROCUREMENT_MANAGER` ✍✎✖ |
| Procurement analytics | `CEO` 👁, `OPS_MANAGER` 👁, `VIEWER` 👁 |

### 3.7 Finance
| Stage | Role → access |
|---|---|
| Invoices & invoicing | `FINANCE` ✍✎, `PROCUREMENT_MANAGER` ✎ (PO-linked), `OPS_MANAGER` 👁, `CEO` 👁 |
| Payments (Stripe) | `FINANCE` ✍✎ |
| Credit memos | `FINANCE` ✍✎ |
| Freight audit | `FINANCE` ✍✎ |
| Financial analytics | `FINANCE` 👁, `CEO` 👁, `VIEWER` 👁 |

### 3.8 Automation & alerting
| Stage | Role → access |
|---|---|
| Automation systems & commands | `WAREHOUSE_MANAGER` ✍✎, `OPS_MANAGER` ✎ |
| Alert rules | `OPS_MANAGER` ✍✎, `WAREHOUSE_MANAGER` ✍✎ |
| Notifications (templates/logs) | `OPS_MANAGER` ✍✎, `WAREHOUSE_MANAGER` ✎, `STORE_MANAGER` ✎, `BOPIS_OWNER` ✎, `CUSTOMER_SUPPORT` 👁, `FINANCE` 👁, `LOGISTICS_MANAGER` ✎, `VIEWER` 👁 |

### 3.9 AI platform
| Stage | Role → access |
|---|---|
| AI training / datasets / experiments | `OPS_MANAGER` ✍✎, `ADMIN` ✍✎ |
| Model registry & deployments | `ADMIN` ✍✎, `OPS_MANAGER` ✎ |
| Rules & fallbacks | `OPS_MANAGER` ✍✎ |
| AI briefings / forecasts | `CEO` 👁, `OPS_MANAGER` 👁, `LOGISTICS_MANAGER` 👁, `FINANCE` 👁 |
| AI health & cost logs | `ADMIN` 👁✎ |

### 3.10 Integrations, EDI & iPaaS
| Stage | Role → access |
|---|---|
| Integration Hub / stores / connectors | `OPS_MANAGER` ✍✎, `ADMIN` ✍✎ |
| Webhooks & sync configs | `OPS_MANAGER` ✍✎, `ADMIN` ✎ |
| EDI partners & documents | `LOGISTICS_MANAGER` ✍✎, `ADMIN` ✎ |
| Import/export jobs | `OPS_MANAGER` ✍, scoped ✍ per role (see §3.1), `VIEWER` 👁 |

### 3.11 Administration
| Stage | Role → access |
|---|---|
| RBAC (roles/permissions) | `ADMIN` only |
| Settings / company config | `ADMIN` ✍✎, `CEO` 👁 |
| Users & roles | `ADMIN` |
| Audit log | `ADMIN` 👁, `CEO` 👁, `OPS_MANAGER` 👁 |
| Workflows | `OPS_MANAGER` ✍✎, `ADMIN` ✎ |
| Documents | `OPS_MANAGER` ✍✎, `CEO` 👁 |

---

## 4. Permission Matrix (seeded `nx_role_permissions`)

`can_*` flags per role per permission_group (source: `V24__seed_default_permissions.sql` + `V25__seed_import_permissions.sql`; `tenant_id = NULL` = global defaults, tenants can override):

| Permission group | ADMIN | CEO | OPS | WH MGMT | PICKER | PACKER | LOADER | STORE | BOPIS | SUPPORT | PROCURE | FINANCE | LOGISTICS | VIEWER |
|---|---|---|---|---|---|---|---|---|---|---|---|---|---|---|
| **orders** | * | V | V/C/E/D | – | – | – | – | V/C/E/D | V/C/E/D | V/C/E | – | – | V/C/E/D | V |
| **inventory** | * | V | V/C/E/D | V/C/E/D | V | V | – | V/C/E/D | V/C/E/D | – | – | – | – | V |
| **inventory-receipts** | * | – | V/C/E/D | – | – | – | – | – | – | – | – | – | – | – |
| **products** | * | V | V/C/E/D | – | – | – | – | V/C/E/D | – | V | V/C/E/D | – | – | V |
| **customers** | * | V | V/C/E/D | – | – | – | – | V/C/E/D | V/C/E/D | V/C/E | – | V | – | V |
| **returns** | * | V | V/C/E/D | – | – | – | – | V/C/E/D | – | V/C/E | – | V/E | – | – |
| **shipments** | * | V | V/C/E/D | V/C/E/D | – | – | V | – | – | – | – | – | V/C/E/D | V |
| **picking** | * | – | V/C/E/D | V/C/E/D | V/C/E | – | – | – | – | – | – | – | – | – |
| **packing** | * | – | V/C/E/D | V/C/E/D | – | V/C/E | – | – | – | – | – | – | – | – |
| **shipping** | * | – | V/C/E/D | V/C/E/D | – | – | V/C/E | – | – | – | – | – | V/C/E/D | – |
| **warehouse** | * | – | V/C/E/D | V/C/E/D | – | – | – | – | – | – | – | – | V/C/E/D | – |
| **receiving** | * | – | – | V/C/E/D | – | – | – | – | – | – | – | – | – | – |
| **cycle-counts** | * | – | – | V/C/E/D | – | – | – | – | – | – | – | – | – | – |
| **fulfillment** | * | – | V/C/E/D | V/C/E/D | – | – | – | – | – | – | – | – | V/C/E/D | – |
| **routing** | * | – | V/C/E/D | – | – | – | – | – | – | – | – | – | V/C/E/D | – |
| **routing-rules** | * | – | V/C/E/D | – | – | – | – | – | – | – | – | – | – | – |
| **carriers** | * | – | V/C/E/D | – | – | – | – | – | – | – | – | – | V/C/E/D | – |
| **rate-shopping** | * | – | – | – | – | – | – | – | – | – | – | – | V/C/E/D | – |
| **procurement** | * | – | – | – | – | – | – | – | – | – | V/C/E/D | – | – | – |
| **invoices** | * | – | – | – | – | – | – | – | – | – | V/C/E/D | V/C/E | – | – |
| **payments** | * | – | – | – | – | – | – | – | – | – | – | V/C/E | – | – |
| **bopis** | * | – | – | – | – | – | – | V/C/E/D | V/C/E/D | – | – | – | – | – |
| **analytics** | * | V | V/C/E/D | V/C/E/D | – | – | – | – | – | – | V/C/E/D | V | V/C/E/D | V |
| **notifications** | * | – | V/C/E/D | V/C/E/D | – | – | – | V/C/E/D | V/C/E/D | V | V/C/E/D | V | V/C/E/D | V |
| **ai** | * | V | V/C/E/D | – | – | – | – | – | – | – | – | – | – | – |
| **import** | * | V | V/C/E/D | V/C | – | – | – | V | – | – | V/C | V/C | V/C | V |
| **settings** | * | V | – | – | – | – | – | – | – | – | – | – | – | – |
| **workflows** | * | – | V/C/E/D | – | – | – | – | – | – | – | – | – | – | – |
| **documents** | * | V | V/C/E/D | – | – | – | – | – | – | – | – | – | – | – |
| **audit** | * | V | V/C/E/D | – | – | – | – | – | – | – | – | – | – | – |
| **rbac** | * | – | – | – | – | – | – | – | – | – | – | – | – | – |
| **edi** | * | – | – | – | – | – | – | – | – | – | – | – | V/C/E/D | – |
| **webhooks** | * | – | – | – | – | – | – | – | – | – | – | – | – | – |
| **integration** | * | – | V/C/E/D | – | – | – | – | – | – | – | – | – | – | – |

*Legend: V=can_view, C=can_create, E=can_edit, D=can_delete, * = ADMIN wildcard, – = no permission row.*

**Notes & observed gaps (flagged for review):**
1. **PICKER/PACKER/LOADER** have `can_view` on their module but `can_delete=false`; `create/edit` restricted to their own module — sound least-privilege.
2. **`edi`, `rate-shopping`, `webhooks`, `integration`, `receiving`, `cycle-counts`, `routing-rules`, `rbac`** have no seed rows beyond the roles shown — verify intent (a missing row means **denied** by the resolver, *unless* path matches no mapping — see §6).
3. **OPS_MANAGER** is extremely broad (18+ groups full CRUD) — effectively a non-admin super-user; consider splitting.
4. **CEO** has no `edit` on `orders`/`inventory` — visibility only, by design.
5. **FINANCE** can `edit` but not `delete` invoices/payments — good control lineage.

---

## 5. Verification path (`/rbac`)

- Endpoints under `/rbac/` map to resource `rbac`; **only `ADMIN`** has permission rows → only ADMIN can manage roles/permissions/teams.
- Lookup path: `PermissionAuthorizationFilter` → `PermissionService.resolve` → tenant-specific override first → global default → allow/deny.

---

## 6. Allow-by-default trade-off (must-read)

`PermissionService` returns **allow** when a URL path matches **no** prefix in `PATH_TO_RESOURCE`. Consequences:
- New endpoints are reachable until a mapping is added → **security review gate needed on every new controller**.
- This is by design for velocity (Phase 1), but for GA the team should invert to **deny-by-default** with an explicit allowlist.

---

*Per-module access tables also appear in each [`features/`](./features/) document. Next: [`README.md`](./README.md) index.*
