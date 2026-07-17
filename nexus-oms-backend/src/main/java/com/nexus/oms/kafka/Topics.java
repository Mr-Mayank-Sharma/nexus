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

    public static final String DLQ_SUFFIX = ".dlq";

    public static String dlq(String topic) {
        return topic + DLQ_SUFFIX;
    }

    private Topics() {}
}
