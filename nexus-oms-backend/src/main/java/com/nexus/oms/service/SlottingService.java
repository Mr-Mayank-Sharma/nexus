package com.nexus.oms.service;

import com.nexus.oms.entity.NxSlottingAssignment;
import com.nexus.oms.entity.NxSlottingAudit;
import com.nexus.oms.entity.NxSlottingRule;
import com.nexus.oms.entity.WarehouseBin;
import com.nexus.oms.entity.WarehouseZone;
import com.nexus.oms.exception.ResourceNotFoundException;
import com.nexus.oms.repository.*;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.*;
import java.util.stream.Collectors;

@Service
public class SlottingService {

    private final SlottingAssignmentRepository assignmentRepository;
    private final SlottingRuleRepository ruleRepository;
    private final SlottingAuditRepository auditRepository;
    private final WarehouseBinRepository binRepository;
    private final WarehouseZoneRepository zoneRepository;

    public SlottingService(SlottingAssignmentRepository assignmentRepository,
                           SlottingRuleRepository ruleRepository,
                           SlottingAuditRepository auditRepository,
                           WarehouseBinRepository binRepository,
                           WarehouseZoneRepository zoneRepository) {
        this.assignmentRepository = assignmentRepository;
        this.ruleRepository = ruleRepository;
        this.auditRepository = auditRepository;
        this.binRepository = binRepository;
        this.zoneRepository = zoneRepository;
    }

    public List<NxSlottingAssignment> getAssignments(UUID warehouseId) {
        return assignmentRepository.findByWarehouseId(warehouseId);
    }

