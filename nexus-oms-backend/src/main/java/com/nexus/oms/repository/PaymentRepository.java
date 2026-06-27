package com.nexus.oms.repository;

import com.nexus.oms.entity.Payment;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface PaymentRepository extends JpaRepository<Payment, UUID> {

    Page<Payment> findByTenantId(UUID tenantId, Pageable pageable);

    Optional<Payment> findByTenantIdAndPaymentNumber(UUID tenantId, String paymentNumber);

    List<Payment> findByInvoiceId(UUID invoiceId);

    List<Payment> findByTenantIdAndStatus(UUID tenantId, String status);

    List<Payment> findByCustomerId(UUID customerId);
}
