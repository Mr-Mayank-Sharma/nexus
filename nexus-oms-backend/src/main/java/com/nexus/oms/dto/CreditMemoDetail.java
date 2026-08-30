package com.nexus.oms.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;
import java.util.UUID;

/**
 * T-10: A credit memo with its invoice applications, for the returns page.
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class CreditMemoDetail {

    private UUID id;
    private String memoNumber;
    private UUID returnId;
    private String outcome;
    private BigDecimal amount;
    private BigDecimal taxDelta;
    private String status;
    private java.util.List<InvoiceApplication> invoiceApplications;

    @Data
    @NoArgsConstructor
    @AllArgsConstructor
    @Builder
    public static class InvoiceApplication {
        private UUID invoiceId;
        private BigDecimal amount;
        private BigDecimal taxAmount;
    }
}
