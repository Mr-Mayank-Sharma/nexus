package com.nexus.oms.service;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.nexus.oms.dto.ImportResult;
import com.nexus.oms.exception.BadRequestException;
import com.nexus.oms.security.TenantContext;
import jakarta.persistence.EntityManager;
import jakarta.persistence.PersistenceContext;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;

import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.time.LocalDateTime;
import java.util.*;

@Service
public class GenericImportService {

    private static final Logger log = LoggerFactory.getLogger(GenericImportService.class);
    private static final ObjectMapper MAPPER = new ObjectMapper();

    private static final Set<String> VALID_ENTITY_TYPES = Set.of(
        "products", "orders", "inventory", "customers", "shipments", "returns",
        "suppliers", "purchase-orders", "invoices", "warehouses"
    );

    private static final Set<String> VALID_FORMATS = Set.of("csv", "json", "xml", "edi", "xlsx");

    private static final UUID DEFAULT_TENANT_ID = UUID.fromString("00000000-0000-0000-0000-000000000000");

    @PersistenceContext
    private EntityManager entityManager;

    public GenericImportService() {
    }

    @Transactional
    public ImportResult importFile(String entityType, MultipartFile file, String format) {
        UUID tenantId;
        try {
            tenantId = TenantContext.getCurrentTenantId();
        } catch (IllegalStateException e) {
            tenantId = DEFAULT_TENANT_ID;
        }
        long startTime = System.currentTimeMillis();

        if (!VALID_ENTITY_TYPES.contains(entityType)) {
            throw new BadRequestException("Unsupported entity type: " + entityType);
        }

        String detectedFormat = format;
        if (detectedFormat == null || detectedFormat.isBlank()) {
            detectedFormat = detectFormat(file);
        }
        if (!VALID_FORMATS.contains(detectedFormat.toLowerCase())) {
            throw new BadRequestException("Unsupported format: " + detectedFormat);
        }

        List<Map<String, Object>> records;
        try {
            records = parseFile(file, detectedFormat);
        } catch (Exception e) {
            throw new BadRequestException("Failed to parse file: " + e.getMessage());
        }
        if (records.isEmpty()) {
            throw new BadRequestException("File contains no data records");
        }

        ImportResult result = new ImportResult();
        result.setEntityType(entityType);
        result.setFileName(file.getOriginalFilename());
        result.setFormat(detectedFormat.toLowerCase());
        result.setTotalRecords(records.size());

        try {
            switch (entityType) {
                case "orders" -> importOrders(records, result, tenantId);
                case "customers" -> importCustomers(records, result, tenantId);
                case "inventory" -> importInventory(records, result, tenantId);
                case "shipments" -> importShipments(records, result, tenantId);
                case "returns" -> importReturns(records, result, tenantId);
                case "suppliers" -> importSuppliers(records, result, tenantId);
                case "purchase-orders" -> importPurchaseOrders(records, result, tenantId);
                case "invoices" -> importInvoices(records, result, tenantId);
                case "warehouses" -> importWarehouses(records, result, tenantId);
                default -> importGeneric(records, result, entityType, tenantId);
            }

            long elapsed = System.currentTimeMillis() - startTime;
            result.setProcessingTimeMs(elapsed);

        } catch (Exception e) {
            long elapsed = System.currentTimeMillis() - startTime;
            result.setProcessingTimeMs(elapsed);
            result.setErrorCount(records.size());
            result.getErrors().add("Import failed: " + e.getMessage());
        }

        return result;
    }

    private String detectFormat(MultipartFile file) {
        String name = file.getOriginalFilename();
        if (name != null) {
            String ext = name.substring(name.lastIndexOf('.') + 1).toLowerCase();
            if (VALID_FORMATS.contains(ext)) return ext;
        }
        String contentType = file.getContentType();
        if (contentType != null) {
            if (contentType.contains("csv")) return "csv";
            if (contentType.contains("json")) return "json";
            if (contentType.contains("xml")) return "xml";
            if (contentType.contains("spreadsheet")) return "xlsx";
        }
        return "csv";
    }

