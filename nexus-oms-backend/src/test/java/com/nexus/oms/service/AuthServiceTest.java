package com.nexus.oms.service;

import com.nexus.oms.dto.*;
import com.nexus.oms.entity.CompanySettings;
import com.nexus.oms.entity.NxUser;
import com.nexus.oms.exception.BadRequestException;
import org.springframework.security.authentication.BadCredentialsException;
import com.nexus.oms.config.SsoProviderConfig;
import com.nexus.oms.repository.CompanySettingsRepository;
import com.nexus.oms.repository.RolePermissionRepository;
import com.nexus.oms.repository.UserRepository;
import com.nexus.oms.security.JwtTokenProvider;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.crypto.password.PasswordEncoder;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class AuthServiceTest {

    @Mock
    private UserRepository userRepository;
    @Mock
    private PasswordEncoder passwordEncoder;
    @Mock
    private JwtTokenProvider jwtTokenProvider;
    @Mock
    private CompanySettingsRepository companySettingsRepository;
    @Mock
    private RolePermissionRepository rolePermissionRepository;
    @Mock
    private SsoProviderConfig ssoProviderConfig;

    private final ObjectMapper objectMapper = new ObjectMapper();
    private AuthService authService;
    private NxUser testUser;
    private UUID tenantId;

    @BeforeEach
    void setUp() {
        authService = new AuthService(userRepository, passwordEncoder, jwtTokenProvider, companySettingsRepository, rolePermissionRepository, ssoProviderConfig, objectMapper);
        tenantId = UUID.randomUUID();
        testUser = NxUser.builder()
                .id(UUID.randomUUID())
                .tenantId(tenantId)
                .username("testuser")
                .passwordHash("encoded-pass")
                .role("VIEWER")
                .build();
    }

    @Test
    void testRegister_CreatesUserAndReturnsAuth() {
        RegisterRequest request = new RegisterRequest();
        request.setUsername("newuser");
        request.setPassword("Test1234!");
        request.setRole("VIEWER");

        when(userRepository.existsByUsername("newuser")).thenReturn(false);
        when(passwordEncoder.encode("Test1234!")).thenReturn("encoded-new");
        when(userRepository.save(any(NxUser.class))).thenAnswer(i -> {
            NxUser u = i.getArgument(0);
            u.setId(UUID.randomUUID());
            return u;
        });
        when(jwtTokenProvider.generateToken(eq("newuser"), eq("VIEWER"), any())).thenReturn("test-token");

        AuthResponse result = authService.register(request);

        assertNotNull(result.getAccessToken());
        assertEquals("test-token", result.getAccessToken());
        verify(userRepository).save(any(NxUser.class));
    }

    @Test
    void testRegister_WithExistingUsername_ThrowsException() {
        RegisterRequest request = new RegisterRequest();
        request.setUsername("existing");
        request.setPassword("Test1234!");
        request.setRole("VIEWER");

        when(userRepository.existsByUsername("existing")).thenReturn(true);

        assertThrows(BadRequestException.class, () -> authService.register(request));
        verify(userRepository, never()).save(any());
    }

    @Test
    void testRegister_WithInvalidRole_ThrowsException() {
        RegisterRequest request = new RegisterRequest();
        request.setUsername("newuser");
        request.setPassword("Test1234!");
        request.setRole("INVALID_ROLE");

        when(userRepository.existsByUsername("newuser")).thenReturn(false);

        assertThrows(BadRequestException.class, () -> authService.register(request));
        verify(userRepository, never()).save(any());
    }

    @Test
    void testAuthenticate_WithValidCredentials_ReturnsToken() {
        LoginRequest request = new LoginRequest();
        request.setUsername("testuser");
        request.setPassword("Test1234!");

        when(userRepository.findByUsername("testuser")).thenReturn(Optional.of(testUser));
        when(passwordEncoder.matches("Test1234!", "encoded-pass")).thenReturn(true);
        when(companySettingsRepository.findByTenantId(tenantId)).thenReturn(Optional.empty());
        when(jwtTokenProvider.generateToken("testuser", "VIEWER", tenantId)).thenReturn("jwt-token");

        AuthResponse result = authService.authenticate(request);

        assertNotNull(result.getAccessToken());
        assertEquals("jwt-token", result.getAccessToken());
    }

    @Test
    void testAuthenticate_WithInvalidPassword_ThrowsException() {
        LoginRequest request = new LoginRequest();
        request.setUsername("testuser");
        request.setPassword("wrong-pass");

        when(userRepository.findByUsername("testuser")).thenReturn(Optional.of(testUser));
        when(passwordEncoder.matches("wrong-pass", "encoded-pass")).thenReturn(false);

        assertThrows(BadCredentialsException.class, () -> authService.authenticate(request));
    }

    @Test
    void testAuthenticate_UserNotFound_ThrowsException() {
        LoginRequest request = new LoginRequest();
        request.setUsername("nonexistent");
        request.setPassword("pass");

        when(userRepository.findByUsername("nonexistent")).thenReturn(Optional.empty());

        assertThrows(BadCredentialsException.class, () -> authService.authenticate(request));
    }

    @Test
    void testSsoLogin_UnsupportedProvider_ThrowsException() {
        SsoLoginRequest request = new SsoLoginRequest();
        request.setProvider("unsupported");
        request.setIdToken("some-token");

        assertThrows(BadRequestException.class, () -> authService.ssoLogin(request));
    }

    @Test
    void testSsoLogin_ExistingUser_ReturnsToken() {
        SsoLoginRequest request = new SsoLoginRequest();
        request.setProvider("okta");
        String payload = "{\"email\":\"testuser@example.com\"}";
        String encodedPayload = java.util.Base64.getUrlEncoder().withoutPadding().encodeToString(payload.getBytes());
        String idToken = "header." + encodedPayload + ".signature";
        request.setIdToken(idToken);

        when(userRepository.findByUsername("testuser")).thenReturn(Optional.of(testUser));
        when(companySettingsRepository.findByTenantId(tenantId)).thenReturn(Optional.empty());
        when(rolePermissionRepository.findByTenantIdAndRole(tenantId, "VIEWER")).thenReturn(List.of());
        when(jwtTokenProvider.generateToken("testuser", "VIEWER", tenantId)).thenReturn("sso-jwt");

        AuthResponse result = authService.ssoLogin(request);

        assertEquals("sso-jwt", result.getAccessToken());
        verify(userRepository, never()).save(any());
    }

    @Test
    void testSsoLogin_NewUser_CreatesAndReturnsToken() {
        SsoLoginRequest request = new SsoLoginRequest();
        request.setProvider("google");
        request.setTenantId(tenantId.toString());
        String payload = "{\"email\":\"newperson@gmail.com\"}";
        String encodedPayload = java.util.Base64.getUrlEncoder().withoutPadding().encodeToString(payload.getBytes());
        String idToken = "header." + encodedPayload + ".signature";
        request.setIdToken(idToken);

        when(userRepository.findByUsername("newperson")).thenReturn(Optional.empty());
        when(passwordEncoder.encode(anyString())).thenReturn("encoded-sso-pass");
        when(userRepository.save(any(NxUser.class))).thenAnswer(i -> i.getArgument(0));
        when(companySettingsRepository.findByTenantId(any(UUID.class))).thenReturn(Optional.empty());
        when(rolePermissionRepository.findByTenantIdAndRole(any(UUID.class), eq("VIEWER"))).thenReturn(List.of());
        when(jwtTokenProvider.generateToken(eq("newperson"), eq("VIEWER"), any(UUID.class))).thenReturn("new-sso-jwt");

        AuthResponse result = authService.ssoLogin(request);

        assertEquals("new-sso-jwt", result.getAccessToken());
        verify(userRepository).save(any(NxUser.class));
    }

    @Test
    void testForgotPassword_ExistingUser_StoresToken() {
        com.nexus.oms.dto.ForgotPasswordRequest request = new com.nexus.oms.dto.ForgotPasswordRequest();
        request.setEmail("testuser");

        when(userRepository.findByUsername("testuser")).thenReturn(Optional.of(testUser));

        authService.forgotPassword(request);

        verify(userRepository).findByUsername("testuser");
    }

    @Test
    void testForgotPassword_NonExistentUser_DoesNotThrow() {
        com.nexus.oms.dto.ForgotPasswordRequest request = new com.nexus.oms.dto.ForgotPasswordRequest();
        request.setEmail("ghost");

        when(userRepository.findByUsername("ghost")).thenReturn(Optional.empty());
        when(userRepository.findAll()).thenReturn(List.of());

        assertDoesNotThrow(() -> authService.forgotPassword(request));
    }

    @Test
    void testResetPassword_InvalidToken_ThrowsException() {
        ResetPasswordRequest request = new ResetPasswordRequest();
        request.setToken("nonexistent-token");
        request.setNewPassword("NewPass123!");

        assertThrows(BadRequestException.class, () -> authService.resetPassword(request));
    }

    @SuppressWarnings("unchecked")
    @Test
    void testResetPassword_ValidToken_UpdatesPassword() throws Exception {
        com.nexus.oms.dto.ForgotPasswordRequest fpReq = new com.nexus.oms.dto.ForgotPasswordRequest();
        fpReq.setEmail("testuser");
        when(userRepository.findByUsername("testuser")).thenReturn(Optional.of(testUser));
        authService.forgotPassword(fpReq);

        java.lang.reflect.Field field = AuthService.class.getDeclaredField("resetTokens");
        field.setAccessible(true);
        java.util.Map<String, ?> tokens =
                (java.util.Map<String, ?>) field.get(authService);
        String token = tokens.keySet().iterator().next();

        when(passwordEncoder.encode("NewPass123!")).thenReturn("encoded-new-pass");
        when(userRepository.findByUsername("testuser")).thenReturn(Optional.of(testUser));

        ResetPasswordRequest rpReq = new ResetPasswordRequest();
        rpReq.setToken(token);
        rpReq.setNewPassword("NewPass123!");

        assertDoesNotThrow(() -> authService.resetPassword(rpReq));
        verify(userRepository).save(any(NxUser.class));
    }

    @Test
    void testGetAllTenants_ReturnsList() {
        CompanySettings cs = CompanySettings.builder()
                .companyName("TestCo")
                .build();
        cs.setTenantId(tenantId);
        when(companySettingsRepository.findAll()).thenReturn(List.of(cs));

        List<CompanySettings> result = authService.getAllTenants();

        assertEquals(1, result.size());
        assertEquals("TestCo", result.get(0).getCompanyName());
    }

    @Test
    void testGetAllTenants_EmptyList() {
        when(companySettingsRepository.findAll()).thenReturn(List.of());

        List<CompanySettings> result = authService.getAllTenants();

        assertTrue(result.isEmpty());
    }

    @Test
    void testGenerateSsoAuthorizationUrl_UnsupportedProvider_ThrowsException() {
        assertThrows(BadRequestException.class,
                () -> authService.generateSsoAuthorizationUrl("unsupported", tenantId.toString()));
    }

    @Test
    void testGenerateSsoAuthorizationUrl_ConfiguredProvider_ReturnsUrl() {
        SsoProviderConfig.Provider cfg = new SsoProviderConfig.Provider();
        cfg.setClientId("client-123");
        cfg.setAuthorizeUrl("https://auth.example.com/authorize");
        cfg.setRedirectUri("http://localhost:8080/api/v1/auth/sso/{provider}/callback");
        cfg.setScopes("openid,email");

        when(ssoProviderConfig.getProvider("okta")).thenReturn(cfg);

        String url = authService.generateSsoAuthorizationUrl("okta", tenantId.toString());

        assertNotNull(url);
        assertTrue(url.contains("client_id=client-123"));
        assertTrue(url.contains("response_type=code"));
        assertTrue(url.contains("scope=openid email"));
    }

    @Test
    void testGenerateSsoAuthorizationUrl_UnconfiguredProvider_ThrowsException() {
        when(ssoProviderConfig.getProvider("auth0")).thenReturn(null);

        assertThrows(BadRequestException.class,
                () -> authService.generateSsoAuthorizationUrl("auth0", tenantId.toString()));
    }
}
