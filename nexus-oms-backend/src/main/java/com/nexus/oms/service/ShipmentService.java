package com.nexus.oms.service;

import com.nexus.oms.entity.NxShipment;
import com.nexus.oms.exception.ResourceNotFoundException;
import com.nexus.oms.repository.ShipmentRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.UUID;

@Service
public class ShipmentService {

    private final ShipmentRepository shipmentRepository;

    public ShipmentService(ShipmentRepository shipmentRepository) {
        this.shipmentRepository = shipmentRepository;
    }

    public List<NxShipment> getShipmentsByTenant(UUID tenantId) {
        return shipmentRepository.findByTenantId(tenantId);
    }

    public NxShipment getShipment(UUID id) {
        return shipmentRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Shipment", id));
    }

    public NxShipment getByTracking(String trackingNumber) {
        return shipmentRepository.findByTrackingNumber(trackingNumber)
                .orElseThrow(() -> new ResourceNotFoundException("Shipment with tracking", trackingNumber));
    }

    @Transactional
    public NxShipment voidShipment(UUID id) {
        NxShipment shipment = shipmentRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Shipment", id));
        shipment.setVoided(true);
        shipment.setStatus("VOIDED");
        return shipmentRepository.save(shipment);
    }
}
