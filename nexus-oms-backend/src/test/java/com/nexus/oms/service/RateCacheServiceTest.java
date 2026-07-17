package com.nexus.oms.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.nexus.oms.dto.RateQuote;
import com.nexus.oms.dto.RateShoppingResult;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.ValueOperations;

import java.math.BigDecimal;
import java.time.Duration;
import java.util.List;
import java.util.Map;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class RateCacheServiceTest {

    @Mock
    private StringRedisTemplate redisTemplate;

    @Mock
    private ValueOperations<String, String> valueOperations;

    private ObjectMapper objectMapper;
    private RateCacheService rateCacheService;

    @BeforeEach
    void setUp() {
        objectMapper = new ObjectMapper();
        rateCacheService = new RateCacheService(redisTemplate, objectMapper, 15);
        lenient().when(redisTemplate.opsForValue()).thenReturn(valueOperations);
    }

    @Test
    void buildCacheKey_producesDeterministicHash() {
        String key1 = rateCacheService.buildCacheKey("94102", "10001", 2.5, "FEDEX", "GROUND");
        String key2 = rateCacheService.buildCacheKey("94102", "10001", 2.5, "FEDEX", "GROUND");
        assertEquals(key1, key2);
        assertTrue(key1.startsWith("rate:"));
        assertEquals(64 + 5, key1.length()); // "rate:" + 64 hex chars
    }

    @Test
    void buildCacheKey_differsWhenInputChanges() {
        String key1 = rateCacheService.buildCacheKey("94102", "10001", 2.5, "FEDEX", "GROUND");
        String key2 = rateCacheService.buildCacheKey("94102", "10001", 2.5, "UPS", "GROUND");
        assertNotEquals(key1, key2);
    }

    @Test
    void get_whenCacheMiss_returnsNullAndIncrementsMisses() {
        when(valueOperations.get(anyString())).thenReturn(null);

        RateShoppingResult result = rateCacheService.get("rate:nonexistent");

        assertNull(result);
        verify(valueOperations).get("rate:nonexistent");
        verify(valueOperations).increment("ratecache:misses");
    }

    @Test
    void get_whenCacheHit_deserializesAndIncrementsHits() throws Exception {
        RateShoppingResult expected = createSampleResult();
        String json = objectMapper.writeValueAsString(expected);
        when(valueOperations.get("rate:somekey")).thenReturn(json);

        RateShoppingResult result = rateCacheService.get("rate:somekey");

        assertNotNull(result);
        assertEquals("FEDEX", result.getRates().get(0).getCarrierCode());
        assertEquals(new BigDecimal("15.50"), result.getRates().get(0).getTotalCost());
        verify(valueOperations).increment("ratecache:hits");
    }

    @Test
    void put_storesJsonWithDefaultTtl() throws Exception {
        RateShoppingResult result = createSampleResult();

        rateCacheService.put("rate:somekey", result);

        verify(valueOperations).set(eq("rate:somekey"), anyString(), eq(Duration.ofMinutes(15)));
    }

    @Test
    void put_withCustomTtl_storesWithCustomDuration() throws Exception {
        RateShoppingResult result = createSampleResult();

        rateCacheService.put("rate:somekey", result, Duration.ofMinutes(30));

        verify(valueOperations).set(eq("rate:somekey"), anyString(), eq(Duration.ofMinutes(30)));
    }

    @Test
    void evict_deletesKey() {
        rateCacheService.evict("rate:somekey");

        verify(redisTemplate).delete("rate:somekey");
    }

    @Test
    void getStats_returnsCorrectCounts() {
        when(valueOperations.get("ratecache:hits")).thenReturn("40");
        when(valueOperations.get("ratecache:misses")).thenReturn("10");
        when(redisTemplate.keys("rate:*")).thenReturn(Set.of("rate:a", "rate:b", "rate:c"));

        Map<String, Object> stats = rateCacheService.getStats();

        assertEquals(40L, stats.get("hits"));
        assertEquals(10L, stats.get("misses"));
        assertEquals(50L, stats.get("totalRequests"));
        assertEquals("80.0%", stats.get("hitRate"));
        assertEquals(3, stats.get("cachedEntries"));
        assertEquals(15L, stats.get("ttlMinutes"));
    }

    @Test
    void getStats_whenNoData_returnsZeroCounts() {
        when(redisTemplate.keys("rate:*")).thenReturn(Set.of());

        Map<String, Object> stats = rateCacheService.getStats();

        assertEquals(0L, stats.get("hits"));
        assertEquals(0L, stats.get("misses"));
        assertEquals(0L, stats.get("totalRequests"));
        assertEquals("0.0%", stats.get("hitRate"));
        assertEquals(0, stats.get("cachedEntries"));
    }

    private RateShoppingResult createSampleResult() {
        RateQuote quote = RateQuote.builder()
                .carrierCode("FEDEX")
                .carrierName("FedEx")
                .serviceLevel("GROUND")
                .serviceName("FedEx Ground")
                .totalCost(new BigDecimal("15.50"))
                .baseRate(new BigDecimal("5.00"))
                .perKgCharge(new BigDecimal("8.00"))
                .fuelSurcharge(new BigDecimal("2.50"))
                .transitDaysMin(3)
                .transitDaysMax(5)
                .estimatedDelivery("2026-07-20")
                .zone("Z2")
                .build();

        return RateShoppingResult.builder()
                .fromZip("94102")
                .toZip("10001")
                .totalWeightKg(new BigDecimal("2.5"))
                .numPackages(1)
                .rates(List.of(quote))
                .build();
    }
}
