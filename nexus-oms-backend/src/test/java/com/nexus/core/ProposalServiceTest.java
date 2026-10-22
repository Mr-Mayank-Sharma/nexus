package com.nexus.core;

import org.springframework.beans.factory.annotation.Qualifier;
import com.nexus.core.CoreTransactional;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.nexus.core.ai.ProposalService;
import com.nexus.core.service.FacilityService;
import com.nexus.core.service.InventoryService;
import com.nexus.core.service.OrderService;
import com.nexus.core.service.ProductService;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestInstance;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.transaction.support.TransactionTemplate;

import java.math.BigDecimal;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

@SpringBootTest(classes = com.nexus.oms.NexusOmsApplication.class)
@ActiveProfiles("test")
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
class ProposalServiceTest {

    static final ObjectMapper JSON = new ObjectMapper();
    @Autowired ProposalService proposals;
    @Autowired OrderService orders;
    @Autowired com.nexus.core.service.OrderReservationService reservations;
    @Autowired InventoryService inventory;
    @Autowired FacilityService facilities;
    @Autowired ProductService products;
    @Autowired @Qualifier(CoreTransactional.TEMPLATE) TransactionTemplate tx;
    @Autowired JdbcTemplate jdbc;

    String product, a, b;

    @BeforeAll
    void seed() {
        CoreTestData.ensureSeeded(jdbc);
        tx.executeWithoutResult(s -> {
            product = products.create(new ProductService.NewProduct(null, "Proposal Cap", null, null, null, null, null, false, false));
            a = facilities.create(new FacilityService.NewFacility(null, "RETAIL_STORE", "Prop A", null, null, null, null, null, null, null, null, null));
            b = facilities.create(new FacilityService.NewFacility(null, "RETAIL_STORE", "Prop B", null, null, null, null, null, null, null, null, null));
            inventory.receive(product, a, new BigDecimal("10"), null, null, null, null);
            inventory.receive(product, b, new BigDecimal("10"), null, null, null, null);
        });
    }

    private String orderAt(String facility) {
        String o = tx.execute(s -> orders.create(new OrderService.NewOrder(null, null, null, "USD", null, null, null, null, null, null,
                List.of(new OrderService.NewShipGroup("STANDARD", facility, null, null, null, true, false, null, null, null, null)),
                List.of(new OrderService.NewItem(product, "Cap", BigDecimal.ONE, BigDecimal.TEN, null, null, 0)), null, null, null, null, "ORDER_APPROVED", null, "t")));
        tx.executeWithoutResult(s -> reservations.reserve(o, "00001", facility, null, "t"));
        return o;
    }

    private String facilityOf(String o) {
        return jdbc.queryForObject("SELECT facility_id FROM order_item_ship_group WHERE order_id = ?", String.class, o);
    }

    @Test
    void aReallocationWaitsForAPersonThenAppliesAndUndoes() {
        String o = orderAt(a);
        ProposalService.View p = proposals.propose(null, "REALLOCATE", JSON.createObjectNode().put("orderId", o).put("facilityId", b), "A keeps rejecting", null);
        assertEquals("AIP_PENDING", p.statusId(), "REALLOCATE is ASK by default");
        assertEquals(a, facilityOf(o), "nothing changed yet");
        assertTrue(proposals.pending().stream().anyMatch(x -> x.proposalId().equals(p.proposalId())));

        ProposalService.View applied = proposals.approve(p.proposalId(), "manager");
        assertEquals("AIP_APPLIED", applied.statusId(), applied.result());
        assertEquals(b, facilityOf(o));
        assertTrue(jdbc.queryForObject("SELECT comments FROM order_facility_change WHERE order_id = ? AND facility_id = ?", String.class, o, b).contains("AI proposal"));

        assertEquals("AIP_UNDONE", proposals.undo(p.proposalId(), "manager").statusId());
        assertEquals(a, facilityOf(o));
    }

    @Test
    void anAutomaticPolicyAppliesAtOnceAndAFailureIsRecorded() {
        String o = orderAt(a);
        proposals.setPolicy("HOLD_ORDER", "AUTO", null, null);
        try {
            ProposalService.View p = proposals.propose(null, "HOLD_ORDER", JSON.createObjectNode().put("orderId", o), "Address looks wrong", null);
            assertEquals("AIP_APPLIED", p.statusId());
            assertEquals("ORDER_HOLD", jdbc.queryForObject("SELECT status_id FROM order_header WHERE order_id = ?", String.class, o));
            assertEquals("AIP_UNDONE", proposals.undo(p.proposalId(), "manager").statusId());
            assertEquals("ORDER_APPROVED", jdbc.queryForObject("SELECT status_id FROM order_header WHERE order_id = ?", String.class, o));
        } finally {
            proposals.setPolicy("HOLD_ORDER", "ASK", null, null);
        }
        ProposalService.View bad = proposals.propose(null, "RELEASE_HOLD", JSON.createObjectNode().put("orderId", o), "not on hold", null);
        ProposalService.View failed = proposals.approve(bad.proposalId(), "manager");
        assertEquals("AIP_FAILED", failed.statusId());
        assertTrue(failed.result().contains("is not on hold"), failed.result());
        assertThrows(IllegalArgumentException.class, () -> proposals.propose(null, "DELETE_EVERYTHING", JSON.createObjectNode(), null, null));
        assertThrows(IllegalArgumentException.class, () -> proposals.setPolicy("REALLOCATE", "AUTO_UNDER_LIMIT", null, null));
    }

    @Test
    void aCancellationCannotBeUndone() {
        String o = orderAt(a);
        ProposalService.View p = proposals.propose(null, "CANCEL_ITEM", JSON.createObjectNode().put("orderId", o).put("orderItemSeqId", "00001"), "duplicate", null);
        assertEquals("AIP_APPLIED", proposals.approve(p.proposalId(), "manager").statusId());
        IllegalStateException e = assertThrows(IllegalStateException.class, () -> proposals.undo(p.proposalId(), "manager"));
        assertTrue(e.getMessage().contains("can't be undone"));
    }
}
