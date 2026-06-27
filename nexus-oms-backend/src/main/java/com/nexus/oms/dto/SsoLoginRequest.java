package com.nexus.oms.dto;

import jakarta.validation.constraints.NotBlank;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class SsoLoginRequest {

    @NotBlank
    private String provider;

    @NotBlank
    private String idToken;

    private String tenantId;
}
