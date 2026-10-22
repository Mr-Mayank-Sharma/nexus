package com.nexus.oms.controller;

import com.nexus.oms.dto.ApiResponse;
import com.nexus.oms.service.RbacService;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.Map;

/** Roles, permissions and teams of the signed-in company (on the core model since doc 29, phase C). */
@RestController
@RequestMapping("/rbac")
public class RbacController {

    private final RbacService rbacService;

    public RbacController(RbacService rbacService) {
        this.rbacService = rbacService;
    }

    @GetMapping
    public ResponseEntity<ApiResponse<Map<String, String>>> getRbacDashboard() {
        return ResponseEntity.ok(ApiResponse.success(Map.of("status", "ok")));
    }

    @GetMapping("/permissions")
    public ResponseEntity<ApiResponse<List<RbacService.PermissionView>>> getPermissions() {
        return ResponseEntity.ok(ApiResponse.success(rbacService.permissions()));
    }

    @GetMapping("/permissions/role")
    public ResponseEntity<ApiResponse<List<RbacService.PermissionView>>> getPermissionsByRole(@RequestParam String role) {
        return ResponseEntity.ok(ApiResponse.success(rbacService.permissionsOf(role)));
    }

    @PostMapping("/permissions")
    public ResponseEntity<ApiResponse<RbacService.PermissionView>> setPermission(@RequestBody RbacService.PermissionChange change) {
        if (change.role() == null || change.permissionGroup() == null || change.permissionName() == null) {
            throw new IllegalArgumentException("Send role, permissionGroup and permissionName");
        }
        return ResponseEntity.ok(ApiResponse.success(rbacService.setPermission(change), "Permission set"));
    }

    @GetMapping("/user-roles")
    public ResponseEntity<ApiResponse<Page<RbacService.UserRoleView>>> getUserRoles(@RequestParam(defaultValue = "0") int page,
                                                                                    @RequestParam(defaultValue = "20") int size) {
        return ResponseEntity.ok(ApiResponse.success(pageOf(rbacService.userRoles(), page, size)));
    }

    @GetMapping("/user-roles/by-user")
    public ResponseEntity<ApiResponse<List<RbacService.UserRoleView>>> getUserRolesByUser(@RequestParam String userId) {
        return ResponseEntity.ok(ApiResponse.success(rbacService.userRolesOf(userId)));
    }

    /** Body: {@code userId} and {@code role}; the company is always the signed-in one. */
    @PostMapping("/user-roles")
    public ResponseEntity<ApiResponse<RbacService.UserRoleView>> assignRole(@RequestBody Map<String, Object> request) {
        Object userId = request.get("userId");
        Object role = request.get("role");
        if (userId == null || role == null) {
            throw new IllegalArgumentException("Send userId and role");
        }
        return ResponseEntity.ok(ApiResponse.success(rbacService.assignRole(String.valueOf(userId), String.valueOf(role)), "Role assigned"));
    }

    /** The id of a user-role row is the user's id. */
    @DeleteMapping("/user-roles/{id}")
    public ResponseEntity<ApiResponse<Void>> removeRole(@PathVariable String id) {
        rbacService.removeRole(id);
        return ResponseEntity.ok(ApiResponse.success(null, "Role removed"));
    }

    @GetMapping("/teams")
    public ResponseEntity<ApiResponse<Page<RbacService.TeamView>>> getAllTeams(@RequestParam(defaultValue = "0") int page,
                                                                               @RequestParam(defaultValue = "20") int size) {
        return ResponseEntity.ok(ApiResponse.success(pageOf(rbacService.teams(), page, size)));
    }

    @PostMapping("/teams")
    public ResponseEntity<ApiResponse<RbacService.TeamView>> createTeam(@RequestBody Map<String, Object> team) {
        Object name = team.get("name");
        Object description = team.get("description");
        return ResponseEntity.ok(ApiResponse.success(rbacService.createTeam(name == null ? null : String.valueOf(name),
                description == null ? null : String.valueOf(description)), "Team created"));
    }

    @GetMapping("/check-permission")
    public ResponseEntity<ApiResponse<Boolean>> checkPermission(@RequestParam String userId, @RequestParam String permission,
                                                                @RequestParam String action) {
        return ResponseEntity.ok(ApiResponse.success(rbacService.hasPermission(userId, permission, action)));
    }

    /** One page of a list, in the shape the screens read (content, totalElements, totalPages). */
    private static <T> Page<T> pageOf(List<T> items, int page, int size) {
        int from = Math.min(page * size, items.size());
        int to = Math.min(from + size, items.size());
        return new PageImpl<>(items.subList(from, to), PageRequest.of(page, size), items.size());
    }
}
