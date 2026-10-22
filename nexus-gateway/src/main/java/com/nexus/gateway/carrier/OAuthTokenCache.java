package com.nexus.gateway.carrier;

import java.time.Instant;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.Supplier;

/**
 * OAuth access tokens per carrier account, reused until a minute before they expire (carriers limit how often a
 * token may be requested). One cache per carrier.
 */
final class OAuthTokenCache {

    /** A token is renewed this long before it expires, so a request never goes out with one that just ran out. */
    static final long RENEW_BEFORE_SECONDS = 60;
    /** How long a token lasts when the carrier doesn't say. */
    static final long DEFAULT_LIFETIME_SECONDS = 3600;

    /** A token as the carrier gave it: the value and how many seconds it lasts. */
    record IssuedToken(String accessToken, long expiresInSeconds) {
    }

    private record CachedToken(String accessToken, long expiresAtEpochSecond) {
        boolean stillGood(long nowEpochSecond) {
            return expiresAtEpochSecond > nowEpochSecond + RENEW_BEFORE_SECONDS;
        }
    }

    private final Map<String, CachedToken> tokenByAccount = new ConcurrentHashMap<>();

    /** The account's cached token, or a new one from {@code requestToken} when there is none or it is about to expire. */
    String tokenFor(String accountId, Supplier<IssuedToken> requestToken) {
        long now = Instant.now().getEpochSecond();
        CachedToken cached = tokenByAccount.get(accountId);
        if (cached != null && cached.stillGood(now)) {
            return cached.accessToken();
        }
        IssuedToken issued = requestToken.get();
        tokenByAccount.put(accountId, new CachedToken(issued.accessToken(), now + issued.expiresInSeconds()));
        return issued.accessToken();
    }
}
