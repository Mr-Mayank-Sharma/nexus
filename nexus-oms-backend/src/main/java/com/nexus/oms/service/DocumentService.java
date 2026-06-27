package com.nexus.oms.service;

import com.nexus.oms.entity.Document;
import com.nexus.oms.entity.DocumentVersion;
import com.nexus.oms.exception.BadRequestException;
import com.nexus.oms.exception.ResourceNotFoundException;
import com.nexus.oms.repository.DocumentRepository;
import com.nexus.oms.repository.DocumentVersionRepository;
import com.nexus.oms.security.TenantContext;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.UUID;

@Service
public class DocumentService {

    private final DocumentRepository documentRepository;
    private final DocumentVersionRepository documentVersionRepository;

    public DocumentService(DocumentRepository documentRepository,
                           DocumentVersionRepository documentVersionRepository) {
        this.documentRepository = documentRepository;
        this.documentVersionRepository = documentVersionRepository;
    }

    public Page<Document> getAllDocuments(Pageable pageable) {
        UUID tenantId = TenantContext.getCurrentTenantId();
        return documentRepository.findByTenantId(tenantId, pageable);
    }

    public Document getDocument(UUID id) {
        UUID tenantId = TenantContext.getCurrentTenantId();
        return documentRepository.findById(id)
                .filter(d -> d.getTenantId().equals(tenantId))
                .orElseThrow(() -> new ResourceNotFoundException("Document", id));
    }

    @Transactional
    public Document createDocument(Document doc) {
        UUID tenantId = TenantContext.getCurrentTenantId();
        doc.setTenantId(tenantId);
        return documentRepository.save(doc);
    }

    @Transactional
    public Document updateDocument(UUID id, Document updates) {
        UUID tenantId = TenantContext.getCurrentTenantId();
        Document doc = documentRepository.findById(id)
                .filter(d -> d.getTenantId().equals(tenantId))
                .orElseThrow(() -> new ResourceNotFoundException("Document", id));
        if (updates.getTitle() != null) doc.setTitle(updates.getTitle());
        if (updates.getDescription() != null) doc.setDescription(updates.getDescription());
        if (updates.getDocumentType() != null) doc.setDocumentType(updates.getDocumentType());
        if (updates.getCategory() != null) doc.setCategory(updates.getCategory());
        if (updates.getFileName() != null) doc.setFileName(updates.getFileName());
        if (updates.getFileSize() != null) doc.setFileSize(updates.getFileSize());
        if (updates.getMimeType() != null) doc.setMimeType(updates.getMimeType());
        if (updates.getFileUrl() != null) doc.setFileUrl(updates.getFileUrl());
        if (updates.getStoragePath() != null) doc.setStoragePath(updates.getStoragePath());
        if (updates.getFileHash() != null) doc.setFileHash(updates.getFileHash());
        if (updates.getTags() != null) doc.setTags(updates.getTags());
        if (updates.getMetadata() != null) doc.setMetadata(updates.getMetadata());
        if (updates.getIsPublic() != null) doc.setIsPublic(updates.getIsPublic());
        return documentRepository.save(doc);
    }

    @Transactional
    public void deleteDocument(UUID id) {
        UUID tenantId = TenantContext.getCurrentTenantId();
        Document doc = documentRepository.findById(id)
                .filter(d -> d.getTenantId().equals(tenantId))
                .orElseThrow(() -> new ResourceNotFoundException("Document", id));
        documentRepository.delete(doc);
    }

    @Transactional
    public Document uploadNewVersion(UUID documentId, String fileName, Long fileSize, String fileUrl,
                                     String changeNotes) {
        UUID tenantId = TenantContext.getCurrentTenantId();
        Document doc = documentRepository.findById(documentId)
                .filter(d -> d.getTenantId().equals(tenantId))
                .orElseThrow(() -> new ResourceNotFoundException("Document", documentId));

        int nextVersion = doc.getCurrentVersion() != null ? doc.getCurrentVersion() + 1 : 1;

        DocumentVersion version = DocumentVersion.builder()
                .documentId(documentId)
                .versionNumber(nextVersion)
                .fileName(fileName)
                .fileSize(fileSize)
                .fileUrl(fileUrl)
                .changeNotes(changeNotes)
                .build();
        documentVersionRepository.save(version);

        doc.setFileName(fileName);
        doc.setFileSize(fileSize);
        doc.setFileUrl(fileUrl);
        doc.setCurrentVersion(nextVersion);
        return documentRepository.save(doc);
    }

    public List<DocumentVersion> getDocumentVersions(UUID documentId) {
        return documentVersionRepository.findByDocumentIdOrderByVersionNumberDesc(documentId);
    }

    public List<Document> getDocumentsByEntity(String entityType, String entityId) {
        return documentRepository.findByEntityTypeAndEntityId(entityType, UUID.fromString(entityId));
    }
}
