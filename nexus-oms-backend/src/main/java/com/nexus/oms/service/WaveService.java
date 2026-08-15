package com.nexus.oms.service;

import com.nexus.oms.entity.NxOrder;
import com.nexus.oms.entity.NxOrderItem;
import com.nexus.oms.entity.NxPicklist;
import com.nexus.oms.entity.NxPicklistItem;
import com.nexus.oms.entity.NxWave;
import com.nexus.oms.entity.NxWaveRule;
import com.nexus.oms.entity.Warehouse;
import com.nexus.oms.exception.ResourceNotFoundException;
import com.nexus.oms.repository.OrderItemRepository;
import com.nexus.oms.repository.OrderRepository;
import com.nexus.oms.repository.PicklistItemRepository;
import com.nexus.oms.repository.PicklistRepository;
import com.nexus.oms.repository.WarehouseRepository;
import com.nexus.oms.repository.WaveRepository;
import com.nexus.oms.repository.WaveRuleRepository;
import com.nexus.oms.security.TenantContext;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.data.domain.Pageable;
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

    private static final Set<String> PICKABLE_STATES = Set.of(
            "PENDING", "APPROVED", "ALLOCATED", "RELEASED", "PICKING"
    );

    private static final Set<String> PICKLIST_ACTIVE_STATUSES = Set.of("OPEN", "IN_PROGRESS");

    private final WaveRepository waveRepository;
    private final WaveRuleRepository waveRuleRepository;
    private final PicklistRepository picklistRepository;
    private final WarehouseRepository warehouseRepository;
    private final OrderRepository orderRepository;
    private final OrderItemRepository orderItemRepository;
    private final PicklistItemRepository picklistItemRepository;

    public WaveService(WaveRepository waveRepository,
                       WaveRuleRepository waveRuleRepository,
                       PicklistRepository picklistRepository,
                       WarehouseRepository warehouseRepository,
                       OrderRepository orderRepository,
                       OrderItemRepository orderItemRepository,
                       PicklistItemRepository picklistItemRepository) {
        this.waveRepository = waveRepository;
        this.waveRuleRepository = waveRuleRepository;
        this.picklistRepository = picklistRepository;
        this.warehouseRepository = warehouseRepository;
        this.orderRepository = orderRepository;
        this.orderItemRepository = orderItemRepository;
        this.picklistItemRepository = picklistItemRepository;
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
        if (wave.getWarehouseId() == null) {
            UUID tenantId = TenantContext.getCurrentTenantId();
            List<Warehouse> warehouses = warehouseRepository.findByTenantIdAndStatus(tenantId, "ACTIVE");
            if (!warehouses.isEmpty()) {
                wave.setWarehouseId(warehouses.get(0).getId());
            }
        }
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
        List<NxOrder> matchingOrders = findEligibleOrders(wave);
        int totalLineItems = sumQuantities(matchingOrders);

        wave.setStatus("PLANNED");
        wave.setOrderCount(matchingOrders.size());
        wave.setTotalLineItems(totalLineItems);

        log.info("Wave {} planned with {} matching orders, {} total line items", id, matchingOrders.size(), totalLineItems);
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

        List<NxOrder> matchingOrders = findEligibleOrders(wave);
        createPicklistsFromOrders(wave, matchingOrders);
        int totalLineItems = sumQuantities(matchingOrders);
        wave.setOrderCount(matchingOrders.size());
        wave.setTotalLineItems(totalLineItems);
        wave.setReleasedLineItems(totalLineItems);

        log.info("Wave {} released by {} with {} real orders, {} line items",
                id, releasedBy, matchingOrders.size(), totalLineItems);
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
        wave.setCompletedLineItems(wave.getReleasedLineItems());
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

    private List<NxOrder> findEligibleOrders(NxWave wave) {
        List<NxOrder> candidates = orderRepository.findByTenantId(wave.getTenantId(), Pageable.unpaged()).stream()
                .filter(o -> PICKABLE_STATES.contains(o.getStatus()))
                .toList();

        if (candidates.isEmpty()) {
            log.info("Wave {} matched 0 eligible orders for tenant {}", wave.getId(), wave.getTenantId());
            return List.of();
        }

        Set<UUID> alreadyPickedOrderIds = picklistRepository.findByTenantId(wave.getTenantId()).stream()
                .filter(pl -> PICKLIST_ACTIVE_STATUSES.contains(pl.getStatus()))
                .flatMap(pl -> Arrays.stream(pl.getOrderIds() == null ? new String[0]
                        : pl.getOrderIds().split(",")))
                .map(s -> {
                    try {
                        return UUID.fromString(s.trim());
                    } catch (IllegalArgumentException e) {
                        return null;
                    }
                })
                .filter(Objects::nonNull)
                .collect(Collectors.toSet());

        List<NxWaveRule> rules = waveRuleRepository.findByWaveIdAndIsActive(wave.getId(), true);

        return candidates.stream()
                .filter(o -> !alreadyPickedOrderIds.contains(o.getId()))
                .filter(o -> matchesRules(o, rules))
                .toList();
    }

    private boolean matchesRules(NxOrder order, List<NxWaveRule> rules) {
        if (rules.isEmpty()) return true;
        for (NxWaveRule rule : rules) {
            String field = rule.getRuleType() == null ? "" : rule.getRuleType().toUpperCase();
            String actual = switch (field) {
                case "CHANNEL" -> order.getChannel();
                case "STATUS" -> order.getStatus();
                case "FULFILLMENT_TYPE" -> order.getFulfillmentType();
                case "SHIP_FROM", "ZONE" -> order.getShipFrom();
                case "PAYMENT_STATUS" -> order.getPaymentStatus();
                default -> null;
            };
            if (!evaluate(actual, rule.getOperator(), rule.getValue())) {
                return false;
            }
        }
        return true;
    }

    private boolean evaluate(String actual, String operator, String value) {
        String op = operator == null ? "=" : operator.toUpperCase();
        String val = value == null ? "" : value.trim();
        String act = actual == null ? "" : actual;
        return switch (op) {
            case "=", "EQ" -> act.equalsIgnoreCase(val);
            case "!=", "NEQ" -> !act.equalsIgnoreCase(val);
            case "IN" -> Arrays.stream(val.split(","))
                    .map(String::trim).anyMatch(v -> act.equalsIgnoreCase(v));
            case "LIKE", "CONTAINS" -> act.toLowerCase().contains(val.toLowerCase());
            default -> act.equalsIgnoreCase(val);
        };
    }

    private void createPicklistsFromOrders(NxWave wave, List<NxOrder> orders) {
        if (orders.isEmpty()) return;
        if ("ZONE".equals(wave.getWaveType())) {
            Map<String, List<NxOrder>> grouped = orders.stream()
                    .collect(Collectors.groupingBy(o -> resolveZoneForOrder(wave, o)));
            for (Map.Entry<String, List<NxOrder>> entry : grouped.entrySet()) {
                createSinglePicklist(wave, entry.getKey(), entry.getValue());
            }
        } else {
            createSinglePicklist(wave, wave.getName(), orders);
        }
    }

    private void createSinglePicklist(NxWave wave, String label, List<NxOrder> orders) {
        int totalItems = orders.stream()
                .mapToInt(o -> sumQuantities(List.of(o)))
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
                        .map(o -> o.getId().toString())
                        .collect(Collectors.joining(",")))
                .createdBy(wave.getReleasedBy())
                .build();
        picklist = picklistRepository.save(picklist);

        for (NxOrder order : orders) {
            seedItemsIntoPicklist(picklist, order);
        }
    }

    private void seedItemsIntoPicklist(NxPicklist picklist, NxOrder order) {
        List<NxOrderItem> items = orderItemRepository.findByOrderId(order.getId());
        for (NxOrderItem item : items) {
            picklistItemRepository.save(NxPicklistItem.builder()
                    .picklistId(picklist.getId())
                    .tenantId(picklist.getTenantId())
                    .orderId(order.getId())
                    .orderItemId(item.getId())
                    .sku(item.getSku())
                    .productName(item.getProductName())
                    .quantity(item.getQuantity())
                    .pickedQuantity(0)
                    .status("PENDING")
                    .build());
        }
        if (!items.isEmpty()) {
            order.setStatus("ALLOCATED");
            orderRepository.save(order);
        }
    }

    private int sumQuantities(List<NxOrder> orders) {
        int sum = 0;
        for (NxOrder order : orders) {
            for (NxOrderItem item : orderItemRepository.findByOrderId(order.getId())) {
                sum += item.getQuantity() == null ? 0 : item.getQuantity();
            }
        }
        return sum;
    }

    private String resolveZoneForOrder(NxWave wave, NxOrder order) {
        String zone = order.getShipFrom();
        if (zone != null && !zone.isBlank()) return zone;
        if (wave.getZoneFilter() != null && !wave.getZoneFilter().isBlank()) {
            return wave.getZoneFilter().split(",")[0].trim();
        }
        return "DEFAULT";
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
