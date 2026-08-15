package com.nexus.oms.service.ai;

import com.nexus.oms.dto.DemandForecastResponse;
import com.nexus.oms.repository.OrderRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class DemandForecastServiceTest {

    @Mock
    private OrderRepository orderRepository;

    private DemandForecastService service;
    private UUID tenantId;

    @BeforeEach
    void setUp() {
        service = new DemandForecastService(orderRepository);
        tenantId = UUID.randomUUID();
    }

    @Test
    void evaluate_WapeIsLowOnTrendingSeries() {
        List<LocalDateTime> createdAts = new ArrayList<>();
        LocalDate start = LocalDate.now().minusDays(89);
        for (int day = 0; day < 90; day++) {
            int count = 5 + day / 3;
            LocalDate d = start.plusDays(day);
            for (int i = 0; i < count; i++) {
                createdAts.add(LocalDateTime.of(d, LocalTime.NOON).plusMinutes(i));
            }
        }
        when(orderRepository.findCreatedAtSince(eq(tenantId), any(LocalDateTime.class)))
                .thenReturn(createdAts);

        Map<String, Object> eval = service.evaluate(tenantId);

        assertNotNull(eval.get("wape"));
        double wape = (Double) eval.get("wape");
        assertTrue(wape < 50.0, "WAPE should be bounded on a smooth trend, was " + wape);
        assertEquals("HOLT_EXPONENTIAL_SMOOTHING", eval.get("model"));
        assertTrue(eval.get("alpha") instanceof Double);
    }

    @Test
    void forecast_ProducesPositiveNext7AndNext30() {
        List<LocalDateTime> createdAts = new ArrayList<>();
        LocalDate start = LocalDate.now().minusDays(89);
        for (int day = 0; day < 90; day++) {
            int count = 10 + day % 5;
            LocalDate d = start.plusDays(day);
            for (int i = 0; i < count; i++) {
                createdAts.add(LocalDateTime.of(d, LocalTime.NOON).plusMinutes(i));
            }
        }
        when(orderRepository.findCreatedAtSince(eq(tenantId), any(LocalDateTime.class)))
                .thenReturn(createdAts);

        DemandForecastResponse response = service.forecast(tenantId, 30);

        assertNotNull(response);
        assertNotNull(response.getNext7Days());
        assertNotNull(response.getNext30Days());
        assertEquals(7, response.getNext7Days().size());
        assertEquals(30, response.getNext30Days().size());
        assertTrue(response.getNext30Days().values().stream().allMatch(v -> v >= 0));
    }

    @Test
    void forecast_EmptyHistory_ReturnsZeroForecast() {
        when(orderRepository.findCreatedAtSince(eq(tenantId), any(LocalDateTime.class)))
                .thenReturn(List.of());

        DemandForecastResponse response = service.forecast(tenantId, 30);

        assertEquals(7, response.getNext7Days().size());
        assertEquals(30, response.getNext30Days().size());
        assertTrue(response.getNext30Days().values().stream().allMatch(v -> v == 0));
    }

    @Test
    void fitHolt_ProducesFiniteParameters() {
        double[] series = {10, 12, 11, 13, 14, 15, 14, 16, 17, 18, 19, 20};
        DemandForecastService.HoltResult fit = service.fitHolt(series);

        assertTrue(fit.alpha > 0 && fit.alpha <= 1);
        assertTrue(fit.beta > 0 && fit.beta <= 1);
        assertTrue(fit.level > 0);
        assertTrue(fit.residualStd >= 0);
        assertTrue(Double.isFinite(fit.sse));
    }
}
