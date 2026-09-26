package com.nexus.oms.service;

import com.nexus.oms.entity.NxCarrierLabelConfig;
import com.nexus.oms.exception.BadRequestException;
import com.nexus.oms.exception.ResourceNotFoundException;
import com.nexus.oms.integration.carrier.CarrierLabelAdapter;
import com.nexus.oms.repository.CarrierLabelConfigRepository;
import com.nexus.oms.security.TenantContext;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.function.Function;
import java.util.stream.Collectors;

/**
 * CRUD for {@code nx_carrier_label_config} — per-tenant carrier label settings.
 * Adding a carrier is a config row + adapter bean, not a code change.
 */
@Service
public class CarrierLabelConfigService {

    private static final Logger log = LoggerFactory.getLogger(CarrierLabelConfigService.class);

    private final CarrierLabelConfigRepository configRepository;
    private final Map<String, CarrierLabelAdapter> adapters;

    public CarrierLabelConfigService(CarrierLabelConfigRepository configRepository,
                                     List<CarrierLabelAdapter> adapterBeans) {
        this.configRepository = configRepository;
        this.adapters = adapterBeans.stream()
                .collect(Collectors.toMap(CarrierLabelAdapter::getName, Function.identity()));
    }

    @Transactional
    public NxCarrierLabelConfig upsertConfig(NxCarrierLabelConfig config) {
        UUID tenantId = TenantContext.getCurrentTenantId();
        config.setTenantId(tenantId);

        if (config.getCarrierCode() == null || config.getCarrierCode().isBlank()) {
            throw new BadRequestException("carrierCode is required");
        }
        if (config.getAdapterName() == null || config.getAdapterName().isBlank()) {
            throw new BadRequestException("adapterName is required");
        }
        if (!adapters.containsKey(config.getAdapterName())) {
            throw new BadRequestException("Unknown adapter: " + config.getAdapterName()
                    + ". Available: " + adapters.keySet());
        }

        configRepository.findByTenantIdAndCarrierCode(tenantId, config.getCarrierCode())
                .ifPresent(existing -> config.setId(existing.getId()));

        NxCarrierLabelConfig saved = configRepository.save(config);
        log.info("Upserted carrier label config for {}: adapter={} format={}",
                saved.getCarrierCode(), saved.getAdapterName(), saved.getLabelFormat());
        return saved;
    }

    public List<NxCarrierLabelConfig> getConfigs() {
        UUID tenantId = TenantContext.getCurrentTenantId();
        return configRepository.findByTenantId(tenantId);
    }

    public NxCarrierLabelConfig getConfig(UUID id) {
        UUID tenantId = TenantContext.getCurrentTenantId();
        return configRepository.findById(id)
                .filter(c -> c.getTenantId().equals(tenantId))
                .orElseThrow(() -> new ResourceNotFoundException("CarrierLabelConfig", id));
    }

    public NxCarrierLabelConfig getConfigForCarrier(String carrierCode) {
        UUID tenantId = TenantContext.getCurrentTenantId();
        return configRepository.findByTenantIdAndCarrierCode(tenantId, carrierCode)
                .orElseThrow(() -> new ResourceNotFoundException(
                        "No carrier label config for carrier " + carrierCode));
    }

    public List<String> getAvailableAdapters() {
        return adapters.keySet().stream().sorted().toList();
    }

    public CarrierLabelAdapter resolveAdapter(String adapterName) {
        CarrierLabelAdapter adapter = adapters.get(adapterName);
        if (adapter == null) {
            throw new BadRequestException("Unknown adapter: " + adapterName
                    + ". Available: " + adapters.keySet());
        }
        return adapter;
    }

    @Transactional
    public void deleteConfig(UUID id) {
        UUID tenantId = TenantContext.getCurrentTenantId();
        NxCarrierLabelConfig config = getConfig(id);
        if (!config.getTenantId().equals(tenantId)) {
            throw new ResourceNotFoundException("CarrierLabelConfig", id);
        }
        configRepository.delete(config);
        log.info("Deleted carrier label config {}", id);
    }
}