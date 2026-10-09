package com.nexus.oms.service.twin;

import com.nexus.oms.entity.*;
import com.nexus.oms.exception.ResourceNotFoundException;
import com.nexus.oms.repository.*;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.Clock;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.*;
import java.util.function.Function;
import java.util.stream.Collectors;

/**
 * Aggregates everything the Warehouse Twin renders into one tenant-checked snapshot, so the
 * 3D scene loads with a single request. Every number is read from stored records; when a
 * metric has no underlying data it is returned as {@code null} (the UI shows "—") rather
 * than a made-up value.
 */
@Service
public class WarehouseTwinService {

    /** Appointment states that mean the vehicle is physically on site or arriving today. */
    static final Set<String> ACTIVE_APPOINTMENT = Set.of("SCHEDULED", "CONFIRMED", "CHECKED_IN", "IN_PROGRESS");
    static final Set<String> ON_SITE_APPOINTMENT = Set.of("CHECKED_IN", "IN_PROGRESS");
    static final Set<String> SHIPPED = Set.of("SHIPPED", "IN_TRANSIT", "OUT_FOR_DELIVERY", "DELIVERED");

    private final WarehouseRepository warehouseRepository;
    private final WarehouseZoneRepository zoneRepository;
    private final DockDoorRepository dockDoorRepository;
    private final YardLocationRepository yardLocationRepository;
    private final AppointmentRepository appointmentRepository;
    private final NxTrailerRepository trailerRepository;
    private final WarehouseEquipmentRepository equipmentRepository;
    private final InventoryRepository inventoryRepository;
    private final ShipmentRepository shipmentRepository;
    private final Clock clock;

    @org.springframework.beans.factory.annotation.Autowired
    public WarehouseTwinService(WarehouseRepository warehouseRepository, WarehouseZoneRepository zoneRepository,
                                DockDoorRepository dockDoorRepository, YardLocationRepository yardLocationRepository,
                                AppointmentRepository appointmentRepository, NxTrailerRepository trailerRepository,
                                WarehouseEquipmentRepository equipmentRepository, InventoryRepository inventoryRepository,
                                ShipmentRepository shipmentRepository) {
        this(warehouseRepository, zoneRepository, dockDoorRepository, yardLocationRepository, appointmentRepository,
                trailerRepository, equipmentRepository, inventoryRepository, shipmentRepository, Clock.systemDefaultZone());
    }

    WarehouseTwinService(WarehouseRepository warehouseRepository, WarehouseZoneRepository zoneRepository,
                         DockDoorRepository dockDoorRepository, YardLocationRepository yardLocationRepository,
                         AppointmentRepository appointmentRepository, NxTrailerRepository trailerRepository,
                         WarehouseEquipmentRepository equipmentRepository, InventoryRepository inventoryRepository,
                         ShipmentRepository shipmentRepository, Clock clock) {
        this.warehouseRepository = warehouseRepository;
        this.zoneRepository = zoneRepository;
        this.dockDoorRepository = dockDoorRepository;
        this.yardLocationRepository = yardLocationRepository;
        this.appointmentRepository = appointmentRepository;
        this.trailerRepository = trailerRepository;
        this.equipmentRepository = equipmentRepository;
        this.inventoryRepository = inventoryRepository;
        this.shipmentRepository = shipmentRepository;
        this.clock = clock;
    }

    // ------------------------------------------------------------------ DTOs

    public record SiteSummary(UUID id, String code, String name, String type, String city, String state,
                              String status, Double fillPct, int doorsTotal, int doorsOccupied) {}

    public record Kpis(long stockOnHandUnits, int skuCount, int trucksOnSite, int trucksInbound,
                       int trucksOutbound, int arrivalsToday, int outboundShipmentsToday,
                       Double onTimeDeliveryPct30d, Integer onTimeSample30d) {}

    public record Zone(UUID id, String code, String name, String type, BigDecimal capacitySqm, BigDecimal usedSqm,
                       Boolean hazardous) {}

    public record Dock(UUID id, String doorNumber, String doorType, String status, UUID appointmentId,
                       String vehicleLabel) {}

    public record YardSlot(UUID id, String code, String type, String status, Integer capacity, Integer occupancy) {}

    public record Truck(UUID id, String source, String label, String carrier, String direction, String status,
                        String dockDoorNumber, String yardLocationCode, LocalDateTime eta, LocalDateTime arrivedAt,
                        Integer palletCount, Double weightKg, String driverName, String poNumbers) {}

