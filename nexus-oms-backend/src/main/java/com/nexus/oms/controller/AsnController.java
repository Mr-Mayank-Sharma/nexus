package com.nexus.oms.controller;

import com.nexus.oms.dto.ApiResponse;
import com.nexus.oms.dto.AsnRequest;
import com.nexus.oms.entity.NxAsn;
import com.nexus.oms.security.TenantContext;
import com.nexus.oms.service.AsnService;
import jakarta.validation.Valid;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.Map;
import java.util.UUID;

@RestController
@RequestMapping("/asn")
public class AsnController {

    private final AsnService asnService;

    public AsnController(AsnService asnService) {
        this.asnService = asnService;
    }

    @GetMapping
    public ResponseEntity<ApiResponse<Page<NxAsn>>> getAsns(
            @RequestParam(required = false) String status,
            Pageable pageable) {
        return ResponseEntity.ok(ApiResponse.success(
                asnService.getAsns(TenantContext.getCurrentTenantId(), status, pageable)));
    }

    @GetMapping("/{id}")
    public ResponseEntity<ApiResponse<NxAsn>> getAsn(@PathVariable UUID id) {
        return ResponseEntity.ok(ApiResponse.success(asnService.getAsn(id)));
    }

    @PostMapping
    public ResponseEntity<ApiResponse<NxAsn>> createAsn(
            @Valid @RequestBody AsnRequest request) {
        return ResponseEntity.ok(ApiResponse.success(
                asnService.createAsn(TenantContext.getCurrentTenantId(), request),
                "ASN created"));
    }

    @PostMapping("/{asnId}/lines/{lineId}/receive")
    public ResponseEntity<ApiResponse<NxAsn>> receiveLine(
            @PathVariable UUID asnId,
            @PathVariable UUID lineId,
            @RequestBody Map<String, Integer> body,
            @RequestParam(defaultValue = "system") String receivedBy) {
        Integer qty = body.get("receivedQty");
        if (qty == null) {
            throw new com.nexus.oms.exception.BadRequestException("receivedQty is required");
        }
        return ResponseEntity.ok(ApiResponse.success(
                asnService.receiveLine(asnId, lineId, qty, receivedBy),
                "Line received"));
    }

    @PostMapping("/{id}/close")
    public ResponseEntity<ApiResponse<NxAsn>> closeAsn(@PathVariable UUID id) {
        return ResponseEntity.ok(ApiResponse.success(asnService.closeAsn(id), "ASN closed"));
    }

    @DeleteMapping("/{id}")
    public ResponseEntity<ApiResponse<Void>> deleteAsn(@PathVariable UUID id) {
        asnService.deleteAsn(id);
        return ResponseEntity.ok(ApiResponse.success(null, "ASN deleted"));
    }
}
