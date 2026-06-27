package com.nexus.oms.service;

import com.nexus.oms.entity.NxShipment;
import com.nexus.oms.exception.ResourceNotFoundException;
import com.nexus.oms.repository.ShipmentRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.*;

@Service
public class ShipmentServiceEnhanced {

    private final ShipmentRepository shipmentRepository;

    public ShipmentServiceEnhanced(ShipmentRepository shipmentRepository) {
        this.shipmentRepository = shipmentRepository;
    }

    public List<NxShipment> getShipments(UUID tenantId) {
        return shipmentRepository.findByTenantId(tenantId);
    }

    public List<NxShipment> getShipmentsByOrder(UUID orderId) {
        return shipmentRepository.findByOrderId(orderId);
    }

    public NxShipment getShipment(UUID id) {
        return shipmentRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Shipment", id));
    }

    @Transactional
    public NxShipment createShipment(NxShipment shipment) {
        shipment.setStatus("PENDING");
        shipment.setVoided(false);
        return shipmentRepository.save(shipment);
    }

    @Transactional
    public NxShipment updateTracking(UUID shipmentId, String trackingNumber, String carrierId, String serviceLevel) {
        NxShipment s = getShipment(shipmentId);
        s.setTrackingNumber(trackingNumber);
        s.setCarrierId(carrierId);
        s.setServiceLevel(serviceLevel);
        s.setStatus("LABELED");
        return shipmentRepository.save(s);
    }

    @Transactional
    public NxShipment markShipped(UUID shipmentId) {
        NxShipment s = getShipment(shipmentId);
        s.setStatus("SHIPPED");
        s.setShippedAt(LocalDateTime.now());
        return shipmentRepository.save(s);
    }

    @Transactional
    public NxShipment markDelivered(UUID shipmentId) {
        NxShipment s = getShipment(shipmentId);
        s.setStatus("DELIVERED");
        s.setActualDelivery(LocalDateTime.now());
        return shipmentRepository.save(s);
    }

    @Transactional
    public NxShipment voidShipment(UUID id) {
        NxShipment s = getShipment(id);
        s.setVoided(true);
        s.setStatus("VOIDED");
        return shipmentRepository.save(s);
    }

    public Map<String, Object> getDashboardKPIs(UUID tenantId) {
        List<NxShipment> all = shipmentRepository.findByTenantId(tenantId);
        long pending = all.stream().filter(s -> "PENDING".equals(s.getStatus())).count();
        long shipped = all.stream().filter(s -> "SHIPPED".equals(s.getStatus())).count();
        long inTransit = all.stream().filter(s -> "IN_TRANSIT".equals(s.getStatus())).count();
        long delivered = all.stream().filter(s -> "DELIVERED".equals(s.getStatus())).count();
        long exceptions = all.stream().filter(s -> "EXCEPTION".equals(s.getStatus())).count();

        Map<String, Object> kpis = new HashMap<>();
        kpis.put("pending", pending);
        kpis.put("shipped", shipped);
        kpis.put("inTransit", inTransit);
        kpis.put("delivered", delivered);
        kpis.put("exceptions", exceptions);
        kpis.put("total", all.size());
        return kpis;
    }
}
