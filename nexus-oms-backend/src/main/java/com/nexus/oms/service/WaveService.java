package com.nexus.oms.service;

import com.nexus.oms.entity.NxPicklist;
import com.nexus.oms.entity.NxWave;
import com.nexus.oms.entity.NxWaveRule;
import com.nexus.oms.exception.ResourceNotFoundException;
import com.nexus.oms.repository.PicklistRepository;
import com.nexus.oms.repository.WaveRepository;
import com.nexus.oms.repository.WaveRuleRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.time.temporal.ChronoUnit;
import java.util.*;
import java.util.stream.Collectors;

@Service
@Transactional
public class WaveService {

    private static final Logger log = LoggerFactory.getLogger(WaveService.class);

    private static final Set<String> ACTIVE_STATES = Set.of(
            "DRAFT", "PLANNED", "RELEASING", "RELEASING_PAUSED", "RELEASED", "IN_PROGRESS"
    );

    private final WaveRepository waveRepository;
    private final WaveRuleRepository waveRuleRepository;
    private final PicklistRepository picklistRepository;

    public WaveService(WaveRepository waveRepository,
                       WaveRuleRepository waveRuleRepository,
                       PicklistRepository picklistRepository) {
        this.waveRepository = waveRepository;
        this.waveRuleRepository = waveRuleRepository;
        this.picklistRepository = picklistRepository;
    }

    public List<NxWave> getWaves(UUID tenantId, String status) {
        if (status != null && !status.isBlank()) {
            return waveRepository.findByTenantIdAndStatus(tenantId, status);
        }
        return waveRepository.findByTenantId(tenantId);
    }

    public NxWave getWave(UUID id) {
        return waveRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Wave", id));
    }

    public NxWave createWave(NxWave wave) {
        wave.setStatus("DRAFT");
        return waveRepository.save(wave);
    }

    public NxWave updateWave(UUID id, NxWave updates) {
        NxWave wave = getWave(id);
        if (!"DRAFT".equals(wave.getStatus())) {
            throw new IllegalStateException("Only DRAFT waves can be updated. Current status: " + wave.getStatus());
        }
        if (updates.getName() != null) wave.setName(updates.getName());
        if (updates.getWarehouseId() != null) wave.setWarehouseId(updates.getWarehouseId());
        if (updates.getPriority() != null) wave.setPriority(updates.getPriority());
        if (updates.getWaveType() != null) wave.setWaveType(updates.getWaveType());
        if (updates.getZoneFilter() != null) wave.setZoneFilter(updates.getZoneFilter());
        if (updates.getTargetCompletionAt() != null) wave.setTargetCompletionAt(updates.getTargetCompletionAt());
        if (updates.getNotes() != null) wave.setNotes(updates.getNotes());
        if (updates.getMetadata() != null) wave.setMetadata(updates.getMetadata());
        return waveRepository.save(wave);
    }

    public NxWaveRule addRule(UUID waveId, NxWaveRule rule) {
        NxWave wave = getWave(waveId);
        if (!"DRAFT".equals(wave.getStatus())) {
            throw new IllegalStateException("Can only add rules to DRAFT waves. Current status: " + wave.getStatus());
        }
        rule.setWaveId(waveId);
        rule.setTenantId(wave.getTenantId());
        return waveRuleRepository.save(rule);
    }

    public void removeRule(UUID ruleId) {
        NxWaveRule rule = waveRuleRepository.findById(ruleId)
                .orElseThrow(() -> new ResourceNotFoundException("WaveRule", ruleId));
        NxWave wave = getWave(rule.getWaveId());
        if (!"DRAFT".equals(wave.getStatus())) {
            throw new IllegalStateException("Can only remove rules from DRAFT waves. Current status: " + wave.getStatus());
        }
        waveRuleRepository.deleteById(ruleId);
    }

    public NxWave planWave(UUID id) {
        NxWave wave = getWave(id);
        if (!"DRAFT".equals(wave.getStatus())) {
            throw new IllegalStateException("Only DRAFT waves can be planned. Current status: " + wave.getStatus());
        }
        List<NxWaveRule> rules = waveRuleRepository.findByWaveIdAndIsActive(id, true);
        int matchingOrders = simulateOrderCount(wave, rules);
        int totalLines = matchingOrders * 3;

        wave.setStatus("PLANNED");
        wave.setOrderCount(matchingOrders);
        wave.setTotalLineItems(totalLines);

        log.info("Wave {} planned with {} matching orders, {} total line items", id, matchingOrders, totalLines);
        return waveRepository.save(wave);
    }

