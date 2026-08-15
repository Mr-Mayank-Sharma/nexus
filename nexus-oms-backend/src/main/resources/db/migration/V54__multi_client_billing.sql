-- 3PL multi-client billing: rate cards, statements, statement lines.

CREATE TABLE IF NOT EXISTS nx_rate_cards (
    id              UUID PRIMARY KEY,
    tenant_id       UUID NOT NULL,
    client_id       UUID,
    client_name     VARCHAR(255),
    currency        VARCHAR(8) NOT NULL DEFAULT 'USD',
    per_order_fee   NUMERIC(12,2) NOT NULL DEFAULT 0,
    per_line_fee    NUMERIC(12,2) NOT NULL DEFAULT 0,
    picking_fee_per_line NUMERIC(12,2) NOT NULL DEFAULT 0,
    storage_fee_per_unit  NUMERIC(12,2) NOT NULL DEFAULT 0,
    description     TEXT,
    effective_from  TIMESTAMP,
    effective_to    TIMESTAMP,
    is_active       BOOLEAN NOT NULL DEFAULT TRUE,
    created_at      TIMESTAMP NOT NULL DEFAULT NOW(),
    updated_at      TIMESTAMP NOT NULL DEFAULT NOW()
);

CREATE TABLE IF NOT EXISTS nx_billing_statements (
    id              UUID PRIMARY KEY,
    tenant_id       UUID NOT NULL,
    client_id       UUID NOT NULL,
    client_name     VARCHAR(255) NOT NULL,
    currency        VARCHAR(8) NOT NULL DEFAULT 'USD',
    period_start    DATE NOT NULL,
    period_end      DATE NOT NULL,
    status          VARCHAR(20) NOT NULL DEFAULT 'DRAFT',
    order_count     INTEGER NOT NULL DEFAULT 0,
    line_count      INTEGER NOT NULL DEFAULT 0,
    picked_lines    INTEGER NOT NULL DEFAULT 0,
    units_handled   INTEGER NOT NULL DEFAULT 0,
    subtotal        NUMERIC(14,2) NOT NULL DEFAULT 0,
    total           NUMERIC(14,2) NOT NULL DEFAULT 0,
    notes           TEXT,
    created_at      TIMESTAMP NOT NULL DEFAULT NOW(),
    updated_at      TIMESTAMP NOT NULL DEFAULT NOW()
);

CREATE TABLE IF NOT EXISTS nx_billing_statement_lines (
    id              UUID PRIMARY KEY,
    statement_id    UUID NOT NULL REFERENCES nx_billing_statements(id) ON DELETE CASCADE,
    rate_type       VARCHAR(30) NOT NULL,
    description     VARCHAR(500),
    quantity        INTEGER NOT NULL DEFAULT 0,
    unit_price      NUMERIC(12,2) NOT NULL DEFAULT 0,
    amount          NUMERIC(14,2) NOT NULL DEFAULT 0,
    created_at      TIMESTAMP NOT NULL DEFAULT NOW()
);

CREATE INDEX IF NOT EXISTS idx_rate_cards_tenant ON nx_rate_cards(tenant_id);
CREATE INDEX IF NOT EXISTS idx_rate_cards_client ON nx_rate_cards(client_id);
CREATE INDEX IF NOT EXISTS idx_billing_statements_tenant ON nx_billing_statements(tenant_id);
CREATE INDEX IF NOT EXISTS idx_billing_statements_client ON nx_billing_statements(client_id);
CREATE INDEX IF NOT EXISTS idx_billing_statement_lines_stmt ON nx_billing_statement_lines(statement_id);
