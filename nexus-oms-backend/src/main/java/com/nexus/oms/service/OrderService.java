package com.nexus.oms.service;

import com.nexus.oms.dto.*;
import com.nexus.oms.entity.*;
import com.nexus.oms.exception.BadRequestException;
import com.nexus.oms.exception.ResourceNotFoundException;
import com.nexus.oms.kafka.KafkaProducerService;
import com.nexus.oms.repository.*;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
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

    private final OrderRepository orderRepository;
    private final OrderItemRepository orderItemRepository;
    private final CustomerRepository customerRepository;
    private final AddressRepository addressRepository;
    private final InventoryService inventoryService;
    private final KafkaProducerService kafkaProducerService;
    private final ObjectMapper objectMapper;
    private final NodeRepository nodeRepository;

    public OrderService(OrderRepository orderRepository,
                        OrderItemRepository orderItemRepository,
                        CustomerRepository customerRepository,
                        AddressRepository addressRepository,
                        InventoryService inventoryService,
                        KafkaProducerService kafkaProducerService,
                        ObjectMapper objectMapper,
                        NodeRepository nodeRepository) {
        this.orderRepository = orderRepository;
        this.orderItemRepository = orderItemRepository;
        this.customerRepository = customerRepository;
        this.addressRepository = addressRepository;
        this.inventoryService = inventoryService;
        this.kafkaProducerService = kafkaProducerService;
        this.objectMapper = objectMapper;
        this.nodeRepository = nodeRepository;
    }

    @Transactional
    @CacheEvict(value = "orders", allEntries = true)
    public OrderResponse createOrder(UUID tenantId, OrderRequest request) {
        Address shipToAddress = addressRepository.save(Address.builder()
                .tenantId(tenantId)
                .addressLine1(request.getShippingAddress().getLine1())
                .addressLine2(request.getShippingAddress().getLine2())
                .city(request.getShippingAddress().getCity())
                .state(request.getShippingAddress().getState())
                .postalCode(request.getShippingAddress().getPincode())
                .country("IN")
                .addressType("SHIPPING")
                .build());

        NxCustomer customer = customerRepository.findByEmail(request.getCustomerEmail())
                .orElseGet(() -> customerRepository.save(NxCustomer.builder()
                        .tenantId(tenantId)
                        .name(request.getCustomerName())
                        .email(request.getCustomerEmail())
                        .address(shipToAddress)
                        .build()));

        NxOrder order = NxOrder.builder()
                .tenantId(tenantId)
                .channel(request.getChannel())
                .customerId(customer.getId())
                .status("PENDING")
                .shipToAddress(shipToAddress)
                .currency("INR")
                .subtotal(BigDecimal.ZERO)
                .shippingCost(BigDecimal.ZERO)
                .taxAmount(BigDecimal.ZERO)
                .total(BigDecimal.ZERO)
                .build();

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

        kafkaProducerService.publish("order.created", order.getId().toString());

        return toOrderResponse(order);
    }

    @Cacheable(value = "orders", key = "#id")
    public OrderResponse getOrder(UUID id) {
        NxOrder order = orderRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Order", id));
        return toOrderResponse(order);
    }

    public Page<OrderResponse> getOrders(UUID tenantId, String status, String search, Pageable pageable) {
        Page<NxOrder> orders;
        if (search != null && !search.isBlank()) {
            orders = orderRepository.search(tenantId, search, pageable);
        } else if (status != null && !status.isBlank()) {
            orders = orderRepository.findByTenantIdAndStatus(tenantId, status, pageable);
        } else {
            orders = orderRepository.findByTenantId(tenantId, pageable);
        }
        return orders.map(this::toOrderResponse);
    }

    @Transactional
    @CacheEvict(value = "orders", allEntries = true)
    public OrderResponse updateStatus(UUID id, String status, String subStatus, String trackingNumber, String carrierId) {
        NxOrder order = orderRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Order", id));
        order.setStatus(status);
        if (subStatus != null) order.setSubStatus(subStatus);
        if (trackingNumber != null) order.setTrackingNumber(trackingNumber);
        if (carrierId != null) order.setCarrierId(carrierId);

        if ("SHIPPED".equalsIgnoreCase(status)) {
            order.setShippedAt(LocalDateTime.now());
        } else if ("DELIVERED".equalsIgnoreCase(status)) {
            order.setDeliveredAt(LocalDateTime.now());
        }

        order = orderRepository.save(order);
        kafkaProducerService.publish("order." + status.toLowerCase(), order.getId().toString());
        return toOrderResponse(order);
    }

    @Transactional
    @CacheEvict(value = {"orders", "inventory"}, allEntries = true)
    public AllocationResponse allocateOrder(UUID id) {
        NxOrder order = orderRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Order", id));

        UUID tenantId = order.getTenantId();
        List<NxNode> activeNodes = nodeRepository.findByTenantIdAndIsActiveTrue(tenantId);
        if (activeNodes.isEmpty()) {
            throw new BadRequestException("No active warehouse nodes found");
        }

        List<NxOrderItem> items = orderItemRepository.findByOrderId(id);

        UUID selectedNode = activeNodes.stream()
                .filter(node -> items.stream()
                        .allMatch(item -> inventoryService.checkAvailability(
                                tenantId, item.getSku(), node.getId(), item.getQuantity())))
                .map(NxNode::getId)
                .findFirst()
                .orElse(null);

        if (selectedNode == null) {
            throw new BadRequestException("Insufficient inventory to allocate order");
        }

        for (NxOrderItem item : items) {
            inventoryService.reserveInventory(tenantId, item.getSku(), selectedNode, item.getQuantity());
            item.setAllocatedNodeId(selectedNode);
            item.setAllocatedQty(item.getQuantity());
            orderItemRepository.save(item);
        }

        order.setAllocatedNode(selectedNode);
        order.setAllocationRule("NEAREST_AVAILABLE");
        order.setAllocationConfidence(new BigDecimal("0.95"));
        order.setStatus("ALLOCATED");
        order = orderRepository.save(order);

        kafkaProducerService.publish("order.allocated", order.getId().toString());

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
        order.setStatus("CONFIRMED");
        order = orderRepository.save(order);
        kafkaProducerService.publish("order.confirmed", order.getId().toString());
        return toOrderResponse(order);
    }

    @Transactional
    @CacheEvict(value = "orders", allEntries = true)
    public OrderResponse shipOrder(UUID id, String carrierId, String trackingNumber) {
        NxOrder order = orderRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Order", id));
        order.setStatus("SHIPPED");
        order.setCarrierId(carrierId);
        order.setTrackingNumber(trackingNumber);
        order.setShippedAt(LocalDateTime.now());
        order = orderRepository.save(order);
        kafkaProducerService.publish("order.shipped", order.getId().toString());
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
            Address addr = addressRepository.save(Address.builder()
                    .tenantId(tenantId)
                    .addressLine1(request.getShippingAddress().getLine1())
                    .addressLine2(request.getShippingAddress().getLine2())
                    .city(request.getShippingAddress().getCity())
                    .state(request.getShippingAddress().getState())
                    .postalCode(request.getShippingAddress().getPincode())
                    .country("IN")
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

        kafkaProducerService.publish("order.modified", order.getId().toString());
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
            order.setStatus("SPLIT");
            orderRepository.save(order);
        } else {
            BigDecimal remainingSubtotal = remaining.stream()
                    .map(NxOrderItem::getTotalPrice)
                    .reduce(BigDecimal.ZERO, BigDecimal::add);
            order.setSubtotal(remainingSubtotal);
            order.setTotal(remainingSubtotal.add(order.getShippingCost()).add(order.getTaxAmount()));
            orderRepository.save(order);
        }

        kafkaProducerService.publish("order.split", id.toString());
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
            src.setStatus("MERGED");
            orderRepository.save(src);
        }

        target.setSubtotal(target.getSubtotal().add(additionalSubtotal));
        target.setTotal(target.getSubtotal().add(target.getShippingCost()).add(target.getTaxAmount()));
        target = orderRepository.save(target);

        kafkaProducerService.publish("order.merged", target.getId().toString());
        return toOrderResponse(target);
    }

    @Transactional
    @CacheEvict(value = {"orders", "inventory"}, allEntries = true)
    public OrderResponse cancelOrder(UUID id) {
        NxOrder order = orderRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Order", id));

        List<NxOrderItem> items = orderItemRepository.findByOrderId(id);
        for (NxOrderItem item : items) {
            if (item.getAllocatedNodeId() != null && item.getAllocatedQty() > 0) {
                inventoryService.releaseInventory(order.getTenantId(), item.getSku(),
                        item.getAllocatedNodeId(), item.getAllocatedQty());
            }
        }

        order.setStatus("CANCELLED");
        order = orderRepository.save(order);
        kafkaProducerService.publish("order.cancelled", order.getId().toString());
        return toOrderResponse(order);
    }

    private OrderResponse toOrderResponse(NxOrder order) {
        List<NxOrderItem> items = orderItemRepository.findByOrderId(order.getId());
        return OrderResponse.builder()
                .id(order.getId())
                .tenantId(order.getTenantId())
                .externalId(order.getExternalId())
                .channel(order.getChannel())
                .channelOrderId(order.getChannelOrderId())
                .customerId(order.getCustomerId())
                .status(order.getStatus())
                .subStatus(order.getSubStatus())
                .fulfillmentType(order.getFulfillmentType())
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
                .items(items.stream().map(this::toItemDto).collect(Collectors.toList()))
                .build();
    }

    private OrderResponse.OrderItemDto toItemDto(NxOrderItem item) {
        return OrderResponse.OrderItemDto.builder()
                .id(item.getId())
                .sku(item.getSku())
                .productName(item.getProductName())
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
