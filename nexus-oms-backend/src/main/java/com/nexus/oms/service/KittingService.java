package com.nexus.oms.service;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.nexus.oms.entity.NxKitTemplate;
import com.nexus.oms.entity.NxOrder;
import com.nexus.oms.entity.NxOrderItem;
import com.nexus.oms.exception.BadRequestException;
import com.nexus.oms.exception.ResourceNotFoundException;
import com.nexus.oms.repository.KitTemplateRepository;
import com.nexus.oms.repository.OrderItemRepository;
import com.nexus.oms.repository.OrderRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.*;

@Service
public class KittingService {

    private final KitTemplateRepository kitTemplateRepository;
    private final OrderRepository orderRepository;
    private final OrderItemRepository orderItemRepository;
    private final ObjectMapper objectMapper;

    public KittingService(KitTemplateRepository kitTemplateRepository,
                          OrderRepository orderRepository,
                          OrderItemRepository orderItemRepository,
                          ObjectMapper objectMapper) {
        this.kitTemplateRepository = kitTemplateRepository;
        this.orderRepository = orderRepository;
        this.orderItemRepository = orderItemRepository;
        this.objectMapper = objectMapper;
    }

    public List<NxKitTemplate> listTemplates(UUID tenantId) {
        return kitTemplateRepository.findByTenantId(tenantId);
    }

    public NxKitTemplate getTemplate(UUID tenantId, UUID id) {
        return kitTemplateRepository.findById(id)
                .filter(t -> t.getTenantId().equals(tenantId))
                .orElseThrow(() -> new ResourceNotFoundException("KitTemplate", id));
    }

    @Transactional
    public NxKitTemplate createTemplate(UUID tenantId, NxKitTemplate template) {
        validateComponents(template.getComponents());
        template.setId(null);
        template.setTenantId(tenantId);
        template.setIsActive(template.getIsActive() == null ? true : template.getIsActive());
        return kitTemplateRepository.save(template);
    }

    @Transactional
    public NxKitTemplate updateTemplate(UUID tenantId, UUID id, NxKitTemplate template) {
        NxKitTemplate existing = getTemplate(tenantId, id);
        validateComponents(template.getComponents());
        existing.setKitSku(template.getKitSku());
        existing.setName(template.getName());
        existing.setComponents(template.getComponents());
        existing.setIsActive(template.getIsActive());
        return kitTemplateRepository.save(existing);
    }

    @Transactional
    public void deleteTemplate(UUID tenantId, UUID id) {
        kitTemplateRepository.delete(getTemplate(tenantId, id));
    }

    public List<Map<String, Object>> explodePlan(UUID tenantId, UUID orderId) {
        NxOrder order = orderRepository.findById(orderId)
                .orElseThrow(() -> new ResourceNotFoundException("Order", orderId));

        List<Map<String, Object>> plan = new ArrayList<>();
        for (NxOrderItem item : orderItemRepository.findByOrderId(orderId)) {
            Optional<NxKitTemplate> kit = kitTemplateRepository
                    .findByTenantIdAndKitSku(tenantId, item.getSku());
            if (kit.isEmpty() || !Boolean.TRUE.equals(kit.get().getIsActive())) {
                Map<String, Object> line = new LinkedHashMap<>();
                line.put("sku", item.getSku());
                line.put("quantity", item.getQuantity());
                line.put("kit", false);
                plan.add(line);
                continue;
            }
            List<Map<String, Object>> components = parseComponents(kit.get().getComponents());
            for (Map<String, Object> component : components) {
                Map<String, Object> line = new LinkedHashMap<>();
                line.put("sku", component.get("sku"));
                line.put("quantity", ((Number) component.get("quantity")).intValue() * item.getQuantity());
                line.put("kit", true);
                line.put("parentKitSku", item.getSku());
                plan.add(line);
            }
        }
        return plan;
    }

    public boolean hasKitLines(UUID tenantId, List<?> items) {
        for (Object item : items) {
            String sku = skuOf(item);
            if (sku == null || sku.isBlank()) {
                continue;
            }
            Optional<NxKitTemplate> kit = kitTemplateRepository.findByTenantIdAndKitSku(tenantId, sku);
            if (kit.isPresent() && Boolean.TRUE.equals(kit.get().getIsActive())) {
                return true;
            }
        }
        return false;
    }

    private String skuOf(Object item) {
        if (item instanceof Map) {
            Object sku = ((Map<?, ?>) item).get("sku");
            return sku != null ? sku.toString() : null;
        }
        try {
            return (String) item.getClass().getMethod("getSku").invoke(item);
        } catch (Exception e) {
            return null;
        }
    }

    @Transactional
    public List<Map<String, Object>> explodeAndPersist(UUID tenantId, UUID orderId) {
        NxOrder order = orderRepository.findById(orderId)
                .orElseThrow(() -> new ResourceNotFoundException("Order", orderId));
        List<Map<String, Object>> plan = new ArrayList<>();

        for (NxOrderItem item : orderItemRepository.findByOrderId(orderId)) {
            Optional<NxKitTemplate> kit = kitTemplateRepository
                    .findByTenantIdAndKitSku(tenantId, item.getSku());
            if (kit.isEmpty() || !Boolean.TRUE.equals(kit.get().getIsActive())) {
                Map<String, Object> line = new LinkedHashMap<>();
                line.put("sku", item.getSku());
                line.put("quantity", item.getQuantity());
                line.put("kit", false);
                plan.add(line);
                continue;
            }
            List<Map<String, Object>> components = parseComponents(kit.get().getComponents());
            for (Map<String, Object> component : components) {
                int qty = ((Number) component.get("quantity")).intValue() * item.getQuantity();
                NxOrderItem componentItem = NxOrderItem.builder()
                        .orderId(order.getId())
                        .sku((String) component.get("sku"))
                        .productName(kit.get().getName() + " component")
                        .quantity(qty)
                        .unitPrice(java.math.BigDecimal.ZERO)
                        .totalPrice(java.math.BigDecimal.ZERO)
                        .allocatedQty(0)
                        .build();
                orderItemRepository.save(componentItem);

                Map<String, Object> line = new LinkedHashMap<>();
                line.put("sku", component.get("sku"));
                line.put("quantity", qty);
                line.put("kit", true);
                line.put("parentKitSku", item.getSku());
                plan.add(line);
            }
        }
        return plan;
    }

    private void validateComponents(String componentsJson) {
        List<Map<String, Object>> components = parseComponents(componentsJson);
        if (components.isEmpty()) {
            throw new BadRequestException("Kit template must define at least one component");
        }
        for (Map<String, Object> component : components) {
            if (component.get("sku") == null || component.get("quantity") == null
                    || ((Number) component.get("quantity")).intValue() <= 0) {
                throw new BadRequestException("Each kit component needs a non-empty sku and a positive quantity");
            }
        }
    }

    private List<Map<String, Object>> parseComponents(String componentsJson) {
        try {
            JsonNode root = objectMapper.readTree(componentsJson);
            if (root == null || !root.isArray()) {
                throw new BadRequestException("Kit components must be a JSON array");
            }
            List<Map<String, Object>> components = new ArrayList<>();
            for (JsonNode node : root) {
                Map<String, Object> component = new LinkedHashMap<>();
                component.put("sku", node.path("sku").asText());
                component.put("quantity", node.path("quantity").asInt());
                components.add(component);
            }
            return components;
        } catch (JsonProcessingException e) {
            throw new BadRequestException("Invalid kit components JSON: " + e.getMessage());
        }
    }
}
