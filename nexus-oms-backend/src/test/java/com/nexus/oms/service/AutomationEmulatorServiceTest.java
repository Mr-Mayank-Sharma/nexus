package com.nexus.oms.service;

import com.nexus.oms.entity.NxAutomationCommand;
import com.nexus.oms.entity.NxAutomationLog;
import com.nexus.oms.entity.NxAutomationSystem;
import com.nexus.oms.repository.AutomationCommandRepository;
import com.nexus.oms.repository.AutomationLogRepository;
import com.nexus.oms.repository.AutomationSystemRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class AutomationEmulatorServiceTest {

    @Mock
    private AutomationSystemRepository systemRepository;
    @Mock
    private AutomationCommandRepository commandRepository;
    @Mock
    private AutomationLogRepository logRepository;
    @Mock
    private SlottingService slottingService;

    private AutomationEmulatorService service;

    private UUID warehouseId;
    private UUID systemId;
    private NxAutomationSystem emulatorSystem;

    @BeforeEach
    void setUp() {
        service = new AutomationEmulatorService(systemRepository, commandRepository, logRepository, slottingService);
        warehouseId = UUID.randomUUID();
        systemId = UUID.randomUUID();

        emulatorSystem = new NxAutomationSystem();
        emulatorSystem.setId(systemId);
        emulatorSystem.setTenantId(UUID.randomUUID());
        emulatorSystem.setWarehouseId(warehouseId);
        emulatorSystem.setSystemName("Conveyor Emulator");
        emulatorSystem.setProtocol("EMULATOR");
        emulatorSystem.setIsActive(true);
    }

    @Test
    void isEmulatorSystem_trueForProtocolEmulator() {
        assertThat(service.isEmulatorSystem(emulatorSystem)).isTrue();
    }

    @Test
    void isEmulatorSystem_trueWhenNoEndpointConfigured() {
        emulatorSystem.setProtocol("REST");
        emulatorSystem.setEndpointUrl(null);
        assertThat(service.isEmulatorSystem(emulatorSystem)).isTrue();
    }

    @Test
    void isEmulatorSystem_falseForInactiveRealSystem() {
        emulatorSystem.setProtocol("REST");
        emulatorSystem.setEndpointUrl("http://wes.local/api");
        emulatorSystem.setIsActive(false);
        assertThat(service.isEmulatorSystem(emulatorSystem)).isFalse();
    }

    @Test
    void advanceEmulatorSystems_advancesSentToExecuting() {
        NxAutomationCommand command = baseCommand("SENT");
        command.setSentAt(LocalDateTime.now().minusSeconds(1));

        when(systemRepository.findAll()).thenReturn(List.of(emulatorSystem));
        when(commandRepository.findBySystemIdAndStatus(systemId, "SENT")).thenReturn(List.of(command));
        when(commandRepository.findBySystemIdAndStatus(systemId, "EXECUTING")).thenReturn(List.of());
        when(commandRepository.save(any(NxAutomationCommand.class))).thenAnswer(inv -> inv.getArgument(0));

        service.advanceEmulatorSystems();

        assertThat(command.getStatus()).isEqualTo("EXECUTING");
        assertThat(command.getAcknowledgedAt()).isNotNull();
        verify(logRepository).save(any(NxAutomationLog.class));
    }

    @Test
    void advanceEmulatorSystems_completesExecutingWhenWorkElapsed() {
        NxAutomationCommand command = baseCommand("EXECUTING");
        command.setSentAt(LocalDateTime.now().minusSeconds(5));
        command.setAcknowledgedAt(LocalDateTime.now().minusSeconds(5));
        command.setCommandType("PICK");
        command.setParameters("{\"sku\":\"SKU-001\"}");

        when(systemRepository.findAll()).thenReturn(List.of(emulatorSystem));
        when(commandRepository.findBySystemIdAndStatus(systemId, "SENT")).thenReturn(List.of());
        when(commandRepository.findBySystemIdAndStatus(systemId, "EXECUTING")).thenReturn(List.of(command));
        when(commandRepository.save(any(NxAutomationCommand.class))).thenAnswer(inv -> inv.getArgument(0));

        service.advanceEmulatorSystems();

        assertThat(command.getStatus()).isEqualTo("COMPLETED");
        assertThat(command.getCompletedAt()).isNotNull();
        assertThat(command.getExecutionTimeMs()).isPositive();
        assertThat(command.getResult()).contains("simulated");
        verify(slottingService).recordPick(eq(warehouseId), eq("SKU-001"));
    }

    @Test
    void advanceEmulatorSystems_doesNotCompleteBeforeWorkElapsed() {
        NxAutomationCommand command = baseCommand("EXECUTING");
        command.setSentAt(LocalDateTime.now());
        command.setAcknowledgedAt(LocalDateTime.now());
        command.setCommandType("MOVE");
        command.setParameters("{\"sku\":\"SKU-002\",\"destinationBinId\":\"" + UUID.randomUUID() + "\"}");

        when(systemRepository.findAll()).thenReturn(List.of(emulatorSystem));
        when(commandRepository.findBySystemIdAndStatus(systemId, "SENT")).thenReturn(List.of());
        when(commandRepository.findBySystemIdAndStatus(systemId, "EXECUTING")).thenReturn(List.of(command));

        service.advanceEmulatorSystems();

        assertThat(command.getStatus()).isEqualTo("EXECUTING");
        verify(slottingService, never()).recordPick(any(), any());
    }

    @Test
    void advanceEmulatorSystems_putawayFeedsSlottingRecordPutaway() {
        UUID binId = UUID.randomUUID();
        NxAutomationCommand command = baseCommand("EXECUTING");
        command.setSentAt(LocalDateTime.now().minusSeconds(5));
        command.setAcknowledgedAt(LocalDateTime.now().minusSeconds(5));
        command.setCommandType("PUTAWAY");
        command.setParameters("{\"sku\":\"SKU-003\",\"destinationBinId\":\"" + binId + "\"}");

        when(systemRepository.findAll()).thenReturn(List.of(emulatorSystem));
        when(commandRepository.findBySystemIdAndStatus(systemId, "SENT")).thenReturn(List.of());
        when(commandRepository.findBySystemIdAndStatus(systemId, "EXECUTING")).thenReturn(List.of(command));
        when(commandRepository.save(any(NxAutomationCommand.class))).thenAnswer(inv -> inv.getArgument(0));

        service.advanceEmulatorSystems();

        assertThat(command.getStatus()).isEqualTo("COMPLETED");
        verify(slottingService).recordPutaway(eq(warehouseId), eq("SKU-003"), eq(binId));
    }

    @Test
    void advanceEmulatorSystems_skipsCommandsWithoutSkuFeedback() {
        NxAutomationCommand command = baseCommand("EXECUTING");
        command.setSentAt(LocalDateTime.now().minusSeconds(5));
        command.setAcknowledgedAt(LocalDateTime.now().minusSeconds(5));
        command.setCommandType("CYCLE_COUNT");
        command.setParameters("{\"zone\":\"Z1\"}");

        when(systemRepository.findAll()).thenReturn(List.of(emulatorSystem));
        when(commandRepository.findBySystemIdAndStatus(systemId, "SENT")).thenReturn(List.of());
        when(commandRepository.findBySystemIdAndStatus(systemId, "EXECUTING")).thenReturn(List.of(command));
        when(commandRepository.save(any(NxAutomationCommand.class))).thenAnswer(inv -> inv.getArgument(0));

        service.advanceEmulatorSystems();

        assertThat(command.getStatus()).isEqualTo("COMPLETED");
        verify(slottingService, never()).recordPick(any(), any());
        verify(slottingService, never()).recordPutaway(any(), any(), any());
    }

    @Test
    void advanceEmulatorSystems_skipsNonEmulatorSystems() {
        emulatorSystem.setProtocol("REST");
        emulatorSystem.setEndpointUrl("http://wes.local/api");
        emulatorSystem.setIsActive(true);

        when(systemRepository.findAll()).thenReturn(List.of(emulatorSystem));

        service.advanceEmulatorSystems();

        verify(commandRepository, never()).findBySystemIdAndStatus(any(), any());
    }

    private NxAutomationCommand baseCommand(String status) {
        return NxAutomationCommand.builder()
                .id(UUID.randomUUID())
                .tenantId(emulatorSystem.getTenantId())
                .systemId(systemId)
                .commandType("PICK")
                .status(status)
                .priority(5)
                .timeoutMs(30000)
                .retryCount(0)
                .maxRetries(3)
                .assignedBy("USER")
                .build();
    }
}
