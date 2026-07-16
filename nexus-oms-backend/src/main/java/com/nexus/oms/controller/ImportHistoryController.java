package com.nexus.oms.controller;

import com.nexus.oms.dto.ApiResponse;
import com.nexus.oms.dto.ImportResult;
import com.nexus.oms.entity.ImportHistory;
import com.nexus.oms.entity.ImportRecordLog;
import com.nexus.oms.repository.ImportHistoryRepository;
import com.nexus.oms.repository.ImportRecordLogRepository;
import com.nexus.oms.security.TenantContext;
import com.nexus.oms.service.GenericImportService;
import com.nexus.oms.service.ImportTokenService;
import org.springframework.core.io.FileSystemResource;
import org.springframework.core.io.Resource;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import java.io.*;
import java.nio.file.Files;
import java.util.*;



@RestController
@RequestMapping("/import")
public class ImportHistoryController {

    private final GenericImportService genericImportService;
    private final ImportHistoryRepository importHistoryRepository;
    private final ImportRecordLogRepository importRecordLogRepository;
    private final ImportTokenService importTokenService;

    public ImportHistoryController(GenericImportService genericImportService,
                                   ImportHistoryRepository importHistoryRepository,
                                   ImportRecordLogRepository importRecordLogRepository,
                                   ImportTokenService importTokenService) {
        this.genericImportService = genericImportService;
        this.importHistoryRepository = importHistoryRepository;
        this.importRecordLogRepository = importRecordLogRepository;
        this.importTokenService = importTokenService;
    }

    // ---- Signed Upload Token ----

    @PostMapping("/token")
    public ResponseEntity<ApiResponse<Map<String, Object>>> generateToken(
            @RequestParam("entityType") String entityType) {
        String token = importTokenService.generateToken(entityType);
        return ResponseEntity.ok(ApiResponse.success(Map.of(
            "token", token,
            "entityType", entityType,
            "expiresInMs", importTokenService.getTtlMs()
        )));
    }

    // ---- Import Execution ----

