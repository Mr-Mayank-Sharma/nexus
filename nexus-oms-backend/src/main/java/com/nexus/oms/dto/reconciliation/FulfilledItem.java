package com.nexus.oms.dto.reconciliation;

import lombok.*;

import java.util.UUID;

@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class FulfilledItem {

    private UUID orderItemId;
    private String sku;
    private Integer quantity;
    private String location;
    private UUID nodeId;
}