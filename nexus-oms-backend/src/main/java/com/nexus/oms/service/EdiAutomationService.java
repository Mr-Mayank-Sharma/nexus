package com.nexus.oms.service;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.nexus.oms.dto.ApiResponse;
import com.nexus.oms.entity.NxEdiDocument;
import com.nexus.oms.entity.NxEdiPartner;
import com.nexus.oms.entity.NxAsn;
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
    private final AsnService asnService;

    public EdiAutomationService(EdiDocumentRepository ediDocumentRepository,
                                 EdiPartnerRepository ediPartnerRepository,
                                 OrderRepository orderRepository,
                                 AsnService asnService) {
        this.ediDocumentRepository = ediDocumentRepository;
        this.ediPartnerRepository = ediPartnerRepository;
        this.orderRepository = orderRepository;
        this.asnService = asnService;
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

            if ("856".equals(docType) && parsedData.containsKey("shipments")) {
                @SuppressWarnings("unchecked")
                List<Map<String, Object>> shipments = (List<Map<String, Object>>) parsedData.get("shipments");
                for (Map<String, Object> shipment : shipments) {
                    NxAsn asn = asnService.createAsnFromEdi(tenantId, shipment, doc.getId());
                    if (asn != null && doc.getAsnId() == null) {
                        doc.setAsnId(asn.getId());
                    }
                }
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

    /**
     * Dry-run parse: returns parsed EDI data without persisting.
     * Validates structure, extracts fields, returns errors — no DB write.
     */
    /**
     * X12 997 — Functional Acknowledgment. Builds an outbound ack for a
     * previously received inbound document (850/856/810/940). Echoes the
     * interchange/group/transaction control numbers and reports AK1/AK2/AK9
     * accept/reject status. The ack itself is persisted as a docType "997"
     * document so the loop is auditable.
     */
    @Transactional
    public NxEdiDocument generate997Ack(UUID documentId, boolean accepted) {
        NxEdiDocument inbound = getDocument(documentId);
        if ("997".equals(inbound.getDocType())) {
            throw new BadRequestException("997 is an acknowledgment type and cannot be acknowledged itself");
        }

        String interchangedControl = inbound.getInterchangeControlNumber() != null
                ? inbound.getInterchangeControlNumber() : "000000001";
        String groupControl = inbound.getGroupControlNumber() != null
                ? inbound.getGroupControlNumber() : "1";
        String transactionControl = inbound.getControlNumber() != null
                ? inbound.getControlNumber() : "0001";
        String ackCode = accepted ? "A" : "R";

        StringBuilder sb = new StringBuilder();
        sb.append("ISA*00*          *00*          *ZZ*NEXUS           *ZZ*")
          .append(pad(inbound.getPartnerId() == null ? "PARTNER" : inbound.getPartnerId(), 15))
          .append("*").append(nowIso()).append("*").append(nowIso()).append("*")
          .append(ackCode).append("*00501*000000997*0*P*>~")
          .append("\n");
        sb.append("GS*FA*NEXUS*").append(inbound.getPartnerId() == null ? "PARTNER" : inbound.getPartnerId())
          .append("*").append(nowIso()).append("*").append(nowIso()).append("*1*X*005010~\n");
        sb.append("ST*997*0001~\n");
        sb.append("AK1*").append(inbound.getDocType()).append("*").append(groupControl).append("~\n");
        sb.append("AK2*").append(inbound.getDocType()).append("*").append(transactionControl).append("~\n");
        sb.append("AK5*").append(accepted ? "A" : "R").append("*NEXUS could not process the transaction~\n");
        sb.append("AK9*").append(accepted ? "A" : "R").append("*1*1*1*")
          .append(accepted ? "1" : "2").append("~\n");
        sb.append("SE*6*0001~\n");
        sb.append("GE*1*1~\n");
        sb.append("IEA*1*000000997~");

        NxEdiDocument ack = NxEdiDocument.builder()
                .tenantId(inbound.getTenantId())
                .docType("997")
                .filename("ack-" + inbound.getFilename())
                .rawContent(sb.toString())
                .parsedStatus("PARSED")
                .partnerId(inbound.getPartnerId())
                .partnerName(inbound.getPartnerName())
                .build();

        try {
            Map<String, Object> ackData = new LinkedHashMap<>();
            ackData.put("transactionSet", "997");
            ackData.put("acknowledges", inbound.getDocType());
            ackData.put("interchangeControlNumber", interchangedControl);
            ackData.put("groupControlNumber", groupControl);
            ackData.put("transactionControlNumber", transactionControl);
            ackData.put("ackCode", ackCode);
            ackData.put("accepted", accepted);
            ack.setParsedData(MAPPER.writeValueAsString(ackData));
        } catch (JsonProcessingException e) {
            log.warn("Failed to serialize 997 ack data: {}", e.getMessage());
        }

        return ediDocumentRepository.save(ack);
    }

    /**
     * X12 855 — Purchase Order Acknowledgment. Builds an outbound ack for an
     * order created from a 850. Echoes the PO number and per-line ACK
     * quantities (accepted/backordered/rejected). Persisted as docType "855"
     * for the audit loop.
     */
    @Transactional
    public NxEdiDocument generate855Ack(UUID orderId, String poNumber,
                                        List<Map<String, Object>> lineAcks) {
        NxOrder order = orderRepository.findById(orderId)
                .orElseThrow(() -> new ResourceNotFoundException("Order not found: " + orderId));
        UUID tenantId = TenantContext.getCurrentTenantId();

        StringBuilder sb = new StringBuilder();
        sb.append("ISA*00*          *00*          *ZZ*NEXUS           *ZZ*SUPPLIER        ")
          .append("*").append(nowIso()).append("*").append(nowIso()).append("*A*00501*000000855*0*P*>~\n");
        sb.append("GS*PO*NEXUS*SUPPLIER*").append(nowIso()).append("*").append(nowIso())
          .append("*1*X*005010~\n");
        sb.append("ST*855*0001~\n");
        sb.append("BAK*00*AC*").append(poNumber).append("*").append(nowIso()).append("~\n");

        if (lineAcks != null) {
            int line = 0;
            for (Map<String, Object> ack : lineAcks) {
                line++;
                sb.append("ACK*").append(ack.getOrDefault("status", "AC"))
                  .append("*").append(ack.getOrDefault("quantity", "1")).append("~")
                  .append("PO1*").append(line).append("*")
                  .append(ack.getOrDefault("quantity", "1")).append("*EA*")
                  .append(ack.getOrDefault("unitPrice", "0")).append("~");
            }
        }
        sb.append("CTT*").append(lineAcks == null ? 0 : lineAcks.size()).append("~\n");
        sb.append("SE*").append(lineAcks == null ? 4 : 4 + lineAcks.size()).append("*0001~\n");
        sb.append("GE*1*1~\n");
        sb.append("IEA*1*000000855~");

        NxEdiDocument ack = NxEdiDocument.builder()
                .tenantId(tenantId)
                .docType("855")
                .filename("ack-" + poNumber + ".855")
                .rawContent(sb.toString())
                .parsedStatus("PARSED")
                .orderId(orderId)
                .build();

        try {
            Map<String, Object> ackData = new LinkedHashMap<>();
            ackData.put("transactionSet", "855");
            ackData.put("poNumber", poNumber);
            ackData.put("acknowledgeCode", "AC");
            ackData.put("lineAcks", lineAcks == null ? List.of() : lineAcks);
            ack.setParsedData(MAPPER.writeValueAsString(ackData));
        } catch (JsonProcessingException e) {
            log.warn("Failed to serialize 855 ack data: {}", e.getMessage());
        }

        return ediDocumentRepository.save(ack);
    }

    public Map<String, Object> dryRun(String content, String docType) {
        if (content == null || content.isBlank()) {
            throw new BadRequestException("EDI content cannot be empty");
        }

        Map<String, Object> parsedData = parseEdiContent(content, docType);
        List<String> errors = validateParsedData(parsedData, docType);

        Map<String, Object> result = new LinkedHashMap<>();
        result.put("docType", docType);
        result.put("parsedData", parsedData);
        result.put("valid", errors.isEmpty());
        result.put("errors", errors);
        result.put("segmentCount", content.split("~").length);
        return result;
    }

    // ---- Private EDI parsing ----

    private Map<String, Object> parseEdiContent(String content, String docType) {
        return switch (docType) {
            case "850" -> parse850(content);
            case "856" -> parse856(content);
            case "810" -> parse810(content);
            case "940" -> parse940(content);
            default -> throw new BadRequestException("Unsupported EDI document type: " + docType);
        };
    }

    private Map<String, Object> parse850(String content) {
        Map<String, Object> result = new LinkedHashMap<>();
        List<Map<String, Object>> items = new ArrayList<>();

        // Extract basic header info
        extractSegment(content, "BEG", data -> {
            result.put("transactionSet", "850");
            result.put("purchaseOrderType", safeGet(data, 2));
            result.put("purchaseOrderNumber", safeGet(data, 3));
            result.put("releaseNumber", safeGet(data, 4));
            result.put("orderDate", safeGet(data, 5));
        });

        extractSegment(content, "N1", data -> {
            if ("BY".equals(safeGet(data, 1))) {
                result.put("partnerId", safeGet(data, 4));
                result.put("partnerName", safeGet(data, 2));
                result.put("buyerCode", safeGet(data, 4));
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
            if (fields.length > 4 && "VN".equals(safeGet(fields, 4))) {
                item.put("vendorPartNumber", safeGet(fields, 5));
            }
            if (fields.length > 4 && "UP".equals(safeGet(fields, 4))) {
                item.put("upc", safeGet(fields, 5));
            }
            if (fields.length > 4 && "BP".equals(safeGet(fields, 4))) {
                item.put("buyerPartNumber", safeGet(fields, 5));
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
        // Bulk carrier EDI: one 856 file may carry several shipments, each
        // opening with its own BSN. Split on BSN so every shipment becomes its
        // own ASN payload (single-shipment files behave exactly as before).
        List<Map<String, Object>> shipments = new ArrayList<>();
        List<Integer> bsnIndexes = new ArrayList<>();
        Pattern bsnPattern = Pattern.compile("(^|\\n)(?<seg>BSN\\*[^~]+~)", Pattern.MULTILINE);
        Matcher bsnMatcher = bsnPattern.matcher(content);
        int lastEnd = -1;
        while (bsnMatcher.find()) {
            int start = bsnMatcher.start();
            if (lastEnd != -1) {
                shipments.add(parseShipment856(content.substring(lastEnd, start)));
            }
            lastEnd = start;
        }
        if (lastEnd != -1) {
            shipments.add(parseShipment856(content.substring(lastEnd)));
        }
        if (shipments.isEmpty()) {
            return new LinkedHashMap<>();
        }

        Map<String, Object> result = new LinkedHashMap<>();
        result.put("shipments", shipments);
        result.put("shipmentCount", shipments.size());
        result.put("bulk", shipments.size() > 1);
        result.putAll(shipments.get(0));
        return result;
    }

    private Map<String, Object> parseShipment856(String segmentContent) {
        Map<String, Object> result = new LinkedHashMap<>();
        List<Map<String, Object>> packages = new ArrayList<>();

        extractSegment(segmentContent, "BSN", data -> {
            result.put("transactionSet", "856");
            result.put("shipNoticeNumber", safeGet(data, 2));
            result.put("shipDate", safeGet(data, 3));
            result.put("shipTime", safeGet(data, 4));
        });

        extractSegment(segmentContent, "REF", data -> {
            if ("PO".equals(safeGet(data, 1))) {
                result.put("purchaseOrderNumber", safeGet(data, 2));
            }
        });

        extractSegment(segmentContent, "TD1", data -> {
            result.put("packageCount", safeGet(data, 1));
            result.put("packageType", safeGet(data, 2));
        });

        extractSegment(segmentContent, "TD5", data -> {
            result.put("carrierCode", safeGet(data, 2));
            result.put("carrierName", safeGet(data, 3));
            result.put("serviceLevel", safeGet(data, 4));
        });

        extractSegment(segmentContent, "N1", data -> {
            if ("SU".equals(safeGet(data, 1)) || "SF".equals(safeGet(data, 1))) {
                result.putIfAbsent("supplierName", safeGet(data, 2));
            }
        });

        extractSegment(segmentContent, "TD3", data -> {
            result.put("trackingNumber", safeGet(data, 2));
            result.put("packageId", safeGet(data, 3));
        });

        // Extract HL segments with MAN for serial numbers
        Pattern hlPattern = Pattern.compile("HL\\*([^~\\n]+)~?\\n?MAN\\*([^~\\n]+)~?", Pattern.MULTILINE);
        Matcher hlMatcher = hlPattern.matcher(segmentContent);
        while (hlMatcher.find()) {
            Map<String, Object> pkg = new LinkedHashMap<>();
            pkg.put("hlData", hlMatcher.group(1));
            pkg.put("markNumbers", hlMatcher.group(2));
            packages.add(pkg);
        }

        // Extract PRF (purchase order reference)
        extractSegment(segmentContent, "PRF", data -> {
            result.putIfAbsent("purchaseOrderNumber", safeGet(data, 1));
        });

        // Extract LIN + SN1 segments as line items (product id + shipped qty)
        List<Map<String, Object>> items = new ArrayList<>();
        Pattern linPattern = Pattern.compile("LIN\\*([^~\\n]+)~?", Pattern.MULTILINE);
        Matcher linMatcher = linPattern.matcher(segmentContent);
        while (linMatcher.find()) {
            String[] fields = linMatcher.group(1).split("\\*");
            Map<String, Object> item = new LinkedHashMap<>();
            item.put("lineNumber", safeGet(fields, 0));
            if (fields.length > 2) {
                item.put("productId", safeGet(fields, 2));
            }
            items.add(item);
        }
        Pattern sn1Pattern = Pattern.compile("SN1\\*([^~\\n]+)~?", Pattern.MULTILINE);
        Matcher sn1Matcher = sn1Pattern.matcher(segmentContent);
        int idx = 0;
        while (sn1Matcher.find()) {
            String[] fields = sn1Matcher.group(1).split("\\*");
            Map<String, Object> item = idx < items.size() ? items.get(idx) : new LinkedHashMap<>();
            item.putIfAbsent("lineNumber", safeGet(fields, 0));
            item.put("quantity", safeGet(fields, 1));
            if (idx >= items.size()) {
                items.add(item);
            }
            idx++;
        }
        result.put("items", items);

        result.put("packages", packages);
        return result;
    }

    /**
     * X12 940 — Warehouse Shipping Order. Parses the header (order number, ship
     * date, carrier) and the LIN+QTY line items so the warehouse has a concrete
     * outbound instruction set from the carrier/vendor without manual entry.
     */
    private Map<String, Object> parse940(String content) {
        Map<String, Object> result = new LinkedHashMap<>();
        List<Map<String, Object>> items = new ArrayList<>();

        extractSegment(content, "W05", data -> {
            result.put("transactionSet", "940");
            result.put("shippingOrderNumber", safeGet(data, 1));
            result.put("shipDate", safeGet(data, 4));
        });

        extractSegment(content, "N1", data -> {
            if ("SF".equals(safeGet(data, 1))) {
                result.put("shipFromName", safeGet(data, 2));
            }
            if ("ST".equals(safeGet(data, 1))) {
                result.put("shipToName", safeGet(data, 2));
            }
        });

        extractSegment(content, "TD5", data -> {
            result.put("carrierCode", safeGet(data, 2));
            result.put("carrierName", safeGet(data, 3));
        });

        Pattern linPattern = Pattern.compile("LIN\\*([^~\\n]+)~?", Pattern.MULTILINE);
        Matcher linMatcher = linPattern.matcher(content);
        while (linMatcher.find()) {
            String[] fields = linMatcher.group(1).split("\\*");
            Map<String, Object> item = new LinkedHashMap<>();
            item.put("lineNumber", safeGet(fields, 0));
            if (fields.length > 2) {
                item.put("productId", safeGet(fields, 2));
            }
            items.add(item);
        }
        Pattern qtyPattern = Pattern.compile("QTY\\*([^~\\n]+)~?", Pattern.MULTILINE);
        Matcher qtyMatcher = qtyPattern.matcher(content);
        int idx = 0;
        while (qtyMatcher.find()) {
            String[] fields = qtyMatcher.group(1).split("\\*");
            Map<String, Object> item = idx < items.size() ? items.get(idx) : new LinkedHashMap<>();
            item.putIfAbsent("lineNumber", safeGet(fields, 0));
            item.put("quantity", safeGet(fields, 1));
            if (idx >= items.size()) {
                items.add(item);
            }
            idx++;
        }
        result.put("items", items);
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
            if (fields.length > 4) {
                item.put("productIdQualifier", safeGet(fields, 4));
                item.put("productId", safeGet(fields, 5));
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
            case "940" -> {
                if (data.get("shippingOrderNumber") == null)
                    errors.add("Missing shipping order number (W0501)");
                List<?> items = (List<?>) data.getOrDefault("items", Collections.emptyList());
                if (items.isEmpty())
                    errors.add("No line items found (LIN segments)");
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
            consumer.accept((segId + "*" + matcher.group(1)).split("\\*"));
        }
    }

    private String safeGet(String[] arr, int index) {
        return arr != null && index < arr.length ? arr[index].trim() : null;
    }

    private String nowIso() {
        return java.time.LocalDate.now().toString().replace("-", "");
    }

    private String pad(String value, int width) {
        if (value == null) {
            value = "";
        }
        return value.length() >= width ? value.substring(0, width) : value + " ".repeat(width - value.length());
    }

    @FunctionalInterface
    private interface SegmentConsumer {
        void accept(String[] data);
    }
}
