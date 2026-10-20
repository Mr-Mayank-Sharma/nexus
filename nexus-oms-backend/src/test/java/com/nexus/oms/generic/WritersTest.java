package com.nexus.oms.generic;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.nexus.oms.entity.*;
import com.nexus.oms.repository.*;
import com.nexus.oms.service.InventoryService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.SimpleTransactionStatus;

import java.math.BigDecimal;
import java.util.*;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

/** The order and stock writers: what they accept, what they refuse, and that dry runs write nothing. */
class WritersTest {

    final UUID tenant = UUID.randomUUID();
    OrderRepository orders = mock(OrderRepository.class);
    OrderItemRepository items = mock(OrderItemRepository.class);
    CustomerRepository customers = mock(CustomerRepository.class);
    AddressRepository addresses = mock(AddressRepository.class);
    PlatformTransactionManager txm = mock(PlatformTransactionManager.class);
    GenericOrderWriter orderWriter;

    InventoryRepository inventory = mock(InventoryRepository.class);
    InventoryService inventoryService = mock(InventoryService.class);
    ProductRepository products = mock(ProductRepository.class);
    NodeRepository nodes = mock(NodeRepository.class);
    GenericStockWriter stockWriter;

    @BeforeEach
    void setUp() {
        when(txm.getTransaction(any())).thenReturn(new SimpleTransactionStatus());
        orderWriter = new GenericOrderWriter(orders, items, customers, addresses, txm);
        when(orders.save(any())).thenAnswer(a -> { NxOrder o = a.getArgument(0); o.setId(UUID.randomUUID()); return o; });
        when(customers.save(any())).thenAnswer(a -> { NxCustomer c = a.getArgument(0); c.setId(UUID.randomUUID()); return c; });
        when(addresses.save(any())).thenAnswer(a -> { Address x = a.getArgument(0); x.setId(UUID.randomUUID()); return x; });
        stockWriter = new GenericStockWriter(inventory, inventoryService, products, nodes);
    }

    static GenericMapper.Mapped order(Map<String, String> f, List<Map<String, String>> lines) {
        return new GenericMapper.Mapped(new LinkedHashMap<>(f), lines, null);
    }

    static final List<Map<String, String>> ONE_LINE = List.of(Map.of("sku", "JKT-M", "quantity", "2", "unitPrice", "40.00"));

    @Test
    void problemsListEverythingWrongWithAnOrder() {
        var m = order(Map.of("orderId", "", "currency", "dollars", "orderType", "WHOLESALE", "total", "abc", "requestedShipDate", "soon"),
                List.of(Map.of("sku", "", "quantity", "0"), Map.of("sku", "A", "quantity", "1.5", "unitPrice", "x")));
        List<String> p = GenericOrderWriter.problems(m);
        assertTrue(p.contains("orderId is empty"));
        assertTrue(p.stream().anyMatch(s -> s.startsWith("line 1: sku is empty")));
        assertTrue(p.stream().anyMatch(s -> s.startsWith("line 1: quantity")));
        assertTrue(p.stream().anyMatch(s -> s.startsWith("line 2: quantity")), "1.5 is not a whole number");
        assertTrue(p.stream().anyMatch(s -> s.startsWith("line 2: unitPrice")));
        assertTrue(p.contains("total is not a number"));
        assertTrue(p.stream().anyMatch(s -> s.startsWith("currency")));
        assertTrue(p.contains("orderType must be B2C or B2B"));
        assertTrue(p.contains("requestedShipDate is not a date"));
        assertTrue(GenericOrderWriter.problems(order(Map.of("orderId", "1"), List.of())).contains("the order has no lines"));
    }

