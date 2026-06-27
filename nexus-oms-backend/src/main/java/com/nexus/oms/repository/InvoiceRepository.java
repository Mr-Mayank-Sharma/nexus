package com.nexus.oms.repository;

import com.nexus.oms.entity.Invoice;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface InvoiceRepository extends JpaRepository<Invoice, UUID> {

    Page<Invoice> findByTenantId(UUID tenantId, Pageable pageable);

    Optional<Invoice> findByTenantIdAndInvoiceNumber(UUID tenantId, String invoiceNumber);

    List<Invoice> findByTenantIdAndStatus(UUID tenantId, String status);

    List<Invoice> findByOrderId(UUID orderId);

    List<Invoice> findByCustomerId(UUID customerId);

    List<Invoice> findBySupplierId(UUID supplierId);

    long countByTenantIdAndStatus(UUID tenantId, String status);
}
