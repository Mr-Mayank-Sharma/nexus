package com.nexus.core.ai;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.nexus.core.CoreStatus;
import com.nexus.core.model.ai.AiActionPolicy;
import com.nexus.core.model.ai.AiProposal;
import com.nexus.core.model.order.OrderHeader;
import com.nexus.core.service.CoreClock;
import com.nexus.core.service.SequenceService;
import com.nexus.core.service.StatusService;
import jakarta.persistence.EntityManager;
import jakarta.persistence.LockModeType;
import org.springframework.stereotype.Service;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Set;

/**
 * What an AI wants to change (doc 25, section 2, rules 2 to 4). An agent never changes an order itself: it makes a
 * proposal. The action's policy ({@code ai_action_policy}) decides:
 * <ul>
 *   <li>ASK: a person approves or rejects it;</li>
 *   <li>AUTO: applied at once;</li>
 *   <li>AUTO_UNDER_LIMIT: applied at once when the amount is under the policy's limit;</li>
 * </ul>
 * with a daily cap per action for the automatic ones. What each action does, and how it is undone, is
 * {@link ProposalActions}; the proposal records how to undo itself when it is applied.
 * <p>
 * Transactions are explicit ({@link TransactionTemplate}): a failed action rolls back, and the failure is then recorded
 * on the proposal in a second transaction.
 */
@Service("coreProposalService")
public class ProposalService {

    static final ObjectMapper JSON = new ObjectMapper();
    public static final String FLOW = StatusService.DEFAULT_FLOW;

    public static final String ASK = "ASK";
    public static final String AUTO = "AUTO";
    public static final String AUTO_UNDER_LIMIT = "AUTO_UNDER_LIMIT";
    static final Set<String> APPROVAL_MODES = Set.of(ASK, AUTO, AUTO_UNDER_LIMIT);
    static final String DECIDED_BY_POLICY = "policy:auto";

    private final EntityManager entityManager;
    private final SequenceService sequenceService;
    private final StatusService statusService;
    private final ProposalActions proposalActions;
    private final TransactionTemplate transaction;

    public ProposalService(EntityManager entityManager, SequenceService sequenceService, StatusService statusService, ProposalActions proposalActions,
                           PlatformTransactionManager transactionManager) {
        this.entityManager = entityManager;
        this.sequenceService = sequenceService;
        this.statusService = statusService;
        this.proposalActions = proposalActions;
        this.transaction = new TransactionTemplate(transactionManager);
    }

    public record View(String proposalId, String actionType, String orderId, String statusId, String reason, String payload, String result,
                       String decidedBy) {
    }

    // ── the proposal's life ──

    /** Records a proposal, and applies it at once when its policy allows. */
    public View propose(String aiRunId, String actionType, ObjectNode payload, String reason, BigDecimal amount) {
        String proposalId = transaction.execute(status -> {
            if (entityManager.find(AiActionPolicy.class, actionType) == null) {
                throw new IllegalArgumentException("AI may not propose " + actionType);
            }
            AiProposal proposal = new AiProposal();
            proposal.setProposalId(sequenceService.nextId("AiProposal"));
            proposal.setAiRunId(aiRunId);
            proposal.setActionType(actionType);
            proposal.setOrderId(payload.path("orderId").asText(null));
            proposal.setPayload(payload.toString());
            proposal.setReason(reason);
            proposal.setAmount(amount);
            proposal.setStatusId(statusService.initialStatus(FLOW, CoreStatus.Type.AI_PROPOSAL));
            proposal.setCreatedAt(CoreClock.now());
            entityManager.persist(proposal);
            return proposal.getProposalId();
        });
        if (policyAppliesAutomatically(actionType, amount)) {
            return apply(proposalId, DECIDED_BY_POLICY);
        }
        return view(proposalId);
    }

    /** A person approves: the proposal is applied, or marked failed with the reason. */
    public View approve(String proposalId, String userId) {
        return apply(proposalId, userId);
    }

    public View reject(String proposalId, String userId, String note) {
        return transaction.execute(status -> {
            AiProposal proposal = loadForUpdate(proposalId);
            statusService.requireAllowed(FLOW, proposal.getStatusId(), CoreStatus.Proposal.REJECTED);
            proposal.setStatusId(CoreStatus.Proposal.REJECTED);
            proposal.setDecidedBy(userId);
            proposal.setDecidedAt(CoreClock.now());
            proposal.setResult(note);
            return toView(proposal);
        });
    }

