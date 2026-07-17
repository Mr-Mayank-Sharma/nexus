package com.nexus.oms.controller;

import com.nexus.oms.dto.ApiResponse;
import com.nexus.oms.entity.AlertRule;
import com.nexus.oms.entity.NotificationLog;
import com.nexus.oms.entity.NotificationTemplate;
import com.nexus.oms.service.NotificationService;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import jakarta.validation.Valid;
import java.util.List;
import java.util.Map;
import java.util.UUID;

@RestController
@RequestMapping("/notifications")
public class NotificationController {

    private final NotificationService notificationService;

    public NotificationController(NotificationService notificationService) {
        this.notificationService = notificationService;
    }

    @GetMapping
    public ResponseEntity<ApiResponse<Map<String, String>>> getNotificationsDashboard() {
        return ResponseEntity.ok(ApiResponse.success(Map.of("status", "ok")));
    }

    @GetMapping("/templates")
    public ResponseEntity<ApiResponse<Page<NotificationTemplate>>> getAllTemplates(
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size) {
        return ResponseEntity.ok(ApiResponse.success(
                notificationService.getAllTemplates(PageRequest.of(page, size))));
    }

    @GetMapping("/templates/{id}")
    public ResponseEntity<ApiResponse<NotificationTemplate>> getTemplate(@PathVariable UUID id) {
        return ResponseEntity.ok(ApiResponse.success(notificationService.getTemplate(id)));
    }

    @PostMapping("/templates")
    public ResponseEntity<ApiResponse<NotificationTemplate>> createTemplate(
            @Valid @RequestBody NotificationTemplate template) {
        return ResponseEntity.ok(ApiResponse.success(
                notificationService.createTemplate(template), "Template created"));
    }

    @PutMapping("/templates/{id}")
    public ResponseEntity<ApiResponse<NotificationTemplate>> updateTemplate(
            @PathVariable UUID id, @Valid @RequestBody NotificationTemplate template) {
        return ResponseEntity.ok(ApiResponse.success(
                notificationService.updateTemplate(id, template), "Template updated"));
    }

    @PostMapping("/send")
    public ResponseEntity<ApiResponse<NotificationLog>> sendNotification(
            @RequestBody Map<String, Object> request) {
        String channel = (String) request.get("channel");
        String recipient = (String) request.get("recipient");
        String templateCode = (String) request.get("templateCode");
        Map<String, String> variables = (Map<String, String>) request.get("variables");
        return ResponseEntity.ok(ApiResponse.success(
                notificationService.sendNotification(channel, recipient, templateCode, variables),
                "Notification sent"));
    }

    @GetMapping("/logs")
    public ResponseEntity<ApiResponse<Page<NotificationLog>>> getNotificationLogs(
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size) {
        return ResponseEntity.ok(ApiResponse.success(
                notificationService.getNotificationLogs(PageRequest.of(page, size))));
    }

    @GetMapping("/alerts")
    public ResponseEntity<ApiResponse<List<AlertRule>>> getAlertRules() {
        return ResponseEntity.ok(ApiResponse.success(notificationService.getAlertRules()));
    }

    @PostMapping("/alerts")
    public ResponseEntity<ApiResponse<AlertRule>> createAlertRule(@Valid @RequestBody AlertRule rule) {
        return ResponseEntity.ok(ApiResponse.success(
                notificationService.createAlertRule(rule), "Alert rule created"));
    }

    @PutMapping("/alerts/{id}/toggle")
    public ResponseEntity<ApiResponse<AlertRule>> toggleAlertRule(
            @PathVariable UUID id, @RequestParam boolean active) {
        return ResponseEntity.ok(ApiResponse.success(
                notificationService.toggleAlertRule(id, active), "Alert rule toggled"));
    }

    @GetMapping("/unread-count")
    public ResponseEntity<ApiResponse<Long>> getUnreadCount() {
        return ResponseEntity.ok(ApiResponse.success(notificationService.getUnreadCount()));
    }
}
