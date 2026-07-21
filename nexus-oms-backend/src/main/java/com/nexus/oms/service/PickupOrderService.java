package com.nexus.oms.service;

import com.nexus.oms.entity.NxPickupOrder;
import com.nexus.oms.entity.NxPickupOrderItem;
import com.nexus.oms.entity.NxProofOfDelivery;
import com.nexus.oms.exception.BadRequestException;
import com.nexus.oms.exception.ResourceNotFoundException;
import com.nexus.oms.repository.PickupOrderItemRepository;
import com.nexus.oms.repository.PickupOrderRepository;
import com.nexus.oms.repository.ProofOfDeliveryRepository;
import com.nexus.oms.security.TenantContext;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.*;

@Service
public class PickupOrderService {

    private static final Logger log = LoggerFactory.getLogger(PickupOrderService.class);

    private final PickupOrderRepository pickupOrderRepository;
    private final PickupOrderItemRepository pickupOrderItemRepository;
    private final ProofOfDeliveryRepository proofOfDeliveryRepository;

    public PickupOrderService(PickupOrderRepository pickupOrderRepository,
                              PickupOrderItemRepository pickupOrderItemRepository,
                              ProofOfDeliveryRepository proofOfDeliveryRepository) {
        this.pickupOrderRepository = pickupOrderRepository;
        this.pickupOrderItemRepository = pickupOrderItemRepository;
        this.proofOfDeliveryRepository = proofOfDeliveryRepository;
    }

    // ─── Order Retrieval ───────────────────────────────────────────────────

