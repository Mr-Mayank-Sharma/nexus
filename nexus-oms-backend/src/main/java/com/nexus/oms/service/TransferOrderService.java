package com.nexus.oms.service;

import com.nexus.oms.dto.TransferOrderRequest;
import com.nexus.oms.entity.NxTransferOrder;
import com.nexus.oms.entity.NxTransferOrderItem;
import com.nexus.oms.exception.BadRequestException;
import com.nexus.oms.exception.ResourceNotFoundException;
import com.nexus.oms.repository.TransferOrderRepository;
import com.nexus.oms.repository.TransferOrderItemRepository;
import com.nexus.oms.security.TenantContext;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.*;
import java.util.stream.Collectors;

@Service
public class TransferOrderService {

    private static final Logger log = LoggerFactory.getLogger(TransferOrderService.class);

    private final TransferOrderRepository transferOrderRepository;
    private final TransferOrderItemRepository transferOrderItemRepository;

    public TransferOrderService(TransferOrderRepository transferOrderRepository,
                                 TransferOrderItemRepository transferOrderItemRepository) {
        this.transferOrderRepository = transferOrderRepository;
        this.transferOrderItemRepository = transferOrderItemRepository;
    }

    public List<NxTransferOrder> getTransferOrders(UUID tenantId, String status) {
        if (status != null && !status.isBlank()) {
            return transferOrderRepository.findByTenantIdAndStatus(tenantId, status.toUpperCase());
        }
        return transferOrderRepository.findByTenantId(tenantId);
    }

