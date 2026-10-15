package com.nexus.oms.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.nexus.oms.entity.NxAutomationCommand;
import com.nexus.oms.entity.NxAutomationSystem;
import com.nexus.oms.integration.protocol.RestProtocolAdapter;
import com.nexus.oms.repository.*;
import com.nexus.oms.security.TenantContext;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;

import java.util.Optional;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

class AutomationDispatchTest {

    private final AutomationSystemRepository systems = mock(AutomationSystemRepository.class);
    private final AutomationCommandRepository commands = mock(AutomationCommandRepository.class);
    private final RestProtocolAdapter rest = mock(RestProtocolAdapter.class);
    private final AutomationService service = new AutomationService(systems, commands,
            mock(AutomationLogRepository.class), mock(AutomationAlertRepository.class), rest);
    private final UUID systemId = UUID.randomUUID();
    private NxAutomationSystem system;

    @BeforeEach
    void setUp() {
        TenantContext.setCurrentTenantId(UUID.randomUUID());
        system = new NxAutomationSystem();
        system.setId(systemId);
        system.setSystemName("Shuttle 2");
        system.setIsActive(true);
        system.setStatus("ONLINE");
        when(systems.findById(systemId)).thenReturn(Optional.of(system));
        when(commands.save(any())).thenAnswer(i -> {
            NxAutomationCommand c = i.getArgument(0);
            if (c.getId() == null) c.setId(UUID.randomUUID());
            return c;
        });
    }

    @AfterEach
    void tearDown() { TenantContext.clear(); }

    private NxAutomationCommand send() {
        return service.sendCommand(systemId, NxAutomationCommand.builder().commandType("PICK").build());
    }

    @Test
    void emulatorOff_andNoEndpoint_commandWaitsForTheEquipment_insteadOfCompleting() {
        ReflectionTestUtils.setField(service, "emulatorEnabled", false);
        assertEquals("SENT", send().getStatus());
    }

    @Test
    void emulatorOff_andSwitchedOffSystem_failsInsteadOfPretendingToSucceed() {
        ReflectionTestUtils.setField(service, "emulatorEnabled", false);
        system.setIsActive(false);
        NxAutomationCommand c = send();
        assertEquals("FAILED", c.getStatus());
        assertTrue(c.getErrorMessage().contains("switched off"));
    }

    @Test
    void emulatorOn_switchedOffSystem_stillSimulates_asBefore() {
        system.setIsActive(false);
        NxAutomationCommand c = send();
        assertEquals("COMPLETED", c.getStatus());
        assertTrue(c.getResult().contains("\"simulated\":true"));
    }

    @Test
    void equipmentThatOnlyAcceptsTheJob_isNotMarkedCompleted() throws Exception {
        system.setEndpointUrl("https://plc.example.test/jobs");
        when(rest.post(any(), any(), any(), any())).thenReturn(new ObjectMapper().readTree("{\"status\":\"ACCEPTED\"}"));
        assertEquals("ACKNOWLEDGED", send().getStatus());
    }

    @Test
    void equipmentThatAnswersWithAResult_isCompleted() throws Exception {
        system.setEndpointUrl("https://plc.example.test/jobs");
        when(rest.post(any(), any(), any(), any())).thenReturn(new ObjectMapper().readTree("{\"picked\":2}"));
        assertEquals("COMPLETED", send().getStatus());
    }

    @Test
    void asyncFlagIsRecognised() throws Exception {
        var om = new ObjectMapper();
        assertTrue(AutomationService.isAcceptedForLaterCompletion(om.readTree("{\"async\":true}")));
        assertTrue(AutomationService.isAcceptedForLaterCompletion(om.readTree("{\"status\":\"queued\"}")));
        assertFalse(AutomationService.isAcceptedForLaterCompletion(om.readTree("{\"status\":\"done\"}")));
        assertFalse(AutomationService.isAcceptedForLaterCompletion(null));
    }
}
