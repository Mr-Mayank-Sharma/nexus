package com.nexus.oms.service;

import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

class EmailOrderTextParserTest {

    private final EmailOrderTextParser parser = new EmailOrderTextParser();

    @Test
    void extractOrderFromText_full() {
        String body = """
                Customer: John Doe
                Email: john@example.com
                Phone: 555-1234
                Order #: ORD-12345
                Total: $250.00
                SKU: ABC-123 x 2 - Widget A $50.00
                SKU: DEF-456 x 1 - Gadget B $150.00
                Ship To: 123 Main St
                Anytown, CA 90210
                Ship Via: FedEx
                """;

        Map<String, Object> data = parser.extractOrderFromText(body);

        assertEquals("John Doe", data.get("customerName"));
        assertEquals("john@example.com", data.get("customerEmail"));
        assertEquals("555-1234", data.get("customerPhone"));
        assertEquals("ORD-12345", data.get("orderNumber"));
        assertEquals("250.00", data.get("orderTotal"));
        assertEquals(2, data.get("itemCount"));
        assertEquals("FedEx", data.get("shippingMethod"));
        assertNotNull(data.get("shippingAddress"));
    }

    @Test
    void extractOrderFromText_noMatches() {
        Map<String, Object> data = parser.extractOrderFromText("Some random text with nothing useful");

        assertNull(data.get("customerName"));
        assertNull(data.get("customerEmail"));
        assertNull(data.get("orderNumber"));
        assertNull(data.get("orderTotal"));
        assertEquals(0, data.get("itemCount"));
        assertNull(data.get("shippingMethod"));
    }

    @Test
    void extractOrderFromText_tableFormat() {
        String body = """
                2 Widget A $50.00
                1 Gadget B $150.00
                """;

        Map<String, Object> data = parser.extractOrderFromText(body);

        assertEquals(2, data.get("itemCount"));
    }

    @Test
    void extractOrderFromText_purchaseOrderFormat() {
        String body = """
                PO Number: PO-2024-001
                Bill To: Acme Corp
                Total Amount: $1,234.56
                """;

        Map<String, Object> data = parser.extractOrderFromText(body);

        assertEquals("Acme Corp", data.get("customerName"));
        assertEquals("PO-2024-001", data.get("orderNumber"));
        assertEquals("1234.56", data.get("orderTotal"));
    }

    @Test
    void extractOrderFromText_customerLabel() {
        String body = "Name: Alice Smith";
        Map<String, Object> data = parser.extractOrderFromText(body);
        assertEquals("Alice Smith", data.get("customerName"));
    }

    @Test
    void extractOrderFromText_billToLabel() {
        String body = "Bill To: Bob Corp";
        Map<String, Object> data = parser.extractOrderFromText(body);
        assertEquals("Bob Corp", data.get("customerName"));
    }

    @Test
    void calculateConfidence_allSignals() {
        Map<String, Object> data = Map.of(
            "customerName", "John",
            "customerEmail", "j@t.com",
            "orderTotal", "100.00",
            "items", java.util.List.of(Map.of("sku", "A")),
            "orderNumber", "ORD-1",
            "shippingAddress", "123 Main St"
        );
        assertEquals(new BigDecimal("1.0000"), parser.calculateConfidence(data));
    }

    @Test
    void calculateConfidence_noSignals() {
        Map<String, Object> data = Map.of();
        assertEquals(0, BigDecimal.ZERO.compareTo(parser.calculateConfidence(data)));
    }

    @Test
    void calculateConfidence_halfSignals() {
        Map<String, Object> data = Map.of(
            "customerName", "John",
            "customerEmail", "j@t.com",
            "orderTotal", "100.00"
        );
        assertEquals(new BigDecimal("0.5000"), parser.calculateConfidence(data));
    }

    @Test
    void calculateConfidence_blankNameDoesNotCount() {
        Map<String, Object> data = Map.of(
            "customerName", "   ",
            "customerEmail", "",
            "orderTotal", "100.00"
        );
        assertEquals(new BigDecimal("0.1667"), parser.calculateConfidence(data));
    }
}
