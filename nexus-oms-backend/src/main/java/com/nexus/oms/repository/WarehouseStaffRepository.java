package com.nexus.oms.repository;

import com.nexus.oms.entity.WarehouseStaff;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface WarehouseStaffRepository extends JpaRepository<WarehouseStaff, UUID> {

    List<WarehouseStaff> findByWarehouseId(UUID warehouseId);

    Page<WarehouseStaff> findByTenantId(UUID tenantId, Pageable pageable);

    List<WarehouseStaff> findByWarehouseIdAndRole(UUID warehouseId, String role);

    Optional<WarehouseStaff> findByUserId(UUID userId);
}
