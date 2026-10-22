package com.nexus.core.job;

import com.nexus.core.CoreStatus;
import com.nexus.core.service.CoreClock;
import com.nexus.core.service.OrderService;
import jakarta.persistence.EntityManager;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.TransactionDefinition;
import org.springframework.transaction.support.TransactionTemplate;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;

/**
 * The core_auto_cancel job: cancels what is still open on order items whose cancel date has passed (a pickup hold that
 * ran out, an item routing could not place in time). Each item is cancelled in its own transaction, giving its stock
 * back; one that fails is skipped and tried again next run.
 */
@Component("coreAutoCancelJob")
public class AutoCancelJob implements CoreJobHandler {

    private static final Logger log = LoggerFactory.getLogger(AutoCancelJob.class);
    static final String JOB_USER = "core_auto_cancel";

    private final EntityManager entityManager;
    private final OrderService orderService;
    private final TransactionTemplate newTransaction;

    public AutoCancelJob(EntityManager entityManager, OrderService orderService, PlatformTransactionManager transactionManager) {
        this.entityManager = entityManager;
        this.orderService = orderService;
        this.newTransaction = new TransactionTemplate(transactionManager);
        this.newTransaction.setPropagationBehavior(TransactionDefinition.PROPAGATION_REQUIRES_NEW);
    }

    /** An order item, by its key. */
    private record ItemKey(String orderId, String orderItemSeqId) {
    }

    @Override
    public String run(Map<String, String> parameters) {
        LocalDateTime now = CoreClock.now();
        List<ItemKey> dueItems = newTransaction.execute(status -> entityManager.createQuery("SELECT i.orderId, i.orderItemSeqId FROM OrderItem i "
                        + "WHERE i.autoCancelDate <= :now AND i.statusId IN :open", Object[].class)
                .setParameter("now", now)
                .setParameter("open", CoreStatus.Item.OPEN)
                .getResultList()
                .stream()
                .map(row -> new ItemKey((String) row[0], (String) row[1]))
                .toList());

        int cancelled = 0;
        int failed = 0;
        for (ItemKey item : dueItems) {
            try {
                newTransaction.executeWithoutResult(status ->
                        orderService.cancelItem(item.orderId(), item.orderItemSeqId(), null, "Cancel date passed", JOB_USER));
                cancelled++;
            } catch (RuntimeException failure) {
                failed++;
                log.warn("Could not auto-cancel item {} of order {}: {}", item.orderItemSeqId(), item.orderId(), failure.getMessage());
            }
        }
        return cancelled + " item(s) cancelled" + (failed > 0 ? ", " + failed + " failed" : "");
    }
}
