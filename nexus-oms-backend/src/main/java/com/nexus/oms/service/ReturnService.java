package com.nexus.oms.service;

import com.nexus.oms.dto.ReturnResponse;
import com.nexus.oms.entity.NxCustomer;
import com.nexus.oms.entity.NxReturn;
import com.nexus.oms.entity.NxReturnItem;
import com.nexus.oms.exception.ResourceNotFoundException;
import com.nexus.oms.repository.CustomerRepository;
import com.nexus.oms.repository.OrderRepository;
import com.nexus.oms.repository.ReturnItemRepository;
import com.nexus.oms.repository.ReturnRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.*;
import java.util.stream.Collectors;

@Service
public class ReturnService {

    private final ReturnRepository returnRepository;
    private final ReturnItemRepository returnItemRepository;
    private final OrderRepository orderRepository;
    private final CustomerRepository customerRepository;
    private final InventoryService inventoryService;

    public ReturnService(ReturnRepository returnRepository,
                         ReturnItemRepository returnItemRepository,
                         OrderRepository orderRepository,
                         CustomerRepository customerRepository,
                         InventoryService inventoryService) {
        this.returnRepository = returnRepository;
        this.returnItemRepository = returnItemRepository;
        this.orderRepository = orderRepository;
        this.customerRepository = customerRepository;
        this.inventoryService = inventoryService;
    }

    public List<ReturnResponse> getReturns(UUID tenantId, String status) {
        List<NxReturn> returns;
        if (status != null && !status.isBlank()) {
            returns = returnRepository.findByTenantIdAndStatus(tenantId, status.toUpperCase());
        } else {
            returns = returnRepository.findByTenantId(tenantId);
        }
        return returns.stream().map(this::toResponse).collect(Collectors.toList());
    }

    public ReturnResponse getReturn(UUID id) {
        NxReturn nxReturn = returnRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Return", id));
        return toResponse(nxReturn);
    }

    @Transactional
    public ReturnResponse createReturn(NxReturn nxReturn, List<NxReturnItem> items) {
        if (nxReturn.getRmaNumber() == null) {
            nxReturn.setRmaNumber("RMA-" + UUID.randomUUID().toString().substring(0, 8).toUpperCase());
        }
        nxReturn.setStatus("REQUESTED");
        nxReturn = returnRepository.save(nxReturn);
        if (items != null) {
            for (NxReturnItem item : items) {
                item.setReturnId(nxReturn.getId());
                item.setTenantId(nxReturn.getTenantId());
                returnItemRepository.save(item);
            }
        }
        return toResponse(nxReturn);
    }

    @Transactional
    public ReturnResponse approveReturn(UUID id, UUID approvedBy) {
        NxReturn nxReturn = returnRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Return", id));
        nxReturn.setStatus("APPROVED");
        nxReturn.setApprovedAt(LocalDateTime.now());
        nxReturn.setApprovedBy(approvedBy);
        nxReturn = returnRepository.save(nxReturn);

        List<NxReturnItem> items = returnItemRepository.findByReturnId(id);
        for (NxReturnItem item : items) {
            item.setStatus("APPROVED");
            returnItemRepository.save(item);
        }
        return toResponse(nxReturn);
    }

    @Transactional
    public ReturnResponse receiveReturn(UUID id, UUID receivedBy) {
        NxReturn nxReturn = returnRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Return", id));
        nxReturn.setStatus("RECEIVED");
        nxReturn.setReceivedAt(LocalDateTime.now());
        nxReturn.setReceivedBy(receivedBy);
        nxReturn = returnRepository.save(nxReturn);

        List<NxReturnItem> items = returnItemRepository.findByReturnId(id);
        for (NxReturnItem item : items) {
            item.setStatus("RECEIVED");
            returnItemRepository.save(item);
        }
        return toResponse(nxReturn);
    }

