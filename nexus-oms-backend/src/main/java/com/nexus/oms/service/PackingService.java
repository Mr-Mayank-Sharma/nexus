package com.nexus.oms.service;

import com.nexus.oms.entity.NxPackage;
import com.nexus.oms.entity.WarehouseStaff;
import com.nexus.oms.exception.ResourceNotFoundException;
import com.nexus.oms.repository.PackageRepository;
import com.nexus.oms.repository.WarehouseStaffRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.*;

@Service
public class PackingService {

    private final PackageRepository packageRepository;
    private final WarehouseStaffRepository warehouseStaffRepository;

    public PackingService(PackageRepository packageRepository,
                          WarehouseStaffRepository warehouseStaffRepository) {
        this.packageRepository = packageRepository;
        this.warehouseStaffRepository = warehouseStaffRepository;
    }

    public List<NxPackage> getPackages(UUID tenantId) {
        return packageRepository.findByTenantId(tenantId);
    }

    public List<NxPackage> getPackagesByStatus(UUID tenantId, String status) {
        return packageRepository.findByTenantIdAndStatus(tenantId, status);
    }

    public NxPackage getPackage(UUID id) {
        return packageRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Package", id));
    }

    @Transactional
    public NxPackage createPackage(NxPackage pkg) {
        pkg.setStatus("PENDING_PACK");
        return packageRepository.save(pkg);
    }

    @Transactional
    public NxPackage startPacking(UUID packageId) {
        NxPackage pkg = getPackage(packageId);
        pkg.setStatus("PACKING");
        return packageRepository.save(pkg);
    }

    @Transactional
    public NxPackage addItem(UUID packageId, String itemJson) {
        NxPackage pkg = getPackage(packageId);
        String existing = pkg.getItems();
        if (existing == null || existing.isBlank()) {
            pkg.setItems("[" + itemJson + "]");
        } else {
            pkg.setItems(existing.substring(0, existing.length() - 1) + "," + itemJson + "]");
        }
        pkg.setItemCount((pkg.getItemCount() == null ? 0 : pkg.getItemCount()) + 1);
        return packageRepository.save(pkg);
    }

    @Transactional
    public NxPackage completePacking(UUID packageId, String packedBy) {
        NxPackage pkg = getPackage(packageId);
        pkg.setStatus("PACKED");
        pkg.setPackedBy(packedBy);
        pkg.setPackedAt(LocalDateTime.now());
        return packageRepository.save(pkg);
    }

    @Transactional
    public NxPackage generateLabel(UUID packageId, String carrierId, String carrierName,
                                    String serviceLevel, String trackingNumber, String labelUrl) {
        NxPackage pkg = getPackage(packageId);
        pkg.setCarrierId(UUID.fromString(carrierId));
        pkg.setCarrierName(carrierName);
        pkg.setServiceLevel(serviceLevel);
        pkg.setTrackingNumber(trackingNumber);
        pkg.setLabelUrl(labelUrl);
        pkg.setStatus("LABELED");
        return packageRepository.save(pkg);
    }

    @Transactional
    public NxPackage shipPackage(UUID packageId) {
        NxPackage pkg = getPackage(packageId);
        pkg.setStatus("SHIPPED");
        pkg.setShippedAt(LocalDateTime.now());
        return packageRepository.save(pkg);
    }

    @Transactional
    public NxPackage voidPackage(UUID packageId) {
        NxPackage pkg = getPackage(packageId);
        pkg.setStatus("VOIDED");
        return packageRepository.save(pkg);
    }

    public Map<String, Object> getDashboardKPIs(UUID tenantId) {
        long pendingPack = packageRepository.countByTenantIdAndStatus(tenantId, "PENDING_PACK");
        long packing = packageRepository.countByTenantIdAndStatus(tenantId, "PACKING");
        long packed = packageRepository.countByTenantIdAndStatus(tenantId, "PACKED");
        long shipped = packageRepository.countByTenantIdAndStatus(tenantId, "SHIPPED");

        Map<String, Object> kpis = new HashMap<>();
        kpis.put("pendingPack", pendingPack);
        kpis.put("packing", packing);
        kpis.put("packed", packed);
        kpis.put("shipped", shipped);
        return kpis;
    }
}
