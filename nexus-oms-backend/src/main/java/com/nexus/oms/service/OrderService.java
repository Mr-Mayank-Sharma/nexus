package com.nexus.oms.service;

import com.nexus.oms.service.bigcommerce.BigCommerceOrderStatusPushService;
import com.nexus.oms.domain.OrderStatusPolicy;
import com.nexus.oms.dto.*;
import com.nexus.oms.entity.*;
import com.nexus.oms.exception.BadRequestException;
import com.nexus.oms.security.TenantContext;
import com.nexus.oms.exception.ResourceNotFoundException;
import com.nexus.oms.kafka.KafkaProducerService;
import com.nexus.oms.repository.*;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.cache.annotation.CacheEvict;
import org.springframework.cache.annotation.Cacheable;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.*;
import java.util.stream.Collectors;

@Service
public class OrderService {

    private static final Logger log = LoggerFactory.getLogger(OrderService.class);

    private final OrderRepository orderRepository;
    private final OrderItemRepository orderItemRepository;
    private final CustomerRepository customerRepository;
    private final AddressRepository addressRepository;
    private final InventoryService inventoryService;
    private final KafkaProducerService kafkaProducerService;
    private final ObjectMapper objectMapper;
    private final NodeRepository nodeRepository;
    private final WarehouseRepository warehouseRepository;
    private final OrderRoutingService orderRoutingService;
    private final RoutingConfigRepository routingConfigRepository;
    private final KittingService kittingService;
    private final BrokeringService brokeringService;
    private final BigCommerceOrderStatusPushService bigCommerceStatusPushService;
    private final OrderStockCommit orderStockCommit;

    private FulfilmentReleaseService fulfilmentRelease;

    @org.springframework.beans.factory.annotation.Autowired(required = false)
    public void setFulfilmentRelease(FulfilmentReleaseService fulfilmentRelease) { this.fulfilmentRelease = fulfilmentRelease; }

    /** Optional: fills in a line's picture from the catalogue when the channel did not send one. */
    private com.nexus.oms.repository.ProductRepository productRepository;

    @org.springframework.beans.factory.annotation.Autowired(required = false)
    public void setProductRepository(com.nexus.oms.repository.ProductRepository productRepository) { this.productRepository = productRepository; }

    public OrderService(OrderRepository orderRepository,
                        OrderItemRepository orderItemRepository,
                        CustomerRepository customerRepository,
                        AddressRepository addressRepository,
                        InventoryService inventoryService,
                        KafkaProducerService kafkaProducerService,
                        ObjectMapper objectMapper,
                        NodeRepository nodeRepository,
                        WarehouseRepository warehouseRepository,
                        OrderRoutingService orderRoutingService,
                        RoutingConfigRepository routingConfigRepository,
                        KittingService kittingService,
                        BrokeringService brokeringService,
                        BigCommerceOrderStatusPushService bigCommerceStatusPushService,
                        OrderStockCommit orderStockCommit) {
        this.orderRepository = orderRepository;
        this.orderItemRepository = orderItemRepository;
        this.customerRepository = customerRepository;
        this.addressRepository = addressRepository;
        this.inventoryService = inventoryService;
        this.kafkaProducerService = kafkaProducerService;
        this.objectMapper = objectMapper;
        this.nodeRepository = nodeRepository;
        this.warehouseRepository = warehouseRepository;
        this.orderRoutingService = orderRoutingService;
        this.routingConfigRepository = routingConfigRepository;
        this.kittingService = kittingService;
        this.brokeringService = brokeringService;
        this.bigCommerceStatusPushService = bigCommerceStatusPushService;
        this.orderStockCommit = orderStockCommit;
    }

