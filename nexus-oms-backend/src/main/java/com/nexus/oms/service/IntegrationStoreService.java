package com.nexus.oms.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.nexus.oms.dto.IntegrationStoreRequest;
import com.nexus.oms.dto.StoreSyncStatus;
import com.nexus.oms.entity.*;
import com.nexus.oms.exception.BadRequestException;
import com.nexus.oms.exception.ResourceNotFoundException;
import com.nexus.oms.repository.*;
import org.springframework.cache.annotation.CacheEvict;
import org.springframework.cache.annotation.Cacheable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.*;
import java.util.stream.Collectors;

@Service
public class IntegrationStoreService {

    private final NxIntegrationStoreRepository storeRepository;
    private final NxIntegrationStoreSettingRepository settingRepository;
    private final NxIntegrationSyncConfigRepository syncConfigRepository;
    private final NxSyncLogRepository syncLogRepository;
    private final ObjectMapper objectMapper;

    public IntegrationStoreService(NxIntegrationStoreRepository storeRepository,
                                    NxIntegrationStoreSettingRepository settingRepository,
                                    NxIntegrationSyncConfigRepository syncConfigRepository,
                                    NxSyncLogRepository syncLogRepository,
                                    ObjectMapper objectMapper) {
        this.storeRepository = storeRepository;
        this.settingRepository = settingRepository;
        this.syncConfigRepository = syncConfigRepository;
        this.syncLogRepository = syncLogRepository;
        this.objectMapper = objectMapper;
    }

    @Cacheable(value = "storeSettings", key = "'stores:' + #tenantId + ':' + (#platform ?: 'all')")
    public List<NxIntegrationStore> getStores(UUID tenantId, String platform) {
        if (platform != null && !platform.isBlank()) {
            return storeRepository.findByTenantIdAndPlatform(tenantId, platform);
        }
        return storeRepository.findByTenantId(tenantId);
    }

