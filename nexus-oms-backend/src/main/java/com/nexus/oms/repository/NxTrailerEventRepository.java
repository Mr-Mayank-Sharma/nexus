package com.nexus.oms.repository;

import com.nexus.oms.entity.NxTrailerEvent;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.UUID;

public interface NxTrailerEventRepository extends JpaRepository<NxTrailerEvent, UUID> {
    List<NxTrailerEvent> findByTrailerNumberOrderByEventTimeDesc(String trailerNumber);
    List<NxTrailerEvent> findByWarehouseIdAndEventTimeBetween(UUID warehouseId, java.time.LocalDateTime start, java.time.LocalDateTime end);
}
