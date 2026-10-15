package com.nexus.oms.pos;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface PosSnapshotRepository extends JpaRepository<NxPosInventorySnapshot, UUID> {
    Optional<NxPosInventorySnapshot> findBySourceIdAndSku(UUID sourceId, String sku);
    List<NxPosInventorySnapshot> findByNodeId(UUID nodeId);
}
