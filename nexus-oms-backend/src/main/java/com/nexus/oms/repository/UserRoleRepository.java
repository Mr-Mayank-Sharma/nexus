package com.nexus.oms.repository;

import com.nexus.oms.entity.UserRole;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface UserRoleRepository extends JpaRepository<UserRole, UUID> {

    List<UserRole> findByTenantId(UUID tenantId);

    Page<UserRole> findByTenantId(UUID tenantId, Pageable pageable);

    List<UserRole> findByUserId(UUID userId);

    List<UserRole> findByTenantIdAndRole(UUID tenantId, String role);

    List<UserRole> findByTenantIdAndTeam(UUID tenantId, String team);

    Optional<UserRole> findByTenantIdAndUserId(UUID tenantId, UUID userId);

    Optional<UserRole> findByTenantIdAndUserIdAndRole(UUID tenantId, UUID userId, String role);
}
