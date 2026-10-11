package com.nexus.oms.repository;

import com.nexus.oms.entity.NxInventoryPublishLine;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.UUID;

public interface NxInventoryPublishLineRepository extends JpaRepository<NxInventoryPublishLine, UUID> {
    List<NxInventoryPublishLine> findByRunIdOrderBySkuAscExternalLocationIdAsc(UUID runId, Pageable pageable);
}
