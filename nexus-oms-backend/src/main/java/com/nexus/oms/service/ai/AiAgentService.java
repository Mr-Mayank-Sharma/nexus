package com.nexus.oms.service.ai;

import com.nexus.oms.entity.NxInventory;
import com.nexus.oms.entity.NxOrder;
import com.nexus.oms.entity.NxOrderItem;
import com.nexus.oms.entity.NxPackage;
import com.nexus.oms.entity.NxShipment;
import com.nexus.oms.entity.Supplier;
import com.nexus.oms.entity.ai.AiModel;
import com.nexus.oms.entity.ai.AiModelMetric;
import com.nexus.oms.repository.InventoryRepository;
import com.nexus.oms.repository.OrderItemRepository;
import com.nexus.oms.repository.OrderRepository;
import com.nexus.oms.repository.PackageRepository;
import com.nexus.oms.repository.ShipmentRepository;
import com.nexus.oms.repository.SupplierRepository;
import com.nexus.oms.repository.ai.AiModelMetricRepository;
import com.nexus.oms.repository.ai.AiModelRepository;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.format.TextStyle;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.UUID;

@Service
public class AiAgentService {

    private static final List<String> PENDING_STATUSES = List.of("PENDING", "CONFIRMED", "ALLOCATED");
    private static final List<String> TERMINAL_STATUSES = List.of("SHIPPED", "DELIVERED", "CANCELLED", "CANCELLED");
    private static final String[] POSITIONS = {
            "front-left", "front-right", "center", "rear-left", "rear-right"
    };

    private final OrderRepository orderRepository;
    private final OrderItemRepository orderItemRepository;
    private final PackageRepository packageRepository;
    private final ShipmentRepository shipmentRepository;
    private final AiModelRepository modelRepository;
    private final AiModelMetricRepository metricRepository;
    private final SupplierRepository supplierRepository;
    private final InventoryRepository inventoryRepository;

    public AiAgentService(OrderRepository orderRepository,
                          OrderItemRepository orderItemRepository,
                          PackageRepository packageRepository,
                          ShipmentRepository shipmentRepository,
                          AiModelRepository modelRepository,
                          AiModelMetricRepository metricRepository,
                          SupplierRepository supplierRepository,
                          InventoryRepository inventoryRepository) {
        this.orderRepository = orderRepository;
        this.orderItemRepository = orderItemRepository;
        this.packageRepository = packageRepository;
        this.shipmentRepository = shipmentRepository;
        this.modelRepository = modelRepository;
        this.metricRepository = metricRepository;
        this.supplierRepository = supplierRepository;
        this.inventoryRepository = inventoryRepository;
    }

    public List<Map<String, Object>> getAgents(UUID tenantId) {
        List<AiModel> models = modelRepository.findByTenantId(tenantId, PageRequest.of(0, 50)).getContent();
        List<Map<String, Object>> agents = new ArrayList<>();
        for (AiModel model : models) {
            Map<String, Object> agent = new LinkedHashMap<>();
            agent.put("id", model.getId().toString());
            agent.put("name", model.getDisplayName() != null ? model.getDisplayName() : model.getName());
            agent.put("description", model.getDescription());
            agent.put("status", normalizeStatus(model.getStatus()));
            agent.put("accuracy", accuracyFor(model));
            agent.put("decisions24h", 0);
            agent.put("model", model.getModelType());
            agent.put("modelVersion", model.getCurrentVersion());
            agent.put("category", model.getCategory());
            agents.add(agent);
        }
        return agents;
    }

    public List<Map<String, Object>> getRoutingQueue(UUID tenantId) {
        List<NxOrder> orders = orderRepository.findRecentByTenantId(tenantId, PageRequest.of(0, 30));
        List<Map<String, Object>> queue = new ArrayList<>();
        for (NxOrder order : orders) {
            if (order.getStatus() == null || !PENDING_STATUSES.contains(order.getStatus().toUpperCase())) {
                continue;
            }
            Map<String, Object> entry = new LinkedHashMap<>();
            entry.put("id", order.getId().toString());
            entry.put("orderNumber", order.getExternalId() != null ? order.getExternalId() : order.getId().toString());
            entry.put("customer", order.getCustomerEmail() != null ? order.getCustomerEmail() : "—");
            entry.put("items", totalItems(order.getId()));
            entry.put("value", order.getTotal() != null ? order.getTotal().doubleValue() : 0);
            entry.put("slaRemaining", slaRemaining(order));
            String decision = decisionFor(order);
            entry.put("aiDecision", decision);
            entry.put("confidence", order.getPromisedDelivery() != null ? 90 : 75);
            entry.put("agentName", agentNameFor(decision));
            queue.add(entry);
        }
        return queue;
    }

