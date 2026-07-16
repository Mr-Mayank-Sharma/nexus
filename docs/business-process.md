# Nexus OMS — Business Process Document

## 1. Executive Summary

Nexus OMS is a multi-tenant Order Management System serving warehouse, retail, logistics, finance, and executive teams within a single organization. This document describes the business processes surrounding **Role-Based Access Control (RBAC)** and **Secure Data Import**, two critical capabilities that govern who can see what, who can do what, and how external data enters the system.

---

## 2. Business Roles & Their Responsibilities

The system defines **14 distinct business roles**, each mapping to a real-world job function:

| Role | Department | Typical Responsibilities |
|------|-----------|------------------------|
| **ADMIN** | IT / System Administration | System configuration, user management, tenant setup |
| **CEO** | Executive | High-level analytics, audit logs, strategic oversight |
| **OPS_MANAGER** | Operations | Day-to-day order, inventory, and fulfillment management |
| **WAREHOUSE_MANAGER** | Warehouse | Picking, packing, shipping, cycle counts, receiving |
| **PICKER** | Warehouse Floor | Pick items from shelves for outbound orders |
| **PACKER** | Warehouse Floor | Pack items into boxes for shipment |
| **LOADER** | Shipping Dock | Load packed shipments onto carrier vehicles |
| **STORE_MANAGER** | Retail Store | Manage BOPIS orders, store inventory, customer pickups |
| **BOPIS_OWNER** | Retail / Ecommerce | Buy Online Pick Up In Store — dedicated console |
| **CUSTOMER_SUPPORT** | Support Desk | Order lookups, returns processing, customer inquiries |
| **PROCUREMENT_MANAGER** | Procurement | Supplier management, purchase orders, RFQs |
| **FINANCE** | Accounting | Invoicing, payment reconciliation, refunds |
| **LOGISTICS_MANAGER** | Logistics | Carrier management, rate shopping, manifesting |
| **VIEWER** | Cross-Department | Read-only dashboards and reports |

### 2.1 Permission Inheritance & Scope

- All permissions are **tenant-scoped** — each customer organization operates in isolation.
- Permissions are stored per `(tenant, role, resource, action)` tuple.
- A **global default** (`tenant_id = NULL`) applies unless a tenant-specific override exists.
- The **ADMIN** role has a wildcard (`*`) granting access to every resource and action.
- All other roles receive granular, resource-level permissions tailored to their job function.

---

## 3. Business Process: Access Control

### 3.1 User Authentication Flow

```
User Login → JWT Issued → Token Stored Locally → Each API Call Includes JWT
```

1. User authenticates with email/password (or SSO/MFA).
2. Backend validates credentials and issues a signed JWT containing:
   - Username
   - Role (e.g., `OPS_MANAGER`)
   - Tenant ID
3. Frontend stores the JWT in localStorage and attaches it as a `Bearer` token to every API request.
4. Each request is validated for authenticity, role, and specific resource-level permission.

### 3.2 Frontend Permission Enforcement

The frontend enforces permissions at **four levels**:

| Level | Mechanism | Example |
|-------|-----------|---------|
| **Route Protection** | `RoleProtectedRoute` component | A PICKER cannot navigate to `/finance` |
| **Component Gating** | `PermissionGate` component | "Create Order" button hidden from VIEWER |
| **Action Gating** | `EnterpriseToolbar` `permission` prop | "Import File" toolbar action hidden from PICKER |
| **Role Switching** | `switchRole` in AuthContext | ADMIN can preview any role (client-side only) |

### 3.3 UI Visibility Rules

Each page and action follows a consistent pattern:

```
Does user have permission for this resource + action?
  ├── YES → Render the button / link / action
  └── NO  → Hide it entirely (no 403 shown)
```

This creates a **progressive disclosure** experience — users only see what they are allowed to do.

### 3.4 Why Gate the Frontend?

The backend already enforces permissions at the API layer. Frontend gating serves three business purposes:

1. **User Experience** — users aren't shown actions they'll fail to execute.
2. **Reduced Errors** — fewer 403 errors means less confusion and support tickets.
3. **Security Theater Prevention** — hiding controls prevents social engineering attempts.

---

## 4. Business Process: Data Import

### 4.1 The Import Use Case

Organizations frequently need to **bulk-load data** into Nexus OMS:

