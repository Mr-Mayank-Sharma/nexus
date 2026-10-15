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
    private final EquipmentWatchdog watchdog = new EquipmentWatchdog(systems, commands, alerts, logs, 300);
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
}
