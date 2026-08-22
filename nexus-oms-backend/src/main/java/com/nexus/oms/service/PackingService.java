package com.nexus.oms.service;

import com.nexus.oms.entity.NxOrderItem;
import com.nexus.oms.entity.NxPackage;
import com.nexus.oms.entity.WarehouseStaff;
import com.nexus.oms.exception.ResourceNotFoundException;
import com.nexus.oms.repository.OrderItemRepository;
import com.nexus.oms.repository.OrderRepository;
import com.nexus.oms.repository.PackageRepository;
import com.nexus.oms.repository.WarehouseStaffRepository;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.*;

@Service
public class PackingService {

    private final PackageRepository packageRepository;
    private final WarehouseStaffRepository warehouseStaffRepository;
    private final BoxRecommendationService boxRecommendationService;
    private final OrderRepository orderRepository;
    private final OrderItemRepository orderItemRepository;
    private final com.nexus.oms.service.bigcommerce.BigCommerceOrderStatusPushService bigCommerceOrderStatusPushService;

    public PackingService(PackageRepository packageRepository,
                          WarehouseStaffRepository warehouseStaffRepository,
                          BoxRecommendationService boxRecommendationService,
                          OrderRepository orderRepository,
                          OrderItemRepository orderItemRepository,
                          com.nexus.oms.service.bigcommerce.BigCommerceOrderStatusPushService bigCommerceOrderStatusPushService) {
        this.packageRepository = packageRepository;
        this.warehouseStaffRepository = warehouseStaffRepository;
        this.boxRecommendationService = boxRecommendationService;
        this.orderRepository = orderRepository;
        this.orderItemRepository = orderItemRepository;
        this.bigCommerceOrderStatusPushService = bigCommerceOrderStatusPushService;
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
        if (pkg.getOrderId() != null && (pkg.getItems() == null || pkg.getItems().isBlank())) {
            List<NxOrderItem> orderItems = orderItemRepository.findByOrderId(pkg.getOrderId());
            if (!orderItems.isEmpty()) {
                List<Map<String, Object>> itemList = new ArrayList<>();
                for (NxOrderItem oi : orderItems) {
                    Map<String, Object> m = new HashMap<>();
                    m.put("orderItemId", oi.getId().toString());
                    m.put("sku", oi.getSku());
                    m.put("productName", oi.getProductName());
                    m.put("quantity", oi.getQuantity() != null ? oi.getQuantity() : 1);
                    itemList.add(m);
                }
                try {
                    pkg.setItems(new ObjectMapper().writeValueAsString(itemList));
                    pkg.setItemCount(itemList.size());
                } catch (JsonProcessingException ignored) {
                }
            }
        }
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
    public Map<String, Object> recommendBox(UUID tenantId, UUID packageId) {
        Map<String, Object> recommendation = boxRecommendationService.recommendForPackage(tenantId, packageId);
        if (!"NO_FIT".equals(recommendation.get("boxName"))) {
            NxPackage pkg = getPackage(packageId);
            pkg.setBoxName((String) recommendation.get("boxName"));
            String dimensions = (String) recommendation.get("dimensions");
            if (dimensions != null) {
                String[] parts = dimensions.replace(" in", "").split("x");
                if (parts.length == 3) {
                    try {
                        pkg.setWidthIn(Double.parseDouble(parts[0]));
                        pkg.setDepthIn(Double.parseDouble(parts[1]));
                        pkg.setHeightIn(Double.parseDouble(parts[2]));
                    } catch (NumberFormatException ignored) {
                    }
                }
            }
            packageRepository.save(pkg);
        }
        return recommendation;
    }

    public Map<String, Object> validatePack(UUID tenantId, UUID packageId) {
        NxPackage pkg = getPackage(packageId);
        int orderedQty = orderItemRepository.findByOrderId(pkg.getOrderId()).stream()
                .mapToInt(i -> i.getQuantity() != null ? i.getQuantity() : 0)
                .sum();
        int packedQty = pkg.getItemCount() != null ? pkg.getItemCount() : 0;

        Map<String, Object> result = new HashMap<>();
        result.put("packageId", packageId.toString());
        result.put("orderId", pkg.getOrderId().toString());
        result.put("orderedQty", orderedQty);
        result.put("packedQty", packedQty);
        if (packedQty == orderedQty) {
            result.put("valid", true);
            result.put("message", "Packed quantity matches order quantity");
        } else if (packedQty < orderedQty) {
            result.put("valid", false);
            result.put("message", "Under-packed: " + (orderedQty - packedQty) + " item(s) missing");
        } else {
            result.put("valid", false);
            result.put("message", "Over-packed: " + (packedQty - orderedQty) + " extra item(s)");
        }
        return result;
    }

    @Transactional
    public NxPackage generateLabel(UUID packageId, String carrierId, String carrierName,
                                    String serviceLevel, String trackingNumber, String labelUrl) {
        NxPackage pkg = getPackage(packageId);
        if (carrierId != null && !carrierId.isBlank()) {
            try {
                pkg.setCarrierId(UUID.fromString(carrierId));
            } catch (IllegalArgumentException e) {
                pkg.setCarrierId(UUID.nameUUIDFromBytes(carrierId.getBytes()));
            }
        }
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
        NxPackage saved = packageRepository.save(pkg);

        // Transition the related order to SHIPPED and sync to BigCommerce
        if (pkg.getOrderId() != null) {
            orderRepository.findById(pkg.getOrderId()).ifPresent(order -> {
                if (!"SHIPPED".equals(order.getStatus()) && !"DELIVERED".equals(order.getStatus())) {
                    order.setStatus("SHIPPED");
                    order.setCarrierId(pkg.getCarrierId() != null ? pkg.getCarrierId().toString() : order.getCarrierId());
                    if (pkg.getTrackingNumber() != null) {
                        order.setTrackingNumber(pkg.getTrackingNumber());
                    }
                    order.setShippedAt(LocalDateTime.now());
                    orderRepository.save(order);
                    bigCommerceOrderStatusPushService.pushOrderStatus(order.getTenantId(), order.getExternalId(), "SHIPPED");
                }
            });
        }
        return saved;
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