    public NxTransferOrder getTransferOrder(UUID id) {
        return transferOrderRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("TransferOrder", id));
    }

    public List<NxTransferOrderItem> getTransferOrderItems(UUID transferOrderId) {
        return transferOrderItemRepository.findByTransferOrderId(transferOrderId);
    }

    @Transactional
    public NxTransferOrder createTransferOrder(TransferOrderRequest request) {
        UUID tenantId = TenantContext.getCurrentTenantId();

        NxTransferOrder transferOrder = NxTransferOrder.builder()
                .tenantId(tenantId)
                .transferNumber(generateTransferNumber(tenantId))
                .transferType(request.getTransferType())
                .sourceNodeId(request.getSourceNodeId())
                .destinationNodeId(request.getDestinationNodeId())
                .status("DRAFT")
                .priority(request.getPriority() != null ? request.getPriority() : "NORMAL")
                .requestedBy(TenantContext.getCurrentUserId())
                .expectedArrival(request.getExpectedArrival())
                .notes(request.getNotes())
                .build();

        transferOrder = transferOrderRepository.save(transferOrder);

        if (request.getItems() != null && !request.getItems().isEmpty()) {
            for (var itemRequest : request.getItems()) {
                NxTransferOrderItem item = NxTransferOrderItem.builder()
                        .transferOrderId(transferOrder.getId())
                        .tenantId(tenantId)
                        .sku(itemRequest.getSku())
                        .productName(itemRequest.getProductName())
                        .quantityRequested(itemRequest.getQuantityRequested())
                        .unitCost(itemRequest.getUnitCost())
                        .status("PENDING")
                        .build();
                transferOrderItemRepository.save(item);
            }
        }

        log.info("Created transfer order {} from {} to {}", 
                transferOrder.getTransferNumber(), 
                request.getSourceNodeId(), 
                request.getDestinationNodeId());
        return transferOrder;
    }

    @Transactional
    public NxTransferOrder approveTransferOrder(UUID id, UUID approvedBy) {
        NxTransferOrder transferOrder = getTransferOrder(id);

        if (!"PENDING_APPROVAL".equals(transferOrder.getStatus())) {
            throw new BadRequestException("Transfer order must be in PENDING_APPROVAL status to approve. Current: " + transferOrder.getStatus());
        }

        transferOrder.setStatus("APPROVED");
        transferOrder.setApprovedBy(approvedBy);
        transferOrder = transferOrderRepository.save(transferOrder);

        log.info("Transfer order {} approved by {}", transferOrder.getTransferNumber(), approvedBy);
        return transferOrder;
    }

    @Transactional
    public NxTransferOrder shipTransferOrder(UUID id, UUID shippedBy, List<NxTransferOrderItem> shippedItems) {
        NxTransferOrder transferOrder = getTransferOrder(id);

        if (!"APPROVED".equals(transferOrder.getStatus())) {
            throw new BadRequestException("Transfer order must be APPROVED to ship. Current: " + transferOrder.getStatus());
        }

        List<NxTransferOrderItem> existingItems = transferOrderItemRepository.findByTransferOrderId(id);

        if (shippedItems != null) {
            for (NxTransferOrderItem shippedItem : shippedItems) {
                existingItems.stream()
                        .filter(item -> item.getSku().equals(shippedItem.getSku()))
                        .findFirst()
                        .ifPresent(item -> {
                            item.setQuantityShipped(shippedItem.getQuantityShipped());
                            item.setStatus("SHIPPED");
                            transferOrderItemRepository.save(item);
                        });
            }
        } else {
            for (NxTransferOrderItem item : existingItems) {
                item.setQuantityShipped(item.getQuantityRequested());
                item.setStatus("SHIPPED");
                transferOrderItemRepository.save(item);
            }
        }

        transferOrder.setStatus("IN_TRANSIT");
        transferOrder = transferOrderRepository.save(transferOrder);

        log.info("Transfer order {} shipped by {}", transferOrder.getTransferNumber(), shippedBy);
        return transferOrder;
    }

    @Transactional
    public NxTransferOrder receiveTransferOrder(UUID id, UUID receivedBy, List<NxTransferOrderItem> receivedItems) {
        NxTransferOrder transferOrder = getTransferOrder(id);

        if (!"IN_TRANSIT".equals(transferOrder.getStatus())) {
            throw new BadRequestException("Transfer order must be IN_TRANSIT to receive. Current: " + transferOrder.getStatus());
        }

        List<NxTransferOrderItem> existingItems = transferOrderItemRepository.findByTransferOrderId(id);

        if (receivedItems != null) {
            for (NxTransferOrderItem receivedItem : receivedItems) {
                existingItems.stream()
                        .filter(item -> item.getSku().equals(receivedItem.getSku()))
                        .findFirst()
                        .ifPresent(item -> {
                            item.setQuantityReceived(receivedItem.getQuantityReceived());
                            item.setStatus("RECEIVED");
                            transferOrderItemRepository.save(item);
                        });
            }
        } else {
            for (NxTransferOrderItem item : existingItems) {
                item.setQuantityReceived(item.getQuantityShipped() != null ? item.getQuantityShipped() : item.getQuantityRequested());
                item.setStatus("RECEIVED");
                transferOrderItemRepository.save(item);
            }
        }

        transferOrder.setStatus("RECEIVED");
        transferOrder.setActualArrival(LocalDateTime.now());
        transferOrder = transferOrderRepository.save(transferOrder);

        log.info("Transfer order {} received by {}", transferOrder.getTransferNumber(), receivedBy);
        return transferOrder;
    }

    @Transactional
    public NxTransferOrder cancelTransferOrder(UUID id, UUID cancelledBy) {
        NxTransferOrder transferOrder = getTransferOrder(id);

        if ("RECEIVED".equals(transferOrder.getStatus()) || "CANCELLED".equals(transferOrder.getStatus())) {
            throw new BadRequestException("Cannot cancel transfer order in status: " + transferOrder.getStatus());
        }

        transferOrder.setStatus("CANCELLED");
        transferOrder = transferOrderRepository.save(transferOrder);

        List<NxTransferOrderItem> items = transferOrderItemRepository.findByTransferOrderId(id);
        for (NxTransferOrderItem item : items) {
            item.setStatus("CANCELLED");
            transferOrderItemRepository.save(item);
        }

        log.info("Transfer order {} cancelled by {}", transferOrder.getTransferNumber(), cancelledBy);
        return transferOrder;
    }

    public List<NxTransferOrder> getTransfersInTransit(UUID tenantId) {
        return transferOrderRepository.findByTenantIdAndStatus(tenantId, "IN_TRANSIT");
    }

    public List<NxTransferOrder> getTransfersByNode(UUID nodeId) {
        List<NxTransferOrder> asSource = transferOrderRepository.findBySourceNodeId(nodeId);
        List<NxTransferOrder> asDest = transferOrderRepository.findByDestinationNodeId(nodeId);

        Set<UUID> seen = new HashSet<>();
        List<NxTransferOrder> result = new ArrayList<>();

        for (NxTransferOrder t : asSource) {
            if (seen.add(t.getId())) result.add(t);
        }
        for (NxTransferOrder t : asDest) {
            if (seen.add(t.getId())) result.add(t);
        }

        return result;
    }

    public Map<String, Object> getTransferStats(UUID tenantId) {
        List<NxTransferOrder> allTransfers = transferOrderRepository.findByTenantId(tenantId);

        Map<String, Object> stats = new LinkedHashMap<>();
        stats.put("totalTransfers", allTransfers.size());
        stats.put("draft", allTransfers.stream().filter(t -> "DRAFT".equals(t.getStatus())).count());
        stats.put("pendingApproval", allTransfers.stream().filter(t -> "PENDING_APPROVAL".equals(t.getStatus())).count());
        stats.put("approved", allTransfers.stream().filter(t -> "APPROVED".equals(t.getStatus())).count());
        stats.put("inTransit", allTransfers.stream().filter(t -> "IN_TRANSIT".equals(t.getStatus())).count());
        stats.put("received", allTransfers.stream().filter(t -> "RECEIVED".equals(t.getStatus())).count());
        stats.put("cancelled", allTransfers.stream().filter(t -> "CANCELLED".equals(t.getStatus())).count());

        long urgentCount = allTransfers.stream()
                .filter(t -> "URGENT".equals(t.getPriority()) && !"RECEIVED".equals(t.getStatus()) && !"CANCELLED".equals(t.getStatus()))
                .count();
        stats.put("urgentTransfers", urgentCount);

        return stats;
    }

    private String generateTransferNumber(UUID tenantId) {
        String prefix = "TRF-" + tenantId.toString().substring(0, 4).toUpperCase();
        String timestamp = String.valueOf(System.currentTimeMillis()).substring(8);
        return prefix + "-" + timestamp;
    }
}
