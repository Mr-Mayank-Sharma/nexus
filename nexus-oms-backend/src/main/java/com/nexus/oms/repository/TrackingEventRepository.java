package com.nexus.oms.repository;

import com.nexus.oms.entity.NxTrackingEvent;
import org.springframework.data.jpa.repository.JpaRepository;
import java.util.List;
import java.util.UUID;

public interface TrackingEventRepository extends JpaRepository<NxTrackingEvent, UUID> {

    List<NxTrackingEvent> findByShipmentIdOrderByTimestampDesc(UUID shipmentId);
}
