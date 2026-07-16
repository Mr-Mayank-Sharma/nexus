# Nexus OMS — Implementation Document

## 1. System Architecture

```
┌─────────────────────────────────────────────────────────────┐
│                     Frontend (React + TypeScript)            │
│  ┌──────────┐  ┌──────────────┐  ┌──────────────────────┐   │
│  │ Auth UI  │  │  Pages (65+) │  │  RBAC Components     │   │
│  │ Login /   │  │  Orders,     │  │  PermissionGate      │   │
│  │ Logout   │  │  Inventory,  │  │  RequireRole         │   │
│  │          │  │  Import, etc │  │  RoleProtectedRoute  │   │
│  └──────────┘  └──────────────┘  │  useAccess hook      │   │
│                                  └──────────────────────┘   │
│  ┌──────────────────────────────────────────────────────┐    │
│  │  API Client (Axios) — auto-attaches JWT Bearer token │    │
│  └──────────────────────────────────────────────────────┘    │
└──────────────────────────┬──────────────────────────────────┘
                           │ HTTPS /api/v1/*
                           │
┌──────────────────────────▼──────────────────────────────────┐
│                  Backend (Spring Boot 3 + Java 17)           │
│                                                              │
│  ┌─────────────────┐    ┌─────────────────────────────┐     │
│  │ Filter Chain    │    │ Service Layer               │     │
│  │                 │    │                             │     │
│  │ 1. RateLimiting │    │ PermissionService (cache)   │     │
│  │ 2. ImportToken  │    │ ImportTokenService (HMAC)   │     │
│  │ 3. JWT Auth     │    │ GenericImportService        │     │
│  │ 4. Permission   │    │ AuthService                 │     │
│  │    Authorization│    │ RbacService                 │     │
│  └─────────────────┘    └─────────────────────────────┘     │
│                                                              │
│  ┌──────────────────────────────────────────────────────┐    │
│  │  Database (PostgreSQL + Row-Level Security)          │    │
│  │  nx_users | nx_role_permissions | nx_import_history  │    │
│  └──────────────────────────────────────────────────────┘    │
└──────────────────────────────────────────────────────────────┘
```

---

## 2. Backend Implementation

### 2.1 Security Filter Chain

The filter chain processes every HTTP request in this order:

| Order | Filter | Purpose |
|-------|--------|---------|
| 1 | `RateLimitingFilter` | Throttle requests per IP/path tier |
| 2 | `ImportTokenAuthenticationFilter` | Validate signed import tokens (`X-Import-Token` header) |
| 3 | `JwtAuthenticationFilter` | Validate standard JWT (`Authorization: Bearer` header) |
| 4 | `PermissionAuthorizationFilter` | Check resource-level permissions |

#### 2.1.1 ImportTokenAuthenticationFilter

**File:** `src/main/java/com/nexus/oms/security/ImportTokenAuthenticationFilter.java`

```java
@Component
public class ImportTokenAuthenticationFilter extends OncePerRequestFilter {
    // Checks for X-Import-Token header
    // On valid token → sets SecurityContext with ROLE_IMPORT_TOKEN
    // This role bypasses further permission checks (like ADMIN)
}
```

- Runs before JWT filter so it can handle token-only requests.
- If both `X-Import-Token` and `Authorization: Bearer` are present, JWT takes precedence.
- External integrations send only `X-Import-Token` (no Bearer) — the token auth is sufficient.

#### 2.1.2 PermissionAuthorizationFilter

**File:** `src/main/java/com/nexus/oms/security/PermissionAuthorizationFilter.java`

```java
@Component
public class PermissionAuthorizationFilter extends OncePerRequestFilter {
    // For each authenticated request:
    //   1. Extract role from SecurityContext
    //   2. Resolve resource from URL path (via PermissionService.resolveResource)
    //   3. Resolve action from HTTP method (POST→create, GET→view, PUT→edit, DELETE→delete)
    //   4. Check if role has permission for (resource, action)
    //   5. If not → 403 Forbidden
    //   6. ADMIN and IMPORT_TOKEN roles bypass all checks
}
```

**Key behavioral change:** The `/import/` path is no longer bypassed. Import endpoints now require proper `import:create` or `import:view` permissions via the role-permission matrix.

#### 2.1.3 SecurityConfig

**File:** `src/main/java/com/nexus/oms/security/SecurityConfig.java`

```java
.requestMatchers("/auth/**").permitAll()          // Login, register
.requestMatchers("/swagger-ui/**").permitAll()    // API docs
.requestMatchers("/actuator/health").permitAll()  // Health check
.requestMatchers("/webhooks/**").permitAll()      // External webhooks
.requestMatchers(HttpMethod.POST, "/import/**").authenticated()  // Import requires auth
.anyRequest().authenticated()                     // Everything else requires auth
```

