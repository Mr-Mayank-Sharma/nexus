package com.nexus.oms.controller;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.nexus.oms.entity.NxAutomationCommand;
import com.nexus.oms.entity.NxPicklist;
import com.nexus.oms.repository.AutomationCommandRepository;
import com.nexus.oms.repository.PicklistItemRepository;
import com.nexus.oms.repository.PicklistRepository;
import com.nexus.oms.repository.PickerAssignmentRepository;
import com.nexus.oms.security.JwtTokenProvider;
import com.nexus.oms.security.TenantAwarePrincipal;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.context.annotation.ComponentScan;
import org.springframework.context.annotation.FilterType;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.test.web.servlet.MockMvc;

import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;

import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(value = TaskQueueController.class, excludeFilters = {
    @ComponentScan.Filter(type = FilterType.REGEX, pattern = "com\\.nexus\\.oms\\.filter\\..*"),
    @ComponentScan.Filter(type = FilterType.REGEX, pattern = "com\\.nexus\\.oms\\.security\\.SecurityConfig"),
    @ComponentScan.Filter(type = FilterType.REGEX, pattern = "com\\.nexus\\.oms\\.security\\..*Filter")
})
@AutoConfigureMockMvc(addFilters = false)
class TaskQueueControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @MockBean
    private PicklistRepository picklistRepository;

    @MockBean
    private PicklistItemRepository picklistItemRepository;

    @MockBean
    private AutomationCommandRepository commandRepository;

    @MockBean
    private PickerAssignmentRepository pickerAssignmentRepository;

    @MockBean
    private JwtTokenProvider jwtTokenProvider;

    private UUID tenantId;

    @BeforeEach
    void setUp() {
        tenantId = UUID.randomUUID();
        SecurityContextHolder.getContext().setAuthentication(
                new UsernamePasswordAuthenticationToken(
                        new TenantAwarePrincipal("testuser", tenantId), null,
                        List.of(new SimpleGrantedAuthority("ROLE_ADMIN"))
                )
        );
    }

    @Test
    void getAllTaskQueues_buildsTasksFromRealPicklistsAndCommands() throws Exception {
        UUID picklistId = UUID.randomUUID();
        NxPicklist picklist = NxPicklist.builder()
                .id(picklistId).tenantId(tenantId).name("Wave-1 - ZONE-A")
                .priority("HIGH").status("IN_PROGRESS")
                .totalItems(5).pickedItems(2).orderIds(UUID.randomUUID().toString())
                .createdAt(LocalDateTime.now()).build();
        when(picklistRepository.findByTenantId(tenantId)).thenReturn(List.of(picklist));
        when(picklistItemRepository.countByPicklistIdAndStatus(picklistId, "PENDING")).thenReturn(3L);

        UUID commandId = UUID.randomUUID();
        NxAutomationCommand command = NxAutomationCommand.builder()
                .id(commandId).tenantId(tenantId).systemId(UUID.randomUUID())
                .commandType("SORTER_DISPATCH").status("SENT").priority(5)
                .createdAt(LocalDateTime.now()).build();
        when(commandRepository.findByTenantId(tenantId)).thenReturn(List.of(command));

        mockMvc.perform(get("/task-queues"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.pickTasks.length()").value(1))
                .andExpect(jsonPath("$.data.pickTasks[0].type").value("PICK_TASK"))
                .andExpect(jsonPath("$.data.pickTasks[0].pendingItems").value(3))
                .andExpect(jsonPath("$.data.automationTasks.length()").value(1))
                .andExpect(jsonPath("$.data.automationTasks[0].source").value("WES"))
                .andExpect(jsonPath("$.data.automationTasks[0].commandType").doesNotExist())
                .andExpect(jsonPath("$.data.pickupTasks.length()").value(0))
                .andExpect(jsonPath("$.data.total").value(2));
    }
}
