package com.nexus.oms.service.ai;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.nexus.oms.entity.ai.*;
import com.nexus.oms.repository.ai.*;
import com.nexus.oms.security.TenantContext;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.*;
import java.util.stream.Collectors;

@Service
public class AiFeatureStoreService {

    private static final Logger log = LoggerFactory.getLogger(AiFeatureStoreService.class);

    private final AiFeatureDefinitionRepository definitionRepository;
    private final AiFeatureValueRepository valueRepository;
    private final ObjectMapper objectMapper;

    public AiFeatureStoreService(AiFeatureDefinitionRepository definitionRepository,
                                  AiFeatureValueRepository valueRepository,
                                  ObjectMapper objectMapper) {
        this.definitionRepository = definitionRepository;
        this.valueRepository = valueRepository;
        this.objectMapper = objectMapper;
    }

    public Page<AiFeatureDefinition> getDefinitions(UUID tenantId, String featureGroup, Pageable pageable) {
        if (featureGroup != null)
            return (Page<AiFeatureDefinition>) definitionRepository.findByTenantIdAndFeatureGroup(tenantId, featureGroup);
        return definitionRepository.findByTenantId(tenantId, pageable);
    }

    @Transactional
    public AiFeatureDefinition createDefinition(AiFeatureDefinition def) {
        def.setIsActive(true);
        def.setVersion(1);
        return definitionRepository.save(def);
    }

    public List<AiFeatureValue> getFeatureValues(UUID tenantId, UUID featureId, String entityId, LocalDate asOfDate) {
        return valueRepository.findByTenantIdAndFeatureIdAndEntityIdAndAsOfDate(tenantId, featureId, entityId, asOfDate);
    }

    @Transactional
    public void storeFeatureValue(AiFeatureValue value) {
        valueRepository.save(value);
    }

    @Transactional
    public void storeFeatureValues(List<AiFeatureValue> values) {
        valueRepository.saveAll(values);
    }

    public Map<String, Object> getEntityFeatures(UUID tenantId, String entityId, LocalDate asOfDate) {
        List<AiFeatureValue> values = valueRepository.findAllForEntity(tenantId, entityId, asOfDate);
        Map<String, Object> features = new LinkedHashMap<>();
        for (AiFeatureValue v : values) {
            definitionRepository.findById(v.getFeatureId()).ifPresent(def -> {
                if (v.getNumericValue() != null) features.put(def.getName(), v.getNumericValue());
                else if (v.getBoolValue() != null) features.put(def.getName(), v.getBoolValue());
                else if (v.getValue() != null) features.put(def.getName(), v.getValue());
            });
        }
        return features;
    }

    public long getFeatureCount(UUID tenantId) {
        return definitionRepository.countByTenantIdAndFeatureGroup(tenantId, null);
    }

    public List<Map<String, Object>> getFeatureGroups(UUID tenantId) {
        Map<String, Long> groups = new LinkedHashMap<>();
        definitionRepository.findByTenantId(tenantId, Pageable.unpaged())
                .forEach(def -> groups.merge(def.getFeatureGroup(), 1L, Long::sum));
        return groups.entrySet().stream()
                .map(e -> {
                    Map<String, Object> g = new LinkedHashMap<>();
                    g.put("group", e.getKey());
                    g.put("count", e.getValue());
                    return g;
                })
                .collect(Collectors.toList());
    }
}
