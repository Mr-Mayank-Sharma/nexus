package com.nexus.oms.service;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.nexus.oms.config.SsoProviderConfig;
import com.nexus.oms.dto.*;
import com.nexus.core.model.security.UserAccount;
import com.nexus.core.service.UserAccountService;
import com.nexus.oms.entity.CompanySettings;
import com.nexus.oms.exception.BadRequestException;
import com.nexus.oms.integration.core.CredentialVault;
import com.nexus.oms.repository.CompanySettingsRepository;
import com.nexus.oms.security.JwtTokenProvider;
import com.nexus.oms.security.TenantContext;
import com.nexus.oms.service.auth.AuthTokenStore;
import com.nexus.oms.service.auth.AuthTokenStore.AuthToken;
import com.nexus.oms.tenancy.PlatformDirectory;
import com.nexus.oms.tenancy.TenantDatabaseRegistry;
import com.nexus.oms.service.auth.IdTokenVerifier;
import com.nexus.oms.service.auth.IdTokenVerifier.VerifiedIdentity;
import com.nexus.oms.service.auth.PasswordResetMailer;
import com.nexus.oms.service.auth.TotpService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.jdbc.DataSourceProperties;
import org.springframework.security.authentication.BadCredentialsException;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.web.util.UriComponentsBuilder;

import java.net.URI;
import java.net.URLEncoder;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.security.SecureRandom;
import java.time.Duration;
import java.util.*;
import java.util.function.Supplier;

/**
 * Sign-in (doc 29, phase C): the shared database's {@link PlatformDirectory} says which company a username belongs to;
 * the user, its role and permissions are in that company's core database ({@link UserAccountService}). Every step that
 * reads or changes a user runs as the user's company ({@link #asCompanyOf}).
 */
@Service
public class AuthService {

    private static final Logger log = LoggerFactory.getLogger(AuthService.class);
    private static final Set<String> ALLOWED_ROLES = Set.of(
        "ADMIN", "CEO", "OPS_MANAGER", "WAREHOUSE_MANAGER",
        "PICKER", "PACKER", "LOADER",
        "STORE_MANAGER", "BOPIS_OWNER", "CUSTOMER_SUPPORT",
        "PROCUREMENT_MANAGER", "FINANCE", "LOGISTICS_MANAGER", "VIEWER"
    );
    private static final Set<String> SSO_PROVIDERS = Set.of("okta", "auth0", "google", "microsoft");

    static final Duration MFA_CHALLENGE_TTL = Duration.ofMinutes(5);
    static final Duration PASSWORD_RESET_TTL = Duration.ofHours(1);
    static final Duration SSO_STATE_TTL = Duration.ofMinutes(10);
    static final Duration SSO_EXCHANGE_TTL = Duration.ofSeconds(60);
    static final int MAX_MFA_ATTEMPTS = 5;
    static final int RECOVERY_CODE_COUNT = 10;
    private static final String MFA_ISSUER = "Nexus";
    private static final String RECOVERY_ALPHABET = "ABCDEFGHJKLMNPQRSTUVWXYZ23456789";
    private static final SecureRandom RANDOM = new SecureRandom();

    private final PlatformDirectory platformDirectory;
    private final UserAccountService userAccountService;
    private final TenantDatabaseRegistry tenantDatabases;
    private final DataSourceProperties sharedDatabase;
    private final PasswordEncoder passwordEncoder;
    private final JwtTokenProvider jwtTokenProvider;
    private final CompanySettingsRepository companySettingsRepository;
    private final SsoProviderConfig ssoProviderConfig;
    private final ObjectMapper objectMapper;
    private final AuthTokenStore tokenStore;
    private final TotpService totpService;
    private final IdTokenVerifier idTokenVerifier;
    private final CredentialVault credentialVault;
    private final PasswordResetMailer resetMailer;
    private final HttpClient httpClient;