    public NxPickupOrder getPickupOrder(UUID id) {
        return pickupOrderRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("PickupOrder", id));
    }

    public List<NxPickupOrder> getPendingPickups(UUID nodeId) {
        return pickupOrderRepository.findPendingPickupsByNode(nodeId);
    }

    public List<NxPickupOrder> getPickupsByPicker(UUID pickerId) {
        return pickupOrderRepository.findByPickerId(pickerId);
    }

    public List<NxPickupOrder> getReadyForHandoff() {
        UUID tenantId = TenantContext.getCurrentTenantId();
        return pickupOrderRepository.findReadyForHandoff(tenantId);
    }

    public List<NxPickupOrderItem> getPickupItems(UUID pickupOrderId) {
        return pickupOrderItemRepository.findByPickupOrderId(pickupOrderId);
    }

    public NxPickupOrder getByPickupCode(String pickupCode) {
        return pickupOrderRepository.findByPickupCode(pickupCode);
    }

    // ─── Picking Workflow ──────────────────────────────────────────────────

    @Transactional
    public NxPickupOrder assignPicker(UUID pickupOrderId, UUID pickerId, String pickerName) {
        NxPickupOrder order = pickupOrderRepository.findById(pickupOrderId)
                .orElseThrow(() -> new ResourceNotFoundException("PickupOrder", pickupOrderId));

        if (!"PENDING".equals(order.getStatus())) {
            throw new BadRequestException("Order is not in PENDING status");
        }

        order.setPickerId(pickerId);
        order.setPickerName(pickerName);
        order.setStatus("PICKING");
        order = pickupOrderRepository.save(order);

        log.info("Assigned picker {} to pickup order {}", pickerName, order.getOrderNumber());
        return order;
    }

    @Transactional
    public NxPickupOrder startPicking(UUID pickupOrderId) {
        NxPickupOrder order = pickupOrderRepository.findById(pickupOrderId)
                .orElseThrow(() -> new ResourceNotFoundException("PickupOrder", pickupOrderId));

        if (!"PICKING".equals(order.getStatus())) {
            throw new BadRequestException("Order is not in PICKING status");
        }

        order.setStatus("PICKING");
        order.setPickedAt(LocalDateTime.now());
        return pickupOrderRepository.save(order);
    }

    @Transactional
    public NxPickupOrderItem pickItem(UUID itemId, Integer pickedQuantity, String notes) {
        NxPickupOrderItem item = pickupOrderItemRepository.findById(itemId)
                .orElseThrow(() -> new ResourceNotFoundException("PickupOrderItem", itemId));

        if (pickedQuantity > item.getQuantity()) {
            throw new BadRequestException("Cannot pick more than ordered quantity");
        }

        item.setPickedQuantity(pickedQuantity);
        item.setNotes(notes);
        item.setStatus(pickedQuantity.equals(item.getQuantity()) ? "PICKED" : "SHORT");
        return pickupOrderItemRepository.save(item);
    }

    @Transactional
    public NxPickupOrderItem substituteItem(UUID itemId, String substitutedSku, Integer quantity) {
        NxPickupOrderItem item = pickupOrderItemRepository.findById(itemId)
                .orElseThrow(() -> new ResourceNotFoundException("PickupOrderItem", itemId));

        item.setSubstitutedSku(substitutedSku);
        item.setPickedQuantity(quantity);
        item.setStatus("SUBSTITUTED");
        return pickupOrderItemRepository.save(item);
    }

    @Transactional
    public NxPickupOrder completePicking(UUID pickupOrderId) {
        NxPickupOrder order = pickupOrderRepository.findById(pickupOrderId)
                .orElseThrow(() -> new ResourceNotFoundException("PickupOrder", pickupOrderId));

        List<NxPickupOrderItem> items = pickupOrderItemRepository.findByPickupOrderId(pickupOrderId);
        boolean allPicked = items.stream().allMatch(i -> i.getPickedQuantity() > 0);

        if (!allPicked) {
            throw new BadRequestException("Not all items have been picked");
        }

        order.setStatus("PICKED");
        order.setPickedAt(LocalDateTime.now());
        return pickupOrderRepository.save(order);
    }

    @Transactional
    public NxPickupOrder packOrder(UUID pickupOrderId) {
        NxPickupOrder order = pickupOrderRepository.findById(pickupOrderId)
                .orElseThrow(() -> new ResourceNotFoundException("PickupOrder", pickupOrderId));

        if (!"PICKED".equals(order.getStatus())) {
            throw new BadRequestException("Order must be PICKED before packing");
        }

        order.setStatus("PACKED");
        order.setPackedAt(LocalDateTime.now());
        return pickupOrderRepository.save(order);
    }

    @Transactional
    public NxPickupOrder markReadyForHandoff(UUID pickupOrderId) {
        NxPickupOrder order = pickupOrderRepository.findById(pickupOrderId)
                .orElseThrow(() -> new ResourceNotFoundException("PickupOrder", pickupOrderId));

        if (!"PACKED".equals(order.getStatus())) {
            throw new BadRequestException("Order must be PACKED before marking ready");
        }

        order.setStatus("READY_FOR_HANDOFF");
        order.setReadyAt(LocalDateTime.now());
        return pickupOrderRepository.save(order);
    }

    @Transactional
    public NxPickupOrder handoffOrder(UUID pickupOrderId) {
        NxPickupOrder order = pickupOrderRepository.findById(pickupOrderId)
                .orElseThrow(() -> new ResourceNotFoundException("PickupOrder", pickupOrderId));

        if (!"READY_FOR_HANDOFF".equals(order.getStatus())) {
            throw new BadRequestException("Order is not ready for handoff");
        }

        order.setStatus("HANDED_OFF");
        order.setHandedOffAt(LocalDateTime.now());
        return pickupOrderRepository.save(order);
    }

    // ─── Proof of Delivery ─────────────────────────────────────────────────

    @Transactional
    public NxProofOfDelivery collectOrder(UUID pickupOrderId, NxProofOfDelivery pod) {
        NxPickupOrder order = pickupOrderRepository.findById(pickupOrderId)
                .orElseThrow(() -> new ResourceNotFoundException("PickupOrder", pickupOrderId));

        UUID tenantId = TenantContext.getCurrentTenantId();
        pod.setTenantId(tenantId);
        pod.setPickupOrderId(pickupOrderId);
        pod.setOrderNumber(order.getOrderNumber());
        pod.setCollectedAt(LocalDateTime.now());
        pod = proofOfDeliveryRepository.save(pod);

        order.setStatus("POD_COLLECTED");
        order.setCollectedAt(LocalDateTime.now());
        pickupOrderRepository.save(order);

        log.info("Collected pickup order {} with POD", order.getOrderNumber());
        return pod;
    }

    public NxProofOfDelivery getPOD(UUID pickupOrderId) {
        return proofOfDeliveryRepository.findByPickupOrderId(pickupOrderId);
    }

    // ─── Status Counts ─────────────────────────────────────────────────────

    public Map<String, Long> getStatusCounts(UUID nodeId) {
        List<NxPickupOrder> all = pickupOrderRepository.findByNodeIdAndStatus(nodeId, null);
        Map<String, Long> counts = new LinkedHashMap<>();
        counts.put("total", (long) all.size());
        counts.put("pending", (long) pickupOrderRepository.findByNodeIdAndStatus(nodeId, "PENDING").size());
        counts.put("picking", (long) pickupOrderRepository.findByNodeIdAndStatus(nodeId, "PICKING").size());
        counts.put("ready", (long) pickupOrderRepository.findByNodeIdAndStatus(nodeId, "READY_FOR_HANDOFF").size());
        counts.put("collected", (long) pickupOrderRepository.findByNodeIdAndStatus(nodeId, "POD_COLLECTED").size());
        return counts;
    }
}
