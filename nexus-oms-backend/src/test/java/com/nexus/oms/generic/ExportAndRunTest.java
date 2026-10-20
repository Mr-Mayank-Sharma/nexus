package com.nexus.oms.generic;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.nexus.oms.entity.NxOrder;
import com.nexus.oms.entity.NxOrderItem;
import com.nexus.oms.exception.BadRequestException;
import com.nexus.oms.jobs.JobHandler;
import com.nexus.oms.repository.*;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.*;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

/** The exporter's mapping, batching and watermark, and a whole import run through the service. */
class ExportAndRunTest {

    static final ObjectMapper M = new ObjectMapper();
    final UUID tenant = UUID.randomUUID();

    OrderRepository orders = mock(OrderRepository.class);
    OrderItemRepository items = mock(OrderItemRepository.class);
    GenericHttp http = mock(GenericHttp.class);
    GenericExporter exporter = new GenericExporter(orders, items, mock(ShipmentRepository.class), mock(InventoryRepository.class), mock(NodeRepository.class), http);

    static GenericConfig export(String json) { return GenericConfig.parse(json, "EXPORT", "orders"); }

    NxOrder order(String number, LocalDateTime updated) {
        NxOrder o = NxOrder.builder().tenantId(tenant).channel("SHOPIFY").channelOrderId(number).status("SHIPPED")
                .currency("USD").total(new BigDecimal("12.50")).trackingNumber("1Z" + number)
                .metadata("{\"orderType\":\"B2B\",\"requestedShipDate\":\"2027-01-15T00:00:00Z\"}").build();
        o.setId(UUID.randomUUID());
        o.setUpdatedAt(updated);
        return o;
    }

    @Test
    void ordersAreMappedWithNestedTargetsTemplatesAndLines() {
        NxOrder o = order("1001", LocalDateTime.of(2026, 10, 1, 9, 0));
        when(orders.findTop2000ByTenantIdAndUpdatedAtAfterOrderByUpdatedAtAsc(eq(tenant), any())).thenReturn(List.of(o));
        when(items.findByOrderIdIn(List.of(o.getId()))).thenReturn(List.of(NxOrderItem.builder().orderId(o.getId()).sku("A").quantity(2).unitPrice(BigDecimal.ONE).build()));
        GenericConfig c = export("{\"request\":{\"baseUrl\":\"https://x.example.com\"},\"fields\":{\"order.number\":\"orderNumber\",\"order.type\":\"orderType\","
                + "\"status\":\"status|lower\",\"note\":\"{channel}-{orderNumber}\",\"source\":\"=nexus\"},\"lines\":{\"path\":\"items\",\"fields\":{\"code\":\"sku\",\"qty\":\"quantity\"}}}");
        var list = exporter.collect(tenant, "orders", c, null, 10);
        ObjectNode p = list.get(0).payload();
        assertEquals("1001", p.at("/order/number").asText());
        assertEquals("B2B", p.at("/order/type").asText());
        assertEquals("shipped", p.get("status").asText());
        assertEquals("SHOPIFY-1001", p.get("note").asText());
        assertEquals("nexus", p.get("source").asText());
        assertEquals("A", p.at("/items/0/code").asText());
        assertEquals("2", p.at("/items/0/qty").asText());
        assertEquals("1001", list.get(0).ref());
    }

    GenericExporter.Item item(String ref, int hour) {
        ObjectNode p = M.createObjectNode().put("n", ref);
        return new GenericExporter.Item(ref, p, LocalDateTime.of(2026, 10, 1, hour, 0));
    }