    /** A new company gets its own core database at sign-up (off in tests, which run on one in-memory database). */
    @Value("${nexus.tenancy.database-per-company:false}")
    private boolean databasePerCompany;

    @Value("${app.frontend-url:http://localhost:5173}")
    private String frontendUrl;

    @Value("${app.jwt.expiration-ms:900000}")
    private long accessTokenTtlMs = 900_000L;

    @org.springframework.beans.factory.annotation.Autowired
    public AuthService(PlatformDirectory platformDirectory,
                       UserAccountService userAccountService,
                       TenantDatabaseRegistry tenantDatabases,
                       DataSourceProperties sharedDatabase,
                       PasswordEncoder passwordEncoder,
                       JwtTokenProvider jwtTokenProvider,
                       CompanySettingsRepository companySettingsRepository,
                       SsoProviderConfig ssoProviderConfig,
                       ObjectMapper objectMapper,
                       AuthTokenStore tokenStore,
                       TotpService totpService,
                       IdTokenVerifier idTokenVerifier,
                       CredentialVault credentialVault,
                       PasswordResetMailer resetMailer) {
        this(platformDirectory, userAccountService, tenantDatabases, sharedDatabase, passwordEncoder, jwtTokenProvider, companySettingsRepository,
                ssoProviderConfig, objectMapper, tokenStore, totpService, idTokenVerifier, credentialVault, resetMailer, HttpClient.newHttpClient());
    }

    AuthService(PlatformDirectory platformDirectory,
                UserAccountService userAccountService,
                TenantDatabaseRegistry tenantDatabases,
                DataSourceProperties sharedDatabase,
                PasswordEncoder passwordEncoder,
                JwtTokenProvider jwtTokenProvider,
                CompanySettingsRepository companySettingsRepository,
                SsoProviderConfig ssoProviderConfig,
                ObjectMapper objectMapper,
                AuthTokenStore tokenStore,
                TotpService totpService,
                IdTokenVerifier idTokenVerifier,
                CredentialVault credentialVault,
                PasswordResetMailer resetMailer,
                HttpClient httpClient) {
        this.platformDirectory = platformDirectory;
        this.userAccountService = userAccountService;
        this.tenantDatabases = tenantDatabases;
        this.sharedDatabase = sharedDatabase;
        this.passwordEncoder = passwordEncoder;
        this.jwtTokenProvider = jwtTokenProvider;
        this.companySettingsRepository = companySettingsRepository;
        this.ssoProviderConfig = ssoProviderConfig;
        this.objectMapper = objectMapper;
        this.tokenStore = tokenStore;
        this.totpService = totpService;
        this.idTokenVerifier = idTokenVerifier;
        this.credentialVault = credentialVault;
        this.resetMailer = resetMailer;
        this.httpClient = httpClient;
    }

    /** A user and the company it belongs to. */
    record CompanyUser(UUID tenantId, UserAccount account) {
    }

    // ------------------------------------------------------------------ password login + MFA

    public AuthResponse authenticate(LoginRequest request) {
        CompanyUser user = findCompanyUser(request.getUsername())
                .orElseThrow(() -> new BadCredentialsException("Invalid username or password"));
        UserAccount account = user.account();
        if (Boolean.TRUE.equals(account.getDisabled()) || !passwordEncoder.matches(request.getPassword(), account.getCurrentPassword())) {
            throw new BadCredentialsException("Invalid username or password");
        }

        if (Boolean.TRUE.equals(account.getMfaEnabled())) {
            String mfaToken = tokenStore.issue(AuthTokenStore.MFA_CHALLENGE, account.getUsername(), null, MFA_CHALLENGE_TTL);
            return AuthResponse.builder()
                    .mfaRequired(true)
                    .mfaToken(mfaToken)
                    .username(account.getUsername())
                    .tenantId(user.tenantId().toString())
                    .build();
        }

        AuthResponse response = buildAuthResponse(user);
        response.setMfaEnrollmentRequired(tenantRequiresMfa(user.tenantId()));
        return response;
    }

