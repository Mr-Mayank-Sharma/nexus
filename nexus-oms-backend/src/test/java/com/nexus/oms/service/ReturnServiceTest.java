package com.nexus.oms.service;

import com.nexus.oms.dto.ReturnResponse;
import com.nexus.oms.entity.NxCustomer;
import com.nexus.oms.entity.NxReturn;
import com.nexus.oms.entity.NxReturnItem;
import com.nexus.oms.exception.ResourceNotFoundException;
import com.nexus.oms.repository.CustomerRepository;
import com.nexus.oms.repository.OrderRepository;
import com.nexus.oms.repository.ReturnItemRepository;
import com.nexus.oms.repository.ReturnRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class ReturnServiceTest {

    @Mock
    private ReturnRepository returnRepository;
    @Mock
    private ReturnItemRepository returnItemRepository;
    @Mock
    private OrderRepository orderRepository;
    @Mock
    private CustomerRepository customerRepository;

    private ReturnService returnService;
    private UUID tenantId;
    private UUID returnId;
    private UUID customerId;
    private NxReturn nxReturn;
    private NxCustomer customer;

    @BeforeEach
    void setUp() {
        returnService = new ReturnService(returnRepository, returnItemRepository, orderRepository, customerRepository);
        tenantId = UUID.randomUUID();
        returnId = UUID.randomUUID();
        customerId = UUID.randomUUID();
        customer = NxCustomer.builder().id(customerId).name("John Doe").email("john@example.com").build();
        nxReturn = NxReturn.builder()
                .id(returnId)
                .tenantId(tenantId)
                .orderId(UUID.randomUUID())
                .customerId(customerId)
                .status("REQUESTED")
                .reason("Damaged")
                .rmaNumber("RMA-TEST123")
                .createdAt(LocalDateTime.now())
                .updatedAt(LocalDateTime.now())
                .build();
    }

    @Test
    void getReturns_withStatusFilter() {
        when(returnRepository.findByTenantIdAndStatus(tenantId, "OPEN")).thenReturn(List.of(nxReturn));
        when(customerRepository.findById(customerId)).thenReturn(Optional.of(customer));
        when(returnItemRepository.findByReturnId(returnId)).thenReturn(List.of());

        List<ReturnResponse> result = returnService.getReturns(tenantId, "OPEN");

        assertEquals(1, result.size());
        assertEquals("RMA-TEST123", result.get(0).getRmaNumber());
        verify(returnRepository).findByTenantIdAndStatus(tenantId, "OPEN");
    }

    @Test
    void getReturns_withoutStatusFilter() {
        when(returnRepository.findByTenantId(tenantId)).thenReturn(List.of(nxReturn));
        when(customerRepository.findById(customerId)).thenReturn(Optional.of(customer));
        when(returnItemRepository.findByReturnId(returnId)).thenReturn(List.of());

        List<ReturnResponse> result = returnService.getReturns(tenantId, null);

        assertEquals(1, result.size());
        verify(returnRepository).findByTenantId(tenantId);
    }

    @Test
    void getReturns_withBlankStatusFallsBack() {
        when(returnRepository.findByTenantId(tenantId)).thenReturn(List.of(nxReturn));
        when(customerRepository.findById(customerId)).thenReturn(Optional.of(customer));
        when(returnItemRepository.findByReturnId(returnId)).thenReturn(List.of());

        List<ReturnResponse> result = returnService.getReturns(tenantId, "  ");

        assertEquals(1, result.size());
        verify(returnRepository).findByTenantId(tenantId);
    }

    @Test
    void getReturn_found() {
        when(returnRepository.findById(returnId)).thenReturn(Optional.of(nxReturn));
        when(customerRepository.findById(customerId)).thenReturn(Optional.of(customer));
        when(returnItemRepository.findByReturnId(returnId)).thenReturn(List.of());

        ReturnResponse result = returnService.getReturn(returnId);

        assertEquals("RMA-TEST123", result.getRmaNumber());
    }

    @Test
    void getReturn_notFound_throws() {
        when(returnRepository.findById(returnId)).thenReturn(Optional.empty());

        assertThrows(ResourceNotFoundException.class, () -> returnService.getReturn(returnId));
    }

    @Test
    void createReturn_generatesRmaNumber() {
        NxReturn newReturn = NxReturn.builder().tenantId(tenantId).orderId(UUID.randomUUID()).build();
        when(returnRepository.save(any())).thenAnswer(i -> i.getArgument(0));

        ReturnResponse result = returnService.createReturn(newReturn, null);

        assertNotNull(result);
        verify(returnRepository).save(argThat(r -> r.getRmaNumber() != null && r.getRmaNumber().startsWith("RMA-")));
    }

    @Test
    void createReturn_withItems() {
        NxReturn newReturn = NxReturn.builder().tenantId(tenantId).orderId(UUID.randomUUID()).rmaNumber("RMA-EXISTING").build();
        NxReturnItem item = NxReturnItem.builder().sku("SKU-001").quantity(2).build();
        when(returnRepository.save(any())).thenAnswer(i -> {
            NxReturn r = i.getArgument(0);
            if (r.getId() == null) r.setId(UUID.randomUUID());
            return r;
        });
        when(returnItemRepository.save(any())).thenAnswer(i -> i.getArgument(0));

        returnService.createReturn(newReturn, List.of(item));

        verify(returnItemRepository).save(argThat(i -> i.getReturnId() != null));
    }

    @Test
    void approveReturn() {
        when(returnRepository.findById(returnId)).thenReturn(Optional.of(nxReturn));
        when(returnRepository.save(any())).thenAnswer(i -> i.getArgument(0));
        when(returnItemRepository.findByReturnId(returnId)).thenReturn(List.of());
        when(customerRepository.findById(customerId)).thenReturn(Optional.of(customer));
        when(returnItemRepository.findByReturnId(returnId)).thenReturn(List.of());

        ReturnResponse result = returnService.approveReturn(returnId, UUID.randomUUID());

        assertEquals("APPROVED", result.getStatus());
    }

    @Test
    void receiveReturn() {
        when(returnRepository.findById(returnId)).thenReturn(Optional.of(nxReturn));
        when(returnRepository.save(any())).thenAnswer(i -> i.getArgument(0));
        when(customerRepository.findById(customerId)).thenReturn(Optional.of(customer));
        when(returnItemRepository.findByReturnId(returnId)).thenReturn(List.of());

        ReturnResponse result = returnService.receiveReturn(returnId, UUID.randomUUID());

        assertEquals("RECEIVED", result.getStatus());
    }

    @Test
    void inspectReturn_allInspected() {
        NxReturnItem item = NxReturnItem.builder().id(UUID.randomUUID()).sku("SKU-001").status("PENDING").build();
        NxReturnItem inspected = NxReturnItem.builder().id(item.getId()).condition("GOOD").grade("A").disposition("RESTOCK").build();
        when(returnRepository.findById(returnId)).thenReturn(Optional.of(nxReturn));
        when(returnItemRepository.findById(item.getId())).thenReturn(Optional.of(item));
        when(returnItemRepository.findByReturnId(returnId)).thenReturn(List.of(item));
        lenient().when(returnRepository.save(any())).thenAnswer(i -> i.getArgument(0));
        lenient().when(returnItemRepository.save(any())).thenAnswer(i -> i.getArgument(0));
        when(customerRepository.findById(customerId)).thenReturn(Optional.of(customer));

        ReturnResponse result = returnService.inspectReturn(returnId, List.of(inspected), UUID.randomUUID());

        assertNotNull(result);
    }

    @Test
    void processRefund() {
        when(returnRepository.findById(returnId)).thenReturn(Optional.of(nxReturn));
        when(returnRepository.save(any())).thenAnswer(i -> i.getArgument(0));
        when(returnItemRepository.findByReturnId(returnId)).thenReturn(List.of());
        when(customerRepository.findById(customerId)).thenReturn(Optional.of(customer));
        when(returnItemRepository.findByReturnId(returnId)).thenReturn(List.of());

        ReturnResponse result = returnService.processRefund(returnId, new BigDecimal("50.00"), "REF-001");

        assertEquals("REFUNDED", result.getStatus());
    }

    @Test
    void processRefund_withRejectedItems() {
        NxReturnItem rejectedItem = NxReturnItem.builder().id(UUID.randomUUID()).sku("SKU-002").status("REJECTED").build();
        NxReturnItem pendingItem = NxReturnItem.builder().id(UUID.randomUUID()).sku("SKU-001").status("APPROVED").build();
        when(returnRepository.findById(returnId)).thenReturn(Optional.of(nxReturn));
        when(returnRepository.save(any())).thenAnswer(i -> i.getArgument(0));
        when(returnItemRepository.findByReturnId(returnId)).thenReturn(List.of(rejectedItem, pendingItem));
        when(returnItemRepository.save(any())).thenAnswer(i -> i.getArgument(0));
        when(customerRepository.findById(customerId)).thenReturn(Optional.of(customer));

        returnService.processRefund(returnId, new BigDecimal("50.00"), "REF-001");

        verify(returnItemRepository).save(argThat(i -> "REFUNDED".equals(i.getStatus())));
    }

    @Test
    void rejectReturn() {
        when(returnRepository.findById(returnId)).thenReturn(Optional.of(nxReturn));
        when(returnRepository.save(any())).thenAnswer(i -> i.getArgument(0));

        ReturnResponse result = returnService.rejectReturn(returnId, "Defective");

        assertEquals("REJECTED", result.getStatus());
    }

    @Test
    void updateReturnStatus_cancelled() {
        when(returnRepository.findById(returnId)).thenReturn(Optional.of(nxReturn));
        when(returnRepository.save(any())).thenAnswer(i -> i.getArgument(0));

        ReturnResponse result = returnService.updateReturnStatus(returnId, "CANCELLED");

        assertEquals("CANCELLED", result.getStatus());
        verify(returnRepository).save(argThat(r -> r.getRejectedReason() != null));
    }

    @Test
    void updateReturnStatus_otherStatus() {
        when(returnRepository.findById(returnId)).thenReturn(Optional.of(nxReturn));
        when(returnRepository.save(any())).thenAnswer(i -> i.getArgument(0));

        ReturnResponse result = returnService.updateReturnStatus(returnId, "HOLD");

        assertEquals("HOLD", result.getStatus());
    }

    @Test
    void getReturnItems() {
        NxReturnItem item = NxReturnItem.builder().id(UUID.randomUUID()).sku("SKU-001").build();
        when(returnItemRepository.findByReturnId(returnId)).thenReturn(List.of(item));

        List<NxReturnItem> result = returnService.getReturnItems(returnId);

        assertEquals(1, result.size());
    }

    @Test
    void getReturnKPIs() {
        NxReturn req = NxReturn.builder().status("REQUESTED").build();
        NxReturn app = NxReturn.builder().status("APPROVED").build();
        NxReturn rec = NxReturn.builder().status("RECEIVED").build();
        NxReturn ins = NxReturn.builder().status("INSPECTED").build();
        NxReturn ref = NxReturn.builder().status("REFUNDED").build();
        NxReturn rej = NxReturn.builder().status("REJECTED").build();
        when(returnRepository.findByTenantId(tenantId)).thenReturn(List.of(req, app, rec, ins, ref, rej));

        Map<String, Object> kpis = returnService.getReturnKPIs(tenantId);

        assertEquals(6, kpis.get("total"));
        assertEquals(1L, kpis.get("pending"));
        assertEquals(1L, kpis.get("approved"));
    }

    @Test
    void getReturnReasons() {
        NxReturn r1 = NxReturn.builder().reason("Damaged").build();
        NxReturn r2 = NxReturn.builder().reason("Damaged").build();
        NxReturn r3 = NxReturn.builder().reason("Wrong Item").build();
        when(returnRepository.findByTenantId(tenantId)).thenReturn(List.of(r1, r2, r3));

        List<Map<String, Object>> reasons = returnService.getReturnReasons(tenantId);

        assertEquals(2, reasons.size());
        assertEquals("Damaged", reasons.get(0).get("reason"));
        assertEquals(2L, reasons.get(0).get("count"));
    }

    @Test
    void returnNotFound_throwsResourceNotFoundException() {
        when(returnRepository.findById(any())).thenReturn(Optional.empty());

        assertThrows(ResourceNotFoundException.class, () -> returnService.getReturn(UUID.randomUUID()));
        assertThrows(ResourceNotFoundException.class, () -> returnService.approveReturn(UUID.randomUUID(), UUID.randomUUID()));
    }
}
