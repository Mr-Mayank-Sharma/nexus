package com.nexus.oms.repository;

import com.nexus.oms.entity.NxCarrierAccount;
import org.springframework.data.jpa.repository.JpaRepository;
import java.util.List;
import java.util.UUID;

public interface CarrierAccountRepository extends JpaRepository<NxCarrierAccount, UUID> {

    List<NxCarrierAccount> findByTenantIdAndIsActiveTrue(UUID tenantId);

    List<NxCarrierAccount> findByTenantIdAndCarrierId(UUID tenantId, String carrierId);
}
