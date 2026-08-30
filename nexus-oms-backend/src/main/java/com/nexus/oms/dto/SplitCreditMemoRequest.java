package com.nexus.oms.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;
import java.util.List;
import java.util.UUID;

/**
 * T-10: Request to split a return claim into multiple credit memos.
 * Each transaction in the claim becomes its own credit memo.
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class SplitCreditMemoRequest {

    private UUID returnId;
    private List<TransactionSplit> transactions;

    @Data
    @NoArgsConstructor
    @AllArgsConstructor
    @Builder
    public static class TransactionSplit {
        private UUID invoiceId;
        private BigDecimal amount;
        private BigDecimal taxAmount;
        private String outcome;   // REFUND | STORE_CREDIT | LIKE_FOR_LIKE_EXCHANGE | LESSER_VALUE_EXCHANGE
    }
}
