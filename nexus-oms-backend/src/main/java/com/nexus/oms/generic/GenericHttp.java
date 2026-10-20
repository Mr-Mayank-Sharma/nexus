package com.nexus.oms.generic;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.nexus.oms.exception.BadRequestException;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.net.InetAddress;
import java.net.URI;
import java.net.URLEncoder;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.time.*;
import java.time.format.DateTimeFormatter;
import java.util.*;
import java.util.function.Consumer;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Talks to the outside system of a generic connection: pages through records (import) and sends batches (export).
 * Guard rails: no redirects, private and internal addresses refused unless explicitly allowed, a timeout per call,
 * retries with back-off on 429 and 5xx (honouring Retry-After up to 30 s), at most 10 MB per response and a cap on pages.
 */
@Component
public class GenericHttp {

    static final int MAX_BYTES = 10 * 1024 * 1024;
    private static final Pattern NEXT_LINK = Pattern.compile("<([^>]+)>\\s*;\\s*rel=\"?next\"?");

    private final HttpClient client;
    private final boolean allowPrivateNetwork;
    private Sleeper sleeper = ms -> Thread.sleep(ms);

    public GenericHttp(@Value("${nexus.generic.allow-private-network:false}") boolean allowPrivateNetwork) {
        this.allowPrivateNetwork = allowPrivateNetwork;
        this.client = HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(15)).followRedirects(HttpClient.Redirect.NEVER).build();
    }

    interface Sleeper { void sleep(long ms) throws InterruptedException; }

    void setSleeper(Sleeper s) { this.sleeper = s; }

    public record Page(int number, String url, int records) {}

    /**
     * Fetches pages and hands each page's records to {@code sink}. Returns what was fetched, page by page.
     * @param since only records changed after this (when the config names an incremental parameter); null = all
     * @param pageLimit stop after this many pages (preview uses 1)
     */
    public List<Page> fetch(GenericConfig c, String secret, Instant since, int pageLimit, Consumer<List<JsonNode>> sink) {
        Map<String, String> query = new LinkedHashMap<>(c.query());
        if (since != null && !c.sinceParam().isBlank()) query.put(c.sinceParam(), formatSince(since, c.sinceFormat()));
        if ("PAGE".equals(c.pageType()) && !c.sizeParam().isBlank()) query.put(c.sizeParam(), String.valueOf(c.pageSize()));
        if ("OFFSET".equals(c.pageType()) && !c.sizeParam().isBlank()) query.put(c.sizeParam(), String.valueOf(c.pageSize()));

        List<Page> pages = new ArrayList<>();
        int limit = Math.min(pageLimit, c.maxPages());
        int page = c.startPage(), offset = 0;
        String cursor = null, nextUrl = null;
        for (int n = 1; n <= limit; n++) {
            String url;
            if (nextUrl != null) url = nextUrl;
            else {
                Map<String, String> q = new LinkedHashMap<>(query);
                switch (c.pageType()) {
                    case "PAGE" -> q.put(c.pageParam(), String.valueOf(page));
                    case "OFFSET" -> q.put(c.offsetParam(), String.valueOf(offset));
                    case "CURSOR" -> { if (cursor != null) q.put(c.cursorParam(), cursor); }
                    default -> { }
                }
                url = buildUrl(c, q, secret);
            }
            Response r = send(c, secret, url, "GET".equals(c.method()) ? null : "{}");
            JsonNode body = readJson(r.body);
            JsonNode arr = JsonPaths.get(body, c.recordsPath());
            if (!arr.isArray()) {
                throw new BadRequestException("Page " + n + ": nothing at recordsPath '" + c.recordsPath() + "' is a list (got "
                        + (arr.isMissingNode() ? "nothing" : arr.getNodeType().toString().toLowerCase()) + ")");
            }
            List<JsonNode> records = new ArrayList<>();
            arr.forEach(records::add);
            pages.add(new Page(n, redact(url, c), records.size()));
            sink.accept(records);
            if (records.isEmpty()) break;
            switch (c.pageType()) {
                case "PAGE" -> { if (records.size() < c.pageSize() && !c.sizeParam().isBlank()) return pages; page++; }
                case "OFFSET" -> { if (records.size() < c.pageSize()) return pages; offset += records.size(); }
                case "CURSOR" -> {
                    JsonNode cur = JsonPaths.get(body, c.cursorPath());
                    if (cur.isMissingNode() || cur.isNull() || cur.asText().isBlank()) return pages;
                    cursor = cur.asText();
                }
                case "LINK_HEADER" -> {
                    nextUrl = nextLink(r.linkHeader);
                    if (nextUrl == null) return pages;
                    checkSameHost(c, nextUrl);
                }
                default -> { return pages; }
            }
        }
        return pages;
    }

    /** Sends one export payload. Returns the HTTP status; throws with the response text on failure. */
    public int send(GenericConfig c, String secret, String jsonBody) {
        return send(c, secret, buildUrl(c, c.query(), secret), jsonBody).status;
    }

    record Response(int status, String body, String linkHeader) {}

    private Response send(GenericConfig c, String secret, String url, String body) {
        URI uri = URI.create(url);
        guardHost(uri.getHost());
        HttpRequest.Builder b = HttpRequest.newBuilder(uri).timeout(Duration.ofSeconds(30)).header("Accept", "application/json");
        c.headers().forEach(b::header);
        switch (c.authType()) {
            case "BASIC" -> b.header("Authorization", "Basic " + Base64.getEncoder().encodeToString(
                    (c.authUsername() + ":" + nz(secret)).getBytes(StandardCharsets.UTF_8)));
            case "BEARER" -> b.header("Authorization", "Bearer " + nz(secret));
            case "HEADER" -> b.header(c.authHeaderName(), nz(secret));
            default -> { }
        }
        if (body == null) b.GET();
        else b.header("Content-Type", "application/json").method(c.method(), HttpRequest.BodyPublishers.ofString(body));
        HttpRequest req = b.build();

        int attempt = 0;
        while (true) {
            attempt++;
            HttpResponse<byte[]> res;
            try {
                res = client.send(req, HttpResponse.BodyHandlers.ofByteArray());
            } catch (java.io.IOException e) {
                if (attempt < 3) { pause(1000L * attempt); continue; }
                throw new BadRequestException("Could not reach " + uri.getHost() + ": " + e.getMessage());
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                throw new BadRequestException("Interrupted");
            }
            int s = res.statusCode();
            if ((s == 429 || s >= 500) && attempt < 4) {
                long wait = res.headers().firstValue("Retry-After").map(GenericHttp::retryAfterMs).orElse(1000L * attempt * attempt);
                pause(Math.min(30_000L, wait));
                continue;
            }
            byte[] bytes = res.body() == null ? new byte[0] : res.body();
            if (bytes.length > MAX_BYTES) throw new BadRequestException("Response from " + uri.getHost() + " is larger than 10 MB; use a smaller page size");
            String text = new String(bytes, StandardCharsets.UTF_8);
            if (s >= 300 && s < 400) throw new BadRequestException("The server answered with a redirect (" + s + "); use the final URL");
            if (s >= 400) throw new BadRequestException("HTTP " + s + " from " + uri.getHost() + ": " + abbreviate(text, 300));
            return new Response(s, text, res.headers().firstValue("Link").orElse(null));
        }
    }

    private void pause(long ms) {
        try { sleeper.sleep(ms); } catch (InterruptedException e) { Thread.currentThread().interrupt(); }
    }

    static long retryAfterMs(String v) {
        try { return Long.parseLong(v.trim()) * 1000L; } catch (Exception e) { return 2000L; }
    }

    String buildUrl(GenericConfig c, Map<String, String> q, String secret) {
        Map<String, String> all = new LinkedHashMap<>(q);
        if ("QUERY".equals(c.authType())) all.put(c.authQueryName(), nz(secret));
        StringBuilder sb = new StringBuilder(c.baseUrl()).append(c.path());
        String sep = c.path().contains("?") ? "&" : "?";
        for (var e : all.entrySet()) {
            sb.append(sep).append(enc(e.getKey())).append('=').append(enc(e.getValue()));
            sep = "&";
        }
        return sb.toString();
    }

    /** Refuses loopback, private, link-local and other internal addresses unless the deployment allows them. */
    void guardHost(String host) {
        if (allowPrivateNetwork) return;
        if (host == null) throw new BadRequestException("No host");
        try {
            for (InetAddress a : InetAddress.getAllByName(host)) {
                if (a.isLoopbackAddress() || a.isSiteLocalAddress() || a.isLinkLocalAddress() || a.isAnyLocalAddress()
                        || a.isMulticastAddress() || isUniqueLocalV6(a) || isCarrierNat(a)) {
                    throw new BadRequestException("Refusing to call " + host + ": it resolves to a private or internal address");
                }
            }
        } catch (java.net.UnknownHostException e) {
            throw new BadRequestException("Unknown host: " + host);
        }
    }

    private static boolean isUniqueLocalV6(InetAddress a) {
        byte[] b = a.getAddress();
        return b.length == 16 && (b[0] & 0xfe) == 0xfc;
    }

    private static boolean isCarrierNat(InetAddress a) {
        byte[] b = a.getAddress();
        return b.length == 4 && (b[0] & 0xff) == 100 && (b[1] & 0xc0) == 64;
    }

    private void checkSameHost(GenericConfig c, String next) {
        String base = URI.create(c.baseUrl()).getHost();
        String host = URI.create(next).getHost();
        if (host == null || !host.equalsIgnoreCase(base)) throw new BadRequestException("The next-page link points at another host (" + host + ")");
    }

    static String nextLink(String header) {
        if (header == null) return null;
        for (String part : header.split(",")) {
            Matcher m = NEXT_LINK.matcher(part.trim());
            if (m.find()) return m.group(1);
        }
        return null;
    }

    static String formatSince(Instant since, String format) {
        return switch (format) {
            case "EPOCH_SECONDS" -> String.valueOf(since.getEpochSecond());
            case "EPOCH_MILLIS" -> String.valueOf(since.toEpochMilli());
            case "DATE" -> DateTimeFormatter.ISO_LOCAL_DATE.format(since.atZone(ZoneOffset.UTC));
            default -> DateTimeFormatter.ISO_INSTANT.format(since.truncatedTo(java.time.temporal.ChronoUnit.SECONDS));
        };
    }

    static JsonNode readJson(String text) {
        try {
            return GenericConfig.JSON.readTree(text == null || text.isBlank() ? "[]" : text);
        } catch (Exception e) {
            throw new BadRequestException("The response is not JSON: " + abbreviate(text, 200));
        }
    }

    static ArrayNode array() { return GenericConfig.JSON.createArrayNode(); }

    /** URLs in logs never carry the secret. */
    static String redact(String url, GenericConfig c) {
        if (!"QUERY".equals(c.authType())) return url;
        return url.replaceAll("([?&]" + Pattern.quote(enc(c.authQueryName())) + "=)[^&]*", "$1***");
    }

    private static String enc(String s) { return URLEncoder.encode(s == null ? "" : s, StandardCharsets.UTF_8); }

    private static String nz(String s) { return s == null ? "" : s; }

    static String abbreviate(String s, int n) {
        if (s == null) return "";
        s = s.replaceAll("\\s+", " ").trim();
        return s.length() <= n ? s : s.substring(0, n) + "…";
    }
}