### 2.2 Permission Service

**File:** `src/main/java/com/nexus/oms/service/PermissionService.java`

#### 2.2.1 Path-to-Resource Mapping

```java
PATH_TO_RESOURCE.put("/api/users/", "users");
PATH_TO_RESOURCE.put("/orders/", "orders");
PATH_TO_RESOURCE.put("/inventory/", "inventory");
// ... 30+ mappings covering all API endpoints
PATH_TO_RESOURCE.put("/import/", "import");
```

- URL paths are normalized (trailing slashes stripped, `/api/v1/` prefix accounted for).
- Unmapped paths return `null` → filter allows the request through (no specific permission needed).

#### 2.2.2 Method-to-Action Mapping

| HTTP Method | Action |
|-------------|--------|
| GET | `view` |
| POST | `create` |
| PUT | `edit` |
| PATCH | `edit` |
| DELETE | `delete` |

#### 2.2.3 Caching

```java
private final Cache<String, Boolean> permissionCache;
// TTL: 60 seconds (configurable)
// Key format: "role:resource:action"
// Invalidated by RbacService.setPermission()
```

- Cache avoids DB lookups on every request.
- Changes propagate within 60 seconds max (or instantly when `setPermission()` is called).
- Cache is per-instance (not distributed) — acceptable for single-instance deployments.

### 2.3 Import Token Service

**File:** `src/main/java/com/nexus/oms/service/ImportTokenService.java`

#### 2.3.1 Token Format

```
base64url(payload) "." base64url(signature)

Payload: {"entityType":"products","iat":1700000000000,"exp":1700001800000}
Signature: HMAC-SHA256(secret, base64url(payload))
```

#### 2.3.2 Key Properties

| Property | Value | Configurable |
|----------|-------|-------------|
| Signing Algorithm | HMAC-SHA256 | No |
| Signing Key | Derived from `app.jwt.secret` | Via `application.properties` |
| Default TTL | 30 minutes | Via `app.import-token.ttl-ms` |
| Payload Fields | `entityType`, `iat`, `exp` | No |

#### 2.3.3 Validation Rules

1. Token must contain exactly two parts separated by `.`
2. Signature must match HMAC-SHA256 of the payload
3. Comparison uses **constant-time** algorithm to prevent timing attacks
4. `exp` timestamp must be in the future
5. Base64url decoding must succeed and produce valid JSON

### 2.4 Database Schema

#### 2.4.1 Role Permissions Table

```sql
CREATE TABLE nx_role_permissions (
    id              UUID PRIMARY KEY,
    tenant_id       UUID REFERENCES nx_tenants(id),  -- NULL = global default
    role            VARCHAR(50) NOT NULL,             -- e.g. 'OPS_MANAGER'
    permission_group VARCHAR(100) NOT NULL,           -- e.g. 'orders', 'import'
    permission_name  VARCHAR(50) NOT NULL,            -- 'view', 'create', 'edit', 'delete'
    can_view        BOOLEAN NOT NULL DEFAULT FALSE,
    can_create      BOOLEAN NOT NULL DEFAULT FALSE,
    can_edit        BOOLEAN NOT NULL DEFAULT FALSE,
    can_delete      BOOLEAN NOT NULL DEFAULT FALSE,
    created_at      TIMESTAMP NOT NULL DEFAULT NOW(),
    UNIQUE (tenant_id, role, permission_group, permission_name)
);
```

#### 2.4.2 Seed Data (V24 + V25)

Two Flyway migrations seed the default permissions:

- **V24__seed_default_permissions.sql** — Seeds all 14 roles with granular resource permissions. ADMIN gets wildcard (`*`). Other roles get operation-specific permissions (e.g., OPS_MANAGER gets full CRUD on 18 resources; PICKER gets view-only on inventory + limited picking operations).

- **V25__seed_import_permissions.sql** — Adds `import` permission group for roles that need import capabilities:
  - OPS_MANAGER: full CRUD
  - WAREHOUSE_MANAGER: view + create
  - PROCUREMENT_MANAGER: view + create
  - FINANCE: view + create
  - LOGISTICS_MANAGER: view + create
  - CEO: view only
  - STORE_MANAGER: view only
  - VIEWER: view only

### 2.5 Import History Controller Endpoints

| Method | Path | Auth Required | Permission | Description |
|--------|------|---------------|------------|-------------|
| POST | `/import/token` | JWT | `import:create` | Generate signed upload token |
| POST | `/import/{entityType}` | JWT or Signed Token | `import:create` | Upload and import a file |
| POST | `/import/history/{id}/reprocess` | JWT | `import:create` | Re-import from stored file |
| GET | `/import/history` | JWT | `import:view` | Paginated import history |
| GET | `/import/history/{id}` | JWT | `import:view` | Single import detail |
| GET | `/import/history/{id}/logs` | JWT | `import:view` | Per-row processing logs |
| GET | `/import/history/{id}/download/original` | JWT | `import:view` | Download original file |
| GET | `/import/history/{id}/download/errors` | JWT | `import:view` | Download error CSV |
| GET | `/import/entity-types` | Public | None | List valid entity types |
| GET | `/import/formats` | Public | None | List supported formats |
| GET | `/import/modes` | Public | None | List import modes |

