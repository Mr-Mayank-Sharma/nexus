package com.nexus.core.service;

import com.nexus.core.CoreStatus;
import com.nexus.core.model.product.ProductStore;
import com.nexus.core.model.product.ProductStoreSetting;
import jakarta.persistence.EntityManager;
import org.springframework.stereotype.Service;
import com.nexus.core.CoreTransactional;

import java.util.Optional;

/**
 * Product stores (one per brand or sales channel) and their settings. Settings are coded values
 * ({@code PRODUCT_STORE_SETTING} enumerations), so a new setting needs no schema change.
 */
@Service("coreProductStoreService")
@CoreTransactional
public class ProductStoreService {

    public static final String SETTING_TYPE = "PRODUCT_STORE_SETTING";
    static final String SALES_CHANNEL_TYPE = "ORDER_SALES_CHANNEL";
    static final long DEFAULT_DAYS_TO_RETURN = 30;
    private static final String STORE_ID_PATTERN = "[A-Za-z0-9_.-]{1,40}";

    private final EntityManager entityManager;
    private final EnumerationService enumerationService;

    public ProductStoreService(EntityManager entityManager, EnumerationService enumerationService) {
        this.entityManager = entityManager;
        this.enumerationService = enumerationService;
    }

    public record NewStore(String productStoreId, String storeName, String currencyUomId, String timeZone,
                           String salesChannelEnumId, String externalId, String storeDomain) {
    }

    /**
     * Creates the store, or returns the existing one with this id unchanged. A new store holds stock for its orders,
     * brokers them, allows splits, takes returns for 30 days, and approves orders by hand.
     */
    public String createIfMissing(NewStore newStore) {
        String storeId = newStore.productStoreId();
        if (storeId == null || !storeId.matches(STORE_ID_PATTERN)) {
            throw new IllegalArgumentException("A store id is 1 to 40 letters, digits, dots, dashes or underscores, not '" + storeId + "'");
        }
        if (entityManager.find(ProductStore.class, storeId) != null) {
            return storeId;
        }
        if (newStore.salesChannelEnumId() != null) {
            enumerationService.require(SALES_CHANNEL_TYPE, newStore.salesChannelEnumId());
        }

        ProductStore store = new ProductStore();
        store.setProductStoreId(storeId);
        boolean hasName = newStore.storeName() != null && !newStore.storeName().isBlank();
        store.setStoreName(hasName ? newStore.storeName().trim() : storeId);
        store.setPrimaryStoreGroupId(ProductService.ALL_STORES);
        store.setDefaultCurrencyUomId(newStore.currencyUomId());
        store.setDefaultTimeZoneString(newStore.timeZone());
        store.setDefaultSalesChannelEnumId(newStore.salesChannelEnumId());
        store.setExternalId(newStore.externalId());
        store.setStoreDomain(newStore.storeDomain());
        store.setReserveInventory(true);
        store.setRequireInventory(true);
        store.setCheckInventory(true);
        store.setEnableBrokering(true);
        store.setAllowSplit(true);
        store.setAutoApproveOrder(false);
        store.setDaysToReturn(DEFAULT_DAYS_TO_RETURN);
        store.setHeaderApprovedStatus(CoreStatus.Order.APPROVED);
        store.setItemApprovedStatus(CoreStatus.Item.APPROVED);
        entityManager.persist(store);
        return storeId;
    }

    /** The store's setting; empty when it is not set or blank. */
    @CoreTransactional(readOnly = true)
    public Optional<String> setting(String productStoreId, String settingEnumId) {
        ProductStoreSetting setting = entityManager.find(ProductStoreSetting.class, new ProductStoreSetting.Key(productStoreId, settingEnumId));
        return Optional.ofNullable(setting).map(ProductStoreSetting::getSettingValue).filter(value -> !value.isBlank());
    }

    /** A whole-number setting, or the fallback when it is not set; a setting that isn't a number is an error. */
    public long settingLong(String productStoreId, String settingEnumId, long fallback) {
        return setting(productStoreId, settingEnumId).map(value -> {
            try {
                return Long.parseLong(value.trim());
            } catch (NumberFormatException notANumber) {
                throw new IllegalStateException("Store " + productStoreId + " setting " + settingEnumId + " should be a whole number, but is '" + value + "'");
            }
        }).orElse(fallback);
    }

    /** Sets (or replaces) a setting. The setting must be a known PRODUCT_STORE_SETTING and the store must exist. */
    public void setSetting(String productStoreId, String settingEnumId, String value) {
        enumerationService.require(SETTING_TYPE, settingEnumId);
        if (entityManager.find(ProductStore.class, productStoreId) == null) {
            throw new IllegalArgumentException("Unknown store " + productStoreId);
        }
        ProductStoreSetting setting = entityManager.find(ProductStoreSetting.class, new ProductStoreSetting.Key(productStoreId, settingEnumId));
        if (setting == null) {
            setting = new ProductStoreSetting();
            setting.setProductStoreId(productStoreId);
            setting.setSettingTypeEnumId(settingEnumId);
            setting.setSettingValue(value);
            entityManager.persist(setting);
        } else {
            setting.setSettingValue(value);
        }
    }
}
