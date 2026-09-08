package com.nexus.oms.service;

import com.nexus.oms.entity.NxBrokeringQueue;
import com.nexus.oms.entity.NxBrokeringRun;
import com.nexus.oms.entity.NxOrder;
import com.nexus.oms.exception.BadRequestException;
import com.nexus.oms.exception.ResourceNotFoundException;
import com.nexus.oms.repository.BrokeringQueueRepository;
import com.nexus.oms.repository.BrokeringRunRepository;
import com.nexus.oms.repository.OrderRepository;
import com.nexus.oms.security.TenantContext;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.TransactionStatus;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionTemplate;

import java.time.LocalDateTime;
import java.util.*;

@Service
public class BrokeringService {

    private static final Logger log = LoggerFactory.getLogger(BrokeringService.class);

    private final BrokeringQueueRepository brokeringQueueRepository;
    private final BrokeringRunRepository brokeringRunRepository;
    private final OrderRepository orderRepository;
    private final OrderRoutingService orderRoutingService;
    private final TransactionTemplate transactionTemplate;

    public BrokeringService(BrokeringQueueRepository brokeringQueueRepository,
                            BrokeringRunRepository brokeringRunRepository,
                            OrderRepository orderRepository,
                            OrderRoutingService orderRoutingService,
                            PlatformTransactionManager transactionManager) {
        this.brokeringQueueRepository = brokeringQueueRepository;
        this.brokeringRunRepository = brokeringRunRepository;
        this.orderRepository = orderRepository;
        this.orderRoutingService = orderRoutingService;
        this.transactionTemplate = new TransactionTemplate(transactionManager);
    }

    @Transactional
    public NxBrokeringQueue enqueueOrder(UUID orderId, String priority) {
        UUID tenantId = TenantContext.getCurrentTenantId();

        NxOrder order = orderRepository.findById(orderId)
                .orElseThrow(() -> new ResourceNotFoundException("Order", orderId));

        if (!"PENDING".equals(order.getStatus()) && !"CONFIRMED".equals(order.getStatus())) {
            throw new BadRequestException("Order must be PENDING or CONFIRMED to enqueue for brokering");
        }

        List<NxBrokeringQueue> existing = brokeringQueueRepository.findByOrderId(orderId);
        if (!existing.isEmpty() && existing.stream().anyMatch(q -> "WAITING".equals(q.getStatus()) || "PROCESSING".equals(q.getStatus()))) {
            throw new BadRequestException("Order already in brokering queue");
        }

        NxBrokeringQueue queueEntry = NxBrokeringQueue.builder()
                .tenantId(tenantId)
                .orderId(orderId)
                .priority(priority != null ? priority : "NORMAL")
                .status("WAITING")
                .attempts(0)
                .maxAttempts(3)
                .nextRunAt(LocalDateTime.now().plusMinutes(5))
                .enteredAt(LocalDateTime.now())
                .build();

        queueEntry = brokeringQueueRepository.save(queueEntry);
        log.info("Order {} enqueued for brokering with priority {}", orderId, queueEntry.getPriority());
        return queueEntry;
    }

