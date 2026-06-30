package com.nexus.oms.repository;

import com.nexus.oms.entity.NxCustomer;
import org.springframework.data.jpa.repository.JpaRepository;
import java.util.Optional;
import java.util.UUID;

public interface CustomerRepository extends JpaRepository<NxCustomer, UUID> {

    Optional<NxCustomer> findByEmail(String email);

    Optional<NxCustomer> findByTenantIdAndExternalId(UUID tenantId, String externalId);

    Optional<NxCustomer> findByTenantIdAndEmail(UUID tenantId, String email);
}
