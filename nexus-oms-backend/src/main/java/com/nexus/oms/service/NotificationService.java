package com.nexus.oms.service;

import com.nexus.oms.entity.AlertRule;
import com.nexus.oms.entity.NotificationLog;
import com.nexus.oms.entity.NotificationTemplate;
import com.nexus.oms.exception.BadRequestException;
import com.nexus.oms.exception.ResourceNotFoundException;
import com.nexus.oms.repository.AlertRuleRepository;
import com.nexus.oms.repository.NotificationLogRepository;
import com.nexus.oms.repository.NotificationTemplateRepository;
import com.nexus.oms.security.TenantContext;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;
import java.util.UUID;

@Service
public class NotificationService {

    private final NotificationTemplateRepository notificationTemplateRepository;
    private final NotificationLogRepository notificationLogRepository;
    private final AlertRuleRepository alertRuleRepository;

    public NotificationService(NotificationTemplateRepository notificationTemplateRepository,
                               NotificationLogRepository notificationLogRepository,
                               AlertRuleRepository alertRuleRepository) {
        this.notificationTemplateRepository = notificationTemplateRepository;
        this.notificationLogRepository = notificationLogRepository;
        this.alertRuleRepository = alertRuleRepository;
    }

    public Page<NotificationTemplate> getAllTemplates(Pageable pageable) {
        UUID tenantId = TenantContext.getCurrentTenantId();
        return notificationTemplateRepository.findByTenantId(tenantId, pageable);
    }

    public NotificationTemplate getTemplate(UUID id) {
        UUID tenantId = TenantContext.getCurrentTenantId();
        return notificationTemplateRepository.findById(id)
                .filter(t -> t.getTenantId().equals(tenantId))
                .orElseThrow(() -> new ResourceNotFoundException("NotificationTemplate", id));
    }

    @Transactional
    public NotificationTemplate createTemplate(NotificationTemplate t) {
        UUID tenantId = TenantContext.getCurrentTenantId();
        t.setTenantId(tenantId);
        return notificationTemplateRepository.save(t);
    }

    @Transactional
    public NotificationTemplate updateTemplate(UUID id, NotificationTemplate updates) {
        UUID tenantId = TenantContext.getCurrentTenantId();
        NotificationTemplate t = notificationTemplateRepository.findById(id)
                .filter(tmpl -> tmpl.getTenantId().equals(tenantId))
                .orElseThrow(() -> new ResourceNotFoundException("NotificationTemplate", id));
        if (updates.getTemplateCode() != null) t.setTemplateCode(updates.getTemplateCode());
        if (updates.getName() != null) t.setName(updates.getName());
        if (updates.getChannel() != null) t.setChannel(updates.getChannel());
        if (updates.getSubject() != null) t.setSubject(updates.getSubject());
        if (updates.getBody() != null) t.setBody(updates.getBody());
        if (updates.getVariables() != null) t.setVariables(updates.getVariables());
        if (updates.getIsActive() != null) t.setIsActive(updates.getIsActive());
        return notificationTemplateRepository.save(t);
    }

    @Transactional
    public NotificationLog sendNotification(String channel, String recipient, String templateCode,
                                            Map<String, String> variables) {
        UUID tenantId = TenantContext.getCurrentTenantId();
        NotificationTemplate template = notificationTemplateRepository
                .findByTenantIdAndTemplateCodeAndChannel(tenantId, templateCode, channel)
                .orElseThrow(() -> new ResourceNotFoundException(
                        "NotificationTemplate with code " + templateCode + " and channel " + channel, ""));

        String resolvedBody = template.getBody();
        if (variables != null) {
            for (Map.Entry<String, String> entry : variables.entrySet()) {
                resolvedBody = resolvedBody.replace("{{" + entry.getKey() + "}}", entry.getValue());
            }
        }

        NotificationLog log = NotificationLog.builder()
                .tenantId(tenantId)
                .channel(channel)
                .recipient(recipient)
                .subject(template.getSubject())
                .body(resolvedBody)
                .status("SENT")
                .sentAt(LocalDateTime.now())
                .build();
        return notificationLogRepository.save(log);
    }

    public Page<NotificationLog> getNotificationLogs(Pageable pageable) {
        UUID tenantId = TenantContext.getCurrentTenantId();
        return notificationLogRepository.findByTenantId(tenantId, pageable);
    }

    public List<AlertRule> getAlertRules() {
        UUID tenantId = TenantContext.getCurrentTenantId();
        return alertRuleRepository.findByTenantIdAndIsActive(tenantId, true);
    }

    @Transactional
    public AlertRule createAlertRule(AlertRule rule) {
        UUID tenantId = TenantContext.getCurrentTenantId();
        rule.setTenantId(tenantId);
        return alertRuleRepository.save(rule);
    }

    @Transactional
    public AlertRule toggleAlertRule(UUID id, boolean active) {
        UUID tenantId = TenantContext.getCurrentTenantId();
        AlertRule rule = alertRuleRepository.findById(id)
                .filter(r -> r.getTenantId().equals(tenantId))
                .orElseThrow(() -> new ResourceNotFoundException("AlertRule", id));
        rule.setIsActive(active);
        return alertRuleRepository.save(rule);
    }

    public Long getUnreadCount() {
        UUID tenantId = TenantContext.getCurrentTenantId();
        return notificationLogRepository.countByTenantIdAndStatus(tenantId, "PENDING");
    }
}
