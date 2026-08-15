package com.nexus.oms.service.ai;

import com.nexus.oms.dto.DemandForecastResponse;
import com.nexus.oms.repository.OrderRepository;
import org.springframework.stereotype.Service;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.*;

@Service
public class DemandForecastService {

    private static final int DEFAULT_HISTORY_DAYS = 90;
    private static final int DEFAULT_HOLDOUT_DAYS = 14;
    private static final double P90_Z = 1.2816;

    private final OrderRepository orderRepository;

    public DemandForecastService(OrderRepository orderRepository) {
        this.orderRepository = orderRepository;
    }

    public DemandForecastResponse forecast(UUID tenantId, int horizonDays) {
        LocalDateTime from = LocalDateTime.now().minusDays(DEFAULT_HISTORY_DAYS);
        List<LocalDateTime> createdAts = orderRepository.findCreatedAtSince(tenantId, from);

        SortedMap<LocalDate, Integer> daily = new TreeMap<>();
        LocalDate today = LocalDate.now();
        for (LocalDate d = today.minusDays(DEFAULT_HISTORY_DAYS); !d.isAfter(today); d = d.plusDays(1)) {
            daily.put(d, 0);
        }
        for (LocalDateTime ts : createdAts) {
            LocalDate d = ts.toLocalDate();
            if (daily.containsKey(d)) {
                daily.put(d, daily.get(d) + 1);
            }
        }

        int[] series = daily.values().stream().mapToInt(Integer::intValue).toArray();
        if (series.length < 14) {
            return emptyForecast();
        }

        HoltResult fit = fitHolt(toDouble(series));
        int horizon = Math.max(1, horizonDays);
        Map<String, Integer> next7 = new LinkedHashMap<>();
        Map<String, Integer> next30 = new LinkedHashMap<>();
        Map<Double, Double> next = forecastSeries(series.length, fit, horizon);
        for (int h = 1; h <= horizon; h++) {
            LocalDate d = daily.lastKey().plusDays(h);
            int value = (int) Math.max(0, Math.round(next.getOrDefault((double) h, 0.0)));
            if (h <= 7) next7.put(d.toString(), value);
            next30.put(d.toString(), value);
        }

        return DemandForecastResponse.builder()
                .next7Days(next7)
                .next30Days(next30)
                .confidence(DemandForecastResponse.ConfidenceInterval.builder()
                        .lower(Math.max(0, fit.level + fit.trend - P90_Z * fit.residualStd))
                        .upper(fit.level + fit.trend + P90_Z * fit.residualStd)
                        .build())
                .build();
    }

    public Map<String, Object> evaluate(UUID tenantId) {
        LocalDateTime from = LocalDateTime.now().minusDays(DEFAULT_HISTORY_DAYS);
        List<LocalDateTime> createdAts = orderRepository.findCreatedAtSince(tenantId, from);

        SortedMap<LocalDate, Integer> daily = new TreeMap<>();
        LocalDate today = LocalDate.now();
        for (LocalDate d = today.minusDays(DEFAULT_HISTORY_DAYS); !d.isAfter(today); d = d.plusDays(1)) {
            daily.put(d, 0);
        }
        for (LocalDateTime ts : createdAts) {
            LocalDate d = ts.toLocalDate();
            if (daily.containsKey(d)) {
                daily.put(d, daily.get(d) + 1);
            }
        }

        int[] series = daily.values().stream().mapToInt(Integer::intValue).toArray();
        Map<String, Object> result = new LinkedHashMap<>();
        result.put("historyDays", series.length);

        if (series.length < 14) {
            result.put("wape", null);
            result.put("model", "HOLT_EXPONENTIAL_SMOOTHING");
            result.put("message", "Insufficient order history for holdout evaluation");
            return result;
        }

        double[] actual = Arrays.stream(Arrays.copyOfRange(series, series.length - DEFAULT_HOLDOUT_DAYS, series.length))
                .asDoubleStream().toArray();
        double[] train = Arrays.stream(Arrays.copyOfRange(series, 0, series.length - DEFAULT_HOLDOUT_DAYS))
                .asDoubleStream().toArray();
        HoltResult fit = fitHolt(train);
        double[] predicted = forecastSeries(train.length, fit, DEFAULT_HOLDOUT_DAYS)
                .values().stream().mapToDouble(Double::doubleValue).toArray();

        double absErrorSum = 0;
        double actualSum = 0;
        for (int i = 0; i < actual.length; i++) {
            absErrorSum += Math.abs(actual[i] - predicted[i]);
            actualSum += actual[i];
        }
        double wape = actualSum > 0 ? absErrorSum / actualSum : 0;

        result.put("wape", Math.round(wape * 10000) / 100.0);
        result.put("mae", Math.round(absErrorSum / actual.length * 100) / 100.0);
        result.put("alpha", fit.alpha);
        result.put("beta", fit.beta);
        result.put("holdoutDays", DEFAULT_HOLDOUT_DAYS);
        result.put("model", "HOLT_EXPONENTIAL_SMOOTHING");
        return result;
    }

