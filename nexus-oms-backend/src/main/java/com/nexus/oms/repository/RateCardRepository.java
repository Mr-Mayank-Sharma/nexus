package com.nexus.oms.repository;

import com.nexus.oms.entity.NxRateCard;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface RateCardRepository extends JpaRepository<NxRateCard, UUID> {

    List<NxRateCard> findByTenantId(UUID tenantId);

    List<NxRateCard> findByTenantIdAndIsActive(UUID tenantId, Boolean isActive);

    List<NxRateCard> findByTenantIdAndClientId(UUID tenantId, UUID clientId);

    Optional<NxRateCard> findFirstByTenantIdAndClientIdAndIsActiveOrderByEffectiveFromDesc(UUID tenantId, UUID clientId, Boolean isActive);

    Optional<NxRateCard> findFirstByTenantIdAndClientIdIsNullAndIsActiveOrderByEffectiveFromDesc(UUID tenantId, Boolean isActive);
}
