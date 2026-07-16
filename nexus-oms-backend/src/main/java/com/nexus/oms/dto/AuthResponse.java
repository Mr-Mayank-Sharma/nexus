package com.nexus.oms.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import java.util.List;

@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class AuthResponse {

    private String accessToken;
    private String tokenType;
    private long expiresIn;
    private String role;
    private String username;
    private String tenantId;
    private String tenantName;
    private boolean mfaRequired;
    private String mfaToken;
    private String email;
    private String fullName;
    private boolean passwordResetRequired;
    private String ssoProvider;
    private List<String> permissions;
}
