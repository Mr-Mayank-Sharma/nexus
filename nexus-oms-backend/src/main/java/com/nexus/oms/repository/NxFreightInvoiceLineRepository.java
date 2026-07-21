package com.nexus.oms.repository;

import com.nexus.oms.entity.NxFreightInvoiceLine;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.UUID;

public interface NxFreightInvoiceLineRepository extends JpaRepository<NxFreightInvoiceLine, UUID> {
    List<NxFreightInvoiceLine> findByInvoiceId(UUID invoiceId);
    List<NxFreightInvoiceLine> findByInvoiceIdAndStatus(UUID invoiceId, String status);
}
