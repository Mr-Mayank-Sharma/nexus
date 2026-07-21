package com.nexus.oms.service;

import com.nexus.oms.entity.NxEndlessAisleOrder;
import com.nexus.oms.exception.BadRequestException;
import com.nexus.oms.exception.ResourceNotFoundException;
import com.nexus.oms.repository.EndlessAisleOrderRepository;
import com.nexus.oms.security.TenantContext;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.*;
import java.util.stream.Collectors;

@Service
public class EndlessAisleService {

    private static final Logger log = LoggerFactory.getLogger(EndlessAisleService.class);

    private final EndlessAisleOrderRepository endlessAisleOrderRepository;

    public EndlessAisleService(EndlessAisleOrderRepository endlessAisleOrderRepository) {
        this.endlessAisleOrderRepository = endlessAisleOrderRepository;
    }

    // ─── CRUD Operations ────────────────────────────────────────────────

    @Transactional
    public NxEndlessAisleOrder createOrder(NxEndlessAisleOrder order) {
        UUID tenantId = TenantContext.getCurrentTenantId();
        order.setTenantId(tenantId);

        if (order.getQuantity() == null || order.getQuantity() <= 0) {
            throw new BadRequestException("Quantity must be greater than 0");
        }
        if (order.getUnitPrice() == null || order.getUnitPrice().compareTo(BigDecimal.ZERO) <= 0) {
            throw new BadRequestException("Unit price must be greater than 0");
        }

        order.setTotalAmount(order.getUnitPrice().multiply(BigDecimal.valueOf(order.getQuantity())));
        order.setStatus("PENDING");

        log.info("Endless Aisle order created: store={}, sku={}, qty={}", order.getStoreId(), order.getProductSku(), order.getQuantity());
        return endlessAisleOrderRepository.save(order);
    }

    @Transactional
    public NxEndlessAisleOrder updateOrder(UUID id, NxEndlessAisleOrder updates) {
        NxEndlessAisleOrder order = endlessAisleOrderRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("EndlessAisleOrder", id));

        if (updates.getCustomerName() != null) order.setCustomerName(updates.getCustomerName());
        if (updates.getCustomerEmail() != null) order.setCustomerEmail(updates.getCustomerEmail());
        if (updates.getCustomerPhone() != null) order.setCustomerPhone(updates.getCustomerPhone());
        if (updates.getFulfillmentType() != null) order.setFulfillmentType(updates.getFulfillmentType());
        if (updates.getShipToAddress() != null) order.setShipToAddress(updates.getShipToAddress());
        if (updates.getNotes() != null) order.setNotes(updates.getNotes());

        if (updates.getQuantity() != null && updates.getQuantity() > 0) {
            order.setQuantity(updates.getQuantity());
            order.setTotalAmount(order.getUnitPrice().multiply(BigDecimal.valueOf(order.getQuantity())));
        }

        return endlessAisleOrderRepository.save(order);
    }

    public List<NxEndlessAisleOrder> getOrders() {
        UUID tenantId = TenantContext.getCurrentTenantId();
        return endlessAisleOrderRepository.findByTenantIdOrderByCreatedAtDesc(tenantId);
    }

    public NxEndlessAisleOrder getOrder(UUID id) {
        return endlessAisleOrderRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("EndlessAisleOrder", id));
    }

    @Transactional
    public void deleteOrder(UUID id) {
        NxEndlessAisleOrder order = endlessAisleOrderRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("EndlessAisleOrder", id));

        if ("SHIPPED".equals(order.getStatus()) || "DELIVERED".equals(order.getStatus())) {
            throw new BadRequestException("Cannot delete order that has been shipped or delivered");
        }

        endlessAisleOrderRepository.deleteById(id);
    }

    // ─── Status Operations ──────────────────────────────────────────────

    @Transactional
    public NxEndlessAisleOrder updateStatus(UUID id, String newStatus, String notes) {
        NxEndlessAisleOrder order = endlessAisleOrderRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("EndlessAisleOrder", id));

        String currentStatus = order.getStatus();
        validateStatusTransition(currentStatus, newStatus);

        order.setStatus(newStatus);
        if (notes != null && !notes.isBlank()) {
            order.setNotes(notes);
        }

        log.info("Endless Aisle order status updated: id={}, {} -> {}", id, currentStatus, newStatus);
        return endlessAisleOrderRepository.save(order);
    }

    private void validateStatusTransition(String from, String to) {
        Map<String, Set<String>> validTransitions = Map.of(
            "PENDING", Set.of("CONFIRMED", "CANCELLED"),
            "CONFIRMED", Set.of("PROCESSING", "CANCELLED"),
            "PROCESSING", Set.of("SHIPPED", "CANCELLED"),
            "SHIPPED", Set.of("DELIVERED"),
            "DELIVERED", Set.of(),
            "CANCELLED", Set.of()
        );

        if (!validTransitions.getOrDefault(from, Set.of()).contains(to)) {
            throw new BadRequestException("Invalid status transition: " + from + " -> " + to);
        }
    }

    // ─── Queries ────────────────────────────────────────────────────────

    public List<NxEndlessAisleOrder> getOrdersByStatus(String status) {
        UUID tenantId = TenantContext.getCurrentTenantId();
        return endlessAisleOrderRepository.findByTenantIdAndStatusOrderByCreatedAtDesc(tenantId, status);
    }

    public List<NxEndlessAisleOrder> getOrdersByStore(UUID storeId) {
        return endlessAisleOrderRepository.findByStoreIdOrderByCreatedAtDesc(storeId);
    }

    public List<NxEndlessAisleOrder> getOrdersByCustomer(UUID customerId) {
        return endlessAisleOrderRepository.findByCustomerIdOrderByCreatedAtDesc(customerId);
    }

    // ─── Analytics ──────────────────────────────────────────────────────

    public Map<String, Object> getEndlessAisleStats() {
        UUID tenantId = TenantContext.getCurrentTenantId();

        List<NxEndlessAisleOrder> allOrders = endlessAisleOrderRepository.findByTenantIdOrderByCreatedAtDesc(tenantId);

        long totalOrders = allOrders.size();
        long pendingOrders = allOrders.stream().filter(o -> "PENDING".equals(o.getStatus())).count();
        long processingOrders = allOrders.stream().filter(o -> "PROCESSING".equals(o.getStatus()) || "CONFIRMED".equals(o.getStatus())).count();
        long shippedOrders = allOrders.stream().filter(o -> "SHIPPED".equals(o.getStatus())).count();
        long deliveredOrders = allOrders.stream().filter(o -> "DELIVERED".equals(o.getStatus())).count();
        long cancelledOrders = allOrders.stream().filter(o -> "CANCELLED".equals(o.getStatus())).count();

        BigDecimal totalRevenue = allOrders.stream()
                .filter(o -> !"CANCELLED".equals(o.getStatus()))
                .map(NxEndlessAisleOrder::getTotalAmount)
                .reduce(BigDecimal.ZERO, BigDecimal::add);

        Map<String, Object> stats = new LinkedHashMap<>();
        stats.put("totalOrders", totalOrders);
        stats.put("pendingOrders", pendingOrders);
        stats.put("processingOrders", processingOrders);
        stats.put("shippedOrders", shippedOrders);
        stats.put("deliveredOrders", deliveredOrders);
        stats.put("cancelledOrders", cancelledOrders);
        stats.put("totalRevenue", totalRevenue);

        return stats;
    }
}
