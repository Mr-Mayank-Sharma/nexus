package com.nexus.oms.service;

import com.nexus.oms.entity.NxCarrier;
import com.nexus.oms.repository.CarrierRepository;
import com.nexus.oms.security.TenantContext;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.Map;
import java.util.NoSuchElementException;
import java.util.UUID;

@Service
public class CarrierService {

    private static final Logger log = LoggerFactory.getLogger(CarrierService.class);

    private final CarrierRepository carrierRepository;

    public CarrierService(CarrierRepository carrierRepository) {
        this.carrierRepository = carrierRepository;
    }

    public Page<NxCarrier> getCarriers(UUID tenantId, Pageable pageable) {
        return carrierRepository.findByTenantId(tenantId, pageable);
    }

    public NxCarrier getCarrier(UUID id) {
        return carrierRepository.findById(id)
                .orElseThrow(() -> new NoSuchElementException("Carrier not found: " + id));
    }

    @Transactional
    public NxCarrier createCarrier(NxCarrier carrier) {
        carrier.setTenantId(TenantContext.getCurrentTenantId());
        NxCarrier saved = carrierRepository.save(carrier);
        log.info("Created carrier: {} ({})", saved.getName(), saved.getCode());
        return saved;
    }

    @Transactional
    public NxCarrier updateCarrier(UUID id, NxCarrier updates) {
        NxCarrier existing = getCarrier(id);
        if (updates.getName() != null) existing.setName(updates.getName());
        if (updates.getCode() != null) existing.setCode(updates.getCode());
        if (updates.getType() != null) existing.setType(updates.getType());
        if (updates.getStatus() != null) existing.setStatus(updates.getStatus());
        if (updates.getAccountNumber() != null) existing.setAccountNumber(updates.getAccountNumber());
        if (updates.getApiKeyEncrypted() != null) existing.setApiKeyEncrypted(updates.getApiKeyEncrypted());
        if (updates.getApiSecretEncrypted() != null) existing.setApiSecretEncrypted(updates.getApiSecretEncrypted());
        if (updates.getIsActive() != null) existing.setIsActive(updates.getIsActive());
        return carrierRepository.save(existing);
    }

    @Transactional
    public void deleteCarrier(UUID id) {
        NxCarrier carrier = getCarrier(id);
        carrierRepository.delete(carrier);
        log.info("Deleted carrier: {} ({})", carrier.getName(), carrier.getCode());
    }

    public Map<String, Object> getCarrierKPIs(UUID tenantId) {
        Page<NxCarrier> all = carrierRepository.findByTenantId(tenantId, Pageable.unpaged());
        java.util.List<NxCarrier> carriers = all.getContent();
        long activeCount = carriers.stream().filter(c -> Boolean.TRUE.equals(c.getIsActive())).count();
        long totalShipments = carriers.stream().mapToLong(c -> c.getTotalShipments() != null ? c.getTotalShipments() : 0L).sum();
        double avgOtd = carriers.stream()
                .filter(c -> c.getOtdRate() != null)
                .mapToDouble(c -> c.getOtdRate().doubleValue())
                .average().orElse(0);
        double avgCost = carriers.stream()
                .filter(c -> c.getAvgCost() != null)
                .mapToDouble(c -> c.getAvgCost().doubleValue())
                .average().orElse(0);
        return Map.of(
                "activeCarriers", activeCount,
                "totalCarriers", carriers.size(),
                "totalShipments", totalShipments,
                "avgOtdRate", BigDecimal.valueOf(avgOtd).setScale(2, java.math.RoundingMode.HALF_UP),
                "avgCost", BigDecimal.valueOf(avgCost).setScale(2, java.math.RoundingMode.HALF_UP)
        );
    }
}