    @Transactional
    @CacheEvict(value = "orders", allEntries = true)
    public OrderResponse createOrder(UUID tenantId, OrderRequest request) {
        String country = request.getShippingAddress().getCountry() != null 
                ? request.getShippingAddress().getCountry() 
                : "US";
        
        Address shipToAddress = addressRepository.save(Address.builder()
                .tenantId(tenantId)
                .addressLine1(request.getShippingAddress().getLine1())
                .addressLine2(request.getShippingAddress().getLine2())
                .city(request.getShippingAddress().getCity())
                .state(request.getShippingAddress().getState())
                .postalCode(request.getShippingAddress().getPincode())
                .country(country)
                .addressType("SHIPPING")
                .build());

        // SECURITY: tenant-scoped lookup (was findByEmail — matched across tenants)
        List<NxCustomer> existingCustomers = customerRepository.findAllByTenantIdAndEmail(tenantId, request.getCustomerEmail());
        NxCustomer customer = !existingCustomers.isEmpty() ? existingCustomers.get(0)
                : customerRepository.save(NxCustomer.builder()
                        .tenantId(tenantId)
                        .name(request.getCustomerName())
                        .email(request.getCustomerEmail())
                        .address(shipToAddress)
                        .build());

        String currency = request.getCurrency() != null ? request.getCurrency() : "USD";
        
        NxOrder order = NxOrder.builder()
                .tenantId(tenantId)
                .channel(request.getChannel())
                .customerId(customer.getId())
                .customerEmail(customer.getEmail())
                .status("PENDING")
                .fulfillmentType(request.getFulfillmentType() != null && !request.getFulfillmentType().isBlank()
                        ? request.getFulfillmentType().toUpperCase()
                        : "SHIP_TO_HOME")
                .shipToAddress(shipToAddress)
                .currency(currency)
                .subtotal(BigDecimal.ZERO)
                .shippingCost(BigDecimal.ZERO)
                .taxAmount(BigDecimal.ZERO)
                .total(BigDecimal.ZERO)
                .orderType(FulfilmentReleaseService.normaliseType(request.getOrderType()))
                .requestedShipDate(request.getRequestedShipDate())
                .build();
        if (fulfilmentRelease != null) fulfilmentRelease.plan(order);

        order = orderRepository.save(order);

        BigDecimal subtotal = BigDecimal.ZERO;
        for (OrderRequest.OrderItemRequest itemReq : request.getItems()) {
            BigDecimal totalPrice = itemReq.getUnitPrice().multiply(BigDecimal.valueOf(itemReq.getQuantity()));
            NxOrderItem item = NxOrderItem.builder()
                    .orderId(order.getId())
                    .sku(itemReq.getSku())
                    .productName(itemReq.getProductName())
                    .quantity(itemReq.getQuantity())
                    .unitPrice(itemReq.getUnitPrice())
                    .totalPrice(totalPrice)
                    .allocatedQty(0)
                    .build();
            orderItemRepository.save(item);
            subtotal = subtotal.add(totalPrice);
        }

        order.setSubtotal(subtotal);
        order.setTotal(subtotal.add(order.getShippingCost()).add(order.getTaxAmount()));
        order = orderRepository.save(order);

        if (kittingService.hasKitLines(tenantId, request.getItems())) {
            kittingService.explodeAndPersist(tenantId, order.getId());
        }

        kafkaProducerService.publishEntityEvent("order.created", order.getId().toString());

        return toOrderResponse(order);
    }

    @Cacheable(value = "orders", key = "#id")
    public OrderResponse getOrder(UUID id) {
        NxOrder order = orderRepository.findByIdAndTenantId(id, TenantContext.getCurrentTenantId())
                .orElseThrow(() -> new ResourceNotFoundException("Order", id));
        return toOrderResponse(order);
    }

    @Cacheable(value = "orders", key = "#tenantId + ':' + #id")
    public OrderResponse getOrder(UUID tenantId, String id) {
        try {
            return getOrder(UUID.fromString(id));
        } catch (IllegalArgumentException ignored) {
            NxOrder order = orderRepository.findByTenantIdAndChannelOrderId(tenantId, id)
                    .orElseThrow(() -> new ResourceNotFoundException("Order", id));
            return toOrderResponse(order);
        }
    }

    public Page<OrderResponse> getOrders(UUID tenantId, String status, String search, Pageable pageable) {
        return getOrders(tenantId, status, search, null, pageable);
    }

