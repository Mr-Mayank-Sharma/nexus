package com.nexus.oms.dto;

import jakarta.validation.Valid;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;
import java.math.BigDecimal;
import java.util.List;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class OrderModifyRequest {

    @Valid
    private OrderRequest.ShippingAddress shippingAddress;

    @Valid
    private List<OrderItemModify> items;

    private BigDecimal shippingCost;

    @Data
    @NoArgsConstructor
    @AllArgsConstructor
    public static class OrderItemModify {
        private String sku;
        private String productName;
        private Integer quantity;
        private BigDecimal unitPrice;
    }
}
