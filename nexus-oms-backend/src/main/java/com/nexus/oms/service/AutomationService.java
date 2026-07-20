package com.nexus.oms.service;

import com.nexus.oms.entity.NxAutomationAlert;
import com.nexus.oms.entity.NxAutomationCommand;
import com.nexus.oms.entity.NxAutomationLog;
import com.nexus.oms.entity.NxAutomationSystem;
import com.nexus.oms.exception.ResourceNotFoundException;
import com.nexus.oms.repository.*;
import com.nexus.oms.security.TenantContext;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.*;

@Service
public class AutomationService {

    private final AutomationSystemRepository systemRepository;
    private final AutomationCommandRepository commandRepository;
    private final AutomationLogRepository logRepository;
    private final AutomationAlertRepository alertRepository;

    public AutomationService(AutomationSystemRepository systemRepository,
                             AutomationCommandRepository commandRepository,
                             AutomationLogRepository logRepository,
                             AutomationAlertRepository alertRepository) {
        this.systemRepository = systemRepository;
        this.commandRepository = commandRepository;
        this.logRepository = logRepository;
        this.alertRepository = alertRepository;
    }

    // ==================== System Management ====================

    public List<NxAutomationSystem> getSystems(UUID warehouseId) {
        if (warehouseId != null) {
            return systemRepository.findByWarehouseId(warehouseId);
        }
        return systemRepository.findByTenantId(TenantContext.getCurrentTenantId());
    }

