package com.nexus.oms.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.nexus.oms.entity.CompanySettings;
import com.nexus.oms.exception.BadRequestException;
import com.nexus.oms.repository.CompanySettingsRepository;
import com.nexus.oms.security.TenantAwarePrincipal;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;

import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class CompanySettingsServiceTest {

    @Mock
    private CompanySettingsRepository companySettingsRepository;

    private ObjectMapper objectMapper;
    private CompanySettingsService companySettingsService;
    private UUID tenantId;

    @BeforeEach
    void setUp() {
        objectMapper = new ObjectMapper();
        companySettingsService = new CompanySettingsService(companySettingsRepository, objectMapper);
        tenantId = UUID.randomUUID();
        SecurityContextHolder.getContext().setAuthentication(
                new UsernamePasswordAuthenticationToken(
                        new TenantAwarePrincipal("admin", tenantId),
                        null,
                        List.of(new SimpleGrantedAuthority("ROLE_ADMIN"))));
    }

    @AfterEach
    void tearDown() {
        SecurityContextHolder.clearContext();
    }

    @Test
    void getSettings_existing() {
        CompanySettings settings = CompanySettings.builder().tenantId(tenantId).companyName("TestCo").build();
        when(companySettingsRepository.findByTenantId(tenantId)).thenReturn(Optional.of(settings));

        CompanySettings result = companySettingsService.getSettings();

        assertEquals("TestCo", result.getCompanyName());
    }

    @Test
    void getSettings_createsDefaults_whenMissing() {
        when(companySettingsRepository.findByTenantId(tenantId)).thenReturn(Optional.empty());
        when(companySettingsRepository.save(any())).thenAnswer(i -> i.getArgument(0));

        CompanySettings result = companySettingsService.getSettings();

        assertEquals("USD", result.getDefaultCurrency());
        assertEquals("en", result.getDefaultLanguage());
        assertEquals(tenantId, result.getTenantId());
    }

    @Test
    void updateSettings_updatesAllFields() {
        CompanySettings existing = CompanySettings.builder().tenantId(tenantId).companyName("Old").build();
        CompanySettings updates = CompanySettings.builder().companyName("New").defaultCurrency("EUR").build();
        when(companySettingsRepository.findByTenantId(tenantId)).thenReturn(Optional.of(existing));
        when(companySettingsRepository.save(any())).thenAnswer(i -> i.getArgument(0));

        CompanySettings result = companySettingsService.updateSettings(updates);

        assertEquals("New", result.getCompanyName());
        assertEquals("EUR", result.getDefaultCurrency());
    }

    @Test
    void updateSettings_notFound_throws() {
        when(companySettingsRepository.findByTenantId(tenantId)).thenReturn(Optional.empty());

        assertThrows(Exception.class, () -> companySettingsService.updateSettings(new CompanySettings()));
    }

    @Test
    void updateFeatureFlags_merges() {
        CompanySettings existing = CompanySettings.builder()
                .tenantId(tenantId)
                .featureFlags("{\"flag1\": true}")
                .build();
        when(companySettingsRepository.findByTenantId(tenantId)).thenReturn(Optional.of(existing));
        when(companySettingsRepository.save(any())).thenAnswer(i -> i.getArgument(0));

        CompanySettings result = companySettingsService.updateFeatureFlags(Map.of("flag2", false));

        assertNotNull(result);
    }

    @Test
    void updateFeatureFlags_withNullFlags() {
        CompanySettings existing = CompanySettings.builder()
                .tenantId(tenantId)
                .featureFlags(null)
                .build();
        when(companySettingsRepository.findByTenantId(tenantId)).thenReturn(Optional.of(existing));
        when(companySettingsRepository.save(any())).thenAnswer(i -> i.getArgument(0));

        CompanySettings result = companySettingsService.updateFeatureFlags(Map.of("flag1", true));

        assertNotNull(result);
    }

    @Test
    void getFeatureFlag_returnsBoolean() {
        CompanySettings existing = CompanySettings.builder()
                .tenantId(tenantId)
                .featureFlags("{\"enabled\": true}")
                .build();
        when(companySettingsRepository.findByTenantId(tenantId)).thenReturn(Optional.of(existing));

        Object result = companySettingsService.getFeatureFlag("enabled");

        assertEquals(true, result);
    }

    @Test
    void getFeatureFlag_returnsText() {
        CompanySettings existing = CompanySettings.builder()
                .tenantId(tenantId)
                .featureFlags("{\"mode\": \"test\"}")
                .build();
        when(companySettingsRepository.findByTenantId(tenantId)).thenReturn(Optional.of(existing));

        Object result = companySettingsService.getFeatureFlag("mode");

        assertEquals("test", result);
    }

    @Test
    void getFeatureFlag_returnsNull_whenMissing() {
        CompanySettings existing = CompanySettings.builder()
                .tenantId(tenantId)
                .featureFlags("{}")
                .build();
        when(companySettingsRepository.findByTenantId(tenantId)).thenReturn(Optional.of(existing));

        assertNull(companySettingsService.getFeatureFlag("nonexistent"));
    }

    @Test
    void isFeatureEnabled_returnsTrue() {
        CompanySettings existing = CompanySettings.builder()
                .tenantId(tenantId)
                .featureFlags("{\"flag\": true}")
                .build();
        when(companySettingsRepository.findByTenantId(tenantId)).thenReturn(Optional.of(existing));

        assertTrue(companySettingsService.isFeatureEnabled("flag"));
    }

    @Test
    void isFeatureEnabled_returnsFalse() {
        CompanySettings existing = CompanySettings.builder()
                .tenantId(tenantId)
                .featureFlags("{\"flag\": false}")
                .build();
        when(companySettingsRepository.findByTenantId(tenantId)).thenReturn(Optional.of(existing));

        assertFalse(companySettingsService.isFeatureEnabled("flag"));
    }

    @Test
    void updateSecurityPolicy_merges() {
        CompanySettings existing = CompanySettings.builder()
                .tenantId(tenantId)
                .securityPolicy("{\"mfa\": false}")
                .build();
        when(companySettingsRepository.findByTenantId(tenantId)).thenReturn(Optional.of(existing));
        when(companySettingsRepository.save(any())).thenAnswer(i -> i.getArgument(0));

        CompanySettings result = companySettingsService.updateSecurityPolicy(Map.of("mfa", true));

        assertNotNull(result);
    }

    @Test
    void updateFeatureFlags_badJson_throws() {
        CompanySettings existing = CompanySettings.builder()
                .tenantId(tenantId)
                .featureFlags("not json")
                .build();
        when(companySettingsRepository.findByTenantId(tenantId)).thenReturn(Optional.of(existing));

        assertThrows(BadRequestException.class,
                () -> companySettingsService.updateFeatureFlags(Map.of("flag", true)));
    }
}
