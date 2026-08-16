package com.nexus.oms.controller;

import com.nexus.oms.entity.NxPicklist;
import com.nexus.oms.entity.NxPicklistItem;
import com.nexus.oms.repository.NxCycleCountRepository;
import com.nexus.oms.repository.NxInventoryReceiptRepository;
import com.nexus.oms.repository.PackageRepository;
import com.nexus.oms.repository.PicklistItemRepository;
import com.nexus.oms.repository.PicklistRepository;
import com.nexus.oms.repository.ShipmentRepository;
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

@WebMvcTest(value = RfController.class, excludeFilters = {
    @ComponentScan.Filter(type = FilterType.REGEX, pattern = "com\\.nexus\\.oms\\.filter\\..*"),
    @ComponentScan.Filter(type = FilterType.REGEX, pattern = "com\\.nexus\\.oms\\.security\\.SecurityConfig"),
    @ComponentScan.Filter(type = FilterType.REGEX, pattern = "com\\.nexus\\.oms\\.security\\..*Filter")
})
@AutoConfigureMockMvc(addFilters = false)
class RfControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @MockBean
    private PicklistRepository picklistRepository;

    @MockBean
    private PackageRepository packageRepository;

    @MockBean
    private ShipmentRepository shipmentRepository;

    @MockBean
    private NxInventoryReceiptRepository receiptRepository;

    @MockBean
    private NxCycleCountRepository cycleCountRepository;

    @MockBean
    private PicklistItemRepository picklistItemRepository;

    @MockBean
    private JwtTokenProvider jwtTokenProvider;

    private UUID tenantId;
    private UUID pickerId;

    @BeforeEach
    void setUp() {
        tenantId = UUID.randomUUID();
        pickerId = UUID.randomUUID();
        SecurityContextHolder.getContext().setAuthentication(
                new UsernamePasswordAuthenticationToken(
                        new TenantAwarePrincipal("picker1", tenantId), null,
                        List.of(new SimpleGrantedAuthority("ROLE_PICKER"))
                )
        );
    }

    @Test
    void tasks_returnsAssignedActivePicklistsWithItems() throws Exception {
        UUID picklistId = UUID.randomUUID();
        NxPicklist picklist = NxPicklist.builder()
                .id(picklistId).tenantId(tenantId).name("Wave-1 ZONE-A")
                .assigneeId(pickerId).status("IN_PROGRESS").priority("HIGH")
                .totalItems(2).pickedItems(1).orderIds(UUID.randomUUID().toString())
                .createdAt(LocalDateTime.now()).build();

        NxPicklistItem item = NxPicklistItem.builder()
                .id(UUID.randomUUID()).picklistId(picklistId).tenantId(tenantId)
                .sku("SKU-001").productName("Widget").quantity(4).pickedQuantity(2)
                .fromLocation("A-01-02").status("PENDING").build();

        when(picklistRepository.findByAssigneeId(pickerId)).thenReturn(List.of(picklist));
        when(picklistItemRepository.findByPicklistId(picklistId)).thenReturn(List.of(item));

        mockMvc.perform(get("/rf/tasks").param("pickerId", pickerId.toString()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.length()").value(1))
                .andExpect(jsonPath("$.data[0].picklistName").value("Wave-1 ZONE-A"))
                .andExpect(jsonPath("$.data[0].status").value("IN_PROGRESS"))
                .andExpect(jsonPath("$.data[0].totalItems").value(2))
                .andExpect(jsonPath("$.data[0].pickedItems").value(1))
                .andExpect(jsonPath("$.data[0].items.length()").value(1))
                .andExpect(jsonPath("$.data[0].items[0].sku").value("SKU-001"))
                .andExpect(jsonPath("$.data[0].items[0].fromLocation").value("A-01-02"));
    }

    @Test
    void tasks_excludesCompletedAndCancelledPicklists() throws Exception {
        NxPicklist completed = NxPicklist.builder()
                .id(UUID.randomUUID()).tenantId(tenantId).name("Done")
                .assigneeId(pickerId).status("COMPLETED").totalItems(3).pickedItems(3)
                .orderIds(UUID.randomUUID().toString()).createdAt(LocalDateTime.now()).build();
        NxPicklist cancelled = NxPicklist.builder()
                .id(UUID.randomUUID()).tenantId(tenantId).name("Cancelled")
                .assigneeId(pickerId).status("CANCELLED").totalItems(3).pickedItems(0)
                .orderIds(UUID.randomUUID().toString()).createdAt(LocalDateTime.now()).build();

        when(picklistRepository.findByAssigneeId(pickerId)).thenReturn(List.of(completed, cancelled));

        mockMvc.perform(get("/rf/tasks").param("pickerId", pickerId.toString()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.length()").value(0));
    }

    @Test
    void tasks_filtersOtherTenants() throws Exception {
        UUID otherTenant = UUID.randomUUID();
        NxPicklist foreign = NxPicklist.builder()
                .id(UUID.randomUUID()).tenantId(otherTenant).name("Foreign")
                .assigneeId(pickerId).status("IN_PROGRESS").totalItems(3).pickedItems(1)
                .orderIds(UUID.randomUUID().toString()).createdAt(LocalDateTime.now()).build();

        when(picklistRepository.findByAssigneeId(pickerId)).thenReturn(List.of(foreign));

        mockMvc.perform(get("/rf/tasks").param("pickerId", pickerId.toString()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.length()").value(0));
    }
}