    public AuthResponse verifyMfa(MfaVerificationRequest request) {
        AuthToken challenge = tokenStore.find(AuthTokenStore.MFA_CHALLENGE, request.getMfaToken())
                .orElseThrow(() -> new BadRequestException("Invalid or expired MFA session. Please login again."));
        if (challenge.attempts() >= MAX_MFA_ATTEMPTS) {
            tokenStore.delete(challenge);
            throw new BadRequestException("Too many invalid codes. Please login again.");
        }

        CompanyUser user = requireUser(challenge.username());
        if (!checkTotpOrRecoveryCode(user, request.getTotpCode())) {
            int attempts = tokenStore.recordFailedAttempt(challenge);
            if (attempts >= MAX_MFA_ATTEMPTS) {
                tokenStore.delete(challenge);
                throw new BadRequestException("Too many invalid codes. Please login again.");
            }
            throw new BadRequestException("Invalid verification code");
        }

        tokenStore.delete(challenge);
        return buildAuthResponse(user);
    }

    /** For the account security screen: is MFA on, and does the company's policy require it? */
    public Map<String, Object> mfaStatus(String username) {
        CompanyUser user = requireUser(username);
        UserAccount account = user.account();
        String codes = account.getMfaRecoveryCodes();
        int remainingCodes = codes == null || codes.isBlank() ? 0 : codes.split(",").length;
        boolean enabled = Boolean.TRUE.equals(account.getMfaEnabled());
        Map<String, Object> status = new LinkedHashMap<>();
        status.put("enabled", enabled);
        status.put("requiredByTenant", tenantRequiresMfa(user.tenantId()));
        status.put("recoveryCodesRemaining", enabled ? remainingCodes : 0);
        return status;
    }

    /** Starts (or restarts) enrolment: stores a pending secret; MFA stays off until activated. */
    public MfaEnrollmentResponse beginMfaEnrollment(String username) {
        CompanyUser user = requireUser(username);
        UserAccount account = user.account();
        if (Boolean.TRUE.equals(account.getMfaEnabled())) {
            throw new BadRequestException("MFA is already enabled. Disable it first to re-enrol.");
        }
        String secret = totpService.generateSecret();
        asCompany(user.tenantId(), () -> {
            userAccountService.setPendingMfaSecret(account.getUserId(), credentialVault.encrypt(secret));
            return null;
        });
        String accountName = account.getEmailAddress() != null ? account.getEmailAddress() : account.getUsername();
        return MfaEnrollmentResponse.builder()
                .secret(secret)
                .otpauthUri(totpService.otpauthUri(MFA_ISSUER, accountName, secret))
                .build();
    }

    /** Confirms enrolment with a first code and returns single-use recovery codes (shown once). */
    public List<String> activateMfa(String username, String code) {
        CompanyUser user = requireUser(username);
        UserAccount account = user.account();
        if (Boolean.TRUE.equals(account.getMfaEnabled())) {
            throw new BadRequestException("MFA is already enabled.");
        }
        if (account.getMfaSecretRef() == null) {
            throw new BadRequestException("Start MFA enrolment first.");
        }
        if (!totpService.verify(credentialVault.decrypt(account.getMfaSecretRef()), code)) {
            throw new BadRequestException("Invalid verification code");
        }
        List<String> codes = new ArrayList<>();
        List<String> hashes = new ArrayList<>();
        for (int i = 0; i < RECOVERY_CODE_COUNT; i++) {
            String recovery = randomRecoveryCode();
            codes.add(recovery);
            hashes.add(sha256(normaliseRecoveryCode(recovery)));
        }
        asCompany(user.tenantId(), () -> {
            userAccountService.enableMfa(account.getUserId(), String.join(",", hashes));
            return null;
        });
        return codes;
    }

