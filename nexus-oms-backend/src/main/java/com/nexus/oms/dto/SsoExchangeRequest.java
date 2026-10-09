package com.nexus.oms.dto;

import jakarta.validation.constraints.NotBlank;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

/** One-time code from the SSO callback redirect, exchanged for tokens in a POST body. */
@Data
@NoArgsConstructor
@AllArgsConstructor
public class SsoExchangeRequest {
    @NotBlank
    private String code;
}