    @Test
    void anOrderAlreadyImportedIsSkipped() {
        when(orders.findByTenantIdAndChannelAndChannelOrderId(tenant, "WOOCOMMERCE", "77")).thenReturn(Optional.of(new NxOrder()));
        assertEquals(GenericOrderWriter.Result.ALREADY_IMPORTED,
                orderWriter.write(tenant, "woocommerce", "Woo", order(Map.of("orderId", "77"), ONE_LINE), true));
        verify(orders, never()).save(any());
    }

    @Test
    void aDryRunWritesNothing() {
        when(orders.findByTenantIdAndChannelAndChannelOrderId(any(), any(), any())).thenReturn(Optional.empty());
        assertEquals(GenericOrderWriter.Result.WOULD_CREATE, orderWriter.write(tenant, "X", "c", order(Map.of("orderId", "1"), ONE_LINE), false));
        verifyNoInteractions(customers, addresses, items);
        verify(orders, never()).save(any());
    }

    @Test
    void aB2bOrderIsSavedWithItsLinesShipDateAndTotals() throws Exception {
        when(orders.findByTenantIdAndChannelAndChannelOrderId(any(), any(), any())).thenReturn(Optional.empty());
        when(customers.findAllByTenantIdAndEmail(tenant, "buyer@acme.com")).thenReturn(List.of());
        var m = order(Map.of("orderId", "PO-881", "orderNumber", "881", "email", "Buyer@Acme.com", "customerName", "Acme Stores",
                "orderType", "b2b", "requestedShipDate", "2027-01-15", "currency", "eur", "shipCity", "Lyon", "shipLine1", "1 Rue X"), ONE_LINE);
        assertEquals(GenericOrderWriter.Result.CREATED, orderWriter.write(tenant, "portal", "B2B portal", m, true));

        ArgumentCaptor<NxOrder> o = ArgumentCaptor.forClass(NxOrder.class);
        verify(orders).save(o.capture());
        NxOrder saved = o.getValue();
        assertEquals("PORTAL", saved.getChannel());
        assertEquals("PO-881", saved.getChannelOrderId());
        assertEquals("881", saved.getExternalId());
        assertEquals("PENDING", saved.getStatus());
        assertEquals("EUR", saved.getCurrency());
        assertEquals(0, new BigDecimal("80.00").compareTo(saved.getSubtotal()), "subtotal falls back to the lines");
        assertEquals(0, new BigDecimal("80.00").compareTo(saved.getTotal()));
        assertNotNull(saved.getShipToAddressId());
        var meta = new ObjectMapper().readTree(saved.getMetadata());
        assertEquals("B2B", meta.get("orderType").asText());
        assertEquals("2027-01-15T00:00:00Z", meta.get("requestedShipDate").asText());

        ArgumentCaptor<NxOrderItem> it = ArgumentCaptor.forClass(NxOrderItem.class);
        verify(items).save(it.capture());
        assertEquals("JKT-M", it.getValue().getSku());
        assertEquals(2, it.getValue().getQuantity());
        assertEquals(0, new BigDecimal("80.00").compareTo(it.getValue().getTotalPrice()));
    }

    @Test
    void cancelledAndRefundedKeepTheirState() {
        assertEquals("CANCELLED", GenericOrderWriter.mapStatus("Cancelled"));
        assertEquals("RETURNED", GenericOrderWriter.mapStatus("refunded"));
        assertEquals("PENDING", GenericOrderWriter.mapStatus("processing"));
        assertEquals("PENDING", GenericOrderWriter.mapStatus(null));
    }

    // ---------------------------------------------------------------- stock

    NxNode node(String name) {
        NxNode n = new NxNode();
        n.setId(UUID.randomUUID());
        n.setName(name);
        return n;
    }

    static GenericMapper.Mapped stock(String sku, String qty, String loc) {
        Map<String, String> f = new LinkedHashMap<>();
        f.put("sku", sku); f.put("quantity", qty); f.put("location", loc);
        return new GenericMapper.Mapped(f, List.of(), null);
    }

