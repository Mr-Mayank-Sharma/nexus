package com.nexus.oms.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import java.util.Map;

@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class DemandForecastResponse {

    private Map<String, Integer> next7Days;
    private Map<String, Integer> next30Days;
    private ConfidenceInterval confidence;

    @Data
    @NoArgsConstructor
    @AllArgsConstructor
    @Builder
    public static class ConfidenceInterval {
        private double lower;
        private double upper;
    }
}
