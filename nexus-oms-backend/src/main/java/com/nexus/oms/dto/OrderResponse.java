package com.nexus.oms.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;

@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class OrderResponse {

    private UUID id;
    private UUID tenantId;
    private String externalId;
    private String channel;
    private String channelOrderId;
    private UUID customerId;
    private String customerName;
    private String customerEmail;
    private String status;
    private String subStatus;
    private String fulfillmentType;
    private Object shipTo;
    private Object billingAddress;
    private String currency;
    private BigDecimal subtotal;
    private BigDecimal shippingCost;
    private BigDecimal taxAmount;
    private BigDecimal total;
    private String paymentStatus;
    private String paymentReference;
    private UUID allocatedNode;
    private String allocationRule;
    private BigDecimal allocationConfidence;
    private String carrierId;
    private String trackingNumber;
    private String labelUrl;
    private LocalDateTime promisedDelivery;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;
    private LocalDateTime shippedAt;
    private LocalDateTime deliveredAt;
    private Object metadata;
    private List<OrderItemDto> items;

    @Data
    @NoArgsConstructor
    @AllArgsConstructor
    @Builder
    public static class OrderItemDto {
        private UUID id;
        private String sku;
        private String productName;
        private Integer quantity;
        private BigDecimal unitPrice;
        private BigDecimal totalPrice;
        private UUID allocatedNodeId;
        private Integer allocatedQty;
    }
}
