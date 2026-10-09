package com.nexus.oms.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/** Secret and otpauth:// URI (render as a QR code) for enrolling an authenticator app. */
@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class MfaEnrollmentResponse {
    private String secret;
    private String otpauthUri;
}