    public NxBrokeringRun processBrokeringQueue() {
        UUID tenantId = TenantContext.getCurrentTenantId();
        long startTime = System.currentTimeMillis();

        // Phase 1: create run record + fetch waiting orders (own transaction)
        RunContext ctx = transactionTemplate.execute(status -> {
            NxBrokeringRun run = NxBrokeringRun.builder()
                    .tenantId(tenantId)
                    .runType("SCHEDULED")
                    .startedAt(LocalDateTime.now())
                    .status("RUNNING")
                    .build();
            run = brokeringRunRepository.save(run);

            List<NxBrokeringQueue> waitingOrders = brokeringQueueRepository
                    .findByTenantIdAndStatusAndNextRunAtBefore(tenantId, "WAITING", LocalDateTime.now());

            waitingOrders.sort(Comparator.comparing(NxBrokeringQueue::getPriority).reversed());
            return new RunContext(run, waitingOrders);
        });

        int processed = 0;
        int allocated = 0;
        int failed = 0;

        // Phase 2: process each order in its own transaction. Previously the whole run was one
        // @Transactional method, so a single order's exception marked the transaction rollback-only
        // and rolled back every other order's successful allocation.
        for (NxBrokeringQueue queueEntry : ctx.waitingOrders) {
            String outcome = transactionTemplate.execute(status -> processQueueEntry(queueEntry.getId(), 5));
            switch (outcome == null ? "FAILED" : outcome) {
                case "ALLOCATED" -> allocated++;
                case "FAILED" -> failed++;
                default -> { /* RETRY / SKIPPED — still counted as processed */ }
            }
            processed++;
        }

        // Phase 3: complete the run record (own transaction)
        NxBrokeringRun run = ctx.run;
        int finalProcessed = processed;
        int finalAllocated = allocated;
        int finalFailed = failed;
        transactionTemplate.executeWithoutResult(status -> {
            long executionTime = System.currentTimeMillis() - startTime;
            run.setCompletedAt(LocalDateTime.now());
            run.setOrdersProcessed(finalProcessed);
            run.setOrdersAllocated(finalAllocated);
            run.setOrdersFailed(finalFailed);
            run.setExecutionTimeMs((int) executionTime);
            run.setStatus("COMPLETED");
            brokeringRunRepository.save(run);
        });

        log.info("Brokering run {} completed: {} processed, {} allocated, {} failed in {}ms",
                run.getId(), processed, allocated, failed, System.currentTimeMillis() - startTime);
        return run;
    }

    public NxBrokeringRun processPriorityQueue() {
        UUID tenantId = TenantContext.getCurrentTenantId();
        long startTime = System.currentTimeMillis();

        // Phase 1: create run record + fetch priority orders (own transaction)
        RunContext ctx = transactionTemplate.execute(status -> {
            NxBrokeringRun run = NxBrokeringRun.builder()
                    .tenantId(tenantId)
                    .runType("PRIORITY")
                    .startedAt(LocalDateTime.now())
                    .status("RUNNING")
                    .build();
            run = brokeringRunRepository.save(run);

            List<NxBrokeringQueue> priorityOrders = brokeringQueueRepository
                    .findByTenantIdAndStatusAndPriorityIn(tenantId, "WAITING", List.of("HIGH", "URGENT"));
            return new RunContext(run, priorityOrders);
        });

        int processed = 0;
        int allocated = 0;
        int failed = 0;

        // Phase 2: process each order in its own transaction (see processBrokeringQueue)
        for (NxBrokeringQueue queueEntry : ctx.waitingOrders) {
            String outcome = transactionTemplate.execute(status -> processQueueEntry(queueEntry.getId(), 2));
            switch (outcome == null ? "FAILED" : outcome) {
                case "ALLOCATED" -> allocated++;
                case "FAILED" -> failed++;
                default -> { /* RETRY / SKIPPED — still counted as processed */ }
            }
            processed++;
        }

        // Phase 3: complete the run record (own transaction)
        NxBrokeringRun run = ctx.run;
        int finalProcessed = processed;
        int finalAllocated = allocated;
        int finalFailed = failed;
        transactionTemplate.executeWithoutResult(status -> {
            long executionTime = System.currentTimeMillis() - startTime;
            run.setCompletedAt(LocalDateTime.now());
            run.setOrdersProcessed(finalProcessed);
            run.setOrdersAllocated(finalAllocated);
            run.setOrdersFailed(finalFailed);
            run.setExecutionTimeMs((int) executionTime);
            run.setStatus("COMPLETED");
            brokeringRunRepository.save(run);
        });

        log.info("Priority brokering run {} completed: {} processed, {} allocated, {} failed",
                run.getId(), processed, allocated, failed);
        return run;
    }

