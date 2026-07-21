package com.nexus.oms.service;

import com.nexus.oms.entity.NxApprovalRule;
import com.nexus.oms.entity.NxOrderApproval;
import com.nexus.oms.exception.BadRequestException;
import com.nexus.oms.exception.ResourceNotFoundException;
import com.nexus.oms.repository.ApprovalRuleRepository;
import com.nexus.oms.repository.OrderApprovalRepository;
import com.nexus.oms.security.TenantContext;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.*;

@Service
public class OrderApprovalService {

    private static final Logger log = LoggerFactory.getLogger(OrderApprovalService.class);

    private final ApprovalRuleRepository approvalRuleRepository;
    private final OrderApprovalRepository orderApprovalRepository;

    public OrderApprovalService(ApprovalRuleRepository approvalRuleRepository,
                                OrderApprovalRepository orderApprovalRepository) {
        this.approvalRuleRepository = approvalRuleRepository;
        this.orderApprovalRepository = orderApprovalRepository;
    }

    // ─── Approval Rules ────────────────────────────────────────────────────

    @Transactional
    public NxApprovalRule createRule(NxApprovalRule rule) {
        UUID tenantId = TenantContext.getCurrentTenantId();
        rule.setTenantId(tenantId);
        return approvalRuleRepository.save(rule);
    }

    @Transactional
    public NxApprovalRule updateRule(UUID id, NxApprovalRule updates) {
        NxApprovalRule rule = approvalRuleRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("ApprovalRule", id));
        if (updates.getName() != null) rule.setName(updates.getName());
        if (updates.getRuleType() != null) rule.setRuleType(updates.getRuleType());
        if (updates.getAction() != null) rule.setAction(updates.getAction());
        if (updates.getThresholdValue() != null) rule.setThresholdValue(updates.getThresholdValue());
        if (updates.getThresholdString() != null) rule.setThresholdString(updates.getThresholdString());
        if (updates.getPriority() != null) rule.setPriority(updates.getPriority());
        if (updates.getActive() != null) rule.setActive(updates.getActive());
        return approvalRuleRepository.save(rule);
    }

    public List<NxApprovalRule> getRules() {
        UUID tenantId = TenantContext.getCurrentTenantId();
        return approvalRuleRepository.findByTenantIdOrderByPriorityAsc(tenantId);
    }

    public NxApprovalRule getRule(UUID id) {
        return approvalRuleRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("ApprovalRule", id));
    }

    @Transactional
    public void deleteRule(UUID id) {
        approvalRuleRepository.deleteById(id);
    }

    // ─── Order Approval ────────────────────────────────────────────────────

    @Transactional
    public NxOrderApproval evaluateOrder(UUID orderId, String orderNumber, BigDecimal orderTotal, UUID customerId) {
        UUID tenantId = TenantContext.getCurrentTenantId();

        NxOrderApproval existing = orderApprovalRepository.findByOrderId(orderId);
        if (existing != null) return existing;

        List<NxApprovalRule> rules = approvalRuleRepository.findByTenantIdAndActiveTrue(tenantId);
        List<String> matchedRules = new ArrayList<>();
        String finalAction = "AUTO_APPROVE";

        for (NxApprovalRule rule : rules) {
            boolean matched = false;
            switch (rule.getRuleType()) {
                case "AMOUNT_THRESHOLD":
                    if (orderTotal != null && rule.getThresholdValue() != null &&
                        orderTotal.compareTo(rule.getThresholdValue()) > 0) {
                        matched = true;
                    }
                    break;
                case "REPEAT_CUSTOMER":
                    long previousOrders = orderApprovalRepository.findByCustomerId(customerId).size();
                    if (previousOrders == 0) {
                        matched = true;
                    }
                    break;
            }

            if (matched) {
                matchedRules.add(rule.getName() + " (" + rule.getRuleType() + ")");
                if ("REJECT".equals(rule.getAction())) {
                    finalAction = "REJECT";
                    break;
                } else if ("HOLD_FOR_REVIEW".equals(rule.getAction())) {
                    finalAction = "HOLD_FOR_REVIEW";
                }
            }
        }

        NxOrderApproval approval = NxOrderApproval.builder()
                .tenantId(tenantId)
                .orderId(orderId)
                .orderNumber(orderNumber)
                .orderTotal(orderTotal)
                .customerId(customerId)
                .riskScore(BigDecimal.ZERO)
                .status(finalAction)
                .matchedRules(String.join(", ", matchedRules))
                .build();
        approval = orderApprovalRepository.save(approval);

        log.info("Order {} evaluation: action={}, rules={}", orderNumber, finalAction, matchedRules);
        return approval;
    }

    @Transactional
    public NxOrderApproval manualReview(UUID id, String decision, String reviewer, String notes) {
        NxOrderApproval approval = orderApprovalRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("OrderApproval", id));

        if (!"MANUAL_REVIEW".equals(approval.getStatus())) {
            throw new BadRequestException("Order is not in MANUAL_REVIEW status");
        }

        if (!"APPROVED".equals(decision) && !"REJECTED".equals(decision)) {
            throw new BadRequestException("Decision must be APPROVED or REJECTED");
        }

        approval.setStatus(decision);
        approval.setReviewedBy(reviewer);
        approval.setReviewNotes(notes);
        approval.setDecidedAt(LocalDateTime.now());
        return orderApprovalRepository.save(approval);
    }

    public List<NxOrderApproval> getPendingReviews() {
        UUID tenantId = TenantContext.getCurrentTenantId();
        return orderApprovalRepository.findByTenantIdAndStatus(tenantId, "MANUAL_REVIEW");
    }

    public List<NxOrderApproval> getAllApprovals() {
        UUID tenantId = TenantContext.getCurrentTenantId();
        return orderApprovalRepository.findByTenantId(tenantId);
    }

    public NxOrderApproval getApproval(UUID orderId) {
        return orderApprovalRepository.findByOrderId(orderId);
    }

    public Map<String, Object> getApprovalStats() {
        UUID tenantId = TenantContext.getCurrentTenantId();
        List<NxOrderApproval> all = orderApprovalRepository.findByTenantId(tenantId);

        long pending = all.stream().filter(a -> "MANUAL_REVIEW".equals(a.getStatus())).count();
        long approved = all.stream().filter(a -> "APPROVED".equals(a.getStatus())).count();
        long rejected = all.stream().filter(a -> "REJECTED".equals(a.getStatus())).count();
        long autoApproved = all.stream().filter(a -> "AUTO_APPROVE".equals(a.getStatus())).count();

        Map<String, Object> stats = new LinkedHashMap<>();
        stats.put("total", all.size());
        stats.put("pending", pending);
        stats.put("approved", approved);
        stats.put("rejected", rejected);
        stats.put("autoApproved", autoApproved);
        return stats;
    }
}
