-- Doc 29, phase C: sign-in on the core. Users, roles and permissions live in each company's core database; the shared
-- database keeps only what sign-in needs before a company is known.

-- The companies.
CREATE TABLE IF NOT EXISTS platform_tenant (
    tenant_id     UUID PRIMARY KEY,
    company_name  VARCHAR(255) NOT NULL,
    created_at    TIMESTAMP    NOT NULL DEFAULT CURRENT_TIMESTAMP
);

-- Which company a username (and its email) belongs to.
CREATE TABLE IF NOT EXISTS platform_login (
    username      VARCHAR(100) PRIMARY KEY,
    email         VARCHAR(255),
    tenant_id     UUID         NOT NULL,
    created_at    TIMESTAMP    NOT NULL DEFAULT CURRENT_TIMESTAMP
);
CREATE INDEX IF NOT EXISTS ix_platform_login_email ON platform_login (LOWER(email));

-- Single-use sign-in tokens (MFA challenges, password resets, SSO state) are used before sign-in: platform data.
ALTER TABLE nx_auth_tokens RENAME TO platform_auth_token;

INSERT INTO platform_tenant (tenant_id, company_name)
SELECT tenant_id, COALESCE(NULLIF(TRIM(company_name), ''), 'Company') FROM nx_company_settings
ON CONFLICT (tenant_id) DO NOTHING;
INSERT INTO platform_tenant (tenant_id, company_name)
SELECT DISTINCT tenant_id, 'Company' FROM nx_users WHERE tenant_id IS NOT NULL
ON CONFLICT (tenant_id) DO NOTHING;

INSERT INTO platform_login (username, email, tenant_id)
SELECT username, email, tenant_id FROM nx_users WHERE tenant_id IS NOT NULL
ON CONFLICT (username) DO NOTHING;
