-- Doc 29, phase B: one core database per company. This table lives in the shared (control) database and says where
-- each company's core database is. A company without a row uses the shared database.
CREATE TABLE IF NOT EXISTS platform_tenant_database (
    tenant_id          UUID PRIMARY KEY,
    jdbc_url           VARCHAR(500) NOT NULL,
    username           VARCHAR(100) NOT NULL,
    password_sealed    VARCHAR(1000) NOT NULL,
    status             VARCHAR(20)  NOT NULL DEFAULT 'ACTIVE',
    created_at         TIMESTAMP    NOT NULL DEFAULT CURRENT_TIMESTAMP
);