    /** Undoes an applied proposal with its compensating action. */
    public View undo(String proposalId, String userId) {
        return transaction.execute(status -> {
            AiProposal proposal = loadForUpdate(proposalId);
            statusService.requireAllowed(FLOW, proposal.getStatusId(), CoreStatus.Proposal.UNDONE);
            if (proposal.getUndoPayload() == null) {
                throw new IllegalStateException("A " + proposal.getActionType() + " can't be undone");
            }
            String undoAction = ProposalActions.undoActionOf(proposal.getActionType());
            proposalActions.apply(undoAction, parse(proposal.getUndoPayload()), "Undo of AI proposal " + proposalId, userId);
            proposal.setStatusId(CoreStatus.Proposal.UNDONE);
            String earlierResult = proposal.getResult() == null ? "" : proposal.getResult() + "; ";
            proposal.setResult(earlierResult + "undone by " + userId);
            return toView(proposal);
        });
    }

    /** Ties proposals an agent made to its run, once the run is recorded. */
    public void link(List<String> proposalIds, String aiRunId) {
        if (proposalIds.isEmpty()) {
            return;
        }
        transaction.executeWithoutResult(status ->
                proposalIds.forEach(proposalId -> entityManager.find(AiProposal.class, proposalId).setAiRunId(aiRunId)));
    }

    // ── reading ──

    /** Proposals waiting for a person, oldest first. */
    public List<View> pending() {
        return transaction.execute(status -> entityManager.createQuery("SELECT p FROM AiProposal p WHERE p.statusId = :pending ORDER BY p.createdAt",
                        AiProposal.class)
                .setParameter("pending", CoreStatus.Proposal.PENDING)
                .getResultList()
                .stream()
                .map(this::toView)
                .toList());
    }

    public View view(String proposalId) {
        return transaction.execute(status -> toView(load(proposalId)));
    }

    /** The order's value, for policies with a limit; null for an unknown order. */
    public BigDecimal orderValue(String orderId) {
        return transaction.execute(status -> {
            OrderHeader header = entityManager.find(OrderHeader.class, orderId);
            return header == null ? null : header.getGrandTotal();
        });
    }

    // ── policies ──

    public record PolicyView(String actionType, String description, String riskLevel, String approvalMode, BigDecimal limitAmount, Long dailyLimit) {
    }

    /** How each AI action is approved, by action type. */
    public List<PolicyView> policies() {
        return transaction.execute(status -> entityManager.createQuery("SELECT p FROM AiActionPolicy p ORDER BY p.actionType", AiActionPolicy.class)
                .getResultList()
                .stream()
                .map(policy -> new PolicyView(policy.getActionType(), policy.getDescription(), policy.getRiskLevel(), policy.getApprovalMode(),
                        policy.getLimitAmount(), policy.getDailyLimit()))
                .toList());
    }

    /** Changes a policy: ASK, AUTO, or AUTO_UNDER_LIMIT with a limit amount. A null daily limit keeps the current one. */
    public void setPolicy(String actionType, String approvalMode, BigDecimal limitAmount, Long dailyLimit) {
        if (!APPROVAL_MODES.contains(approvalMode)) {
            throw new IllegalArgumentException("Approval is ASK, AUTO or AUTO_UNDER_LIMIT");
        }
        if (AUTO_UNDER_LIMIT.equals(approvalMode) && limitAmount == null) {
            throw new IllegalArgumentException("AUTO_UNDER_LIMIT needs a limit amount");
        }
        transaction.executeWithoutResult(status -> {
            AiActionPolicy policy = entityManager.find(AiActionPolicy.class, actionType);
            if (policy == null) {
                throw new IllegalArgumentException("Unknown action " + actionType);
            }
            policy.setApprovalMode(approvalMode);
            policy.setLimitAmount(limitAmount);
            if (dailyLimit != null) {
                policy.setDailyLimit(dailyLimit);
            }
        });
    }

