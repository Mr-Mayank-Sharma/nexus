-- Seed default RolePermission entries for all 14 roles.
-- This ensures the permission system has meaningful defaults on first deploy.
-- ADMIN gets a wildcard (*) that grants all; other roles get granular resource-level permissions.
-- The tenant_id is left NULL for global defaults; individual tenants can override.

-- ADMIN: wildcard — everything
INSERT INTO nx_role_permissions (id, tenant_id, role, permission_group, permission_name, can_view, can_create, can_edit, can_delete, created_at)
SELECT gen_random_uuid(), NULL, 'ADMIN', '*', '*', TRUE, TRUE, TRUE, TRUE, NOW()
WHERE NOT EXISTS (SELECT 1 FROM nx_role_permissions WHERE role = 'ADMIN' AND permission_group = '*' AND permission_name = '*' AND tenant_id IS NULL);

-- CEO: view + some edit on strategic modules
INSERT INTO nx_role_permissions (id, tenant_id, role, permission_group, permission_name, can_view, can_create, can_edit, can_delete, created_at)
SELECT gen_random_uuid(), NULL, 'CEO', m, p, TRUE, FALSE, FALSE, FALSE, NOW()
FROM (VALUES ('analytics'), ('ai'), ('orders'), ('inventory'), ('customers'), ('returns'), ('shipments'), ('products'), ('settings'), ('documents'), ('audit')) AS modules(m),
     (VALUES ('view'), ('create'), ('edit'), ('delete')) AS perms(p)
WHERE NOT EXISTS (SELECT 1 FROM nx_role_permissions WHERE role = 'CEO' AND permission_group = m AND permission_name = p AND tenant_id IS NULL);

-- OPS_MANAGER: full access to operations modules
INSERT INTO nx_role_permissions (id, tenant_id, role, permission_group, permission_name, can_view, can_create, can_edit, can_delete, created_at)
SELECT gen_random_uuid(), NULL, 'OPS_MANAGER', m, p, TRUE, TRUE, TRUE, TRUE, NOW()
FROM (VALUES ('orders'), ('inventory'), ('customers'), ('returns'), ('shipments'), ('products'), ('picking'), ('packing'), ('shipping'), ('warehouse'), ('routing'), ('carriers'), ('fulfillment'), ('notifications'), ('analytics'), ('ai'), ('documents'), ('audit')) AS modules(m),
     (VALUES ('view'), ('create'), ('edit'), ('delete')) AS perms(p)
WHERE NOT EXISTS (SELECT 1 FROM nx_role_permissions WHERE role = 'OPS_MANAGER' AND permission_group = m AND permission_name = p AND tenant_id IS NULL);

-- WAREHOUSE_MANAGER: full access to warehouse-facing modules
INSERT INTO nx_role_permissions (id, tenant_id, role, permission_group, permission_name, can_view, can_create, can_edit, can_delete, created_at)
SELECT gen_random_uuid(), NULL, 'WAREHOUSE_MANAGER', m, p, TRUE, TRUE, TRUE, TRUE, NOW()
FROM (VALUES ('inventory'), ('picking'), ('packing'), ('shipping'), ('warehouse'), ('receiving'), ('cycle-counts'), ('fulfillment'), ('shipments'), ('notifications'), ('analytics')) AS modules(m),
     (VALUES ('view'), ('create'), ('edit'), ('delete')) AS perms(p)
WHERE NOT EXISTS (SELECT 1 FROM nx_role_permissions WHERE role = 'WAREHOUSE_MANAGER' AND permission_group = m AND permission_name = p AND tenant_id IS NULL);

-- PICKER: operate picking + view inventory
INSERT INTO nx_role_permissions (id, tenant_id, role, permission_group, permission_name, can_view, can_create, can_edit, can_delete, created_at)
SELECT gen_random_uuid(), NULL, 'PICKER', m, p,
       CASE WHEN p = 'delete' THEN FALSE ELSE TRUE END,
       CASE WHEN m = 'picking' AND p = 'create' THEN TRUE ELSE FALSE END,
       CASE WHEN m = 'picking' AND p = 'edit' THEN TRUE ELSE FALSE END,
       FALSE, NOW()
