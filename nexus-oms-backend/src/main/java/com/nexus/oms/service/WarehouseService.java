package com.nexus.oms.service;

import com.nexus.oms.entity.*;
import com.nexus.oms.exception.ResourceNotFoundException;
import com.nexus.oms.repository.*;
import com.nexus.oms.security.TenantContext;
import org.springframework.cache.annotation.CacheEvict;
import org.springframework.cache.annotation.Cacheable;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

@Service
public class WarehouseService {

    private final WarehouseRepository warehouseRepository;
    private final WarehouseZoneRepository warehouseZoneRepository;
    private final WarehouseBinRepository warehouseBinRepository;
    private final WarehouseStaffRepository warehouseStaffRepository;
    private final WarehouseEquipmentRepository warehouseEquipmentRepository;

    public WarehouseService(WarehouseRepository warehouseRepository,
                            WarehouseZoneRepository warehouseZoneRepository,
                            WarehouseBinRepository warehouseBinRepository,
                            WarehouseStaffRepository warehouseStaffRepository,
                            WarehouseEquipmentRepository warehouseEquipmentRepository) {
        this.warehouseRepository = warehouseRepository;
        this.warehouseZoneRepository = warehouseZoneRepository;
        this.warehouseBinRepository = warehouseBinRepository;
        this.warehouseStaffRepository = warehouseStaffRepository;
        this.warehouseEquipmentRepository = warehouseEquipmentRepository;
    }

    // ---- Warehouse CRUD ----

    @Cacheable(value = "warehouses", key = "'page:' + #pageable.pageNumber + ':' + #pageable.pageSize")
    public Page<Warehouse> getAllWarehouses(Pageable pageable) {
        return warehouseRepository.findByTenantId(TenantContext.getCurrentTenantId(), pageable);
    }

    @Cacheable(value = "warehouses", key = "#id")
    public Warehouse getWarehouse(UUID id) {
        return warehouseRepository.findById(id)
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

        List<WarehouseBin> bins = warehouseBinRepository.findByWarehouseId(id);
        if (!bins.isEmpty()) {
            throw new IllegalStateException("Cannot delete warehouse with existing bins");
        }

        warehouseRepository.delete(warehouse);
    }

    // ---- Warehouse Zones ----

    @Cacheable(value = "warehouseZones", key = "#warehouseId")
    public List<WarehouseZone> getZones(UUID warehouseId) {
        return warehouseZoneRepository.findByWarehouseId(warehouseId);
    }

    @Transactional
    @CacheEvict(value = "warehouseZones", allEntries = true)
    public WarehouseZone createZone(WarehouseZone zone) {
        zone.setTenantId(TenantContext.getCurrentTenantId());
        return warehouseZoneRepository.save(zone);
    }

    @Transactional
    @CacheEvict(value = "warehouseZones", allEntries = true)
    public WarehouseZone updateZone(UUID id, WarehouseZone updates) {
        WarehouseZone existing = warehouseZoneRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("WarehouseZone", id));

        if (updates.getWarehouseId() != null) existing.setWarehouseId(updates.getWarehouseId());
        if (updates.getCode() != null) existing.setCode(updates.getCode());
        if (updates.getName() != null) existing.setName(updates.getName());
        if (updates.getZoneType() != null) existing.setZoneType(updates.getZoneType());
        if (updates.getZoneCategory() != null) existing.setZoneCategory(updates.getZoneCategory());
        if (updates.getTemperatureMin() != null) existing.setTemperatureMin(updates.getTemperatureMin());
        if (updates.getTemperatureMax() != null) existing.setTemperatureMax(updates.getTemperatureMax());
        if (updates.getHumidityMin() != null) existing.setHumidityMin(updates.getHumidityMin());
        if (updates.getHumidityMax() != null) existing.setHumidityMax(updates.getHumidityMax());
        if (updates.getCapacitySqm() != null) existing.setCapacitySqm(updates.getCapacitySqm());
        if (updates.getUsedSqm() != null) existing.setUsedSqm(updates.getUsedSqm());
        if (updates.getIsActive() != null) existing.setIsActive(updates.getIsActive());
        if (updates.getIsHazardous() != null) existing.setIsHazardous(updates.getIsHazardous());
        if (updates.getSecurityLevel() != null) existing.setSecurityLevel(updates.getSecurityLevel());
        if (updates.getMetadata() != null) existing.setMetadata(updates.getMetadata());