    @Transactional
    public ReturnResponse inspectReturn(UUID id, List<NxReturnItem> inspectedItems, UUID inspectedBy) {
        NxReturn nxReturn = returnRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Return", id));

        for (NxReturnItem inspected : inspectedItems) {
            NxReturnItem item = returnItemRepository.findById(inspected.getId())
                    .orElseThrow(() -> new ResourceNotFoundException("ReturnItem", inspected.getId()));
            item.setCondition(inspected.getCondition());
            item.setConditionNotes(inspected.getConditionNotes());
            item.setGrade(inspected.getGrade());
            item.setDisposition(inspected.getDisposition());
            item.setRefundAmount(inspected.getRefundAmount());
            item.setStatus("INSPECTED");
            item.setInspectedAt(LocalDateTime.now());
            item.setInspectedBy(inspectedBy);
            returnItemRepository.save(item);
        }

        List<NxReturnItem> allItems = returnItemRepository.findByReturnId(id);
        boolean allInspected = allItems.stream().allMatch(i -> "INSPECTED".equals(i.getStatus()) || "REJECTED".equals(i.getStatus()));
        if (allInspected) {
            nxReturn.setStatus("INSPECTED");
        }
        nxReturn.setInspectedAt(LocalDateTime.now());
        nxReturn = returnRepository.save(nxReturn);
        return toResponse(nxReturn);
    }

    @Transactional
    public ReturnResponse processRefund(UUID id, BigDecimal refundAmount, String refundReference) {
        NxReturn nxReturn = returnRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Return", id));
        nxReturn.setStatus("REFUNDED");
        nxReturn.setRefundAmount(refundAmount);
        nxReturn.setRefundReference(refundReference);
        nxReturn.setRefundedAt(LocalDateTime.now());
        nxReturn = returnRepository.save(nxReturn);

        List<NxReturnItem> items = returnItemRepository.findByReturnId(id);
        for (NxReturnItem item : items) {
            if (!"REJECTED".equals(item.getStatus())) {
                item.setStatus("REFUNDED");
                if (item.getRefundAmount() == null) {
                    item.setRefundAmount(BigDecimal.ZERO);
                }
                returnItemRepository.save(item);
            }
        }
        return toResponse(nxReturn);
    }

    @Transactional
    public ReturnResponse rejectReturn(UUID id, String reason) {
        NxReturn nxReturn = returnRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Return", id));
        nxReturn.setStatus("REJECTED");
        nxReturn.setRejectedReason(reason);
        nxReturn = returnRepository.save(nxReturn);

        List<NxReturnItem> items = returnItemRepository.findByReturnId(id);
        for (NxReturnItem item : items) {
            item.setStatus("REJECTED");
            returnItemRepository.save(item);
        }
        return toResponse(nxReturn);
    }

    @Transactional
    public ReturnResponse updateReturnStatus(UUID id, String status) {
        NxReturn nxReturn = returnRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Return", id));
        String newStatus = status.toUpperCase();
        nxReturn.setStatus(newStatus);
        if ("CANCELLED".equals(newStatus) || "REJECTED".equals(newStatus)) {
            nxReturn.setRejectedReason("Status updated to " + newStatus);
        }
        nxReturn = returnRepository.save(nxReturn);
        return toResponse(nxReturn);
    }

