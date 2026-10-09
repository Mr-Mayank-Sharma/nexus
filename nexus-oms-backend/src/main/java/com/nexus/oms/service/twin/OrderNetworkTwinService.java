package com.nexus.oms.service.twin;

import com.nexus.oms.entity.*;
import com.nexus.oms.repository.*;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.Clock;
import java.time.LocalDateTime;
import java.util.*;
import java.util.function.Function;
import java.util.stream.Collectors;

/**
 * Read model for the Order Flow Twin: how orders moved through the tenant's network in a time
 * window — sales channel → fulfilment node → ship-to region — plus lifecycle, brokering queue
 * and exceptions. Built from orders, allocations, addresses, inventory and queue rows in a fixed
 * number of batched queries. Cost and distance are the allocator's own estimates and are labelled
 * as such; anything without data is returned as null.
 */
@Service
public class OrderNetworkTwinService {

    static final int MAX_WINDOW_HOURS = 24 * 30;
    static final Set<String> BACKORDER = Set.of("FAILED", "PARTIALLY_ALLOCATED", "BACKORDERED");
    static final Set<String> ROUTED = Set.of("ALLOCATED", "SHIPPED", "FULFILLED", "DELIVERED", "PARTIALLY_ALLOCATED");
    static final List<String> OPEN_QUEUE = List.of("PENDING", "QUEUED", "PROCESSING", "RETRY", "FAILED");
    static final String UNKNOWN_REGION = "Unknown";

    private final OrderRepository orderRepository;
    private final OrderAllocationRepository allocationRepository;
    private final AddressRepository addressRepository;
    private final WarehouseRepository warehouseRepository;
    private final InventoryRepository inventoryRepository;
    private final BrokeringQueueRepository queueRepository;
    private final FulfillmentExceptionRepository exceptionRepository;
    private final Clock clock;

    @org.springframework.beans.factory.annotation.Autowired
    public OrderNetworkTwinService(OrderRepository orderRepository, OrderAllocationRepository allocationRepository,
                                   AddressRepository addressRepository, WarehouseRepository warehouseRepository,
                                   InventoryRepository inventoryRepository, BrokeringQueueRepository queueRepository,
                                   FulfillmentExceptionRepository exceptionRepository) {
        this(orderRepository, allocationRepository, addressRepository, warehouseRepository, inventoryRepository,
                queueRepository, exceptionRepository, Clock.systemDefaultZone());
    }

    OrderNetworkTwinService(OrderRepository orderRepository, OrderAllocationRepository allocationRepository,
                            AddressRepository addressRepository, WarehouseRepository warehouseRepository,
                            InventoryRepository inventoryRepository, BrokeringQueueRepository queueRepository,
                            FulfillmentExceptionRepository exceptionRepository, Clock clock) {
        this.orderRepository = orderRepository;
        this.allocationRepository = allocationRepository;
        this.addressRepository = addressRepository;
        this.warehouseRepository = warehouseRepository;
        this.inventoryRepository = inventoryRepository;
        this.queueRepository = queueRepository;
        this.exceptionRepository = exceptionRepository;
        this.clock = clock;
    }

    // ------------------------------------------------------------------ DTOs

    public record Kpis(int orders, BigDecimal revenue, Double routedPct, Double splitRatePct, int backorders,
                       BigDecimal avgEstimatedCostPerOrder, Double avgPromiseConfidencePct, int openExceptions,
                       int queueDepth) {}

    public record Channel(String id, String name, int orders, BigDecimal revenue) {}

    public record Node(UUID id, String code, String name, String type, String city, String state, String status,
                       Double fillPct, int allocations, long unitsAllocated, long availableUnits, int skuCount,
                       BigDecimal avgEstimatedCost) {}

    public record Region(String id, int orders) {}

    public record Flow(String from, String to, String kind, int orders) {}

    public record StageCount(String status, int orders) {}

    public record AllocationView(UUID nodeId, String nodeName, String status, Integer quantityAllocated,
                                 Integer quantityRequested, String strategy, String ruleName,
                                 BigDecimal estimatedCost, BigDecimal estimatedDistanceKm, BigDecimal confidence,
                                 LocalDateTime promiseDate) {}