        return warehouseZoneRepository.save(existing);
    }

    // ---- Warehouse Bins ----

    @Cacheable(value = "warehouseBins", key = "#warehouseId")
    public List<WarehouseBin> getBins(UUID warehouseId) {
        return warehouseBinRepository.findByWarehouseId(warehouseId);
    }

    @Cacheable(value = "warehouseBins", key = "'empty:' + #warehouseId")
    public List<WarehouseBin> getEmptyBins(UUID warehouseId) {
        return warehouseBinRepository.findByWarehouseIdAndIsEmpty(warehouseId, true);
    }

    @Transactional
    @CacheEvict(value = "warehouseBins", allEntries = true)
    public WarehouseBin createBin(WarehouseBin bin) {
        bin.setTenantId(TenantContext.getCurrentTenantId());
        return warehouseBinRepository.save(bin);
    }

    @Transactional
    @CacheEvict(value = "warehouseBins", allEntries = true)
    public WarehouseBin reserveBin(UUID id) {
        WarehouseBin bin = warehouseBinRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("WarehouseBin", id));
        bin.setIsReserved(true);
        return warehouseBinRepository.save(bin);
    }

    @Transactional
    @CacheEvict(value = "warehouseBins", allEntries = true)
    public WarehouseBin releaseBin(UUID id) {
        WarehouseBin bin = warehouseBinRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("WarehouseBin", id));
        bin.setIsReserved(false);
        bin.setIsEmpty(true);
        return warehouseBinRepository.save(bin);
    }

    // ---- Warehouse Staff ----

    @Cacheable(value = "warehouseStaff", key = "#warehouseId")
    public List<WarehouseStaff> getStaff(UUID warehouseId) {
        return warehouseStaffRepository.findByWarehouseId(warehouseId);
    }

    @Transactional
    @CacheEvict(value = "warehouseStaff", allEntries = true)
    public WarehouseStaff createStaff(WarehouseStaff staff) {
        staff.setTenantId(TenantContext.getCurrentTenantId());
        return warehouseStaffRepository.save(staff);
    }

    @Transactional
    @CacheEvict(value = "warehouseStaff", allEntries = true)
    public WarehouseStaff updateStaff(UUID id, WarehouseStaff updates) {
        WarehouseStaff existing = warehouseStaffRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("WarehouseStaff", id));

        if (updates.getWarehouseId() != null) existing.setWarehouseId(updates.getWarehouseId());
        if (updates.getEmployeeCode() != null) existing.setEmployeeCode(updates.getEmployeeCode());
        if (updates.getFirstName() != null) existing.setFirstName(updates.getFirstName());
        if (updates.getLastName() != null) existing.setLastName(updates.getLastName());
        if (updates.getRole() != null) existing.setRole(updates.getRole());
        if (updates.getShift() != null) existing.setShift(updates.getShift());
        if (updates.getSkills() != null) existing.setSkills(updates.getSkills());
        if (updates.getProductivityScore() != null) existing.setProductivityScore(updates.getProductivityScore());
        if (updates.getItemsPickedToday() != null) existing.setItemsPickedToday(updates.getItemsPickedToday());
        if (updates.getItemsPackedToday() != null) existing.setItemsPackedToday(updates.getItemsPackedToday());
        if (updates.getIsActive() != null) existing.setIsActive(updates.getIsActive());
        if (updates.getCertExpiresAt() != null) existing.setCertExpiresAt(updates.getCertExpiresAt());

        return warehouseStaffRepository.save(existing);
    }

    @Transactional
    @CacheEvict(value = "warehouseStaff", allEntries = true)
    public WarehouseStaff incrementPickCount(UUID id) {
        WarehouseStaff staff = warehouseStaffRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("WarehouseStaff", id));
        staff.setItemsPickedToday(staff.getItemsPickedToday() == null ? 1 : staff.getItemsPickedToday() + 1);
        return warehouseStaffRepository.save(staff);
    }

    // ---- Warehouse Equipment ----

    @Cacheable(value = "warehouseEquipment", key = "#warehouseId")
    public List<WarehouseEquipment> getEquipment(UUID warehouseId) {
        return warehouseEquipmentRepository.findByWarehouseId(warehouseId);
    }

    @Transactional
    @CacheEvict(value = "warehouseEquipment", allEntries = true)
    public WarehouseEquipment createEquipment(WarehouseEquipment equipment) {
        equipment.setTenantId(TenantContext.getCurrentTenantId());
        return warehouseEquipmentRepository.save(equipment);
    }

    @Transactional
    @CacheEvict(value = "warehouseEquipment", allEntries = true)
    public WarehouseEquipment updateEquipmentStatus(UUID id, String status) {
        WarehouseEquipment equipment = warehouseEquipmentRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("WarehouseEquipment", id));
        equipment.setStatus(status);
        return warehouseEquipmentRepository.save(equipment);
    }

    // ---- Dashboard / Summary ----

    @Cacheable(value = "warehouses", key = "'summary:' + #warehouseId")
    public Map<String, Object> getWarehouseSummary(UUID warehouseId) {
        List<WarehouseBin> allBins = warehouseBinRepository.findByWarehouseId(warehouseId);
        long emptyBins = warehouseBinRepository.countByWarehouseIdAndIsEmpty(warehouseId, true);
        long totalBins = allBins.size();
        long occupiedBins = totalBins - emptyBins;

        List<WarehouseStaff> allStaff = warehouseStaffRepository.findByWarehouseId(warehouseId);
        long totalStaff = allStaff.size();
        long activeStaff = allStaff.stream().filter(s -> Boolean.TRUE.equals(s.getIsActive())).count();

        List<WarehouseEquipment> allEquipment = warehouseEquipmentRepository.findByWarehouseId(warehouseId);
        long totalEquipment = allEquipment.size();
        long availableEquipment = allEquipment.stream()
                .filter(eq -> "AVAILABLE".equalsIgnoreCase(eq.getStatus()))
                .count();

        double capacityUtilization = totalBins > 0
                ? Math.round(((double) occupiedBins / totalBins) * 100.0 * 100.0) / 100.0
                : 0.0;

        Map<String, Object> summary = new HashMap<>();
        summary.put("totalBins", totalBins);
        summary.put("emptyBins", emptyBins);
        summary.put("occupiedBins", occupiedBins);
        summary.put("totalStaff", totalStaff);
        summary.put("activeStaff", activeStaff);
        summary.put("totalEquipment", totalEquipment);
        summary.put("availableEquipment", availableEquipment);
        summary.put("capacityUtilization", capacityUtilization);
        return summary;
    }
}
