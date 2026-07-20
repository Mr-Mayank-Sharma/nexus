package com.nexus.oms.service;

import com.nexus.oms.entity.NxAppointment;
import com.nexus.oms.entity.NxDockDoor;
import com.nexus.oms.entity.NxYardLocation;
import com.nexus.oms.exception.ResourceNotFoundException;
import com.nexus.oms.repository.AppointmentRepository;
import com.nexus.oms.repository.DockDoorRepository;
import com.nexus.oms.repository.YardLocationRepository;
import com.nexus.oms.security.TenantContext;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.time.format.DateTimeFormatter;
import java.util.*;
import java.util.concurrent.atomic.AtomicInteger;

@Service
public class YardService {

    private final DockDoorRepository dockDoorRepository;
    private final YardLocationRepository yardLocationRepository;
    private final AppointmentRepository appointmentRepository;

    public YardService(DockDoorRepository dockDoorRepository,
                       YardLocationRepository yardLocationRepository,
                       AppointmentRepository appointmentRepository) {
        this.dockDoorRepository = dockDoorRepository;
        this.yardLocationRepository = yardLocationRepository;
        this.appointmentRepository = appointmentRepository;
    }

    // ---- Dock Door Operations ----

    public List<NxDockDoor> getDockDoors(UUID warehouseId) {
        return dockDoorRepository.findByWarehouseId(warehouseId);
    }

