package com.nexus.oms.service;

import com.nexus.oms.entity.NxOrder;
import com.nexus.oms.entity.NxOrderItem;
import com.nexus.oms.exception.ResourceNotFoundException;
import com.nexus.oms.repository.OrderItemRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.UUID;

/**
 * Takes an order's allocated stock off the books when the order ships.
 *
 * <p>Shipping used to change only an order's status, so on-hand stock stayed overstated forever and
 * the allocation was never cleared. Every path that ships an order calls {@link #consumeOnShip}.
 * It is idempotent per order line (via {@code consumedQty}), so an order shipped through packing and
 * then marked shipped by hand is only deducted once.
 *
 * <p>Limit: it works from the per-line allocation, which is recorded when one node covers the whole
 * order. For a split allocation the per-SKU truth lives in the allocation metadata and lines carry no
 * node, so those lines are skipped (and logged) rather than guessed.
 */
@Service
public class OrderStockCommit {

    private static final Logger log = LoggerFactory.getLogger(OrderStockCommit.class);

    private final OrderItemRepository orderItemRepository;
    private final InventoryService inventoryService;

    public OrderStockCommit(OrderItemRepository orderItemRepository, InventoryService inventoryService) {
        this.orderItemRepository = orderItemRepository;
        this.inventoryService = inventoryService;
    }

    /** @return units taken off on-hand by this call (0 when already consumed or nothing was allocated) */
    @Transactional
    public int consumeOnShip(NxOrder order) {
        int consumed = 0;
        for (NxOrderItem item : orderItemRepository.findByOrderId(order.getId())) {
            int outstanding = nz(item.getAllocatedQty()) - nz(item.getConsumedQty());
            if (outstanding <= 0) continue;
            UUID node = item.getAllocatedNodeId() != null ? item.getAllocatedNodeId() : order.getAllocatedNode();
            if (node == null) {
                log.warn("Order {} line {} shipped with {} allocated but no node recorded; stock not deducted",
                        order.getId(), item.getSku(), outstanding);
                continue;
            }
            try {
                consumed += inventoryService.consumeAllocated(order.getTenantId(), item.getSku(), node, outstanding,
                        "ORDER", order.getId().toString());
                item.setConsumedQty(nz(item.getConsumedQty()) + outstanding);
                orderItemRepository.save(item);
            } catch (ResourceNotFoundException e) {
                log.warn("Order {} line {}: no stock record to deduct from ({})", order.getId(), item.getSku(), e.getMessage());
            }
        }
        return consumed;
    }

    private static int nz(Integer v) {
        return v != null ? v : 0;
    }
}
