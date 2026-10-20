package com.nexus.oms.repository;

import com.nexus.oms.entity.NxEdiPartner;
import org.springframework.data.jpa.repository.JpaRepository;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface EdiPartnerRepository extends JpaRepository<NxEdiPartner, UUID> {
    List<NxEdiPartner> findByTenantId(UUID tenantId);
    Optional<NxEdiPartner> findByTenantIdAndPartnerCode(UUID tenantId, String partnerCode);
}
