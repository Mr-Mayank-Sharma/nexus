package com.nexus.oms.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;
import java.util.UUID;

@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class ReturnResponse {

    private UUID id;
    private String rmaNumber;
    private UUID orderId;
    private String orderNumber;
    private String customerName;
    private String customerEmail;
    private String status;
    private String reason;
    private String grade;
    private String disposition;
    private BigDecimal refundAmount;
    private String refundStatus;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;
    private List<Map<String, Object>> items;
}