    public Page<OrderResponse> getOrders(UUID tenantId, String status, String search, String channel, Pageable pageable) {
        Page<NxOrder> orders;
        if ((search == null || search.isBlank()) && channel != null && !channel.isBlank()) {
            orders = orderRepository.findByChannel(tenantId, status == null || status.isBlank() ? null : status, channel.trim().toUpperCase(), pageable);
        } else if (search != null && !search.isBlank()) {
            orders = orderRepository.search(tenantId, search, pageable);
        } else if (status != null && !status.isBlank()) {
            orders = orderRepository.findByTenantIdAndStatus(tenantId, status, pageable);
        } else {
            orders = orderRepository.findByTenantId(tenantId, pageable);
        }
        // One query for every line on the page and one for the customers, instead of two per order.
        List<UUID> ids = orders.getContent().stream().map(NxOrder::getId).toList();
        Map<UUID, List<NxOrderItem>> itemsByOrder = ids.isEmpty() ? Map.of()
                : orderItemRepository.findByOrderIdIn(ids).stream().collect(Collectors.groupingBy(NxOrderItem::getOrderId));
        Set<UUID> customerIds = orders.getContent().stream().map(NxOrder::getCustomerId).filter(Objects::nonNull).collect(Collectors.toSet());
        Map<UUID, NxCustomer> customers = customerIds.isEmpty() ? Map.of()
                : customerRepository.findAllById(customerIds).stream().collect(Collectors.toMap(NxCustomer::getId, c -> c, (x, y) -> x));
        return orders.map(o -> toOrderResponse(o, itemsByOrder.getOrDefault(o.getId(), List.of()), o.getCustomerId() == null ? null : customers.get(o.getCustomerId())));
    }

    /** Orders per status, and the total, for list headers and tabs. */
    public Map<String, Long> countByStatus(UUID tenantId) {
        Map<String, Long> out = new LinkedHashMap<>();
        long total = 0;
        for (Object[] row : orderRepository.countByStatus(tenantId)) {
            long n = ((Number) row[1]).longValue();
            out.put(String.valueOf(row[0]), n);
            total += n;
        }
        out.put("ALL", total);
        return out;
    }

    @Transactional
    @CacheEvict(value = "orders", allEntries = true)
    public OrderResponse updateStatus(UUID id, String status, String subStatus, String trackingNumber, String carrierId) {
        NxOrder order = orderRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Order", id));
        // PUT /orders/{id}/status used to accept any jump (e.g. DELIVERED -> PENDING).
        OrderStatusPolicy.requireTransition(order, status);
        if (subStatus != null) order.setSubStatus(subStatus);
        if (trackingNumber != null) order.setTrackingNumber(trackingNumber);
        if (carrierId != null) order.setCarrierId(carrierId);

        if ("SHIPPED".equalsIgnoreCase(status)) {
            order.setShippedAt(LocalDateTime.now());
        } else if ("DELIVERED".equalsIgnoreCase(status)) {
            order.setDeliveredAt(LocalDateTime.now());
        }

        order = orderRepository.save(order);
        if (java.util.Set.of("SHIPPED", "IN_TRANSIT", "DELIVERED", "FULFILLED").contains(status.toUpperCase())) {
            orderStockCommit.consumeOnShip(order);   // idempotent: a no-op if it already shipped
        }
        bigCommerceStatusPushService.pushOrderStatus(order.getTenantId(), order.getExternalId(), status);
        kafkaProducerService.publishEntityEvent("order." + status.toLowerCase(), order.getId().toString());
        return toOrderResponse(order);
    }

    @Transactional
    @CacheEvict(value = {"orders", "inventory"}, allEntries = true)
    public AllocationResponse allocateOrder(UUID id) {
        NxOrder order = orderRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Order", id));

        // Idempotency guard: never double-reserve inventory for an already-allocated order
        if (order.getAllocatedNode() != null || "ALLOCATED".equalsIgnoreCase(order.getStatus())) {
            throw new BadRequestException("Order is already allocated to " + order.getAllocatedNode());
        }

        UUID tenantId = order.getTenantId();
        List<NxOrderItem> items = orderItemRepository.findByOrderId(id);
        UUID selectedNode = findNodeWithStock(tenantId, items);
        if (selectedNode == null) {
            throw new BadRequestException("Insufficient inventory to allocate order");
        }

        // Sort by SKU for consistent lock ordering across concurrent allocations (prevents deadlocks)
        List<NxOrderItem> sortedItems = items.stream()
                .sorted(java.util.Comparator.comparing(NxOrderItem::getSku, java.util.Comparator.nullsLast(java.util.Comparator.naturalOrder())))
                .toList();
        for (NxOrderItem item : sortedItems) {
            inventoryService.reserveInventory(tenantId, item.getSku(), selectedNode, item.getQuantity(), "ORDER", order.getId().toString());
            item.setAllocatedNodeId(selectedNode);
            item.setAllocatedQty(item.getQuantity());
            orderItemRepository.save(item);
        }

        order.setAllocatedNode(selectedNode);
        order.setAllocationRule("NEAREST_AVAILABLE");
        order.setAllocationConfidence(new BigDecimal("0.95"));
        OrderStatusPolicy.requireTransition(order, OrderStatusPolicy.ALLOCATED);
        order = orderRepository.save(order);

        kafkaProducerService.publishEntityEvent("order.allocated", order.getId().toString());

        return AllocationResponse.builder()
                .warehouse(selectedNode.toString())
                .carrier("AUTO")
                .boxSize("STANDARD")
                .pickPackDetails("All items allocated from node " + selectedNode)
                .confidence(new BigDecimal("0.95"))
                .rule("NEAREST_AVAILABLE")
                .build();
    }

