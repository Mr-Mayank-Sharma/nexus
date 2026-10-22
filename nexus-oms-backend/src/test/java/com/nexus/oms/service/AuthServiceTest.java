package com.nexus.oms.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.nexus.core.model.security.UserAccount;
import com.nexus.core.service.UserAccountService;
import com.nexus.oms.config.SsoProviderConfig;
import com.nexus.oms.dto.*;
import com.nexus.oms.entity.CompanySettings;
import com.nexus.oms.exception.BadRequestException;
import com.nexus.oms.integration.core.CredentialVault;
import com.nexus.oms.repository.CompanySettingsRepository;
import com.nexus.oms.security.JwtTokenProvider;
import com.nexus.oms.service.auth.AuthTokenStore;
import com.nexus.oms.service.auth.IdTokenVerifier;
import com.nexus.oms.service.auth.IdTokenVerifier.VerifiedIdentity;
import com.nexus.oms.service.auth.PasswordResetMailer;
import com.nexus.oms.service.auth.TotpService;
import com.nexus.oms.tenancy.PlatformDirectory;
import com.nexus.oms.tenancy.TenantDatabaseRegistry;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;
import org.springframework.boot.autoconfigure.jdbc.DataSourceProperties;
import org.springframework.core.io.ClassPathResource;
import org.springframework.jdbc.datasource.DriverManagerDataSource;
import org.springframework.jdbc.datasource.init.ScriptUtils;
import org.springframework.security.authentication.BadCredentialsException;
import org.springframework.security.crypto.password.PasswordEncoder;

import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.ByteBuffer;
import java.sql.Connection;
import java.util.*;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

/**
 * Sign-in rules. The shared sign-in directory and the single-use tokens are the real classes on an in-memory database;
 * each company's users are an in-memory stand-in for the core user service.
 */
