package com.nexus.oms.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;
import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.UUID;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class InventoryReceiptRequest {

    @NotNull
    private UUID nodeId;

    @NotBlank
    private String receiptType;

    private String referenceNumber;

    @NotBlank
    private String sku;

    private String productName;

    @NotNull
    private Integer quantity;

    private BigDecimal unitCost;

    private String lotNumber;

    private LocalDateTime expiryDate;
}