    public record Equipment(UUID id, String code, String type, String model, String status, Integer batteryLevel,
                            boolean assigned) {}

    public record StockLine(String sku, long onHand, long allocated, long available, String stockStatus) {}

    public record Shipment(UUID id, UUID orderId, String carrier, String serviceLevel, String trackingNumber,
                           String status, LocalDateTime createdAt, LocalDateTime shippedAt,
                           LocalDateTime estimatedDelivery, LocalDateTime actualDelivery) {}

    public record Snapshot(SiteSummary site, String address, BigDecimal latitude, BigDecimal longitude,
                           BigDecimal totalCapacitySqm, BigDecimal usedCapacitySqm, Kpis kpis,
                           List<Zone> zones, List<Dock> docks, List<YardSlot> yard, List<Truck> trucks,
                           List<Equipment> equipment, List<StockLine> topInventory, List<Shipment> shipments,
                           LocalDateTime generatedAt) {}

    // ------------------------------------------------------------------ queries

    @Transactional(readOnly = true)
    public List<SiteSummary> listSites(UUID tenantId) {
        List<Warehouse> warehouses = warehouseRepository.findByTenantId(tenantId, PageRequest.of(0, 200)).getContent();
        return warehouses.stream()
                .map(wh -> summarize(wh, dockDoorRepository.findByWarehouseId(wh.getId()), Set.of()))
                .sorted(Comparator.comparing(SiteSummary::code, Comparator.nullsLast(String::compareTo)))
                .toList();
    }

    @Transactional(readOnly = true)
    public Snapshot snapshot(UUID tenantId, UUID warehouseId) {
        Warehouse wh = warehouseRepository.findByIdAndTenantId(warehouseId, tenantId)
                .orElseThrow(() -> new ResourceNotFoundException("Warehouse", warehouseId));

        List<NxDockDoor> doors = dockDoorRepository.findByWarehouseId(warehouseId);
        List<NxYardLocation> yardLocations = yardLocationRepository.findByWarehouseId(warehouseId);
        List<NxAppointment> appointments = appointmentRepository.findByWarehouseIdAndStatusIn(warehouseId, ACTIVE_APPOINTMENT);
        List<NxTrailer> trailers = trailerRepository.findByWarehouseId(warehouseId);
        List<NxInventory> inventory = inventoryRepository.findByTenantIdAndNodeId(tenantId, warehouseId);
        List<NxShipment> shipments = shipmentRepository.findTop50ByTenantIdAndOriginNodeIdOrderByCreatedAtDesc(tenantId, warehouseId);

        Map<UUID, NxDockDoor> doorById = doors.stream().collect(Collectors.toMap(NxDockDoor::getId, Function.identity()));
        Map<UUID, NxYardLocation> yardById = yardLocations.stream().collect(Collectors.toMap(NxYardLocation::getId, Function.identity()));
        Map<UUID, NxAppointment> apptById = appointments.stream().collect(Collectors.toMap(NxAppointment::getId, Function.identity()));
        // A door/truck link may be recorded on either side; prefer the door's pointer, fall back
        // to an on-site appointment that names the door.
        Map<UUID, NxAppointment> onSiteByDoor = appointments.stream()
                .filter(a -> a.getDockDoorId() != null && ON_SITE_APPOINTMENT.contains(upper(a.getStatus())))
                .collect(Collectors.toMap(NxAppointment::getDockDoorId, Function.identity(), (a, b) -> a));

        List<Truck> trucks = trucks(appointments, trailers, doorById, yardById);
        LocalDate today = LocalDate.now(clock);

        return new Snapshot(
                summarize(wh, doors, onSiteByDoor.keySet()),
                joinAddress(wh),
                wh.getLatitude(), wh.getLongitude(),
                wh.getTotalCapacitySqm(), wh.getUsedCapacitySqm(),
                kpis(inventory, trucks, appointments, shipments, today),
                zoneRepository.findByWarehouseId(warehouseId).stream()
                        .map(z -> new Zone(z.getId(), z.getCode(), z.getName(), z.getZoneType(),
                                z.getCapacitySqm(), z.getUsedSqm(), z.getIsHazardous()))
                        .toList(),
                doors.stream()
                        .sorted(Comparator.comparing(NxDockDoor::getDoorNumber, Comparator.nullsLast(String::compareTo)))
                        .map(d -> {
                            NxAppointment at = Optional.ofNullable(apptById.get(d.getCurrentAppointmentId()))
                                    .orElse(onSiteByDoor.get(d.getId()));
                            return new Dock(d.getId(), d.getDoorNumber(), d.getDoorType(), d.getStatus(),
                                    at != null ? at.getId() : null, vehicleLabel(at));
                        })
                        .toList(),
                yardLocations.stream()
                        .map(y -> new YardSlot(y.getId(), y.getLocationCode(), y.getLocationType(), y.getStatus(),
                                y.getCapacity(), y.getCurrentOccupancy()))
                        .toList(),
                trucks,
                equipmentRepository.findByWarehouseId(warehouseId).stream()
                        .filter(e -> !Boolean.FALSE.equals(e.getIsActive()))
                        .map(e -> new Equipment(e.getId(), e.getCode(), e.getEquipmentType(), e.getModel(),
                                e.getStatus(), e.getBatteryLevel(), e.getAssignedTo() != null))
                        .toList(),
                topInventory(inventory),
                shipments.stream().limit(20).map(this::shipment).toList(),
                LocalDateTime.now(clock));
    }

