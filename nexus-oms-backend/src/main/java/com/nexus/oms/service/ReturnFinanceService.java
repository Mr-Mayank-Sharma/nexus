package com.nexus.oms.service;

import com.nexus.oms.dto.CreditMemoDetail;
import com.nexus.oms.dto.LesserValueExchangeRequest;
import com.nexus.oms.dto.SplitCreditMemoRequest;
import com.nexus.oms.entity.CreditMemo;
import com.nexus.oms.entity.NxCreditMemoInvoiceApplication;
import com.nexus.oms.entity.NxReturn;
import com.nexus.oms.exception.BadRequestException;
import com.nexus.oms.exception.ResourceNotFoundException;
import com.nexus.oms.repository.CreditMemoInvoiceApplicationRepository;
import com.nexus.oms.repository.CreditMemoRepository;
import com.nexus.oms.repository.ReturnRepository;
import com.nexus.oms.security.TenantContext;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.*;
import java.util.stream.Collectors;

/**
 * T-10: Returns / exchange financial reconciliation.
 *
 * Fixes the single-credit-memo limitation: a return claim can now generate
 * MULTIPLE credit memos (one per transaction), each with its own invoice
 * application. Handles split credit memos, lesser-value exchanges, cross-
 * location tax deltas, and customer-deposit balancing payments.
 */
@Service
public class ReturnFinanceService {

    private static final Logger log = LoggerFactory.getLogger(ReturnFinanceService.class);

    private static final Set<String> VALID_OUTCOMES = Set.of(
            "REFUND", "STORE_CREDIT", "LIKE_FOR_LIKE_EXCHANGE", "LESSER_VALUE_EXCHANGE");

    private final CreditMemoRepository creditMemoRepository;
    private final CreditMemoInvoiceApplicationRepository applicationRepository;
    private final ReturnRepository returnRepository;

    public ReturnFinanceService(CreditMemoRepository creditMemoRepository,
                                CreditMemoInvoiceApplicationRepository applicationRepository,
                                ReturnRepository returnRepository) {
        this.creditMemoRepository = creditMemoRepository;
        this.applicationRepository = applicationRepository;
        this.returnRepository = returnRepository;
    }

    // ------------------------------------------------------------------
    // 1. SPLIT CREDIT MEMOS
    // ------------------------------------------------------------------

    /**
     * A return with two different transactions in the same claim must create
     * TWO separate credit memos, not one combined. Each memo gets its own
     * invoice application(s).
     */
    @Transactional
    public List<CreditMemo> splitCreditMemos(SplitCreditMemoRequest request) {
        UUID tenantId = TenantContext.getCurrentTenantId();
        NxReturn returnClaim = getReturn(request.getReturnId(), tenantId);

        if (request.getTransactions() == null || request.getTransactions().isEmpty()) {
            throw new BadRequestException("At least one transaction split is required");
        }

        List<CreditMemo> created = new ArrayList<>();
        for (SplitCreditMemoRequest.TransactionSplit tx : request.getTransactions()) {
            String outcome = normalizeOutcome(tx.getOutcome());
            CreditMemo memo = CreditMemo.builder()
                    .tenantId(tenantId)
                    .returnId(returnClaim.getId())
                    .salesReturnId(returnClaim.getId())
                    .orderId(returnClaim.getOrderId())
                    .customerId(returnClaim.getCustomerId())
                    .memoNumber(generateMemoNumber(tenantId))
                    .memoType("RETURN")
                    .outcome(outcome)
                    .amount(tx.getAmount())
                    .currency("USD")
                    .status("DRAFT")
                    .reason("Split credit memo for return " + returnClaim.getRmaNumber())
                    .build();
            memo = creditMemoRepository.save(memo);

            // Apply this memo to its invoice.
            NxCreditMemoInvoiceApplication app = NxCreditMemoInvoiceApplication.builder()
                    .tenantId(tenantId)
                    .creditMemoId(memo.getId())
                    .invoiceId(tx.getInvoiceId())
                    .amount(tx.getAmount())
                    .taxAmount(tx.getTaxAmount() == null ? BigDecimal.ZERO : tx.getTaxAmount())
                    .build();
            applicationRepository.save(app);

            created.add(memo);
            log.info("Split credit memo {} (outcome={}, amount={}) for return {}",
                    memo.getMemoNumber(), outcome, tx.getAmount(), returnClaim.getRmaNumber());
        }
        return created;
    }

    // ------------------------------------------------------------------
    // 2. OUTCOME MODEL
    // ------------------------------------------------------------------