    public record OrderView(UUID id, String reference, String channel, String status, BigDecimal total,
                            String currency, String region, String city, LocalDateTime createdAt,
                            LocalDateTime shippedAt, LocalDateTime deliveredAt, List<AllocationView> allocations) {}

    public record QueueItem(UUID id, UUID orderId, String priority, String status, Integer attempts,
                            String failureReason, LocalDateTime enteredAt) {}

    public record ExceptionItem(UUID id, UUID orderId, String type, String severity, String status, String title,
                                String suggestedAction, LocalDateTime detectedAt) {}

    public record Snapshot(int windowHours, LocalDateTime since, Kpis kpis, List<Channel> channels, List<Node> nodes,
                           List<Region> regions, List<Flow> flows, List<StageCount> lifecycle,
                           List<OrderView> recentOrders, List<QueueItem> queue, List<ExceptionItem> exceptions,
                           LocalDateTime generatedAt) {}

    // ------------------------------------------------------------------ query

    @Transactional(readOnly = true)
    public Snapshot snapshot(UUID tenantId, int requestedHours) {
        int hours = Math.max(1, Math.min(requestedHours, MAX_WINDOW_HOURS));
        LocalDateTime now = LocalDateTime.now(clock);
        LocalDateTime since = now.minusHours(hours);

        List<NxOrder> orders = orderRepository.findTop5000ByTenantIdAndCreatedAtAfterOrderByCreatedAtDesc(tenantId, since);
        Map<UUID, NxOrder> orderById = orders.stream().collect(Collectors.toMap(NxOrder::getId, Function.identity()));
        List<NxOrderAllocation> allocations = orders.isEmpty() ? List.of()
                : allocationRepository.findByTenantIdAndOrderIdIn(tenantId, orderById.keySet());
        Map<UUID, List<NxOrderAllocation>> allocByOrder = allocations.stream()
                .collect(Collectors.groupingBy(NxOrderAllocation::getOrderId));

        Set<UUID> addressIds = orders.stream().map(NxOrder::getShipToAddressId).filter(Objects::nonNull).collect(Collectors.toSet());
        Map<UUID, Address> addressById = addressIds.isEmpty() ? Map.of()
                : addressRepository.findAllById(addressIds).stream().collect(Collectors.toMap(Address::getId, Function.identity()));

        List<Warehouse> warehouses = warehouseRepository.findByTenantId(tenantId, PageRequest.of(0, 200)).getContent();
        Map<UUID, List<NxInventory>> stockByNode = inventoryRepository.findByTenantId(tenantId).stream()
                .filter(i -> i.getNodeId() != null)
                .collect(Collectors.groupingBy(NxInventory::getNodeId));

        List<NxBrokeringQueue> queue = queueRepository.findByTenantIdAndStatusIn(tenantId, OPEN_QUEUE);
        List<NxFulfillmentException> openExceptions = exceptionRepository
                .findByTenantIdAndStatus(tenantId, "OPEN", PageRequest.of(0, 50)).getContent();
        long openExceptionCount = exceptionRepository.countByTenantIdAndStatus(tenantId, "OPEN");

        // Flows and per-entity tallies.
        Map<String, int[]> channelOrders = new TreeMap<>();
        Map<String, BigDecimal> channelRevenue = new HashMap<>();
        Map<String, Integer> regionOrders = new HashMap<>();
        Map<String, Integer> flowCounts = new LinkedHashMap<>();
        Map<String, Integer> lifecycle = new LinkedHashMap<>();
        BigDecimal revenue = BigDecimal.ZERO;
        int routed = 0, split = 0, backorders = 0, costed = 0, confidenceSamples = 0;
        BigDecimal costTotal = BigDecimal.ZERO;
        BigDecimal confidenceTotal = BigDecimal.ZERO;

        for (NxOrder o : orders) {
            String channel = channelId(o.getChannel());
            String region = region(addressById.get(o.getShipToAddressId()));
            String status = upper(o.getStatus());
            BigDecimal total = o.getTotal() == null ? BigDecimal.ZERO : o.getTotal();

            channelOrders.computeIfAbsent(channel, k -> new int[1])[0]++;
            channelRevenue.merge(channel, total, BigDecimal::add);
            regionOrders.merge(region, 1, Integer::sum);
            lifecycle.merge(status.isEmpty() ? "UNKNOWN" : status, 1, Integer::sum);
            revenue = revenue.add(total);
            if (BACKORDER.contains(status)) backorders++;

            List<NxOrderAllocation> live = allocByOrder.getOrDefault(o.getId(), List.of()).stream()
                    .filter(a -> a.getNodeId() != null && !"FAILED".equalsIgnoreCase(a.getStatus())
                            && !"CANCELLED".equalsIgnoreCase(a.getStatus()))
                    .toList();
            Set<UUID> nodes = live.stream().map(NxOrderAllocation::getNodeId).collect(Collectors.toSet());
            if (!nodes.isEmpty() || ROUTED.contains(status)) routed++;
            if (nodes.size() > 1) split++;
            for (UUID node : nodes) {
                flowCounts.merge("channel:" + channel + "|node:" + node, 1, Integer::sum);
                flowCounts.merge("node:" + node + "|region:" + region, 1, Integer::sum);
            }
            BigDecimal orderCost = live.stream().map(NxOrderAllocation::getCostEstimated).filter(Objects::nonNull)
                    .reduce(BigDecimal.ZERO, BigDecimal::add);
            if (live.stream().anyMatch(a -> a.getCostEstimated() != null)) {
                costTotal = costTotal.add(orderCost);
                costed++;
            }
            for (NxOrderAllocation a : live) {
                if (a.getDeliveryPromiseConfidence() != null) {
                    confidenceTotal = confidenceTotal.add(a.getDeliveryPromiseConfidence());
                    confidenceSamples++;
                }
            }
        }

        Map<UUID, List<NxOrderAllocation>> allocByNode = allocations.stream()
                .filter(a -> a.getNodeId() != null && !"FAILED".equalsIgnoreCase(a.getStatus()))
                .collect(Collectors.groupingBy(NxOrderAllocation::getNodeId));

        List<Node> nodes = warehouses.stream().map(w -> node(w, allocByNode.getOrDefault(w.getId(), List.of()),
                        stockByNode.getOrDefault(w.getId(), List.of())))
                .sorted(Comparator.comparingInt(Node::allocations).reversed().thenComparing(Node::code, Comparator.nullsLast(String::compareTo)))
                .toList();

        int orderCount = orders.size();
        Kpis kpis = new Kpis(
                orderCount,
                revenue.setScale(2, RoundingMode.HALF_UP),
                pct(routed, orderCount),
                pct(split, routed),
                backorders,
                costed == 0 ? null : costTotal.divide(BigDecimal.valueOf(costed), 2, RoundingMode.HALF_UP),
                confidenceSamples == 0 ? null : confidenceTotal.multiply(BigDecimal.valueOf(100))
                        .divide(BigDecimal.valueOf(confidenceSamples), 1, RoundingMode.HALF_UP).doubleValue(),
                (int) openExceptionCount,
                queue.size());

        List<Flow> flows = flowCounts.entrySet().stream().map(e -> {
            String[] parts = e.getKey().split("\\|");
            String kind = parts[0].startsWith("channel:") ? "CHANNEL_TO_NODE" : "NODE_TO_REGION";
            return new Flow(parts[0], parts[1], kind, e.getValue());
        }).toList();

        return new Snapshot(
                hours, since, kpis,
                channelOrders.entrySet().stream()
                        .map(e -> new Channel("channel:" + e.getKey(), e.getKey(), e.getValue()[0],
                                channelRevenue.getOrDefault(e.getKey(), BigDecimal.ZERO).setScale(2, RoundingMode.HALF_UP)))
                        .sorted(Comparator.comparingInt(Channel::orders).reversed())
                        .toList(),
                nodes,
                regionOrders.entrySet().stream()
                        .map(e -> new Region("region:" + e.getKey(), e.getValue()))
                        .sorted(Comparator.comparingInt(Region::orders).reversed())
                        .toList(),
                flows,
                lifecycle.entrySet().stream().map(e -> new StageCount(e.getKey(), e.getValue())).toList(),
                orders.stream().limit(25)
                        .map(o -> orderView(o, addressById.get(o.getShipToAddressId()), allocByOrder.getOrDefault(o.getId(), List.of())))
                        .toList(),
                queue.stream()
                        .sorted(Comparator.comparing(NxBrokeringQueue::getEnteredAt, Comparator.nullsLast(Comparator.naturalOrder())))
                        .limit(25)
                        .map(q -> new QueueItem(q.getId(), q.getOrderId(), q.getPriority(), q.getStatus(), q.getAttempts(),
                                q.getFailureReason(), q.getEnteredAt()))
                        .toList(),
                openExceptions.stream()
                        .limit(25)
                        .map(x -> new ExceptionItem(x.getId(), x.getOrderId(), firstNonBlank(x.getExceptionType(), x.getType()),
                                x.getSeverity(), x.getStatus(), firstNonBlank(x.getTitle(), x.getMessage()),
                                x.getSuggestedAction(), x.getDetectedAt()))
                        .toList(),
                now);
    }

