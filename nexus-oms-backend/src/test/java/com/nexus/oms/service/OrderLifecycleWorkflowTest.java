package com.nexus.oms.service;

import com.nexus.oms.entity.NxApprovalRule;
import com.nexus.oms.entity.NxOrderApproval;
import com.nexus.oms.entity.NxParkedOrder;
import com.nexus.oms.exception.BadRequestException;
import com.nexus.oms.exception.ResourceNotFoundException;
import com.nexus.oms.repository.ApprovalRuleRepository;
import com.nexus.oms.repository.OrderApprovalRepository;
import com.nexus.oms.repository.ParkedOrderRepository;
import com.nexus.oms.security.TenantContext;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class OrderLifecycleWorkflowTest {

    @Mock private ApprovalRuleRepository approvalRuleRepository;
    @Mock private OrderApprovalRepository orderApprovalRepository;
    @Mock private ParkedOrderRepository parkedOrderRepository;

    private OrderApprovalService approvalService;
    private ParkedOrderService parkedService;
    private UUID tenantId;
    private UUID orderId;
    private UUID customerId;

    @BeforeEach
    void setUp() {
        approvalService = new OrderApprovalService(approvalRuleRepository, orderApprovalRepository);
        parkedService = new ParkedOrderService(parkedOrderRepository);
        tenantId = UUID.randomUUID();
        orderId = UUID.randomUUID();
        customerId = UUID.randomUUID();
        TenantContext.setCurrentTenantId(tenantId);
    }

    @AfterEach
    void tearDown() {
        TenantContext.clear();
    }

    // ─── Approval: auto-approve when no rules match ──────────────────────

    @Test
    void evaluateOrder_noRules_autoApproves() {
        when(orderApprovalRepository.findByOrderId(orderId)).thenReturn(null);
        when(approvalRuleRepository.findByTenantIdAndActiveTrue(tenantId)).thenReturn(List.of());
        when(orderApprovalRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));

        NxOrderApproval approval = approvalService.evaluateOrder(orderId, "SO-1",
                BigDecimal.valueOf(25.00), customerId);

        assertEquals("AUTO_APPROVE", approval.getStatus());
        assertEquals("SO-1", approval.getOrderNumber());
        verify(orderApprovalRepository).save(any());
    }

    // ─── Approval: amount threshold forces manual review ─────────────────

    @Test
    void evaluateOrder_highValueOrder_isHeldForManualReview() {
        NxApprovalRule rule = NxApprovalRule.builder()
                .tenantId(tenantId).name("High Value").ruleType("AMOUNT_THRESHOLD")
                .action("HOLD_FOR_REVIEW").thresholdValue(BigDecimal.valueOf(1000))
                .active(true).build();
        when(orderApprovalRepository.findByOrderId(orderId)).thenReturn(null);
        when(approvalRuleRepository.findByTenantIdAndActiveTrue(tenantId)).thenReturn(List.of(rule));
        when(orderApprovalRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));
        when(orderApprovalRepository.findByTenantIdAndStatus(tenantId, "MANUAL_REVIEW"))
                .thenReturn(List.of(NxOrderApproval.builder().status("MANUAL_REVIEW").build()));

        NxOrderApproval approval = approvalService.evaluateOrder(orderId, "SO-2",
                BigDecimal.valueOf(5000.00), customerId);

        assertEquals("MANUAL_REVIEW", approval.getStatus());
        assertTrue(approval.getMatchedRules().contains("High Value"));
        assertEquals(1, approvalService.getPendingReviews().size());
    }

    // ─── Approval: reject rule wins over hold ────────────────────────────

    @Test
    void evaluateOrder_rejectRuleWins() {
        NxApprovalRule reject = NxApprovalRule.builder()
                .tenantId(tenantId).name("Blacklist").ruleType("BLACKLIST")
                .action("REJECT").thresholdString(customerId.toString()).active(true).build();
        when(orderApprovalRepository.findByOrderId(orderId)).thenReturn(null);
        when(approvalRuleRepository.findByTenantIdAndActiveTrue(tenantId)).thenReturn(List.of(reject));
        when(orderApprovalRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));

        NxOrderApproval approval = approvalService.evaluateOrder(orderId, "SO-3",
                BigDecimal.valueOf(50.00), customerId);

        assertEquals("REJECT", approval.getStatus());
    }

    // ─── Approval: manual review approves / rejects ──────────────────────

    @Test
    void manualReview_approvesHeldOrder() {
        NxOrderApproval held = NxOrderApproval.builder()
                .id(UUID.randomUUID()).tenantId(tenantId).orderId(orderId)
                .orderNumber("SO-2").status("MANUAL_REVIEW").build();
        when(orderApprovalRepository.findById(held.getId())).thenReturn(Optional.of(held));
        when(orderApprovalRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));

        NxOrderApproval result = approvalService.manualReview(held.getId(), "APPROVED", "ops-user", "ok");

        assertEquals("APPROVED", result.getStatus());
        assertEquals("ops-user", result.getReviewedBy());
        assertNotNull(result.getDecidedAt());
    }

    @Test
    void manualReview_rejectsHeldOrder() {
        NxOrderApproval held = NxOrderApproval.builder()
                .id(UUID.randomUUID()).tenantId(tenantId).orderId(orderId)
                .orderNumber("SO-2").status("MANUAL_REVIEW").build();
        when(orderApprovalRepository.findById(held.getId())).thenReturn(Optional.of(held));
        when(orderApprovalRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));

        NxOrderApproval result = approvalService.manualReview(held.getId(), "REJECTED", "ops-user", "fraud");

        assertEquals("REJECTED", result.getStatus());
        assertEquals("fraud", result.getReviewNotes());
    }

    @Test
    void manualReview_onNonHeldOrder_throws() {
        NxOrderApproval auto = NxOrderApproval.builder()
                .id(UUID.randomUUID()).tenantId(tenantId).orderId(orderId)
                .orderNumber("SO-1").status("AUTO_APPROVE").build();
        when(orderApprovalRepository.findById(auto.getId())).thenReturn(Optional.of(auto));

        assertThrows(BadRequestException.class,
                () -> approvalService.manualReview(auto.getId(), "APPROVED", "ops", "nope"));
    }

    @Test
    void approvalStats_aggregateCounts() {
        NxOrderApproval a = NxOrderApproval.builder().status("MANUAL_REVIEW").build();
        NxOrderApproval b = NxOrderApproval.builder().status("APPROVED").build();
        NxOrderApproval c = NxOrderApproval.builder().status("REJECTED").build();
        NxOrderApproval d = NxOrderApproval.builder().status("AUTO_APPROVE").build();
        when(orderApprovalRepository.findByTenantId(tenantId)).thenReturn(List.of(a, b, c, d));

        Map<String, Object> stats = approvalService.getApprovalStats();

        assertEquals(4L, stats.get("total"));
        assertEquals(1L, stats.get("pending"));
        assertEquals(1L, stats.get("approved"));
        assertEquals(1L, stats.get("rejected"));
        assertEquals(1L, stats.get("autoApproved"));
    }

    // ─── Parked orders: park → release / cancel ──────────────────────────

    @Test
    void parkOrder_persistsWithStatusAndTenant() {
        NxParkedOrder input = NxParkedOrder.builder()
                .orderId(orderId).orderNumber("SO-9").reason("PREORDER")
                .priority(2).sku("SKU-1").quantity(3).customerEmail("x@y.com").build();
        when(parkedOrderRepository.findByOrderId(orderId)).thenReturn(null);
        when(parkedOrderRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));

        NxParkedOrder saved = parkedService.parkOrder(input);

        assertEquals(tenantId, saved.getTenantId());
        assertEquals("PARKED", saved.getStatus());
    }

    @Test
    void parkOrder_duplicateParkedOrder_throws() {
        NxParkedOrder existing = NxParkedOrder.builder().orderId(orderId).status("PARKED").build();
        when(parkedOrderRepository.findByOrderId(orderId)).thenReturn(existing);

        assertThrows(BadRequestException.class,
                () -> parkedService.parkOrder(NxParkedOrder.builder().orderId(orderId).build()));
    }

    @Test
    void releaseParkedOrder_requiresParkedStatus() {
        NxParkedOrder released = NxParkedOrder.builder().id(UUID.randomUUID()).status("RELEASED").build();
        when(parkedOrderRepository.findById(released.getId())).thenReturn(Optional.of(released));

        assertThrows(BadRequestException.class, () -> parkedService.releaseOrder(released.getId(), "now"));
    }

    @Test
    void parkReleaseCancel_fullLifecycle() {
        UUID parkedId = UUID.randomUUID();
        NxParkedOrder parked = NxParkedOrder.builder()
                .id(parkedId).tenantId(tenantId).orderId(orderId)
                .orderNumber("SO-9").status("PARKED").build();
        when(parkedOrderRepository.findById(parkedId)).thenReturn(Optional.of(parked));
        when(parkedOrderRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));

        NxParkedOrder released = parkedService.releaseOrder(parkedId, "stock arrived");

        assertEquals("RELEASED", released.getStatus());
        assertNotNull(released.getReleasedAt());
    }

    @Test
    void parkedOrder_notFound_throws() {
        when(parkedOrderRepository.findById(any())).thenReturn(Optional.empty());

        assertThrows(ResourceNotFoundException.class, () -> parkedService.getParkedOrder(UUID.randomUUID()));
    }
}
