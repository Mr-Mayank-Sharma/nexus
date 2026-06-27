package com.nexus.oms.service;

import com.nexus.oms.entity.NxInventory;
import com.nexus.oms.exception.BadRequestException;
import com.nexus.oms.exception.ResourceNotFoundException;
import com.nexus.oms.repository.InventoryRepository;
import org.springframework.cache.annotation.CacheEvict;
import org.springframework.cache.annotation.Cacheable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.UUID;

@Service
public class InventoryService {

    private final InventoryRepository inventoryRepository;

    public InventoryService(InventoryRepository inventoryRepository) {
        this.inventoryRepository = inventoryRepository;
    }

    @Cacheable(value = "inventory", key = "#tenantId")
    public List<NxInventory> getInventoryByTenant(UUID tenantId) {
        return inventoryRepository.findByTenantId(tenantId);
    }

    @Cacheable(value = "inventory", key = "#tenantId + ':' + #sku")
    public NxInventory getBySku(UUID tenantId, String sku) {
        List<NxInventory> items = inventoryRepository.findByTenantIdAndSku(tenantId, sku);
        if (items.isEmpty()) {
            throw new ResourceNotFoundException("Inventory", sku);
        }
        return items.get(0);
    }

    public Integer getAvailableToPromise(UUID tenantId, String sku) {
        return inventoryRepository.getAvailableToPromise(tenantId, sku);
    }

    @Transactional
    @CacheEvict(value = "inventory", allEntries = true)
    public NxInventory adjustInventory(UUID id, int quantityChange) {
        NxInventory inv = inventoryRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Inventory", id));

        int newQty = inv.getQuantityOnHand() + quantityChange;
        if (newQty < 0) {
            throw new BadRequestException("Insufficient inventory to reduce");
        }
        inv.setQuantityOnHand(newQty);
        return inventoryRepository.save(inv);
    }

    @Transactional
    public boolean checkAvailability(UUID tenantId, String sku, UUID nodeId, int qty) {
        return inventoryRepository.findByTenantIdAndSkuAndNodeId(tenantId, sku, nodeId)
                .map(inv -> (inv.getQuantityOnHand() - inv.getQuantityAllocated() - inv.getQuantityReserved()) >= qty)
                .orElse(false);
    }

    @Transactional
    @CacheEvict(value = "inventory", allEntries = true)
    public void reserveInventory(UUID tenantId, String sku, UUID nodeId, int qty) {
        NxInventory inv = inventoryRepository.findByTenantIdAndSkuAndNodeId(tenantId, sku, nodeId)
                .orElseThrow(() -> new ResourceNotFoundException("Inventory at node", nodeId));

        int available = inv.getQuantityOnHand() - inv.getQuantityAllocated() - inv.getQuantityReserved();
        if (available < qty) {
            throw new BadRequestException("Insufficient inventory: available " + available + ", requested " + qty);
        }
        inv.setQuantityAllocated(inv.getQuantityAllocated() + qty);
        inventoryRepository.save(inv);
    }

    @Transactional
    @CacheEvict(value = "inventory", allEntries = true)
    public void releaseInventory(UUID tenantId, String sku, UUID nodeId, int qty) {
        inventoryRepository.findByTenantIdAndSkuAndNodeId(tenantId, sku, nodeId)
                .ifPresent(inv -> {
                    int newAllocated = Math.max(0, inv.getQuantityAllocated() - qty);
                    inv.setQuantityAllocated(newAllocated);
                    inventoryRepository.save(inv);
                });
    }
}
