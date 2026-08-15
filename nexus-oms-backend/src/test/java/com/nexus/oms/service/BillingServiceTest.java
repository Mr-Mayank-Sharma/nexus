package com.nexus.oms.service;

import com.nexus.oms.entity.*;
import com.nexus.oms.exception.BadRequestException;
import com.nexus.oms.exception.ResourceNotFoundException;
import com.nexus.oms.repository.*;
import com.nexus.oms.security.TenantContext;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class BillingServiceTest {

    @Mock private BillingStatementRepository statementRepository;
    @Mock private BillingStatementLineRepository lineRepository;
    @Mock private RateCardService rateCardService;
    @Mock private OrderRepository orderRepository;
    @Mock private OrderItemRepository orderItemRepository;
    @Mock private PicklistItemRepository picklistItemRepository;
    @Mock private CustomerRepository customerRepository;

    private BillingService billingService;
    private UUID tenantId;
    private UUID clientId;
    private NxCustomer client;
    private NxRateCard rateCard;

    @BeforeEach
    void setUp() {
        billingService = new BillingService(statementRepository, lineRepository, rateCardService,
                orderRepository, orderItemRepository, picklistItemRepository, customerRepository);
        tenantId = UUID.randomUUID();
        clientId = UUID.randomUUID();
        TenantContext.setCurrentTenantId(tenantId);

        client = NxCustomer.builder().id(clientId).tenantId(tenantId).name("Acme Retail").email("billing@acme.com").build();
        rateCard = NxRateCard.builder()
                .id(UUID.randomUUID()).tenantId(tenantId).clientId(clientId).clientName("Acme Retail")
                .currency("USD")
                .perOrderFee(new BigDecimal("2.00"))
                .perLineFee(new BigDecimal("0.50"))
                .pickingFeePerLine(new BigDecimal("0.25"))
                .storageFeePerUnit(new BigDecimal("0.10"))
                .isActive(true).build();
    }

    @AfterEach
    void tearDown() {
        TenantContext.clear();
    }

    private NxOrder order(UUID id, String status) {
        return NxOrder.builder().id(id).tenantId(tenantId).customerId(clientId)
                .status(status).channel("SHOPIFY").createdAt(LocalDateTime.now()).build();
    }

    @Test
    void generateStatement_computesTotalsFromRealOrderData() {
        UUID o1 = UUID.randomUUID();
        UUID o2 = UUID.randomUUID();

        when(customerRepository.findById(clientId)).thenReturn(Optional.of(client));
        when(rateCardService.resolveRateCard(tenantId, clientId)).thenReturn(rateCard);
        when(statementRepository.findFirstByTenantIdAndClientIdAndPeriodStartAndPeriodEnd(any(), any(), any(), any()))
                .thenReturn(Optional.empty());
        when(orderRepository.findByTenantIdAndCustomerIdAndCreatedAtBetween(any(), any(), any(), any()))
                .thenReturn(List.of(order(o1, "SHIPPED"), order(o2, "CANCELLED")));
        when(orderItemRepository.findByOrderIdIn(List.of(o1))).thenReturn(List.of(
                NxOrderItem.builder().id(UUID.randomUUID()).orderId(o1).sku("SKU-A").quantity(3).build(),
                NxOrderItem.builder().id(UUID.randomUUID()).orderId(o1).sku("SKU-B").quantity(1).build()));
        when(picklistItemRepository.findByOrderIdIn(List.of(o1))).thenReturn(List.of(
                NxPicklistItem.builder().id(UUID.randomUUID()).picklistId(UUID.randomUUID()).orderId(o1).quantity(1).build()));
        when(statementRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));

        NxBillingStatement stmt = billingService.generateStatement(clientId, LocalDate.of(2026, 8, 1), LocalDate.of(2026, 8, 31));

        // 1 fulfilled order (cancelled excluded), 2 lines, 4 units, 1 picked line
        assertEquals(1, stmt.getOrderCount());
        assertEquals(2, stmt.getLineCount());
        assertEquals(4, stmt.getUnitsHandled());
        assertEquals(1, stmt.getPickedLines());
        assertEquals(new BigDecimal("3.65"), stmt.getSubtotal()); // 2.00 + 1.00 + 0.25 + 0.40
        assertEquals(stmt.getSubtotal(), stmt.getTotal());
        assertEquals("DRAFT", stmt.getStatus());
        assertEquals("Acme Retail", stmt.getClientName());
        verify(lineRepository, times(4)).save(any());
    }

    @Test
    void generateStatement_rejectsDuplicatePeriod() {
        when(customerRepository.findById(clientId)).thenReturn(Optional.of(client));
        when(rateCardService.resolveRateCard(tenantId, clientId)).thenReturn(rateCard);
        when(statementRepository.findFirstByTenantIdAndClientIdAndPeriodStartAndPeriodEnd(any(), any(), any(), any()))
                .thenReturn(Optional.of(new NxBillingStatement()));

        assertThrows(BadRequestException.class, () ->
                billingService.generateStatement(clientId, LocalDate.of(2026, 8, 1), LocalDate.of(2026, 8, 31)));
    }

    @Test
    void generateStatement_rejectsMissingRateCard() {
        when(customerRepository.findById(clientId)).thenReturn(Optional.of(client));
        when(rateCardService.resolveRateCard(tenantId, clientId))
                .thenThrow(new BadRequestException("No active rate card found for client"));

        assertThrows(BadRequestException.class, () ->
                billingService.generateStatement(clientId, LocalDate.of(2026, 8, 1), LocalDate.of(2026, 8, 31)));
    }

    @Test
    void generateStatement_rejectsForeignClient() {
        NxCustomer foreign = NxCustomer.builder().id(clientId).tenantId(UUID.randomUUID()).name("Other").build();
        when(customerRepository.findById(clientId)).thenReturn(Optional.of(foreign));

        assertThrows(ResourceNotFoundException.class, () ->
                billingService.generateStatement(clientId, LocalDate.of(2026, 8, 1), LocalDate.of(2026, 8, 31)));
    }

    @Test
    void updateStatus_acceptsValidStatuses() {
        NxBillingStatement stmt = NxBillingStatement.builder().id(UUID.randomUUID()).tenantId(tenantId).status("DRAFT").build();
        when(statementRepository.findById(stmt.getId())).thenReturn(Optional.of(stmt));
        when(statementRepository.save(stmt)).thenReturn(stmt);

        assertEquals("ISSUED", billingService.updateStatus(stmt.getId(), "ISSUED").getStatus());
        assertThrows(BadRequestException.class, () -> billingService.updateStatus(stmt.getId(), "UNKNOWN"));
    }
}
