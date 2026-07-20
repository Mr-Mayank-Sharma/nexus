package com.nexus.oms.controller;

import com.nexus.oms.dto.ApiResponse;
import com.nexus.oms.entity.NxAppointment;
import com.nexus.oms.entity.NxDockDoor;
import com.nexus.oms.entity.NxYardLocation;
import com.nexus.oms.service.YardService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.time.LocalDate;
import java.util.List;
import java.util.Map;
import java.util.UUID;

@Tag(name = "Yard & Dock Management", description = "Yard, dock door, and appointment management APIs")
@RestController
@RequestMapping("/yards")
public class YardController {

    private final YardService yardService;

    public YardController(YardService yardService) {
        this.yardService = yardService;
    }

    // ---- Dock Door Endpoints ----

    @Operation(summary = "List dock doors for a warehouse")
    @GetMapping("/docks")
    public ResponseEntity<ApiResponse<List<NxDockDoor>>> getDockDoors(
            @RequestParam UUID warehouseId) {
        return ResponseEntity.ok(ApiResponse.success(yardService.getDockDoors(warehouseId)));
    }

    @Operation(summary = "Get dock door by ID")
    @GetMapping("/docks/{id}")
    public ResponseEntity<ApiResponse<NxDockDoor>> getDockDoor(@PathVariable UUID id) {
        return ResponseEntity.ok(ApiResponse.success(yardService.getDockDoor(id)));
    }

    @Operation(summary = "Create a new dock door")
    @PostMapping("/docks")
    public ResponseEntity<ApiResponse<NxDockDoor>> createDockDoor(
            @RequestBody NxDockDoor dockDoor) {
        return ResponseEntity.ok(ApiResponse.success(yardService.createDockDoor(dockDoor), "Dock door created"));
    }

    @Operation(summary = "Update dock door details")
    @PutMapping("/docks/{id}")
    public ResponseEntity<ApiResponse<NxDockDoor>> updateDockDoor(
            @PathVariable UUID id,
            @RequestBody NxDockDoor dockDoor) {
        return ResponseEntity.ok(ApiResponse.success(yardService.updateDockDoor(id, dockDoor), "Dock door updated"));
    }

    @Operation(summary = "Assign a vehicle to a dock door")
    @PostMapping("/docks/{id}/assign")
    public ResponseEntity<ApiResponse<NxDockDoor>> assignVehicleToDoor(
            @PathVariable UUID id,
            @RequestParam UUID vehicleId,
            @RequestParam(required = false) UUID appointmentId) {
        return ResponseEntity.ok(ApiResponse.success(
                yardService.assignVehicleToDoor(id, vehicleId, appointmentId), "Vehicle assigned to dock"));
    }

    @Operation(summary = "Release a dock door")
    @PostMapping("/docks/{id}/release")
    public ResponseEntity<ApiResponse<NxDockDoor>> releaseDoor(@PathVariable UUID id) {
        return ResponseEntity.ok(ApiResponse.success(yardService.releaseDoor(id), "Dock door released"));
    }

    @Operation(summary = "Get dock door utilization stats")
    @GetMapping("/docks/utilization")
    public ResponseEntity<ApiResponse<Map<String, Object>>> getDockUtilization(
            @RequestParam UUID warehouseId) {
        return ResponseEntity.ok(ApiResponse.success(yardService.getDockUtilization(warehouseId)));
    }

    // ---- Yard Location Endpoints ----

    @Operation(summary = "List yard locations for a warehouse")
    @GetMapping("/locations")
    public ResponseEntity<ApiResponse<List<NxYardLocation>>> getYardLocations(
            @RequestParam UUID warehouseId) {
        return ResponseEntity.ok(ApiResponse.success(yardService.getYardLocations(warehouseId)));
    }

    @Operation(summary = "Get yard location by ID")
    @GetMapping("/locations/{id}")
    public ResponseEntity<ApiResponse<NxYardLocation>> getYardLocation(@PathVariable UUID id) {
        return ResponseEntity.ok(ApiResponse.success(yardService.getYardLocation(id)));
    }

    @Operation(summary = "Create a new yard location")
    @PostMapping("/locations")
    public ResponseEntity<ApiResponse<NxYardLocation>> createYardLocation(
            @RequestBody NxYardLocation location) {
        return ResponseEntity.ok(ApiResponse.success(yardService.createYardLocation(location), "Yard location created"));
    }

    @Operation(summary = "Update yard location details")
    @PutMapping("/locations/{id}")
    public ResponseEntity<ApiResponse<NxYardLocation>> updateYardLocation(
            @PathVariable UUID id,
            @RequestBody NxYardLocation location) {
        return ResponseEntity.ok(ApiResponse.success(yardService.updateYardLocation(id, location), "Yard location updated"));
    }

