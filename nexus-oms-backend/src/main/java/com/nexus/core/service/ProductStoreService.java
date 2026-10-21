package com.nexus.core.service;

import com.nexus.core.model.product.ProductStore;
import com.nexus.core.model.product.ProductStoreSetting;
import jakarta.persistence.EntityManager;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.Optional;

/**
 * Product stores (one per brand or sales channel) and their settings. Settings are coded values
 * ({@code PRODUCT_STORE_SETTING} enumerations), so a new setting needs no schema change.
 */
@Service("coreProductStoreService")
@Transactional
public class ProductStoreService {

    public static final String SETTING_TYPE = "PRODUCT_STORE_SETTING";

    private final EntityManager em;
    private final EnumerationService enumerations;

    public ProductStoreService(EntityManager em, EnumerationService enumerations) {
        this.em = em;
        this.enumerations = enumerations;
    }

    public record NewStore(String productStoreId, String storeName, String currencyUomId, String timeZone,
                           String salesChannelEnumId, String externalId, String storeDomain) {
    }

    /** Creates the store, or returns the existing one with this id unchanged. */
    public String createIfMissing(NewStore n) {
        if (n.productStoreId() == null || !n.productStoreId().matches("[A-Za-z0-9_.-]{1,40}")) {
            throw new IllegalArgumentException("A store id is 1 to 40 letters, digits, dots, dashes or underscores, not '" + n.productStoreId() + "'");
        }
        if (em.find(ProductStore.class, n.productStoreId()) != null) return n.productStoreId();
        ProductStore s = new ProductStore();
        s.setProductStoreId(n.productStoreId());
        s.setStoreName(n.storeName() == null || n.storeName().isBlank() ? n.productStoreId() : n.storeName().trim());
        s.setPrimaryStoreGroupId(ProductService.ALL_STORES);
        s.setDefaultCurrencyUomId(n.currencyUomId());
        s.setDefaultTimeZoneString(n.timeZone());
        if (n.salesChannelEnumId() != null) enumerations.require("ORDER_SALES_CHANNEL", n.salesChannelEnumId());
        s.setDefaultSalesChannelEnumId(n.salesChannelEnumId());
        s.setExternalId(n.externalId());
        s.setStoreDomain(n.storeDomain());
        s.setReserveInventory(true);
        s.setRequireInventory(true);
        s.setCheckInventory(true);
        s.setEnableBrokering(true);
        s.setAllowSplit(true);
        s.setAutoApproveOrder(false);
        s.setDaysToReturn(30L);
        s.setHeaderApprovedStatus("ORDER_APPROVED");
        s.setItemApprovedStatus("ITEM_APPROVED");
        em.persist(s);
        return s.getProductStoreId();
    }

    @Transactional(readOnly = true)
    public Optional<String> setting(String productStoreId, String settingEnumId) {
        ProductStoreSetting s = em.find(ProductStoreSetting.class, new ProductStoreSetting.Key(productStoreId, settingEnumId));
        return Optional.ofNullable(s).map(ProductStoreSetting::getSettingValue).filter(v -> !v.isBlank());
    }

    public long settingLong(String productStoreId, String settingEnumId, long fallback) {
        return setting(productStoreId, settingEnumId).map(v -> {
            try {
                return Long.parseLong(v.trim());
            } catch (NumberFormatException e) {
                throw new IllegalStateException("Store " + productStoreId + " setting " + settingEnumId + " should be a whole number, but is '" + v + "'");
            }
        }).orElse(fallback);
    }

    public void setSetting(String productStoreId, String settingEnumId, String value) {
        enumerations.require(SETTING_TYPE, settingEnumId);
        if (em.find(ProductStore.class, productStoreId) == null) throw new IllegalArgumentException("Unknown store " + productStoreId);
        ProductStoreSetting s = em.find(ProductStoreSetting.class, new ProductStoreSetting.Key(productStoreId, settingEnumId));
        if (s == null) {
            s = new ProductStoreSetting();
            s.setProductStoreId(productStoreId);
            s.setSettingTypeEnumId(settingEnumId);
            s.setSettingValue(value);
            em.persist(s);
        } else {
            s.setSettingValue(value);
        }
    }
}
