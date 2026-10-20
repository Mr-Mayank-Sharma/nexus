package com.nexus.oms.generic;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.nexus.oms.exception.BadRequestException;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

/** Paths, value expressions, the record mapper and configuration checks. */
class MappingTest {

    static final ObjectMapper M = new ObjectMapper();

    static JsonNode json(String s) throws Exception { return M.readTree(s); }

    @Test
    void pathsReadNestedObjectsArraysAndQuotedKeys() throws Exception {
        JsonNode n = json("{\"a\":{\"b\":[{\"c\":1},{\"c\":2}]},\"x-y\":{\"z\":\"q\"}}");
        assertEquals(2, JsonPaths.get(n, "a.b[1].c").asInt());
        assertEquals("q", JsonPaths.get(n, "[\"x-y\"].z").asText());
        assertTrue(JsonPaths.get(n, "a.missing.deeper").isMissingNode());
        assertSame(n, JsonPaths.get(n, "$"));
        assertSame(n, JsonPaths.get(n, ""));
    }

    @Test
    void invalidPathsAreRejectedUpFront() {
        assertThrows(BadRequestException.class, () -> JsonPaths.check("a..b"));
        assertThrows(BadRequestException.class, () -> JsonPaths.check("a[x]"));
        assertThrows(BadRequestException.class, () -> JsonPaths.check(" "));
        assertDoesNotThrow(() -> JsonPaths.check("items[0].sku"));
    }

    @Test
    void putBuildsNestedObjects() {
        ObjectNode o = M.createObjectNode();
        JsonPaths.put(o, "order.ship.city", "Paris");
        JsonPaths.put(o, "order.id", "7");
        assertEquals("Paris", o.at("/order/ship/city").asText());
        assertEquals("7", o.at("/order/id").asText());
    }

    @Test
    void literalsTemplatesAndTransforms() throws Exception {
        JsonNode r = json("{\"first\":\" Ann \",\"last\":\"Lee\",\"cents\":1999,\"qty\":\"2.0\",\"when\":1700000000,\"cur\":\"usd\"}");
        assertEquals("USD", ValueExpr.compile("=USD").eval(r));
        assertEquals("Ann Lee", ValueExpr.compile("{first} {last}").eval(r));
        assertEquals("19.99", ValueExpr.compile("cents|cents").eval(r));
        assertEquals("2", ValueExpr.compile("qty|int").eval(r));
        assertEquals("USD", ValueExpr.compile("cur|upper").eval(r));
        assertEquals("2023-11-14T22:13:20Z", ValueExpr.compile("when|date").eval(r));
        assertEquals("B2C", ValueExpr.compile("missing|default:B2C").eval(r));
        assertNull(ValueExpr.compile("missing").eval(r));
        assertNull(ValueExpr.compile("{missing} {gone}").eval(r), "a template with nothing behind it is empty, not blanks");
    }

    @Test
    void datesInCommonShapesBecomeIso() {
        assertEquals("2026-03-01T10:00:00Z", ValueExpr.toIso("2026-03-01T15:30:00+05:30"));
        assertEquals("2026-03-01T00:00:00Z", ValueExpr.toIso("2026-03-01"));
        assertEquals("2026-03-01T10:00:00Z", ValueExpr.toIso("2026-03-01 10:00:00"));
        assertEquals("2023-11-14T22:13:20Z", ValueExpr.toIso("1700000000000"));
        assertThrows(IllegalArgumentException.class, () -> ValueExpr.toIso("next tuesday"));
    }

    @Test
    void unknownTransformIsRejectedWhenSaving() {
        assertThrows(BadRequestException.class, () -> ValueExpr.compile("a|explode"));
    }

