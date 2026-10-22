package com.nexus.oms.coremigration;

import com.nexus.core.service.ProductService;
import com.nexus.core.service.ProductStoreService;
import jakarta.persistence.EntityManager;
import jakarta.persistence.FlushModeType;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.support.TransactionTemplate;

import java.math.BigDecimal;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

import static com.nexus.oms.coremigration.LegacyRows.LEGACY_ID;
import static com.nexus.oms.coremigration.LegacyRows.text;

/**
 * Copies one tenant's connected stores (nx_integration_stores) into product stores, and its products (products) into
 * the core product model: SKU and the old id as identifications, name, description, picture, weight, the price as
 * DEFAULT_PRICE and the cost as AVERAGE_COST in the company's default currency (legacy products carry no currency),
 * and the category by name. Safe to run again: anything already copied (by NEXUS_LEGACY_ID or store id) is skipped.
 */
@Service
public class ProductBackfill {

    private static final Logger log = LoggerFactory.getLogger(ProductBackfill.class);
    private static final int CHUNK_SIZE = 250;
    private static final int MAX_ID_LENGTH = 40;

    private final JdbcTemplate jdbc;
    private final ProductService productService;
    private final ProductStoreService productStoreService;
    private final TransactionTemplate transaction;
    private final EntityManager entityManager;

    public ProductBackfill(JdbcTemplate jdbc, ProductService productService, ProductStoreService productStoreService, TransactionTemplate transaction,
                           EntityManager entityManager) {
        this.jdbc = jdbc;
        this.productService = productService;
        this.productStoreService = productStoreService;
        this.transaction = transaction;
        this.entityManager = entityManager;
    }

    public record Result(int storesScanned, int storesCreated, int productsScanned, int productsCreated,
                         int productsSkipped, String priceCurrency) {
    }

    /** Products created and skipped in one chunk. */
    private record ChunkCounts(int created, int skipped) {
    }

    public Result run(UUID tenantId) {
        if (tenantId == null) {
            throw new IllegalArgumentException("Run the product copy as a user of the company whose products to copy");
        }
        String currency = companyCurrency(tenantId);

        // Step 1: stores.
        List<Map<String, Object>> legacyStores = jdbc.queryForList("SELECT store_code, store_name, platform, currency, timezone, "
                + "external_store_id, external_domain FROM nx_integration_stores WHERE tenant_id = ? ORDER BY created_at", tenantId);
        int storesCreated = transaction.execute(status -> copyStores(legacyStores, currency));

        // Step 2: products, in chunks of one transaction each. Read once: what is already copied, and the categories.
        List<Map<String, Object>> legacyProducts = jdbc.queryForList("SELECT id, sku, product_name, description, category, unit_price, "
                + "cost_price, weight, image_url FROM products WHERE tenant_id = ? ORDER BY created_at, id", tenantId);
        Set<String> copiedLegacyIds = new HashSet<>(jdbc.queryForList("SELECT id_value FROM good_identification "
                + "WHERE good_identification_type_id = ? AND thru_date IS NULL", String.class, LEGACY_ID));
        Map<String, String> categoryIdByName = new HashMap<>();
        jdbc.queryForList("SELECT category_name, product_category_id FROM product_category")
                .forEach(row -> categoryIdByName.putIfAbsent(String.valueOf(row.get("category_name")), String.valueOf(row.get("product_category_id"))));

        int created = 0;
        int skipped = 0;
        for (int start = 0; start < legacyProducts.size(); start += CHUNK_SIZE) {
            List<Map<String, Object>> chunk = legacyProducts.subList(start, Math.min(legacyProducts.size(), start + CHUNK_SIZE));
            ChunkCounts chunkCounts = transaction.execute(status -> {
                // Only new rows: nothing in a chunk needs to see the chunk's own unflushed rows, so flush once at commit.
                entityManager.setFlushMode(FlushModeType.COMMIT);
                return copyProducts(chunk, currency, copiedLegacyIds, categoryIdByName);
            });
            created += chunkCounts.created();
            skipped += chunkCounts.skipped();
        }
        log.info("Product copy for tenant {}: {} stores ({} new), {} products ({} new, {} already copied), prices in {}",
                tenantId, legacyStores.size(), storesCreated, legacyProducts.size(), created, skipped, currency);
        return new Result(legacyStores.size(), storesCreated, legacyProducts.size(), created, skipped, currency);
    }