    @Operation(summary = "Assign a vehicle to a yard location")
    @PostMapping("/locations/{id}/assign")
    public ResponseEntity<ApiResponse<NxYardLocation>> assignToYard(@PathVariable UUID id) {
        return ResponseEntity.ok(ApiResponse.success(yardService.assignToYard(id), "Assigned to yard location"));
    }

    @Operation(summary = "Release a yard location")
    @PostMapping("/locations/{id}/release")
    public ResponseEntity<ApiResponse<NxYardLocation>> releaseYard(@PathVariable UUID id) {
        return ResponseEntity.ok(ApiResponse.success(yardService.releaseYard(id), "Yard location released"));
    }

    @Operation(summary = "Get yard utilization stats")
    @GetMapping("/locations/utilization")
    public ResponseEntity<ApiResponse<Map<String, Object>>> getYardUtilization(
            @RequestParam UUID warehouseId) {
        return ResponseEntity.ok(ApiResponse.success(yardService.getYardUtilization(warehouseId)));
    }

    // ---- Appointment Endpoints ----

    @Operation(summary = "List appointments with optional status filter")
    @GetMapping("/appointments")
    public ResponseEntity<ApiResponse<List<NxAppointment>>> getAppointments(
            @RequestParam UUID warehouseId,
            @RequestParam(required = false) String status) {
        return ResponseEntity.ok(ApiResponse.success(yardService.getAppointments(warehouseId, status)));
    }

    @Operation(summary = "Get appointment by ID")
    @GetMapping("/appointments/{id}")
    public ResponseEntity<ApiResponse<NxAppointment>> getAppointment(@PathVariable UUID id) {
        return ResponseEntity.ok(ApiResponse.success(yardService.getAppointment(id)));
    }

    @Operation(summary = "Request a new appointment")
    @PostMapping("/appointments")
    public ResponseEntity<ApiResponse<NxAppointment>> requestAppointment(
            @RequestBody NxAppointment appointment) {
        return ResponseEntity.ok(ApiResponse.success(
                yardService.requestAppointment(appointment), "Appointment requested"));
    }

    @Operation(summary = "Confirm an appointment and auto-assign dock and yard")
    @PostMapping("/appointments/{id}/confirm")
    public ResponseEntity<ApiResponse<NxAppointment>> confirmAppointment(@PathVariable UUID id) {
        return ResponseEntity.ok(ApiResponse.success(yardService.confirmAppointment(id), "Appointment confirmed"));
    }

    @Operation(summary = "Check in a confirmed appointment")
    @PostMapping("/appointments/{id}/check-in")
    public ResponseEntity<ApiResponse<NxAppointment>> checkInAppointment(
            @PathVariable UUID id,
            @RequestParam String checkedInBy) {
        return ResponseEntity.ok(ApiResponse.success(
                yardService.checkInAppointment(id, checkedInBy), "Appointment checked in"));
    }

    @Operation(summary = "Start an in-progress appointment")
    @PostMapping("/appointments/{id}/start")
    public ResponseEntity<ApiResponse<NxAppointment>> startAppointment(@PathVariable UUID id) {
        return ResponseEntity.ok(ApiResponse.success(yardService.startAppointment(id), "Appointment started"));
    }

    @Operation(summary = "Complete an appointment and release resources")
    @PostMapping("/appointments/{id}/complete")
    public ResponseEntity<ApiResponse<NxAppointment>> completeAppointment(
            @PathVariable UUID id,
            @RequestParam String completedBy) {
        return ResponseEntity.ok(ApiResponse.success(
                yardService.completeAppointment(id, completedBy), "Appointment completed"));
    }

    @Operation(summary = "Cancel an appointment from any active state")
    @PostMapping("/appointments/{id}/cancel")
    public ResponseEntity<ApiResponse<NxAppointment>> cancelAppointment(@PathVariable UUID id) {
        return ResponseEntity.ok(ApiResponse.success(yardService.cancelAppointment(id), "Appointment cancelled"));
    }

    @Operation(summary = "Mark an appointment as no-show")
    @PostMapping("/appointments/{id}/no-show")
    public ResponseEntity<ApiResponse<NxAppointment>> markNoShow(@PathVariable UUID id) {
        return ResponseEntity.ok(ApiResponse.success(yardService.markNoShow(id), "Appointment marked as no-show"));
    }

    @Operation(summary = "Get appointment calendar for a date")
    @GetMapping("/appointments/calendar")
    public ResponseEntity<ApiResponse<List<Map<String, Object>>>> getAppointmentCalendar(
            @RequestParam UUID warehouseId,
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate date) {
        return ResponseEntity.ok(ApiResponse.success(yardService.getAppointmentCalendar(warehouseId, date)));
    }

    @Operation(summary = "Get appointment statistics for a warehouse")
    @GetMapping("/appointments/stats")
    public ResponseEntity<ApiResponse<Map<String, Object>>> getAppointmentStats(
            @RequestParam UUID warehouseId) {
        return ResponseEntity.ok(ApiResponse.success(yardService.getAppointmentStats(warehouseId)));
    }
}
