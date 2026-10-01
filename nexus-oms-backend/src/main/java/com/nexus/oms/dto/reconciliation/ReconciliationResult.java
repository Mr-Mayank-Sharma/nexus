package com.nexus.oms.dto.reconciliation;

import lombok.*;

import java.util.UUID;

@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class ReconciliationResult {

    private UUID reconciliationId;
    private UUID orderId;
    private String orderNumber;
    private MatchType matchType;
    private Resolution resolution;
    private Integer inventoryDelta;
    private String notes;
}