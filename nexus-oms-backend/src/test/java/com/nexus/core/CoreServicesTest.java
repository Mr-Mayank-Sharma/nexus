package com.nexus.core;

import org.springframework.beans.factory.annotation.Qualifier;
import com.nexus.core.CoreTransactional;
import com.nexus.core.model.basic.Enumeration;
import com.nexus.core.model.basic.EnumerationType;
import com.nexus.core.model.basic.StatusFlow;
import com.nexus.core.model.basic.StatusFlowItem;
import com.nexus.core.model.basic.StatusFlowTransition;
import com.nexus.core.model.basic.StatusItem;
import com.nexus.core.model.basic.StatusType;
import com.nexus.core.service.EnumerationService;
import com.nexus.core.service.SequenceService;
import com.nexus.core.service.StatusService;
import com.nexus.core.service.SystemPropertyService;
import jakarta.persistence.EntityManager;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestInstance;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.transaction.support.TransactionTemplate;

import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;

import static org.junit.jupiter.api.Assertions.*;

/** The core framework services on the test database (H2; tables created from the entity mappings). */
@SpringBootTest(classes = com.nexus.oms.NexusOmsApplication.class)
@ActiveProfiles("test")
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
class CoreServicesTest {

    @Autowired SequenceService sequences;
    @Autowired StatusService statuses;
    @Autowired EnumerationService enumerations;
    @Autowired SystemPropertyService properties;
    @Autowired EntityManager em;
    @Autowired @Qualifier(CoreTransactional.TEMPLATE) TransactionTemplate tx;
    @Autowired JdbcTemplate jdbc;

    @BeforeAll
    void seed() {
        CoreTestData.ensureSeeded(jdbc);
        tx.executeWithoutResult(s -> {
            if (em.find(StatusType.class, "T_ORDER") != null) return;
            StatusType t = new StatusType(); t.setStatusTypeId("T_ORDER"); t.setDescription("Test order"); em.persist(t);
            for (String[] st : new String[][]{{"T_CREATED", "Created"}, {"T_APPROVED", "Approved"}, {"T_SHIPPED", "Shipped"}}) {
                StatusItem i = new StatusItem(); i.setStatusId(st[0]); i.setStatusTypeId("T_ORDER"); i.setDescription(st[1]); em.persist(i);
            }
            if (em.find(StatusFlow.class, "Default") == null) {
                StatusFlow f = new StatusFlow(); f.setStatusFlowId("Default"); f.setStatusTypeId("T_ORDER"); em.persist(f);
            }
            for (String s0 : List.of("T_CREATED", "T_APPROVED", "T_SHIPPED")) {
                StatusFlowItem fi = new StatusFlowItem(); fi.setStatusFlowId("Default"); fi.setStatusId(s0); fi.setIsInitial(s0.equals("T_CREATED")); em.persist(fi);
            }
            long n = 1;
            for (String[] tr : new String[][]{{"T_CREATED", "T_APPROVED"}, {"T_APPROVED", "T_SHIPPED"}}) {
                StatusFlowTransition x = new StatusFlowTransition(); x.setStatusFlowId("Default"); x.setStatusId(tr[0]);
                x.setToStatusId(tr[1]); x.setTransitionSequence(n++); em.persist(x);
            }
            EnumerationType et = new EnumerationType(); et.setEnumTypeId("T_CHANNEL"); em.persist(et);
            for (String[] e : new String[][]{{"T_WEB", "2"}, {"T_POS", "1"}}) {
                Enumeration en = new Enumeration(); en.setEnumId(e[0]); en.setEnumTypeId("T_CHANNEL"); en.setSequenceNum(Long.parseLong(e[1])); em.persist(en);
            }
        });
    }

    @Test
    void idsAreReadableUniqueAndSurviveConcurrency() throws Exception {
        String first = sequences.nextId("TestEntity");
        assertTrue(Long.parseLong(first) >= 10000, "ids start at 10000 like OFBiz");
        Set<String> seen = ConcurrentHashMap.newKeySet();
        ExecutorService pool = Executors.newFixedThreadPool(8);
        for (int i = 0; i < 400; i++) pool.submit(() -> seen.add(sequences.nextId("TestEntity")));
        pool.shutdown();
        assertTrue(pool.awaitTermination(30, TimeUnit.SECONDS));
        assertEquals(400, seen.size(), "every id is unique");
        assertFalse(seen.contains(first));
        assertEquals("00007", SequenceService.seqId(7));
    }

