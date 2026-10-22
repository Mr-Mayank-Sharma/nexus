package com.nexus.oms.service;

import com.nexus.core.service.UserAccountService;
import com.nexus.oms.security.TenantContext;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.UUID;

/**
 * Roles, permissions and teams of the signed-in company, on the core security model (doc 29, phase C). The views keep
 * the field names the RBAC screen has always read, so the screen did not change when the data moved.
 * <p>
 * A permission row on the screen ({@code permissionGroup}, {@code permissionName}, {@code canView}...) is one grant in
 * the core: "*" (everything), "group:*" (every action on a resource) or "group:action".
 */
@Service
public class RbacService {

    static final String ALL = "*";

    private final UserAccountService userAccountService;
    private final PermissionService permissionService;

    public RbacService(UserAccountService userAccountService, PermissionService permissionService) {
        this.userAccountService = userAccountService;
        this.permissionService = permissionService;
    }

    public record PermissionView(String id, UUID tenantId, String role, String permissionGroup, String permissionName, boolean canView,
                                 boolean canCreate, boolean canEdit, boolean canDelete, boolean canApprove) {
    }

    /** A permission change from the screen: the row it names, and its flags. */
    public record PermissionChange(String role, String permissionGroup, String permissionName, Boolean canView, Boolean canCreate, Boolean canEdit,
                                   Boolean canDelete, Boolean canApprove) {
    }

    public record UserRoleView(String id, UUID tenantId, String userId, String username, String role, boolean isActive) {
    }

    public record TeamView(String id, UUID tenantId, String name, String description, boolean isActive) {
    }

    // ── permissions ──

    /** Every role's grants, as permission rows. */
    public List<PermissionView> permissions() {
        List<PermissionView> rows = new ArrayList<>();
        for (String role : userAccountService.roles()) {
            rows.addAll(permissionsOf(role));
        }
        return rows;
    }

    public List<PermissionView> permissionsOf(String role) {
        UUID tenantId = TenantContext.getCurrentTenantId();
        return userAccountService.permissionsOf(role).stream().map(grant -> toRow(tenantId, role, grant)).toList();
    }

    /**
     * Sets a permission row: the grant it names is given when the row allows its own action (for "view", canView;
     * for "*", any flag), and taken away otherwise.
     */
    public PermissionView setPermission(PermissionChange change) {
        String group = change.permissionGroup().trim().toLowerCase(Locale.ROOT);
        String name = change.permissionName().trim().toLowerCase(Locale.ROOT);
        String grant = ALL.equals(group) ? UserAccountService.EVERYTHING : UserAccountService.permissionId(group, name);
        boolean granted = switch (name) {
            case "view" -> Boolean.TRUE.equals(change.canView());
            case "create" -> Boolean.TRUE.equals(change.canCreate());
            case "edit" -> Boolean.TRUE.equals(change.canEdit());
            case "delete" -> Boolean.TRUE.equals(change.canDelete());
            case "approve" -> Boolean.TRUE.equals(change.canApprove());
            default -> anyTrue(change.canView(), change.canCreate(), change.canEdit(), change.canDelete(), change.canApprove());
        };
        userAccountService.setGrant(change.role(), grant, granted);
        UUID tenantId = TenantContext.getCurrentTenantId();
        permissionService.invalidateCache(tenantId, change.role());
        return toRow(tenantId, change.role(), grant);
    }

    /** Whether the user's role may do the action on the resource. */
    public boolean hasPermission(String userId, String resource, String action) {
        String role = userAccountService.roleOf(userId);
        if (role == null) {
            return false;
        }
        Set<String> grants = userAccountService.permissionsOf(role);
        return UserAccountService.allows(grants, resource, action);
    }

    // ── user roles ──

    public List<UserRoleView> userRoles() {
        UUID tenantId = TenantContext.getCurrentTenantId();
        return userAccountService.users().stream()
                .filter(user -> user.role() != null)
                .map(user -> new UserRoleView(user.userId(), tenantId, user.userId(), user.username(), user.role(), !user.disabled()))
                .toList();
    }

    public List<UserRoleView> userRolesOf(String userId) {
        return userRoles().stream().filter(view -> view.userId().equals(userId)).toList();
    }

    public UserRoleView assignRole(String userId, String role) {
        userAccountService.setRole(userId, role);
        permissionService.invalidateAllCache();
        UserAccountService.UserView user = userAccountService.view(userId);
        return new UserRoleView(user.userId(), TenantContext.getCurrentTenantId(), user.userId(), user.username(), user.role(), !user.disabled());
    }

    /** The id of a user-role row is the user's id. */
    public void removeRole(String userId) {
        userAccountService.clearRole(userId);
        permissionService.invalidateAllCache();
    }

    // ── teams ──

    public List<TeamView> teams() {
        UUID tenantId = TenantContext.getCurrentTenantId();
        return userAccountService.teams().stream().map(team -> new TeamView(team.teamId(), tenantId, team.name(), team.description(), true)).toList();
    }

    public TeamView createTeam(String name, String description) {
        String teamId = userAccountService.createTeam(name, description);
        return new TeamView(teamId, TenantContext.getCurrentTenantId(), name, description, true);
    }

    // ── internals ──

    /** A grant as the screen's row: "*" is every group and action, "group:*" every action, "group:action" one. */
    private static PermissionView toRow(UUID tenantId, String role, String grant) {
        String rowId = role + "/" + grant;
        if (UserAccountService.EVERYTHING.equals(grant)) {
            return new PermissionView(rowId, tenantId, role, ALL, ALL, true, true, true, true, true);
        }
        int separator = grant.indexOf(':');
        String group = grant.substring(0, separator);
        String action = grant.substring(separator + 1);
        if (ALL.equals(action)) {
            return new PermissionView(rowId, tenantId, role, group, ALL, true, true, true, true, true);
        }
        return new PermissionView(rowId, tenantId, role, group, action, "view".equals(action), "create".equals(action), "edit".equals(action),
                "delete".equals(action), "approve".equals(action));
    }

    private static boolean anyTrue(Boolean... flags) {
        for (Boolean flag : flags) {
            if (Boolean.TRUE.equals(flag)) {
                return true;
            }
        }
        return false;
    }
}
