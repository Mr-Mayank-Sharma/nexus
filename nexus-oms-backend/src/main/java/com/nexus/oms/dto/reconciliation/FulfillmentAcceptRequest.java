package com.nexus.oms.dto.reconciliation;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotNull;
import lombok.*;

import java.util.UUID;

@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class FulfillmentAcceptRequest {

    @NotNull
    private UUID orderId;

    @Valid
    @NotNull
    private ShopifyFulfillment fulfillment;
}