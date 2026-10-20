package com.nexus.oms.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;
import java.util.UUID;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class CycleCountRequest {

    @NotNull
    private UUID nodeId;

    @NotBlank
    private String sku;

    private String productName;

    @NotNull
    private Integer expectedQty;

    private String notes;
}
