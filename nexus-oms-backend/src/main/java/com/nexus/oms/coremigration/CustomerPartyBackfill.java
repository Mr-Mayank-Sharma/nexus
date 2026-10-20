package com.nexus.oms.coremigration;

import com.nexus.core.service.GeoService;
import com.nexus.core.service.PartyService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.support.TransactionTemplate;

import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * Copies customers and their addresses from the legacy tables (nx_customers, nx_addresses) into the core party model
 * (doc 23 section 4), for one tenant. Each customer becomes a person with the CUSTOMER role, its email, phone and
 * address, and a NEXUS_LEGACY_ID identification holding the old id, which makes the copy safe to run again: customers
 * already copied are skipped. The legacy tables are only read.
 */
@Service
public class CustomerPartyBackfill {

    private static final Logger log = LoggerFactory.getLogger(CustomerPartyBackfill.class);
    static final String LEGACY_ID = "NEXUS_LEGACY_ID";
    private static final int CHUNK = 200;

    private final JdbcTemplate jdbc;
    private final PartyService parties;
    private final TransactionTemplate tx;
    private final GeoService geos;

    public CustomerPartyBackfill(JdbcTemplate jdbc, PartyService parties, TransactionTemplate tx, GeoService geos) {
        this.jdbc = jdbc;
        this.parties = parties;
        this.tx = tx;
        this.geos = geos;
    }

    public record Result(int scanned, int created, int skipped, int addressesWithoutCountryMatch) {
    }

    public Result run(UUID tenantId) {
        if (tenantId == null) throw new IllegalArgumentException("Run the customer copy as a user of the company whose customers to copy");
        List<Map<String, Object>> rows = jdbc.queryForList(
                "SELECT c.id, c.external_id, c.name, c.email, c.phone, a.full_name, a.address_line1, a.address_line2, a.city, "
                        + "a.state, a.postal_code, a.country, a.is_residential FROM nx_customers c "
                        + "LEFT JOIN nx_addresses a ON a.id = c.address_id WHERE c.tenant_id = ? ORDER BY c.created_at, c.id", tenantId);
        int created = 0, skipped = 0, noCountry = 0;
        for (int from = 0; from < rows.size(); from += CHUNK) {
            List<Map<String, Object>> chunk = rows.subList(from, Math.min(rows.size(), from + CHUNK));
            int[] counts = tx.execute(status -> copy(chunk));
            created += counts[0];
            skipped += counts[1];
            noCountry += counts[2];
        }
        log.info("Customer copy for tenant {}: {} scanned, {} created, {} already copied, {} addresses without a known country",
                tenantId, rows.size(), created, skipped, noCountry);
        return new Result(rows.size(), created, skipped, noCountry);
    }

    private int[] copy(List<Map<String, Object>> chunk) {
        int created = 0, skipped = 0, noCountry = 0;
        for (Map<String, Object> r : chunk) {
            String legacyId = String.valueOf(r.get("id"));
            if (parties.findByIdentification(LEGACY_ID, legacyId).isPresent()) {
                skipped++;
                continue;
            }
            String partyId = parties.createPerson(PartyService.PersonName.parse(str(r, "name")), str(r, "external_id"));
            parties.addRole(partyId, "CUSTOMER");
            parties.setIdentification(partyId, LEGACY_ID, legacyId);
            parties.addEmail(partyId, str(r, "email"), "PRIMARY_EMAIL", "ORDER_EMAIL");
            parties.addPhone(partyId, str(r, "phone"), "PRIMARY_PHONE");
            if (str(r, "address_line1") != null || str(r, "city") != null) {
                PartyService.AddressInput a = new PartyService.AddressInput(str(r, "full_name"), str(r, "address_line1"),
                        str(r, "address_line2"), str(r, "city"), str(r, "state"), str(r, "postal_code"), str(r, "country"),
                        null, null, (Boolean) r.get("is_residential"));
                parties.addPostalAddress(partyId, a, "SHIPPING_LOCATION", "PRIMARY_LOCATION");
                if (geos.countryId(a.country()).isEmpty()) noCountry++;
            }
            created++;
        }
        return new int[]{created, skipped, noCountry};
    }

    private static String str(Map<String, Object> r, String key) {
        Object v = r.get(key);
        return v == null || String.valueOf(v).isBlank() ? null : String.valueOf(v);
    }

    /** For tests and reports: how many parties came from the legacy customer table. */
    public long copiedCount() {
        Long n = jdbc.queryForObject("SELECT COUNT(*) FROM party_identification WHERE party_identification_type_id = ?", Long.class, LEGACY_ID);
        return n == null ? 0 : n;
    }
}
