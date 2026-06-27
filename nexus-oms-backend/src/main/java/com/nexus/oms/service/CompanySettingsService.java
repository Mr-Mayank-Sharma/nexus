package com.nexus.oms.service;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.nexus.oms.entity.CompanySettings;
import com.nexus.oms.exception.BadRequestException;
import com.nexus.oms.exception.ResourceNotFoundException;
import com.nexus.oms.repository.CompanySettingsRepository;
import com.nexus.oms.security.TenantContext;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.Map;
import java.util.UUID;

@Service
public class CompanySettingsService {

    private final CompanySettingsRepository companySettingsRepository;
    private final ObjectMapper objectMapper;

    public CompanySettingsService(CompanySettingsRepository companySettingsRepository,
                                  ObjectMapper objectMapper) {
        this.companySettingsRepository = companySettingsRepository;
        this.objectMapper = objectMapper;
    }

    @Transactional
    public CompanySettings getSettings() {
        UUID tenantId = TenantContext.getCurrentTenantId();
        return companySettingsRepository.findByTenantId(tenantId)
                .orElseGet(() -> {
                    CompanySettings defaults = CompanySettings.builder()
                            .tenantId(tenantId)
                            .defaultCurrency("USD")
                            .defaultLanguage("en")
                            .defaultTimezone("UTC")
                            .dateFormat("YYYY-MM-DD")
                            .timeFormat("HH:mm:ss")
                            .featureFlags("{}")
                            .securityPolicy("{}")
                            .build();
                    return companySettingsRepository.save(defaults);
                });
    }

    @Transactional
    public CompanySettings updateSettings(CompanySettings updates) {
        UUID tenantId = TenantContext.getCurrentTenantId();
        CompanySettings settings = companySettingsRepository.findByTenantId(tenantId)
                .orElseThrow(() -> new ResourceNotFoundException("CompanySettings for tenant", tenantId));
        if (updates.getCompanyName() != null) settings.setCompanyName(updates.getCompanyName());
        if (updates.getCompanyLogo() != null) settings.setCompanyLogo(updates.getCompanyLogo());
        if (updates.getTaxId() != null) settings.setTaxId(updates.getTaxId());
        if (updates.getRegistrationNumber() != null) settings.setRegistrationNumber(updates.getRegistrationNumber());
        if (updates.getDefaultCurrency() != null) settings.setDefaultCurrency(updates.getDefaultCurrency());
        if (updates.getDefaultLanguage() != null) settings.setDefaultLanguage(updates.getDefaultLanguage());
        if (updates.getDefaultTimezone() != null) settings.setDefaultTimezone(updates.getDefaultTimezone());
        if (updates.getDateFormat() != null) settings.setDateFormat(updates.getDateFormat());
        if (updates.getTimeFormat() != null) settings.setTimeFormat(updates.getTimeFormat());
        if (updates.getFiscalYearStart() != null) settings.setFiscalYearStart(updates.getFiscalYearStart());
        if (updates.getCountries() != null) settings.setCountries(updates.getCountries());
        if (updates.getRegions() != null) settings.setRegions(updates.getRegions());
        if (updates.getHolidays() != null) settings.setHolidays(updates.getHolidays());
        if (updates.getFeatureFlags() != null) settings.setFeatureFlags(updates.getFeatureFlags());
        if (updates.getSecurityPolicy() != null) settings.setSecurityPolicy(updates.getSecurityPolicy());
        if (updates.getBackupConfig() != null) settings.setBackupConfig(updates.getBackupConfig());
        return companySettingsRepository.save(settings);
    }

    @Transactional
    public CompanySettings updateFeatureFlags(Map<String, Object> flags) {
        UUID tenantId = TenantContext.getCurrentTenantId();
        CompanySettings settings = companySettingsRepository.findByTenantId(tenantId)
                .orElseThrow(() -> new ResourceNotFoundException("CompanySettings for tenant", tenantId));
        try {
            JsonNode existing = objectMapper.readTree(
                    settings.getFeatureFlags() != null ? settings.getFeatureFlags() : "{}");
            JsonNode merged = mergeJson(existing, objectMapper.valueToTree(flags));
            settings.setFeatureFlags(objectMapper.writeValueAsString(merged));
            return companySettingsRepository.save(settings);
        } catch (JsonProcessingException e) {
            throw new BadRequestException("Failed to parse feature flags: " + e.getMessage());
        }
    }

    public Object getFeatureFlag(String flagName) {
        UUID tenantId = TenantContext.getCurrentTenantId();
        CompanySettings settings = companySettingsRepository.findByTenantId(tenantId)
                .orElseThrow(() -> new ResourceNotFoundException("CompanySettings for tenant", tenantId));
        try {
            JsonNode flags = objectMapper.readTree(
                    settings.getFeatureFlags() != null ? settings.getFeatureFlags() : "{}");
            JsonNode flag = flags.get(flagName);
            if (flag == null) return null;
            if (flag.isBoolean()) return flag.asBoolean();
            if (flag.isTextual()) return flag.asText();
            if (flag.isNumber()) return flag.numberValue();
            return flag.toString();
        } catch (JsonProcessingException e) {
            throw new BadRequestException("Failed to parse feature flags: " + e.getMessage());
        }
    }

    @Transactional
    public CompanySettings updateSecurityPolicy(Map<String, Object> policy) {
        UUID tenantId = TenantContext.getCurrentTenantId();
        CompanySettings settings = companySettingsRepository.findByTenantId(tenantId)
                .orElseThrow(() -> new ResourceNotFoundException("CompanySettings for tenant", tenantId));
        try {
            JsonNode existing = objectMapper.readTree(
                    settings.getSecurityPolicy() != null ? settings.getSecurityPolicy() : "{}");
            JsonNode merged = mergeJson(existing, objectMapper.valueToTree(policy));
            settings.setSecurityPolicy(objectMapper.writeValueAsString(merged));
            return companySettingsRepository.save(settings);
        } catch (JsonProcessingException e) {
            throw new BadRequestException("Failed to parse security policy: " + e.getMessage());
        }
    }

    public boolean isFeatureEnabled(String flagName) {
        Object flag = getFeatureFlag(flagName);
        return flag instanceof Boolean && (Boolean) flag;
    }

    private JsonNode mergeJson(JsonNode base, JsonNode overrides) {
        if (base.isObject() && overrides.isObject()) {
            com.fasterxml.jackson.databind.node.ObjectNode baseObj = (com.fasterxml.jackson.databind.node.ObjectNode) base;
            overrides.fields().forEachRemaining(entry -> baseObj.set(entry.getKey(), entry.getValue()));
            return baseObj;
        }
        return overrides;
    }
}
