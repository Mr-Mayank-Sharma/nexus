package com.nexus.gateway.carrier;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;

import java.io.IOException;
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
    private static final Duration REQUEST_TIMEOUT = Duration.ofSeconds(30);
    private static final int MAX_RAW_ERROR_LENGTH = 300;
    /** Where FedEx and UPS put their first error message. */
    private static final String[] ERROR_MESSAGE_PATHS = {"/errors/0/message", "/response/errors/0/message", "/error_description", "/message"};

    private HttpJson() {
    }

    /** Sends a JSON body (or none) and returns the JSON reply; a reply of 300 or above is a {@link CarrierException}. */
    public static JsonNode send(String method, String url, Map<String, String> headers, Object body) {
        try {
            HttpRequest.Builder request = HttpRequest.newBuilder(URI.create(url)).timeout(REQUEST_TIMEOUT).header("Accept", "application/json");
            headers.forEach(request::header);
            if (body == null) {
                request.method(method, HttpRequest.BodyPublishers.noBody());
            } else {
                request.header("Content-Type", "application/json").method(method, HttpRequest.BodyPublishers.ofString(JSON.writeValueAsString(body)));
            }
            return readReply(CLIENT.send(request.build(), HttpResponse.BodyHandlers.ofString()));
        } catch (CarrierException carrierSaidNo) {
            throw carrierSaidNo;
        } catch (InterruptedException interrupted) {
            Thread.currentThread().interrupt();
            throw unreachable(url, interrupted);
        } catch (Exception failure) {
            throw unreachable(url, failure);
        }
    }

    /** Posts a form (OAuth token requests) and returns the JSON reply. */
    public static JsonNode form(String url, Map<String, String> headers, Map<String, String> fields) {
        try {
            String formBody = fields.entrySet().stream()
                    .map(field -> URLEncoder.encode(field.getKey(), StandardCharsets.UTF_8) + "=" + URLEncoder.encode(field.getValue(), StandardCharsets.UTF_8))
                    .collect(Collectors.joining("&"));
            HttpRequest.Builder request = HttpRequest.newBuilder(URI.create(url))
                    .timeout(REQUEST_TIMEOUT)
                    .header("Content-Type", "application/x-www-form-urlencoded")
                    .header("Accept", "application/json")
                    .POST(HttpRequest.BodyPublishers.ofString(formBody));
            headers.forEach(request::header);
            return readReply(CLIENT.send(request.build(), HttpResponse.BodyHandlers.ofString()));
        } catch (CarrierException carrierSaidNo) {
            throw carrierSaidNo;
        } catch (InterruptedException interrupted) {
            Thread.currentThread().interrupt();
            throw unreachable(url, interrupted);
        } catch (Exception failure) {
            throw unreachable(url, failure);
        }
    }

    private static JsonNode readReply(HttpResponse<String> response) throws IOException {
        String text = response.body() == null ? "" : response.body();
        JsonNode reply = text.isBlank() ? JSON.createObjectNode() : JSON.readTree(text);
        if (response.statusCode() >= 300) {
            throw new CarrierException(response.statusCode(), errorText(reply, text));
        }
        return reply;
    }

    private static CarrierException unreachable(String url, Exception cause) {
        return new CarrierException(502, "Could not reach " + URI.create(url).getHost() + ": " + cause.getMessage());
    }

    /** The first error message a carrier response carries, in the shapes FedEx and UPS use; else the raw text, cut. */
    static String errorText(JsonNode reply, String rawText) {
        for (String path : ERROR_MESSAGE_PATHS) {
            JsonNode message = reply.at(path);
            if (!message.isMissingNode() && !message.isNull() && !message.asText().isBlank()) {
                return message.asText();
            }
        }
        return rawText.length() > MAX_RAW_ERROR_LENGTH ? rawText.substring(0, MAX_RAW_ERROR_LENGTH) : rawText;
    }
}
