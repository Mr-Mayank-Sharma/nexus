package com.nexus.oms.repository;

import com.nexus.oms.entity.NxFreightInvoice;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface NxFreightInvoiceRepository extends JpaRepository<NxFreightInvoice, UUID> {
    List<NxFreightInvoice> findByTenantIdAndStatus(UUID tenantId, String status);
    List<NxFreightInvoice> findByTenantId(UUID tenantId);
    Optional<NxFreightInvoice> findByTenantIdAndInvoiceNumber(UUID tenantId, String invoiceNumber);
    long countByTenantIdAndStatus(UUID tenantId, String status);
}
