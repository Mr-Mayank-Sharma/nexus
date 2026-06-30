package com.nexus.oms.service;

import com.nexus.oms.entity.RolePermission;
import com.nexus.oms.entity.Team;
import com.nexus.oms.entity.UserRole;
import com.nexus.oms.exception.BadRequestException;
import com.nexus.oms.exception.ResourceNotFoundException;
import com.nexus.oms.repository.RolePermissionRepository;
import com.nexus.oms.repository.TeamRepository;
import com.nexus.oms.repository.UserRoleRepository;
import com.nexus.oms.security.TenantContext;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Service
public class RbacService {

    private final RolePermissionRepository rolePermissionRepository;
    private final UserRoleRepository userRoleRepository;
    private final TeamRepository teamRepository;

    public RbacService(RolePermissionRepository rolePermissionRepository,
                       UserRoleRepository userRoleRepository,
                       TeamRepository teamRepository) {
        this.rolePermissionRepository = rolePermissionRepository;
        this.userRoleRepository = userRoleRepository;
        this.teamRepository = teamRepository;
    }

    public List<RolePermission> getPermissions(UUID tenantId) {
        return rolePermissionRepository.findByTenantId(tenantId);
    }

    public List<RolePermission> getPermissionsByRole(String role) {
        UUID tenantId = TenantContext.getCurrentTenantId();
        return rolePermissionRepository.findByTenantIdAndRole(tenantId, role);
    }

    @Transactional
    public RolePermission setPermission(RolePermission p) {
        UUID tenantId = TenantContext.getCurrentTenantId();
        p.setTenantId(tenantId);
        List<RolePermission> existing = rolePermissionRepository
                .findByTenantIdAndRoleAndPermissionGroup(tenantId, p.getRole(), p.getPermissionGroup());
        Optional<RolePermission> match = existing.stream()
                .filter(rp -> rp.getPermissionName().equals(p.getPermissionName()))
                .findFirst();
        if (match.isPresent()) {
            RolePermission rp = match.get();
            if (p.getCanView() != null) rp.setCanView(p.getCanView());
            if (p.getCanCreate() != null) rp.setCanCreate(p.getCanCreate());
            if (p.getCanEdit() != null) rp.setCanEdit(p.getCanEdit());
            if (p.getCanDelete() != null) rp.setCanDelete(p.getCanDelete());
            if (p.getCanApprove() != null) rp.setCanApprove(p.getCanApprove());
            return rolePermissionRepository.save(rp);
        }
        return rolePermissionRepository.save(p);
    }

    public List<UserRole> getUserRoles(UUID userId) {
        return userRoleRepository.findByUserId(userId);
    }

    public Page<UserRole> getAllUserRoles(Pageable pageable) {
        UUID tenantId = TenantContext.getCurrentTenantId();
        return userRoleRepository.findByTenantId(tenantId, pageable);
    }

    @Transactional
    public UserRole assignRole(UUID tenantId, UUID userId, String role) {
        Optional<UserRole> existing = userRoleRepository.findByTenantIdAndUserIdAndRole(tenantId, userId, role);
        if (existing.isPresent()) {
            throw new BadRequestException("User already has role: " + role);
        }
        UserRole userRole = UserRole.builder()
                .tenantId(tenantId)
                .userId(userId)
                .role(role)
                .build();
        return userRoleRepository.save(userRole);
    }

    @Transactional
    public void removeRole(UUID id) {
        UserRole userRole = userRoleRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("UserRole", id));
        userRoleRepository.delete(userRole);
    }

    public Page<Team> getAllTeams(Pageable pageable) {
        UUID tenantId = TenantContext.getCurrentTenantId();
        return teamRepository.findByTenantId(tenantId, pageable);
    }

    @Transactional
    public Team createTeam(Team t) {
        UUID tenantId = TenantContext.getCurrentTenantId();
        t.setTenantId(tenantId);
        return teamRepository.save(t);
    }

    public boolean hasPermission(UUID tenantId, UUID userId, String permissionName, String action) {
        Optional<UserRole> userRole = userRoleRepository.findByTenantIdAndUserId(tenantId, userId);
        if (userRole.isEmpty()) return false;

        UserRole ur = userRole.get();
        List<RolePermission> perms = rolePermissionRepository
                .findByTenantIdAndRole(tenantId, ur.getRole());
        for (RolePermission rp : perms) {
            if (rp.getPermissionName().equals(permissionName)) {
                Boolean result = getCanAction(rp, action);
                if (Boolean.TRUE.equals(result)) return true;
            }
        }
        return false;
    }

    private Boolean getCanAction(RolePermission rp, String action) {
        return switch (action.toLowerCase()) {
            case "view" -> rp.getCanView();
            case "create" -> rp.getCanCreate();
            case "edit" -> rp.getCanEdit();
            case "delete" -> rp.getCanDelete();
            case "approve" -> rp.getCanApprove();
            default -> false;
        };
    }
}
