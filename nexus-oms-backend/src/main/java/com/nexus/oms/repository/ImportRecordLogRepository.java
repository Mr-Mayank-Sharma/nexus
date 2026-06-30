package com.nexus.oms.repository;

import com.nexus.oms.entity.ImportRecordLog;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.UUID;

@Repository
public interface ImportRecordLogRepository extends JpaRepository<ImportRecordLog, UUID> {

    List<ImportRecordLog> findByImportHistoryIdOrderByRowNumberAsc(UUID importHistoryId);

    Page<ImportRecordLog> findByImportHistoryIdOrderByRowNumberAsc(UUID importHistoryId, Pageable pageable);

    List<ImportRecordLog> findByImportHistoryIdAndStatusOrderByRowNumberAsc(UUID importHistoryId, String status);

    long countByImportHistoryIdAndStatus(UUID importHistoryId, String status);

    void deleteByImportHistoryId(UUID importHistoryId);
}
