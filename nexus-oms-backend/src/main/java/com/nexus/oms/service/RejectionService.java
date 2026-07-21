package com.nexus.oms.service;

import com.nexus.oms.entity.NxOrderRejection;
import com.nexus.oms.entity.NxRejectionReason;
import com.nexus.oms.exception.BadRequestException;
import com.nexus.oms.exception.ResourceNotFoundException;
import com.nexus.oms.repository.OrderRejectionRepository;
import com.nexus.oms.repository.RejectionReasonRepository;
import com.nexus.oms.security.TenantContext;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.*;

@Service
public class RejectionService {

    private static final Logger log = LoggerFactory.getLogger(RejectionService.class);

    private final RejectionReasonRepository rejectionReasonRepository;
    private final OrderRejectionRepository orderRejectionRepository;

    public RejectionService(RejectionReasonRepository rejectionReasonRepository,
                            OrderRejectionRepository orderRejectionRepository) {
        this.rejectionReasonRepository = rejectionReasonRepository;
        this.orderRejectionRepository = orderRejectionRepository;
    }

    // ─── Rejection Reasons ─────────────────────────────────────────────────

    @Transactional
    public NxRejectionReason createReason(NxRejectionReason reason) {
        UUID tenantId = TenantContext.getCurrentTenantId();
        reason.setTenantId(tenantId);
        return rejectionReasonRepository.save(reason);
    }

    @Transactional
    public NxRejectionReason updateReason(UUID id, NxRejectionReason updates) {
        NxRejectionReason reason = rejectionReasonRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("RejectionReason", id));
        if (updates.getLabel() != null) reason.setLabel(updates.getLabel());
        if (updates.getDescription() != null) reason.setDescription(updates.getDescription());
        if (updates.getCategory() != null) reason.setCategory(updates.getCategory());
        if (updates.getInventoryImpact() != null) reason.setInventoryImpact(updates.getInventoryImpact());
        if (updates.getRequiresPhoto() != null) reason.setRequiresPhoto(updates.getRequiresPhoto());
        if (updates.getRequiresNotes() != null) reason.setRequiresNotes(updates.getRequiresNotes());
        if (updates.getActive() != null) reason.setActive(updates.getActive());
        if (updates.getSortOrder() != null) reason.setSortOrder(updates.getSortOrder());
        return rejectionReasonRepository.save(reason);
    }

    public List<NxRejectionReason> getReasons() {
        UUID tenantId = TenantContext.getCurrentTenantId();
        return rejectionReasonRepository.findByTenantIdAndActiveTrue(tenantId);
    }

    public List<NxRejectionReason> getReasonsByCategory(String category) {
        UUID tenantId = TenantContext.getCurrentTenantId();
        return rejectionReasonRepository.findByTenantIdAndCategory(tenantId, category);
    }

    public NxRejectionReason getReason(UUID id) {
        return rejectionReasonRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("RejectionReason", id));
    }

    @Transactional
    public void deleteReason(UUID id) {
        rejectionReasonRepository.deleteById(id);
    }

    // ─── Order Rejections ──────────────────────────────────────────────────

    @Transactional
    public NxOrderRejection rejectItem(NxOrderRejection rejection) {
        UUID tenantId = TenantContext.getCurrentTenantId();
        rejection.setTenantId(tenantId);

        UUID rejectionReasonId = rejection.getRejectionReasonId();
        NxRejectionReason reason = rejectionReasonRepository.findById(rejectionReasonId)
                .orElseThrow(() -> new ResourceNotFoundException("RejectionReason", rejectionReasonId));

        if (reason.getRequiresPhoto() && (rejection.getPhotoPath() == null || rejection.getPhotoPath().isBlank())) {
            throw new BadRequestException("Photo is required for this rejection reason");
        }
        if (reason.getRequiresNotes() && (rejection.getNotes() == null || rejection.getNotes().isBlank())) {
            throw new BadRequestException("Notes are required for this rejection reason");
        }

        rejection.setRejectionCode(reason.getCode());
        rejection.setInventoryAction(reason.getInventoryImpact());
        rejection = orderRejectionRepository.save(rejection);

        log.info("Rejected item {} for order {}: reason={}, qty={}",
                rejection.getSku(), rejection.getOrderNumber(), reason.getCode(), rejection.getQuantity());
        return rejection;
    }

    @Transactional
    public NxOrderRejection processRejection(UUID id) {
        NxOrderRejection rejection = orderRejectionRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("OrderRejection", id));

        if (!"PENDING".equals(rejection.getStatus())) {
            throw new BadRequestException("Rejection is not in PENDING status");
        }

        rejection.setStatus("PROCESSED");
        rejection.setInventoryAdjusted(true);
        rejection.setProcessedAt(LocalDateTime.now());
        return orderRejectionRepository.save(rejection);
    }

    public List<NxOrderRejection> getRejectionsByOrder(UUID orderId) {
        return orderRejectionRepository.findByOrderId(orderId);
    }

    public List<NxOrderRejection> getPendingRejections() {
        UUID tenantId = TenantContext.getCurrentTenantId();
        return orderRejectionRepository.findByTenantIdAndStatus(tenantId, "PENDING");
    }

    public List<NxOrderRejection> getAllRejections() {
        UUID tenantId = TenantContext.getCurrentTenantId();
        return orderRejectionRepository.findByTenantId(tenantId);
    }

    public Map<String, Object> getRejectionStats(UUID tenantId) {
        List<NxOrderRejection> all = orderRejectionRepository.findByTenantId(tenantId);
        long pending = all.stream().filter(r -> "PENDING".equals(r.getStatus())).count();
        long processed = all.stream().filter(r -> "PROCESSED".equals(r.getStatus())).count();

        Map<String, Long> byReason = new LinkedHashMap<>();
        for (NxOrderRejection r : all) {
            byReason.merge(r.getRejectionCode(), (long) r.getQuantity(), Long::sum);
        }

        Map<String, Object> stats = new LinkedHashMap<>();
        stats.put("total", all.size());
        stats.put("pending", pending);
        stats.put("processed", processed);
        stats.put("byReason", byReason);
        return stats;
    }
}
