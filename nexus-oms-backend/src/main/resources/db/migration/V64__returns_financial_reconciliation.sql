-- ============================================================
-- T-10: Returns / Exchange Financial Reconciliation
-- Fixes the single-credit-memo limitation: a return claim can now
-- generate MULTIPLE credit memos (one per transaction), each with
-- its own invoice application.
-- ============================================================

-- 1. Link credit memos to a shared sales-return claim (the parent).
ALTER TABLE nx_credit_memos ADD COLUMN IF NOT EXISTS sales_return_id UUID;
ALTER TABLE nx_credit_memos ADD COLUMN IF NOT EXISTS return_id UUID REFERENCES nx_returns(id);
ALTER TABLE nx_credit_memos ADD COLUMN IF NOT EXISTS outcome VARCHAR(20) DEFAULT 'REFUND';
    -- REFUND | STORE_CREDIT | LIKE_FOR_LIKE_EXCHANGE | LESSER_VALUE_EXCHANGE
ALTER TABLE nx_credit_memos ADD COLUMN IF NOT EXISTS tax_delta DECIMAL(12,2) DEFAULT 0.00;
ALTER TABLE nx_credit_memos ADD COLUMN IF NOT EXISTS source_location_id UUID;
ALTER TABLE nx_credit_memos ADD COLUMN IF NOT EXISTS target_location_id UUID;
ALTER TABLE nx_credit_memos ADD COLUMN IF NOT EXISTS customer_deposit_applied DECIMAL(12,2) DEFAULT 0.00;

-- 2. INVOICE APPLICATION: a credit memo can apply to multiple invoices
--    (replaces the single invoice_id assumption).
CREATE TABLE IF NOT EXISTS nx_credit_memo_invoice_applications (
    id              UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    tenant_id       UUID NOT NULL,
    credit_memo_id  UUID NOT NULL REFERENCES nx_credit_memos(id) ON DELETE CASCADE,
    invoice_id      UUID NOT NULL,
    amount          DECIMAL(12,2) NOT NULL,
    tax_amount      DECIMAL(12,2) DEFAULT 0.00,
    created_at      TIMESTAMP NOT NULL DEFAULT now(),
    UNIQUE (credit_memo_id, invoice_id)
);
CREATE INDEX IF NOT EXISTS idx_cm_invoice_app_cm ON nx_credit_memo_invoice_applications(credit_memo_id);
CREATE INDEX IF NOT EXISTS idx_cm_invoice_app_invoice ON nx_credit_memo_invoice_applications(invoice_id);
