package com.nexus.oms.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import java.math.BigDecimal;
import java.util.List;

@Data @NoArgsConstructor @AllArgsConstructor @Builder
public class RateShoppingResult {
    private String fromZip;
    private String toZip;
    private String toCountry;
    private BigDecimal totalWeightKg;
    private BigDecimal declaredValue;
    private int numPackages;
    private List<RateQuote> rates;
    private RateQuote fastest;
    private RateQuote cheapest;
    private RateQuote bestValue;
    private RateQuote selected;
    private long executionTimeMs;
}
