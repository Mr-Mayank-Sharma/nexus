package com.nexus.oms.service;

import com.nexus.oms.entity.*;
import com.nexus.oms.exception.BadRequestException;
import com.nexus.oms.exception.ResourceNotFoundException;
import com.nexus.oms.repository.*;
import com.nexus.oms.security.TenantContext;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDateTime;
import java.util.*;
import java.util.stream.Collectors;

@Service
public class ReplenishmentService {

    private final NxReplenishmentRuleRepository ruleRepository;
    private final NxReplenishmentSuggestionRepository suggestionRepository;
    private final InventoryRepository inventoryRepository;

    public ReplenishmentService(NxReplenishmentRuleRepository ruleRepository,
                                 NxReplenishmentSuggestionRepository suggestionRepository,
                                 InventoryRepository inventoryRepository) {
        this.ruleRepository = ruleRepository;
        this.suggestionRepository = suggestionRepository;
        this.inventoryRepository = inventoryRepository;
    }

    // ---- Rules ----

    public List<NxReplenishmentRule> getRules(UUID warehouseId) {
        return ruleRepository.findByWarehouseIdAndIsActiveTrue(warehouseId);
    }

    public NxReplenishmentRule getRule(UUID id) {
        return ruleRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("ReplenishmentRule", id));
    }

    @Transactional
    public NxReplenishmentRule createRule(NxReplenishmentRule rule) {
        rule.setTenantId(TenantContext.getCurrentTenantId());
        if (rule.getIsActive() == null) rule.setIsActive(true);
        return ruleRepository.save(rule);
    }

    @Transactional
    public NxReplenishmentRule updateRule(UUID id, NxReplenishmentRule updates) {
        NxReplenishmentRule existing = ruleRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("ReplenishmentRule", id));

        if (updates.getRuleName() != null) existing.setRuleName(updates.getRuleName());
        if (updates.getRuleType() != null) existing.setRuleType(updates.getRuleType());
        if (updates.getItemCategory() != null) existing.setItemCategory(updates.getItemCategory());
        if (updates.getItemClass() != null) existing.setItemClass(updates.getItemClass());
        if (updates.getReorderPoint() != null) existing.setReorderPoint(updates.getReorderPoint());
        if (updates.getReorderQty() != null) existing.setReorderQty(updates.getReorderQty());
        if (updates.getSafetyStock() != null) existing.setSafetyStock(updates.getSafetyStock());
        if (updates.getMaxStock() != null) existing.setMaxStock(updates.getMaxStock());
        if (updates.getLeadTimeDays() != null) existing.setLeadTimeDays(updates.getLeadTimeDays());
        if (updates.getDemandWindowDays() != null) existing.setDemandWindowDays(updates.getDemandWindowDays());
        if (updates.getPriority() != null) existing.setPriority(updates.getPriority());
        if (updates.getNotes() != null) existing.setNotes(updates.getNotes());

        return ruleRepository.save(existing);
    }

    @Transactional
    public void deleteRule(UUID id) {
        NxReplenishmentRule rule = ruleRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("ReplenishmentRule", id));
        rule.setIsActive(false);
        ruleRepository.save(rule);
    }

    public NxReplenishmentSuggestion getSuggestion(UUID id) {
        return suggestionRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("ReplenishmentSuggestion", id));
    }

    // ---- Suggestions ----

    public List<NxReplenishmentSuggestion> getSuggestions(UUID warehouseId, String status) {
        if (status != null && !status.isBlank()) {
            return suggestionRepository.findByWarehouseIdAndStatus(warehouseId, status);
        }
        return suggestionRepository.findByWarehouseId(warehouseId);
    }

    @Transactional
    public NxReplenishmentSuggestion approveSuggestion(UUID suggestionId, String approvedBy) {
        NxReplenishmentSuggestion suggestion = suggestionRepository.findById(suggestionId)
                .orElseThrow(() -> new ResourceNotFoundException("ReplenishmentSuggestion", suggestionId));

        if (!"PENDING".equals(suggestion.getStatus())) {
            throw new BadRequestException("Only PENDING suggestions can be approved");
        }

        suggestion.setStatus("APPROVED");
        suggestion.setApprovedBy(approvedBy);
        return suggestionRepository.save(suggestion);
    }

    @Transactional
    public NxReplenishmentSuggestion rejectSuggestion(UUID suggestionId, String reason) {
        NxReplenishmentSuggestion suggestion = suggestionRepository.findById(suggestionId)
                .orElseThrow(() -> new ResourceNotFoundException("ReplenishmentSuggestion", suggestionId));

        suggestion.setStatus("REJECTED");
        suggestion.setNotes(reason);
        return suggestionRepository.save(suggestion);
    }

    @Transactional
    public List<NxReplenishmentSuggestion> generateSuggestions(UUID warehouseId) {
        UUID tenantId = TenantContext.getCurrentTenantId();
        List<NxReplenishmentRule> rules = ruleRepository.findByWarehouseIdAndIsActiveTrue(warehouseId);
        List<NxReplenishmentSuggestion> created = new ArrayList<>();

        for (NxReplenishmentRule rule : rules) {
            List<NxInventory> items = inventoryRepository.findByTenantId(tenantId);
            for (NxInventory item : items) {
                int onHand = item.getQuantityOnHand() != null ? item.getQuantityOnHand() : 0;
                int reorderPt = rule.getReorderPoint() != null ? rule.getReorderPoint().intValue() : 0;
                int reorderQty = rule.getReorderQty() != null ? rule.getReorderQty().intValue() : 0;

                if (onHand <= reorderPt && reorderQty > 0) {
                    String priority = onHand == 0 ? "CRITICAL" : onHand <= reorderPt * 0.5 ? "HIGH" : "MEDIUM";

                    NxReplenishmentSuggestion suggestion = NxReplenishmentSuggestion.builder()
                            .tenantId(tenantId)
                            .warehouseId(warehouseId)
                            .inventoryItemId(item.getId())
                            .sku(item.getSku())
                            .productName(item.getSku())
                            .ruleId(rule.getId())
                            .ruleType(rule.getRuleType())
                            .currentQty(onHand)
                            .reorderPoint(reorderPt)
                            .suggestedQty(reorderQty)
                            .priority(priority)
                            .estimatedDeliveryDays(rule.getLeadTimeDays())
                            .status("PENDING")
                            .build();
                    created.add(suggestionRepository.save(suggestion));
                }
            }
        }
        return created;
    }

    public Map<String, Object> getReplenishmentStats(UUID warehouseId) {
        long pending = suggestionRepository.countByWarehouseIdAndStatus(warehouseId, "PENDING");
        long approved = suggestionRepository.countByWarehouseIdAndStatus(warehouseId, "APPROVED");
        long ordered = suggestionRepository.countByWarehouseIdAndStatus(warehouseId, "ORDERED");
        long totalRules = ruleRepository.findByWarehouseIdAndIsActiveTrue(warehouseId).size();

        Map<String, Object> result = new LinkedHashMap<>();
        result.put("totalRules", totalRules);
        result.put("pendingSuggestions", pending);
        result.put("approvedSuggestions", approved);
        result.put("orderedSuggestions", ordered);
        return result;
    }
}
