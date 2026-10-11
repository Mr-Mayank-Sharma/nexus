package com.nexus.oms.repository;

import com.nexus.oms.entity.NxInventoryPublishRun;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;

import java.time.LocalDateTime;
import java.util.Collection;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface NxInventoryPublishRunRepository extends JpaRepository<NxInventoryPublishRun, UUID> {
    List<NxInventoryPublishRun> findByIntegrationStoreIdOrderByStartedAtDesc(UUID integrationStoreId, Pageable pageable);

    Optional<NxInventoryPublishRun> findFirstByIntegrationStoreIdOrderByStartedAtDesc(UUID integrationStoreId);

    /** The last run that really changed the channel (never a dry run); its lines say what Nexus last set. */
    Optional<NxInventoryPublishRun> findFirstByIntegrationStoreIdAndDryRunFalseAndStatusInOrderByStartedAtDesc(
            UUID integrationStoreId, Collection<String> statuses);

    List<NxInventoryPublishRun> findByIntegrationStoreIdAndStatusAndStartedAtBefore(
            UUID integrationStoreId, String status, LocalDateTime before);

    void deleteByIntegrationStoreIdAndStartedAtBefore(UUID integrationStoreId, LocalDateTime before);
}
