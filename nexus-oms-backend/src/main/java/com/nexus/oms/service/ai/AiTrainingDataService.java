package com.nexus.oms.service.ai;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.nexus.oms.entity.ai.AiFeatureValue;
import com.nexus.oms.repository.OrderItemRepository;
import com.nexus.oms.repository.ai.AiFeatureDefinitionRepository;
import com.nexus.oms.repository.ai.AiFeatureValueRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.*;

/**
 * Phase 0+1 training-data plumbing.
 *
 * Aggregates real order history into per-SKU daily demand, exports it as a
 * JSONL training set (consumed by scripts/ml/forecast.py), and builds the
 * exact feature vector contract shared with the Python feature engineer so
 * that ONNX serving and training see identical inputs.
 */
@Service
public class AiTrainingDataService {

    private static final Logger log = LoggerFactory.getLogger(AiTrainingDataService.class);

    public static final int LOOKBACK = 7;
    public static final List<String> FEATURE_COLUMNS = List.of(
            "day_of_week", "month", "is_weekend", "is_festive_season",
            "lag_1", "lag_2", "lag_3", "lag_4", "lag_5", "lag_6", "lag_7",
            "rolling_mean_7", "rolling_std_7");
    private static final Set<Integer> FESTIVE_MONTHS = Set.of(10, 11, 12, 1);

    private final OrderItemRepository orderItemRepository;
    private final AiFeatureDefinitionRepository definitionRepository;
    private final AiFeatureValueRepository valueRepository;
    private final ObjectMapper objectMapper;

    public AiTrainingDataService(OrderItemRepository orderItemRepository,
                                 AiFeatureDefinitionRepository definitionRepository,
                                 AiFeatureValueRepository valueRepository,
                                 ObjectMapper objectMapper) {
        this.orderItemRepository = orderItemRepository;
        this.definitionRepository = definitionRepository;
        this.valueRepository = valueRepository;
        this.objectMapper = objectMapper;
    }

    /** Per-SKU daily demand map: (sku, date) -> units. */
    public Map<String, Map<LocalDate, Double>> dailyDemandBySku(UUID tenantId, int lookbackDays) {
        LocalDateTime from = LocalDate.now().minusDays(lookbackDays).atStartOfDay();
        List<Object[]> rows = orderItemRepository.aggregateDemandBySkuAndDay(tenantId, from);
        Map<String, Map<LocalDate, Double>> bySku = new HashMap<>();
        for (Object[] row : rows) {
            String sku = (String) row[0];
            java.sql.Date sqlDate = (java.sql.Date) row[1];
            Number demand = (Number) row[2];
            bySku.computeIfAbsent(sku, k -> new HashMap<>())
                    .put(sqlDate.toLocalDate(), demand.doubleValue());
        }
        return bySku;
    }

    /**
     * Build the ordered feature vector for a SKU on targetDate using the last
     * LOOKBACK days of demand. Must match scripts/ml/forecast.py build_features().
     */
    public float[] buildFeatures(Map<String, Map<LocalDate, Double>> bySku, String sku, LocalDate targetDate) {
        double[] series = new double[LOOKBACK];
        Map<LocalDate, Double> skuSeries = bySku.getOrDefault(sku, Map.of());
        for (int lag = 1; lag <= LOOKBACK; lag++) {
            series[lag - 1] = skuSeries.getOrDefault(targetDate.minusDays(lag), 0.0);
        }
        double sum = 0;
        for (double v : series) sum += v;
        double mean = sum / LOOKBACK;
        double variance = 0;
        for (double v : series) variance += (v - mean) * (v - mean);
        double std = Math.sqrt(variance / LOOKBACK);

        int dow = targetDate.getDayOfWeek().getValue() % 7; // Monday=0 .. Sunday=6
        int month = targetDate.getMonthValue();

        float[] features = new float[FEATURE_COLUMNS.size()];
        features[0] = dow;
        features[1] = month;
        features[2] = dow >= 5 ? 1 : 0;
        features[3] = FESTIVE_MONTHS.contains(month) ? 1 : 0;
        for (int lag = 1; lag <= LOOKBACK; lag++) {
            features[3 + lag] = (float) series[lag - 1];
        }
        features[11] = (float) mean;
        features[12] = (float) std;
        return features;
    }

    /** JSONL export: one {"sku","date","demand"} per SKU/day, oldest first. */
    public String exportDemandJsonl(UUID tenantId, int lookbackDays) {
        Map<String, Map<LocalDate, Double>> bySku = dailyDemandBySku(tenantId, lookbackDays);
        StringBuilder sb = new StringBuilder();
        List<String> skus = new ArrayList<>(bySku.keySet());
        Collections.sort(skus);
        for (String sku : skus) {
            Map<LocalDate, Double> series = bySku.get(sku);
            List<LocalDate> dates = new ArrayList<>(series.keySet());
            Collections.sort(dates);
            for (LocalDate date : dates) {
                try {
                    Map<String, Object> rec = Map.of(
                            "sku", sku,
                            "date", date.toString(),
                            "demand", series.get(date));
                    sb.append(objectMapper.writeValueAsString(rec)).append("\n");
                } catch (Exception e) {
                    log.warn("Failed to serialize demand record for {} on {}: {}", sku, date, e.getMessage());
                }
            }
        }
        return sb.toString();
    }

    public long getDemandRecordCount(UUID tenantId, int lookbackDays) {
        return dailyDemandBySku(tenantId, lookbackDays).values().stream().mapToLong(Map::size).sum();
    }

    public List<String> getSkusWithHistory(UUID tenantId, int lookbackDays) {
        return new ArrayList<>(dailyDemandBySku(tenantId, lookbackDays).keySet());
    }

    /**
     * Materialize daily demand into the feature store (ai_feature_values) so the
     * feature store holds real demand history rather than schema-only rows.
     */
    @Transactional
    public int materializeDemandFeatures(UUID tenantId, int lookbackDays) {
        var def = definitionRepository.findFirstByTenantIdAndNameAndEntityType(tenantId, "daily_demand", "SKU").orElse(null);
        if (def == null) {
            log.warn("Feature definition 'daily_demand' (SKU) not found; skipping feature-store materialization");
            return 0;
        }
        Map<String, Map<LocalDate, Double>> bySku = dailyDemandBySku(tenantId, lookbackDays);
        List<AiFeatureValue> values = new ArrayList<>();
        bySku.forEach((sku, series) ->
                series.forEach((date, demand) ->
                        values.add(AiFeatureValue.builder()
                                .tenantId(tenantId)
                                .featureId(def.getId())
                                .entityId(sku)
                                .entityType("SKU")
                                .numericValue(BigDecimal.valueOf(demand))
                                .asOfDate(date)
                                .build())));
        if (!values.isEmpty()) {
            valueRepository.saveAll(values);
        }
        return values.size();
    }
}