    // ------------------------------------------------------------------ helpers

    private SiteSummary summarize(Warehouse wh, List<NxDockDoor> doors, Set<UUID> doorsWithOnSiteAppointment) {
        int occupied = (int) doors.stream()
                .filter(d -> d.getCurrentVehicleId() != null || d.getCurrentAppointmentId() != null
                        || "OCCUPIED".equalsIgnoreCase(d.getStatus()) || doorsWithOnSiteAppointment.contains(d.getId()))
                .count();
        return new SiteSummary(wh.getId(), wh.getCode(), wh.getName(), wh.getType(), wh.getCity(), wh.getState(),
                wh.getStatus(), fillPct(wh.getUsedCapacitySqm(), wh.getTotalCapacitySqm()), doors.size(), occupied);
    }

    static Double fillPct(BigDecimal used, BigDecimal total) {
        if (used == null || total == null || total.signum() <= 0) {
            return null;
        }
        return used.multiply(BigDecimal.valueOf(100)).divide(total, 1, RoundingMode.HALF_UP).doubleValue();
    }

    private List<Truck> trucks(List<NxAppointment> appointments, List<NxTrailer> trailers,
                               Map<UUID, NxDockDoor> doorById, Map<UUID, NxYardLocation> yardById) {
        List<Truck> trucks = new ArrayList<>();
        Set<String> trailerNumbersFromAppointments = new HashSet<>();
        for (NxAppointment a : appointments) {
            if (a.getTrailerNumber() != null) trailerNumbersFromAppointments.add(a.getTrailerNumber());
            NxDockDoor door = doorById.get(a.getDockDoorId());
            NxYardLocation yard = yardById.get(a.getYardLocationId());
            trucks.add(new Truck(a.getId(), "APPOINTMENT",
                    firstNonBlank(a.getTrailerNumber(), a.getVehicleLicensePlate(), a.getAppointmentNumber()),
                    firstNonBlank(a.getCarrierName(), a.getCarrierCode()),
                    direction(a.getType()), a.getStatus(),
                    door != null ? door.getDoorNumber() : null,
                    yard != null ? yard.getLocationCode() : null,
                    a.getEstimatedArrival(), a.getActualArrival(),
                    a.getPalletCount(), a.getWeightKg(), a.getDriverName(), a.getPoNumbers()));
        }
        // Trailers on site that are not already represented by an appointment.
        for (NxTrailer t : trailers) {
            if (t.getCheckedOutAt() != null || "CHECKED_OUT".equalsIgnoreCase(t.getStatus())
                    || trailerNumbersFromAppointments.contains(t.getTrailerNumber())) {
                continue;
            }
            NxDockDoor door = doorById.get(t.getCurrentDockDoorId());
            NxYardLocation yard = yardById.get(t.getCurrentYardLocationId());
            trucks.add(new Truck(t.getId(), "TRAILER", t.getTrailerNumber(), t.getCarrierCode(),
                    Boolean.TRUE.equals(t.getLoaded()) ? "OUTBOUND" : "INBOUND", t.getStatus(),
                    door != null ? door.getDoorNumber() : null,
                    yard != null ? yard.getLocationCode() : null,
                    null, t.getCheckedInAt(), t.getPalletCount(), null, null, null));
        }
        return trucks;
    }

