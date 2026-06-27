package com.nexus.oms.dto;

import com.nexus.oms.entity.NxOrderAllocation;
import com.nexus.oms.entity.NxFulfillmentException;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;

@Data @NoArgsConstructor @AllArgsConstructor @Builder
public class AllocationResult {
    private UUID orderId;
    private String strategy;
    private String status;
    private List<NxOrderAllocation> allocations;
    private LocalDateTime estimatedDeliveryDate;
    private BigDecimal confidenceScore;
    private BigDecimal totalCost;
    private List<NxFulfillmentException> exceptions;
    private String explanation;
    private Long executionTimeMs;
}