    public NxBrokeringRun manualBrokeringRun(List<UUID> orderIds) {
        UUID tenantId = TenantContext.getCurrentTenantId();
        long startTime = System.currentTimeMillis();

        // Phase 1: create run record (own transaction)
        NxBrokeringRun run = transactionTemplate.execute(status -> {
            NxBrokeringRun r = NxBrokeringRun.builder()
                    .tenantId(tenantId)
                    .runType("MANUAL")
                    .startedAt(LocalDateTime.now())
                    .status("RUNNING")
                    .triggeredBy(TenantContext.getCurrentUserId())
                    .build();
            return brokeringRunRepository.save(r);
        });

        int processed = 0;
        int allocated = 0;
        int failed = 0;

        // Phase 2: allocate each order in its own transaction (see processBrokeringQueue)
        for (UUID orderId : orderIds) {
            String outcome = transactionTemplate.execute(status -> {
                try {
                    var allocationRequest = new com.nexus.oms.dto.AllocationRequest();
                    allocationRequest.setOrderId(orderId);
                    allocationRequest.setDryRun(false);

                    var result = orderRoutingService.allocateOrder(allocationRequest);
                    return "ALLOCATED".equals(result.getStatus()) ? "ALLOCATED" : "FAILED";
                } catch (Exception e) {
                    log.error("Manual brokering failed for order {}: {}", orderId, e.getMessage());
                    return "FAILED";
                }
            });
            if ("ALLOCATED".equals(outcome)) {
                allocated++;
            } else {
                failed++;
            }
            processed++;
        }

        // Phase 3: complete the run record (own transaction)
        NxBrokeringRun finalRun = run;
        int finalProcessed = processed;
        int finalAllocated = allocated;
        int finalFailed = failed;
        transactionTemplate.executeWithoutResult(status -> {
            long executionTime = System.currentTimeMillis() - startTime;
            finalRun.setCompletedAt(LocalDateTime.now());
            finalRun.setOrdersProcessed(finalProcessed);
            finalRun.setOrdersAllocated(finalAllocated);
            finalRun.setOrdersFailed(finalFailed);
            finalRun.setExecutionTimeMs((int) executionTime);
            finalRun.setStatus("COMPLETED");
            brokeringRunRepository.save(finalRun);
        });

        return finalRun;
    }

    /**
     * Processes a single queue entry inside the caller's transaction. Re-fetches the entry by ID
     * so it is a managed entity within this transaction. Returns the outcome:
     * "ALLOCATED", "FAILED", "RETRY" (backoff), or "SKIPPED" (entry no longer exists).
     */
    private String processQueueEntry(UUID queueEntryId, int retryDelayMinutes) {
        NxBrokeringQueue queueEntry = brokeringQueueRepository.findById(queueEntryId).orElse(null);
        if (queueEntry == null) {
            return "SKIPPED";
        }

        // Skip orders already allocated elsewhere (e.g. manual allocate) — prevents double allocation
        NxOrder queuedOrder = orderRepository.findById(queueEntry.getOrderId()).orElse(null);
        if (queuedOrder != null && ("ALLOCATED".equals(queuedOrder.getStatus()) || queuedOrder.getAllocatedNode() != null)) {
            queueEntry.setStatus("ALLOCATED");
            queueEntry.setExitedAt(LocalDateTime.now());
            if (queuedOrder.getAllocatedNode() != null) {
                queueEntry.setAllocatedNodeId(queuedOrder.getAllocatedNode());
            }
            brokeringQueueRepository.save(queueEntry);
            return "ALLOCATED";
        }

        try {
            queueEntry.setStatus("PROCESSING");
            queueEntry.setAttempts(queueEntry.getAttempts() + 1);
            queueEntry.setLastAttemptAt(LocalDateTime.now());
            brokeringQueueRepository.save(queueEntry);

            var allocationRequest = new com.nexus.oms.dto.AllocationRequest();
            allocationRequest.setOrderId(queueEntry.getOrderId());
            allocationRequest.setDryRun(false);

            var result = orderRoutingService.allocateOrder(allocationRequest);

            if ("ALLOCATED".equals(result.getStatus())) {
                queueEntry.setStatus("ALLOCATED");
                queueEntry.setExitedAt(LocalDateTime.now());
                if (!result.getAllocations().isEmpty()) {
                    queueEntry.setAllocatedNodeId(result.getAllocations().get(0).getNodeId());
                }
                brokeringQueueRepository.save(queueEntry);
                return "ALLOCATED";
            }

            if (queueEntry.getAttempts() >= queueEntry.getMaxAttempts()) {
                queueEntry.setStatus("FAILED");
                queueEntry.setFailureReason("Max attempts exceeded");
                queueEntry.setExitedAt(LocalDateTime.now());
                brokeringQueueRepository.save(queueEntry);
                return "FAILED";
            }

            queueEntry.setStatus("WAITING");
            queueEntry.setNextRunAt(LocalDateTime.now().plusMinutes((long) retryDelayMinutes * queueEntry.getAttempts()));
            brokeringQueueRepository.save(queueEntry);
            return "RETRY";
        } catch (Exception e) {
            log.error("Brokering failed for order {}: {}", queueEntry.getOrderId(), e.getMessage());
            queueEntry.setStatus("FAILED");
            queueEntry.setFailureReason(e.getMessage());
            queueEntry.setExitedAt(LocalDateTime.now());
            brokeringQueueRepository.save(queueEntry);
            return "FAILED";
        }
    }

