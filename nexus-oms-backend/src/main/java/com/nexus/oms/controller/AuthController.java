package com.nexus.oms.controller;

import com.nexus.oms.dto.*;
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
        return ResponseEntity.ok(ApiResponse.success(authService.register(request)));
    }

    @PostMapping("/mfa/verify")
    public ResponseEntity<ApiResponse<AuthResponse>> verifyMfa(@Valid @RequestBody MfaVerificationRequest request) {
        return ResponseEntity.ok(ApiResponse.success(authService.verifyMfa(request)));
    }

    @PostMapping("/sso/{provider}")
    public ResponseEntity<ApiResponse<AuthResponse>> ssoLogin(
            @PathVariable String provider,
            @Valid @RequestBody SsoLoginRequest request) {
        request.setProvider(provider);
        return ResponseEntity.ok(ApiResponse.success(authService.ssoLogin(request)));
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
        List<TenantResponse> tenants = authService.getAllTenants().stream()
                .map(settings -> TenantResponse.builder()
                        .id(settings.getTenantId().toString())
                        .name(settings.getCompanyName() != null ? settings.getCompanyName() : "Unnamed Company")
                        .logoUrl(settings.getCompanyLogo())
                        .isActive(true)
                        .plan("enterprise")
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
}
