package com.nexus.oms.service;

import com.nexus.oms.entity.NxOrderRejection;
import com.nexus.oms.entity.NxRejectionReason;
import com.nexus.oms.exception.BadRequestException;
import com.nexus.oms.exception.ResourceNotFoundException;
import com.nexus.oms.repository.OrderAllocationRepository;
import com.nexus.oms.repository.OrderRepository;
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
    private final OrderRoutingService routingService;
    private final InventoryService inventoryService;
    private final OrderAllocationRepository allocationRepository;
    private final OrderRepository orderRepository;

    public RejectionService(RejectionReasonRepository rejectionReasonRepository,
                            OrderRejectionRepository orderRejectionRepository,
                            OrderRoutingService routingService,
                            InventoryService inventoryService,
                            OrderAllocationRepository allocationRepository,
                            OrderRepository orderRepository) {
        this.rejectionReasonRepository = rejectionReasonRepository;
        this.orderRejectionRepository = orderRejectionRepository;
        this.routingService = routingService;
        this.inventoryService = inventoryService;
        this.allocationRepository = allocationRepository;
        this.orderRepository = orderRepository;
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

    /**
     * A store says it cannot ship part of an order. In one transaction this: records the rejection;
     * re-routes the order so it does not become a split shipment (see
     * {@link OrderRoutingService#rerouteAfterRejection}); and, unless the reason says the units are fine,
     * holds the rejected units out of available-to-promise at that store. On-hand is not changed here:
     * a write-off needs a manager to {@link #processRejection process} it.
     */
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
        if (rejection.getQuantity() == null || rejection.getQuantity() <= 0) {
            throw new BadRequestException("Rejected quantity must be at least 1");
        }

        // The order is looked up, not trusted: it must be this tenant's, and its number comes from it.
        final UUID orderId = rejection.getOrderId();
        var order = orderRepository.findById(orderId)
                .filter(o -> tenantId.equals(o.getTenantId()))
                .orElseThrow(() -> new ResourceNotFoundException("Order", orderId));
        if (rejection.getOrderNumber() == null || rejection.getOrderNumber().isBlank()) {
            rejection.setOrderNumber(order.getChannelOrderId() != null ? order.getChannelOrderId()
                    : order.getExternalId() != null ? order.getExternalId() : order.getId().toString().substring(0, 8));
        }
        if (rejection.getRejectedBy() == null || rejection.getRejectedBy().isBlank()) {
            rejection.setRejectedBy(TenantContext.getCurrentUsername());
        }

        UUID nodeId = resolveRejectingNode(rejection);
        rejection.setNodeId(nodeId);
        rejection.setRejectionCode(reason.getCode());
        rejection.setInventoryAction(reason.getInventoryImpact());
        rejection = orderRejectionRepository.save(rejection);

        // Stores that already rejected this order must not get it back.
        Set<UUID> alreadyRejected = new HashSet<>();
        for (NxOrderRejection earlier : orderRejectionRepository.findByOrderId(rejection.getOrderId())) {
            if (earlier.getNodeId() != null) alreadyRejected.add(earlier.getNodeId());
        }

        OrderRoutingService.RerouteResult reroute = routingService.rerouteAfterRejection(
                rejection.getOrderId(), nodeId, rejection.getSku(), rejection.getQuantity(), alreadyRejected);
        rejection.setRerouteOutcome(reroute.outcome());
        rejection.setRerouteNote(reroute.note());

        // Order matters: the reservation is released by the re-route first, so the hold below acts on
        // the units that are really on the shelf, not on ones still promised to this order.
        if (!"RESTOCK".equals(reason.getInventoryImpact())) {
            int held = inventoryService.holdUnits(tenantId, rejection.getSku(), nodeId, rejection.getQuantity());
            if (held < rejection.getQuantity()) {
                rejection.setRerouteNote(append(rejection.getRerouteNote(), "Only " + held + " of "
                        + rejection.getQuantity() + " units could be held; the rest were already unavailable."));
            }
        }
        rejection = orderRejectionRepository.save(rejection);

        log.info("Rejected item {} for order {}: reason={}, qty={}, node={}, outcome={}",
                rejection.getSku(), rejection.getOrderNumber(), reason.getCode(), rejection.getQuantity(), nodeId, reroute.outcome());
        return rejection;
    }

    /**
     * The manager's decision on a rejection. A write-off ({@code DAMAGE_WRITE_OFF}, {@code RETURN_TO_VENDOR})
     * now really takes the held units off on-hand, with a ledger line. {@code QUARANTINE} leaves the units
     * held for a cycle count, and {@code RESTOCK} has nothing to do. {@code inventoryAdjusted} is true only
     * when on-hand actually changed, because it used to be set to true without anything having moved.
     */
    @Transactional
    public NxOrderRejection processRejection(UUID id) {
        NxOrderRejection rejection = orderRejectionRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("OrderRejection", id));

        if (!"PENDING".equals(rejection.getStatus())) {
            throw new BadRequestException("Rejection is not in PENDING status");
        }

        boolean adjusted = false;
        String action = rejection.getInventoryAction();
        if (("DAMAGE_WRITE_OFF".equals(action) || "RETURN_TO_VENDOR".equals(action)) && rejection.getNodeId() != null) {
            int off = inventoryService.writeOffHeldUnits(rejection.getTenantId(), rejection.getSku(), rejection.getNodeId(),
                    rejection.getQuantity(), "REJECTION", rejection.getId().toString(), "Rejected at pick: " + rejection.getRejectionCode());
            adjusted = off > 0;
        }

        rejection.setStatus("PROCESSED");
        rejection.setInventoryAdjusted(adjusted);
        rejection.setProcessedAt(LocalDateTime.now());
        return orderRejectionRepository.save(rejection);
    }

    /** The store is named by the caller, or inferred when exactly one allocation of the order holds the SKU. */
    private UUID resolveRejectingNode(NxOrderRejection rejection) {
        if (rejection.getNodeId() != null) return rejection.getNodeId();
        Set<UUID> holders = new LinkedHashSet<>();
        for (var allocation : allocationRepository.findByOrderId(rejection.getOrderId())) {
            if (!"ALLOCATED".equals(allocation.getStatus()) || allocation.getNodeId() == null) continue;
            if (OrderRoutingService.skuQuantitiesOf(allocation).getOrDefault(rejection.getSku(), 0) > 0) {
                holders.add(allocation.getNodeId());
            }
        }
        if (holders.size() == 1) return holders.iterator().next();
        throw new BadRequestException(holders.isEmpty()
                ? "No store is currently allocated " + rejection.getSku() + " on this order"
                : rejection.getSku() + " is split across " + holders.size() + " stores on this order; say which store is rejecting (nodeId)");
    }

    private static String append(String existing, String more) {
        return existing == null || existing.isBlank() ? more : existing + " " + more;
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
