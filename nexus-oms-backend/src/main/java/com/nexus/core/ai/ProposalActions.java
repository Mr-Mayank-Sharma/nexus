package com.nexus.core.ai;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.nexus.core.CoreStatus;
import com.nexus.core.model.order.OrderItemShipGroup;
import com.nexus.core.service.OrderService;
import com.nexus.core.service.RoutingService;
import com.nexus.core.service.RoutingSetupService;
import com.nexus.core.service.SequenceService;
import org.springframework.stereotype.Component;

/**
 * What each kind of AI proposal does, and how to undo it. Every action goes through the same services a person's
 * button calls, so status flows and stock checks still hold.
 * <ul>
 *   <li>REALLOCATE: move a ship group to another facility; undo moves it back.</li>
 *   <li>HOLD_ORDER / RELEASE_HOLD: put an order on hold or release it; each undoes the other.</li>
 *   <li>CANCEL_ITEM: cancel an item; can't be undone.</li>
 *   <li>ACTIVATE_ROUTING: a drafted routing group takes over from the live one; undo swaps them back.</li>
 * </ul>
 */
@Component("coreProposalActions")
class ProposalActions {

    static final String REALLOCATE = "REALLOCATE";
    static final String HOLD_ORDER = "HOLD_ORDER";
    static final String RELEASE_HOLD = "RELEASE_HOLD";
    static final String CANCEL_ITEM = "CANCEL_ITEM";
    static final String ACTIVATE_ROUTING = "ACTIVATE_ROUTING";

    private static final String FIRST_SHIP_GROUP = SequenceService.seqId(1);

    private final OrderService orderService;
    private final RoutingService routingService;
    private final RoutingSetupService routingSetupService;

    ProposalActions(OrderService orderService, RoutingService routingService, RoutingSetupService routingSetupService) {
        this.orderService = orderService;
        this.routingService = routingService;
        this.routingSetupService = routingSetupService;
    }

    /** Does the action. Throws when it can't be done now (the caller records the proposal as failed). */
    void apply(String actionType, JsonNode payload, String reason, String userId) {
        String orderId = payload.path("orderId").asText();
        switch (actionType) {
            case REALLOCATE -> routingService.reallocate(orderId, payload.path("shipGroupSeqId").asText(FIRST_SHIP_GROUP),
                    payload.path("facilityId").asText(), reason, userId);
            case HOLD_ORDER -> {
                if (CoreStatus.Order.HOLD.equals(orderStatus(orderId))) {
                    throw new IllegalStateException("Order " + orderId + " is already on hold");
                }
                orderService.changeStatus(orderId, CoreStatus.Order.HOLD, reason, userId);
            }
            case RELEASE_HOLD -> {
                String currentStatus = orderStatus(orderId);
                if (!CoreStatus.Order.HOLD.equals(currentStatus)) {
                    throw new IllegalStateException("Order " + orderId + " is not on hold; it is " + currentStatus);
                }
                orderService.changeStatus(orderId, CoreStatus.Order.APPROVED, reason, userId);
            }
            case CANCEL_ITEM -> orderService.cancelItem(orderId, payload.path("orderItemSeqId").asText(), null, reason, userId);
            case ACTIVATE_ROUTING -> routingSetupService.handOverSchedule(payload.path("activateGroupId").asText(),
                    payload.path("pauseGroupId").asText(null));
            default -> throw new IllegalArgumentException("Unknown action " + actionType);
        }
    }

    /** The action that undoes this one (applied with {@link #undoPayload}). */
    static String undoActionOf(String actionType) {
        return switch (actionType) {
            case HOLD_ORDER -> RELEASE_HOLD;
            case RELEASE_HOLD -> HOLD_ORDER;
            default -> actionType;   // a move is undone by a move back, a hand-over by a hand-over back
        };
    }

    /**
     * What would put things back, read before the action is applied: the location before a move, the order for a
     * status change, the groups swapped for a routing hand-over. Null when the action can't be undone.
     */
    ObjectNode undoPayload(String actionType, JsonNode payload) {
        String orderId = payload.path("orderId").asText();
        return switch (actionType) {
            case REALLOCATE -> {
                String shipGroupSeqId = payload.path("shipGroupSeqId").asText(FIRST_SHIP_GROUP);
                OrderItemShipGroup shipGroup = orderService.shipGroupOrNull(orderId, shipGroupSeqId);
                boolean wasSomewhere = shipGroup != null && shipGroup.getFacilityId() != null;
                yield wasSomewhere
                        ? ProposalService.JSON.createObjectNode().put("orderId", orderId).put("shipGroupSeqId", shipGroupSeqId)
                                .put("facilityId", shipGroup.getFacilityId())
                        : null;
            }
            case HOLD_ORDER, RELEASE_HOLD -> ProposalService.JSON.createObjectNode().put("orderId", orderId);
            case ACTIVATE_ROUTING -> {
                JsonNode pausedGroup = payload.path("pauseGroupId");
                boolean hadLiveGroup = !pausedGroup.isMissingNode() && !pausedGroup.isNull();
                yield hadLiveGroup
                        ? ProposalService.JSON.createObjectNode().put("activateGroupId", pausedGroup.asText())
                                .put("pauseGroupId", payload.path("activateGroupId").asText())
                        : null;
            }
            default -> null;
        };
    }

    private String orderStatus(String orderId) {
        return orderService.header(orderId).getStatusId();
    }
}