    // ------------------------------------------------------------------ helpers

    private Node node(Warehouse w, List<NxOrderAllocation> allocations, List<NxInventory> stock) {
        long units = allocations.stream().mapToLong(a -> a.getQuantityAllocated() == null ? 0 : a.getQuantityAllocated()).sum();
        long available = stock.stream().mapToLong(i -> Math.max(0,
                nz(i.getQuantityOnHand()) - nz(i.getQuantityAllocated()) - nz(i.getQuantityReserved()))).sum();
        int skus = (int) stock.stream().map(NxInventory::getSku).filter(Objects::nonNull).distinct().count();
        List<BigDecimal> costs = allocations.stream().map(NxOrderAllocation::getCostEstimated).filter(Objects::nonNull).toList();
        BigDecimal avgCost = costs.isEmpty() ? null
                : costs.stream().reduce(BigDecimal.ZERO, BigDecimal::add).divide(BigDecimal.valueOf(costs.size()), 2, RoundingMode.HALF_UP);
        return new Node(w.getId(), w.getCode(), w.getName(), w.getType(), w.getCity(), w.getState(), w.getStatus(),
                WarehouseTwinService.fillPct(w.getUsedCapacitySqm(), w.getTotalCapacitySqm()),
                allocations.size(), units, available, skus, avgCost);
    }