    public NxWave releaseWave(UUID id, String releasedBy) {
        NxWave wave = getWave(id);
        if (!"PLANNED".equals(wave.getStatus())) {
            throw new IllegalStateException("Only PLANNED waves can be released. Current status: " + wave.getStatus());
        }

        wave.setStatus("IN_PROGRESS");
        wave.setReleasedAt(LocalDateTime.now());
        wave.setReleasedBy(releasedBy);

        List<NxWaveRule> rules = waveRuleRepository.findByWaveIdAndIsActive(id, true);
        List<Map<String, Object>> simulatedOrders = simulateMatchingOrders(wave, rules);

        createPicklistsFromWave(wave, simulatedOrders);
        wave.setReleasedLineItems(wave.getTotalLineItems());

        log.info("Wave {} released by {} with {} orders, {} picklists created",
                id, releasedBy, simulatedOrders.size(), Math.max(1, simulatedOrders.size()));
        return waveRepository.save(wave);
    }

    public NxWave pauseWave(UUID id) {
        NxWave wave = getWave(id);
        if (!"IN_PROGRESS".equals(wave.getStatus())) {
            throw new IllegalStateException("Only IN_PROGRESS waves can be paused. Current status: " + wave.getStatus());
        }
        wave.setStatus("RELEASING_PAUSED");
        log.info("Wave {} paused", id);
        return waveRepository.save(wave);
    }

    public NxWave resumeWave(UUID id) {
        NxWave wave = getWave(id);
        if (!"RELEASING_PAUSED".equals(wave.getStatus())) {
            throw new IllegalStateException("Only RELEASING_PAUSED waves can be resumed. Current status: " + wave.getStatus());
        }
        wave.setStatus("IN_PROGRESS");
        log.info("Wave {} resumed", id);
        return waveRepository.save(wave);
    }

    public NxWave completeWave(UUID id) {
        NxWave wave = getWave(id);
        if (!"IN_PROGRESS".equals(wave.getStatus())) {
            throw new IllegalStateException("Only IN_PROGRESS waves can be completed. Current status: " + wave.getStatus());
        }
        wave.setStatus("COMPLETED");
        wave.setCompletedAt(LocalDateTime.now());
        wave.setCompletedLineItems(wave.getTotalLineItems());
        log.info("Wave {} completed", id);
        return waveRepository.save(wave);
    }

    public NxWave cancelWave(UUID id) {
        NxWave wave = getWave(id);
        if (!ACTIVE_STATES.contains(wave.getStatus())) {
            throw new IllegalStateException("Cannot cancel wave in status: " + wave.getStatus());
        }
        String previousStatus = wave.getStatus();
        wave.setStatus("CANCELLED");
        log.info("Wave {} cancelled from status {}", id, previousStatus);
        return waveRepository.save(wave);
    }

    public NxWave optimizeWave(UUID id) {
        NxWave wave = getWave(id);
        List<NxWaveRule> rules = waveRuleRepository.findByWaveIdAndIsActive(id, true);

        int score = calculateOptimizationScore(wave, rules);
        wave.setOptimizationScore(score);

        log.info("Wave {} optimized with score {}/100", id, score);
        return waveRepository.save(wave);
    }

