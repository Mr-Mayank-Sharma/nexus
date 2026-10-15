package com.nexus.oms.repository;

import com.nexus.oms.entity.NxProcessedEvent;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;

public interface ProcessedEventRepository extends JpaRepository<NxProcessedEvent, String> {

    @Modifying
    @Transactional
    @Query("delete from NxProcessedEvent p where p.processedAt < :cutoff")
    int deleteOlderThan(LocalDateTime cutoff);
}
