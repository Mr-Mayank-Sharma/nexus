package com.nexus.core;

import com.nexus.core.job.CoreJobHandler;
import com.nexus.core.job.CoreMessageHandler;
import com.nexus.core.job.JobService;
import com.nexus.core.job.MessageService;
import com.nexus.core.service.CoreClock;
import com.nexus.core.service.FacilityService;
import com.nexus.core.service.InventoryService;
import com.nexus.core.service.OrderService;
import com.nexus.core.service.ProductService;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestInstance;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.ApplicationContext;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.transaction.support.TransactionTemplate;

import java.math.BigDecimal;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

@SpringBootTest(classes = com.nexus.oms.NexusOmsApplication.class)
@ActiveProfiles("test")
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
class JobsMessagesTest {

    @Autowired JobService jobs;
    @Autowired MessageService messages;
    @Autowired OrderService orders;
    @Autowired InventoryService inventory;
    @Autowired FacilityService facilities;
    @Autowired ProductService products;
    @Autowired ApplicationContext context;
    @Autowired TransactionTemplate tx;
    @Autowired JdbcTemplate jdbc;

    String product, facility;

    @BeforeAll
    void seed() {
        CoreTestData.ensureSeeded(jdbc);
        tx.executeWithoutResult(s -> {
            product = products.create(new ProductService.NewProduct(null, "Job Lamp", null, null, null, null, null, false, false));
            facility = facilities.create(new FacilityService.NewFacility(null, "WAREHOUSE", "Job WH", null, null, null, null, null, null, null, null, null));
            inventory.receive(product, facility, new BigDecimal("20"), null, null, null, null);
        });
    }

    /** An approved order of n lamps, routed to the warehouse and held there. */
    private String heldOrder(int n) {
        String o = tx.execute(s -> orders.create(new OrderService.NewOrder(null, null, null, "USD", null, null, null, null, null, null,
                List.of(new OrderService.NewShipGroup("STANDARD", facility, null, null, null, true, false, null, null, null, null)),
                List.of(new OrderService.NewItem(product, "Lamp", BigDecimal.valueOf(n), BigDecimal.TEN, null, null, 0)), null, null, null, null,
                "ORDER_APPROVED", null, "t")));
        tx.executeWithoutResult(s -> orders.reserve(o, "00001", facility, null, "t"));
        return o;
    }

    @Test
    void everySeededJobAndMessageTypeHasItsHandler() {
        for (Map<String, Object> j : jdbc.queryForList("SELECT job_name, service_name FROM service_job")) {
            assertNotNull(context.getBean((String) j.get("service_name"), CoreJobHandler.class), "job " + j.get("job_name"));
        }
        for (String h : jdbc.queryForList("SELECT consume_service_name FROM system_message_type WHERE consume_service_name IS NOT NULL", String.class)) {
            assertNotNull(context.getBean(h, CoreMessageHandler.class), h);
        }
        assertEquals("Y", jdbc.queryForObject("SELECT paused FROM service_job WHERE job_name = 'core_routing'", String.class), "routing waits for a person");
    }

    @Test
    void aJobRunsOnceAtATimeAndKeepsItsHistory() {
        JobService.Run r = jobs.runJob("core_auto_cancel", "admin").orElseThrow();
        assertFalse(r.hasError(), r.errors());
        assertTrue(r.results().endsWith("item(s) cancelled"), r.results());
        assertNull(jdbc.queryForObject("SELECT locked_until FROM service_job_run_lock WHERE job_name = 'core_auto_cancel'", java.sql.Timestamp.class));
        assertFalse(jobs.runs("core_auto_cancel", 5).isEmpty());
        assertEquals(0, jobs.prune(CoreClock.now().minusDays(7)), "recent runs are kept");

        // another server holds it
        jdbc.update("UPDATE service_job_run_lock SET locked_until = ?, locked_by = 'other-host' WHERE job_name = 'core_auto_cancel'",
                java.sql.Timestamp.valueOf(CoreClock.now().plusMinutes(5)));
        assertTrue(jobs.runJob("core_auto_cancel", "admin").isEmpty());
        assertFalse(jobs.due(CoreClock.now()).contains("core_auto_cancel"));
        jdbc.update("UPDATE service_job_run_lock SET locked_until = NULL WHERE job_name = 'core_auto_cancel'");

        IllegalArgumentException bad = assertThrows(IllegalArgumentException.class, () -> jobs.setSchedule("core_auto_cancel", "every five minutes"));
        assertTrue(bad.getMessage().contains("six fields"));
        jobs.setPaused("core_auto_cancel", true);
        assertFalse(jobs.due(CoreClock.now().plusDays(1)).contains("core_auto_cancel"));
        jobs.setPaused("core_auto_cancel", false);
        assertTrue(jobs.due(CoreClock.now().plusDays(1)).contains("core_auto_cancel"), "due again once its next time has come");
    }

