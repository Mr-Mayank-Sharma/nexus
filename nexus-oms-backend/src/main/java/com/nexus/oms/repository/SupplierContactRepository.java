package com.nexus.oms.repository;

import com.nexus.oms.entity.SupplierContact;
import org.springframework.data.jpa.repository.JpaRepository;
import java.util.List;
import java.util.UUID;

public interface SupplierContactRepository extends JpaRepository<SupplierContact, UUID> {

    List<SupplierContact> findBySupplierId(UUID supplierId);

    List<SupplierContact> findBySupplierIdAndIsPrimary(UUID supplierId, boolean isPrimary);
}