    /**
     * RMA disposition action. Applies the disposition decision per inspected
     * item and links it to the warehouse consequence:
     * RESTOCK   -> increments sellable inventory back by quantity
     * REFURBISH -> increments inventory (condition = refurbished)
     * SCRAP     -> no inventory change, item marked scrapped
     * Also computes a default refund amount from the item's original
     * refundable value when a disposition is provided, keeping the RMA and
     * refund records linked.
     */
    @Transactional
    public List<NxReturnItem> applyDisposition(UUID returnId, List<NxReturnItem> decisions) {
        List<NxReturnItem> updated = new ArrayList<>();
        for (NxReturnItem decision : decisions) {
            NxReturnItem item = returnItemRepository.findById(decision.getId())
                    .orElseThrow(() -> new ResourceNotFoundException("ReturnItem", decision.getId()));
            if (decision.getDisposition() == null) {
                throw new IllegalArgumentException("Disposition is required for item " + item.getId());
            }
            String disposition = decision.getDisposition().toUpperCase();
            item.setDisposition(disposition);
            item.setDispositionNotes(decision.getDispositionNotes());
            item.setStatus("DISPOSED");
            item.setDisposedAt(LocalDateTime.now());
            item.setDisposedBy(decision.getDisposedBy());

            switch (disposition) {
                case "RESTOCK", "REPACKAGE" -> {
                    int qty = item.getQuantity() != null ? item.getQuantity() : 0;
                    if (qty > 0) {
                        inventoryService.adjustInventoryBySku(item.getTenantId(), item.getSku(), qty);
                    }
                }
                case "REFURBISH" -> {
                    item.setCondition("REFURBISHED");
                    int qty = item.getQuantity() != null ? item.getQuantity() : 0;
                    if (qty > 0) {
                        inventoryService.adjustInventoryBySku(item.getTenantId(), item.getSku(), qty);
                    }
                }
                case "SCRAP", "DONATE" -> {
                    item.setCondition("SCRAPPED");
                }
                default -> throw new IllegalArgumentException(
                        "Unsupported disposition: " + disposition + " (expected RESTOCK/REPACKAGE/REFURBISH/SCRAP/DONATE)");
            }

            if (item.getRefundAmount() == null && item.getOriginalPrice() != null) {
                item.setRefundAmount(switch (disposition) {
                    case "RESTOCK", "REPACKAGE" -> item.getOriginalPrice().multiply(BigDecimal.valueOf(item.getQuantity() == null ? 1 : item.getQuantity()));
                    case "REFURBISH" -> item.getOriginalPrice().multiply(BigDecimal.valueOf(0.8))
                            .multiply(BigDecimal.valueOf(item.getQuantity() == null ? 1 : item.getQuantity()));
                    default -> BigDecimal.ZERO;
                });
            }
            returnItemRepository.save(item);
            updated.add(item);
        }
        return updated;
    }

    @Transactional
    public ReturnResponse settleRefund(UUID id, BigDecimal totalRefund, String refundReference) {
        NxReturn nxReturn = returnRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Return", id));
        List<NxReturnItem> items = returnItemRepository.findByReturnId(id);
        if (totalRefund == null) {
            totalRefund = items.stream()
                    .filter(i -> i.getRefundAmount() != null)
                    .map(NxReturnItem::getRefundAmount)
                    .reduce(BigDecimal.ZERO, BigDecimal::add);
        }
        nxReturn.setStatus("REFUNDED");
        nxReturn.setRefundAmount(totalRefund);
        nxReturn.setRefundReference(refundReference);
        nxReturn.setRefundedAt(LocalDateTime.now());
        nxReturn = returnRepository.save(nxReturn);

        for (NxReturnItem item : items) {
            if (!"REJECTED".equals(item.getStatus()) && !"SCRAP".equals(item.getDisposition())) {
                item.setStatus("REFUNDED");
                returnItemRepository.save(item);
            }
        }
        return toResponse(nxReturn);
    }

    public List<NxReturnItem> getReturnItems(UUID returnId) {
        return returnItemRepository.findByReturnId(returnId);
    }

    public Map<String, Object> getReturnKPIs(UUID tenantId) {
        List<NxReturn> allReturns = returnRepository.findByTenantId(tenantId);
        int total = allReturns.size();
        long pending = allReturns.stream().filter(r -> "REQUESTED".equals(r.getStatus())).count();
        long approved = allReturns.stream().filter(r -> "APPROVED".equals(r.getStatus())).count();
        long received = allReturns.stream().filter(r -> "RECEIVED".equals(r.getStatus())).count();
        long inspected = allReturns.stream().filter(r -> "INSPECTED".equals(r.getStatus())).count();
        long refunded = allReturns.stream().filter(r -> "REFUNDED".equals(r.getStatus())).count();
        long rejected = allReturns.stream().filter(r -> "REJECTED".equals(r.getStatus())).count();

        Map<String, Object> kpis = new HashMap<>();
        kpis.put("total", total);
        kpis.put("pending", pending);
        kpis.put("approved", approved);
        kpis.put("received", received);
        kpis.put("inspected", inspected);
        kpis.put("refunded", refunded);
        kpis.put("rejected", rejected);
        return kpis;
    }

