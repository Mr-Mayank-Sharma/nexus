package com.nexus.oms.service;

import com.nexus.oms.config.SsoProviderConfig;
import com.nexus.oms.dto.*;
import com.nexus.oms.entity.CompanySettings;
import com.nexus.oms.entity.NxUser;
import com.nexus.oms.exception.BadRequestException;
import com.nexus.oms.repository.CompanySettingsRepository;
import com.nexus.oms.repository.UserRepository;
import com.nexus.oms.security.JwtTokenProvider;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.security.authentication.BadCredentialsException;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.util.UriComponentsBuilder;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.LocalDateTime;
import java.util.*;
import com.fasterxml.jackson.databind.ObjectMapper;

@Service
public class AuthService {

    private static final Set<String> ALLOWED_ROLES = Set.of("ADMIN", "CEO", "OPS", "WAREHOUSE", "VIEWER", "FINANCE", "LOGISTICS_MANAGER");
    private static final Set<String> SSO_PROVIDERS = Set.of("okta", "auth0", "google", "microsoft");
    private static final long MFA_TOKEN_EXPIRY_MS = 300_000;
    private static final long SSO_STATE_EXPIRY_MS = 600_000;

    private final UserRepository userRepository;
    private final PasswordEncoder passwordEncoder;
    private final JwtTokenProvider jwtTokenProvider;
    private final CompanySettingsRepository companySettingsRepository;
    private final SsoProviderConfig ssoProviderConfig;
    private final ObjectMapper objectMapper;
    private final HttpClient httpClient;

    @Value("${app.frontend-url:http://localhost:5173}")
    private String frontendUrl;

    private final Map<String, MfaSession> mfaSessions = new HashMap<>();
    private final Map<String, PasswordResetToken> resetTokens = new HashMap<>();
    private final Map<String, SsoState> ssoStates = new HashMap<>();

    public AuthService(UserRepository userRepository,
                       PasswordEncoder passwordEncoder,
                       JwtTokenProvider jwtTokenProvider,
                       CompanySettingsRepository companySettingsRepository,
                       SsoProviderConfig ssoProviderConfig,
                       ObjectMapper objectMapper) {
        this.userRepository = userRepository;
        this.passwordEncoder = passwordEncoder;
        this.jwtTokenProvider = jwtTokenProvider;
        this.companySettingsRepository = companySettingsRepository;
        this.ssoProviderConfig = ssoProviderConfig;
        this.objectMapper = objectMapper;
        this.httpClient = HttpClient.newHttpClient();
    }

    public AuthResponse authenticate(LoginRequest request) {
        NxUser user = userRepository.findByUsername(request.getUsername())
                .orElseThrow(() -> new BadCredentialsException("Invalid username or password"));

        if (!passwordEncoder.matches(request.getPassword(), user.getPasswordHash())) {
            throw new BadCredentialsException("Invalid username or password");
        }

        CompanySettings settings = companySettingsRepository.findByTenantId(user.getTenantId()).orElse(null);
        boolean mfaEnabled = false;
        if (settings != null && settings.getSecurityPolicy() != null && settings.getSecurityPolicy().contains("mfa")) {
            try {
                com.fasterxml.jackson.databind.ObjectMapper om = new com.fasterxml.jackson.databind.ObjectMapper();
                com.fasterxml.jackson.databind.JsonNode policy = om.readTree(settings.getSecurityPolicy());
                mfaEnabled = policy.has("mfaEnabled") && policy.get("mfaEnabled").asBoolean();
            } catch (Exception ignored) {}
        }

        if (mfaEnabled) {
            String mfaToken = UUID.randomUUID().toString();
            mfaSessions.put(mfaToken, new MfaSession(user.getUsername(), System.currentTimeMillis()));
            return AuthResponse.builder()
                    .mfaRequired(true)
                    .mfaToken(mfaToken)
                    .username(user.getUsername())
                    .tenantId(user.getTenantId().toString())
                    .build();
        }

        return buildAuthResponse(user);
    }

    public AuthResponse verifyMfa(MfaVerificationRequest request) {
        MfaSession session = mfaSessions.get(request.getMfaToken());
        if (session == null) {
            throw new BadRequestException("Invalid or expired MFA session");
        }
        if (System.currentTimeMillis() - session.createdAt > MFA_TOKEN_EXPIRY_MS) {
            mfaSessions.remove(request.getMfaToken());
            throw new BadRequestException("MFA session expired. Please login again.");
        }

        String expectedTotp = generateTotpForUser(session.username);
        if (!request.getTotpCode().equals(expectedTotp)) {
            throw new BadRequestException("Invalid verification code");
        }

        mfaSessions.remove(request.getMfaToken());
        NxUser user = userRepository.findByUsername(session.username)
                .orElseThrow(() -> new BadCredentialsException("User not found"));
        return buildAuthResponse(user);
    }

