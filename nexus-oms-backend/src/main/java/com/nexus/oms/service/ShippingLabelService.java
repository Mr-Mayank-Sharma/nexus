package com.nexus.oms.service;

import com.nexus.oms.entity.NxCarrierLabelConfig;
import com.nexus.oms.entity.NxShippingLabel;
import com.nexus.oms.exception.BadRequestException;
import com.nexus.oms.exception.ResourceNotFoundException;
import com.nexus.oms.integration.carrier.CarrierLabelAdapter;
import com.nexus.oms.integration.carrier.CarrierShipmentRequest;
import com.nexus.oms.integration.carrier.CarrierShipmentResponse;
import com.nexus.oms.kafka.KafkaProducerService;
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
    private final CarrierLabelConfigService carrierLabelConfigService;
    private final KafkaProducerService kafkaProducerService;

    public ShippingLabelService(ShippingLabelRepository shippingLabelRepository,
                                CarrierLabelConfigService carrierLabelConfigService,
                                KafkaProducerService kafkaProducerService) {
        this.shippingLabelRepository = shippingLabelRepository;
        this.carrierLabelConfigService = carrierLabelConfigService;
        this.kafkaProducerService = kafkaProducerService;
    }

    @Transactional
    public NxShippingLabel generateLabel(NxShippingLabel label) {
        UUID tenantId = TenantContext.getCurrentTenantId();
        label.setTenantId(tenantId);

        if (label.getLabelSource() == null) {
            label.setLabelSource("SIMULATED");
        }
        if (label.getTrackingNumber() == null) {
            label.setTrackingNumber(generateTrackingNumber(label.getCarrier()));
        }
        if (label.getLabelUrl() == null) {
            label.setLabelUrl("/api/v1/labels/" + label.getId() + "/download");
        }

        label = shippingLabelRepository.save(label);
        log.info("Generated shipping label for order {}: tracking={} source={}",
                label.getOrderNumber(), label.getTrackingNumber(), label.getLabelSource());
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
            if (label.getLabelSource() == null) {
                label.setLabelSource("SIMULATED");
            }
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
                .labelSource("SIMULATED")
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

    /**
     * Real carrier purchase path (T-12). Resolves the tenant's carrier config,
     * calls the configured adapter, persists the purchased label and emits
     * the {@code order.label_generated} Kafka event. Idempotent: re-posting
     * the same order returns the existing label instead of re-purchasing.
     */
    @Transactional
    public NxShippingLabel generateCarrierLabel(NxShippingLabel label) {
        UUID tenantId = TenantContext.getCurrentTenantId();
        label.setTenantId(tenantId);

        if (label.getCarrier() == null || label.getCarrier().isBlank()) {
            throw new BadRequestException("carrier is required for carrier label generation");
        }

        // Idempotency: existing label for this order+carrier is returned as-is.
        if (label.getOrderId() != null) {
            List<NxShippingLabel> existing = shippingLabelRepository.findByOrderId(label.getOrderId());
            Optional<NxShippingLabel> sameCarrier = existing.stream()
                    .filter(l -> l.getCarrier().equalsIgnoreCase(label.getCarrier()))
                    .filter(l -> "CARRIER".equals(l.getLabelSource()))
                    .findFirst();
            if (sameCarrier.isPresent()) {
                log.info("Carrier label already exists for order {} carrier {} — returning existing",
                        label.getOrderNumber(), label.getCarrier());
                return sameCarrier.get();
            }
        }

        NxCarrierLabelConfig config = carrierLabelConfigService.getConfigForCarrier(label.getCarrier());
        CarrierLabelAdapter adapter = carrierLabelConfigService.resolveAdapter(config.getAdapterName());

        CarrierShipmentRequest shipmentRequest = CarrierShipmentRequest.builder()
                .tenantId(tenantId)
                .orderId(label.getOrderId())
                .orderNumber(label.getOrderNumber())
                .carrierCode(label.getCarrier())
                .serviceType(label.getServiceType())
                .fromName(label.getFromName())
                .fromAddress(label.getFromAddress())
                .toName(label.getToName())
                .toAddress(label.getToAddress())
                .weight(label.getWeight())
                .dimensions(label.getDimensions())
                .build();

        CarrierShipmentResponse response = adapter.createShipment(shipmentRequest);
        if (!response.isSuccess()) {
            throw new BadRequestException("Carrier " + label.getCarrier() + " rejected shipment: "
                    + response.getRawResponse());
        }

        label.setTrackingNumber(response.getTrackingNumber());
        label.setLabelBase64(response.getLabelBase64());
        label.setLabelFormat(response.getLabelFormat() != null ? response.getLabelFormat() : adapter.getLabelFormat());
        label.setAdapterName(adapter.getName());
        label.setLabelSource("CARRIER");
        label.setStatus("GENERATED");
        if (label.getGeneratedAt() == null) {
            label.setGeneratedAt(LocalDateTime.now());
        }
        if (label.getLabelUrl() == null) {
            label.setLabelUrl("/api/v1/labels/" + label.getId() + "/download");
        }

        shippingLabelRepository.save(label);
        log.info("Carrier label purchased for order {}: carrier={} tracking={} adapter={}",
                label.getOrderNumber(), label.getCarrier(), label.getTrackingNumber(), adapter.getName());

        kafkaProducerService.publish("order.label_generated",
                "{\"orderId\":\"" + label.getOrderId() + "\",\"orderNumber\":\"" + label.getOrderNumber()
                        + "\",\"carrier\":\"" + label.getCarrier() + "\",\"trackingNumber\":\""
                        + label.getTrackingNumber() + "\",\"labelSource\":\"CARRIER\"}");
        return label;
    }

    /** Required-field validation for a label against its carrier adapter. */
    public List<String> validateLabelForCarrier(UUID id) {
        NxShippingLabel label = getLabel(id);
        if (label.getAdapterName() == null) {
            throw new BadRequestException("Label " + id + " has no adapter (not a carrier label)");
        }
        CarrierLabelAdapter adapter = carrierLabelConfigService.resolveAdapter(label.getAdapterName());
        return adapter.validateLabel(label);
    }

    /** Full label payload (incl. base64) for download/preview. */
    public NxShippingLabel getLabelWithData(UUID id) {
        return getLabel(id);
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