FROM (VALUES ('picking'), ('inventory')) AS modules(m),
     (VALUES ('view'), ('create'), ('edit'), ('delete')) AS perms(p)
WHERE NOT EXISTS (SELECT 1 FROM nx_role_permissions WHERE role = 'PICKER' AND permission_group = m AND permission_name = p AND tenant_id IS NULL);

-- PACKER: operate packing + view inventory
INSERT INTO nx_role_permissions (id, tenant_id, role, permission_group, permission_name, can_view, can_create, can_edit, can_delete, created_at)
SELECT gen_random_uuid(), NULL, 'PACKER', m, p,
       CASE WHEN p = 'delete' THEN FALSE ELSE TRUE END,
       CASE WHEN m = 'packing' AND p = 'create' THEN TRUE ELSE FALSE END,
       CASE WHEN m = 'packing' AND p = 'edit' THEN TRUE ELSE FALSE END,
       FALSE, NOW()
FROM (VALUES ('packing'), ('inventory')) AS modules(m),
     (VALUES ('view'), ('create'), ('edit'), ('delete')) AS perms(p)
WHERE NOT EXISTS (SELECT 1 FROM nx_role_permissions WHERE role = 'PACKER' AND permission_group = m AND permission_name = p AND tenant_id IS NULL);

-- LOADER: operate shipping + view shipments
INSERT INTO nx_role_permissions (id, tenant_id, role, permission_group, permission_name, can_view, can_create, can_edit, can_delete, created_at)
SELECT gen_random_uuid(), NULL, 'LOADER', m, p,
       CASE WHEN p = 'delete' THEN FALSE ELSE TRUE END,
       CASE WHEN m = 'shipping' AND p = 'create' THEN TRUE ELSE FALSE END,
       CASE WHEN m = 'shipping' AND p = 'edit' THEN TRUE ELSE FALSE END,
       FALSE, NOW()
FROM (VALUES ('shipping'), ('shipments')) AS modules(m),
     (VALUES ('view'), ('create'), ('edit'), ('delete')) AS perms(p)
WHERE NOT EXISTS (SELECT 1 FROM nx_role_permissions WHERE role = 'LOADER' AND permission_group = m AND permission_name = p AND tenant_id IS NULL);

-- STORE_MANAGER: full access to store-facing modules
INSERT INTO nx_role_permissions (id, tenant_id, role, permission_group, permission_name, can_view, can_create, can_edit, can_delete, created_at)
SELECT gen_random_uuid(), NULL, 'STORE_MANAGER', m, p, TRUE, TRUE, TRUE, TRUE, NOW()
FROM (VALUES ('orders'), ('inventory'), ('customers'), ('products'), ('returns'), ('notifications'), ('bopis')) AS modules(m),
     (VALUES ('view'), ('create'), ('edit'), ('delete')) AS perms(p)
WHERE NOT EXISTS (SELECT 1 FROM nx_role_permissions WHERE role = 'STORE_MANAGER' AND permission_group = m AND permission_name = p AND tenant_id IS NULL);

-- BOPIS_OWNER: full access to BOPIS-facing modules
INSERT INTO nx_role_permissions (id, tenant_id, role, permission_group, permission_name, can_view, can_create, can_edit, can_delete, created_at)
SELECT gen_random_uuid(), NULL, 'BOPIS_OWNER', m, p, TRUE, TRUE, TRUE, TRUE, NOW()
FROM (VALUES ('bopis'), ('orders'), ('inventory'), ('customers'), ('notifications')) AS modules(m),
     (VALUES ('view'), ('create'), ('edit'), ('delete')) AS perms(p)
WHERE NOT EXISTS (SELECT 1 FROM nx_role_permissions WHERE role = 'BOPIS_OWNER' AND permission_group = m AND permission_name = p AND tenant_id IS NULL);

-- CUSTOMER_SUPPORT: view + edit customers, orders, returns, products
INSERT INTO nx_role_permissions (id, tenant_id, role, permission_group, permission_name, can_view, can_create, can_edit, can_delete, created_at)
SELECT gen_random_uuid(), NULL, 'CUSTOMER_SUPPORT', m, p,
       CASE WHEN p = 'delete' THEN FALSE ELSE TRUE END,
       CASE WHEN m IN ('orders', 'returns') AND p = 'create' THEN TRUE ELSE FALSE END,
       CASE WHEN m IN ('orders', 'returns', 'customers') AND p = 'edit' THEN TRUE ELSE FALSE END,
       FALSE, NOW()
