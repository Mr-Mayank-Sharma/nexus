package com.nexus.oms.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class RoutingRuleRequest {

    @NotBlank
    private String name;

    private String description;

    @NotNull
    private Integer priority;

    @NotBlank
    private String ruleType;

    private String conditions;

    private String actions;

    private Boolean isActive;
}
