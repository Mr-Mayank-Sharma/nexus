package com.nexus.oms.service;

import com.nexus.oms.dto.AsnRequest;
import com.nexus.oms.dto.InventoryReceiptRequest;
import com.nexus.oms.entity.NxAsn;
import com.nexus.oms.entity.NxAsnLine;
import com.nexus.oms.exception.BadRequestException;
import com.nexus.oms.exception.ResourceNotFoundException;
import com.nexus.oms.repository.NxAsnLineRepository;
import com.nexus.oms.repository.NxAsnRepository;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Map;
import java.util.UUID;

@Service
public class AsnService {

    private static final double RECEIVING_TOLERANCE_PCT = 10.0;

    private final NxAsnRepository asnRepository;
    private final NxAsnLineRepository asnLineRepository;
    private final InventoryReceiptService inventoryReceiptService;
    private final SlottingService slottingService;

    public AsnService(NxAsnRepository asnRepository,
                      NxAsnLineRepository asnLineRepository,
                      InventoryReceiptService inventoryReceiptService,
                      SlottingService slottingService) {
        this.asnRepository = asnRepository;
        this.asnLineRepository = asnLineRepository;
        this.inventoryReceiptService = inventoryReceiptService;
        this.slottingService = slottingService;
    }

    public Page<NxAsn> getAsns(UUID tenantId, String status, Pageable pageable) {
        if (status != null && !status.isBlank()) {
            return asnRepository.findByTenantIdAndStatus(tenantId, status, pageable);
        }
        return asnRepository.findByTenantId(tenantId, pageable);
    }

    public NxAsn getAsn(UUID id) {
        return asnRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Asn", id));
    }

    @Transactional
    public NxAsn createAsn(UUID tenantId, AsnRequest request) {
        if (asnRepository.existsByTenantIdAndAsnNumber(tenantId, request.getAsnNumber())) {
            throw new BadRequestException("ASN " + request.getAsnNumber() + " already exists");
        }
        if (request.getLines().stream().anyMatch(l -> l.getExpectedQty() <= 0)) {
            throw new BadRequestException("Expected quantity must be positive");
        }

        NxAsn asn = NxAsn.builder()
                .tenantId(tenantId)
                .asnNumber(request.getAsnNumber())
                .nodeId(request.getNodeId())
                .supplierName(request.getSupplierName())
                .carrierCode(request.getCarrierCode())
                .trackingNumber(request.getTrackingNumber())
                .purchaseOrderNumber(request.getPurchaseOrderNumber())
                .shipDate(request.getShipDate())
                .expectedArrivalDate(request.getExpectedArrivalDate())
                .status("OPEN")
                .source("MANUAL")
                .build();

        request.getLines().forEach(l -> {
            NxAsnLine line = NxAsnLine.builder()
                    .asn(asn)
                    .tenantId(tenantId)
                    .sku(l.getSku())
                    .productName(l.getProductName())
                    .expectedQty(l.getExpectedQty())
                    .receivedQty(0)
                    .lotNumber(l.getLotNumber())
                    .expiryDate(l.getExpiryDate())
                    .status("PENDING")
                    .build();
            asn.getLines().add(line);
        });

        return asnRepository.save(asn);
    }

    /**
     * Builds an ASN from parsed EDI 856 data ({@code shipNoticeNumber}, carrier,
     * tracking, PO, and {@code items}). Source is EDI_856 and the originating
     * document is linked. No-op returns null when the notice number is missing.
     */
    @Transactional
    public NxAsn createAsnFromEdi(UUID tenantId, Map<String, Object> parsed856, UUID ediDocumentId) {
        String asnNumber = parsed856.get("shipNoticeNumber") != null
                ? String.valueOf(parsed856.get("shipNoticeNumber")) : null;
        if (asnNumber == null || asnNumber.isBlank()) {
            return null;
        }
        if (asnRepository.existsByTenantIdAndAsnNumber(tenantId, asnNumber)) {
            return asnRepository.findByTenantIdAndAsnNumber(tenantId, asnNumber).orElse(null);
        }

        NxAsn asn = NxAsn.builder()
                .tenantId(tenantId)
                .asnNumber(asnNumber)
                .purchaseOrderNumber(parsed856.get("purchaseOrderNumber") != null
                        ? String.valueOf(parsed856.get("purchaseOrderNumber")) : null)
                .carrierCode(parsed856.get("carrierCode") != null
                        ? String.valueOf(parsed856.get("carrierCode")) : null)
                .supplierName(parsed856.get("supplierName") != null
                        ? String.valueOf(parsed856.get("supplierName")) : null)
                .trackingNumber(parsed856.get("trackingNumber") != null
                        ? String.valueOf(parsed856.get("trackingNumber")) : null)
                .ediDocumentId(ediDocumentId)
                .status("OPEN")
                .source("EDI_856")
                .build();

        @SuppressWarnings("unchecked")
        List<Map<String, Object>> items = (List<Map<String, Object>>) parsed856.getOrDefault("items", List.of());
        for (Map<String, Object> item : items) {
            Object skuObj = item.get("productId");
            Object qtyObj = item.get("quantity");
            if (skuObj == null || qtyObj == null) continue;
            String sku = String.valueOf(skuObj);
            if (sku.isBlank()) continue;
            int qty;
            try {
                qty = Integer.parseInt(String.valueOf(qtyObj));
            } catch (NumberFormatException e) {
                continue;
            }
            NxAsnLine line = NxAsnLine.builder()
                    .asn(asn)
                    .tenantId(tenantId)
                    .sku(sku)
                    .expectedQty(qty)
                    .receivedQty(0)
                    .status("PENDING")
                    .build();
            asn.getLines().add(line);
        }

        if (asn.getLines().isEmpty()) {
            return null;
        }
        return asnRepository.save(asn);
    }