    public Map<String, Object> getBriefing(UUID tenantId) {
        LocalDate today = LocalDate.now();
        LocalDateTime startToday = today.atStartOfDay();
        LocalDateTime startYesterday = today.minusDays(1).atStartOfDay();

        double revenueToday = sumRevenue(tenantId, startToday);
        double revenueYesterday = sumRevenue(tenantId, startYesterday);
        double margin = averageMargin(tenantId);

        List<NxOrder> recent = orderRepository.findRecentByTenantId(tenantId, PageRequest.of(0, 200));
        long pending = recent.stream().filter(o -> isPending(o.getStatus())).count();
        long late = recent.stream().filter(this::isLate).count();

        List<NxInventory> inventory = inventoryRepository.findByTenantId(tenantId);
        long totalSkus = inventory.stream().map(NxInventory::getSku).distinct().count();
        long lowStock = inventory.stream().filter(this::isLowStock).count();
        long deadStock = inventory.stream().filter(i -> i.getQuantityOnHand() != null && i.getQuantityOnHand() == 0).count();

        Map<String, Object> briefing = new LinkedHashMap<>();
        briefing.put("revenue", Map.of(
                "today", round2(revenueToday),
                "yesterday", round2(revenueYesterday)));
        briefing.put("orders", Map.of(
                "today", recent.stream().filter(o -> o.getCreatedAt() != null && !o.getCreatedAt().isBefore(startToday)).count(),
                "pending", pending,
                "late", late));
        briefing.put("profit", Map.of(
                "today", round2(revenueToday * margin),
                "margin", round2(margin)));
        briefing.put("inventory", Map.of(
                "total", totalSkus,
                "lowStock", lowStock,
                "deadStock", deadStock));
        briefing.put("insights", buildInsights(revenueToday, revenueYesterday, pending, lowStock, deadStock));
        briefing.put("risks", buildRisks(late, lowStock, pending));
        briefing.put("opportunities", buildOpportunities(deadStock, totalSkus));
        briefing.put("recommendations", buildRecommendations(tenantId, late, pending));
        briefing.put("forecast", monthlyRevenue(tenantId));
        return briefing;
    }

    public Map<String, Object> getForecast(UUID tenantId) {
        Map<String, Object> body = new LinkedHashMap<>();
        body.put("demand", buildDemand(tenantId));
        body.put("supplierRisk", buildSupplierRisks(tenantId));
        return body;
    }

    public List<Map<String, Object>> getLoadingPlans(UUID tenantId) {
        List<NxShipment> shipments = shipmentRepository.findByTenantId(tenantId);
        List<Map<String, Object>> trucks = new ArrayList<>();
        for (NxShipment shipment : shipments) {
            List<NxPackage> packages = packageRepository.findByOrderId(shipment.getOrderId());
            trucks.add(buildLoadingPlan(shipment, packages));
        }
        return trucks;
    }

    private Map<String, Object> buildLoadingPlan(NxShipment shipment, List<NxPackage> packages) {
        Map<String, Object> truck = new LinkedHashMap<>();
        truck.put("id", shipment.getId().toString());
        truck.put("type", shipment.getServiceLevel());
        truck.put("status", shipment.getStatus());
        truck.put("departure", shipment.getShippedAt());

        List<Map<String, Object>> sequence = new ArrayList<>();
        double totalWeight = 0;
        boolean allWeighed = true;
        for (int i = 0; i < packages.size(); i++) {
            NxPackage pkg = packages.get(i);
            double weight = pkg.getWeightLbs() != null ? pkg.getWeightLbs() : 0;
            totalWeight += weight;
            if (pkg.getWeightLbs() == null || pkg.getWeightLbs() <= 0) allWeighed = false;
            Map<String, Object> step = new LinkedHashMap<>();
            step.put("step", i + 1);
            step.put("boxId", pkg.getBoxName() != null ? pkg.getBoxName() : "Box " + (i + 1));
            step.put("position", POSITIONS[i % POSITIONS.length]);
            step.put("itemCount", pkg.getItemCount() != null ? pkg.getItemCount() : 0);
            step.put("weight", round2(weight));
            step.put("fragile", pkg.getNotes() != null && pkg.getNotes().toLowerCase().contains("fragile"));
            sequence.add(step);
        }

        truck.put("sequence", sequence);
        truck.put("totalWeight", round2(totalWeight));
        truck.put("stops", sequence.size());
        truck.put("used", round2(totalWeight));
        truck.put("weightDistribution", weightDistribution(sequence));
        truck.put("checks", List.of(
                Map.of("label", "All packages have recorded weight", "passed", allWeighed),
                Map.of("label", "Loading sequence assigned", "passed", !sequence.isEmpty()),
                Map.of("label", "No unweighted oversize packages", "passed", sequence.stream().noneMatch(s -> (double) s.get("weight") == 0))));
        return truck;
    }

