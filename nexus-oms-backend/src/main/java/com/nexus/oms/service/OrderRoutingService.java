package com.nexus.oms.service;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.nexus.oms.dto.AllocationRequest;
import com.nexus.oms.dto.AllocationResult;
import com.nexus.oms.dto.ExceptionResolutionRequest;
import com.nexus.oms.entity.*;
import com.nexus.oms.exception.BadRequestException;
import com.nexus.oms.exception.ResourceNotFoundException;
import com.nexus.oms.repository.OrderAllocationRepository;
import com.nexus.oms.repository.FulfillmentExceptionRepository;
import com.nexus.oms.repository.NxRoutingRuleRepository;
import com.nexus.oms.repository.RoutingConfigRepository;
import com.nexus.oms.repository.RoutingLogRepository;
import com.nexus.oms.repository.WarehouseRepository;
import com.nexus.oms.repository.OrderRepository;
import com.nexus.oms.security.TenantContext;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDateTime;
import java.util.*;
import java.util.stream.Collectors;

@Service
public class OrderRoutingService {

    private static final Logger log = LoggerFactory.getLogger(OrderRoutingService.class);
    private static final ObjectMapper MAPPER = new ObjectMapper();
    private final RuleConditionEvaluator conditionEvaluator = new RuleConditionEvaluator();

    private final OrderAllocationRepository allocationRepository;
    private final FulfillmentExceptionRepository exceptionRepository;
    private final NxRoutingRuleRepository routingRuleRepository;
    private final RoutingConfigRepository routingConfigRepository;
    private final RoutingLogRepository routingLogRepository;
    private final WarehouseRepository warehouseRepository;
    private final OrderRepository orderRepository;

    public OrderRoutingService(OrderAllocationRepository allocationRepository,
                                FulfillmentExceptionRepository exceptionRepository,
                                NxRoutingRuleRepository routingRuleRepository,
                                RoutingConfigRepository routingConfigRepository,
                                RoutingLogRepository routingLogRepository,
                                WarehouseRepository warehouseRepository,
                                OrderRepository orderRepository) {
        this.allocationRepository = allocationRepository;
        this.exceptionRepository = exceptionRepository;
        this.routingRuleRepository = routingRuleRepository;
        this.routingConfigRepository = routingConfigRepository;
        this.routingLogRepository = routingLogRepository;
        this.warehouseRepository = warehouseRepository;
        this.orderRepository = orderRepository;
    }

