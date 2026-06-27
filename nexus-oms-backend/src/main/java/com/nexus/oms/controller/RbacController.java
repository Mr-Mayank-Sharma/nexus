package com.nexus.oms.controller;

import com.nexus.oms.dto.ApiResponse;
import com.nexus.oms.entity.RolePermission;
import com.nexus.oms.entity.Team;
import com.nexus.oms.entity.UserRole;
import com.nexus.oms.security.TenantContext;
import com.nexus.oms.service.RbacService;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;
import java.util.UUID;

@RestController
@RequestMapping("/rbac")
public class RbacController {

    private final RbacService rbacService;

    public RbacController(RbacService rbacService) {
        this.rbacService = rbacService;
    }

    @GetMapping("/permissions")
    public ResponseEntity<ApiResponse<List<RolePermission>>> getPermissions() {
        return ResponseEntity.ok(ApiResponse.success(
                rbacService.getPermissions(TenantContext.getCurrentTenantId())));
    }

    @GetMapping("/permissions/role")
    public ResponseEntity<ApiResponse<List<RolePermission>>> getPermissionsByRole(@RequestParam String role) {
        return ResponseEntity.ok(ApiResponse.success(rbacService.getPermissionsByRole(role)));
    }

    @PostMapping("/permissions")
    public ResponseEntity<ApiResponse<RolePermission>> setPermission(@RequestBody RolePermission rolePermission) {
        return ResponseEntity.ok(ApiResponse.success(
                rbacService.setPermission(rolePermission), "Permission set"));
    }

    @GetMapping("/user-roles")
    public ResponseEntity<ApiResponse<List<UserRole>>> getUserRoles(@RequestParam UUID userId) {
        return ResponseEntity.ok(ApiResponse.success(rbacService.getUserRoles(userId)));
    }

    @PostMapping("/user-roles")
    public ResponseEntity<ApiResponse<UserRole>> assignRole(@RequestBody Map<String, Object> request) {
        UUID tenantId = UUID.fromString((String) request.get("tenantId"));
        UUID userId = UUID.fromString((String) request.get("userId"));
        String role = (String) request.get("role");
        return ResponseEntity.ok(ApiResponse.success(
                rbacService.assignRole(tenantId, userId, role), "Role assigned"));
    }

    @DeleteMapping("/user-roles/{id}")
    public ResponseEntity<ApiResponse<Void>> removeRole(@PathVariable UUID id) {
        rbacService.removeRole(id);
        return ResponseEntity.ok(ApiResponse.success(null, "Role removed"));
    }

    @GetMapping("/teams")
    public ResponseEntity<ApiResponse<Page<Team>>> getAllTeams(
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size) {
        return ResponseEntity.ok(ApiResponse.success(
                rbacService.getAllTeams(PageRequest.of(page, size))));
    }

    @PostMapping("/teams")
    public ResponseEntity<ApiResponse<Team>> createTeam(@RequestBody Team team) {
        return ResponseEntity.ok(ApiResponse.success(
                rbacService.createTeam(team), "Team created"));
    }

    @GetMapping("/check-permission")
    public ResponseEntity<ApiResponse<Boolean>> checkPermission(
            @RequestParam UUID tenantId,
            @RequestParam UUID userId,
            @RequestParam String permission,
            @RequestParam String action) {
        return ResponseEntity.ok(ApiResponse.success(
                rbacService.hasPermission(tenantId, userId, permission, action)));
    }
}