    private static class RunContext {
        final NxBrokeringRun run;
        final List<NxBrokeringQueue> waitingOrders;

        RunContext(NxBrokeringRun run, List<NxBrokeringQueue> waitingOrders) {
            this.run = run;
            this.waitingOrders = waitingOrders;
        }
    }

    public List<NxBrokeringQueue> getQueue(UUID tenantId, String status) {
        if (status != null && !status.isBlank()) {
            return brokeringQueueRepository.findByTenantIdAndStatus(tenantId, status.toUpperCase());
        }
        return brokeringQueueRepository.findByTenantId(tenantId);
    }

    public Map<String, Object> getQueueStats(UUID tenantId) {
        Map<String, Object> stats = new LinkedHashMap<>();
        stats.put("waiting", brokeringQueueRepository.countByTenantIdAndStatus(tenantId, "WAITING"));
        stats.put("processing", brokeringQueueRepository.countByTenantIdAndStatus(tenantId, "PROCESSING"));
        stats.put("allocated", brokeringQueueRepository.countByTenantIdAndStatus(tenantId, "ALLOCATED"));
        stats.put("failed", brokeringQueueRepository.countByTenantIdAndStatus(tenantId, "FAILED"));
        stats.put("expired", brokeringQueueRepository.countByTenantIdAndStatus(tenantId, "EXPIRED"));
        return stats;
    }

    public List<NxBrokeringRun> getRunHistory(UUID tenantId) {
        return brokeringRunRepository.findByTenantIdOrderByStartedAtDesc(tenantId);
    }

    public NxBrokeringRun getRun(UUID id) {
        return brokeringRunRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("BrokeringRun", id));
    }

    @Transactional
    public void removeFromQueue(UUID id) {
        NxBrokeringQueue queueEntry = brokeringQueueRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("BrokeringQueue", id));
        queueEntry.setStatus("EXPIRED");
        queueEntry.setExitedAt(LocalDateTime.now());
        brokeringQueueRepository.save(queueEntry);
    }

    @Transactional
    public List<NxBrokeringQueue> expireStaleOrders() {
        UUID tenantId = TenantContext.getCurrentTenantId();
        LocalDateTime cutoff = LocalDateTime.now().minusHours(24);

        List<NxBrokeringQueue> staleOrders = brokeringQueueRepository
                .findByTenantIdAndStatusAndNextRunAtBefore(tenantId, "WAITING", cutoff);

        List<NxBrokeringQueue> expired = new ArrayList<>();
        for (NxBrokeringQueue order : staleOrders) {
            order.setStatus("EXPIRED");
            order.setFailureReason("Expired: in queue for more than 24 hours");
            order.setExitedAt(LocalDateTime.now());
            brokeringQueueRepository.save(order);
            expired.add(order);
        }

        log.info("Expired {} stale brokering orders", expired.size());
        return expired;
    }
}
