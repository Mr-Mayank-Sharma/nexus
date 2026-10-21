package com.nexus.oms.coremigration;

import com.nexus.core.CoreTestData;
import com.nexus.core.service.ProductService;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestInstance;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.ActiveProfiles;

import java.math.BigDecimal;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;

@SpringBootTest
@ActiveProfiles("test")
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
class ProductBackfillTest {

    @Autowired ProductBackfill backfill;
    @Autowired ProductService products;
    @Autowired JdbcTemplate jdbc;

    final UUID tenant = UUID.randomUUID();
    final UUID noCurrencyTenant = UUID.randomUUID();
    final UUID boots = UUID.randomUUID();

    @BeforeAll
    void legacy() {
        CoreTestData.ensureSeeded(jdbc);
        jdbc.update("INSERT INTO nx_company_settings (id, tenant_id, company_name, default_currency, created_at, updated_at) VALUES (?, ?, 'Test Co', 'inr', CURRENT_TIMESTAMP, CURRENT_TIMESTAMP)", UUID.randomUUID(), tenant);
        jdbc.update("INSERT INTO nx_integration_stores (id, tenant_id, store_code, store_name, platform, currency, timezone, external_domain, is_active, created_at, updated_at) "
                + "VALUES (?, ?, 'shop-live', 'Main shop', 'SHOPIFY', 'INR', 'Asia/Kolkata', 'main.myshopify.com', TRUE, CURRENT_TIMESTAMP, CURRENT_TIMESTAMP)", UUID.randomUUID(), tenant);
        jdbc.update("INSERT INTO nx_integration_stores (id, tenant_id, store_code, store_name, platform, is_active, created_at, updated_at) "
                + "VALUES (?, ?, 'amazon', 'Amazon', 'amazon', TRUE, CURRENT_TIMESTAMP, CURRENT_TIMESTAMP)", UUID.randomUUID(), tenant);
        jdbc.update("INSERT INTO products (id, tenant_id, sku, product_name, description, category, unit_price, cost_price, weight, image_url, is_active, created_at, updated_at) "
                + "VALUES (?, ?, 'BOOT-9', 'Chelsea Boot 9', 'Leather', 'Footwear', 129.50, 61.00, 1.2, 'https://cdn.example.com/boot.jpg', TRUE, CURRENT_TIMESTAMP, CURRENT_TIMESTAMP)", boots, tenant);
        jdbc.update("INSERT INTO products (id, tenant_id, sku, product_name, unit_price, is_active, created_at, updated_at) "
                + "VALUES (?, ?, 'SOCK-1', 'Socks', 9.00, TRUE, CURRENT_TIMESTAMP, CURRENT_TIMESTAMP)", UUID.randomUUID(), tenant);
    }

    @Test
    void copiesStoresAndProductsOnce() {
        ProductBackfill.Result first = backfill.run(tenant);
        assertEquals(2, first.storesCreated());
        assertEquals(2, first.productsCreated());
        assertEquals("INR", first.priceCurrency());

        assertEquals("WEB_SALES_CHANNEL", jdbc.queryForObject("SELECT default_sales_channel_enum_id FROM product_store WHERE product_store_id = 'SHOP-LIVE'", String.class));
        assertEquals("MARKETPLACE_SALES_CHANNEL", jdbc.queryForObject("SELECT default_sales_channel_enum_id FROM product_store WHERE product_store_id = 'AMAZON'", String.class));
        assertEquals("INR", jdbc.queryForObject("SELECT default_currency_uom_id FROM product_store WHERE product_store_id = 'AMAZON'", String.class), "a store without a currency takes the company's");

        String boot = products.findByIdentification("SKU", "BOOT-9").orElseThrow();
        assertEquals(boot, products.findByIdentification("NEXUS_LEGACY_ID", boots.toString()).orElseThrow());
        assertEquals(0, new BigDecimal("129.50").compareTo(products.price(boot, "DEFAULT_PRICE", "INR", null).orElseThrow()));
        assertEquals(0, new BigDecimal("61.00").compareTo(products.price(boot, "AVERAGE_COST", "INR", null).orElseThrow()));
        assertEquals("Footwear", jdbc.queryForObject("SELECT c.category_name FROM product p JOIN product_category c ON c.product_category_id = p.primary_product_category_id WHERE p.product_id = ?", String.class, boot));
        assertEquals("https://cdn.example.com/boot.jpg", jdbc.queryForObject("SELECT small_image_url FROM product WHERE product_id = ?", String.class, boot));

        ProductBackfill.Result second = backfill.run(tenant);
        assertEquals(0, second.storesCreated());
        assertEquals(0, second.productsCreated());
        assertEquals(2, second.productsSkipped());
    }

    @Test
    void refusesWithoutACompanyCurrency() {
        IllegalStateException e = assertThrows(IllegalStateException.class, () -> backfill.run(noCurrencyTenant));
        assertTrue(e.getMessage().startsWith("Set the company's default currency"));
        assertEquals("SHOP_LIVE.EU", ProductBackfill.storeId("shop live.eu", null));
    }
}
