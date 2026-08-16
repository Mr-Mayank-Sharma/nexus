package com.nexus.oms.service;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.nexus.oms.entity.NxAutomationCommand;
import com.nexus.oms.entity.NxAutomationLog;
import com.nexus.oms.entity.NxAutomationSystem;
import com.nexus.oms.repository.AutomationCommandRepository;
import com.nexus.oms.repository.AutomationLogRepository;
import com.nexus.oms.repository.AutomationSystemRepository;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Duration;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;

@Service
public class AutomationEmulatorService {

    private static final ObjectMapper MAPPER = new ObjectMapper();

    private static final Map<String, Duration> WORK_DURATION_BY_TYPE = Map.of(
            "PICK", Duration.ofMillis(1200),
            "PICK_PACK", Duration.ofMillis(1500),
            "MOVE", Duration.ofMillis(800),
            "PUTAWAY", Duration.ofMillis(900),
            "CYCLE_COUNT", Duration.ofMillis(2000),
            "RECEIVE", Duration.ofMillis(1100)
    );

    private static final Duration DEFAULT_WORK_DURATION = Duration.ofMillis(1000);

    private final AutomationSystemRepository systemRepository;
    private final AutomationCommandRepository commandRepository;
    private final AutomationLogRepository logRepository;
    private final SlottingService slottingService;

    public AutomationEmulatorService(AutomationSystemRepository systemRepository,
                                     AutomationCommandRepository commandRepository,
                                     AutomationLogRepository logRepository,
                                     SlottingService slottingService) {
        this.systemRepository = systemRepository;
        this.commandRepository = commandRepository;
        this.logRepository = logRepository;
        this.slottingService = slottingService;
    }

    @Scheduled(fixedDelay = 3000)
    @Transactional
    public void advanceEmulatorSystems() {
        List<NxAutomationSystem> emulators = new ArrayList<>();
        for (NxAutomationSystem system : systemRepository.findAll()) {
            if (isEmulatorSystem(system)) {
                emulators.add(system);
            }
        }

        for (NxAutomationSystem system : emulators) {
            List<NxAutomationCommand> sent = commandRepository.findBySystemIdAndStatus(system.getId(), "SENT");
            for (NxAutomationCommand command : sent) {
                command.setStatus("EXECUTING");
                command.setAcknowledgedAt(LocalDateTime.now());
                commandRepository.save(command);
                log(system, command, "INFO", "COMMAND_ACK",
                        "Command " + command.getCommandType() + " accepted by emulator system " + system.getSystemName());
            }

            List<NxAutomationCommand> executing = commandRepository.findBySystemIdAndStatus(system.getId(), "EXECUTING");
            for (NxAutomationCommand command : executing) {
                LocalDateTime ackAt = command.getAcknowledgedAt() != null
                        ? command.getAcknowledgedAt()
                        : command.getSentAt() != null ? command.getSentAt() : command.getCreatedAt();
                Duration work = workDuration(command.getCommandType());
                Duration elapsed = Duration.between(ackAt, LocalDateTime.now());
                if (elapsed.compareTo(work) < 0) {
                    continue;
                }

                command.setStatus("COMPLETED");
                command.setCompletedAt(LocalDateTime.now());
                command.setExecutionTimeMs(Math.max(1, Duration.between(
                        command.getSentAt() != null ? command.getSentAt() : ackAt,
                        command.getCompletedAt()).toMillis()));
                command.setResult("{\"success\":true,\"message\":\"Command executed successfully\",\"simulated\":true,\"reason\":\"completed by emulator loop\"}");
                commandRepository.save(command);
                log(system, command, "INFO", "COMMAND_COMPLETE",
                        "Command " + command.getCommandType() + " completed in " + command.getExecutionTimeMs() + "ms (emulator loop)");

                feedSlottingFeedback(system, command);
            }
        }
    }

    private void feedSlottingFeedback(NxAutomationSystem system, NxAutomationCommand command) {
        Map<String, Object> params = parseParams(command.getParameters());
        if (params.isEmpty()) {
            return;
        }
        String sku = firstString(params, "sku", "productSku", "itemSku", "skuCode");
        if (sku == null || sku.isBlank()) {
            return;
        }

        String type = command.getCommandType() == null ? "" : command.getCommandType().toUpperCase();
        if (type.equals("PICK") || type.equals("PICK_PACK")) {
            slottingService.recordPick(system.getWarehouseId(), sku);
        } else if (type.equals("PUTAWAY") || type.equals("MOVE") || type.equals("RECEIVE")) {
            UUID binId = parseUuid(firstString(params, "binId", "destinationBinId", "targetBinId", "putawayBinId"));
            slottingService.recordPutaway(system.getWarehouseId(), sku, binId);
        }
    }

    public boolean isEmulatorSystem(NxAutomationSystem system) {
        if (system == null || !Boolean.TRUE.equals(system.getIsActive())) {
            return false;
        }
        if (system.getProtocol() != null && "EMULATOR".equalsIgnoreCase(system.getProtocol())) {
            return true;
        }
        return system.getEndpointUrl() == null || system.getEndpointUrl().isBlank();
    }

    private Duration workDuration(String commandType) {
        if (commandType == null) {
            return DEFAULT_WORK_DURATION;
        }
        return WORK_DURATION_BY_TYPE.getOrDefault(commandType.toUpperCase(), DEFAULT_WORK_DURATION);
    }

    private Map<String, Object> parseParams(String json) {
        if (json == null || json.isBlank()) {
            return Map.of();
        }
        try {
            JsonNode node = MAPPER.readTree(json);
            if (node.isObject()) {
                return MAPPER.convertValue(node, Map.class);
            }
        } catch (Exception ignored) {
        }
        return Map.of();
    }

    private String firstString(Map<String, Object> params, String... keys) {
        for (String key : keys) {
            Object value = params.get(key);
            if (value != null && !value.toString().isBlank()) {
                return value.toString();
            }
        }
        return null;
    }

    private UUID parseUuid(String value) {
        if (value == null || value.isBlank()) {
            return null;
        }
        try {
            return UUID.fromString(value);
        } catch (IllegalArgumentException e) {
            return null;
        }
    }

    private void log(NxAutomationSystem system, NxAutomationCommand command, String level, String event, String message) {
        NxAutomationLog entry = NxAutomationLog.builder()
                .tenantId(system.getTenantId())
                .systemId(system.getId())
                .commandId(command.getId())
                .logLevel(level)
                .event(event)
                .message(message)
                .build();
        logRepository.save(entry);
    }
}