    @Test
    void batchesAreWrappedAndTheWatermarkFollowsWhatWasAccepted() throws Exception {
        GenericConfig c = export("{\"request\":{\"baseUrl\":\"https://x.example.com\"},\"delivery\":{\"batchSize\":2,\"wrapper\":\"orders\"},\"fields\":{\"n\":\"orderNumber\"}}");
        List<String> sent = new ArrayList<>();
        when(http.send(any(), any(), anyString())).thenAnswer(a -> { sent.add(a.getArgument(2)); if (sent.size() == 2) throw new BadRequestException("HTTP 422"); return 200; });
        var r = exporter.deliver(c, "s", List.of(item("a", 1), item("b", 2), item("c", 3), item("d", 4), item("e", 5)), null);
        assertEquals(2, r.sent(), "the first batch went; the second was refused, so the run stops there");
        assertEquals(LocalDateTime.of(2026, 10, 1, 2, 0), r.newWatermark(), "the watermark only covers accepted records");
        assertEquals(1, r.failedBatches());
        assertTrue(r.problems().get(0).contains("HTTP 422"));
        JsonNode first = M.readTree(sent.get(0));
        assertEquals(2, first.get("orders").size());
        assertEquals(2, sent.size(), "nothing after the refused batch is sent");
    }

    @Test
    void aSingleRecordBatchWithoutWrapperIsAnObject() {
        GenericConfig c = export("{\"request\":{\"baseUrl\":\"https://x.example.com\"},\"delivery\":{\"batchSize\":1},\"fields\":{\"n\":\"orderNumber\"}}");
        assertTrue(GenericExporter.body(c, List.of(item("a", 1))).isObject());
        GenericConfig many = export("{\"request\":{\"baseUrl\":\"https://x.example.com\"},\"fields\":{\"n\":\"orderNumber\"}}");
        assertTrue(GenericExporter.body(many, List.of(item("a", 1))).isArray());
    }

    @Test
    void fileExportFlattensOneRowPerLineAndIsSpreadsheetSafe() {
        ObjectNode p = M.createObjectNode().put("order", "=1+1");
        p.putObject("ship").put("city", "Lyon");
        p.putArray("lines").add(M.createObjectNode().put("sku", "A")).add(M.createObjectNode().put("sku", "B"));
        String csv = GenericExporter.csv(List.of(new GenericExporter.Item("1", p, null)));
        String[] rows = csv.split("\r\n");
        assertEquals("order,ship.city,lines.sku", rows[0]);
        assertEquals("'=1+1,Lyon,A", rows[1], "formula-looking cells are neutralised");
        assertEquals("'=1+1,Lyon,B", rows[2]);
    }

    @Test
    void fileDeliveryNeverCallsOut() {
        GenericConfig c = export("{\"delivery\":{\"mode\":\"FILE\"},\"fields\":{\"n\":\"orderNumber\"}}");
        var r = exporter.deliver(c, null, List.of(item("a", 3)), null);
        assertNotNull(r.csv());
        assertEquals(LocalDateTime.of(2026, 10, 1, 3, 0), r.newWatermark());
        verifyNoInteractions(http);
    }

    // ---------------------------------------------------------------- the service

