package com.nexus.oms.service;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.nexus.oms.dto.ApiResponse;
import com.nexus.oms.entity.NxEdiDocument;
import com.nexus.oms.entity.NxEdiPartner;
import com.nexus.oms.entity.NxOrder;
import com.nexus.oms.exception.BadRequestException;
import com.nexus.oms.exception.ResourceNotFoundException;
import com.nexus.oms.repository.EdiDocumentRepository;
import com.nexus.oms.repository.EdiPartnerRepository;
import com.nexus.oms.repository.OrderRepository;
import com.nexus.oms.security.TenantContext;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.*;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

@Service
public class EdiAutomationService {

    private static final Logger log = LoggerFactory.getLogger(EdiAutomationService.class);
    private static final ObjectMapper MAPPER = new ObjectMapper();

    private final EdiDocumentRepository ediDocumentRepository;
    private final EdiPartnerRepository ediPartnerRepository;
    private final OrderRepository orderRepository;

    public EdiAutomationService(EdiDocumentRepository ediDocumentRepository,
                                 EdiPartnerRepository ediPartnerRepository,
                                 OrderRepository orderRepository) {
        this.ediDocumentRepository = ediDocumentRepository;
        this.ediPartnerRepository = ediPartnerRepository;
        this.orderRepository = orderRepository;
    }

    public Page<NxEdiDocument> getDocuments(String docType, String status, Pageable pageable) {
        UUID tenantId = TenantContext.getCurrentTenantId();
        if (docType != null && !docType.isBlank()) {
            return ediDocumentRepository.findByTenantIdAndDocType(tenantId, docType, pageable);
        }
        if (status != null && !status.isBlank()) {
            return ediDocumentRepository.findByTenantIdAndParsedStatus(tenantId, status, pageable);
        }
        return ediDocumentRepository.findByTenantId(tenantId, pageable);
    }

