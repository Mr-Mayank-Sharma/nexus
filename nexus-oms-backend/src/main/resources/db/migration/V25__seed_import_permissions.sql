-- Seed 'import' permission_group for roles that need import capabilities.
-- Import endpoints are no longer fully public; they require authentication and permission checks.
-- Permission groups: import (resource for all /import/** endpoints)

-- ADMIN already has wildcard (*) from V24 — no change needed.

-- CEO: view-only access to import history + results
INSERT INTO nx_role_permissions (id, tenant_id, role, permission_group, permission_name, can_view, can_create, can_edit, can_delete, created_at)
SELECT gen_random_uuid(), NULL, 'CEO', 'import', p,
       CASE WHEN p = 'view' THEN TRUE ELSE FALSE END,
       FALSE, FALSE, FALSE, NOW()
FROM (VALUES ('view'), ('create'), ('edit'), ('delete')) AS perms(p)
WHERE NOT EXISTS (SELECT 1 FROM nx_role_permissions WHERE role = 'CEO' AND permission_group = 'import' AND permission_name = p AND tenant_id IS NULL);

-- OPS_MANAGER: full import access
INSERT INTO nx_role_permissions (id, tenant_id, role, permission_group, permission_name, can_view, can_create, can_edit, can_delete, created_at)
SELECT gen_random_uuid(), NULL, 'OPS_MANAGER', 'import', p, TRUE, TRUE, TRUE, TRUE, NOW()
FROM (VALUES ('view'), ('create'), ('edit'), ('delete')) AS perms(p)
WHERE NOT EXISTS (SELECT 1 FROM nx_role_permissions WHERE role = 'OPS_MANAGER' AND permission_group = 'import' AND permission_name = p AND tenant_id IS NULL);

-- WAREHOUSE_MANAGER: import inventory data
INSERT INTO nx_role_permissions (id, tenant_id, role, permission_group, permission_name, can_view, can_create, can_edit, can_delete, created_at)
SELECT gen_random_uuid(), NULL, 'WAREHOUSE_MANAGER', 'import', p,
       CASE WHEN p = 'delete' THEN FALSE ELSE TRUE END,
       CASE WHEN p = 'create' THEN TRUE ELSE FALSE END,
       FALSE, FALSE, NOW()
FROM (VALUES ('view'), ('create'), ('edit'), ('delete')) AS perms(p)
WHERE NOT EXISTS (SELECT 1 FROM nx_role_permissions WHERE role = 'WAREHOUSE_MANAGER' AND permission_group = 'import' AND permission_name = p AND tenant_id IS NULL);

-- PROCUREMENT_MANAGER: import products, purchase orders
INSERT INTO nx_role_permissions (id, tenant_id, role, permission_group, permission_name, can_view, can_create, can_edit, can_delete, created_at)
SELECT gen_random_uuid(), NULL, 'PROCUREMENT_MANAGER', 'import', p,
       CASE WHEN p = 'delete' THEN FALSE ELSE TRUE END,
       CASE WHEN p = 'create' THEN TRUE ELSE FALSE END,
       FALSE, FALSE, NOW()
FROM (VALUES ('view'), ('create'), ('edit'), ('delete')) AS perms(p)
WHERE NOT EXISTS (SELECT 1 FROM nx_role_permissions WHERE role = 'PROCUREMENT_MANAGER' AND permission_group = 'import' AND permission_name = p AND tenant_id IS NULL);

-- FINANCE: import invoices
INSERT INTO nx_role_permissions (id, tenant_id, role, permission_group, permission_name, can_view, can_create, can_edit, can_delete, created_at)
SELECT gen_random_uuid(), NULL, 'FINANCE', 'import', p,
       CASE WHEN p = 'delete' THEN FALSE ELSE TRUE END,
       CASE WHEN p = 'create' THEN TRUE ELSE FALSE END,
       FALSE, FALSE, NOW()
FROM (VALUES ('view'), ('create'), ('edit'), ('delete')) AS perms(p)
WHERE NOT EXISTS (SELECT 1 FROM nx_role_permissions WHERE role = 'FINANCE' AND permission_group = 'import' AND permission_name = p AND tenant_id IS NULL);

-- LOGISTICS_MANAGER: import shipments, carriers
INSERT INTO nx_role_permissions (id, tenant_id, role, permission_group, permission_name, can_view, can_create, can_edit, can_delete, created_at)
SELECT gen_random_uuid(), NULL, 'LOGISTICS_MANAGER', 'import', p,
       CASE WHEN p = 'delete' THEN FALSE ELSE TRUE END,
       CASE WHEN p = 'create' THEN TRUE ELSE FALSE END,
       FALSE, FALSE, NOW()
FROM (VALUES ('view'), ('create'), ('edit'), ('delete')) AS perms(p)
WHERE NOT EXISTS (SELECT 1 FROM nx_role_permissions WHERE role = 'LOGISTICS_MANAGER' AND permission_group = 'import' AND permission_name = p AND tenant_id IS NULL);

-- STORE_MANAGER: view-only import history
INSERT INTO nx_role_permissions (id, tenant_id, role, permission_group, permission_name, can_view, can_create, can_edit, can_delete, created_at)
SELECT gen_random_uuid(), NULL, 'STORE_MANAGER', 'import', p,
       CASE WHEN p = 'view' THEN TRUE ELSE FALSE END,
       FALSE, FALSE, FALSE, NOW()
FROM (VALUES ('view'), ('create'), ('edit'), ('delete')) AS perms(p)
WHERE NOT EXISTS (SELECT 1 FROM nx_role_permissions WHERE role = 'STORE_MANAGER' AND permission_group = 'import' AND permission_name = p AND tenant_id IS NULL);

-- VIEWER: read-only access to import results
INSERT INTO nx_role_permissions (id, tenant_id, role, permission_group, permission_name, can_view, can_create, can_edit, can_delete, created_at)
SELECT gen_random_uuid(), NULL, 'VIEWER', 'import', p,
       CASE WHEN p = 'view' THEN TRUE ELSE FALSE END,
       FALSE, FALSE, FALSE, NOW()
FROM (VALUES ('view'), ('create'), ('edit'), ('delete')) AS perms(p)
WHERE NOT EXISTS (SELECT 1 FROM nx_role_permissions WHERE role = 'VIEWER' AND permission_group = 'import' AND permission_name = p AND tenant_id IS NULL);
