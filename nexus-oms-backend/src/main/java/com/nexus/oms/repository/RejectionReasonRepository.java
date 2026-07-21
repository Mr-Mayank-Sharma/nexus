package com.nexus.oms.repository;

import com.nexus.oms.entity.NxRejectionReason;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;
import java.util.List;
import java.util.UUID;

@Repository
public interface RejectionReasonRepository extends JpaRepository<NxRejectionReason, UUID> {
    List<NxRejectionReason> findByTenantId(UUID tenantId);
    List<NxRejectionReason> findByTenantIdAndActiveTrue(UUID tenantId);
    List<NxRejectionReason> findByTenantIdAndCategory(UUID tenantId, String category);
    NxRejectionReason findByTenantIdAndCode(UUID tenantId, String code);
}
