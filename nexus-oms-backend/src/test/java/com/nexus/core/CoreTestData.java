package com.nexus.core;

import org.springframework.core.io.ClassPathResource;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.init.ScriptUtils;

import java.sql.Connection;

/** Loads the generated core seed (statuses, coded lists, units, geography, party types) into a test database once. */
public final class CoreTestData {

    private static final String[] SEEDS = {"db/core/common/V2__core_seed.sql", "db/core/common/V4__core_party_seed.sql"};

    private CoreTestData() {
    }

    public static synchronized void ensureSeeded(JdbcTemplate jdbc) {
        Long done = jdbc.queryForObject("SELECT COUNT(*) FROM status_item WHERE status_id = 'PARTY_ENABLED'", Long.class);
        if (done != null && done > 0) return;
        jdbc.execute((Connection c) -> {
            for (String seed : SEEDS) ScriptUtils.executeSqlScript(c, new ClassPathResource(seed));
            return null;
        });
    }
}
