package com.nexus.oms.dto;

import jakarta.validation.constraints.NotBlank;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;
import java.util.Map;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class IntegrationStoreRequest {

    @NotBlank
    private String storeCode;

    @NotBlank
    private String storeName;

    @NotBlank
    private String platform;

    private String platformType;

    private String currency;

    private String defaultLocale;

    private String timezone;

    private String externalStoreId;

    private String externalDomain;

    private Map<String, String> settings;

    private Boolean isActive;
}
