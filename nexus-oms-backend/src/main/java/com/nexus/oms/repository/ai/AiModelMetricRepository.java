package com.nexus.oms.repository.ai;

import com.nexus.oms.entity.ai.*;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface AiModelMetricRepository extends JpaRepository<AiModelMetric, UUID> {
    List<AiModelMetric> findByModelIdAndMetricNameAndRecordedAtAfterOrderByRecordedAtAsc(UUID modelId, String metricName, LocalDateTime after);
    List<AiModelMetric> findByModelIdOrderByRecordedAtDesc(UUID modelId, Pageable pageable);
    @Query("SELECT m.metricName, AVG(m.metricValue) FROM AiModelMetric m WHERE m.modelId = :modelId AND m.recordedAt >= :since GROUP BY m.metricName")
    List<Object[]> avgMetricsSince(@Param("modelId") UUID modelId, @Param("since") LocalDateTime since);
}