    @Test
    void mapperMapsFieldsAndLinesAndReportsBadValuesPerRecord() throws Exception {
        GenericConfig c = GenericConfig.parse("""
            {"request":{"baseUrl":"https://shop.example.com","path":"/orders"},
             "fields":{"orderId":"id","total":"total|number","customerName":"{b.first} {b.last}"},
             "lines":{"path":"items","fields":{"sku":"sku","quantity":"qty|int","unitPrice":"price"}}}
            """, "IMPORT", "orders");
        GenericMapper m = new GenericMapper(c);
        var ok = m.map(json("{\"id\":5,\"total\":\"12.50\",\"b\":{\"first\":\"A\",\"last\":\"B\"},\"items\":[{\"sku\":\"S1\",\"qty\":2,\"price\":\"6.25\"}]}"));
        assertTrue(ok.ok());
        assertEquals("5", ok.get("orderId"));
        assertEquals("A B", ok.get("customerName"));
        assertEquals(List.of(Map.of("sku", "S1", "quantity", "2", "unitPrice", "6.25")), ok.lines());

        var bad = m.map(json("{\"id\":6,\"total\":\"twelve\",\"items\":[]}"));
        assertFalse(bad.ok());
        assertTrue(bad.problem().startsWith("total:"), bad.problem());
    }

    @Test
    void configChecksWhatEachEntityNeeds() {
        String base = "\"request\":{\"baseUrl\":\"https://x.example.com\"}";
        assertThrows(BadRequestException.class, () -> GenericConfig.parse("{" + base + ",\"fields\":{}}", "IMPORT", "orders"), "orderId must be mapped");
        assertThrows(BadRequestException.class, () -> GenericConfig.parse("{" + base + ",\"fields\":{\"orderId\":\"id\"}}", "IMPORT", "orders"), "lines are required");
        assertThrows(BadRequestException.class, () -> GenericConfig.parse("{" + base + ",\"fields\":{\"sku\":\"s\"}}", "IMPORT", "inventory"), "quantity is required");
        assertDoesNotThrow(() -> GenericConfig.parse("{" + base + ",\"fields\":{\"sku\":\"s\",\"quantity\":\"q\"}}", "IMPORT", "inventory"));
        assertThrows(BadRequestException.class, () -> GenericConfig.parse("{" + base + ",\"fields\":{\"sku\":\"s\",\"quantity\":\"q\"}}", "IMPORT", "invoices"));
        assertThrows(BadRequestException.class, () -> GenericConfig.parse("{\"delivery\":{\"mode\":\"FILE\"},\"fields\":{}}", "EXPORT", "orders"), "nothing mapped");
        assertDoesNotThrow(() -> GenericConfig.parse("{\"delivery\":{\"mode\":\"FILE\"},\"fields\":{\"n\":\"orderNumber\"}}", "EXPORT", "orders"), "FILE needs no URL");
    }

    @Test
    void configRefusesUrlsThatCarryCredentialsOrAreNotHttp() {
        String f = ",\"fields\":{\"sku\":\"s\",\"quantity\":\"q\"}}";
        assertThrows(BadRequestException.class, () -> GenericConfig.parse("{\"request\":{\"baseUrl\":\"https://u:p@x.example.com\"}" + f, "IMPORT", "inventory"));
        assertThrows(BadRequestException.class, () -> GenericConfig.parse("{\"request\":{\"baseUrl\":\"file:///etc/passwd\"}" + f, "IMPORT", "inventory"));
        assertThrows(BadRequestException.class, () -> GenericConfig.parse("{\"request\":{\"baseUrl\":\"https://x.example.com\"},\"auth\":{\"type\":\"HEADER\"}" + f, "IMPORT", "inventory"));
        assertThrows(BadRequestException.class, () -> GenericConfig.parse("{\"request\":{\"baseUrl\":\"https://x.example.com\"},\"pagination\":{\"type\":\"CURSOR\"}" + f, "IMPORT", "inventory"));
    }

    @Test
    void pageLimitIsCapped() {
        GenericConfig c = GenericConfig.parse("{\"request\":{\"baseUrl\":\"https://x.example.com\"},\"pagination\":{\"type\":\"PAGE\",\"maxPages\":99999},\"fields\":{\"sku\":\"s\",\"quantity\":\"q\"}}", "IMPORT", "inventory");
        assertEquals(GenericConfig.HARD_MAX_PAGES, c.maxPages());
    }

    @Test
    void everyTemplateIsAValidConfiguration() throws Exception {
        for (var t : GenericTemplates.ALL) {
            String json = M.writeValueAsString(t.config());
            assertDoesNotThrow(() -> GenericConfig.parse(json, t.direction(), t.entityType()), t.key());
        }
    }
}
