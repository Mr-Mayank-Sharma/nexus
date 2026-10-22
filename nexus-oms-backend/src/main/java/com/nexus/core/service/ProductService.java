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
import com.nexus.core.CoreTransactional;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Locale;
import java.util.Optional;

/**
 * Products (OFBiz product model): ids of every kind (SKU, barcode, channel ids) in {@code good_identification},
 * variants and substitutes and kit components in {@code product_assoc}, size and colour as features, categories,
 * and dated prices per currency and store group.
 */
@Service("coreProductService")
@CoreTransactional
public class ProductService {

    /** The store group a price applies to when it applies to every store. */
    public static final String ALL_STORES = "_NA_";
    public static final String PURCHASE = "PURCHASE";
    static final String DEFAULT_PRODUCT_TYPE = "FINISHED_GOOD";
    static final String DEFAULT_WEIGHT_UNIT = "WT_kg";
    static final String CATALOG_CATEGORY = "CATALOG_CATEGORY";

    private final EntityManager entityManager;
    private final SequenceService sequenceService;

    public ProductService(EntityManager entityManager, SequenceService sequenceService) {
        this.entityManager = entityManager;
        this.sequenceService = sequenceService;
    }

    public record NewProduct(String productTypeId, String productName, String internalName, String description,
                             String imageUrl, BigDecimal weight, String weightUomId, Boolean virtual, Boolean variant) {
    }

    // ── products ──

    /** Creates a product (a finished good unless a type is given) that needs stock, can be returned, and is taxed and shipped. */
    public String create(NewProduct newProduct) {
        Product product = new Product();
        product.setProductId(sequenceService.nextId("Product"));
        product.setProductTypeId(newProduct.productTypeId() == null ? DEFAULT_PRODUCT_TYPE : newProduct.productTypeId());
        product.setProductName(blankToNull(newProduct.productName()));
        product.setInternalName(blankToNull(newProduct.internalName()));
        product.setDescription(blankToNull(newProduct.description()));
        String imageUrl = blankToNull(newProduct.imageUrl());
        product.setSmallImageUrl(imageUrl);
        product.setMediumImageUrl(imageUrl);
        product.setLargeImageUrl(imageUrl);
        product.setOriginalImageUrl(imageUrl);
        product.setProductWeight(newProduct.weight());
        String weightUnit = newProduct.weightUomId() == null ? DEFAULT_WEIGHT_UNIT : newProduct.weightUomId();
        product.setWeightUomId(newProduct.weight() == null ? null : weightUnit);
        product.setIsVirtual(Boolean.TRUE.equals(newProduct.virtual()));
        product.setIsVariant(Boolean.TRUE.equals(newProduct.variant()));
        product.setRequireInventory(true);
        product.setReturnable(true);
        product.setTaxable(true);
        product.setChargeShipping(true);
        product.setIntroductionDate(CoreClock.now());
        entityManager.persist(product);
        return product.getProductId();
    }

    // ── ids ──

    /** Sets the current id of a kind (ending any earlier one); a blank value does nothing. */
    public void setIdentification(String productId, String typeId, String value) {
        if (value == null || value.isBlank()) {
            return;
        }
        String trimmedValue = value.trim();
        List<GoodIdentification> current = entityManager.createQuery("SELECT g FROM GoodIdentification g WHERE g.productId = :productId "
                        + "AND g.goodIdentificationTypeId = :typeId AND g.thruDate IS NULL", GoodIdentification.class)
                .setParameter("productId", productId)
                .setParameter("typeId", typeId)
                .getResultList();
        boolean alreadySet = current.stream().anyMatch(identification -> identification.getIdValue().equals(trimmedValue));
        if (alreadySet) {
            return;
        }
        LocalDateTime now = CoreClock.now();
        current.forEach(identification -> identification.setThruDate(now));

        GoodIdentification identification = new GoodIdentification();
        identification.setGoodIdentificationTypeId(typeId);
        identification.setProductId(productId);
        identification.setFromDate(now);
        identification.setIdValue(trimmedValue);
        entityManager.persist(identification);
    }