    @Transactional
    @CacheEvict(value = "orders", allEntries = true)
    public OrderResponse confirmOrder(UUID id) {
        NxOrder order = orderRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Order", id));

        boolean allocated = false;
        if ("PENDING".equalsIgnoreCase(order.getStatus()) && isAutoAllocationEnabled(order.getTenantId())) {
            try {
                AllocationRequest request = new AllocationRequest();
                request.setOrderId(id);
                request.setStrategy(null);
                request.setDryRun(false);
                AllocationResult result = orderRoutingService.allocateOrder(request);
                // Only treat as allocated if every allocation row succeeded (not empty / FAILED)
                allocated = "ALLOCATED".equals(result.getStatus())
                        && !result.getAllocations().isEmpty()
                        && result.getAllocations().stream().allMatch(a -> "ALLOCATED".equals(a.getStatus()));
                order = orderRepository.findById(id)
                        .orElseThrow(() -> new ResourceNotFoundException("Order", id));
            } catch (Exception e) {
                log.warn("Auto-allocation failed for order {}: {}", id, e.getMessage());
            }
        }

        if (!allocated) {
            // Routing already persisted its honest outcome (PARTIALLY_ALLOCATED / FAILED), and
            // `order` was re-read from the DB after the allocation above. Do NOT stomp that to
            // CONFIRMED: T-17 made these states the truthful record of a short allocation, and
            // overwriting them here re-hides the shortfall from every downstream consumer
            // (dashboard, Orders UI, AI agent queue, backorder exception) on the main
            // confirmation path — the one path every order takes.
            // CONFIRMED is still right when routing had no opinion: auto-allocation is off, or
            // allocateOrder threw before saving anything (order is still PENDING).
            boolean routingReportedShortfall = "PARTIALLY_ALLOCATED".equals(order.getStatus())
                    || "FAILED".equals(order.getStatus());
            if (!routingReportedShortfall) {
                OrderStatusPolicy.transitionIfAllowed(order, OrderStatusPolicy.CONFIRMED, "confirmOrder");
            }
            order = orderRepository.save(order);
            try {
                brokeringService.enqueueOrder(id, "NORMAL");
            } catch (Exception e) {
                log.warn("Could not enqueue order {} for brokering: {}", id, e.getMessage());
            }
            kafkaProducerService.publishEntityEvent("order.confirmed", order.getId().toString());
        }

        return toOrderResponse(order);
    }

    private boolean isAutoAllocationEnabled(UUID tenantId) {
        return routingConfigRepository.findByTenantId(tenantId)
                .map(NxRoutingConfig::getEnableAutoAllocation)
                .orElse(false);
    }

    @Transactional
    @CacheEvict(value = "orders", allEntries = true)
    public OrderResponse shipOrder(UUID id, String carrierId, String trackingNumber) {
        NxOrder order = orderRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Order", id));
        // Previously shipped orders in any state, including CANCELLED.
        OrderStatusPolicy.requireTransition(order, OrderStatusPolicy.SHIPPED);
        order.setCarrierId(carrierId);
        order.setTrackingNumber(trackingNumber);
        order.setShippedAt(LocalDateTime.now());
        order = orderRepository.save(order);
        orderStockCommit.consumeOnShip(order);   // the stock leaves the books with the parcel
        bigCommerceStatusPushService.pushOrderStatus(order.getTenantId(), order.getExternalId(), "SHIPPED");
        kafkaProducerService.publishEntityEvent("order.shipped", order.getId().toString());
        return toOrderResponse(order);
    }

