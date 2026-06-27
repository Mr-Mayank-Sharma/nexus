package com.nexus.oms.repository;

import com.nexus.oms.entity.CreditMemo;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface CreditMemoRepository extends JpaRepository<CreditMemo, UUID> {

    Page<CreditMemo> findByTenantId(UUID tenantId, Pageable pageable);

    Optional<CreditMemo> findByTenantIdAndMemoNumber(UUID tenantId, String memoNumber);

    List<CreditMemo> findByInvoiceId(UUID invoiceId);

    List<CreditMemo> findByOrderId(UUID orderId);

    List<CreditMemo> findByTenantIdAndStatus(UUID tenantId, String status);
}
