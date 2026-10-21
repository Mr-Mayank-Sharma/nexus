package com.nexus.gateway.carrier;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;

import java.net.URI;
import java.net.URLEncoder;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.Map;
import java.util.stream.Collectors;

/** JSON over HTTP for carrier APIs, with a timeout and the carrier's error text on failure. */
public final class HttpJson {

    static final ObjectMapper JSON = new ObjectMapper();
    private static final HttpClient CLIENT = HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(10)).build();
    private static final Duration TIMEOUT = Duration.ofSeconds(30);

    private HttpJson() {
    }

    public static JsonNode send(String method, String url, Map<String, String> headers, Object body) {
        try {
            HttpRequest.Builder b = HttpRequest.newBuilder(URI.create(url)).timeout(TIMEOUT).header("Accept", "application/json");
            headers.forEach(b::header);
            if (body == null) b.method(method, HttpRequest.BodyPublishers.noBody());
            else b.header("Content-Type", "application/json").method(method, HttpRequest.BodyPublishers.ofString(JSON.writeValueAsString(body)));
            return read(CLIENT.send(b.build(), HttpResponse.BodyHandlers.ofString()));
        } catch (CarrierException e) {
            throw e;
        } catch (Exception e) {
            throw new CarrierException(502, "Could not reach " + URI.create(url).getHost() + ": " + e.getMessage());
        }
    }

    public static JsonNode form(String url, Map<String, String> headers, Map<String, String> fields) {
        try {
            String form = fields.entrySet().stream().map(e -> URLEncoder.encode(e.getKey(), StandardCharsets.UTF_8) + "="
                    + URLEncoder.encode(e.getValue(), StandardCharsets.UTF_8)).collect(Collectors.joining("&"));
            HttpRequest.Builder b = HttpRequest.newBuilder(URI.create(url)).timeout(TIMEOUT).header("Content-Type", "application/x-www-form-urlencoded")
                    .header("Accept", "application/json").POST(HttpRequest.BodyPublishers.ofString(form));
            headers.forEach(b::header);
            return read(CLIENT.send(b.build(), HttpResponse.BodyHandlers.ofString()));
        } catch (CarrierException e) {
            throw e;
        } catch (Exception e) {
            throw new CarrierException(502, "Could not reach " + URI.create(url).getHost() + ": " + e.getMessage());
        }
    }

    private static JsonNode read(HttpResponse<String> r) throws Exception {
        String text = r.body() == null ? "" : r.body();
        JsonNode node = text.isBlank() ? JSON.createObjectNode() : JSON.readTree(text);
        if (r.statusCode() >= 300) throw new CarrierException(r.statusCode(), errorText(node, text));
        return node;
    }

    /** The first error message a carrier response carries, in the shapes FedEx and UPS use. */
    static String errorText(JsonNode n, String raw) {
        for (String path : new String[]{"/errors/0/message", "/response/errors/0/message", "/error_description", "/message"}) {
            JsonNode v = n.at(path);
            if (!v.isMissingNode() && !v.isNull() && !v.asText().isBlank()) return v.asText();
        }
        return raw.length() > 300 ? raw.substring(0, 300) : raw;
    }
}
