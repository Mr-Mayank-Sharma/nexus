package com.nexus.oms.service.ai;

import com.nexus.oms.entity.NxOrder;
import com.nexus.oms.entity.NxPackage;
import com.nexus.oms.repository.OrderRepository;
import com.nexus.oms.repository.PackageRepository;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

@Service
public class AiPackingService {

    private static final List<String> SMALL_MATERIALS = List.of("Mailer", "Tissue Paper");
    private static final List<String> MEDIUM_MATERIALS = List.of("Box", "Bubble Wrap", "Tape");
    private static final List<String> LARGE_MATERIALS = List.of("Box", "Packing Peanuts", "Tape");

    private final OrderRepository orderRepository;
    private final PackageRepository packageRepository;

    public AiPackingService(OrderRepository orderRepository, PackageRepository packageRepository) {
        this.orderRepository = orderRepository;
        this.packageRepository = packageRepository;
    }

    public List<Map<String, Object>> getPackingPlans(UUID tenantId) {
        List<NxOrder> orders = orderRepository.findRecentByTenantId(tenantId, PageRequest.of(0, 20));
        List<Map<String, Object>> plans = new ArrayList<>();
        for (NxOrder order : orders) {
            NxPackage pkg = packageRepository.findByOrderId(order.getId()).stream().findFirst().orElse(null);
            if (pkg == null) {
                continue;
            }
            Map<String, Object> entry = new LinkedHashMap<>();
            entry.put("orderId", order.getId().toString());
            entry.put("orderNumber", order.getExternalId());
            entry.put("status", order.getStatus());
            entry.put("aiBoxPlan", buildPlan(pkg));
            plans.add(entry);
        }
        return plans;
    }

    private Map<String, Object> buildPlan(NxPackage pkg) {
        double width = safe(pkg.getWidthIn());
        double depth = safe(pkg.getDepthIn());
        double height = safe(pkg.getHeightIn());
        double volume = width * depth * height;

        String boxType = volume > 0 ? categorize(volume) : categorizeByItemCount(pkg.getItemCount());
        int capacity = capacityFor(boxType);
        int itemCount = pkg.getItemCount() != null ? pkg.getItemCount() : 0;
        double fillRate = capacity > 0 ? Math.min(1.0, (double) itemCount / capacity) : 0.5;

        Map<String, Object> plan = new LinkedHashMap<>();
        plan.put("boxType", boxType);
        if (width > 0 && depth > 0 && height > 0) {
            plan.put("dimensions", String.format("%.0fx%.0fx%.0f in", width, depth, height));
        } else {
            plan.put("dimensions", pkg.getBoxName());
        }
        plan.put("weight", pkg.getWeightLbs() != null ? pkg.getWeightLbs() : 0);
        plan.put("fillRate", Math.round(fillRate * 100) / 100.0);
        plan.put("materials", materialsFor(boxType));
        plan.put("confidence", confidenceFor(pkg));
        return plan;
    }

    private String categorize(double volume) {
        if (volume < 600) return "Small";
        if (volume < 2500) return "Medium";
        return "Large";
    }

    private String categorizeByItemCount(Integer itemCount) {
        if (itemCount == null) return "Medium";
        if (itemCount <= 3) return "Small";
        if (itemCount <= 10) return "Medium";
        return "Large";
    }

    private int capacityFor(String boxType) {
        switch (boxType) {
            case "Small": return 5;
            case "Large": return 30;
            default: return 15;
        }
    }

    private List<String> materialsFor(String boxType) {
        switch (boxType) {
            case "Small": return SMALL_MATERIALS;
            case "Large": return LARGE_MATERIALS;
            default: return MEDIUM_MATERIALS;
        }
    }

    private double confidenceFor(NxPackage pkg) {
        double confidence = 0.90;
        if (pkg.getWidthIn() == null || pkg.getDepthIn() == null || pkg.getHeightIn() == null) {
            confidence -= 0.08;
        }
        if (pkg.getWeightLbs() == null || pkg.getWeightLbs() <= 0) {
            confidence -= 0.07;
        }
        return Math.round(confidence * 100) / 100.0;
    }

    private double safe(Double value) {
        return value != null ? value : 0;
    }
}
