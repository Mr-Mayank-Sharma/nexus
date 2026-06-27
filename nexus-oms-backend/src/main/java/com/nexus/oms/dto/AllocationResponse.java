package com.nexus.oms.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import java.math.BigDecimal;

@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class AllocationResponse {

    private String warehouse;
    private String carrier;
    private String boxSize;
    private String pickPackDetails;
    private BigDecimal confidence;
    private String rule;
}
