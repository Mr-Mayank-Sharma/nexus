package com.nexus.oms.controller;

import com.nexus.oms.dto.ApiResponse;
import com.nexus.oms.entity.Workflow;
import com.nexus.oms.entity.WorkflowExecution;
import com.nexus.oms.entity.WorkflowStep;
import com.nexus.oms.service.WorkflowService;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;
import java.util.UUID;

@RestController
@RequestMapping("/workflows")
public class WorkflowController {

    private final WorkflowService workflowService;

    public WorkflowController(WorkflowService workflowService) {
        this.workflowService = workflowService;
    }

    @GetMapping
    public ResponseEntity<ApiResponse<Page<Workflow>>> getAllWorkflows(
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size) {
        return ResponseEntity.ok(ApiResponse.success(
                workflowService.getAllWorkflows(PageRequest.of(page, size))));
    }

    @GetMapping("/{id}")
    public ResponseEntity<ApiResponse<Workflow>> getWorkflow(@PathVariable UUID id) {
        return ResponseEntity.ok(ApiResponse.success(workflowService.getWorkflow(id)));
    }

    @PostMapping
    public ResponseEntity<ApiResponse<Workflow>> createWorkflow(@RequestBody Map<String, Object> request) {
        Workflow workflow = (Workflow) request.get("workflow");
        List<WorkflowStep> steps = (List<WorkflowStep>) request.get("steps");
        return ResponseEntity.ok(ApiResponse.success(
                workflowService.createWorkflow(workflow, steps), "Workflow created"));
    }

    @PutMapping("/{id}/status")
    public ResponseEntity<ApiResponse<Workflow>> updateWorkflowStatus(
            @PathVariable UUID id, @RequestParam String status) {
        return ResponseEntity.ok(ApiResponse.success(
                workflowService.updateWorkflowStatus(id, status), "Workflow status updated"));
    }

    @PutMapping("/{id}/toggle")
    public ResponseEntity<ApiResponse<Workflow>> toggleWorkflow(
            @PathVariable UUID id, @RequestParam boolean active) {
        return ResponseEntity.ok(ApiResponse.success(
                workflowService.toggleWorkflow(id, active), "Workflow toggled"));
    }

    @GetMapping("/{id}/steps")
    public ResponseEntity<ApiResponse<List<WorkflowStep>>> getWorkflowSteps(@PathVariable UUID id) {
        return ResponseEntity.ok(ApiResponse.success(workflowService.getWorkflowSteps(id)));
    }

    @PostMapping("/{id}/execute")
    public ResponseEntity<ApiResponse<WorkflowExecution>> executeWorkflow(
            @PathVariable UUID id, @RequestBody Map<String, Object> request) {
        String entityType = (String) request.get("entityType");
        String entityId = (String) request.get("entityId");
        Map<String, Object> inputData = (Map<String, Object>) request.get("inputData");
        return ResponseEntity.ok(ApiResponse.success(
                workflowService.executeWorkflow(id, entityType, entityId, inputData),
                "Workflow executed"));
    }

    @GetMapping("/{id}/executions")
    public ResponseEntity<ApiResponse<List<WorkflowExecution>>> getExecutions(@PathVariable UUID id) {
        return ResponseEntity.ok(ApiResponse.success(workflowService.getExecutions(id)));
    }
}
