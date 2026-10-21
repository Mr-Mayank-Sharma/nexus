package com.nexus.core.service;

import com.nexus.core.model.inventory.InventoryItem;
import com.nexus.core.model.order.OrderItem;
import com.nexus.core.model.shipment.Picklist;
import com.nexus.core.model.shipment.PicklistBin;
import com.nexus.core.model.shipment.PicklistItem;
import com.nexus.core.model.shipment.PicklistRole;
import jakarta.persistence.EntityManager;
import jakarta.persistence.LockModeType;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Picking at a store or a warehouse without a WMS (HotWax store fulfilment): a picklist gathers order ship groups
 * held at one facility, one bin each; the picker records what was found; completing it ships each bin and short-picks
 * what was not found, so routing can send it elsewhere.
 */
@Service("corePicklistService")
@Transactional
public class PicklistService {

    public static final String FLOW = StatusService.DEFAULT_FLOW;

    private final EntityManager em;
    private final SequenceService sequences;
    private final StatusService statuses;
    private final OrderService orders;
    private final ShipmentService shipments;

    public PicklistService(EntityManager em, SequenceService sequences, StatusService statuses, OrderService orders, ShipmentService shipments) {
        this.em = em;
        this.sequences = sequences;
        this.statuses = statuses;
        this.orders = orders;
        this.shipments = shipments;
    }

    public record GroupRef(String orderId, String shipGroupSeqId) {
    }

    /** A picklist for these ship groups at the facility, from what each item holds there. */
    public String create(String facilityId, String shipmentMethodTypeId, List<GroupRef> groups, String userId) {
        if (groups == null || groups.isEmpty()) throw new IllegalArgumentException("A picklist needs at least one order");
        Picklist p = new Picklist();
        p.setPicklistId(sequences.nextId("Picklist"));
        p.setFacilityId(facilityId);
        p.setShipmentMethodTypeId(shipmentMethodTypeId);
        p.setStatusId(statuses.initialStatus(FLOW, "PICKLIST_STATUS"));
        p.setPicklistDate(CoreClock.now());
        p.setCreatedByUserLogin(userId);
        em.persist(p);
        long bin = 1;
        for (GroupRef g : groups) {
            if (!facilityId.equals(orders.shipGroup(g.orderId(), g.shipGroupSeqId()).getFacilityId())) {
                throw new IllegalArgumentException("Order " + g.orderId() + " ship group " + g.shipGroupSeqId() + " is not routed to " + facilityId);
            }
            PicklistBin b = new PicklistBin();
            b.setPicklistBinId(sequences.nextId("PicklistBin"));
            b.setPicklistId(p.getPicklistId());
            b.setBinLocationNumber(bin++);
            b.setPrimaryOrderId(g.orderId());
            b.setPrimaryShipGroupSeqId(g.shipGroupSeqId());
            em.persist(b);
            int added = 0;
            for (OrderItem i : orders.items(g.orderId())) {
                if (!g.shipGroupSeqId().equals(i.getShipGroupSeqId()) || !"ITEM_APPROVED".equals(i.getStatusId())) continue;
                for (OrderService.Held h : orders.held(g.orderId(), i.getOrderItemSeqId())) {
                    InventoryItem inv = em.find(InventoryItem.class, h.inventoryItemId());
                    if (!facilityId.equals(inv.getFacilityId())) continue;
                    PicklistItem pi = new PicklistItem();
                    pi.setPicklistBinId(b.getPicklistBinId());
                    pi.setOrderId(g.orderId());
                    pi.setOrderItemSeqId(i.getOrderItemSeqId());
                    pi.setShipGroupSeqId(g.shipGroupSeqId());
                    pi.setInventoryItemId(h.inventoryItemId());
                    pi.setItemStatusId("PICKITEM_PENDING");
                    pi.setQuantity(h.quantity());
                    em.persist(pi);
                    added++;
                }
            }
            if (added == 0) throw new IllegalStateException("Order " + g.orderId() + " holds nothing at " + facilityId + " to pick");
        }
        return p.getPicklistId();
    }

    public void assign(String picklistId, String pickerPartyId, String userId) {
        Picklist p = picklist(picklistId);
        PicklistRole r = new PicklistRole();
        r.setPicklistId(picklistId);
        r.setPartyId(pickerPartyId);
        r.setRoleTypeId("PICKER");
        r.setFromDate(CoreClock.now());
        em.persist(r);
        if ("PICKLIST_INPUT".equals(p.getStatusId())) {
            statuses.requireAllowed(FLOW, p.getStatusId(), "PICKLIST_ASSIGNED");
            p.setStatusId("PICKLIST_ASSIGNED");
        }
    }