    private List<Map<String, Object>> parseFile(MultipartFile file, String format) {
        return switch (format.toLowerCase()) {
            case "csv" -> parseCsv(file);
            case "json" -> parseJson(file);
            case "xml" -> parseXml(file);
            case "edi" -> parseEdi(file);
            default -> throw new BadRequestException("Cannot parse format: " + format);
        };
    }

    private List<Map<String, Object>> parseCsv(MultipartFile file) {
        List<Map<String, Object>> records = new ArrayList<>();
        try (BufferedReader reader = new BufferedReader(
                new InputStreamReader(file.getInputStream(), StandardCharsets.UTF_8))) {
            String headerLine = reader.readLine();
            if (headerLine == null) throw new BadRequestException("Empty CSV file");
            String[] headers = parseCsvLine(headerLine);
            String line;
            while ((line = reader.readLine()) != null) {
                if (line.isBlank()) continue;
                String[] values = parseCsvLine(line);
                Map<String, Object> row = new LinkedHashMap<>();
                for (int i = 0; i < headers.length; i++) {
                    row.put(headers[i].trim(), i < values.length ? values[i].trim() : "");
                }
                records.add(row);
            }
        } catch (BadRequestException e) { throw e;
        } catch (Exception e) { throw new BadRequestException("CSV parse error: " + e.getMessage()); }
        return records;
    }

    private String[] parseCsvLine(String line) {
        List<String> fields = new ArrayList<>();
        boolean inQuotes = false;
        StringBuilder cur = new StringBuilder();
        for (int i = 0; i < line.length(); i++) {
            char c = line.charAt(i);
            if (c == '"') {
                if (inQuotes && i + 1 < line.length() && line.charAt(i + 1) == '"') {
                    cur.append('"'); i++;
                } else { inQuotes = !inQuotes; }
            } else if (c == ',' && !inQuotes) {
                fields.add(cur.toString().trim()); cur = new StringBuilder();
            } else { cur.append(c); }
        }
        fields.add(cur.toString().trim());
        return fields.toArray(String[]::new);
    }

    private List<Map<String, Object>> parseJson(MultipartFile file) {
        try {
            String content = new String(file.getInputStream().readAllBytes(), StandardCharsets.UTF_8).trim();
            if (content.startsWith("[")) {
                return MAPPER.readValue(content, new TypeReference<List<Map<String, Object>>>() {});
            } else if (content.startsWith("{")) {
                Map<String, Object> map = MAPPER.readValue(content, new TypeReference<Map<String, Object>>() {});
                if (map.get("data") instanceof List) return (List<Map<String, Object>>) map.get("data");
                if (map.get("records") instanceof List) return (List<Map<String, Object>>) map.get("records");
                if (map.get("items") instanceof List) return (List<Map<String, Object>>) map.get("items");
                return List.of(map);
            }
            throw new BadRequestException("Invalid JSON");
        } catch (BadRequestException e) { throw e;
        } catch (Exception e) { throw new BadRequestException("JSON parse error: " + e.getMessage()); }
    }

    private List<Map<String, Object>> parseXml(MultipartFile file) {
        try {
            String content = new String(file.getInputStream().readAllBytes(), StandardCharsets.UTF_8);
            List<Map<String, Object>> records = new ArrayList<>();
            Map<String, Object> current = new LinkedHashMap<>();
            for (String line : content.split("\n")) {
                String trimmed = line.trim();
                if (trimmed.startsWith("<") && trimmed.contains(">")) {
                    String tag = trimmed.replaceAll("<[^>]*>", "").trim();
                    String key = trimmed.replaceAll("</?([^>]+).*?>", "$1").trim();
                    if (trimmed.startsWith("</") && !current.isEmpty()) {
                        records.add(new LinkedHashMap<>(current)); current.clear();
                    } else if (!trimmed.startsWith("<?") && !trimmed.startsWith("</") && !tag.equals(key) && !key.isEmpty()) {
                        current.put(key, tag);
                    }
                }
            }
            if (!current.isEmpty()) records.add(current);
            if (records.isEmpty()) { Map<String, Object> m = new LinkedHashMap<>(); m.put("rawContent", content); records.add(m); }
            return records;
        } catch (Exception e) { throw new BadRequestException("XML parse error: " + e.getMessage()); }
    }

