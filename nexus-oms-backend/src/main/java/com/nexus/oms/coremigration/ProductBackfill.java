package com.nexus.oms.coremigration;

import com.nexus.core.service.ProductService;
import com.nexus.core.service.ProductStoreService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import jakarta.persistence.EntityManager;
import jakarta.persistence.FlushModeType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.support.TransactionTemplate;

import java.math.BigDecimal;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.Locale;
import java.util.Map;
import java.util.UUID;

/**
 * Copies one tenant's connected stores (nx_integration_stores) into product stores, and its products (products) into
 * the core product model: SKU and the old id as identifications, name, description, picture, weight, the price as
 * DEFAULT_PRICE and the cost as AVERAGE_COST in the company's default currency (legacy products carry no currency),
 * and the category by name. Safe to run again: anything already copied (by NEXUS_LEGACY_ID or store id) is skipped.
 */
@Service
public class ProductBackfill {

    private static final Logger log = LoggerFactory.getLogger(ProductBackfill.class);
    static final String LEGACY_ID = "NEXUS_LEGACY_ID";
    private static final int CHUNK = 250;

    private final JdbcTemplate jdbc;
    private final ProductService products;
    private final ProductStoreService stores;
    private final TransactionTemplate tx;
    private final EntityManager em;

    public ProductBackfill(JdbcTemplate jdbc, ProductService products, ProductStoreService stores, TransactionTemplate tx, EntityManager em) {
        this.jdbc = jdbc;
        this.products = products;
        this.stores = stores;
        this.tx = tx;
        this.em = em;
    }

    public record Result(int storesScanned, int storesCreated, int productsScanned, int productsCreated,
                         int productsSkipped, String priceCurrency) {
    }

    public Result run(UUID tenantId) {
        if (tenantId == null) throw new IllegalArgumentException("Run the product copy as a user of the company whose products to copy");
        String currency = companyCurrency(tenantId);

        List<Map<String, Object>> legacyStores = jdbc.queryForList("SELECT store_code, store_name, platform, currency, timezone, "
                + "external_store_id, external_domain FROM nx_integration_stores WHERE tenant_id = ? ORDER BY created_at", tenantId);
        int storesCreated = tx.execute(s -> {
            int n = 0;
            for (Map<String, Object> r : legacyStores) {
                String id = storeId(str(r, "store_code"), str(r, "platform"));
                boolean existed = jdbc.queryForObject("SELECT COUNT(*) FROM product_store WHERE product_store_id = ?", Long.class, id) > 0;
                stores.createIfMissing(new ProductStoreService.NewStore(id, str(r, "store_name"), currencyOrNull(str(r, "currency"), currency),
                        str(r, "timezone"), channel(str(r, "platform")), str(r, "external_store_id"), str(r, "external_domain")));
                if (!existed) n++;
            }
            return n;
        });

        List<Map<String, Object>> rows = jdbc.queryForList("SELECT id, sku, product_name, description, category, unit_price, "
                + "cost_price, weight, image_url FROM products WHERE tenant_id = ? ORDER BY created_at, id", tenantId);
        // Read once instead of querying per row: what is already copied, and the categories that exist.
        Set<String> copied = new HashSet<>(jdbc.queryForList("SELECT id_value FROM good_identification "
                + "WHERE good_identification_type_id = ? AND thru_date IS NULL", String.class, LEGACY_ID));
        Map<String, String> categories = new HashMap<>();
        jdbc.queryForList("SELECT category_name, product_category_id FROM product_category")
                .forEach(c -> categories.putIfAbsent(String.valueOf(c.get("category_name")), String.valueOf(c.get("product_category_id"))));
        int created = 0, skipped = 0;
        for (int from = 0; from < rows.size(); from += CHUNK) {
            List<Map<String, Object>> chunk = rows.subList(from, Math.min(rows.size(), from + CHUNK));
            int[] c = tx.execute(s -> {
                // new rows only: nothing in a chunk needs to see the chunk's own unflushed rows, so flush once at commit
                em.setFlushMode(FlushModeType.COMMIT);
                return copy(chunk, currency, copied, categories);
            });
            created += c[0];
            skipped += c[1];
        }
        log.info("Product copy for tenant {}: {} stores ({} new), {} products ({} new, {} already copied), prices in {}",
                tenantId, legacyStores.size(), storesCreated, rows.size(), created, skipped, currency);
        return new Result(legacyStores.size(), storesCreated, rows.size(), created, skipped, currency);
    }

