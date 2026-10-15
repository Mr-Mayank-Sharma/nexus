package com.nexus.oms.service;

import com.nexus.oms.entity.NxAutomationCommand;
import com.nexus.oms.entity.NxAutomationSystem;
import com.nexus.oms.repository.AutomationCommandRepository;
import com.nexus.oms.repository.AutomationLogRepository;
import com.nexus.oms.repository.AutomationSystemRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.time.LocalDateTime;
import java.util.Optional;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

class EquipmentCallbackServiceTest {

    private final AutomationSystemRepository systems = mock(AutomationSystemRepository.class);
    private final AutomationCommandRepository commands = mock(AutomationCommandRepository.class);
    private final AutomationLogRepository logs = mock(AutomationLogRepository.class);
    private final com.nexus.oms.repository.AutomationAlertRepository alerts = mock(com.nexus.oms.repository.AutomationAlertRepository.class);
    private final EquipmentCallbackService service = new EquipmentCallbackService(systems, commands, logs, alerts);

    private final UUID systemId = UUID.randomUUID();
    private final UUID commandId = UUID.randomUUID();
    private NxAutomationSystem system;
    private NxAutomationCommand command;

    @BeforeEach
    void setUp() {
        system = new NxAutomationSystem();
        system.setId(systemId);
        system.setTenantId(UUID.randomUUID());
        system.setApiKey("s3cret");
        system.setStatus("OFFLINE");
        command = NxAutomationCommand.builder().id(commandId).systemId(systemId).commandType("PICK")
                .status("SENT").sentAt(LocalDateTime.now().minusSeconds(5)).build();
        when(systems.findById(systemId)).thenReturn(Optional.of(system));
        when(commands.findById(commandId)).thenReturn(Optional.of(command));
        when(commands.save(any())).thenAnswer(i -> i.getArgument(0));
        when(systems.save(any())).thenAnswer(i -> i.getArgument(0));
    }

    private int rejectedWith(Runnable r) {
        return assertThrows(EquipmentCallbackService.Rejected.class, r::run).httpStatus();
    }

    @Test
    void wrongOrMissingKey_isRefused_andChangesNothing() {
        assertEquals(401, rejectedWith(() -> service.report(systemId, commandId, "nope", "COMPLETED", null, null)));
        assertEquals(401, rejectedWith(() -> service.report(systemId, commandId, null, "COMPLETED", null, null)));
        assertEquals("SENT", command.getStatus());
        verify(commands, never()).save(any());
    }

    @Test
    void systemWithoutASecret_cannotCallBack() {
        system.setApiKey(" ");
        assertEquals(403, rejectedWith(() -> service.heartbeat(systemId, "", null)));
    }

    @Test
    void commandMovesThroughTheLifecycle() {
        service.report(systemId, commandId, "s3cret", "ACKNOWLEDGED", null, null);
        assertEquals("ACKNOWLEDGED", command.getStatus());
        assertNotNull(command.getAcknowledgedAt());
        service.report(systemId, commandId, "s3cret", "EXECUTING", null, null);
        service.report(systemId, commandId, "s3cret", "COMPLETED", "{\"picked\":3}", null);
        assertEquals("COMPLETED", command.getStatus());
        assertEquals("{\"picked\":3}", command.getResult());
        assertNotNull(command.getCompletedAt());
        assertTrue(command.getExecutionTimeMs() >= 0);
    }

    @Test
    void failure_recordsTheEquipmentsReason() {
        service.report(systemId, commandId, "s3cret", "FAILED", null, "tote jam at lane 4");
        assertEquals("FAILED", command.getStatus());
        assertEquals("tote jam at lane 4", command.getErrorMessage());
    }

    @Test
    void aRetriedCallback_isAcceptedAndChangesNothing() {
        command.setStatus("COMPLETED");
        service.report(systemId, commandId, "s3cret", "COMPLETED", null, null);
        verify(commands, never()).save(any());
    }

    @Test
    void aFinishedCommand_cannotBeReopened_orGoBackwards() {
        command.setStatus("COMPLETED");
        assertEquals(409, rejectedWith(() -> service.report(systemId, commandId, "s3cret", "FAILED", null, null)));
        command.setStatus("EXECUTING");
        assertEquals(409, rejectedWith(() -> service.report(systemId, commandId, "s3cret", "ACKNOWLEDGED", null, null)));
    }

    @Test
    void aCommandOfAnotherSystem_isNotFound() {
        command.setSystemId(UUID.randomUUID());
        assertEquals(404, rejectedWith(() -> service.report(systemId, commandId, "s3cret", "COMPLETED", null, null)));
    }

    @Test
    void unsupportedStatus_isABadRequest() {
        assertEquals(400, rejectedWith(() -> service.report(systemId, commandId, "s3cret", "PENDING", null, null)));
        assertEquals(400, rejectedWith(() -> service.report(systemId, commandId, "s3cret", "whatever", null, null)));
    }

    @Test
    void heartbeat_recoversAFaultedSystem_butNeverOverridesAnOperatorsSwitchOffOrMaintenance() {
        system.setStatus("ERROR");
        assertEquals("ONLINE", service.heartbeat(systemId, "s3cret", null).getStatus());
        system.setStatus("OFFLINE");
        assertEquals("OFFLINE", service.heartbeat(systemId, "s3cret", null).getStatus());
        system.setStatus("MAINTENANCE");
        assertEquals("MAINTENANCE", service.heartbeat(systemId, "s3cret", null).getStatus());
        assertEquals("ERROR", service.heartbeat(systemId, "s3cret", "ERROR").getStatus());
    }

    @Test
    void heartbeat_recordsItself_andClosesTheMissedHeartbeatAlert() {
        var alert = new com.nexus.oms.entity.NxAutomationAlert();
        alert.setAlertType(EquipmentWatchdog.HEARTBEAT_ALERT);
        alert.setStatus("ACTIVE");
        when(alerts.findBySystemIdAndStatus(systemId, "ACTIVE")).thenReturn(java.util.List.of(alert));
        system.setStatus("ERROR");

        service.heartbeat(systemId, "s3cret", null);

        assertNotNull(system.getLastHeartbeatAt());
        assertEquals("RESOLVED", alert.getStatus());
        assertNotNull(alert.getResolvedAt());
    }
}
