package com.nexus.oms.config;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.context.annotation.Configuration;

import java.util.HashMap;
import java.util.Map;

@Configuration
@ConfigurationProperties(prefix = "sso")
public class SsoProviderConfig {

    private Map<String, Provider> providers = new HashMap<>();

    public Map<String, Provider> getProviders() {
        return providers;
    }

    public void setProviders(Map<String, Provider> providers) {
        this.providers = providers;
    }

    public Provider getProvider(String name) {
        return providers.get(name.toLowerCase());
    }

    public static class Provider {
        private String clientId;
        private String clientSecret;
        private String authorizeUrl;
        private String tokenUrl;
        private String userInfoUrl;
        private String scopes = "openid email profile";
        private String redirectUri = "http://localhost:8080/api/v1/auth/sso/{provider}/callback";
        /** Expected {@code iss} of ID tokens, e.g. https://accounts.google.com. Required for ID-token login. */
        private String issuer;
        /** JWKS endpoint used to verify ID-token signatures. Required for ID-token login. */
        private String jwkSetUri;
        /**
         * Link sign-ins to existing users by e-mail even when the IdP does not assert
         * {@code email_verified=true}. Only enable for a workforce IdP you control (Okta, Entra ID).
         */
        private boolean trustEmail = false;
        /**
         * When set, first-time SSO users with no matching account are created as VIEWER in this
         * tenant. When unset (default), unknown identities are rejected and must be invited.
         */
        private String autoProvisionTenantId;

        public String getClientId() { return clientId; }
        public void setClientId(String clientId) { this.clientId = clientId; }
        public String getClientSecret() { return clientSecret; }
        public void setClientSecret(String clientSecret) { this.clientSecret = clientSecret; }
        public String getAuthorizeUrl() { return authorizeUrl; }
        public void setAuthorizeUrl(String authorizeUrl) { this.authorizeUrl = authorizeUrl; }
        public String getTokenUrl() { return tokenUrl; }
        public void setTokenUrl(String tokenUrl) { this.tokenUrl = tokenUrl; }
        public String getUserInfoUrl() { return userInfoUrl; }
        public void setUserInfoUrl(String userInfoUrl) { this.userInfoUrl = userInfoUrl; }
        public String getScopes() { return scopes; }
        public void setScopes(String scopes) { this.scopes = scopes; }
        public String getRedirectUri() { return redirectUri; }
        public void setRedirectUri(String redirectUri) { this.redirectUri = redirectUri; }
        public String getIssuer() { return issuer; }
        public void setIssuer(String issuer) { this.issuer = issuer; }
        public String getJwkSetUri() { return jwkSetUri; }
        public void setJwkSetUri(String jwkSetUri) { this.jwkSetUri = jwkSetUri; }
        public boolean isTrustEmail() { return trustEmail; }
        public void setTrustEmail(boolean trustEmail) { this.trustEmail = trustEmail; }
        public String getAutoProvisionTenantId() { return autoProvisionTenantId; }
        public void setAutoProvisionTenantId(String autoProvisionTenantId) { this.autoProvisionTenantId = autoProvisionTenantId; }
    }
}