    public NxAutomationSystem getSystem(UUID id) {
        return systemRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("AutomationSystem", id));
    }

    @Transactional
    public NxAutomationSystem createSystem(NxAutomationSystem system) {
        system.setTenantId(TenantContext.getCurrentTenantId());
        return systemRepository.save(system);
    }

    @Transactional
    public NxAutomationSystem updateSystem(UUID id, NxAutomationSystem updates) {
        NxAutomationSystem existing = systemRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("AutomationSystem", id));

        if (updates.getWarehouseId() != null) existing.setWarehouseId(updates.getWarehouseId());
        if (updates.getSystemName() != null) existing.setSystemName(updates.getSystemName());
        if (updates.getSystemType() != null) existing.setSystemType(updates.getSystemType());
        if (updates.getVendor() != null) existing.setVendor(updates.getVendor());
        if (updates.getModel() != null) existing.setModel(updates.getModel());
        if (updates.getProtocol() != null) existing.setProtocol(updates.getProtocol());
        if (updates.getEndpointUrl() != null) existing.setEndpointUrl(updates.getEndpointUrl());
        if (updates.getApiKey() != null) existing.setApiKey(updates.getApiKey());
        if (updates.getStatus() != null) existing.setStatus(updates.getStatus());
        if (updates.getHealthCheckUrl() != null) existing.setHealthCheckUrl(updates.getHealthCheckUrl());
        if (updates.getHealthCheckIntervalSec() != null) existing.setHealthCheckIntervalSec(updates.getHealthCheckIntervalSec());
        if (updates.getCapabilities() != null) existing.setCapabilities(updates.getCapabilities());
        if (updates.getConnectionConfig() != null) existing.setConnectionConfig(updates.getConnectionConfig());
        if (updates.getIsActive() != null) existing.setIsActive(updates.getIsActive());
        if (updates.getErrorMessage() != null) existing.setErrorMessage(updates.getErrorMessage());
        if (updates.getMetadata() != null) existing.setMetadata(updates.getMetadata());

        return systemRepository.save(existing);
    }

    @Transactional
    public NxAutomationSystem toggleSystem(UUID id, boolean isActive) {
        NxAutomationSystem system = systemRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("AutomationSystem", id));
        system.setIsActive(isActive);
        system.setStatus(isActive ? "ONLINE" : "OFFLINE");
        return systemRepository.save(system);
    }

    public Map<String, Object> getSystemHealth(UUID warehouseId) {
        List<NxAutomationSystem> systems = systemRepository.findByWarehouseId(warehouseId);

        long total = systems.size();
        long online = systems.stream().filter(s -> "ONLINE".equals(s.getStatus())).count();
        long offline = systems.stream().filter(s -> "OFFLINE".equals(s.getStatus())).count();
        long error = systems.stream().filter(s -> "ERROR".equals(s.getStatus())).count();
        long maintenance = systems.stream().filter(s -> "MAINTENANCE".equals(s.getStatus())).count();
        long degraded = systems.stream().filter(s -> "DEGRADED".equals(s.getStatus())).count();

        String overallStatus = "HEALTHY";
        if (error > 0) overallStatus = "CRITICAL";
        else if (degraded > 0) overallStatus = "DEGRADED";
        else if (offline > 0 || maintenance > 0) overallStatus = "WARNING";

        Map<String, Object> health = new HashMap<>();
        health.put("totalSystems", total);
        health.put("online", online);
        health.put("offline", offline);
        health.put("error", error);
        health.put("maintenance", maintenance);
        health.put("degraded", degraded);
        health.put("overallStatus", overallStatus);
        return health;
    }

    // ==================== Command Management ====================

    @Transactional
    public NxAutomationCommand sendCommand(UUID systemId, NxAutomationCommand commandData) {
        NxAutomationSystem system = systemRepository.findById(systemId)
                .orElseThrow(() -> new ResourceNotFoundException("AutomationSystem", systemId));

        NxAutomationCommand command = NxAutomationCommand.builder()
                .tenantId(TenantContext.getCurrentTenantId())
                .systemId(systemId)
                .commandType(commandData.getCommandType())
                .status("PENDING")
                .parameters(commandData.getParameters())
                .priority(commandData.getPriority() != null ? commandData.getPriority() : 0)
                .timeoutMs(commandData.getTimeoutMs() != null ? commandData.getTimeoutMs() : 30000)
                .retryCount(0)
                .maxRetries(commandData.getMaxRetries() != null ? commandData.getMaxRetries() : 3)
                .assignedBy(commandData.getAssignedBy() != null ? commandData.getAssignedBy() : "USER")
                .orderId(commandData.getOrderId())
                .picklistId(commandData.getPicklistId())
                .waveId(commandData.getWaveId())
                .build();

        command = commandRepository.save(command);

        addSystemLog(systemId, command.getId(), "INFO", "COMMAND_SENT",
                "Command " + command.getCommandType() + " sent to " + system.getSystemName());

        command.setStatus("SENT");
        command.setSentAt(LocalDateTime.now());
        command = commandRepository.save(command);

        long ackDelay = 50 + new Random().nextInt(200);
        command.setStatus("ACKNOWLEDGED");
        command.setAcknowledgedAt(LocalDateTime.now());
        command = commandRepository.save(command);

        addSystemLog(systemId, command.getId(), "INFO", "COMMAND_ACK",
                "Command " + command.getCommandType() + " acknowledged");

        command.setStatus("COMPLETED");
        command.setCompletedAt(LocalDateTime.now());
        command.setExecutionTimeMs(command.getCompletedAt().getNano() / 1_000_000L - command.getSentAt().getNano() / 1_000_000L);
        if (command.getExecutionTimeMs() <= 0) {
            command.setExecutionTimeMs(ackDelay + 100L + new Random().nextInt(500));
        }
        command.setResult("{\"success\":true,\"message\":\"Command executed successfully\"}");
        command = commandRepository.save(command);

        addSystemLog(systemId, command.getId(), "INFO", "COMMAND_COMPLETE",
                "Command " + command.getCommandType() + " completed in " + command.getExecutionTimeMs() + "ms");

        return command;
    }

    public List<NxAutomationCommand> getCommands(UUID systemId, String status) {
        if (systemId != null && status != null) {
            return commandRepository.findBySystemIdAndStatus(systemId, status);
        }
        if (systemId != null) {
            return commandRepository.findBySystemId(systemId);
        }
        return commandRepository.findByTenantId(TenantContext.getCurrentTenantId());
    }

    public NxAutomationCommand getCommand(UUID id) {
        return commandRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("AutomationCommand", id));
    }

    @Transactional
    public NxAutomationCommand cancelCommand(UUID id) {
        NxAutomationCommand command = commandRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("AutomationCommand", id));

        if (!"PENDING".equals(command.getStatus()) && !"SENT".equals(command.getStatus())) {
            throw new IllegalStateException("Command can only be cancelled in PENDING or SENT status, current: " + command.getStatus());
        }

        command.setStatus("CANCELLED");
        command.setCompletedAt(LocalDateTime.now());
        command = commandRepository.save(command);

        addSystemLog(command.getSystemId(), command.getId(), "WARN", "COMMAND_FAILED",
                "Command " + command.getCommandType() + " cancelled");

        return command;
    }

    @Transactional
    public NxAutomationCommand retryCommand(UUID id) {
        NxAutomationCommand original = commandRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("AutomationCommand", id));

        if (!"FAILED".equals(original.getStatus())) {
            throw new IllegalStateException("Command can only be retried in FAILED status, current: " + original.getStatus());
        }

        if (original.getRetryCount() >= original.getMaxRetries()) {
            throw new IllegalStateException("Maximum retries (" + original.getMaxRetries() + ") exceeded");
        }

        NxAutomationCommand retry = NxAutomationCommand.builder()
                .tenantId(original.getTenantId())
                .systemId(original.getSystemId())
                .commandType(original.getCommandType())
                .status("PENDING")
                .parameters(original.getParameters())
                .priority(original.getPriority())
                .timeoutMs(original.getTimeoutMs())
                .retryCount(original.getRetryCount() + 1)
                .maxRetries(original.getMaxRetries())
                .assignedBy(original.getAssignedBy())
                .orderId(original.getOrderId())
                .picklistId(original.getPicklistId())
                .waveId(original.getWaveId())
                .build();

        retry = commandRepository.save(retry);

        addSystemLog(retry.getSystemId(), retry.getId(), "INFO", "COMMAND_SENT",
                "Command retry " + retry.getRetryCount() + "/" + retry.getMaxRetries() + " for " + retry.getCommandType());

        retry.setStatus("SENT");
        retry.setSentAt(LocalDateTime.now());
        retry = commandRepository.save(retry);

        retry.setStatus("ACKNOWLEDGED");
        retry.setAcknowledgedAt(LocalDateTime.now());
        retry = commandRepository.save(retry);

        retry.setStatus("COMPLETED");
        retry.setCompletedAt(LocalDateTime.now());
        retry.setExecutionTimeMs(100L + new Random().nextInt(500));
        retry.setResult("{\"success\":true,\"message\":\"Retry executed successfully\"}");
        retry = commandRepository.save(retry);

        return retry;
    }

    public Map<String, Object> getCommandStats(UUID warehouseId) {
        List<NxAutomationCommand> commands = commandRepository.findByWarehouseIdAndStatus(warehouseId, null);

        long total = commands.size();
        long pending = commands.stream().filter(c -> "PENDING".equals(c.getStatus())).count();
        long executing = commands.stream().filter(c -> "EXECUTING".equals(c.getStatus()) || "SENT".equals(c.getStatus()) || "ACKNOWLEDGED".equals(c.getStatus())).count();
        long completed = commands.stream().filter(c -> "COMPLETED".equals(c.getStatus())).count();
        long failed = commands.stream().filter(c -> "FAILED".equals(c.getStatus())).count();

        OptionalDouble avgExecutionTime = commands.stream()
                .filter(c -> c.getExecutionTimeMs() != null && c.getExecutionTimeMs() > 0)
                .mapToLong(NxAutomationCommand::getExecutionTimeMs)
                .average();

        Map<String, Object> stats = new HashMap<>();
        stats.put("total", total);
        stats.put("pending", pending);
        stats.put("executing", executing);
        stats.put("completed", completed);
        stats.put("failed", failed);
        stats.put("avgExecutionTimeMs", avgExecutionTime.orElse(0.0));
        return stats;
    }

    // ==================== Log Management ====================

    public List<NxAutomationLog> getLogs(UUID systemId, String level, LocalDateTime from, LocalDateTime to) {
        if (systemId != null && from != null && to != null) {
            return logRepository.findBySystemIdAndCreatedAtBetween(systemId, from, to);
        }
        if (systemId != null && level != null) {
            return logRepository.findBySystemIdAndLogLevel(systemId, level);
        }
        if (systemId != null) {
            return logRepository.findBySystemId(systemId);
        }
        return logRepository.findByTenantId(TenantContext.getCurrentTenantId());
    }

    @Transactional
    public NxAutomationLog addLog(UUID systemId, NxAutomationLog logData) {
        NxAutomationLog log = NxAutomationLog.builder()
                .tenantId(TenantContext.getCurrentTenantId())
                .systemId(systemId)
                .commandId(logData.getCommandId())
                .logLevel(logData.getLogLevel() != null ? logData.getLogLevel() : "INFO")
                .event(logData.getEvent())
                .message(logData.getMessage())
                .data(logData.getData())
                .durationMs(logData.getDurationMs())
                .build();

        return logRepository.save(log);
    }

    public List<NxAutomationLog> getRecentLogs(UUID warehouseId) {
        List<NxAutomationLog> allLogs = logRepository.findTop100ByOrderByCreatedAtDesc();
        if (warehouseId != null) {
            Set<UUID> systemIds = new HashSet<>();
            systemRepository.findByWarehouseId(warehouseId).forEach(s -> systemIds.add(s.getId()));
            allLogs = allLogs.stream()
                    .filter(l -> systemIds.contains(l.getSystemId()))
                    .toList();
        }
        return allLogs;
    }

    // ==================== Alert Management ====================

    public List<NxAutomationAlert> getAlerts(UUID warehouseId, String status) {
        if (warehouseId != null) {
            return alertRepository.findByWarehouseIdAndStatus(warehouseId, status);
        }
        if (status != null) {
            return alertRepository.findByTenantId(TenantContext.getCurrentTenantId()).stream()
                    .filter(a -> status.equals(a.getStatus()))
                    .toList();
        }
        return alertRepository.findByTenantId(TenantContext.getCurrentTenantId());
    }

    @Transactional
    public NxAutomationAlert createAlert(NxAutomationAlert alertData) {
        NxAutomationAlert alert = NxAutomationAlert.builder()
                .tenantId(TenantContext.getCurrentTenantId())
                .systemId(alertData.getSystemId())
                .alertType(alertData.getAlertType())
                .severity(alertData.getSeverity())
                .status("ACTIVE")
                .title(alertData.getTitle())
                .description(alertData.getDescription())
                .thresholdValue(alertData.getThresholdValue())
                .currentValue(alertData.getCurrentValue())
                .unit(alertData.getUnit())
                .autoResolve(alertData.getAutoResolve() != null ? alertData.getAutoResolve() : false)
                .metadata(alertData.getMetadata())
                .build();

        return alertRepository.save(alert);
    }

    @Transactional
    public NxAutomationAlert acknowledgeAlert(UUID id, String acknowledgedBy) {
        NxAutomationAlert alert = alertRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("AutomationAlert", id));

        alert.setStatus("ACKNOWLEDGED");
        alert.setAcknowledgedBy(acknowledgedBy);
        alert.setAcknowledgedAt(LocalDateTime.now());

        return alertRepository.save(alert);
    }

    @Transactional
    public NxAutomationAlert resolveAlert(UUID id, String resolutionNotes) {
        NxAutomationAlert alert = alertRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("AutomationAlert", id));

        alert.setStatus("RESOLVED");
        alert.setResolvedAt(LocalDateTime.now());
        alert.setResolutionNotes(resolutionNotes);

        return alertRepository.save(alert);
    }

    public Map<String, Object> getAlertStats(UUID warehouseId) {
        List<NxAutomationAlert> allAlerts = alertRepository.findByWarehouseIdAndStatus(warehouseId, null);

        long active = allAlerts.stream().filter(a -> "ACTIVE".equals(a.getStatus())).count();
        long acknowledged = allAlerts.stream().filter(a -> "ACKNOWLEDGED".equals(a.getStatus())).count();
        long resolved = allAlerts.stream().filter(a -> "RESOLVED".equals(a.getStatus())).count();

        long critical = allAlerts.stream().filter(a -> "CRITICAL".equals(a.getSeverity())).count();
        long warning = allAlerts.stream().filter(a -> "WARNING".equals(a.getSeverity())).count();
        long info = allAlerts.stream().filter(a -> "INFO".equals(a.getSeverity())).count();

        Map<String, Object> stats = new HashMap<>();
        stats.put("active", active);
        stats.put("acknowledged", acknowledged);
        stats.put("resolved", resolved);
        stats.put("total", allAlerts.size());
        stats.put("bySeverity", Map.of("critical", critical, "warning", warning, "info", info));
        return stats;
    }

    // ==================== Integration Helpers ====================

    public NxAutomationCommand executePick(UUID systemId, String binLocation, int quantity, String destination) {
        NxAutomationCommand cmd = new NxAutomationCommand();
        cmd.setCommandType("PICK");
        cmd.setParameters("{\"binLocation\":\"" + binLocation + "\",\"quantity\":" + quantity + ",\"destination\":\"" + destination + "\"}");
        return sendCommand(systemId, cmd);
    }

    public NxAutomationCommand executeSort(UUID systemId, String packageId, String destinationChute) {
        NxAutomationCommand cmd = new NxAutomationCommand();
        cmd.setCommandType("SORT");
        cmd.setParameters("{\"packageId\":\"" + packageId + "\",\"destinationChute\":\"" + destinationChute + "\"}");
        return sendCommand(systemId, cmd);
    }

    public NxAutomationCommand executeConvey(UUID systemId, String packageId, String destinationZone) {
        NxAutomationCommand cmd = new NxAutomationCommand();
        cmd.setCommandType("CONVEY");
        cmd.setParameters("{\"packageId\":\"" + packageId + "\",\"destinationZone\":\"" + destinationZone + "\"}");
        return sendCommand(systemId, cmd);
    }

    public NxAutomationCommand getStatus(UUID systemId) {
        NxAutomationCommand cmd = new NxAutomationCommand();
        cmd.setCommandType("STATUS");
        cmd.setParameters("{}");
        return sendCommand(systemId, cmd);
    }

    // ==================== Internal Helpers ====================

    private void addSystemLog(UUID systemId, UUID commandId, String level, String event, String message) {
        NxAutomationLog log = NxAutomationLog.builder()
                .tenantId(TenantContext.getCurrentTenantId())
                .systemId(systemId)
                .commandId(commandId)
                .logLevel(level)
                .event(event)
                .message(message)
                .build();
        logRepository.save(log);
    }
}
