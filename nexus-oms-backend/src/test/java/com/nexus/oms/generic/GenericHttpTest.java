package com.nexus.oms.generic;

import com.fasterxml.jackson.databind.JsonNode;
import com.nexus.oms.exception.BadRequestException;
import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpServer;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.net.InetSocketAddress;
import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.*;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.Function;

import static org.junit.jupiter.api.Assertions.*;

/** The fetcher against a real HTTP server on this machine. */
class GenericHttpTest {

    HttpServer server;
    String base;
    final List<HttpExchange> seen = new CopyOnWriteArrayList<>();
    final List<String> bodies = new CopyOnWriteArrayList<>();
    Function<HttpExchange, Object[]> handler;   // returns {status, body, extraHeaders map}
    GenericHttp http = new GenericHttp(true);

    @BeforeEach
    void start() throws Exception {
        server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
        server.createContext("/", ex -> {
            seen.add(ex);
            bodies.add(new String(ex.getRequestBody().readAllBytes(), StandardCharsets.UTF_8));
            Object[] r = handler.apply(ex);
            @SuppressWarnings("unchecked") Map<String, String> h = r.length > 2 ? (Map<String, String>) r[2] : Map.of();
            h.forEach((k, v) -> ex.getResponseHeaders().add(k, v));
            byte[] b = ((String) r[1]).getBytes(StandardCharsets.UTF_8);
            ex.sendResponseHeaders((int) r[0], b.length == 0 ? -1 : b.length);
            if (b.length > 0) ex.getResponseBody().write(b);
            ex.close();
        });
        server.start();
        base = "http://127.0.0.1:" + server.getAddress().getPort();
        http.setSleeper(ms -> { });
    }

    @AfterEach
    void stop() { server.stop(0); }

    GenericConfig cfg(String extra) {
        return GenericConfig.parse("{\"request\":{\"baseUrl\":\"" + base + "\",\"path\":\"/orders\"}," + extra
                + "\"fields\":{\"sku\":\"sku\",\"quantity\":\"q\"}}", "IMPORT", "inventory");
    }

    static Map<String, String> query(HttpExchange ex) {
        Map<String, String> m = new LinkedHashMap<>();
        String q = ex.getRequestURI().getRawQuery();
        if (q != null) for (String p : q.split("&")) { String[] kv = p.split("=", 2); m.put(java.net.URLDecoder.decode(kv[0], StandardCharsets.UTF_8), kv.length > 1 ? java.net.URLDecoder.decode(kv[1], StandardCharsets.UTF_8) : ""); }
        return m;
    }

    static String items(int from, int n) {
        StringBuilder sb = new StringBuilder("[");
        for (int i = 0; i < n; i++) sb.append(i > 0 ? "," : "").append("{\"sku\":\"S").append(from + i).append("\"}");
        return sb.append("]").toString();
    }

    List<JsonNode> collect(GenericConfig c, String secret, Instant since) {
        List<JsonNode> all = new ArrayList<>();
        http.fetch(c, secret, since, 1000, all::addAll);
        return all;
    }

    @Test
    void pagesUntilAShortPageAndSendsPageSizeAndSince() {
        handler = ex -> {
            int page = Integer.parseInt(query(ex).get("page"));
            return new Object[]{200, page < 3 ? items(page * 10, 2) : items(page * 10, 1)};
        };
        GenericConfig c = cfg("\"pagination\":{\"type\":\"PAGE\",\"sizeParam\":\"per_page\",\"size\":2},\"incremental\":{\"param\":\"modified_after\"},");
        List<JsonNode> all = collect(c, null, Instant.parse("2026-10-01T12:00:00.123Z"));
        assertEquals(5, all.size());
        assertEquals(3, seen.size());
        Map<String, String> q = query(seen.get(0));
        assertEquals("2", q.get("per_page"));
        assertEquals("2026-10-01T12:00:00Z", q.get("modified_after"));
    }

    @Test
    void cursorPagingFollowsTheCursorUntilItIsGone() {
        handler = ex -> {
            String cur = query(ex).get("cursor");
            if (cur == null) return new Object[]{200, "{\"result\":" + items(0, 2) + ",\"pagination\":{\"nextPageCursor\":\"abc\"}}"};
            return new Object[]{200, "{\"result\":" + items(2, 1) + ",\"pagination\":{\"nextPageCursor\":null}}"};
        };
        GenericConfig c = cfg("\"recordsPath\":\"result\",\"pagination\":{\"type\":\"CURSOR\",\"cursorPath\":\"pagination.nextPageCursor\"},");
        assertEquals(3, collect(c, null, null).size());
        assertEquals("abc", query(seen.get(1)).get("cursor"));
    }

    @Test
    void linkHeaderPagingStaysOnTheSameHost() {
        handler = ex -> query(ex).containsKey("p")
                ? new Object[]{200, items(9, 1)}
                : new Object[]{200, items(0, 2), Map.of("Link", "<" + base + "/orders?p=2>; rel=\"next\"")};
        assertEquals(3, collect(cfg("\"pagination\":{\"type\":\"LINK_HEADER\"},"), null, null).size());

        seen.clear();
        handler = ex -> new Object[]{200, items(0, 1), Map.of("Link", "<https://elsewhere.example.com/x>; rel=\"next\"")};
        BadRequestException e = assertThrows(BadRequestException.class, () -> collect(cfg("\"pagination\":{\"type\":\"LINK_HEADER\"},"), null, null));
        assertTrue(e.getMessage().contains("another host"));
    }

