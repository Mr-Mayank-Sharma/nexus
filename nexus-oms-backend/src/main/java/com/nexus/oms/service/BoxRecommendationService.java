package com.nexus.oms.service;

import com.nexus.oms.entity.NxBoxTemplate;
import com.nexus.oms.entity.NxPackage;
import com.nexus.oms.repository.BoxTemplateRepository;
import com.nexus.oms.repository.PackageRepository;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

@Service
public class BoxRecommendationService {

    private static final double DEFAULT_AVG_ITEM_VOLUME_IN3 = 120;
    private static final double DEFAULT_VOLUME_FILL_FACTOR = 0.85;

    private final BoxTemplateRepository boxTemplateRepository;
    private final PackageRepository packageRepository;

    public BoxRecommendationService(BoxTemplateRepository boxTemplateRepository,
                                    PackageRepository packageRepository) {
        this.boxTemplateRepository = boxTemplateRepository;
        this.packageRepository = packageRepository;
    }

    public List<NxBoxTemplate> getActiveBoxes(UUID tenantId) {
        List<NxBoxTemplate> tenantBoxes = boxTemplateRepository.findByTenantIdAndIsActiveTrue(tenantId);
        return tenantBoxes.isEmpty() ? defaultBoxes() : tenantBoxes;
    }

    public Map<String, Object> recommend(UUID tenantId, double requiredVolume, double requiredWeight, int itemCount) {
        List<NxBoxTemplate> boxes = boxTemplateRepository.findSmallestFitting(tenantId, requiredVolume);
        if (boxes.isEmpty()) {
            boxes = defaultBoxes().stream()
                    .filter(b -> b.getVolumeCapacityIn3() >= requiredVolume)
                    .toList();
        }

        NxBoxTemplate match = null;
        for (NxBoxTemplate box : boxes) {
            if (box.getMaxWeightLbs() >= requiredWeight
                    && (box.getMaxItemCount() == 0 || box.getMaxItemCount() >= itemCount)) {
                match = box;
                break;
            }
        }

        Map<String, Object> result = new LinkedHashMap<>();
        if (match == null) {
            result.put("boxName", "NO_FIT");
            result.put("reason", "No box template fits the required volume/weight");
            result.put("requiredVolume", round(requiredVolume));
            result.put("requiredWeight", round(requiredWeight));
            return result;
        }

        double fillRate = match.getVolumeCapacityIn3() > 0
                ? Math.min(1.0, requiredVolume / match.getVolumeCapacityIn3())
                : 0.0;
        double weightUtilization = match.getMaxWeightLbs() > 0
                ? Math.min(1.0, requiredWeight / match.getMaxWeightLbs())
                : 0.0;

        result.put("boxName", match.getName());
        result.put("dimensions", String.format("%.0fx%.0fx%.0f in",
                match.getWidthIn(), match.getDepthIn(), match.getHeightIn()));
        result.put("volumeCapacity", match.getVolumeCapacityIn3());
        result.put("maxWeight", match.getMaxWeightLbs());
        result.put("fillRate", round(fillRate));
        result.put("weightUtilization", round(weightUtilization));
        result.put("recommendedBy", "VOLUME");
        return result;
    }

    public Map<String, Object> recommendForPackage(UUID tenantId, UUID packageId) {
        NxPackage pkg = packageRepository.findById(packageId).orElse(null);
        if (pkg == null) {
            Map<String, Object> none = new LinkedHashMap<>();
            none.put("boxName", "UNKNOWN");
            none.put("reason", "Package not found");
            return none;
        }

        double volume;
        if (pkg.getWidthIn() != null && pkg.getHeightIn() != null && pkg.getDepthIn() != null
                && pkg.getWidthIn() > 0 && pkg.getHeightIn() > 0 && pkg.getDepthIn() > 0) {
            volume = pkg.getWidthIn() * pkg.getHeightIn() * pkg.getDepthIn();
        } else {
            int itemCount = pkg.getItemCount() != null ? pkg.getItemCount() : 1;
            volume = itemCount * DEFAULT_AVG_ITEM_VOLUME_IN3;
        }
        volume = volume / DEFAULT_VOLUME_FILL_FACTOR;

        double weight = pkg.getWeightLbs() != null ? pkg.getWeightLbs() : 0;
        int itemCount = pkg.getItemCount() != null ? pkg.getItemCount() : 1;
        return recommend(tenantId, volume, weight, itemCount);
    }

    private List<NxBoxTemplate> defaultBoxes() {
        List<NxBoxTemplate> boxes = new ArrayList<>();
        boxes.add(box(null, "SM-MAILER", 11, 3, 8, 264, 5, 3));
        boxes.add(box(null, "SM-BOX", 12, 6, 9, 648, 15, 6));
        boxes.add(box(null, "MD-BOX", 16, 8, 12, 1536, 30, 12));
        boxes.add(box(null, "LG-BOX", 20, 10, 16, 3200, 50, 20));
        boxes.add(box(null, "XL-BOX", 24, 14, 20, 6720, 80, 40));
        return boxes;
    }

    private NxBoxTemplate box(UUID tenantId, String name, double w, double h, double d,
                              double volume, double maxWeight, int maxItems) {
        return NxBoxTemplate.builder()
                .tenantId(tenantId)
                .name(name)
                .widthIn(w)
                .heightIn(h)
                .depthIn(d)
                .volumeCapacityIn3(volume)
                .maxWeightLbs(maxWeight)
                .maxItemCount(maxItems)
                .isActive(true)
                .build();
    }

    private double round(double value) {
        return Math.round(value * 100) / 100.0;
    }
}
