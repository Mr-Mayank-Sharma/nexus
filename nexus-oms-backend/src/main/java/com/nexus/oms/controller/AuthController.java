package com.nexus.oms.controller;

import com.nexus.oms.dto.*;
import com.nexus.oms.security.TenantAwarePrincipal;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import com.nexus.oms.service.AuthService;
import com.nexus.oms.service.CompanySettingsService;
import jakarta.validation.Valid;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.io.IOException;
import java.util.List;
import java.util.stream.Collectors;

@RestController
@RequestMapping("/auth")
public class AuthController {

    private final AuthService authService;
    private final CompanySettingsService companySettingsService;

    @org.springframework.beans.factory.annotation.Value("${app.registration.enabled:true}")
    private boolean registrationEnabled;

    public AuthController(AuthService authService, CompanySettingsService companySettingsService) {
        this.authService = authService;
        this.companySettingsService = companySettingsService;
    }

    @PostMapping("/login")
    public ResponseEntity<ApiResponse<AuthResponse>> login(@Valid @RequestBody LoginRequest request) {
        return ResponseEntity.ok(ApiResponse.success(authService.authenticate(request)));
    }

    @PostMapping("/register")
    public ResponseEntity<ApiResponse<AuthResponse>> register(@Valid @RequestBody RegisterRequest request) {
        if (!registrationEnabled) {
            // Self-registration is disabled in secured deployments — admins provision
            // accounts (or an invite flow) instead of allowing anonymous sign-ups.
            return ResponseEntity.status(org.springframework.http.HttpStatus.FORBIDDEN)
                    .body(ApiResponse.error("Registration is disabled. Contact your administrator."));
        }
        return ResponseEntity.ok(ApiResponse.success(authService.register(request)));
    }

    @PostMapping("/mfa/verify")
    public ResponseEntity<ApiResponse<AuthResponse>> verifyMfa(@Valid @RequestBody MfaVerificationRequest request) {
        return ResponseEntity.ok(ApiResponse.success(authService.verifyMfa(request)));
    }

    @GetMapping("/mfa/status")
    public ResponseEntity<ApiResponse<java.util.Map<String, Object>>> mfaStatus() {
        String username = currentUsernameOrNull();
        if (username == null) {
            return unauthorized();
        }
        return ResponseEntity.ok(ApiResponse.success(authService.mfaStatus(username)));
    }

    @PostMapping("/mfa/enroll")
    public ResponseEntity<ApiResponse<MfaEnrollmentResponse>> beginMfaEnrollment() {
        String username = currentUsernameOrNull();
        if (username == null) {
            return unauthorized();
        }
        return ResponseEntity.ok(ApiResponse.success(authService.beginMfaEnrollment(username)));
    }

    @PostMapping("/mfa/activate")
    public ResponseEntity<ApiResponse<List<String>>> activateMfa(@Valid @RequestBody MfaCodeRequest request) {
        String username = currentUsernameOrNull();
        if (username == null) {
            return unauthorized();
        }
        return ResponseEntity.ok(ApiResponse.success(authService.activateMfa(username, request.getCode()),
                "MFA enabled. Store these recovery codes somewhere safe; they are shown once."));
    }

    @PostMapping("/mfa/disable")
    public ResponseEntity<ApiResponse<Void>> disableMfa(@Valid @RequestBody MfaCodeRequest request) {
        String username = currentUsernameOrNull();
        if (username == null) {
            return unauthorized();
        }
        authService.disableMfa(username, request.getCode());
        return ResponseEntity.ok(ApiResponse.success(null, "MFA disabled"));
    }

    @PostMapping("/sso/exchange")
    public ResponseEntity<ApiResponse<AuthResponse>> exchangeSsoCode(@Valid @RequestBody SsoExchangeRequest request) {
        return ResponseEntity.ok(ApiResponse.success(authService.exchangeSsoCode(request.getCode())));
    }

    @PostMapping("/sso/{provider}")
    public ResponseEntity<ApiResponse<AuthResponse>> ssoLogin(
            @PathVariable String provider,
            @Valid @RequestBody SsoLoginRequest request) {
        request.setProvider(provider);
        return ResponseEntity.ok(ApiResponse.success(authService.ssoLogin(request)));
    }

    @PostMapping("/refresh")
    public ResponseEntity<ApiResponse<AuthResponse>> refresh(@Valid @RequestBody RefreshTokenRequest request) {
        return ResponseEntity.ok(ApiResponse.success(authService.refreshToken(request.getRefreshToken())));
    }

    @PostMapping("/forgot-password")
    public ResponseEntity<ApiResponse<Void>> forgotPassword(@Valid @RequestBody ForgotPasswordRequest request) {
        authService.forgotPassword(request);
        return ResponseEntity.ok(ApiResponse.success(null, "Password reset link sent to your email"));
    }

    @PostMapping("/reset-password")
    public ResponseEntity<ApiResponse<Void>> resetPassword(@Valid @RequestBody ResetPasswordRequest request) {
        authService.resetPassword(request);
        return ResponseEntity.ok(ApiResponse.success(null, "Password has been reset successfully"));
    }

    @GetMapping("/tenants")
    public ResponseEntity<ApiResponse<List<TenantResponse>>> listTenants() {
        // Anonymous callers get an empty list; signed-in users see only their own tenant.
        List<TenantResponse> tenants = authService.getTenantsFor(currentPrincipal().map(TenantAwarePrincipal::tenantId).orElse(null)).stream()
                .map(settings -> TenantResponse.builder()
                        .id(settings.getTenantId().toString())
                        .name(settings.getCompanyName() != null ? settings.getCompanyName() : "Unnamed Company")
                        .logoUrl(settings.getCompanyLogo())
                        .isActive(true)
                        .build())
                .collect(Collectors.toList());
        return ResponseEntity.ok(ApiResponse.success(tenants));
    }

    @GetMapping("/sso/providers")
    public ResponseEntity<ApiResponse<List<String>>> getSsoProviders() {
        return ResponseEntity.ok(ApiResponse.success(List.of("okta", "auth0", "google", "microsoft")));
    }

    @GetMapping("/sso/{provider}/authorize")
    public void authorizeSso(
            @PathVariable String provider,
            @RequestParam(required = false) String tenantId,
            HttpServletResponse response) throws IOException {
        String redirectUrl = authService.generateSsoAuthorizationUrl(provider, tenantId);
        response.sendRedirect(redirectUrl);
    }

    @GetMapping("/sso/{provider}/callback")
    public void handleSsoCallback(
            @PathVariable String provider,
            @RequestParam String code,
            @RequestParam String state,
            HttpServletResponse response) throws IOException {
        String frontendUrl = authService.handleSsoCallback(provider, code, state);
        response.sendRedirect(frontendUrl);
    }

    /** /auth/** is public at the filter level, so endpoints that need a user check it here. */
    private static java.util.Optional<TenantAwarePrincipal> currentPrincipal() {
        Authentication auth = SecurityContextHolder.getContext().getAuthentication();
        if (auth != null && auth.getPrincipal() instanceof TenantAwarePrincipal principal) {
            return java.util.Optional.of(principal);
        }
        return java.util.Optional.empty();
    }

    private static String currentUsernameOrNull() {
        return currentPrincipal().map(TenantAwarePrincipal::username).orElse(null);
    }

    private static <T> ResponseEntity<ApiResponse<T>> unauthorized() {
        return ResponseEntity.status(org.springframework.http.HttpStatus.UNAUTHORIZED)
                .body(ApiResponse.error("Sign in to manage MFA"));
    }
}