    @Test
    void retriesTooManyRequestsAndServerErrorsThenSucceeds() {
        AtomicInteger n = new AtomicInteger();
        handler = ex -> switch (n.incrementAndGet()) {
            case 1 -> new Object[]{429, "slow down", Map.of("Retry-After", "1")};
            case 2 -> new Object[]{503, "busy"};
            default -> new Object[]{200, items(0, 1)};
        };
        assertEquals(1, collect(cfg(""), null, null).size());
        assertEquals(3, n.get());
    }

    @Test
    void clientErrorsStopWithTheServersMessage() {
        handler = ex -> new Object[]{401, "{\"message\":\"bad key\"}"};
        BadRequestException e = assertThrows(BadRequestException.class, () -> collect(cfg(""), null, null));
        assertTrue(e.getMessage().contains("HTTP 401") && e.getMessage().contains("bad key"), e.getMessage());
        assertEquals(1, seen.size(), "4xx is not retried");
    }

    @Test
    void redirectsAreNotFollowed() {
        handler = ex -> new Object[]{302, "", Map.of("Location", "http://169.254.169.254/latest")};
        BadRequestException e = assertThrows(BadRequestException.class, () -> collect(cfg(""), null, null));
        assertTrue(e.getMessage().contains("redirect"));
    }

    @Test
    void recordsPathMustPointAtAList() {
        handler = ex -> new Object[]{200, "{\"data\":{\"not\":\"a list\"}}"};
        BadRequestException e = assertThrows(BadRequestException.class, () -> collect(cfg("\"recordsPath\":\"data\","), null, null));
        assertTrue(e.getMessage().contains("recordsPath"));
    }

    @Test
    void authIsSentTheWayTheConfigSays() {
        handler = ex -> new Object[]{200, "[]"};
        collect(cfg("\"auth\":{\"type\":\"BASIC\",\"username\":\"ck\"},"), "cs", null);
        assertEquals("Basic " + Base64.getEncoder().encodeToString("ck:cs".getBytes()), seen.get(0).getRequestHeaders().getFirst("Authorization"));
        collect(cfg("\"auth\":{\"type\":\"BEARER\"},"), "tok", null);
        assertEquals("Bearer tok", seen.get(1).getRequestHeaders().getFirst("Authorization"));
        collect(cfg("\"auth\":{\"type\":\"HEADER\",\"headerName\":\"X-Api-Key\"},"), "k1", null);
        assertEquals("k1", seen.get(2).getRequestHeaders().getFirst("X-Api-Key"));
        List<GenericHttp.Page> pages = http.fetch(cfg("\"auth\":{\"type\":\"QUERY\",\"queryName\":\"api_key\"},"), "s3cret", null, 1, r -> { });
        assertEquals("s3cret", query(seen.get(3)).get("api_key"));
        assertFalse(pages.get(0).url().contains("s3cret"), "page URLs that go to logs never carry the secret");
    }

    @Test
    void exportSendsJsonWithTheConfiguredMethod() {
        handler = ex -> new Object[]{201, "{}"};
        GenericConfig c = GenericConfig.parse("{\"request\":{\"baseUrl\":\"" + base + "\",\"path\":\"/in\",\"method\":\"PUT\"},\"fields\":{\"a\":\"b\"}}", "EXPORT", "orders");
        assertEquals(201, http.send(c, null, "[{\"a\":1}]"));
        assertEquals("PUT", seen.get(0).getRequestMethod());
        assertEquals("[{\"a\":1}]", bodies.get(0));
        assertEquals("application/json", seen.get(0).getRequestHeaders().getFirst("Content-Type"));
    }

    @Test
    void privateAndInternalAddressesAreRefusedByDefault() {
        GenericHttp strict = new GenericHttp(false);
        handler = ex -> new Object[]{200, "[]"};
        BadRequestException e = assertThrows(BadRequestException.class, () -> strict.fetch(cfg(""), null, null, 1, r -> { }));
        assertTrue(e.getMessage().contains("private or internal"));
        assertTrue(seen.isEmpty(), "nothing was sent");
        for (String host : List.of("10.1.2.3", "192.168.0.10", "172.16.5.4", "169.254.169.254", "100.64.0.1", "0.0.0.0", "::1", "fd00::1")) {
            assertThrows(BadRequestException.class, () -> strict.guardHost(host), host);
        }
    }

    @Test
    void sinceFormats() {
        Instant t = Instant.parse("2026-10-01T12:34:56.789Z");
        assertEquals("1790858096", GenericHttp.formatSince(t, "EPOCH_SECONDS"));
        assertEquals("1790858096789", GenericHttp.formatSince(t, "EPOCH_MILLIS"));
        assertEquals("2026-10-01", GenericHttp.formatSince(t, "DATE"));
        assertEquals("2026-10-01T12:34:56Z", GenericHttp.formatSince(t, "ISO"));
    }

    @Test
    void nextLinkIsFoundAmongOtherRelations() {
        assertEquals("https://a/x?page=3", GenericHttp.nextLink("<https://a/x?page=1>; rel=\"prev\", <https://a/x?page=3>; rel=\"next\""));
        assertNull(GenericHttp.nextLink("<https://a/x?page=1>; rel=\"prev\""));
        assertNotNull(URI.create(base));
    }
}
