package com.nexus.oms.service;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.nexus.oms.entity.NxEmailParsedOrder;
import com.nexus.oms.entity.NxOrder;
import com.nexus.oms.exception.BadRequestException;
import com.nexus.oms.exception.ResourceNotFoundException;
import com.nexus.oms.repository.EmailParsedOrderRepository;
import com.nexus.oms.repository.OrderRepository;
import com.nexus.oms.security.TenantContext;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;

import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.time.LocalDateTime;
import java.util.*;
import java.util.stream.Collectors;

@Service
public class EmailOrderParsingService {

    private static final Logger log = LoggerFactory.getLogger(EmailOrderParsingService.class);
    private static final ObjectMapper MAPPER = new ObjectMapper();
    private final EmailOrderTextParser textParser = new EmailOrderTextParser();

    private final EmailParsedOrderRepository emailParsedOrderRepository;
    private final OrderRepository orderRepository;

    public EmailOrderParsingService(EmailParsedOrderRepository emailParsedOrderRepository,
                                     OrderRepository orderRepository) {
        this.emailParsedOrderRepository = emailParsedOrderRepository;
        this.orderRepository = orderRepository;
    }

    public Page<NxEmailParsedOrder> getParsedOrders(String status, Pageable pageable) {
        UUID tenantId = TenantContext.getCurrentTenantId();
        if (status != null && !status.isBlank()) {
            return emailParsedOrderRepository.findByTenantIdAndStatus(tenantId, status, pageable);
        }
        return emailParsedOrderRepository.findByTenantId(tenantId, pageable);
    }

    public NxEmailParsedOrder getParsedOrder(UUID id) {
        return emailParsedOrderRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Parsed order not found: " + id));
    }

    @Transactional
    public NxEmailParsedOrder parseEmailContent(String subject, String from, String body, String attachmentType) {
        UUID tenantId = TenantContext.getCurrentTenantId();

        Map<String, Object> parsedData = textParser.extractOrderFromText(body);
        BigDecimal confidence = textParser.calculateConfidence(parsedData);

        NxEmailParsedOrder parsed = NxEmailParsedOrder.builder()
                .tenantId(tenantId)
                .emailSubject(subject)
                .emailFrom(from)
                .emailReceivedAt(LocalDateTime.now())
                .attachmentType(attachmentType != null ? attachmentType : "NONE")
                .rawBody(body.length() > 5000 ? body.substring(0, 5000) : body)
                .status(confidence.compareTo(new BigDecimal("0.7")) >= 0 ? "PARSED" : "PENDING_REVIEW")
                .confidenceScore(confidence)
                .customerName((String) parsedData.get("customerName"))
                .customerEmail((String) parsedData.get("customerEmail"))
                .customerPhone((String) parsedData.get("customerPhone"))
                .itemCount((Integer) parsedData.getOrDefault("itemCount", 0))
                .build();

        if (parsedData.containsKey("orderTotal")) {
            parsed.setOrderTotal(new BigDecimal(parsedData.get("orderTotal").toString()));
        }

        try {
            parsed.setParsedData(MAPPER.writeValueAsString(parsedData));
        } catch (JsonProcessingException e) {
            log.warn("Failed to serialize parsed data: {}", e.getMessage());
        }

        return emailParsedOrderRepository.save(parsed);
    }