    private Kpis kpis(List<NxInventory> inventory, List<Truck> trucks, List<NxAppointment> appointments,
                      List<NxShipment> shipments, LocalDate today) {
        long onHand = inventory.stream().mapToLong(i -> nz(i.getQuantityOnHand())).sum();
        int skuCount = (int) inventory.stream().map(NxInventory::getSku).filter(Objects::nonNull).distinct().count();
        List<Truck> onSite = trucks.stream()
                .filter(t -> "TRAILER".equals(t.source()) || ON_SITE_APPOINTMENT.contains(upper(t.status())))
                .toList();
        int inbound = (int) onSite.stream().filter(t -> "INBOUND".equals(t.direction())).count();
        int arrivalsToday = (int) appointments.stream()
                .filter(a -> a.getEstimatedArrival() != null && a.getEstimatedArrival().toLocalDate().equals(today))
                .count();
        int outboundToday = (int) shipments.stream()
                .filter(s -> s.getShippedAt() != null && s.getShippedAt().toLocalDate().equals(today))
                .count();

        LocalDateTime since = today.minusDays(30).atStartOfDay();
        List<NxShipment> delivered = shipments.stream()
                .filter(s -> s.getActualDelivery() != null && s.getEstimatedDelivery() != null
                        && s.getActualDelivery().isAfter(since))
                .toList();
        Double onTime = null;
        if (!delivered.isEmpty()) {
            long hits = delivered.stream().filter(s -> !s.getActualDelivery().isAfter(s.getEstimatedDelivery())).count();
            onTime = BigDecimal.valueOf(hits * 100.0 / delivered.size()).setScale(1, RoundingMode.HALF_UP).doubleValue();
        }
        return new Kpis(onHand, skuCount, onSite.size(), inbound, onSite.size() - inbound, arrivalsToday,
                outboundToday, onTime, delivered.isEmpty() ? null : delivered.size());
    }

    static List<StockLine> topInventory(List<NxInventory> inventory) {
        Map<String, long[]> bySku = new LinkedHashMap<>();
        Map<String, Long> threshold = new HashMap<>();
        for (NxInventory i : inventory) {
            if (i.getSku() == null) continue;
            long[] agg = bySku.computeIfAbsent(i.getSku(), k -> new long[2]);
            agg[0] += nz(i.getQuantityOnHand());
            agg[1] += nz(i.getQuantityAllocated()) + nz(i.getQuantityReserved());
            long t = Math.max(nz(i.getSafetyStock()), nz(i.getReorderPoint()));
            threshold.merge(i.getSku(), t, Long::sum);
        }
        return bySku.entrySet().stream()
                .sorted((a, b) -> Long.compare(b.getValue()[0], a.getValue()[0]))
                .limit(8)
                .map(e -> {
                    long onHand = e.getValue()[0];
                    long available = Math.max(0, onHand - e.getValue()[1]);
                    long t = threshold.getOrDefault(e.getKey(), 0L);
                    String status = available <= 0 ? "OUT_OF_STOCK" : (t > 0 && available <= t ? "LOW_STOCK" : "IN_STOCK");
                    return new StockLine(e.getKey(), onHand, e.getValue()[1], available, status);
                })
                .toList();
    }

    private Shipment shipment(NxShipment s) {
        return new Shipment(s.getId(), s.getOrderId(), s.getCarrierId(), s.getServiceLevel(), s.getTrackingNumber(),
                s.getStatus(), s.getCreatedAt(), s.getShippedAt(), s.getEstimatedDelivery(), s.getActualDelivery());
    }

    private static String vehicleLabel(NxAppointment a) {
        if (a == null) return null;
        return firstNonBlank(a.getTrailerNumber(), a.getVehicleLicensePlate(), a.getAppointmentNumber());
    }

    private static String direction(String appointmentType) {
        if (appointmentType == null) return "INBOUND";
        String t = appointmentType.toUpperCase();
        return t.contains("OUT") || t.contains("SHIP") || t.contains("PICKUP") ? "OUTBOUND" : "INBOUND";
    }

    private static String joinAddress(Warehouse wh) {
        return Arrays.asList(wh.getAddressLine1(), wh.getCity(), wh.getState(), wh.getZipCode()).stream()
                .filter(s -> s != null && !s.isBlank())
                .collect(Collectors.joining(", "));
    }

    private static String firstNonBlank(String... values) {
        for (String v : values) {
            if (v != null && !v.isBlank()) return v;
        }
        return null;
    }

    private static String upper(String s) {
        return s == null ? "" : s.toUpperCase();
    }

    private static long nz(Integer v) {
        return v == null ? 0 : v;
    }
}
