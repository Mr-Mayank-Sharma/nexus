-- Promotions Engine: promotion rules, coupon codes, usage tracking
CREATE TABLE nx_promotions (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    tenant_id UUID NOT NULL,
    name VARCHAR(255) NOT NULL,
    description TEXT,
    promotion_type VARCHAR(50) NOT NULL, -- PERCENTAGE, FIXED_AMOUNT, BOGO, FREE_SHIPPING, BUY_X_GET_Y
    discount_value DECIMAL(12,2) NOT NULL,
    min_order_amount DECIMAL(12,2),
    min_quantity INTEGER,
    max_uses_total INTEGER,
    max_uses_per_customer INTEGER,
    current_uses INTEGER NOT NULL DEFAULT 0,
    coupon_code VARCHAR(100) UNIQUE,
    start_date TIMESTAMP NOT NULL,
    end_date TIMESTAMP NOT NULL,
    applicable_channels VARCHAR(255), -- comma-separated: ONLINE,IN_STORE,MOBILE,ALL
    applicable_product_ids TEXT, -- comma-separated product IDs, null = all products
    applicable_category_ids TEXT, -- comma-separated category IDs
    stackable BOOLEAN NOT NULL DEFAULT FALSE,
    priority INTEGER NOT NULL DEFAULT 10,
    active BOOLEAN NOT NULL DEFAULT TRUE,
    created_at TIMESTAMP NOT NULL DEFAULT NOW(),
    updated_at TIMESTAMP NOT NULL DEFAULT NOW()
);

CREATE INDEX idx_promotions_tenant ON nx_promotions(tenant_id);
CREATE INDEX idx_promotions_coupon ON nx_promotions(coupon_code) WHERE coupon_code IS NOT NULL;
CREATE INDEX idx_promotions_active ON nx_promotions(tenant_id, active, start_date, end_date);

CREATE TABLE nx_promotion_usage (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    tenant_id UUID NOT NULL,
    promotion_id UUID NOT NULL REFERENCES nx_promotions(id),
    order_id UUID,
    customer_id UUID,
    coupon_code VARCHAR(100),
    discount_amount DECIMAL(12,2) NOT NULL,
    order_total DECIMAL(12,2),
    used_at TIMESTAMP NOT NULL DEFAULT NOW()
);

CREATE INDEX idx_promotion_usage_tenant ON nx_promotion_usage(tenant_id);
CREATE INDEX idx_promotion_usage_promotion ON nx_promotion_usage(promotion_id);
CREATE INDEX idx_promotion_usage_customer ON nx_promotion_usage(customer_id);
