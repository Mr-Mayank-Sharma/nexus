package com.nexus.oms.repository;

import com.nexus.oms.entity.NxCarrierLabelConfig;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Repository
public interface CarrierLabelConfigRepository extends JpaRepository<NxCarrierLabelConfig, UUID> {
    List<NxCarrierLabelConfig> findByTenantId(UUID tenantId);
    Optional<NxCarrierLabelConfig> findByTenantIdAndCarrierCode(UUID tenantId, String carrierCode);
    List<NxCarrierLabelConfig> findByTenantIdAndIsActiveTrue(UUID tenantId);
}