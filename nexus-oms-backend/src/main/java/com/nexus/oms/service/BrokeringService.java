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
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.*;

@Service
public class BrokeringService {

    private static final Logger log = LoggerFactory.getLogger(BrokeringService.class);

    private final BrokeringQueueRepository brokeringQueueRepository;
    private final BrokeringRunRepository brokeringRunRepository;
    private final OrderRepository orderRepository;
    private final OrderRoutingService orderRoutingService;

    public BrokeringService(BrokeringQueueRepository brokeringQueueRepository,
                            BrokeringRunRepository brokeringRunRepository,
                            OrderRepository orderRepository,
                            OrderRoutingService orderRoutingService) {
        this.brokeringQueueRepository = brokeringQueueRepository;
        this.brokeringRunRepository = brokeringRunRepository;
        this.orderRepository = orderRepository;
        this.orderRoutingService = orderRoutingService;
    }

    @Transactional
    public NxBrokeringQueue enqueueOrder(UUID orderId, String priority) {
        UUID tenantId = TenantContext.getCurrentTenantId();

        NxOrder order = orderRepository.findById(orderId)
                .orElseThrow(() -> new ResourceNotFoundException("Order", orderId));

        if (!"PENDING".equals(order.getStatus())) {
            throw new BadRequestException("Order must be PENDING to enqueue for brokering");
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

    @Transactional
    public NxBrokeringRun processBrokeringQueue() {
        UUID tenantId = TenantContext.getCurrentTenantId();
        long startTime = System.currentTimeMillis();

        NxBrokeringRun run = NxBrokeringRun.builder()
                .tenantId(tenantId)
                .runType("SCHEDULED")
                .startedAt(LocalDateTime.now())
                .status("RUNNING")
                .build();
        run = brokeringRunRepository.save(run);

        List<NxBrokeringQueue> waitingOrders = brokeringQueueRepository
                .findByStatusAndNextRunAtBefore("WAITING", LocalDateTime.now());

        waitingOrders.sort(Comparator.comparing(NxBrokeringQueue::getPriority).reversed());

        int processed = 0;
        int allocated = 0;
        int failed = 0;

        for (NxBrokeringQueue queueEntry : waitingOrders) {
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
                    allocated++;
                } else {
                    if (queueEntry.getAttempts() >= queueEntry.getMaxAttempts()) {
                        queueEntry.setStatus("FAILED");
                        queueEntry.setFailureReason("Max attempts exceeded");
                        queueEntry.setExitedAt(LocalDateTime.now());
                        failed++;
                    } else {
                        queueEntry.setStatus("WAITING");
                        queueEntry.setNextRunAt(LocalDateTime.now().plusMinutes(5 * queueEntry.getAttempts()));
                    }
                }
                brokeringQueueRepository.save(queueEntry);
                processed++;
            } catch (Exception e) {
                log.error("Brokering failed for order {}: {}", queueEntry.getOrderId(), e.getMessage());
                queueEntry.setStatus("FAILED");
                queueEntry.setFailureReason(e.getMessage());
                queueEntry.setExitedAt(LocalDateTime.now());
                brokeringQueueRepository.save(queueEntry);
                failed++;
                processed++;
            }
        }

        long executionTime = System.currentTimeMillis() - startTime;
        run.setCompletedAt(LocalDateTime.now());
        run.setOrdersProcessed(processed);
        run.setOrdersAllocated(allocated);
        run.setOrdersFailed(failed);
        run.setExecutionTimeMs((int) executionTime);
        run.setStatus("COMPLETED");
        brokeringRunRepository.save(run);

        log.info("Brokering run {} completed: {} processed, {} allocated, {} failed in {}ms",
                run.getId(), processed, allocated, failed, executionTime);
        return run;
    }

    @Transactional
    public NxBrokeringRun processPriorityQueue() {
        UUID tenantId = TenantContext.getCurrentTenantId();
        long startTime = System.currentTimeMillis();

        NxBrokeringRun run = NxBrokeringRun.builder()
                .tenantId(tenantId)
                .runType("PRIORITY")
                .startedAt(LocalDateTime.now())
                .status("RUNNING")
                .build();
        run = brokeringRunRepository.save(run);

        List<NxBrokeringQueue> priorityOrders = brokeringQueueRepository
                .findByStatusAndPriorityIn("WAITING", List.of("HIGH", "URGENT"));

        int processed = 0;
        int allocated = 0;
        int failed = 0;

        for (NxBrokeringQueue queueEntry : priorityOrders) {
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
                    allocated++;
                } else {
                    if (queueEntry.getAttempts() >= queueEntry.getMaxAttempts()) {
                        queueEntry.setStatus("FAILED");
                        queueEntry.setFailureReason("Max attempts exceeded");
                        queueEntry.setExitedAt(LocalDateTime.now());
                        failed++;
                    } else {
                        queueEntry.setStatus("WAITING");
                        queueEntry.setNextRunAt(LocalDateTime.now().plusMinutes(2));
                    }
                }
                brokeringQueueRepository.save(queueEntry);
                processed++;
            } catch (Exception e) {
                log.error("Priority brokering failed for order {}: {}", queueEntry.getOrderId(), e.getMessage());
                queueEntry.setStatus("FAILED");
                queueEntry.setFailureReason(e.getMessage());
                queueEntry.setExitedAt(LocalDateTime.now());
                brokeringQueueRepository.save(queueEntry);
                failed++;
                processed++;
            }
        }

        long executionTime = System.currentTimeMillis() - startTime;
        run.setCompletedAt(LocalDateTime.now());
        run.setOrdersProcessed(processed);
        run.setOrdersAllocated(allocated);
        run.setOrdersFailed(failed);
        run.setExecutionTimeMs((int) executionTime);
        run.setStatus("COMPLETED");
        brokeringRunRepository.save(run);

        log.info("Priority brokering run {} completed: {} processed, {} allocated, {} failed",
                run.getId(), processed, allocated, failed);
        return run;
    }

    @Transactional
    public NxBrokeringRun manualBrokeringRun(List<UUID> orderIds) {
        UUID tenantId = TenantContext.getCurrentTenantId();
        long startTime = System.currentTimeMillis();

        NxBrokeringRun run = NxBrokeringRun.builder()
                .tenantId(tenantId)
                .runType("MANUAL")
                .startedAt(LocalDateTime.now())
                .status("RUNNING")
                .triggeredBy(TenantContext.getCurrentUserId())
                .build();
        run = brokeringRunRepository.save(run);

        int processed = 0;
        int allocated = 0;
        int failed = 0;

        for (UUID orderId : orderIds) {
            try {
                var allocationRequest = new com.nexus.oms.dto.AllocationRequest();
                allocationRequest.setOrderId(orderId);
                allocationRequest.setDryRun(false);

                var result = orderRoutingService.allocateOrder(allocationRequest);

                if ("ALLOCATED".equals(result.getStatus())) {
                    allocated++;
                } else {
                    failed++;
                }
                processed++;
            } catch (Exception e) {
                log.error("Manual brokering failed for order {}: {}", orderId, e.getMessage());
                failed++;
                processed++;
            }
        }

        long executionTime = System.currentTimeMillis() - startTime;
        run.setCompletedAt(LocalDateTime.now());
        run.setOrdersProcessed(processed);
        run.setOrdersAllocated(allocated);
        run.setOrdersFailed(failed);
        run.setExecutionTimeMs((int) executionTime);
        run.setStatus("COMPLETED");
        brokeringRunRepository.save(run);

        return run;
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
                .findByStatusAndNextRunAtBefore("WAITING", cutoff);

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