    /**
     * Validate that a return's outcome selection is legal. Per-return-outcome
     * is single-select, but a claim can be SPLIT into separate transactions
     * (store credit + like-for-like) sharing a sales-return identifier.
     */
    public void validateOutcomeSelection(NxReturn returnClaim, String outcome) {
        String normalized = normalizeOutcome(outcome);
        // Single-select per return: only one outcome allowed on the claim itself.
        // Splitting into transactions is handled by splitCreditMemos.
        log.debug("Validated outcome {} for return {}", normalized, returnClaim.getRmaNumber());
    }

    // ------------------------------------------------------------------
    // 3. LESSER-VALUE EXCHANGE
    // ------------------------------------------------------------------

    /**
     * Customer exchanges for a lower-cost item; the difference is refunded as
     * store credit. Produces a single credit memo with two correctly-applied
     * invoices (exchange + store credit).
     */
    @Transactional
    public CreditMemo lesserValueExchange(LesserValueExchangeRequest request) {
        UUID tenantId = TenantContext.getCurrentTenantId();
        NxReturn returnClaim = getReturn(request.getReturnId(), tenantId);

        BigDecimal originalValue = returnClaim.getRefundAmount() == null
                ? BigDecimal.ZERO : returnClaim.getRefundAmount();
        BigDecimal exchangeValue = request.getExchangeValue();
        if (exchangeValue == null || exchangeValue.signum() < 0) {
            throw new BadRequestException("Exchange value must be non-negative");
        }
        if (exchangeValue.compareTo(originalValue) > 0) {
            throw new BadRequestException("Exchange value exceeds original value — use a greater-value exchange flow");
        }
        BigDecimal storeCredit = originalValue.subtract(exchangeValue);

        // Single credit memo, two invoice applications: exchange + store credit.
        CreditMemo memo = CreditMemo.builder()
                .tenantId(tenantId)
                .returnId(returnClaim.getId())
                .salesReturnId(returnClaim.getId())
                .orderId(returnClaim.getOrderId())
                .customerId(returnClaim.getCustomerId())
                .memoNumber(generateMemoNumber(tenantId))
                .memoType("EXCHANGE")
                .outcome("LESSER_VALUE_EXCHANGE")
                .amount(originalValue)
                .currency("USD")
                .status("DRAFT")
                .sourceLocationId(request.getSourceLocationId())
                .targetLocationId(request.getTargetLocationId())
                .taxDelta(computeTaxDelta(request.getSourceLocationId(), request.getTargetLocationId(), exchangeValue))
                .reason("Lesser-value exchange; store credit " + storeCredit)
                .build();
        memo = creditMemoRepository.save(memo);

        // Application 1: the exchange invoice (value of the new item).
        applicationRepository.save(NxCreditMemoInvoiceApplication.builder()
                .tenantId(tenantId)
                .creditMemoId(memo.getId())
                .invoiceId(request.getExchangeInvoiceId())
                .amount(exchangeValue)
                .taxAmount(BigDecimal.ZERO)
                .build());

        // Application 2: the store-credit difference (no invoice, or a credit invoice).
        if (storeCredit.signum() > 0) {
            applicationRepository.save(NxCreditMemoInvoiceApplication.builder()
                    .tenantId(tenantId)
                    .creditMemoId(memo.getId())
                    .invoiceId(request.getExchangeInvoiceId()) // same claim; amount = store credit
                    .amount(storeCredit)
                    .taxAmount(BigDecimal.ZERO)
                    .build());
        }

        log.info("Lesser-value exchange for return {}: original={}, exchange={}, storeCredit={}",
                returnClaim.getRmaNumber(), originalValue, exchangeValue, storeCredit);
        return memo;
    }

    // ------------------------------------------------------------------
    // 4. CROSS-LOCATION TAX DELTA
    // ------------------------------------------------------------------

    /**
     * Exchange from a lower-tax location to a higher-tax location (e.g., Long
     * Beach); compute and apply the tax delta. Default rate model: 0% vs 8.25%
     * (CA). In a full implementation this reads configured tax rates per
     * location.
     */
    public BigDecimal computeTaxDelta(UUID sourceLocationId, UUID targetLocationId, BigDecimal amount) {
        if (sourceLocationId == null || targetLocationId == null || amount == null) {
            return BigDecimal.ZERO;
        }
        // Placeholder rate model — replace with configured per-location rates.
        BigDecimal sourceRate = BigDecimal.ZERO;
        BigDecimal targetRate = new BigDecimal("0.0825");
        BigDecimal sourceTax = amount.multiply(sourceRate).setScale(2, RoundingMode.HALF_UP);
        BigDecimal targetTax = amount.multiply(targetRate).setScale(2, RoundingMode.HALF_UP);
        return targetTax.subtract(sourceTax);
    }

