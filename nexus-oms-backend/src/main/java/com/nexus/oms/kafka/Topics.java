package com.nexus.oms.kafka;

public final class Topics {

    public static final String ORDER_CREATED = "order.created";
    public static final String ORDER_UPDATED = "order.updated";
    public static final String ORDER_CANCELLED = "order.cancelled";
    public static final String ORDER_CONFIRMED = "order.confirmed";
    public static final String ORDER_ALLOCATED = "order.allocated";
    public static final String ORDER_SHIPPED = "order.shipped";
    public static final String ORDER_DELIVERED = "order.delivered";
    public static final String SHIPMENT_CREATED = "shipment.created";
    public static final String SHIPMENT_UPDATED = "shipment.updated";
    public static final String INVENTORY_ADJUSTED = "inventory.adjusted";
    public static final String PAYMENT_RECEIVED = "payment.received";
    public static final String PAYMENT_REFUNDED = "payment.refunded";
    public static final String RETURN_CREATED = "return.created";
    public static final String RETURN_APPROVED = "return.approved";

    /** Order events that are published but have no dedicated consumer (analytics, integrations and audit read them). */
    public static final java.util.List<String> ORDER_EXTRAS = java.util.List.of(
            "order.modified", "order.split", "order.merged", "order.label_generated", "order.fulfilled");

    /** Every topic the consumer service listens to; each needs a dead-letter topic. */
    public static final java.util.List<String> CONSUMED = java.util.List.of(
            ORDER_CREATED, ORDER_UPDATED, ORDER_CANCELLED, ORDER_CONFIRMED, ORDER_ALLOCATED, ORDER_SHIPPED, ORDER_DELIVERED,
            SHIPMENT_CREATED, SHIPMENT_UPDATED, INVENTORY_ADJUSTED, PAYMENT_RECEIVED, PAYMENT_REFUNDED, RETURN_CREATED, RETURN_APPROVED);

    /** The topic an order status change is published to. */
    public static String orderStatus(String status) {
        return "order." + status.toLowerCase(java.util.Locale.ROOT);
    }

    public static final String DLQ_SUFFIX = ".dlq";

    public static String dlq(String topic) {
        return topic + DLQ_SUFFIX;
    }

    private Topics() {}
}