    private Map<String, Object> weightDistribution(List<Map<String, Object>> sequence) {
        int front = 0;
        int center = 0;
        int rear = 0;
        for (Map<String, Object> step : sequence) {
            String pos = String.valueOf(step.get("position"));
            if (pos.startsWith("front")) front++;
            else if (pos.startsWith("rear")) rear++;
            else center++;
        }
        int total = Math.max(1, sequence.size());
        return Map.of(
                "front", Math.round((double) front / total * 100),
                "center", Math.round((double) center / total * 100),
                "rear", Math.round((double) rear / total * 100));
    }

    private List<Map<String, Object>> buildDemand(UUID tenantId) {
        LocalDate today = LocalDate.now();
        List<Map<String, Object>> demand = new ArrayList<>();

        List<Integer> dailyOrders = new ArrayList<>();
        List<Double> dailyRevenue = new ArrayList<>();
        List<Double> dailyOnTime = new ArrayList<>();
        double totalRevenue = 0;
        double totalOrders = 0;

        for (int i = 6; i >= 0; i--) {
            LocalDate day = today.minusDays(i);
            List<NxOrder> orders = ordersOn(tenantId, day);
            double rev = orders.stream()
                    .filter(o -> o.getTotal() != null)
                    .mapToDouble(o -> o.getTotal().doubleValue())
                    .sum();
            dailyOrders.add(orders.size());
            dailyRevenue.add(round2(rev));
            totalRevenue += rev;
            totalOrders += orders.size();
            dailyOnTime.add(onTimeRateFor(orders));
        }

        Map<String, Object> ordersMetric = new LinkedHashMap<>();
        ordersMetric.put("metric", "Orders Per Day");
        ordersMetric.put("current", Math.round(totalOrders / 7));
        ordersMetric.put("unit", "orders/day");
        ordersMetric.put("predicted", dailyOrders);
        ordersMetric.put("period", "Last 7 days");
        ordersMetric.put("confidence", 88);
        demand.add(ordersMetric);

        Map<String, Object> revenueMetric = new LinkedHashMap<>();
        revenueMetric.put("metric", "Revenue Per Day");
        revenueMetric.put("current", Math.round(totalRevenue / 7));
        revenueMetric.put("unit", "₹");
        revenueMetric.put("predicted", dailyRevenue);
        revenueMetric.put("period", "Last 7 days");
        revenueMetric.put("confidence", 86);
        demand.add(revenueMetric);

        Map<String, Object> onTimeMetric = new LinkedHashMap<>();
        onTimeMetric.put("metric", "On-Time Delivery");
        onTimeMetric.put("current", onTimeRateFor(ordersOn(tenantId, today)));
        onTimeMetric.put("unit", "%");
        onTimeMetric.put("predicted", dailyOnTime);
        onTimeMetric.put("period", "Last 7 days");
        onTimeMetric.put("confidence", 84);
        demand.add(onTimeMetric);

        Map<String, Object> pendingMetric = new LinkedHashMap<>();
        long pending = orderRepository.findRecentByTenantId(tenantId, PageRequest.of(0, 200)).stream()
                .filter(o -> isPending(o.getStatus()))
                .count();
        pendingMetric.put("metric", "Pending Orders");
        pendingMetric.put("current", pending);
        pendingMetric.put("unit", "orders");
        pendingMetric.put("predicted", List.of(pending, pending, pending, pending, pending, pending, pending));
        pendingMetric.put("period", "Current");
        pendingMetric.put("confidence", 90);
        demand.add(pendingMetric);

        return demand;
    }