    /** The product whose current id of this kind is the value (SKU, barcode, channel id); newest first when several share it. */
    @CoreTransactional(readOnly = true)
    public Optional<String> findByIdentification(String typeId, String value) {
        if (value == null || value.isBlank()) {
            return Optional.empty();
        }
        return entityManager.createQuery("SELECT g.productId FROM GoodIdentification g WHERE g.goodIdentificationTypeId = :typeId "
                        + "AND g.idValue = :value AND g.thruDate IS NULL ORDER BY g.fromDate DESC", String.class)
                .setParameter("typeId", typeId)
                .setParameter("value", value.trim())
                .setMaxResults(1)
                .getResultList()
                .stream()
                .findFirst();
    }

    /** A product as a picker shows it. */
    public record ProductRef(String productId, String productName, String sku) {
    }

    /** Products whose name or current SKU contains the text (any case), by name. */
    @CoreTransactional(readOnly = true)
    public List<ProductRef> search(String text, int limit) {
        String pattern = "%" + (text == null ? "" : text.trim().toLowerCase(Locale.ROOT)) + "%";
        return entityManager.createQuery("SELECT p.productId, p.productName, g.idValue FROM Product p, GoodIdentification g WHERE g.productId = p.productId "
                        + "AND g.goodIdentificationTypeId = 'SKU' AND g.thruDate IS NULL AND (LOWER(p.productName) LIKE :pattern OR LOWER(g.idValue) LIKE :pattern) "
                        + "ORDER BY p.productName", Object[].class)
                .setParameter("pattern", pattern)
                .setMaxResults(limit)
                .getResultList()
                .stream()
                .map(row -> new ProductRef((String) row[0], (String) row[1], (String) row[2]))
                .toList();
    }

    // ── links between products ──

    /** Links two products (variant, substitute, kit component) from now on; an existing current link is kept. */
    public void associate(String productId, String productIdTo, String assocTypeId, Long sequenceNum, BigDecimal quantity) {
        if (productId.equals(productIdTo)) {
            throw new IllegalArgumentException("A product can't be linked to itself");
        }
        if (associated(productId, assocTypeId).contains(productIdTo)) {
            return;
        }
        ProductAssoc link = new ProductAssoc();
        link.setProductId(productId);
        link.setProductIdTo(productIdTo);
        link.setProductAssocTypeId(assocTypeId);
        link.setFromDate(CoreClock.now());
        link.setSequenceNum(sequenceNum);
        link.setQuantity(quantity);
        entityManager.persist(link);
    }

    /** Current links of a kind from a product, in sequence order: its variants, substitutes or components. */
    @CoreTransactional(readOnly = true)
    public List<String> associated(String productId, String assocTypeId) {
        LocalDateTime now = CoreClock.now();
        return entityManager.createQuery("SELECT a.productIdTo FROM ProductAssoc a WHERE a.productId = :productId AND a.productAssocTypeId = :typeId "
                        + "AND a.fromDate <= :now AND (a.thruDate IS NULL OR a.thruDate > :now) ORDER BY a.sequenceNum, a.fromDate", String.class)
                .setParameter("productId", productId)
                .setParameter("typeId", assocTypeId)
                .setParameter("now", now)
                .getResultList();
    }

    // ── features and categories ──

    /** The feature (e.g. SIZE "9"), created once per type and code. */
    public String feature(String featureTypeId, String idCode, String description) {
        List<String> existing = entityManager.createQuery("SELECT f.productFeatureId FROM ProductFeature f WHERE f.productFeatureTypeId = :typeId "
                        + "AND f.idCode = :code", String.class)
                .setParameter("typeId", featureTypeId)
                .setParameter("code", idCode)
                .getResultList();
        if (!existing.isEmpty()) {
            return existing.get(0);
        }
        ProductFeature feature = new ProductFeature();
        feature.setProductFeatureId(sequenceService.nextId("ProductFeature"));
        feature.setProductFeatureTypeId(featureTypeId);
        feature.setIdCode(idCode);
        feature.setDescription(description == null ? idCode : description);
        entityManager.persist(feature);
        return feature.getProductFeatureId();
    }

    /** Gives the product a feature, from now on. */
    public void applyFeature(String productId, String productFeatureId, String applTypeId) {
        ProductFeatureAppl application = new ProductFeatureAppl();
        application.setProductId(productId);
        application.setProductFeatureId(productFeatureId);
        application.setFromDate(CoreClock.now());
        application.setProductFeatureApplTypeId(applTypeId);
        entityManager.persist(application);
    }

