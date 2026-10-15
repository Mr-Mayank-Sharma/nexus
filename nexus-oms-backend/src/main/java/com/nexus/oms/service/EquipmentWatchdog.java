package com.nexus.oms.service;

import com.nexus.oms.entity.NxAutomationAlert;
import com.nexus.oms.entity.NxAutomationCommand;
import com.nexus.oms.entity.NxAutomationLog;
import com.nexus.oms.entity.NxAutomationSystem;
import com.nexus.oms.repository.AutomationAlertRepository;
import com.nexus.oms.repository.AutomationCommandRepository;
import com.nexus.oms.repository.AutomationLogRepository;
import com.nexus.oms.repository.AutomationSystemRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Duration;
import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;

/**
 * Catches what equipment fails to say. A command that never reports back is failed after its timeout,
 * and equipment that used to send heartbeats and has gone quiet is marked ERROR with a critical alert.
 * Both are opened once, not every cycle, and the alert closes itself when the equipment reports again.
 * Only equipment that has reported at least once is watched: a system that never calls back is not "down".
 * Runs inside one tenant's context (see EquipmentWatchdogScheduler).
 */
@Service
public class EquipmentWatchdog {

    public static final String HEARTBEAT_ALERT = "HEARTBEAT_MISSED";
    public static final String COMMAND_ALERT = "COMMAND_TIMEOUT";
    static final long DEFAULT_COMMAND_TIMEOUT_MS = 300_000L;

    private static final Logger log = LoggerFactory.getLogger(EquipmentWatchdog.class);

    private final AutomationSystemRepository systems;
    private final AutomationCommandRepository commands;
    private final AutomationAlertRepository alerts;
    private final AutomationLogRepository logs;
    private final long heartbeatTimeoutSeconds;

    public EquipmentWatchdog(AutomationSystemRepository systems, AutomationCommandRepository commands,
                             AutomationAlertRepository alerts, AutomationLogRepository logs,
                             @Value("${nexus.equipment.heartbeat-timeout-seconds:300}") long heartbeatTimeoutSeconds) {
        this.systems = systems;
        this.commands = commands;
        this.alerts = alerts;
        this.logs = logs;
        this.heartbeatTimeoutSeconds = heartbeatTimeoutSeconds;
    }

    /** @return number of commands failed plus systems flagged, for the caller's log line */
    @Transactional
    public int sweep(LocalDateTime now) {
        return failTimedOutCommands(now) + flagSilentSystems(now);
    }

    int failTimedOutCommands(LocalDateTime now) {
        int failed = 0;
        for (NxAutomationCommand c : commands.findByStatusIn(List.of("SENT", "ACKNOWLEDGED", "EXECUTING"))) {
            if (c.getSentAt() == null) continue;
            long limit = c.getTimeoutMs() != null && c.getTimeoutMs() > 0 ? c.getTimeoutMs() : DEFAULT_COMMAND_TIMEOUT_MS;
            if (Duration.between(c.getSentAt(), now).toMillis() < limit) continue;
            c.setStatus("FAILED");
            c.setCompletedAt(now);
            c.setErrorMessage("Timed out: no result from the equipment within " + (limit / 1000) + "s");
            commands.save(c);
            raise(c.getTenantId(), c.getSystemId(), COMMAND_ALERT, "WARNING",
                    "Command timed out", c.getCommandType() + " got no result within " + (limit / 1000) + "s");
            record(c.getTenantId(), c.getSystemId(), c.getId(), "ERROR", "COMMAND_TIMEOUT", c.getErrorMessage());
            failed++;
        }
        return failed;
    }

    int flagSilentSystems(LocalDateTime now) {
        int flagged = 0;
        for (NxAutomationSystem s : systems.findByLastHeartbeatAtIsNotNull()) {
            if ("OFFLINE".equals(s.getStatus()) || "MAINTENANCE".equals(s.getStatus()) || "ERROR".equals(s.getStatus())) continue;
            if (Duration.between(s.getLastHeartbeatAt(), now).getSeconds() < heartbeatTimeoutSeconds) continue;
            s.setStatus("ERROR");
            s.setErrorMessage("No heartbeat since " + s.getLastHeartbeatAt());
            systems.save(s);
            raise(s.getTenantId(), s.getId(), HEARTBEAT_ALERT, "CRITICAL", "Equipment stopped reporting",
                    s.getSystemName() + " has sent no heartbeat since " + s.getLastHeartbeatAt());
            record(s.getTenantId(), s.getId(), null, "ERROR", "HEARTBEAT_MISSED", s.getErrorMessage());
            flagged++;
        }
        return flagged;
    }

    private void raise(UUID tenantId, UUID systemId, String type, String severity, String title, String description) {
        boolean open = alerts.findBySystemIdAndStatus(systemId, "ACTIVE").stream().anyMatch(a -> type.equals(a.getAlertType()));
        if (open && HEARTBEAT_ALERT.equals(type)) return;
        NxAutomationAlert a = new NxAutomationAlert();
        a.setTenantId(tenantId);
        a.setSystemId(systemId);
        a.setAlertType(type);
        a.setSeverity(severity);
        a.setStatus("ACTIVE");
        a.setTitle(title);
        a.setDescription(description);
        alerts.save(a);
    }

    private void record(UUID tenantId, UUID systemId, UUID commandId, String level, String event, String message) {
        NxAutomationLog l = new NxAutomationLog();
        l.setTenantId(tenantId);
        l.setSystemId(systemId);
        l.setCommandId(commandId);
        l.setLogLevel(level);
        l.setEvent(event);
        l.setMessage(message);
        logs.save(l);
        log.warn("Equipment watchdog: {} ({})", message, event);
    }
}