    public void disableMfa(String username, String code) {
        CompanyUser user = requireUser(username);
        if (!Boolean.TRUE.equals(user.account().getMfaEnabled())) {
            return;
        }
        if (!checkTotpOrRecoveryCode(user, code)) {
            throw new BadRequestException("Invalid verification code");
        }
        asCompany(user.tenantId(), () -> {
            userAccountService.disableMfa(user.account().getUserId());
            return null;
        });
    }

    /** A current TOTP code, or one of the recovery codes (which is then used up). */
    private boolean checkTotpOrRecoveryCode(CompanyUser user, String code) {
        UserAccount account = user.account();
        if (code == null || account.getMfaSecretRef() == null) {
            return false;
        }
        if (totpService.verify(credentialVault.decrypt(account.getMfaSecretRef()), code.trim())) {
            return true;
        }
        String storedCodes = account.getMfaRecoveryCodes();
        if (storedCodes == null || storedCodes.isBlank()) {
            return false;
        }
        String candidate = sha256(normaliseRecoveryCode(code));
        List<String> remaining = new ArrayList<>(Arrays.asList(storedCodes.split(",")));
        boolean matched = remaining.removeIf(stored -> MessageDigest.isEqual(stored.getBytes(StandardCharsets.US_ASCII),
                candidate.getBytes(StandardCharsets.US_ASCII)));
        if (matched) {
            asCompany(user.tenantId(), () -> {
                userAccountService.setRecoveryCodes(account.getUserId(), String.join(",", remaining));
                return null;
            });
        }
        return matched;
    }

    private boolean tenantRequiresMfa(UUID tenantId) {
        CompanySettings settings = companySettingsRepository.findByTenantId(tenantId).orElse(null);
        if (settings == null || settings.getSecurityPolicy() == null || !settings.getSecurityPolicy().contains("mfa")) {
            return false;
        }
        try {
            JsonNode policy = objectMapper.readTree(settings.getSecurityPolicy());
            return policy.has("mfaEnabled") && policy.get("mfaEnabled").asBoolean();
        } catch (Exception e) {
            log.warn("Unreadable security policy for tenant {}: {}", tenantId, e.getMessage());
            return false;
        }
    }

    // ------------------------------------------------------------------ SSO

    /** Direct ID-token login (e.g. Google One Tap). The token must verify against the IdP's JWKS. */
    public AuthResponse ssoLogin(SsoLoginRequest request) {
        String provider = request.getProvider().toLowerCase();
        if (!SSO_PROVIDERS.contains(provider)) {
            throw new BadRequestException("Unsupported SSO provider: " + provider);
        }
        VerifiedIdentity identity;
        try {
            identity = idTokenVerifier.verify(provider, request.getIdToken());
        } catch (IdTokenVerifier.InvalidIdTokenException e) {
            log.warn("SSO login rejected for provider {}: {}", provider, e.getMessage());
            throw new BadCredentialsException("Invalid ID token");
        }
        return buildAuthResponse(resolveSsoUser(provider, identity));
    }

    public String generateSsoAuthorizationUrl(String provider, String tenantId) {
        provider = provider.toLowerCase();
        if (!SSO_PROVIDERS.contains(provider)) {
            throw new BadRequestException("Unsupported SSO provider: " + provider);
        }

        SsoProviderConfig.Provider cfg = ssoProviderConfig.getProvider(provider);
        if (cfg == null || cfg.getClientId() == null) {
            throw new BadRequestException("SSO provider " + provider + " is not configured");
        }

        // The caller-supplied tenantId is deliberately ignored: tenant membership comes from an
        // existing account or the provider's admin-configured auto-provision tenant, never the URL.
        String state = tokenStore.issue(AuthTokenStore.SSO_STATE, null, provider, SSO_STATE_TTL);
        String redirectUri = cfg.getRedirectUri().replace("{provider}", provider);

        return UriComponentsBuilder.fromUriString(cfg.getAuthorizeUrl())
                .queryParam("client_id", cfg.getClientId())
                .queryParam("redirect_uri", redirectUri)
                .queryParam("response_type", "code")
                .queryParam("scope", cfg.getScopes().replace(",", " "))
                .queryParam("state", state)
                .build()
                .toUriString();
    }

