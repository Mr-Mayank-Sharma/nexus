package com.nexus.oms.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.nexus.oms.config.SsoProviderConfig;
import com.nexus.oms.dto.*;
import com.nexus.oms.entity.CompanySettings;
import com.nexus.oms.entity.NxAuthToken;
import com.nexus.oms.entity.NxUser;
import com.nexus.oms.entity.NxUserIdentity;
import com.nexus.oms.exception.BadRequestException;
import com.nexus.oms.integration.core.CredentialVault;
import com.nexus.oms.repository.AuthTokenRepository;
import com.nexus.oms.repository.CompanySettingsRepository;
import com.nexus.oms.repository.RolePermissionRepository;
import com.nexus.oms.repository.UserIdentityRepository;
import com.nexus.oms.repository.UserRepository;
import com.nexus.oms.security.JwtTokenProvider;
import com.nexus.oms.service.auth.AuthTokenStore;
import com.nexus.oms.service.auth.IdTokenVerifier;
import com.nexus.oms.service.auth.IdTokenVerifier.VerifiedIdentity;
import com.nexus.oms.service.auth.PasswordResetMailer;
import com.nexus.oms.service.auth.TotpService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;
import org.springframework.security.authentication.BadCredentialsException;
import org.springframework.security.crypto.password.PasswordEncoder;

