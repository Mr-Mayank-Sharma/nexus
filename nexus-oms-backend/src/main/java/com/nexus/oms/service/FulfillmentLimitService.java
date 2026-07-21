package com.nexus.oms.service;

import com.nexus.oms.entity.NxFulfillmentCapacityLog;
import com.nexus.oms.entity.NxFulfillmentLimit;
import com.nexus.oms.exception.BadRequestException;
import com.nexus.oms.exception.ResourceNotFoundException;
import com.nexus.oms.repository.FulfillmentCapacityLogRepository;
import com.nexus.oms.repository.FulfillmentLimitRepository;
import com.nexus.oms.security.TenantContext;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.time.DayOfWeek;
import java.time.LocalDate;
import java.time.temporal.TemporalAdjusters;
import java.util.*;

@Service
public class FulfillmentLimitService {

    private static final Logger log = LoggerFactory.getLogger(FulfillmentLimitService.class);

    private final FulfillmentLimitRepository fulfillmentLimitRepository;
    private final FulfillmentCapacityLogRepository capacityLogRepository;

    public FulfillmentLimitService(FulfillmentLimitRepository fulfillmentLimitRepository,
                                    FulfillmentCapacityLogRepository capacityLogRepository) {
        this.fulfillmentLimitRepository = fulfillmentLimitRepository;
        this.capacityLogRepository = capacityLogRepository;
    }

    @Transactional
    public NxFulfillmentLimit createLimit(NxFulfillmentLimit limit) {
        UUID tenantId = TenantContext.getCurrentTenantId();
        limit.setTenantId(tenantId);

        NxFulfillmentLimit existing = fulfillmentLimitRepository.findByTenantIdAndNodeId(tenantId, limit.getNodeId());
        if (existing != null) {
            throw new BadRequestException("Limit already exists for this node");
        }

        limit = fulfillmentLimitRepository.save(limit);
        log.info("Created fulfillment limit for node {}", limit.getNodeId());
        return limit;
    }

    @Transactional
    public NxFulfillmentLimit updateLimit(UUID id, NxFulfillmentLimit updates) {
        NxFulfillmentLimit limit = fulfillmentLimitRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("FulfillmentLimit", id));

        if (updates.getMaxOrdersPerDay() != null) limit.setMaxOrdersPerDay(updates.getMaxOrdersPerDay());
        if (updates.getMaxOrdersPerWeek() != null) limit.setMaxOrdersPerWeek(updates.getMaxOrdersPerWeek());
        if (updates.getMaxItemsPerDay() != null) limit.setMaxItemsPerDay(updates.getMaxItemsPerDay());
        if (updates.getAlertThreshold() != null) limit.setAlertThreshold(updates.getAlertThreshold());