    @Test
    void stockLocationsResolveByMapThenIdThenName() {
        NxNode east = node("Warehouse East"), nyc = node("Store NYC");
        when(nodes.findByTenantId(tenant)).thenReturn(List.of(east, nyc));
        var run = stockWriter.start(tenant, Map.of("WH-01", east.getId().toString()), "ERP");
        assertEquals(east.getId(), run.location("WH-01"));
        assertEquals(nyc.getId(), run.location(nyc.getId().toString()));
        assertEquals(nyc.getId(), run.location("store nyc"));
        assertNull(run.location("WH-99"));
        assertNull(run.location(null), "no location, and more than one exists");
    }

    @Test
    void stockRowsAreCreatedUpdatedOrLeftAloneThroughTheLedger() {
        NxNode east = node("East");
        when(nodes.findByTenantId(tenant)).thenReturn(List.of(east));
        when(products.existsByTenantIdAndSku(tenant, "A")).thenReturn(true);
        when(products.existsByTenantIdAndSku(tenant, "B")).thenReturn(true);
        when(products.existsByTenantIdAndSku(tenant, "C")).thenReturn(true);
        NxInventory b = NxInventory.builder().sku("B").nodeId(east.getId()).quantityOnHand(5).build();
        NxInventory c = NxInventory.builder().sku("C").nodeId(east.getId()).quantityOnHand(9).build();
        when(inventory.findByTenantIdAndSkuAndNodeId(tenant, "A", east.getId())).thenReturn(Optional.empty());
        when(inventory.findByTenantIdAndSkuAndNodeId(tenant, "B", east.getId())).thenReturn(Optional.of(b));
        when(inventory.findByTenantIdAndSkuAndNodeId(tenant, "C", east.getId())).thenReturn(Optional.of(c));
        var run = stockWriter.start(tenant, Map.of(), "ERP");

        // dry run first: nothing written
        assertEquals(GenericStockWriter.Result.WOULD_CREATE, run.write(stock("A", "3", null), false));
        assertEquals(GenericStockWriter.Result.WOULD_UPDATE, run.write(stock("B", "8", null), false));
        verifyNoInteractions(inventoryService);

        assertEquals(GenericStockWriter.Result.CREATED, run.write(stock("A", "3", null), true));
        assertEquals(GenericStockWriter.Result.UPDATED, run.write(stock("B", "8", null), true));
        assertEquals(GenericStockWriter.Result.UNCHANGED, run.write(stock("C", "9", null), true));
        verify(inventoryService).importOpeningBalance(argThat(i -> "A".equals(i.getSku()) && i.getQuantityOnHand() == 3), contains("ERP"));
        verify(inventoryService).adjustInventoryBySku(eq(tenant), eq("B"), eq(east.getId()), eq(3), contains("ERP"));
        verifyNoMoreInteractions(inventoryService);
    }

    @Test
    void stockRowsThatCannotBeUsedSayWhy() {
        NxNode a = node("A"), b = node("B");
        when(nodes.findByTenantId(tenant)).thenReturn(List.of(a, b));
        when(products.existsByTenantIdAndSku(tenant, "KNOWN")).thenReturn(true);
        var run = stockWriter.start(tenant, Map.of(), "POS");
        assertTrue(assertThrows(IllegalArgumentException.class, () -> run.write(stock("KNOWN", "1", null), true)).getMessage().contains("more than one location"));
        assertTrue(assertThrows(IllegalArgumentException.class, () -> run.write(stock("KNOWN", "1", "Z"), true)).getMessage().contains("not mapped"));
        assertTrue(assertThrows(IllegalArgumentException.class, () -> run.write(stock("NEW", "1", "A"), true)).getMessage().contains("not in the Nexus catalogue"));
        assertTrue(assertThrows(IllegalArgumentException.class, () -> run.write(stock("KNOWN", "lots", "A"), true)).getMessage().contains("whole number"));
        verifyNoInteractions(inventoryService);
    }
}