    @Transactional
    @CacheEvict(value = "orders", allEntries = true)
    public OrderResponse modifyOrder(UUID id, UUID tenantId, OrderModifyRequest request) {
        NxOrder order = orderRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Order", id));

        if (!order.getTenantId().equals(tenantId)) {
            throw new BadRequestException("Order does not belong to current tenant");
        }

        if (List.of("SHIPPED", "DELIVERED", "CANCELLED").contains(order.getStatus().toUpperCase())) {
            throw new BadRequestException("Cannot modify order in " + order.getStatus() + " status");
        }

        if (request.getShippingAddress() != null) {
            String country = request.getShippingAddress().getCountry() != null 
                    ? request.getShippingAddress().getCountry() 
                    : "US";
            
            Address addr = addressRepository.save(Address.builder()
                    .tenantId(tenantId)
                    .addressLine1(request.getShippingAddress().getLine1())
                    .addressLine2(request.getShippingAddress().getLine2())
                    .city(request.getShippingAddress().getCity())
                    .state(request.getShippingAddress().getState())
                    .postalCode(request.getShippingAddress().getPincode())
                    .country(country)
                    .addressType("SHIPPING")
                    .build());
            order.setShipToAddress(addr);
        }

        if (request.getShippingCost() != null) {
            order.setShippingCost(request.getShippingCost());
        }

        if (request.getItems() != null && !request.getItems().isEmpty()) {
            List<NxOrderItem> existingItems = orderItemRepository.findByOrderId(id);

            for (NxOrderItem existing : existingItems) {
                if (existing.getAllocatedQty() != null && existing.getAllocatedQty() > 0) {
                    throw new BadRequestException("Cannot modify items on allocated order. Reverse allocation first.");
                }
            }

            orderItemRepository.deleteAll(existingItems);

            BigDecimal subtotal = BigDecimal.ZERO;
            for (OrderModifyRequest.OrderItemModify itemReq : request.getItems()) {
                BigDecimal totalPrice = itemReq.getUnitPrice().multiply(BigDecimal.valueOf(itemReq.getQuantity()));
                NxOrderItem item = NxOrderItem.builder()
                        .orderId(order.getId())
                        .sku(itemReq.getSku())
                        .productName(itemReq.getProductName())
                        .quantity(itemReq.getQuantity())
                        .unitPrice(itemReq.getUnitPrice())
                        .totalPrice(totalPrice)
                        .allocatedQty(0)
                        .build();
                orderItemRepository.save(item);
                subtotal = subtotal.add(totalPrice);
            }
            order.setSubtotal(subtotal);
        }

        order.setTotal(order.getSubtotal().add(order.getShippingCost()).add(order.getTaxAmount()));
        order = orderRepository.save(order);

        kafkaProducerService.publishEntityEvent("order.modified", order.getId().toString());
        return toOrderResponse(order);
    }

    @Transactional
    @CacheEvict(value = "orders", allEntries = true)
    public List<OrderResponse> splitOrder(UUID id, UUID tenantId, SplitOrderRequest request) {
        NxOrder order = orderRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Order", id));

        if (!order.getTenantId().equals(tenantId)) {
            throw new BadRequestException("Order does not belong to current tenant");
        }

        if (!"PENDING".equals(order.getStatus()) && !"CONFIRMED".equals(order.getStatus())) {
            throw new BadRequestException("Can only split PENDING or CONFIRMED orders");
        }

        List<NxOrderItem> allItems = orderItemRepository.findByOrderId(id);
        Map<UUID, NxOrderItem> itemMap = allItems.stream()
                .collect(Collectors.toMap(NxOrderItem::getId, i -> i));

        List<OrderResponse> splitOrders = new ArrayList<>();

        for (SplitOrderRequest.SplitGroup group : request.getGroups()) {
            NxOrder split = NxOrder.builder()
                    .tenantId(order.getTenantId())
                    .channel(order.getChannel())
                    .customerId(order.getCustomerId())
                    .status("PENDING")
                    .shipToAddress(order.getShipToAddress())
                    .currency(order.getCurrency())
                    .fulfillmentType(order.getFulfillmentType())
                    .subtotal(BigDecimal.ZERO)
                    .shippingCost(BigDecimal.ZERO)
                    .taxAmount(BigDecimal.ZERO)
                    .total(BigDecimal.ZERO)
                    .build();
            split = orderRepository.save(split);

            BigDecimal subtotal = BigDecimal.ZERO;
            for (String itemIdStr : group.getItemIds()) {
                UUID itemId = UUID.fromString(itemIdStr);
                NxOrderItem original = itemMap.get(itemId);
                if (original == null) continue;

                NxOrderItem newItem = NxOrderItem.builder()
                        .orderId(split.getId())
                        .sku(original.getSku())
                        .productName(original.getProductName())
                        .quantity(original.getQuantity())
                        .unitPrice(original.getUnitPrice())
                        .totalPrice(original.getTotalPrice())
                        .allocatedQty(0)
                        .build();
                orderItemRepository.save(newItem);
                subtotal = subtotal.add(original.getTotalPrice());

                orderItemRepository.delete(original);
            }

            split.setSubtotal(subtotal);
            split.setTotal(subtotal);
            split = orderRepository.save(split);
            splitOrders.add(toOrderResponse(split));
        }

        List<NxOrderItem> remaining = orderItemRepository.findByOrderId(id);
        if (remaining.isEmpty()) {
            OrderStatusPolicy.requireTransition(order, OrderStatusPolicy.SPLIT);
            orderRepository.save(order);
        } else {
            BigDecimal remainingSubtotal = remaining.stream()
                    .map(NxOrderItem::getTotalPrice)
                    .reduce(BigDecimal.ZERO, BigDecimal::add);
            order.setSubtotal(remainingSubtotal);
            order.setTotal(remainingSubtotal.add(order.getShippingCost()).add(order.getTaxAmount()));
            orderRepository.save(order);
        }

        kafkaProducerService.publishEntityEvent("order.split", id.toString());
        return splitOrders;
    }