    @Transactional
    public NxEmailParsedOrder parseCsvAttachment(MultipartFile file, String subject, String from) {
        UUID tenantId = TenantContext.getCurrentTenantId();
        Map<String, Object> parsedData = new LinkedHashMap<>();
        List<Map<String, Object>> items = new ArrayList<>();

        try (BufferedReader reader = new BufferedReader(
                new InputStreamReader(file.getInputStream(), StandardCharsets.UTF_8))) {
            String headerLine = reader.readLine();
            if (headerLine == null) throw new BadRequestException("Empty CSV file");

            String[] headers = headerLine.split(",");
            String line;
            while ((line = reader.readLine()) != null) {
                if (line.isBlank()) continue;
                String[] values = line.split(",");
                Map<String, Object> row = new LinkedHashMap<>();
                for (int i = 0; i < headers.length && i < values.length; i++) {
                    row.put(headers[i].trim(), values[i].trim());
                }
                items.add(row);
            }
        } catch (BadRequestException e) {
            throw e;
        } catch (Exception e) {
            throw new BadRequestException("Failed to parse CSV: " + e.getMessage());
        }

        parsedData.put("items", items);
        parsedData.put("itemCount", items.size());
        parsedData.put("source", "CSV_ATTACHMENT");

        if (items.isEmpty()) {
            throw new BadRequestException("CSV has no data rows");
        }

        Map<String, Object> firstRow = items.get(0);
        parsedData.put("customerName", firstRow.getOrDefault("CustomerName",
                firstRow.getOrDefault("customer_name", firstRow.getOrDefault("Name", ""))));
        parsedData.put("customerEmail", firstRow.getOrDefault("Email",
                firstRow.getOrDefault("email", firstRow.getOrDefault("CustomerEmail", ""))));

        BigDecimal total = BigDecimal.ZERO;
        for (Map<String, Object> item : items) {
            String priceStr = (String) item.getOrDefault("Price",
                    item.getOrDefault("price", item.getOrDefault("UnitPrice", "0")));
            String qtyStr = (String) item.getOrDefault("Quantity",
                    item.getOrDefault("quantity", item.getOrDefault("Qty", "1")));
            try {
                BigDecimal price = new BigDecimal(priceStr.replaceAll("[^\\d.]", ""));
                int qty = Integer.parseInt(qtyStr.replaceAll("[^\\d]", ""));
                total = total.add(price.multiply(BigDecimal.valueOf(qty)));
            } catch (NumberFormatException ignored) {}
        }
        parsedData.put("orderTotal", total);

        BigDecimal confidence = new BigDecimal("0.80");
        if (items.size() >= 2) confidence = new BigDecimal("0.90");

        NxEmailParsedOrder parsed = NxEmailParsedOrder.builder()
                .tenantId(tenantId)
                .emailSubject(subject != null ? subject : file.getOriginalFilename())
                .emailFrom(from)
                .attachmentFilename(file.getOriginalFilename())
                .attachmentType("CSV")
                .emailReceivedAt(LocalDateTime.now())
                .status("PARSED")
                .confidenceScore(confidence)
                .customerName((String) parsedData.get("customerName"))
                .customerEmail((String) parsedData.get("customerEmail"))
                .itemCount(items.size())
                .orderTotal(total)
                .build();

        try {
            parsed.setParsedData(MAPPER.writeValueAsString(parsedData));
        } catch (JsonProcessingException e) {
            log.warn("Failed to serialize parsed CSV data: {}", e.getMessage());
        }

        return emailParsedOrderRepository.save(parsed);
    }

    @Transactional
    public NxEmailParsedOrder approveOrder(UUID id) {
        NxEmailParsedOrder parsed = getParsedOrder(id);

        if (!"PARSED".equals(parsed.getStatus()) && !"PENDING_REVIEW".equals(parsed.getStatus())) {
            throw new BadRequestException("Order cannot be approved in status: " + parsed.getStatus());
        }

        NxOrder order = NxOrder.builder()
                .tenantId(parsed.getTenantId())
                .channel("EMAIL")
                .status("PENDING")
                .customerEmail(parsed.getCustomerEmail())
                .total(parsed.getOrderTotal())
                .build();

        if (parsed.getCustomerName() != null) {
            try {
                order.setMetadata(MAPPER.writeValueAsString(Map.of(
                    "customerName", parsed.getCustomerName(),
                    "customerPhone", parsed.getCustomerPhone() != null ? parsed.getCustomerPhone() : "",
                    "source", "EMAIL_PARSER",
                    "parsedOrderId", parsed.getId().toString(),
                    "emailSubject", parsed.getEmailSubject(),
                    "emailFrom", parsed.getEmailFrom()
                )));
            } catch (JsonProcessingException e) {
                log.warn("Failed to serialize order metadata: {}", e.getMessage());
            }
        }

        order = orderRepository.save(order);

        parsed.setOrderId(order.getId());
        parsed.setStatus("APPROVED");
        parsed.setProcessedAt(LocalDateTime.now());

        return emailParsedOrderRepository.save(parsed);
    }

    @Transactional
    public NxEmailParsedOrder rejectOrder(UUID id, String reason) {
        NxEmailParsedOrder parsed = getParsedOrder(id);
        parsed.setStatus("REJECTED");
        parsed.setRejectionReason(reason);
        parsed.setProcessedAt(LocalDateTime.now());
        return emailParsedOrderRepository.save(parsed);
    }

    public Map<String, Object> getKPIs() {
        UUID tenantId = TenantContext.getCurrentTenantId();
        Map<String, Object> kpis = new HashMap<>();
        kpis.put("total", emailParsedOrderRepository.findByTenantId(tenantId, Pageable.unpaged()).getTotalElements());
        kpis.put("new", emailParsedOrderRepository.countByTenantIdAndStatus(tenantId, "NEW"));
        kpis.put("parsed", emailParsedOrderRepository.countByTenantIdAndStatus(tenantId, "PARSED"));
        kpis.put("pendingReview", emailParsedOrderRepository.countByTenantIdAndStatus(tenantId, "PENDING_REVIEW"));
        kpis.put("approved", emailParsedOrderRepository.countByTenantIdAndStatus(tenantId, "APPROVED"));
        kpis.put("rejected", emailParsedOrderRepository.countByTenantIdAndStatus(tenantId, "REJECTED"));
        kpis.put("failed", emailParsedOrderRepository.countByTenantIdAndStatus(tenantId, "FAILED"));
        return kpis;
    }

}
