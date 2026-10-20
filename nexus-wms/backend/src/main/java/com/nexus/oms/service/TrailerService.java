package com.nexus.oms.service;

import com.nexus.oms.entity.*;
import com.nexus.oms.exception.BadRequestException;
import com.nexus.oms.exception.ResourceNotFoundException;
import com.nexus.oms.repository.*;
import com.nexus.oms.security.TenantContext;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.Duration;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.*;
import java.util.stream.Collectors;

@Service
public class TrailerService {

    private final NxTrailerRepository trailerRepository;
    private final NxTrailerEventRepository trailerEventRepository;
    private final DockDoorRepository dockDoorRepository;
    private final YardLocationRepository yardLocationRepository;

    public TrailerService(NxTrailerRepository trailerRepository,
                          NxTrailerEventRepository trailerEventRepository,
                          DockDoorRepository dockDoorRepository,
                          YardLocationRepository yardLocationRepository) {
        this.trailerRepository = trailerRepository;
        this.trailerEventRepository = trailerEventRepository;
        this.dockDoorRepository = dockDoorRepository;
        this.yardLocationRepository = yardLocationRepository;
    }

    public List<NxTrailer> getTrailers(UUID warehouseId, String status) {
        if (status != null && !status.isBlank()) {
            return trailerRepository.findByWarehouseIdAndStatus(warehouseId, status);
        }
        return trailerRepository.findByWarehouseId(warehouseId);
    }

    public NxTrailer getTrailer(UUID id) {
        return trailerRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Trailer", id));
    }

    public List<NxTrailerEvent> getTrailerEvents(String trailerNumber) {
        return trailerEventRepository.findByTrailerNumberOrderByEventTimeDesc(trailerNumber);
    }

    @Transactional
    public NxTrailer checkIn(String trailerNumber, UUID warehouseId, UUID dockDoorId,
                              String carrierCode, String driverName, String licensePlate,
                              String performedBy) {
        UUID tenantId = TenantContext.getCurrentTenantId();

        Optional<NxTrailer> existing = trailerRepository.findByWarehouseIdAndTrailerNumber(warehouseId, trailerNumber);
        NxTrailer trailer;
        if (existing.isPresent()) {
            trailer = existing.get();
        } else {
            trailer = NxTrailer.builder()
                    .tenantId(tenantId)
                    .warehouseId(warehouseId)
                    .trailerNumber(trailerNumber)
                    .carrierCode(carrierCode)
                    .licensePlate(licensePlate)
                    .dwelledMinutes(0)
                    .build();
        }

        trailer.setStatus("IN_YARD");
        trailer.setCheckedInAt(LocalDateTime.now());
        trailer.setLastEventAt(LocalDateTime.now());
        trailer.setCarrierCode(carrierCode);
        trailer.setLicensePlate(licensePlate);
        trailer = trailerRepository.save(trailer);

        NxTrailerEvent event = NxTrailerEvent.builder()
                .tenantId(tenantId)
                .warehouseId(warehouseId)
                .trailerNumber(trailerNumber)
                .eventType("CHECKED_IN")
                .dockDoorId(dockDoorId)
                .carrierCode(carrierCode)
                .driverName(driverName)
                .licensePlate(licensePlate)
                .performedBy(performedBy)
                .eventTime(LocalDateTime.now())
                .build();
        trailerEventRepository.save(event);

        return trailer;
    }

    @Transactional
    public NxTrailer dockTrailer(UUID trailerId, UUID dockDoorId, String performedBy) {
        NxTrailer trailer = trailerRepository.findById(trailerId)
                .orElseThrow(() -> new ResourceNotFoundException("Trailer", trailerId));

        NxDockDoor door = dockDoorRepository.findById(dockDoorId)
                .orElseThrow(() -> new ResourceNotFoundException("DockDoor", dockDoorId));

        if ("MAINTENANCE".equals(door.getStatus()) || "CLOSED".equals(door.getStatus())) {
            throw new BadRequestException("Dock door is " + door.getStatus().toLowerCase());
        }

        trailer.setStatus("AT_DOCK");
        trailer.setCurrentDockDoorId(dockDoorId);
        trailer.setDockedAt(LocalDateTime.now());
        trailer.setLastEventAt(LocalDateTime.now());
        trailer = trailerRepository.save(trailer);

        door.setStatus("OCCUPIED");
        dockDoorRepository.save(door);

        NxTrailerEvent event = NxTrailerEvent.builder()
                .tenantId(trailer.getTenantId())
                .warehouseId(trailer.getWarehouseId())
                .trailerNumber(trailer.getTrailerNumber())
                .eventType("DOCKED")
                .dockDoorId(dockDoorId)
                .performedBy(performedBy)
                .eventTime(LocalDateTime.now())
                .build();
        trailerEventRepository.save(event);

        return trailer;
    }

    @Transactional
    public NxTrailer checkOut(UUID trailerId, boolean loaded, int palletCount,
                               String sealNumber, String performedBy) {
        NxTrailer trailer = trailerRepository.findById(trailerId)
                .orElseThrow(() -> new ResourceNotFoundException("Trailer", trailerId));

        trailer.setStatus("DEPARTED");
        trailer.setCheckedOutAt(LocalDateTime.now());
        trailer.setLastEventAt(LocalDateTime.now());
        trailer.setLoaded(loaded);
        trailer.setPalletCount(palletCount);
        trailer.setSealNumber(sealNumber);
        trailer = trailerRepository.save(trailer);

        // Release dock door
        if (trailer.getCurrentDockDoorId() != null) {
            dockDoorRepository.findById(trailer.getCurrentDockDoorId()).ifPresent(door -> {
                door.setStatus("AVAILABLE");
                dockDoorRepository.save(door);
            });
        }

        NxTrailerEvent event = NxTrailerEvent.builder()
                .tenantId(trailer.getTenantId())
                .warehouseId(trailer.getWarehouseId())
                .trailerNumber(trailer.getTrailerNumber())
                .eventType("CHECKED_OUT")
                .loaded(loaded)
                .palletCount(palletCount)
                .sealNumber(sealNumber)
                .performedBy(performedBy)
                .eventTime(LocalDateTime.now())
                .build();
        trailerEventRepository.save(event);

        return trailer;
    }

    public Map<String, Object> getTrailerStats(UUID warehouseId) {
        List<NxTrailer> all = trailerRepository.findByWarehouseId(warehouseId);

        long inYard = all.stream().filter(t -> "IN_YARD".equals(t.getStatus())).count();
        long atDock = all.stream().filter(t -> "AT_DOCK".equals(t.getStatus())).count();
        long departed = all.stream().filter(t -> "DEPARTED".equals(t.getStatus())).count();

        double avgDwell = all.stream()
                .filter(t -> t.getDwelledMinutes() != null && t.getDwelledMinutes() > 0)
                .mapToInt(NxTrailer::getDwelledMinutes)
                .average().orElse(0.0);

        Map<String, Object> result = new LinkedHashMap<>();
        result.put("total", all.size());
        result.put("inYard", inYard);
        result.put("atDock", atDock);
        result.put("departed", departed);
        result.put("avgDwellMinutes", Math.round(avgDwell));
        return result;
    }
}