---

## 3. Frontend Implementation

### 3.1 RBAC Component Suite

All RBAC components live in `src/components/rbac/`:

| Component | Purpose |
|-----------|---------|
| `PermissionGate.tsx` | Conditionally renders children based on `resource` + `action` |
| `RequireRole.tsx` | Conditionally renders children based on minimum role level |
| `RoleProtectedRoute.tsx` | Route guard — redirects unauthorized users |
| `useAccess.ts` | Hook that checks if user has a specific permission |
| `index.ts` | Re-exports all RBAC components |

#### 3.1.1 PermissionGate API

```tsx
<PermissionGate resource="orders" action="create">
  <button onClick={handleCreateOrder}>New Order</button>
</PermissionGate>

<PermissionGate resource="inventory" action="edit">
  <button onClick={handleAdjust}>Adjust Inventory</button>
</PermissionGate>

// Multiple actions (OR logic)
<PermissionGate resource="settings" action={['create', 'edit']}>
  <button>Configure</button>
</PermissionGate>
```

#### 3.1.2 useAccess Hook

```ts
const { can, role, permissions } = useAccess()

if (can('orders', 'create')) { /* show create button */ }
if (can('orders', '*')) { /* show all order actions */ }
```

### 3.2 EnterpriseToolbar Permission Prop

The `EnterpriseToolbar` component accepts an optional `permission` field on action items:

```tsx
actions={[
  { 
    label: 'Import File', 
    icon: <FileUp />, 
    onClick: handleImport, 
    variant: 'primary',
    permission: { resource: 'import', action: 'create' }
  },
]}
```

The toolbar internally wraps the action button with `PermissionGate` when `permission` is present.

### 3.3 Import/Export Center Flow

```
ImportExportCenter.tsx
  │
  ├── Toolbar actions (gated with permission prop)
  │   ├── Import File → resource: "import", action: "create"
  │   ├── New Import  → resource: "import", action: "create"
  │   └── New Export  → resource: "import", action: "create"
  │
  ├── Import Jobs DataGrid
  │   └── Retry button  → PermissionGate(resource: "import", action: "edit")
  │   └── Cancel button → PermissionGate(resource: "import", action: "delete")
  │
  ├── Create Job Modal
  │   └── Submit button → PermissionGate(resource: "import", action: "create")
  │
  └── Import File Modal
      ├── Drop zone → File selection (ungated, pre-upload)
      └── Import button → PermissionGate(resource: "import", action: "create")
```

### 3.4 API Client Layer

**File:** `src/api/importApi.ts`

```typescript
// Get signed upload token
async function getImportToken(entityType: string): Promise<string | null>

// Upload file with optional signed token
async function importFile(
  entityType: string,
  file: File,
  format?: string,
  importToken?: string  // Passed as X-Import-Token header
): Promise<ApiResponse<ImportResult>>
```

The Axios client (`src/api/client.ts`) auto-attaches the JWT `Bearer` token from localStorage on every request. When `importToken` is provided, it is sent as `X-Import-Token` alongside the Bearer token. For external integrations, only `X-Import-Token` is needed.

### 3.5 Gated Pages (Complete Inventory)

All 65+ pages in `src/pages/` have been audited and gated with `PermissionGate`. Below is the complete list organized by resource:

| Resource | Pages Gated | Actions Gated |
|----------|-------------|---------------|
| **orders** | OrdersPage, CreateOrderPage, OrderDetailPage, FulfillmentPage, ReturnsPage, ReturnsEnhancedPage, BOPISPage, BopisOwnerPage, PreOrdersPage, OrderRoutingPage | create, edit, delete |
| **inventory** | InventoryPage, InventoryEnhancedPage, InventoryReceivingPage, CycleCountPage, ATPRulesPage | create, edit |
| **warehouse** | WarehousePage, WavePlanningPage, LaborManagementPage, TaskQueuesPage | create, edit, delete |
| **picking** | PickingPage | create, edit |
| **packing** | PackingPage, AiPackingPage | create, edit |
| **shipping** | ShippingPage, ManifestPage, CarrierRateShoppingPage | create, edit, delete |
| **customers** | CustomersPage | create, edit, delete |
| **products** | ProductsPage | create, edit, delete |
| **payments** | PaymentsPage | create, edit |
| **settings** | SettingsPage, UsersPage, StoresPage, StoreDashboardPage, DocumentsPage, LabelPrintingPage, LaunchPadPage, AuditPage, AiPage, AiForecastingPage, AiAuditTrailPage, AiBriefingPage, AiExperimentsPage, AiLoadingPage, AiPlatformPage | create, edit, delete |
| **logistics** | CarriersPage, ManifestPage (logistics actions) | create, edit, delete |
| **procurement** | ProcurementPage | create, edit |
| **routing** | RoutingRulesPage | create, edit, delete |
| **workflows** | WorkflowsPage | create, edit |
| **reports** | AnalyticsPage, ReportBuilderPage | create, edit, delete |
| **integrations** | IntegrationHubPage, IntegrationStoresPage, EmailOrderParsingPage, EdiAutomationPage, AmazonIntegrationPage, WalmartIntegrationPage, EbayIntegrationPage, BigCommercePage, IntegrationMarketplacePage, B2BPortalPage | create, edit, delete |
| **import** | ImportExportCenter | create, edit, delete |

