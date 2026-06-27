package com.nexus.oms.service;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.nexus.oms.dto.RateQuote;
import com.nexus.oms.dto.RateShoppingRequest;
import com.nexus.oms.dto.RateShoppingResult;
import com.nexus.oms.entity.NxCarrierRate;
import com.nexus.oms.entity.NxRateShoppingLog;
import com.nexus.oms.repository.CarrierRateRepository;
import com.nexus.oms.repository.RateShoppingLogRepository;
import com.nexus.oms.security.TenantContext;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.*;
import java.util.stream.Collectors;

@Service
public class RateShoppingService {

    private static final Logger log = LoggerFactory.getLogger(RateShoppingService.class);
    private static final ObjectMapper MAPPER = new ObjectMapper();
    private static final DateTimeFormatter DATE_FMT = DateTimeFormatter.ofPattern("yyyy-MM-dd");

    private final CarrierRateRepository carrierRateRepository;
    private final RateShoppingLogRepository rateShoppingLogRepository;

    public RateShoppingService(CarrierRateRepository carrierRateRepository,
                                RateShoppingLogRepository rateShoppingLogRepository) {
        this.carrierRateRepository = carrierRateRepository;
        this.rateShoppingLogRepository = rateShoppingLogRepository;
    }

    @Transactional
    public RateShoppingResult shopRates(RateShoppingRequest request) {
        long startTime = System.currentTimeMillis();
        UUID tenantId = TenantContext.getCurrentTenantId();

        if (request.getToCountry() == null || request.getToCountry().isBlank()) {
            request.setToCountry("US");
        }
        if (request.getNumPackages() == null || request.getNumPackages() < 1) {
            request.setNumPackages(1);
        }
        if (request.getDeclaredValue() == null) {
            request.setDeclaredValue(BigDecimal.ZERO);
        }
        if (request.getResidential() == null) {
            request.setResidential(false);
        }

        List<NxCarrierRate> eligibleRates;
        if (request.getServiceLevels() != null && !request.getServiceLevels().isEmpty()) {
            eligibleRates = carrierRateRepository.findEligibleRatesByService(
                    tenantId, request.getTotalWeightKg(), request.getServiceLevels());
        } else {
            eligibleRates = carrierRateRepository.findEligibleRates(tenantId, request.getTotalWeightKg());
        }

        List<RateQuote> quotes = eligibleRates.stream()
                .map(rate -> calculateQuote(rate, request))
                .sorted(Comparator.comparing(RateQuote::getTotalCost))
                .collect(Collectors.toList());

        RateQuote fastest = quotes.stream()
                .min(Comparator.comparingInt(RateQuote::getTransitDaysMin))
                .orElse(null);

        RateQuote cheapest = quotes.isEmpty() ? null : quotes.get(0);

        RateQuote bestValue = quotes.stream()
                .filter(q -> q.getTransitDaysMax() <= 5)
                .min(Comparator.comparing(RateQuote::getTotalCost))
                .orElse(cheapest);

        RateQuote selected = request.getOrderId() != null ? cheapest : null;

        if (fastest != null) fastest.setRecommendation("Fastest");
        if (cheapest != null) cheapest.setRecommendation("Cheapest");
        if (bestValue != null && !bestValue.equals(fastest) && !bestValue.equals(cheapest)) {
            bestValue.setRecommendation("Best Value");
        }

        long execTime = System.currentTimeMillis() - startTime;

        RateShoppingResult result = RateShoppingResult.builder()
                .fromZip(request.getFromZip())
                .toZip(request.getToZip())
                .toCountry(request.getToCountry())
                .totalWeightKg(request.getTotalWeightKg())
                .declaredValue(request.getDeclaredValue())
                .numPackages(request.getNumPackages())
                .rates(quotes)
                .fastest(fastest)
                .cheapest(cheapest)
                .bestValue(bestValue)
                .selected(selected)
                .executionTimeMs(execTime)
                .build();

        saveShoppingLog(request, result, selected, tenantId);

        return result;
    }