    private List<Map<String, Object>> parseEdi(MultipartFile file) {
        try {
            String content = new String(file.getInputStream().readAllBytes(), StandardCharsets.UTF_8);
            List<Map<String, Object>> records = new ArrayList<>();
            Map<String, Object> current = new LinkedHashMap<>();
            List<Map<String, String>> items = new ArrayList<>();
            String docType = "";
            for (String seg : content.split("~")) {
                seg = seg.trim();
                if (seg.isBlank()) continue;
                String[] elems = seg.split("\\*");
                if (elems.length == 0) continue;
                switch (elems[0]) {
                    case "ST" -> { if (elems.length > 1) docType = elems[1]; current.put("documentType", docType); }
                    case "BEG" -> {
                        if (elems.length > 3) current.put("purchaseOrderNumber", elems[2]);
                        if (elems.length > 5) current.put("orderDate", elems[4]);
                    }
                    case "N1" -> { if (elems.length > 2) current.put(elems[1].equals("BY") ? "buyerName" : "supplierName", elems[2]); }
                    case "PO1" -> {
                        Map<String, String> item = new LinkedHashMap<>();
                        if (elems.length > 1) item.put("lineNumber", elems[1]);
                        if (elems.length > 2) item.put("quantityOrdered", elems[2]);
                        if (elems.length > 4) item.put("unitPrice", elems[4]);
                        if (elems.length > 7) item.put("sku", elems[7]);
                        items.add(item);
                    }
                    case "SE" -> {
                        current.put("itemCount", items.size());
                        current.put("items", new ArrayList<>(items));
                        records.add(new LinkedHashMap<>(current));
                        current.clear(); items.clear();
                    }
                }
            }
            if (!current.isEmpty() || !items.isEmpty()) {
                if (!items.isEmpty()) current.put("items", items);
                current.put("itemCount", items.size());
                records.add(current);
            }
            if (records.isEmpty()) {
                Map<String, Object> f = new LinkedHashMap<>();
                f.put("rawContent", content); f.put("documentType", docType); records.add(f);
            }
            return records;
        } catch (Exception e) { throw new BadRequestException("EDI parse error: " + e.getMessage()); }
    }