    @Test
    void anImportRunCountsResultsListsFailedRowsAndMovesTheWatermarkOnlyWhenApplied() throws Exception {
        GenericConnectionRepository repo = mock(GenericConnectionRepository.class);
        GenericOrderWriter writer = mock(GenericOrderWriter.class);
        GenericConnectionService service = new GenericConnectionService(repo, http, writer, mock(GenericStockWriter.class), exporter);

        NxGenericConnection conn = new NxGenericConnection();
        conn.setId(UUID.randomUUID());
        conn.setTenantId(tenant);
        conn.setName("Woo");
        conn.setDirection("IMPORT");
        conn.setEntityType("orders");
        conn.setChannel("WOOCOMMERCE");
        conn.setConfig("{\"request\":{\"baseUrl\":\"https://x.example.com\"},\"fields\":{\"orderId\":\"id\"},\"lines\":{\"path\":\"items\",\"fields\":{\"sku\":\"sku\",\"quantity\":\"q\"}}}");
        when(repo.findByIdAndTenantId(conn.getId(), tenant)).thenReturn(Optional.of(conn));

        List<JsonNode> page = List.of(
                M.readTree("{\"id\":1,\"items\":[{\"sku\":\"A\",\"q\":1}]}"),
                M.readTree("{\"id\":2,\"items\":[{\"sku\":\"A\",\"q\":1}]}"),
                M.readTree("{\"id\":3,\"items\":[]}"));
        when(http.fetch(any(), any(), any(), anyInt(), any())).thenAnswer(a -> {
            java.util.function.Consumer<List<JsonNode>> sink = a.getArgument(4);
            sink.accept(page);
            return List.of(new GenericHttp.Page(1, "https://x.example.com", 3));
        });
        when(writer.write(eq(tenant), eq("WOOCOMMERCE"), eq("Woo"), argThat(m -> "1".equals(m.get("orderId"))), anyBoolean())).thenReturn(GenericOrderWriter.Result.CREATED);
        when(writer.write(eq(tenant), eq("WOOCOMMERCE"), eq("Woo"), argThat(m -> "2".equals(m.get("orderId"))), anyBoolean())).thenReturn(GenericOrderWriter.Result.ALREADY_IMPORTED);

        JobHandler.Outcome dry = service.runImport(tenant, conn.getId(), false, new JobHandler.Context(tenant, Map.of()));
        assertEquals(3, dry.total());
        assertEquals(1, dry.failed());
        assertEquals("3", dry.issues().get(0).rowRef());
        assertTrue(dry.issues().get(0).reason().contains("no lines"));
        assertNull(conn.getWatermark(), "a dry run never moves the watermark");
        verify(repo, never()).save(any());

        JobHandler.Outcome real = service.runImport(tenant, conn.getId(), true, new JobHandler.Context(tenant, Map.of()));
        assertTrue(real.message().contains("1 created") && real.message().contains("1 already imported") && real.message().contains("1 failed"), real.message());
        assertNotNull(conn.getWatermark());
        verify(repo).save(conn);
    }

    @Test
    void connectionsAreCheckedWhenSavedAndSecretsAreKeptOnUpdate() {
        GenericConnectionRepository repo = mock(GenericConnectionRepository.class);
        when(repo.save(any())).thenAnswer(a -> a.getArgument(0));
        GenericConnectionService service = new GenericConnectionService(repo, http, mock(GenericOrderWriter.class), mock(GenericStockWriter.class), exporter);
        Map<String, Object> cfg = Map.of("request", Map.of("baseUrl", "https://erp.example.com"), "fields", Map.of("sku", "code", "quantity", "qty"));

        assertThrows(BadRequestException.class, () -> service.create(tenant, new GenericConnectionService.ConnectionRequest("Bad", "IMPORT", "inventory", null, Map.of("fields", Map.of()), null)));
        NxGenericConnection c = service.create(tenant, new GenericConnectionService.ConnectionRequest("ERP stock", "import", "Inventory", null, cfg, "key-1"));
        assertEquals("IMPORT", c.getDirection());
        assertEquals("inventory", c.getEntityType());
        assertTrue(c.getHasSecret());

        when(repo.findByIdAndTenantId(c.getId(), tenant)).thenReturn(Optional.of(c));
        service.update(tenant, c.getId(), new GenericConnectionService.ConnectionRequest("ERP stock", "IMPORT", "inventory", null, cfg, ""));
        assertEquals("key-1", c.getSecret(), "an empty secret on update keeps the saved one");

        when(repo.existsByTenantIdAndName(tenant, "Taken")).thenReturn(true);
        assertThrows(BadRequestException.class, () -> service.create(tenant, new GenericConnectionService.ConnectionRequest("Taken", "IMPORT", "inventory", null, cfg, null)));
    }

    @Test
    void theSecretIsNeverSerialised() throws Exception {
        NxGenericConnection c = new NxGenericConnection();
        c.setSecret("top-secret");
        String json = new ObjectMapper().findAndRegisterModules().writeValueAsString(c);
        assertFalse(json.contains("top-secret"));
        assertTrue(json.contains("\"hasSecret\":true"));
    }
}
