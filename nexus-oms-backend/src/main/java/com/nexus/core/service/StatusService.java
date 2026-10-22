package com.nexus.core.service;

import com.nexus.core.model.basic.StatusItem;
import jakarta.persistence.EntityManager;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Objects;

/**
 * Statuses and the changes allowed between them, held as data ({@code status_item}, {@code status_flow_transition})
 * instead of in code, so a client's flow can be changed without a release. Every status type's standard changes are in
 * the {@value #DEFAULT_FLOW} flow; a product store may point at its own flow later. A null flow means the default one.
 */
@Service("coreStatusService")
@Transactional(readOnly = true)
public class StatusService {

    public static final String DEFAULT_FLOW = "Default";

    private final EntityManager entityManager;

    public StatusService(EntityManager entityManager) {
        this.entityManager = entityManager;
    }

    public StatusItem status(String statusId) {
        StatusItem status = entityManager.find(StatusItem.class, statusId);
        if (status == null) {
            throw new IllegalArgumentException("Unknown status " + statusId);
        }
        return status;
    }

    /** True when the flow allows the change (staying in the same status is always allowed). */
    public boolean isAllowed(String flowId, String fromStatusId, String toStatusId) {
        if (Objects.equals(fromStatusId, toStatusId)) {
            return true;
        }
        Long transitions = entityManager.createQuery("SELECT COUNT(t) FROM StatusFlowTransition t WHERE t.statusFlowId = :flowId "
                        + "AND t.statusId = :fromStatus AND t.toStatusId = :toStatus", Long.class)
                .setParameter("flowId", flowOrDefault(flowId))
                .setParameter("fromStatus", fromStatusId)
                .setParameter("toStatus", toStatusId)
                .getSingleResult();
        return transitions > 0;
    }

    /** Throws {@link StatusTransitionException} with a readable message (naming what may follow) when the change is not allowed. */
    public void requireAllowed(String flowId, String fromStatusId, String toStatusId) {
        if (isAllowed(flowId, fromStatusId, toStatusId)) {
            return;
        }
        List<String> allowedNext = next(flowId, fromStatusId).stream().map(StatusItem::getDescription).toList();
        throw new StatusTransitionException(fromStatusId, toStatusId, describe(fromStatusId), describe(toStatusId), allowedNext);
    }

    /** Statuses reachable from this one, in the flow's order. */
    public List<StatusItem> next(String flowId, String fromStatusId) {
        return entityManager.createQuery("SELECT s FROM StatusFlowTransition t, StatusItem s WHERE s.statusId = t.toStatusId "
                        + "AND t.statusFlowId = :flowId AND t.statusId = :fromStatus ORDER BY t.transitionSequence", StatusItem.class)
                .setParameter("flowId", flowOrDefault(flowId))
                .setParameter("fromStatus", fromStatusId)
                .getResultList();
    }

    /** The status a new record of this type starts in; the flow must have exactly one. */
    public String initialStatus(String flowId, String statusTypeId) {
        List<String> initialStatuses = entityManager.createQuery("SELECT i.statusId FROM StatusFlowItem i, StatusItem s WHERE s.statusId = i.statusId "
                        + "AND i.statusFlowId = :flowId AND s.statusTypeId = :typeId AND i.isInitial = :initial", String.class)
                .setParameter("flowId", flowOrDefault(flowId))
                .setParameter("typeId", statusTypeId)
                .setParameter("initial", Boolean.TRUE)
                .getResultList();
        if (initialStatuses.size() != 1) {
            throw new IllegalStateException("Status type " + statusTypeId + " needs exactly one initial status, found " + initialStatuses.size());
        }
        return initialStatuses.get(0);
    }

    private static String flowOrDefault(String flowId) {
        return flowId == null ? DEFAULT_FLOW : flowId;
    }

    /** The status's description, or its id when it has none; "none" for no status. */
    private String describe(String statusId) {
        if (statusId == null) {
            return "none";
        }
        StatusItem status = entityManager.find(StatusItem.class, statusId);
        return status == null || status.getDescription() == null ? statusId : status.getDescription();
    }

    /** A status change that the flow does not allow. */
    public static class StatusTransitionException extends IllegalStateException {
        private final String fromStatusId;
        private final String toStatusId;

        StatusTransitionException(String fromStatusId, String toStatusId, String fromName, String toName, List<String> allowedNext) {
            super("Can't change from " + fromName + " to " + toName
                    + (allowedNext.isEmpty() ? "; nothing can follow " + fromName + "." : ". Allowed next: " + String.join(", ", allowedNext) + "."));
            this.fromStatusId = fromStatusId;
            this.toStatusId = toStatusId;
        }

        public String getFromStatusId() {
            return fromStatusId;
        }

        public String getToStatusId() {
            return toStatusId;
        }
    }
}