    @Transactional
    public AllocationResult allocateOrder(AllocationRequest request) {
        long startTime = System.currentTimeMillis();
        UUID tenantId = TenantContext.getCurrentTenantId();

        NxOrder order = orderRepository.findById(request.getOrderId())
                .orElseThrow(() -> new ResourceNotFoundException("Order not found: " + request.getOrderId()));

        if (!"PENDING".equals(order.getStatus())) {
            throw new BadRequestException("Order must be in PENDING status to allocate");
        }

        String strategy = request.getStrategy();
        if (strategy == null || strategy.isBlank()) {
            NxRoutingConfig config = routingConfigRepository.findByTenantId(tenantId).orElse(null);
            strategy = config != null ? config.getDefaultStrategy() : "HYBRID";
        }

        boolean dryRun = request.getDryRun() != null && request.getDryRun();

        List<NxOrderAllocation> allocations;
        List<NxFulfillmentException> exceptions = new ArrayList<>();
        String explanation;
        BigDecimal confidenceScore;
        LocalDateTime deliveryPromise;
        BigDecimal totalCost;

        switch (strategy.toUpperCase()) {
            case "RULE_BASED" -> {
                allocations = ruleBasedAllocation(order, tenantId);
                explanation = "Allocated using rule-based routing engine";
                confidenceScore = new BigDecimal("0.7500");
            }
            case "AI_OPTIMIZED" -> {
                allocations = aiOptimizedAllocation(order, tenantId);
                explanation = "Allocated using AI optimization model";
                confidenceScore = new BigDecimal("0.8500");
            }
            case "HYBRID" -> {
                List<NxOrderAllocation> ruleAllocs = ruleBasedAllocation(order, tenantId);
                List<NxOrderAllocation> aiAllocs = aiOptimizedAllocation(order, tenantId);

                NxRoutingConfig config = routingConfigRepository.findByTenantId(tenantId).orElse(null);
                BigDecimal threshold = config != null && config.getAiConfidenceThreshold() != null
                        ? config.getAiConfidenceThreshold()
                        : new BigDecimal("0.7000");

                allocations = selectBestAllocations(ruleAllocs, aiAllocs, threshold);
                explanation = "Allocated using hybrid strategy (rule + AI ensemble)";
                confidenceScore = calculateHybridConfidence(ruleAllocs, aiAllocs);
            }
            default ->
                throw new BadRequestException("Invalid strategy: " + strategy + ". Must be RULE_BASED, AI_OPTIMIZED, or HYBRID");
        }

        deliveryPromise = calculateDeliveryPromise(order, allocations);
        totalCost = allocations.stream()
                .map(a -> a.getCostEstimated() != null ? a.getCostEstimated() : BigDecimal.ZERO)
                .reduce(BigDecimal.ZERO, BigDecimal::add);

        if (!dryRun) {
            allocations.forEach(a -> {
                a.setAllocatedAt(LocalDateTime.now());
                a.setAllocatedBy(getCurrentUserId());
                allocationRepository.save(a);
            });

            order.setAllocatedNode(allocations.isEmpty() ? null : allocations.get(0).getNodeId());
            order.setAllocationRule(strategy);
            order.setAllocationConfidence(confidenceScore);
            order.setPromisedDelivery(deliveryPromise);
            order.setStatus("ALLOCATED");
            orderRepository.save(order);

            if (configHasExceptionDetection(tenantId)) {
                exceptions = detectFulfillmentExceptions(order, allocations, tenantId);
                if (!exceptions.isEmpty()) {
                    exceptionRepository.saveAll(exceptions);
                }
            }
        }

        long execTime = System.currentTimeMillis() - startTime;

        saveRoutingLog(order.getId(), tenantId, strategy, allocations, exceptions,
                deliveryPromise, totalCost, confidenceScore, (int) execTime, "SUCCESS", null);

        return AllocationResult.builder()
                .orderId(order.getId())
                .strategy(strategy)
                .status(dryRun ? "SIMULATED" : "ALLOCATED")
                .allocations(allocations)
                .estimatedDeliveryDate(deliveryPromise)
                .confidenceScore(confidenceScore)
                .totalCost(totalCost)
                .exceptions(exceptions)
                .explanation(explanation)
                .executionTimeMs(execTime)
                .build();
    }

    List<NxOrderAllocation> ruleBasedAllocation(NxOrder order, UUID tenantId) {
        List<NxRoutingRule> rules = routingRuleRepository.findByTenantIdAndIsActiveTrue(tenantId);
        List<Warehouse> warehouses = warehouseRepository.findByTenantIdAndStatus(tenantId, "ACTIVE");
        List<NxOrderAllocation> allocations = new ArrayList<>();

        if (rules.isEmpty() || warehouses.isEmpty()) {
            return createDefaultAllocation(order, tenantId, warehouses);
        }

        for (NxRoutingRule rule : rules) {
            if (!conditionEvaluator.evaluate(rule, order)) continue;

            JsonNode actions = conditionEvaluator.parseJson(rule.getActions());
            if (actions == null || !actions.has("nodeIds")) continue;

            List<UUID> targetNodeIds = new ArrayList<>();
            actions.get("nodeIds").forEach(n -> targetNodeIds.add(UUID.fromString(n.asText())));

            for (UUID nodeId : targetNodeIds) {
                warehouseRepository.findById(nodeId).ifPresent(wh -> {
                    allocations.add(buildAllocation(order, tenantId, wh, rule, "RULE_BASED"));
                });
            }
            if (!allocations.isEmpty()) break;
        }

        if (allocations.isEmpty()) {
            allocations.addAll(createDefaultAllocation(order, tenantId, warehouses));
        }

        return allocations;
    }

