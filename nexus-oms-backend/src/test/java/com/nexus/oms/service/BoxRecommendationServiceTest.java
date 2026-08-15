package com.nexus.oms.service;

import com.nexus.oms.entity.NxBoxTemplate;
import com.nexus.oms.entity.NxPackage;
import com.nexus.oms.repository.BoxTemplateRepository;
import com.nexus.oms.repository.PackageRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyDouble;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class BoxRecommendationServiceTest {

    @Mock
    private BoxTemplateRepository boxTemplateRepository;
    @Mock
    private PackageRepository packageRepository;

    private BoxRecommendationService service;
    private UUID tenantId;

    @BeforeEach
    void setUp() {
        service = new BoxRecommendationService(boxTemplateRepository, packageRepository);
        tenantId = UUID.randomUUID();
    }

    private NxBoxTemplate box(String name, double volume, double maxWeight, int maxItems) {
        return NxBoxTemplate.builder()
                .tenantId(tenantId)
                .name(name)
                .widthIn(10.0)
                .heightIn(10.0)
                .depthIn(10.0)
                .volumeCapacityIn3(volume)
                .maxWeightLbs(maxWeight)
                .maxItemCount(maxItems)
                .build();
    }

    @Test
    void recommend_PicksSmallestBoxThatFitsVolume() {
        when(boxTemplateRepository.findSmallestFitting(tenantId, 700)).thenReturn(List.of(
                box("SM-BOX", 648, 15, 6),
                box("MD-BOX", 1536, 30, 12)));

        Map<String, Object> result = service.recommend(tenantId, 700, 10, 2);

        assertEquals("SM-BOX", result.get("boxName"));
        assertEquals("VOLUME", result.get("recommendedBy"));
        assertNotNull(result.get("fillRate"));
    }

    @Test
    void recommend_FallsBackToDefaultsWhenNoTenantTemplates() {
        when(boxTemplateRepository.findSmallestFitting(tenantId, 100)).thenReturn(List.of());

        Map<String, Object> result = service.recommend(tenantId, 100, 2, 1);

        assertEquals("SM-MAILER", result.get("boxName"));
    }

    @Test
    void recommend_ReportsNoFitWhenWeightExceedsCapacity() {
        when(boxTemplateRepository.findSmallestFitting(tenantId, 500)).thenReturn(List.of(
                box("SM-BOX", 648, 15, 6)));

        Map<String, Object> result = service.recommend(tenantId, 500, 100, 2);

        assertEquals("NO_FIT", result.get("boxName"));
    }

    @Test
    void recommendForPackage_EstimatesVolumeFromItemCount() {
        NxPackage pkg = new NxPackage();
        pkg.setId(UUID.randomUUID());
        pkg.setItemCount(4);
        pkg.setWeightLbs(8.0);

        when(packageRepository.findById(pkg.getId())).thenReturn(Optional.of(pkg));
        when(boxTemplateRepository.findSmallestFitting(any(UUID.class), anyDouble())).thenReturn(List.of(
                box("SM-BOX", 648, 15, 6)));

        Map<String, Object> result = service.recommendForPackage(tenantId, pkg.getId());

        assertEquals("SM-BOX", result.get("boxName"));
    }
}
