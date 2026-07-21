package com.nexus.oms.service;

import com.nexus.oms.entity.NxFreightAuditLog;
import com.nexus.oms.entity.NxFreightInvoice;
import com.nexus.oms.entity.NxFreightInvoiceLine;
import com.nexus.oms.exception.BadRequestException;
import com.nexus.oms.exception.ResourceNotFoundException;
import com.nexus.oms.repository.NxFreightAuditLogRepository;
import com.nexus.oms.repository.NxFreightInvoiceLineRepository;
import com.nexus.oms.repository.NxFreightInvoiceRepository;
import com.nexus.oms.security.TenantContext;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDateTime;
import java.util.*;

@Service
public class FreightAuditService {

    private final NxFreightInvoiceRepository invoiceRepository;
    private final NxFreightInvoiceLineRepository invoiceLineRepository;
    private final NxFreightAuditLogRepository auditLogRepository;

    public FreightAuditService(NxFreightInvoiceRepository invoiceRepository,
                                NxFreightInvoiceLineRepository invoiceLineRepository,
                                NxFreightAuditLogRepository auditLogRepository) {
        this.invoiceRepository = invoiceRepository;
        this.invoiceLineRepository = invoiceLineRepository;
        this.auditLogRepository = auditLogRepository;
    }

    // ---- Invoice CRUD ----

    public List<NxFreightInvoice> getInvoices(String status) {
        UUID tenantId = TenantContext.getCurrentTenantId();
        if (status != null && !status.isBlank()) {
            return invoiceRepository.findByTenantIdAndStatus(tenantId, status);
        }
        return invoiceRepository.findByTenantId(tenantId);
    }