| Entity Type | Example Use Case |
|------------|------------------|
| Products | New catalog upload from ERP |
| Orders | Migration from legacy system |
| Inventory | Periodic stock reconciliation |
| Customers | CRM export import |
| Shipments | Carrier manifest upload |
| Returns | RMA batch processing |
| Suppliers | Vendor list onboarding |
| Purchase Orders | Procurement system integration |
| Invoices | Finance batch upload |
| Warehouses | Multi-site setup |

### 4.2 Import Flow (Business View)

```
Prepare File → Authenticate → Get Token → Upload → Validate → Import → Review Results
```

| Step | Business Owner | Description |
|------|---------------|-------------|
| 1. Prepare File | Data Steward | Format data as CSV, JSON, XML, EDI, or Excel |
| 2. Authenticate | System | User logs in with their OMS credentials |
| 3. Get Token | System | A short-lived signed upload token is generated |
| 4. Upload | Data Steward | File is uploaded via the Import/Export Center UI |
| 5. Validate | System | Rows are validated against business rules |
| 6. Import | System | Valid rows are written to the database |
| 7. Review | Data Steward | Success/error counts and detailed logs displayed |

### 4.3 Import Modes

| Mode | Behavior | When to Use |
|------|----------|-------------|
| `VALIDATE_ONLY` | No data written, only validation | Testing import file quality |
| `CONTINUE_ON_ERROR` | Skip bad rows, import the rest | Large files with minor issues |
| `STOP_ON_FIRST_ERROR` | Halt on first failure | When data integrity is critical |
| `INSERT_ONLY` | New records only | Fresh data loads |
| `UPDATE_ONLY` | Existing records only | Corrections and updates |
| `UPSERT` | Insert or update | Ongoing synchronization |

### 4.4 Security Evolution

The import endpoint was originally **fully public** (no authentication required). This was a business risk — anyone with the URL could upload data.

The business decision was to replace public access with **signed upload tokens**:

| Before | After |
|--------|-------|
| No auth required | JWT or signed token required |
| Any caller could upload | Only authorized roles can request tokens |
| No audit trail for uploader | Token generation is logged |
| No time limit | Tokens expire after 30 minutes |
| No scope restriction | Tokens are scoped to one entity type |

---

## 5. Business Benefits

### 5.1 Security

- **Principle of Least Privilege** — every role gets only the permissions it needs.
- **Defense in Depth** — both frontend and backend enforce permissions independently.
- **No Public Endpoints** — sensitive operations (import, export) require authentication.

### 5.2 Operational Efficiency

- **Self-Service Imports** — authorized users can bulk-load data without IT intervention.
- **Progressive Disclosure** — cleaner UI, fewer distractions, faster task completion.
- **Role Switching** — ADMIN can troubleshoot by previewing exactly what each role sees.

### 5.3 Compliance

- **Audit Trail** — every permission check and import operation is logged.
- **Tenant Isolation** — data is strictly isolated between customer organizations.
- **Role Standardization** — 14 well-defined roles map to real organizational structures.

---

## 6. Process Flow Diagrams (Textual)

### 6.1 Daily Warehouse Operation (PICKER)

```
Login → Dashboard (read-only metrics)
     → Picking Page (view assigned picks)
         → Pick items (scan, confirm)
         → View inventory (read-only lookup)
     → Logout
```

PICKER cannot:
- Create orders
- Modify inventory
- View financial data
- Access settings

### 6.2 Operations Manager Bulk Import

```
Login → Navigate to Import/Export Center
     → Click "Import File"
     → Select entity type (e.g., "Products")
     → Upload CSV file
     → System validates + imports
     → Review error report
     → Download error file if needed
     → Return to Products page to verify
```

### 6.3 External System Integration

```
External System → POST /import/token?entityType=orders (with API key)
                → Receives signed token (30min TTL)
                → POST /import/orders (with X-Import-Token header)
                → File is validated and imported
                → Response includes success/error counts
```

---

## 7. Key Business Rules

1. **ADMIN bypasses all permission checks** — wildcard access to everything.
2. **VIEWER can see but never touch** — read-only on dashboards and reports.
3. **Import tokens expire in 30 minutes** — limits exposure if leaked.
4. **Import tokens are entity-type scoped** — an orders token cannot import invoices.
5. **Frontend gating is UX, not security** — backend enforcement is the real control.
6. **Permission cache expires after 60 seconds** — changes take effect within 1 minute.
