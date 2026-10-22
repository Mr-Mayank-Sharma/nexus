-- H2 test database (Flyway is off in tests): tables that have no JPA entity.
CREATE TABLE IF NOT EXISTS shedlock (
    name       VARCHAR(64)  NOT NULL PRIMARY KEY,
    lock_until TIMESTAMP    NOT NULL,
    locked_at  TIMESTAMP    NOT NULL,
    locked_by  VARCHAR(255) NOT NULL
);

CREATE TABLE IF NOT EXISTS nx_outbox (
    id          UUID         PRIMARY KEY,
    tenant_id   UUID,
    topic       VARCHAR(200) NOT NULL,
    msg_key     VARCHAR(200),
    payload     TEXT         NOT NULL,
    status      VARCHAR(16)  NOT NULL DEFAULT 'PENDING',
    attempts    INTEGER      NOT NULL DEFAULT 0,
    last_error  TEXT,
    created_at  TIMESTAMP    NOT NULL,
    sent_at     TIMESTAMP
);

-- Platform tables (doc 29): which company is where, and what sign-in needs before a company is known.
CREATE TABLE IF NOT EXISTS platform_tenant_database (
    tenant_id          UUID PRIMARY KEY,
    jdbc_url           VARCHAR(500) NOT NULL,
    username           VARCHAR(100) NOT NULL,
    password_sealed    VARCHAR(1000) NOT NULL,
    status             VARCHAR(20)  NOT NULL DEFAULT 'ACTIVE',
    created_at         TIMESTAMP    NOT NULL DEFAULT CURRENT_TIMESTAMP
);

CREATE TABLE IF NOT EXISTS platform_tenant (
    tenant_id     UUID PRIMARY KEY,
    company_name  VARCHAR(255) NOT NULL,
    created_at    TIMESTAMP    NOT NULL DEFAULT CURRENT_TIMESTAMP
);

CREATE TABLE IF NOT EXISTS platform_login (
    username      VARCHAR(100) PRIMARY KEY,
    email         VARCHAR(255),
    tenant_id     UUID         NOT NULL,
    created_at    TIMESTAMP    NOT NULL DEFAULT CURRENT_TIMESTAMP
);

CREATE TABLE IF NOT EXISTS platform_auth_token (
    id          UUID         PRIMARY KEY,
    token_type  VARCHAR(32)  NOT NULL,
    token_hash  VARCHAR(64)  NOT NULL,
    username    VARCHAR(255),
    payload     VARCHAR(2000),
    attempts    INTEGER      NOT NULL DEFAULT 0,
    expires_at  TIMESTAMP    NOT NULL,
    created_at  TIMESTAMP    NOT NULL DEFAULT CURRENT_TIMESTAMP
);
