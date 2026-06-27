package com.nexus.oms.dto;

import jakarta.validation.constraints.NotBlank;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class BigCommerceConfigRequest {

    @NotBlank
    private String storeHash;

    @NotBlank
    private String accessToken;

    private String clientId;

    private String apiPath;

    private Boolean autoSyncOrders;

    private Boolean autoSyncInventory;

    private Integer syncIntervalMinutes;
}
