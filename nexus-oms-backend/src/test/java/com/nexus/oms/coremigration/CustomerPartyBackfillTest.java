package com.nexus.oms.coremigration;

import com.nexus.core.CoreTestData;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestInstance;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.ActiveProfiles;

import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;

@SpringBootTest
@ActiveProfiles("test")
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
class CustomerPartyBackfillTest {

    @Autowired CustomerPartyBackfill backfill;
    @Autowired JdbcTemplate jdbc;

    final UUID tenant = UUID.randomUUID();
    final UUID otherTenant = UUID.randomUUID();
    final UUID withAddress = UUID.randomUUID();
    final UUID withoutAddress = UUID.randomUUID();

    @BeforeAll
    void legacyCustomers() {
        CoreTestData.ensureSeeded(jdbc);
        UUID addr = UUID.randomUUID();
        jdbc.update("INSERT INTO nx_addresses (id, tenant_id, address_line1, city, state, postal_code, country, full_name, created_at, updated_at) "
                + "VALUES (?, ?, '12 Hill Rd', 'Edison', 'NJ', '08817', 'US', 'Olivia Patel', CURRENT_TIMESTAMP, CURRENT_TIMESTAMP)", addr, tenant);
        jdbc.update("INSERT INTO nx_customers (id, tenant_id, external_id, name, email, phone, address_id, created_at) "
                + "VALUES (?, ?, 'SHOP-1', 'Olivia Patel', 'Olivia@Example.com', '+1 732 555 0100', ?, CURRENT_TIMESTAMP)", withAddress, tenant, addr);
        jdbc.update("INSERT INTO nx_customers (id, tenant_id, name, email, created_at) "
                + "VALUES (?, ?, 'Ravi', 'ravi@example.com', CURRENT_TIMESTAMP)", withoutAddress, tenant);
        jdbc.update("INSERT INTO nx_customers (id, tenant_id, name, created_at) VALUES (?, ?, 'Someone Else', CURRENT_TIMESTAMP)",
                UUID.randomUUID(), otherTenant);
    }

    @Test
    void copiesOnceAndOnlyTheCallersCompany() {
        CustomerPartyBackfill.Result first = backfill.run(tenant);
        assertEquals(2, first.scanned());
        assertEquals(2, first.created());
        assertEquals(0, first.addressesWithoutCountryMatch());

        String partyId = jdbc.queryForObject("SELECT party_id FROM party_identification WHERE party_identification_type_id = 'NEXUS_LEGACY_ID' AND id_value = ?",
                String.class, withAddress.toString());
        assertEquals("Olivia", jdbc.queryForObject("SELECT first_name FROM person WHERE party_id = ?", String.class, partyId));
        assertEquals("SHOP-1", jdbc.queryForObject("SELECT external_id FROM party WHERE party_id = ?", String.class, partyId));
        assertEquals("US-NJ", jdbc.queryForObject("SELECT pa.state_province_geo_id FROM postal_address pa JOIN party_contact_mech pcm "
                + "ON pcm.contact_mech_id = pa.contact_mech_id WHERE pcm.party_id = ?", String.class, partyId));
        assertEquals("olivia@example.com", jdbc.queryForObject("SELECT cm.info_string FROM contact_mech cm JOIN party_contact_mech pcm "
                + "ON pcm.contact_mech_id = cm.contact_mech_id WHERE pcm.party_id = ? AND cm.contact_mech_type_id = 'EMAIL_ADDRESS'", String.class, partyId));
        assertEquals(1, jdbc.queryForObject("SELECT COUNT(*) FROM party_role WHERE party_id = ? AND role_type_id = 'CUSTOMER'", Long.class, partyId));

        CustomerPartyBackfill.Result second = backfill.run(tenant);
        assertEquals(0, second.created(), "running again copies nothing new");
        assertEquals(2, second.skipped());

        assertThrows(IllegalArgumentException.class, () -> backfill.run(null));
    }
}
