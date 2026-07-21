package com.nexus.oms.service;

import com.nexus.oms.entity.NxATPRule;
import com.nexus.oms.entity.NxATPSnapshot;
import com.nexus.oms.exception.BadRequestException;
import com.nexus.oms.exception.ResourceNotFoundException;
import com.nexus.oms.repository.ATPRuleRepository;
import com.nexus.oms.repository.ATPSnapshotRepository;
import com.nexus.oms.security.TenantContext;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDateTime;
import java.util.*;

@Service
public class ATPCalculationEngine {

    private static final Logger log = LoggerFactory.getLogger(ATPCalculationEngine.class);

    private final ATPRuleRepository atpRuleRepository;
    private final ATPSnapshotRepository atpSnapshotRepository;

    public ATPCalculationEngine(ATPRuleRepository atpRuleRepository, ATPSnapshotRepository atpSnapshotRepository) {
        this.atpRuleRepository = atpRuleRepository;
        this.atpSnapshotRepository = atpSnapshotRepository;
    }

    // ─── ATP Rules ─────────────────────────────────────────────────────────

    @Transactional
    public NxATPRule createRule(NxATPRule rule) {
        UUID tenantId = TenantContext.getCurrentTenantId();
        rule.setTenantId(tenantId);
        return atpRuleRepository.save(rule);
    }