    public List<Map<String, Object>> getReturnReasons(UUID tenantId) {
        Map<String, Long> reasonCounts = returnRepository.findByTenantId(tenantId).stream()
                .filter(r -> r.getReason() != null)
                .collect(Collectors.groupingBy(NxReturn::getReason, Collectors.counting()));
        return reasonCounts.entrySet().stream()
                .map(e -> {
                    Map<String, Object> m = new HashMap<>();
                    m.put("reason", e.getKey());
                    m.put("count", e.getValue());
                    return m;
                })
                .sorted((a, b) -> Long.compare((Long) b.get("count"), (Long) a.get("count")))
                .collect(Collectors.toList());
    }

    public Map<String, Object> getReturnsAnalytics(UUID tenantId) {
        List<NxReturn> all = returnRepository.findByTenantId(tenantId);
        long total = all.size();
        long totalOrders = orderRepository.countByTenantIdAndStatusNot(tenantId, "CANCELLED");
        double returnRate = totalOrders > 0 ? (double) total / totalOrders : 0.0;

        BigDecimal refundSum = all.stream()
                .filter(r -> r.getRefundAmount() != null)
                .map(NxReturn::getRefundAmount)
                .reduce(BigDecimal.ZERO, BigDecimal::add);
        long refundedCount = all.stream().filter(r -> r.getRefundAmount() != null).count();
        double avgRefund = refundedCount > 0 ? refundSum.doubleValue() / refundedCount : 0.0;

        Map<String, Object> m = new HashMap<>();
        m.put("totalReturns", total);
        m.put("returnRate", Math.round(returnRate * 1000) / 1000.0);
        m.put("avgRefundAmount", Math.round(avgRefund * 100) / 100.0);
        m.put("topReasons", getReturnReasons(tenantId));
        return m;
    }

    private ReturnResponse toResponse(NxReturn nxReturn) {
        NxCustomer customer = nxReturn.getCustomerId() != null
                ? customerRepository.findById(nxReturn.getCustomerId()).orElse(null)
                : null;
        String customerName = customer != null ? customer.getName() : "Unknown";
        String customerEmail = customer != null ? customer.getEmail() : null;

        List<NxReturnItem> items = returnItemRepository.findByReturnId(nxReturn.getId());

        return ReturnResponse.builder()
                .id(nxReturn.getId())
                .rmaNumber(nxReturn.getRmaNumber() != null ? nxReturn.getRmaNumber() :
                        "RMA-" + nxReturn.getId().toString().substring(0, 8).toUpperCase())
                .orderId(nxReturn.getOrderId())
                .customerName(customerName)
                .customerEmail(customerEmail)
                .status(nxReturn.getStatus())
                .reason(nxReturn.getReason())
                .grade(nxReturn.getGrade())
                .disposition(nxReturn.getDisposition())
                .refundAmount(nxReturn.getRefundAmount())
                .refundStatus(nxReturn.getRefundReference() != null ? "COMPLETED" :
                        "REFUNDED".equals(nxReturn.getStatus()) ? "PROCESSED" : "PENDING")
                .items(items != null ? items.stream().map(this::toItemMap).collect(Collectors.toList()) : Collections.emptyList())
                .createdAt(nxReturn.getCreatedAt())
                .updatedAt(nxReturn.getUpdatedAt())
                .build();
    }

    private Map<String, Object> toItemMap(NxReturnItem item) {
        Map<String, Object> m = new HashMap<>();
        m.put("id", item.getId());
        m.put("sku", item.getSku());
        m.put("productName", item.getProductName());
        m.put("quantity", item.getQuantity());
        m.put("returnReason", item.getReturnReason());
        m.put("condition", item.getCondition());
        m.put("conditionNotes", item.getConditionNotes());
        m.put("grade", item.getGrade());
        m.put("disposition", item.getDisposition());
        m.put("refundAmount", item.getRefundAmount());
        m.put("status", item.getStatus());
        return m;
    }
}