    @Test
    void statusFlowAllowsOnlyListedChanges() {
        assertTrue(statuses.isAllowed(null, "T_CREATED", "T_APPROVED"));
        assertTrue(statuses.isAllowed(null, "T_APPROVED", "T_APPROVED"), "staying in the same status is always allowed");
        assertFalse(statuses.isAllowed(null, "T_CREATED", "T_SHIPPED"));
        StatusService.StatusTransitionException e = assertThrows(StatusService.StatusTransitionException.class,
                () -> statuses.requireAllowed(null, "T_CREATED", "T_SHIPPED"));
        assertEquals("Can't change from Created to Shipped. Allowed next: Approved.", e.getMessage());
        assertEquals("T_CREATED", statuses.initialStatus(null, "T_ORDER"));
        assertEquals(List.of("T_SHIPPED"), statuses.next(null, "T_APPROVED").stream().map(StatusItem::getStatusId).toList());
    }

    @Test
    void enumerationsAreOrderedAndChecked() {
        assertEquals(List.of("T_POS", "T_WEB"), enumerations.list("T_CHANNEL").stream().map(Enumeration::getEnumId).toList());
        assertEquals("T_WEB", enumerations.require("T_CHANNEL", "T_WEB").getEnumId());
        IllegalArgumentException e = assertThrows(IllegalArgumentException.class, () -> enumerations.require("T_CHANNEL", "T_FAX"));
        assertTrue(e.getMessage().contains("Valid values: T_POS, T_WEB"), e.getMessage());
    }

    @Test
    void settingsAreTypedAndEveryChangeIsAudited() {
        SecurityContextHolder.getContext().setAuthentication(new UsernamePasswordAuthenticationToken("ops.lead", null, List.of()));
        try {
            properties.set("routing", "max.splits", "2", "Most locations an order may split across");
            properties.set("routing", "max.splits", "3", null);
        } finally {
            SecurityContextHolder.clearContext();
        }
        assertEquals(3, properties.getLong("routing", "max.splits", 1));
        assertEquals(7, properties.getLong("routing", "missing", 7));
        properties.set("routing", "split.allowed", "Y", null);
        assertTrue(properties.getBoolean("routing", "split.allowed", false));

        List<Map<String, Object>> audit = jdbc.queryForList("SELECT old_value_text, new_value_text, changed_by_user_id, pk_primary_value, pk_secondary_value "
                + "FROM entity_audit_log WHERE changed_entity_name = 'SystemProperty' AND pk_secondary_value = 'max.splits' ORDER BY changed_date, audit_history_seq_id");
        assertEquals(2, audit.size(), "the create and the change are both recorded");
        assertNull(audit.get(0).get("OLD_VALUE_TEXT"));
        assertEquals("2", audit.get(0).get("NEW_VALUE_TEXT"));
        assertEquals("2", audit.get(1).get("OLD_VALUE_TEXT"));
        assertEquals("3", audit.get(1).get("NEW_VALUE_TEXT"));
        assertEquals("ops.lead", audit.get(1).get("CHANGED_BY_USER_ID"));
        assertEquals("routing", audit.get(1).get("PK_PRIMARY_VALUE"));
        Set<Object> ids = new HashSet<>();
        jdbc.queryForList("SELECT audit_history_seq_id FROM entity_audit_log", String.class).forEach(ids::add);
        assertFalse(ids.isEmpty());
    }

    @Test
    void wrongSettingTypeGivesAReadableError() {
        properties.set("routing", "bad.number", "two", null);
        IllegalStateException e = assertThrows(IllegalStateException.class, () -> properties.getLong("routing", "bad.number", 1));
        assertEquals("Setting routing/bad.number should be a whole number, but is 'two'", e.getMessage());
    }
}