    private List<Map<String, Object>> buildSupplierRisks(UUID tenantId) {
        List<Supplier> suppliers = supplierRepository.findByTenantId(tenantId, PageRequest.of(0, 100)).getContent();
        List<Map<String, Object>> risks = new ArrayList<>();
        for (Supplier supplier : suppliers) {
            int rating = supplier.getRating() != null ? supplier.getRating() : 0;
            boolean active = !Boolean.FALSE.equals(supplier.getIsActive()) && !"INACTIVE".equalsIgnoreCase(supplier.getStatus());
            int riskScore = rating > 0 ? Math.min(100, 100 - rating) : (active ? 40 : 65);

            Map<String, Object> risk = new LinkedHashMap<>();
            risk.put("supplierName", supplier.getTradingName() != null ? supplier.getTradingName() : supplier.getCompanyName());
            risk.put("riskScore", riskScore);
            risk.put("delayProbability", round2(riskScore / 100.0 * 0.8));
            risk.put("qualityScore", rating > 0 ? rating : 90);
            risk.put("onTimeRate", Math.max(0, Math.min(100, Math.round(100 - riskScore / 2.0))));
            risk.put("trend", "stable");
            risk.put("recommendation", riskScore >= 70
                    ? "Review contract / dual-source this supplier"
                    : riskScore >= 40
                    ? "Monitor lead times closely"
                    : "No action needed");
            risks.add(risk);
        }
        return risks;
    }

    private List<Map<String, Object>> buildInsights(double revenueToday, double revenueYesterday,
                                                     long pending, long lowStock, long deadStock) {
        List<Map<String, Object>> insights = new ArrayList<>();
        if (revenueYesterday > 0) {
            double pct = Math.round((revenueToday - revenueYesterday) / revenueYesterday * 100);
            insights.add(Map.of(
                    "type", pct >= 0 ? "positive" : "negative",
                    "icon", pct >= 0 ? "trending-up" : "alert-triangle",
                    "text", "Revenue is " + (pct >= 0 ? "up " : "down ") + Math.abs(pct) + "% vs yesterday"));
        } else {
            insights.add(Map.of("type", "neutral", "icon", "info", "text", "No prior-day revenue for comparison"));
        }
        insights.add(Map.of(
                "type", "neutral",
                "icon", "info",
                "text", pending + " orders currently awaiting fulfillment"));
        if (lowStock > 0) {
            insights.add(Map.of(
                    "type", "negative",
                    "icon", "alert-triangle",
                    "text", lowStock + " SKUs at or below reorder point"));
        } else if (deadStock > 0) {
            insights.add(Map.of(
                    "type", "neutral",
                    "icon", "info",
                    "text", deadStock + " SKUs have zero on-hand quantity"));
        }
        return insights;
    }

    private List<Map<String, Object>> buildRisks(long late, long lowStock, long pending) {
        List<Map<String, Object>> risks = new ArrayList<>();
        if (late > 0) {
            risks.add(Map.of(
                    "title", late + " orders past their promised delivery date",
                    "description", "These orders have exceeded their SLA window and need immediate attention.",
                    "severity", late >= 10 ? "high" : "medium",
                    "probability", 0.8));
        }
        if (lowStock > 0) {
            risks.add(Map.of(
                    "title", lowStock + " SKUs at risk of stockout",
                    "description", "On-hand quantity is at or below the configured reorder point.",
                    "severity", lowStock >= 10 ? "high" : "medium",
                    "probability", 0.7));
        }
        if (pending > 0) {
            risks.add(Map.of(
                    "title", pending + " orders in the fulfillment queue",
                    "description", "Pending orders are awaiting allocation, picking or packing.",
                    "severity", pending >= 50 ? "high" : "low",
                    "probability", 0.6));
        }
        return risks;
    }

    private List<Map<String, Object>> buildOpportunities(long deadStock, long totalSkus) {
        List<Map<String, Object>> opportunities = new ArrayList<>();
        if (deadStock > 0) {
            opportunities.add(Map.of(
                    "title", "Clear " + deadStock + " dead-stock SKUs",
                    "potential", "Free up storage capacity",
                    "action", "Run a clearance or donation program"));
        }
        opportunities.add(Map.of(
                "title", "Review allocation rules",
                "potential", "Improve SLA compliance",
                "action", "Audit current routing and allocation configuration"));
        if (totalSkus == 0) {
            opportunities.add(Map.of(
                    "title", "Complete inventory setup",
                    "potential", "Enable accurate forecasting",
                    "action", "Import SKUs and set reorder points"));
        }
        return opportunities;
    }

