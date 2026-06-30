package com.nexus.oms.dto.ai;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class AiSuggestionDto {
    private String actionType;
    private String label;
    private String description;
    private double confidence;
    private String orderId;
}
