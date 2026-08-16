package com.nexus.oms.dto;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class AsnRequest {

    @NotBlank
    private String asnNumber;

    private UUID nodeId;

    private String supplierName;

    private String carrierCode;

    private String trackingNumber;

    private String purchaseOrderNumber;

    private LocalDateTime shipDate;

    private LocalDateTime expectedArrivalDate;

    @Valid
    @NotEmpty
    private List<AsnLineRequest> lines;

    @Data
    @NoArgsConstructor
    @AllArgsConstructor
    public static class AsnLineRequest {
        @NotBlank
        private String sku;

        private String productName;

        @NotNull
        private Integer expectedQty;

        private String lotNumber;

        private LocalDateTime expiryDate;
    }
}
