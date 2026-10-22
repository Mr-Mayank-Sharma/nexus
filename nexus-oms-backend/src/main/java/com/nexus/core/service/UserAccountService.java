package com.nexus.core.service;

import com.nexus.core.CoreTransactional;
import com.nexus.core.model.security.UserAccount;
import com.nexus.core.model.security.UserAuthIdentity;
import com.nexus.core.model.security.UserGroup;
import com.nexus.core.model.security.UserGroupMember;
import com.nexus.core.model.security.UserGroupPermission;
import com.nexus.core.model.security.UserPermission;
import jakarta.persistence.EntityManager;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Optional;
import java.util.Set;

/**
 * Users, roles, teams and permissions of one company (doc 29, phase C), the OFBiz/Moqui way:
 * <ul>
 *   <li>a user is a {@code user_account} (the password is stored only as a hash made by the caller);</li>
 *   <li>a role is a user group of type UGT_ROLE, a team one of type UGT_TEAM; membership is dated;</li>
 *   <li>a permission is "*" (everything), "resource:*" (every action on a resource) or "resource:action", granted to a
 *       group; a role may do what any of its grants allows.</li>
 * </ul>
 * Which company a username belongs to is the host's business (the shared sign-in index); this service works in the
 * current company's database.
 */
@Service("coreUserAccountService")
@CoreTransactional
public class UserAccountService {

    public static final String ROLE_GROUP = "UGT_ROLE";
    public static final String TEAM_GROUP = "UGT_TEAM";
    public static final String EVERYTHING = "*";
    static final String WILDCARD_ACTION = "*";

    private final EntityManager entityManager;
    private final SequenceService sequenceService;

    public UserAccountService(EntityManager entityManager, SequenceService sequenceService) {
        this.entityManager = entityManager;
        this.sequenceService = sequenceService;
    }

    /** A user to create. {@code userId} may be given (copying a user from elsewhere); null takes a new id. */
    public record NewUser(String userId, String username, String emailAddress, String passwordHash, String fullName, String role) {
    }

    /** A user as the rest of the system sees it (never the password hash). */
    public record UserView(String userId, String username, String emailAddress, String fullName, String role, boolean disabled, boolean mfaEnabled) {
    }

    // ── users ──

    /** Creates the user with its role. */
    public String create(NewUser newUser) {
        if (newUser.username() == null || newUser.username().isBlank()) {
            throw new IllegalArgumentException("A user needs a username");
        }
        if (findByUsername(newUser.username()).isPresent()) {
            throw new IllegalArgumentException("Username " + newUser.username() + " is taken");
        }
        UserAccount user = new UserAccount();
        user.setUserId(newUser.userId() == null ? sequenceService.nextId("UserAccount") : newUser.userId());
        user.setUsername(newUser.username().trim());
        user.setEmailAddress(normaliseEmail(newUser.emailAddress()));
        user.setUserFullName(newUser.fullName());
        user.setCurrentPassword(newUser.passwordHash());
        user.setPasswordHashType("BCRYPT");
        user.setPasswordSetDate(CoreClock.now());
        user.setDisabled(false);
        user.setMfaEnabled(false);
        user.setSuccessiveFailedLogins(0L);
        entityManager.persist(user);
        if (newUser.role() != null) {
            setRole(user.getUserId(), newUser.role());
        }
        return user.getUserId();
    }

    @CoreTransactional(readOnly = true)
    public Optional<UserAccount> findByUsername(String username) {
        if (username == null) {
            return Optional.empty();
        }
        return entityManager.createQuery("SELECT u FROM UserAccount u WHERE u.username = :username", UserAccount.class)
                .setParameter("username", username.trim())
                .getResultList()
                .stream()
                .findFirst();
    }

    @CoreTransactional(readOnly = true)
    public Optional<UserAccount> findById(String userId) {
        return Optional.ofNullable(entityManager.find(UserAccount.class, userId));
    }

    @CoreTransactional(readOnly = true)
    public List<UserAccount> findByEmail(String emailAddress) {
        return entityManager.createQuery("SELECT u FROM UserAccount u WHERE u.emailAddress = :email", UserAccount.class)
                .setParameter("email", normaliseEmail(emailAddress))
                .getResultList();
    }

    /** Every user of the company with their role, by username. */
    @CoreTransactional(readOnly = true)
    public List<UserView> users() {
        return entityManager.createQuery("SELECT u FROM UserAccount u ORDER BY u.username", UserAccount.class)
                .getResultList()
                .stream()
                .map(this::toView)
                .toList();
    }

    @CoreTransactional(readOnly = true)
    public UserView view(String userId) {
        return toView(loadUser(userId));
    }

    public void setPasswordHash(String userId, String passwordHash) {
        UserAccount user = loadUser(userId);
        user.setCurrentPassword(passwordHash);
        user.setPasswordSetDate(CoreClock.now());
        user.setRequirePasswordChange(false);
    }

