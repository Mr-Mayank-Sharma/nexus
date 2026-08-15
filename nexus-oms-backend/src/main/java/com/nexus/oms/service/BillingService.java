package com.nexus.oms.service;

import com.nexus.oms.entity.*;
import com.nexus.oms.exception.BadRequestException;
import com.nexus.oms.exception.ResourceNotFoundException;
import com.nexus.oms.repository.*;
import com.nexus.oms.security.TenantContext;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

@Service
public class BillingService {

    private final BillingStatementRepository statementRepository;
    private final BillingStatementLineRepository lineRepository;
    private final RateCardService rateCardService;
    private final OrderRepository orderRepository;
    private final OrderItemRepository orderItemRepository;
    private final PicklistItemRepository picklistItemRepository;
    private final CustomerRepository customerRepository;

    public BillingService(BillingStatementRepository statementRepository,
                          BillingStatementLineRepository lineRepository,
                          RateCardService rateCardService,
                          OrderRepository orderRepository,
                          OrderItemRepository orderItemRepository,
                          PicklistItemRepository picklistItemRepository,
                          CustomerRepository customerRepository) {
        this.statementRepository = statementRepository;
        this.lineRepository = lineRepository;
        this.rateCardService = rateCardService;
        this.orderRepository = orderRepository;
        this.orderItemRepository = orderItemRepository;
        this.picklistItemRepository = picklistItemRepository;
        this.customerRepository = customerRepository;
    }

    @Transactional
    public NxBillingStatement generateStatement(UUID clientId, LocalDate periodStart, LocalDate periodEnd) {
        if (periodStart == null || periodEnd == null || periodEnd.isBefore(periodStart)) {
            throw new BadRequestException("Valid periodStart and periodEnd are required");
        }
        UUID tenantId = TenantContext.getCurrentTenantId();
        NxCustomer client = customerRepository.findById(clientId)
                .filter(c -> c.getTenantId().equals(tenantId))
                .orElseThrow(() -> new ResourceNotFoundException("Customer", clientId));

        NxRateCard rateCard = rateCardService.resolveRateCard(tenantId, clientId);

        statementRepository.findFirstByTenantIdAndClientIdAndPeriodStartAndPeriodEnd(tenantId, clientId, periodStart, periodEnd)
                .ifPresent(s -> { throw new BadRequestException("Statement already exists for this client and period"); });

        LocalDateTime from = periodStart.atStartOfDay();
        LocalDateTime to = periodEnd.plusDays(1).atStartOfDay();

        List<NxOrder> orders = orderRepository.findByTenantIdAndCustomerIdAndCreatedAtBetween(tenantId, clientId, from, to)
                .stream()
                .filter(o -> !o.getStatus().toUpperCase().contains("CANCEL") && !o.getStatus().toUpperCase().contains("FAILED"))
                .toList();

        List<UUID> orderIds = orders.stream().map(NxOrder::getId).toList();
        int lineCount = 0;
        int unitsHandled = 0;
        if (!orderIds.isEmpty()) {
            for (NxOrderItem item : orderItemRepository.findByOrderIdIn(orderIds)) {
                lineCount++;
                unitsHandled += item.getQuantity() == null ? 0 : item.getQuantity();
            }
        }
        int pickedLines = orderIds.isEmpty() ? 0 : picklistItemRepository.findByOrderIdIn(orderIds).size();

        List<NxBillingStatementLine> lines = new ArrayList<>();
        addLine(lines, "PER_ORDER", "Order fulfillment fees", orders.size(), rateCard.getPerOrderFee());
        addLine(lines, "PER_LINE", "Line item handling fees", lineCount, rateCard.getPerLineFee());
        addLine(lines, "PICKING", "Picking fees", pickedLines, rateCard.getPickingFeePerLine());
        addLine(lines, "STORAGE", "Storage / units handled", unitsHandled, rateCard.getStorageFeePerUnit());

        BigDecimal subtotal = lines.stream()
                .map(NxBillingStatementLine::getAmount)
                .reduce(BigDecimal.ZERO, BigDecimal::add);

        NxBillingStatement statement = NxBillingStatement.builder()
                .tenantId(tenantId)
                .clientId(clientId)
                .clientName(client.getName())
                .currency(rateCard.getCurrency())
                .periodStart(periodStart)
                .periodEnd(periodEnd)
                .status("DRAFT")
                .orderCount(orders.size())
                .lineCount(lineCount)
                .pickedLines(pickedLines)
                .unitsHandled(unitsHandled)
                .subtotal(subtotal)
                .total(subtotal)
                .notes("Generated from rate card: " + (rateCard.getClientName() != null ? rateCard.getClientName() : "Default"))
                .build();

        statement = statementRepository.save(statement);
        for (NxBillingStatementLine line : lines) {
            line.setStatementId(statement.getId());
            lineRepository.save(line);
        }
        return statement;
    }

    private void addLine(List<NxBillingStatementLine> lines, String type, String description, int quantity, BigDecimal unitPrice) {
        if (quantity <= 0 || unitPrice == null || unitPrice.signum() == 0) return;
        lines.add(NxBillingStatementLine.builder()
                .rateType(type)
                .description(description)
                .quantity(quantity)
                .unitPrice(unitPrice)
                .amount(unitPrice.multiply(BigDecimal.valueOf(quantity)))
                .build());
    }

    public List<NxBillingStatement> getStatements(UUID clientId) {
        UUID tenantId = TenantContext.getCurrentTenantId();
        if (clientId != null) return statementRepository.findByTenantIdAndClientId(tenantId, clientId);
        return statementRepository.findByTenantId(tenantId);
    }

    public NxBillingStatement getStatement(UUID id) {
        UUID tenantId = TenantContext.getCurrentTenantId();
        return statementRepository.findById(id)
                .filter(s -> s.getTenantId().equals(tenantId))
                .orElseThrow(() -> new ResourceNotFoundException("BillingStatement", id));
    }

    public List<NxBillingStatementLine> getStatementLines(UUID id) {
        getStatement(id);
        return lineRepository.findByStatementId(id);
    }

    @Transactional
    public NxBillingStatement updateStatus(UUID id, String status) {
        if (!List.of("DRAFT", "ISSUED", "PAID").contains(status)) {
            throw new BadRequestException("Invalid status: " + status);
        }
        NxBillingStatement statement = getStatement(id);
        statement.setStatus(status);
        return statementRepository.save(statement);
    }
}
