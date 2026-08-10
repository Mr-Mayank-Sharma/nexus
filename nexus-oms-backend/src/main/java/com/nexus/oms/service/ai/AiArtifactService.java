package com.nexus.oms.service.ai;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.nexus.oms.entity.ai.AiModelVersion;
import com.nexus.oms.repository.ai.AiModelVersionRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.MessageDigest;
import java.util.List;
import java.util.Map;
import java.util.NoSuchElementException;
import java.util.UUID;

/**
 * Stores ONNX artifacts produced by scripts/ml/forecast.py on the local
 * filesystem, records the checksum + feature contract on the version row, and
 * seeds per-SKU calibration baselines so the global model starts tenant-tuned.
 */
@Service
public class AiArtifactService {

    private static final Logger log = LoggerFactory.getLogger(AiArtifactService.class);

    private final AiModelVersionRepository versionRepository;
    private final AiCalibrationService calibrationService;
    private final ObjectMapper objectMapper;
    private final String artifactDir;

    public AiArtifactService(AiModelVersionRepository versionRepository,
                             AiCalibrationService calibrationService,
                             ObjectMapper objectMapper,
                             @Value("${nexus.ai.artifact-dir:./ai-artifacts}") String artifactDir) {
        this.versionRepository = versionRepository;
        this.calibrationService = calibrationService;
        this.objectMapper = objectMapper;
        this.artifactDir = artifactDir;
    }

    @Transactional
    public AiModelVersion storeArtifact(UUID modelId, UUID versionId, byte[] bytes, String fileName) throws IOException {
        AiModelVersion version = versionRepository.findById(versionId)
                .orElseThrow(() -> new NoSuchElementException("Version not found: " + versionId));

        Path dir = Path.of(artifactDir).toAbsolutePath().normalize();
        Files.createDirectories(dir);
        String safeName = (fileName == null || fileName.isBlank())
                ? "model.onnx" : Path.of(fileName).getFileName().toString();
        Path target = dir.resolve(modelId + "-" + versionId + "-" + safeName);
        Files.write(target, bytes);

        version.setModelFileUrl(target.toString());
        version.setArtifactFormat("ONNX");
        version.setArtifactChecksum(sha256(bytes));
        version.setFramework("lightgbm");
        version.setModelSizeBytes((long) bytes.length);
        version.setStatus("VALIDATED");
        if (version.getFeatureColumns() == null || version.getFeatureColumns().isBlank()) {
            version.setFeatureColumns(toJson(AiTrainingDataService.FEATURE_COLUMNS));
        }
        AiModelVersion saved = versionRepository.save(version);
        log.info("Stored artifact {} for version {} ({} bytes, sha256={})",
                target, versionId, bytes.length, version.getArtifactChecksum());
        return saved;
    }

    /** Attach feature columns + optional calibration baseline supplied by the trainer. */
    @Transactional
    public void attachMetadata(UUID tenantId, UUID modelId, UUID versionId, List<String> featureColumns,
                               Map<String, Double> skuRatios, String calibrationType) {
        AiModelVersion version = versionRepository.findById(versionId)
                .orElseThrow(() -> new NoSuchElementException("Version not found: " + versionId));
        if (featureColumns != null && !featureColumns.isEmpty()) {
            version.setFeatureColumns(toJson(featureColumns));
        }
        if (calibrationType != null) {
            version.setCalibrationType(calibrationType);
        }
        versionRepository.save(version);

        if (tenantId != null && skuRatios != null && !skuRatios.isEmpty()) {
            calibrationService.seedBaseline(tenantId, modelId, versionId, skuRatios);
        }
    }

    private String sha256(byte[] bytes) {
        try {
            MessageDigest md = MessageDigest.getInstance("SHA-256");
            byte[] hash = md.digest(bytes);
            StringBuilder sb = new StringBuilder();
            for (byte b : hash) sb.append(String.format("%02x", b));
            return sb.toString();
        } catch (Exception e) {
            throw new IllegalStateException("SHA-256 not available", e);
        }
    }

    private String toJson(Object obj) {
        try {
            return objectMapper.writeValueAsString(obj);
        } catch (Exception e) {
            return "[]";
        }
    }

    public List<String> parseFeatureColumns(String json) {
        if (json == null || json.isBlank()) return AiTrainingDataService.FEATURE_COLUMNS;
        try {
            return objectMapper.readValue(json, new TypeReference<List<String>>() {});
        } catch (Exception e) {
            log.warn("Failed to parse feature columns, using defaults: {}", e.getMessage());
            return AiTrainingDataService.FEATURE_COLUMNS;
        }
    }

    public Map<String, Double> parseSkuRatios(String json) {
        if (json == null || json.isBlank()) return Map.of();
        try {
            return objectMapper.readValue(json, new TypeReference<Map<String, Double>>() {});
        } catch (Exception e) {
            log.warn("Failed to parse calibration baseline: {}", e.getMessage());
            return Map.of();
        }
    }
}
