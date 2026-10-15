package com.nexus.oms.service;

import com.nexus.oms.entity.NxAutomationAlert;
import com.nexus.oms.entity.NxAutomationCommand;
import com.nexus.oms.entity.NxAutomationSystem;
import com.nexus.oms.repository.AutomationAlertRepository;
import com.nexus.oms.repository.AutomationCommandRepository;
import com.nexus.oms.repository.AutomationLogRepository;
import com.nexus.oms.repository.AutomationSystemRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

class EquipmentWatchdogTest {

    private final AutomationSystemRepository systems = mock(AutomationSystemRepository.class);
    private final AutomationCommandRepository commands = mock(AutomationCommandRepository.class);
    private final AutomationAlertRepository alerts = mock(AutomationAlertRepository.class);
    private final AutomationLogRepository logs = mock(AutomationLogRepository.class);
    private final AutomationService automation = mock(AutomationService.class);
    private final EquipmentWatchdog watchdog = new EquipmentWatchdog(systems, commands, alerts, logs, automation, 300);
    private final LocalDateTime now = LocalDateTime.of(2026, 10, 15, 12, 0);
    private final UUID tenant = UUID.randomUUID();

    @BeforeEach
    void setUp() {
        when(alerts.findBySystemIdAndStatus(any(), any())).thenReturn(List.of());
    }

    private NxAutomationCommand command(String status, long sentSecondsAgo, Integer timeoutMs) {
        return NxAutomationCommand.builder().id(UUID.randomUUID()).tenantId(tenant).systemId(UUID.randomUUID())
                .commandType("PICK").status(status).sentAt(now.minusSeconds(sentSecondsAgo)).timeoutMs(timeoutMs).build();
    }

    private NxAutomationSystem system(String status, Long silentSeconds) {
        NxAutomationSystem s = new NxAutomationSystem();
        s.setId(UUID.randomUUID());
        s.setTenantId(tenant);
        s.setSystemName("Sorter 1");
        s.setStatus(status);
        if (silentSeconds != null) s.setLastHeartbeatAt(now.minusSeconds(silentSeconds));
        return s;
    }

    @Test
    void aCommandPastItsTimeout_isFailed_withAnAlert() {
        var late = command("SENT", 400, null);          // default 300 s
        var custom = command("EXECUTING", 40, 30_000);  // its own 30 s
        var fine = command("EXECUTING", 40, null);
        when(commands.findByStatusIn(any())).thenReturn(List.of(late, custom, fine));

        assertEquals(2, watchdog.failTimedOutCommands(now));

        assertEquals("FAILED", late.getStatus());
        assertEquals("FAILED", custom.getStatus());
        assertEquals("EXECUTING", fine.getStatus());
        assertTrue(late.getErrorMessage().startsWith("Timed out"));
        verify(alerts, times(2)).save(any());
    }

    @Test
    void silentEquipment_isMarkedError_withOneCriticalAlert() {
        var quiet = system("ONLINE", 900L);
        when(systems.findByLastHeartbeatAtIsNotNull()).thenReturn(List.of(quiet));

        assertEquals(1, watchdog.flagSilentSystems(now));

        assertEquals("ERROR", quiet.getStatus());
        ArgumentCaptor<NxAutomationAlert> a = ArgumentCaptor.forClass(NxAutomationAlert.class);
        verify(alerts).save(a.capture());
        assertEquals("CRITICAL", a.getValue().getSeverity());
        assertEquals(EquipmentWatchdog.HEARTBEAT_ALERT, a.getValue().getAlertType());
    }

    @Test
    void recentOrSwitchedOffOrAlreadyFlaggedEquipment_isLeftAlone() {
        when(systems.findByLastHeartbeatAtIsNotNull()).thenReturn(List.of(
                system("ONLINE", 30L), system("OFFLINE", 9000L), system("MAINTENANCE", 9000L), system("ERROR", 9000L)));

        assertEquals(0, watchdog.flagSilentSystems(now));
        verify(alerts, never()).save(any());
    }

    @Test
    void anOpenHeartbeatAlert_isNotDuplicated() {
        var quiet = system("ONLINE", 900L);
        var open = new NxAutomationAlert();
        open.setAlertType(EquipmentWatchdog.HEARTBEAT_ALERT);
        when(alerts.findBySystemIdAndStatus(quiet.getId(), "ACTIVE")).thenReturn(List.of(open));
        when(systems.findByLastHeartbeatAtIsNotNull()).thenReturn(List.of(quiet));

        watchdog.flagSilentSystems(now);

        verify(alerts, never()).save(any());
    }

    private NxAutomationCommand failedAtDispatch(long failedSecondsAgo, int retries, int max) {
        var c = command("FAILED", failedSecondsAgo + 5, 30000);
        c.setCompletedAt(now.minusSeconds(failedSecondsAgo));
        c.setRetryCount(retries);
        c.setMaxRetries(max);
        return c;   // errorMessage null and never acknowledged: the call to the equipment errored
    }

    @Test
    void aCommandThatFailedToBeDelivered_isRetriedAfterItsBackoff_once() {
        var c = failedAtDispatch(45, 0, 3);
        var again = command("SENT", 0, 30000);
        when(commands.findByStatusAndCompletedAtAfter(any(), any())).thenReturn(List.of(c));
        when(automation.retryCommand(c.getId())).thenReturn(again);

        assertEquals(1, watchdog.retryUndelivered(now));

        verify(automation).retryCommand(c.getId());
        verify(automation).markRetried(c, again.getId());
    }

    @Test
    void retriesWaitForTheBackoff_andStopAtMaxRetries() {
        when(commands.findByStatusAndCompletedAtAfter(any(), any())).thenReturn(List.of(
                failedAtDispatch(10, 0, 3),      // 30 s not yet elapsed
                failedAtDispatch(50, 1, 3),      // needs 60 s
                failedAtDispatch(900, 3, 3)));   // out of retries

        assertEquals(0, watchdog.retryUndelivered(now));
        verify(automation, never()).retryCommand(any());
    }

    @Test
    void commandsThatMayHaveRun_areNeverAutomaticallyRetried() {
        var timedOut = failedAtDispatch(600, 0, 3);
        timedOut.setErrorMessage("Timed out: no result from the equipment within 30s");
        var reported = failedAtDispatch(600, 0, 3);
        reported.setAcknowledgedAt(now.minusSeconds(700));
        var alreadyRetried = failedAtDispatch(600, 0, 3);
        alreadyRetried.setMetadata("{\"retriedAs\":\"x\"}");
        when(commands.findByStatusAndCompletedAtAfter(any(), any())).thenReturn(List.of(timedOut, reported, alreadyRetried));

        assertEquals(0, watchdog.retryUndelivered(now));
        verify(automation, never()).retryCommand(any());
    }
}