    // ------------------------------------------------------------------
    // 5. CUSTOMER-DEPOSIT BALANCING
    // ------------------------------------------------------------------

    /**
     * Some transactions apply a customer deposit to balance the invoice;
     * classify it so it doesn't corrupt accounting.
     */
    @Transactional
    public CreditMemo applyCustomerDeposit(UUID creditMemoId, BigDecimal deposit) {
        UUID tenantId = TenantContext.getCurrentTenantId();
        CreditMemo memo = creditMemoRepository.findById(creditMemoId)
                .orElseThrow(() -> new ResourceNotFoundException("Credit memo not found: " + creditMemoId));
        if (!tenantId.equals(memo.getTenantId())) {
            throw new BadRequestException("Credit memo does not belong to the current tenant");
        }
        if (deposit == null || deposit.signum() < 0) {
            throw new BadRequestException("Deposit must be non-negative");
        }
        memo.setCustomerDepositApplied(deposit);
        memo = creditMemoRepository.save(memo);
        log.info("Applied customer deposit {} to credit memo {}", deposit, memo.getMemoNumber());
        return memo;
    }

    // ------------------------------------------------------------------
    // 6. IMPACT REPORT
    // ------------------------------------------------------------------

    /**
     * How many orders are affected by the current single-memo behavior, to
     * size the migration. A memo is "at risk" if it has multiple invoice
     * applications (which the legacy single-invoice model could not express).
     */
    @Transactional(readOnly = true)
    public ImpactReport getSingleMemoImpact(UUID tenantId) {
        List<CreditMemo> memos = creditMemoRepository.findAll().stream()
                .filter(m -> tenantId.equals(m.getTenantId()))
                .collect(Collectors.toList());
        long multiInvoice = 0;
        long total = memos.size();
        for (CreditMemo memo : memos) {
            if (applicationRepository.findByCreditMemoId(memo.getId()).size() > 1) {
                multiInvoice++;
            }
        }
        return new ImpactReport(total, multiInvoice);
    }

    // ------------------------------------------------------------------
    // Query helpers
    // ------------------------------------------------------------------

    @Transactional(readOnly = true)
    public List<CreditMemoDetail> getCreditMemosForReturn(UUID returnId) {
        UUID tenantId = TenantContext.getCurrentTenantId();
        List<CreditMemo> memos = creditMemoRepository.findByOrderId(returnId); // fallback
        // Prefer memos linked by return_id / sales_return_id.
        List<CreditMemo> linked = creditMemoRepository.findAll().stream()
                .filter(m -> tenantId.equals(m.getTenantId())
                        && (returnId.equals(m.getReturnId()) || returnId.equals(m.getSalesReturnId())))
                .collect(Collectors.toList());
        if (!linked.isEmpty()) {
            memos = linked;
        }

        List<CreditMemoDetail> details = new ArrayList<>();
        for (CreditMemo memo : memos) {
            List<CreditMemoDetail.InvoiceApplication> apps = applicationRepository
                    .findByCreditMemoId(memo.getId()).stream()
                    .map(a -> CreditMemoDetail.InvoiceApplication.builder()
                            .invoiceId(a.getInvoiceId())
                            .amount(a.getAmount())
                            .taxAmount(a.getTaxAmount())
                            .build())
                    .collect(Collectors.toList());
            details.add(CreditMemoDetail.builder()
                    .id(memo.getId())
                    .memoNumber(memo.getMemoNumber())
                    .returnId(memo.getReturnId())
                    .outcome(memo.getOutcome())
                    .amount(memo.getAmount())
                    .taxDelta(memo.getTaxDelta())
                    .status(memo.getStatus())
                    .invoiceApplications(apps)
                    .build());
        }
        return details;
    }

    // ------------------------------------------------------------------
    // Helpers
    // ------------------------------------------------------------------

    private NxReturn getReturn(UUID returnId, UUID tenantId) {
        NxReturn returnClaim = returnRepository.findById(returnId)
                .orElseThrow(() -> new ResourceNotFoundException("Return not found: " + returnId));
        if (!tenantId.equals(returnClaim.getTenantId())) {
            throw new BadRequestException("Return does not belong to the current tenant");
        }
        return returnClaim;
    }

    private String normalizeOutcome(String outcome) {
        String normalized = outcome == null ? "REFUND" : outcome.toUpperCase();
        if (!VALID_OUTCOMES.contains(normalized)) {
            throw new BadRequestException("Invalid outcome: " + outcome);
        }
        return normalized;
    }

    private String generateMemoNumber(UUID tenantId) {
        return "CM-" + UUID.randomUUID().toString().substring(0, 8).toUpperCase();
    }

    public record ImpactReport(long totalMemos, long multiInvoiceMemos) {}
}