    public AuthResponse ssoLogin(SsoLoginRequest request) {
        String provider = request.getProvider().toLowerCase();
        if (!SSO_PROVIDERS.contains(provider)) {
            throw new BadRequestException("Unsupported SSO provider: " + provider);
        }

        String email = extractEmailFromIdToken(request.getIdToken(), provider);
        if (email == null) {
            throw new BadCredentialsException("Invalid ID token");
        }

        String username = email.split("@")[0];
        NxUser user = userRepository.findByUsername(username).orElse(null);

        if (user == null) {
            UUID tenantId = request.getTenantId() != null
                    ? UUID.fromString(request.getTenantId())
                    : UUID.randomUUID();
            user = NxUser.builder()
                    .username(username)
                    .passwordHash(passwordEncoder.encode(UUID.randomUUID().toString()))
                    .email(email)
                    .role("VIEWER")
                    .tenantId(tenantId)
                    .build();
            userRepository.save(user);
        }

        return buildAuthResponse(user);
    }

    @Transactional
    public void forgotPassword(ForgotPasswordRequest request) {
        Optional<NxUser> userOpt = userRepository.findByUsername(request.getEmail());
        if (userOpt.isEmpty()) {
            userOpt = userRepository.findAll().stream()
                    .filter(u -> request.getEmail().equals(u.getEmail()))
                    .findFirst();
        }
        if (userOpt.isEmpty()) {
            return;
        }

        String token = UUID.randomUUID().toString();
        resetTokens.put(token, new PasswordResetToken(userOpt.get().getUsername(), System.currentTimeMillis()));
    }

    @Transactional
    public void resetPassword(ResetPasswordRequest request) {
        PasswordResetToken prt = resetTokens.get(request.getToken());
        if (prt == null) {
            throw new BadRequestException("Invalid or expired reset token");
        }
        if (System.currentTimeMillis() - prt.createdAt > 3_600_000) {
            resetTokens.remove(request.getToken());
            throw new BadRequestException("Reset token expired");
        }

        NxUser user = userRepository.findByUsername(prt.username)
                .orElseThrow(() -> new BadCredentialsException("User not found"));
        user.setPasswordHash(passwordEncoder.encode(request.getNewPassword()));
        userRepository.save(user);
        resetTokens.remove(request.getToken());
    }

