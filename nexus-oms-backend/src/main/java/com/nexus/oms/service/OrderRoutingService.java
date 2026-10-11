package com.nexus.oms.service;

import com.nexus.oms.domain.OrderStatusPolicy;
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
import com.nexus.oms.repository.OrderItemRepository;
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
import java.time.Duration;
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
    private final OrderItemRepository orderItemRepository;
    private final InventoryService inventoryService;

    public OrderRoutingService(OrderAllocationRepository allocationRepository,
                                FulfillmentExceptionRepository exceptionRepository,
                                NxRoutingRuleRepository routingRuleRepository,
                                RoutingConfigRepository routingConfigRepository,
                                RoutingLogRepository routingLogRepository,
                                WarehouseRepository warehouseRepository,
                                OrderRepository orderRepository,
                                OrderItemRepository orderItemRepository,
                                InventoryService inventoryService) {
        this.allocationRepository = allocationRepository;
        this.exceptionRepository = exceptionRepository;
        this.routingRuleRepository = routingRuleRepository;
        this.routingConfigRepository = routingConfigRepository;
        this.routingLogRepository = routingLogRepository;
        this.warehouseRepository = warehouseRepository;
        this.orderRepository = orderRepository;
        this.orderItemRepository = orderItemRepository;
        this.inventoryService = inventoryService;
    }

    @Transactional
    public AllocationResult allocateOrder(AllocationRequest request) {
        long startTime = System.currentTimeMillis();
        UUID tenantId = TenantContext.getCurrentTenantId();

        NxOrder order = orderRepository.findById(request.getOrderId())
                .orElseThrow(() -> new ResourceNotFoundException("Order not found: " + request.getOrderId()));

        if (!"PENDING".equals(order.getStatus()) && !"CONFIRMED".equals(order.getStatus())) {
            throw new BadRequestException("Order must be in PENDING or CONFIRMED status to allocate");
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
        LocalDateTime deliveryPromise;
        BigDecimal totalCost;

        switch (strategy.toUpperCase()) {
            case "RULE_BASED" -> {
                allocations = ruleBasedAllocation(order, tenantId);
                explanation = "Allocated using rule-based routing engine";
            }
            case "AI_OPTIMIZED" -> {
                allocations = aiOptimizedAllocation(order, tenantId);
                explanation = "Allocated using AI optimization model";
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
            }
            default ->
                throw new BadRequestException("Invalid strategy: " + strategy + ". Must be RULE_BASED, AI_OPTIMIZED, or HYBRID");
        }

        deliveryPromise = calculateDeliveryPromise(order, allocations);
        totalCost = allocations.stream()
                .map(a -> a.getCostEstimated() != null ? a.getCostEstimated() : BigDecimal.ZERO)
                .reduce(BigDecimal.ZERO, BigDecimal::add);

        // T-17 invariant: committed units must never exceed ordered units, and any shortfall must
        // be visible as an explicit backorder rather than being papered over with an over-quantity
        // or wrongly-located allocation. The allocator is written to satisfy this, so a violation
        // here is a programming error and must abort the transaction rather than corrupt stock.
        int demandTotal = totalDemand(collectOrderDemand(order));
        int allocatedTotal = sumAllocated(allocations);
        NxOrderAllocation backorder = findBackorder(allocations);

        if (allocatedTotal > demandTotal) {
            throw new BadRequestException("Allocation invariant violated: allocated " + allocatedTotal
                    + " exceeds ordered " + demandTotal);
        }
        if (allocatedTotal < demandTotal && backorder == null) {
            throw new BadRequestException("Allocation invariant violated: allocated " + allocatedTotal
                    + " of " + demandTotal + " with no backorder raised");
        }

        // T-17: the strategy block above used to set a fixed prior (0.75 for RULE_BASED, 0.85 for
        // AI_OPTIMIZED, and a 40/60 blend of the two for HYBRID) that never looked at the plan it
        // was describing. That made confidence a restatement of "which strategy ran" rather than a
        // measure of this order's delivery risk, and it was worst precisely where it mattered: a
        // PARTIALLY_ALLOCATED order scored the same 0.85 as a fully-stocked one, so the number the
        // AI briefing and the Orders UI show as promise confidence overstated short orders to the
        // people deciding what to do about them. Now that demand, coverage and the backorder are
        // all known, the prior is replaced by a score derived from them.
        BigDecimal confidenceScore =
                derivePlanConfidence(order, allocations, demandTotal, allocatedTotal, backorder);

        if (!dryRun) {
            // Winning node = first allocation that actually holds quantity. The routing plan
            // can contain 0-qty rows (e.g. small orders split across nodes), so allocated_node
            // must point at a node that can truly fulfill the order.
            UUID primaryNode = allocations.stream()
                    .filter(a -> a.getQuantityAllocated() != null && a.getQuantityAllocated() > 0)
                    .map(NxOrderAllocation::getNodeId)
                    .findFirst()
                    .orElse(allocations.isEmpty() ? null : allocations.get(0).getNodeId());

            // Consume stock so brokered orders can't oversell. Runs inside the caller's
            // transaction: a failed reservation throws BadRequestException and rolls back the
            // entire allocation.
            //
            // T-17: reserve PER ALLOCATION NODE against the per-SKU quantities that node was
            // actually given. The old code reserved every order item in full against
            // primaryNode, which contradicted any split plan — node B could be recorded as
            // holding half the order while all of it was consumed from node A. If any node's
            // reservation fails, everything already reserved in this attempt is released so no
            // stock leaks before the order is retried.
            List<Reservation> reserved = new ArrayList<>();
            try {
                for (NxOrderAllocation allocation : allocations) {
                    if (!"ALLOCATED".equals(allocation.getStatus()) || allocation.getNodeId() == null) {
                        continue;
                    }
                    skuQuantitiesOf(allocation).forEach((sku, qty) -> {
                        if (qty <= 0) return;
                        inventoryService.reserveInventory(tenantId, sku, allocation.getNodeId(), qty, "ORDER", order.getId().toString());
                        reserved.add(new Reservation(allocation.getNodeId(), sku, qty));
                    });
                }
            } catch (Exception e) {
                for (Reservation r : reserved) {
                    inventoryService.releaseInventory(tenantId, r.sku(), r.nodeId(), r.qty(), "ORDER", order.getId().toString());
                }
                throw e;
            }

            allocations.forEach(a -> {
                a.setAllocatedAt(LocalDateTime.now());
                a.setAllocatedBy(getCurrentUserId());
                allocationRepository.save(a);
            });

            order.setAllocatedNode(primaryNode);
            order.setAllocationRule(strategy);
            order.setAllocationConfidence(confidenceScore);
            order.setPromisedDelivery(deliveryPromise);
            // Honest terminal state: a short order is PARTIALLY_ALLOCATED, and an order nothing
            // could cover is FAILED. Marking either ALLOCATED overstates what was actually sourced.
            OrderStatusPolicy.transitionIfAllowed(order, resolveOrderStatus(demandTotal, allocatedTotal, backorder != null), "OrderRoutingService");
            orderRepository.save(order);

            // Persist item-level allocation so nx_order_items reflects the allocation
            // (allocated_node_id / allocated_qty were previously left NULL). Only safe when a
            // SINGLE node covers the whole order; for a genuine split the per-SKU truth lives in
            // the allocation metadata, and claiming one node holds every item would be a lie.
            if (primaryNode != null && backorder == null && isSingleNodeAllocation(allocations)) {
                List<NxOrderItem> items = orderItemRepository.findByOrderId(order.getId());
                for (NxOrderItem item : items) {
                    item.setAllocatedNodeId(primaryNode);
                    item.setAllocatedQty(item.getQuantity());
                }
                orderItemRepository.saveAll(items);
            }

            // A backorder is a correctness signal, not an optional nicety: always raise it, even
            // when exception detection is disabled in tenant config.
            if (backorder != null) {
                exceptions.add(buildException(order.getId(), backorder.getId(), tenantId,
                        "INVENTORY_SHORTAGE", "HIGH",
                        "Order partially allocated - backorder raised",
                        "Allocated " + allocatedTotal + " of " + demandTotal
                                + " ordered units; " + (demandTotal - allocatedTotal)
                                + " units could not be sourced.",
                        "BACKORDER", true));
            }

            if (configHasExceptionDetection(tenantId)) {
                exceptions.addAll(detectFulfillmentExceptions(order, allocations, tenantId));
            }

            // Saved outside the config gate on purpose: a backorder is a correctness signal, and
            // the operator has to see it even when automatic detection is switched off. Gating the
            // save on that flag meant the shortage was computed, appended to `exceptions`, reported
            // in the response, and then silently thrown away on the floor.
            if (!exceptions.isEmpty()) {
                exceptionRepository.saveAll(exceptions);
            }
        }

        long execTime = System.currentTimeMillis() - startTime;

        saveRoutingLog(order.getId(), tenantId, strategy, allocations, exceptions,
                deliveryPromise, totalCost, confidenceScore, (int) execTime, "SUCCESS", null);

        return AllocationResult.builder()
                .orderId(order.getId())
                .strategy(strategy)
                .status(dryRun ? "SIMULATED"
                        : resolveOrderStatus(demandTotal, allocatedTotal, backorder != null))
                .allocations(allocations)
                .estimatedDeliveryDate(deliveryPromise)
                .confidenceScore(confidenceScore)
                .totalCost(totalCost)
                .exceptions(exceptions)
                .explanation(explanation)
                .executionTimeMs(execTime)
                .build();
    }

    /** A stock reservation already taken during this attempt, for compensating release on failure. */
    private record Reservation(UUID nodeId, String sku, int qty) {}

    private static int sumAllocated(List<NxOrderAllocation> allocations) {
        return allocations.stream()
                .filter(a -> "ALLOCATED".equals(a.getStatus()))
                .mapToInt(a -> a.getQuantityAllocated() == null ? 0 : a.getQuantityAllocated())
                .sum();
    }

    /**
     * Maps an allocation outcome onto the order lifecycle state that actually describes it.
     *
     * <p>T-17: this used to be a hardcoded {@code "ALLOCATED"} regardless of outcome. The only way
     * to reach PARTIALLY_ALLOCATED/FAILED is a backorder row, which {@code allocateStockAware}
     * always raises when demand is short, so a shortfall can never be reported as a success.
     */
    private static String resolveOrderStatus(int demandTotal, int allocatedTotal, boolean hasBackorder) {
        if (demandTotal == 0 || allocatedTotal == demandTotal) return "ALLOCATED";
        if (!hasBackorder) return "PARTIALLY_ALLOCATED";
        return allocatedTotal == 0 ? "FAILED" : "PARTIALLY_ALLOCATED";
    }

    private static NxOrderAllocation findBackorder(List<NxOrderAllocation> allocations) {
        return allocations.stream()
                .filter(a -> "FAILED".equals(a.getStatus()) && "BACKORDER".equals(a.getNodeName()))
                .findFirst()
                .orElse(null);
    }

    private static boolean isSingleNodeAllocation(List<NxOrderAllocation> allocations) {
        return allocations.stream()
                .filter(a -> "ALLOCATED".equals(a.getStatus()) && a.getQuantityAllocated() != null
                        && a.getQuantityAllocated() > 0)
                .map(NxOrderAllocation::getNodeId)
                .distinct()
                .limit(2)
                .count() <= 1;
    }

    /**
     * Reads back the per-SKU split recorded on an allocation by {@code buildAllocation}. This is
     * what reservation and downstream fulfillment act on, so a malformed/missing breakdown must
     * fail loudly rather than silently reserving the wrong thing.
     */
    @SuppressWarnings("unchecked")
    static Map<String, Integer> skuQuantitiesOf(NxOrderAllocation allocation) {
        String metadata = allocation.getMetadata();
        if (metadata == null || metadata.isBlank()) {
            throw new BadRequestException("Allocation " + allocation.getNodeName()
                    + " has no SKU breakdown and cannot be reserved");
        }
        try {
            JsonNode node = MAPPER.readTree(metadata).get("skuQuantities");
            if (node == null || !node.isObject()) {
                throw new BadRequestException("Allocation " + allocation.getNodeName()
                        + " is missing its SKU breakdown");
            }
            Map<String, Integer> quantities = new LinkedHashMap<>();
            node.fields().forEachRemaining(f -> quantities.put(f.getKey(), f.getValue().asInt()));
            return quantities;
        } catch (BadRequestException e) {
            throw e;
        } catch (Exception e) {
            throw new BadRequestException("Allocation " + allocation.getNodeName()
                    + " has an unreadable SKU breakdown: " + e.getMessage());
        }
    }

    List<NxOrderAllocation> ruleBasedAllocation(NxOrder order, UUID tenantId) {
        List<NxRoutingRule> rules = routingRuleRepository.findByTenantIdAndIsActiveTrue(tenantId);
        List<Warehouse> warehouses = warehouseRepository.findOperational(tenantId);

        if (rules.isEmpty() || warehouses.isEmpty()) {
            return createDefaultAllocation(order, tenantId, warehouses);
        }

        // T-17 Bug A: the old loop called buildAllocation() once per node in the rule, and
        // buildAllocation() assigned the FULL order quantity to each — so a rule listing three
        // nodes over-committed the order 3x and blew up at reservation time. Rules are now only
        // used to produce an ORDERED candidate list; the shared allocator decides how much each
        // node actually gets, bounded by real stock.
        for (NxRoutingRule rule : rules) {
            if (!conditionEvaluator.evaluate(rule, order)) continue;

            JsonNode actions = conditionEvaluator.parseJson(rule.getActions());
            if (actions == null || !actions.has("nodeIds")) continue;

            List<Candidate> candidates = new ArrayList<>();
            actions.get("nodeIds").forEach(n -> {
                UUID nodeId = UUID.fromString(n.asText());
                warehouseRepository.findById(nodeId)
                        // Rule actions are tenant data; never let a rule reach another tenant's node.
                        .filter(wh -> tenantId.equals(wh.getTenantId()))
                        .ifPresent(wh -> candidates.add(new Candidate(wh, rule)));
            });

            if (!candidates.isEmpty()) {
                return allocateStockAware(order, tenantId, candidates, "RULE_BASED").allocations();
            }
        }

        return createDefaultAllocation(order, tenantId, warehouses);
    }

    List<NxOrderAllocation> aiOptimizedAllocation(NxOrder order, UUID tenantId) {
        List<Warehouse> warehouses = warehouseRepository.findOperational(tenantId);

        if (warehouses.isEmpty()) {
            return List.of(backorderAllocation(order, tenantId, "AI_OPTIMIZED",
                    collectOrderDemand(order), "No active warehouses available for routing"));
        }

        String shipRegion = conditionEvaluator.extractRegion(order.getShipToAddress());
        Map<String, Integer> demanded = collectOrderDemand(order);

        // Scoring still decides node PREFERENCE (and therefore priority), but the score no longer
        // decides QUANTITIES.
        List<Warehouse> scored = warehouses.stream()
                .map(wh -> {
                    boolean fulfillsAll = canFulfill(order, wh, demanded);
                    BigDecimal base = scoreNodeForOrder(wh, order, shipRegion);
                    if (fulfillsAll) {
                        base = base.add(BigDecimal.valueOf(40));
                    } else if (canFulfillAny(order, wh, demanded)) {
                        base = base.add(BigDecimal.valueOf(10));
                    } else {
                        base = base.subtract(BigDecimal.valueOf(50));
                    }
                    return new AbstractMap.SimpleEntry<>(wh, base);
                })
                .sorted((a, b) -> b.getValue().compareTo(a.getValue()))
                .limit(3)
                .map(Map.Entry::getKey)
                .toList();

        // T-17 Bug B: the old code split quantity as `remaining / remainingNodes` with a
        // Math.max(1, ...) floor, so a node that FAILED its stock check still received an
        // allocation and the split could exceed what any node actually holds. The score now only
        // orders the candidates; the shared allocator caps every node at its real availability.
        List<Candidate> candidates = scored.stream()
                .map(wh -> new Candidate(wh, null))
                .toList();

        return allocateStockAware(order, tenantId, candidates, "AI_OPTIMIZED").allocations();
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

    public List<NxOrderAllocation> getAllocations() {
        UUID tenantId = TenantContext.getCurrentTenantId();
        return allocationRepository.findByTenantId(tenantId, Sort.by(Sort.Direction.DESC, "createdAt"));
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

        if (request.getResolutionStrategy() != null
                && !NxFulfillmentException.RESOLUTION_STRATEGIES.contains(request.getResolutionStrategy())) {
            throw new BadRequestException("Unknown resolution strategy '" + request.getResolutionStrategy()
                    + "'; use one of " + new java.util.TreeSet<>(NxFulfillmentException.RESOLUTION_STRATEGIES));
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

    // ---- Shared stock-aware allocation ----

    /** A node eligible to receive demand, plus the rule that nominated it (if any). */
    private record Candidate(Warehouse warehouse, NxRoutingRule rule) {}

    /**
     * Outcome of a stock-aware allocation pass, plus the bookkeeping the persistence boundary needs
     * to enforce the T-17 invariant.
     */
    static final class AllocationPlan {
        private final List<NxOrderAllocation> allocations = new ArrayList<>();
        private final Map<String, Integer> unallocated = new LinkedHashMap<>();

        List<NxOrderAllocation> allocations() { return allocations; }

        Map<String, Integer> unallocated() { return unallocated; }

        /** Units actually committed to nodes (excludes the backorder row). */
        int allocatedTotal() {
            return allocations.stream()
                    .filter(a -> "ALLOCATED".equals(a.getStatus()))
                    .mapToInt(a -> a.getQuantityAllocated() == null ? 0 : a.getQuantityAllocated())
                    .sum();
        }

        /** Units that could not be sourced anywhere and are raised as a backorder. */
        int unallocatedTotal() { return unallocated.values().stream().mapToInt(Integer::intValue).sum(); }

        boolean hasBackorder() { return !unallocated.isEmpty(); }
    }

    /**
     * Single stock-aware allocation path shared by RULE_BASED, AI_OPTIMIZED and the default path.
     *
     * <p>Greedy in candidate order: each node takes as much of the still-outstanding demand as it can
     * actually source, then the next node gets what's left. Nothing is allocated without a positive
     * stock check, and demand that no candidate can cover becomes an explicit backorder row instead
     * of being silently assigned to an arbitrary node.
     *
     * <p>Shared pools need care: a node with no inventory row of its own resolves to the same
     * tenant+SKU shared row as every other such node. Without tracking what earlier candidates already
     * drew from that pool, two nodes would each see the full shared quantity and plan to commit it
     * twice — so draws against the shared row are netted out as candidates are walked.
     */
    private AllocationPlan allocateStockAware(NxOrder order, UUID tenantId,
                                              List<Candidate> candidates, String strategy) {
        AllocationPlan plan = new AllocationPlan();

        Map<String, Integer> remaining = new LinkedHashMap<>(collectOrderDemand(order));
        remaining.values().removeIf(q -> q == null || q <= 0);
        if (remaining.isEmpty()) {
            return plan;
        }

        Map<String, Integer> sharedDrawn = new HashMap<>();
        int priority = 1;

        for (Candidate candidate : candidates) {
            if (remaining.isEmpty()) break;

            Warehouse wh = candidate.warehouse();
            Map<String, Integer> take = new LinkedHashMap<>();
            Map<String, Boolean> drawnFromShared = new HashMap<>();
            int nodeTotal = 0;

            for (Map.Entry<String, Integer> entry : remaining.entrySet()) {
                int outstanding = entry.getValue();
                if (outstanding <= 0) continue;

                InventoryService.StockPosition position =
                        inventoryService.resolveStock(tenantId, entry.getKey(), wh.getId());
                drawnFromShared.put(entry.getKey(), position.fromSharedPool());

                int effective = position.available();
                if (position.fromSharedPool()) {
                    // Net out what earlier candidates already drew from this same shared row.
                    effective = Math.max(0, effective - sharedDrawn.getOrDefault(entry.getKey(), 0));
                }

                int grant = Math.min(outstanding, effective);
                if (grant <= 0) continue;

                take.put(entry.getKey(), grant);
                nodeTotal += grant;
            }

            if (nodeTotal <= 0) {
                // This node can source nothing — emit no row at all rather than a 0-qty or
                // over-quantity allocation. T-17: never mark ALLOCATED without a stock check.
                continue;
            }

            take.forEach((sku, qty) -> {
                remaining.merge(sku, -qty, Integer::sum);
                if (Boolean.TRUE.equals(drawnFromShared.get(sku))) {
                    sharedDrawn.merge(sku, qty, Integer::sum);
                }
            });

            plan.allocations.add(buildAllocation(order, tenantId, wh, candidate.rule(), strategy,
                    nodeTotal, take, priority++, "ALLOCATED"));
        }

        remaining.values().removeIf(q -> q <= 0);
        if (!remaining.isEmpty()) {
            plan.unallocated.putAll(remaining);
            plan.allocations.add(backorderAllocation(order, tenantId, strategy, remaining,
                    "No node can supply " + describeDemand(remaining)));
        }

        return plan;
    }

    private List<NxOrderAllocation> createDefaultAllocation(NxOrder order, UUID tenantId, List<Warehouse> warehouses) {
        if (warehouses.isEmpty()) {
            return List.of(backorderAllocation(order, tenantId, "RULE_BASED",
                    collectOrderDemand(order), "No active warehouses available for routing"));
        }

        // T-17 Bug C: the old code did `.orElse(warehouses.get(0))`, so an order no warehouse could
        // fulfil was still committed to warehouse #1 as a full ALLOCATED row and only failed later,
        // at reservation time. Every active node is now a candidate and the allocator falls back to
        // a backorder when none of them can cover demand.
        List<Candidate> candidates = warehouses.stream()
                .map(wh -> new Candidate(wh, null))
                .toList();

        return allocateStockAware(order, tenantId, candidates, "RULE_BASED").allocations();
    }

    /**
     * Explicit unmet-demand row. Carries the outstanding quantity as {@code quantityRequested} with
     * {@code quantityAllocated = 0} so the accounting stays honest: nothing is claimed to be
     * allocated that was not.
     *
     * <p>T-17: {@code unmet} must be the quantity still missing AFTER the successful candidates
     * have been allocated. Recomputing the whole order demand here would make a 60%-covered order
     * report 100 units backordered on top of the 60 already allocated, and the exception text would
     * claim a shortfall that is larger than the real one.
     */
    private NxOrderAllocation backorderAllocation(NxOrder order, UUID tenantId, String strategy,
                                                 Map<String, Integer> unmet, String reason) {
        return NxOrderAllocation.builder()
                .orderId(order.getId())
                .tenantId(tenantId)
                .nodeId(null)
                .nodeName("BACKORDER")
                .priority(0)
                .quantityAllocated(0)
                .quantityRequested(totalDemand(unmet))
                .status("FAILED")
                .allocationStrategy(strategy)
                .metadata(demandJson(Map.of("backorderReason", reason, "unmetDemand", unmet)))
                .build();
    }

    private static int totalDemand(Map<String, Integer> demand) {
        return demand.values().stream().filter(Objects::nonNull).mapToInt(Integer::intValue).sum();
    }

    private static String describeDemand(Map<String, Integer> demand) {
        return demand.entrySet().stream()
                .map(e -> e.getKey() + " x" + e.getValue())
                .collect(Collectors.joining(", "));
    }

    private static String demandJson(Map<String, Object> payload) {
        try {
            return MAPPER.writeValueAsString(payload);
        } catch (Exception e) {
            return null;
        }
    }

    private Map<String, Integer> collectOrderDemand(NxOrder order) {
        Map<String, Integer> demand = new HashMap<>();
        List<NxOrderItem> items = orderItemRepository.findByOrderId(order.getId());
        for (NxOrderItem item : items) {
            demand.merge(item.getSku(), item.getQuantity(), Integer::sum);
        }
        if (demand.isEmpty()) {
            int estimated = estimateOrderQuantity(order);
            String metadata = order.getMetadata();
            if (metadata != null) {
                try {
                    JsonNode node = MAPPER.readTree(metadata);
                    if (node.has("sku")) {
                        demand.put(node.get("sku").asText(), estimated);
                    }
                } catch (Exception ignored) {}
            }
        }
        return demand;
    }

    private boolean canFulfill(NxOrder order, Warehouse wh, Map<String, Integer> demanded) {
        if (demanded.isEmpty()) return true;
        return demanded.entrySet().stream()
                .allMatch(e -> inventoryService.checkAvailability(order.getTenantId(), e.getKey(), wh.getId(), e.getValue()));
    }

    private boolean canFulfillAny(NxOrder order, Warehouse wh, Map<String, Integer> demanded) {
        if (demanded.isEmpty()) return true;
        return demanded.entrySet().stream()
                .anyMatch(e -> inventoryService.checkAvailability(order.getTenantId(), e.getKey(), wh.getId(), e.getValue()));
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

    /**
     * Delivery-promise confidence derived from what the allocator actually found.
     *
     * <p>Replaces the strategy-level constants (0.75 / 0.85). The starting point is a neutral
     * prior and every factor below is real evidence from this plan, so the score moves with the
     * order instead of with the strategy name:
     *
     * <ul>
     *   <li><b>Coverage</b> — allocated units over ordered units. This is the dominant factor and
     *       the one the constants got most wrong: a plan that sourced 4 of 10 units is not an 85%
     *       promise. A backorder row hard-caps the score, because an order we could not fully
     *       source is not a promise we can make.</li>
     *   <li><b>Node health</b> — the share of allocated quantity coming from nodes that are not
     *       ACTIVE, and from nodes running hot on capacity. Weight is counted per unit rather
     *       than per row so a 1-unit row on a saturated node cannot dilute a 50-unit row on a
     *       healthy one.</li>
     *   <li><b>Promise slack</b> — a promised date already in the past, or imminent, is not a
     *       confident promise. Computed from the same ALLOCATED rows that set the promise date,
     *       so the two can never disagree.</li>
     * </ul>
     *
     * <p>Clamped to [0, 1] and rounded to 4dp to match the column. This is a risk indicator for
     * triage, not a probability: it is derived from allocation state only and does not model
     * carrier performance or historical on-time rates. It is stored on the order as
     * {@code allocation_confidence} and per node in {@code delivery_promise_confidence}.
     */
    private BigDecimal derivePlanConfidence(NxOrder order, List<NxOrderAllocation> allocations,
                                            int demandTotal, int allocatedTotal,
                                            NxOrderAllocation backorder) {
        if (demandTotal <= 0) {
            return BigDecimal.ZERO;
        }

        // Neutral prior: assume nothing about the order, then let evidence move it.
        BigDecimal confidence = new BigDecimal("0.9000");

        // --- Coverage: allocated / ordered, scaled to a 0.40 penalty at zero coverage. ---
        BigDecimal coverage = BigDecimal.valueOf(allocatedTotal)
                .divide(BigDecimal.valueOf(demandTotal), 4, RoundingMode.HALF_UP);
        BigDecimal coveragePenalty = BigDecimal.ONE.subtract(coverage)
                .multiply(new BigDecimal("0.40"));
        confidence = confidence.subtract(coveragePenalty);

        // A backorder means the order is not fully sourced. Cap rather than subtract: no amount
        // of healthy nodes makes a short order a confident full delivery.
        if (backorder != null) {
            confidence = confidence.min(new BigDecimal("0.4000"));
        }

        // --- Node health, weighted by the quantity each node is actually holding. ---
        BigDecimal unhealthyUnits = BigDecimal.ZERO;
        BigDecimal saturatedUnits = BigDecimal.ZERO;
        BigDecimal totalUnits = BigDecimal.ZERO;

        for (NxOrderAllocation a : allocations) {
            if (!"ALLOCATED".equals(a.getStatus()) || a.getQuantityAllocated() == null
                    || a.getQuantityAllocated() <= 0) {
                continue;
            }
            BigDecimal units = BigDecimal.valueOf(a.getQuantityAllocated());
            totalUnits = totalUnits.add(units);

            Warehouse wh = a.getNodeId() == null ? null
                    : warehouseRepository.findById(a.getNodeId()).orElse(null);
            if (wh == null) {
                // Node vanished between planning and now — treat as unverified, not as healthy.
                unhealthyUnits = unhealthyUnits.add(units);
                continue;
            }
            if (!"ACTIVE".equals(wh.getStatus())) {
                unhealthyUnits = unhealthyUnits.add(units);
            }
            if (isSaturated(wh)) {
                saturatedUnits = saturatedUnits.add(units);
            }
        }

        if (totalUnits.compareTo(BigDecimal.ZERO) > 0) {
            BigDecimal unhealthyShare = unhealthyUnits.divide(totalUnits, 4, RoundingMode.HALF_UP);
            BigDecimal saturatedShare = saturatedUnits.divide(totalUnits, 4, RoundingMode.HALF_UP);
            // 0.30 for a non-ACTIVE node, 0.15 for a node at/over 90% capacity utilisation.
            confidence = confidence
                    .subtract(unhealthyShare.multiply(new BigDecimal("0.30")))
                    .subtract(saturatedShare.multiply(new BigDecimal("0.15")));
        }

        // --- Promise slack, from the same rows that produced the promise date. ---
        LocalDateTime promise = calculateDeliveryPromise(order, allocations);
        if (promise == null) {
            // No ALLOCATED row means there is nothing to promise from. Anything being shipped is
            // already covered by the backorder cap above; this guards the fully-uncovered case.
            confidence = confidence.min(new BigDecimal("0.2000"));
        } else {
            long hours = Duration.between(LocalDateTime.now(), promise).toHours();
            if (hours <= 0) {
                // Promised date is already in the past.
                confidence = confidence.subtract(new BigDecimal("0.4000"));
            } else if (hours < 24) {
                confidence = confidence.subtract(new BigDecimal("0.1500"));
            }
        }

        return confidence.max(BigDecimal.ZERO).min(BigDecimal.ONE).setScale(4, RoundingMode.HALF_UP);
    }

    /** Node at or above 90% of usable capacity: still functional, but with little margin. */
    private boolean isSaturated(Warehouse wh) {
        if (wh.getTotalCapacitySqm() == null || wh.getUsedCapacitySqm() == null) return false;
        if (wh.getTotalCapacitySqm().compareTo(BigDecimal.ZERO) <= 0) return false;
        BigDecimal utilization = wh.getUsedCapacitySqm()
                .divide(wh.getTotalCapacitySqm(), 4, RoundingMode.HALF_UP);
        return utilization.compareTo(new BigDecimal("0.90")) >= 0;
    }

    /**
     * Per-node promise confidence for the allocation row itself.
     *
     * <p>T-17: the previous version started from a hardcoded 0.75 and added fixed offsets, so every
     * node scored 0.85 when ACTIVE and no node could ever score high-but-not-maximal on the
     * strength of its own state. It now reports the node's actual condition on its own scale, and
     * {@code derivePlanConfidence} carries the order-level risk. These two are deliberately
     * different measures: this one answers "how sound is this node", the other answers "how risky
     * is this order's delivery".
     */
    private BigDecimal calculateNodeConfidence(Warehouse wh, NxOrder order) {
        // Start from a healthy-but-unremarkable node, then adjust on measured state only.
        BigDecimal confidence = new BigDecimal("0.7000");

        if (wh == null) return BigDecimal.ZERO;
        if (!"ACTIVE".equals(wh.getStatus())) {
            // A node that is not active cannot be a confident fulfiller regardless of capacity.
            return new BigDecimal("0.1000");
        }
        confidence = confidence.add(new BigDecimal("0.1500"));

        if (wh.getTotalCapacitySqm() != null && wh.getUsedCapacitySqm() != null
                && wh.getTotalCapacitySqm().compareTo(BigDecimal.ZERO) > 0) {
            BigDecimal utilization = wh.getUsedCapacitySqm()
                    .divide(wh.getTotalCapacitySqm(), 4, RoundingMode.HALF_UP);
            if (utilization.compareTo(new BigDecimal("0.90")) >= 0) {
                confidence = confidence.subtract(new BigDecimal("0.2500"));
            } else if (utilization.compareTo(new BigDecimal("0.85")) < 0) {
                // Comfortable headroom is genuine evidence in the node's favour.
                confidence = confidence.add(new BigDecimal("0.1000"));
            }
        } else {
            // No capacity data at all: unverified, so do not claim the headroom bonus.
            confidence = confidence.subtract(new BigDecimal("0.0500"));
        }

        return confidence.max(BigDecimal.ZERO).min(BigDecimal.ONE).setScale(4, RoundingMode.HALF_UP);
    }

    /**
     * Earliest date the order can be promised, derived from the nodes actually fulfilling it.
     *
     * <p>T-17: the promise is computed from real {@code ALLOCATED} rows only. The previous guard
     * returned {@code null} if <em>any</em> row was {@code FAILED}, and since T-17 deliberately
     * emits a {@code FAILED} backorder row for every shortfall, that made the promise
     * unconditionally {@code null} for every partially-allocated order — exactly the orders a
     * promise date matters most for. A promise is still withheld when nothing is being fulfilled
     * (no {@code ALLOCATED} rows), because there is then no node to promise from.
     */
    private LocalDateTime calculateDeliveryPromise(NxOrder order, List<NxOrderAllocation> allocations) {
        LocalDateTime now = LocalDateTime.now();

        List<NxOrderAllocation> fulfilling = allocations.stream()
                .filter(a -> "ALLOCATED".equals(a.getStatus()))
                .toList();

        if (fulfilling.isEmpty()) {
            return null;
        }

        int processingDays = 1;
        int transitDays = 3;

        String shipRegion = conditionEvaluator.extractRegion(order.getShipToAddress());
        for (NxOrderAllocation alloc : fulfilling) {
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

    /**
     * Chooses between the rule-based and AI candidate plans.
     *
     * <p>T-17: coverage is compared BEFORE confidence. Previously the winner was picked on
     * delivery-confidence alone, so a plan that could only source part of the order could be
     * preferred over one that could source all of it, purely on a routing score. A shorter plan
     * can never be the better outcome when a fuller one is on the table.
     */
    private List<NxOrderAllocation> selectBestAllocations(
            List<NxOrderAllocation> ruleAllocs, List<NxOrderAllocation> aiAllocs, BigDecimal threshold) {
        if (aiAllocs.isEmpty()) return ruleAllocs;
        if (ruleAllocs.isEmpty()) return aiAllocs;

        int ruleCovered = sumAllocated(ruleAllocs);
        int aiCovered = sumAllocated(aiAllocs);
        if (ruleCovered != aiCovered) {
            return ruleCovered > aiCovered ? ruleAllocs : aiAllocs;
        }

        BigDecimal ruleConfidence = averageConfidence(ruleAllocs);
        BigDecimal aiConfidence = averageConfidence(aiAllocs);

        if (aiConfidence.compareTo(threshold) >= 0 && aiConfidence.compareTo(ruleConfidence) >= 0) {
            return aiAllocs;
        }
        return ruleAllocs;
    }

    /**
     * Mean delivery confidence across a plan's real allocations, used only to choose
     * <em>between</em> candidate plans in {@link #selectBestAllocations}. The order-level score
     * reported to callers is {@link #derivePlanConfidence}, which measures coverage and risk
     * rather than averaging node scores.
     *
     * <p>Backorder rows are excluded: they record an absence of stock, not a routing decision, so
     * folding their null/zero confidence into the mean would penalise a plan purely for surfacing
     * a shortfall. This used to divide by {@code list.size()}, which counted the backorder row in
     * the denominator while it contributed nothing to the numerator, halving the score of any
     * partial plan.
     *
     * <p>Note that this is now correctness and clarity rather than a live behavioural fix. Because
     * {@link #selectBestAllocations} gates on coverage first, only plans with identical coverage
     * ever reach this comparison, and identical coverage implies an identical number of backorder
     * rows — so the old divisor deflated both candidates by the same factor and could not change
     * which plan won. The divisor was corrected anyway because it makes the method mean what its
     * name says, and because it stops being harmless the moment a second caller appears.
     */
    private static BigDecimal averageConfidence(List<NxOrderAllocation> allocations) {
        List<NxOrderAllocation> real = allocations.stream()
                .filter(a -> "ALLOCATED".equals(a.getStatus()))
                .toList();
        if (real.isEmpty()) return BigDecimal.ZERO;
        return real.stream()
                .map(a -> a.getDeliveryPromiseConfidence() != null ? a.getDeliveryPromiseConfidence() : BigDecimal.ZERO)
                .reduce(BigDecimal.ZERO, BigDecimal::add)
                .divide(BigDecimal.valueOf(real.size()), 4, RoundingMode.HALF_UP);
    }

    private boolean configHasExceptionDetection(UUID tenantId) {
        return routingConfigRepository.findByTenantId(tenantId)
                .map(NxRoutingConfig::getEnableExceptionDetection)
                .orElse(true);
    }

    /**
     * Builds one allocation row for a node.
     *
     * <p>T-17: {@code allocatedQty} and the per-SKU breakdown are supplied by the caller, which has
     * already verified them against real stock. This method previously called
     * {@code estimateOrderQuantity(order)} internally and unconditionally set status to ALLOCATED,
     * which is what let a node that could not fulfil anything still be recorded as fully allocated.
     */
    private NxOrderAllocation buildAllocation(NxOrder order, UUID tenantId, Warehouse wh,
                                              NxRoutingRule rule, String strategy,
                                              int allocatedQty, Map<String, Integer> skuQuantities,
                                              int priority, String status) {
        return NxOrderAllocation.builder()
                .orderId(order.getId())
                .tenantId(tenantId)
                .nodeId(wh.getId())
                .nodeName(wh.getName())
                .nodeType(wh.getType() != null ? wh.getType() : "WAREHOUSE")
                .priority(priority)
                .quantityAllocated(allocatedQty)
                .quantityRequested(allocatedQty)
                .status(status)
                .allocationStrategy(strategy)
                .ruleId(rule != null ? rule.getId() : null)
                .ruleName(rule != null ? rule.getName() : null)
                .costEstimated(estimateShippingCost(wh, order, allocatedQty))
                .distanceKm(estimateDistance(wh, conditionEvaluator.extractRegion(order.getShipToAddress())))
                .deliveryPromiseConfidence(calculateNodeConfidence(wh, order))
                // nx_order_allocations has scalar quantity columns, so the per-SKU split — which is
                // what actually makes a multi-node split auditable — is recorded in the JSONB
                // metadata column rather than being discarded.
                .metadata(demandJson(Map.of("skuQuantities", skuQuantities)))
                .build();
    }

    private NxFulfillmentException buildException(UUID orderId, UUID allocationId, UUID tenantId,
                                                    String type, String severity, String title,
                                                    String description, String strategy, boolean autoResolvable) {
        if (strategy != null && !NxFulfillmentException.RESOLUTION_STRATEGIES.contains(strategy)) {
            throw new IllegalArgumentException("Not a resolution strategy: " + strategy);
        }
        return NxFulfillmentException.builder()
                .orderId(orderId)
                .allocationId(allocationId)
                .tenantId(tenantId)
                .type(type)
                // `exception_type` (V67) is the column the dashboard reads first. It used to be left
                // NULL here, so routing exceptions were rendered as "UNKNOWN" on that view even
                // though the type was known. Both fields carry the same value for routing errors.
                .exceptionType(type)
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
