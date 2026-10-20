package com.nexus.oms.service;

import com.nexus.oms.entity.Warehouse;
import com.nexus.oms.exception.ResourceNotFoundException;
import com.nexus.oms.repository.WarehouseRepository;
import com.nexus.oms.security.TenantContext;
import org.springframework.cache.annotation.CacheEvict;
import org.springframework.cache.annotation.Cacheable;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * Locations (warehouses and distribution centres) as the OMS needs them: list, create, edit, delete, and a capacity
 * summary from floor area. What happens inside the building (zones, bins, staff, equipment) is Nexus WMS.
 */
@Service
public class LocationService {

    private final WarehouseRepository warehouseRepository;

    public LocationService(WarehouseRepository warehouseRepository) {
        this.warehouseRepository = warehouseRepository;
    }

    @Cacheable(value = "warehouses", key = "'page:' + T(com.nexus.oms.security.TenantContext).getCurrentTenantId() + ':' + #pageable.pageNumber + ':' + #pageable.pageSize")
    public Page<Warehouse> getAllWarehouses(Pageable pageable) {
        return warehouseRepository.findByTenantId(TenantContext.getCurrentTenantId(), pageable);
    }

    @Cacheable(value = "warehouses", key = "#id")
    public Warehouse getWarehouse(UUID id) {
        return warehouseRepository.findByIdAndTenantId(id, TenantContext.getCurrentTenantId())
                .orElseThrow(() -> new ResourceNotFoundException("Warehouse", id));
    }

    @Transactional
    @CacheEvict(value = "warehouses", allEntries = true)
    public Warehouse createWarehouse(Warehouse warehouse) {
        warehouse.setTenantId(TenantContext.getCurrentTenantId());
        return warehouseRepository.save(warehouse);
    }

    @Transactional
    @CacheEvict(value = "warehouses", allEntries = true)
    public Warehouse updateWarehouse(UUID id, Warehouse updates) {
        Warehouse existing = warehouseRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Warehouse", id));

        if (updates.getCode() != null) existing.setCode(updates.getCode());
        if (updates.getName() != null) existing.setName(updates.getName());
        if (updates.getType() != null) existing.setType(updates.getType());
        if (updates.getStatus() != null) existing.setStatus(updates.getStatus());
        if (updates.getAddressLine1() != null) existing.setAddressLine1(updates.getAddressLine1());
        if (updates.getAddressLine2() != null) existing.setAddressLine2(updates.getAddressLine2());
        if (updates.getCity() != null) existing.setCity(updates.getCity());
        if (updates.getState() != null) existing.setState(updates.getState());
        if (updates.getZipCode() != null) existing.setZipCode(updates.getZipCode());
        if (updates.getCountry() != null) existing.setCountry(updates.getCountry());
        if (updates.getLatitude() != null) existing.setLatitude(updates.getLatitude());
        if (updates.getLongitude() != null) existing.setLongitude(updates.getLongitude());
        if (updates.getTotalCapacitySqm() != null) existing.setTotalCapacitySqm(updates.getTotalCapacitySqm());
        if (updates.getUsedCapacitySqm() != null) existing.setUsedCapacitySqm(updates.getUsedCapacitySqm());
        if (updates.getTotalCapacityCbm() != null) existing.setTotalCapacityCbm(updates.getTotalCapacityCbm());
        if (updates.getUsedCapacityCbm() != null) existing.setUsedCapacityCbm(updates.getUsedCapacityCbm());
        if (updates.getDockCount() != null) existing.setDockCount(updates.getDockCount());
        if (updates.getIsActive() != null) existing.setIsActive(updates.getIsActive());
        if (updates.getOperatingHours() != null) existing.setOperatingHours(updates.getOperatingHours());
        if (updates.getContactName() != null) existing.setContactName(updates.getContactName());
        if (updates.getContactPhone() != null) existing.setContactPhone(updates.getContactPhone());
        if (updates.getContactEmail() != null) existing.setContactEmail(updates.getContactEmail());
        if (updates.getMetadata() != null) existing.setMetadata(updates.getMetadata());

        return warehouseRepository.save(existing);
    }

    @Transactional
    @CacheEvict(value = "warehouses", allEntries = true)
    public void deleteWarehouse(UUID id) {
        Warehouse warehouse = warehouseRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Warehouse", id));


        warehouseRepository.delete(warehouse);
    }

    @Cacheable(value = "warehouses", key = "'all-summary:' + T(com.nexus.oms.security.TenantContext).getCurrentTenantId()")
    public List<Map<String, Object>> getAllWarehousesSummary() {
        List<Map<String, Object>> summaries = new ArrayList<>();
        for (Warehouse wh : warehouseRepository.findByTenantId(TenantContext.getCurrentTenantId(), PageRequest.of(0, 50)).getContent()) {
            Map<String, Object> summary = summarise(wh);
            summary.put("id", wh.getId().toString());
            summary.put("name", wh.getName());
            summary.put("code", wh.getCode());
            summary.put("status", wh.getStatus());
            summary.put("city", wh.getCity());
            summary.put("country", wh.getCountry());
            summaries.add(summary);
        }
        return summaries;
    }

    public Map<String, Object> getWarehouseSummary(UUID warehouseId) {
        return summarise(getWarehouse(warehouseId));
    }

    /** Capacity used, from floor area. Null when the location has no area recorded, rather than a made-up zero. */
    private static Map<String, Object> summarise(Warehouse wh) {
        Map<String, Object> summary = new HashMap<>();
        BigDecimal used = wh.getUsedCapacitySqm(), total = wh.getTotalCapacitySqm();
        summary.put("capacityUtilization", used == null || total == null || total.signum() <= 0 ? null
                : used.multiply(BigDecimal.valueOf(100)).divide(total, 2, RoundingMode.HALF_UP).doubleValue());
        summary.put("dockCount", wh.getDockCount());
        return summary;
    }
}
