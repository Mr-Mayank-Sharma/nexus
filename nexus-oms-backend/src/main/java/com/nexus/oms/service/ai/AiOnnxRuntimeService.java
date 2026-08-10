package com.nexus.oms.service.ai;

import ai.onnxruntime.*;
import com.nexus.oms.entity.ai.AiModel;
import com.nexus.oms.entity.ai.AiModelVersion;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;

/**
 * In-process ONNX model serving via ONNX Runtime Java.
 *
 * A single lightgbm2onnx artifact (median P50 model) is loaded once per version
 * and cached. The service builds the ordered feature vector (see
 * AiTrainingDataService.FEATURE_COLUMNS) and runs inference in ~1-10ms, with no
 * external serving sidecar required.
 */
@Service
public class AiOnnxRuntimeService {

    private static final Logger log = LoggerFactory.getLogger(AiOnnxRuntimeService.class);

    private final Map<UUID, OrtSession> sessions = new ConcurrentHashMap<>();
    private final AiTrainingDataService trainingDataService;
    private final AiCalibrationService calibrationService;
    private final String artifactDir;

    public AiOnnxRuntimeService(AiTrainingDataService trainingDataService,
                                AiCalibrationService calibrationService,
                                @Value("${nexus.ai.artifact-dir:./ai-artifacts}") String artifactDir) {
        this.trainingDataService = trainingDataService;
        this.calibrationService = calibrationService;
        this.artifactDir = artifactDir;
    }

    /** True when a valid, loadable ONNX artifact is attached to this version. */
    public boolean isArtifactAvailable(AiModelVersion version) {
        if (version == null || version.getModelFileUrl() == null) return false;
        try {
            return load(version) != null;
        } catch (Exception e) {
            log.warn("ONNX artifact unavailable for version {}: {}", version.getId(), e.getMessage());
            return false;
        }
    }

    private OrtSession load(AiModelVersion version) throws OrtException {
        UUID versionId = version.getId();
        OrtSession cached = sessions.get(versionId);
        if (cached != null) return cached;

        String path = artifactPath(version);
        if (path == null || !Files.exists(Path.of(path))) {
            log.warn("ONNX artifact file not found for version {} at {}", versionId, path);
            return null;
        }
        OrtEnvironment env = OrtEnvironment.getEnvironment();
        OrtSession session = env.createSession(path, new OrtSession.SessionOptions());
        sessions.put(versionId, session);
        log.info("Loaded ONNX artifact for version {} ({} bytes)", versionId, session.getInputInfo().size());
        return session;
    }

    /** Resolve the on-disk artifact path from modelFileUrl (URL or relative/local path). */
    private String artifactPath(AiModelVersion version) {
        String url = version.getModelFileUrl();
        if (url == null || url.isBlank()) return null;
        if (url.startsWith("file://")) return url.substring("file://".length());
        if (url.contains("://")) return null; // remote storage not yet supported
        Path candidate = Path.of(url);
        if (candidate.isAbsolute()) return url;
        return Path.of(artifactDir, url).toString();
    }

    /**
     * Run the median model for a SKU on targetDate. Returns an empty Optional when
     * the artifact is missing or inference fails, so the caller can fall back.
     */
    public Optional<Map<String, Object>> predictDemand(UUID tenantId, AiModel model, AiModelVersion version,
                                                       String sku, java.time.LocalDate targetDate) {
        try {
            OrtSession session = load(version);
            if (session == null) return Optional.empty();

            var bySku = trainingDataService.dailyDemandBySku(tenantId, 120);
            float[] features = trainingDataService.buildFeatures(bySku, sku, targetDate);

            String inputName = session.getInputNames().iterator().next();
            OnnxTensor tensor = OnnxTensor.createTensor(
                    OrtEnvironment.getEnvironment(), new float[][]{features});
            Map<String, OnnxTensor> feed = Map.of(inputName, tensor);
            try (OrtSession.Result result = session.run(feed)) {
                OnnxValue raw = result.get(0);
                if (raw == null) return Optional.empty();
                float predicted = extractScalar(raw);

                // Calibrate global prediction to tenant/SKU
                double scale = calibrationService.scaleFactor(tenantId, model.getId(), sku);
                double calibrated = Math.max(0.0, predicted * scale);

                Map<String, Object> out = new LinkedHashMap<>();
                out.put("predictedOrders", Math.round(calibrated * 100.0) / 100.0);
                out.put("rawPrediction", Math.round(predicted * 100.0) / 100.0);
                out.put("calibrationFactor", scale);
                out.put("calibrated", Math.abs(scale - 1.0) > 0.001);
                out.put("sku", sku);
                out.put("date", targetDate.toString());
                out.put("modelType", "DEMAND_FORECAST");
                out.put("engine", "ONNX_RUNTIME");
                out.put("unit", "units");
                return Optional.of(out);
            } finally {
                tensor.close();
            }
        } catch (Exception e) {
            log.warn("ONNX inference failed for model {} version {} sku {}: {}",
                    model.getId(), version.getId(), sku, e.getMessage());
            return Optional.empty();
        }
    }

    private float extractScalar(OnnxValue value) throws OrtException {
        if (value instanceof OnnxTensor tensor) {
            long[] shape = tensor.getInfo().getShape();
            // lightgbm2onnx often returns [1, 1]; unbox accordingly (float or double)
            Object v = tensor.getValue();
            if (shape.length >= 2 && shape[1] == 1) {
                if (v instanceof float[][] fa) return fa[0][0];
                if (v instanceof double[][] da) return (float) da[0][0];
            }
            if (shape.length == 1) {
                if (v instanceof float[] fa) return fa[0];
                if (v instanceof double[] da) return (float) da[0];
            }
            if (v instanceof float[][] fa2) return fa2[0][0];
            if (v instanceof double[][] da2) return (float) da2[0][0];
            if (v instanceof Number num) return num.floatValue();
        }
        throw new OrtException("Unexpected ONNX output type: " + value);
    }

    /** Release a session (e.g. after a version is replaced). */
    public void unload(UUID versionId) {
        OrtSession session = sessions.remove(versionId);
        if (session != null) {
            try {
                session.close();
            } catch (Exception e) {
                log.debug("Failed to close ONNX session {}: {}", versionId, e.getMessage());
            }
        }
    }
}