    public NxIntegrationStore getStore(UUID id) {
        return storeRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("IntegrationStore", id));
    }

    public Optional<NxIntegrationStore> findStoreByExternalDomain(String domain) {
        return storeRepository.findByExternalDomain(domain);
    }

    @Transactional
    @CacheEvict(value = "storeSettings", allEntries = true)
    public NxIntegrationStore createStore(UUID tenantId, IntegrationStoreRequest request) {
        if (storeRepository.findByTenantIdAndStoreCode(tenantId, request.getStoreCode()).isPresent()) {
            throw new BadRequestException("Store code already exists: " + request.getStoreCode());
        }

        NxIntegrationStore store = NxIntegrationStore.builder()
                .tenantId(tenantId)
                .storeCode(request.getStoreCode())
                .storeName(request.getStoreName())
                .platform(request.getPlatform().toUpperCase())
                .platformType(request.getPlatformType())
                .currency(request.getCurrency())
                .defaultLocale(request.getDefaultLocale())
                .timezone(request.getTimezone())
                .externalStoreId(request.getExternalStoreId())
                .externalDomain(request.getExternalDomain())
                .isActive(request.getIsActive() != null ? request.getIsActive() : true)
                .build();
        store = storeRepository.save(store);

        if (request.getSettings() != null) {
            for (Map.Entry<String, String> entry : request.getSettings().entrySet()) {
                NxIntegrationStoreSetting setting = NxIntegrationStoreSetting.builder()
                        .storeId(store.getId())
                        .settingType(entry.getKey())
                        .settingValue(entry.getValue())
                        .isEncrypted(entry.getKey().contains("token") || entry.getKey().contains("secret"))
                        .build();
                settingRepository.save(setting);
            }
        }

        String[] defaultSyncs = {"ORDER_IMPORT", "PRODUCT_SYNC", "INVENTORY_PUSH", "FULFILLMENT_PUSH", "REFUND_PUSH"};
        for (String syncType : defaultSyncs) {
            NxIntegrationSyncConfig syncConfig = NxIntegrationSyncConfig.builder()
                    .storeId(store.getId())
                    .syncType(syncType)
                    .enabled(true)
                    .intervalMinutes(15)
                    .build();
            syncConfigRepository.save(syncConfig);
        }

        return store;
    }

    @Transactional
    @CacheEvict(value = "storeSettings", allEntries = true)
    public NxIntegrationStore updateStore(UUID id, IntegrationStoreRequest request) {
        NxIntegrationStore store = getStore(id);
        if (request.getStoreName() != null) store.setStoreName(request.getStoreName());
        if (request.getCurrency() != null) store.setCurrency(request.getCurrency());
        if (request.getDefaultLocale() != null) store.setDefaultLocale(request.getDefaultLocale());
        if (request.getTimezone() != null) store.setTimezone(request.getTimezone());
        if (request.getExternalStoreId() != null) store.setExternalStoreId(request.getExternalStoreId());
        if (request.getExternalDomain() != null) store.setExternalDomain(request.getExternalDomain());
        if (request.getIsActive() != null) store.setIsActive(request.getIsActive());
        store = storeRepository.save(store);

        if (request.getSettings() != null) {
            for (Map.Entry<String, String> entry : request.getSettings().entrySet()) {
                NxIntegrationStoreSetting setting = settingRepository
                        .findByStoreIdAndSettingType(store.getId(), entry.getKey())
                        .orElse(NxIntegrationStoreSetting.builder()
                                .storeId(store.getId())
                                .settingType(entry.getKey())
                                .build());
                setting.setSettingValue(entry.getValue());
                settingRepository.save(setting);
            }
        }
        return store;
    }

    @Transactional
    @CacheEvict(value = "storeSettings", allEntries = true)
    public void deleteStore(UUID id) {
        NxIntegrationStore store = getStore(id);
        settingRepository.deleteByStoreId(id);
        storeRepository.delete(store);
    }

    @Cacheable(value = "storeSettings", key = "'settings:' + #storeId")
    public List<NxIntegrationStoreSetting> getSettings(UUID storeId) {
        return settingRepository.findByStoreId(storeId);
    }

    @Cacheable(value = "storeSettings", key = "'setting:' + #storeId + ':' + #settingType")
    public String getSetting(UUID storeId, String settingType) {
        return settingRepository.findByStoreIdAndSettingType(storeId, settingType)
                .map(NxIntegrationStoreSetting::getSettingValue)
                .orElse(null);
    }

    @Cacheable(value = "storeSettings", key = "'settingsMap:' + #storeId")
    public Map<String, String> getSettingsMap(UUID storeId) {
        return settingRepository.findByStoreId(storeId).stream()
                .collect(Collectors.toMap(NxIntegrationStoreSetting::getSettingType,
                        s -> s.getSettingValue() != null ? s.getSettingValue() : ""));
    }

    public StoreSyncStatus getSyncStatus(UUID storeId) {
        NxIntegrationStore store = getStore(storeId);
        List<NxIntegrationSyncConfig> syncConfigs = syncConfigRepository.findByStoreId(storeId);

        StoreSyncStatus status = StoreSyncStatus.builder()
                .storeId(store.getId())
                .storeCode(store.getStoreCode())
                .storeName(store.getStoreName())
                .platform(store.getPlatform())
                .connected(store.getIsActive())
                .syncTypes(syncConfigs.stream().map(sc -> StoreSyncStatus.SyncTypeStatus.builder()
                        .syncType(sc.getSyncType())
                        .enabled(sc.getEnabled())
                        .intervalMinutes(sc.getIntervalMinutes())
                        .lastSyncAt(sc.getLastSyncAt())
                        .lastSyncStatus(sc.getLastSyncStatus())
                        .lastSyncMessage(sc.getLastSyncMessage())
                        .build()).collect(Collectors.toList()))
                .build();
        return status;
    }
}