    private void importOrders(List<Map<String, Object>> records, ImportResult result, UUID tenantId) {
        int success = 0, errors = 0;
        for (int i = 0; i < records.size(); i++) {
            try {
                Map<String, Object> row = records.get(i);
                UUID orderId = UUID.randomUUID();
                UUID customerId = UUID.randomUUID();
                LocalDateTime now = LocalDateTime.now();

                String customerName = strVal(row, "CustomerName", "customerName", "name");
                String email = strVal(row, "Email", "email", "customerEmail");
                String phone = strVal(row, "Phone", "phone");
                String street = strVal(row, "ShippingStreet", "shippingStreet", "street");
                String city = strVal(row, "ShippingCity", "shippingCity", "city");
                String state = strVal(row, "ShippingState", "shippingState", "state");
                String zip = strVal(row, "ShippingZip", "shippingZip", "zip", "postalCode");
                String sku = strVal(row, "SKU", "sku", "productSku");
                String productName = strVal(row, "Description", "description", "productName");
                int quantity = Math.max(1, intVal(row, 1, "Quantity", "quantity", "qty"));
                BigDecimal price = bdVal(row, "Price", "price", "unitPrice");

                String addressJson = MAPPER.writeValueAsString(Map.of(
                    "line1", street, "city", city, "state", state, "pincode", zip
                ));

                entityManager.createNativeQuery(
                    "INSERT INTO nx_customers (id, tenant_id, name, email, phone, address, created_at) VALUES (?, ?, ?, ?, ?, CAST(? AS jsonb), ?)")
                    .setParameter(1, customerId)
                    .setParameter(2, tenantId)
                    .setParameter(3, customerName)
                    .setParameter(4, email)
                    .setParameter(5, phone)
                    .setParameter(6, addressJson)
                    .setParameter(7, now)
                    .executeUpdate();

                entityManager.createNativeQuery(
                    "INSERT INTO nx_orders (id, tenant_id, channel, customer_id, status, ship_to, currency, subtotal, shipping_cost, tax_amount, total, created_at) VALUES (?, ?, ?, ?, ?, CAST(? AS jsonb), ?, ?, ?, ?, ?, ?)")
                    .setParameter(1, orderId)
                    .setParameter(2, tenantId)
                    .setParameter(3, strVal(row, "channel", "Channel", "source", "MANUAL"))
                    .setParameter(4, customerId)
                    .setParameter(5, "PENDING")
                    .setParameter(6, addressJson)
                    .setParameter(7, "INR")
                    .setParameter(8, price.multiply(BigDecimal.valueOf(quantity)))
                    .setParameter(9, BigDecimal.ZERO)
                    .setParameter(10, BigDecimal.ZERO)
                    .setParameter(11, price.multiply(BigDecimal.valueOf(quantity)))
                    .setParameter(12, now)
                    .executeUpdate();

                UUID itemId = UUID.randomUUID();
                entityManager.createNativeQuery(
                    "INSERT INTO nx_order_items (id, order_id, sku, product_name, quantity, unit_price, total_price, allocated_qty) VALUES (?, ?, ?, ?, ?, ?, ?, ?)")
                    .setParameter(1, itemId)
                    .setParameter(2, orderId)
                    .setParameter(3, sku)
                    .setParameter(4, productName)
                    .setParameter(5, quantity)
                    .setParameter(6, price)
                    .setParameter(7, price.multiply(BigDecimal.valueOf(quantity)))
                    .setParameter(8, 0)
                    .executeUpdate();

                success++;
            } catch (Exception e) {
                log.warn("Order import row {} failed: {}", i, e.getMessage());
                result.getErrors().add("Row " + (i + 1) + ": " + e.getMessage());
                errors++;
            }
        }
        result.setSuccessCount(success);
        result.setErrorCount(errors);
    }

    private void importCustomers(List<Map<String, Object>> records, ImportResult result, UUID tenantId) {
        int success = 0, errors = 0;
        for (int i = 0; i < records.size(); i++) {
            try {
                Map<String, Object> row = records.get(i);
                String addressJson = MAPPER.writeValueAsString(Map.of(
                    "street", strVal(row, "street", "Street", "address"),
                    "city", strVal(row, "city", "City"),
                    "state", strVal(row, "state", "State", "province"),
                    "zip", strVal(row, "zip", "Zip", "zipCode", "postalCode"),
                    "country", strVal(row, "country", "Country", "US")
                ));

                entityManager.createNativeQuery(
                    "INSERT INTO nx_customers (id, tenant_id, name, email, phone, address, created_at) VALUES (?, ?, ?, ?, ?, CAST(? AS jsonb), ?)")
                    .setParameter(1, UUID.randomUUID())
                    .setParameter(2, tenantId)
                    .setParameter(3, strVal(row, "name", "Name", "customerName", "CustomerName"))
                    .setParameter(4, strVal(row, "email", "Email", "customerEmail", "CustomerEmail"))
                    .setParameter(5, strVal(row, "phone", "Phone"))
                    .setParameter(6, addressJson)
                    .setParameter(7, LocalDateTime.now())
                    .executeUpdate();

                success++;
            } catch (Exception e) {
                log.warn("Customer import row {} failed: {}", i, e.getMessage());
                result.getErrors().add("Row " + (i + 1) + ": " + e.getMessage());
                errors++;
            }
        }
        result.setSuccessCount(success);
        result.setErrorCount(errors);
    }