    /** Creates each store not there yet; returns how many were new. */
    private int copyStores(List<Map<String, Object>> legacyStores, String companyCurrency) {
        int created = 0;
        for (Map<String, Object> legacyStore : legacyStores) {
            String storeId = storeId(text(legacyStore, "store_code"), text(legacyStore, "platform"));
            boolean existed = jdbc.queryForObject("SELECT COUNT(*) FROM product_store WHERE product_store_id = ?", Long.class, storeId) > 0;
            String storeCurrency = knownCurrencyOr(text(legacyStore, "currency"), companyCurrency);
            productStoreService.createIfMissing(new ProductStoreService.NewStore(storeId, text(legacyStore, "store_name"), storeCurrency,
                    text(legacyStore, "timezone"), channel(text(legacyStore, "platform")), text(legacyStore, "external_store_id"),
                    text(legacyStore, "external_domain")));
            if (!existed) {
                created++;
            }
        }
        return created;
    }

    private ChunkCounts copyProducts(List<Map<String, Object>> chunk, String currency, Set<String> copiedLegacyIds, Map<String, String> categoryIdByName) {
        int created = 0;
        int skipped = 0;
        for (Map<String, Object> legacyProduct : chunk) {
            String legacyId = String.valueOf(legacyProduct.get("id"));
            boolean firstTimeSeen = copiedLegacyIds.add(legacyId);
            if (!firstTimeSeen) {
                skipped++;
                continue;
            }
            String sku = text(legacyProduct, "sku");
            ProductService.NewProduct newProduct = new ProductService.NewProduct("FINISHED_GOOD", text(legacyProduct, "product_name"), sku,
                    text(legacyProduct, "description"), text(legacyProduct, "image_url"), (BigDecimal) legacyProduct.get("weight"), "WT_kg", false, false);
            String productId = productService.create(newProduct);
            productService.setIdentification(productId, LEGACY_ID, legacyId);
            productService.setIdentification(productId, "SKU", sku);
            productService.setPrice(productId, "DEFAULT_PRICE", currency, null, (BigDecimal) legacyProduct.get("unit_price"));
            productService.setPrice(productId, "AVERAGE_COST", currency, null, (BigDecimal) legacyProduct.get("cost_price"));
            String category = text(legacyProduct, "category");
            if (category != null) {
                String categoryId = categoryIdByName.computeIfAbsent(category.trim(), productService::category);
                productService.addToCategory(productId, categoryId, true);
            }
            created++;
        }
        return new ChunkCounts(created, skipped);
    }

    /** The company's default currency; products have none of their own, so without it nothing can be priced. */
    private String companyCurrency(UUID tenantId) {
        List<String> currencies = jdbc.queryForList("SELECT default_currency FROM nx_company_settings WHERE tenant_id = ? "
                + "AND default_currency IS NOT NULL", String.class, tenantId);
        String currency = currencies.isEmpty() ? null : knownCurrencyOr(currencies.get(0), null);
        if (currency == null) {
            throw new IllegalStateException("Set the company's default currency in Settings first; legacy products have no currency of their own");
        }
        return currency;
    }

    /** The code, upper case, when the core knows it as a currency; the fallback otherwise. */
    private String knownCurrencyOr(String code, String fallback) {
        if (code == null || code.isBlank()) {
            return fallback;
        }
        String currency = code.trim().toUpperCase(Locale.ROOT);
        Long known = jdbc.queryForObject("SELECT COUNT(*) FROM uom WHERE uom_id = ? AND uom_type_id = 'CURRENCY_MEASURE'", Long.class, currency);
        return known != null && known > 0 ? currency : fallback;
    }

    /** Store ids are the old store codes (or the platform), cleaned to the allowed characters and cut to 40. */
    static String storeId(String storeCode, String platform) {
        String base;
        if (storeCode != null && !storeCode.isBlank()) {
            base = storeCode;
        } else {
            base = platform == null ? "STORE" : platform;
        }
        String cleaned = base.trim().toUpperCase(Locale.ROOT).replaceAll("[^A-Z0-9_.-]", "_");
        return cleaned.length() > MAX_ID_LENGTH ? cleaned.substring(0, MAX_ID_LENGTH) : cleaned;
    }

    static String channel(String platform) {
        if (platform == null) {
            return "UNKNWN_SALES_CHANNEL";
        }
        return switch (platform.trim().toUpperCase(Locale.ROOT)) {
            case "SHOPIFY", "BIGCOMMERCE", "WOOCOMMERCE", "MAGENTO", "SALESFORCE_COMMERCE" -> "WEB_SALES_CHANNEL";
            case "AMAZON", "EBAY", "WALMART", "FLIPKART" -> "MARKETPLACE_SALES_CHANNEL";
            case "POS", "SHOPIFY_POS" -> "POS_SALES_CHANNEL";
            default -> "UNKNWN_SALES_CHANNEL";
        };
    }
}
