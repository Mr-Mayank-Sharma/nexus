package com.nexus.oms.service;

import com.nexus.oms.entity.NxShippingLabel;
import com.nexus.oms.exception.BadRequestException;
import com.nexus.oms.exception.ResourceNotFoundException;
import com.nexus.oms.repository.ShippingLabelRepository;
import com.nexus.oms.security.TenantContext;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.*;

@Service
public class ShippingLabelService {

    private static final Logger log = LoggerFactory.getLogger(ShippingLabelService.class);

    private final ShippingLabelRepository shippingLabelRepository;

    public ShippingLabelService(ShippingLabelRepository shippingLabelRepository) {
        this.shippingLabelRepository = shippingLabelRepository;
    }

    @Transactional
    public NxShippingLabel generateLabel(NxShippingLabel label) {
        UUID tenantId = TenantContext.getCurrentTenantId();
        label.setTenantId(tenantId);

        if (label.getTrackingNumber() == null) {
            label.setTrackingNumber(generateTrackingNumber(label.getCarrier()));
        }
        if (label.getLabelUrl() == null) {
            label.setLabelUrl("/api/v1/labels/" + label.getId() + "/download");
        }

        label = shippingLabelRepository.save(label);
        log.info("Generated shipping label for order {}: tracking={}", label.getOrderNumber(), label.getTrackingNumber());
        return label;
    }

    @Transactional
    public List<NxShippingLabel> generateLabelsForOrder(UUID orderId, String orderNumber, List<NxShippingLabel> labels) {
        UUID tenantId = TenantContext.getCurrentTenantId();
        List<NxShippingLabel> generated = new ArrayList<>();

        for (NxShippingLabel label : labels) {
            label.setTenantId(tenantId);
            label.setOrderId(orderId);
            label.setOrderNumber(orderNumber);
            if (label.getTrackingNumber() == null) {
                label.setTrackingNumber(generateTrackingNumber(label.getCarrier()));
            }
            generated.add(shippingLabelRepository.save(label));
        }

        log.info("Generated {} labels for order {}", generated.size(), orderNumber);
        return generated;
    }

    @Transactional
    public NxShippingLabel generateBopisLabel(UUID pickupOrderId, String orderNumber, String fromName, String fromAddress) {
        UUID tenantId = TenantContext.getCurrentTenantId();

        NxShippingLabel label = NxShippingLabel.builder()
                .tenantId(tenantId)
                .pickupOrderId(pickupOrderId)
                .orderNumber(orderNumber)
                .carrier("LOCAL")
                .serviceType("SAME_DAY")
                .trackingNumber(generateTrackingNumber("LOCAL"))
                .status("GENERATED")
                .fromName(fromName)
                .fromAddress(fromAddress)
                .labelUrl("/api/v1/labels/bopis/" + pickupOrderId)
                .build();

        label = shippingLabelRepository.save(label);
        log.info("Generated BOPIS label for pickup {}: tracking={}", pickupOrderId, label.getTrackingNumber());
        return label;
    }

    @Transactional
    public NxShippingLabel markPrinted(UUID id) {
        NxShippingLabel label = shippingLabelRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("ShippingLabel", id));
        label.setStatus("PRINTED");
        label.setPrintedAt(LocalDateTime.now());
        return shippingLabelRepository.save(label);
    }

    @Transactional
    public NxShippingLabel markAttached(UUID id) {
        NxShippingLabel label = shippingLabelRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("ShippingLabel", id));
        label.setStatus("ATTACHED");
        label.setAttachedAt(LocalDateTime.now());
        return shippingLabelRepository.save(label);
    }

    public NxShippingLabel getLabel(UUID id) {
        return shippingLabelRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("ShippingLabel", id));
    }

    public List<NxShippingLabel> getLabelsByOrder(UUID orderId) {
        return shippingLabelRepository.findByOrderId(orderId);
    }

    public NxShippingLabel getLabelByPickupOrder(UUID pickupOrderId) {
        return shippingLabelRepository.findByPickupOrderId(pickupOrderId);
    }

    public List<NxShippingLabel> getPendingLabels() {
        UUID tenantId = TenantContext.getCurrentTenantId();
        return shippingLabelRepository.findByTenantIdAndStatus(tenantId, "GENERATED");
    }

    public List<NxShippingLabel> getAllLabels() {
        UUID tenantId = TenantContext.getCurrentTenantId();
        return shippingLabelRepository.findByTenantId(tenantId);
    }

    private String generateTrackingNumber(String carrier) {
        String prefix = switch (carrier != null ? carrier.toUpperCase() : "GEN") {
            case "FEDEX" -> "FX";
            case "UPS" -> "UP";
            case "USPS" -> "US";
            case "DHL" -> "DH";
            case "LOCAL" -> "LC";
            default -> "GN";
        };
        return prefix + System.currentTimeMillis() + String.format("%04d", new Random().nextInt(10000));
    }
}
