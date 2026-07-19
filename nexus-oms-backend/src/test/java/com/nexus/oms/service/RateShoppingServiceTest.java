package com.nexus.oms.service;

import com.nexus.oms.dto.RateQuote;
import com.nexus.oms.dto.RateShoppingRequest;
import com.nexus.oms.dto.RateShoppingResult;
import com.nexus.oms.entity.NxCarrierRate;
import com.nexus.oms.repository.CarrierRateRepository;
import com.nexus.oms.repository.RateShoppingLogRepository;
import com.nexus.oms.security.TenantAwarePrincipal;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;

import java.math.BigDecimal;
import java.util.List;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class RateShoppingServiceTest {

    @Mock
    private CarrierRateRepository carrierRateRepository;
    @Mock
    private RateShoppingLogRepository rateShoppingLogRepository;
    @Mock
    private RateCacheService rateCacheService;

    private RateShoppingService rateShoppingService;
    private UUID tenantId;

    @BeforeEach
    void setUp() {
        rateShoppingService = new RateShoppingService(carrierRateRepository, rateShoppingLogRepository, rateCacheService);
        tenantId = UUID.randomUUID();
        SecurityContextHolder.getContext().setAuthentication(
                new UsernamePasswordAuthenticationToken(
                        new TenantAwarePrincipal("admin", tenantId),
                        null,
                        List.of(new SimpleGrantedAuthority("ROLE_ADMIN"))));
    }

    @AfterEach
    void tearDown() {
        SecurityContextHolder.clearContext();
    }

    private RateShoppingRequest request(String from, String to, BigDecimal weight) {
        RateShoppingRequest r = new RateShoppingRequest();
        r.setFromZip(from);
        r.setToZip(to);
        r.setTotalWeightKg(weight);
        return r;
    }

    private NxCarrierRate rate(String code, BigDecimal base, BigDecimal perKg, BigDecimal fuelPct,
                                BigDecimal residential, int minDays, int maxDays, String service) {
        NxCarrierRate r = new NxCarrierRate();
        r.setCarrierCode(code);
        r.setBaseRate(base);
        r.setPerKgRate(perKg);
        r.setFuelSurchargePct(fuelPct);
        r.setResidentialSurcharge(residential);
        r.setTransitDaysMin(minDays);
        r.setTransitDaysMax(maxDays);
        r.setServiceLevel(service);
        return r;
    }

    @Test
    void shopRates_cacheHit_returnsCached() {
        RateShoppingResult cached = RateShoppingResult.builder().fromZip("10001").build();
        when(rateCacheService.buildCacheKey(anyString(), anyString(), anyDouble(), any(), any())).thenReturn("key");
        when(rateCacheService.get("key")).thenReturn(cached);

        RateShoppingRequest req = request("10001", "90001", BigDecimal.TEN);
        RateShoppingResult result = rateShoppingService.shopRates(req);

        assertEquals("10001", result.getFromZip());
        verify(carrierRateRepository, never()).findEligibleRates(any(), any());
    }

    @Test
    void shopRates_cacheMiss_queriesAndCaches() {
        NxCarrierRate rate = rate("UPS", BigDecimal.TEN, BigDecimal.ZERO, BigDecimal.ZERO, null, 2, 5, "GROUND");

        when(rateCacheService.buildCacheKey(anyString(), anyString(), anyDouble(), any(), any())).thenReturn("key");
        when(rateCacheService.get("key")).thenReturn(null);
        when(carrierRateRepository.findEligibleRates(any(), any())).thenReturn(List.of(rate));

        RateShoppingResult result = rateShoppingService.shopRates(request("10001", "90001", BigDecimal.TEN));

        assertFalse(result.getRates().isEmpty());
        verify(rateCacheService).put(eq("key"), any());
    }

    @Test
    void shopRates_setsDefaults() {
        when(rateCacheService.buildCacheKey(anyString(), anyString(), anyDouble(), any(), any())).thenReturn("key");
        when(rateCacheService.get("key")).thenReturn(null);
        when(carrierRateRepository.findEligibleRates(any(), any())).thenReturn(List.of());

        RateShoppingResult result = rateShoppingService.shopRates(request("10001", "90001", BigDecimal.TEN));

        assertEquals("US", result.getToCountry());
    }

    @Test
    void shopRates_filtersByServiceLevels() {
        when(rateCacheService.buildCacheKey(anyString(), anyString(), anyDouble(), any(), any())).thenReturn("key");
        when(rateCacheService.get("key")).thenReturn(null);
        when(carrierRateRepository.findEligibleRatesByService(any(), any(), anyList())).thenReturn(List.of());

        RateShoppingRequest req = request("10001", "90001", BigDecimal.TEN);
        req.setServiceLevels(List.of("GROUND"));

        RateShoppingResult result = rateShoppingService.shopRates(req);

        assertNotNull(result);
        verify(carrierRateRepository).findEligibleRatesByService(any(), any(), anyList());
    }

    @Test
    void shopRates_perKgCharge() {
        NxCarrierRate rate = rate("FEDEX", BigDecimal.TEN, BigDecimal.valueOf(2), BigDecimal.ZERO, null, 1, 3, "PRIORITY");
        rate.setCarrierName("FedEx");
        rate.setServiceName("Priority Overnight");
        rate.setZone("Z1");

        when(rateCacheService.buildCacheKey(anyString(), anyString(), anyDouble(), any(), any())).thenReturn("key");
        when(rateCacheService.get("key")).thenReturn(null);
        when(carrierRateRepository.findEligibleRates(any(), any())).thenReturn(List.of(rate));

        RateShoppingResult result = rateShoppingService.shopRates(request("10001", "90001", BigDecimal.valueOf(5)));

        assertEquals(1, result.getRates().size());
        assertEquals("FEDEX", result.getRates().get(0).getCarrierCode());
    }

    @Test
    void shopRates_withFuelSurcharge() {
        NxCarrierRate rate = rate("UPS", BigDecimal.valueOf(100), BigDecimal.ZERO, BigDecimal.valueOf(10), null, 1, 2, "GROUND");

        when(rateCacheService.buildCacheKey(anyString(), anyString(), anyDouble(), any(), any())).thenReturn("key");
        when(rateCacheService.get("key")).thenReturn(null);
        when(carrierRateRepository.findEligibleRates(any(), any())).thenReturn(List.of(rate));

        RateShoppingResult result = rateShoppingService.shopRates(request("10001", "90001", BigDecimal.ONE));

        assertEquals(1, result.getRates().size());
    }

    @Test
    void shopRates_residentialSurcharge() {
        NxCarrierRate rate = rate("UPS", BigDecimal.valueOf(50), BigDecimal.ZERO, BigDecimal.ZERO, BigDecimal.valueOf(5), 1, 2, "GROUND");

        when(rateCacheService.buildCacheKey(anyString(), anyString(), anyDouble(), any(), any())).thenReturn("key");
        when(rateCacheService.get("key")).thenReturn(null);
        when(carrierRateRepository.findEligibleRates(any(), any())).thenReturn(List.of(rate));

        RateShoppingRequest req = request("10001", "90001", BigDecimal.ONE);
        req.setResidential(true);

        RateShoppingResult result = rateShoppingService.shopRates(req);

        assertEquals(1, result.getRates().size());
    }

    @Test
    void shopRates_multiPackage() {
        NxCarrierRate rate = rate("UPS", BigDecimal.TEN, BigDecimal.ZERO, BigDecimal.ZERO, null, 1, 2, "GROUND");

        when(rateCacheService.buildCacheKey(anyString(), anyString(), anyDouble(), any(), any())).thenReturn("key");
        when(rateCacheService.get("key")).thenReturn(null);
        when(carrierRateRepository.findEligibleRates(any(), any())).thenReturn(List.of(rate));

        RateShoppingRequest req = request("10001", "90001", BigDecimal.ONE);
        req.setNumPackages(3);

        RateShoppingResult result = rateShoppingService.shopRates(req);

        assertEquals(1, result.getRates().size());
    }

    @Test
    void getBestRate_returnsCheapest() {
        NxCarrierRate rate = rate("UPS", BigDecimal.TEN, BigDecimal.ZERO, BigDecimal.ZERO, null, 1, 2, "GROUND");

        when(rateCacheService.buildCacheKey(anyString(), anyString(), anyDouble(), any(), any())).thenReturn("key");
        when(rateCacheService.get("key")).thenReturn(null);
        when(carrierRateRepository.findEligibleRates(any(), any())).thenReturn(List.of(rate));

        RateShoppingResult result = rateShoppingService.getBestRate(request("10001", "90001", BigDecimal.ONE));

        assertNotNull(result.getSelected());
        assertEquals(1, result.getRates().size());
    }

    @Test
    void shopRates_withOrderId_selectsCheapest() {
        NxCarrierRate rate = rate("UPS", BigDecimal.TEN, BigDecimal.ZERO, BigDecimal.ZERO, null, 1, 2, "GROUND");

        when(rateCacheService.buildCacheKey(anyString(), anyString(), anyDouble(), any(), any())).thenReturn("key");
        when(rateCacheService.get("key")).thenReturn(null);
        when(carrierRateRepository.findEligibleRates(any(), any())).thenReturn(List.of(rate));

        RateShoppingRequest req = request("10001", "90001", BigDecimal.ONE);
        req.setOrderId(UUID.randomUUID());

        RateShoppingResult result = rateShoppingService.shopRates(req);

        assertNotNull(result.getSelected());
    }


}
