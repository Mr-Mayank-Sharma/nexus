package com.nexus.oms.repository;

import com.nexus.oms.entity.NxSerializedInventory;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface SerializedInventoryRepository extends JpaRepository<NxSerializedInventory, UUID> {

    Optional<NxSerializedInventory> findByTenantIdAndEpc(UUID tenantId, String epc);

    Page<NxSerializedInventory> findByTenantId(UUID tenantId, Pageable pageable);

    Page<NxSerializedInventory> findByTenantIdAndLocationIdAndStatus(
            UUID tenantId, UUID locationId, String status, Pageable pageable);

    List<NxSerializedInventory> findByTenantIdAndLocationId(UUID tenantId, UUID locationId);

    List<NxSerializedInventory> findByTenantIdAndSku(UUID tenantId, String sku);

    long countByTenantIdAndLocationIdAndStatus(UUID tenantId, UUID locationId, String status);
}