FROM (VALUES ('orders'), ('customers'), ('returns'), ('products'), ('notifications')) AS modules(m),
     (VALUES ('view'), ('create'), ('edit'), ('delete')) AS perms(p)
WHERE NOT EXISTS (SELECT 1 FROM nx_role_permissions WHERE role = 'CUSTOMER_SUPPORT' AND permission_group = m AND permission_name = p AND tenant_id IS NULL);

-- PROCUREMENT_MANAGER: full access to procurement modules
INSERT INTO nx_role_permissions (id, tenant_id, role, permission_group, permission_name, can_view, can_create, can_edit, can_delete, created_at)
SELECT gen_random_uuid(), NULL, 'PROCUREMENT_MANAGER', m, p, TRUE, TRUE, TRUE, TRUE, NOW()
FROM (VALUES ('procurement'), ('invoices'), ('products'), ('notifications'), ('analytics')) AS modules(m),
     (VALUES ('view'), ('create'), ('edit'), ('delete')) AS perms(p)
WHERE NOT EXISTS (SELECT 1 FROM nx_role_permissions WHERE role = 'PROCUREMENT_MANAGER' AND permission_group = m AND permission_name = p AND tenant_id IS NULL);

-- FINANCE: view + edit invoices, payments, returns, analytics
INSERT INTO nx_role_permissions (id, tenant_id, role, permission_group, permission_name, can_view, can_create, can_edit, can_delete, created_at)
SELECT gen_random_uuid(), NULL, 'FINANCE', m, p,
       CASE WHEN p = 'delete' THEN FALSE ELSE TRUE END,
       CASE WHEN m IN ('invoices', 'payments') AND p = 'create' THEN TRUE ELSE FALSE END,
       CASE WHEN m IN ('invoices', 'payments', 'returns') AND p = 'edit' THEN TRUE ELSE FALSE END,
       FALSE, NOW()
FROM (VALUES ('invoices'), ('payments'), ('returns'), ('customers'), ('analytics'), ('notifications')) AS modules(m),
     (VALUES ('view'), ('create'), ('edit'), ('delete')) AS perms(p)
WHERE NOT EXISTS (SELECT 1 FROM nx_role_permissions WHERE role = 'FINANCE' AND permission_group = m AND permission_name = p AND tenant_id IS NULL);

-- LOGISTICS_MANAGER: full access to logistics modules
INSERT INTO nx_role_permissions (id, tenant_id, role, permission_group, permission_name, can_view, can_create, can_edit, can_delete, created_at)
SELECT gen_random_uuid(), NULL, 'LOGISTICS_MANAGER', m, p, TRUE, TRUE, TRUE, TRUE, NOW()
FROM (VALUES ('shipping'), ('carriers'), ('routing'), ('fulfillment'), ('orders'), ('shipments'), ('warehouse'), ('notifications'), ('analytics')) AS modules(m),
     (VALUES ('view'), ('create'), ('edit'), ('delete')) AS perms(p)
WHERE NOT EXISTS (SELECT 1 FROM nx_role_permissions WHERE role = 'LOGISTICS_MANAGER' AND permission_group = m AND permission_name = p AND tenant_id IS NULL);

-- VIEWER: read-only on dashboard-facing modules
INSERT INTO nx_role_permissions (id, tenant_id, role, permission_group, permission_name, can_view, can_create, can_edit, can_delete, created_at)
SELECT gen_random_uuid(), NULL, 'VIEWER', m, p,
       CASE WHEN p = 'view' THEN TRUE ELSE FALSE END,
       FALSE, FALSE, FALSE, NOW()
FROM (VALUES ('orders'), ('inventory'), ('products'), ('analytics'), ('shipments'), ('customers')) AS modules(m),
     (VALUES ('view'), ('create'), ('edit'), ('delete')) AS perms(p)
WHERE NOT EXISTS (SELECT 1 FROM nx_role_permissions WHERE role = 'VIEWER' AND permission_group = m AND permission_name = p AND tenant_id IS NULL);
