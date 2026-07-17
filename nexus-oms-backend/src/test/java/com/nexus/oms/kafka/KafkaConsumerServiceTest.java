package com.nexus.oms.kafka;

import com.nexus.oms.entity.NxAuditLog;
import com.nexus.oms.repository.AuditLogRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;
import org.springframework.kafka.core.KafkaTemplate;

import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class KafkaConsumerServiceTest {

    @Mock
    private AuditLogRepository auditLogRepository;

    @Mock
    private KafkaTemplate<String, String> kafkaTemplate;

    private KafkaConsumerService consumerService;

    @BeforeEach
    void setUp() {
        consumerService = new KafkaConsumerService(auditLogRepository, kafkaTemplate);
    }

    @Test
    void handleOrderCreated_savesAuditLog() {
        UUID orderId = UUID.randomUUID();
        UUID tenantId = UUID.randomUUID();
        EventRecord event = EventRecord.create("ORDER_CREATED", Topics.ORDER_CREATED, orderId.toString(), tenantId);
        String json = event.toJson();

        consumerService.handleOrderCreated(json);

        ArgumentCaptor<NxAuditLog> captor = ArgumentCaptor.forClass(NxAuditLog.class);
        verify(auditLogRepository).save(captor.capture());
        NxAuditLog log = captor.getValue();
        assertEquals(orderId, log.getEntityId());
        assertEquals("ORDER", log.getEntityType());
        assertEquals("CREATED", log.getEventType());
    }

    @Test
    void handleOrderCancelled_savesAuditLog() {
        UUID orderId = UUID.randomUUID();
        EventRecord event = EventRecord.create("ORDER_CANCELLED", Topics.ORDER_CANCELLED, orderId.toString(), null);
        String json = event.toJson();

        consumerService.handleOrderCancelled(json);

        ArgumentCaptor<NxAuditLog> captor = ArgumentCaptor.forClass(NxAuditLog.class);
        verify(auditLogRepository).save(captor.capture());
        assertEquals(orderId, captor.getValue().getEntityId());
        assertEquals("CANCELLED", captor.getValue().getEventType());
    }

    @Test
    void handleShipmentCreated_savesAuditLog() {
        UUID shipmentId = UUID.randomUUID();
        EventRecord event = EventRecord.create("SHIPMENT_CREATED", Topics.SHIPMENT_CREATED, shipmentId.toString(), null);
        String json = event.toJson();

        consumerService.handleShipmentCreated(json);

        ArgumentCaptor<NxAuditLog> captor = ArgumentCaptor.forClass(NxAuditLog.class);
        verify(auditLogRepository).save(captor.capture());
        assertEquals(shipmentId, captor.getValue().getEntityId());
        assertEquals("SHIPMENT", captor.getValue().getEntityType());
    }

    @Test
    void handleInventoryAdjusted_savesAuditLog() {
        UUID inventoryId = UUID.randomUUID();
        EventRecord event = EventRecord.create("INVENTORY_ADJUSTED", Topics.INVENTORY_ADJUSTED, inventoryId.toString(), null);
        String json = event.toJson();

        consumerService.handleInventoryAdjusted(json);

        verify(auditLogRepository).save(argThat(log ->
                log.getEntityId().equals(inventoryId) && "INVENTORY".equals(log.getEntityType())));
    }

    @Test
    void handlePaymentReceived_savesAuditLog() {
        UUID paymentId = UUID.randomUUID();
        EventRecord event = EventRecord.create("PAYMENT_RECEIVED", Topics.PAYMENT_RECEIVED, paymentId.toString(), null);
        String json = event.toJson();

        consumerService.handlePaymentReceived(json);

        verify(auditLogRepository).save(argThat(log ->
                log.getEntityId().equals(paymentId) && "PAYMENT".equals(log.getEntityType())));
    }

    @Test
    void handleReturnCreated_savesAuditLog() {
        UUID returnId = UUID.randomUUID();
        EventRecord event = EventRecord.create("RETURN_CREATED", Topics.RETURN_CREATED, returnId.toString(), null);
        String json = event.toJson();

        consumerService.handleReturnCreated(json);

        verify(auditLogRepository).save(argThat(log ->
                log.getEntityId().equals(returnId) && "RETURN".equals(log.getEntityType())));
    }

    @Test
    void onProcessingError_sendsToDlq() {
        UUID orderId = UUID.randomUUID();
        EventRecord event = EventRecord.create("ORDER_CREATED", Topics.ORDER_CREATED, orderId.toString(), null);
        String json = event.toJson();

        doThrow(new RuntimeException("db error")).when(auditLogRepository).save(any());

        consumerService.handleOrderCreated(json);

        verify(auditLogRepository).save(any());
        verify(kafkaTemplate).send(eq(Topics.dlq(Topics.ORDER_CREATED)), eq(json));
    }

    @Test
    void onProcessingError_whenDlqFails_doesNotThrow() {
        UUID orderId = UUID.randomUUID();
        EventRecord event = EventRecord.create("ORDER_CREATED", Topics.ORDER_CREATED, orderId.toString(), null);
        String json = event.toJson();

        doThrow(new RuntimeException("db error")).when(auditLogRepository).save(any());
        doThrow(new RuntimeException("kafka down")).when(kafkaTemplate).send(anyString(), anyString());

        assertDoesNotThrow(() -> consumerService.handleOrderCreated(json));
    }
}
