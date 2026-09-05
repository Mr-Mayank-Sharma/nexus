package com.nexus.oms.service;

import com.nexus.oms.entity.NxInventory;
import com.nexus.oms.exception.BadRequestException;
import com.nexus.oms.exception.ResourceNotFoundException;
import com.nexus.oms.repository.InventoryRepository;
import com.nexus.oms.service.bigcommerce.BigCommerceInventorySyncService;
import org.springframework.cache.annotation.CacheEvict;
import org.springframework.cache.annotation.Cacheable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.UUID;

@Service
public class InventoryService {

    private final InventoryRepository inventoryRepository;
    private final BigCommerceInventorySyncService bigCommerceInventorySyncService;

    public InventoryService(InventoryRepository inventoryRepository,
                            BigCommerceInventorySyncService bigCommerceInventorySyncService) {
        this.inventoryRepository = inventoryRepository;
        this.bigCommerceInventorySyncService = bigCommerceInventorySyncService;
    }

    @Transactional
    @CacheEvict(value = "inventory", allEntries = true)
    public NxInventory createInventory(NxInventory inventory) {
        NxInventory saved = inventoryRepository.save(inventory);
        bigCommerceInventorySyncService.pushSkuInventory(saved.getTenantId(), saved.getSku());
        return saved;
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

    @Cacheable(value = "inventory", key = "'atp:' + #tenantId + ':' + #sku")
    public Integer getAvailableToPromise(UUID tenantId, String sku) {
        return inventoryRepository.getAvailableToPromise(tenantId, sku);
    }

    @Transactional
    @CacheEvict(value = "inventory", allEntries = true)
    public NxInventory adjustInventoryBySku(UUID tenantId, String sku, int quantityDelta) {
        NxInventory inv = getBySku(tenantId, sku);
        int newQty = inv.getQuantityOnHand() + quantityDelta;
        if (newQty < 0) {
            throw new BadRequestException("Insufficient inventory to reduce (SKU: " + sku + ")");
        }
        inv.setQuantityOnHand(newQty);
        NxInventory saved = inventoryRepository.save(inv);
        bigCommerceInventorySyncService.pushSkuInventory(tenantId, sku);
        return saved;
    }

    /**
     * Location-scoped deduction for ship-from-store / endless-aisle flows.
     * Deducts sellable stock (quantityOnHand) at a specific store node and
     * fails fast when the node has insufficient stock for the SKU.
     */
    @Transactional
    @CacheEvict(value = "inventory", allEntries = true)
    public NxInventory adjustInventoryBySkuAtNode(UUID tenantId, String sku, UUID nodeId, int quantityDelta) {
        NxInventory inv = inventoryRepository.findByTenantIdAndSkuAndNodeId(tenantId, sku, nodeId)
                .orElseThrow(() -> new ResourceNotFoundException(
                        "No inventory for SKU " + sku + " at node " + nodeId));
        int newQty = inv.getQuantityOnHand() + quantityDelta;
        if (newQty < 0) {
            throw new BadRequestException("Insufficient inventory at node " + nodeId
                    + " for SKU " + sku + " (on hand: " + inv.getQuantityOnHand() + ")");
        }
        inv.setQuantityOnHand(newQty);
        NxInventory saved = inventoryRepository.save(inv);
        bigCommerceInventorySyncService.pushSkuInventory(tenantId, sku);
        return saved;
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
        NxInventory saved = inventoryRepository.save(inv);
        bigCommerceInventorySyncService.pushSkuInventory(inv.getTenantId(), inv.getSku());
        return saved;
    }

    @Cacheable(value = "inventory", key = "'check:' + #tenantId + ':' + #sku + ':' + #nodeId + ':' + #qty")
    @Transactional
    public boolean checkAvailability(UUID tenantId, String sku, UUID nodeId, int qty) {
        // Inventory is a shared tenant+SKU pool (node_id is NULL on seeded rows).
        // Match node-specific rows first, then fall back to the shared pool.
        return inventoryRepository.findByTenantIdAndSkuAndNodeId(tenantId, sku, nodeId)
                .map(inv -> available(inv) >= qty)
                .orElseGet(() -> inventoryRepository.findByTenantIdAndSku(tenantId, sku).stream()
                        .anyMatch(inv -> available(inv) >= qty));
    }

    private int available(NxInventory inv) {
        return inv.getQuantityOnHand() - inv.getQuantityAllocated() - inv.getQuantityReserved();
    }

    // NOTE: no @Transactional — the atomic UPDATE commits immediately so row locks are never
    // held during the external BigCommerce HTTP push below (which caused deadlocks under load).
    @CacheEvict(value = "inventory", allEntries = true)
    public void reserveInventory(UUID tenantId, String sku, UUID nodeId, int qty) {
        // Atomic conditional update — safe under concurrency (no optimistic-lock retries needed)
        int updated = inventoryRepository.reserveAtomic(tenantId, sku, nodeId, qty);
        if (updated == 0) {
            NxInventory inv = inventoryRepository.findByTenantIdAndSku(tenantId, sku).stream()
                    .findFirst()
                    .orElseThrow(() -> new ResourceNotFoundException("Inventory", sku));
            int available = available(inv);
            throw new BadRequestException("Insufficient inventory: available " + available + ", requested " + qty);
        }
        bigCommerceInventorySyncService.pushSkuInventory(tenantId, sku);
    }

    @CacheEvict(value = "inventory", allEntries = true)
    public void releaseInventory(UUID tenantId, String sku, UUID nodeId, int qty) {
        inventoryRepository.releaseAtomic(tenantId, sku, nodeId, qty);
        bigCommerceInventorySyncService.pushSkuInventory(tenantId, sku);
    }
}