    /**
     * Completes the authorization-code flow and redirects to the frontend with a short-lived,
     * single-use {@code sso_code}. Tokens are never placed in the URL; the SPA exchanges the
     * code via {@link #exchangeSsoCode(String)}.
     */
    public String handleSsoCallback(String provider, String code, String state) {
        provider = provider.toLowerCase();
        Optional<AuthToken> ssoState = tokenStore.consume(AuthTokenStore.SSO_STATE, state);
        if (ssoState.isEmpty() || !provider.equals(ssoState.get().payload())) {
            return frontendUrl + "/login?error=sso_invalid_state";
        }

        SsoProviderConfig.Provider cfg = ssoProviderConfig.getProvider(provider);
        if (cfg == null) {
            return frontendUrl + "/login?error=sso_not_configured";
        }

        try {
            String redirectUri = cfg.getRedirectUri().replace("{provider}", provider);
            String tokenBody = "grant_type=authorization_code"
                    + "&code=" + URLEncoder.encode(code, StandardCharsets.UTF_8)
                    + "&redirect_uri=" + URLEncoder.encode(redirectUri, StandardCharsets.UTF_8)
                    + "&client_id=" + URLEncoder.encode(cfg.getClientId(), StandardCharsets.UTF_8)
                    + "&client_secret=" + URLEncoder.encode(cfg.getClientSecret(), StandardCharsets.UTF_8);

            HttpRequest tokenRequest = HttpRequest.newBuilder()
                    .uri(URI.create(cfg.getTokenUrl()))
                    .header("Content-Type", "application/x-www-form-urlencoded")
                    .POST(HttpRequest.BodyPublishers.ofString(tokenBody))
                    .build();

            HttpResponse<String> tokenResponse = httpClient.send(tokenRequest, HttpResponse.BodyHandlers.ofString());
            if (tokenResponse.statusCode() != 200) {
                return frontendUrl + "/login?error=sso_token_exchange_failed";
            }

            JsonNode tokenJson = objectMapper.readTree(tokenResponse.body());
            VerifiedIdentity identity;
            if (tokenJson.hasNonNull("id_token")) {
                identity = idTokenVerifier.verify(provider, tokenJson.get("id_token").asText());
            } else if (cfg.getUserInfoUrl() != null && tokenJson.hasNonNull("access_token")) {
                // Server-to-server call over TLS to the configured IdP with a token it just issued
                // to us, so the response is authentic without a signature.
                identity = fetchUserInfo(cfg, tokenJson.get("access_token").asText());
                if (identity == null) {
                    return frontendUrl + "/login?error=sso_userinfo_failed";
                }
            } else {
                return frontendUrl + "/login?error=sso_no_identity";
            }

            CompanyUser user = resolveSsoUser(provider, identity);
            String exchange = tokenStore.issue(AuthTokenStore.SSO_EXCHANGE, user.account().getUsername(), provider, SSO_EXCHANGE_TTL);
            return frontendUrl + "/login?sso_code=" + URLEncoder.encode(exchange, StandardCharsets.UTF_8);
        } catch (BadCredentialsException e) {
            return frontendUrl + "/login?error=sso_no_account";
        } catch (IdTokenVerifier.InvalidIdTokenException e) {
            log.warn("SSO callback rejected ID token for provider {}: {}", provider, e.getMessage());
            return frontendUrl + "/login?error=sso_invalid_token";
        } catch (Exception e) {
            log.warn("SSO callback failed for provider {}: {}", provider, e.getMessage());
            return frontendUrl + "/login?error=sso_callback_failed";
        }
    }

    /** Swaps the one-time code from {@link #handleSsoCallback} for access + refresh tokens. */
    public AuthResponse exchangeSsoCode(String code) {
        AuthToken token = tokenStore.consume(AuthTokenStore.SSO_EXCHANGE, code)
                .orElseThrow(() -> new BadCredentialsException("Invalid or expired SSO code"));
        AuthResponse response = buildAuthResponse(requireUser(token.username()));
        response.setSsoProvider(token.payload());
        return response;
    }