@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class AuthServiceTest {

    @Mock private PasswordEncoder passwordEncoder;
    @Mock private JwtTokenProvider jwtTokenProvider;
    @Mock private CompanySettingsRepository companySettingsRepository;
    @Mock private TenantDatabaseRegistry tenantDatabases;
    @Mock private SsoProviderConfig ssoProviderConfig;
    @Mock private IdTokenVerifier idTokenVerifier;
    @Mock private PasswordResetMailer resetMailer;
    @Mock private HttpClient httpClient;

    private final ObjectMapper objectMapper = new ObjectMapper();
    private final TotpService totpService = new TotpService();
    private final CredentialVault credentialVault = new CredentialVault();
    private final InMemoryUserAccounts users = new InMemoryUserAccounts();

    private PlatformDirectory platformDirectory;
    private AuthService authService;
    private UserAccount testUser;
    private UUID tenantId;

    @BeforeEach
    void setUp() throws Exception {
        // Step 1: a fresh shared database with the platform tables.
        DriverManagerDataSource sharedDatabase = new DriverManagerDataSource(
                "jdbc:h2:mem:auth_" + UUID.randomUUID().toString().replace("-", "") + ";MODE=PostgreSQL;DB_CLOSE_DELAY=-1", "sa", "");
        try (Connection connection = sharedDatabase.getConnection()) {
            ScriptUtils.executeSqlScript(connection, new ClassPathResource("schema.sql"));
        }
        platformDirectory = new PlatformDirectory(sharedDatabase);

        authService = new AuthService(platformDirectory, users, tenantDatabases, new DataSourceProperties(), passwordEncoder, jwtTokenProvider,
                companySettingsRepository, ssoProviderConfig, objectMapper, new AuthTokenStore(sharedDatabase), totpService, idTokenVerifier,
                credentialVault, resetMailer, httpClient);

        // Step 2: one company with one VIEWER, "testuser".
        tenantId = UUID.randomUUID();
        platformDirectory.addCompany(tenantId, "Acme");
        testUser = users.add(tenantId, "testuser", "test@acme.com", "encoded-pass", "VIEWER");

        when(companySettingsRepository.findByTenantId(any())).thenReturn(Optional.empty());
        when(jwtTokenProvider.generateToken(eq("testuser"), eq("VIEWER"), eq(tenantId))).thenReturn("jwt-testuser");
    }

    // ------------------------------------------------------------------ registration + login

    @Test
    void register_CreatesUserAndReturnsAuth() {
        RegisterRequest request = new RegisterRequest();
        request.setUsername("newuser");
        request.setPassword("Test1234!");
        request.setRole("VIEWER");
        when(passwordEncoder.encode("Test1234!")).thenReturn("encoded-new");
        when(jwtTokenProvider.generateToken(eq("newuser"), eq("VIEWER"), any())).thenReturn("test-token");

        AuthResponse result = authService.register(request);

        assertEquals("test-token", result.getAccessToken());
        assertTrue(users.findByUsername("newuser").isPresent(), "the user is created in the new company");
        assertEquals("VIEWER", users.roleOf(users.findByUsername("newuser").get().getUserId()));
        ArgumentCaptor<CompanySettings> settings = ArgumentCaptor.forClass(CompanySettings.class);
        verify(companySettingsRepository).save(settings.capture());
        assertEquals("newuser", settings.getValue().getCompanyName(), "company name defaults to the username");
    }

    @Test
    void register_WithExistingUsername_Throws() {
        RegisterRequest request = new RegisterRequest();
        request.setUsername("existing");
        request.setPassword("Test1234!");
        users.add(UUID.randomUUID(), "existing", null, "x", "VIEWER");
        int before = users.count();

        assertThrows(BadRequestException.class, () -> authService.register(request));
        assertEquals(before, users.count());
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
        assertNotEquals(enrol.getSecret(), testUser.getMfaSecretRef(), "secret must be stored encrypted");

        List<String> recovery = authService.activateMfa("testuser", totpNow(enrol.getSecret()));
        assertEquals(10, recovery.size());
        assertTrue(testUser.getMfaEnabled());

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
        assertFalse(testUser.getMfaEnabled());
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
        assertEquals(1, users.count());
    }

    @Test
    void ssoLogin_ExistingIdentityLink_ReturnsLinkedUser() {
        when(idTokenVerifier.verify("google", "tok")).thenReturn(new VerifiedIdentity("sub-1", "other@x.com", false));
        users.linkIdentity(testUser.getUserId(), "google", "sub-1", "other@x.com");

        assertEquals("jwt-testuser", authService.ssoLogin(new SsoLoginRequest("google", "tok", null)).getAccessToken());
    }

    @Test
    void ssoLogin_VerifiedEmailWithSingleMatch_LinksAndLogsIn() {
        when(idTokenVerifier.verify("google", "tok")).thenReturn(new VerifiedIdentity("sub-2", "test@acme.com", true));

        AuthResponse result = authService.ssoLogin(new SsoLoginRequest("google", "tok", null));

        assertEquals("jwt-testuser", result.getAccessToken());
        assertEquals(Optional.of(testUser.getUserId()), users.userWithIdentity("google", "sub-2"), "the identity is linked");
    }

    @Test
    void ssoLogin_SameLocalPartDifferentDomain_DoesNotReachOtherAccount() {
        // Previously username = email.split("@")[0], so admin@attacker.com became user "admin".
        users.add(UUID.randomUUID(), "admin", "admin@victim.com", "x", "ADMIN");
        when(idTokenVerifier.verify("google", "tok")).thenReturn(new VerifiedIdentity("sub-3", "admin@attacker.com", true));

        assertThrows(BadCredentialsException.class, () -> authService.ssoLogin(new SsoLoginRequest("google", "tok", null)));
        verify(jwtTokenProvider, never()).generateToken(eq("admin"), any(), any());
    }

    @Test
    void ssoLogin_UnverifiedEmail_IsNotLinked() {
        when(idTokenVerifier.verify("google", "tok")).thenReturn(new VerifiedIdentity("sub-4", "test@acme.com", false));

        assertThrows(BadCredentialsException.class, () -> authService.ssoLogin(new SsoLoginRequest("google", "tok", null)));
        assertTrue(users.userWithIdentity("google", "sub-4").isEmpty());
    }

    @Test
    void ssoLogin_AmbiguousEmail_IsRejected() {
        users.add(UUID.randomUUID(), "twin", "test@acme.com", "x", "VIEWER");
        when(idTokenVerifier.verify("google", "tok")).thenReturn(new VerifiedIdentity("sub-5", "test@acme.com", true));

        assertThrows(BadCredentialsException.class, () -> authService.ssoLogin(new SsoLoginRequest("google", "tok", null)));
    }

    @Test
    void ssoLogin_UnknownIdentity_IgnoresCallerSuppliedTenant() {
        UUID victimTenant = UUID.randomUUID();
        when(idTokenVerifier.verify("google", "tok")).thenReturn(new VerifiedIdentity("sub-6", "new@acme.com", true));

        assertThrows(BadCredentialsException.class,
                () -> authService.ssoLogin(new SsoLoginRequest("google", "tok", victimTenant.toString())));
        assertEquals(1, users.count(), "no account is created");
    }

    @Test
    void ssoLogin_AutoProvisionTenant_CreatesViewer() {
        UUID provisionTenant = UUID.randomUUID();
        SsoProviderConfig.Provider cfg = new SsoProviderConfig.Provider();
        cfg.setAutoProvisionTenantId(provisionTenant.toString());
        when(ssoProviderConfig.getProvider("google")).thenReturn(cfg);
        when(idTokenVerifier.verify("google", "tok")).thenReturn(new VerifiedIdentity("sub-7", "new@acme.com", true));
        when(passwordEncoder.encode(anyString())).thenReturn("random");
        when(jwtTokenProvider.generateToken(eq("new@acme.com"), eq("VIEWER"), eq(provisionTenant))).thenReturn("jwt-new");

        assertEquals("jwt-new", authService.ssoLogin(new SsoLoginRequest("google", "tok", null)).getAccessToken());
        String newUserId = users.findByUsername("new@acme.com").orElseThrow().getUserId();
        assertEquals("VIEWER", users.roleOf(newUserId));
        assertEquals(Optional.of(newUserId), users.userWithIdentity("google", "sub-7"));
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

        ArgumentCaptor<String> token = ArgumentCaptor.forClass(String.class);
        verify(resetMailer).sendResetLink(eq("test@acme.com"), eq("testuser"), token.capture());
        when(passwordEncoder.encode("NewPass123!")).thenReturn("encoded-newpass");

        authService.resetPassword(new ResetPasswordRequest(token.getValue(), "NewPass123!"));

        assertEquals("encoded-newpass", testUser.getCurrentPassword());
        assertThrows(BadRequestException.class,
                () -> authService.resetPassword(new ResetPasswordRequest(token.getValue(), "Another123!")));
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
        users.add(UUID.randomUUID(), "ghost-one", "ghost@x.com", "x", "VIEWER");
        users.add(UUID.randomUUID(), "ghost-two", "ghost@x.com", "x", "VIEWER");

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
        verifyNoInteractions(companySettingsRepository);
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

    /**
     * Each company's users, in memory: what the core user service keeps in the company's database. Usernames are
     * unique across companies (the shared directory enforces that), so one map serves every company here.
     */
    private final class InMemoryUserAccounts extends UserAccountService {

        private final Map<String, UserAccount> usersById = new LinkedHashMap<>();
        private final Map<String, String> rolesByUserId = new HashMap<>();
        private final Map<String, String> userIdsByIdentity = new HashMap<>();

        InMemoryUserAccounts() {
            super(null, null);
        }

        /** A user in the company, also recorded in the shared sign-in directory. */
        UserAccount add(UUID companyId, String username, String email, String passwordHash, String role) {
            if (platformDirectory.companyName(companyId).isEmpty()) {
                platformDirectory.addCompany(companyId, "Company");
            }
            String userId = create(new NewUser(null, username, email, passwordHash, null, role));
            platformDirectory.addLogin(username, email, companyId);
            return usersById.get(userId);
        }

        int count() {
            return usersById.size();
        }

        @Override
        public String create(NewUser newUser) {
            UserAccount user = new UserAccount();
            user.setUserId(newUser.userId() == null ? UUID.randomUUID().toString() : newUser.userId());
            user.setUsername(newUser.username());
            user.setEmailAddress(newUser.emailAddress());
            user.setCurrentPassword(newUser.passwordHash());
            user.setMfaEnabled(false);
            usersById.put(user.getUserId(), user);
            rolesByUserId.put(user.getUserId(), newUser.role());
            return user.getUserId();
        }

        @Override
        public Optional<UserAccount> findByUsername(String username) {
            return usersById.values().stream().filter(user -> user.getUsername().equals(username)).findFirst();
        }

        @Override
        public Optional<UserAccount> findById(String userId) {
            return Optional.ofNullable(usersById.get(userId));
        }

        @Override
        public void setPasswordHash(String userId, String passwordHash) {
            usersById.get(userId).setCurrentPassword(passwordHash);
        }

        @Override
        public void setPendingMfaSecret(String userId, String encryptedSecret) {
            usersById.get(userId).setMfaSecretRef(encryptedSecret);
        }

        @Override
        public void enableMfa(String userId, String recoveryCodeHashes) {
            usersById.get(userId).setMfaEnabled(true);
            usersById.get(userId).setMfaRecoveryCodes(recoveryCodeHashes);
        }

        @Override
        public void disableMfa(String userId) {
            UserAccount user = usersById.get(userId);
            user.setMfaEnabled(false);
            user.setMfaSecretRef(null);
            user.setMfaRecoveryCodes(null);
        }

        @Override
        public void setRecoveryCodes(String userId, String recoveryCodeHashes) {
            usersById.get(userId).setMfaRecoveryCodes(recoveryCodeHashes);
        }

        @Override
        public void linkIdentity(String userId, String providerId, String externalSubject, String emailAddress) {
            userIdsByIdentity.put(providerId + "|" + externalSubject, userId);
        }

        @Override
        public Optional<String> userWithIdentity(String providerId, String externalSubject) {
            return Optional.ofNullable(userIdsByIdentity.get(providerId + "|" + externalSubject));
        }

        @Override
        public String roleOf(String userId) {
            return rolesByUserId.get(userId);
        }

        @Override
        public Set<String> permissionsOf(String role) {
            return Set.of();
        }
    }
}
