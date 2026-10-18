package com.nexus.oms.service.netsuite;

import org.junit.jupiter.api.Test;

import java.util.LinkedHashMap;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

/**
 * The expected values were computed with Python's own hmac/hashlib/urllib, independently of this code, so a
 * mistake in the signing or encoding here cannot hide. They prove the primitives; only a real NetSuite account
 * can prove the whole contract.
 */
class NetSuiteOAuthTest {

    private static final String URL = "https://1234567-sb1.suitetalk.api.netsuite.com/services/rest/query/v1/suiteql";

    private static Map<String, String> query() {
        Map<String, String> q = new LinkedHashMap<>();
        q.put("limit", "1000");
        q.put("offset", "0");
        return q;
    }

    @Test
    void theHeaderCarriesTheSignatureComputedIndependently() {
        String header = NetSuiteOAuth.authorization("POST", URL, query(), "1234567_SB1",
                "consumerkey123", "consumersecret456", "tokenid789", "tokensecret012", "nonce0123456789ab", 1700000000L);

        assertTrue(header.startsWith("OAuth realm=\"1234567_SB1\""), header);
        assertTrue(header.contains("oauth_signature=\"RJWtfv925s%2Bn0oSrJYZXjtnzAUqkoK6xlZidi67cQKo%3D\""), header);
        assertTrue(header.contains("oauth_signature_method=\"HMAC-SHA256\""));
        assertTrue(header.contains("oauth_nonce=\"nonce0123456789ab\""));
        assertTrue(header.contains("oauth_timestamp=\"1700000000\""));
    }

    @Test
    void theSecretsAreNeverPutInTheHeader() {
        String header = NetSuiteOAuth.authorization("POST", URL, query(), "1234567_SB1",
                "consumerkey123", "consumersecret456", "tokenid789", "tokensecret012", "n", 1L);

        assertFalse(header.contains("consumersecret456"));
        assertFalse(header.contains("tokensecret012"));
    }

    @Test
    void theBaseStringSortsAndEncodesTheParameters() {
        Map<String, String> oauth = new LinkedHashMap<>();
        oauth.put("oauth_version", "1.0");
        oauth.put("oauth_nonce", "a b");
        String base = NetSuiteOAuth.signatureBase("post", URL, query(), oauth);

        assertTrue(base.startsWith("POST&https%3A%2F%2F1234567-sb1.suitetalk.api.netsuite.com%2Fservices%2Frest%2Fquery%2Fv1%2Fsuiteql&"));
        // limit, oauth_nonce, oauth_version, offset: sorted, and the space is %20 (double-encoded as %2520), never "+"
        assertTrue(base.endsWith("limit%3D1000%26oauth_nonce%3Da%2520b%26oauth_version%3D1.0%26offset%3D0"), base);
    }

    @Test
    void accountIdsMapToTheRealmAndHost() {
        assertEquals("1234567_SB1", NetSuiteOAuth.realm("1234567_sb1"));
        assertEquals("1234567-sb1.suitetalk.api.netsuite.com", NetSuiteOAuth.host("1234567_SB1"));
        assertEquals("7654321.suitetalk.api.netsuite.com", NetSuiteOAuth.host("7654321"));
    }

    @Test
    void percentEncodingFollowsRfc3986() {
        assertEquals("a%20b", NetSuiteOAuth.enc("a b"));
        assertEquals("%2A", NetSuiteOAuth.enc("*"));
        assertEquals("~-._", NetSuiteOAuth.enc("~-._"));
        assertEquals("%2B%3D%2F", NetSuiteOAuth.enc("+=/"));
    }
}
