package com.nexus.oms.service;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.nexus.oms.config.SsoProviderConfig;
import com.nexus.oms.dto.*;
import com.nexus.oms.entity.CompanySettings;
import com.nexus.oms.entity.NxAuthToken;
import com.nexus.oms.entity.NxUser;
import com.nexus.oms.entity.NxUserIdentity;
import com.nexus.oms.entity.RolePermission;
import com.nexus.oms.exception.BadRequestException;
import com.nexus.oms.integration.core.CredentialVault;
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
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.security.authentication.BadCredentialsException;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
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

    private final UserRepository userRepository;
    private final PasswordEncoder passwordEncoder;
    private final JwtTokenProvider jwtTokenProvider;
    private final CompanySettingsRepository companySettingsRepository;
    private final RolePermissionRepository rolePermissionRepository;
    private final SsoProviderConfig ssoProviderConfig;
    private final ObjectMapper objectMapper;
    private final AuthTokenStore tokenStore;
    private final TotpService totpService;
    private final IdTokenVerifier idTokenVerifier;
    private final UserIdentityRepository identityRepository;
    private final CredentialVault credentialVault;
    private final PasswordResetMailer resetMailer;
    private final HttpClient httpClient;

    @Value("${app.frontend-url:http://localhost:5173}")
    private String frontendUrl;

    @Value("${app.jwt.expiration-ms:900000}")
    private long accessTokenTtlMs = 900_000L;

    @org.springframework.beans.factory.annotation.Autowired
    public AuthService(UserRepository userRepository,
                       PasswordEncoder passwordEncoder,
                       JwtTokenProvider jwtTokenProvider,
                       CompanySettingsRepository companySettingsRepository,
                       RolePermissionRepository rolePermissionRepository,
                       SsoProviderConfig ssoProviderConfig,
                       ObjectMapper objectMapper,
                       AuthTokenStore tokenStore,
                       TotpService totpService,
                       IdTokenVerifier idTokenVerifier,
                       UserIdentityRepository identityRepository,
                       CredentialVault credentialVault,
                       PasswordResetMailer resetMailer) {
        this(userRepository, passwordEncoder, jwtTokenProvider, companySettingsRepository,
                rolePermissionRepository, ssoProviderConfig, objectMapper, tokenStore, totpService,
                idTokenVerifier, identityRepository, credentialVault, resetMailer, HttpClient.newHttpClient());
    }

    AuthService(UserRepository userRepository,
                PasswordEncoder passwordEncoder,
                JwtTokenProvider jwtTokenProvider,
                CompanySettingsRepository companySettingsRepository,
                RolePermissionRepository rolePermissionRepository,
                SsoProviderConfig ssoProviderConfig,
                ObjectMapper objectMapper,
                AuthTokenStore tokenStore,
                TotpService totpService,
                IdTokenVerifier idTokenVerifier,
                UserIdentityRepository identityRepository,
                CredentialVault credentialVault,
                PasswordResetMailer resetMailer,
                HttpClient httpClient) {
        this.userRepository = userRepository;
        this.passwordEncoder = passwordEncoder;
        this.jwtTokenProvider = jwtTokenProvider;
        this.companySettingsRepository = companySettingsRepository;
        this.rolePermissionRepository = rolePermissionRepository;
        this.ssoProviderConfig = ssoProviderConfig;
        this.objectMapper = objectMapper;
        this.tokenStore = tokenStore;
        this.totpService = totpService;
        this.idTokenVerifier = idTokenVerifier;
        this.identityRepository = identityRepository;
        this.credentialVault = credentialVault;
        this.resetMailer = resetMailer;
        this.httpClient = httpClient;
    }

    // ------------------------------------------------------------------ password login + MFA

    public AuthResponse authenticate(LoginRequest request) {
        NxUser user = userRepository.findByUsername(request.getUsername())
                .orElseThrow(() -> new BadCredentialsException("Invalid username or password"));

        if (!passwordEncoder.matches(request.getPassword(), user.getPasswordHash())) {
            throw new BadCredentialsException("Invalid username or password");
        }

        if (user.isMfaEnabled()) {
            String mfaToken = tokenStore.issue(AuthTokenStore.MFA_CHALLENGE, user.getUsername(), null, MFA_CHALLENGE_TTL);
            return AuthResponse.builder()
                    .mfaRequired(true)
                    .mfaToken(mfaToken)
                    .username(user.getUsername())
                    .tenantId(user.getTenantId().toString())
                    .build();
        }

        AuthResponse response = buildAuthResponse(user);
        response.setMfaEnrollmentRequired(tenantRequiresMfa(user.getTenantId()));
        return response;
    }

    public AuthResponse verifyMfa(MfaVerificationRequest request) {
        NxAuthToken challenge = tokenStore.find(AuthTokenStore.MFA_CHALLENGE, request.getMfaToken())
                .orElseThrow(() -> new BadRequestException("Invalid or expired MFA session. Please login again."));
        if (challenge.getAttempts() >= MAX_MFA_ATTEMPTS) {
            tokenStore.delete(challenge);
            throw new BadRequestException("Too many invalid codes. Please login again.");
        }

        NxUser user = userRepository.findByUsername(challenge.getUsername())
                .orElseThrow(() -> new BadCredentialsException("User not found"));

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

    /** Starts (or restarts) enrolment: stores a pending secret; MFA stays off until activated. */
    @Transactional
    public MfaEnrollmentResponse beginMfaEnrollment(String username) {
        NxUser user = requireUser(username);
        if (user.isMfaEnabled()) {
            throw new BadRequestException("MFA is already enabled. Disable it first to re-enrol.");
        }
        String secret = totpService.generateSecret();
        user.setMfaSecret(credentialVault.encrypt(secret));
        userRepository.save(user);
        String account = user.getEmail() != null ? user.getEmail() : user.getUsername();
        return MfaEnrollmentResponse.builder()
                .secret(secret)
                .otpauthUri(totpService.otpauthUri(MFA_ISSUER, account, secret))
                .build();
    }

    /** Confirms enrolment with a first code and returns single-use recovery codes (shown once). */
    @Transactional
    public List<String> activateMfa(String username, String code) {
        NxUser user = requireUser(username);
        if (user.isMfaEnabled()) {
            throw new BadRequestException("MFA is already enabled.");
        }
        if (user.getMfaSecret() == null) {
            throw new BadRequestException("Start MFA enrolment first.");
        }
        if (!totpService.verify(credentialVault.decrypt(user.getMfaSecret()), code)) {
            throw new BadRequestException("Invalid verification code");
        }
        List<String> codes = new ArrayList<>();
        List<String> hashes = new ArrayList<>();
        for (int i = 0; i < RECOVERY_CODE_COUNT; i++) {
            String recovery = randomRecoveryCode();
            codes.add(recovery);
            hashes.add(sha256(normaliseRecoveryCode(recovery)));
        }
        user.setMfaEnabled(true);
        user.setMfaRecoveryCodes(String.join(",", hashes));
        userRepository.save(user);
        return codes;
    }

    @Transactional
    public void disableMfa(String username, String code) {
        NxUser user = requireUser(username);
        if (!user.isMfaEnabled()) {
            return;
        }
        if (!checkTotpOrRecoveryCode(user, code)) {
            throw new BadRequestException("Invalid verification code");
        }
        user.setMfaEnabled(false);
        user.setMfaSecret(null);
        user.setMfaRecoveryCodes(null);
        userRepository.save(user);
    }

    private boolean checkTotpOrRecoveryCode(NxUser user, String code) {
        if (code == null || user.getMfaSecret() == null) {
            return false;
        }
        if (totpService.verify(credentialVault.decrypt(user.getMfaSecret()), code.trim())) {
            return true;
        }
        if (user.getMfaRecoveryCodes() == null || user.getMfaRecoveryCodes().isBlank()) {
            return false;
        }
        String candidate = sha256(normaliseRecoveryCode(code));
        List<String> remaining = new ArrayList<>(Arrays.asList(user.getMfaRecoveryCodes().split(",")));
        boolean matched = false;
        for (Iterator<String> it = remaining.iterator(); it.hasNext(); ) {
            String stored = it.next();
            if (MessageDigest.isEqual(stored.getBytes(StandardCharsets.US_ASCII), candidate.getBytes(StandardCharsets.US_ASCII))) {
                it.remove();
                matched = true;
                break;
            }
        }
        if (matched) {
            user.setMfaRecoveryCodes(String.join(",", remaining));
            userRepository.save(user);
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
    @Transactional
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
    @Transactional
    public String handleSsoCallback(String provider, String code, String state) {
        provider = provider.toLowerCase();
        Optional<NxAuthToken> ssoState = tokenStore.consume(AuthTokenStore.SSO_STATE, state);
        if (ssoState.isEmpty()) {
            return frontendUrl + "/login?error=sso_invalid_state";
        }
        if (!provider.equals(ssoState.get().getPayload())) {
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

            NxUser user = resolveSsoUser(provider, identity);
            String exchange = tokenStore.issue(AuthTokenStore.SSO_EXCHANGE, user.getUsername(), provider, SSO_EXCHANGE_TTL);
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
        NxAuthToken token = tokenStore.consume(AuthTokenStore.SSO_EXCHANGE, code)
                .orElseThrow(() -> new BadCredentialsException("Invalid or expired SSO code"));
        NxUser user = requireUser(token.getUsername());
        AuthResponse response = buildAuthResponse(user);
        response.setSsoProvider(token.getPayload());
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
     *   <li>an existing link on (provider, subject);</li>
     *   <li>otherwise exactly one user with the same e-mail, when the IdP asserts it is verified
     *       (or the provider is configured with {@code trust-email}); the link is then recorded;</li>
     *   <li>otherwise a new VIEWER in the provider's {@code auto-provision-tenant-id}, if set.</li>
     * </ol>
     * Anything else is rejected — no account is ever created in a caller-chosen tenant.
     */
    NxUser resolveSsoUser(String provider, VerifiedIdentity identity) {
        Optional<NxUserIdentity> link = identityRepository.findByProviderAndSubject(provider, identity.subject());
        if (link.isPresent()) {
            return userRepository.findById(link.get().getUserId())
                    .orElseThrow(() -> new BadCredentialsException("Linked account no longer exists"));
        }

        SsoProviderConfig.Provider cfg = ssoProviderConfig.getProvider(provider);
        boolean emailTrusted = identity.email() != null
                && (identity.emailVerified() || (cfg != null && cfg.isTrustEmail()));

        if (emailTrusted) {
            List<NxUser> matches = userRepository.findByEmailIgnoreCase(identity.email());
            if (matches.size() == 1) {
                NxUser user = matches.get(0);
                linkIdentity(user, provider, identity);
                return user;
            }
            if (matches.size() > 1) {
                log.warn("SSO e-mail matches {} accounts for provider {}; refusing to guess", matches.size(), provider);
                throw new BadCredentialsException("This e-mail belongs to more than one account. Ask an administrator to link it.");
            }
        }

        if (cfg != null && cfg.getAutoProvisionTenantId() != null && emailTrusted) {
            if (userRepository.existsByUsername(identity.email())) {
                throw new BadCredentialsException("An account with this username already exists.");
            }
            NxUser user = NxUser.builder()
                    .username(identity.email())
                    .passwordHash(passwordEncoder.encode(UUID.randomUUID().toString()))
                    .email(identity.email())
                    .role("VIEWER")
                    .tenantId(UUID.fromString(cfg.getAutoProvisionTenantId()))
                    .build();
            user = userRepository.save(user);
            linkIdentity(user, provider, identity);
            return user;
        }

        throw new BadCredentialsException("No Nexus account is linked to this sign-in. Ask your administrator for an invite.");
    }

    private void linkIdentity(NxUser user, String provider, VerifiedIdentity identity) {
        identityRepository.save(NxUserIdentity.builder()
                .userId(user.getId())
                .tenantId(user.getTenantId())
                .provider(provider)
                .subject(identity.subject())
                .email(identity.email())
                .build());
    }

    // ------------------------------------------------------------------ password reset

    @Transactional
    public void forgotPassword(ForgotPasswordRequest request) {
        String identifier = request.getEmail() == null ? "" : request.getEmail().trim();
        Optional<NxUser> userOpt = userRepository.findByUsername(identifier);
        if (userOpt.isEmpty()) {
            List<NxUser> byEmail = userRepository.findByEmailIgnoreCase(identifier);
            // Ambiguous e-mails get no link; the response is identical either way so the
            // endpoint does not reveal which accounts exist.
            userOpt = byEmail.size() == 1 ? Optional.of(byEmail.get(0)) : Optional.empty();
        }
        if (userOpt.isEmpty()) {
            return;
        }
        NxUser user = userOpt.get();
        tokenStore.revokeAll(AuthTokenStore.PASSWORD_RESET, user.getUsername());
        String token = tokenStore.issue(AuthTokenStore.PASSWORD_RESET, user.getUsername(), null, PASSWORD_RESET_TTL);
        resetMailer.sendResetLink(user.getEmail(), user.getUsername(), token);
    }

    @Transactional
    public void resetPassword(ResetPasswordRequest request) {
        NxAuthToken token = tokenStore.consume(AuthTokenStore.PASSWORD_RESET, request.getToken())
                .orElseThrow(() -> new BadRequestException("Invalid or expired reset token"));

        NxUser user = userRepository.findByUsername(token.getUsername())
                .orElseThrow(() -> new BadCredentialsException("User not found"));
        user.setPasswordHash(passwordEncoder.encode(request.getNewPassword()));
        userRepository.save(user);
        tokenStore.revokeAll(AuthTokenStore.MFA_CHALLENGE, user.getUsername());
    }

    // ------------------------------------------------------------------ registration, tenants, refresh

    /** The caller's own tenant only; listing every tenant leaked the customer list to anonymous users. */
    public List<CompanySettings> getTenantsFor(UUID tenantId) {
        if (tenantId == null) {
            return List.of();
        }
        return companySettingsRepository.findByTenantId(tenantId).map(List::of).orElse(List.of());
    }

    public AuthResponse register(RegisterRequest request) {
        if (userRepository.existsByUsername(request.getUsername())) {
            throw new BadRequestException("Username already exists");
        }

        String role = request.getRole() != null ? request.getRole().toUpperCase() : "VIEWER";
        if (!ALLOWED_ROLES.contains(role)) {
            throw new BadRequestException("Invalid role: " + role + ". Allowed roles: " + ALLOWED_ROLES);
        }

        UUID tenantId = UUID.randomUUID();

        NxUser user = NxUser.builder()
                .username(request.getUsername())
                .passwordHash(passwordEncoder.encode(request.getPassword()))
                .email(request.getEmail())
                .role(role)
                .tenantId(tenantId)
                .build();

        userRepository.save(user);

        return buildAuthResponse(user);
    }

    public AuthResponse refreshToken(String refreshToken) {
        if (!jwtTokenProvider.validateToken(refreshToken)) {
            throw new BadRequestException("Invalid or expired refresh token");
        }
        if (!jwtTokenProvider.isRefreshToken(refreshToken)) {
            throw new BadRequestException("Token is not a refresh token");
        }

        String username = jwtTokenProvider.getUsernameFromToken(refreshToken);
        NxUser user = userRepository.findByUsername(username)
                .orElseThrow(() -> new BadCredentialsException("User not found"));

        return buildAuthResponse(user);
    }

    private NxUser requireUser(String username) {
        return userRepository.findByUsername(username)
                .orElseThrow(() -> new BadCredentialsException("User not found"));
    }

    private AuthResponse buildAuthResponse(NxUser user) {
        String token = jwtTokenProvider.generateToken(user.getUsername(), user.getRole(), user.getTenantId());
        String tenantName = null;
        Optional<CompanySettings> settings = companySettingsRepository.findByTenantId(user.getTenantId());
        if (settings.isPresent()) {
            tenantName = settings.get().getCompanyName();
        }

        List<String> permissions = new ArrayList<>();
        try {
            List<RolePermission> rolePerms = rolePermissionRepository
                    .findByTenantIdAndRole(user.getTenantId(), user.getRole());
            for (RolePermission rp : rolePerms) {
                if (Boolean.TRUE.equals(rp.getCanView())) permissions.add(rp.getPermissionGroup() + ":view");
                if (Boolean.TRUE.equals(rp.getCanCreate())) permissions.add(rp.getPermissionGroup() + ":create");
                if (Boolean.TRUE.equals(rp.getCanEdit())) permissions.add(rp.getPermissionGroup() + ":edit");
                if (Boolean.TRUE.equals(rp.getCanDelete())) permissions.add(rp.getPermissionGroup() + ":delete");
            }
        } catch (Exception e) {
            log.warn("Failed to fetch permissions for user {} role {}: {}", user.getUsername(), user.getRole(), e.getMessage());
        }

        String refreshToken = jwtTokenProvider.generateRefreshToken(user.getUsername(), user.getRole(), user.getTenantId());

        return AuthResponse.builder()
                .id(user.getId() != null ? user.getId().toString() : null)
                .accessToken(token)
                .refreshToken(refreshToken)
                .tokenType("Bearer")
                .expiresIn(accessTokenTtlMs)
                .role(user.getRole())
                .username(user.getUsername())
                .tenantId(user.getTenantId().toString())
                .tenantName(tenantName)
                .email(user.getEmail())
                .fullName(user.getUsername())
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