    @PostMapping(value = "/{entityType}", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    public ResponseEntity<ApiResponse<ImportResult>> importFile(
            @PathVariable("entityType") String entityType,
            @RequestParam("file") MultipartFile file,
            @RequestParam(value = "format", required = false) String format,
            @RequestParam(value = "mode", defaultValue = "CONTINUE_ON_ERROR") String mode) {
        ImportResult result = genericImportService.importFile(entityType, file, format, mode);
        return ResponseEntity.ok(ApiResponse.success(result,
            "Import completed: " + result.getSuccessCount() + " of " + result.getTotalRecords() + " records imported"));
    }

    // ---- Import History ----

    @GetMapping("/history")
    public ResponseEntity<ApiResponse<Page<ImportHistory>>> getImportHistory(
            @RequestParam(value = "status", required = false) String status,
            Pageable pageable) {
        UUID tenantId = TenantContext.getCurrentTenantId();
        Page<ImportHistory> history;
        if (status != null && !status.isBlank()) {
            history = importHistoryRepository.findByTenantIdAndStatusOrderByCreatedAtDesc(tenantId, status.toUpperCase(), pageable);
        } else {
            history = importHistoryRepository.findByTenantIdOrderByCreatedAtDesc(tenantId, pageable);
        }
        return ResponseEntity.ok(ApiResponse.success(history));
    }

    @GetMapping("/history/{id}")
    public ResponseEntity<ApiResponse<ImportHistory>> getImportDetail(@PathVariable UUID id) {
        Optional<ImportHistory> opt = importHistoryRepository.findById(id);
        if (opt.isEmpty()) {
            return ResponseEntity.notFound().build();
        }
        return ResponseEntity.ok(ApiResponse.success(opt.get()));
    }

    // ---- Processing Logs ----

    @GetMapping("/history/{id}/logs")
    public ResponseEntity<ApiResponse<List<ImportRecordLog>>> getProcessingLogs(@PathVariable UUID id) {
        if (!importHistoryRepository.existsById(id)) {
            return ResponseEntity.notFound().build();
        }
        List<ImportRecordLog> logs = importRecordLogRepository.findByImportHistoryIdOrderByRowNumberAsc(id);
        return ResponseEntity.ok(ApiResponse.success(logs));
    }

    // ---- File Downloads ----

    @GetMapping("/history/{id}/download/original")
    public ResponseEntity<Resource> downloadOriginalFile(@PathVariable UUID id) {
        Optional<ImportHistory> opt = importHistoryRepository.findById(id);
        if (opt.isEmpty() || opt.get().getStoredFilePath() == null) {
            return ResponseEntity.notFound().build();
        }
        ImportHistory history = opt.get();
        Resource resource = new FileSystemResource(history.getStoredFilePath());
        if (!resource.exists()) {
            return ResponseEntity.notFound().build();
        }
        return ResponseEntity.ok()
            .contentType(MediaType.APPLICATION_OCTET_STREAM)
            .header(HttpHeaders.CONTENT_DISPOSITION,
                "attachment; filename=\"" + history.getOriginalFileName() + "\"")
            .body(resource);
    }

    @GetMapping("/history/{id}/download/errors")
    public ResponseEntity<Resource> downloadErrorFile(@PathVariable UUID id) {
        Optional<ImportHistory> opt = importHistoryRepository.findById(id);
        if (opt.isEmpty() || opt.get().getErrorFilePath() == null) {
            return ResponseEntity.notFound().build();
        }
        ImportHistory history = opt.get();
        Resource resource = new FileSystemResource(history.getErrorFilePath());
        if (!resource.exists()) {
            return ResponseEntity.notFound().build();
        }
        String errorFileName = "errors_" + history.getOriginalFileName().replaceAll("\\.[^.]+$", ".csv");
        return ResponseEntity.ok()
            .contentType(MediaType.parseMediaType("text/csv"))
            .header(HttpHeaders.CONTENT_DISPOSITION,
                "attachment; filename=\"" + errorFileName + "\"")
            .body(resource);
    }

    // ---- Reprocess ----

    @PostMapping("/history/{id}/reprocess")
    public ResponseEntity<ApiResponse<ImportResult>> reprocessImport(@PathVariable UUID id) {
        Optional<ImportHistory> opt = importHistoryRepository.findById(id);
        if (opt.isEmpty() || opt.get().getStoredFilePath() == null) {
            return ResponseEntity.notFound().build();
        }
        ImportHistory history = opt.get();

        // Read stored file and re-import
        try {
            java.nio.file.Path path = java.nio.file.Paths.get(history.getStoredFilePath());
            byte[] content = Files.readAllBytes(path);
            MultipartFile multipartFile = new ByteArrayMultipartFile(
                history.getOriginalFileName(),
                content,
                getMediaType(history.getFileFormat())
            );

            ImportResult result = genericImportService.importFile(
                history.getImportType(), multipartFile, history.getFileFormat(), history.getImportMode());
            return ResponseEntity.ok(ApiResponse.success(result,
                "Re-import completed: " + result.getSuccessCount() + " of " + result.getTotalRecords() + " records imported"));
        } catch (Exception e) {
            return ResponseEntity.internalServerError()
                .body(ApiResponse.error("Re-import failed: " + e.getMessage()));
        }
    }

    // ---- Metadata ----

    @GetMapping("/entity-types")
    public ResponseEntity<ApiResponse<List<String>>> getEntityTypes() {
        return ResponseEntity.ok(ApiResponse.success(List.copyOf(GenericImportService.VALID_ENTITY_TYPES)));
    }

    @GetMapping("/formats")
    public ResponseEntity<ApiResponse<List<Map<String, String>>>> getSupportedFormats() {
        return ResponseEntity.ok(ApiResponse.success(List.of(
            Map.of("id", "csv", "label", "CSV (Comma-Separated Values)", "extensions", ".csv"),
            Map.of("id", "json", "label", "JSON (JavaScript Object Notation)", "extensions", ".json"),
            Map.of("id", "xml", "label", "XML (Extensible Markup Language)", "extensions", ".xml"),
            Map.of("id", "edi", "label", "EDI X12 (Electronic Data Interchange)", "extensions", ".edi,.850,.856,.810"),
            Map.of("id", "xlsx", "label", "Excel (Microsoft Excel Spreadsheet)", "extensions", ".xlsx,.xls")
        )));
    }

    @GetMapping("/modes")
    public ResponseEntity<ApiResponse<List<String>>> getImportModes() {
        return ResponseEntity.ok(ApiResponse.success(List.copyOf(GenericImportService.VALID_IMPORT_MODES)));
    }

    private String getMediaType(String format) {
        return switch (format.toLowerCase()) {
            case "csv" -> "text/csv";
            case "json" -> "application/json";
            case "xml" -> "application/xml";
            case "edi" -> "text/plain";
            case "xlsx" -> "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet";
            default -> "application/octet-stream";
        };
    }

    // ---- Inner class for re-processing ----

    static class ByteArrayMultipartFile implements MultipartFile {
        private final String name;
        private final byte[] content;
        private final String contentType;

        ByteArrayMultipartFile(String name, byte[] content, String contentType) {
            this.name = name;
            this.content = content;
            this.contentType = contentType;
        }

        @Override public String getName() { return name; }
        @Override public String getOriginalFilename() { return name; }
        @Override public String getContentType() { return contentType; }
        @Override public boolean isEmpty() { return content.length == 0; }
        @Override public long getSize() { return content.length; }
        @Override public byte[] getBytes() { return content; }
        @Override public InputStream getInputStream() { return new ByteArrayInputStream(content); }
        @Override public void transferTo(File dest) throws IOException { Files.write(dest.toPath(), content); }
    }
}