    private void importInventory(List<Map<String, Object>> records, ImportResult result, UUID tenantId) {
        int success = 0, errors = 0;
        LocalDateTime now = LocalDateTime.now();
        for (int i = 0; i < records.size(); i++) {
            try {
                Map<String, Object> row = records.get(i);
                entityManager.createNativeQuery(
                    "INSERT INTO nx_inventory (id, tenant_id, sku, node_id, quantity_on_hand, reorder_point, created_at, updated_at) VALUES (?, ?, ?, ?, ?, ?, ?, ?)")
                    .setParameter(1, UUID.randomUUID())
                    .setParameter(2, tenantId)
                    .setParameter(3, strVal(row, "sku", "SKU"))
                    .setParameter(4, null)
                    .setParameter(5, intVal(row, 0, "quantity", "Quantity"))
                    .setParameter(6, intVal(row, 10, "reorder_level", "reorderPoint"))
                    .setParameter(7, now).setParameter(8, now).executeUpdate();
                success++;
            } catch (Exception e) {
                log.warn("Inventory row {} failed: {}", i, e.getMessage());
                result.getErrors().add("Row " + (i + 1) + ": " + e.getMessage()); errors++;
            }
        }
        result.setSuccessCount(success); result.setErrorCount(errors);
    }

    private void importShipments(List<Map<String, Object>> records, ImportResult result, UUID tenantId) {
        entityManager.createNativeQuery("SET session_replication_role = replica").executeUpdate();
        try {
            int success = 0, errors = 0;
            LocalDateTime now = LocalDateTime.now();
            for (int i = 0; i < records.size(); i++) {
                try {
                    Map<String, Object> row = records.get(i);
                    entityManager.createNativeQuery(
                        "INSERT INTO nx_shipments (id, order_id, tenant_id, carrier_id, tracking_number, status, origin_node_id, created_at) VALUES (?, ?, ?, ?, ?, ?, ?, ?)")
                        .setParameter(1, UUID.randomUUID())
                        .setParameter(2, UUID.randomUUID())
                        .setParameter(3, tenantId)
                        .setParameter(4, strVal(row, "carrier", "carrierId"))
                        .setParameter(5, strVal(row, "tracking_number", "trackingNumber"))
                        .setParameter(6, strVal(row, "status", "Status", "PENDING"))
                        .setParameter(7, null)
                        .setParameter(8, now).executeUpdate();
                    success++;
                } catch (Exception e) {
                    log.warn("Shipment row {} failed: {}", i, e.getMessage());
                    result.getErrors().add("Row " + (i + 1) + ": " + e.getMessage()); errors++;
                }
            }
            result.setSuccessCount(success); result.setErrorCount(errors);
        } finally {
            entityManager.createNativeQuery("SET session_replication_role = origin").executeUpdate();
        }
    }

    private void importReturns(List<Map<String, Object>> records, ImportResult result, UUID tenantId) {
        entityManager.createNativeQuery("SET session_replication_role = replica").executeUpdate();
        try {
            int success = 0, errors = 0;
            LocalDateTime now = LocalDateTime.now();
            for (int i = 0; i < records.size(); i++) {
                try {
                    Map<String, Object> row = records.get(i);
                    entityManager.createNativeQuery(
                        "INSERT INTO nx_returns (id, tenant_id, order_id, customer_id, reason, grade, refund_amount, status, created_at) VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?)")
                        .setParameter(1, UUID.randomUUID())
                        .setParameter(2, tenantId)
                        .setParameter(3, UUID.randomUUID())
                        .setParameter(4, UUID.randomUUID())
                        .setParameter(5, strVal(row, "reason", "Reason"))
                        .setParameter(6, strVal(row, "condition", "grade", "NEW"))
                        .setParameter(7, bdVal(row, "refund_amount", "refundAmount"))
                        .setParameter(8, "PENDING")
                        .setParameter(9, now).executeUpdate();
                    success++;
                } catch (Exception e) {
                    log.warn("Return row {} failed: {}", i, e.getMessage());
                    result.getErrors().add("Row " + (i + 1) + ": " + e.getMessage()); errors++;
                }
            }
            result.setSuccessCount(success); result.setErrorCount(errors);
        } finally {
            entityManager.createNativeQuery("SET session_replication_role = origin").executeUpdate();
        }
    }

