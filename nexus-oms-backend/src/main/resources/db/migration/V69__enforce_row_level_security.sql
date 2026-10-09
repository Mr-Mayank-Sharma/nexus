-- V69: Make Row-Level Security actually enforce tenant isolation
--
-- Before this migration RLS was decorative:
--   * the application connected as the database owner / superuser, which bypasses RLS;
--   * ~68 tables created after V26 had no policy at all;
--   * the policy hid tenant_id IS NULL rows, so enforcing it would have broken global
--     reference data (default role permissions, shared catalogues).
--
-- After this migration:
--   * every table with a tenant_id column has RLS enabled + FORCED and one policy;
--   * a NOLOGIN, non-superuser role `nexus_app` exists with DML rights only; the app
--     switches to it (SET ROLE) on every tenant-scoped connection (TenantAwareDataSource);
--   * the policy shows a row when it is global (tenant_id IS NULL), belongs to the current
--     tenant, or no tenant is set (Flyway, schedulers, unauthenticated webhooks that resolve
--     their tenant later). Cross-tenant reads and writes from a tenant request return nothing.

DO $$
BEGIN
    IF NOT EXISTS (SELECT 1 FROM pg_roles WHERE rolname = 'nexus_app') THEN
        CREATE ROLE nexus_app NOLOGIN NOSUPERUSER NOBYPASSRLS NOCREATEDB NOCREATEROLE;
    END IF;
    -- Lets a non-superuser login role SET ROLE nexus_app (superusers can already).
    EXECUTE format('GRANT nexus_app TO %I', current_user);
EXCEPTION WHEN insufficient_privilege THEN
    RAISE WARNING 'V69: could not create/grant role nexus_app (%). Create it manually: '
                  'CREATE ROLE nexus_app NOLOGIN; GRANT nexus_app TO <app login role>;', SQLERRM;
END;
$$;

DO $$
BEGIN
    IF EXISTS (SELECT 1 FROM pg_roles WHERE rolname = 'nexus_app') THEN
        EXECUTE 'GRANT USAGE ON SCHEMA public TO nexus_app';
        EXECUTE 'GRANT SELECT, INSERT, UPDATE, DELETE ON ALL TABLES IN SCHEMA public TO nexus_app';
        EXECUTE 'GRANT USAGE, SELECT, UPDATE ON ALL SEQUENCES IN SCHEMA public TO nexus_app';
        EXECUTE 'GRANT EXECUTE ON ALL FUNCTIONS IN SCHEMA public TO nexus_app';
        EXECUTE format('ALTER DEFAULT PRIVILEGES FOR ROLE %I IN SCHEMA public '
                       'GRANT SELECT, INSERT, UPDATE, DELETE ON TABLES TO nexus_app', current_user);
        EXECUTE format('ALTER DEFAULT PRIVILEGES FOR ROLE %I IN SCHEMA public '
                       'GRANT USAGE, SELECT, UPDATE ON SEQUENCES TO nexus_app', current_user);
        IF EXISTS (SELECT 1 FROM pg_namespace WHERE nspname = 'app') THEN
            EXECUTE 'GRANT USAGE ON SCHEMA app TO nexus_app';
            EXECUTE 'GRANT EXECUTE ON ALL FUNCTIONS IN SCHEMA app TO nexus_app';
        END IF;
    END IF;
END;
$$;

DO $$
DECLARE
    tbl TEXT;
    col_type TEXT;
    tenant_match TEXT;
    pol RECORD;
BEGIN
    FOR tbl, col_type IN
        SELECT c.table_name, c.data_type
        FROM information_schema.columns c
        JOIN information_schema.tables t
          ON t.table_schema = c.table_schema AND t.table_name = c.table_name
        WHERE c.table_schema = 'public'
          AND c.column_name = 'tenant_id'
          AND t.table_type = 'BASE TABLE'
    LOOP
        -- Replace whatever policies earlier migrations left behind with one consistent policy.
        FOR pol IN SELECT policyname FROM pg_policies WHERE schemaname = 'public' AND tablename = tbl LOOP
            EXECUTE format('DROP POLICY IF EXISTS %I ON %I', pol.policyname, tbl);
        END LOOP;

        -- Most tenant_id columns are uuid; a few legacy ones are text.
        tenant_match := CASE WHEN col_type = 'uuid'
            THEN $m$tenant_id = nullif(current_setting('app.current_tenant_id', true), '')::uuid$m$
            ELSE $m$tenant_id::text = nullif(current_setting('app.current_tenant_id', true), '')$m$
        END;

        EXECUTE format('ALTER TABLE %I ENABLE ROW LEVEL SECURITY', tbl);
        EXECUTE format('ALTER TABLE %I FORCE ROW LEVEL SECURITY', tbl);
        EXECUTE format($p$
            CREATE POLICY tenant_isolation ON %I
            USING (
                tenant_id IS NULL
                OR nullif(current_setting('app.current_tenant_id', true), '') IS NULL
                OR %s
            )
            WITH CHECK (
                tenant_id IS NULL
                OR nullif(current_setting('app.current_tenant_id', true), '') IS NULL
                OR %s
            )$p$, tbl, tenant_match, tenant_match);
    END LOOP;
END;
$$;