    private VerifiedIdentity fetchUserInfo(SsoProviderConfig.Provider cfg, String accessToken) throws Exception {
        HttpRequest userInfoRequest = HttpRequest.newBuilder()
                .uri(URI.create(cfg.getUserInfoUrl()))
                .header("Authorization", "Bearer " + accessToken)
                .GET()
                .build();
        HttpResponse<String> response = httpClient.send(userInfoRequest, HttpResponse.BodyHandlers.ofString());
        if (response.statusCode() != 200) {
            return null;
        }
        JsonNode info = objectMapper.readTree(response.body());
        if (!info.hasNonNull("sub")) {
            return null;
        }
        String email = info.hasNonNull("email") ? info.get("email").asText() : null;
        boolean verified = info.has("email_verified") && info.get("email_verified").asBoolean();
        return new VerifiedIdentity(info.get("sub").asText(), email, verified);
    }

    /**
     * Maps a verified IdP identity to a Nexus user:
     * <ol>
     *   <li>an existing link on (provider, subject), looked for in the companies the email signs in to, then in all;</li>
     *   <li>otherwise exactly one user with the same e-mail, when the IdP asserts it is verified
     *       (or the provider is configured with {@code trust-email}); the link is then recorded;</li>
     *   <li>otherwise a new VIEWER in the provider's {@code auto-provision-tenant-id}, if set.</li>
     * </ol>
     * Anything else is rejected — no account is ever created in a caller-chosen company.
     */
    CompanyUser resolveSsoUser(String provider, VerifiedIdentity identity) {
        Optional<CompanyUser> linked = findLinkedUser(provider, identity);
        if (linked.isPresent()) {
            return linked.get();
        }

        SsoProviderConfig.Provider cfg = ssoProviderConfig.getProvider(provider);
        boolean emailTrusted = identity.email() != null
                && (identity.emailVerified() || (cfg != null && cfg.isTrustEmail()));

        if (emailTrusted) {
            List<PlatformDirectory.Login> matches = platformDirectory.loginsWithEmail(identity.email());
            if (matches.size() == 1) {
                CompanyUser user = requireUser(matches.get(0).username());
                asCompany(user.tenantId(), () -> {
                    userAccountService.linkIdentity(user.account().getUserId(), provider, identity.subject(), identity.email());
                    return null;
                });
                return user;
            }
            if (matches.size() > 1) {
                log.warn("SSO e-mail matches {} accounts for provider {}; refusing to guess", matches.size(), provider);
                throw new BadCredentialsException("This e-mail belongs to more than one account. Ask an administrator to link it.");
            }
        }

        if (cfg != null && cfg.getAutoProvisionTenantId() != null && emailTrusted) {
            if (platformDirectory.usernameTaken(identity.email())) {
                throw new BadCredentialsException("An account with this username already exists.");
            }
            UUID tenantId = UUID.fromString(cfg.getAutoProvisionTenantId());
            String userId = asCompany(tenantId, () -> {
                String newUserId = userAccountService.create(new UserAccountService.NewUser(null, identity.email(), identity.email(),
                        passwordEncoder.encode(UUID.randomUUID().toString()), null, "VIEWER"));
                userAccountService.linkIdentity(newUserId, provider, identity.subject(), identity.email());
                return newUserId;
            });
            platformDirectory.addLogin(identity.email(), identity.email(), tenantId);
            return new CompanyUser(tenantId, asCompany(tenantId, () -> userAccountService.findById(userId).orElseThrow()));
        }

        throw new BadCredentialsException("No Nexus account is linked to this sign-in. Ask your administrator for an invite.");
    }

