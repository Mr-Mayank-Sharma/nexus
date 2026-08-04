package com.nexus.oms.ai;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.CommandLineRunner;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.http.client.SimpleClientHttpRequestFactory;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestTemplate;

import java.util.Map;

/**
 * On startup, calls POST /api/warmup on both Flask AI servers
 * so models are pre-loaded before the first real request.
 */
@Component
@ConditionalOnProperty(name = "nexus.ai.warmup-on-startup", havingValue = "true", matchIfMissing = false)
public class AiHealthCheck implements CommandLineRunner {

    private static final Logger log = LoggerFactory.getLogger(AiHealthCheck.class);

    private final RestTemplate restTemplate;
    private final String baseUrlOps;
    private final String baseUrlIntel;

    public AiHealthCheck(
            @Value("${nexus.ai.base-url-ops}") String baseUrlOps,
            @Value("${nexus.ai.base-url-intel}") String baseUrlIntel,
            @Value("${nexus.ai.timeout-ms:30000}") int timeoutMs) {
        this.baseUrlOps = baseUrlOps;
        this.baseUrlIntel = baseUrlIntel;
        SimpleClientHttpRequestFactory factory = new SimpleClientHttpRequestFactory();
        factory.setConnectTimeout(Math.min(timeoutMs, 10000));
        factory.setReadTimeout(Math.min(timeoutMs, 15000));
        this.restTemplate = new RestTemplate(factory);
    }

    @Override
    public void run(String... args) {
        log.info("AI warmup: calling Flask servers...");

        warmup(baseUrlOps, "ops (port 5000)");
        warmup(baseUrlIntel, "intel (port 5001)");
    }

    private void warmup(String baseUrl, String label) {
        try {
            @SuppressWarnings("unchecked")
            Map<String, Object> response = restTemplate.postForObject(
                    baseUrl + "/api/warmup", Map.of(), Map.class);
            log.info("AI warmup OK for {}: models loaded", label);
            if (response != null && response.containsKey("models_loaded")) {
                log.info("  models_loaded: {}", response.get("models_loaded"));
            }
        } catch (Exception e) {
            log.warn("AI warmup failed for {} (non-fatal): {}", label, e.getMessage());
        }
    }
}
