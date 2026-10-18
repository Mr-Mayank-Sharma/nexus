package com.nexus.oms.service.netsuite;

import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.util.*;

/**
 * Token-based authentication (OAuth 1.0a, HMAC-SHA256) for NetSuite's REST services, built from NetSuite's
 * public documentation. The signature base string is the HTTP method, the URL without its query, and the
 * sorted, percent-encoded oauth and query parameters, joined with "&"; the key is the consumer secret and
 * the token secret joined with "&".
 *
 * <p>Kept free of any HTTP client so its output can be pinned in a test.
 */
final class NetSuiteOAuth {

    private NetSuiteOAuth() {
    }

    /** Account ids look like {@code 1234567} or {@code 1234567_SB1}: the realm keeps the underscore, the host does not. */
    static String realm(String accountId) {
        return accountId.trim().toUpperCase(Locale.ROOT);
    }

    static String host(String accountId) {
        return accountId.trim().toLowerCase(Locale.ROOT).replace('_', '-') + ".suitetalk.api.netsuite.com";
    }

    static String authorization(String method, String baseUrl, Map<String, String> query, String accountId,
                                String consumerKey, String consumerSecret, String tokenId, String tokenSecret,
                                String nonce, long timestampSeconds) {
        Map<String, String> oauth = new TreeMap<>();
        oauth.put("oauth_consumer_key", consumerKey);
        oauth.put("oauth_token", tokenId);
        oauth.put("oauth_signature_method", "HMAC-SHA256");
        oauth.put("oauth_timestamp", Long.toString(timestampSeconds));
        oauth.put("oauth_nonce", nonce);
        oauth.put("oauth_version", "1.0");

        String base = signatureBase(method, baseUrl, query, oauth);
        String signature = hmac(enc(consumerSecret) + "&" + enc(tokenSecret), base);

        StringBuilder header = new StringBuilder("OAuth realm=\"").append(realm(accountId)).append('"');
        oauth.forEach((k, v) -> header.append(", ").append(k).append("=\"").append(enc(v)).append('"'));
        header.append(", oauth_signature=\"").append(enc(signature)).append('"');
        return header.toString();
    }

    static String signatureBase(String method, String baseUrl, Map<String, String> query, Map<String, String> oauth) {
        List<String[]> params = new ArrayList<>();
        oauth.forEach((k, v) -> params.add(new String[]{enc(k), enc(v)}));
        if (query != null) query.forEach((k, v) -> params.add(new String[]{enc(k), enc(v)}));
        params.sort(Comparator.<String[], String>comparing(p -> p[0]).thenComparing(p -> p[1]));
        StringBuilder normalized = new StringBuilder();
        for (String[] p : params) {
            if (normalized.length() > 0) normalized.append('&');
            normalized.append(p[0]).append('=').append(p[1]);
        }
        return method.toUpperCase(Locale.ROOT) + "&" + enc(baseUrl) + "&" + enc(normalized.toString());
    }

    /** RFC 3986 percent-encoding: unreserved characters stay, everything else is %XX, and a space is %20 not +. */
    static String enc(String value) {
        return URLEncoder.encode(value, StandardCharsets.UTF_8).replace("+", "%20").replace("*", "%2A").replace("%7E", "~");
    }

    static String hmac(String key, String data) {
        try {
            Mac mac = Mac.getInstance("HmacSHA256");
            mac.init(new SecretKeySpec(key.getBytes(StandardCharsets.UTF_8), "HmacSHA256"));
            return Base64.getEncoder().encodeToString(mac.doFinal(data.getBytes(StandardCharsets.UTF_8)));
        } catch (Exception e) {
            throw new IllegalStateException("Could not sign the NetSuite request", e);
        }
    }
}
