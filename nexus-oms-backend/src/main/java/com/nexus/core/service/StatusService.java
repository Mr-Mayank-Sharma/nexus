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
 * the {@value #DEFAULT_FLOW} flow; a product store may point at its own flow later.
 */
@Service("coreStatusService")
@Transactional(readOnly = true)
public class StatusService {

    public static final String DEFAULT_FLOW = "Default";

    private final EntityManager em;

    public StatusService(EntityManager em) {
        this.em = em;
    }

    public StatusItem status(String statusId) {
        StatusItem item = em.find(StatusItem.class, statusId);
        if (item == null) throw new IllegalArgumentException("Unknown status " + statusId);
        return item;
    }

    public boolean isAllowed(String flowId, String fromStatusId, String toStatusId) {
        if (Objects.equals(fromStatusId, toStatusId)) return true;
        Long n = em.createQuery("SELECT COUNT(t) FROM StatusFlowTransition t WHERE t.statusFlowId = :f "
                        + "AND t.statusId = :from AND t.toStatusId = :to", Long.class)
                .setParameter("f", flowId == null ? DEFAULT_FLOW : flowId)
                .setParameter("from", fromStatusId).setParameter("to", toStatusId).getSingleResult();
        return n > 0;
    }

    /** Throws {@link StatusTransitionException} with a readable message when the change is not allowed. */
    public void requireAllowed(String flowId, String fromStatusId, String toStatusId) {
        if (!isAllowed(flowId, fromStatusId, toStatusId)) {
            throw new StatusTransitionException(fromStatusId, toStatusId, describe(fromStatusId), describe(toStatusId),
                    next(flowId, fromStatusId).stream().map(StatusItem::getDescription).toList());
        }
    }

    /** Statuses reachable from this one, in the flow's order. */
    public List<StatusItem> next(String flowId, String fromStatusId) {
        return em.createQuery("SELECT s FROM StatusFlowTransition t, StatusItem s WHERE s.statusId = t.toStatusId "
                        + "AND t.statusFlowId = :f AND t.statusId = :from ORDER BY t.transitionSequence", StatusItem.class)
                .setParameter("f", flowId == null ? DEFAULT_FLOW : flowId).setParameter("from", fromStatusId).getResultList();
    }

    /** The status a new record of this type starts in. */
    public String initialStatus(String flowId, String statusTypeId) {
        List<String> ids = em.createQuery("SELECT i.statusId FROM StatusFlowItem i, StatusItem s WHERE s.statusId = i.statusId "
                        + "AND i.statusFlowId = :f AND s.statusTypeId = :t AND i.isInitial = :initial", String.class)
                .setParameter("f", flowId == null ? DEFAULT_FLOW : flowId).setParameter("t", statusTypeId)
                .setParameter("initial", Boolean.TRUE).getResultList();
        if (ids.size() != 1) throw new IllegalStateException("Status type " + statusTypeId + " needs exactly one initial status, found " + ids.size());
        return ids.get(0);
    }

    private String describe(String statusId) {
        if (statusId == null) return "none";
        StatusItem s = em.find(StatusItem.class, statusId);
        return s == null || s.getDescription() == null ? statusId : s.getDescription();
    }

    /** A status change that the flow does not allow. */
    public static class StatusTransitionException extends IllegalStateException {
        private final String fromStatusId;
        private final String toStatusId;

        StatusTransitionException(String from, String to, String fromName, String toName, List<String> allowed) {
            super("Can't change from " + fromName + " to " + toName
                    + (allowed.isEmpty() ? "; nothing can follow " + fromName + "." : ". Allowed next: " + String.join(", ", allowed) + "."));
            this.fromStatusId = from;
            this.toStatusId = to;
        }

        public String getFromStatusId() { return fromStatusId; }
        public String getToStatusId() { return toStatusId; }
    }
}
