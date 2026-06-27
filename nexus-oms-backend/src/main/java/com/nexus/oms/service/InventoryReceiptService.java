package com.nexus.oms.service;

import com.nexus.oms.dto.InventoryReceiptRequest;
import com.nexus.oms.entity.NxInventory;
import com.nexus.oms.entity.NxInventoryReceipt;
import com.nexus.oms.exception.ResourceNotFoundException;
import com.nexus.oms.repository.NxInventoryReceiptRepository;
import com.nexus.oms.repository.InventoryRepository;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;

@Service
public class InventoryReceiptService {

    private final NxInventoryReceiptRepository receiptRepository;
    private final InventoryRepository inventoryRepository;

    public InventoryReceiptService(NxInventoryReceiptRepository receiptRepository,
                                    InventoryRepository inventoryRepository) {
        this.receiptRepository = receiptRepository;
        this.inventoryRepository = inventoryRepository;
    }

    public Page<NxInventoryReceipt> getReceipts(UUID tenantId, String status, Pageable pageable) {
        if (status != null && !status.isBlank()) {
            return receiptRepository.findByTenantIdAndStatus(tenantId, status, pageable);
        }
        return receiptRepository.findByTenantId(tenantId, pageable);
    }

    public NxInventoryReceipt getReceipt(UUID id) {
        return receiptRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("InventoryReceipt", id));
    }

    @Transactional
    public NxInventoryReceipt createReceipt(UUID tenantId, InventoryReceiptRequest request) {
        NxInventoryReceipt receipt = NxInventoryReceipt.builder()
                .tenantId(tenantId)
                .nodeId(request.getNodeId())
                .receiptType(request.getReceiptType())
                .referenceNumber(request.getReferenceNumber())
                .sku(request.getSku())
                .productName(request.getProductName())
                .quantity(request.getQuantity())
                .unitCost(request.getUnitCost())
                .lotNumber(request.getLotNumber())
                .expiryDate(request.getExpiryDate())
                .status("PENDING")
                .build();
        return receiptRepository.save(receipt);
    }

    @Transactional
    public NxInventoryReceipt receiveInventory(UUID id, String receivedBy) {
        NxInventoryReceipt receipt = getReceipt(id);
        if (!"PENDING".equals(receipt.getStatus())) {
            throw new IllegalStateException("Receipt already " + receipt.getStatus());
        }

        java.util.Optional<NxInventory> existing = inventoryRepository
                .findByTenantIdAndSkuAndNodeId(receipt.getTenantId(), receipt.getSku(), receipt.getNodeId());

        NxInventory inv;
        if (existing.isEmpty()) {
            inv = NxInventory.builder()
                    .tenantId(receipt.getTenantId())
                    .sku(receipt.getSku())
                    .nodeId(receipt.getNodeId())
                    .quantityOnHand(receipt.getQuantity())
                    .quantityAllocated(0)
                    .quantityReserved(0)
                    .quantityInTransit(0)
                    .quantityOnOrder(0)
                    .quantityDamaged(0)
                    .safetyStock(0)
                    .reorderPoint(0)
                    .reorderQty(0)
                    .lotNumber(receipt.getLotNumber())
                    .build();
        } else {
            inv = existing.get();
            inv.setQuantityOnHand(inv.getQuantityOnHand() + receipt.getQuantity());
            if (receipt.getLotNumber() != null) inv.setLotNumber(receipt.getLotNumber());
        }
        inventoryRepository.save(inv);

        receipt.setStatus("RECEIVED");
        receipt.setReceivedBy(receivedBy);
        receipt.setReceivedAt(LocalDateTime.now());
        return receiptRepository.save(receipt);
    }

    @Transactional
    public void deleteReceipt(UUID id) {
        receiptRepository.deleteById(id);
    }
}
