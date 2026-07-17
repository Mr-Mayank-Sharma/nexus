package com.nexus.oms.kafka;

import org.junit.jupiter.api.Test;

import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;

class EventRecordTest {

    @Test
    void create_generatesValidEvent() {
        UUID tenantId = UUID.randomUUID();
        EventRecord event = EventRecord.create("order.created", "order-service", "order-123", tenantId);

        assertNotNull(event.eventId());
        assertEquals("order.created", event.eventType());
        assertEquals("order-service", event.source());
        assertNotNull(event.timestamp());
        assertEquals("order-123", event.data());
        assertEquals(tenantId, event.tenantId());
    }

    @Test
    void create_generatesUniqueEventIds() {
        EventRecord e1 = EventRecord.create("t", "s", "d1", null);
        EventRecord e2 = EventRecord.create("t", "s", "d2", null);
        assertNotEquals(e1.eventId(), e2.eventId());
    }

    @Test
    void toJson_and_fromJson_roundTrips() {
        UUID tenantId = UUID.randomUUID();
        EventRecord original = EventRecord.create("shipment.created", "shipment-svc", "SHIP-001", tenantId);

        String json = original.toJson();
        EventRecord restored = EventRecord.fromJson(json);

        assertEquals(original.eventId(), restored.eventId());
        assertEquals(original.eventType(), restored.eventType());
        assertEquals(original.source(), restored.source());
        assertEquals(original.data(), restored.data());
        assertEquals(original.tenantId(), restored.tenantId());
    }

    @Test
    void fromJson_handlesNullTenantId() {
        EventRecord original = EventRecord.create("test.event", "svc", "data", null);
        String json = original.toJson();
        EventRecord restored = EventRecord.fromJson(json);
        assertNull(restored.tenantId());
    }
}