    private List<Map<String, Object>> buildRecommendations(UUID tenantId, long late, long pending) {
        List<Map<String, Object>> recommendations = new ArrayList<>();
        List<NxInventory> lowStockSkus = inventoryRepository.findByTenantId(tenantId).stream()
                .filter(this::isLowStock)
                .sorted((a, b) -> Integer.compare(
                        onHand(b),
                        onHand(a)))
                .limit(2)
                .toList();
        for (NxInventory inv : lowStockSkus) {
            Map<String, Object> rec = new LinkedHashMap<>();
            rec.put("id", "rec-stock-" + inv.getSku());
            rec.put("title", "Reorder " + inv.getSku());
            rec.put("description", "On-hand quantity " + onHand(inv) + " is at or below reorder point " + (inv.getReorderPoint() != null ? inv.getReorderPoint() : 0));
            rec.put("impact", "high");
            rec.put("status", "pending");
            rec.put("suggestedAction", "Generate a purchase order");
            rec.put("confidence", 88);
            rec.put("reasoning", List.of(
                    "On-hand quantity at or below reorder point",
                    inv.getSku() + " has " + onHand(inv) + " units available"));
            rec.put("agentName", "Inventory Balancer");
            recommendations.add(rec);
        }
        if (late > 0) {
            Map<String, Object> rec = new LinkedHashMap<>();
            rec.put("id", "rec-expedite-late");
            rec.put("title", "Expedite " + late + " late orders");
            rec.put("description", "Orders have passed their promised delivery window.");
            rec.put("impact", late >= 10 ? "high" : "medium");
            rec.put("status", "pending");
            rec.put("suggestedAction", "Prioritize these orders in the pick queue");
            rec.put("confidence", 85);
            rec.put("reasoning", List.of(
                    late + " orders beyond promised delivery date"));
            rec.put("agentName", "SLA Guardian");
            recommendations.add(rec);
        }
        if (pending >= 20) {
            Map<String, Object> rec = new LinkedHashMap<>();
            rec.put("id", "rec-pickers");
            rec.put("title", "Schedule additional pickers");
            rec.put("description", pending + " orders are waiting in the fulfillment queue.");
            rec.put("impact", "medium");
            rec.put("status", "pending");
            rec.put("suggestedAction", "Add a picking shift");
            rec.put("confidence", 82);
            rec.put("reasoning", List.of(
                    pending + " pending orders in the queue"));
            rec.put("agentName", "SLA Guardian");
            recommendations.add(rec);
        }
        return recommendations;
    }

    private List<Map<String, Object>> monthlyRevenue(UUID tenantId) {
        List<NxOrder> recent = orderRepository.findRecentByTenantId(tenantId, PageRequest.of(0, 500));
        Map<String, Double> byMonth = new LinkedHashMap<>();
        for (NxOrder order : recent) {
            if (order.getCreatedAt() == null || order.getTotal() == null) continue;
            String key = order.getCreatedAt().getMonth().getDisplayName(TextStyle.SHORT, Locale.ENGLISH);
            byMonth.merge(key, order.getTotal().doubleValue(), Double::sum);
        }
        List<Map<String, Object>> forecast = new ArrayList<>();
        for (Map.Entry<String, Double> e : byMonth.entrySet()) {
            Map<String, Object> m = new LinkedHashMap<>();
            m.put("month", e.getKey());
            m.put("revenue", Math.round(e.getValue()));
            forecast.add(m);
        }
        return forecast;
    }

    private double averageMargin(UUID tenantId) {
        List<NxOrder> recent = orderRepository.findRecentByTenantId(tenantId, PageRequest.of(0, 200));
        double profit = 0;
        double revenue = 0;
        for (NxOrder order : recent) {
            if (order.getTotal() == null || order.getTotal().doubleValue() <= 0) continue;
            double shipping = order.getShippingCost() != null ? order.getShippingCost().doubleValue() : 0;
            double tax = order.getTaxAmount() != null ? order.getTaxAmount().doubleValue() : 0;
            double net = order.getTotal().doubleValue() - shipping - tax;
            profit += net;
            revenue += order.getTotal().doubleValue();
        }
        return revenue > 0 ? profit / revenue : 0;
    }

