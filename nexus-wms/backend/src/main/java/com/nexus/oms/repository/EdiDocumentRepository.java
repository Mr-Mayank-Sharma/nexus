package com.nexus.oms.repository;

import com.nexus.oms.entity.NxEdiDocument;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import java.util.List;
import java.util.UUID;

public interface EdiDocumentRepository extends JpaRepository<NxEdiDocument, UUID> {
    Page<NxEdiDocument> findByTenantId(UUID tenantId, Pageable pageable);
    Page<NxEdiDocument> findByTenantIdAndDocType(UUID tenantId, String docType, Pageable pageable);
    Page<NxEdiDocument> findByTenantIdAndParsedStatus(UUID tenantId, String parsedStatus, Pageable pageable);
    List<NxEdiDocument> findByOrderId(UUID orderId);
    long countByTenantIdAndParsedStatus(UUID tenantId, String parsedStatus);
}