    public void setEmail(String userId, String emailAddress) {
        loadUser(userId).setEmailAddress(normaliseEmail(emailAddress));
    }

    public void setDisabled(String userId, boolean disabled) {
        UserAccount user = loadUser(userId);
        user.setDisabled(disabled);
        user.setDisabledDateTime(disabled ? CoreClock.now() : null);
    }

    // ── multi-factor sign-in ──

    /** Keeps a not-yet-active MFA secret (already encrypted by the caller). */
    public void setPendingMfaSecret(String userId, String encryptedSecret) {
        loadUser(userId).setMfaSecretRef(encryptedSecret);
    }

    /** Turns MFA on with the recovery codes' hashes (comma-separated). */
    public void enableMfa(String userId, String recoveryCodeHashes) {
        UserAccount user = loadUser(userId);
        user.setMfaEnabled(true);
        user.setMfaRecoveryCodes(recoveryCodeHashes);
    }

    public void disableMfa(String userId) {
        UserAccount user = loadUser(userId);
        user.setMfaEnabled(false);
        user.setMfaSecretRef(null);
        user.setMfaRecoveryCodes(null);
    }

    /** Replaces the remaining recovery codes' hashes (after one was used). */
    public void setRecoveryCodes(String userId, String recoveryCodeHashes) {
        loadUser(userId).setMfaRecoveryCodes(recoveryCodeHashes);
    }

    // ── outside sign-in identities (Google, Microsoft, Okta) ──

    public void linkIdentity(String userId, String providerId, String externalSubject, String emailAddress) {
        UserAuthIdentity identity = new UserAuthIdentity();
        identity.setProviderId(providerId);
        identity.setExternalSubject(externalSubject);
        identity.setUserId(userId);
        identity.setEmailAddress(normaliseEmail(emailAddress));
        identity.setLinkedDate(CoreClock.now());
        entityManager.persist(identity);
    }

    @CoreTransactional(readOnly = true)
    public Optional<String> userWithIdentity(String providerId, String externalSubject) {
        UserAuthIdentity identity = entityManager.find(UserAuthIdentity.class, new UserAuthIdentity.Key(providerId, externalSubject));
        return Optional.ofNullable(identity).map(UserAuthIdentity::getUserId);
    }

    // ── roles ──

    /** The user's current role; null when the user has none. */
    @CoreTransactional(readOnly = true)
    public String roleOf(String userId) {
        return currentGroupsOf(userId, ROLE_GROUP).stream().findFirst().orElse(null);
    }

    /** Gives the user this role, ending the role it had (a user has one role). */
    public void setRole(String userId, String role) {
        UserGroup roleGroup = entityManager.find(UserGroup.class, role);
        if (roleGroup == null || !ROLE_GROUP.equals(roleGroup.getGroupTypeEnumId())) {
            throw new IllegalArgumentException("Unknown role " + role + "; use one of " + roles());
        }
        LocalDateTime now = CoreClock.now();
        for (UserGroupMember membership : currentMemberships(userId, ROLE_GROUP)) {
            if (membership.getUserGroupId().equals(role)) {
                return;   // already has it
            }
            membership.setThruDate(now);
        }
        addMembership(userId, role, now);
    }

    /** Ends the user's role; the user then may do nothing until given another. */
    public void clearRole(String userId) {
        LocalDateTime now = CoreClock.now();
        currentMemberships(userId, ROLE_GROUP).forEach(membership -> membership.setThruDate(now));
    }

    /** The roles a user can be given. */
    @CoreTransactional(readOnly = true)
    public List<String> roles() {
        return groupsOfType(ROLE_GROUP).stream().map(UserGroup::getUserGroupId).toList();
    }

    // ── teams ──

    public record Team(String teamId, String name, String description) {
    }

    /** A new team; returns its id. The name is kept as the group's description, the description after it. */
    public String createTeam(String name, String description) {
        if (name == null || name.isBlank()) {
            throw new IllegalArgumentException("A team needs a name");
        }
        UserGroup team = new UserGroup();
        team.setUserGroupId(sequenceService.nextId("UserGroup"));
        team.setGroupTypeEnumId(TEAM_GROUP);
        team.setDescription(description == null || description.isBlank() ? name.trim() : name.trim() + " - " + description.trim());
        entityManager.persist(team);
        return team.getUserGroupId();
    }

    @CoreTransactional(readOnly = true)
    public List<Team> teams() {
        return groupsOfType(TEAM_GROUP).stream().map(group -> {
            String text = group.getDescription() == null ? "" : group.getDescription();
            int separator = text.indexOf(" - ");
            String name = separator < 0 ? text : text.substring(0, separator);
            String description = separator < 0 ? null : text.substring(separator + 3);
            return new Team(group.getUserGroupId(), name, description);
        }).toList();
    }

