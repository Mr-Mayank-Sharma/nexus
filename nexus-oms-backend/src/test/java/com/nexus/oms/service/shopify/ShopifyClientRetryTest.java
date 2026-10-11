package com.nexus.oms.service.shopify;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.web.client.RestClientResponseException;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.jupiter.api.Assertions.*;

class ShopifyClientRetryTest {

    private final List<Long> pauses = new ArrayList<>();
    private final ShopifyClient client = new ShopifyClient(new ObjectMapper()) {
        @Override
        void pause(long millis) {
            pauses.add(millis);
        }
    };

    private static RestClientResponseException http(HttpStatus status, String retryAfter) {
        HttpHeaders headers = new HttpHeaders();
        if (retryAfter != null) headers.add("Retry-After", retryAfter);
        return new RestClientResponseException(status.name(), status.value(), status.name(), headers, new byte[0], null);
    }

    @Test
    void throttled_waitsForRetryAfter_thenSucceeds() {
        AtomicInteger calls = new AtomicInteger();

        String result = client.retrying(true, () -> {
            if (calls.incrementAndGet() < 3) throw http(HttpStatus.TOO_MANY_REQUESTS, "2.0");
            return "ok";
        });

        assertEquals("ok", result);
        assertEquals(3, calls.get());
        assertEquals(List.of(2000L, 2000L), pauses);
    }

    @Test
    void givesUpAfterMaxAttempts() {
        AtomicInteger calls = new AtomicInteger();

        assertThrows(RestClientResponseException.class, () -> client.retrying(true, () -> {
            calls.incrementAndGet();
            throw http(HttpStatus.TOO_MANY_REQUESTS, null);
        }));

        assertEquals(ShopifyClient.MAX_ATTEMPTS, calls.get());
        assertEquals(List.of(500L, 1000L, 2000L), pauses, "exponential without Retry-After");
    }

    @Test
    void transientServerErrors_retryForReads_butNotForWrites() {
        AtomicInteger readCalls = new AtomicInteger();
        client.retrying(true, () -> {
            if (readCalls.incrementAndGet() == 1) throw http(HttpStatus.BAD_GATEWAY, null);
            return "ok";
        });
        assertEquals(2, readCalls.get());

        AtomicInteger writeCalls = new AtomicInteger();
        assertThrows(RestClientResponseException.class, () -> client.retrying(false, () -> {
            writeCalls.incrementAndGet();
            throw http(HttpStatus.BAD_GATEWAY, null);
        }));
        assertEquals(1, writeCalls.get(), "a write that may have succeeded is never repeated");
    }

    @Test
    void clientErrors_neverRetry() {
        AtomicInteger calls = new AtomicInteger();

        assertThrows(RestClientResponseException.class, () -> client.retrying(true, () -> {
            calls.incrementAndGet();
            throw http(HttpStatus.UNPROCESSABLE_ENTITY, null);
        }));

        assertEquals(1, calls.get());
    }

    @Test
    void throttledWrites_areRetried_becauseTheyWereRejected() {
        AtomicInteger calls = new AtomicInteger();

        client.retrying(false, () -> {
            if (calls.incrementAndGet() == 1) throw http(HttpStatus.TOO_MANY_REQUESTS, "1");
            return "ok";
        });

        assertEquals(2, calls.get());
    }

    @Test
    void backoff_isCapped() {
        assertEquals(10_000L, ShopifyClient.backoffMillis("60", 1));
        assertEquals(500L, ShopifyClient.backoffMillis("garbage", 1));
    }
}