    List<NxOrderAllocation> aiOptimizedAllocation(NxOrder order, UUID tenantId) {
        List<Warehouse> warehouses = warehouseRepository.findByTenantIdAndStatus(tenantId, "ACTIVE");
        List<NxOrderAllocation> allocations = new ArrayList<>();

        if (warehouses.isEmpty()) return allocations;

        String shipRegion = conditionEvaluator.extractRegion(order.getShipToAddress());

        List<Warehouse> scored = warehouses.stream()
                .map(wh -> {
                    BigDecimal score = scoreNodeForOrder(wh, order, shipRegion);
                    return new AbstractMap.SimpleEntry<>(wh, score);
                })
                .sorted((a, b) -> b.getValue().compareTo(a.getValue()))
                .limit(3)
                .map(Map.Entry::getKey)
                .toList();

        int totalQty = estimateOrderQuantity(order);
        int remaining = totalQty;

        for (int i = 0; i < scored.size() && remaining > 0; i++) {
            Warehouse wh = scored.get(i);
            int allocQty = (i == scored.size() - 1) ? remaining : remaining / (scored.size() - i);
            BigDecimal cost = estimateShippingCost(wh, order, allocQty);
            BigDecimal distance = estimateDistance(wh, shipRegion);

            NxOrderAllocation allocation = NxOrderAllocation.builder()
                    .orderId(order.getId())
                    .tenantId(tenantId)
                    .nodeId(wh.getId())
                    .nodeName(wh.getName())
                    .nodeType(wh.getType() != null ? wh.getType() : "WAREHOUSE")
                    .priority(i + 1)
                    .quantityAllocated(allocQty)
                    .quantityRequested(allocQty)
                    .status("ALLOCATED")
                    .allocationStrategy("AI_OPTIMIZED")
                    .costEstimated(cost)
                    .distanceKm(distance)
                    .deliveryPromiseConfidence(calculateNodeConfidence(wh, order))
                    .build();

            allocations.add(allocation);
            remaining -= allocQty;
        }

        return allocations;
    }

    @Transactional
    public AllocationResult reallocateOrder(UUID orderId, String strategy) {
        AllocationRequest request = new AllocationRequest();
        request.setOrderId(orderId);
        request.setStrategy(strategy);
        request.setDryRun(false);

        List<NxOrderAllocation> existing = allocationRepository.findByOrderId(orderId);
        existing.forEach(a -> {
            a.setStatus("CANCELLED");
            allocationRepository.save(a);
        });

        return allocateOrder(request);
    }

    public List<NxOrderAllocation> getAllocationsForOrder(UUID orderId) {
        return allocationRepository.findByOrderId(orderId);
    }

    public Page<NxFulfillmentException> getExceptions(String status, String severity, Pageable pageable) {
        UUID tenantId = TenantContext.getCurrentTenantId();
        if (status != null && !status.isBlank()) {
            return exceptionRepository.findByTenantIdAndStatus(tenantId, status, pageable);
        }
        if (severity != null && !severity.isBlank()) {
            return exceptionRepository.findByTenantIdAndSeverity(tenantId, severity, pageable);
        }
        return exceptionRepository.findByTenantId(tenantId, pageable);
    }

    public NxFulfillmentException getException(UUID id) {
        return exceptionRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Exception not found: " + id));
    }

    @Transactional
    public NxFulfillmentException resolveException(UUID id, ExceptionResolutionRequest request) {
        UUID tenantId = TenantContext.getCurrentTenantId();
        NxFulfillmentException exception = getException(id);

        if (!tenantId.equals(exception.getTenantId())) {
            throw new BadRequestException("Tenant mismatch");
        }

        exception.setStatus("RESOLVED");
        exception.setResolution(request.getResolution());
        exception.setResolutionStrategy(request.getResolutionStrategy());
        exception.setResolvedAt(LocalDateTime.now());
        exception.setResolvedBy(getCurrentUserId());

        return exceptionRepository.save(exception);
    }

    @Transactional
    public NxFulfillmentException escalateException(UUID id) {
        NxFulfillmentException exception = getException(id);
        exception.setStatus("ESCALATED");
        exception.setSeverity("CRITICAL");
        exception.setEscalatedAt(LocalDateTime.now());
        return exceptionRepository.save(exception);
    }

    public Map<String, Object> getRoutingKPIs() {
        UUID tenantId = TenantContext.getCurrentTenantId();
        Map<String, Object> kpis = new HashMap<>();

        kpis.put("totalAllocationsToday", allocationRepository.countByTenantId(tenantId));
        kpis.put("activeAllocations", allocationRepository.countByTenantIdAndStatus(tenantId, "ALLOCATED"));
        kpis.put("openExceptions", exceptionRepository.countByTenantIdAndStatus(tenantId, "OPEN"));
        kpis.put("criticalExceptions", exceptionRepository.countByTenantIdAndSeverity(tenantId, "CRITICAL"));

        return kpis;
    }

    public Map<String, Object> simulateAllocation(AllocationRequest request) {
        request.setDryRun(true);
        AllocationResult result = allocateOrder(request);
        return Map.of(
            "orderId", result.getOrderId(),
            "strategy", result.getStrategy(),
            "allocations", result.getAllocations(),
            "estimatedDeliveryDate", result.getEstimatedDeliveryDate(),
            "confidenceScore", result.getConfidenceScore(),
            "totalCost", result.getTotalCost(),
            "exceptions", result.getExceptions(),
            "explanation", result.getExplanation(),
            "executionTimeMs", result.getExecutionTimeMs(),
            "dryRun", true
        );
    }