    /** What the picker found: quantity per (order, item), from zero (not found) up to what was asked. */
    public void recordPick(String picklistId, String orderId, String orderItemSeqId, BigDecimal found) {
        Picklist p = picklist(picklistId);
        if (List.of("PICKLIST_COMPLETED", "PICKLIST_CANCELLED").contains(p.getStatusId())) throw new IllegalStateException("Picklist " + picklistId + " is " + p.getStatusId());
        List<PicklistItem> items = items(picklistId).stream()
                .filter(i -> i.getOrderId().equals(orderId) && i.getOrderItemSeqId().equals(orderItemSeqId)).toList();
        if (items.isEmpty()) throw new IllegalArgumentException("Picklist " + picklistId + " has no item " + orderItemSeqId + " of order " + orderId);
        BigDecimal asked = items.stream().map(PicklistItem::getQuantity).reduce(BigDecimal.ZERO, BigDecimal::add);
        if (found.signum() < 0 || found.compareTo(asked) > 0) throw new IllegalArgumentException("Found must be between 0 and " + asked.stripTrailingZeros().toPlainString());
        BigDecimal left = found;
        for (PicklistItem i : items) {
            BigDecimal take = i.getQuantity().min(left);
            i.setPickedQuantity(take);
            i.setItemStatusId(take.signum() > 0 ? "PICKITEM_PICKED" : "PICKITEM_CANCELLED");
            left = left.subtract(take);
        }
    }

    /**
     * Completes the picklist: what was not picked is short-picked (its hold here released, this facility excluded for
     * the item), and each bin with something picked ships with the carrier and service given. Returns the shipments.
     */
    public List<String> complete(String picklistId, String carrierPartyId, String shipmentMethodTypeId, String userId) {
        Picklist p = picklist(picklistId);
        statuses.requireAllowed(FLOW, p.getStatusId(), "PICKLIST_PICKED");
        List<PicklistItem> all = items(picklistId);
        if (all.stream().anyMatch(i -> "PICKITEM_PENDING".equals(i.getItemStatusId()))) {
            throw new IllegalStateException("Picklist " + picklistId + " still has items to pick");
        }
        p.setStatusId("PICKLIST_PICKED");
        Map<String, Map<String, BigDecimal>> pickedByGroup = new LinkedHashMap<>();
        for (PicklistItem i : all) {
            BigDecimal picked = i.getPickedQuantity() == null ? BigDecimal.ZERO : i.getPickedQuantity();
            BigDecimal shortQ = i.getQuantity().subtract(picked);
            if (shortQ.signum() > 0) orders.shortPick(i.getOrderId(), i.getOrderItemSeqId(), p.getFacilityId(), shortQ, "Not found when picking " + picklistId, userId);
            if (picked.signum() > 0) {
                pickedByGroup.computeIfAbsent(i.getOrderId() + "|" + i.getShipGroupSeqId(), k -> new LinkedHashMap<>())
                        .merge(i.getOrderItemSeqId(), picked, BigDecimal::add);
            }
        }
        List<String> out = new ArrayList<>();
        for (Map.Entry<String, Map<String, BigDecimal>> e : pickedByGroup.entrySet()) {
            String[] k = e.getKey().split("\\|");
            String shipmentId = shipments.createSalesShipment(k[0], k[1], e.getValue(), userId);
            shipments.ship(shipmentId, carrierPartyId, shipmentMethodTypeId, null, null, userId);
            out.add(shipmentId);
        }
        all.stream().filter(i -> "PICKITEM_PICKED".equals(i.getItemStatusId())).forEach(i -> i.setItemStatusId("PICKITEM_COMPLETED"));
        p.setStatusId("PICKLIST_COMPLETED");
        return out;
    }

    /** Cancels the picklist; the orders keep their holds and can go on another picklist. */
    public void cancel(String picklistId) {
        Picklist p = picklist(picklistId);
        statuses.requireAllowed(FLOW, p.getStatusId(), "PICKLIST_CANCELLED");
        p.setStatusId("PICKLIST_CANCELLED");
        items(picklistId).forEach(i -> i.setItemStatusId("PICKITEM_CANCELLED"));
    }

    @Transactional(readOnly = true)
    public List<PicklistItem> items(String picklistId) {
        return em.createQuery("SELECT i FROM PicklistItem i, PicklistBin b WHERE b.picklistBinId = i.picklistBinId AND b.picklistId = :p "
                + "ORDER BY b.binLocationNumber, i.orderItemSeqId", PicklistItem.class).setParameter("p", picklistId).getResultList();
    }

    private Picklist picklist(String picklistId) {
        Picklist p = em.find(Picklist.class, picklistId, LockModeType.PESSIMISTIC_WRITE);
        if (p == null) throw new IllegalArgumentException("Unknown picklist " + picklistId);
        return p;
    }
}
