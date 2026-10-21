package com.nexus.core.job;

import com.nexus.core.model.order.OrderItem;
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

    private final EntityManager em;
    private final OrderService orders;
    private final TransactionTemplate tx;

    public AutoCancelJob(EntityManager em, OrderService orders, PlatformTransactionManager tm) {
        this.em = em;
        this.orders = orders;
        this.tx = new TransactionTemplate(tm);
        this.tx.setPropagationBehavior(TransactionDefinition.PROPAGATION_REQUIRES_NEW);
    }

    @Override
    public String run(Map<String, String> parameters) {
        LocalDateTime now = CoreClock.now();
        List<String[]> due = tx.execute(s -> em.createQuery("SELECT i FROM OrderItem i WHERE i.autoCancelDate <= :now "
                        + "AND i.statusId IN ('ITEM_CREATED', 'ITEM_APPROVED')", OrderItem.class).setParameter("now", now).getResultList().stream()
                .map(i -> new String[]{i.getOrderId(), i.getOrderItemSeqId()}).toList());
        int done = 0, failed = 0;
        for (String[] k : due) {
            try {
                tx.executeWithoutResult(s -> orders.cancelItem(k[0], k[1], null, "Cancel date passed", "core_auto_cancel"));
                done++;
            } catch (RuntimeException e) {
                failed++;
                log.warn("Could not auto-cancel item {} of order {}: {}", k[1], k[0], e.getMessage());
            }
        }
        return done + " item(s) cancelled" + (failed > 0 ? ", " + failed + " failed" : "");
    }
}
