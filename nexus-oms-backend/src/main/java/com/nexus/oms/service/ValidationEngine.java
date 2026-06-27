package com.nexus.oms.service;

import com.nexus.oms.entity.IntegrationValidationRule;
import com.nexus.oms.repository.IntegrationValidationRuleRepository;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

@Service
public class ValidationEngine {

    private final IntegrationValidationRuleRepository ruleRepository;

    public ValidationEngine(IntegrationValidationRuleRepository ruleRepository) {
        this.ruleRepository = ruleRepository;
    }

    public List<String> validate(String payload, String entityType, UUID tenantId) {
        List<String> errors = new ArrayList<>();
        List<IntegrationValidationRule> rules = ruleRepository.findByTenantIdAndEntityType(tenantId, entityType);
        for (IntegrationValidationRule rule : rules) {
            if (!Boolean.TRUE.equals(rule.getIsActive())) continue;
            if (!validateField(payload, rule)) {
                errors.add(rule.getErrorMessage() != null ? rule.getErrorMessage() : "Validation failed for field: " + rule.getFieldPath());
            }
        }
        return errors;
    }

    public boolean validateField(Object value, IntegrationValidationRule rule) {
        if (value == null) return false;
        String sv = value.toString();
        String op = rule.getOperator();
        String rv = rule.getValue();
        if ("EQUALS".equals(op)) return sv.equals(rv);
        if ("NOT_EQUALS".equals(op)) return !sv.equals(rv);
        if ("CONTAINS".equals(op)) return sv.contains(rv);
        if ("STARTS_WITH".equals(op)) return sv.startsWith(rv);
        if ("ENDS_WITH".equals(op)) return sv.endsWith(rv);
        if ("REGEX".equals(op)) return sv.matches(rv);
        if ("NOT_NULL".equals(op)) return true;
        if ("LENGTH_GT".equals(op)) return rv != null && sv.length() > Integer.parseInt(rv);
        if ("LENGTH_LT".equals(op)) return rv != null && sv.length() < Integer.parseInt(rv);
        if ("MIN".equals(op)) {
            try {
                return Double.parseDouble(sv) >= Double.parseDouble(rv);
            } catch (NumberFormatException e) {
                return false;
            }
        }
        if ("MAX".equals(op)) {
            try {
                return Double.parseDouble(sv) <= Double.parseDouble(rv);
            } catch (NumberFormatException e) {
                return false;
            }
        }
        return true;
    }

    public boolean validateAgainstSchema(String payload, String schemaType) {
        return payload != null && !payload.isBlank();
    }
}
