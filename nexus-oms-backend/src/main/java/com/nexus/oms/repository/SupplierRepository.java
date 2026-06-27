package com.nexus.oms.repository;

import com.nexus.oms.entity.Supplier;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface SupplierRepository extends JpaRepository<Supplier, UUID> {

    Page<Supplier> findByTenantId(UUID tenantId, Pageable pageable);

    Optional<Supplier> findByTenantIdAndSupplierCode(UUID tenantId, String supplierCode);

    List<Supplier> findByTenantIdAndStatus(UUID tenantId, String status);

    List<Supplier> findByTenantIdAndSupplierType(UUID tenantId, String supplierType);
}
