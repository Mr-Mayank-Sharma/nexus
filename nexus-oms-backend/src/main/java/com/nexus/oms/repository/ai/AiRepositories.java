package com.nexus.oms.repository.ai;

import com.nexus.oms.entity.ai.*;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface AiModelRepository extends JpaRepository<AiModel, UUID> {
    Page<AiModel> findByTenantId(UUID tenantId, Pageable pageable);
    Page<AiModel> findByTenantIdAndCategory(UUID tenantId, String category, Pageable pageable);
    Page<AiModel> findByTenantIdAndModelType(UUID tenantId, String modelType, Pageable pageable);
    Page<AiModel> findByCategory(String category, Pageable pageable);
    Optional<AiModel> findByTenantIdAndName(UUID tenantId, String name);
    Page<AiModel> findByStatus(String status, Pageable pageable);
    long countByTenantIdAndStatus(UUID tenantId, String status);
    long countByTenantIdAndCategory(UUID tenantId, String category);
    long countByStatus(String status);
    @Query("SELECT m FROM AiModel m WHERE m.modelType = :type AND (m.tenantId = :tenantId OR m.category = 'GLOBAL') ORDER BY m.createdAt DESC")
    Page<AiModel> findAvailableForTenant(@Param("tenantId") UUID tenantId, @Param("type") String modelType, Pageable pageable);
}

public interface AiModelVersionRepository extends JpaRepository<AiModelVersion, UUID> {
    List<AiModelVersion> findByModelIdOrderByCreatedAtDesc(UUID modelId);
    Page<AiModelVersion> findByModelId(UUID modelId, Pageable pageable);
    Optional<AiModelVersion> findByModelIdAndVersion(UUID modelId, String version);
    Optional<AiModelVersion> findTopByModelIdAndStatusOrderByCreatedAtDesc(UUID modelId, String status);
    long countByModelId(UUID modelId);
    List<AiModelVersion> findByModelIdAndStatus(UUID modelId, String status);
}

public interface AiDeploymentRepository extends JpaRepository<AiDeployment, UUID> {
    List<AiDeployment> findByTenantIdAndModelId(UUID tenantId, UUID modelId);
    Optional<AiDeployment> findByTenantIdAndModelIdAndEnvironment(UUID tenantId, UUID modelId, String environment);
    List<AiDeployment> findByTenantIdAndStatus(UUID tenantId, String status);
    List<AiDeployment> findByTenantId(UUID tenantId);
    List<AiDeployment> findByVersionId(UUID versionId);
}

public interface AiFeatureDefinitionRepository extends JpaRepository<AiFeatureDefinition, UUID> {
    Page<AiFeatureDefinition> findByTenantId(UUID tenantId, Pageable pageable);
    List<AiFeatureDefinition> findByTenantIdAndFeatureGroup(UUID tenantId, String featureGroup);
    Optional<AiFeatureDefinition> findByTenantIdAndName(UUID tenantId, String name);
    List<AiFeatureDefinition> findByTenantIdAndEntityType(UUID tenantId, String entityType);
    long countByTenantIdAndFeatureGroup(UUID tenantId, String featureGroup);
}

public interface AiFeatureValueRepository extends JpaRepository<AiFeatureValue, UUID> {
    List<AiFeatureValue> findByTenantIdAndFeatureIdAndEntityIdAndAsOfDate(UUID tenantId, UUID featureId, String entityId, LocalDate asOfDate);
    List<AiFeatureValue> findByTenantIdAndFeatureIdAndAsOfDate(UUID tenantId, UUID featureId, LocalDate asOfDate);
    @Query("SELECT f FROM AiFeatureValue f WHERE f.tenantId = :tenantId AND f.entityId = :entityId AND f.asOfDate = :date")
    List<AiFeatureValue> findAllForEntity(@Param("tenantId") UUID tenantId, @Param("entityId") String entityId, @Param("date") LocalDate date);
    void deleteByAsOfDateBefore(LocalDate date);
}

public interface AiTrainingJobRepository extends JpaRepository<AiTrainingJob, UUID> {
    Page<AiTrainingJob> findByTenantId(UUID tenantId, Pageable pageable);
    Page<AiTrainingJob> findByModelId(UUID modelId, Pageable pageable);
    List<AiTrainingJob> findByModelIdAndStatus(UUID modelId, String status);
    Page<AiTrainingJob> findByTenantIdAndStatus(UUID tenantId, String status, Pageable pageable);
    long countByTenantIdAndStatus(UUID tenantId, String status);
    long countByModelIdAndStatus(UUID modelId, String status);
    @Query("SELECT COALESCE(AVG(j.accuracy), 0) FROM AiTrainingJob j WHERE j.modelId = :modelId AND j.status = 'COMPLETED'")
    java.math.BigDecimal avgAccuracyByModel(@Param("modelId") UUID modelId);
}

public interface AiInferenceLogRepository extends JpaRepository<AiInferenceLog, UUID> {
    Page<AiInferenceLog> findByTenantId(UUID tenantId, Pageable pageable);
    Page<AiInferenceLog> findByTenantIdAndModelId(UUID tenantId, UUID modelId, Pageable pageable);
    long countByTenantIdAndModelIdAndStatus(UUID tenantId, UUID modelId, String status);
    long countByTenantIdAndFallbackUsed(UUID tenantId, boolean fallbackUsed);
    long countByTenantIdAndRuleEngineUsed(UUID tenantId, boolean ruleEngineUsed);
    @Query("SELECT COALESCE(AVG(l.latencyMs), 0) FROM AiInferenceLog l WHERE l.modelId = :modelId AND l.createdAt >= :since")
    java.math.BigDecimal avgLatencyByModelSince(@Param("modelId") UUID modelId, @Param("since") LocalDateTime since);
    @Query("SELECT COALESCE(SUM(l.cost), 0) FROM AiInferenceLog l WHERE l.tenantId = :tenantId AND l.createdAt >= :since")
    java.math.BigDecimal totalCostByTenantSince(@Param("tenantId") UUID tenantId, @Param("since") LocalDateTime since);
    long countByTenantIdAndCreatedAtAfter(UUID tenantId, LocalDateTime after);
    long countByModelIdAndStatusAndCreatedAtAfter(UUID modelId, String status, LocalDateTime after);
    void deleteByCreatedAtBefore(LocalDateTime date);
}

