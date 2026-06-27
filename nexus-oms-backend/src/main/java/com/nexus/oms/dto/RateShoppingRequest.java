package com.nexus.oms.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;
import java.math.BigDecimal;
import java.util.List;
import java.util.UUID;

@Data @NoArgsConstructor @AllArgsConstructor
public class RateShoppingRequest {
    private UUID orderId;

    @NotBlank
    private String fromZip;

    @NotBlank
    private String toZip;

    private String toCountry;

    @NotNull
    private BigDecimal totalWeightKg;

    private BigDecimal declaredValue;

    private Integer numPackages;

    private Boolean residential;

    private List<String> serviceLevels;
}
