package com.nexus.oms.service.auth;

import com.nexus.oms.config.SsoProviderConfig;
import org.springframework.security.oauth2.core.DelegatingOAuth2TokenValidator;
import org.springframework.security.oauth2.core.OAuth2TokenValidator;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.jwt.JwtClaimNames;
import org.springframework.security.oauth2.jwt.JwtClaimValidator;
import org.springframework.security.oauth2.jwt.JwtDecoder;
import org.springframework.security.oauth2.jwt.JwtException;
import org.springframework.security.oauth2.jwt.JwtValidators;
import org.springframework.security.oauth2.jwt.NimbusJwtDecoder;
import org.springframework.stereotype.Service;

import java.util.Collection;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.Function;

/**
 * Verifies OpenID Connect ID tokens: signature against the provider's JWKS, plus
 * {@code iss}, {@code aud} (must contain our client id), {@code exp} and {@code nbf}.
 *
 * <p>Any failure throws {@link InvalidIdTokenException}; there is no fallback identity.</p>
 */
@Service
public class IdTokenVerifier {

    public record VerifiedIdentity(String subject, String email, boolean emailVerified) {}

    public static class InvalidIdTokenException extends RuntimeException {
        public InvalidIdTokenException(String message) { super(message); }
        public InvalidIdTokenException(String message, Throwable cause) { super(message, cause); }
    }

    private final SsoProviderConfig config;
    private final Function<SsoProviderConfig.Provider, JwtDecoder> decoderFactory;
    private final Map<String, JwtDecoder> decoders = new ConcurrentHashMap<>();

    @org.springframework.beans.factory.annotation.Autowired
    public IdTokenVerifier(SsoProviderConfig config) {
        this(config, IdTokenVerifier::jwksDecoder);
    }

    IdTokenVerifier(SsoProviderConfig config, Function<SsoProviderConfig.Provider, JwtDecoder> decoderFactory) {
        this.config = config;
        this.decoderFactory = decoderFactory;
    }

    public VerifiedIdentity verify(String provider, String idToken) {
        SsoProviderConfig.Provider cfg = config.getProvider(provider);
        if (cfg == null || cfg.getClientId() == null || cfg.getIssuer() == null || cfg.getJwkSetUri() == null) {
            throw new InvalidIdTokenException("SSO provider " + provider
                    + " is not configured for ID-token verification (client-id, issuer, jwk-set-uri)");
        }
        if (idToken == null || idToken.isBlank()) {
            throw new InvalidIdTokenException("Missing ID token");
        }
        JwtDecoder decoder = decoders.computeIfAbsent(provider.toLowerCase(), p -> withValidators(decoderFactory.apply(cfg), cfg));
        Jwt jwt;
        try {
            jwt = decoder.decode(idToken);
        } catch (JwtException e) {
            throw new InvalidIdTokenException("ID token rejected: " + e.getMessage(), e);
        }
        String subject = jwt.getSubject();
        if (subject == null || subject.isBlank()) {
            throw new InvalidIdTokenException("ID token has no subject");
        }
        Object verified = jwt.getClaims().get("email_verified");
        boolean emailVerified = Boolean.TRUE.equals(verified) || "true".equalsIgnoreCase(String.valueOf(verified));
        return new VerifiedIdentity(subject, jwt.getClaimAsString("email"), emailVerified);
    }

    private static JwtDecoder jwksDecoder(SsoProviderConfig.Provider cfg) {
        return NimbusJwtDecoder.withJwkSetUri(cfg.getJwkSetUri()).build();
    }

    private static JwtDecoder withValidators(JwtDecoder decoder, SsoProviderConfig.Provider cfg) {
        OAuth2TokenValidator<Jwt> validator = new DelegatingOAuth2TokenValidator<>(
                JwtValidators.createDefaultWithIssuer(cfg.getIssuer()),
                new JwtClaimValidator<Object>(JwtClaimNames.AUD, aud -> audienceContains(aud, cfg.getClientId())));
        if (decoder instanceof NimbusJwtDecoder nimbus) {
            nimbus.setJwtValidator(validator);
            return nimbus;
        }
        // Non-Nimbus decoders (tests) are wrapped so the same validators still apply.
        return token -> {
            Jwt jwt = decoder.decode(token);
            var result = validator.validate(jwt);
            if (result.hasErrors()) {
                throw new org.springframework.security.oauth2.jwt.JwtValidationException(
                        result.getErrors().iterator().next().getDescription(), result.getErrors());
            }
            return jwt;
        };
    }

    private static boolean audienceContains(Object aud, String clientId) {
        if (aud instanceof Collection<?> values) {
            return values.stream().anyMatch(v -> clientId.equals(String.valueOf(v)));
        }
        return aud != null && clientId.equals(String.valueOf(aud));
    }
}
