package com.nexus.oms.integration.protocol;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.*;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestTemplate;

import java.util.Map;

@Component
public class GraphqlProtocolAdapter {

    private static final Logger log = LoggerFactory.getLogger(GraphqlProtocolAdapter.class);
    private final ObjectMapper objectMapper = new ObjectMapper();

    public JsonNode query(String endpoint, Map<String, String> headers, String query, Map<String, Object> variables) {
        try {
            ObjectNode body = objectMapper.createObjectNode();
            body.put("query", query);
            if (variables != null) body.set("variables", objectMapper.valueToTree(variables));

            RestTemplate rt = new RestTemplate();
            HttpHeaders httpHeaders = new HttpHeaders();
            if (headers != null) headers.forEach(httpHeaders::set);
            httpHeaders.setContentType(MediaType.APPLICATION_JSON);

            HttpEntity<String> entity = new HttpEntity<>(objectMapper.writeValueAsString(body), httpHeaders);
            ResponseEntity<String> res = rt.exchange(endpoint, HttpMethod.POST, entity, String.class);

            if (res.getStatusCode().is2xxSuccessful() && res.getBody() != null) {
                JsonNode json = objectMapper.readTree(res.getBody());
                JsonNode errors = json.get("errors");
                if (errors != null && errors.isArray() && errors.size() > 0) {
                    log.warn("GraphQL errors: {}", errors);
                    ObjectNode result = objectMapper.createObjectNode();
                    result.set("data", json.get("data"));
                    result.set("errors", errors);
                    return result;
                }
                return json.get("data");
            }
            log.warn("GraphQL non-2xx: {}", res.getStatusCode());
            return null;
        } catch (Exception e) {
            log.error("GraphQL query failed", e);
            return null;
        }
    }

    public JsonNode mutate(String endpoint, Map<String, String> headers, String mutation, Map<String, Object> variables) {
        return query(endpoint, headers, mutation, variables);
    }
}
