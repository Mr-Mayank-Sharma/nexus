package com.nexus.oms.integration.core;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.nexus.oms.dto.IntegrationStoreRequest;
import com.nexus.oms.entity.NxIntegrationStore;
import com.nexus.oms.exception.BadRequestException;
import com.nexus.oms.exception.ResourceNotFoundException;
import com.nexus.oms.integration.dto.ConnectorConfig;
import com.nexus.oms.repository.NxIntegrationStoreRepository;
import com.nexus.oms.security.TenantContext;
import com.nexus.oms.service.IntegrationStoreService;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.util.*;

/**
 * The Integration Hub's connections, kept where every other integration is kept: as integration stores.
 * That makes them tenant-scoped (row-level security plus an explicit check), persistent across restarts, and
 * their secrets sealed at rest. The hub used to hold connectors in one global in-memory map, so every
 * organisation listed everyone's connections and all of them, with their credentials, vanished on restart.
 * A connector object is built from the stored settings for each request and holds no state of its own.
 */
@Service
public class StoreConnectorService {

    private static final ObjectMapper JSON = new ObjectMapper();

    private final ConnectorRegistry registry;
    private final IntegrationStoreService stores;
    private final NxIntegrationStoreRepository storeRepository;
    private final CredentialVault vault;

    public StoreConnectorService(ConnectorRegistry registry, IntegrationStoreService stores,
                                 NxIntegrationStoreRepository storeRepository, CredentialVault vault) {
        this.registry = registry;
        this.stores = stores;
        this.storeRepository = storeRepository;
        this.vault = vault;
    }

    /** This organisation's stores whose platform the hub has a connector for. */
    public List<NxIntegrationStore> list() {
        UUID tenant = TenantContext.getCurrentTenantId();
        Set<String> known = registry.getRegisteredPlatforms();
        return storeRepository.findByTenantId(tenant).stream()
                .filter(s -> s.getPlatform() != null && known.contains(s.getPlatform().toUpperCase()))
                .toList();
    }

    public NxIntegrationStore find(String id) {
        UUID storeId;
        try { storeId = UUID.fromString(id); } catch (IllegalArgumentException e) { throw new ResourceNotFoundException("Connector", id); }
        NxIntegrationStore s = storeRepository.findById(storeId).orElseThrow(() -> new ResourceNotFoundException("Connector", id));
        if (!TenantContext.getCurrentTenantId().equals(s.getTenantId())) throw new ResourceNotFoundException("Connector", id);
        return s;
    }

    /** A freshly initialised connector for the stored connection. Secrets are opened in memory only. */
    public Connector open(String id) {
        NxIntegrationStore s = find(id);
        String platform = s.getPlatform().toUpperCase();
        Map<String, String> settings = new HashMap<>(stores.getSettingsMap(s.getId()));
        ConnectorConfig cfg = new ConnectorConfig();
        cfg.setId(s.getId().toString());
        cfg.setTenantId(s.getTenantId().toString());
        cfg.setPlatformType(platform);
        cfg.setStoreCode(s.getStoreCode());
        cfg.setStoreName(s.getStoreName());
        cfg.setSettings(settings);
        cfg.setCredentials(new HashMap<>(settings));
        return registry.initialize(cfg);
    }

    public NxIntegrationStore create(ConnectorConfig config) {
        if (config.getPlatformType() == null || config.getPlatformType().isBlank()) throw new BadRequestException("platformType is required");
        String platform = config.getPlatformType().toUpperCase();
        if (!registry.getRegisteredPlatforms().contains(platform)) throw new BadRequestException("No connector for platform: " + platform);
        Map<String, String> all = new LinkedHashMap<>();
        if (config.getSettings() != null) all.putAll(config.getSettings());
        if (config.getCredentials() != null) all.putAll(config.getCredentials());
        IntegrationStoreRequest req = new IntegrationStoreRequest();
        req.setPlatform(platform);
        req.setPlatformType(platform);
        req.setStoreCode((platform + "-" + UUID.randomUUID().toString().substring(0, 8)).toLowerCase());
        req.setStoreName(config.getStoreName() != null && !config.getStoreName().isBlank() ? config.getStoreName() : platform);
        req.setSettings(all);
        req.setIsActive(true);
        return stores.createStore(TenantContext.getCurrentTenantId(), req);
    }

    public void remove(String id) {
        stores.deleteStore(find(id).getId());
    }

    /** Remembers the outcome of the last real connection test so the screen can show it after a restart. */
    public void recordTest(NxIntegrationStore s, boolean ok, String message) {
        Map<String, Object> meta = new LinkedHashMap<>();
        try {
            if (s.getMetadata() != null && !s.getMetadata().isBlank()) meta.putAll(JSON.readValue(s.getMetadata(), Map.class));
        } catch (Exception ignored) { /* start afresh */ }
        meta.put("lastTestAt", LocalDateTime.now().toString());
        meta.put("lastTestOk", ok);
        meta.put("lastTestMessage", message);
        try { s.setMetadata(JSON.writeValueAsString(meta)); } catch (Exception ignored) { return; }
        storeRepository.save(s);
    }

    /** Last recorded test outcome, or an empty map when it has never been tested. */
    public Map<String, Object> lastTest(NxIntegrationStore s) {
        try {
            if (s.getMetadata() == null || s.getMetadata().isBlank()) return Map.of();
            Map<?, ?> m = JSON.readValue(s.getMetadata(), Map.class);
            Map<String, Object> out = new LinkedHashMap<>();
            for (String k : List.of("lastTestAt", "lastTestOk", "lastTestMessage")) if (m.containsKey(k)) out.put(k, m.get(k));
            return out;
        } catch (Exception e) {
            return Map.of();
        }
    }
}