    public NxSlottingAssignment getAssignment(UUID id) {
        return assignmentRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("SlottingAssignment", id));
    }

    public List<NxSlottingRule> getRules(UUID warehouseId) {
        return ruleRepository.findByWarehouseIdAndIsActiveOrderByPriorityAsc(warehouseId, true);
    }

    @Transactional
    public NxSlottingRule createRule(NxSlottingRule rule) {
        return ruleRepository.save(rule);
    }

    @Transactional
    public NxSlottingRule updateRule(UUID id, NxSlottingRule updates) {
        NxSlottingRule existing = ruleRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("SlottingRule", id));

        if (updates.getRuleName() != null) existing.setRuleName(updates.getRuleName());
        if (updates.getRuleType() != null) existing.setRuleType(updates.getRuleType());
        if (updates.getCriteria() != null) existing.setCriteria(updates.getCriteria());
        if (updates.getTargetZoneId() != null) existing.setTargetZoneId(updates.getTargetZoneId());
        if (updates.getTargetBinClass() != null) existing.setTargetBinClass(updates.getTargetBinClass());
        if (updates.getPriority() != null) existing.setPriority(updates.getPriority());
        if (updates.getIsActive() != null) existing.setIsActive(updates.getIsActive());
        if (updates.getNotes() != null) existing.setNotes(updates.getNotes());
        if (updates.getMetadata() != null) existing.setMetadata(updates.getMetadata());

        return ruleRepository.save(existing);
    }

    @Transactional
    public NxSlottingRule toggleRule(UUID id, Boolean isActive) {
        NxSlottingRule rule = ruleRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("SlottingRule", id));
        rule.setIsActive(isActive);
        return ruleRepository.save(rule);
    }

    @Transactional(readOnly = true)
    public Map<String, Object> analyzeSlotting(UUID warehouseId) {
        List<NxSlottingAssignment> assignments = assignmentRepository.findByWarehouseId(warehouseId);
        List<WarehouseBin> allBins = binRepository.findByWarehouseId(warehouseId);

        double avgScore = assignments.isEmpty() ? 0.0
                : assignments.stream().mapToDouble(a -> a.getSlottingScore() != null ? a.getSlottingScore() : 0.0).average().orElse(0.0);

        Map<String, Long> velocityDistribution = assignments.stream()
                .filter(a -> a.getVelocityClass() != null)
                .collect(Collectors.groupingBy(NxSlottingAssignment::getVelocityClass, Collectors.counting()));

        long totalBins = allBins.size();
        long usedBins = allBins.stream().filter(b -> !Boolean.TRUE.equals(b.getIsEmpty())).count();
        double spaceUtilization = totalBins > 0 ? Math.round(((double) usedBins / totalBins) * 100.0 * 100.0) / 100.0 : 0.0;

        List<String> recommendations = generateRecommendations(assignments, allBins);

        Map<String, Object> result = new LinkedHashMap<>();
        result.put("overallSlottingScore", Math.round(avgScore * 100.0) / 100.0);
        result.put("velocityDistribution", velocityDistribution);
        result.put("totalAssignments", assignments.size());
        result.put("spaceUtilization", Map.of(
                "totalBins", totalBins,
                "usedBins", usedBins,
                "utilizationPercent", spaceUtilization
        ));
        result.put("recommendations", recommendations);
        return result;
    }

    @Transactional
    public Map<String, Object> optimizeSlotting(UUID warehouseId) {
        List<NxSlottingAssignment> assignments = assignmentRepository.findByWarehouseId(warehouseId);
        List<NxSlottingRule> activeRules = ruleRepository.findByWarehouseIdAndIsActiveOrderByPriorityAsc(warehouseId, true);

        double avgScoreBefore = assignments.isEmpty() ? 0.0
                : assignments.stream().mapToDouble(a -> a.getSlottingScore() != null ? a.getSlottingScore() : 0.0).average().orElse(0.0);

        int movesRecommended = 0;
        int movesCompleted = 0;

        for (NxSlottingAssignment assignment : assignments) {
            String targetBinClass = resolveTargetBinClass(assignment, activeRules);
            if (targetBinClass == null) continue;

            WarehouseBin currentBin = binRepository.findById(assignment.getBinId()).orElse(null);
            if (currentBin != null && targetBinClass.equals(currentBin.getBinClass())) {
                continue;
            }

            List<WarehouseBin> targetBins = binRepository.findAvailableByWarehouseIdAndBinClass(warehouseId, targetBinClass);
            WarehouseBin targetBin = targetBins.stream()
                    .filter(b -> !b.getId().equals(assignment.getBinId()))
                    .findFirst()
                    .orElse(null);

            if (targetBin == null) continue;

            movesRecommended++;

            NxSlottingAudit audit = NxSlottingAudit.builder()
                    .tenantId(assignment.getTenantId())
                    .warehouseId(warehouseId)
                    .sku(assignment.getSku())
                    .productName(assignment.getProductName())
                    .fromBinId(assignment.getBinId())
                    .fromBinCode(currentBin != null ? currentBin.getCode() : null)
                    .toBinId(targetBin.getId())
                    .toBinCode(targetBin.getCode())
                    .fromZoneId(assignment.getZoneId())
                    .toZoneId(targetBin.getZoneId())
                    .reason("RULE_BASED")
                    .action("MOVE")
                    .movedQuantity(assignment.getAssignedQuantity())
                    .performedBy("SYSTEM")
                    .notes("AI-driven slotting optimization")
                    .build();
            auditRepository.save(audit);

            assignment.setBinId(targetBin.getId());
            assignment.setZoneId(targetBin.getZoneId());
            assignment.setLastSlottingAt(LocalDateTime.now());
            assignment.setAssignedBy("AI");
            assignment.setSlottingScore(calculateSlottingScore(assignment));
            assignmentRepository.save(assignment);

            movesCompleted++;
        }

        List<NxSlottingAssignment> updatedAssignments = assignmentRepository.findByWarehouseId(warehouseId);
        double avgScoreAfter = updatedAssignments.isEmpty() ? 0.0
                : updatedAssignments.stream().mapToDouble(a -> a.getSlottingScore() != null ? a.getSlottingScore() : 0.0).average().orElse(0.0);

        Map<String, Object> result = new LinkedHashMap<>();
        result.put("movesRecommended", movesRecommended);
        result.put("movesCompleted", movesCompleted);
        result.put("avgScoreBefore", Math.round(avgScoreBefore * 100.0) / 100.0);
        result.put("avgScoreAfter", Math.round(avgScoreAfter * 100.0) / 100.0);
        return result;
    }

    @Transactional
    public NxSlottingAssignment reassignSku(String sku, UUID warehouseId, UUID targetBinId, Integer quantity, String performedBy) {
        List<NxSlottingAssignment> existing = assignmentRepository.findByWarehouseId(warehouseId).stream()
                .filter(a -> sku.equals(a.getSku()))
                .toList();

        WarehouseBin targetBin = binRepository.findById(targetBinId)
                .orElseThrow(() -> new ResourceNotFoundException("WarehouseBin", targetBinId));

        NxSlottingAssignment assignment;
        UUID fromBinId = null;
        String fromBinCode = null;
        UUID fromZoneId = null;

        if (!existing.isEmpty()) {
            assignment = existing.get(0);
            fromBinId = assignment.getBinId();
            fromZoneId = assignment.getZoneId();
            WarehouseBin fromBin = binRepository.findById(fromBinId).orElse(null);
            fromBinCode = fromBin != null ? fromBin.getCode() : null;
            assignment.setBinId(targetBinId);
            assignment.setZoneId(targetBin.getZoneId());
            assignment.setAssignedQuantity(quantity);
            assignment.setLastSlottingAt(LocalDateTime.now());
            assignment.setAssignedBy("USER");
            assignment.setSlottingScore(calculateSlottingScore(assignment));
            assignmentRepository.save(assignment);
        } else {
            assignment = NxSlottingAssignment.builder()
                    .warehouseId(warehouseId)
                    .sku(sku)
                    .binId(targetBinId)
                    .zoneId(targetBin.getZoneId())
                    .assignedQuantity(quantity)
                    .velocityClass("C")
                    .assignedBy("USER")
                    .lastSlottingAt(LocalDateTime.now())
                    .slottingScore(50.0)
                    .build();
            assignmentRepository.save(assignment);
        }

        NxSlottingAudit audit = NxSlottingAudit.builder()
                .warehouseId(warehouseId)
                .sku(sku)
                .fromBinId(fromBinId)
                .fromBinCode(fromBinCode)
                .toBinId(targetBinId)
                .toBinCode(targetBin.getCode())
                .fromZoneId(fromZoneId)
                .toZoneId(targetBin.getZoneId())
                .reason("MANUAL")
                .action("MOVE")
                .movedQuantity(quantity)
                .performedBy(performedBy)
                .build();
        auditRepository.save(audit);

        return assignment;
    }

    @Transactional(readOnly = true)
    public Map<String, Object> getVelocityAnalysis(UUID warehouseId) {
        List<NxSlottingAssignment> allAssignments = assignmentRepository.findByWarehouseId(warehouseId);

        if (allAssignments.isEmpty()) {
            Map<String, Object> empty = new LinkedHashMap<>();
            empty.put("groups", Map.of());
            empty.put("totalSkus", 0);
            return empty;
        }

        List<NxSlottingAssignment> sorted = allAssignments.stream()
                .sorted(Comparator.comparingInt((NxSlottingAssignment a) -> a.getPickFrequency() != null ? a.getPickFrequency() : 0).reversed())
                .toList();

        int total = sorted.size();
        int aCount = Math.max(1, (int) Math.ceil(total * 0.20));
        int bCount = Math.max(1, (int) Math.ceil(total * 0.30));
        int cCount = Math.max(1, (int) Math.ceil(total * 0.30));

        List<NxSlottingAssignment> aGroup = sorted.subList(0, Math.min(aCount, total));
        List<NxSlottingAssignment> bGroup = sorted.subList(Math.min(aCount, total), Math.min(aCount + bCount, total));
        List<NxSlottingAssignment> cGroup = sorted.subList(Math.min(aCount + bCount, total), Math.min(aCount + bCount + cCount, total));
        List<NxSlottingAssignment> dGroup = sorted.subList(Math.min(aCount + bCount + cCount, total), total);

        Map<String, Object> groups = new LinkedHashMap<>();
        groups.put("A", buildVelocityGroup(aGroup, "PRIORITY"));
        groups.put("B", buildVelocityGroup(bGroup, "STANDARD"));
        groups.put("C", buildVelocityGroup(cGroup, "BULK"));
        groups.put("D", buildVelocityGroup(dGroup, "BULK"));

        Map<String, Object> result = new LinkedHashMap<>();
        result.put("groups", groups);
        result.put("totalSkus", total);
        return result;
    }

    @Transactional(readOnly = true)
    public Map<String, Object> getSpaceUtilization(UUID warehouseId) {
        List<WarehouseBin> allBins = binRepository.findByWarehouseId(warehouseId);

        long totalBins = allBins.size();
        long emptyBins = allBins.stream().filter(b -> Boolean.TRUE.equals(b.getIsEmpty())).count();
        long reservedBins = allBins.stream().filter(b -> Boolean.TRUE.equals(b.getIsReserved())).count();
        long usedBins = totalBins - emptyBins;
        double utilizationPercent = totalBins > 0
                ? Math.round(((double) usedBins / totalBins) * 100.0 * 100.0) / 100.0
                : 0.0;

        Map<UUID, List<WarehouseBin>> byZone = allBins.stream()
                .filter(b -> b.getZoneId() != null)
                .collect(Collectors.groupingBy(WarehouseBin::getZoneId));

        List<Map<String, Object>> zoneBreakdown = new ArrayList<>();
        for (Map.Entry<UUID, List<WarehouseBin>> entry : byZone.entrySet()) {
            List<WarehouseBin> zoneBins = entry.getValue();
            long zoneTotal = zoneBins.size();
            long zoneEmpty = zoneBins.stream().filter(b -> Boolean.TRUE.equals(b.getIsEmpty())).count();
            zoneBreakdown.add(Map.of(
                    "zoneId", entry.getKey(),
                    "totalBins", zoneTotal,
                    "usedBins", zoneTotal - zoneEmpty,
                    "emptyBins", zoneEmpty,
                    "utilizationPercent", zoneTotal > 0
                            ? Math.round(((double) (zoneTotal - zoneEmpty) / zoneTotal) * 100.0 * 100.0) / 100.0
                            : 0.0
            ));
        }

        Map<String, Object> result = new LinkedHashMap<>();
        result.put("totalBins", totalBins);
        result.put("usedBins", usedBins);
        result.put("emptyBins", emptyBins);
        result.put("reservedBins", reservedBins);
        result.put("utilizationPercent", utilizationPercent);
        result.put("byZone", zoneBreakdown);
        return result;
    }

    public List<NxSlottingAudit> getSlottingAuditLog(UUID warehouseId, LocalDateTime from, LocalDateTime to) {
        if (from != null && to != null) {
            return auditRepository.findByWarehouseIdAndCreatedAtBetween(warehouseId, from, to);
        }
        return auditRepository.findByWarehouseId(warehouseId);
    }

    public double calculateSlottingScore(NxSlottingAssignment assignment) {
        double score = 0.0;

        if (assignment.getVelocityClass() != null && assignment.getBinId() != null) {
            WarehouseBin bin = binRepository.findById(assignment.getBinId()).orElse(null);
            if (bin != null) {
                boolean velocityMatch = switch (assignment.getVelocityClass()) {
                    case "A" -> "PRIORITY".equals(bin.getBinClass());
                    case "B" -> "STANDARD".equals(bin.getBinClass()) || "PRIORITY".equals(bin.getBinClass());
                    case "C" -> "BULK".equals(bin.getBinClass()) || "STANDARD".equals(bin.getBinClass());
                    case "D" -> "BULK".equals(bin.getBinClass());
                    default -> false;
                };
                score += velocityMatch ? 40.0 : 10.0;
            }
        }

        int freq = assignment.getPickFrequency() != null ? assignment.getPickFrequency() : 0;
        if (freq >= 50) score += 30.0;
        else if (freq >= 20) score += 25.0;
        else if (freq >= 5) score += 15.0;
        else score += 5.0;

        score += 15.0;

        if (assignment.getLastPickedAt() != null) {
            long hoursSincePick = java.time.Duration.between(assignment.getLastPickedAt(), LocalDateTime.now()).toHours();
            if (hoursSincePick < 24) score += 15.0;
            else if (hoursSincePick < 168) score += 10.0;
            else if (hoursSincePick < 720) score += 5.0;
        }

        return Math.min(100.0, Math.round(score * 100.0) / 100.0);
    }

    private String resolveTargetBinClass(NxSlottingAssignment assignment, List<NxSlottingRule> rules) {
        for (NxSlottingRule rule : rules) {
            if (matchRule(assignment, rule)) {
                return rule.getTargetBinClass();
            }
        }

        if (assignment.getVelocityClass() != null) {
            return switch (assignment.getVelocityClass()) {
                case "A" -> "PRIORITY";
                case "B" -> "STANDARD";
                case "C", "D" -> "BULK";
                default -> null;
            };
        }
        return null;
    }

    private boolean matchRule(NxSlottingAssignment assignment, NxSlottingRule rule) {
        if ("VELOCITY_BASED".equals(rule.getRuleType()) && assignment.getVelocityClass() != null) {
            return rule.getCriteria() != null && rule.getCriteria().contains(assignment.getVelocityClass());
        }
        return false;
    }

    private List<String> generateRecommendations(List<NxSlottingAssignment> assignments, List<WarehouseBin> bins) {
        List<String> recommendations = new ArrayList<>();

        for (NxSlottingAssignment assignment : assignments) {
            if (assignment.getSlottingScore() != null && assignment.getSlottingScore() < 40.0) {
                WarehouseBin currentBin = bins.stream()
                        .filter(b -> b.getId().equals(assignment.getBinId()))
                        .findFirst().orElse(null);
                String binCode = currentBin != null ? currentBin.getCode() : "unknown";
                recommendations.add(String.format(
                        "SKU %s (score: %.1f) in bin %s should be relocated - low slotting score",
                        assignment.getSku(), assignment.getSlottingScore(), binCode));
            }

            if (assignment.getPickFrequency() != null && assignment.getPickFrequency() > 20
                    && assignment.getBinId() != null) {
                WarehouseBin currentBin = bins.stream()
                        .filter(b -> b.getId().equals(assignment.getBinId()))
                        .findFirst().orElse(null);
                if (currentBin != null && !"PRIORITY".equals(currentBin.getBinClass())
                        && "A".equals(assignment.getVelocityClass())) {
                    recommendations.add(String.format(
                            "SKU %s (picks: %d) in bin %s (%s) should move to PRIORITY bin - high velocity",
                            assignment.getSku(), assignment.getPickFrequency(),
                            currentBin.getCode(), currentBin.getBinClass()));
                }
            }
        }

        return recommendations;
    }

    private Map<String, Object> buildVelocityGroup(List<NxSlottingAssignment> group, String recommendedBinClass) {
        double avgPickFreq = group.isEmpty() ? 0.0
                : group.stream().mapToInt(a -> a.getPickFrequency() != null ? a.getPickFrequency() : 0).average().orElse(0.0);

        List<Map<String, Object>> skus = group.stream().map(a -> {
            Map<String, Object> skuInfo = new LinkedHashMap<>();
            skuInfo.put("sku", a.getSku());
            skuInfo.put("productName", a.getProductName());
            skuInfo.put("pickFrequency", a.getPickFrequency());
            skuInfo.put("binId", a.getBinId());
            skuInfo.put("slottingScore", a.getSlottingScore());
            return skuInfo;
        }).toList();

        Map<String, Object> result = new LinkedHashMap<>();
        result.put("count", group.size());
        result.put("avgPickFrequency", Math.round(avgPickFreq * 100.0) / 100.0);
        result.put("recommendedBinClass", recommendedBinClass);
        result.put("skus", skus);
        return result;
    }
}
