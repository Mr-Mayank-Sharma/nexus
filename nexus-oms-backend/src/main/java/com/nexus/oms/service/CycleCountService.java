package com.nexus.oms.service;

import com.nexus.oms.dto.CycleCountRequest;
import com.nexus.oms.entity.NxCycleCount;
import com.nexus.oms.entity.NxInventory;
import com.nexus.oms.exception.ResourceNotFoundException;
import com.nexus.oms.repository.NxCycleCountRepository;
import com.nexus.oms.repository.InventoryRepository;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;

@Service
public class CycleCountService {

    private final NxCycleCountRepository cycleCountRepository;
    private final InventoryRepository inventoryRepository;

    public CycleCountService(NxCycleCountRepository cycleCountRepository,
                              InventoryRepository inventoryRepository) {
        this.cycleCountRepository = cycleCountRepository;
        this.inventoryRepository = inventoryRepository;
    }

    public Page<NxCycleCount> getCycleCounts(UUID tenantId, String status, Pageable pageable) {
        if (status != null && !status.isBlank()) {
            return cycleCountRepository.findByTenantIdAndStatus(tenantId, status, pageable);
        }
        return cycleCountRepository.findByTenantId(tenantId, pageable);
    }

    public NxCycleCount getCycleCount(UUID id) {
        return cycleCountRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("CycleCount", id));
    }

    @Transactional
    public NxCycleCount createCycleCount(UUID tenantId, CycleCountRequest request) {
        NxCycleCount count = NxCycleCount.builder()
                .tenantId(tenantId)
                .nodeId(request.getNodeId())
                .sku(request.getSku())
                .productName(request.getProductName())
                .expectedQty(request.getExpectedQty())
                .status("PENDING")
                .notes(request.getNotes())
                .build();
        return cycleCountRepository.save(count);
    }

    @Transactional
    public NxCycleCount performCount(UUID id, Integer countedQty, String countedBy) {
        NxCycleCount count = getCycleCount(id);
        if (!"PENDING".equals(count.getStatus())) {
            throw new IllegalStateException("Cycle count already " + count.getStatus());
        }

        count.setCountedQty(countedQty);
        count.setCountedBy(countedBy);
        count.setCountedAt(LocalDateTime.now());

        boolean match = countedQty.equals(count.getExpectedQty());
        count.setStatus(match ? "MATCH" : "MISMATCH");

        java.util.Optional<NxInventory> existing = inventoryRepository
                .findByTenantIdAndSkuAndNodeId(count.getTenantId(), count.getSku(), count.getNodeId());
        if (existing.isPresent()) {
            NxInventory inv = existing.get();
            inv.setQuantityOnHand(countedQty);
            inventoryRepository.save(inv);
        }

        return cycleCountRepository.save(count);
    }
}
