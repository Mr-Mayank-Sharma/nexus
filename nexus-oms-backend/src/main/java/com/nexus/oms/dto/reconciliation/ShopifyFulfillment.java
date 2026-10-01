package com.nexus.oms.dto.reconciliation;

import lombok.*;

import java.util.List;

@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class ShopifyFulfillment {

    private String shopifyFulfillmentId;
    private String status;
    private String createdAt;
    private List<ShopifyFulfillmentLine> lineItems;
}