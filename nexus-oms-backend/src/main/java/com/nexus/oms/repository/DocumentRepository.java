package com.nexus.oms.repository;

import com.nexus.oms.entity.Document;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface DocumentRepository extends JpaRepository<Document, UUID> {

    Page<Document> findByTenantId(UUID tenantId, Pageable pageable);

    List<Document> findByTenantIdAndDocumentType(UUID tenantId, String documentType);

    List<Document> findByEntityTypeAndEntityId(String entityType, UUID entityId);

    Optional<Document> findByTenantIdAndDocumentNumber(UUID tenantId, String documentNumber);

    List<Document> findByTenantIdAndCategory(UUID tenantId, String category);
}
