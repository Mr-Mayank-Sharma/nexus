package com.nexus.oms.controller;

import com.nexus.oms.dto.ApiResponse;
import com.nexus.oms.service.SampleDataGenerator;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Set;

@RestController
@RequestMapping("/api/sample-data")
public class SampleDataController {

    private static final Set<String> VALID_ENTITY_TYPES = Set.of(
        "products", "orders", "inventory", "customers", "shipments", "returns",
        "suppliers", "purchase-orders", "invoices", "warehouses"
    );

    private final SampleDataGenerator sampleDataGenerator;

    public SampleDataController(SampleDataGenerator sampleDataGenerator) {
        this.sampleDataGenerator = sampleDataGenerator;
    }

    @GetMapping("/entity-types")
    public ResponseEntity<ApiResponse<List<String>>> getEntityTypes() {
        return ResponseEntity.ok(ApiResponse.success(
            VALID_ENTITY_TYPES.stream().sorted().toList()));
    }

    @GetMapping("/generate/{entityType}")
    public ResponseEntity<byte[]> generateSample(
            @PathVariable String entityType,
            @RequestParam(defaultValue = "10") int count,
            @RequestParam(defaultValue = "csv") String format) {

        if (!VALID_ENTITY_TYPES.contains(entityType)) {
            return ResponseEntity.badRequest().body(
                ("Unsupported entity type: " + entityType).getBytes());
        }

        byte[] data = sampleDataGenerator.generateSample(entityType, count, format);
        String fileName = sampleDataGenerator.guessFileName(entityType, format);

        MediaType mediaType = switch (format) {
            case "json" -> MediaType.APPLICATION_JSON;
            case "xml" -> MediaType.APPLICATION_XML;
            default -> MediaType.parseMediaType("text/csv");
        };

        return ResponseEntity.ok()
            .contentType(mediaType)
            .header(HttpHeaders.CONTENT_DISPOSITION,
                "attachment; filename=\"" + fileName + "\"")
            .body(data);
    }
}
