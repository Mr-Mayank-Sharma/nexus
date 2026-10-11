package com.nexus.oms.service;

import com.nexus.oms.entity.NxShipment;
import com.nexus.oms.exception.ResourceNotFoundException;
import com.nexus.oms.domain.OrderStatusPolicy;
import com.nexus.oms.repository.OrderRepository;
import com.nexus.oms.repository.ShipmentRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.*;

@Service
public class ShipmentServiceEnhanced {

    private final ShipmentRepository shipmentRepository;
    private final OrderRepository orderRepository;
    private final OrderStockCommit orderStockCommit;

    public ShipmentServiceEnhanced(ShipmentRepository shipmentRepository, OrderRepository orderRepository,
                                   OrderStockCommit orderStockCommit) {
        this.shipmentRepository = shipmentRepository;
        this.orderRepository = orderRepository;
        this.orderStockCommit = orderStockCommit;
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
        NxShipment saved = shipmentRepository.save(s);
        // A shipped parcel means the order has shipped and its stock has left: marking a shipment
        // shipped used to leave the order's status and the stock untouched.
        if (s.getOrderId() != null) {
            orderRepository.findById(s.getOrderId()).ifPresent(order -> {
                if (!"SHIPPED".equals(order.getStatus()) && !"DELIVERED".equals(order.getStatus())
                        && OrderStatusPolicy.transitionIfAllowed(order, OrderStatusPolicy.SHIPPED, "ShipmentService")) {
                    order.setShippedAt(LocalDateTime.now());
                    if (s.getTrackingNumber() != null) order.setTrackingNumber(s.getTrackingNumber());
                    orderRepository.save(order);
                }
                orderStockCommit.consumeOnShip(order);
            });
        }
        return saved;
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
