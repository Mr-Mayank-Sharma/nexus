package com.nexus.oms.service.auth;

import com.nexus.oms.config.SsoProviderConfig;
import com.nimbusds.jose.JWSAlgorithm;
import com.nimbusds.jose.JWSHeader;
import com.nimbusds.jose.crypto.RSASSASigner;
import com.nimbusds.jwt.JWTClaimsSet;
import com.nimbusds.jwt.SignedJWT;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.security.oauth2.jwt.NimbusJwtDecoder;

import java.security.KeyPair;
import java.security.KeyPairGenerator;
import java.security.interfaces.RSAPublicKey;
import java.util.Base64;
import java.util.Date;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

class IdTokenVerifierTest {

    private static final String ISSUER = "https://idp.example.com";
    private static final String CLIENT_ID = "nexus-client";

    private KeyPair trustedKey;
    private KeyPair attackerKey;
    private IdTokenVerifier verifier;

    @BeforeEach
    void setUp() throws Exception {
        KeyPairGenerator gen = KeyPairGenerator.getInstance("RSA");
        gen.initialize(2048);
        trustedKey = gen.generateKeyPair();
        attackerKey = gen.generateKeyPair();

        SsoProviderConfig.Provider cfg = new SsoProviderConfig.Provider();
        cfg.setClientId(CLIENT_ID);
        cfg.setIssuer(ISSUER);
        cfg.setJwkSetUri("https://idp.example.com/jwks");
        SsoProviderConfig config = new SsoProviderConfig();
        config.setProviders(Map.of("okta", cfg));

        verifier = new IdTokenVerifier(config,
                p -> NimbusJwtDecoder.withPublicKey((RSAPublicKey) trustedKey.getPublic()).build());
    }

    @Test
    void validToken_IsAccepted() throws Exception {
        var identity = verifier.verify("okta", sign(trustedKey, claims(ISSUER, CLIENT_ID, 300).build()));
        assertEquals("user-123", identity.subject());
        assertEquals("u@acme.com", identity.email());
        assertTrue(identity.emailVerified());
    }

    @Test
    void unsignedForgery_IsRejected() {
        String payload = Base64.getUrlEncoder().withoutPadding()
                .encodeToString("{\"sub\":\"x\",\"email\":\"admin@victim.com\"}".getBytes());
        assertThrows(IdTokenVerifier.InvalidIdTokenException.class,
                () -> verifier.verify("okta", "eyJhbGciOiJub25lIn0." + payload + ".sig"));
    }

    @Test
    void tokenSignedByAnotherKey_IsRejected() throws Exception {
        assertThrows(IdTokenVerifier.InvalidIdTokenException.class,
                () -> verifier.verify("okta", sign(attackerKey, claims(ISSUER, CLIENT_ID, 300).build())));
    }

    @Test
    void wrongIssuer_IsRejected() throws Exception {
        assertThrows(IdTokenVerifier.InvalidIdTokenException.class,
                () -> verifier.verify("okta", sign(trustedKey, claims("https://evil.example.com", CLIENT_ID, 300).build())));
    }

    @Test
    void tokenForAnotherClient_IsRejected() throws Exception {
        assertThrows(IdTokenVerifier.InvalidIdTokenException.class,
                () -> verifier.verify("okta", sign(trustedKey, claims(ISSUER, "some-other-app", 300).build())));
    }

    @Test
    void expiredToken_IsRejected() throws Exception {
        assertThrows(IdTokenVerifier.InvalidIdTokenException.class,
                () -> verifier.verify("okta", sign(trustedKey, claims(ISSUER, CLIENT_ID, -600).build())));
    }

    @Test
    void providerWithoutIssuerOrJwks_IsRejected() {
        SsoProviderConfig.Provider bare = new SsoProviderConfig.Provider();
        bare.setClientId(CLIENT_ID);
        SsoProviderConfig config = new SsoProviderConfig();
        config.setProviders(Map.of("google", bare));
        IdTokenVerifier unconfigured = new IdTokenVerifier(config);

        assertThrows(IdTokenVerifier.InvalidIdTokenException.class, () -> unconfigured.verify("google", "a.b.c"));
    }

    private static JWTClaimsSet.Builder claims(String issuer, String audience, int expiresInSeconds) {
        Date now = new Date();
        return new JWTClaimsSet.Builder()
                .issuer(issuer)
                .audience(audience)
                .subject("user-123")
                .claim("email", "u@acme.com")
                .claim("email_verified", true)
                .issueTime(new Date(now.getTime() - 60_000))
                .expirationTime(new Date(now.getTime() + expiresInSeconds * 1000L));
    }

    private static String sign(KeyPair key, JWTClaimsSet claims) throws Exception {
        SignedJWT jwt = new SignedJWT(new JWSHeader(JWSAlgorithm.RS256), claims);
        jwt.sign(new RSASSASigner(key.getPrivate()));
        return jwt.serialize();
    }
}
