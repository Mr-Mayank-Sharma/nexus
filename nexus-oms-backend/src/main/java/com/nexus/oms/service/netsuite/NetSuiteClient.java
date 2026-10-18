package com.nexus.oms.service.netsuite;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.nexus.oms.exception.BadRequestException;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientResponseException;

import java.security.SecureRandom;
import java.util.*;

/**
 * Reads from NetSuite through SuiteQL over the REST services, signed with token-based authentication.
 * Read-only: nothing here writes to NetSuite.
 *
 * <p><b>Not yet run against a real account.</b> It follows NetSuite's published request format; the first
 * run against a sandbox is the test that matters, and the queries that depend on a customer's account
 * layout are settings, not code.
 */
@Component
public class NetSuiteClient {

    static final int PAGE_SIZE = 1000;
    private static final SecureRandom RANDOM = new SecureRandom();
    private final ObjectMapper mapper;

    public NetSuiteClient(ObjectMapper mapper) {
        this.mapper = mapper;
    }

    public record Credentials(String accountId, String consumerKey, String consumerSecret, String tokenId, String tokenSecret) {
        boolean complete() {
            return notBlank(accountId) && notBlank(consumerKey) && notBlank(consumerSecret) && notBlank(tokenId) && notBlank(tokenSecret);
        }

        private static boolean notBlank(String s) {
            return s != null && !s.isBlank();
        }
    }

    /** All rows of a SuiteQL query, read page by page. A row is a map of lower-cased column name to value. */
    public List<Map<String, Object>> query(Credentials c, String suiteQl) {
        requireComplete(c);
        List<Map<String, Object>> rows = new ArrayList<>();
        int offset = 0;
        while (true) {
            JsonNode page = postPage(c, suiteQl, offset);
            for (JsonNode item : page.path("items")) {
                Map<String, Object> row = new LinkedHashMap<>();
                item.fields().forEachRemaining(f -> {
                    if (!"links".equals(f.getKey())) row.put(f.getKey().toLowerCase(Locale.ROOT), toValue(f.getValue()));
                });
                rows.add(row);
            }
            if (!page.path("hasMore").asBoolean(false)) return rows;
            offset += PAGE_SIZE;
        }
    }

    /** A cheap call that proves the account id and the four token values work together. */
    public void testConnection(Credentials c) {
        query(c, "SELECT id FROM location FETCH FIRST 1 ROWS ONLY");
    }

    private JsonNode postPage(Credentials c, String suiteQl, int offset) {
        String baseUrl = "https://" + NetSuiteOAuth.host(c.accountId()) + "/services/rest/query/v1/suiteql";
        Map<String, String> query = new LinkedHashMap<>();
        query.put("limit", Integer.toString(PAGE_SIZE));
        query.put("offset", Integer.toString(offset));
        String auth = NetSuiteOAuth.authorization("POST", baseUrl, query, c.accountId(), c.consumerKey(), c.consumerSecret(),
                c.tokenId(), c.tokenSecret(), nonce(), System.currentTimeMillis() / 1000);
        try {
            String body = RestClient.create().post()
                    .uri(baseUrl + "?limit=" + PAGE_SIZE + "&offset=" + offset)
                    .header("Authorization", auth)
                    .header("Prefer", "transient")
                    .contentType(MediaType.APPLICATION_JSON)
                    .body(mapper.writeValueAsString(Map.of("q", suiteQl)))
                    .retrieve()
                    .body(String.class);
            return mapper.readTree(body);
        } catch (RestClientResponseException e) {
            throw new BadRequestException("NetSuite answered " + e.getStatusCode().value() + ": " + abbreviate(e.getResponseBodyAsString()));
        } catch (BadRequestException e) {
            throw e;
        } catch (Exception e) {
            throw new BadRequestException("Could not reach NetSuite: " + e.getMessage());
        }
    }

    private static void requireComplete(Credentials c) {
        if (c == null || !c.complete()) {
            throw new BadRequestException("NetSuite needs an account id, consumer key and secret, and token id and secret.");
        }
    }

    private static Object toValue(JsonNode n) {
        if (n == null || n.isNull()) return null;
        if (n.isNumber()) return n.numberValue();
        if (n.isBoolean()) return n.booleanValue();
        return n.asText();
    }

    private static String nonce() {
        byte[] b = new byte[10];
        RANDOM.nextBytes(b);
        StringBuilder sb = new StringBuilder();
        for (byte x : b) sb.append(String.format("%02x", x));
        return sb.toString();
    }

    private static String abbreviate(String s) {
        return s == null ? "" : (s.length() > 300 ? s.substring(0, 300) : s);
    }
}
