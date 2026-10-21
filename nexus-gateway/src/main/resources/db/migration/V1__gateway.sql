-- Nexus Gateway schema. Runs on PostgreSQL (production) and H2 in PostgreSQL mode (tests).

-- A Nexus installation (one client) that may call the gateway; its API key is kept only as a SHA-256 hash.
CREATE TABLE gateway_client (
    client_id      VARCHAR(40)  NOT NULL PRIMARY KEY,
    client_name    VARCHAR(255) NOT NULL,
    api_key_hash   VARCHAR(64)  NOT NULL,
    active         BOOLEAN      NOT NULL DEFAULT TRUE,
    created_at     TIMESTAMP    NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT uk_gateway_client_key UNIQUE (api_key_hash)
);

-- A carrier or email account of a client, for one store or (store_id NULL) every store of the client.
-- secret holds the encrypted credentials (AES-GCM, master key from the environment); settings the plain ones.
CREATE TABLE provider_account (
    account_id     VARCHAR(40)  NOT NULL PRIMARY KEY,
    client_id      VARCHAR(40)  NOT NULL REFERENCES gateway_client (client_id),
    store_id       VARCHAR(40),
    provider       VARCHAR(40)  NOT NULL,
    environment    VARCHAR(20)  NOT NULL,
    settings       VARCHAR(4000),
    secret         VARCHAR(4000),
    active         BOOLEAN      NOT NULL DEFAULT TRUE,
    created_at     TIMESTAMP    NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at     TIMESTAMP    NOT NULL DEFAULT CURRENT_TIMESTAMP
);
CREATE INDEX ix_provider_account_lookup ON provider_account (client_id, provider, store_id);

-- An email template of a client, for one store or every store; {{name}} placeholders.
CREATE TABLE email_template (
    template_row_id VARCHAR(40)  NOT NULL PRIMARY KEY,
    client_id       VARCHAR(40)  NOT NULL REFERENCES gateway_client (client_id),
    store_id        VARCHAR(40),
    template_id     VARCHAR(100) NOT NULL,
    subject         VARCHAR(500) NOT NULL,
    body_html       TEXT         NOT NULL,
    updated_at      TIMESTAMP    NOT NULL DEFAULT CURRENT_TIMESTAMP
);
CREATE INDEX ix_email_template_lookup ON email_template (client_id, template_id, store_id);

-- A label the gateway created, so the OMS can fetch it again (reprint) without calling the carrier.
CREATE TABLE label_document (
    label_id        VARCHAR(40)  NOT NULL PRIMARY KEY,
    client_id       VARCHAR(40)  NOT NULL REFERENCES gateway_client (client_id),
    store_id        VARCHAR(40),
    carrier         VARCHAR(40)  NOT NULL,
    tracking_number VARCHAR(100),
    carrier_shipment_id VARCHAR(100),
    image_format    VARCHAR(20)  NOT NULL,
    content_base64  TEXT         NOT NULL,
    voided          BOOLEAN      NOT NULL DEFAULT FALSE,
    created_at      TIMESTAMP    NOT NULL DEFAULT CURRENT_TIMESTAMP
);
CREATE INDEX ix_label_document_tracking ON label_document (client_id, tracking_number);

-- Every call: who, what, which provider, the result and how long it took.
CREATE TABLE request_log (
    request_id      VARCHAR(40)  NOT NULL PRIMARY KEY,
    client_id       VARCHAR(40),
    store_id        VARCHAR(40),
    operation       VARCHAR(40)  NOT NULL,
    provider        VARCHAR(40),
    outcome         VARCHAR(20)  NOT NULL,
    duration_ms     BIGINT       NOT NULL,
    reference       VARCHAR(255),
    error_text      VARCHAR(4000),
    created_at      TIMESTAMP    NOT NULL DEFAULT CURRENT_TIMESTAMP
);
CREATE INDEX ix_request_log_client_time ON request_log (client_id, created_at);
