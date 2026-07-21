package com.nexus.oms.repository;

import com.nexus.oms.entity.NxReplenishmentSuggestion;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.UUID;

public interface NxReplenishmentSuggestionRepository extends JpaRepository<NxReplenishmentSuggestion, UUID> {
    List<NxReplenishmentSuggestion> findByWarehouseIdAndStatus(UUID warehouseId, String status);
    List<NxReplenishmentSuggestion> findByWarehouseId(UUID warehouseId);
    long countByWarehouseIdAndStatus(UUID warehouseId, String status);
}
