package com.nexus.core;

import com.nexus.core.service.FacilityService;
import com.nexus.core.service.InventoryService;
import com.nexus.core.service.PartyService;
import com.nexus.core.service.ProductService;
import com.nexus.core.service.PromiseService;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestInstance;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.transaction.support.TransactionTemplate;

import java.math.BigDecimal;
import java.sql.Timestamp;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.Map;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;

@SpringBootTest(classes = com.nexus.oms.NexusOmsApplication.class)
@ActiveProfiles("test")
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
@org.junit.jupiter.api.TestMethodOrder(org.junit.jupiter.api.MethodOrderer.OrderAnnotation.class)
class PromiseServiceTest {

    @Autowired PromiseService promise;
    @Autowired FacilityService facilities;
    @Autowired InventoryService inventory;
    @Autowired ProductService products;
    @Autowired PartyService parties;
    @Autowired TransactionTemplate tx;
    @Autowired JdbcTemplate jdbc;

    String edison, chicago, boot, rare, party;
    static final PromiseService.Destination HOBOKEN = new PromiseService.Destination(40.744, -74.0324, "07030", "US");
    static final LocalDateTime WEDNESDAY_10 = LocalDateTime.of(2026, 10, 21, 10, 0);   // a Wednesday, UTC

    @BeforeAll
    void seed() {
        CoreTestData.ensureSeeded(jdbc);
        tx.executeWithoutResult(s -> {
            edison = facilities.create(new FacilityService.NewFacility(null, "DISTRIBUTION_CENTER", "Promise Edison DC", null, null, null,
                    new BigDecimal("40.52"), new BigDecimal("-74.41"), null, null, null, null));
            chicago = facilities.create(new FacilityService.NewFacility(null, "DISTRIBUTION_CENTER", "Promise Chicago DC", null, null, null,
                    new BigDecimal("41.88"), new BigDecimal("-87.63"), null, null, null, 0L));
            boot = products.create(new ProductService.NewProduct(null, "Promise Boot", null, null, null, null, null, false, false));
            rare = products.create(new ProductService.NewProduct(null, "Promise Rare", null, null, null, null, null, false, false));
            inventory.receive(boot, edison, new BigDecimal("10"), null, null, null, null);
            inventory.receive(boot, chicago, new BigDecimal("10"), null, null, null, null);
            inventory.receive(rare, chicago, new BigDecimal("1"), null, null, null, null);
            party = parties.createPerson(PartyService.PersonName.parse("Promise Customer"), null);
        });
    }

    @Test
    @org.junit.jupiter.api.Order(1)
    void shipsTodayBeforeTheCutoffAndUsesDistanceRulesWithoutHistory() {
        PromiseService.Answer a = promise.promise(Map.of(boot, BigDecimal.ONE), HOBOKEN, "STANDARD", WEDNESDAY_10);
        assertTrue(a.deliverable());
        PromiseService.Option e = a.options().stream().filter(o -> o.facilityId().equals(edison)).findFirst().orElseThrow();
        assertEquals(LocalDate.of(2026, 10, 21), e.shipDate(), "before 15:00: ships today");
        assertEquals("rule", e.basis());
        assertEquals(LocalDate.of(2026, 10, 22), e.expectedDelivery(), "about 40 km: one working day");
        assertEquals(LocalDate.of(2026, 10, 23), e.promiseBy());
        assertEquals(edison, a.best().facilityId());
        PromiseService.Option c = a.options().stream().filter(o -> o.facilityId().equals(chicago)).findFirst().orElseThrow();
        assertEquals(LocalDate.of(2026, 10, 22), c.shipDate(), "a daily limit of 0: full today, ships tomorrow");
        assertTrue(c.reasons().stream().anyMatch(r -> r.startsWith("full for today")), c.reasons().toString());
        assertTrue(a.summary().startsWith("Delivered by 2026-10-23"), a.summary());
    }

    @Test
    @org.junit.jupiter.api.Order(2)
    void afterTheCutoffOnFridayItShipsMonday() {
        PromiseService.Answer a = promise.promise(Map.of(boot, BigDecimal.ONE), HOBOKEN, "STANDARD", LocalDateTime.of(2026, 10, 23, 18, 0));
        assertEquals(LocalDate.of(2026, 10, 26), a.best().shipDate());
        assertEquals(LocalDate.of(2026, 10, 27), a.best().expectedDelivery());
        assertFalse(promise.promise(Map.of(rare, new BigDecimal("2")), HOBOKEN, "STANDARD", WEDNESDAY_10).deliverable(), "nobody holds two");
        assertEquals(chicago, promise.promise(Map.of(rare, BigDecimal.ONE), HOBOKEN, "NEXT_DAY", WEDNESDAY_10).best().facilityId());
    }

    @Test
    @org.junit.jupiter.api.Order(3)   // last: the history it adds changes Edison's basis
    void historyTakesOverOnceThereIsEnoughOfIt() {
        // 25 past deliveries from Edison to Hoboken: 20 took 2 working days, 5 took 4
        String address = tx.execute(s -> parties.addPostalAddress(party, new PartyService.AddressInput("Promise Customer", "1 Hudson St", null, "Hoboken", "NJ",
                "07030", "US", new BigDecimal("40.744"), new BigDecimal("-74.0324"), true), "SHIPPING_LOCATION"));
        for (int i = 0; i < 25; i++) {
            String id = "PRM" + UUID.randomUUID().toString().substring(0, 8);
            LocalDateTime start = LocalDateTime.of(2026, 9, 7, 10, 0).plusWeeks(i % 4);   // Mondays
            LocalDateTime arrive = start.plusDays(i < 20 ? 2 : 4);
            jdbc.update("INSERT INTO shipment (shipment_id, shipment_type_id, status_id, origin_facility_id, destination_contact_mech_id) VALUES (?, 'SALES_SHIPMENT', 'SHIPMENT_DELIVERED', ?, ?)",
                    id, edison, address);
            jdbc.update("INSERT INTO shipment_route_segment (shipment_id, shipment_route_segment_id, shipment_method_type_id, actual_start_date, actual_arrival_date) "
                    + "VALUES (?, '00001', 'STANDARD', ?, ?)", id, Timestamp.valueOf(start), Timestamp.valueOf(arrive));
        }
        PromiseService.Option e = promise.promise(Map.of(boot, BigDecimal.ONE), HOBOKEN, "STANDARD", WEDNESDAY_10).options().stream()
                .filter(o -> o.facilityId().equals(edison)).findFirst().orElseThrow();
        assertEquals("history", e.basis());
        assertEquals("HIGH", e.confidence());
        assertEquals(2, e.transitDaysExpected());
        assertEquals(4, e.transitDaysPromise(), "the 90th percentile: late costs trust");
        assertTrue(e.reasons().stream().anyMatch(r -> r.startsWith("from 25 past deliveries")), e.reasons().toString());
    }
}
