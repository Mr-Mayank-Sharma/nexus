package com.nexus.oms.repository;

import com.nexus.oms.entity.DocumentVersion;
import org.springframework.data.jpa.repository.JpaRepository;
import java.util.List;
import java.util.UUID;

public interface DocumentVersionRepository extends JpaRepository<DocumentVersion, UUID> {

    List<DocumentVersion> findByDocumentId(UUID documentId);

    List<DocumentVersion> findByDocumentIdOrderByVersionNumberDesc(UUID documentId);
}
