package com.nexus.core.job;

import com.nexus.core.model.service.SystemMessage;
import com.nexus.core.model.service.SystemMessageError;
import com.nexus.core.model.service.SystemMessageType;
import com.nexus.core.service.CoreClock;
import com.nexus.core.service.SequenceService;
import jakarta.persistence.EntityManager;
import org.springframework.context.ApplicationContext;
import org.springframework.stereotype.Service;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.TransactionDefinition;
import org.springframework.transaction.support.TransactionTemplate;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;
import java.util.Optional;

/**
 * Messages between Nexus and other systems (Moqui's {@code system_message}), the transport of doc 27. A received
 * message is stored first and consumed after, so a sender gets its answer at once and a failure never loses it. The
 * same type and idempotency key is stored once, so a sender may safely send again. Consuming runs the type's handler in
 * its own transaction; a failure is kept as a {@code system_message_error} and retried after 1, 2, 4 and 8 minutes,
 * then left in error for a person. This bean is also the {@code core_messages} job.
 */
@Service("coreMessageService")
public class MessageService implements CoreJobHandler {

    public static final int MAX_ATTEMPTS = 5;

    private final EntityManager em;
    private final SequenceService sequences;
    private final ApplicationContext context;
    private final TransactionTemplate tx;

    public MessageService(EntityManager em, SequenceService sequences, ApplicationContext context, PlatformTransactionManager tm) {
        this.em = em;
        this.sequences = sequences;
        this.context = context;
        this.tx = new TransactionTemplate(tm);
        this.tx.setPropagationBehavior(TransactionDefinition.PROPAGATION_REQUIRES_NEW);
    }

    public record Received(String systemMessageId, boolean duplicate, String statusId) {
    }

    /** Stores a message from another system; the same type and key again returns the first one. */
    public Received receive(String typeId, String text, String idempotencyKey, String remoteMessageId, String remoteId) {
        return tx.execute(s -> {
            SystemMessageType type = em.find(SystemMessageType.class, typeId);
            if (type == null || type.getConsumeServiceName() == null) throw new IllegalArgumentException("Nexus does not accept messages of type " + typeId);
            if (idempotencyKey != null && !idempotencyKey.isBlank()) {
                Optional<SystemMessage> same = em.createQuery("SELECT m FROM SystemMessage m WHERE m.systemMessageTypeId = :t AND m.idempotencyKey = :k "
                        + "AND m.isOutgoing = false", SystemMessage.class).setParameter("t", typeId).setParameter("k", idempotencyKey.trim())
                        .setMaxResults(1).getResultList().stream().findFirst();
                if (same.isPresent()) return new Received(same.get().getSystemMessageId(), true, same.get().getStatusId());
            }
            SystemMessage m = new SystemMessage();
            m.setSystemMessageId(sequences.nextId("SystemMessage"));
            m.setSystemMessageTypeId(typeId);
            m.setSystemMessageRemoteId(remoteId);
            m.setStatusId("SmsgReceived");
            m.setIsOutgoing(false);
            m.setInitDate(CoreClock.now());
            m.setFailCount(0L);
            m.setIdempotencyKey(idempotencyKey == null || idempotencyKey.isBlank() ? null : idempotencyKey.trim());
            m.setRemoteMessageId(remoteMessageId);
            m.setMessageText(text);
            em.persist(m);
            return new Received(m.getSystemMessageId(), false, m.getStatusId());
        });
    }

    /** The core_messages job: consumes what is waiting. */
    @Override
    public String run(Map<String, String> parameters) {
        int limit = Integer.parseInt(parameters.getOrDefault("limit", "200"));
        int[] c = consumeDue(limit);
        return c[0] + " consumed, " + c[1] + " failed";
    }

    /** Consumes received messages and retries failed ones whose wait is over, oldest first. Returns {consumed, failed}. */
    public int[] consumeDue(int limit) {
        LocalDateTime now = CoreClock.now();
        List<String> ids = tx.execute(s -> em.createQuery("SELECT m FROM SystemMessage m WHERE m.isOutgoing = false AND m.statusId IN ('SmsgReceived', 'SmsgError') "
                        + "ORDER BY m.initDate, m.systemMessageId", SystemMessage.class).setMaxResults(limit * 4).getResultList().stream()
                .filter(m -> "SmsgReceived".equals(m.getStatusId()) || retryDue(m, now)).limit(limit).map(SystemMessage::getSystemMessageId).toList());
        int ok = 0, bad = 0;
        for (String id : ids) {
            if (consume(id)) ok++;
            else bad++;
        }
        return new int[]{ok, bad};
    }

    static boolean retryDue(SystemMessage m, LocalDateTime now) {
        long fails = m.getFailCount() == null ? 0 : m.getFailCount();
        if (fails >= MAX_ATTEMPTS) return false;
        LocalDateTime last = m.getLastAttemptDate() == null ? m.getInitDate() : m.getLastAttemptDate();
        return !last.plusMinutes(1L << Math.max(0, fails - 1)).isAfter(now);
    }

    /** Consumes one message now; true when its handler succeeded. */
    public boolean consume(String systemMessageId) {
        String[] handler = tx.execute(s -> {
            SystemMessage m = em.find(SystemMessage.class, systemMessageId, jakarta.persistence.LockModeType.PESSIMISTIC_WRITE);
            if (m == null || !List.of("SmsgReceived", "SmsgError").contains(m.getStatusId())) return null;
            m.setStatusId("SmsgConsuming");
            m.setLastAttemptDate(CoreClock.now());
            return new String[]{em.find(SystemMessageType.class, m.getSystemMessageTypeId()).getConsumeServiceName(), m.getMessageText()};
        });
        if (handler == null) return false;
        try {
            tx.executeWithoutResult(s -> {
                context.getBean(handler[0], CoreMessageHandler.class).consume(systemMessageId, handler[1]);
                SystemMessage m = em.find(SystemMessage.class, systemMessageId);
                m.setStatusId("SmsgConsumed");
                m.setProcessedDate(CoreClock.now());
            });
            return true;
        } catch (RuntimeException e) {
            tx.executeWithoutResult(s -> {
                SystemMessage m = em.find(SystemMessage.class, systemMessageId);
                m.setStatusId("SmsgError");
                m.setFailCount((m.getFailCount() == null ? 0 : m.getFailCount()) + 1);
                SystemMessageError err = new SystemMessageError();
                err.setSystemMessageId(systemMessageId);
                err.setErrorDate(CoreClock.now());
                err.setAttemptedStatusId("SmsgConsumed");
                String text = e.getClass().getSimpleName() + ": " + e.getMessage();
                err.setErrorText(text.length() > 4000 ? text.substring(0, 4000) : text);
                em.persist(err);
            });
            return false;
        }
    }

    public record MessageView(String systemMessageId, String typeId, String statusId, long failCount, String initDate, String processedDate,
                              String lastError) {
    }

    public Optional<MessageView> view(String systemMessageId) {
        return tx.execute(s -> Optional.ofNullable(em.find(SystemMessage.class, systemMessageId)).map(m -> new MessageView(m.getSystemMessageId(),
                m.getSystemMessageTypeId(), m.getStatusId(), m.getFailCount() == null ? 0 : m.getFailCount(), String.valueOf(m.getInitDate()),
                m.getProcessedDate() == null ? null : m.getProcessedDate().toString(),
                em.createQuery("SELECT e.errorText FROM SystemMessageError e WHERE e.systemMessageId = :m ORDER BY e.errorDate DESC", String.class)
                        .setParameter("m", systemMessageId).setMaxResults(1).getResultList().stream().findFirst().orElse(null))));
    }
}
