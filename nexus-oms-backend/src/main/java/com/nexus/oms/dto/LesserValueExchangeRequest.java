package com.nexus.oms.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;
import java.util.UUID;

/**
 * T-10: Request to process a lesser-value exchange.
 * Customer exchanges for a lower-cost item; the difference is refunded as
 * store credit. Produces a single credit memo with two correctly-applied
 * invoices (exchange + store credit).
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class LesserValueExchangeRequest {

    private UUID returnId;
    private UUID exchangeInvoiceId;   // invoice for the new (lower-cost) item
    private BigDecimal exchangeValue; // value of the new item
    private UUID sourceLocationId;
    private UUID targetLocationId;
}
