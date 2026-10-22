package com.nexus.oms.agent;

import org.junit.jupiter.api.Test;

import java.util.Optional;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;

/** Reading the company from an agent key (doc 29, phase B). */
class AgentKeyCompanyFilterTest {

    @Test
    void aKeyWithACompanyPartNamesThatCompany() {
        UUID companyId = UUID.randomUUID();
        String key = "nxa_" + companyId.toString().replace("-", "") + ".randomPart-with_url-safe_chars";
        assertEquals(Optional.of(companyId), AgentKeyCompanyFilter.companyOf("Bearer " + key));
    }

    @Test
    void anOlderKeyOrNoKeyNamesNoCompany() {
        assertEquals(Optional.empty(), AgentKeyCompanyFilter.companyOf("Bearer nxa_abcdefRandomOnly"));
        assertEquals(Optional.empty(), AgentKeyCompanyFilter.companyOf(null));
        assertEquals(Optional.empty(), AgentKeyCompanyFilter.companyOf("Basic something"));
        assertEquals(Optional.empty(), AgentKeyCompanyFilter.companyOf("Bearer nxa_NOT-HEX-NOT-HEX-NOT-HEX-NOT-HEX.x"));
    }
}