    @Transactional
    public NxAsn receiveLine(UUID asnId, UUID lineId, int receivedQty, String receivedBy) {
        NxAsn asn = getAsn(asnId);
        if (!"OPEN".equals(asn.getStatus())) {
            throw new IllegalStateException("ASN is " + asn.getStatus() + ", not OPEN");
        }
        if (asn.getNodeId() == null) {
            throw new BadRequestException("ASN " + asn.getAsnNumber() + " has no destination warehouse; receiving requires a nodeId");
        }

        NxAsnLine line = asn.getLines().stream()
                .filter(l -> l.getId().equals(lineId))
                .findFirst()
                .orElseThrow(() -> new ResourceNotFoundException("AsnLine", lineId));
        if ("RECEIVED".equals(line.getStatus())) {
            throw new BadRequestException("Line " + line.getSku() + " already fully received");
        }
        if (receivedQty <= 0) {
            throw new BadRequestException("Received quantity must be positive");
        }

        int newReceived = line.getReceivedQty() + receivedQty;
        int toleranceLimit = (int) Math.floor(line.getExpectedQty() * (1 + RECEIVING_TOLERANCE_PCT / 100.0));
        if (newReceived > toleranceLimit) {
            throw new BadRequestException("Over-receipt for SKU " + line.getSku() + ": " + newReceived
                    + " received vs " + line.getExpectedQty() + " expected (tolerance "
                    + RECEIVING_TOLERANCE_PCT + "%)");
        }

        InventoryReceiptRequest receiptRequest = new InventoryReceiptRequest();
        receiptRequest.setNodeId(asn.getNodeId());
        receiptRequest.setReceiptType("ASN");
        receiptRequest.setReferenceNumber(asn.getAsnNumber());
        receiptRequest.setSku(line.getSku());
        receiptRequest.setProductName(line.getProductName());
        receiptRequest.setQuantity(receivedQty);
        receiptRequest.setLotNumber(line.getLotNumber());
        receiptRequest.setExpiryDate(line.getExpiryDate());
        var receipt = inventoryReceiptService.createReceipt(asn.getTenantId(), receiptRequest);
        inventoryReceiptService.receiveInventory(receipt.getId(), receivedBy);

        if (asn.getNodeId() != null) {
            slottingService.recommendPutaway(line.getSku(), asn.getNodeId(), receivedQty, receivedBy);
        }

        line.setReceivedQty(newReceived);
        if (newReceived >= line.getExpectedQty()) {
            line.setStatus("RECEIVED");
        } else {
            line.setStatus("PARTIAL");
        }
        asnLineRepository.save(line);

        boolean allReceived = asn.getLines().stream().allMatch(l -> "RECEIVED".equals(l.getStatus()));
        if (allReceived) {
            asn.setStatus("COMPLETE");
            asn.setReceivedBy(receivedBy);
            asn.setReceivedAt(java.time.LocalDateTime.now());
        }
        return asnRepository.save(asn);
    }

    @Transactional
    public NxAsn closeAsn(UUID id) {
        NxAsn asn = getAsn(id);
        if ("COMPLETE".equals(asn.getStatus())) {
            return asn;
        }
        asn.setStatus("CLOSED");
        return asnRepository.save(asn);
    }

    @Transactional
    public void deleteAsn(UUID id) {
        asnRepository.deleteById(id);
    }
}