    // ---- Private helpers ----

    private List<NxOrderAllocation> createDefaultAllocation(NxOrder order, UUID tenantId, List<Warehouse> warehouses) {
        if (warehouses.isEmpty()) {
            NxOrderAllocation failed = NxOrderAllocation.builder()
                    .orderId(order.getId())
                    .tenantId(tenantId)
                    .status("FAILED")
                    .allocationStrategy("RULE_BASED")
                    .nodeName("No active warehouses")
                    .build();
            return List.of(failed);
        }

        Warehouse closest = warehouses.get(0);
        NxOrderAllocation alloc = NxOrderAllocation.builder()
                .orderId(order.getId())
                .tenantId(tenantId)
                .nodeId(closest.getId())
                .nodeName(closest.getName())
                .nodeType(closest.getType() != null ? closest.getType() : "WAREHOUSE")
                .priority(1)
                .quantityAllocated(estimateOrderQuantity(order))
                .quantityRequested(estimateOrderQuantity(order))
                .status("ALLOCATED")
                .allocationStrategy("RULE_BASED")
                .costEstimated(estimateShippingCost(closest, order, 1))
                .deliveryPromiseConfidence(new BigDecimal("0.6500"))
                .build();
        return List.of(alloc);
    }

    private BigDecimal scoreNodeForOrder(Warehouse wh, NxOrder order, String shipRegion) {
        BigDecimal score = BigDecimal.valueOf(50);

        String whRegion = wh.getState() != null ? wh.getState() : "";
        if (shipRegion != null && whRegion.equalsIgnoreCase(shipRegion)) {
            score = score.add(BigDecimal.valueOf(30));
        } else if (shipRegion != null) {
            String whCountry = wh.getCountry() != null ? wh.getCountry() : "";
            if ("US".equalsIgnoreCase(shipRegion) && "US".equalsIgnoreCase(whCountry)) {
                score = score.add(BigDecimal.valueOf(15));
            }
        }

        if (wh.getTotalCapacitySqm() != null && wh.getTotalCapacitySqm().compareTo(BigDecimal.ZERO) > 0
                && wh.getUsedCapacitySqm() != null) {
            BigDecimal utilization = wh.getUsedCapacitySqm()
                    .divide(wh.getTotalCapacitySqm(), 4, RoundingMode.HALF_UP);
            if (utilization.compareTo(new BigDecimal("0.85")) < 0) {
                score = score.add(BigDecimal.valueOf(10));
            } else if (utilization.compareTo(new BigDecimal("0.95")) > 0) {
                score = score.subtract(BigDecimal.valueOf(20));
            }
        }

        if ("ACTIVE".equals(wh.getStatus())) {
            score = score.add(BigDecimal.valueOf(10));
        }

        return score;
    }

    private BigDecimal estimateShippingCost(Warehouse wh, NxOrder order, int quantity) {
        BigDecimal base = new BigDecimal("5.00");
        if (order.getTotal() != null) {
            base = base.add(order.getTotal().multiply(new BigDecimal("0.05")));
        }
        base = base.multiply(BigDecimal.valueOf(Math.max(1, quantity)));
        return base.setScale(2, RoundingMode.HALF_UP);
    }

    private BigDecimal estimateDistance(Warehouse wh, String shipRegion) {
        if (shipRegion != null && wh.getState() != null && wh.getState().equalsIgnoreCase(shipRegion)) {
            return new BigDecimal("50.00");
        }
        return new BigDecimal("500.00");
    }

    private BigDecimal calculateNodeConfidence(Warehouse wh, NxOrder order) {
        BigDecimal confidence = new BigDecimal("0.7500");
        if ("ACTIVE".equals(wh.getStatus())) {
            confidence = confidence.add(new BigDecimal("0.1000"));
        }
        if (wh.getUsedCapacitySqm() != null && wh.getTotalCapacitySqm() != null
                && wh.getTotalCapacitySqm().compareTo(BigDecimal.ZERO) > 0) {
            BigDecimal utilization = wh.getUsedCapacitySqm()
                    .divide(wh.getTotalCapacitySqm(), 4, RoundingMode.HALF_UP);
            if (utilization.compareTo(new BigDecimal("0.90")) > 0) {
                confidence = confidence.subtract(new BigDecimal("0.1500"));
            }
        }
        return confidence.min(BigDecimal.ONE).max(BigDecimal.ZERO).setScale(4, RoundingMode.HALF_UP);
    }