    private int[] copy(List<Map<String, Object>> chunk, String currency, Set<String> copied, Map<String, String> categories) {
        int created = 0, skipped = 0;
        for (Map<String, Object> r : chunk) {
            String legacyId = String.valueOf(r.get("id"));
            if (!copied.add(legacyId)) {
                skipped++;
                continue;
            }
            String productId = products.create(new ProductService.NewProduct("FINISHED_GOOD", str(r, "product_name"), str(r, "sku"),
                    str(r, "description"), str(r, "image_url"), (BigDecimal) r.get("weight"), "WT_kg", false, false));
            products.setIdentification(productId, LEGACY_ID, legacyId);
            products.setIdentification(productId, "SKU", str(r, "sku"));
            products.setPrice(productId, "DEFAULT_PRICE", currency, null, (BigDecimal) r.get("unit_price"));
            products.setPrice(productId, "AVERAGE_COST", currency, null, (BigDecimal) r.get("cost_price"));
            String category = str(r, "category");
            if (category != null) {
                String categoryId = categories.computeIfAbsent(category.trim(), products::category);
                products.addToCategory(productId, categoryId, true);
            }
            created++;
        }
        return new int[]{created, skipped};
    }

    private String companyCurrency(UUID tenantId) {
        List<String> c = jdbc.queryForList("SELECT default_currency FROM nx_company_settings WHERE tenant_id = ? AND default_currency IS NOT NULL", String.class, tenantId);
        String cur = c.isEmpty() ? null : currencyOrNull(c.get(0), null);
        if (cur == null) throw new IllegalStateException("Set the company's default currency in Settings first; legacy products have no currency of their own");
        return cur;
    }

    private String currencyOrNull(String code, String fallback) {
        if (code == null || code.isBlank()) return fallback;
        String c = code.trim().toUpperCase(Locale.ROOT);
        Long known = jdbc.queryForObject("SELECT COUNT(*) FROM uom WHERE uom_id = ? AND uom_type_id = 'CURRENCY_MEASURE'", Long.class, c);
        return known != null && known > 0 ? c : fallback;
    }

    /** Store ids are the old store codes, cleaned to the allowed characters. */
    static String storeId(String storeCode, String platform) {
        String base = storeCode != null && !storeCode.isBlank() ? storeCode : platform == null ? "STORE" : platform;
        String id = base.trim().toUpperCase(Locale.ROOT).replaceAll("[^A-Z0-9_.-]", "_");
        return id.length() > 40 ? id.substring(0, 40) : id;
    }

    static String channel(String platform) {
        if (platform == null) return "UNKNWN_SALES_CHANNEL";
        return switch (platform.trim().toUpperCase(Locale.ROOT)) {
            case "SHOPIFY", "BIGCOMMERCE", "WOOCOMMERCE", "MAGENTO", "SALESFORCE_COMMERCE" -> "WEB_SALES_CHANNEL";
            case "AMAZON", "EBAY", "WALMART", "FLIPKART" -> "MARKETPLACE_SALES_CHANNEL";
            case "POS", "SHOPIFY_POS" -> "POS_SALES_CHANNEL";
            default -> "UNKNWN_SALES_CHANNEL";
        };
    }

    private static String str(Map<String, Object> r, String key) {
        Object v = r.get(key);
        return v == null || String.valueOf(v).isBlank() ? null : String.valueOf(v);
    }
}
