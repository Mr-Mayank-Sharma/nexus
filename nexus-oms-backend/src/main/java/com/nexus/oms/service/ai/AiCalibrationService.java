package com.nexus.oms.service.ai;

import com.nexus.oms.entity.ai.AiCalibration;
import com.nexus.oms.repository.ai.AiCalibrationRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDate;
import java.util.*;

/**
 * Per-tenant / per-SKU calibration for the global model.
 *
 * Calibrates global LightGBM forecasts to a tenant's actual demand. The scale
 * factor is the exponentially-smoothed ratio observed/forecast, seeded from the
 * Python training script's calibration.json and updated with each confirmed
 * forecast-vs-actual pair. This is the "global model + per-tenant calibration"
 * layer of the architecture.
 */
@Service
public class AiCalibrationService {

    private static final Logger log = LoggerFactory.getLogger(AiCalibrationService.class);

    private static final double DEFAULT_SCALE = 1.0;
    private static final double SMOOTHING = 0.3;         // alpha for exponential smoothing
    private static final double MIN_SAMPLES = 3.0;       // samples before we trust entity scale
    private static final double MAX_SCALE = 5.0;         // clamp to avoid runaway factors
    private static final double MIN_SCALE = 0.2;

    private final AiCalibrationRepository calibrationRepository;

    public AiCalibrationService(AiCalibrationRepository calibrationRepository) {
        this.calibrationRepository = calibrationRepository;
    }

    /** Current scale factor for (tenant, model, entity). Defaults to 1.0 when absent. */
    public double scaleFactor(UUID tenantId, UUID modelId, String entityId) {
        if (entityId == null) return DEFAULT_SCALE;
        return calibrationRepository.findByTenantIdAndModelIdAndEntityId(tenantId, modelId, entityId)
                .map(c -> c.getScaleFactor() == null ? DEFAULT_SCALE : c.getScaleFactor().doubleValue())
                .orElse(DEFAULT_SCALE);
    }

    /** Observed-ratio seeding: scale = observed / forecast. Used on artifact upload. */
    @Transactional
    public void seedBaseline(UUID tenantId, UUID modelId, UUID versionId, Map<String, Double> skuRatios) {
        if (skuRatios == null || skuRatios.isEmpty()) return;
        int seeded = 0;
        for (Map.Entry<String, Double> e : skuRatios.entrySet()) {
            upsert(tenantId, modelId, versionId, e.getKey(), e.getValue(), 1);
            seeded++;
        }
        log.info("Seeded {} calibration entries for model {}", seeded, modelId);
    }

    /**
     * Update calibration with a confirmed (forecast, actual) pair for an entity.
     * scale_new = scale_old * (1-alpha) + observed/forecast * alpha
     */
    @Transactional
    public void recordActual(UUID tenantId, UUID modelId, UUID versionId, String entityId,
                             double forecast, double actual) {
        if (entityId == null || forecast <= 0) return;
        double ratio = Math.max(MIN_SCALE, Math.min(MAX_SCALE, actual / forecast));
        upsert(tenantId, modelId, versionId, entityId, ratio, 0);
    }

    private void upsert(UUID tenantId, UUID modelId, UUID versionId, String entityId,
                        double ratio, int minSamples) {
        AiCalibration cal = calibrationRepository
                .findByTenantIdAndModelIdAndEntityId(tenantId, modelId, entityId)
                .orElseGet(() -> {
                    double initial = DEFAULT_SCALE;
                    if (minSamples > 0) {
                        initial = ratio;
                    }
                    return AiCalibration.builder()
                            .tenantId(tenantId)
                            .modelId(modelId)
                            .versionId(versionId)
                            .entityId(entityId)
                            .entityType("SKU")
                            .scaleFactor(BigDecimal.valueOf(initial))
                            .sampleCount(0)
                            .build();
                });

        int samples = cal.getSampleCount() == null ? 0 : cal.getSampleCount();
        double current = cal.getScaleFactor() == null ? DEFAULT_SCALE : cal.getScaleFactor().doubleValue();

        if (samples < MIN_SAMPLES) {
            // Start with the raw ratio until enough observations accumulate
            current = ratio;
        } else {
            current = current * (1 - SMOOTHING) + ratio * SMOOTHING;
        }
        current = Math.max(MIN_SCALE, Math.min(MAX_SCALE, current));

        cal.setScaleFactor(BigDecimal.valueOf(current).setScale(6, RoundingMode.HALF_UP));
        cal.setSampleCount(samples + 1);
        cal.setWeight(BigDecimal.valueOf(Math.min(samples + 1, 100) / 100.0).setScale(6, RoundingMode.HALF_UP));
        calibrationRepository.save(cal);
    }

    /** Delete all calibrations for a model (e.g. on model archive / new global model). */
    @Transactional
    public void clearForModel(UUID modelId) {
        calibrationRepository.deleteByModelId(modelId);
    }

    public List<AiCalibration> listForModel(UUID tenantId, UUID modelId) {
        return calibrationRepository.findByTenantIdAndModelId(tenantId, modelId);
    }
}
