package com.nexus.oms.controller;

import com.nexus.oms.dto.ApiResponse;
import com.nexus.oms.entity.NxTrailer;
import com.nexus.oms.entity.NxTrailerEvent;
import com.nexus.oms.service.TrailerService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;
import java.util.UUID;

@Tag(name = "Trailer Lifecycle", description = "Trailer check-in/out, dwell tracking, event history")
@RestController
@RequestMapping("/trailers")
public class TrailerController {

    private final TrailerService trailerService;

    public TrailerController(TrailerService trailerService) {
        this.trailerService = trailerService;
    }

    @Operation(summary = "List trailers for a warehouse with optional status filter")
    @GetMapping
    public ResponseEntity<ApiResponse<List<NxTrailer>>> getTrailers(
            @RequestParam UUID warehouseId,
            @RequestParam(required = false) String status) {
        return ResponseEntity.ok(ApiResponse.success(trailerService.getTrailers(warehouseId, status)));
    }

    @Operation(summary = "Get trailer by ID")
    @GetMapping("/{id}")
    public ResponseEntity<ApiResponse<NxTrailer>> getTrailer(@PathVariable UUID id) {
        return ResponseEntity.ok(ApiResponse.success(trailerService.getTrailer(id)));
    }

    @Operation(summary = "Get event history for a trailer")
    @GetMapping("/{id}/events")
    public ResponseEntity<ApiResponse<List<NxTrailerEvent>>> getTrailerEvents(
            @PathVariable UUID id) {
        NxTrailer trailer = trailerService.getTrailer(id);
        return ResponseEntity.ok(ApiResponse.success(
                trailerService.getTrailerEvents(trailer.getTrailerNumber())));
    }

    @Operation(summary = "Check in a trailer to the yard")
    @PostMapping("/check-in")
    public ResponseEntity<ApiResponse<NxTrailer>> checkIn(
            @RequestParam String trailerNumber,
            @RequestParam UUID warehouseId,
            @RequestParam(required = false) UUID dockDoorId,
            @RequestParam(required = false) String carrierCode,
            @RequestParam(required = false) String driverName,
            @RequestParam(required = false) String licensePlate,
            @RequestParam(required = false) String performedBy) {
        return ResponseEntity.ok(ApiResponse.success(
                trailerService.checkIn(trailerNumber, warehouseId, dockDoorId,
                        carrierCode, driverName, licensePlate, performedBy),
                "Trailer checked in"));
    }

    @Operation(summary = "Dock a trailer at a specific dock door")
    @PostMapping("/{id}/dock")
    public ResponseEntity<ApiResponse<NxTrailer>> dockTrailer(
            @PathVariable UUID id,
            @RequestParam UUID dockDoorId,
            @RequestParam(required = false) String performedBy) {
        return ResponseEntity.ok(ApiResponse.success(
                trailerService.dockTrailer(id, dockDoorId, performedBy),
                "Trailer docked"));
    }

    @Operation(summary = "Check out a trailer from the yard")
    @PostMapping("/{id}/check-out")
    public ResponseEntity<ApiResponse<NxTrailer>> checkOut(
            @PathVariable UUID id,
            @RequestParam boolean loaded,
            @RequestParam int palletCount,
            @RequestParam(required = false) String sealNumber,
            @RequestParam(required = false) String performedBy) {
        return ResponseEntity.ok(ApiResponse.success(
                trailerService.checkOut(id, loaded, palletCount, sealNumber, performedBy),
                "Trailer checked out"));
    }

    @Operation(summary = "Get trailer statistics for a warehouse")
    @GetMapping("/stats")
    public ResponseEntity<ApiResponse<Map<String, Object>>> getTrailerStats(
            @RequestParam UUID warehouseId) {
        return ResponseEntity.ok(ApiResponse.success(trailerService.getTrailerStats(warehouseId)));
    }
}
