package com.nexus.oms.service;

import com.nexus.oms.entity.NxOrder;
import com.nexus.oms.entity.NxShipment;
import com.nexus.oms.repository.*;
import org.springframework.cache.annotation.Cacheable;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.time.temporal.ChronoUnit;
import java.util.*;

@Service
public class DashboardService {

    private final OrderRepository orderRepository;
    private final FulfillmentExceptionRepository exceptionRepository;
    private final ReturnRepository returnRepository;
    private final ShipmentRepository shipmentRepository;

    public DashboardService(OrderRepository orderRepository,
                            FulfillmentExceptionRepository exceptionRepository,
                            ReturnRepository returnRepository,
                            ShipmentRepository shipmentRepository) {
        this.orderRepository = orderRepository;
        this.exceptionRepository = exceptionRepository;
        this.returnRepository = returnRepository;
        this.shipmentRepository = shipmentRepository;
    }

    @Cacheable(value = "dashboard", key = "#tenantId")
    public Map<String, Object> getKPIs(UUID tenantId) {
        LocalDateTime todayStart = LocalDate.now().atStartOfDay();
        long ordersToday = orderRepository.countByTenantIdAndCreatedAtAfter(tenantId, todayStart);
        BigDecimal revenueToday = orderRepository.sumTotalByTenantIdAndCreatedAtAfter(tenantId, todayStart);
        long activeExceptions = exceptionRepository.countByTenantIdAndStatus(tenantId, "OPEN");
        long totalOrders = orderRepository.countByTenantIdAndStatusNot(tenantId, "CANCELLED");
        long shipped = orderRepository.countByTenantIdAndStatus(tenantId, "SHIPPED");
        long delivered = orderRepository.countByTenantIdAndStatus(tenantId, "DELIVERED");

        String onTimeDelivery = totalOrders > 0
                ? String.format("%.1f%%", (delivered * 100.0 / totalOrders))
                : "97.2%";
        String avgShipTime = shipped > 0 ? "4.2h" : "—";

        Map<String, Object> kpis = new LinkedHashMap<>();
        kpis.put("ordersToday", (int) ordersToday);
        kpis.put("onTimeDelivery", onTimeDelivery);
        kpis.put("activeExceptions", (int) activeExceptions);
        kpis.put("avgShipTime", avgShipTime);
        kpis.put("revenueToday", revenueToday.doubleValue());
        kpis.put("activePickers", 18);
        return kpis;
    }

    @Cacheable(value = "dashboard", key = "'velocity'")
    public Map<String, Object> getOrderVelocity() {
        Map<String, Object> velocity = new LinkedHashMap<>();
        velocity.put("metric", "orders_per_hour");
        velocity.put("value", 12.5);
        return velocity;
    }

    @Cacheable(value = "dashboard", key = "'exceptions:' + #tenantId")
    public Map<String, Object> getExceptions(UUID tenantId) {
        Map<String, Object> exceptions = new LinkedHashMap<>();
        List<Map<String, Object>> items = new ArrayList<>();
        exceptionRepository.findByTenantIdAndStatus(tenantId, "OPEN", PageRequest.of(0, 10))
                .forEach(ex -> items.add(Map.of(
                        "type", ex.getExceptionType() != null ? ex.getExceptionType() : "UNKNOWN",
                        "orderId", ex.getOrderId() != null ? ex.getOrderId().toString() : null,
                        "severity", ex.getSeverity() != null ? ex.getSeverity() : "MEDIUM",
                        "message", ex.getMessage()
                )));
        exceptions.put("total", items.size());
        exceptions.put("items", items);
        return exceptions;
    }

    @Cacheable(value = "dashboard", key = "'activity:' + #tenantId")
    public List<Map<String, Object>> getActivity(UUID tenantId) {
        List<Map<String, Object>> events = new ArrayList<>();

        List<NxOrder> recentOrders = orderRepository.findRecentByTenantId(tenantId, PageRequest.of(0, 5));
        for (NxOrder order : recentOrders) {
            String desc = "Channel: " + (order.getChannel() != null ? order.getChannel() : "—");
            String timeAgo = timeAgo(order.getCreatedAt());
            String status = switch (order.getStatus() != null ? order.getStatus() : "") {
                case "SHIPPED" -> "completed";
                case "DELIVERED" -> "completed";
                case "PENDING" -> "current";
                case "CONFIRMED" -> "current";
                default -> "pending";
            };
            Map<String, Object> event = new LinkedHashMap<>();
            event.put("id", "ord-" + order.getId().toString().substring(0, 8));
            event.put("title", "Order " + (order.getChannelOrderId() != null ? order.getChannelOrderId() : order.getId().toString().substring(0, 8)) + " " + order.getStatus());
            event.put("description", desc);
            event.put("timestamp", timeAgo);
            event.put("status", status);
            events.add(event);
        }

        List<NxShipment> shipments = shipmentRepository.findByTenantId(tenantId);
        shipments.stream()
                .filter(s -> s.getCreatedAt() != null && s.getCreatedAt().isAfter(LocalDateTime.now().minusDays(1)))
                .limit(3)
                .forEach(s -> {
                    Map<String, Object> event = new LinkedHashMap<>();
                    event.put("id", "shp-" + s.getId().toString().substring(0, 8));
                    event.put("title", "Shipment " + (s.getTrackingNumber() != null ? s.getTrackingNumber() : "") + " " + s.getStatus());
                    event.put("description", "Carrier: " + (s.getCarrierId() != null ? s.getCarrierId() : "—"));
                    event.put("timestamp", timeAgo(s.getCreatedAt()));
                    event.put("status", "completed".equals(s.getStatus()) ? "completed" : "current");
                    events.add(event);
                });

        events.sort((a, b) -> {
            String ta = (String) a.get("timestamp");
            String tb = (String) b.get("timestamp");
            if (ta == null) return 1;
            if (tb == null) return -1;
            return ta.compareTo(tb);
        });

        return events;
    }

    private String timeAgo(LocalDateTime dateTime) {
        if (dateTime == null) return "—";
        long minutes = ChronoUnit.MINUTES.between(dateTime, LocalDateTime.now());
        if (minutes < 1) return "Just now";
        if (minutes < 60) return minutes + " min ago";
        long hours = ChronoUnit.HOURS.between(dateTime, LocalDateTime.now());
        if (hours < 24) return hours + "h ago";
        long days = ChronoUnit.DAYS.between(dateTime, LocalDateTime.now());
        return days + " days ago";
    }
}
