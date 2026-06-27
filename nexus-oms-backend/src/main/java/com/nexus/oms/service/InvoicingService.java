package com.nexus.oms.service;

import com.nexus.oms.entity.CreditMemo;
import com.nexus.oms.entity.Invoice;
import com.nexus.oms.entity.InvoiceItem;
import com.nexus.oms.entity.Payment;
import com.nexus.oms.exception.BadRequestException;
import com.nexus.oms.exception.ResourceNotFoundException;
import com.nexus.oms.repository.CreditMemoRepository;
import com.nexus.oms.repository.InvoiceItemRepository;
import com.nexus.oms.repository.InvoiceRepository;
import com.nexus.oms.repository.PaymentRepository;
import com.nexus.oms.security.TenantContext;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDate;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

@Service
public class InvoicingService {

    private final InvoiceRepository invoiceRepository;
    private final InvoiceItemRepository invoiceItemRepository;
    private final PaymentRepository paymentRepository;
    private final CreditMemoRepository creditMemoRepository;

    public InvoicingService(InvoiceRepository invoiceRepository,
                            InvoiceItemRepository invoiceItemRepository,
                            PaymentRepository paymentRepository,
                            CreditMemoRepository creditMemoRepository) {
        this.invoiceRepository = invoiceRepository;
        this.invoiceItemRepository = invoiceItemRepository;
        this.paymentRepository = paymentRepository;
        this.creditMemoRepository = creditMemoRepository;
    }

    @Transactional(readOnly = true)
    public Page<Invoice> getAllInvoices(Pageable pageable) {
        UUID tenantId = TenantContext.getCurrentTenantId();
        return invoiceRepository.findByTenantId(tenantId, pageable);
    }

    @Transactional(readOnly = true)
    public Invoice getInvoice(UUID id) {
        return invoiceRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Invoice", id));
    }

    @Transactional
    public Invoice createInvoice(Invoice inv, List<InvoiceItem> items) {
        UUID tenantId = TenantContext.getCurrentTenantId();
        inv.setTenantId(tenantId);
        inv.setInvoiceNumber("INV-" + System.currentTimeMillis());

        BigDecimal subtotal = BigDecimal.ZERO;
        BigDecimal taxAmount = BigDecimal.ZERO;

        for (InvoiceItem item : items) {
            BigDecimal lineTotal = item.getUnitPrice()
                    .multiply(BigDecimal.valueOf(item.getQuantity()));
            item.setTotalPrice(lineTotal);
            subtotal = subtotal.add(lineTotal);
            if (item.getTaxRate() != null && item.getTaxRate().compareTo(BigDecimal.ZERO) > 0) {
                taxAmount = taxAmount.add(
                        lineTotal.multiply(item.getTaxRate())
                                .divide(BigDecimal.valueOf(100), 2, RoundingMode.HALF_UP));
            }
        }

        BigDecimal discount = inv.getDiscountAmount() != null ? inv.getDiscountAmount() : BigDecimal.ZERO;
        BigDecimal shipping = inv.getShippingCost() != null ? inv.getShippingCost() : BigDecimal.ZERO;

        inv.setSubtotal(subtotal);
        inv.setTaxAmount(taxAmount);
        inv.setTotalAmount(subtotal.add(taxAmount).subtract(discount).add(shipping));
        inv.setAmountPaid(BigDecimal.ZERO);
        inv.setBalanceDue(inv.getTotalAmount());

        if (inv.getInvoiceDate() == null) {
            inv.setInvoiceDate(LocalDate.now());
        }
        if (inv.getStatus() == null) {
            inv.setStatus("PENDING");
        }

        Invoice saved = invoiceRepository.save(inv);

        for (InvoiceItem item : items) {
            item.setInvoiceId(saved.getId());
        }
        invoiceItemRepository.saveAll(items);

        return saved;
    }

    @Transactional
    public Invoice updateInvoiceStatus(UUID id, String status) {
        Invoice inv = invoiceRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Invoice", id));
        inv.setStatus(status);
        return invoiceRepository.save(inv);
    }

