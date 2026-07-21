package com.nexus.oms.dto;

import lombok.Data;
import java.math.BigDecimal;

@Data
public class TransferOrderItemRequest {
    private String sku;
    private String productName;
    private Integer quantityRequested;
    private BigDecimal unitCost;
}
