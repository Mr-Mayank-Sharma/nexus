package com.nexus.oms.service;

import com.nexus.oms.entity.CreditMemo;
import com.nexus.oms.entity.Invoice;
import com.nexus.oms.entity.InvoiceItem;
import com.nexus.oms.entity.Payment;
import com.nexus.oms.exception.BadRequestException;
import com.nexus.oms.repository.CreditMemoRepository;
import com.nexus.oms.repository.InvoiceItemRepository;
import com.nexus.oms.repository.InvoiceRepository;
import com.nexus.oms.repository.PaymentRepository;
import com.nexus.oms.security.TenantContext;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.Pageable;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class InvoicingServiceTest {

    @Mock private InvoiceRepository invoiceRepository;
    @Mock private InvoiceItemRepository invoiceItemRepository;
    @Mock private PaymentRepository paymentRepository;
    @Mock private CreditMemoRepository creditMemoRepository;

    private InvoicingService service;
    private UUID tenantId;
    private UUID invoiceId;

    @BeforeEach
    void setUp() {
        service = new InvoicingService(invoiceRepository, invoiceItemRepository, paymentRepository, creditMemoRepository);
        tenantId = UUID.randomUUID();
        invoiceId = UUID.randomUUID();
        TenantContext.setCurrentTenantId(tenantId);
    }

    @AfterEach
    void tearDown() {
        TenantContext.clear();
    }

    private Invoice invoice(BigDecimal total, BigDecimal paid, String status) {
        return Invoice.builder().id(invoiceId).tenantId(tenantId).status(status)
                .totalAmount(total).amountPaid(paid)
                .balanceDue(total.subtract(paid)).build();
    }

    @Test
    void createInvoice_computesSubtotalTaxAndTotalsAndLinksItems() {
        Invoice inv = Invoice.builder().tenantId(tenantId).discountAmount(new BigDecimal("5.00"))
                .shippingCost(new BigDecimal("10.00")).build();
        List<InvoiceItem> items = List.of(
                InvoiceItem.builder().sku("A").quantity(2).unitPrice(new BigDecimal("10.00"))
                        .taxRate(new BigDecimal("10")).build(),
                InvoiceItem.builder().sku("B").quantity(1).unitPrice(new BigDecimal("100.00")).build());
        when(invoiceRepository.save(any())).thenAnswer(a -> { Invoice i = a.getArgument(0); i.setId(invoiceId); return i; });

        Invoice saved = service.createInvoice(inv, items);

        assertEquals(new BigDecimal("120.00"), saved.getSubtotal());   // 20 + 100
        assertEquals(new BigDecimal("2.00"), saved.getTaxAmount());    // 10% of 20
        assertEquals(new BigDecimal("127.00"), saved.getTotalAmount()); // 120 + 2 - 5 + 10
        assertEquals(new BigDecimal("127.00"), saved.getBalanceDue());
        assertEquals("PENDING", saved.getStatus());

        ArgumentCaptor<List<InvoiceItem>> captor = ArgumentCaptor.forClass(List.class);
        verify(invoiceItemRepository).saveAll(captor.capture());
        captor.getValue().forEach(i -> assertEquals(invoiceId, i.getInvoiceId()));
        assertEquals(new BigDecimal("20.00"), captor.getValue().get(0).getTotalPrice());
    }

    @Test
    void recordPayment_updatesBalanceAndMarksPaidWhenSettled() {
        Invoice inv = invoice(new BigDecimal("100.00"), BigDecimal.ZERO, "PENDING");
        when(invoiceRepository.findById(invoiceId)).thenReturn(Optional.of(inv));
        Payment payment = Payment.builder().amount(new BigDecimal("100.00")).build();
        when(paymentRepository.save(any())).thenAnswer(a -> a.getArgument(0));
        when(invoiceRepository.save(any())).thenAnswer(a -> a.getArgument(0));

        Payment saved = service.recordPayment(invoiceId, payment);

        assertEquals(invoiceId, saved.getInvoiceId());
        assertEquals(tenantId, saved.getTenantId());
        assertTrue(inv.getBalanceDue().compareTo(BigDecimal.ZERO) == 0);
        assertEquals("PAID", inv.getStatus());
        assertNotNull(inv.getPaidDate());
    }

    @Test
    void recordPayment_overpaymentThrowsBadRequest() {
        Invoice inv = invoice(new BigDecimal("100.00"), BigDecimal.ZERO, "PENDING");
        when(invoiceRepository.findById(invoiceId)).thenReturn(Optional.of(inv));
        Payment payment = Payment.builder().amount(new BigDecimal("150.00")).build();

        assertThrows(BadRequestException.class, () -> service.recordPayment(invoiceId, payment));
        verify(paymentRepository, never()).save(any());
    }

    @Test
    void processRefund_marksPaymentRefundedAndCreatesCreditMemo() {
        Payment payment = Payment.builder().id(UUID.randomUUID()).tenantId(tenantId)
                .invoiceId(invoiceId).paymentNumber("PAY-1").amount(new BigDecimal("50.00")).build();
        when(paymentRepository.findById(payment.getId())).thenReturn(Optional.of(payment));
        when(paymentRepository.save(any())).thenAnswer(a -> a.getArgument(0));
        when(creditMemoRepository.save(any())).thenAnswer(a -> a.getArgument(0));

        Payment result = service.processRefund(payment.getId(), new BigDecimal("50.00"));

        assertEquals("REFUNDED", result.getStatus());
        ArgumentCaptor<CreditMemo> captor = ArgumentCaptor.forClass(CreditMemo.class);
        verify(creditMemoRepository).save(captor.capture());
        assertEquals(invoiceId, captor.getValue().getInvoiceId());
        assertEquals(new BigDecimal("50.00"), captor.getValue().getAmount());
        assertEquals("ISSUED", captor.getValue().getStatus());
    }

    @Test
    void getInvoiceSummary_aggregatesOutstandingOverdueAndPaid() {
        LocalDate today = LocalDate.now();
        Invoice pending = invoice(new BigDecimal("30.00"), BigDecimal.ZERO, "PENDING");
        pending.setDueDate(today.minusDays(5));
        Invoice paid = invoice(new BigDecimal("50.00"), new BigDecimal("50.00"), "PAID");
        paid.setPaidDate(today);
        Invoice future = invoice(new BigDecimal("20.00"), BigDecimal.ZERO, "PENDING");
        future.setDueDate(today.plusDays(10));

        when(invoiceRepository.findByTenantId(eq(tenantId), any(Pageable.class)))
                .thenReturn(new PageImpl<>(List.of(pending, paid, future)));

        Map<String, Object> summary = service.getInvoiceSummary();

        assertEquals(new BigDecimal("50.00"), summary.get("totalOutstanding")); // 30 + 20
        assertEquals(new BigDecimal("30.00"), summary.get("totalOverdue"));
        assertEquals(new BigDecimal("50.00"), summary.get("paidThisMonth"));
        assertEquals(2, summary.get("pendingCount"));
        assertEquals(1, summary.get("overdueCount"));
    }

    @Test
    void getAgingReport_bucketsInvoicesByDaysPastDue() {
        LocalDate today = LocalDate.now();
        Invoice current = invoice(new BigDecimal("40.00"), BigDecimal.ZERO, "PENDING");
        current.setDueDate(today.plusDays(5));
        Invoice bucket30 = invoice(new BigDecimal("100.00"), BigDecimal.ZERO, "PENDING");
        bucket30.setDueDate(today.minusDays(10));
        Invoice bucket60 = invoice(new BigDecimal("200.00"), BigDecimal.ZERO, "PENDING");
        bucket60.setDueDate(today.minusDays(45));
        Invoice bucket90 = invoice(new BigDecimal("300.00"), BigDecimal.ZERO, "PENDING");
        bucket90.setDueDate(today.minusDays(75));
        Invoice bucketPlus = invoice(new BigDecimal("400.00"), BigDecimal.ZERO, "PENDING");
        bucketPlus.setDueDate(today.minusDays(120));
        Invoice paid = invoice(new BigDecimal("500.00"), new BigDecimal("500.00"), "PAID");

        when(invoiceRepository.findByTenantId(tenantId)).thenReturn(List.of(
                current, bucket30, bucket60, bucket90, bucketPlus, paid));

        Map<String, Object> report = service.getAgingReport();

        @SuppressWarnings("unchecked")
        List<Map<String, Object>> buckets = (List<Map<String, Object>>) report.get("buckets");

        Map<String, Map<String, Object>> byLabel = new java.util.HashMap<>();
        for (Map<String, Object> b : buckets) {
            byLabel.put((String) b.get("label"), b);
        }

        assertEquals(1, byLabel.get("current").get("invoiceCount"));
        assertEquals(new BigDecimal("40.00"), byLabel.get("current").get("amount"));
        assertEquals(1, byLabel.get("1-30").get("invoiceCount"));
        assertEquals(new BigDecimal("100.00"), byLabel.get("1-30").get("amount"));
        assertEquals(1, byLabel.get("31-60").get("invoiceCount"));
        assertEquals(new BigDecimal("200.00"), byLabel.get("31-60").get("amount"));
        assertEquals(1, byLabel.get("61-90").get("invoiceCount"));
        assertEquals(new BigDecimal("300.00"), byLabel.get("61-90").get("amount"));
        assertEquals(1, byLabel.get("90+").get("invoiceCount"));
        assertEquals(new BigDecimal("400.00"), byLabel.get("90+").get("amount"));

        assertEquals(new BigDecimal("1040.00"), report.get("totalOutstanding"));
        assertEquals(new BigDecimal("1000.00"), report.get("totalOverdue")); // all but current
        assertEquals(5, report.get("openInvoiceCount"));
    }

    @Test
    void getAgingReport_returnsZeroBucketsWhenNoOpenInvoices() {
        Invoice paid = invoice(new BigDecimal("100.00"), new BigDecimal("100.00"), "PAID");
        when(invoiceRepository.findByTenantId(tenantId)).thenReturn(List.of(paid));

        Map<String, Object> report = service.getAgingReport();

        @SuppressWarnings("unchecked")
        List<Map<String, Object>> buckets = (List<Map<String, Object>>) report.get("buckets");
        assertEquals(5, buckets.size());
        assertEquals(BigDecimal.ZERO, report.get("totalOutstanding"));
        assertEquals(BigDecimal.ZERO, report.get("totalOverdue"));
        assertEquals(0, report.get("openInvoiceCount"));
    }
}
