package com.nexus.core.service;

import com.nexus.core.model.product.GoodIdentification;
import com.nexus.core.model.product.Product;
import com.nexus.core.model.product.ProductAssoc;
import com.nexus.core.model.product.ProductCategory;
import com.nexus.core.model.product.ProductCategoryMember;
import com.nexus.core.model.product.ProductFeature;
import com.nexus.core.model.product.ProductFeatureAppl;
import com.nexus.core.model.product.ProductPrice;
import jakarta.persistence.EntityManager;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

/**
 * Products (OFBiz product model): ids of every kind (SKU, barcode, channel ids) in {@code good_identification},
 * variants and substitutes and kit components in {@code product_assoc}, size and colour as features, categories,
 * and dated prices per currency and store group.
 */
@Service("coreProductService")
@Transactional
public class ProductService {

    public static final String ALL_STORES = "_NA_";
    public static final String PURCHASE = "PURCHASE";

    private final EntityManager em;
    private final SequenceService sequences;

    public ProductService(EntityManager em, SequenceService sequences) {
        this.em = em;
        this.sequences = sequences;
    }

    public record NewProduct(String productTypeId, String productName, String internalName, String description,
                             String imageUrl, BigDecimal weight, String weightUomId, Boolean virtual, Boolean variant) {
    }

    public String create(NewProduct n) {
        Product p = new Product();
        p.setProductId(sequences.nextId("Product"));
        p.setProductTypeId(n.productTypeId() == null ? "FINISHED_GOOD" : n.productTypeId());
        p.setProductName(trim(n.productName()));
        p.setInternalName(trim(n.internalName()));
        p.setDescription(trim(n.description()));
        String img = trim(n.imageUrl());
        p.setSmallImageUrl(img);
        p.setMediumImageUrl(img);
        p.setLargeImageUrl(img);
        p.setOriginalImageUrl(img);
        p.setProductWeight(n.weight());
        p.setWeightUomId(n.weight() == null ? null : (n.weightUomId() == null ? "WT_kg" : n.weightUomId()));
        p.setIsVirtual(Boolean.TRUE.equals(n.virtual()));
        p.setIsVariant(Boolean.TRUE.equals(n.variant()));
        p.setRequireInventory(true);
        p.setReturnable(true);
        p.setTaxable(true);
        p.setChargeShipping(true);
        p.setIntroductionDate(CoreClock.now());
        em.persist(p);
        return p.getProductId();
    }

    /** Sets the current id of a kind (ending any earlier one); a blank value does nothing. */
    public void setIdentification(String productId, String typeId, String value) {
        if (value == null || value.isBlank()) return;
        List<GoodIdentification> current = em.createQuery("SELECT g FROM GoodIdentification g WHERE g.productId = :p "
                        + "AND g.goodIdentificationTypeId = :t AND g.thruDate IS NULL", GoodIdentification.class)
                .setParameter("p", productId).setParameter("t", typeId).getResultList();
        if (current.stream().anyMatch(g -> g.getIdValue().equals(value.trim()))) return;
        LocalDateTime now = CoreClock.now();
        current.forEach(g -> g.setThruDate(now));
        GoodIdentification g = new GoodIdentification();
        g.setGoodIdentificationTypeId(typeId);
        g.setProductId(productId);
        g.setFromDate(now);
        g.setIdValue(value.trim());
        em.persist(g);
    }

    /** The product whose current id of this kind is the value (SKU, barcode, channel id). */
    @Transactional(readOnly = true)
    public Optional<String> findByIdentification(String typeId, String value) {
        if (value == null || value.isBlank()) return Optional.empty();
        return em.createQuery("SELECT g.productId FROM GoodIdentification g WHERE g.goodIdentificationTypeId = :t "
                        + "AND g.idValue = :v AND g.thruDate IS NULL ORDER BY g.fromDate DESC", String.class)
                .setParameter("t", typeId).setParameter("v", value.trim()).setMaxResults(1).getResultList().stream().findFirst();
    }

    /** Links two products (variant, substitute, kit component) from now on; an existing current link is kept. */
    public void associate(String productId, String productIdTo, String assocTypeId, Long sequenceNum, BigDecimal quantity) {
        if (productId.equals(productIdTo)) throw new IllegalArgumentException("A product can't be linked to itself");
        if (!associated(productId, assocTypeId).contains(productIdTo)) {
            ProductAssoc a = new ProductAssoc();
            a.setProductId(productId);
            a.setProductIdTo(productIdTo);
            a.setProductAssocTypeId(assocTypeId);
            a.setFromDate(CoreClock.now());
            a.setSequenceNum(sequenceNum);
            a.setQuantity(quantity);
            em.persist(a);
        }
    }

