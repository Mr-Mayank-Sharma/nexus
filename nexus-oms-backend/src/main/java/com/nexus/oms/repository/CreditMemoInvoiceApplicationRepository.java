package com.nexus.oms.repository;

import com.nexus.oms.entity.NxCreditMemoInvoiceApplication;
import org.springframework.data.jpa.repository.JpaRepository;
import java.util.List;
import java.util.UUID;

public interface CreditMemoInvoiceApplicationRepository extends JpaRepository<NxCreditMemoInvoiceApplication, UUID> {

    List<NxCreditMemoInvoiceApplication> findByCreditMemoId(UUID creditMemoId);

    List<NxCreditMemoInvoiceApplication> findByInvoiceId(UUID invoiceId);

    List<NxCreditMemoInvoiceApplication> findByTenantId(UUID tenantId);
}
