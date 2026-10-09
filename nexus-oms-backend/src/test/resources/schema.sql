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