    @Test
    void autoCancelReleasesAnExpiredHold() {
        String o = heldOrder(2);
        BigDecimal atp = inventory.atp(product, facility);
        jdbc.update("UPDATE order_item SET auto_cancel_date = ? WHERE order_id = ?", java.sql.Timestamp.valueOf(CoreClock.now().minusDays(1)), o);   // a day: JDBC writes in the test JVM zone
        jobs.runJob("core_auto_cancel", null).orElseThrow();
        assertEquals("ITEM_CANCELLED", jdbc.queryForObject("SELECT status_id FROM order_item WHERE order_id = ?", String.class, o));
        assertEquals(0, atp.add(new BigDecimal("2")).compareTo(inventory.atp(product, facility)));
    }

    @Test
    void aWmsShipmentConfirmationIsStoredOnceAndConsumed() {
        String o = heldOrder(2);
        String body = """
                {"shipmentId": "WMS-SH-%s", "orderId": "%s", "shipGroupSeqId": "00001", "shippedAt": "2026-10-22T17:41:00Z",
                 "carrierPartyId": "UPS", "shipmentMethodTypeId": "STANDARD",
                 "packages": [{"packageSeqId": "1", "trackingCode": "1ZJOB", "weight": "1.8", "weightUomId": "WT_kg",
                               "contents": [{"orderItemSeqId": "00001", "quantity": "2"}]}]}
                """.formatted(o, o);
        MessageService.Received first = messages.receive("WMS_SHIPMENT_CONFIRMATION", body, "ship-" + o, "wms-1", null);
        MessageService.Received again = messages.receive("WMS_SHIPMENT_CONFIRMATION", body, "ship-" + o, "wms-1", null);
        assertFalse(first.duplicate());
        assertTrue(again.duplicate());
        assertEquals(first.systemMessageId(), again.systemMessageId());

        assertTrue(messages.consume(first.systemMessageId()));
        assertEquals("SmsgConsumed", messages.view(first.systemMessageId()).orElseThrow().statusId());
        assertEquals("ORDER_COMPLETED", jdbc.queryForObject("SELECT status_id FROM order_header WHERE order_id = ?", String.class, o));
        assertEquals("1ZJOB", jdbc.queryForObject("SELECT tracking_number FROM order_item_ship_group WHERE order_id = ?", String.class, o));
    }

    @Test
    void aBadMessageIsKeptWithItsErrorAndRetriedLater() {
        MessageService.Received r = messages.receive("WMS_SHIPMENT_CONFIRMATION", "{not json", null, null, null);
        assertFalse(messages.consume(r.systemMessageId()));
        MessageService.MessageView v = messages.view(r.systemMessageId()).orElseThrow();
        assertEquals("SmsgError", v.statusId());
        assertEquals(1, v.failCount());
        assertTrue(v.lastError().contains("not JSON"), v.lastError());
        int[] c = messages.consumeDue(50);
        assertEquals("SmsgError", messages.view(r.systemMessageId()).orElseThrow().statusId(), "not retried before its wait is over");
        assertTrue(c[0] + c[1] >= 0);
        IllegalArgumentException unknown = assertThrows(IllegalArgumentException.class, () -> messages.receive("NOPE", "{}", null, null, null));
        assertTrue(unknown.getMessage().contains("does not accept"));
    }

    @Test
    void aWmsShortPickSendsTheItemBackToRouting() {
        String o = heldOrder(3);
        BigDecimal atp = inventory.atp(product, facility);
        MessageService.Received r = messages.receive("WMS_SHORT_PICK", """
                {"orderId": "%s", "shipGroupSeqId": "00001", "reasonEnumId": "NOT_FOUND", "stockAdjusted": false,
                 "items": [{"orderItemSeqId": "00001", "shortQuantity": "3"}]}
                """.formatted(o), "short-" + o, null, null);
        assertTrue(messages.consume(r.systemMessageId()), () -> messages.view(r.systemMessageId()).orElseThrow().lastError());
        assertEquals(0, atp.add(new BigDecimal("3")).compareTo(inventory.atp(product, facility)));
        assertNull(jdbc.queryForObject("SELECT facility_id FROM order_item_ship_group WHERE order_id = ? AND ship_group_seq_id = '00001'", String.class, o), "back in the routing queue");
        assertEquals(java.util.Set.of(facility), orders.excludedFacilities(o, "00001"));
    }
}
