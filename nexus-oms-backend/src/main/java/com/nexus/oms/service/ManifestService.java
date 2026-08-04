package com.nexus.oms.service;

import com.nexus.oms.entity.NxManifest;
import com.nexus.oms.entity.NxManifestShipment;
import com.nexus.oms.exception.ResourceNotFoundException;
import com.nexus.oms.repository.ManifestRepository;
import com.nexus.oms.repository.ManifestShipmentRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.*;
import java.util.stream.Collectors;

@Service
public class ManifestService {

    private final ManifestRepository manifestRepository;
    private final ManifestShipmentRepository shipmentRepository;

    public ManifestService(ManifestRepository manifestRepository,
                           ManifestShipmentRepository shipmentRepository) {
        this.manifestRepository = manifestRepository;
        this.shipmentRepository = shipmentRepository;
    }

    public List<Map<String, Object>> getManifests(UUID tenantId) {
        return manifestRepository.findByTenantIdOrderByCreatedAtDesc(tenantId).stream()
                .map(m -> toResponse(m, shipmentRepository.findByManifestId(m.getId())))
                .collect(Collectors.toList());
    }

    @Transactional
    public Map<String, Object> createManifest(UUID tenantId, Map<String, Object> request) {
        String carrier = request.get("carrier") != null ? String.valueOf(request.get("carrier")) : null;
        String date = request.get("date") != null ? String.valueOf(request.get("date")) : null;

        NxManifest manifest = NxManifest.builder()
                .tenantId(tenantId)
                .carrier(carrier)
                .manifestDate(date)
                .status("Draft")
                .totalWeight(BigDecimal.ZERO)
                .totalCost(BigDecimal.ZERO)
                .build();
        manifest = manifestRepository.save(manifest);

        BigDecimal totalWeight = BigDecimal.ZERO;
        BigDecimal totalCost = BigDecimal.ZERO;

        Object rawShipments = request.get("shipments");
        if (rawShipments instanceof List<?> list) {
            for (Object o : list) {
                if (!(o instanceof Map<?, ?> map)) continue;
                NxManifestShipment shipment = toShipment(tenantId, manifest.getId(), map);
                shipmentRepository.save(shipment);
                if (shipment.getWeight() != null) totalWeight = totalWeight.add(shipment.getWeight());
                if (shipment.getCost() != null) totalCost = totalCost.add(shipment.getCost());
            }
        }

        manifest.setTotalWeight(totalWeight);
        manifest.setTotalCost(totalCost);
        manifest = manifestRepository.save(manifest);

        return toResponse(manifest, shipmentRepository.findByManifestId(manifest.getId()));
    }

    @Transactional
    public Map<String, Object> updateManifest(UUID id, Map<String, Object> updates) {
        NxManifest manifest = manifestRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Manifest", id));

        if (updates.containsKey("status")) manifest.setStatus(String.valueOf(updates.get("status")));
        if (updates.containsKey("bolNumber")) manifest.setBolNumber(String.valueOf(updates.get("bolNumber")));
        if (updates.containsKey("carrier")) manifest.setCarrier(String.valueOf(updates.get("carrier")));
        if (updates.containsKey("date")) manifest.setManifestDate(String.valueOf(updates.get("date")));

        manifest = manifestRepository.save(manifest);
        return toResponse(manifest, shipmentRepository.findByManifestId(manifest.getId()));
    }

    private NxManifestShipment toShipment(UUID tenantId, UUID manifestId, Map<?, ?> map) {
        return NxManifestShipment.builder()
                .manifestId(manifestId)
                .tenantId(tenantId)
                .orderId(nullSafeString(map.get("orderId")))
                .trackingNumber(nullSafeString(map.get("tracking")))
                .service(nullSafeString(map.get("service")))
                .status(nullSafeString(map.get("status")))
                .weight(toBigDecimal(map.get("weight")))
                .cost(toBigDecimal(map.get("cost")))
                .destination(nullSafeString(map.get("destination")))
                .build();
    }

    private Map<String, Object> toResponse(NxManifest manifest, List<NxManifestShipment> shipments) {
        Map<String, Object> m = new HashMap<>();
        m.put("id", manifest.getId());
        m.put("carrier", manifest.getCarrier());
        m.put("date", manifest.getManifestDate());
        m.put("bolNumber", manifest.getBolNumber());
        m.put("status", manifest.getStatus());
        m.put("totalWeight", manifest.getTotalWeight());
        m.put("totalCost", manifest.getTotalCost());
        m.put("createdAt", manifest.getCreatedAt());
        m.put("updatedAt", manifest.getUpdatedAt());
        m.put("shipments", shipments.stream().map(this::shipmentToMap).collect(Collectors.toList()));
        return m;
    }

    private Map<String, Object> shipmentToMap(NxManifestShipment s) {
        Map<String, Object> m = new HashMap<>();
        m.put("id", s.getId());
        m.put("orderId", s.getOrderId());
        m.put("tracking", s.getTrackingNumber());
        m.put("service", s.getService());
        m.put("status", s.getStatus());
        m.put("weight", s.getWeight());
        m.put("cost", s.getCost());
        m.put("destination", s.getDestination());
        return m;
    }

    private String nullSafeString(Object o) {
        return o != null ? String.valueOf(o) : null;
    }

    private BigDecimal toBigDecimal(Object o) {
        if (o == null) return null;
        try {
            return new BigDecimal(String.valueOf(o));
        } catch (NumberFormatException e) {
            return null;
        }
    }
}
