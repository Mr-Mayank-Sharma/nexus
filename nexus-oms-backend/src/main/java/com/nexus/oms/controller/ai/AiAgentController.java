package com.nexus.oms.controller.ai;

import com.nexus.oms.security.TenantContext;
import com.nexus.oms.service.ai.AiAgentService;
import com.nexus.oms.service.ai.AiPackingService;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.LinkedHashMap;
import java.util.Map;

@RestController
@RequestMapping("/ai")
public class AiAgentController {

    private final AiAgentService aiAgentService;
    private final AiPackingService aiPackingService;

    public AiAgentController(AiAgentService aiAgentService, AiPackingService aiPackingService) {
        this.aiAgentService = aiAgentService;
        this.aiPackingService = aiPackingService;
    }

    @GetMapping("/routing")
    public ResponseEntity<Map<String, Object>> getRouting() {
        Map<String, Object> body = new LinkedHashMap<>();
        body.put("agents", aiAgentService.getAgents(TenantContext.getCurrentTenantId()));
        return ResponseEntity.ok(body);
    }

    @GetMapping("/routing/queue")
    public ResponseEntity<Map<String, Object>> getRoutingQueue() {
        Map<String, Object> body = new LinkedHashMap<>();
        body.put("queue", aiAgentService.getRoutingQueue(TenantContext.getCurrentTenantId()));
        return ResponseEntity.ok(body);
    }

    @GetMapping("/briefing")
    public ResponseEntity<Map<String, Object>> getBriefing() {
        return ResponseEntity.ok(aiAgentService.getBriefing(TenantContext.getCurrentTenantId()));
    }

    @GetMapping("/packing")
    public ResponseEntity<Map<String, Object>> getPacking() {
        Map<String, Object> body = new LinkedHashMap<>();
        body.put("orders", aiPackingService.getPackingPlans(TenantContext.getCurrentTenantId()));
        return ResponseEntity.ok(body);
    }

    @GetMapping("/loading")
    public ResponseEntity<Map<String, Object>> getLoading() {
        Map<String, Object> body = new LinkedHashMap<>();
        body.put("trucks", aiAgentService.getLoadingPlans(TenantContext.getCurrentTenantId()));
        return ResponseEntity.ok(body);
    }

    @GetMapping("/forecasting")
    public ResponseEntity<Map<String, Object>> getForecasting() {
        return ResponseEntity.ok(aiAgentService.getForecast(TenantContext.getCurrentTenantId()));
    }

    @PostMapping("/recommendations/{id}/respond")
    public ResponseEntity<Map<String, Object>> respondToRecommendation(
            @PathVariable String id, @RequestBody Map<String, String> body) {
        String action = body.getOrDefault("action", "pending");
        return ResponseEntity.ok(Map.of(
            "success", true,
            "id", id,
            "action", action,
            "message", "Recommendation " + action + " successfully"
        ));
    }
}