    private OrderView orderView(NxOrder o, Address address, List<NxOrderAllocation> allocations) {
        return new OrderView(o.getId(), firstNonBlank(o.getChannelOrderId(), o.getExternalId(), o.getId().toString().substring(0, 8)),
                channelId(o.getChannel()), o.getStatus(), o.getTotal(), o.getCurrency(),
                region(address), address != null ? address.getCity() : null,
                o.getCreatedAt(), o.getShippedAt(), o.getDeliveredAt(),
                allocations.stream()
                        .map(a -> new AllocationView(a.getNodeId(), a.getNodeName(), a.getStatus(), a.getQuantityAllocated(),
                                a.getQuantityRequested(), a.getAllocationStrategy(), a.getRuleName(), a.getCostEstimated(),
                                a.getDistanceKm(), a.getDeliveryPromiseConfidence(), a.getDeliveryPromiseDate()))
                        .toList());
    }

    static String region(Address a) {
        if (a == null) return UNKNOWN_REGION;
        String state = a.getState() != null && !a.getState().isBlank() ? a.getState().trim().toUpperCase() : null;
        String country = a.getCountry() != null && !a.getCountry().isBlank() ? a.getCountry().trim().toUpperCase() : null;
        if (state != null) return country != null && !country.equals("US") && !country.equals("USA") ? state + ", " + country : state;
        return country != null ? country : UNKNOWN_REGION;
    }

    static String channelId(String channel) {
        return channel == null || channel.isBlank() ? "DIRECT" : channel.trim().toUpperCase();
    }

    static Double pct(int part, int whole) {
        if (whole <= 0) return null;
        return BigDecimal.valueOf(part * 100.0 / whole).setScale(1, RoundingMode.HALF_UP).doubleValue();
    }

    private static String firstNonBlank(String... values) {
        for (String v : values) if (v != null && !v.isBlank()) return v;
        return null;
    }

    private static String upper(String s) {
        return s == null ? "" : s.toUpperCase();
    }

    private static long nz(Integer v) {
        return v == null ? 0 : v;
    }
}
