package com.nexus.oms.generic;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.nexus.oms.exception.BadRequestException;

import java.net.URI;
import java.util.*;

/**
 * The configuration of a generic connection, parsed and checked. Stored as JSON on {@link NxGenericConnection}.
 *
 * <pre>
 * {
 *   "request":    { "baseUrl": "https://shop.example.com", "path": "/wp-json/wc/v3/orders", "method": "GET",
 *                   "query": { "status": "processing" }, "headers": { "Accept": "application/json" } },
 *   "auth":       { "type": "BASIC", "username": "ck_..." }      // NONE, BASIC, BEARER, HEADER (headerName), QUERY (queryName)
 *   "pagination": { "type": "PAGE", "pageParam": "page", "sizeParam": "per_page", "size": 50, "maxPages": 50 },
 *                                                                // NONE, PAGE, OFFSET (offsetParam), CURSOR (cursorParam, cursorPath), LINK_HEADER
 *   "incremental": { "param": "modified_after", "format": "ISO" },  // ISO, EPOCH_SECONDS, EPOCH_MILLIS, DATE
 *   "recordsPath": "",                                            // where the array of records is; empty = the response itself
 *   "fields":     { "orderId": "id", "email": "billing.email", "customerName": "{billing.first_name} {billing.last_name}" },
 *   "lines":      { "path": "line_items", "fields": { "sku": "sku", "quantity": "quantity", "unitPrice": "price" } },
 *   "locations":  { "WH-1": "a Nexus location id" },            // inventory: source location code to Nexus location
 *   "delivery":   { "mode": "HTTP", "batchSize": 50, "wrapper": "orders" }  // export only: HTTP or FILE
 * }
 * </pre>
 */