    @Transactional
    public Payment recordPayment(UUID invoiceId, Payment payment) {
        UUID tenantId = TenantContext.getCurrentTenantId();
        Invoice inv = invoiceRepository.findById(invoiceId)
                .orElseThrow(() -> new ResourceNotFoundException("Invoice", invoiceId));

        if (payment.getAmount().compareTo(inv.getBalanceDue()) > 0) {
            throw new BadRequestException("Payment amount exceeds balance due");
        }

        payment.setTenantId(tenantId);
        payment.setInvoiceId(invoiceId);
        Payment saved = paymentRepository.save(payment);

        BigDecimal newAmountPaid = inv.getAmountPaid().add(payment.getAmount());
        inv.setAmountPaid(newAmountPaid);
        inv.setBalanceDue(inv.getTotalAmount().subtract(newAmountPaid));

        if (inv.getBalanceDue().compareTo(BigDecimal.ZERO) <= 0) {
            inv.setStatus("PAID");
            inv.setPaidDate(LocalDate.now());
        }

        invoiceRepository.save(inv);
        return saved;
    }

    @Transactional(readOnly = true)
    public Page<Payment> getAllPayments(Pageable pageable) {
        UUID tenantId = TenantContext.getCurrentTenantId();
        return paymentRepository.findByTenantId(tenantId, pageable);
    }

    @Transactional(readOnly = true)
    public Payment getPayment(UUID id) {
        return paymentRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Payment", id));
    }

    @Transactional
    public Payment processRefund(UUID paymentId, BigDecimal amount) {
        UUID tenantId = TenantContext.getCurrentTenantId();
        Payment payment = paymentRepository.findById(paymentId)
                .orElseThrow(() -> new ResourceNotFoundException("Payment", paymentId));

        payment.setStatus("REFUNDED");
        paymentRepository.save(payment);

        CreditMemo memo = CreditMemo.builder()
                .tenantId(tenantId)
                .invoiceId(payment.getInvoiceId())
                .amount(amount)
                .memoNumber("CM-" + System.currentTimeMillis())
                .reason("Refund for payment " + payment.getPaymentNumber())
                .status("ISSUED")
                .build();
        creditMemoRepository.save(memo);

        return payment;
    }

    @Transactional(readOnly = true)
    public List<Payment> getPaymentsByInvoice(UUID invoiceId) {
        return paymentRepository.findByInvoiceId(invoiceId);
    }

    @Transactional(readOnly = true)
    public Page<CreditMemo> getAllCreditMemos(Pageable pageable) {
        UUID tenantId = TenantContext.getCurrentTenantId();
        return creditMemoRepository.findByTenantId(tenantId, pageable);
    }

    @Transactional(readOnly = true)
    public CreditMemo getCreditMemo(UUID id) {
        return creditMemoRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("CreditMemo", id));
    }

    @Transactional
    public CreditMemo createCreditMemo(CreditMemo memo) {
        UUID tenantId = TenantContext.getCurrentTenantId();
        memo.setTenantId(tenantId);
        return creditMemoRepository.save(memo);
    }

    @Transactional(readOnly = true)
    public Map<String, Object> getInvoiceSummary() {
        UUID tenantId = TenantContext.getCurrentTenantId();
        List<Invoice> allInvoices = invoiceRepository
                .findByTenantId(tenantId, Pageable.unpaged()).getContent();

        BigDecimal totalOutstanding = BigDecimal.ZERO;
        BigDecimal totalOverdue = BigDecimal.ZERO;
        BigDecimal paidThisMonth = BigDecimal.ZERO;
        int pendingCount = 0;
        int paidCount = 0;
        int overdueCount = 0;

        LocalDate today = LocalDate.now();
        LocalDate startOfMonth = today.withDayOfMonth(1);

        for (Invoice inv : allInvoices) {
            BigDecimal balance = inv.getBalanceDue();
            if (balance != null && balance.compareTo(BigDecimal.ZERO) > 0) {
                totalOutstanding = totalOutstanding.add(balance);
                if (inv.getDueDate() != null && inv.getDueDate().isBefore(today)) {
                    totalOverdue = totalOverdue.add(balance);
                    overdueCount++;
                }
            }

            if ("PAID".equals(inv.getStatus()) && inv.getTotalAmount() != null) {
                paidCount++;
                if (inv.getPaidDate() != null && !inv.getPaidDate().isBefore(startOfMonth)) {
                    paidThisMonth = paidThisMonth.add(inv.getTotalAmount());
                }
            }

            if ("PENDING".equals(inv.getStatus()) && balance != null
                    && balance.compareTo(BigDecimal.ZERO) > 0) {
                pendingCount++;
            }
        }

        Map<String, Object> summary = new LinkedHashMap<>();
        summary.put("totalOutstanding", totalOutstanding);
        summary.put("totalOverdue", totalOverdue);
        summary.put("paidThisMonth", paidThisMonth);
        summary.put("pendingCount", pendingCount);
        summary.put("paidCount", paidCount);
        summary.put("overdueCount", overdueCount);
        return summary;
    }
}