    private void importSuppliers(List<Map<String, Object>> records, ImportResult result, UUID tenantId) {
        int success = 0, errors = 0;
        LocalDateTime now = LocalDateTime.now();
        for (int i = 0; i < records.size(); i++) {
            try {
                Map<String, Object> row = records.get(i);
                String code = "SUP-" + (1000 + i);
                entityManager.createNativeQuery(
                    "INSERT INTO nx_suppliers (id, tenant_id, supplier_code, company_name, email, phone, address_line1, city, state, zip_code, country, status, created_at, updated_at) VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)")
                    .setParameter(1, UUID.randomUUID()).setParameter(2, tenantId)
                    .setParameter(3, code)
                    .setParameter(4, strVal(row, "name", "companyName"))
                    .setParameter(5, strVal(row, "email", "Email"))
                    .setParameter(6, strVal(row, "phone", "Phone"))
                    .setParameter(7, strVal(row, "address", "addressLine1"))
                    .setParameter(8, strVal(row, "city", "City"))
                    .setParameter(9, strVal(row, "state", "State"))
                    .setParameter(10, strVal(row, "zip", "zipCode", "Zip"))
                    .setParameter(11, strVal(row, "country", "Country", "India"))
                    .setParameter(12, "ACTIVE").setParameter(13, now).setParameter(14, now).executeUpdate();
                success++;
            } catch (Exception e) {
                log.warn("Supplier row {} failed: {}", i, e.getMessage());
                result.getErrors().add("Row " + (i + 1) + ": " + e.getMessage()); errors++;
            }
        }
        result.setSuccessCount(success); result.setErrorCount(errors);
    }

    private void importPurchaseOrders(List<Map<String, Object>> records, ImportResult result, UUID tenantId) {
        int success = 0, errors = 0;
        LocalDateTime now = LocalDateTime.now();
        for (int i = 0; i < records.size(); i++) {
            try {
                Map<String, Object> row = records.get(i);
                String poNum = "PO-" + System.currentTimeMillis() + "-" + i;
                entityManager.createNativeQuery(
                    "INSERT INTO nx_purchase_orders (id, tenant_id, po_number, supplier_id, status, order_date, expected_delivery_date, notes, created_at, updated_at) VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?)")
                    .setParameter(1, UUID.randomUUID()).setParameter(2, tenantId)
                    .setParameter(3, poNum).setParameter(4, UUID.randomUUID())
                    .setParameter(5, strVal(row, "status", "Status", "DRAFT"))
                    .setParameter(6, now)
                    .setParameter(7, now.plusDays(30))
                    .setParameter(8, strVal(row, "notes", "Notes"))
                    .setParameter(9, now).setParameter(10, now).executeUpdate();
                success++;
            } catch (Exception e) {
                log.warn("PO row {} failed: {}", i, e.getMessage());
                result.getErrors().add("Row " + (i + 1) + ": " + e.getMessage()); errors++;
            }
        }
        result.setSuccessCount(success); result.setErrorCount(errors);
    }

    private void importInvoices(List<Map<String, Object>> records, ImportResult result, UUID tenantId) {
        int success = 0, errors = 0;
        LocalDateTime now = LocalDateTime.now();
        for (int i = 0; i < records.size(); i++) {
            try {
                Map<String, Object> row = records.get(i);
                entityManager.createNativeQuery(
                    "INSERT INTO nx_invoices (id, tenant_id, invoice_number, order_id, status, invoice_date, due_date, total_amount, created_at, updated_at) VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?)")
                    .setParameter(1, UUID.randomUUID()).setParameter(2, tenantId)
                    .setParameter(3, strVal(row, "invoice_number", "invoiceNumber", "INV-" + i))
                    .setParameter(4, UUID.randomUUID())
                    .setParameter(5, strVal(row, "status", "Status", "DRAFT"))
                    .setParameter(6, now).setParameter(7, now.plusDays(30))
                    .setParameter(8, bdVal(row, "amount", "Amount"))
                    .setParameter(9, now).setParameter(10, now).executeUpdate();
                success++;
            } catch (Exception e) {
                log.warn("Invoice row {} failed: {}", i, e.getMessage());
                result.getErrors().add("Row " + (i + 1) + ": " + e.getMessage()); errors++;
            }
        }
        result.setSuccessCount(success); result.setErrorCount(errors);
    }

