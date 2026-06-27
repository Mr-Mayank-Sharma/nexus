package com.nexus.oms.service;

import com.nexus.oms.repository.OrderRepository;
import org.springframework.cache.annotation.Cacheable;
import org.springframework.stereotype.Service;

import java.util.*;

@Service
public class DashboardService {

    private final OrderRepository orderRepository;

    public DashboardService(OrderRepository orderRepository) {
        this.orderRepository = orderRepository;
    }

    @Cacheable(value = "dashboard", key = "#tenantId")
    public Map<String, Object> getKPIs(UUID tenantId) {
        Map<String, Object> kpis = new LinkedHashMap<>();
        kpis.put("totalOrders", orderRepository.countByTenantIdAndStatusNot(tenantId, "CANCELLED"));
        kpis.put("pending", orderRepository.countByTenantIdAndStatus(tenantId, "PENDING"));
        kpis.put("confirmed", orderRepository.countByTenantIdAndStatus(tenantId, "CONFIRMED"));
        kpis.put("allocated", orderRepository.countByTenantIdAndStatus(tenantId, "ALLOCATED"));
        kpis.put("shipped", orderRepository.countByTenantIdAndStatus(tenantId, "SHIPPED"));
        kpis.put("delivered", orderRepository.countByTenantIdAndStatus(tenantId, "DELIVERED"));
        kpis.put("cancelled", orderRepository.countByTenantIdAndStatus(tenantId, "CANCELLED"));
        return kpis;
    }

    public Map<String, Object> getOrderVelocity() {
        Map<String, Object> velocity = new LinkedHashMap<>();
        velocity.put("metric", "orders_per_hour");
        velocity.put("value", 12.5);
        return velocity;
    }

    public Map<String, Object> getExceptions(UUID tenantId) {
        Map<String, Object> exceptions = new LinkedHashMap<>();
        exceptions.put("total", 3);
        exceptions.put("items", List.of(
                Map.of("type", "INVENTORY_SHORTAGE", "orderId", "sample-uuid", "severity", "HIGH"),
                Map.of("type", "CARRIER_FAILURE", "orderId", "sample-uuid-2", "severity", "MEDIUM")
        ));
        return exceptions;
    }
}