    @Transactional
    @CacheEvict(value = "orders", allEntries = true)
    public OrderResponse mergeOrders(MergeOrdersRequest request, UUID tenantId) {
        List<NxOrder> sourceOrders = orderRepository.findAllById(request.getOrderIds());
        if (sourceOrders.isEmpty()) {
            throw new BadRequestException("No source orders found");
        }

        for (NxOrder src : sourceOrders) {
            if (!src.getTenantId().equals(tenantId)) {
                throw new BadRequestException("Order " + src.getId() + " does not belong to current tenant");
            }
            if (List.of("SHIPPED", "DELIVERED", "CANCELLED").contains(src.getStatus().toUpperCase())) {
                throw new BadRequestException("Cannot merge order " + src.getId() + " in " + src.getStatus() + " status");
            }
        }

        NxOrder target = orderRepository.findById(request.getTargetOrderId())
                .orElseThrow(() -> new ResourceNotFoundException("Target order", request.getTargetOrderId()));

        if (!target.getTenantId().equals(tenantId)) {
            throw new BadRequestException("Target order does not belong to current tenant");
        }

        BigDecimal additionalSubtotal = BigDecimal.ZERO;
        for (NxOrder src : sourceOrders) {
            if (src.getId().equals(target.getId())) continue;

            List<NxOrderItem> items = orderItemRepository.findByOrderId(src.getId());
            for (NxOrderItem item : items) {
                NxOrderItem moved = NxOrderItem.builder()
                        .orderId(target.getId())
                        .sku(item.getSku())
                        .productName(item.getProductName())
                        .quantity(item.getQuantity())
                        .unitPrice(item.getUnitPrice())
                        .totalPrice(item.getTotalPrice())
                        .allocatedQty(0)
                        .build();
                orderItemRepository.save(moved);
                additionalSubtotal = additionalSubtotal.add(item.getTotalPrice());
            }
            orderItemRepository.deleteAll(items);
            OrderStatusPolicy.requireTransition(src, OrderStatusPolicy.MERGED);
            orderRepository.save(src);
        }

        target.setSubtotal(target.getSubtotal().add(additionalSubtotal));
        target.setTotal(target.getSubtotal().add(target.getShippingCost()).add(target.getTaxAmount()));
        target = orderRepository.save(target);

        kafkaProducerService.publishEntityEvent("order.merged", target.getId().toString());
        return toOrderResponse(target);
    }