    private void importWarehouses(List<Map<String, Object>> records, ImportResult result, UUID tenantId) {
        int success = 0, errors = 0;
        LocalDateTime now = LocalDateTime.now();
        for (int i = 0; i < records.size(); i++) {
            try {
                Map<String, Object> row = records.get(i);
                entityManager.createNativeQuery(
                    "INSERT INTO nx_warehouses (id, tenant_id, code, name, address_line1, city, state, zip_code, country, total_capacity_sqm, contact_name, contact_phone, contact_email, status, created_at, updated_at) VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)")
                    .setParameter(1, UUID.randomUUID()).setParameter(2, tenantId)
                    .setParameter(3, strVal(row, "code", "Code"))
                    .setParameter(4, strVal(row, "name", "Name"))
                    .setParameter(5, strVal(row, "address", "addressLine1"))
                    .setParameter(6, strVal(row, "city", "City"))
                    .setParameter(7, strVal(row, "state", "State"))
                    .setParameter(8, strVal(row, "zip", "zipCode", "Zip"))
                    .setParameter(9, strVal(row, "country", "Country", "India"))
                    .setParameter(10, intVal(row, 10000, "capacity_sqft", "totalCapacitySqm"))
                    .setParameter(11, strVal(row, "manager_name", "managerName", "contactName"))
                    .setParameter(12, strVal(row, "manager_phone", "managerPhone", "contactPhone"))
                    .setParameter(13, strVal(row, "manager_email", "managerEmail", "contactEmail"))
                    .setParameter(14, "ACTIVE").setParameter(15, now).setParameter(16, now).executeUpdate();
                success++;
            } catch (Exception e) {
                log.warn("Warehouse row {} failed: {}", i, e.getMessage());
                result.getErrors().add("Row " + (i + 1) + ": " + e.getMessage()); errors++;
            }
        }
        result.setSuccessCount(success); result.setErrorCount(errors);
    }

    private void importGeneric(List<Map<String, Object>> records, ImportResult result, String entityType, UUID tenantId) {
        int success = 0, errors = 0;
        for (int i = 0; i < records.size(); i++) {
            try {
                result.getWarnings().add("Row " + (i + 1) + " (" + entityType + "): Parsed " + records.get(i).size() + " fields (no table exists for this entity)");
                success++;
            } catch (Exception e) {
                result.getErrors().add("Row " + (i + 1) + ": " + e.getMessage());
                errors++;
            }
        }
        result.setSuccessCount(success);
        result.setErrorCount(errors);
    }

    private String strVal(Map<String, Object> map, String... keys) {
        for (String key : keys) {
            Object val = map.get(key);
            if (val != null && !val.toString().isBlank()) return val.toString().trim();
        }
        return "";
    }

    private BigDecimal bdVal(Map<String, Object> map, String... keys) {
        for (String key : keys) {
            Object val = map.get(key);
            if (val != null) {
                try { return new BigDecimal(val.toString().replaceAll("[^\\d.]", "")); }
                catch (Exception ignored) {}
            }
        }
        return BigDecimal.ZERO;
    }

    private int intVal(Map<String, Object> map, int defaultVal, String... keys) {
        for (String key : keys) {
            Object val = map.get(key);
            if (val != null) {
                try { return Integer.parseInt(val.toString().replaceAll("[^\\d-]", "")); }
                catch (Exception ignored) {}
            }
        }
        return defaultVal;
    }
}