        limit = fulfillmentLimitRepository.save(limit);
        log.info("Updated fulfillment limit {}", limit.getId());
        return limit;
    }

    public NxFulfillmentLimit getLimit(UUID nodeId) {
        NxFulfillmentLimit limit = fulfillmentLimitRepository.findByNodeId(nodeId);
        if (limit == null) {
            throw new ResourceNotFoundException("FulfillmentLimit for node", nodeId);
        }
        return limit;
    }

    public List<NxFulfillmentLimit> getLimits(UUID tenantId) {
        return fulfillmentLimitRepository.findByTenantId(tenantId);
    }

    public Map<String, Object> checkCapacity(UUID nodeId) {
        NxFulfillmentLimit limit = fulfillmentLimitRepository.findByNodeId(nodeId);

        Map<String, Object> result = new LinkedHashMap<>();
        result.put("nodeId", nodeId);

        if (limit == null) {
            result.put("hasLimit", false);
            result.put("fulfillmentEnabled", true);
            return result;
        }

        result.put("hasLimit", true);
        result.put("fulfillmentEnabled", limit.getFulfillmentEnabled());

        if (limit.getMaxOrdersPerDay() != null && limit.getMaxOrdersPerDay() > 0) {
            BigDecimal percentage = BigDecimal.valueOf(limit.getCurrentOrdersToday())
                    .divide(BigDecimal.valueOf(limit.getMaxOrdersPerDay()), 4, RoundingMode.HALF_UP)
                    .multiply(BigDecimal.valueOf(100));
            result.put("dailyOrdersUsed", limit.getCurrentOrdersToday());
            result.put("dailyOrdersMax", limit.getMaxOrdersPerDay());
            result.put("dailyOrdersPercentage", percentage.setScale(1, RoundingMode.HALF_UP));
            result.put("dailyOrdersAtLimit", limit.getCurrentOrdersToday() >= limit.getMaxOrdersPerDay());
            result.put("dailyOrdersAtThreshold", percentage.compareTo(limit.getAlertThreshold().multiply(BigDecimal.valueOf(100))) >= 0);
        }

        if (limit.getMaxOrdersPerWeek() != null && limit.getMaxOrdersPerWeek() > 0) {
            BigDecimal percentage = BigDecimal.valueOf(limit.getCurrentOrdersThisWeek())
                    .divide(BigDecimal.valueOf(limit.getMaxOrdersPerWeek()), 4, RoundingMode.HALF_UP)
                    .multiply(BigDecimal.valueOf(100));
            result.put("weeklyOrdersUsed", limit.getCurrentOrdersThisWeek());
            result.put("weeklyOrdersMax", limit.getMaxOrdersPerWeek());
            result.put("weeklyOrdersPercentage", percentage.setScale(1, RoundingMode.HALF_UP));
        }

        if (limit.getMaxItemsPerDay() != null && limit.getMaxItemsPerDay() > 0) {
            BigDecimal percentage = BigDecimal.valueOf(limit.getCurrentItemsToday())
                    .divide(BigDecimal.valueOf(limit.getMaxItemsPerDay()), 4, RoundingMode.HALF_UP)
                    .multiply(BigDecimal.valueOf(100));
            result.put("dailyItemsUsed", limit.getCurrentItemsToday());
            result.put("dailyItemsMax", limit.getMaxItemsPerDay());
            result.put("dailyItemsPercentage", percentage.setScale(1, RoundingMode.HALF_UP));
        }

        return result;
    }

    @Transactional
    public NxFulfillmentLimit incrementOrderCount(UUID nodeId, UUID orderId) {
        UUID tenantId = TenantContext.getCurrentTenantId();

        NxFulfillmentLimit limit = fulfillmentLimitRepository.findByTenantIdAndNodeId(tenantId, nodeId);
        if (limit == null) {
            limit = NxFulfillmentLimit.builder()
                    .tenantId(tenantId)
                    .nodeId(nodeId)
                    .fulfillmentEnabled(true)
                    .currentOrdersToday(0)
                    .currentOrdersThisWeek(0)
                    .currentItemsToday(0)
                    .build();
        }

        if (!limit.getFulfillmentEnabled()) {
            throw new BadRequestException("Fulfillment is disabled for this node");
        }

        int previousOrders = limit.getCurrentOrdersToday();

        if (limit.getMaxOrdersPerDay() != null && limit.getMaxOrdersPerDay() > 0) {
            if (limit.getCurrentOrdersToday() >= limit.getMaxOrdersPerDay()) {
                throw new BadRequestException("Node has reached maximum orders per day: " + limit.getMaxOrdersPerDay());
            }
        }

        limit.setCurrentOrdersToday(limit.getCurrentOrdersToday() + 1);
        limit.setCurrentOrdersThisWeek(limit.getCurrentOrdersThisWeek() + 1);
        limit = fulfillmentLimitRepository.save(limit);

        logCapacityChange(tenantId, nodeId, orderId, "ORDER_ASSIGNED", previousOrders, limit.getCurrentOrdersToday(), limit);

        return limit;
    }

    @Transactional
    public NxFulfillmentLimit decrementOrderCount(UUID nodeId, UUID orderId) {
        UUID tenantId = TenantContext.getCurrentTenantId();

        NxFulfillmentLimit limit = fulfillmentLimitRepository.findByTenantIdAndNodeId(tenantId, nodeId);
        if (limit == null) return null;

        int previousOrders = limit.getCurrentOrdersToday();

        if (limit.getCurrentOrdersToday() > 0) {
            limit.setCurrentOrdersToday(limit.getCurrentOrdersToday() - 1);
        }
        if (limit.getCurrentOrdersThisWeek() > 0) {
            limit.setCurrentOrdersThisWeek(limit.getCurrentOrdersThisWeek() - 1);
        }
        limit = fulfillmentLimitRepository.save(limit);

        logCapacityChange(tenantId, nodeId, orderId, "ORDER_REMOVED", previousOrders, limit.getCurrentOrdersToday(), limit);

        return limit;
    }

    @Transactional
    public NxFulfillmentLimit toggleFulfillment(UUID nodeId, boolean enabled) {
        UUID tenantId = TenantContext.getCurrentTenantId();

        NxFulfillmentLimit limit = fulfillmentLimitRepository.findByTenantIdAndNodeId(tenantId, nodeId);
        if (limit == null) {
            limit = NxFulfillmentLimit.builder()
                    .tenantId(tenantId)
                    .nodeId(nodeId)
                    .fulfillmentEnabled(enabled)
                    .currentOrdersToday(0)
                    .currentOrdersThisWeek(0)
                    .currentItemsToday(0)
                    .build();
        } else {
            limit.setFulfillmentEnabled(enabled);
        }

        limit = fulfillmentLimitRepository.save(limit);
        log.info("Fulfillment {} for node {}", enabled ? "enabled" : "disabled", nodeId);
        return limit;
    }

    @Transactional
    public void resetDailyCounts() {
        List<NxFulfillmentLimit> allLimits = fulfillmentLimitRepository.findAll();

        for (NxFulfillmentLimit limit : allLimits) {
            int previousOrders = limit.getCurrentOrdersToday();
            limit.setCurrentOrdersToday(0);
            limit.setCurrentItemsToday(0);
            limit.setLastResetAt(LocalDateTime.now());
            fulfillmentLimitRepository.save(limit);

            logCapacityChange(limit.getTenantId(), limit.getNodeId(), null,
                    "LIMIT_RESET", previousOrders, 0, limit);
        }

        log.info("Reset daily counts for {} fulfillment limits", allLimits.size());
    }

    @Transactional
    public void resetWeeklyCounts() {
        LocalDate now = LocalDate.now();
        if (now.getDayOfWeek() == DayOfWeek.MONDAY) {
            List<NxFulfillmentLimit> allLimits = fulfillmentLimitRepository.findAll();
            for (NxFulfillmentLimit limit : allLimits) {
                limit.setCurrentOrdersThisWeek(0);
                fulfillmentLimitRepository.save(limit);
            }
            log.info("Reset weekly counts for {} fulfillment limits", allLimits.size());
        }
    }

    public List<NxFulfillmentCapacityLog> getCapacityAlerts(UUID tenantId) {
        List<NxFulfillmentLimit> limits = fulfillmentLimitRepository.findByTenantId(tenantId);
        List<NxFulfillmentCapacityLog> alerts = new ArrayList<>();

        for (NxFulfillmentLimit limit : limits) {
            if (limit.getMaxOrdersPerDay() != null && limit.getMaxOrdersPerDay() > 0) {
                BigDecimal percentage = BigDecimal.valueOf(limit.getCurrentOrdersToday())
                        .divide(BigDecimal.valueOf(limit.getMaxOrdersPerDay()), 4, RoundingMode.HALF_UP);

                if (percentage.compareTo(limit.getAlertThreshold()) >= 0) {
                    NxFulfillmentCapacityLog alert = NxFulfillmentCapacityLog.builder()
                            .tenantId(tenantId)
                            .nodeId(limit.getNodeId())
                            .action("LIMIT_REACHED")
                            .ordersBefore(limit.getCurrentOrdersToday())
                            .ordersAfter(limit.getMaxOrdersPerDay())
                            .capacityPercentage(percentage.multiply(BigDecimal.valueOf(100)))
                            .build();
                    alerts.add(alert);
                }
            }
        }

        return alerts;
    }

    public List<NxFulfillmentCapacityLog> getCapacityHistory(UUID nodeId, LocalDateTime start, LocalDateTime end) {
        return capacityLogRepository.findByNodeIdAndCreatedAtBetween(nodeId, start, end);
    }

    private void logCapacityChange(UUID tenantId, UUID nodeId, UUID orderId, String action,
                                    int ordersBefore, int ordersAfter, NxFulfillmentLimit limit) {
        try {
            BigDecimal percentage = BigDecimal.ZERO;
            if (limit.getMaxOrdersPerDay() != null && limit.getMaxOrdersPerDay() > 0) {
                percentage = BigDecimal.valueOf(ordersAfter)
                        .divide(BigDecimal.valueOf(limit.getMaxOrdersPerDay()), 4, RoundingMode.HALF_UP)
                        .multiply(BigDecimal.valueOf(100));
            }

            NxFulfillmentCapacityLog logEntry = NxFulfillmentCapacityLog.builder()
                    .tenantId(tenantId)
                    .nodeId(nodeId)
                    .orderId(orderId)
                    .action(action)
                    .ordersBefore(ordersBefore)
                    .ordersAfter(ordersAfter)
                    .capacityPercentage(percentage)
                    .build();
            capacityLogRepository.save(logEntry);
        } catch (Exception e) {
            log.warn("Failed to log capacity change: {}", e.getMessage());
        }
    }
}
