package com.nexus.oms.dto;

import jakarta.validation.constraints.NotBlank;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class MfaVerificationRequest {

    @NotBlank
    private String mfaToken;

    @NotBlank
    private String totpCode;
}