public interface AiDatasetRepository extends JpaRepository<AiDataset, UUID> {
    Page<AiDataset> findByTenantId(UUID tenantId, Pageable pageable);
    Page<AiDataset> findByTenantIdAndDatasetType(UUID tenantId, String datasetType, Pageable pageable);
    List<AiDataset> findByTenantIdAndDatasetType(UUID tenantId, String datasetType);
}

public interface AiExperimentRepository extends JpaRepository<AiExperiment, UUID> {
    Page<AiExperiment> findByTenantId(UUID tenantId, Pageable pageable);
    List<AiExperiment> findByModelIdAndStatus(UUID modelId, String status);
    Page<AiExperiment> findByModelId(UUID modelId, Pageable pageable);
}

public interface AiPromptRepository extends JpaRepository<AiPrompt, UUID> {
    Page<AiPrompt> findByTenantId(UUID tenantId, Pageable pageable);
    Optional<AiPrompt> findByTenantIdAndNameAndVersion(UUID tenantId, String name, Integer version);
    List<AiPrompt> findByTenantIdAndNameOrderByVersionDesc(UUID tenantId, String name);
    Page<AiPrompt> findByTenantIdAndModelId(UUID tenantId, UUID modelId, Pageable pageable);
}

public interface AiKnowledgeBaseRepository extends JpaRepository<AiKnowledgeBase, UUID> {
    Page<AiKnowledgeBase> findByTenantId(UUID tenantId, Pageable pageable);
    Optional<AiKnowledgeBase> findByTenantIdAndName(UUID tenantId, String name);
    List<AiKnowledgeBase> findByStatus(String status);
}

public interface AiKnowledgeDocumentRepository extends JpaRepository<AiKnowledgeDocument, UUID> {
    Page<AiKnowledgeDocument> findByKnowledgeBaseId(UUID knowledgeBaseId, Pageable pageable);
    long countByKnowledgeBaseIdAndEmbeddingStatus(UUID knowledgeBaseId, String embeddingStatus);
    List<AiKnowledgeDocument> findByKnowledgeBaseIdAndEmbeddingStatus(UUID knowledgeBaseId, String embeddingStatus);
}

public interface AiRuleFallbackRepository extends JpaRepository<AiRuleFallback, UUID> {
    List<AiRuleFallback> findByModelIdAndIsActiveTrueOrderByPriorityAsc(UUID modelId);
    List<AiRuleFallback> findByTenantIdAndModelIdAndIsActiveTrue(UUID tenantId, UUID modelId);
    Page<AiRuleFallback> findByModelId(UUID modelId, Pageable pageable);
}

public interface AiModelMetricRepository extends JpaRepository<AiModelMetric, UUID> {
    List<AiModelMetric> findByModelIdAndMetricNameAndRecordedAtAfterOrderByRecordedAtAsc(UUID modelId, String metricName, LocalDateTime after);
    List<AiModelMetric> findByModelIdOrderByRecordedAtDesc(UUID modelId, Pageable pageable);
    @Query("SELECT m.metricName, AVG(m.metricValue) FROM AiModelMetric m WHERE m.modelId = :modelId AND m.recordedAt >= :since GROUP BY m.metricName")
    List<Object[]> avgMetricsSince(@Param("modelId") UUID modelId, @Param("since") LocalDateTime since);
}

public interface AiGatewayRouteRepository extends JpaRepository<AiGatewayRoute, UUID> {
    Optional<AiGatewayRoute> findByTenantIdAndModelType(UUID tenantId, String modelType);
    Optional<AiGatewayRoute> findByModelType(String modelType);
    List<AiGatewayRoute> findByTenantIdAndIsActiveTrue(UUID tenantId);
    List<AiGatewayRoute> findByIsActiveTrue();
}

public interface AiCostLogRepository extends JpaRepository<AiCostLog, UUID> {
    @Query("SELECT COALESCE(SUM(c.amount), 0) FROM AiCostLog c WHERE c.tenantId = :tenantId AND c.costType = :costType AND c.recordedAt >= :since")
    java.math.BigDecimal sumByTenantAndTypeSince(@Param("tenantId") UUID tenantId, @Param("costType") String costType, @Param("since") LocalDateTime since);
    @Query("SELECT c.costType, SUM(c.amount) FROM AiCostLog c WHERE c.tenantId = :tenantId AND c.recordedAt >= :since GROUP BY c.costType")
    List<Object[]> breakdownByTenantSince(@Param("tenantId") UUID tenantId, @Param("since") LocalDateTime since);
    Page<AiCostLog> findByTenantId(UUID tenantId, Pageable pageable);
}

public interface AiComputeResourceRepository extends JpaRepository<AiComputeResource, UUID> {
    Page<AiComputeResource> findByTenantId(UUID tenantId, Pageable pageable);
    List<AiComputeResource> findByTenantIdAndIsActiveTrue(UUID tenantId);
    List<AiComputeResource> findByIsActiveTrue();
}
