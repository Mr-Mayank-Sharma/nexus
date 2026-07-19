package com.nexus.oms.service;

import com.nexus.oms.dto.RoutingRuleRequest;
import com.nexus.oms.entity.NxRoutingRule;
import com.nexus.oms.exception.BadRequestException;
import com.nexus.oms.exception.ResourceNotFoundException;
import com.nexus.oms.repository.NxRoutingRuleRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.domain.Sort;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class RoutingRuleServiceTest {

    @Mock
    private NxRoutingRuleRepository repository;

    private RoutingRuleService routingRuleService;
    private UUID tenantId;
    private UUID ruleId;

    @BeforeEach
    void setUp() {
        routingRuleService = new RoutingRuleService(repository);
        tenantId = UUID.randomUUID();
        ruleId = UUID.randomUUID();
    }

    @Test
    void getRules() {
        List<NxRoutingRule> rules = List.of(new NxRoutingRule());
        when(repository.findByTenantId(tenantId, Sort.by(Sort.Direction.ASC, "priority"))).thenReturn(rules);

        assertSame(rules, routingRuleService.getRules(tenantId));
    }

    @Test
    void getActiveRules() {
        List<NxRoutingRule> rules = List.of(new NxRoutingRule());
        when(repository.findByTenantIdAndIsActiveTrue(tenantId)).thenReturn(rules);

        assertSame(rules, routingRuleService.getActiveRules(tenantId));
    }

    @Test
    void getRule_found() {
        NxRoutingRule rule = new NxRoutingRule();
        rule.setId(ruleId);
        when(repository.findById(ruleId)).thenReturn(Optional.of(rule));

        assertSame(rule, routingRuleService.getRule(ruleId));
    }

    @Test
    void getRule_notFound_throws() {
        when(repository.findById(ruleId)).thenReturn(Optional.empty());
        assertThrows(ResourceNotFoundException.class, () -> routingRuleService.getRule(ruleId));
    }

    @Test
    void createRule() {
        RoutingRuleRequest req = new RoutingRuleRequest();
        req.setName("High Priority");
        req.setDescription("Route high-value orders");
        req.setPriority(1);
        req.setRuleType("CARRIER");
        req.setConditions("{\"value\":>1000}");
        req.setActions("{\"carrier\":\"FEDEX\"}");
        req.setIsActive(true);

        when(repository.save(any())).thenAnswer(i -> i.getArgument(0));

        NxRoutingRule result = routingRuleService.createRule(tenantId, req);

        assertEquals(tenantId, result.getTenantId());
        assertEquals("High Priority", result.getName());
        assertEquals(1, result.getPriority());
        assertTrue(result.getIsActive());
    }

    @Test
    void createRule_defaultsActive() {
        RoutingRuleRequest req = new RoutingRuleRequest();
        req.setIsActive(null);

        when(repository.save(any())).thenAnswer(i -> i.getArgument(0));

        NxRoutingRule result = routingRuleService.createRule(tenantId, req);

        assertTrue(result.getIsActive());
    }

    @Test
    void updateRule() {
        NxRoutingRule existing = new NxRoutingRule();
        existing.setId(ruleId);
        existing.setName("Old Name");

        RoutingRuleRequest req = new RoutingRuleRequest();
        req.setName("New Name");
        req.setDescription("Updated desc");
        req.setPriority(2);
        req.setRuleType("CARRIER");
        req.setConditions("{}");
        req.setActions("{}");

        when(repository.findById(ruleId)).thenReturn(Optional.of(existing));
        when(repository.save(any())).thenAnswer(i -> i.getArgument(0));

        NxRoutingRule result = routingRuleService.updateRule(ruleId, req);

        assertEquals("New Name", result.getName());
        assertEquals("Updated desc", result.getDescription());
        assertEquals(2, result.getPriority());
        assertEquals("CARRIER", result.getRuleType());
    }

    @Test
    void deleteRule() {
        NxRoutingRule rule = new NxRoutingRule();
        rule.setId(ruleId);
        when(repository.findById(ruleId)).thenReturn(Optional.of(rule));

        routingRuleService.deleteRule(ruleId);

        verify(repository).delete(rule);
    }

    @Test
    void reorderRules() {
        UUID id1 = UUID.randomUUID();
        UUID id2 = UUID.randomUUID();

        NxRoutingRule r1 = new NxRoutingRule();
        r1.setId(id1);
        r1.setPriority(1);
        NxRoutingRule r2 = new NxRoutingRule();
        r2.setId(id2);
        r2.setPriority(2);

        when(repository.findByTenantId(tenantId, Sort.by(Sort.Direction.ASC, "priority")))
                .thenReturn(List.of(r1, r2));
        when(repository.save(any())).thenAnswer(i -> i.getArgument(0));

        routingRuleService.reorderRules(tenantId, List.of(id2, id1));

        assertEquals(1, r2.getPriority());
        assertEquals(2, r1.getPriority());
    }

    @Test
    void reorderRules_ruleNotFound_throws() {
        UUID id1 = UUID.randomUUID();
        UUID missingId = UUID.randomUUID();
        NxRoutingRule r1 = new NxRoutingRule();
        r1.setId(id1);

        when(repository.findByTenantId(tenantId, Sort.by(Sort.Direction.ASC, "priority")))
                .thenReturn(List.of(r1));

        assertThrows(BadRequestException.class,
                () -> routingRuleService.reorderRules(tenantId, List.of(missingId)));
    }
}
