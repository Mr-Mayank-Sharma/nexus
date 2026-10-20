package com.nexus.oms.repository;

import com.nexus.oms.entity.NxTrailer;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface NxTrailerRepository extends JpaRepository<NxTrailer, UUID> {
    List<NxTrailer> findByWarehouseIdAndStatus(UUID warehouseId, String status);
    List<NxTrailer> findByWarehouseId(UUID warehouseId);
    Optional<NxTrailer> findByWarehouseIdAndTrailerNumber(UUID warehouseId, String trailerNumber);
    long countByWarehouseIdAndStatus(UUID warehouseId, String status);
}
