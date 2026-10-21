package com.nexus.core.ai;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.nexus.core.model.ai.AiActionPolicy;
import com.nexus.core.model.ai.AiProposal;
import com.nexus.core.model.order.OrderHeader;
import com.nexus.core.model.order.OrderItemShipGroup;
import com.nexus.core.service.CoreClock;
import com.nexus.core.service.OrderService;
import com.nexus.core.service.RoutingService;
import com.nexus.core.service.SequenceService;
import com.nexus.core.service.StatusService;
import jakarta.persistence.EntityManager;
import jakarta.persistence.LockModeType;
import org.springframework.stereotype.Service;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;

import java.math.BigDecimal;
import java.util.List;

/**
 * What an AI wants to change (doc 25, section 2, rules 2 to 4). An agent never changes an order itself: it makes a
 * proposal. The action's policy decides: ASK (a person approves), AUTO (applied at once) or AUTO_UNDER_LIMIT (at once
 * when the amount is under the limit), with a daily cap per action. A proposal is applied by the same services a
 * person's button calls, so status flows, stock checks and permissions still hold, and it records how to undo itself.
 */
@Service("coreProposalService")
public class ProposalService {

    static final ObjectMapper JSON = new ObjectMapper();
    public static final String FLOW = StatusService.DEFAULT_FLOW;

    private final EntityManager em;
    private final SequenceService sequences;
    private final StatusService statuses;
    private final OrderService orders;
    private final RoutingService routing;
    private final TransactionTemplate tx;

    public ProposalService(EntityManager em, SequenceService sequences, StatusService statuses, OrderService orders, RoutingService routing,
                           PlatformTransactionManager tm) {
        this.em = em;
        this.sequences = sequences;
        this.statuses = statuses;
        this.orders = orders;
        this.routing = routing;
        this.tx = new TransactionTemplate(tm);
    }

    public record View(String proposalId, String actionType, String orderId, String statusId, String reason, String payload, String result, String decidedBy) {
    }

    /** Records a proposal and applies it at once when its policy allows. */
    public View propose(String aiRunId, String actionType, ObjectNode payload, String reason, BigDecimal amount) {
        String id = tx.execute(s -> {
            AiActionPolicy policy = em.find(AiActionPolicy.class, actionType);
            if (policy == null) throw new IllegalArgumentException("AI may not propose " + actionType);
            AiProposal p = new AiProposal();
            p.setProposalId(sequences.nextId("AiProposal"));
            p.setAiRunId(aiRunId);
            p.setActionType(actionType);
            p.setOrderId(payload.path("orderId").asText(null));
            p.setPayload(payload.toString());
            p.setReason(reason);
            p.setAmount(amount);
            p.setStatusId(statuses.initialStatus(FLOW, "AI_PROPOSAL_STATUS"));
            p.setCreatedAt(CoreClock.now());
            em.persist(p);
            return p.getProposalId();
        });
        if (auto(actionType, amount)) return apply(id, "policy:auto");
        return view(id);
    }

    /** A person approves: the proposal is applied, or marked failed with the reason. */
    public View approve(String proposalId, String userId) {
        return apply(proposalId, userId);
    }

    public View reject(String proposalId, String userId, String note) {
        return tx.execute(s -> {
            AiProposal p = lock(proposalId);
            statuses.requireAllowed(FLOW, p.getStatusId(), "AIP_REJECTED");
            p.setStatusId("AIP_REJECTED");
            p.setDecidedBy(userId);
            p.setDecidedAt(CoreClock.now());
            p.setResult(note);
            return view(p);
        });
    }

    /** Undoes an applied proposal with its compensating action. */
    public View undo(String proposalId, String userId) {
        return tx.execute(s -> {
            AiProposal p = lock(proposalId);
            statuses.requireAllowed(FLOW, p.getStatusId(), "AIP_UNDONE");
            if (p.getUndoPayload() == null) throw new IllegalStateException("A " + p.getActionType() + " can't be undone");
            JsonNode u = read(p.getUndoPayload());
            run(p.getActionType().equals("HOLD_ORDER") ? "RELEASE_HOLD" : p.getActionType().equals("RELEASE_HOLD") ? "HOLD_ORDER" : p.getActionType(), u,
                    "Undo of AI proposal " + proposalId, userId);
            p.setStatusId("AIP_UNDONE");
            p.setResult((p.getResult() == null ? "" : p.getResult() + "; ") + "undone by " + userId);
            return view(p);
        });
    }

    public List<View> pending() {
        return tx.execute(s -> em.createQuery("SELECT p FROM AiProposal p WHERE p.statusId = 'AIP_PENDING' ORDER BY p.createdAt", AiProposal.class)
                .getResultList().stream().map(this::view).toList());
    }

    public View view(String proposalId) {
        return tx.execute(s -> view(lockless(proposalId)));
    }

    // ── internals ──

    private View apply(String proposalId, String decidedBy) {
        try {
            return tx.execute(s -> {
                AiProposal p = lock(proposalId);
                statuses.requireAllowed(FLOW, p.getStatusId(), "AIP_APPLIED");
                JsonNode payload = read(p.getPayload());
                ObjectNode undo = undoFor(p.getActionType(), payload);
                run(p.getActionType(), payload, "AI proposal " + proposalId + (p.getReason() == null ? "" : ": " + p.getReason()), decidedBy);
                p.setStatusId("AIP_APPLIED");
                p.setDecidedBy(decidedBy);
                p.setDecidedAt(CoreClock.now());
                p.setAppliedAt(CoreClock.now());
                p.setUndoPayload(undo == null ? null : undo.toString());
                p.setResult("Applied");
                return view(p);
            });
        } catch (RuntimeException e) {
            // the change rolled back; record why it could not be applied
            return tx.execute(s -> {
                AiProposal p = lock(proposalId);
                if (!"AIP_PENDING".equals(p.getStatusId())) throw e;
                p.setStatusId("AIP_FAILED");
                p.setDecidedBy(decidedBy);
                p.setDecidedAt(CoreClock.now());
                p.setResult(e.getMessage());
                return view(p);
            });
        }
    }