    /** The user already linked to this outside identity: first in the companies its email signs in to, then in every company. */
    private Optional<CompanyUser> findLinkedUser(String provider, VerifiedIdentity identity) {
        LinkedHashSet<UUID> companies = new LinkedHashSet<>();
        platformDirectory.loginsWithEmail(identity.email()).forEach(login -> companies.add(login.tenantId()));
        companies.addAll(platformDirectory.companies());
        for (UUID tenantId : companies) {
            Optional<UserAccount> account = asCompany(tenantId, () -> userAccountService.userWithIdentity(provider, identity.subject())
                    .flatMap(userAccountService::findById));
            if (account.isPresent()) {
                return Optional.of(new CompanyUser(tenantId, account.get()));
            }
        }
        return Optional.empty();
    }

    // ------------------------------------------------------------------ password reset

    public void forgotPassword(ForgotPasswordRequest request) {
        String identifier = request.getEmail() == null ? "" : request.getEmail().trim();
        Optional<CompanyUser> user = findCompanyUser(identifier);
        if (user.isEmpty()) {
            List<PlatformDirectory.Login> byEmail = platformDirectory.loginsWithEmail(identifier);
            // Ambiguous e-mails get no link; the response is identical either way so the
            // endpoint does not reveal which accounts exist.
            user = byEmail.size() == 1 ? findCompanyUser(byEmail.get(0).username()) : Optional.empty();
        }
        if (user.isEmpty()) {
            return;
        }
        UserAccount account = user.get().account();
        tokenStore.revokeAll(AuthTokenStore.PASSWORD_RESET, account.getUsername());
        String token = tokenStore.issue(AuthTokenStore.PASSWORD_RESET, account.getUsername(), null, PASSWORD_RESET_TTL);
        resetMailer.sendResetLink(account.getEmailAddress(), account.getUsername(), token);
    }

    public void resetPassword(ResetPasswordRequest request) {
        AuthToken token = tokenStore.consume(AuthTokenStore.PASSWORD_RESET, request.getToken())
                .orElseThrow(() -> new BadRequestException("Invalid or expired reset token"));
        CompanyUser user = requireUser(token.username());
        asCompany(user.tenantId(), () -> {
            userAccountService.setPasswordHash(user.account().getUserId(), passwordEncoder.encode(request.getNewPassword()));
            return null;
        });
        tokenStore.revokeAll(AuthTokenStore.MFA_CHALLENGE, user.account().getUsername());
    }

    // ------------------------------------------------------------------ registration, tenants, refresh

    /** The caller's own company only; listing every company leaked the customer list to anonymous users. */
    public List<CompanySettings> getTenantsFor(UUID tenantId) {
        if (tenantId == null) {
            return List.of();
        }
        return companySettingsRepository.findByTenantId(tenantId).map(List::of).orElse(List.of());
    }

    /**
     * A new company and its first user: the company is recorded in the shared directory, gets its own core database
     * (when {@code nexus.tenancy.database-per-company} is on), and the user is created there with the role asked for.
     */
    public AuthResponse register(RegisterRequest request) {
        if (platformDirectory.usernameTaken(request.getUsername())) {
            throw new BadRequestException("Username already exists");
        }
        String role = request.getRole() != null ? request.getRole().toUpperCase() : "VIEWER";
        if (!ALLOWED_ROLES.contains(role)) {
            throw new BadRequestException("Invalid role: " + role + ". Allowed roles: " + ALLOWED_ROLES);
        }

        UUID tenantId = UUID.randomUUID();
        String companyName = request.getCompanyName() != null && !request.getCompanyName().isBlank()
                ? request.getCompanyName().trim() : request.getUsername();
        platformDirectory.addCompany(tenantId, companyName);
        if (databasePerCompany) {
            asCompany(tenantId, () -> tenantDatabases.provision(tenantId, sharedDatabase.determineUrl(), sharedDatabase.determineUsername(),
                    sharedDatabase.determinePassword()));
        }
        String userId = asCompany(tenantId, () -> userAccountService.create(new UserAccountService.NewUser(null, request.getUsername(),
                request.getEmail(), passwordEncoder.encode(request.getPassword()), null, role)));
        platformDirectory.addLogin(request.getUsername(), request.getEmail(), tenantId);

        // A new company gets its settings record at sign-up (it used to be created lazily by the
        // Settings page, which failed validation and returned 500).
        asCompany(tenantId, () -> companySettingsRepository.save(CompanySettings.builder()
                .tenantId(tenantId)
                .companyName(companyName)
                .defaultCurrency("USD")
                .defaultLanguage("en")
                .defaultTimezone("UTC")
                .dateFormat("YYYY-MM-DD")
                .timeFormat("HH:mm:ss")
                .featureFlags("{}")
                .securityPolicy("{}")
                .build()));

        UserAccount account = asCompany(tenantId, () -> userAccountService.findById(userId).orElseThrow());
        return buildAuthResponse(new CompanyUser(tenantId, account));
    }

