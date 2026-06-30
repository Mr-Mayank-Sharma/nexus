package com.nexus.oms.controller;

import com.nexus.oms.dto.ApiResponse;
import com.nexus.oms.entity.Document;
import com.nexus.oms.entity.DocumentVersion;
import com.nexus.oms.security.TenantContext;
import com.nexus.oms.service.DocumentService;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import jakarta.validation.Valid;
import java.util.List;
import java.util.Map;
import java.util.UUID;

@RestController
@RequestMapping("/documents")
public class DocumentController {

    private final DocumentService documentService;

    public DocumentController(DocumentService documentService) {
        this.documentService = documentService;
    }

    @GetMapping
    public ResponseEntity<ApiResponse<Page<Document>>> getAllDocuments(
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size) {
        return ResponseEntity.ok(ApiResponse.success(
                documentService.getAllDocuments(PageRequest.of(page, size))));
    }

    @GetMapping("/{id}")
    public ResponseEntity<ApiResponse<Document>> getDocument(@PathVariable UUID id) {
        return ResponseEntity.ok(ApiResponse.success(documentService.getDocument(id)));
    }

    @PostMapping
    public ResponseEntity<ApiResponse<Document>> createDocument(@Valid @RequestBody Document document) {
        return ResponseEntity.ok(ApiResponse.success(
                documentService.createDocument(document), "Document created"));
    }

    @PutMapping("/{id}")
    public ResponseEntity<ApiResponse<Document>> updateDocument(@PathVariable UUID id,
                                                                 @Valid @RequestBody Document document) {
        return ResponseEntity.ok(ApiResponse.success(
                documentService.updateDocument(id, document), "Document updated"));
    }

    @DeleteMapping("/{id}")
    public ResponseEntity<ApiResponse<Void>> deleteDocument(@PathVariable UUID id) {
        documentService.deleteDocument(id);
        return ResponseEntity.ok(ApiResponse.success(null, "Document deleted"));
    }

    @PostMapping("/{id}/versions")
    public ResponseEntity<ApiResponse<Document>> uploadNewVersion(@PathVariable UUID id,
                                                                  @RequestBody Map<String, Object> request) {
        String fileName = (String) request.get("fileName");
        Long fileSize = request.get("fileSize") != null ? ((Number) request.get("fileSize")).longValue() : null;
        String fileUrl = (String) request.get("fileUrl");
        String changeNotes = (String) request.get("changeNotes");
        return ResponseEntity.ok(ApiResponse.success(
                documentService.uploadNewVersion(id, fileName, fileSize, fileUrl, changeNotes),
                "New version uploaded"));
    }

    @GetMapping("/{id}/versions")
    public ResponseEntity<ApiResponse<List<DocumentVersion>>> getDocumentVersions(@PathVariable UUID id) {
        return ResponseEntity.ok(ApiResponse.success(documentService.getDocumentVersions(id)));
    }

    @GetMapping("/by-entity")
    public ResponseEntity<ApiResponse<List<Document>>> getDocumentsByEntity(
            @RequestParam String entityType,
            @RequestParam String entityId) {
        return ResponseEntity.ok(ApiResponse.success(
                documentService.getDocumentsByEntity(entityType, entityId)));
    }
}
