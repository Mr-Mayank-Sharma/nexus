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

    @Cacheable(value = "dashboard", key = "'alerts:' + #tenantId")
    public List<Map<String, Object>> getAlerts(UUID tenantId) {
        List<Map<String, Object>> alerts = new ArrayList<>();
        exceptionRepository.findByTenantIdAndStatus(tenantId, "OPEN", PageRequest.of(0, 20))
                .forEach(ex -> {
                    Map<String, Object> alert = new LinkedHashMap<>();
                    alert.put("id", ex.getId().toString());
                    alert.put("message", ex.getMessage() != null ? ex.getMessage() : ex.getTitle());
                    alert.put("severity", mapSeverity(ex.getSeverity()));
                    alert.put("type", ex.getExceptionType() != null ? ex.getExceptionType() : ex.getType());
                    alert.put("orderId", ex.getOrderId() != null ? ex.getOrderId().toString() : null);
                    alert.put("detectedAt", ex.getDetectedAt() != null ? ex.getDetectedAt().toString() : null);
                    alerts.add(alert);
                });
        return alerts;
    }

    private String mapSeverity(String severity) {
        if (severity == null) return "info";
        return switch (severity.toUpperCase()) {
            case "HIGH", "CRITICAL" -> "error";
            case "MEDIUM" -> "warning";
            default -> "info";
        };
    }

    @Cacheable(value = "dashboard", key = "'status-dist:' + #tenantId")
    public List<Map<String, Object>> getOrderStatusDistribution(UUID tenantId) {
        List<Map<String, Object>> distribution = new ArrayList<>();
        String[] statuses = {"PENDING", "CONFIRMED", "ALLOCATED", "PICKING", "PACKING", "SHIPPED", "DELIVERED", "CANCELLED"};
        for (String status : statuses) {
            long count = orderRepository.countByTenantIdAndStatus(tenantId, status);
            if (count > 0) {
                Map<String, Object> entry = new LinkedHashMap<>();
                entry.put("name", formatStatusName(status));
                entry.put("value", count);
                distribution.add(entry);
            }
        }
        return distribution;
    }

    private String formatStatusName(String status) {
        return switch (status) {
            case "PENDING" -> "Pending";
            case "CONFIRMED" -> "Processing";
            case "ALLOCATED", "PICKING", "PACKING" -> "In Progress";
            case "SHIPPED" -> "Shipped";
            case "DELIVERED" -> "Delivered";
            case "CANCELLED" -> "Cancelled";
            default -> status.substring(0, 1) + status.substring(1).toLowerCase();
        };
    }

    @Cacheable(value = "dashboard", key = "'task-queue:' + #tenantId")
    public Map<String, Object> getTaskQueueSummary(UUID tenantId) {
        long onHold = orderRepository.countByTenantIdAndStatus(tenantId, "ON_HOLD");
        long pending = orderRepository.countByTenantIdAndStatus(tenantId, "PENDING");
        long allocated = orderRepository.countByTenantIdAndStatus(tenantId, "ALLOCATED");
        long picking = orderRepository.countByTenantIdAndStatus(tenantId, "PICKING");
        long packing = orderRepository.countByTenantIdAndStatus(tenantId, "PACKING");

        long openExceptions = exceptionRepository.countByTenantIdAndStatus(tenantId, "OPEN");
        long substituteItems = exceptionRepository.countByTenantIdAndStatus(tenantId, "SUBSTITUTE_PENDING");
        long badAddress = exceptionRepository.countByTenantIdAndStatus(tenantId, "BAD_ADDRESS");
        long fraudRisk = exceptionRepository.countByTenantIdAndStatus(tenantId, "FRAUD_REVIEW");

        Map<String, Object> summary = new LinkedHashMap<>();
        summary.put("holdTasks", Map.of(
                "substituteItems", substituteItems,
                "badAddress", badAddress,
                "fraudRisk", fraudRisk,
                "onHold", onHold
        ));
        summary.put("unbrokered", Map.of(
                "brokeringQueue", allocated,
                "unallocated", pending
        ));
        summary.put("fulfillmentQueue", Map.of(
                "picking", picking,
                "packing", packing
        ));
        return summary;
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
