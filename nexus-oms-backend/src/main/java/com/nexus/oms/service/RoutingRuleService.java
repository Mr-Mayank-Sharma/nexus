package com.nexus.oms.service;

import com.nexus.oms.dto.RoutingRuleRequest;
import com.nexus.oms.entity.NxRoutingRule;
import com.nexus.oms.exception.BadRequestException;
import com.nexus.oms.exception.ResourceNotFoundException;
import com.nexus.oms.repository.NxRoutingRuleRepository;
import org.springframework.cache.annotation.CacheEvict;
import org.springframework.cache.annotation.Cacheable;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.UUID;

@Service
public class RoutingRuleService {

    private final NxRoutingRuleRepository repository;

    public RoutingRuleService(NxRoutingRuleRepository repository) {
        this.repository = repository;
    }

    @Cacheable(value = "routingRules", key = "#tenantId")
    public List<NxRoutingRule> getRules(UUID tenantId) {
        return repository.findByTenantId(tenantId, Sort.by(Sort.Direction.ASC, "priority"));
    }

    @Cacheable(value = "routingRules", key = "'active:' + #tenantId")
    public List<NxRoutingRule> getActiveRules(UUID tenantId) {
        return repository.findByTenantIdAndIsActiveTrue(tenantId);
    }

    public NxRoutingRule getRule(UUID id) {
        return repository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("RoutingRule", id));
    }

    @Transactional
    @CacheEvict(value = "routingRules", allEntries = true)
    public NxRoutingRule createRule(UUID tenantId, RoutingRuleRequest request) {
        NxRoutingRule rule = NxRoutingRule.builder()
                .tenantId(tenantId)
                .name(request.getName())
                .description(request.getDescription())
                .priority(request.getPriority())
                .ruleType(request.getRuleType())
                .conditions(request.getConditions())
                .actions(request.getActions())
                .isActive(request.getIsActive() != null ? request.getIsActive() : true)
                .build();
        return repository.save(rule);
    }

    @Transactional
    @CacheEvict(value = "routingRules", allEntries = true)
    public NxRoutingRule updateRule(UUID id, RoutingRuleRequest request) {
        NxRoutingRule rule = getRule(id);
        rule.setName(request.getName());
        rule.setDescription(request.getDescription());
        rule.setPriority(request.getPriority());
        rule.setRuleType(request.getRuleType());
        rule.setConditions(request.getConditions());
        rule.setActions(request.getActions());
        if (request.getIsActive() != null) rule.setIsActive(request.getIsActive());
        return repository.save(rule);
    }

    @Transactional
    @CacheEvict(value = "routingRules", allEntries = true)
    public void deleteRule(UUID id) {
        NxRoutingRule rule = getRule(id);
        repository.delete(rule);
    }

    @Transactional
    @CacheEvict(value = "routingRules", allEntries = true)
    public void reorderRules(UUID tenantId, List<UUID> ruleIds) {
        List<NxRoutingRule> rules = repository.findByTenantId(tenantId,
                Sort.by(Sort.Direction.ASC, "priority"));
        for (int i = 0; i < ruleIds.size(); i++) {
            UUID ruleId = ruleIds.get(i);
            NxRoutingRule rule = rules.stream()
                    .filter(r -> r.getId().equals(ruleId))
                    .findFirst()
                    .orElseThrow(() -> new BadRequestException("Rule not found: " + ruleId));
            rule.setPriority(i + 1);
            repository.save(rule);
        }
    }
}