    public RateShoppingResult getBestRate(RateShoppingRequest request) {
        RateShoppingResult result = shopRates(request);
        RateQuote best = result.getCheapest();
        if (best != null) {
            result.setSelected(best);
            result.setRates(List.of(best));
        }
        return result;
    }

    private RateQuote calculateQuote(NxCarrierRate rate, RateShoppingRequest request) {
        BigDecimal totalCost = rate.getBaseRate();

        if (rate.getPerKgRate() != null && rate.getPerKgRate().compareTo(BigDecimal.ZERO) > 0) {
            BigDecimal perKgCharge = request.getTotalWeightKg().multiply(rate.getPerKgRate());
            totalCost = totalCost.add(perKgCharge);
        }

        if (rate.getFuelSurchargePct() != null && rate.getFuelSurchargePct().compareTo(BigDecimal.ZERO) > 0) {
            BigDecimal fuelSurcharge = totalCost.multiply(rate.getFuelSurchargePct())
                    .divide(new BigDecimal("100"), 2, RoundingMode.HALF_UP);
            totalCost = totalCost.add(fuelSurcharge);
        }

        if (Boolean.TRUE.equals(request.getResidential())
                && rate.getResidentialSurcharge() != null
                && rate.getResidentialSurcharge().compareTo(BigDecimal.ZERO) > 0) {
            totalCost = totalCost.add(rate.getResidentialSurcharge());
        }

        if (request.getNumPackages() > 1) {
            totalCost = totalCost.multiply(BigDecimal.valueOf(request.getNumPackages()));
        }

        LocalDate today = LocalDate.now();
        String estDelivery = today.plusDays(rate.getTransitDaysMin()).format(DATE_FMT);

        return RateQuote.builder()
                .carrierCode(rate.getCarrierCode())
                .carrierName(rate.getCarrierName())
                .serviceLevel(rate.getServiceLevel())
                .serviceName(rate.getServiceName())
                .totalCost(totalCost.setScale(2, RoundingMode.HALF_UP))
                .baseRate(rate.getBaseRate())
                .perKgCharge(request.getTotalWeightKg().multiply(
                        rate.getPerKgRate() != null ? rate.getPerKgRate() : BigDecimal.ZERO).setScale(2, RoundingMode.HALF_UP))
                .fuelSurcharge(BigDecimal.ZERO)
                .residentialSurcharge(Boolean.TRUE.equals(request.getResidential()) && rate.getResidentialSurcharge() != null
                        ? rate.getResidentialSurcharge() : BigDecimal.ZERO)
                .transitDaysMin(rate.getTransitDaysMin())
                .transitDaysMax(rate.getTransitDaysMax())
                .estimatedDelivery(estDelivery)
                .zone(rate.getZone())
                .build();
    }

    private void saveShoppingLog(RateShoppingRequest request, RateShoppingResult result,
                                  RateQuote selected, UUID tenantId) {
        try {
            String resultsJson = MAPPER.writeValueAsString(result.getRates());

            rateShoppingLogRepository.save(NxRateShoppingLog.builder()
                    .orderId(request.getOrderId())
                    .tenantId(tenantId)
                    .fromZip(request.getFromZip())
                    .toZip(request.getToZip())
                    .toCountry(request.getToCountry())
                    .totalWeightKg(request.getTotalWeightKg())
                    .declaredValue(request.getDeclaredValue())
                    .numPackages(request.getNumPackages())
                    .results(resultsJson)
                    .selectedCarrierCode(selected != null ? selected.getCarrierCode() : null)
                    .selectedService(selected != null ? selected.getServiceLevel() : null)
                    .totalCost(selected != null ? selected.getTotalCost() : BigDecimal.ZERO)
                    .estimatedDeliveryDays(selected != null ? selected.getTransitDaysMin() : null)
                    .executionTimeMs((int) result.getExecutionTimeMs())
                    .build());
        } catch (JsonProcessingException e) {
            log.warn("Failed to serialize rate results for log: {}", e.getMessage());
        }
    }
}
