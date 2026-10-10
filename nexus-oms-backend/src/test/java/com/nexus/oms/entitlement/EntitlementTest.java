package com.nexus.oms.entitlement;

import com.nexus.oms.entity.NxTenantSubscription;
import com.nexus.oms.repository.TenantSubscriptionRepository;
import com.nexus.oms.security.TenantContext;
import jakarta.servlet.FilterChain;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.context.annotation.ClassPathScanningCandidateComponentProvider;
import org.springframework.core.type.filter.AnnotationTypeFilter;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.time.*;
import java.util.*;
import java.util.stream.Collectors;

import static com.nexus.oms.entitlement.ProductModule.*;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

class EntitlementTest {

    private static final UUID TENANT = UUID.randomUUID();
    private final TenantSubscriptionRepository repo = mock(TenantSubscriptionRepository.class);
    private final Clock clock = Clock.fixed(Instant.parse("2026-10-10T12:00:00Z"), ZoneOffset.UTC);
    private final EntitlementService service = new EntitlementService(repo, clock);

    @AfterEach
    void clear() { TenantContext.clear(); }

    private NxTenantSubscription sub(String edition, String addOns, LocalDateTime until) {
        return NxTenantSubscription.builder().tenantId(TENANT).edition(edition).addOns(addOns).validUntil(until).build();
    }

    @Test
    void tenantWithoutSubscription_keepsEverything() {
        when(repo.findById(TENANT)).thenReturn(Optional.empty());
        assertEquals(EnumSet.allOf(ProductModule.class), service.forTenant(TENANT).modules());
    }

    @Test
    void omsEdition_excludesWms_andAddsOnlyBoughtAddOns() {
        var e = service.resolve(sub("oms", "EDI, bogus, WMS", null));
        assertEquals(EnumSet.of(PLATFORM, OMS, EDI), e.modules(), "WMS is an edition, not an add-on");
    }

    @Test
    void expiredPlan_fallsBackToPlatformOnly() {
        var e = service.resolve(sub("SUITE", "AI", LocalDateTime.of(2026, 10, 1, 0, 0)));
        assertTrue(e.expired());
        assertEquals(EnumSet.of(PLATFORM), e.modules());
    }

    @Test
    void unknownEdition_grantsPlatformOnly() {
        assertEquals(EnumSet.of(PLATFORM), service.resolve(sub("GOLD", null, null)).modules());
    }

    @Test
    void lookupsAreCachedPerTenant() {
        when(repo.findById(TENANT)).thenReturn(Optional.of(sub("WMS", null, null)));
        service.forTenant(TENANT);
        service.forTenant(TENANT);
        verify(repo, times(1)).findById(TENANT);
        service.evict(TENANT);
        service.forTenant(TENANT);
        verify(repo, times(2)).findById(TENANT);
    }

    @Test
    void paths_mapToModules_longestPrefixWins() {
        assertEquals(OMS, forApiPath("/orders/123/cancel"));
        assertEquals(OMS, forApiPath("/twin/orders"));
        assertEquals(WMS, forApiPath("/twin/warehouses/abc"));
        assertEquals(OMS, forApiPath("/returns-finance/summary"));
        assertEquals(WMS, forApiPath("/picking"));
        assertEquals(THREE_PL, forApiPath("/billing/statements"));
        assertEquals(PLATFORM, forApiPath("/ordersXYZ"), "prefix must end at a path segment");
        assertEquals(PLATFORM, forApiPath("/inventory"));
    }

    @Test
    void filter_refusesUnlicensedModule_forEveryRole() throws Exception {
        when(repo.findById(TENANT)).thenReturn(Optional.of(sub("OMS", null, null)));
        TenantContext.setCurrentTenantId(TENANT);
        var filter = new ModuleEntitlementFilter(service);

        var blocked = new MockHttpServletResponse();
        FilterChain chain = mock(FilterChain.class);
        filter.doFilter(new MockHttpServletRequest("GET", "/waves"), blocked, chain);
        assertEquals(403, blocked.getStatus());
        assertTrue(blocked.getContentAsString().contains("\"module\":\"WMS\""));
        verifyNoInteractions(chain);

        var allowed = new MockHttpServletResponse();
        filter.doFilter(new MockHttpServletRequest("GET", "/orders"), allowed, chain);
        assertEquals(200, allowed.getStatus());
        verify(chain).doFilter(any(), any());
    }

    /**
     * Every controller is either assigned to a module or explicitly listed here as shared
     * platform. A new OMS/WMS controller therefore cannot ship unlicensed by accident.
     */
    @Test
    void everyControllerIsClassified() {
        Set<String> platform = Set.of("/analytics", "/auth", "/connectors", "/customers", "/dashboard",
                "/documents", "/entitlements", "/import", "/integration-hub", "/integration/webhooks", "/integration-platform", "/integration-stores",
                "/integrations/bigcommerce", "/inventory", "/invoicing", "/notifications", "/procurement",
                "/products", "/rbac", "/replenishment", "/reports", "/sample-data", "/settings", "/shipments",
                "/shopify", "/sync", "/transfers", "/twin", "/warehouses", "/webhooks", "/workflows");
        var scanner = new ClassPathScanningCandidateComponentProvider(false);
        scanner.addIncludeFilter(new AnnotationTypeFilter(RestController.class));
        Set<String> unclassified = scanner.findCandidateComponents("com.nexus.oms").stream()
                .map(bd -> {
                    try { return Class.forName(bd.getBeanClassName()).getAnnotation(RequestMapping.class); }
                    catch (ClassNotFoundException e) { throw new IllegalStateException(e); }
                })
                .filter(Objects::nonNull)
                .flatMap(rm -> Arrays.stream(rm.value().length > 0 ? rm.value() : rm.path()))
                .filter(p -> forApiPath(p) == PLATFORM && !platform.contains(p))
                .collect(Collectors.toCollection(TreeSet::new));
        assertEquals(Set.of(), unclassified, "assign these to a module in ProductModule or list them as platform");
    }
}
