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
    }
}
