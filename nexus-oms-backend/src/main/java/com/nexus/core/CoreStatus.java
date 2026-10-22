package com.nexus.core;

import java.util.Set;

/**
 * Every status id the core code checks or sets, in one place, so a typo is a compile error instead of a silent bug.
 * The values are rows of {@code status_item}, seeded by the core seed migrations; the allowed changes between them are
 * rows of {@code status_flow_transition}, checked by {@code StatusService}.
 */
public final class CoreStatus {

    private CoreStatus() {
    }

    /** Status types: the {@code status_type_id} a group of statuses belongs to. */
    public static final class Type {
        public static final String ORDER = "ORDER_STATUS";
        public static final String ORDER_ITEM = "ORDER_ITEM_STATUS";
        public static final String PAYMENT = "PAYMENT_PREF_STATUS";
        public static final String SHIPMENT = "SHIPMENT_STATUS";
        public static final String PICKLIST = "PICKLIST_STATUS";
        public static final String RETURN = "RETURN_STATUS";
        public static final String TRANSFER = "INVENTORY_XFER_STATUS";
        public static final String ROUTING = "ROUTING_STATUS";
        public static final String AI_PROPOSAL = "AI_PROPOSAL_STATUS";

        private Type() {
        }
    }

    /** An order as a whole. */
    public static final class Order {
        public static final String CREATED = "ORDER_CREATED";
        public static final String APPROVED = "ORDER_APPROVED";
        public static final String HOLD = "ORDER_HOLD";
        public static final String SENT = "ORDER_SENT";
        public static final String COMPLETED = "ORDER_COMPLETED";
        public static final String REJECTED = "ORDER_REJECTED";
        public static final String CANCELLED = "ORDER_CANCELLED";

        /** Statuses in which the order is still being worked on. */
        public static final Set<String> OPEN = Set.of(CREATED, APPROVED, HOLD);

        private Order() {
        }
    }

    /** One line of an order. */
    public static final class Item {
        public static final String CREATED = "ITEM_CREATED";
        public static final String APPROVED = "ITEM_APPROVED";
        public static final String COMPLETED = "ITEM_COMPLETED";
        public static final String REJECTED = "ITEM_REJECTED";
        public static final String CANCELLED = "ITEM_CANCELLED";

        /** Statuses in which an item can still be held, shipped or cancelled. */
        public static final Set<String> OPEN = Set.of(CREATED, APPROVED);

        /** Statuses in which an item no longer counts towards the order's value. */
        public static final Set<String> GONE = Set.of(CANCELLED, REJECTED);

        private Item() {
        }
    }

    /** How an order is paid. */
    public static final class Payment {
        public static final String NOT_RECEIVED = "PAYMENT_NOT_RECEIVED";
        public static final String AUTHORIZED = "PAYMENT_AUTHORIZED";
        public static final String SETTLED = "PAYMENT_SETTLED";
        public static final String REFUNDED = "PAYMENT_REFUNDED";
        public static final String DECLINED = "PAYMENT_DECLINED";
        public static final String CANCELLED = "PAYMENT_CANCELLED";

        private Payment() {
        }
    }

    /** Goods on their way, out (sales, transfers) or in (returns). */
    public static final class Shipment {
        public static final String INPUT = "SHIPMENT_INPUT";
        public static final String SCHEDULED = "SHIPMENT_SCHEDULED";
        public static final String PICKED = "SHIPMENT_PICKED";
        public static final String PACKED = "SHIPMENT_PACKED";
        public static final String SHIPPED = "SHIPMENT_SHIPPED";
        public static final String DELIVERED = "SHIPMENT_DELIVERED";
        public static final String CANCELLED = "SHIPMENT_CANCELLED";

        /** Statuses before packing, in which boxes may still be added. */
        public static final Set<String> BEFORE_PACKING = Set.of(INPUT, SCHEDULED, PICKED);

        private Shipment() {
        }
    }

    /** A pick run at a store or warehouse, and its lines. */
    public static final class Picklist {
        public static final String INPUT = "PICKLIST_INPUT";
        public static final String ASSIGNED = "PICKLIST_ASSIGNED";
        public static final String PICKED = "PICKLIST_PICKED";
        public static final String COMPLETED = "PICKLIST_COMPLETED";
        public static final String CANCELLED = "PICKLIST_CANCELLED";

        public static final String ITEM_PENDING = "PICKITEM_PENDING";
        public static final String ITEM_PICKED = "PICKITEM_PICKED";
        public static final String ITEM_COMPLETED = "PICKITEM_COMPLETED";
        public static final String ITEM_CANCELLED = "PICKITEM_CANCELLED";

        private Picklist() {
        }
    }

    /** A customer return and its lines. */
    public static final class Return {
        public static final String REQUESTED = "RETURN_REQUESTED";
        public static final String ACCEPTED = "RETURN_ACCEPTED";
        public static final String RECEIVED = "RETURN_RECEIVED";
        public static final String COMPLETED = "RETURN_COMPLETED";
        public static final String CANCELLED = "RETURN_CANCELLED";

        private Return() {
        }
    }

    /** Stock moving between locations. */
    public static final class Transfer {
        public static final String REQUESTED = "IXF_REQUESTED";
        public static final String SCHEDULED = "IXF_SCHEDULED";
        public static final String EN_ROUTE = "IXF_EN_ROUTE";
        public static final String RECEIVED_SHORT = "IXF_RECEIVED_SHORT";
        public static final String COMPLETE = "IXF_COMPLETE";
        public static final String CANCELLED = "IXF_CANCELLED";

        private Transfer() {
        }
    }

    /** A routing or one of its rules. */
    public static final class Routing {
        public static final String DRAFT = "ROUTING_DRAFT";
        public static final String ACTIVE = "ROUTING_ACTIVE";
        public static final String ARCHIVED = "ROUTING_ARCHIVED";

        private Routing() {
        }
    }

    /** A change an AI proposed. */
    public static final class Proposal {
        public static final String PENDING = "AIP_PENDING";
        public static final String APPLIED = "AIP_APPLIED";
        public static final String REJECTED = "AIP_REJECTED";
        public static final String FAILED = "AIP_FAILED";
        public static final String UNDONE = "AIP_UNDONE";

        private Proposal() {
        }
    }

    /** A message from or to another system. */
    public static final class Message {
        public static final String RECEIVED = "SmsgReceived";
        public static final String CONSUMING = "SmsgConsuming";
        public static final String CONSUMED = "SmsgConsumed";
        public static final String ERROR = "SmsgError";

        private Message() {
        }
    }
}
