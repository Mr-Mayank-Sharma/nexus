package com.nexus.oms.ai;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.nexus.oms.dto.AllocationResponse;
import com.nexus.oms.dto.DemandForecastResponse;
import com.nexus.oms.dto.InventoryRecommendation;
import com.nexus.oms.exception.AiServiceException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestTemplate;

import java.math.BigDecimal;
import java.util.Map;

@Service
public class AiService {

    private static final Logger log = LoggerFactory.getLogger(AiService.class);

    private final RestTemplate restTemplate;
    private final ObjectMapper objectMapper;
    private final String baseUrlOps;
    private final String baseUrlIntel;

    public AiService(@Value("${nexus.ai.base-url-ops}") String baseUrlOps,
                     @Value("${nexus.ai.base-url-intel}") String baseUrlIntel) {
        this.restTemplate = new RestTemplate();
        this.objectMapper = new ObjectMapper();
        this.baseUrlOps = baseUrlOps;
        this.baseUrlIntel = baseUrlIntel;
    }

    public AllocationResponse callRoutingAi(Map<String, Object> input) {
        try {
            String response = restTemplate.postForObject(
                    baseUrlOps + "/routing", input, String.class);
            JsonNode json = objectMapper.readTree(response);
            return AllocationResponse.builder()
                    .warehouse(json.path("warehouse").asText("DEFAULT"))
                    .carrier(json.path("carrier").asText("AUTO"))
                    .boxSize(json.path("box_size").asText("STANDARD"))
                    .pickPackDetails(json.path("pick_pack").asText("Standard picking"))
                    .confidence(new BigDecimal(json.path("confidence").asDouble(0.9)))
                    .rule(json.path("rule").asText("BEST_MATCH"))
                    .build();
        } catch (Exception e) {
            log.warn("Routing AI call failed: {}", e.getMessage(), e);
            return fallbackRouting();
        }
    }

    public AllocationResponse callCarrierAi(Map<String, Object> input) {
        try {
            String response = restTemplate.postForObject(
                    baseUrlOps + "/carrier", input, String.class);
            JsonNode json = objectMapper.readTree(response);
            return AllocationResponse.builder()
                    .carrier(json.path("carrier").asText("STANDARD"))
                    .build();
        } catch (Exception e) {
            log.warn("Carrier AI call failed: {}", e.getMessage(), e);
            return AllocationResponse.builder().carrier("STANDARD").build();
        }
    }

    public AllocationResponse callBoxAi(Map<String, Object> input) {
        try {
            String response = restTemplate.postForObject(
                    baseUrlOps + "/box", input, String.class);
            JsonNode json = objectMapper.readTree(response);
            return AllocationResponse.builder()
                    .boxSize(json.path("box_size").asText("STANDARD"))
                    .build();
        } catch (Exception e) {
            log.warn("Box AI call failed: {}", e.getMessage(), e);
            return AllocationResponse.builder().boxSize("STANDARD").build();
        }
    }

    public AllocationResponse callPickPackAi(Map<String, Object> input) {
        try {
            String response = restTemplate.postForObject(
                    baseUrlOps + "/pickpack", input, String.class);
            JsonNode json = objectMapper.readTree(response);
            return AllocationResponse.builder()
                    .pickPackDetails(json.path("instructions").asText("Standard pick-pack"))
                    .build();
        } catch (Exception e) {
            log.warn("PickPack AI call failed: {}", e.getMessage(), e);
            return AllocationResponse.builder().pickPackDetails("Standard pick-pack").build();
        }
    }

    public DemandForecastResponse callDemandAi(Map<String, Object> input) {
        try {
            String response = restTemplate.postForObject(
                    baseUrlIntel + "/demand", input, String.class);
            JsonNode json = objectMapper.readTree(response);
            return DemandForecastResponse.builder()
                    .next7Days(objectMapper.convertValue(json.path("next_7_days"), Map.class))
                    .next30Days(objectMapper.convertValue(json.path("next_30_days"), Map.class))
                    .confidence(DemandForecastResponse.ConfidenceInterval.builder()
                            .lower(json.path("confidence").path("lower").asDouble(0.8))
                            .upper(json.path("confidence").path("upper").asDouble(0.95))
                            .build())
                    .build();
        } catch (Exception e) {
            log.warn("Demand AI call failed: {}", e.getMessage(), e);
            return fallbackDemand();
        }
    }

    public InventoryRecommendation callInventoryAi(Map<String, Object> input) {
        try {
            String response = restTemplate.postForObject(
                    baseUrlIntel + "/inventory", input, String.class);
            JsonNode json = objectMapper.readTree(response);
            return InventoryRecommendation.builder()
                    .needsReorder(json.path("needs_reorder").asBoolean(false))
                    .recommendedQty(json.path("recommended_qty").asInt(0))
                    .confidence(json.path("confidence").asDouble(0.0))
                    .build();
        } catch (Exception e) {
            log.warn("Inventory AI call failed: {}", e.getMessage(), e);
            return InventoryRecommendation.builder()
                    .needsReorder(false)
                    .recommendedQty(0)
                    .confidence(0.0)
                    .build();
        }
    }

    private AllocationResponse fallbackRouting() {
        return AllocationResponse.builder()
                .warehouse("FALLBACK_WH")
                .carrier("FALLBACK_CARRIER")
                .boxSize("MEDIUM")
                .pickPackDetails("Fallback routing applied")
                .confidence(BigDecimal.valueOf(0.5))
                .rule("FALLBACK")
                .build();
    }

    private DemandForecastResponse fallbackDemand() {
        return DemandForecastResponse.builder()
                .next7Days(Map.of())
                .next30Days(Map.of())
                .confidence(DemandForecastResponse.ConfidenceInterval.builder()
                        .lower(0.0).upper(0.0).build())
                .build();
    }
}
