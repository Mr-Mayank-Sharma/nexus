package com.nexus.oms.controller;

import com.nexus.oms.dto.ApiResponse;
import com.nexus.oms.entity.NxCustomer;
import com.nexus.oms.entity.NxBillingStatement;
import com.nexus.oms.entity.NxOrder;
import com.nexus.oms.exception.ResourceNotFoundException;
import com.nexus.oms.repository.BillingStatementRepository;
import com.nexus.oms.repository.CustomerRepository;
import com.nexus.oms.repository.OrderItemRepository;
import com.nexus.oms.repository.OrderRepository;
import com.nexus.oms.security.TenantContext;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.*;
import java.util.stream.Collectors;

@Tag(name = "Client Portal", description = "Self-service 3PL client portal dashboards")
@RestController
@RequestMapping("/client-portal")
public class ClientPortalController {

    private static final Set<String> OPEN_STATUSES = Set.of(
            "PENDING", "APPROVED", "ALLOCATED", "RELEASED", "PICKING", "PACKED", "READY");
    private static final Set<String> FULFILLED_STATUSES = Set.of("SHIPPED", "DELIVERED", "COMPLETED");

    private final CustomerRepository customerRepository;
    private final OrderRepository orderRepository;
    private final OrderItemRepository orderItemRepository;
    private final BillingStatementRepository billingStatementRepository;

    public ClientPortalController(CustomerRepository customerRepository,
                                  OrderRepository orderRepository,
                                  OrderItemRepository orderItemRepository,
                                  BillingStatementRepository billingStatementRepository) {
        this.customerRepository = customerRepository;
        this.orderRepository = orderRepository;
        this.orderItemRepository = orderItemRepository;
        this.billingStatementRepository = billingStatementRepository;
    }

    @Operation(summary = "Client overview dashboard (orders, fulfillment, invoices)")
    @GetMapping("/overview")
    public ResponseEntity<ApiResponse<Map<String, Object>>> overview(@RequestParam UUID clientId) {
        UUID tenantId = TenantContext.getCurrentTenantId();
        NxCustomer client = customerRepository.findById(clientId)
                .filter(c -> c.getTenantId().equals(tenantId))
                .orElseThrow(() -> new ResourceNotFoundException("Customer", clientId));

        LocalDateTime from = LocalDate.now().minusDays(30).atStartOfDay();
        List<NxOrder> orders = orderRepository.findByTenantIdAndCustomerIdAndCreatedAtBetween(tenantId, clientId, from, LocalDateTime.now());

        int open = 0;
        int fulfilled = 0;
        int unitsOrdered = 0;
        Map<String, Long> byStatus = new TreeMap<>();
        for (NxOrder order : orders) {
            String status = order.getStatus() == null ? "UNKNOWN" : order.getStatus().toUpperCase();
            byStatus.merge(status, 1L, Long::sum);
            if (OPEN_STATUSES.contains(status)) open++;
            if (FULFILLED_STATUSES.contains(status)) fulfilled++;
        }
        List<UUID> orderIds = orders.stream().map(NxOrder::getId).toList();
        if (!orderIds.isEmpty()) {
            for (var item : orderItemRepository.findByOrderIdIn(orderIds)) {
                unitsOrdered += item.getQuantity() == null ? 0 : item.getQuantity();
            }
        }

        List<NxBillingStatement> issued = billingStatementRepository
                .findByTenantIdAndClientIdAndStatus(tenantId, clientId, "ISSUED");
        BigDecimal outstanding = issued.stream()
                .map(NxBillingStatement::getTotal)
                .filter(Objects::nonNull)
                .reduce(BigDecimal.ZERO, BigDecimal::add);

        List<Map<String, Object>> recentOrders = orders.stream()
                .sorted(Comparator.comparing(NxOrder::getCreatedAt).reversed())
                .limit(10)
                .map(o -> Map.<String, Object>of(
                        "id", o.getId(),
                        "orderNumber", o.getChannelOrderId() != null ? o.getChannelOrderId()
                                : (o.getExternalId() != null ? o.getExternalId() : o.getId().toString()),
                        "status", o.getStatus(),
                        "totalAmount", o.getTotal() == null ? BigDecimal.ZERO : o.getTotal(),
                        "createdAt", o.getCreatedAt() == null ? "" : o.getCreatedAt().toString()))
                .collect(Collectors.toList());

        Map<String, Object> summary = new LinkedHashMap<>();
        summary.put("client", Map.of("id", client.getId(), "name", client.getName(),
                "email", client.getEmail() == null ? "" : client.getEmail()));
        summary.put("ordersLast30d", orders.size());
        summary.put("openOrders", open);
        summary.put("fulfilledOrders", fulfilled);
        summary.put("unitsOrdered", unitsOrdered);
        summary.put("ordersByStatus", byStatus);
        summary.put("outstandingInvoices", issued.size());
        summary.put("outstandingBalance", outstanding);
        summary.put("recentOrders", recentOrders);

        return ResponseEntity.ok(ApiResponse.success(summary));
    }
}
