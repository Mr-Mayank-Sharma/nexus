package com.nexus.oms.coremigration;

import com.nexus.core.service.GeoService;
import com.nexus.core.service.PartyService;
import jakarta.persistence.EntityManager;
import jakarta.persistence.FlushModeType;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.support.TransactionTemplate;

import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

import static com.nexus.oms.coremigration.LegacyRows.LEGACY_ID;
import static com.nexus.oms.coremigration.LegacyRows.text;

/**
 * Copies customers and their addresses from the legacy tables (nx_customers, nx_addresses) into the core party model
 * (doc 23 section 4), for one tenant. Each customer becomes a person with the CUSTOMER role, its email, phone and
 * address, and a NEXUS_LEGACY_ID identification holding the old id, which makes the copy safe to run again: customers
 * already copied are skipped. The legacy tables are only read.
 */
@Service
public class CustomerPartyBackfill {

    private static final Logger log = LoggerFactory.getLogger(CustomerPartyBackfill.class);
    private static final int CHUNK_SIZE = 200;

    private final JdbcTemplate jdbc;
    private final PartyService partyService;
    private final TransactionTemplate transaction;
    private final GeoService geoService;
    private final EntityManager entityManager;

    public CustomerPartyBackfill(JdbcTemplate jdbc, PartyService partyService, TransactionTemplate transaction, GeoService geoService,
                                 EntityManager entityManager) {
        this.jdbc = jdbc;
        this.partyService = partyService;
        this.transaction = transaction;
        this.geoService = geoService;
        this.entityManager = entityManager;
    }

    public record Result(int scanned, int created, int skipped, int addressesWithoutCountryMatch) {
    }

    /** Customers created and skipped, and addresses whose country could not be matched, in one chunk. */
    private record ChunkCounts(int created, int skipped, int addressesWithoutCountryMatch) {
    }

    public Result run(UUID tenantId) {
        if (tenantId == null) {
            throw new IllegalArgumentException("Run the customer copy as a user of the company whose customers to copy");
        }
        List<Map<String, Object>> customers = jdbc.queryForList(
                "SELECT c.id, c.external_id, c.name, c.email, c.phone, a.full_name, a.address_line1, a.address_line2, a.city, "
                        + "a.state, a.postal_code, a.country, a.is_residential FROM nx_customers c "
                        + "LEFT JOIN nx_addresses a ON a.id = c.address_id WHERE c.tenant_id = ? ORDER BY c.created_at, c.id", tenantId);
        Set<String> copiedLegacyIds = new HashSet<>(jdbc.queryForList("SELECT id_value FROM party_identification "
                + "WHERE party_identification_type_id = ?", String.class, LEGACY_ID));

        int created = 0;
        int skipped = 0;
        int withoutCountry = 0;
        for (int start = 0; start < customers.size(); start += CHUNK_SIZE) {
            List<Map<String, Object>> chunk = customers.subList(start, Math.min(customers.size(), start + CHUNK_SIZE));
            ChunkCounts chunkCounts = transaction.execute(status -> {
                entityManager.setFlushMode(FlushModeType.COMMIT);
                return copyCustomers(chunk, copiedLegacyIds);
            });
            created += chunkCounts.created();
            skipped += chunkCounts.skipped();
            withoutCountry += chunkCounts.addressesWithoutCountryMatch();
        }
        log.info("Customer copy for tenant {}: {} scanned, {} created, {} already copied, {} addresses without a known country",
                tenantId, customers.size(), created, skipped, withoutCountry);
        return new Result(customers.size(), created, skipped, withoutCountry);
    }

    private ChunkCounts copyCustomers(List<Map<String, Object>> chunk, Set<String> copiedLegacyIds) {
        int created = 0;
        int skipped = 0;
        int withoutCountry = 0;
        for (Map<String, Object> customer : chunk) {
            String legacyId = String.valueOf(customer.get("id"));
            boolean firstTimeSeen = copiedLegacyIds.add(legacyId);
            if (!firstTimeSeen) {
                skipped++;
                continue;
            }
            String partyId = partyService.createPerson(PartyService.PersonName.parse(text(customer, "name")), text(customer, "external_id"));
            partyService.addRole(partyId, "CUSTOMER");
            partyService.setIdentification(partyId, LEGACY_ID, legacyId);
            partyService.addEmail(partyId, text(customer, "email"), "PRIMARY_EMAIL", "ORDER_EMAIL");
            partyService.addPhone(partyId, text(customer, "phone"), "PRIMARY_PHONE");

            boolean hasAddress = text(customer, "address_line1") != null || text(customer, "city") != null;
            if (hasAddress) {
                PartyService.AddressInput address = new PartyService.AddressInput(text(customer, "full_name"), text(customer, "address_line1"),
                        text(customer, "address_line2"), text(customer, "city"), text(customer, "state"), text(customer, "postal_code"),
                        text(customer, "country"), null, null, (Boolean) customer.get("is_residential"));
                partyService.addPostalAddress(partyId, address, "SHIPPING_LOCATION", "PRIMARY_LOCATION");
                if (geoService.countryId(address.country()).isEmpty()) {
                    withoutCountry++;
                }
            }
            created++;
        }
        return new ChunkCounts(created, skipped, withoutCountry);
    }

    /** For tests and reports: how many parties came from the legacy customer table. */
    public long copiedCount() {
        Long count = jdbc.queryForObject("SELECT COUNT(*) FROM party_identification WHERE party_identification_type_id = ?", Long.class, LEGACY_ID);
        return count == null ? 0 : count;
    }
}