    public HoltResult fitHolt(double[] series) {
        double bestAlpha = 0.2;
        double bestBeta = 0.1;
        double bestSse = Double.MAX_VALUE;
        for (double alpha = 0.05; alpha <= 0.95; alpha += 0.05) {
            for (double beta = 0.05; beta <= 0.95; beta += 0.05) {
                double sse = fitError(series, alpha, beta);
                if (sse < bestSse) {
                    bestSse = sse;
                    bestAlpha = alpha;
                    bestBeta = beta;
                }
            }
        }

        double[] state = runHolt(series, bestAlpha, bestBeta);
        double level = state[0];
        double trend = state[1];
        double[] fitted = fittedSeries(series, bestAlpha, bestBeta);
        double sse = 0;
        for (int i = 0; i < series.length; i++) {
            double e = series[i] - fitted[i];
            sse += e * e;
        }
        double residualStd = series.length > 2 ? Math.sqrt(sse / (series.length - 2)) : 0;

        HoltResult result = new HoltResult();
        result.alpha = bestAlpha;
        result.beta = bestBeta;
        result.level = level;
        result.trend = trend;
        result.residualStd = residualStd;
        result.sse = sse;
        return result;
    }

    private double fitError(double[] series, double alpha, double beta) {
        double[] fitted = fittedSeries(series, alpha, beta);
        double sse = 0;
        for (int i = 1; i < series.length; i++) {
            double e = series[i] - fitted[i];
            sse += e * e;
        }
        return sse;
    }

    private double[] fittedSeries(double[] series, double alpha, double beta) {
        double[] fitted = new double[series.length];
        double level = series[0];
        double trend = series.length > 1 ? series[1] - series[0] : 0;
        fitted[0] = level;
        for (int i = 1; i < series.length; i++) {
            double prevLevel = level;
            level = alpha * series[i] + (1 - alpha) * (level + trend);
            trend = beta * (level - prevLevel) + (1 - beta) * trend;
            fitted[i] = level;
        }
        return fitted;
    }

    private double[] runHolt(double[] series, double alpha, double beta) {
        double level = series[0];
        double trend = series.length > 1 ? series[1] - series[0] : 0;
        for (int i = 1; i < series.length; i++) {
            double prevLevel = level;
            level = alpha * series[i] + (1 - alpha) * (level + trend);
            trend = beta * (level - prevLevel) + (1 - beta) * trend;
        }
        return new double[]{level, trend};
    }

    private Map<Double, Double> forecastSeries(int length, HoltResult fit, int horizon) {
        Map<Double, Double> forecast = new LinkedHashMap<>();
        for (int h = 1; h <= horizon; h++) {
            forecast.put((double) h, fit.level + h * fit.trend);
        }
        return forecast;
    }

    private DemandForecastResponse emptyForecast() {
        return DemandForecastResponse.builder()
                .next7Days(Map.of())
                .next30Days(Map.of())
                .confidence(DemandForecastResponse.ConfidenceInterval.builder().lower(0).upper(0).build())
                .build();
    }

    public static class HoltResult {
        public double alpha;
        public double beta;
        public double level;
        public double trend;
        public double residualStd;
        public double sse;
    }

    private double[] toDouble(int[] values) {
        double[] out = new double[values.length];
        for (int i = 0; i < values.length; i++) {
            out[i] = values[i];
        }
        return out;
    }
}
