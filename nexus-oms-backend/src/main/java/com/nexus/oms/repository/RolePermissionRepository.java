package com.nexus.oms.repository;

import com.nexus.oms.entity.RolePermission;
import org.springframework.data.jpa.repository.JpaRepository;
import java.util.List;
import java.util.UUID;

public interface RolePermissionRepository extends JpaRepository<RolePermission, UUID> {

    List<RolePermission> findByTenantId(UUID tenantId);

    List<RolePermission> findByTenantIdAndRole(UUID tenantId, String role);

    List<RolePermission> findByTenantIdAndRoleAndPermissionGroup(UUID tenantId, String role, String permissionGroup);

    List<RolePermission> findByTenantIdAndPermissionGroup(UUID tenantId, String permissionGroup);
}