    @Transactional
    public NxATPRule updateRule(UUID id, NxATPRule updates) {
        NxATPRule rule = atpRuleRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("ATPRule", id));
        if (updates.getName() != null) rule.setName(updates.getName());
        if (updates.getRuleType() != null) rule.setRuleType(updates.getRuleType());
        if (updates.getPriority() != null) rule.setPriority(updates.getPriority());
        if (updates.getSafetyStockPercentage() != null) rule.setSafetyStockPercentage(updates.getSafetyStockPercentage());
        if (updates.getReserveWindowHours() != null) rule.setReserveWindowHours(updates.getReserveWindowHours());
        if (updates.getActive() != null) rule.setActive(updates.getActive());
        return atpRuleRepository.save(rule);
    }

    public List<NxATPRule> getRules() {
        UUID tenantId = TenantContext.getCurrentTenantId();
        return atpRuleRepository.findByTenantIdOrderByPriorityAsc(tenantId);
    }

    public NxATPRule getRule(UUID id) {
        return atpRuleRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("ATPRule", id));
    }

    @Transactional
    public void deleteRule(UUID id) {
        atpRuleRepository.deleteById(id);
    }

    // ─── Core ATP Calculation ──────────────────────────────────────────────

    public Map<String, Object> calculateATP(UUID nodeId, String sku) {
        UUID tenantId = TenantContext.getCurrentTenantId();

        NxATPSnapshot latest = atpSnapshotRepository.findFirstByNodeIdAndSkuOrderBySnapshotDateDesc(nodeId, sku);

        if (latest == null) {
            return Map.of(
                "nodeId", nodeId,
                "sku", sku,
                "available", false,
                "atpQuantity", 0,
                "message", "No inventory snapshot found"
            );
        }

        List<NxATPRule> rules = atpRuleRepository.findByTenantIdAndActiveTrue(tenantId);

        int safetyStock = calculateSafetyStock(latest.getPhysicalStock(), rules);
        int reserved = calculateReservedStock(latest, rules);
        int atpQuantity = Math.max(0, latest.getPhysicalStock() - safetyStock - reserved - latest.getAllocatedStock());
        int totalDemand = latest.getTotalDemand();
        int netATP = Math.max(0, atpQuantity - totalDemand);

        NxATPSnapshot snapshot = NxATPSnapshot.builder()
                .tenantId(tenantId)
                .nodeId(nodeId)
                .sku(sku)
                .physicalStock(latest.getPhysicalStock())
                .reservedStock(reserved)
                .safetyStock(safetyStock)
                .allocatedStock(latest.getAllocatedStock())
                .atpQuantity(atpQuantity)
                .totalDemand(totalDemand)
                .netATP(netATP)
                .snapshotDate(LocalDateTime.now())
                .build();
        atpSnapshotRepository.save(snapshot);

        Map<String, Object> result = new LinkedHashMap<>();
        result.put("nodeId", nodeId);
        result.put("sku", sku);
        result.put("physicalStock", latest.getPhysicalStock());
        result.put("safetyStock", safetyStock);
        result.put("reservedStock", reserved);
        result.put("allocatedStock", latest.getAllocatedStock());
        result.put("atpQuantity", atpQuantity);
        result.put("totalDemand", totalDemand);
        result.put("netATP", netATP);
        result.put("available", netATP > 0);
        result.put("snapshotTime", LocalDateTime.now());
        result.put("rulesApplied", rules.size());

        return result;
    }

    public Map<String, Object> calculateBulkATP(UUID nodeId, List<String> skus) {
        Map<String, Object> results = new LinkedHashMap<>();
        for (String sku : skus) {
            results.put(sku, calculateATP(nodeId, sku));
        }
        return results;
    }

    public List<Map<String, Object>> findNodesWithATP(String sku, Integer requiredQuantity) {
        UUID tenantId = TenantContext.getCurrentTenantId();

        List<NxATPSnapshot> allSnapshots = atpSnapshotRepository.findBySku(tenantId, sku);

        List<Map<String, Object>> availableNodes = new ArrayList<>();

        for (NxATPSnapshot snapshot : allSnapshots) {
            List<NxATPRule> rules = atpRuleRepository.findByTenantIdAndActiveTrue(tenantId);

            int safetyStock = calculateSafetyStock(snapshot.getPhysicalStock(), rules);
            int reserved = calculateReservedStock(snapshot, rules);
            int atp = Math.max(0, snapshot.getPhysicalStock() - safetyStock - reserved - snapshot.getAllocatedStock());

            if (atp >= requiredQuantity) {
                Map<String, Object> nodeInfo = new LinkedHashMap<>();
                nodeInfo.put("nodeId", snapshot.getNodeId());
                nodeInfo.put("atpQuantity", atp);
                nodeInfo.put("physicalStock", snapshot.getPhysicalStock());
                availableNodes.add(nodeInfo);
            }
        }

        availableNodes.sort((a, b) -> Integer.compare((Integer) b.get("atpQuantity"), (Integer) a.get("atpQuantity")));

        return availableNodes;
    }

    // ─── Reservation Operations ────────────────────────────────────────────

    @Transactional
    public boolean reserveStock(UUID nodeId, String sku, int quantity) {
        UUID tenantId = TenantContext.getCurrentTenantId();

        NxATPSnapshot snapshot = atpSnapshotRepository.findFirstByNodeIdAndSkuOrderBySnapshotDateDesc(nodeId, sku);
        if (snapshot == null) return false;

        List<NxATPRule> rules = atpRuleRepository.findByTenantIdAndActiveTrue(tenantId);
        int safetyStock = calculateSafetyStock(snapshot.getPhysicalStock(), rules);
        int reserved = calculateReservedStock(snapshot, rules);
        int atp = Math.max(0, snapshot.getPhysicalStock() - safetyStock - reserved - snapshot.getAllocatedStock());

        if (atp < quantity) {
            log.warn("Insufficient ATP for reservation: nodeId={}, sku={}, requested={}, available={}", nodeId, sku, quantity, atp);
            return false;
        }

        NxATPSnapshot newSnapshot = NxATPSnapshot.builder()
                .tenantId(tenantId)
                .nodeId(nodeId)
                .sku(sku)
                .physicalStock(snapshot.getPhysicalStock())
                .reservedStock(snapshot.getReservedStock() + quantity)
                .safetyStock(safetyStock)
                .allocatedStock(snapshot.getAllocatedStock())
                .atpQuantity(atp - quantity)
                .totalDemand(snapshot.getTotalDemand())
                .netATP(Math.max(0, atp - quantity - snapshot.getTotalDemand()))
                .snapshotDate(LocalDateTime.now())
                .build();
        atpSnapshotRepository.save(newSnapshot);

        log.info("Reserved stock: nodeId={}, sku={}, quantity={}", nodeId, sku, quantity);
        return true;
    }

    @Transactional
    public void releaseReservation(UUID nodeId, String sku, int quantity) {
        UUID tenantId = TenantContext.getCurrentTenantId();

        NxATPSnapshot snapshot = atpSnapshotRepository.findFirstByNodeIdAndSkuOrderBySnapshotDateDesc(nodeId, sku);
        if (snapshot == null) return;

        NxATPSnapshot newSnapshot = NxATPSnapshot.builder()
                .tenantId(tenantId)
                .nodeId(nodeId)
                .sku(sku)
                .physicalStock(snapshot.getPhysicalStock())
                .reservedStock(Math.max(0, snapshot.getReservedStock() - quantity))
                .safetyStock(snapshot.getSafetyStock())
                .allocatedStock(snapshot.getAllocatedStock())
                .atpQuantity(snapshot.getAtpQuantity() + quantity)
                .totalDemand(snapshot.getTotalDemand())
                .netATP(snapshot.getNetATP() + quantity)
                .snapshotDate(LocalDateTime.now())
                .build();
        atpSnapshotRepository.save(newSnapshot);

        log.info("Released reservation: nodeId={}, sku={}, quantity={}", nodeId, sku, quantity);
    }

    @Transactional
    public void allocateStock(UUID nodeId, String sku, int quantity) {
        UUID tenantId = TenantContext.getCurrentTenantId();

        NxATPSnapshot snapshot = atpSnapshotRepository.findFirstByNodeIdAndSkuOrderBySnapshotDateDesc(nodeId, sku);
        if (snapshot == null) return;

        NxATPSnapshot newSnapshot = NxATPSnapshot.builder()
                .tenantId(tenantId)
                .nodeId(nodeId)
                .sku(sku)
                .physicalStock(snapshot.getPhysicalStock())
                .reservedStock(Math.max(0, snapshot.getReservedStock() - quantity))
                .safetyStock(snapshot.getSafetyStock())
                .allocatedStock(snapshot.getAllocatedStock() + quantity)
                .atpQuantity(snapshot.getAtpQuantity())
                .totalDemand(Math.max(0, snapshot.getTotalDemand() - quantity))
                .netATP(snapshot.getNetATP())
                .snapshotDate(LocalDateTime.now())
                .build();
        atpSnapshotRepository.save(newSnapshot);

        log.info("Allocated stock: nodeId={}, sku={}, quantity={}", nodeId, sku, quantity);
    }

    // ─── Snapshot Operations ───────────────────────────────────────────────

    public List<NxATPSnapshot> getSnapshots(UUID nodeId, String sku) {
        if (nodeId != null && sku != null) {
            return atpSnapshotRepository.findByNodeIdAndSku(nodeId, sku);
        }
        UUID tenantId = TenantContext.getCurrentTenantId();
        return atpSnapshotRepository.findByTenantId(tenantId);
    }

    @Transactional
    public NxATPSnapshot updateSnapshot(UUID nodeId, String sku, int physicalStock) {
        UUID tenantId = TenantContext.getCurrentTenantId();

        NxATPSnapshot existing = atpSnapshotRepository.findFirstByNodeIdAndSkuOrderBySnapshotDateDesc(nodeId, sku);

        List<NxATPRule> rules = atpRuleRepository.findByTenantIdAndActiveTrue(tenantId);
        int safetyStock = calculateSafetyStock(physicalStock, rules);
        int reserved = existing != null ? existing.getReservedStock() : 0;
        int allocated = existing != null ? existing.getAllocatedStock() : 0;
        int totalDemand = existing != null ? existing.getTotalDemand() : 0;
        int atp = Math.max(0, physicalStock - safetyStock - reserved - allocated);

        NxATPSnapshot snapshot = NxATPSnapshot.builder()
                .tenantId(tenantId)
                .nodeId(nodeId)
                .sku(sku)
                .physicalStock(physicalStock)
                .reservedStock(reserved)
                .safetyStock(safetyStock)
                .allocatedStock(allocated)
                .atpQuantity(atp)
                .totalDemand(totalDemand)
                .netATP(Math.max(0, atp - totalDemand))
                .snapshotDate(LocalDateTime.now())
                .build();
        return atpSnapshotRepository.save(snapshot);
    }

    // ─── Private Helpers ───────────────────────────────────────────────────

    private int calculateSafetyStock(int physicalStock, List<NxATPRule> rules) {
        for (NxATPRule rule : rules) {
            if ("SAFETY_STOCK".equals(rule.getRuleType()) && rule.getSafetyStockPercentage() != null) {
                return BigDecimal.valueOf(physicalStock)
                        .multiply(rule.getSafetyStockPercentage())
                        .divide(BigDecimal.valueOf(100), 0, RoundingMode.CEILING)
                        .intValue();
            }
        }
        return 0;
    }

    private int calculateReservedStock(NxATPSnapshot snapshot, List<NxATPRule> rules) {
        int reserved = snapshot.getReservedStock();
        for (NxATPRule rule : rules) {
            if ("HARD_RESERVE".equals(rule.getRuleType()) && rule.getReserveWindowHours() != null) {
                reserved = Math.max(reserved, snapshot.getPhysicalStock() / 10);
            }
        }
        return reserved;
    }
}
