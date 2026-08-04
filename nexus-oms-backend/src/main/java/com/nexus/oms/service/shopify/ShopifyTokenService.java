package com.nexus.oms.service.shopify;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.nexus.oms.exception.BadRequestException;
import com.nexus.oms.service.IntegrationStoreService;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientException;

import java.time.Instant;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

@Service
public class ShopifyTokenService {

    private static final long CACHE_TTL_SECONDS = 23 * 60 * 60;

    private final IntegrationStoreService storeService;
    private final ObjectMapper objectMapper;
    private final Map<UUID, TokenEntry> cache = new ConcurrentHashMap<>();

    public ShopifyTokenService(IntegrationStoreService storeService, ObjectMapper objectMapper) {
        this.storeService = storeService;
        this.objectMapper = objectMapper;
    }

    public String getAccessToken(UUID storeId) {
        TokenEntry cached = cache.get(storeId);
        if (cached != null && cached.expiresAt.isAfter(Instant.now())) {
            return cached.token;
        }

        String storedToken = storeService.getSetting(storeId, "access_token");
        String clientId = storeService.getSetting(storeId, "client_id");
        String clientSecret = storeService.getSetting(storeId, "client_secret");

        if (clientId == null || clientSecret == null) {
            cache.put(storeId, new TokenEntry(storedToken, Instant.now().plusSeconds(CACHE_TTL_SECONDS)));
            return storedToken;
        }

        String shopDomain = storeService.getSetting(storeId, "shop_domain");
        String refreshed = refresh(shopDomain, clientId, clientSecret);
        storeService.updateSetting(storeId, "access_token", refreshed);
        cache.put(storeId, new TokenEntry(refreshed, Instant.now().plusSeconds(CACHE_TTL_SECONDS)));
        return refreshed;
    }

    private String refresh(String shopDomain, String clientId, String clientSecret) {
        try {
            String url = "https://" + shopDomain + "/admin/oauth/access_token";
            String response = RestClient.builder()
                    .baseUrl(url)
                    .build()
                    .post()
                    .contentType(MediaType.APPLICATION_FORM_URLENCODED)
                    .body("grant_type=client_credentials&client_id=" + clientId + "&client_secret=" + clientSecret)
                    .retrieve()
                    .body(String.class);
            JsonNode json = objectMapper.readTree(response);
            if (!json.has("access_token")) {
                throw new BadRequestException("Shopify token refresh failed: " + json.toString());
            }
            return json.get("access_token").asText();
        } catch (RestClientException e) {
            throw new BadRequestException("Shopify token refresh error: " + e.getMessage());
        } catch (Exception e) {
            throw new BadRequestException("Shopify token refresh failed: " + e.getMessage());
        }
    }

    private record TokenEntry(String token, Instant expiresAt) {
    }
}