    private LocalDateTime calculateDeliveryPromise(NxOrder order, List<NxOrderAllocation> allocations) {
        LocalDateTime now = LocalDateTime.now();

        if (allocations.isEmpty() || allocations.stream().anyMatch(a -> "FAILED".equals(a.getStatus()))) {
            return null;
        }

        int processingDays = 1;
        int transitDays = 3;

        String shipRegion = conditionEvaluator.extractRegion(order.getShipToAddress());
        for (NxOrderAllocation alloc : allocations) {
            String nodeRegion = extractNodeRegion(alloc.getNodeName());
            if (shipRegion != null && shipRegion.equalsIgnoreCase(nodeRegion)) {
                transitDays = 1;
                break;
            }
        }

        return now.plusDays(processingDays + transitDays);
    }

    private List<NxFulfillmentException> detectFulfillmentExceptions(
            NxOrder order, List<NxOrderAllocation> allocations, UUID tenantId) {
        List<NxFulfillmentException> exceptions = new ArrayList<>();

        for (NxOrderAllocation alloc : allocations) {
            if (alloc.getNodeId() == null) {
                exceptions.add(buildException(order.getId(), alloc.getId(), tenantId,
                        "SYSTEM_ERROR", "HIGH", "No fulfillment node assigned",
                        "Unable to assign a fulfillment node for this allocation",
                        "REALLOCATE", true));
                continue;
            }

            Warehouse wh = warehouseRepository.findById(alloc.getNodeId()).orElse(null);
            if (wh == null) {
                exceptions.add(buildException(order.getId(), alloc.getId(), tenantId,
                        "SYSTEM_ERROR", "HIGH", "Fulfillment node not found",
                        "The allocated warehouse no longer exists in the system",
                        "REALLOCATE", true));
                continue;
            }

            if (wh.getUsedCapacitySqm() != null && wh.getTotalCapacitySqm() != null
                    && wh.getTotalCapacitySqm().compareTo(BigDecimal.ZERO) > 0) {
                BigDecimal utilization = wh.getUsedCapacitySqm()
                        .divide(wh.getTotalCapacitySqm(), 4, RoundingMode.HALF_UP);
                if (utilization.compareTo(new BigDecimal("0.95")) > 0) {
                    exceptions.add(buildException(order.getId(), alloc.getId(), tenantId,
                            "CAPACITY_EXCEEDED", "HIGH",
                            "Warehouse at capacity: " + wh.getName(),
                            "Utilization is " + utilization.multiply(BigDecimal.valueOf(100))
                                    .setScale(1, RoundingMode.HALF_UP) + "%",
                            "REALLOCATE", true));
                }
            }
        }

        return exceptions;
    }

    private List<NxOrderAllocation> selectBestAllocations(
            List<NxOrderAllocation> ruleAllocs, List<NxOrderAllocation> aiAllocs, BigDecimal threshold) {
        if (aiAllocs.isEmpty()) return ruleAllocs;
        if (ruleAllocs.isEmpty()) return aiAllocs;

        BigDecimal ruleConfidence = ruleAllocs.stream()
                .map(a -> a.getDeliveryPromiseConfidence() != null ? a.getDeliveryPromiseConfidence() : BigDecimal.ZERO)
                .reduce(BigDecimal.ZERO, BigDecimal::add)
                .divide(BigDecimal.valueOf(Math.max(1, ruleAllocs.size())), 4, RoundingMode.HALF_UP);

        BigDecimal aiConfidence = aiAllocs.stream()
                .map(a -> a.getDeliveryPromiseConfidence() != null ? a.getDeliveryPromiseConfidence() : BigDecimal.ZERO)
                .reduce(BigDecimal.ZERO, BigDecimal::add)
                .divide(BigDecimal.valueOf(Math.max(1, aiAllocs.size())), 4, RoundingMode.HALF_UP);

        if (aiConfidence.compareTo(threshold) >= 0 && aiConfidence.compareTo(ruleConfidence) >= 0) {
            return aiAllocs;
        }
        return ruleAllocs;
    }

