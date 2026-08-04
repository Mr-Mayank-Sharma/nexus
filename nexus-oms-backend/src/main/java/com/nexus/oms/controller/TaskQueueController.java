package com.nexus.oms.controller;

import com.nexus.oms.dto.ApiResponse;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;
import java.util.UUID;

@RestController
@RequestMapping("/task-queues")
public class TaskQueueController {

    @GetMapping
    public ResponseEntity<ApiResponse<List<Map<String, Object>>>> getAllTaskQueues() {
        return ResponseEntity.ok(ApiResponse.success(List.of()));
    }

    @PatchMapping("/{id}")
    public ResponseEntity<ApiResponse<Map<String, Object>>> updateTaskQueue(
            @PathVariable UUID id, @RequestBody Map<String, Object> request) {
        return ResponseEntity.ok(ApiResponse.success(Map.of(
                "id", id.toString(),
                "status", "updated"
        ), "Task queue updated"));
    }
}
