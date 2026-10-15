package com.nexus.oms.service;

import com.nexus.oms.entity.NxAutomationCommand;
import com.nexus.oms.entity.NxAutomationLog;
import com.nexus.oms.entity.NxAutomationSystem;
import com.nexus.oms.repository.AutomationCommandRepository;
import com.nexus.oms.repository.AutomationLogRepository;
import com.nexus.oms.repository.AutomationSystemRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.time.LocalDateTime;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

/**
 * The inbound half of equipment automation: the machine (or its controller) tells Nexus what happened.
 * Until now commands went out and nothing could come back, so a command stayed SENT forever unless the
 * synchronous HTTP reply happened to say more. A callback carries the system's shared secret and moves a
 * command through SENT, ACKNOWLEDGED, EXECUTING and then COMPLETED or FAILED. Repeating the same status
 * is accepted and changes nothing, because equipment retries.
 */
@Service
public class EquipmentCallbackService {

    public static class Rejected extends RuntimeException {
        private final int httpStatus;
        public Rejected(int httpStatus, String message) { super(message); this.httpStatus = httpStatus; }
        public int httpStatus() { return httpStatus; }
    }

    private static final Set<String> TERMINAL = Set.of("COMPLETED", "FAILED", "CANCELLED");
    private static final Map<String, Integer> ORDER = Map.of("PENDING", 0, "SENT", 1, "ACKNOWLEDGED", 2, "EXECUTING", 3, "COMPLETED", 4, "FAILED", 4);

    private final AutomationSystemRepository systems;
    private final AutomationCommandRepository commands;
    private final AutomationLogRepository logs;

    public EquipmentCallbackService(AutomationSystemRepository systems, AutomationCommandRepository commands,
                                    AutomationLogRepository logs) {
        this.systems = systems;
        this.commands = commands;
        this.logs = logs;
    }

    /** Equipment is alive. Marks the system ONLINE (unless it is switched off or in maintenance) and records when. */
    @Transactional
    public NxAutomationSystem heartbeat(UUID systemId, String presentedKey, String reportedStatus) {
        NxAutomationSystem system = authenticate(systemId, presentedKey);
        system.setLastHealthCheckAt(LocalDateTime.now());
        if ("ERROR".equals(reportedStatus) || "DEGRADED".equals(reportedStatus)) {
            system.setStatus(reportedStatus);
        } else if (!"OFFLINE".equals(system.getStatus()) && !"MAINTENANCE".equals(system.getStatus())) {
            system.setStatus("ONLINE");
        }
        return systems.save(system);
    }

    @Transactional
    public NxAutomationCommand report(UUID systemId, UUID commandId, String presentedKey, String newStatus,
                                      String resultJson, String error) {
        NxAutomationSystem system = authenticate(systemId, presentedKey);
        String status = newStatus == null ? "" : newStatus.trim().toUpperCase();
        if (!ORDER.containsKey(status) || "PENDING".equals(status) || "SENT".equals(status)) {
            throw new Rejected(400, "status must be one of ACKNOWLEDGED, EXECUTING, COMPLETED, FAILED");
        }
        NxAutomationCommand command = commands.findById(commandId)
                .filter(c -> systemId.equals(c.getSystemId()))
                .orElseThrow(() -> new Rejected(404, "No such command for this system"));

        if (status.equals(command.getStatus())) return command;                       // a retried callback
        if (TERMINAL.contains(command.getStatus())) {
            throw new Rejected(409, "Command is already " + command.getStatus() + " and cannot become " + status);
        }
        if (ORDER.getOrDefault(command.getStatus(), 0) > ORDER.get(status)) {
            throw new Rejected(409, "Command is " + command.getStatus() + "; it cannot go back to " + status);
        }

        LocalDateTime now = LocalDateTime.now();
        command.setStatus(status);
        if ("ACKNOWLEDGED".equals(status) && command.getAcknowledgedAt() == null) command.setAcknowledgedAt(now);
        if ("COMPLETED".equals(status) || "FAILED".equals(status)) {
            command.setCompletedAt(now);
            if (command.getSentAt() != null) command.setExecutionTimeMs(java.time.Duration.between(command.getSentAt(), now).toMillis());
            if (resultJson != null) command.setResult(resultJson);
            if ("FAILED".equals(status)) command.setErrorMessage(error != null ? error : "Reported failed by equipment");
        }
        commands.save(command);

        NxAutomationLog log = new NxAutomationLog();
        log.setTenantId(system.getTenantId());
        log.setSystemId(systemId);
        log.setCommandId(commandId);
        log.setLogLevel("FAILED".equals(status) ? "ERROR" : "INFO");
        log.setEvent("EQUIPMENT_" + status);
        log.setMessage("Equipment reported " + status + " for " + command.getCommandType() + (error != null ? ": " + error : ""));
        logs.save(log);

        system.setLastHealthCheckAt(now);
        systems.save(system);
        return command;
    }

    private NxAutomationSystem authenticate(UUID systemId, String presentedKey) {
        NxAutomationSystem system = systems.findById(systemId).orElseThrow(() -> new Rejected(404, "Unknown equipment system"));
        String expected = system.getApiKey();
        if (expected == null || expected.isBlank()) {
            throw new Rejected(403, "This system has no shared secret configured, so it cannot call back");
        }
        boolean ok = presentedKey != null && MessageDigest.isEqual(
                expected.getBytes(StandardCharsets.UTF_8), presentedKey.getBytes(StandardCharsets.UTF_8));
        if (!ok) throw new Rejected(401, "Invalid equipment key");
        return system;
    }
}
