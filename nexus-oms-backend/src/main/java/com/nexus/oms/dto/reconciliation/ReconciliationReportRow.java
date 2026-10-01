package com.nexus.oms.dto.reconciliation;

import lombok.*;

import java.time.LocalDateTime;
import java.util.UUID;

@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class ReconciliationReportRow {

    private UUID orderId;
    private String orderNumber;
    private String matchType;
    private String resolution;
    private String reason;
    private String netsuiteSyncStatus;
    private Integer inventoryDelta;
    private LocalDateTime createdAt;
}