    /** The category with this name, created when missing. */
    public String category(String name) {
        List<String> existing = entityManager.createQuery("SELECT c.productCategoryId FROM ProductCategory c WHERE c.categoryName = :name", String.class)
                .setParameter("name", name.trim())
                .getResultList();
        if (!existing.isEmpty()) {
            return existing.get(0);
        }
        ProductCategory category = new ProductCategory();
        category.setProductCategoryId(sequenceService.nextId("ProductCategory"));
        category.setProductCategoryTypeId(CATALOG_CATEGORY);
        category.setCategoryName(name.trim());
        entityManager.persist(category);
        return category.getProductCategoryId();
    }

    /** Puts the product in a category from now on; {@code primary} also makes it the product's main category. */
    public void addToCategory(String productId, String productCategoryId, boolean primary) {
        ProductCategoryMember member = new ProductCategoryMember();
        member.setProductCategoryId(productCategoryId);
        member.setProductId(productId);
        member.setFromDate(CoreClock.now());
        entityManager.persist(member);
        if (primary) {
            entityManager.find(Product.class, productId).setPrimaryProductCategoryId(productCategoryId);
        }
    }

    // ── prices ──

    /** Sets a price from now on, ending the current one of the same type, currency and store group. A null price does nothing. */
    public void setPrice(String productId, String priceTypeId, String currencyUomId, String storeGroupId, BigDecimal price) {
        if (price == null) {
            return;
        }
        if (price.signum() < 0) {
            throw new IllegalArgumentException("A price can't be negative");
        }
        String storeGroup = storeGroupId == null ? ALL_STORES : storeGroupId;
        LocalDateTime now = CoreClock.now();
        currentPrices(productId, priceTypeId, currencyUomId, storeGroup).forEach(earlierPrice -> earlierPrice.setThruDate(now));

        ProductPrice newPrice = new ProductPrice();
        newPrice.setProductId(productId);
        newPrice.setProductPriceTypeId(priceTypeId);
        newPrice.setProductPricePurposeId(PURCHASE);
        newPrice.setCurrencyUomId(currencyUomId);
        newPrice.setProductStoreGroupId(storeGroup);
        newPrice.setFromDate(now);
        newPrice.setPrice(price);
        entityManager.persist(newPrice);
    }

    /** The current price: the store group's own price, else the all-stores price. */
    @CoreTransactional(readOnly = true)
    public Optional<BigDecimal> price(String productId, String priceTypeId, String currencyUomId, String storeGroupId) {
        boolean ownGroup = storeGroupId != null && !ALL_STORES.equals(storeGroupId);
        if (ownGroup) {
            Optional<BigDecimal> groupPrice = newestPrice(productId, priceTypeId, currencyUomId, storeGroupId);
            if (groupPrice.isPresent()) {
                return groupPrice;
            }
        }
        return newestPrice(productId, priceTypeId, currencyUomId, ALL_STORES);
    }

    private Optional<BigDecimal> newestPrice(String productId, String priceTypeId, String currencyUomId, String storeGroupId) {
        return currentPrices(productId, priceTypeId, currencyUomId, storeGroupId).stream().map(ProductPrice::getPrice).findFirst();
    }

    /** Prices not yet ended, newest first. */
    private List<ProductPrice> currentPrices(String productId, String priceTypeId, String currencyUomId, String storeGroupId) {
        return entityManager.createQuery("SELECT p FROM ProductPrice p WHERE p.productId = :productId AND p.productPriceTypeId = :priceTypeId "
                        + "AND p.currencyUomId = :currency AND p.productStoreGroupId = :storeGroup AND p.thruDate IS NULL ORDER BY p.fromDate DESC",
                        ProductPrice.class)
                .setParameter("productId", productId)
                .setParameter("priceTypeId", priceTypeId)
                .setParameter("currency", currencyUomId)
                .setParameter("storeGroup", storeGroupId)
                .getResultList();
    }

    private static String blankToNull(String value) {
        return value == null || value.isBlank() ? null : value.trim();
    }
}
