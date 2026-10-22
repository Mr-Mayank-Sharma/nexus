package com.nexus.core.job;

import com.nexus.core.CoreTransactional;
import org.springframework.beans.factory.annotation.Qualifier;
import com.nexus.core.CoreStatus;
import com.nexus.core.model.service.SystemMessage;
import com.nexus.core.model.service.SystemMessageError;
import com.nexus.core.model.service.SystemMessageType;
import com.nexus.core.service.CoreClock;
import com.nexus.core.service.SequenceService;
import jakarta.persistence.EntityManager;
import jakarta.persistence.LockModeType;
import org.springframework.context.ApplicationContext;
import org.springframework.stereotype.Service;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.TransactionDefinition;
import org.springframework.transaction.support.TransactionTemplate;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;

/**
 * Messages between Nexus and other systems (Moqui's {@code system_message}), the transport of doc 27.
 * <ul>
 *   <li>A received message is stored first and consumed after, so a sender gets its answer at once and a failure never
 *       loses it.</li>
 *   <li>The same type and idempotency key is stored once, so a sender may safely send again.</li>
 *   <li>Consuming runs the type's handler bean in its own transaction. A failure is kept as a
 *       {@code system_message_error} and retried after 1, 2, 4 and 8 minutes, then left in error for a person.</li>
 * </ul>
 * This bean is also the {@code core_messages} job.
 */
@Service("coreMessageService")
public class MessageService implements CoreJobHandler {

    public static final int MAX_ATTEMPTS = 5;
    private static final int MAX_ERROR_LENGTH = 4000;
    /** Statuses a message can be consumed from. */
    private static final Set<String> CONSUMABLE = Set.of(CoreStatus.Message.RECEIVED, CoreStatus.Message.ERROR);

    private final EntityManager entityManager;
    private final SequenceService sequenceService;
    private final ApplicationContext applicationContext;
    private final TransactionTemplate newTransaction;

    public MessageService(EntityManager entityManager, SequenceService sequenceService, ApplicationContext applicationContext,
                          @Qualifier(CoreTransactional.MANAGER) PlatformTransactionManager transactionManager) {
        this.entityManager = entityManager;
        this.sequenceService = sequenceService;
        this.applicationContext = applicationContext;
        this.newTransaction = new TransactionTemplate(transactionManager);
        this.newTransaction.setPropagationBehavior(TransactionDefinition.PROPAGATION_REQUIRES_NEW);
    }

    public record Received(String systemMessageId, boolean duplicate, String statusId) {
    }

    public record ConsumeResult(int consumed, int failed) {
    }

    public record MessageView(String systemMessageId, String typeId, String statusId, long failCount, String initDate, String processedDate,
                              String lastError) {
    }

    /** A message about to be consumed: which handler bean takes it, and its text. */
    private record Delivery(String handlerBeanName, String messageText) {
    }

    // ── receiving ──

    /** Stores a message from another system; the same type and key again returns the first one. */
    public Received receive(String typeId, String text, String idempotencyKey, String remoteMessageId, String remoteId) {
        String key = idempotencyKey == null || idempotencyKey.isBlank() ? null : idempotencyKey.trim();
        return newTransaction.execute(status -> {
            SystemMessageType type = entityManager.find(SystemMessageType.class, typeId);
            if (type == null || type.getConsumeServiceName() == null) {
                throw new IllegalArgumentException("Nexus does not accept messages of type " + typeId);
            }
            if (key != null) {
                Optional<SystemMessage> sameMessage = receivedWithKey(typeId, key);
                if (sameMessage.isPresent()) {
                    return new Received(sameMessage.get().getSystemMessageId(), true, sameMessage.get().getStatusId());
                }
            }
            SystemMessage message = new SystemMessage();
            message.setSystemMessageId(sequenceService.nextId("SystemMessage"));
            message.setSystemMessageTypeId(typeId);
            message.setSystemMessageRemoteId(remoteId);
            message.setStatusId(CoreStatus.Message.RECEIVED);
            message.setIsOutgoing(false);
            message.setInitDate(CoreClock.now());
            message.setFailCount(0L);
            message.setIdempotencyKey(key);
            message.setRemoteMessageId(remoteMessageId);
            message.setMessageText(text);
            entityManager.persist(message);
            return new Received(message.getSystemMessageId(), false, message.getStatusId());
        });
    }

    // ── consuming ──

    /** The core_messages job: consumes what is waiting (parameter {@code limit}, 200 when unset). */
    @Override
    public String run(Map<String, String> parameters) {
        int limit = Integer.parseInt(parameters.getOrDefault("limit", "200"));
        ConsumeResult result = consumeDue(limit);
        return result.consumed() + " consumed, " + result.failed() + " failed";
    }

