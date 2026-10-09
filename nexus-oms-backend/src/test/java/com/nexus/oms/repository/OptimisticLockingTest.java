package com.nexus.oms.repository;

import com.nexus.oms.entity.NxOrder;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest;
import org.springframework.boot.test.autoconfigure.jdbc.AutoConfigureTestDatabase;
import org.springframework.orm.ObjectOptimisticLockingFailureException;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionTemplate;

import java.math.BigDecimal;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;

/** Two writers that read the same order version: the second commit must be rejected. */
@DataJpaTest
@ActiveProfiles("test")
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.NONE)
@Transactional(propagation = Propagation.NOT_SUPPORTED)
class OptimisticLockingTest {

    @Autowired OrderRepository orderRepository;
    @Autowired PlatformTransactionManager txManager;

    @Test
    void staleWriteIsRejected() {
        TransactionTemplate tx = new TransactionTemplate(txManager);
        UUID id = tx.execute(s -> orderRepository.save(NxOrder.builder()
                .tenantId(UUID.randomUUID()).channel("SHOPIFY").status("PENDING")
                .currency("USD").total(new BigDecimal("10.00")).build()).getId());

        NxOrder readByUserA = tx.execute(s -> orderRepository.findById(id).orElseThrow());
        NxOrder readByUserB = tx.execute(s -> orderRepository.findById(id).orElseThrow());
        assertEquals(0L, readByUserA.getVersion());

        readByUserA.setStatus("CANCELLED");
        tx.executeWithoutResult(s -> orderRepository.save(readByUserA));

        readByUserB.setStatus("ALLOCATED");
        assertThrows(ObjectOptimisticLockingFailureException.class,
                () -> tx.executeWithoutResult(s -> orderRepository.save(readByUserB)));

        NxOrder stored = tx.execute(s -> orderRepository.findById(id).orElseThrow());
        assertEquals("CANCELLED", stored.getStatus(), "the first write survives; the stale one did not overwrite it");
        assertEquals(1L, stored.getVersion());
    }
}
