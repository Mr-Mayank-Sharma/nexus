package com.nexus.oms.dto.reconciliation;

import lombok.*;

@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class ShopifyFulfillmentLine {

    private String lineItemId;
    private String sku;
    private Integer quantity;
    private String locationId;
}