package com.nexus.oms.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import java.math.BigDecimal;

@Data @NoArgsConstructor @AllArgsConstructor @Builder
public class RateQuote {
    private String carrierCode;
    private String carrierName;
    private String serviceLevel;
    private String serviceName;
    private BigDecimal totalCost;
    private BigDecimal baseRate;
    private BigDecimal perKgCharge;
    private BigDecimal fuelSurcharge;
    private BigDecimal residentialSurcharge;
    private Integer transitDaysMin;
    private Integer transitDaysMax;
    private String estimatedDelivery;
    private String recommendation;
    private String zone;
}
