package com.nexus.oms.service;

import com.nexus.oms.entity.NxParkedOrder;
import com.nexus.oms.exception.BadRequestException;
import com.nexus.oms.exception.ResourceNotFoundException;
import com.nexus.oms.repository.ParkedOrderRepository;
import com.nexus.oms.security.TenantContext;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;

@Service
public class ParkedOrderService {

    private static final Logger log = LoggerFactory.getLogger(ParkedOrderService.class);

    private final ParkedOrderRepository parkedOrderRepository;

    public ParkedOrderService(ParkedOrderRepository parkedOrderRepository) {
        this.parkedOrderRepository = parkedOrderRepository;
    }

    @Transactional
    public NxParkedOrder parkOrder(NxParkedOrder parkedOrder) {
        UUID tenantId = TenantContext.getCurrentTenantId();
        parkedOrder.setTenantId(tenantId);

        NxParkedOrder existing = parkedOrderRepository.findByOrderId(parkedOrder.getOrderId());
        if (existing != null && "PARKED".equals(existing.getStatus())) {
            throw new BadRequestException("Order is already parked");
        }

        parkedOrder = parkedOrderRepository.save(parkedOrder);
        log.info("Parked order {} with reason {}", parkedOrder.getOrderNumber(), parkedOrder.getReason());
        return parkedOrder;
    }

    @Transactional
    public NxParkedOrder releaseOrder(UUID id, String releaseReason) {
        NxParkedOrder parkedOrder = parkedOrderRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("ParkedOrder", id));

        if (!"PARKED".equals(parkedOrder.getStatus())) {
            throw new BadRequestException("Order is not in PARKED status");
        }

        parkedOrder.setStatus("RELEASED");
        parkedOrder.setReleasedAt(LocalDateTime.now());
        parkedOrder = parkedOrderRepository.save(parkedOrder);

        log.info("Released parked order {} with reason {}", parkedOrder.getOrderNumber(), releaseReason);
        return parkedOrder;
    }

    @Transactional
    public NxParkedOrder cancelOrder(UUID id, String cancelReason) {
        NxParkedOrder parkedOrder = parkedOrderRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("ParkedOrder", id));

        parkedOrder.setStatus("CANCELLED");
        parkedOrder.setReleasedAt(LocalDateTime.now());
        parkedOrder = parkedOrderRepository.save(parkedOrder);

        log.info("Cancelled parked order {} with reason {}", parkedOrder.getOrderNumber(), cancelReason);
        return parkedOrder;
    }

    public List<NxParkedOrder> getParkedOrders(UUID tenantId) {
        return parkedOrderRepository.findByTenantIdAndStatus(tenantId, "PARKED");
    }

    public List<NxParkedOrder> getParkedOrdersByReason(UUID tenantId, String reason) {
        return parkedOrderRepository.findByTenantIdAndReason(tenantId, reason);
    }

    public NxParkedOrder getParkedOrder(UUID id) {
        return parkedOrderRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("ParkedOrder", id));
    }

    public NxParkedOrder getParkedOrderByOrderId(UUID orderId) {
        return parkedOrderRepository.findByOrderId(orderId);
    }

    public List<NxParkedOrder> getParkedOrdersBySku(UUID tenantId, String sku) {
        return parkedOrderRepository.findByTenantIdAndSku(tenantId, sku);
    }

    @Transactional
    public NxParkedOrder updatePriority(UUID id, Integer newPriority) {
        NxParkedOrder parkedOrder = parkedOrderRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("ParkedOrder", id));

        parkedOrder.setPriority(newPriority);
        return parkedOrderRepository.save(parkedOrder);
    }

    @Transactional
    public NxParkedOrder updateNotes(UUID id, String notes) {
        NxParkedOrder parkedOrder = parkedOrderRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("ParkedOrder", id));

        parkedOrder.setNotes(notes);
        return parkedOrderRepository.save(parkedOrder);
    }

    public long countByReason(UUID tenantId, String reason) {
        return parkedOrderRepository.findByTenantIdAndReason(tenantId, reason).size();
    }
}
