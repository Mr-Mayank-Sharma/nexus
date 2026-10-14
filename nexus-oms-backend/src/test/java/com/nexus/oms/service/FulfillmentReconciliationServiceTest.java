package com.nexus.oms.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.nexus.oms.dto.reconciliation.MatchType;
import com.nexus.oms.dto.reconciliation.ReconciliationReportRow;
import com.nexus.oms.dto.reconciliation.ReconciliationResult;
import com.nexus.oms.dto.reconciliation.Resolution;
import com.nexus.oms.dto.reconciliation.ShopifyFulfillment;
import com.nexus.oms.dto.reconciliation.ShopifyFulfillmentLine;
import com.nexus.oms.entity.NxIntegrationStore;
import com.nexus.oms.entity.NxOrder;
import com.nexus.oms.entity.NxOrderItem;
import com.nexus.oms.entity.NxFulfillmentReconciliation;
import com.nexus.oms.kafka.KafkaProducerService;
import com.nexus.oms.repository.FulfillmentReconciliationRepository;
import com.nexus.oms.repository.NxIntegrationStoreRepository;
import com.nexus.oms.repository.OrderItemRepository;
import com.nexus.oms.repository.OrderRepository;
import com.nexus.oms.security.TenantContext;
import com.nexus.oms.service.shopify.ShopifyClient;
import com.nexus.oms.service.shopify.ShopifyTokenService;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class FulfillmentReconciliationServiceTest {

    private static final UUID TENANT_ID = UUID.randomUUID();
    private static final UUID ORDER_ID = UUID.randomUUID();
    private static final UUID ITEM_ID = UUID.randomUUID();
    private static final UUID NODE_ID = UUID.randomUUID();

    @Mock FulfillmentReconciliationRepository reconciliationRepository;
    @Mock OrderRepository orderRepository;
    @Mock OrderItemRepository orderItemRepository;
    @Mock InventoryService inventoryService;
    @Mock KafkaProducerService kafkaProducerService;
    @Mock NxIntegrationStoreRepository storeRepository;
    @Mock IntegrationStoreService storeService;
    @Mock ShopifyTokenService tokenService;
    @Mock ShopifyClient shopifyClient;

    private FulfillmentReconciliationService service;

    @BeforeEach
    void setUp() {
        // consumeAllocated reports how many units it really took off on-hand
        lenient().when(inventoryService.consumeAllocated(any(), any(), any(), anyInt(), any(), any()))
                .thenAnswer(inv -> inv.getArgument(3));
        service = new FulfillmentReconciliationService(
                reconciliationRepository, orderRepository, orderItemRepository, inventoryService,
                kafkaProducerService, storeRepository, storeService, tokenService, shopifyClient,
                new ObjectMapper());
        TenantContext.setCurrentTenantId(TENANT_ID);
    }

    @AfterEach
    void tearDown() {
        TenantContext.clear();
    }

    // ====================================================================
    // acceptFulfillment
    // ====================================================================

    @Test
    void acceptFulfillment_exactMatch_acceptsAndPublishesEvent() {
        NxOrder order = order("PROCESSING");
        NxOrderItem item = item("SKU-1", 2, 0);
        when(orderRepository.findById(ORDER_ID)).thenReturn(Optional.of(order));
        when(orderItemRepository.findByOrderId(ORDER_ID)).thenReturn(List.of(item));

        ReconciliationResult result = service.acceptFulfillment(ORDER_ID, fulfillment("F-1", line("SKU-1", 2, null)));

        assertEquals(Resolution.ACCEPTED, result.getResolution());
        assertEquals(MatchType.EXACT, result.getMatchType());
        assertEquals(-2, result.getInventoryDelta());
        assertEquals(2, item.getFulfilledQty());
        assertEquals("FULFILLED", order.getStatus());
        verify(inventoryService).consumeAllocated(eq(TENANT_ID), eq("SKU-1"), eq(NODE_ID), eq(2), eq("ORDER_FULFILLMENT"), any());
        verify(kafkaProducerService).publishEntityEvent("order.fulfilled", ORDER_ID.toString());
        verify(reconciliationRepository).save(any(NxFulfillmentReconciliation.class));
        verify(orderRepository).save(order);
    }

    @Test
    void acceptFulfillment_partialQuantity_partialAccepted() {
        NxOrder order = order("PROCESSING");
        NxOrderItem item = item("SKU-1", 2, 0);
        when(orderRepository.findById(ORDER_ID)).thenReturn(Optional.of(order));
        when(orderItemRepository.findByOrderId(ORDER_ID)).thenReturn(List.of(item));

        ReconciliationResult result = service.acceptFulfillment(ORDER_ID, fulfillment("F-1", line("SKU-1", 1, null)));

        assertEquals(Resolution.PARTIAL_ACCEPTED, result.getResolution());
        assertEquals(MatchType.QUANTITY_DELTA, result.getMatchType());
        assertEquals(1, item.getFulfilledQty());
        assertEquals("PROCESSING", order.getStatus());
        verify(inventoryService).consumeAllocated(eq(TENANT_ID), eq("SKU-1"), eq(NODE_ID), eq(1), eq("ORDER_FULFILLMENT"), any());
        verify(kafkaProducerService).publishEntityEvent("order.fulfilled", ORDER_ID.toString());
        verify(orderRepository, never()).save(any());
    }

    @Test
    void acceptFulfillment_unknownSku_flagsWithoutMutatingState() {
        NxOrder order = order("PROCESSING");
        NxOrderItem item = item("SKU-1", 2, 0);
        when(orderRepository.findById(ORDER_ID)).thenReturn(Optional.of(order));
        when(orderItemRepository.findByOrderId(ORDER_ID)).thenReturn(List.of(item));

        ReconciliationResult result = service.acceptFulfillment(ORDER_ID, fulfillment("F-1", line("SKU-X", 1, null)));

        assertEquals(Resolution.FLAGGED, result.getResolution());
        assertEquals(MatchType.ANOMALY, result.getMatchType());
        assertEquals(0, result.getInventoryDelta());
        assertEquals(0, item.getFulfilledQty());
        assertEquals("PROCESSING", order.getStatus());
        verifyNoInteractions(inventoryService);
        verifyNoInteractions(kafkaProducerService);
        verify(orderRepository, never()).save(any());
        verify(reconciliationRepository).save(any(NxFulfillmentReconciliation.class));
    }

    @Test
    void acceptFulfillment_duplicateFulfillmentId_isIdempotent() {
        NxFulfillmentReconciliation existing = NxFulfillmentReconciliation.builder()
                .tenantId(TENANT_ID)
                .orderId(ORDER_ID)
                .orderNumber("SO-1001")
                .matchType("EXACT")
                .resolution("ACCEPTED")
                .inventoryDelta(-2)
                .notes("already accepted")
                .build();
        when(reconciliationRepository.findByTenantIdAndShopifyFulfillmentId(TENANT_ID, "F-1"))
                .thenReturn(Optional.of(existing));

        ReconciliationResult result = service.acceptFulfillment(ORDER_ID, fulfillment("F-1", line("SKU-1", 2, null)));

        assertEquals(Resolution.ACCEPTED, result.getResolution());
        assertEquals(MatchType.EXACT, result.getMatchType());
        assertEquals(-2, result.getInventoryDelta());
        verifyNoInteractions(orderRepository);
        verifyNoInteractions(inventoryService);
        verify(reconciliationRepository, never()).save(any());
    }

    @Test
    void acceptFulfillment_locationChange_detectedAsTruth() {
        NxOrder order = order("PROCESSING");
        NxOrderItem item = item("SKU-1", 2, 0);
        when(orderRepository.findById(ORDER_ID)).thenReturn(Optional.of(order));
        when(orderItemRepository.findByOrderId(ORDER_ID)).thenReturn(List.of(item));

        ReconciliationResult result = service.acceptFulfillment(ORDER_ID, fulfillment("F-1", line("SKU-1", 2, "LOC-B")));

        assertEquals(MatchType.LOCATION_CHANGE, result.getMatchType());
        assertEquals(Resolution.ACCEPTED, result.getResolution());
        assertTrue(result.getNotes().contains("LOC-B"));
        assertEquals("FULFILLED", order.getStatus());
    }

    // ====================================================================
    // handleFulfillmentWebhook
    // ====================================================================

    @Test
    void handleFulfillmentWebhook_unknownOrder_isNoOp() {
        NxIntegrationStore store = new NxIntegrationStore();
        store.setTenantId(TENANT_ID);
        when(orderRepository.findByTenantIdAndChannelOrderId(TENANT_ID, "1001")).thenReturn(Optional.empty());

        service.handleFulfillmentWebhook(store, Map.of("id", "1001", "fulfillments", List.of()));

        verify(reconciliationRepository, never()).save(any());
        verifyNoInteractions(inventoryService);
    }

    @Test
    void handleFulfillmentWebhook_knownOrder_acceptsFulfillment() {
        NxIntegrationStore store = new NxIntegrationStore();
        store.setTenantId(TENANT_ID);
        NxOrder order = order("PROCESSING");
        NxOrderItem item = item("SKU-1", 2, 0);
        when(orderRepository.findByTenantIdAndChannelOrderId(TENANT_ID, "1001")).thenReturn(Optional.of(order));
        when(orderRepository.findById(ORDER_ID)).thenReturn(Optional.of(order));
        when(orderItemRepository.findByOrderId(ORDER_ID)).thenReturn(List.of(item));

        Map<String, Object> payload = Map.of(
                "id", "1001",
                "fulfillments", List.of(Map.of(
                        "id", "F-1",
                        "status", "fulfilled",
                        "line_items", List.of(Map.of(
                                "id", "LI-1", "sku", "SKU-1", "quantity", 2)))));

        service.handleFulfillmentWebhook(store, payload);

        assertEquals("FULFILLED", order.getStatus());
        verify(reconciliationRepository).save(any(NxFulfillmentReconciliation.class));
        verify(inventoryService).consumeAllocated(eq(TENANT_ID), eq("SKU-1"), eq(NODE_ID), eq(2), eq("ORDER_FULFILLMENT"), any());
    }

    // ====================================================================
    // Report & history
    // ====================================================================

    @Test
    void getMismatchReport_mapsFlaggedRows() {
        NxFulfillmentReconciliation rec = NxFulfillmentReconciliation.builder()
                .tenantId(TENANT_ID)
                .orderId(ORDER_ID)
                .orderNumber("SO-1001")
                .matchType("ANOMALY")
                .resolution("FLAGGED")
                .inventoryDelta(0)
                .notes("manual review required")
                .build();
        when(reconciliationRepository.findByTenantIdAndResolutionInOrderByCreatedAtDesc(
                TENANT_ID, List.of("FLAGGED", "REPAIRED")))
                .thenReturn(List.of(rec));

        List<ReconciliationReportRow> rows = service.getMismatchReport(TENANT_ID);

        assertEquals(1, rows.size());
        assertEquals(ORDER_ID, rows.get(0).getOrderId());
        assertEquals("FLAGGED", rows.get(0).getResolution());
        assertEquals("manual review required", rows.get(0).getReason());
    }

    @Test
    void getOrderHistory_returnsLedgerRows() {
        NxFulfillmentReconciliation rec = NxFulfillmentReconciliation.builder()
                .tenantId(TENANT_ID)
                .orderId(ORDER_ID)
                .orderNumber("SO-1001")
                .build();
        when(reconciliationRepository.findByTenantIdAndOrderIdOrderByCreatedAtDesc(TENANT_ID, ORDER_ID))
                .thenReturn(List.of(rec));

        List<NxFulfillmentReconciliation> history = service.getOrderHistory(TENANT_ID, ORDER_ID);

        assertEquals(1, history.size());
        assertEquals(ORDER_ID, history.get(0).getOrderId());
    }

    // ====================================================================
    // Helpers
    // ====================================================================

    private NxOrder order(String status) {
        NxOrder order = new NxOrder();
        order.setId(ORDER_ID);
        order.setTenantId(TENANT_ID);
        order.setStatus(status);
        order.setChannelOrderId("SO-1001");
        return order;
    }

    private NxOrderItem item(String sku, int qty, int fulfilledQty) {
        NxOrderItem item = new NxOrderItem();
        item.setId(ITEM_ID);
        item.setSku(sku);
        item.setQuantity(qty);
        item.setFulfilledQty(fulfilledQty);
        item.setAllocatedNodeId(NODE_ID);
        return item;
    }

    private ShopifyFulfillment fulfillment(String id, ShopifyFulfillmentLine... lines) {
        return ShopifyFulfillment.builder()
                .shopifyFulfillmentId(id)
                .status("fulfilled")
                .lineItems(List.of(lines))
                .build();
    }

    private ShopifyFulfillmentLine line(String sku, int qty, String locationId) {
        return ShopifyFulfillmentLine.builder()
                .sku(sku)
                .quantity(qty)
                .locationId(locationId)
                .build();
    }
}