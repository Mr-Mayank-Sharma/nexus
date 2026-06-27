package com.nexus.oms.controller;

import com.nexus.oms.dto.ApiResponse;
import com.nexus.oms.dto.ImportResult;
import com.nexus.oms.service.GenericImportService;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/import")
public class GenericImportController {

    private final GenericImportService genericImportService;

    public GenericImportController(GenericImportService genericImportService) {
        this.genericImportService = genericImportService;
    }

    @PostMapping(value = "/{entityType}", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    public ResponseEntity<ApiResponse<ImportResult>> importFile(
            @PathVariable("entityType") String entityType,
            @RequestParam("file") MultipartFile file,
            @RequestParam(value = "format", required = false) String format) {
        ImportResult result = genericImportService.importFile(entityType, file, format);
        return ResponseEntity.ok(ApiResponse.success(result, "Import completed: " + result.getSuccessCount() + " of " + result.getTotalRecords() + " records imported"));
    }

    @GetMapping("/entity-types")
    public ResponseEntity<ApiResponse<List<String>>> getEntityTypes() {
        return ResponseEntity.ok(ApiResponse.success(List.of(
            "products", "orders", "inventory", "customers", "shipments",
            "returns", "suppliers", "purchase-orders", "invoices", "warehouses"
        )));
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
}
