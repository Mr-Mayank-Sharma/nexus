package com.nexus.oms.controller;

import com.nexus.oms.dto.ApiResponse;
import com.nexus.oms.entity.NxEdiDocument;
import com.nexus.oms.entity.NxEdiPartner;
import com.nexus.oms.service.EdiAutomationService;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import jakarta.validation.Valid;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Map;
import java.util.UUID;

@RestController
@RequestMapping("/edi")
public class EdiAutomationController {

    private final EdiAutomationService ediAutomationService;

    public EdiAutomationController(EdiAutomationService ediAutomationService) {
        this.ediAutomationService = ediAutomationService;
    }

    @GetMapping
    public ResponseEntity<ApiResponse<Page<NxEdiDocument>>> getDocuments(
            @RequestParam(required = false) String docType,
            @RequestParam(required = false) String status,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size) {
        Pageable pageable = PageRequest.of(page, size);
        return ResponseEntity.ok(ApiResponse.success(
                ediAutomationService.getDocuments(docType, status, pageable)));
    }

    @GetMapping("/{id}")
    public ResponseEntity<ApiResponse<NxEdiDocument>> getDocument(@PathVariable UUID id) {
        return ResponseEntity.ok(ApiResponse.success(ediAutomationService.getDocument(id)));
    }

    @PostMapping("/upload")
    public ResponseEntity<ApiResponse<NxEdiDocument>> uploadEdi(
            @RequestParam("file") MultipartFile file,
            @RequestParam("docType") String docType) throws IOException {
        String content = new String(file.getBytes(), StandardCharsets.UTF_8);
        NxEdiDocument doc = ediAutomationService.uploadAndParse(
                file.getOriginalFilename(), content, docType);
        return ResponseEntity.ok(ApiResponse.success(doc, "EDI document processed"));
    }

    @PostMapping("/parse")
    public ResponseEntity<ApiResponse<NxEdiDocument>> parseEdi(
            @RequestParam("content") String content,
            @RequestParam("docType") String docType) {
        NxEdiDocument doc = ediAutomationService.uploadAndParse(null, content, docType);
        return ResponseEntity.ok(ApiResponse.success(doc, "EDI content parsed"));
    }

    @PostMapping("/{id}/reprocess")
    public ResponseEntity<ApiResponse<NxEdiDocument>> reprocess(@PathVariable UUID id) {
        NxEdiDocument doc = ediAutomationService.reprocess(id);
        return ResponseEntity.ok(ApiResponse.success(doc, "EDI document reprocessed"));
    }

    @GetMapping("/kpis")
    public ResponseEntity<ApiResponse<Map<String, Object>>> getKPIs() {
        return ResponseEntity.ok(ApiResponse.success(ediAutomationService.getKPIs()));
    }

    @GetMapping("/partners")
    public ResponseEntity<ApiResponse<List<NxEdiPartner>>> getPartners() {
        return ResponseEntity.ok(ApiResponse.success(ediAutomationService.getPartners()));
    }

    @PostMapping("/partners")
    public ResponseEntity<ApiResponse<NxEdiPartner>> createPartner(
            @Valid @RequestBody NxEdiPartner partner) {
        return ResponseEntity.ok(ApiResponse.success(
                ediAutomationService.createPartner(partner), "EDI partner created"));
    }
}
