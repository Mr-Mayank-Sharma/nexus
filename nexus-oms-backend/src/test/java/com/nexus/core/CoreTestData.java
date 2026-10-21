package com.nexus.core;

import org.springframework.core.io.ClassPathResource;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.init.ScriptUtils;

import java.sql.Connection;

/** Loads the generated core seed into a test database, each file once (statuses, coded lists, geography, types). */
public final class CoreTestData {

    /** Seed file and a query that is non-zero once that file has been loaded. */
    private static final String[][] SEEDS = {
            {"db/core/common/V2__core_seed.sql", "SELECT COUNT(*) FROM status_item WHERE status_id = 'ORDER_CREATED'"},
            {"db/core/common/V4__core_party_seed.sql", "SELECT COUNT(*) FROM status_item WHERE status_id = 'PARTY_ENABLED'"},
            {"db/core/common/V6__core_product_seed.sql", "SELECT COUNT(*) FROM product_type WHERE product_type_id = 'FINISHED_GOOD'"},
            {"db/core/common/V8__core_facility_inventory_seed.sql", "SELECT COUNT(*) FROM facility_type WHERE facility_type_id = 'WAREHOUSE'"},
            {"db/core/common/V10__core_order_seed.sql", "SELECT COUNT(*) FROM order_type WHERE order_type_id = 'SALES_ORDER'"},
            {"db/core/common/V12__core_fulfilment_return_seed.sql", "SELECT COUNT(*) FROM shipment_type WHERE shipment_type_id = 'SALES_SHIPMENT'"},
    };

    private CoreTestData() {
    }

    public static synchronized void ensureSeeded(JdbcTemplate jdbc) {
        for (String[] seed : SEEDS) {
            Long loaded = jdbc.queryForObject(seed[1], Long.class);
            if (loaded != null && loaded > 0) continue;
            jdbc.execute((Connection c) -> {
                ScriptUtils.executeSqlScript(c, new ClassPathResource(seed[0]));
                return null;
            });
        }
    }
}
