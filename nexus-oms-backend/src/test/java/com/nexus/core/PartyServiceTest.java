package com.nexus.core;

import org.springframework.beans.factory.annotation.Qualifier;
import com.nexus.core.CoreTransactional;
import com.nexus.core.model.party.Party;
import com.nexus.core.model.party.Person;
import com.nexus.core.model.party.PostalAddress;
import com.nexus.core.service.GeoService;
import com.nexus.core.service.PartyService;
import com.nexus.core.service.StatusService;
import jakarta.persistence.EntityManager;
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
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;

@SpringBootTest(classes = com.nexus.oms.NexusOmsApplication.class)
@ActiveProfiles("test")
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
class PartyServiceTest {

    @Autowired PartyService parties;
    @Autowired GeoService geos;
    @Autowired EntityManager em;
    @Autowired @Qualifier(CoreTransactional.TEMPLATE) TransactionTemplate tx;
    @Autowired JdbcTemplate jdbc;

    @BeforeAll
    void seed() {
        CoreTestData.ensureSeeded(jdbc);
    }

    @Test
    void geographyIsMatchedFromFreeText() {
        assertEquals(Optional.of("USA"), geos.countryId("US"));
        assertEquals(Optional.of("USA"), geos.countryId("united states"));
        assertEquals(Optional.of("IND"), geos.countryId("IND"));
        assertEquals(Optional.of("US-NJ"), geos.stateId("USA", "New Jersey"));
        assertEquals(Optional.of("US-NJ"), geos.stateId("USA", "nj"));
        assertEquals(Optional.of("IN-MH"), geos.stateId("IND", "Maharashtra"));
        assertEquals(Optional.empty(), geos.countryId("Narnia"));
        assertEquals(Optional.empty(), geos.stateId("USA", "Maharashtra"), "a state is only matched inside its country");
    }

    @Test
    void aCustomerWithContactsRolesAndOutsideIds() {
        String id = tx.execute(s -> {
            String p = parties.createPerson(PartyService.PersonName.parse("  Asha  Devi Rao "), "SHOP-778");
            parties.addRole(p, "CUSTOMER");
            parties.addRole(p, "CUSTOMER");   // adding a role twice is harmless
            parties.setIdentification(p, "SHOPIFY_CUST_ID", "778");
            parties.addEmail(p, "Asha.Rao@Example.com", "PRIMARY_EMAIL");
            parties.addPhone(p, "+91 98765 43210", "PRIMARY_PHONE");
            parties.addPostalAddress(p, new PartyService.AddressInput("Asha Rao", "12 MG Road", null, "Pune", "Maharashtra",
                    "411001", "India", new BigDecimal("18.52"), new BigDecimal("73.85"), true), "SHIPPING_LOCATION");
            return p;
        });
        Person person = em.find(Person.class, id);
        assertEquals("Asha Devi", person.getFirstName());
        assertEquals("Rao", person.getLastName());
        Party party = em.find(Party.class, id);
        assertEquals("PERSON", party.getPartyTypeId());
        assertEquals("PARTY_ENABLED", party.getStatusId());
        assertEquals("SHOP-778", party.getExternalId());
        assertEquals(Optional.of(id), parties.findByIdentification("SHOPIFY_CUST_ID", "778"));
        assertEquals(List.of(id), parties.findByEmail("asha.rao@example.com"), "emails match regardless of case");
        List<String> shipping = parties.contacts(id, "SHIPPING_LOCATION");
        assertEquals(1, shipping.size());
        PostalAddress a = em.find(PostalAddress.class, shipping.get(0));
        assertEquals("IND", a.getCountryGeoId());
        assertEquals("IN-MH", a.getStateProvinceGeoId());
        assertEquals(1, jdbc.queryForObject("SELECT COUNT(*) FROM party_role WHERE party_id = ?", Long.class, id));
    }

    @Test
    void disablingIsCheckedAndKeptInHistory() {
        String id = tx.execute(s -> parties.createOrganization("Wholesale Buyers Ltd", null));
        tx.executeWithoutResult(s -> parties.setStatus(id, "PARTY_DISABLED"));
        assertEquals("PARTY_DISABLED", em.find(Party.class, id).getStatusId());
        assertEquals(2, jdbc.queryForObject("SELECT COUNT(*) FROM party_status WHERE party_id = ?", Long.class, id));
        assertEquals("PARTY_GROUP", em.find(Party.class, id).getPartyTypeId());
        assertThrows(IllegalArgumentException.class, () -> parties.createOrganization(" ", null));
        assertThrows(StatusService.StatusTransitionException.class,
                () -> tx.executeWithoutResult(s -> parties.setStatus(id, "ORDER_APPROVED")));
    }
}