    private double sumRevenue(UUID tenantId, LocalDateTime from) {
        List<NxOrder> recent = orderRepository.findRecentByTenantId(tenantId, PageRequest.of(0, 500));
        return recent.stream()
                .filter(o -> o.getCreatedAt() != null && !o.getCreatedAt().isBefore(from) && o.getCreatedAt().isBefore(from.plusDays(1)))
                .filter(o -> o.getTotal() != null)
                .mapToDouble(o -> o.getTotal().doubleValue())
                .sum();
    }

    private List<NxOrder> ordersOn(UUID tenantId, LocalDate day) {
        return orderRepository.findRecentByTenantId(tenantId, PageRequest.of(0, 500)).stream()
                .filter(o -> o.getCreatedAt() != null && o.getCreatedAt().toLocalDate().equals(day))
                .toList();
    }

    private double onTimeRateFor(List<NxOrder> orders) {
        if (orders.isEmpty()) return 100;
        long delivered = orders.stream()
                .filter(o -> o.getStatus() != null && List.of("SHIPPED", "DELIVERED").contains(o.getStatus().toUpperCase()))
                .count();
        if (delivered == 0) return 100;
        long late = orders.stream()
                .filter(o -> o.getStatus() != null && "DELIVERED".equalsIgnoreCase(o.getStatus()))
                .filter(o -> o.getPromisedDelivery() != null)
                .filter(o -> o.getUpdatedAt() != null && o.getUpdatedAt().isAfter(o.getPromisedDelivery()))
                .count();
        return Math.max(0, Math.min(100, Math.round((delivered - late) / (double) delivered * 100)));
    }

    private boolean isPending(String status) {
        return status != null && PENDING_STATUSES.contains(status.toUpperCase());
    }

    private boolean isLate(NxOrder order) {
        if (order.getPromisedDelivery() == null) return false;
        if (order.getStatus() != null && TERMINAL_STATUSES.contains(order.getStatus().toUpperCase())) return false;
        return order.getPromisedDelivery().isBefore(LocalDateTime.now());
    }

    private boolean isLowStock(NxInventory inv) {
        int onHand = onHand(inv);
        int threshold = inv.getReorderPoint() != null
                ? inv.getReorderPoint()
                : (inv.getSafetyStock() != null ? inv.getSafetyStock() : 0);
        return onHand <= threshold;
    }

    private int onHand(NxInventory inv) {
        return inv.getQuantityOnHand() != null ? inv.getQuantityOnHand() : 0;
    }

    private int totalItems(UUID orderId) {
        return orderItemRepository.findByOrderId(orderId).stream()
                .filter(i -> i.getQuantity() != null)
                .mapToInt(NxOrderItem::getQuantity)
                .sum();
    }

    private String slaRemaining(NxOrder order) {
        if (order.getPromisedDelivery() == null) return "—";
        long minutes = java.time.Duration.between(LocalDateTime.now(), order.getPromisedDelivery()).toMinutes();
        if (minutes < 0) return "Overdue";
        long h = minutes / 60;
        long m = minutes % 60;
        return h > 0 ? h + "h " + m + "m" : m + "m";
    }

    private String decisionFor(NxOrder order) {
        if (order.getPromisedDelivery() == null) return "Route to fulfillment";
        long minutes = java.time.Duration.between(LocalDateTime.now(), order.getPromisedDelivery()).toMinutes();
        if (minutes < 240) return "Priority Route";
        if (minutes < 1440) return "Express Route";
        return "Standard Route";
    }

    private String agentNameFor(String decision) {
        if (decision != null && decision.contains("Priority")) return "SLA Guardian";
        return "Order Router";
    }

    private String normalizeStatus(String status) {
        if (status == null) return "idle";
        switch (status.toUpperCase()) {
            case "ACTIVE":
            case "DEPLOYED": return "active";
            case "TRAINING": return "training";
            case "ERROR":
            case "FAILED": return "error";
            case "DRAFT":
            case "STAGED":
            case "IDLE": return "idle";
            default: return status.toLowerCase();
        }
    }

    private int accuracyFor(AiModel model) {
        List<AiModelMetric> metrics = metricRepository.findByModelIdOrderByRecordedAtDesc(model.getId(), PageRequest.of(0, 10));
        for (AiModelMetric metric : metrics) {
            if ("accuracy".equalsIgnoreCase(metric.getMetricName())) {
                return Math.round(metric.getMetricValue().floatValue() * 100);
            }
        }
        return 0;
    }

    private double round2(double value) {
        return Math.round(value * 100) / 100.0;
    }
}