---

## 4. Testing

### 4.1 Unit Tests

| Test Class | Tests | Coverage |
|-----------|-------|----------|
| `PermissionServiceTest.java` | 12 | Resource resolution, action mapping, cache behavior |
| `PermissionAuthorizationFilterTest.java` | 10 | Public paths, bypass roles, permission checks, 403 responses |
| `PermissionMatrixIntegrationTest.java` | 22 | All 14 roles, 31+ resources, all HTTP methods, public endpoints, auth-permissions field |

### 4.2 Integration Test Coverage

The `PermissionMatrixIntegrationTest` validates:

1. **All 14 roles** can access their permitted endpoints and receive 200
2. **All 14 roles** are denied from non-permitted endpoints (403)
3. **Public endpoints** (`/auth/**`, `/webhooks/**`, `/actuator/health`) are accessible without auth
4. **Admin wildcard** works across all resources
5. **`/import/**` no longer public** — requires auth or signed token
6. **`/import/token`** requires JWT with `import:create` permission
7. **Auth response** includes `permissions` field with correct list

---

## 5. Configuration Reference

### 5.1 application.properties

```properties
# JWT Configuration
app.jwt.secret=your-base64-encoded-secret-key-here
app.jwt.expiration-ms=86400000

# Import Token Configuration
app.import-token.ttl-ms=1800000  # 30 minutes (default)

# Permission Cache
app.permission.cache-ttl-ms=60000  # 60 seconds
```

### 5.2 Environment Variables

| Variable | Default | Description |
|----------|---------|-------------|
| `APP_JWT_SECRET` | — | HMAC signing key for JWT and import tokens |
| `APP_JWT_EXPIRATION_MS` | `86400000` | JWT validity period |
| `APP_IMPORT_TOKEN_TTL_MS` | `1800000` | Signed upload token validity |
| `SPRING_DATASOURCE_URL` | — | PostgreSQL JDBC URL |
| `SPRING_DATASOURCE_USERNAME` | — | Database user |
| `SPRING_DATASOURCE_PASSWORD` | — | Database password |

---

## 6. Migration Summary

All Flyway migrations related to the permission system:

| Migration | Description |
|-----------|-------------|
| `V24__seed_default_permissions.sql` | Seeds 14 roles with granular resource permissions |
| `V25__seed_import_permissions.sql` | Adds `import` permission group for authorized roles |

---

## 7. New Files Created

### Backend

| File | Purpose |
|------|---------|
| `src/main/java/com/nexus/oms/service/ImportTokenService.java` | HMAC-SHA256 token generation and validation |
| `src/main/java/com/nexus/oms/security/ImportTokenAuthenticationFilter.java` | Authenticates requests via `X-Import-Token` header |
| `src/main/resources/db/migration/V25__seed_import_permissions.sql` | Seeds `import` permission group |

### Documentation

| File | Purpose |
|------|---------|
| `docs/business-process.md` | This document — business process overview |
| `docs/implementation.md` | This document — technical implementation details |

---

## 8. Modified Files

### Backend

| File | Change |
|------|--------|
| `SecurityConfig.java` | Removed `/import/**` permitAll; requires POST auth; added import token filter |
| `PermissionAuthorizationFilter.java` | Removed `/import/` bypass; IMPORT_TOKEN role treated like ADMIN |
| `ImportHistoryController.java` | Added `POST /import/token` endpoint; injected ImportTokenService |

### Frontend

| File | Change |
|------|--------|
| `src/api/importApi.ts` | Added `getImportToken()` function; `importFile()` accepts optional import token |
| `src/pages/ImportExportCenter.tsx` | Gated toolbar/modal actions; file upload uses signed token |
| ~65 `src/pages/*Page.tsx` files | Added `PermissionGate` wrappers around all interactive elements |
