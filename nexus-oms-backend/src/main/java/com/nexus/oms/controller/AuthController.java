package com.nexus.oms.controller;

import com.nexus.oms.dto.*;
import com.nexus.oms.service.AuthService;
import com.nexus.oms.service.CompanySettingsService;
import jakarta.validation.Valid;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

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
}