    /**
     * Whether the policy applies this proposal without a person: never for ASK or past the daily cap; always for
     * AUTO; for AUTO_UNDER_LIMIT only when the amount is known and under the limit.
     */
    private boolean policyAppliesAutomatically(String actionType, BigDecimal amount) {
        return Boolean.TRUE.equals(transaction.execute(status -> {
            AiActionPolicy policy = entityManager.find(AiActionPolicy.class, actionType);
            if (policy == null || ASK.equals(policy.getApprovalMode())) {
                return false;
            }
            if (policy.getDailyLimit() != null && appliedToday(actionType) >= policy.getDailyLimit()) {
                return false;
            }
            if (AUTO.equals(policy.getApprovalMode())) {
                return true;
            }
            return AUTO_UNDER_LIMIT.equals(policy.getApprovalMode()) && amount != null && policy.getLimitAmount() != null
                    && amount.compareTo(policy.getLimitAmount()) < 0;
        }));
    }

    /** How many proposals of this action were applied today (undone ones still count). */
    private long appliedToday(String actionType) {
        LocalDateTime startOfToday = CoreClock.now().toLocalDate().atStartOfDay();
        return entityManager.createQuery("SELECT COUNT(p) FROM AiProposal p WHERE p.actionType = :actionType "
                        + "AND p.statusId IN (:applied, :undone) AND p.appliedAt >= :startOfToday", Long.class)
                .setParameter("actionType", actionType)
                .setParameter("applied", CoreStatus.Proposal.APPLIED)
                .setParameter("undone", CoreStatus.Proposal.UNDONE)
                .setParameter("startOfToday", startOfToday)
                .getSingleResult();
    }

    // ── internals ──

    /**
     * Applies the proposal in one transaction. When the action fails, that transaction rolls back and a second one
     * marks the proposal failed with the reason (unless someone else decided it meanwhile).
     */
    private View apply(String proposalId, String decidedBy) {
        try {
            return transaction.execute(status -> {
                AiProposal proposal = loadForUpdate(proposalId);
                statusService.requireAllowed(FLOW, proposal.getStatusId(), CoreStatus.Proposal.APPLIED);
                JsonNode payload = parse(proposal.getPayload());
                ObjectNode undoPayload = proposalActions.undoPayload(proposal.getActionType(), payload);   // read before the change
                String reason = "AI proposal " + proposalId + (proposal.getReason() == null ? "" : ": " + proposal.getReason());
                proposalActions.apply(proposal.getActionType(), payload, reason, decidedBy);

                LocalDateTime now = CoreClock.now();
                proposal.setStatusId(CoreStatus.Proposal.APPLIED);
                proposal.setDecidedBy(decidedBy);
                proposal.setDecidedAt(now);
                proposal.setAppliedAt(now);
                proposal.setUndoPayload(undoPayload == null ? null : undoPayload.toString());
                proposal.setResult("Applied");
                return toView(proposal);
            });
        } catch (RuntimeException failure) {
            return transaction.execute(status -> {
                AiProposal proposal = loadForUpdate(proposalId);
                if (!CoreStatus.Proposal.PENDING.equals(proposal.getStatusId())) {
                    throw failure;   // not ours to mark: it was decided some other way
                }
                proposal.setStatusId(CoreStatus.Proposal.FAILED);
                proposal.setDecidedBy(decidedBy);
                proposal.setDecidedAt(CoreClock.now());
                proposal.setResult(failure.getMessage());
                return toView(proposal);
            });
        }
    }

    private AiProposal loadForUpdate(String proposalId) {
        AiProposal proposal = entityManager.find(AiProposal.class, proposalId, LockModeType.PESSIMISTIC_WRITE);
        if (proposal == null) {
            throw new IllegalArgumentException("Unknown proposal " + proposalId);
        }
        return proposal;
    }

    private AiProposal load(String proposalId) {
        AiProposal proposal = entityManager.find(AiProposal.class, proposalId);
        if (proposal == null) {
            throw new IllegalArgumentException("Unknown proposal " + proposalId);
        }
        return proposal;
    }

    private View toView(AiProposal proposal) {
        return new View(proposal.getProposalId(), proposal.getActionType(), proposal.getOrderId(), proposal.getStatusId(), proposal.getReason(),
                proposal.getPayload(), proposal.getResult(), proposal.getDecidedBy());
    }

    private static JsonNode parse(String storedJson) {
        try {
            return JSON.readTree(storedJson);
        } catch (JsonProcessingException corrupt) {
            throw new IllegalStateException("A stored proposal could not be read", corrupt);
        }
    }
}