    @Transactional
    @CacheEvict(value = {"orders", "inventory"}, allEntries = true)
    public OrderResponse cancelOrder(UUID id) {
        NxOrder order = orderRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Order", id));
        // Check before touching inventory: cancelling a SHIPPED/DELIVERED order used to succeed
        // and put its already-shipped units back into available stock.
        if (!OrderStatusPolicy.isAllowed(order.getStatus(), OrderStatusPolicy.CANCELLED)) {
            throw new OrderStatusPolicy.IllegalOrderTransitionException(
                    OrderStatusPolicy.normalise(order.getStatus()), OrderStatusPolicy.CANCELLED);
        }

        List<NxOrderItem> items = orderItemRepository.findByOrderId(id);
        for (NxOrderItem item : items) {
            // Only the part still reserved: units already shipped were taken off stock when they
            // shipped, and releasing them again would free another order's allocation.
            int stillReserved = (item.getAllocatedQty() == null ? 0 : item.getAllocatedQty())
                    - (item.getConsumedQty() == null ? 0 : item.getConsumedQty());
            if (item.getAllocatedNodeId() != null && stillReserved > 0) {
                inventoryService.releaseInventory(order.getTenantId(), item.getSku(),
                        item.getAllocatedNodeId(), stillReserved, "ORDER", order.getId().toString());
            }
        }

        OrderStatusPolicy.requireTransition(order, OrderStatusPolicy.CANCELLED);
        order = orderRepository.save(order);
        bigCommerceStatusPushService.pushOrderStatus(order.getTenantId(), order.getExternalId(), "CANCELLED");
        kafkaProducerService.publishEntityEvent("order.cancelled", order.getId().toString());
        return toOrderResponse(order);
    }

    public Map<String, Long> getOrderStats(UUID tenantId) {
        Map<String, Long> stats = new LinkedHashMap<>();
        stats.put("activePicklists", orderRepository.countByTenantIdAndStatus(tenantId, "PENDING"));
        stats.put("pendingItems", orderRepository.countByTenantIdAndStatus(tenantId, "CONFIRMED"));
        stats.put("pickedItems", orderRepository.countByTenantIdAndStatus(tenantId, "ALLOCATED"));
        stats.put("pendingPack", orderRepository.countByTenantIdAndStatus(tenantId, "ALLOCATED"));
        stats.put("packing", orderRepository.countByTenantIdAndStatus(tenantId, "PICKING"));
        stats.put("packed", orderRepository.countByTenantIdAndStatus(tenantId, "PACKED"));
        stats.put("shipped", orderRepository.countByTenantIdAndStatus(tenantId, "SHIPPED"));
        long completedToday = orderRepository.countByTenantIdAndCreatedAtAfter(tenantId, LocalDateTime.now().withHour(0).withMinute(0).withSecond(0));
        stats.put("completedToday", completedToday);
        return stats;
    }

    /** True when one active location holds enough stock for every line, i.e. allocate would succeed. */
    @Transactional(readOnly = true)
    public boolean canAllocate(UUID id) {
        NxOrder order = orderRepository.findById(id).orElse(null);
        if (order == null || order.getAllocatedNode() != null) return false;
        return findNodeWithStock(order.getTenantId(), orderItemRepository.findByOrderId(id)) != null;
    }

    /** The first active location (or operational warehouse) that can cover every line, or null. */
    private UUID findNodeWithStock(UUID tenantId, List<NxOrderItem> items) {
        List<UUID> candidateNodeIds = nodeRepository.findByTenantIdAndIsActiveTrue(tenantId).stream()
                .map(NxNode::getId)
                .collect(Collectors.toList());
        if (candidateNodeIds.isEmpty()) {
            // Fall back to the operational warehouses — inventory is a shared tenant+SKU pool (node_id NULL)
            candidateNodeIds = warehouseRepository.findOperational(tenantId).stream()
                    .map(Warehouse::getId)
                    .collect(Collectors.toList());
        }
        if (candidateNodeIds.isEmpty()) {
            throw new BadRequestException("No active warehouse nodes found");
        }
        return candidateNodeIds.stream()
                .filter(nodeId -> items.stream()
                        .allMatch(item -> inventoryService.checkAvailability(
                                tenantId, item.getSku(), nodeId, item.getQuantity())))
                .findFirst()
                .orElse(null);
    }

    private OrderResponse toOrderResponse(NxOrder order) {
        NxCustomer customer = order.getCustomerId() == null ? null : customerRepository.findById(order.getCustomerId()).orElse(null);
        List<NxOrderItem> items = orderItemRepository.findByOrderId(order.getId());
        return toOrderResponse(order, items, customer, catalogueImages(order.getTenantId(), items));
    }