    public NxDockDoor getDockDoor(UUID id) {
        return dockDoorRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("DockDoor", id));
    }

    @Transactional
    public NxDockDoor createDockDoor(NxDockDoor door) {
        door.setTenantId(TenantContext.getCurrentTenantId());
        return dockDoorRepository.save(door);
    }

    @Transactional
    public NxDockDoor updateDockDoor(UUID id, NxDockDoor updates) {
        NxDockDoor existing = dockDoorRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("DockDoor", id));

        if (updates.getWarehouseId() != null) existing.setWarehouseId(updates.getWarehouseId());
        if (updates.getDoorNumber() != null) existing.setDoorNumber(updates.getDoorNumber());
        if (updates.getDoorType() != null) existing.setDoorType(updates.getDoorType());
        if (updates.getStatus() != null) existing.setStatus(updates.getStatus());
        if (updates.getDockHeight() != null) existing.setDockHeight(updates.getDockHeight());
        if (updates.getHasLeveler() != null) existing.setHasLeveler(updates.getHasLeveler());
        if (updates.getHasSeal() != null) existing.setHasSeal(updates.getHasSeal());
        if (updates.getMaxWidthCm() != null) existing.setMaxWidthCm(updates.getMaxWidthCm());
        if (updates.getMaxHeightCm() != null) existing.setMaxHeightCm(updates.getMaxHeightCm());
        if (updates.getMaxWeightKg() != null) existing.setMaxWeightKg(updates.getMaxWeightKg());
        if (updates.getZoneId() != null) existing.setZoneId(updates.getZoneId());
        if (updates.getNotes() != null) existing.setNotes(updates.getNotes());
        if (updates.getMetadata() != null) existing.setMetadata(updates.getMetadata());

        return dockDoorRepository.save(existing);
    }

    @Transactional
    public NxDockDoor assignVehicleToDoor(UUID doorId, UUID vehicleId, UUID appointmentId) {
        NxDockDoor door = dockDoorRepository.findById(doorId)
                .orElseThrow(() -> new ResourceNotFoundException("DockDoor", doorId));

        if ("MAINTENANCE".equals(door.getStatus()) || "CLOSED".equals(door.getStatus())) {
            throw new IllegalStateException("Dock door " + door.getStatus().toLowerCase() + " cannot accept vehicles");
        }

        door.setCurrentVehicleId(vehicleId);
        door.setCurrentAppointmentId(appointmentId);
        door.setStatus("OCCUPIED");
        return dockDoorRepository.save(door);
    }

    @Transactional
    public NxDockDoor releaseDoor(UUID doorId) {
        NxDockDoor door = dockDoorRepository.findById(doorId)
                .orElseThrow(() -> new ResourceNotFoundException("DockDoor", doorId));

        door.setCurrentVehicleId(null);
        door.setCurrentAppointmentId(null);
        door.setStatus("AVAILABLE");
        return dockDoorRepository.save(door);
    }

    public Map<String, Object> getDockUtilization(UUID warehouseId) {
        List<NxDockDoor> doors = dockDoorRepository.findByWarehouseId(warehouseId);

        long total = doors.size();
        long available = doors.stream().filter(d -> "AVAILABLE".equals(d.getStatus())).count();
        long occupied = doors.stream().filter(d -> "OCCUPIED".equals(d.getStatus())).count();
        long maintenance = doors.stream().filter(d -> "MAINTENANCE".equals(d.getStatus())).count();
        long closed = doors.stream().filter(d -> "CLOSED".equals(d.getStatus())).count();
        double utilizationPercent = total > 0
                ? Math.round(((double) occupied / total) * 100.0 * 100.0) / 100.0
                : 0.0;

        Map<String, Object> result = new LinkedHashMap<>();
        result.put("total", total);
        result.put("available", available);
        result.put("occupied", occupied);
        result.put("maintenance", maintenance);
        result.put("closed", closed);
        result.put("utilizationPercent", utilizationPercent);
        return result;
    }

    // ---- Yard Location Operations ----

    public List<NxYardLocation> getYardLocations(UUID warehouseId) {
        return yardLocationRepository.findByWarehouseId(warehouseId);
    }

    public NxYardLocation getYardLocation(UUID id) {
        return yardLocationRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("YardLocation", id));
    }

    @Transactional
    public NxYardLocation createYardLocation(NxYardLocation location) {
        location.setTenantId(TenantContext.getCurrentTenantId());
        return yardLocationRepository.save(location);
    }

    @Transactional
    public NxYardLocation updateYardLocation(UUID id, NxYardLocation updates) {
        NxYardLocation existing = yardLocationRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("YardLocation", id));

        if (updates.getWarehouseId() != null) existing.setWarehouseId(updates.getWarehouseId());
        if (updates.getLocationCode() != null) existing.setLocationCode(updates.getLocationCode());
        if (updates.getLocationType() != null) existing.setLocationType(updates.getLocationType());
        if (updates.getStatus() != null) existing.setStatus(updates.getStatus());
        if (updates.getCapacity() != null) existing.setCapacity(updates.getCapacity());
        if (updates.getZone() != null) existing.setZone(updates.getZone());
        if (updates.getNotes() != null) existing.setNotes(updates.getNotes());
        if (updates.getMetadata() != null) existing.setMetadata(updates.getMetadata());

        return yardLocationRepository.save(existing);
    }

    @Transactional
    public NxYardLocation assignToYard(UUID locationId) {
        NxYardLocation location = yardLocationRepository.findById(locationId)
                .orElseThrow(() -> new ResourceNotFoundException("YardLocation", locationId));

        if ("RESERVED".equals(location.getStatus())) {
            throw new IllegalStateException("Yard location is reserved");
        }

        int capacity = location.getCapacity() != null ? location.getCapacity() : 0;
        int current = location.getCurrentOccupancy() != null ? location.getCurrentOccupancy() : 0;

        if (capacity > 0 && current >= capacity) {
            throw new IllegalStateException("Yard location is at full capacity");
        }

        location.setCurrentOccupancy(current + 1);
        if (capacity > 0 && location.getCurrentOccupancy() >= capacity) {
            location.setStatus("OCCUPIED");
        }

        return yardLocationRepository.save(location);
    }

    @Transactional
    public NxYardLocation releaseYard(UUID locationId) {
        NxYardLocation location = yardLocationRepository.findById(locationId)
                .orElseThrow(() -> new ResourceNotFoundException("YardLocation", locationId));

        int current = location.getCurrentOccupancy() != null ? location.getCurrentOccupancy() : 0;
        if (current > 0) {
            location.setCurrentOccupancy(current - 1);
        }
        if (location.getCurrentOccupancy() == 0) {
            location.setStatus("AVAILABLE");
        }

        return yardLocationRepository.save(location);
    }

    public Map<String, Object> getYardUtilization(UUID warehouseId) {
        List<NxYardLocation> locations = yardLocationRepository.findByWarehouseId(warehouseId);

        int totalCapacity = 0;
        int currentOccupancy = 0;
        Map<String, Map<String, Object>> byZone = new LinkedHashMap<>();

        for (NxYardLocation loc : locations) {
            int cap = loc.getCapacity() != null ? loc.getCapacity() : 0;
            int occ = loc.getCurrentOccupancy() != null ? loc.getCurrentOccupancy() : 0;
            totalCapacity += cap;
            currentOccupancy += occ;

            String zone = loc.getZone() != null ? loc.getZone() : "UNASSIGNED";
            byZone.computeIfAbsent(zone, z -> {
                Map<String, Object> zoneData = new LinkedHashMap<>();
                zoneData.put("capacity", 0);
                zoneData.put("occupancy", 0);
                zoneData.put("locationCount", 0);
                return zoneData;
            });
            Map<String, Object> zoneData = byZone.get(zone);
            zoneData.put("capacity", (int) zoneData.get("capacity") + cap);
            zoneData.put("occupancy", (int) zoneData.get("occupancy") + occ);
            zoneData.put("locationCount", (int) zoneData.get("locationCount") + 1);
        }

        Map<String, Object> result = new LinkedHashMap<>();
        result.put("totalCapacity", totalCapacity);
        result.put("currentOccupancy", currentOccupancy);
        result.put("utilizationPercent", totalCapacity > 0
                ? Math.round(((double) currentOccupancy / totalCapacity) * 100.0 * 100.0) / 100.0
                : 0.0);
        result.put("totalLocations", locations.size());
        result.put("byZone", byZone);
        return result;
    }

    // ---- Appointment Operations ----

    public List<NxAppointment> getAppointments(UUID warehouseId, String status) {
        if (status != null && !status.isBlank()) {
            return appointmentRepository.findByWarehouseIdAndStatus(warehouseId, status);
        }
        return appointmentRepository.findByWarehouseId(warehouseId);
    }

    public NxAppointment getAppointment(UUID id) {
        return appointmentRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Appointment", id));
    }

    @Transactional
    public NxAppointment requestAppointment(NxAppointment appointment) {
        appointment.setTenantId(TenantContext.getCurrentTenantId());
        appointment.setStatus("REQUESTED");
        appointment.setAppointmentNumber(generateAppointmentNumber());
        return appointmentRepository.save(appointment);
    }

    @Transactional
    public NxAppointment confirmAppointment(UUID id) {
        NxAppointment apt = appointmentRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Appointment", id));

        if (!"REQUESTED".equals(apt.getStatus())) {
            throw new IllegalStateException("Only REQUESTED appointments can be confirmed");
        }

        // Auto-assign dock door
        List<NxDockDoor> availableDoors = dockDoorRepository.findByWarehouseIdAndStatus(
                apt.getWarehouseId(), "AVAILABLE");
        if (!availableDoors.isEmpty()) {
            NxDockDoor door = availableDoors.get(0);
            apt.setDockDoorId(door.getId());
        }

        // Auto-assign yard location
        List<NxYardLocation> availableLocations = yardLocationRepository.findByWarehouseIdAndStatus(
                apt.getWarehouseId(), "AVAILABLE");
        if (!availableLocations.isEmpty()) {
            NxYardLocation loc = availableLocations.get(0);
            apt.setYardLocationId(loc.getId());
        }

        apt.setStatus("CONFIRMED");
        return appointmentRepository.save(apt);
    }

    @Transactional
    public NxAppointment checkInAppointment(UUID id, String checkedInBy) {
        NxAppointment apt = appointmentRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Appointment", id));

        if (!"CONFIRMED".equals(apt.getStatus())) {
            throw new IllegalStateException("Only CONFIRMED appointments can be checked in");
        }

        apt.setStatus("CHECKED_IN");
        apt.setActualArrival(LocalDateTime.now());
        apt.setCheckedInBy(checkedInBy);

        // Assign to dock if not already assigned
        if (apt.getDockDoorId() == null) {
            List<NxDockDoor> availableDoors = dockDoorRepository.findByWarehouseIdAndStatus(
                    apt.getWarehouseId(), "AVAILABLE");
            if (!availableDoors.isEmpty()) {
                apt.setDockDoorId(availableDoors.get(0).getId());
            }
        }

        // Mark dock door as occupied
        if (apt.getDockDoorId() != null) {
            NxDockDoor door = dockDoorRepository.findById(apt.getDockDoorId()).orElse(null);
            if (door != null) {
                door.setCurrentVehicleId(apt.getId());
                door.setCurrentAppointmentId(apt.getId());
                door.setStatus("OCCUPIED");
                dockDoorRepository.save(door);
            }
        }

        // Increment yard occupancy
        if (apt.getYardLocationId() != null) {
            NxYardLocation loc = yardLocationRepository.findById(apt.getYardLocationId()).orElse(null);
            if (loc != null) {
                int current = loc.getCurrentOccupancy() != null ? loc.getCurrentOccupancy() : 0;
                loc.setCurrentOccupancy(current + 1);
                int capacity = loc.getCapacity() != null ? loc.getCapacity() : 0;
                if (capacity > 0 && loc.getCurrentOccupancy() >= capacity) {
                    loc.setStatus("OCCUPIED");
                }
                yardLocationRepository.save(loc);
            }
        }

        return appointmentRepository.save(apt);
    }

    @Transactional
    public NxAppointment startAppointment(UUID id) {
        NxAppointment apt = appointmentRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Appointment", id));

        if (!"CHECKED_IN".equals(apt.getStatus())) {
            throw new IllegalStateException("Only CHECKED_IN appointments can be started");
        }

        apt.setStatus("IN_PROGRESS");
        return appointmentRepository.save(apt);
    }

    @Transactional
    public NxAppointment completeAppointment(UUID id, String completedBy) {
        NxAppointment apt = appointmentRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Appointment", id));

        if (!"IN_PROGRESS".equals(apt.getStatus())) {
            throw new IllegalStateException("Only IN_PROGRESS appointments can be completed");
        }

        apt.setStatus("COMPLETED");
        apt.setActualDeparture(LocalDateTime.now());
        apt.setCompletedBy(completedBy);

        // Release dock door
        if (apt.getDockDoorId() != null) {
            NxDockDoor door = dockDoorRepository.findById(apt.getDockDoorId()).orElse(null);
            if (door != null) {
                door.setCurrentVehicleId(null);
                door.setCurrentAppointmentId(null);
                door.setStatus("AVAILABLE");
                dockDoorRepository.save(door);
            }
        }

        // Release yard occupancy
        if (apt.getYardLocationId() != null) {
            NxYardLocation loc = yardLocationRepository.findById(apt.getYardLocationId()).orElse(null);
            if (loc != null) {
                int current = loc.getCurrentOccupancy() != null ? loc.getCurrentOccupancy() : 0;
                if (current > 0) {
                    loc.setCurrentOccupancy(current - 1);
                }
                if (loc.getCurrentOccupancy() == 0) {
                    loc.setStatus("AVAILABLE");
                }
                yardLocationRepository.save(loc);
            }
        }

        return appointmentRepository.save(apt);
    }

    @Transactional
    public NxAppointment cancelAppointment(UUID id) {
        NxAppointment apt = appointmentRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Appointment", id));

        Set<String> activeStates = Set.of("REQUESTED", "CONFIRMED", "CHECKED_IN", "IN_PROGRESS");
        if (!activeStates.contains(apt.getStatus())) {
            throw new IllegalStateException("Cannot cancel appointment in " + apt.getStatus() + " status");
        }

        apt.setStatus("CANCELLED");

        // Release dock door
        if (apt.getDockDoorId() != null) {
            NxDockDoor door = dockDoorRepository.findById(apt.getDockDoorId()).orElse(null);
            if (door != null && apt.getDockDoorId().equals(door.getCurrentAppointmentId())) {
                door.setCurrentVehicleId(null);
                door.setCurrentAppointmentId(null);
                door.setStatus("AVAILABLE");
                dockDoorRepository.save(door);
            }
        }

        // Release yard
        if (apt.getYardLocationId() != null) {
            NxYardLocation loc = yardLocationRepository.findById(apt.getYardLocationId()).orElse(null);
            if (loc != null) {
                int current = loc.getCurrentOccupancy() != null ? loc.getCurrentOccupancy() : 0;
                if (current > 0) {
                    loc.setCurrentOccupancy(current - 1);
                }
                if (loc.getCurrentOccupancy() == 0) {
                    loc.setStatus("AVAILABLE");
                }
                yardLocationRepository.save(loc);
            }
        }

        return appointmentRepository.save(apt);
    }

    @Transactional
    public NxAppointment markNoShow(UUID id) {
        NxAppointment apt = appointmentRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Appointment", id));

        if (!"CONFIRMED".equals(apt.getStatus()) && !"CHECKED_IN".equals(apt.getStatus())) {
            throw new IllegalStateException("Only CONFIRMED or CHECKED_IN appointments can be marked as no-show");
        }

        apt.setStatus("NO_SHOW");

        // Release dock door
        if (apt.getDockDoorId() != null) {
            NxDockDoor door = dockDoorRepository.findById(apt.getDockDoorId()).orElse(null);
            if (door != null && apt.getDockDoorId().equals(door.getCurrentAppointmentId())) {
                door.setCurrentVehicleId(null);
                door.setCurrentAppointmentId(null);
                door.setStatus("AVAILABLE");
                dockDoorRepository.save(door);
            }
        }

        // Release yard
        if (apt.getYardLocationId() != null) {
            NxYardLocation loc = yardLocationRepository.findById(apt.getYardLocationId()).orElse(null);
            if (loc != null) {
                int current = loc.getCurrentOccupancy() != null ? loc.getCurrentOccupancy() : 0;
                if (current > 0) {
                    loc.setCurrentOccupancy(current - 1);
                }
                if (loc.getCurrentOccupancy() == 0) {
                    loc.setStatus("AVAILABLE");
                }
                yardLocationRepository.save(loc);
            }
        }

        return appointmentRepository.save(apt);
    }

    public List<Map<String, Object>> getAppointmentCalendar(UUID warehouseId, LocalDate date) {
        LocalDateTime dayStart = date.atTime(LocalTime.of(6, 0));
        LocalDateTime dayEnd = date.atTime(LocalTime.of(22, 0));

        List<NxAppointment> appointments = appointmentRepository
                .findByWarehouseIdAndEstimatedArrivalBetween(warehouseId, dayStart, dayEnd);

        List<Map<String, Object>> slots = new ArrayList<>();
        for (int hour = 6; hour < 22; hour++) {
            LocalTime slotStart = LocalTime.of(hour, 0);
            LocalTime slotEnd = LocalTime.of(hour + 1, 0);

            List<NxAppointment> slotAppointments = new ArrayList<>();
            for (NxAppointment apt : appointments) {
                if (apt.getEstimatedArrival() != null) {
                    LocalTime arrivalTime = apt.getEstimatedArrival().toLocalTime();
                    if (!arrivalTime.isBefore(slotStart) && arrivalTime.isBefore(slotEnd)) {
                        slotAppointments.add(apt);
                    }
                }
            }

            Map<String, Object> slot = new LinkedHashMap<>();
            slot.put("timeSlot", String.format("%02d:00-%02d:00", hour, hour + 1));
            slot.put("appointmentCount", slotAppointments.size());
            slot.put("appointments", slotAppointments.stream().map(apt -> {
                Map<String, Object> summary = new LinkedHashMap<>();
                summary.put("id", apt.getId());
                summary.put("appointmentNumber", apt.getAppointmentNumber());
                summary.put("type", apt.getType());
                summary.put("status", apt.getStatus());
                summary.put("carrierName", apt.getCarrierName());
                summary.put("vehicleLicensePlate", apt.getVehicleLicensePlate());
                summary.put("dockDoorId", apt.getDockDoorId());
                return summary;
            }).toList());
            slots.add(slot);
        }

        return slots;
    }

    public Map<String, Object> getAppointmentStats(UUID warehouseId) {
        List<NxAppointment> all = appointmentRepository.findByWarehouseId(warehouseId);

        long total = all.size();
        long confirmed = all.stream().filter(a -> "CONFIRMED".equals(a.getStatus())).count();
        long inProgress = all.stream().filter(a -> "IN_PROGRESS".equals(a.getStatus())).count();
        long completed = all.stream().filter(a -> "COMPLETED".equals(a.getStatus())).count();
        long noShows = all.stream().filter(a -> "NO_SHOW".equals(a.getStatus())).count();
        long cancelled = all.stream().filter(a -> "CANCELLED".equals(a.getStatus())).count();

        // Average turnaround time in minutes for completed appointments
        OptionalDouble avgTurnaround = all.stream()
                .filter(a -> "COMPLETED".equals(a.getStatus()))
                .filter(a -> a.getActualArrival() != null && a.getActualDeparture() != null)
                .mapToLong(a -> java.time.Duration.between(a.getActualArrival(), a.getActualDeparture()).toMinutes())
                .average();

        Map<String, Object> result = new LinkedHashMap<>();
        result.put("total", total);
        result.put("requested", all.stream().filter(a -> "REQUESTED".equals(a.getStatus())).count());
        result.put("confirmed", confirmed);
        result.put("checkedIn", all.stream().filter(a -> "CHECKED_IN".equals(a.getStatus())).count());
        result.put("inProgress", inProgress);
        result.put("completed", completed);
        result.put("cancelled", cancelled);
        result.put("noShows", noShows);
        result.put("avgTurnaroundMinutes", avgTurnaround.isPresent()
                ? Math.round(avgTurnaround.getAsDouble()) : 0);
        return result;
    }

    private String generateAppointmentNumber() {
        String dateStr = LocalDate.now().format(DateTimeFormatter.ofPattern("yyyyMMdd"));
        long countToday = appointmentRepository.findAll().stream()
                .filter(a -> a.getAppointmentNumber() != null
                        && a.getAppointmentNumber().contains(dateStr))
                .count();
        return String.format("APT-%s-%04d", dateStr, countToday + 1);
    }
}
