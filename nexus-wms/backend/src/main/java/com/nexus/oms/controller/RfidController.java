package com.nexus.oms.controller;

import com.nexus.oms.dto.ApiResponse;
import com.nexus.oms.dto.CycleCountResult;
import com.nexus.oms.dto.RfidScanResult;
import com.nexus.oms.dto.RfidSessionRequest;
import com.nexus.oms.entity.NxRfidScanSession;
import com.nexus.oms.entity.NxSerializedInventory;
import com.nexus.oms.security.TenantContext;
import com.nexus.oms.service.RfidIngestionService;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.UUID;

@RestController
@RequestMapping("/rfid")
public class RfidController {

    private final RfidIngestionService rfidIngestionService;

    public RfidController(RfidIngestionService rfidIngestionService) {
        this.rfidIngestionService = rfidIngestionService;
    }

    @PostMapping("/sessions")
    public ResponseEntity<ApiResponse<NxRfidScanSession>> openSession(
            @RequestBody RfidSessionRequest request) {
        return ResponseEntity.ok(ApiResponse.success(
                rfidIngestionService.openSession(request.getLocationId(), request.getSessionType(), request.getMode()),
                "Scan session opened"));
    }

    @PostMapping("/sessions/{id}/epcs")
    public ResponseEntity<ApiResponse<RfidScanResult>> ingestEpcs(
            @PathVariable UUID id,
            @RequestBody List<String> epcs) {
        return ResponseEntity.ok(ApiResponse.success(
                rfidIngestionService.ingestEpcs(id, epcs),
                "EPCs ingested"));
    }

    @PostMapping("/sessions/{id}/complete")
    public ResponseEntity<ApiResponse<NxRfidScanSession>> completeSession(@PathVariable UUID id) {
        return ResponseEntity.ok(ApiResponse.success(
                rfidIngestionService.completeSession(id),
                "Scan session completed"));
    }

    @PostMapping("/sessions/{id}/receive")
    public ResponseEntity<ApiResponse<Integer>> receive(
            @PathVariable UUID id,
            @RequestParam UUID locationId,
            @RequestBody List<String> epcs) {
        return ResponseEntity.ok(ApiResponse.success(
                rfidIngestionService.receive(id, locationId, epcs),
                "EPCs received"));
    }

    @PostMapping("/sessions/{id}/cycle-count")
    public ResponseEntity<ApiResponse<CycleCountResult>> runCycleCount(
            @PathVariable UUID id,
            @RequestParam UUID locationId) {
        return ResponseEntity.ok(ApiResponse.success(
                rfidIngestionService.runSerializedCycleCount(id, locationId),
                "Cycle count completed"));
    }

    @GetMapping("/inventory")
    public ResponseEntity<ApiResponse<Page<NxSerializedInventory>>> getInventory(
            @RequestParam(required = false) UUID locationId,
            @RequestParam(required = false) String status,
            Pageable pageable) {
        UUID tenantId = TenantContext.getCurrentTenantId();
        Page<NxSerializedInventory> result;
        if (locationId != null && status != null) {
            result = rfidIngestionService.getInventory(tenantId, locationId, status, pageable);
        } else {
            result = rfidIngestionService.getInventory(tenantId, pageable);
        }
        return ResponseEntity.ok(ApiResponse.success(result));
    }

    @PostMapping("/retag")
    public ResponseEntity<ApiResponse<RfidIngestionService.RetagResult>> retag(
            @RequestParam String oldEpc,
            @RequestParam String newEpc,
            @RequestParam UUID locationId) {
        return ResponseEntity.ok(ApiResponse.success(
                rfidIngestionService.retag(oldEpc, newEpc, locationId),
                "Tag retagged"));
    }

    @GetMapping("/decode/{epc}")
    public ResponseEntity<ApiResponse<RfidIngestionService.DecodedEpc>> decode(@PathVariable String epc) {
        return ResponseEntity.ok(ApiResponse.success(rfidIngestionService.decodeEpc(epc)));
    }
}