    /** Pictures from the product catalogue for lines that arrived without one, keyed by SKU. */
    private Map<String, String> catalogueImages(UUID tenantId, List<NxOrderItem> items) {
        if (productRepository == null || tenantId == null) return Map.of();
        List<String> skus = items.stream()
                .filter(i -> i.getSku() != null && (i.getImageUrl() == null || i.getImageUrl().isBlank()))
                .map(NxOrderItem::getSku).distinct().toList();
        if (skus.isEmpty()) return Map.of();
        Map<String, String> images = new HashMap<>();
        for (var p : productRepository.findByTenantIdAndSkuIn(tenantId, skus)) {
            if (p.getImageUrl() != null && !p.getImageUrl().isBlank()) images.putIfAbsent(p.getSku(), p.getImageUrl());
        }
        return images;
    }

    private OrderResponse toOrderResponse(NxOrder order, List<NxOrderItem> items, NxCustomer customer) {
        return toOrderResponse(order, items, customer, Map.of());
    }

    private OrderResponse toOrderResponse(NxOrder order, List<NxOrderItem> items, NxCustomer customer, Map<String, String> images) {
        String customerName = customer == null ? null : customer.getName();
        String customerEmail = customer == null ? null : customer.getEmail();

        return OrderResponse.builder()
                .id(order.getId())
                .tenantId(order.getTenantId())
                .externalId(order.getExternalId())
                .channel(order.getChannel())
                .channelOrderId(order.getChannelOrderId())
                .customerId(order.getCustomerId())
                .customerName(customerName)
                .customerEmail(customerEmail)
                .status(order.getStatus())
                .subStatus(order.getSubStatus())
                .fulfillmentType(order.getFulfillmentType())
                .orderType(order.getOrderType())
                .requestedShipDate(order.getRequestedShipDate())
                .releaseAt(order.getReleaseAt())
                .releasedAt(order.getReleasedAt())
                .shipTo(toAddressMap(order.getShipToAddress()))
                .billingAddress(toAddressMap(order.getBillingAddress()))
                .currency(order.getCurrency())
                .subtotal(order.getSubtotal())
                .shippingCost(order.getShippingCost())
                .taxAmount(order.getTaxAmount())
                .total(order.getTotal())
                .paymentStatus(order.getPaymentStatus())
                .paymentReference(order.getPaymentReference())
                .allocatedNode(order.getAllocatedNode())
                .allocationRule(order.getAllocationRule())
                .allocationConfidence(order.getAllocationConfidence())
                .carrierId(order.getCarrierId())
                .trackingNumber(order.getTrackingNumber())
                .labelUrl(order.getLabelUrl())
                .promisedDelivery(order.getPromisedDelivery())
                .createdAt(order.getCreatedAt())
                .updatedAt(order.getUpdatedAt())
                .shippedAt(order.getShippedAt())
                .deliveredAt(order.getDeliveredAt())
                .metadata(parseJson(order.getMetadata()))
                .items(items.stream().map(i -> toItemDto(i, images)).collect(Collectors.toList()))
                .build();
    }

    private OrderResponse.OrderItemDto toItemDto(NxOrderItem item, Map<String, String> images) {
        String image = item.getImageUrl() != null && !item.getImageUrl().isBlank() ? item.getImageUrl() : images.get(item.getSku());
        return OrderResponse.OrderItemDto.builder()
                .id(item.getId())
                .sku(item.getSku())
                .productName(item.getProductName())
                .imageUrl(image)
                .quantity(item.getQuantity())
                .unitPrice(item.getUnitPrice())
                .totalPrice(item.getTotalPrice())
                .allocatedNodeId(item.getAllocatedNodeId())
                .allocatedQty(item.getAllocatedQty())
                .build();
    }

    private Map<String, Object> toAddressMap(Address address) {
        if (address == null) return null;
        Map<String, Object> map = new LinkedHashMap<>();
        map.put("line1", address.getAddressLine1());
        map.put("line2", address.getAddressLine2());
        map.put("city", address.getCity());
        map.put("state", address.getState());
        map.put("pincode", address.getPostalCode());
        map.put("country", address.getCountry());
        map.put("fullName", address.getFullName());
        map.put("company", address.getCompany());
        map.put("phone", address.getPhone());
        return map;
    }

    private Object parseJson(String json) {
        if (json == null || json.isBlank()) return null;
        try {
            return objectMapper.readTree(json);
        } catch (JsonProcessingException e) {
            return json;
        }
    }
}