    private void run(String actionType, JsonNode p, String reason, String userId) {
        String orderId = p.path("orderId").asText();
        switch (actionType) {
            case "REALLOCATE" -> routing.reallocate(orderId, p.path("shipGroupSeqId").asText("00001"), p.path("facilityId").asText(), reason, userId);
            case "HOLD_ORDER" -> {
                if ("ORDER_HOLD".equals(status(orderId))) throw new IllegalStateException("Order " + orderId + " is already on hold");
                orders.changeStatus(orderId, "ORDER_HOLD", reason, userId);
            }
            case "RELEASE_HOLD" -> {
                if (!"ORDER_HOLD".equals(status(orderId))) throw new IllegalStateException("Order " + orderId + " is not on hold; it is " + status(orderId));
                orders.changeStatus(orderId, "ORDER_APPROVED", reason, userId);
            }
            case "CANCEL_ITEM" -> orders.cancelItem(orderId, p.path("orderItemSeqId").asText(), null, reason, userId);
            default -> throw new IllegalArgumentException("Unknown action " + actionType);
        }
    }

    private String status(String orderId) {
        OrderHeader h = em.find(OrderHeader.class, orderId);
        if (h == null) throw new IllegalArgumentException("Unknown order " + orderId);
        return h.getStatusId();
    }

    /** What would put things back: the location before a move; the opposite status change; nothing for a cancel. */
    private ObjectNode undoFor(String actionType, JsonNode p) {
        return switch (actionType) {
            case "REALLOCATE" -> {
                String group = p.path("shipGroupSeqId").asText("00001");
                OrderItemShipGroup g = em.find(OrderItemShipGroup.class, new OrderItemShipGroup.Key(p.path("orderId").asText(), group));
                yield g == null || g.getFacilityId() == null ? null
                        : JSON.createObjectNode().put("orderId", p.path("orderId").asText()).put("shipGroupSeqId", group).put("facilityId", g.getFacilityId());
            }
            case "HOLD_ORDER", "RELEASE_HOLD" -> JSON.createObjectNode().put("orderId", p.path("orderId").asText());
            default -> null;
        };
    }

    private boolean auto(String actionType, BigDecimal amount) {
        return Boolean.TRUE.equals(tx.execute(s -> {
            AiActionPolicy policy = em.find(AiActionPolicy.class, actionType);
            if (policy == null || "ASK".equals(policy.getApprovalMode())) return false;
            if (policy.getDailyLimit() != null) {
                long today = em.createQuery("SELECT COUNT(p) FROM AiProposal p WHERE p.actionType = :t AND p.statusId IN ('AIP_APPLIED', 'AIP_UNDONE') "
                        + "AND p.appliedAt >= :d", Long.class).setParameter("t", actionType).setParameter("d", CoreClock.now().toLocalDate().atStartOfDay()).getSingleResult();
                if (today >= policy.getDailyLimit()) return false;
            }
            if ("AUTO".equals(policy.getApprovalMode())) return true;
            return "AUTO_UNDER_LIMIT".equals(policy.getApprovalMode()) && amount != null && policy.getLimitAmount() != null
                    && amount.compareTo(policy.getLimitAmount()) < 0;
        }));
    }

    /** Changes a policy: ASK, AUTO or AUTO_UNDER_LIMIT with a limit. */
    public void setPolicy(String actionType, String approvalMode, BigDecimal limitAmount, Long dailyLimit) {
        if (!List.of("ASK", "AUTO", "AUTO_UNDER_LIMIT").contains(approvalMode)) throw new IllegalArgumentException("Approval is ASK, AUTO or AUTO_UNDER_LIMIT");
        if ("AUTO_UNDER_LIMIT".equals(approvalMode) && limitAmount == null) throw new IllegalArgumentException("AUTO_UNDER_LIMIT needs a limit amount");
        tx.executeWithoutResult(s -> {
            AiActionPolicy p = em.find(AiActionPolicy.class, actionType);
            if (p == null) throw new IllegalArgumentException("Unknown action " + actionType);
            p.setApprovalMode(approvalMode);
            p.setLimitAmount(limitAmount);
            if (dailyLimit != null) p.setDailyLimit(dailyLimit);
        });
    }

    /** The order's value, for policies with a limit. */
    public BigDecimal orderValue(String orderId) {
        return tx.execute(s -> {
            OrderHeader h = em.find(OrderHeader.class, orderId);
            return h == null ? null : h.getGrandTotal();
        });
    }

    private AiProposal lock(String id) {
        AiProposal p = em.find(AiProposal.class, id, LockModeType.PESSIMISTIC_WRITE);
        if (p == null) throw new IllegalArgumentException("Unknown proposal " + id);
        return p;
    }

    private AiProposal lockless(String id) {
        AiProposal p = em.find(AiProposal.class, id);
        if (p == null) throw new IllegalArgumentException("Unknown proposal " + id);
        return p;
    }

    private View view(AiProposal p) {
        return new View(p.getProposalId(), p.getActionType(), p.getOrderId(), p.getStatusId(), p.getReason(), p.getPayload(), p.getResult(), p.getDecidedBy());
    }

    private static JsonNode read(String s) {
        try {
            return JSON.readTree(s);
        } catch (Exception e) {
            throw new IllegalStateException("A stored proposal could not be read", e);
        }
    }
}