    public AuthResponse refreshToken(String refreshToken) {
        if (!jwtTokenProvider.validateToken(refreshToken)) {
            throw new BadRequestException("Invalid or expired refresh token");
        }
        if (!jwtTokenProvider.isRefreshToken(refreshToken)) {
            throw new BadRequestException("Token is not a refresh token");
        }
        return buildAuthResponse(requireUser(jwtTokenProvider.getUsernameFromToken(refreshToken)));
    }

    // ------------------------------------------------------------------ internals

    /** The user with this username, in its company's database. */
    private Optional<CompanyUser> findCompanyUser(String username) {
        return platformDirectory.companyOfUsername(username)
                .flatMap(tenantId -> asCompany(tenantId, () -> userAccountService.findByUsername(username))
                        .map(account -> new CompanyUser(tenantId, account)));
    }

    private CompanyUser requireUser(String username) {
        return findCompanyUser(username).orElseThrow(() -> new BadCredentialsException("User not found"));
    }

    /** Runs the work in the company's database (sign-in runs before anyone is signed in). */
    private static <T> T asCompany(UUID tenantId, Supplier<T> work) {
        return TenantContext.callAs(tenantId, work);
    }

    private AuthResponse buildAuthResponse(CompanyUser user) {
        UserAccount account = user.account();
        UUID tenantId = user.tenantId();
        String role = asCompany(tenantId, () -> userAccountService.roleOf(account.getUserId()));
        String token = jwtTokenProvider.generateToken(account.getUsername(), role, tenantId);
        String refreshToken = jwtTokenProvider.generateRefreshToken(account.getUsername(), role, tenantId);
        String tenantName = platformDirectory.companyName(tenantId).orElse(null);
        List<String> permissions = role == null ? List.of() : new ArrayList<>(asCompany(tenantId, () -> userAccountService.permissionsOf(role)));

        return AuthResponse.builder()
                .id(account.getUserId())
                .accessToken(token)
                .refreshToken(refreshToken)
                .tokenType("Bearer")
                .expiresIn(accessTokenTtlMs)
                .role(role)
                .username(account.getUsername())
                .tenantId(tenantId.toString())
                .tenantName(tenantName)
                .email(account.getEmailAddress())
                .fullName(account.getUserFullName() != null ? account.getUserFullName() : account.getUsername())
                .mfaRequired(false)
                .passwordResetRequired(false)
                .permissions(permissions)
                .build();
    }

    private static String randomRecoveryCode() {
        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < 10; i++) {
            if (i == 5) sb.append('-');
            sb.append(RECOVERY_ALPHABET.charAt(RANDOM.nextInt(RECOVERY_ALPHABET.length())));
        }
        return sb.toString();
    }

    private static String normaliseRecoveryCode(String code) {
        return code.replace("-", "").replace(" ", "").toUpperCase();
    }

    private static String sha256(String value) {
        try {
            return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256")
                    .digest(value.getBytes(StandardCharsets.UTF_8)));
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException(e);
        }
    }
}