    public NxEdiDocument getDocument(UUID id) {
        return ediDocumentRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("EDI document not found: " + id));
    }

    @Transactional
    public NxEdiDocument uploadAndParse(String filename, String rawContent, String docType) {
        UUID tenantId = TenantContext.getCurrentTenantId();

        if (rawContent == null || rawContent.isBlank()) {
            throw new BadRequestException("EDI content cannot be empty");
        }

        NxEdiDocument doc = NxEdiDocument.builder()
                .tenantId(tenantId)
                .docType(docType)
                .filename(filename)
                .rawContent(rawContent)
                .parsedStatus("PENDING")
                .build();

        doc = ediDocumentRepository.save(doc);

        try {
            Map<String, Object> parsedData = parseEdiContent(rawContent, docType);
            doc.setParsedData(MAPPER.writeValueAsString(parsedData));
            doc.setParsedStatus("PARSED");

            extractControlNumbers(rawContent, doc);

            if (parsedData.containsKey("partnerId")) {
                doc.setPartnerId((String) parsedData.get("partnerId"));
                doc.setPartnerName((String) parsedData.get("partnerName"));
            }

            List<String> errors = validateParsedData(parsedData, docType);
            if (!errors.isEmpty()) {
                doc.setValidationErrors(MAPPER.writeValueAsString(errors));
                doc.setParsedStatus("VALIDATED");
            }

            if ("850".equals(docType) && parsedData.containsKey("orderData")) {
                @SuppressWarnings("unchecked")
                Map<String, Object> orderData = (Map<String, Object>) parsedData.get("orderData");
                NxOrder order = createOrderFromEdi(orderData, tenantId, doc.getId());
                doc.setOrderId(order.getId());
            }

            doc.setProcessedAt(LocalDateTime.now());
        } catch (Exception e) {
            log.error("EDI parsing failed for {}: {}", filename, e.getMessage());
            doc.setParsedStatus("FAILED");
            doc.setErrorMessage(e.getMessage());
        }

        return ediDocumentRepository.save(doc);
    }

    @Transactional
    public NxEdiDocument reprocess(UUID id) {
        NxEdiDocument doc = getDocument(id);
        doc.setParsedStatus("PENDING");
        doc.setErrorMessage(null);
        doc.setValidationErrors(null);
        ediDocumentRepository.save(doc);

        return uploadAndParse(doc.getFilename(), doc.getRawContent(), doc.getDocType());
    }

    public Map<String, Object> getKPIs() {
        UUID tenantId = TenantContext.getCurrentTenantId();
        Map<String, Object> kpis = new HashMap<>();
        kpis.put("totalDocuments", ediDocumentRepository.countByTenantIdAndParsedStatus(tenantId, null));
        kpis.put("pending", ediDocumentRepository.countByTenantIdAndParsedStatus(tenantId, "PENDING"));
        kpis.put("parsed", ediDocumentRepository.countByTenantIdAndParsedStatus(tenantId, "PARSED"));
        kpis.put("validated", ediDocumentRepository.countByTenantIdAndParsedStatus(tenantId, "VALIDATED"));
        kpis.put("failed", ediDocumentRepository.countByTenantIdAndParsedStatus(tenantId, "FAILED"));
        return kpis;
    }

    public List<NxEdiPartner> getPartners() {
        return ediPartnerRepository.findByTenantId(TenantContext.getCurrentTenantId());
    }

    @Transactional
    public NxEdiPartner createPartner(NxEdiPartner partner) {
        partner.setTenantId(TenantContext.getCurrentTenantId());
        return ediPartnerRepository.save(partner);
    }

    // ---- Private EDI parsing ----

    private Map<String, Object> parseEdiContent(String content, String docType) {
        return switch (docType) {
            case "850" -> parse850(content);
            case "856" -> parse856(content);
            case "810" -> parse810(content);
            default -> throw new BadRequestException("Unsupported EDI document type: " + docType);
        };
    }

    private Map<String, Object> parse850(String content) {
        Map<String, Object> result = new LinkedHashMap<>();
        List<Map<String, Object>> items = new ArrayList<>();

        // Extract basic header info
        extractSegment(content, "BEG", data -> {
            result.put("transactionSet", "850");
            result.put("purchaseOrderType", safeGet(data, 1));
            result.put("purchaseOrderNumber", safeGet(data, 2));
            result.put("releaseNumber", safeGet(data, 3));
            result.put("orderDate", safeGet(data, 4));
        });

        extractSegment(content, "N1", data -> {
            if ("BY".equals(safeGet(data, 1))) {
                result.put("partnerId", safeGet(data, 3));
                result.put("partnerName", safeGet(data, 2));
                result.put("buyerCode", safeGet(data, 3));
            }
            if ("ST".equals(safeGet(data, 1))) {
                result.put("shipToName", safeGet(data, 2));
            }
        });

        extractSegment(content, "N3", data -> {
            result.putIfAbsent("addressLine1", safeGet(data, 1));
        });
        extractSegment(content, "N4", data -> {
            result.putIfAbsent("city", safeGet(data, 1));
            result.putIfAbsent("state", safeGet(data, 2));
            result.putIfAbsent("zip", safeGet(data, 3));
        });

        // Extract PO1 segments (line items)
        Pattern po1Pattern = Pattern.compile("PO1\\*([^~]+)~?", Pattern.MULTILINE);
        Matcher po1Matcher = po1Pattern.matcher(content);
        while (po1Matcher.find()) {
            String[] fields = po1Matcher.group(1).split("\\*");
            Map<String, Object> item = new LinkedHashMap<>();
            item.put("lineNumber", safeGet(fields, 0));
            item.put("quantityOrdered", safeGet(fields, 1));
            item.put("unitOfMeasure", safeGet(fields, 2));
            item.put("unitPrice", safeGet(fields, 3));
            if (fields.length > 5 && "VN".equals(safeGet(fields, 5))) {
                item.put("vendorPartNumber", safeGet(fields, 6));
            }
            if (fields.length > 7 && "UP".equals(safeGet(fields, 7))) {
                item.put("upc", safeGet(fields, 8));
            }
            if (fields.length > 9 && "BP".equals(safeGet(fields, 9))) {
                item.put("buyerPartNumber", safeGet(fields, 10));
            }
            items.add(item);
        }

        result.put("items", items);

        // Build order data
        Map<String, Object> orderData = new LinkedHashMap<>();
        orderData.put("channel", "EDI");
        orderData.put("channelOrderId", result.get("purchaseOrderNumber"));
        orderData.put("status", "PENDING");
        orderData.put("ediDocumentType", "850");
        orderData.put("items", items);

        result.put("orderData", orderData);

        return result;
    }

    private Map<String, Object> parse856(String content) {
        Map<String, Object> result = new LinkedHashMap<>();
        List<Map<String, Object>> packages = new ArrayList<>();

        extractSegment(content, "BSN", data -> {
            result.put("transactionSet", "856");
            result.put("shipNoticeNumber", safeGet(data, 1));
            result.put("shipDate", safeGet(data, 2));
            result.put("shipTime", safeGet(data, 3));
        });

        extractSegment(content, "REF", data -> {
            if ("PO".equals(safeGet(data, 1))) {
                result.put("purchaseOrderNumber", safeGet(data, 2));
            }
        });

        extractSegment(content, "TD1", data -> {
            result.put("packageCount", safeGet(data, 1));
            result.put("packageType", safeGet(data, 2));
        });

        extractSegment(content, "TD5", data -> {
            result.put("carrierCode", safeGet(data, 2));
            result.put("carrierName", safeGet(data, 3));
            result.put("serviceLevel", safeGet(data, 4));
        });

        extractSegment(content, "TD3", data -> {
            result.put("trackingNumber", safeGet(data, 2));
            result.put("packageId", safeGet(data, 3));
        });

        // Extract HL segments with MAN for serial numbers
        Pattern hlPattern = Pattern.compile("HL\\*([^~]+)~?(?:[^~]*~?)*?MAN\\*([^~]+)~?", Pattern.MULTILINE);
        Matcher hlMatcher = hlPattern.matcher(content);
        while (hlMatcher.find()) {
            Map<String, Object> pkg = new LinkedHashMap<>();
            pkg.put("hlData", hlMatcher.group(1));
            pkg.put("markNumbers", hlMatcher.group(2));
            packages.add(pkg);
        }

        // Extract PRF (purchase order reference)
        extractSegment(content, "PRF", data -> {
            result.putIfAbsent("purchaseOrderNumber", safeGet(data, 1));
        });

        result.put("packages", packages);
        return result;
    }

    private Map<String, Object> parse810(String content) {
        Map<String, Object> result = new LinkedHashMap<>();
        List<Map<String, Object>> items = new ArrayList<>();

        extractSegment(content, "BIG", data -> {
            result.put("transactionSet", "810");
            result.put("invoiceDate", safeGet(data, 1));
            result.put("invoiceNumber", safeGet(data, 2));
            result.put("purchaseOrderNumber", safeGet(data, 3));
        });

        extractSegment(content, "N1", data -> {
            if ("BY".equals(safeGet(data, 1))) {
                result.put("buyerName", safeGet(data, 2));
            }
            if ("SE".equals(safeGet(data, 1))) {
                result.put("sellerName", safeGet(data, 2));
            }
        });

        // Extract IT1 segments (invoice items)
        Pattern it1Pattern = Pattern.compile("IT1\\*([^~]+)~?", Pattern.MULTILINE);
        Matcher it1Matcher = it1Pattern.matcher(content);
        while (it1Matcher.find()) {
            String[] fields = it1Matcher.group(1).split("\\*");
            Map<String, Object> item = new LinkedHashMap<>();
            item.put("lineNumber", safeGet(fields, 0));
            item.put("quantityInvoiced", safeGet(fields, 1));
            item.put("unitOfMeasure", safeGet(fields, 2));
            item.put("unitPrice", safeGet(fields, 3));
            if (fields.length > 5) {
                item.put("productIdQualifier", safeGet(fields, 5));
                item.put("productId", safeGet(fields, 6));
            }
            items.add(item);
        }
        result.put("items", items);

        // Extract TDS (total monetary value)
        extractSegment(content, "TDS", data -> {
            result.put("totalInvoiceAmount", safeGet(data, 1));
        });

        return result;
    }

    private void extractControlNumbers(String content, NxEdiDocument doc) {
        Pattern isaPattern = Pattern.compile("ISA\\*[^*]*\\*[^*]*\\*[^*]*\\*[^*]*\\*[^*]*\\*[^*]*\\*[^*]*\\*[^*]*\\*([^*]+)");
        Matcher isaMatcher = isaPattern.matcher(content);
        if (isaMatcher.find()) {
            doc.setInterchangeControlNumber(isaMatcher.group(1));
        }

        Pattern gsPattern = Pattern.compile("GS\\*[^*]*\\*[^*]*\\*[^*]*\\*[^*]*\\*[^*]*\\*[^*]*\\*([^*]+)");
        Matcher gsMatcher = gsPattern.matcher(content);
        if (gsMatcher.find()) {
            doc.setGroupControlNumber(gsMatcher.group(1));
        }

        Pattern stPattern = Pattern.compile("ST\\*[^*]*\\*([^*]+)");
        Matcher stMatcher = stPattern.matcher(content);
        if (stMatcher.find()) {
            doc.setControlNumber(stMatcher.group(1));
        }
    }

    private List<String> validateParsedData(Map<String, Object> data, String docType) {
        List<String> errors = new ArrayList<>();
        switch (docType) {
            case "850" -> {
                if (data.get("purchaseOrderNumber") == null)
                    errors.add("Missing purchase order number (BEG02)");
                if (data.get("orderDate") == null)
                    errors.add("Missing order date (BEG04)");
                List<?> items = (List<?>) data.getOrDefault("items", Collections.emptyList());
                if (items.isEmpty())
                    errors.add("No line items found (PO1 segments)");
            }
            case "856" -> {
                if (data.get("shipNoticeNumber") == null)
                    errors.add("Missing ship notice number (BSN01)");
            }
            case "810" -> {
                if (data.get("invoiceNumber") == null)
                    errors.add("Missing invoice number (BIG02)");
                if (data.get("totalInvoiceAmount") == null)
                    errors.add("Missing total amount (TDS01)");
            }
        }
        return errors;
    }

    private NxOrder createOrderFromEdi(Map<String, Object> orderData, UUID tenantId, UUID ediDocId) {
        NxOrder order = NxOrder.builder()
                .tenantId(tenantId)
                .channel("EDI")
                .channelOrderId((String) orderData.get("channelOrderId"))
                .status("PENDING")
                .build();

        try {
            String metadata = MAPPER.writeValueAsString(Map.of(
                "ediDocumentId", ediDocId.toString(),
                "ediType", "850",
                "ediPurchaseOrderNumber", orderData.get("channelOrderId")
            ));
            order.setMetadata(metadata);
        } catch (JsonProcessingException e) {
            log.warn("Failed to serialize EDI metadata: {}", e.getMessage());
        }

        return orderRepository.save(order);
    }

    private void extractSegment(String content, String segId, SegmentConsumer consumer) {
        Pattern pattern = Pattern.compile(segId + "\\*([^~]+)~?", Pattern.MULTILINE);
        Matcher matcher = pattern.matcher(content);
        if (matcher.find()) {
            consumer.accept(matcher.group(1).split("\\*"));
        }
    }

    private String safeGet(String[] arr, int index) {
        return arr != null && index < arr.length ? arr[index].trim() : null;
    }

    @FunctionalInterface
    private interface SegmentConsumer {
        void accept(String[] data);
    }
}