    // ── permissions ──

    /** The permission ids granted to a role now. */
    @CoreTransactional(readOnly = true)
    public Set<String> permissionsOf(String role) {
        LocalDateTime now = CoreClock.now();
        return new LinkedHashSet<>(entityManager.createQuery("SELECT p.userPermissionId FROM UserGroupPermission p WHERE p.userGroupId = :role "
                        + "AND p.fromDate <= :now AND (p.thruDate IS NULL OR p.thruDate > :now) ORDER BY p.userPermissionId", String.class)
                .setParameter("role", role)
                .setParameter("now", now)
                .getResultList());
    }

    /** Whether a role may do the action on the resource: "*", "resource:*" or "resource:action" grants it. */
    public static boolean allows(Set<String> grantedPermissions, String resource, String action) {
        if (grantedPermissions.contains(EVERYTHING)) {
            return true;
        }
        return grantedPermissions.contains(permissionId(resource, WILDCARD_ACTION)) || grantedPermissions.contains(permissionId(resource, action));
    }

    public static String permissionId(String resource, String action) {
        return resource.trim().toLowerCase(Locale.ROOT) + ":" + action.trim().toLowerCase(Locale.ROOT);
    }

    /** Grants (or, with {@code granted} false, takes away) a permission from a role. */
    public void setGrant(String role, String permissionId, boolean granted) {
        if (entityManager.find(UserGroup.class, role) == null) {
            throw new IllegalArgumentException("Unknown role " + role);
        }
        LocalDateTime now = CoreClock.now();
        List<UserGroupPermission> current = entityManager.createQuery("SELECT p FROM UserGroupPermission p WHERE p.userGroupId = :role "
                        + "AND p.userPermissionId = :permission AND p.fromDate <= :now AND (p.thruDate IS NULL OR p.thruDate > :now)", UserGroupPermission.class)
                .setParameter("role", role)
                .setParameter("permission", permissionId)
                .setParameter("now", now)
                .getResultList();
        if (granted && current.isEmpty()) {
            if (entityManager.find(UserPermission.class, permissionId) == null) {
                UserPermission permission = new UserPermission();
                permission.setUserPermissionId(permissionId);
                permission.setDescription(permissionId);
                entityManager.persist(permission);
            }
            UserGroupPermission grant = new UserGroupPermission();
            grant.setUserGroupId(role);
            grant.setUserPermissionId(permissionId);
            grant.setFromDate(now);
            entityManager.persist(grant);
        } else if (!granted) {
            current.forEach(grant -> grant.setThruDate(now));
        }
    }

    // ── internals ──

    private UserView toView(UserAccount user) {
        return new UserView(user.getUserId(), user.getUsername(), user.getEmailAddress(), user.getUserFullName(), roleOf(user.getUserId()),
                Boolean.TRUE.equals(user.getDisabled()), Boolean.TRUE.equals(user.getMfaEnabled()));
    }

    private List<String> currentGroupsOf(String userId, String groupType) {
        return currentMemberships(userId, groupType).stream().map(UserGroupMember::getUserGroupId).toList();
    }

    private List<UserGroupMember> currentMemberships(String userId, String groupType) {
        LocalDateTime now = CoreClock.now();
        return entityManager.createQuery("SELECT m FROM UserGroupMember m, UserGroup g WHERE g.userGroupId = m.userGroupId AND m.userId = :userId "
                        + "AND g.groupTypeEnumId = :groupType AND m.fromDate <= :now AND (m.thruDate IS NULL OR m.thruDate > :now) ORDER BY m.fromDate DESC",
                        UserGroupMember.class)
                .setParameter("userId", userId)
                .setParameter("groupType", groupType)
                .setParameter("now", now)
                .getResultList();
    }

    private void addMembership(String userId, String userGroupId, LocalDateTime from) {
        UserGroupMember membership = new UserGroupMember();
        membership.setUserGroupId(userGroupId);
        membership.setUserId(userId);
        membership.setFromDate(from);
        entityManager.persist(membership);
    }

    private List<UserGroup> groupsOfType(String groupType) {
        return entityManager.createQuery("SELECT g FROM UserGroup g WHERE g.groupTypeEnumId = :groupType ORDER BY g.userGroupId", UserGroup.class)
                .setParameter("groupType", groupType)
                .getResultList();
    }

    private UserAccount loadUser(String userId) {
        UserAccount user = entityManager.find(UserAccount.class, userId);
        if (user == null) {
            throw new IllegalArgumentException("Unknown user " + userId);
        }
        return user;
    }

    private static String normaliseEmail(String emailAddress) {
        return emailAddress == null || emailAddress.isBlank() ? null : emailAddress.trim().toLowerCase(Locale.ROOT);
    }
}
