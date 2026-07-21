package com.nexus.oms.dto;

import lombok.Data;
import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;

@Data
public class TransferOrderRequest {
    private String transferType;
    private UUID sourceNodeId;
    private UUID destinationNodeId;
    private String priority;
    private LocalDateTime expectedArrival;
    private String notes;
    private List<TransferOrderItemRequest> items;
}