    public NxFreightInvoice getInvoice(UUID id) {
        return invoiceRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("FreightInvoice", id));
    }

    public List<NxFreightInvoiceLine> getInvoiceLines(UUID invoiceId) {
        return invoiceLineRepository.findByInvoiceId(invoiceId);
    }

    public List<NxFreightAuditLog> getAuditLogs(UUID invoiceId) {
        return auditLogRepository.findByInvoiceIdOrderByCreatedAtDesc(invoiceId);
    }

    @Transactional
    public NxFreightInvoice createInvoice(NxFreightInvoice invoice) {
        invoice.setTenantId(TenantContext.getCurrentTenantId());
        if (invoice.getStatus() == null) invoice.setStatus("DRAFT");
        if (invoice.getCurrency() == null) invoice.setCurrency("USD");
        if (invoice.getMatchedPercentage() == null) invoice.setMatchedPercentage(BigDecimal.ZERO);
        return invoiceRepository.save(invoice);
    }

    @Transactional
    public NxFreightInvoiceLine addInvoiceLine(UUID invoiceId, NxFreightInvoiceLine line) {
        NxFreightInvoice invoice = getInvoice(invoiceId);
        line.setTenantId(invoice.getTenantId());
        line.setInvoiceId(invoiceId);

        if (line.getLineNumber() == null) {
            List<NxFreightInvoiceLine> existing = invoiceLineRepository.findByInvoiceId(invoiceId);
            line.setLineNumber(existing.size() + 1);
        }

        // Calculate variance
        if (line.getBilledAmount() != null && line.getExpectedAmount() != null) {
            line.setVarianceAmount(line.getBilledAmount().subtract(line.getExpectedAmount()));
        }

        if (line.getStatus() == null) line.setStatus("PENDING");
        return invoiceLineRepository.save(line);
    }

    // ---- Audit / Match ----

    @Transactional
    public Map<String, Object> performAuditMatch(UUID invoiceId) {
        NxFreightInvoice invoice = getInvoice(invoiceId);
        List<NxFreightInvoiceLine> lines = invoiceLineRepository.findByInvoiceId(invoiceId);

        int totalLines = lines.size();
        int matchedLines = 0;
        BigDecimal totalBilled = BigDecimal.ZERO;
        BigDecimal totalExpected = BigDecimal.ZERO;
        BigDecimal totalVariance = BigDecimal.ZERO;

        for (NxFreightInvoiceLine line : lines) {
            totalBilled = totalBilled.add(line.getBilledAmount() != null ? line.getBilledAmount() : BigDecimal.ZERO);
            totalExpected = totalExpected.add(line.getExpectedAmount() != null ? line.getExpectedAmount() : BigDecimal.ZERO);

            if (line.getVarianceAmount() == null || line.getVarianceAmount().compareTo(BigDecimal.ZERO) == 0) {
                line.setStatus("MATCHED");
                matchedLines++;
            } else if (line.getVarianceAmount().abs().compareTo(new BigDecimal("5.00")) <= 0) {
                line.setStatus("MATCHED");
                matchedLines++;
            } else {
                line.setStatus("VARIANCE");
            }
            invoiceLineRepository.save(line);
        }

        totalVariance = totalBilled.subtract(totalExpected);
        BigDecimal matchPct = totalLines > 0
                ? BigDecimal.valueOf(matchedLines).multiply(BigDecimal.valueOf(100))
                        .divide(BigDecimal.valueOf(totalLines), 2, RoundingMode.HALF_UP)
                : BigDecimal.ZERO;

        invoice.setTotalAmount(totalBilled);
        invoice.setTotalNet(totalBilled.subtract(
                invoice.getTotalDiscount() != null ? invoice.getTotalDiscount() : BigDecimal.ZERO));
        invoice.setMatchedPercentage(matchPct);

        if (matchPct.compareTo(new BigDecimal("95")) >= 0) {
            invoice.setStatus("AUDITED");
        } else {
            invoice.setStatus("REVIEW_REQUIRED");
        }
        invoiceRepository.save(invoice);

        // Log the audit action
        createAuditLog(invoiceId, "AUDIT_MATCH", "system", null, invoice.getStatus(),
                String.format("Matched %d/%d lines (%s%%). Variance: $%s",
                        matchedLines, totalLines, matchPct, totalVariance));

        Map<String, Object> result = new LinkedHashMap<>();
        result.put("invoiceId", invoiceId);
        result.put("totalLines", totalLines);
        result.put("matchedLines", matchedLines);
        result.put("matchPercentage", matchPct);
        result.put("totalBilled", totalBilled);
        result.put("totalExpected", totalExpected);
        result.put("totalVariance", totalVariance);
        result.put("status", invoice.getStatus());
        return result;
    }

    // ---- State transitions ----

    @Transactional
    public NxFreightInvoice approveInvoice(UUID invoiceId, String approvedBy) {
        NxFreightInvoice invoice = getInvoice(invoiceId);
        if (!"AUDITED".equals(invoice.getStatus()) && !"REVIEW_REQUIRED".equals(invoice.getStatus())) {
            throw new BadRequestException("Only AUDITED or REVIEW_REQUIRED invoices can be approved");
        }
        String prevStatus = invoice.getStatus();
        invoice.setStatus("APPROVED");
        invoice.setApprovedBy(approvedBy);
        invoiceRepository.save(invoice);
        createAuditLog(invoiceId, "APPROVE", approvedBy, prevStatus, "APPROVED", "Invoice approved for payment");
        return invoice;
    }

    @Transactional
    public NxFreightInvoice disputeInvoice(UUID invoiceId, String reason, String performedBy) {
        NxFreightInvoice invoice = getInvoice(invoiceId);
        String prevStatus = invoice.getStatus();
        invoice.setStatus("DISPUTED");
        invoice.setDisputeReason(reason);
        invoiceRepository.save(invoice);
        createAuditLog(invoiceId, "DISPUTE", performedBy, prevStatus, "DISPUTED", reason);
        return invoice;
    }

    @Transactional
    public NxFreightInvoice markPaid(UUID invoiceId, String performedBy) {
        NxFreightInvoice invoice = getInvoice(invoiceId);
        if (!"APPROVED".equals(invoice.getStatus())) {
            throw new BadRequestException("Only APPROVED invoices can be marked as paid");
        }
        String prevStatus = invoice.getStatus();
        invoice.setStatus("PAID");
        invoice.setPaidAt(LocalDateTime.now());
        invoiceRepository.save(invoice);
        createAuditLog(invoiceId, "MARK_PAID", performedBy, prevStatus, "PAID", "Invoice marked as paid");
        return invoice;
    }

    // ---- Stats ----

    public Map<String, Object> getAuditStats() {
        UUID tenantId = TenantContext.getCurrentTenantId();
        Map<String, Object> stats = new LinkedHashMap<>();
        stats.put("draft", invoiceRepository.countByTenantIdAndStatus(tenantId, "DRAFT"));
        stats.put("audited", invoiceRepository.countByTenantIdAndStatus(tenantId, "AUDITED"));
        stats.put("reviewRequired", invoiceRepository.countByTenantIdAndStatus(tenantId, "REVIEW_REQUIRED"));
        stats.put("approved", invoiceRepository.countByTenantIdAndStatus(tenantId, "APPROVED"));
        stats.put("disputed", invoiceRepository.countByTenantIdAndStatus(tenantId, "DISPUTED"));
        stats.put("paid", invoiceRepository.countByTenantIdAndStatus(tenantId, "PAID"));
        return stats;
    }

    // ---- Internal ----

    private void createAuditLog(UUID invoiceId, String action, String performedBy,
                                 String fromStatus, String toStatus, String notes) {
        NxFreightAuditLog log = NxFreightAuditLog.builder()
                .tenantId(TenantContext.getCurrentTenantId())
                .invoiceId(invoiceId)
                .action(action)
                .performedBy(performedBy)
                .fromStatus(fromStatus)
                .toStatus(toStatus)
                .notes(notes)
                .build();
        auditLogRepository.save(log);
    }
}