    private BigDecimal calculateHybridConfidence(
            List<NxOrderAllocation> ruleAllocs, List<NxOrderAllocation> aiAllocs) {
        BigDecimal ruleConf = ruleAllocs.stream()
                .map(a -> a.getDeliveryPromiseConfidence() != null ? a.getDeliveryPromiseConfidence() : BigDecimal.ZERO)
                .reduce(BigDecimal.ZERO, BigDecimal::add)
                .divide(BigDecimal.valueOf(Math.max(1, ruleAllocs.size())), 4, RoundingMode.HALF_UP);

        BigDecimal aiConf = aiAllocs.stream()
                .map(a -> a.getDeliveryPromiseConfidence() != null ? a.getDeliveryPromiseConfidence() : BigDecimal.ZERO)
                .reduce(BigDecimal.ZERO, BigDecimal::add)
                .divide(BigDecimal.valueOf(Math.max(1, aiAllocs.size())), 4, RoundingMode.HALF_UP);

        return ruleConf.multiply(new BigDecimal("0.4"))
                .add(aiConf.multiply(new BigDecimal("0.6")))
                .setScale(4, RoundingMode.HALF_UP);
    }

    private boolean configHasExceptionDetection(UUID tenantId) {
        return routingConfigRepository.findByTenantId(tenantId)
                .map(NxRoutingConfig::getEnableExceptionDetection)
                .orElse(true);
    }

    private NxOrderAllocation buildAllocation(NxOrder order, UUID tenantId, Warehouse wh,
                                               NxRoutingRule rule, String strategy) {
        int qty = estimateOrderQuantity(order);
        return NxOrderAllocation.builder()
                .orderId(order.getId())
                .tenantId(tenantId)
                .nodeId(wh.getId())
                .nodeName(wh.getName())
                .nodeType(wh.getType() != null ? wh.getType() : "WAREHOUSE")
                .priority(1)
                .quantityAllocated(qty)
                .quantityRequested(qty)
                .status("ALLOCATED")
                .allocationStrategy(strategy)
                .ruleId(rule.getId())
                .ruleName(rule.getName())
                .costEstimated(estimateShippingCost(wh, order, qty))
                    .distanceKm(estimateDistance(wh, conditionEvaluator.extractRegion(order.getShipToAddress())))
                .deliveryPromiseConfidence(calculateNodeConfidence(wh, order))
                .build();
    }

    private NxFulfillmentException buildException(UUID orderId, UUID allocationId, UUID tenantId,
                                                    String type, String severity, String title,
                                                    String description, String strategy, boolean autoResolvable) {
        return NxFulfillmentException.builder()
                .orderId(orderId)
                .allocationId(allocationId)
                .tenantId(tenantId)
                .type(type)
                .severity(severity)
                .status("OPEN")
                .title(title)
                .description(description)
                .resolutionStrategy(strategy)
                .autoResolvable(autoResolvable)
                .detectedAt(LocalDateTime.now())
                .build();
    }

    private void saveRoutingLog(UUID orderId, UUID tenantId, String strategy,
                                 List<NxOrderAllocation> allocations, List<NxFulfillmentException> exceptions,
                                 LocalDateTime deliveryPromise, BigDecimal totalCost,
                                 BigDecimal confidence, int execTime, String status, String error) {
        try {
            routingLogRepository.save(NxRoutingLog.builder()
                    .orderId(orderId)
                    .tenantId(tenantId)
                    .strategy(strategy)
                    .selectedNodeId(allocations.isEmpty() ? null : allocations.get(0).getNodeId())
                    .confidenceScore(confidence)
                    .deliveryPromiseEstimate(deliveryPromise)
                    .costEstimate(totalCost)
                    .executionTimeMs(execTime)
                    .status(status)
                    .errorMessage(error)
                    .build());
        } catch (Exception e) {
            log.warn("Failed to save routing log: {}", e.getMessage());
        }
    }

    private int estimateOrderQuantity(NxOrder order) {
        String metadata = order.getMetadata();
        if (metadata != null) {
            try {
                JsonNode node = MAPPER.readTree(metadata);
                if (node.has("estimatedQuantity")) {
                    return node.get("estimatedQuantity").asInt(1);
                }
            } catch (Exception ignored) {}
        }
        return 1;
    }

    private String extractNodeRegion(String nodeName) {
        if (nodeName == null) return null;
        String[] parts = nodeName.split(",");
        return parts.length > 1 ? parts[parts.length - 1].trim() : null;
    }

    private UUID getCurrentUserId() {
        try {
            return TenantContext.getCurrentTenantId();
        } catch (Exception e) {
            return null;
        }
    }

}
