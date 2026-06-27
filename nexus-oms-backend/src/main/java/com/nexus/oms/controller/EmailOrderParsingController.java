package com.nexus.oms.controller;

import com.nexus.oms.dto.ApiResponse;
import com.nexus.oms.entity.NxEmailParsedOrder;
import com.nexus.oms.service.EmailOrderParsingService;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.Map;
import java.util.UUID;

@RestController
@RequestMapping("/email-parser")
public class EmailOrderParsingController {

    private final EmailOrderParsingService emailOrderParsingService;

    public EmailOrderParsingController(EmailOrderParsingService emailOrderParsingService) {
        this.emailOrderParsingService = emailOrderParsingService;
    }

    @GetMapping
    public ResponseEntity<ApiResponse<Page<NxEmailParsedOrder>>> getParsedOrders(
            @RequestParam(required = false) String status,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size) {
        Pageable pageable = PageRequest.of(page, size);
        return ResponseEntity.ok(ApiResponse.success(
                emailOrderParsingService.getParsedOrders(status, pageable)));
    }

    @GetMapping("/{id}")
    public ResponseEntity<ApiResponse<NxEmailParsedOrder>> getParsedOrder(@PathVariable UUID id) {
        return ResponseEntity.ok(ApiResponse.success(emailOrderParsingService.getParsedOrder(id)));
    }

    @PostMapping("/parse")
    public ResponseEntity<ApiResponse<NxEmailParsedOrder>> parseEmailContent(
            @RequestParam("subject") String subject,
            @RequestParam("from") String from,
            @RequestParam("body") String body) {
        NxEmailParsedOrder parsed = emailOrderParsingService.parseEmailContent(subject, from, body, "HTML");
        return ResponseEntity.ok(ApiResponse.success(parsed, "Email content parsed"));
    }

    @PostMapping("/parse-csv")
    public ResponseEntity<ApiResponse<NxEmailParsedOrder>> parseCsv(
            @RequestParam("file") MultipartFile file,
            @RequestParam(value = "subject", required = false) String subject,
            @RequestParam(value = "from", required = false) String from) throws IOException {
        NxEmailParsedOrder parsed = emailOrderParsingService.parseCsvAttachment(file, subject, from);
        return ResponseEntity.ok(ApiResponse.success(parsed, "CSV attachment parsed"));
    }

    @PostMapping("/{id}/approve")
    public ResponseEntity<ApiResponse<NxEmailParsedOrder>> approveOrder(@PathVariable UUID id) {
        NxEmailParsedOrder parsed = emailOrderParsingService.approveOrder(id);
        return ResponseEntity.ok(ApiResponse.success(parsed, "Order approved and created"));
    }

    @PostMapping("/{id}/reject")
    public ResponseEntity<ApiResponse<NxEmailParsedOrder>> rejectOrder(
            @PathVariable UUID id,
            @RequestParam("reason") String reason) {
        NxEmailParsedOrder parsed = emailOrderParsingService.rejectOrder(id, reason);
        return ResponseEntity.ok(ApiResponse.success(parsed, "Order rejected"));
    }

    @GetMapping("/kpis")
    public ResponseEntity<ApiResponse<Map<String, Object>>> getKPIs() {
        return ResponseEntity.ok(ApiResponse.success(emailOrderParsingService.getKPIs()));
    }
}