    public List<CompanySettings> getAllTenants() {
        return companySettingsRepository.findAll();
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

    public String generateSsoAuthorizationUrl(String provider, String tenantId) {
        provider = provider.toLowerCase();
        if (!SSO_PROVIDERS.contains(provider)) {
            throw new BadRequestException("Unsupported SSO provider: " + provider);
        }

        SsoProviderConfig.Provider cfg = ssoProviderConfig.getProvider(provider);
        if (cfg == null || cfg.getClientId() == null) {
            throw new BadRequestException("SSO provider " + provider + " is not configured");
        }

        String state = UUID.randomUUID().toString();
        ssoStates.put(state, new SsoState(provider, tenantId, System.currentTimeMillis()));

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

    public String handleSsoCallback(String provider, String code, String state) {
        provider = provider.toLowerCase();
        SsoState ssoState = ssoStates.remove(state);
        if (ssoState == null) {
            return frontendUrl + "/login?error=sso_invalid_state";
        }
        if (System.currentTimeMillis() - ssoState.createdAt > SSO_STATE_EXPIRY_MS) {
            return frontendUrl + "/login?error=sso_state_expired";
        }

        SsoProviderConfig.Provider cfg = ssoProviderConfig.getProvider(provider);
        if (cfg == null) {
            return frontendUrl + "/login?error=sso_not_configured";
        }

        try {
            String redirectUri = cfg.getRedirectUri().replace("{provider}", provider);
            String tokenBody = "grant_type=authorization_code"
                    + "&code=" + java.net.URLEncoder.encode(code, "UTF-8")
                    + "&redirect_uri=" + java.net.URLEncoder.encode(redirectUri, "UTF-8")
                    + "&client_id=" + java.net.URLEncoder.encode(cfg.getClientId(), "UTF-8")
                    + "&client_secret=" + java.net.URLEncoder.encode(cfg.getClientSecret(), "UTF-8");

            HttpRequest tokenRequest = HttpRequest.newBuilder()
                    .uri(URI.create(cfg.getTokenUrl()))
                    .header("Content-Type", "application/x-www-form-urlencoded")
                    .POST(HttpRequest.BodyPublishers.ofString(tokenBody))
                    .build();

            HttpResponse<String> tokenResponse = httpClient.send(tokenRequest, HttpResponse.BodyHandlers.ofString());
            if (tokenResponse.statusCode() != 200) {
                return frontendUrl + "/login?error=sso_token_exchange_failed";
            }

            com.fasterxml.jackson.databind.JsonNode tokenJson = objectMapper.readTree(tokenResponse.body());
            String idToken = tokenJson.has("id_token") ? tokenJson.get("id_token").asText() : null;

            if (idToken == null && cfg.getUserInfoUrl() != null) {
                String accessToken = tokenJson.get("access_token").asText();
                HttpRequest userInfoRequest = HttpRequest.newBuilder()
                        .uri(URI.create(cfg.getUserInfoUrl()))
                        .header("Authorization", "Bearer " + accessToken)
                        .GET()
                        .build();
                HttpResponse<String> userInfoResponse = httpClient.send(userInfoRequest, HttpResponse.BodyHandlers.ofString());
                if (userInfoResponse.statusCode() == 200) {
                    com.fasterxml.jackson.databind.JsonNode userInfo = objectMapper.readTree(userInfoResponse.body());
                    String email = userInfo.has("email") ? userInfo.get("email").asText() : null;
                    if (email == null && userInfo.has("preferred_username")) {
                        email = userInfo.get("preferred_username").asText();
                    }
                    if (email != null) {
                        NxUser user = findOrCreateSsoUser(email, provider, ssoState.tenantId);
                        String jwt = jwtTokenProvider.generateToken(user.getUsername(), user.getRole(), user.getTenantId());
                        return frontendUrl + "/login?token=" + jwt + "&user=" + java.net.URLEncoder.encode(user.getUsername(), "UTF-8");
                    }
                }
                return frontendUrl + "/login?error=sso_userinfo_failed";
            }

            if (idToken != null) {
                String email = extractEmailFromIdToken(idToken, provider);
                if (email != null) {
                    NxUser user = findOrCreateSsoUser(email, provider, ssoState.tenantId);
                    String jwt = jwtTokenProvider.generateToken(user.getUsername(), user.getRole(), user.getTenantId());
                    return frontendUrl + "/login?token=" + jwt + "&user=" + java.net.URLEncoder.encode(user.getUsername(), "UTF-8");
                }
            }

            return frontendUrl + "/login?error=sso_no_email";
        } catch (Exception e) {
            return frontendUrl + "/login?error=sso_callback_failed";
        }
    }

    private NxUser findOrCreateSsoUser(String email, String provider, String tenantId) {
        String username = email.split("@")[0];
        NxUser user = userRepository.findByUsername(username).orElse(null);

        if (user == null) {
            UUID tid = tenantId != null ? UUID.fromString(tenantId) : UUID.randomUUID();
            user = NxUser.builder()
                    .username(username)
                    .passwordHash(passwordEncoder.encode(UUID.randomUUID().toString()))
                    .email(email)
                    .role("VIEWER")
                    .tenantId(tid)
                    .build();
            userRepository.save(user);
        }

        return user;
    }

    private AuthResponse buildAuthResponse(NxUser user) {
        String token = jwtTokenProvider.generateToken(user.getUsername(), user.getRole(), user.getTenantId());
        String tenantName = null;
        Optional<CompanySettings> settings = companySettingsRepository.findByTenantId(user.getTenantId());
        if (settings.isPresent()) {
            tenantName = settings.get().getCompanyName();
        }

        return AuthResponse.builder()
                .accessToken(token)
                .tokenType("Bearer")
                .expiresIn(86400000L)
                .role(user.getRole())
                .username(user.getUsername())
                .tenantId(user.getTenantId().toString())
                .tenantName(tenantName)
                .email(user.getEmail())
                .fullName(user.getUsername())
                .mfaRequired(false)
                .passwordResetRequired(false)
                .build();
    }

    private String extractEmailFromIdToken(String idToken, String provider) {
        try {
            String[] parts = idToken.split("\\.");
            if (parts.length == 3) {
                byte[] decoded = java.util.Base64.getUrlDecoder().decode(parts[1]);
                String json = new String(decoded);
                com.fasterxml.jackson.databind.ObjectMapper om = new com.fasterxml.jackson.databind.ObjectMapper();
                com.fasterxml.jackson.databind.JsonNode payload = om.readTree(json);
                if (payload.has("email")) {
                    return payload.get("email").asText();
                }
                if (payload.has("preferred_username")) {
                    return payload.get("preferred_username").asText();
                }
                return "sso_" + provider + "_" + UUID.randomUUID().toString().substring(0, 8);
            }
            return "sso_" + provider + "_" + UUID.randomUUID().toString().substring(0, 8);
        } catch (Exception e) {
            return "sso_" + provider + "_" + UUID.randomUUID().toString().substring(0, 8);
        }
    }

    private String generateTotpForUser(String username) {
        int code = Math.abs((username.hashCode() + (int)(System.currentTimeMillis() / 30000)) % 1000000);
        return String.format("%06d", code);
    }

    private static class SsoState {
        final String provider;
        final String tenantId;
        final long createdAt;
        SsoState(String provider, String tenantId, long createdAt) {
            this.provider = provider;
            this.tenantId = tenantId;
            this.createdAt = createdAt;
        }
    }

    private static class MfaSession {
        final String username;
        final long createdAt;
        MfaSession(String username, long createdAt) {
            this.username = username;
            this.createdAt = createdAt;
        }
    }

    private static class PasswordResetToken {
        final String username;
        final long createdAt;
        PasswordResetToken(String username, long createdAt) {
            this.username = username;
            this.createdAt = createdAt;
        }
    }
}
