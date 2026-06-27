package com.nexus.oms.integration.protocol;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.*;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestTemplate;

import java.net.URI;
import java.util.*;

@Component
public class RestProtocolAdapter {

    private static final Logger log = LoggerFactory.getLogger(RestProtocolAdapter.class);
    private final ObjectMapper objectMapper = new ObjectMapper();

    public JsonNode get(String baseUrl, String path, Map<String, String> headers, Map<String, String> queryParams) {
        RestTemplate rt = buildRestTemplate(headers);
        String url = buildUrl(baseUrl, path, queryParams);
        ResponseEntity<String> res = rt.exchange(url, HttpMethod.GET, null, String.class);
        return parseResponse(res, url);
    }

    public JsonNode post(String baseUrl, String path, Map<String, String> headers, Object body) {
        RestTemplate rt = buildRestTemplate(headers);
        String url = baseUrl + path;
        HttpEntity<?> entity = new HttpEntity<>(body, toHttpHeaders(headers));
        ResponseEntity<String> res = rt.exchange(url, HttpMethod.POST, entity, String.class);
        return parseResponse(res, url);
    }

    public JsonNode put(String baseUrl, String path, Map<String, String> headers, Object body) {
        RestTemplate rt = buildRestTemplate(headers);
        String url = baseUrl + path;
        HttpEntity<?> entity = new HttpEntity<>(body, toHttpHeaders(headers));
        ResponseEntity<String> res = rt.exchange(url, HttpMethod.PUT, entity, String.class);
        return parseResponse(res, url);
    }

    public JsonNode delete(String baseUrl, String path, Map<String, String> headers) {
        RestTemplate rt = buildRestTemplate(headers);
        String url = baseUrl + path;
        ResponseEntity<String> res = rt.exchange(url, HttpMethod.DELETE, null, String.class);
        return parseResponse(res, url);
    }

    public List<JsonNode> paginatedGet(String baseUrl, String path, Map<String, String> headers,
                                        Map<String, String> queryParams, String itemsField,
                                        String nextPageTokenField, int maxPages) {
        List<JsonNode> allItems = new ArrayList<>();
        int page = 0;
        String cursor = null;

        while (page < maxPages) {
            Map<String, String> params = new LinkedHashMap<>(queryParams);
            if (cursor != null) params.put(nextPageTokenField, cursor);

            JsonNode response = get(baseUrl, path, headers, params);
            JsonNode items = itemsField != null && !itemsField.isBlank() ? response.get(itemsField) : response;

            if (items != null && items.isArray()) {
                items.forEach(allItems::add);
            }

            cursor = extractNextPageToken(response, nextPageTokenField);
            if (cursor == null) break;
            page++;
        }

        return allItems;
    }

    private String buildUrl(String baseUrl, String path, Map<String, String> queryParams) {
        StringBuilder url = new StringBuilder(baseUrl);
        if (!path.startsWith("/")) url.append("/");
        url.append(path);

        if (queryParams != null && !queryParams.isEmpty()) {
            url.append("?");
            queryParams.forEach((k, v) -> url.append(k).append("=").append(encode(v)).append("&"));
            url.setLength(url.length() - 1);
        }
        return url.toString();
    }

    private RestTemplate buildRestTemplate(Map<String, String> headers) {
        return new RestTemplate();
    }

    private HttpHeaders toHttpHeaders(Map<String, String> h) {
        HttpHeaders headers = new HttpHeaders();
        if (h != null) h.forEach(headers::set);
        headers.setContentType(MediaType.APPLICATION_JSON);
        headers.setAccept(List.of(MediaType.APPLICATION_JSON));
        return headers;
    }

    private JsonNode parseResponse(ResponseEntity<String> res, String url) {
        try {
            if (res.getStatusCode().is2xxSuccessful() && res.getBody() != null) {
                return objectMapper.readTree(res.getBody());
            }
            log.warn("Non-2xx response from {}: {}", url, res.getStatusCode());
            ObjectNode errorNode = objectMapper.createObjectNode();
            errorNode.put("statusCode", res.getStatusCode().value());
            errorNode.put("body", res.getBody());
            errorNode.put("error", "HTTP " + res.getStatusCode());
            return errorNode;
        } catch (Exception e) {
            log.error("Failed to parse response from {}", url, e);
            ObjectNode errorNode = objectMapper.createObjectNode();
            errorNode.put("error", e.getMessage());
            return errorNode;
        }
    }

    private String extractNextPageToken(JsonNode response, String field) {
        if (field == null || field.isBlank()) return null;
        JsonNode token = response.get(field);
        return token != null && !token.isNull() ? token.asText() : null;
    }

    private String encode(String s) {
        try { return java.net.URLEncoder.encode(s, "UTF-8"); } catch (Exception e) { return s; }
    }
}
