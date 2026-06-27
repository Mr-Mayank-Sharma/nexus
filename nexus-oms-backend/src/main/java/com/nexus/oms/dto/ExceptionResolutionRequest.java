package com.nexus.oms.dto;

import jakarta.validation.constraints.NotBlank;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data @NoArgsConstructor @AllArgsConstructor
public class ExceptionResolutionRequest {
    @NotBlank
    private String resolution;

    private String resolutionStrategy;
}