    /** Current links of a kind from a product, in sequence order: its variants, substitutes or components. */
    @Transactional(readOnly = true)
    public List<String> associated(String productId, String assocTypeId) {
        LocalDateTime now = CoreClock.now();
        return em.createQuery("SELECT a.productIdTo FROM ProductAssoc a WHERE a.productId = :p AND a.productAssocTypeId = :t "
                        + "AND a.fromDate <= :now AND (a.thruDate IS NULL OR a.thruDate > :now) ORDER BY a.sequenceNum, a.fromDate", String.class)
                .setParameter("p", productId).setParameter("t", assocTypeId).setParameter("now", now).getResultList();
    }

    /** The feature (e.g. SIZE "9"), created once per type and code. */
    public String feature(String featureTypeId, String idCode, String description) {
        List<String> found = em.createQuery("SELECT f.productFeatureId FROM ProductFeature f WHERE f.productFeatureTypeId = :t AND f.idCode = :c", String.class)
                .setParameter("t", featureTypeId).setParameter("c", idCode).getResultList();
        if (!found.isEmpty()) return found.get(0);
        ProductFeature f = new ProductFeature();
        f.setProductFeatureId(sequences.nextId("ProductFeature"));
        f.setProductFeatureTypeId(featureTypeId);
        f.setIdCode(idCode);
        f.setDescription(description == null ? idCode : description);
        em.persist(f);
        return f.getProductFeatureId();
    }

    public void applyFeature(String productId, String productFeatureId, String applTypeId) {
        ProductFeatureAppl a = new ProductFeatureAppl();
        a.setProductId(productId);
        a.setProductFeatureId(productFeatureId);
        a.setFromDate(CoreClock.now());
        a.setProductFeatureApplTypeId(applTypeId);
        em.persist(a);
    }

    /** The category with this name, created when missing. */
    public String category(String name) {
        List<String> found = em.createQuery("SELECT c.productCategoryId FROM ProductCategory c WHERE c.categoryName = :n", String.class)
                .setParameter("n", name.trim()).getResultList();
        if (!found.isEmpty()) return found.get(0);
        ProductCategory c = new ProductCategory();
        c.setProductCategoryId(sequences.nextId("ProductCategory"));
        c.setProductCategoryTypeId("CATALOG_CATEGORY");
        c.setCategoryName(name.trim());
        em.persist(c);
        return c.getProductCategoryId();
    }

    public void addToCategory(String productId, String productCategoryId, boolean primary) {
        ProductCategoryMember m = new ProductCategoryMember();
        m.setProductCategoryId(productCategoryId);
        m.setProductId(productId);
        m.setFromDate(CoreClock.now());
        em.persist(m);
        if (primary) em.find(Product.class, productId).setPrimaryProductCategoryId(productCategoryId);
    }

    /** Sets a price from now on, ending the current one of the same type, currency and store group. */
    public void setPrice(String productId, String priceTypeId, String currencyUomId, String storeGroupId, BigDecimal price) {
        if (price == null) return;
        if (price.signum() < 0) throw new IllegalArgumentException("A price can't be negative");
        String group = storeGroupId == null ? ALL_STORES : storeGroupId;
        LocalDateTime now = CoreClock.now();
        currentPrices(productId, priceTypeId, currencyUomId, group).forEach(p -> p.setThruDate(now));
        ProductPrice p = new ProductPrice();
        p.setProductId(productId);
        p.setProductPriceTypeId(priceTypeId);
        p.setProductPricePurposeId(PURCHASE);
        p.setCurrencyUomId(currencyUomId);
        p.setProductStoreGroupId(group);
        p.setFromDate(now);
        p.setPrice(price);
        em.persist(p);
    }

    /** The current price: the store group's own price, else the all-stores price. */
    @Transactional(readOnly = true)
    public Optional<BigDecimal> price(String productId, String priceTypeId, String currencyUomId, String storeGroupId) {
        if (storeGroupId != null && !ALL_STORES.equals(storeGroupId)) {
            Optional<BigDecimal> own = currentPrices(productId, priceTypeId, currencyUomId, storeGroupId).stream().map(ProductPrice::getPrice).findFirst();
            if (own.isPresent()) return own;
        }
        return currentPrices(productId, priceTypeId, currencyUomId, ALL_STORES).stream().map(ProductPrice::getPrice).findFirst();
    }

    private List<ProductPrice> currentPrices(String productId, String typeId, String currency, String group) {
        return em.createQuery("SELECT p FROM ProductPrice p WHERE p.productId = :id AND p.productPriceTypeId = :t AND p.currencyUomId = :c "
                        + "AND p.productStoreGroupId = :g AND p.thruDate IS NULL ORDER BY p.fromDate DESC", ProductPrice.class)
                .setParameter("id", productId).setParameter("t", typeId).setParameter("c", currency).setParameter("g", group).getResultList();
    }

    private static String trim(String s) {
        return s == null || s.isBlank() ? null : s.trim();
    }
}
