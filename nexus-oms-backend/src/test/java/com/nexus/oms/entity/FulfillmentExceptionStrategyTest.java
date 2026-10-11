package com.nexus.oms.entity;

import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.Set;
import java.util.TreeSet;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import static org.junit.jupiter.api.Assertions.*;

/** The Java constant and the database CHECK constraint must list the same resolution strategies. */
class FulfillmentExceptionStrategyTest {

    @Test
    void javaStrategies_matchTheMigrationsCheckConstraint() throws IOException {
        String sql = new String(getClass().getResourceAsStream("/db/migration/V14__order_routing_ai.sql").readAllBytes(), StandardCharsets.UTF_8);
        Matcher m = Pattern.compile("resolution_strategy\\s+VARCHAR\\(\\d+\\)\\s+CHECK\\s*\\(resolution_strategy\\s+IN\\s*\\(([^)]*)\\)", Pattern.DOTALL).matcher(sql);
        assertTrue(m.find(), "could not find the resolution_strategy CHECK in V14");
        Set<String> inDb = new TreeSet<>();
        for (String v : m.group(1).split(",")) inDb.add(v.trim().replace("'", ""));

        assertEquals(inDb, new TreeSet<>(NxFulfillmentException.RESOLUTION_STRATEGIES));
    }

    @Test
    void allocationStrategyNames_areNotResolutionStrategies() {
        for (String allocation : new String[]{"RULE_BASED", "AI_OPTIMIZED", "NEAREST_AVAILABLE", "LOWEST_COST"}) {
            assertFalse(NxFulfillmentException.RESOLUTION_STRATEGIES.contains(allocation));
        }
    }
}