public record GenericConfig(
        String baseUrl, String path, String method, Map<String, String> query, Map<String, String> headers,
        String authType, String authUsername, String authHeaderName, String authQueryName,
        String pageType, String pageParam, String sizeParam, int pageSize, int startPage, String offsetParam,
        String cursorParam, String cursorPath, int maxPages,
        String sinceParam, String sinceFormat,
        String recordsPath, Map<String, String> fields, String linesPath, Map<String, String> lineFields,
        Map<String, String> locations,
        String deliveryMode, int batchSize, String wrapper) {

    static final ObjectMapper JSON = new ObjectMapper();
    static final Set<String> AUTH_TYPES = Set.of("NONE", "BASIC", "BEARER", "HEADER", "QUERY");
    static final Set<String> PAGE_TYPES = Set.of("NONE", "PAGE", "OFFSET", "CURSOR", "LINK_HEADER");
    static final Set<String> SINCE_FORMATS = Set.of("ISO", "EPOCH_SECONDS", "EPOCH_MILLIS", "DATE");
    static final int HARD_MAX_PAGES = 500;

    /** What each entity needs mapped before a connection can be saved. */
    static final Map<String, List<String>> REQUIRED_IMPORT = Map.of(
            "orders", List.of("orderId"),
            "inventory", List.of("sku", "quantity"));
    static final Map<String, List<String>> REQUIRED_IMPORT_LINES = Map.of("orders", List.of("sku", "quantity"));
    static final Set<String> IMPORT_ENTITIES = REQUIRED_IMPORT.keySet();
    static final Set<String> EXPORT_ENTITIES = Set.of("orders", "shipments", "inventory");

    public static GenericConfig parse(String json, String direction, String entityType) {
        JsonNode root;
        try {
            root = JSON.readTree(json == null || json.isBlank() ? "{}" : json);
        } catch (Exception e) {
            throw new BadRequestException("The configuration is not valid JSON: " + e.getMessage());
        }
        if (!root.isObject()) throw new BadRequestException("The configuration must be a JSON object.");
        boolean export = "EXPORT".equals(direction);

        JsonNode req = root.path("request"), auth = root.path("auth"), pg = root.path("pagination"),
                inc = root.path("incremental"), lines = root.path("lines"), dl = root.path("delivery");

        String deliveryMode = upper(dl.path("mode").asText(export ? "HTTP" : "HTTP"));
        boolean needsUrl = !export || "HTTP".equals(deliveryMode);
        String baseUrl = trimSlash(req.path("baseUrl").asText(""));
        if (needsUrl) checkUrl(baseUrl);
        String path = req.path("path").asText("");
        if (!path.isEmpty() && !path.startsWith("/")) path = "/" + path;
        String method = upper(req.path("method").asText(export ? "POST" : "GET"));
        if (!Set.of("GET", "POST", "PUT", "PATCH").contains(method)) throw new BadRequestException("Unsupported method: " + method);

        String authType = upper(auth.path("type").asText("NONE"));
        if (!AUTH_TYPES.contains(authType)) throw new BadRequestException("auth.type must be one of " + AUTH_TYPES);
        String headerName = auth.path("headerName").asText("");
        String queryName = auth.path("queryName").asText("");
        if ("HEADER".equals(authType) && headerName.isBlank()) throw new BadRequestException("auth.headerName is needed for HEADER auth");
        if ("QUERY".equals(authType) && queryName.isBlank()) throw new BadRequestException("auth.queryName is needed for QUERY auth");

        String pageType = upper(pg.path("type").asText("NONE"));
        if (!PAGE_TYPES.contains(pageType)) throw new BadRequestException("pagination.type must be one of " + PAGE_TYPES);
        String cursorPath = pg.path("cursorPath").asText("");
        if ("CURSOR".equals(pageType) && cursorPath.isBlank()) throw new BadRequestException("pagination.cursorPath is needed for CURSOR paging");
        int maxPages = Math.max(1, Math.min(HARD_MAX_PAGES, pg.path("maxPages").asInt(50)));

        String sinceFormat = upper(inc.path("format").asText("ISO"));
        if (!SINCE_FORMATS.contains(sinceFormat)) throw new BadRequestException("incremental.format must be one of " + SINCE_FORMATS);

        Map<String, String> fields = strings(root.path("fields"));
        Map<String, String> lineFields = strings(lines.path("fields"));
        String linesPath = lines.path("path").asText("");

        if (export) {
            if (!EXPORT_ENTITIES.contains(entityType)) throw new BadRequestException("Export supports " + EXPORT_ENTITIES);
            if (!Set.of("HTTP", "FILE").contains(deliveryMode)) throw new BadRequestException("delivery.mode must be HTTP or FILE");
            if (fields.isEmpty()) throw new BadRequestException("Map at least one field to send (fields).");
        } else {
            if (!IMPORT_ENTITIES.contains(entityType)) throw new BadRequestException("Import supports " + IMPORT_ENTITIES);
            for (String f : REQUIRED_IMPORT.get(entityType)) {
                if (!fields.containsKey(f)) throw new BadRequestException("fields." + f + " must be mapped for " + entityType);
            }
            List<String> needLines = REQUIRED_IMPORT_LINES.getOrDefault(entityType, List.of());
            if (!needLines.isEmpty()) {
                if (linesPath.isBlank()) throw new BadRequestException("lines.path must say where the order lines are");
                for (String f : needLines) {
                    if (!lineFields.containsKey(f)) throw new BadRequestException("lines.fields." + f + " must be mapped");
                }
            }
        }
        // every expression must parse now, not halfway through a run
        fields.values().forEach(ValueExpr::compile);
        lineFields.values().forEach(ValueExpr::compile);

        return new GenericConfig(baseUrl, path, method, strings(req.path("query")), strings(req.path("headers")),
                authType, auth.path("username").asText(""), headerName, queryName,
                pageType, pg.path("pageParam").asText("page"), pg.path("sizeParam").asText(""), Math.max(1, Math.min(1000, pg.path("size").asInt(50))),
                pg.path("startPage").asInt(1), pg.path("offsetParam").asText("offset"),
                pg.path("cursorParam").asText("cursor"), cursorPath, maxPages,
                inc.path("param").asText(""), sinceFormat,
                root.path("recordsPath").asText(""), fields, linesPath, lineFields, strings(root.path("locations")),
                deliveryMode, Math.max(1, Math.min(500, dl.path("batchSize").asInt(50))), dl.path("wrapper").asText(""));
    }

    static void checkUrl(String url) {
        if (url.isBlank()) throw new BadRequestException("request.baseUrl is required");
        URI u;
        try {
            u = URI.create(url);
        } catch (Exception e) {
            throw new BadRequestException("request.baseUrl is not a valid URL");
        }
        if (u.getHost() == null || !Set.of("http", "https").contains(String.valueOf(u.getScheme()).toLowerCase())) {
            throw new BadRequestException("request.baseUrl must be an http(s) URL with a host");
        }
        if (u.getUserInfo() != null) throw new BadRequestException("Put credentials in the connection's secret, not in the URL");
    }

    private static Map<String, String> strings(JsonNode n) {
        Map<String, String> m = new LinkedHashMap<>();
        if (n != null && n.isObject()) n.fields().forEachRemaining(e -> m.put(e.getKey(), e.getValue().isValueNode() ? e.getValue().asText() : e.getValue().toString()));
        return m;
    }

    private static String upper(String s) { return s == null ? "" : s.trim().toUpperCase(Locale.ROOT); }

    private static String trimSlash(String s) { return s.endsWith("/") ? s.substring(0, s.length() - 1) : s.trim(); }
}
