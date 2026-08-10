package com.nexus.oms.repository.ai;

import com.nexus.oms.entity.ai.*;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;
import java.util.UUID;

public interface AiCalibrationRepository extends JpaRepository<AiCalibration, UUID> {
    Optional<AiCalibration> findByTenantIdAndModelIdAndEntityId(UUID tenantId, UUID modelId, String entityId);
    java.util.List<AiCalibration> findByTenantIdAndModelId(UUID tenantId, UUID modelId);
    void deleteByModelId(UUID modelId);
}
