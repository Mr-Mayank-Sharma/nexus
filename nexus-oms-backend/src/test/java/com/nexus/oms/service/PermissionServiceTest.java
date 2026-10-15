package com.nexus.oms.service;

import com.nexus.oms.entity.RolePermission;
import com.nexus.oms.repository.RolePermissionRepository;
import com.nexus.oms.security.TenantContext;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.MockedStatic;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;

import java.lang.reflect.Field;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class PermissionServiceTest {

    @Mock
    private RolePermissionRepository rolePermissionRepository;

    private PermissionService permissionService;
    private UUID tenantId;
    private MockedStatic<TenantContext> tenantContextMock;

    @BeforeEach
    void setUp() {
        tenantId = UUID.randomUUID();
        tenantContextMock = mockStatic(TenantContext.class);
        tenantContextMock.when(TenantContext::getCurrentTenantId).thenReturn(tenantId);
        permissionService = new PermissionService(rolePermissionRepository);
    }

    @AfterEach
    void tearDown() {
        tenantContextMock.close();
    }

    @Test
    void testAdminAlwaysHasPermission() {
        assertTrue(permissionService.hasPermission("ADMIN", "orders", "create"));
        assertTrue(permissionService.hasPermission("ADMIN", "inventory", "delete"));
        assertTrue(permissionService.hasPermission("ADMIN", "nonexistent", "view"));
    }

    @Test
    void testViewerWithExplicitPermission() {
        RolePermission rp = RolePermission.builder()
                .tenantId(tenantId)
                .role("VIEWER")
                .permissionGroup("orders")
                .permissionName("view")
                .canView(true)
                .canCreate(false)
                .canEdit(false)
                .canDelete(false)
                .build();

        when(rolePermissionRepository.findByTenantIdAndRole(tenantId, "VIEWER"))
                .thenReturn(List.of(rp));

        assertTrue(permissionService.hasPermission("VIEWER", "orders", "view"));
        assertFalse(permissionService.hasPermission("VIEWER", "orders", "create"));
        assertFalse(permissionService.hasPermission("VIEWER", "orders", "delete"));
    }

    @Test
    void testNullRoleReturnsFalse() {
        assertFalse(permissionService.hasPermission(null, "orders", "view"));
    }

    @Test
    void testNullResourceReturnsFalse() {
        assertFalse(permissionService.hasPermission("VIEWER", null, "view"));
    }

    @Test
    void testNullActionReturnsFalse() {
        assertFalse(permissionService.hasPermission("VIEWER", "orders", null));
    }

    @Test
    void testPermissionNotFoundReturnsFalse() {
        when(rolePermissionRepository.findByTenantIdAndRole(tenantId, "VIEWER"))
                .thenReturn(List.of());

        assertFalse(permissionService.hasPermission("VIEWER", "orders", "view"));
    }

    @Test
    void testWildcardPermissionGroupGrantsAll() {
        RolePermission rp = RolePermission.builder()
                .tenantId(tenantId)
                .role("OPS_MANAGER")
                .permissionGroup("*")
                .permissionName("*")
                .canView(true)
                .canCreate(true)
                .canEdit(true)
                .canDelete(true)
                .build();

        when(rolePermissionRepository.findByTenantIdAndRole(tenantId, "OPS_MANAGER"))
                .thenReturn(List.of(rp));

        assertTrue(permissionService.hasPermission("OPS_MANAGER", "orders", "view"));
        assertTrue(permissionService.hasPermission("OPS_MANAGER", "inventory", "create"));
        assertTrue(permissionService.hasPermission("OPS_MANAGER", "customers", "delete"));
    }

    @Test
    void testCacheIsUsed() {
        RolePermission rp = RolePermission.builder()
                .tenantId(tenantId)
                .role("VIEWER")
                .permissionGroup("orders")
                .permissionName("view")
                .canView(true)
                .build();

        when(rolePermissionRepository.findByTenantIdAndRole(tenantId, "VIEWER"))
                .thenReturn(List.of(rp));

        assertTrue(permissionService.hasPermission("VIEWER", "orders", "view"));
        assertTrue(permissionService.hasPermission("VIEWER", "orders", "view"));

        verify(rolePermissionRepository, times(1)).findByTenantIdAndRole(any(), any());
    }

    @Test
    void testCacheInvalidation() {
        RolePermission rp = RolePermission.builder()
                .tenantId(tenantId)
                .role("VIEWER")
                .permissionGroup("orders")
                .permissionName("view")
                .canView(true)
                .build();

        when(rolePermissionRepository.findByTenantIdAndRole(tenantId, "VIEWER"))
                .thenReturn(List.of(rp));

        assertTrue(permissionService.hasPermission("VIEWER", "orders", "view"));

        permissionService.invalidateCache(tenantId, "VIEWER");

        assertTrue(permissionService.hasPermission("VIEWER", "orders", "view"));

        verify(rolePermissionRepository, times(2)).findByTenantIdAndRole(any(), any());
    }

    @Test
    void testResolveResource() {
        assertEquals("orders", permissionService.resolveResource("/orders/123"));
        assertEquals("inventory", permissionService.resolveResource("/inventory/items/456"));
        assertEquals("customers", permissionService.resolveResource("/customers/789"));
        assertEquals("products", permissionService.resolveResource("/products/"));
        assertEquals("import", permissionService.resolveResource("/import/orders"));
        assertNull(permissionService.resolveResource("/unknown/path"));
    }

    /**
     * Regression guard: {@code /yard/}, {@code /yards/} and {@code /trailers/} are dock/trailer
     * routes and must resolve to the {@code shipping} permission group.
     *
     * <p>These three prefixes were previously registered twice in
     * {@code PermissionService.PATH_TO_RESOURCE} — first as {@code shipping}, then overwritten as
     * {@code warehouse}. Because {@code resolveResource} returns the FIRST prefix match in
     * {@code LinkedHashMap} insertion order, the duplicate {@code put} kept the original slot but
     * replaced its value, so yard and trailer requests were authorized against {@code warehouse}.
     * That denied the {@code LOADER} role, which is granted {@code shipping} but not
     * {@code warehouse}.
     */
    @Test
    void testYardAndTrailerRoutesResolveToShipping() {
        assertEquals("shipping", permissionService.resolveResource("/yard/"));
        assertEquals("shipping", permissionService.resolveResource("/yard/42"));
        assertEquals("shipping", permissionService.resolveResource("/yards/"));
        assertEquals("shipping", permissionService.resolveResource("/yards/42"));
        assertEquals("shipping", permissionService.resolveResource("/trailers/"));
        assertEquals("shipping", permissionService.resolveResource("/trailers/7/dock-door"));
    }

    /**
     * Guards the structural invariant that {@code resolveResource} depends on: every registered
     * prefix must be reachable, i.e. no EARLIER prefix may also match a path under it.
     *
     * <p>The current table stays correct only because every key ends with {@code "/"} — that stops
     * a broad prefix from swallowing a narrower one ({@code /inventory/} does not match
     * {@code /inventory-receipts/}, {@code /routing/} does not match {@code /routing-rules/}).
     * That is a subtle, easy-to-break property, and it is exactly what a duplicated {@code put}
     * defeated. This test also pins the mapping count so the figure quoted in the docs
     * (74) cannot silently drift again.
     */
    @Test
    void testEveryPathPrefixIsReachableUnderFirstPrefixMatch() throws Exception {
        LinkedHashMap<String, String> map = pathToResourceMap();

        assertEquals(77, map.size(),
                "expected 76 unique route-prefix mappings; update the documented count when adding one");

        List<String> keys = new ArrayList<>(map.keySet());
        for (int i = 0; i < keys.size(); i++) {
            final String current = keys.get(i);
            final String probe = current + "probe";

            for (int j = 0; j < i; j++) {
                final String earlier = keys.get(j);
                assertFalse(probe.startsWith(earlier),
                        () -> "prefix " + current + " is unreachable — shadowed by earlier " + earlier);
            }

            assertEquals(map.get(current), permissionService.resolveResource(probe),
                    () -> "prefix " + current + " does not resolve to its registered resource");
        }
    }

    @SuppressWarnings("unchecked")
    private LinkedHashMap<String, String> pathToResourceMap() throws Exception {
        Field field = PermissionService.class.getDeclaredField("PATH_TO_RESOURCE");
        field.setAccessible(true);
        Map<String, String> map = (Map<String, String>) field.get(null);
        return (LinkedHashMap<String, String>) map;
    }

    @Test
    void testResolveAction() {
        assertEquals("view", permissionService.resolveAction("GET"));
        assertEquals("create", permissionService.resolveAction("POST"));
        assertEquals("edit", permissionService.resolveAction("PUT"));
        assertEquals("edit", permissionService.resolveAction("PATCH"));
        assertEquals("delete", permissionService.resolveAction("DELETE"));
        assertNull(permissionService.resolveAction("OPTIONS"));
    }

    @Test
    void testWildcardPermissionNameWithSpecificGroup() {
        RolePermission rp = RolePermission.builder()
                .tenantId(tenantId)
                .role("OPS_MANAGER")
                .permissionGroup("inventory")
                .permissionName("*")
                .canView(true)
                .canCreate(true)
                .canEdit(true)
                .canDelete(true)
                .build();

        when(rolePermissionRepository.findByTenantIdAndRole(tenantId, "OPS_MANAGER"))
                .thenReturn(List.of(rp));

        assertTrue(permissionService.hasPermission("OPS_MANAGER", "inventory", "view"));
        assertTrue(permissionService.hasPermission("OPS_MANAGER", "inventory", "delete"));
        assertFalse(permissionService.hasPermission("OPS_MANAGER", "orders", "view"));
    }
}
