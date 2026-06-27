package com.nexus.oms.controller;

import com.nexus.oms.dto.ApiResponse;
import com.nexus.oms.dto.RoutingRuleRequest;
import com.nexus.oms.entity.NxRoutingRule;
import com.nexus.oms.security.TenantContext;
import com.nexus.oms.service.RoutingRuleService;
import jakarta.validation.Valid;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.UUID;

@RestController
@RequestMapping("/routing-rules")
public class RoutingRuleController {

    private final RoutingRuleService routingRuleService;

    public RoutingRuleController(RoutingRuleService routingRuleService) {
        this.routingRuleService = routingRuleService;
    }

    @GetMapping
    public ResponseEntity<ApiResponse<List<NxRoutingRule>>> getRules() {
        return ResponseEntity.ok(ApiResponse.success(
                routingRuleService.getRules(TenantContext.getCurrentTenantId())));
    }

    @GetMapping("/active")
    public ResponseEntity<ApiResponse<List<NxRoutingRule>>> getActiveRules() {
        return ResponseEntity.ok(ApiResponse.success(
                routingRuleService.getActiveRules(TenantContext.getCurrentTenantId())));
    }

    @GetMapping("/{id}")
    public ResponseEntity<ApiResponse<NxRoutingRule>> getRule(@PathVariable UUID id) {
        return ResponseEntity.ok(ApiResponse.success(routingRuleService.getRule(id)));
    }

    @PostMapping
    public ResponseEntity<ApiResponse<NxRoutingRule>> createRule(
            @Valid @RequestBody RoutingRuleRequest request) {
        return ResponseEntity.ok(ApiResponse.success(
                routingRuleService.createRule(TenantContext.getCurrentTenantId(), request),
                "Rule created"));
    }

    @PutMapping("/{id}")
    public ResponseEntity<ApiResponse<NxRoutingRule>> updateRule(
            @PathVariable UUID id,
            @Valid @RequestBody RoutingRuleRequest request) {
        return ResponseEntity.ok(ApiResponse.success(
                routingRuleService.updateRule(id, request), "Rule updated"));
    }

    @DeleteMapping("/{id}")
    public ResponseEntity<ApiResponse<Void>> deleteRule(@PathVariable UUID id) {
        routingRuleService.deleteRule(id);
        return ResponseEntity.ok(ApiResponse.success(null, "Rule deleted"));
    }

    @PutMapping("/reorder")
    public ResponseEntity<ApiResponse<Void>> reorderRules(
            @RequestBody List<UUID> ruleIds) {
        routingRuleService.reorderRules(TenantContext.getCurrentTenantId(), ruleIds);
        return ResponseEntity.ok(ApiResponse.success(null, "Rules reordered"));
    }
}
