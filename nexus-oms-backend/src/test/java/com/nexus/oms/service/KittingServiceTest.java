package com.nexus.oms.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.nexus.oms.entity.NxKitTemplate;
import com.nexus.oms.entity.NxOrderItem;
import com.nexus.oms.exception.BadRequestException;
import com.nexus.oms.repository.KitTemplateRepository;
import com.nexus.oms.repository.OrderItemRepository;
import com.nexus.oms.repository.OrderRepository;
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
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class KittingServiceTest {

    @Mock
    private KitTemplateRepository kitTemplateRepository;
    @Mock
    private OrderRepository orderRepository;
    @Mock
    private OrderItemRepository orderItemRepository;

    private KittingService service;
    private UUID tenantId;
    private UUID orderId;

    @BeforeEach
    void setUp() {
        service = new KittingService(kitTemplateRepository, orderRepository, orderItemRepository, new ObjectMapper());
        tenantId = UUID.randomUUID();
        orderId = UUID.randomUUID();
    }

    private NxKitTemplate kit(String sku) {
        return NxKitTemplate.builder()
                .tenantId(tenantId)
                .kitSku(sku)
                .name("Gift Set")
                .components("[{\"sku\":\"COMP-A\",\"quantity\":1},{\"sku\":\"COMP-B\",\"quantity\":2}]")
                .isActive(true)
                .build();
    }

    @Test
    void explodePlan_ExpandsKitIntoComponents_AndKeepsPlainLines() {
        NxOrderItem kitLine = NxOrderItem.builder()
                .orderId(orderId).sku("KIT-1").quantity(2).build();
        NxOrderItem plainLine = NxOrderItem.builder()
                .orderId(orderId).sku("SKU-X").quantity(1).build();

        when(orderRepository.findById(orderId)).thenReturn(java.util.Optional.of(new com.nexus.oms.entity.NxOrder()));
        when(orderItemRepository.findByOrderId(orderId)).thenReturn(List.of(kitLine, plainLine));
        when(kitTemplateRepository.findByTenantIdAndKitSku(tenantId, "KIT-1")).thenReturn(Optional.of(kit("KIT-1")));
        when(kitTemplateRepository.findByTenantIdAndKitSku(tenantId, "SKU-X")).thenReturn(Optional.empty());

        List<Map<String, Object>> plan = service.explodePlan(tenantId, orderId);

        Map<String, Object> componentA = plan.stream()
                .filter(l -> "COMP-A".equals(l.get("sku"))).findFirst().orElseThrow();
        Map<String, Object> componentB = plan.stream()
                .filter(l -> "COMP-B".equals(l.get("sku"))).findFirst().orElseThrow();

        assertEquals(2, componentA.get("quantity"));
        assertEquals(4, componentB.get("quantity"));
        assertTrue((Boolean) plan.get(1).get("kit"));
    }

    @Test
    void explodeAndPersist_SavesComponentLines() {
        NxOrderItem kitLine = NxOrderItem.builder()
                .orderId(orderId).sku("KIT-1").quantity(2).build();

        when(orderRepository.findById(orderId)).thenReturn(java.util.Optional.of(new com.nexus.oms.entity.NxOrder()));
        when(orderItemRepository.findByOrderId(orderId)).thenReturn(List.of(kitLine));
        when(kitTemplateRepository.findByTenantIdAndKitSku(tenantId, "KIT-1")).thenReturn(Optional.of(kit("KIT-1")));

        List<Map<String, Object>> plan = service.explodeAndPersist(tenantId, orderId);

        assertEquals(2, plan.size());
    }

    @Test
    void createTemplate_RejectsEmptyComponents() {
        NxKitTemplate template = NxKitTemplate.builder()
                .kitSku("KIT-1")
                .components("[]")
                .build();

        assertThrows(BadRequestException.class, () -> service.createTemplate(tenantId, template));
    }

    @Test
    void hasKitLines_DetectsActiveKitSku() {
        when(kitTemplateRepository.findByTenantIdAndKitSku(tenantId, "KIT-1")).thenReturn(Optional.of(kit("KIT-1")));

        assertTrue(service.hasKitLines(tenantId, List.of(kitLine("KIT-1"))));
        assertFalse(service.hasKitLines(tenantId, List.of(kitLine("SKU-X"))));
    }

    private Object kitLine(String sku) {
        return new Object() {
            public String getSku() {
                return sku;
            }
        };
    }
}
