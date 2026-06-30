package com.nexus.oms.repository;

import com.nexus.oms.entity.ImportHistory;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.UUID;

@Repository
public interface ImportHistoryRepository extends JpaRepository<ImportHistory, UUID> {

    Page<ImportHistory> findByTenantIdOrderByCreatedAtDesc(UUID tenantId, Pageable pageable);

    List<ImportHistory> findByTenantIdAndImportTypeOrderByCreatedAtDesc(UUID tenantId, String importType);

    Page<ImportHistory> findByTenantIdAndStatusOrderByCreatedAtDesc(UUID tenantId, String status, Pageable pageable);
}