    public Map<String, Object> getWaveStats(UUID tenantId) {
        List<NxWave> allWaves = waveRepository.findByTenantId(tenantId);
        LocalDateTime startOfDay = LocalDateTime.now().withHour(0).withMinute(0).withSecond(0).withNano(0);

        long totalWaves = allWaves.size();
        long activeWaves = allWaves.stream()
                .filter(w -> ACTIVE_STATES.contains(w.getStatus()))
                .count();
        long completedToday = allWaves.stream()
                .filter(w -> "COMPLETED".equals(w.getStatus()))
                .filter(w -> w.getCompletedAt() != null && w.getCompletedAt().isAfter(startOfDay))
                .count();

        double avgCompletionTime = allWaves.stream()
                .filter(w -> "COMPLETED".equals(w.getStatus()) && w.getReleasedAt() != null && w.getCompletedAt() != null)
                .mapToLong(w -> ChronoUnit.MINUTES.between(w.getReleasedAt(), w.getCompletedAt()))
                .average()
                .orElse(0.0);

        Map<String, Object> stats = new LinkedHashMap<>();
        stats.put("totalWaves", totalWaves);
        stats.put("activeWaves", activeWaves);
        stats.put("completedToday", completedToday);
        stats.put("avgCompletionTimeMinutes", Math.round(avgCompletionTime * 100.0) / 100.0);
        return stats;
    }

    private int simulateOrderCount(NxWave wave, List<NxWaveRule> rules) {
        int baseCount = 5 + wave.getTenantId().hashCode() % 20;
        if (rules.isEmpty()) return baseCount;
        return Math.max(1, baseCount - rules.size());
    }

    private List<Map<String, Object>> simulateMatchingOrders(NxWave wave, List<NxWaveRule> rules) {
        int count = wave.getOrderCount() != null ? wave.getOrderCount() : simulateOrderCount(wave, rules);
        List<Map<String, Object>> orders = new ArrayList<>();
        for (int i = 0; i < count; i++) {
            Map<String, Object> order = new LinkedHashMap<>();
            order.put("orderId", UUID.randomUUID());
            order.put("lineItemCount", 1 + (i % 4));
            order.put("zone", resolveZoneForOrder(wave, i));
            orders.add(order);
        }
        return orders;
    }

    private String resolveZoneForOrder(NxWave wave, int index) {
        if (wave.getZoneFilter() != null && !wave.getZoneFilter().isBlank()) {
            String[] zones = wave.getZoneFilter().split(",");
            return zones[index % zones.length].trim();
        }
        return "ZONE-" + (char) ('A' + (index % 5));
    }

    private void createPicklistsFromWave(NxWave wave, List<Map<String, Object>> orders) {
        String waveType = wave.getWaveType();
        if ("ZONE".equals(waveType)) {
            Map<String, List<Map<String, Object>>> grouped = orders.stream()
                    .collect(Collectors.groupingBy(o -> (String) o.get("zone")));
            for (Map.Entry<String, List<Map<String, Object>>> entry : grouped.entrySet()) {
                createSinglePicklist(wave, entry.getKey(), entry.getValue());
            }
        } else {
            createSinglePicklist(wave, wave.getName(), orders);
        }
    }

    private void createSinglePicklist(NxWave wave, String label, List<Map<String, Object>> orders) {
        int totalItems = orders.stream()
                .mapToInt(o -> (int) o.getOrDefault("lineItemCount", 1))
                .sum();

        NxPicklist picklist = NxPicklist.builder()
                .tenantId(wave.getTenantId())
                .name(wave.getName() + " - " + label)
                .waveType(wave.getWaveType())
                .priority(wave.getPriority())
                .status("OPEN")
                .totalItems(totalItems)
                .pickedItems(0)
                .orderIds(orders.stream()
                        .map(o -> o.get("orderId").toString())
                        .collect(Collectors.joining(",")))
                .createdBy(wave.getReleasedBy())
                .build();
        picklistRepository.save(picklist);
    }

    private int calculateOptimizationScore(NxWave wave, List<NxWaveRule> rules) {
        int score = 0;
        int orderCount = wave.getOrderCount() != null ? wave.getOrderCount() : 0;
        score += Math.min(30, orderCount * 3);
        score += Math.min(20, rules.size() * 5);
        if (wave.getZoneFilter() != null && !wave.getZoneFilter().isBlank()) {
            score += 15;
        }
        if (wave.getTargetCompletionAt() != null) {
            long hoursUntil = ChronoUnit.HOURS.between(LocalDateTime.now(), wave.getTargetCompletionAt());
            if (hoursUntil > 0 && hoursUntil <= 24) score += 15;
            else if (hoursUntil > 24) score += 10;
        }
        score += Math.min(20, wave.getTotalLineItems() != null ? wave.getTotalLineItems() : 0);
        return Math.min(100, score);
    }
}