import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.ByteBuffer;
import java.util.*;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class AuthServiceTest {

    @Mock private UserRepository userRepository;
    @Mock private PasswordEncoder passwordEncoder;
    @Mock private JwtTokenProvider jwtTokenProvider;
    @Mock private CompanySettingsRepository companySettingsRepository;
    @Mock private RolePermissionRepository rolePermissionRepository;
    @Mock private SsoProviderConfig ssoProviderConfig;
    @Mock private AuthTokenRepository authTokenRepository;
    @Mock private IdTokenVerifier idTokenVerifier;
    @Mock private UserIdentityRepository identityRepository;
    @Mock private PasswordResetMailer resetMailer;
    @Mock private HttpClient httpClient;

    private final ObjectMapper objectMapper = new ObjectMapper();
    private final TotpService totpService = new TotpService();
    private final CredentialVault credentialVault = new CredentialVault();
    private final Map<String, NxAuthToken> tokenRows = new HashMap<>();

    private AuthService authService;
    private NxUser testUser;
    private UUID tenantId;

    @BeforeEach
    void setUp() {
        // In-memory stand-in for nx_auth_tokens so the real AuthTokenStore logic is exercised.
        when(authTokenRepository.save(any(NxAuthToken.class))).thenAnswer(i -> {
            NxAuthToken t = i.getArgument(0);
            tokenRows.put(t.getTokenType() + "|" + t.getTokenHash(), t);
            return t;
        });
        when(authTokenRepository.findByTokenTypeAndTokenHash(anyString(), anyString()))
                .thenAnswer(i -> Optional.ofNullable(tokenRows.get(i.getArgument(0) + "|" + i.getArgument(1))));
        doAnswer(i -> {
            NxAuthToken t = i.getArgument(0);
            tokenRows.remove(t.getTokenType() + "|" + t.getTokenHash());
            return null;
        }).when(authTokenRepository).delete(any(NxAuthToken.class));
        when(authTokenRepository.deleteByTypeAndUsername(anyString(), anyString())).thenAnswer(i -> {
            tokenRows.values().removeIf(t -> t.getTokenType().equals(i.getArgument(0)) && i.getArgument(1).equals(t.getUsername()));
            return 0;
        });

        authService = new AuthService(userRepository, passwordEncoder, jwtTokenProvider, companySettingsRepository,
                rolePermissionRepository, ssoProviderConfig, objectMapper, new AuthTokenStore(authTokenRepository),
                totpService, idTokenVerifier, identityRepository, credentialVault, resetMailer, httpClient);

        tenantId = UUID.randomUUID();
        testUser = NxUser.builder()
                .id(UUID.randomUUID())
                .tenantId(tenantId)
                .username("testuser")
                .email("test@acme.com")
                .passwordHash("encoded-pass")
                .role("VIEWER")
                .build();
        when(userRepository.findByUsername("testuser")).thenReturn(Optional.of(testUser));
        when(userRepository.findById(testUser.getId())).thenReturn(Optional.of(testUser));
        when(companySettingsRepository.findByTenantId(any())).thenReturn(Optional.empty());
        when(rolePermissionRepository.findByTenantIdAndRole(any(), any())).thenReturn(List.of());
        when(jwtTokenProvider.generateToken(eq("testuser"), eq("VIEWER"), eq(tenantId))).thenReturn("jwt-testuser");
        when(userRepository.save(any(NxUser.class))).thenAnswer(i -> {
            NxUser u = i.getArgument(0);
            if (u.getId() == null) u.setId(UUID.randomUUID());
            return u;
        });
    }

    // ------------------------------------------------------------------ registration + login

    @Test
    void register_CreatesUserAndReturnsAuth() {
        RegisterRequest request = new RegisterRequest();
        request.setUsername("newuser");
        request.setPassword("Test1234!");
        request.setRole("VIEWER");
        when(userRepository.existsByUsername("newuser")).thenReturn(false);
        when(passwordEncoder.encode("Test1234!")).thenReturn("encoded-new");
        when(jwtTokenProvider.generateToken(eq("newuser"), eq("VIEWER"), any())).thenReturn("test-token");

        AuthResponse result = authService.register(request);

        assertEquals("test-token", result.getAccessToken());
        verify(userRepository).save(any(NxUser.class));
        ArgumentCaptor<CompanySettings> settings = ArgumentCaptor.forClass(CompanySettings.class);
        verify(companySettingsRepository).save(settings.capture());
        assertEquals("newuser", settings.getValue().getCompanyName(), "company name defaults to the username");
    }

    @Test
    void register_WithExistingUsername_Throws() {
        RegisterRequest request = new RegisterRequest();
        request.setUsername("existing");
        request.setPassword("Test1234!");
        when(userRepository.existsByUsername("existing")).thenReturn(true);

        assertThrows(BadRequestException.class, () -> authService.register(request));
        verify(userRepository, never()).save(any());
    }

    @Test
    void register_WithInvalidRole_Throws() {
        RegisterRequest request = new RegisterRequest();
        request.setUsername("newuser");
        request.setPassword("Test1234!");
        request.setRole("INVALID_ROLE");

        assertThrows(BadRequestException.class, () -> authService.register(request));
    }

    @Test
    void authenticate_WithValidCredentials_ReturnsToken() {
        when(passwordEncoder.matches("pw", "encoded-pass")).thenReturn(true);

        AuthResponse result = authService.authenticate(new LoginRequest("testuser", "pw", null));

        assertEquals("jwt-testuser", result.getAccessToken());
        assertFalse(result.isMfaRequired());
        assertEquals(900_000L, result.getExpiresIn(), "expiresIn must match the access-token lifetime");
    }

    @Test
    void authenticate_WithInvalidPassword_Throws() {
        when(passwordEncoder.matches("bad", "encoded-pass")).thenReturn(false);

        assertThrows(BadCredentialsException.class, () -> authService.authenticate(new LoginRequest("testuser", "bad", null)));
    }

    @Test
    void authenticate_UnknownUser_Throws() {
        when(userRepository.findByUsername("ghost")).thenReturn(Optional.empty());

        assertThrows(BadCredentialsException.class, () -> authService.authenticate(new LoginRequest("ghost", "pw", null)));
    }

    @Test
    void authenticate_TenantRequiresMfaButUserNotEnrolled_FlagsEnrollment() {
        when(passwordEncoder.matches("pw", "encoded-pass")).thenReturn(true);
        CompanySettings settings = new CompanySettings();
        settings.setSecurityPolicy("{\"mfaEnabled\":true}");
        when(companySettingsRepository.findByTenantId(tenantId)).thenReturn(Optional.of(settings));

        AuthResponse result = authService.authenticate(new LoginRequest("testuser", "pw", null));

        assertTrue(result.isMfaEnrollmentRequired());
    }

    // ------------------------------------------------------------------ MFA

    @Test
    void mfa_EnrolActivateAndLogin_WithRealTotp() throws Exception {
        MfaEnrollmentResponse enrol = authService.beginMfaEnrollment("testuser");
        assertTrue(enrol.getOtpauthUri().startsWith("otpauth://totp/"));
        assertNotEquals(enrol.getSecret(), testUser.getMfaSecret(), "secret must be stored encrypted");

        List<String> recovery = authService.activateMfa("testuser", totpNow(enrol.getSecret()));
        assertEquals(10, recovery.size());
        assertTrue(testUser.isMfaEnabled());

        when(passwordEncoder.matches("pw", "encoded-pass")).thenReturn(true);
        AuthResponse challenge = authService.authenticate(new LoginRequest("testuser", "pw", null));
        assertTrue(challenge.isMfaRequired());
        assertNull(challenge.getAccessToken(), "no access token before the second factor");

        AuthResponse result = authService.verifyMfa(new MfaVerificationRequest(challenge.getMfaToken(), totpNow(enrol.getSecret())));
        assertEquals("jwt-testuser", result.getAccessToken());

        assertThrows(BadRequestException.class,
                () -> authService.verifyMfa(new MfaVerificationRequest(challenge.getMfaToken(), totpNow(enrol.getSecret()))),
                "a challenge can be completed only once");
    }

    @Test
    void mfa_CodeDerivedFromUsernameAndClock_IsRejected() throws Exception {
        String secret = enableMfaForTestUser();
        String challenge = loginChallenge();
        int legacy = Math.abs(("testuser".hashCode() + (int) (System.currentTimeMillis() / 30000)) % 1000000);
        String legacyCode = String.format("%06d", legacy);
        assumeDiffers(legacyCode, totpNow(secret));

        assertThrows(BadRequestException.class,
                () -> authService.verifyMfa(new MfaVerificationRequest(challenge, legacyCode)));
    }

    @Test
    void mfa_LocksAfterFiveWrongCodes() throws Exception {
        String secret = enableMfaForTestUser();
        String challenge = loginChallenge();

        for (int i = 0; i < AuthService.MAX_MFA_ATTEMPTS; i++) {
            assertThrows(BadRequestException.class,
                    () -> authService.verifyMfa(new MfaVerificationRequest(challenge, "000000".equals(totpNow(secret)) ? "111111" : "000000")));
        }
        assertThrows(BadRequestException.class,
                () -> authService.verifyMfa(new MfaVerificationRequest(challenge, totpNow(secret))),
                "even the right code fails once the challenge is locked");
    }

    @Test
    void mfa_RecoveryCodeWorksExactlyOnce() throws Exception {
        MfaEnrollmentResponse enrol = authService.beginMfaEnrollment("testuser");
        String recovery = authService.activateMfa("testuser", totpNow(enrol.getSecret())).get(0);

        assertNotNull(authService.verifyMfa(new MfaVerificationRequest(loginChallenge(), recovery)).getAccessToken());
        String second = loginChallenge();
        assertThrows(BadRequestException.class, () -> authService.verifyMfa(new MfaVerificationRequest(second, recovery)));
    }

    @Test
    void mfaStatus_ReflectsEnrolmentAndTenantPolicy() throws Exception {
        assertEquals(false, authService.mfaStatus("testuser").get("enabled"));
        CompanySettings settings = new CompanySettings();
        settings.setSecurityPolicy("{\"mfaEnabled\":true}");
        when(companySettingsRepository.findByTenantId(tenantId)).thenReturn(Optional.of(settings));
        enableMfaForTestUser();

        var status = authService.mfaStatus("testuser");
        assertEquals(true, status.get("enabled"));
        assertEquals(true, status.get("requiredByTenant"));
        assertEquals(10, status.get("recoveryCodesRemaining"));
    }

    @Test
    void mfa_ActivateWithWrongCode_Throws() {
        authService.beginMfaEnrollment("testuser");

        assertThrows(BadRequestException.class, () -> authService.activateMfa("testuser", "123456x"));
        assertFalse(testUser.isMfaEnabled());
    }

    @Test
    void verifyMfa_UnknownSession_Throws() {
        assertThrows(BadRequestException.class,
                () -> authService.verifyMfa(new MfaVerificationRequest("nope", "123456")));
    }

    // ------------------------------------------------------------------ SSO

    @Test
    void ssoLogin_UnsupportedProvider_Throws() {
        assertThrows(BadRequestException.class,
                () -> authService.ssoLogin(new SsoLoginRequest("myspace", "tok", null)));
    }

    @Test
    void ssoLogin_TokenThatFailsVerification_IsRejected() {
        when(idTokenVerifier.verify(eq("google"), anyString()))
                .thenThrow(new IdTokenVerifier.InvalidIdTokenException("bad signature"));

        assertThrows(BadCredentialsException.class,
                () -> authService.ssoLogin(new SsoLoginRequest("google", "header.payload.forged", null)));
        verify(userRepository, never()).save(any());
    }

    @Test
    void ssoLogin_ExistingIdentityLink_ReturnsLinkedUser() {
        when(idTokenVerifier.verify("google", "tok")).thenReturn(new VerifiedIdentity("sub-1", "other@x.com", false));
        when(identityRepository.findByProviderAndSubject("google", "sub-1"))
                .thenReturn(Optional.of(NxUserIdentity.builder().userId(testUser.getId()).build()));

        assertEquals("jwt-testuser", authService.ssoLogin(new SsoLoginRequest("google", "tok", null)).getAccessToken());
    }

    @Test
    void ssoLogin_VerifiedEmailWithSingleMatch_LinksAndLogsIn() {
        when(idTokenVerifier.verify("google", "tok")).thenReturn(new VerifiedIdentity("sub-2", "test@acme.com", true));
        when(userRepository.findByEmailIgnoreCase("test@acme.com")).thenReturn(List.of(testUser));

        AuthResponse result = authService.ssoLogin(new SsoLoginRequest("google", "tok", null));

        assertEquals("jwt-testuser", result.getAccessToken());
        ArgumentCaptor<NxUserIdentity> link = ArgumentCaptor.forClass(NxUserIdentity.class);
        verify(identityRepository).save(link.capture());
        assertEquals("sub-2", link.getValue().getSubject());
    }

    @Test
    void ssoLogin_SameLocalPartDifferentDomain_DoesNotReachOtherAccount() {
        // Previously username = email.split("@")[0], so admin@attacker.com became user "admin".
        NxUser admin = NxUser.builder().id(UUID.randomUUID()).username("admin").email("admin@victim.com")
                .role("ADMIN").tenantId(UUID.randomUUID()).passwordHash("x").build();
        when(userRepository.findByUsername("admin")).thenReturn(Optional.of(admin));
        when(idTokenVerifier.verify("google", "tok")).thenReturn(new VerifiedIdentity("sub-3", "admin@attacker.com", true));
        when(userRepository.findByEmailIgnoreCase("admin@attacker.com")).thenReturn(List.of());

        assertThrows(BadCredentialsException.class, () -> authService.ssoLogin(new SsoLoginRequest("google", "tok", null)));
        verify(jwtTokenProvider, never()).generateToken(eq("admin"), any(), any());
    }

    @Test
    void ssoLogin_UnverifiedEmail_IsNotLinked() {
        when(idTokenVerifier.verify("google", "tok")).thenReturn(new VerifiedIdentity("sub-4", "test@acme.com", false));
        when(userRepository.findByEmailIgnoreCase("test@acme.com")).thenReturn(List.of(testUser));

        assertThrows(BadCredentialsException.class, () -> authService.ssoLogin(new SsoLoginRequest("google", "tok", null)));
        verify(identityRepository, never()).save(any());
    }

    @Test
    void ssoLogin_AmbiguousEmail_IsRejected() {
        NxUser twin = NxUser.builder().id(UUID.randomUUID()).username("twin").email("test@acme.com")
                .role("VIEWER").tenantId(UUID.randomUUID()).passwordHash("x").build();
        when(idTokenVerifier.verify("google", "tok")).thenReturn(new VerifiedIdentity("sub-5", "test@acme.com", true));
        when(userRepository.findByEmailIgnoreCase("test@acme.com")).thenReturn(List.of(testUser, twin));

        assertThrows(BadCredentialsException.class, () -> authService.ssoLogin(new SsoLoginRequest("google", "tok", null)));
    }

    @Test
    void ssoLogin_UnknownIdentity_IgnoresCallerSuppliedTenant() {
        UUID victimTenant = UUID.randomUUID();
        when(idTokenVerifier.verify("google", "tok")).thenReturn(new VerifiedIdentity("sub-6", "new@acme.com", true));
        when(userRepository.findByEmailIgnoreCase("new@acme.com")).thenReturn(List.of());

        assertThrows(BadCredentialsException.class,
                () -> authService.ssoLogin(new SsoLoginRequest("google", "tok", victimTenant.toString())));
        verify(userRepository, never()).save(any());
    }

    @Test
    void ssoLogin_AutoProvisionTenant_CreatesViewer() {
        UUID provisionTenant = UUID.randomUUID();
        SsoProviderConfig.Provider cfg = new SsoProviderConfig.Provider();
        cfg.setAutoProvisionTenantId(provisionTenant.toString());
        when(ssoProviderConfig.getProvider("google")).thenReturn(cfg);
        when(idTokenVerifier.verify("google", "tok")).thenReturn(new VerifiedIdentity("sub-7", "new@acme.com", true));
        when(userRepository.findByEmailIgnoreCase("new@acme.com")).thenReturn(List.of());
        when(passwordEncoder.encode(anyString())).thenReturn("random");
        when(jwtTokenProvider.generateToken(eq("new@acme.com"), eq("VIEWER"), eq(provisionTenant))).thenReturn("jwt-new");

        assertEquals("jwt-new", authService.ssoLogin(new SsoLoginRequest("google", "tok", null)).getAccessToken());
        verify(identityRepository).save(any(NxUserIdentity.class));
    }

    @Test
    void ssoCallback_RedirectsWithOneTimeCode_NotTokens() throws Exception {
        SsoProviderConfig.Provider cfg = oktaConfig();
        when(ssoProviderConfig.getProvider("okta")).thenReturn(cfg);
        String state = stateFrom(authService.generateSsoAuthorizationUrl("okta", null));

        HttpResponse<String> tokenResponse = mock(HttpResponse.class);
        when(tokenResponse.statusCode()).thenReturn(200);
        when(tokenResponse.body()).thenReturn("{\"id_token\":\"signed.id.token\"}");
        doReturn(tokenResponse).when(httpClient).send(any(HttpRequest.class), any());
        when(idTokenVerifier.verify("okta", "signed.id.token")).thenReturn(new VerifiedIdentity("okta-sub", "test@acme.com", true));
        when(userRepository.findByEmailIgnoreCase("test@acme.com")).thenReturn(List.of(testUser));

        String redirect = authService.handleSsoCallback("okta", "code", state);

        assertTrue(redirect.contains("sso_code="), redirect);
        assertFalse(redirect.contains("jwt-testuser"), "tokens must never appear in the redirect URL");
        String code = java.net.URLDecoder.decode(redirect.substring(redirect.indexOf("sso_code=") + 9), "UTF-8");
        assertEquals("jwt-testuser", authService.exchangeSsoCode(code).getAccessToken());
        assertThrows(BadCredentialsException.class, () -> authService.exchangeSsoCode(code), "exchange codes are single-use");
    }

    @Test
    void ssoCallback_ForgedIdToken_ReturnsError() throws Exception {
        when(ssoProviderConfig.getProvider("okta")).thenReturn(oktaConfig());
        String state = stateFrom(authService.generateSsoAuthorizationUrl("okta", null));
        HttpResponse<String> tokenResponse = mock(HttpResponse.class);
        when(tokenResponse.statusCode()).thenReturn(200);
        when(tokenResponse.body()).thenReturn("{\"id_token\":\"forged\"}");
        doReturn(tokenResponse).when(httpClient).send(any(HttpRequest.class), any());
        when(idTokenVerifier.verify("okta", "forged")).thenThrow(new IdTokenVerifier.InvalidIdTokenException("bad sig"));

        assertTrue(authService.handleSsoCallback("okta", "code", state).contains("error=sso_invalid_token"));
    }

    @Test
    void ssoCallback_UnknownState_ReturnsError() {
        assertTrue(authService.handleSsoCallback("okta", "code", "unknown-state").contains("error=sso_invalid_state"));
    }

    @Test
    void ssoCallback_StateIssuedForAnotherProvider_IsRejected() {
        when(ssoProviderConfig.getProvider("okta")).thenReturn(oktaConfig());
        when(ssoProviderConfig.getProvider("google")).thenReturn(oktaConfig());
        String state = stateFrom(authService.generateSsoAuthorizationUrl("google", null));

        assertTrue(authService.handleSsoCallback("okta", "code", state).contains("error=sso_invalid_state"));
    }

    @Test
    void ssoCallback_UnconfiguredProvider_ReturnsError() {
        when(ssoProviderConfig.getProvider("okta")).thenReturn(oktaConfig());
        String state = stateFrom(authService.generateSsoAuthorizationUrl("okta", null));
        when(ssoProviderConfig.getProvider("okta")).thenReturn(null);

        assertTrue(authService.handleSsoCallback("okta", "code", state).contains("error=sso_not_configured"));
    }

    @Test
    void generateSsoAuthorizationUrl_UnsupportedProvider_Throws() {
        assertThrows(BadRequestException.class, () -> authService.generateSsoAuthorizationUrl("myspace", null));
    }

    @Test
    void generateSsoAuthorizationUrl_UnconfiguredProvider_Throws() {
        when(ssoProviderConfig.getProvider("okta")).thenReturn(null);
        assertThrows(BadRequestException.class, () -> authService.generateSsoAuthorizationUrl("okta", null));
    }

    // ------------------------------------------------------------------ password reset

    @Test
    void forgotPassword_SendsSingleUseLink_AndResetWorksOnce() {
        authService.forgotPassword(new ForgotPasswordRequest("test@acme.com"));
        when(userRepository.findByEmailIgnoreCase("test@acme.com")).thenReturn(List.of(testUser));
        authService.forgotPassword(new ForgotPasswordRequest("test@acme.com"));

        ArgumentCaptor<String> token = ArgumentCaptor.forClass(String.class);
        verify(resetMailer).sendResetLink(eq("test@acme.com"), eq("testuser"), token.capture());
        when(passwordEncoder.encode("NewPass123!")).thenReturn("encoded-newpass");

        authService.resetPassword(new ResetPasswordRequest(token.getValue(), "NewPass123!"));

        assertEquals("encoded-newpass", testUser.getPasswordHash());
        assertThrows(BadRequestException.class,
                () -> authService.resetPassword(new ResetPasswordRequest(token.getValue(), "Another123!")));
        verify(userRepository, never()).findAll();
    }

    @Test
    void forgotPassword_NewRequestInvalidatesOlderLink() {
        authService.forgotPassword(new ForgotPasswordRequest("testuser"));
        authService.forgotPassword(new ForgotPasswordRequest("testuser"));
        ArgumentCaptor<String> tokens = ArgumentCaptor.forClass(String.class);
        verify(resetMailer, times(2)).sendResetLink(any(), eq("testuser"), tokens.capture());

        assertThrows(BadRequestException.class,
                () -> authService.resetPassword(new ResetPasswordRequest(tokens.getAllValues().get(0), "NewPass123!")));
    }

    @Test
    void forgotPassword_UnknownOrAmbiguous_SendsNothing() {
        when(userRepository.findByUsername("ghost@x.com")).thenReturn(Optional.empty());
        when(userRepository.findByEmailIgnoreCase("ghost@x.com")).thenReturn(List.of(testUser, testUser));

        authService.forgotPassword(new ForgotPasswordRequest("ghost@x.com"));

        verifyNoInteractions(resetMailer);
    }

    @Test
    void resetPassword_InvalidToken_Throws() {
        assertThrows(BadRequestException.class,
                () -> authService.resetPassword(new ResetPasswordRequest("invalid", "NewPass123!")));
    }

    // ------------------------------------------------------------------ tenants + refresh

    @Test
    void getTenantsFor_Anonymous_ReturnsNothing() {
        assertTrue(authService.getTenantsFor(null).isEmpty());
        verify(companySettingsRepository, never()).findAll();
    }

    @Test
    void refreshToken_Valid_ReturnsNewAuth() {
        when(jwtTokenProvider.validateToken("refresh-abc")).thenReturn(true);
        when(jwtTokenProvider.isRefreshToken("refresh-abc")).thenReturn(true);
        when(jwtTokenProvider.getUsernameFromToken("refresh-abc")).thenReturn("testuser");

        assertEquals("jwt-testuser", authService.refreshToken("refresh-abc").getAccessToken());
    }

    @Test
    void refreshToken_Invalid_Throws() {
        when(jwtTokenProvider.validateToken("expired")).thenReturn(false);
        assertThrows(BadRequestException.class, () -> authService.refreshToken("expired"));
    }

    @Test
    void refreshToken_AccessToken_Throws() {
        when(jwtTokenProvider.validateToken("access-abc")).thenReturn(true);
        when(jwtTokenProvider.isRefreshToken("access-abc")).thenReturn(false);
        assertThrows(BadRequestException.class, () -> authService.refreshToken("access-abc"));
    }

    // ------------------------------------------------------------------ helpers

    private String enableMfaForTestUser() throws Exception {
        MfaEnrollmentResponse enrol = authService.beginMfaEnrollment("testuser");
        authService.activateMfa("testuser", totpNow(enrol.getSecret()));
        return enrol.getSecret();
    }

    private String loginChallenge() {
        when(passwordEncoder.matches("pw", "encoded-pass")).thenReturn(true);
        AuthResponse challenge = authService.authenticate(new LoginRequest("testuser", "pw", null));
        assertTrue(challenge.isMfaRequired());
        return challenge.getMfaToken();
    }

    private static void assumeDiffers(String a, String b) {
        org.junit.jupiter.api.Assumptions.assumeFalse(a.equals(b), "1-in-a-million collision; skip");
    }

    private static SsoProviderConfig.Provider oktaConfig() {
        SsoProviderConfig.Provider cfg = new SsoProviderConfig.Provider();
        cfg.setClientId("client");
        cfg.setClientSecret("secret");
        cfg.setAuthorizeUrl("https://idp.example.com/authorize");
        cfg.setTokenUrl("https://idp.example.com/token");
        cfg.setRedirectUri("http://localhost:8080/api/v1/auth/sso/{provider}/callback");
        return cfg;
    }

    private static String stateFrom(String authorizeUrl) {
        Matcher m = Pattern.compile("state=([^&]+)").matcher(authorizeUrl);
        assertTrue(m.find());
        return m.group(1);
    }

    /** Independent RFC 6238 implementation, so the test does not just mirror TotpService. */
    private static String totpNow(String base32Secret) throws Exception {
        String alphabet = "ABCDEFGHIJKLMNOPQRSTUVWXYZ234567";
        ByteBuffer key = ByteBuffer.allocate(base32Secret.length() * 5 / 8);
        int buffer = 0, bits = 0;
        for (char c : base32Secret.toCharArray()) {
            buffer = (buffer << 5) | alphabet.indexOf(c);
            bits += 5;
            if (bits >= 8) { key.put((byte) (buffer >> (bits - 8))); bits -= 8; }
        }
        Mac mac = Mac.getInstance("HmacSHA1");
        mac.init(new SecretKeySpec(key.array(), "HmacSHA1"));
        byte[] h = mac.doFinal(ByteBuffer.allocate(8).putLong(System.currentTimeMillis() / 1000 / 30).array());
        int o = h[h.length - 1] & 0xf;
        int bin = ((h[o] & 0x7f) << 24) | ((h[o + 1] & 0xff) << 16) | ((h[o + 2] & 0xff) << 8) | (h[o + 3] & 0xff);
        return String.format("%06d", bin % 1_000_000);
    }
}
