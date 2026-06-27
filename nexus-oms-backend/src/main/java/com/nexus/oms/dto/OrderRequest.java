package com.nexus.oms.dto;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;
import java.math.BigDecimal;
import java.util.List;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class OrderRequest {

    @NotBlank
    private String channel;

    @NotBlank
    private String customerName;

    @NotBlank
    private String customerEmail;

    @NotNull
    @Valid
    private ShippingAddress shippingAddress;

    @NotEmpty
    @Valid
    private List<OrderItemRequest> items;

    @Data
    @NoArgsConstructor
    @AllArgsConstructor
    public static class ShippingAddress {
        private String line1;
        private String line2;
        private String city;
        private String state;
        private String pincode;
        private BigDecimal lat;
        private BigDecimal lon;
    }

    @Data
    @NoArgsConstructor
    @AllArgsConstructor
    public static class OrderItemRequest {
        @NotBlank
        private String sku;
        private String productName;
        @NotNull
        private Integer quantity;
        @NotNull
        private BigDecimal unitPrice;
    }
}