    /** Consumes received messages, and retries failed ones whose wait is over, oldest first. */
    public ConsumeResult consumeDue(int limit) {
        LocalDateTime now = CoreClock.now();
        List<String> dueMessageIds = newTransaction.execute(status -> entityManager.createQuery("SELECT m FROM SystemMessage m "
                        + "WHERE m.isOutgoing = false AND m.statusId IN :consumable ORDER BY m.initDate, m.systemMessageId", SystemMessage.class)
                .setParameter("consumable", CONSUMABLE)
                .setMaxResults(limit * 4)   // failed ones still waiting are skipped below
                .getResultList()
                .stream()
                .filter(message -> CoreStatus.Message.RECEIVED.equals(message.getStatusId()) || retryDue(message, now))
                .limit(limit)
                .map(SystemMessage::getSystemMessageId)
                .toList());
        int consumed = 0;
        int failed = 0;
        for (String messageId : dueMessageIds) {
            if (consume(messageId)) {
                consumed++;
            } else {
                failed++;
            }
        }
        return new ConsumeResult(consumed, failed);
    }

    /** A failed message is retried 1, 2, 4, 8 minutes after its last attempt, until it has failed {@value #MAX_ATTEMPTS} times. */
    static boolean retryDue(SystemMessage message, LocalDateTime now) {
        long failures = message.getFailCount() == null ? 0 : message.getFailCount();
        if (failures >= MAX_ATTEMPTS) {
            return false;
        }
        LocalDateTime lastAttempt = message.getLastAttemptDate() == null ? message.getInitDate() : message.getLastAttemptDate();
        long waitMinutes = 1L << Math.max(0, failures - 1);
        return !lastAttempt.plusMinutes(waitMinutes).isAfter(now);
    }

    /**
     * Consumes one message now, in three transactions: mark it consuming; run the handler and mark it consumed; or, if
     * the handler failed, mark it in error with the reason. True when the handler succeeded.
     */
    public boolean consume(String systemMessageId) {
        Delivery delivery = newTransaction.execute(status -> startConsuming(systemMessageId));
        if (delivery == null) {
            return false;   // gone, or already taken by another consumer
        }
        try {
            newTransaction.executeWithoutResult(status -> {
                applicationContext.getBean(delivery.handlerBeanName(), CoreMessageHandler.class).consume(systemMessageId, delivery.messageText());
                SystemMessage message = entityManager.find(SystemMessage.class, systemMessageId);
                message.setStatusId(CoreStatus.Message.CONSUMED);
                message.setProcessedDate(CoreClock.now());
            });
            return true;
        } catch (RuntimeException failure) {
            newTransaction.executeWithoutResult(status -> recordFailure(systemMessageId, failure));
            return false;
        }
    }

    private Delivery startConsuming(String systemMessageId) {
        SystemMessage message = entityManager.find(SystemMessage.class, systemMessageId, LockModeType.PESSIMISTIC_WRITE);
        if (message == null || !CONSUMABLE.contains(message.getStatusId())) {
            return null;
        }
        message.setStatusId(CoreStatus.Message.CONSUMING);
        message.setLastAttemptDate(CoreClock.now());
        String handlerBeanName = entityManager.find(SystemMessageType.class, message.getSystemMessageTypeId()).getConsumeServiceName();
        return new Delivery(handlerBeanName, message.getMessageText());
    }

    private void recordFailure(String systemMessageId, RuntimeException failure) {
        SystemMessage message = entityManager.find(SystemMessage.class, systemMessageId);
        message.setStatusId(CoreStatus.Message.ERROR);
        message.setFailCount((message.getFailCount() == null ? 0 : message.getFailCount()) + 1);

        String errorText = failure.getClass().getSimpleName() + ": " + failure.getMessage();
        SystemMessageError error = new SystemMessageError();
        error.setSystemMessageId(systemMessageId);
        error.setErrorDate(CoreClock.now());
        error.setAttemptedStatusId(CoreStatus.Message.CONSUMED);
        error.setErrorText(errorText.length() > MAX_ERROR_LENGTH ? errorText.substring(0, MAX_ERROR_LENGTH) : errorText);
        entityManager.persist(error);
    }

    // ── reading ──

    /** A message with its status, failures and latest error. */
    public Optional<MessageView> view(String systemMessageId) {
        return newTransaction.execute(status -> Optional.ofNullable(entityManager.find(SystemMessage.class, systemMessageId))
                .map(message -> new MessageView(message.getSystemMessageId(), message.getSystemMessageTypeId(), message.getStatusId(),
                        message.getFailCount() == null ? 0 : message.getFailCount(), String.valueOf(message.getInitDate()),
                        message.getProcessedDate() == null ? null : message.getProcessedDate().toString(), latestError(systemMessageId))));
    }

    private String latestError(String systemMessageId) {
        return entityManager.createQuery("SELECT e.errorText FROM SystemMessageError e WHERE e.systemMessageId = :messageId ORDER BY e.errorDate DESC",
                        String.class)
                .setParameter("messageId", systemMessageId)
                .setMaxResults(1)
                .getResultList()
                .stream()
                .findFirst()
                .orElse(null);
    }

    private Optional<SystemMessage> receivedWithKey(String typeId, String idempotencyKey) {
        return entityManager.createQuery("SELECT m FROM SystemMessage m WHERE m.systemMessageTypeId = :typeId AND m.idempotencyKey = :key "
                        + "AND m.isOutgoing = false", SystemMessage.class)
                .setParameter("typeId", typeId)
                .setParameter("key", idempotencyKey)
                .setMaxResults(1)
                .getResultList()
                .stream()
                .findFirst();
    }
}
