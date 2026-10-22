package com.nexus.core;

import org.springframework.beans.factory.annotation.Qualifier;
import com.nexus.core.CoreTransactional;
import com.nexus.core.model.product.Product;
import com.nexus.core.service.ProductService;
import com.nexus.core.service.ProductStoreService;
import jakarta.persistence.EntityManager;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestInstance;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.transaction.support.TransactionTemplate;

import java.math.BigDecimal;
import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;

@SpringBootTest(classes = com.nexus.oms.NexusOmsApplication.class)
@ActiveProfiles("test")
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
class ProductServiceTest {

    @Autowired ProductService products;
    @Autowired ProductStoreService stores;
    @Autowired EntityManager em;
    @Autowired @Qualifier(CoreTransactional.TEMPLATE) TransactionTemplate tx;
    @Autowired JdbcTemplate jdbc;

    @BeforeAll
    void seed() {
        CoreTestData.ensureSeeded(jdbc);
    }

    private String product(String name, boolean virtual, boolean variant) {
        return tx.execute(s -> products.create(new ProductService.NewProduct(null, name, null, null, "https://cdn.example.com/" + name + ".jpg",
                new BigDecimal("0.8"), null, virtual, variant)));
    }

    @Test
    void aStyleWithSizeVariantsASubstituteAndIds() {
        String style = product("Runner", true, false);
        String size9 = product("Runner 9", false, true);
        String size10 = product("Runner 10", false, true);
        String other = product("Trail Runner 9", false, false);
        tx.executeWithoutResult(s -> {
            products.associate(style, size9, "PRODUCT_VARIANT", 1L, null);
            products.associate(style, size10, "PRODUCT_VARIANT", 2L, null);
            products.associate(style, size10, "PRODUCT_VARIANT", 2L, null);   // already linked: kept once
            products.associate(size9, other, "PRODUCT_SUBSTITUTE", 1L, null);
            products.applyFeature(size9, products.feature("SIZE", "9", "Size 9"), "DISTINGUISHING_FEAT");
            products.applyFeature(size10, products.feature("SIZE", "10", null), "DISTINGUISHING_FEAT");
            products.setIdentification(size9, "SKU", "RUN-9");
            products.setIdentification(size9, "UPCA", "012345678905");
        });
        assertEquals(List.of(size9, size10), products.associated(style, "PRODUCT_VARIANT"));
        assertEquals(List.of(other), products.associated(size9, "PRODUCT_SUBSTITUTE"));
        assertEquals(Optional.of(size9), products.findByIdentification("SKU", "RUN-9"));
        assertEquals(Optional.of(size9), products.findByIdentification("UPCA", "012345678905"));
        assertEquals(products.feature("SIZE", "9", null), tx.execute(s -> products.feature("SIZE", "9", null)), "a feature is created once");
        Product p = em.find(Product.class, size9);
        assertTrue(p.getIsVariant());
        assertEquals("FINISHED_GOOD", p.getProductTypeId());
        assertEquals("WT_kg", p.getWeightUomId());
        assertThrows(IllegalArgumentException.class, () -> tx.executeWithoutResult(s -> products.associate(style, style, "PRODUCT_VARIANT", 1L, null)));
    }

    @Test
    void changingASkuEndsTheOldOne() {
        String id = product("Cap", false, false);
        tx.executeWithoutResult(s -> products.setIdentification(id, "SKU", "CAP-OLD"));
        tx.executeWithoutResult(s -> products.setIdentification(id, "SKU", "CAP-NEW"));
        assertEquals(Optional.empty(), products.findByIdentification("SKU", "CAP-OLD"));
        assertEquals(Optional.of(id), products.findByIdentification("SKU", "CAP-NEW"));
        assertEquals(2, jdbc.queryForObject("SELECT COUNT(*) FROM good_identification WHERE product_id = ?", Long.class, id), "the old SKU is kept, ended");
    }

    @Test
    void pricesAreDatedPerCurrencyAndStoreGroup() {
        String id = product("Tote", false, false);
        tx.executeWithoutResult(s -> {
            jdbc.update("INSERT INTO product_store_group (product_store_group_id, product_store_group_name) VALUES ('OUTLET', 'Outlet stores')");
            products.setPrice(id, "DEFAULT_PRICE", "INR", null, new BigDecimal("1999"));
            products.setPrice(id, "DEFAULT_PRICE", "INR", null, new BigDecimal("1799"));
            products.setPrice(id, "DEFAULT_PRICE", "INR", "OUTLET", new BigDecimal("1499"));
            products.setPrice(id, "DEFAULT_PRICE", "USD", null, new BigDecimal("24"));
        });
        assertEquals(0, new BigDecimal("1799").compareTo(products.price(id, "DEFAULT_PRICE", "INR", null).orElseThrow()));
        assertEquals(0, new BigDecimal("1499").compareTo(products.price(id, "DEFAULT_PRICE", "INR", "OUTLET").orElseThrow()));
        assertEquals(0, new BigDecimal("1799").compareTo(products.price(id, "DEFAULT_PRICE", "INR", "NO_SUCH_GROUP").orElseThrow()), "falls back to all stores");
        assertEquals(0, new BigDecimal("24").compareTo(products.price(id, "DEFAULT_PRICE", "USD", null).orElseThrow()));
        assertEquals(Optional.empty(), products.price(id, "LIST_PRICE", "INR", null));
        assertThrows(IllegalArgumentException.class, () -> tx.executeWithoutResult(s -> products.setPrice(id, "DEFAULT_PRICE", "INR", null, new BigDecimal("-1"))));
    }

    @Test
    void categoriesAreCreatedOnceByName() {
        String id = product("Scarf", false, false);
        String cat = tx.execute(s -> products.category("Accessories"));
        assertEquals(cat, tx.execute(s -> products.category(" Accessories ")));
        tx.executeWithoutResult(s -> products.addToCategory(id, cat, true));
        assertEquals(cat, em.find(Product.class, id).getPrimaryProductCategoryId());
    }

    @Test
    void storesAndTheirSettings() {
        tx.executeWithoutResult(s -> stores.createIfMissing(new ProductStoreService.NewStore("BRAND_A", "Brand A", "INR", "Asia/Kolkata",
                "WEB_SALES_CHANNEL", null, "brand-a.myshopify.com")));
        assertEquals("BRAND_A", tx.execute(s -> stores.createIfMissing(new ProductStoreService.NewStore("BRAND_A", "Renamed", null, null, null, null, null))),
                "an existing store is returned unchanged");
        tx.executeWithoutResult(s -> stores.setSetting("BRAND_A", "RELEASE_DAYS_B2B", "3"));
        assertEquals(3, stores.settingLong("BRAND_A", "RELEASE_DAYS_B2B", 1));
        assertEquals(1, stores.settingLong("BRAND_A", "RELEASE_DAYS_B2C", 1));
        assertThrows(IllegalArgumentException.class, () -> tx.executeWithoutResult(s -> stores.setSetting("BRAND_A", "NOT_A_SETTING", "1")));
        assertThrows(IllegalArgumentException.class, () -> tx.executeWithoutResult(s -> stores.createIfMissing(
                new ProductStoreService.NewStore("bad id!", "x", null, null, null, null, null))));
        assertThrows(IllegalArgumentException.class, () -> tx.executeWithoutResult(s -> stores.createIfMissing(
                new ProductStoreService.NewStore("BRAND_B", "x", null, null, "FAX_SALES_CHANNEL", null, null))));
        assertEquals("Brand A", jdbc.queryForObject("SELECT store_name FROM product_store WHERE product_store_id = 'BRAND_A'", String.class));
    }
}
