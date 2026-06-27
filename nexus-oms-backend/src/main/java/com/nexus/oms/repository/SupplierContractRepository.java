package com.nexus.oms.repository;

import com.nexus.oms.entity.SupplierContract;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import java.util.List;
import java.util.UUID;

public interface SupplierContractRepository extends JpaRepository<SupplierContract, UUID> {

    List<SupplierContract> findBySupplierId(UUID supplierId);

    Page<SupplierContract> findByTenantId(UUID tenantId, Pageable pageable);

    List<SupplierContract> findByTenantIdAndStatus(UUID tenantId, String status);

    List<SupplierContract> findBySupplierIdAndStatus(UUID supplierId, String status);
}
