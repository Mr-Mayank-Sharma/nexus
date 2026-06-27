package com.nexus.oms.repository;

import com.nexus.oms.entity.Team;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface TeamRepository extends JpaRepository<Team, UUID> {

    Page<Team> findByTenantId(UUID tenantId, Pageable pageable);

    Optional<Team> findByTenantIdAndName(UUID tenantId, String name);

    List<Team> findByTenantIdAndIsActive(UUID tenantId, Boolean isActive);
}
