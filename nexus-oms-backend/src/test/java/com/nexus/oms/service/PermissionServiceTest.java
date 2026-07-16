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

import java.util.List;
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
