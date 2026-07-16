package com.nexus.oms.service;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.nexus.oms.dto.ImportResult;
import com.nexus.oms.entity.*;
import com.nexus.oms.exception.BadRequestException;
import com.nexus.oms.repository.*;
import com.nexus.oms.security.TenantContext;
import org.apache.poi.ss.usermodel.*;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;
import org.springframework.web.multipart.MultipartFile;

import javax.xml.parsers.DocumentBuilder;
import javax.xml.parsers.DocumentBuilderFactory;
import java.io.*;
import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.nio.file.*;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.*;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

@Service
public class GenericImportService {

    private static final Logger log = LoggerFactory.getLogger(GenericImportService.class);
    private static final ObjectMapper MAPPER = new ObjectMapper();

    public static final Set<String> VALID_ENTITY_TYPES = Set.of(
        "products", "orders", "inventory", "customers", "shipments", "returns",
        "suppliers", "purchase-orders", "invoices", "warehouses"
    );

    private static final Set<String> VALID_FORMATS = Set.of("csv", "json", "xml", "edi", "xlsx");

    public static final Set<String> VALID_IMPORT_MODES = Set.of(
        "VALIDATE_ONLY", "CONTINUE_ON_ERROR", "STOP_ON_FIRST_ERROR",
        "INSERT_ONLY", "UPDATE_ONLY", "UPSERT"
    );

    private static final UUID DEFAULT_TENANT_ID = UUID.fromString("00000000-0000-0000-0000-000000000000");

    private static final String IMPORT_FILES_DIR = "./import-files/";

    private static final Pattern EMAIL_PATTERN = Pattern.compile("^[A-Za-z0-9+_.-]+@[A-Za-z0-9.-]+\\.[A-Za-z]{2,}$");

    private final ImportHistoryRepository importHistoryRepository;
    private final ImportRecordLogRepository importRecordLogRepository;
    private final OrderRepository orderRepository;
    private final CustomerRepository customerRepository;
    private final ProductRepository productRepository;
    private final OrderItemRepository orderItemRepository;
    private final AddressRepository addressRepository;
    private final InventoryRepository inventoryRepository;
    private final ShipmentRepository shipmentRepository;
    private final ReturnRepository returnRepository;
    private final SupplierRepository supplierRepository;
    private final PurchaseOrderRepository purchaseOrderRepository;
    private final InvoiceRepository invoiceRepository;
    private final WarehouseRepository warehouseRepository;
    private final TransactionTemplate transactionTemplate;

    public GenericImportService(ImportHistoryRepository importHistoryRepository,
                                ImportRecordLogRepository importRecordLogRepository,
                                OrderRepository orderRepository,
                                CustomerRepository customerRepository,
                                ProductRepository productRepository,
                                OrderItemRepository orderItemRepository,
                                AddressRepository addressRepository,
                                InventoryRepository inventoryRepository,
                                ShipmentRepository shipmentRepository,
                                ReturnRepository returnRepository,
                                SupplierRepository supplierRepository,
                                PurchaseOrderRepository purchaseOrderRepository,
                                InvoiceRepository invoiceRepository,
                                WarehouseRepository warehouseRepository,
                                PlatformTransactionManager transactionManager) {
        this.importHistoryRepository = importHistoryRepository;
        this.importRecordLogRepository = importRecordLogRepository;
        this.orderRepository = orderRepository;
        this.customerRepository = customerRepository;
        this.productRepository = productRepository;
        this.orderItemRepository = orderItemRepository;
        this.addressRepository = addressRepository;
        this.inventoryRepository = inventoryRepository;
        this.shipmentRepository = shipmentRepository;
        this.returnRepository = returnRepository;
        this.supplierRepository = supplierRepository;
        this.purchaseOrderRepository = purchaseOrderRepository;
        this.invoiceRepository = invoiceRepository;
        this.warehouseRepository = warehouseRepository;
        this.transactionTemplate = new TransactionTemplate(transactionManager);
    }

    public ImportResult importFile(String entityType, MultipartFile file, String format) {
        return importFile(entityType, file, format, "CONTINUE_ON_ERROR");
    }

    public ImportResult importFile(String entityType, MultipartFile file, String format, String importMode) {
        UUID tenantId = getTenantId();
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

        if (!VALID_IMPORT_MODES.contains(importMode.toUpperCase())) {
            throw new BadRequestException("Unsupported import mode: " + importMode
                + ". Supported: " + VALID_IMPORT_MODES);
        }
        importMode = importMode.toUpperCase();

        List<Map<String, Object>> records;
        try {
            records = parseFile(file, detectedFormat);
        } catch (Exception e) {
            throw new BadRequestException("Failed to parse file: " + e.getMessage());
        }
        if (records.isEmpty()) {
            throw new BadRequestException("File contains no data records");
        }

        ImportHistory history = createImportHistory(tenantId, entityType, file, detectedFormat, importMode, startTime);
        ImportResult result = buildResult(entityType, file, detectedFormat, records.size());
        List<ImportRecordLog> recordLogs = new ArrayList<>();

        try {
            switch (entityType) {
                case "orders" -> importOrders(records, result, tenantId, history.getId(), recordLogs, importMode);
                case "customers" -> importCustomers(records, result, tenantId, history.getId(), recordLogs, importMode);
                case "inventory" -> importInventory(records, result, tenantId, history.getId(), recordLogs, importMode);
                case "shipments" -> importShipments(records, result, tenantId, history.getId(), recordLogs, importMode);
                case "returns" -> importReturns(records, result, tenantId, history.getId(), recordLogs, importMode);
                case "suppliers" -> importSuppliers(records, result, tenantId, history.getId(), recordLogs, importMode);
                case "purchase-orders" -> importPurchaseOrders(records, result, tenantId, history.getId(), recordLogs, importMode);
                case "invoices" -> importInvoices(records, result, tenantId, history.getId(), recordLogs, importMode);
                case "warehouses" -> importWarehouses(records, result, tenantId, history.getId(), recordLogs, importMode);
                case "products" -> importProducts(records, result, tenantId, history.getId(), recordLogs, importMode);
                default -> importGeneric(records, result, entityType, tenantId, history.getId(), recordLogs);
            }

            long elapsed = System.currentTimeMillis() - startTime;
            result.setProcessingTimeMs(elapsed);
            try {
                saveRecordLogs(recordLogs);
            } catch (Exception e) {
                log.warn("Failed to save record logs: {}", e.getMessage());
                result.getErrors().add("Log save failed: " + e.getMessage());
            }
            try {
                updateHistoryOnComplete(history, result, elapsed);
            } catch (Exception e) {
                log.warn("Failed to update import history: {}", e.getMessage());
                result.getErrors().add("History update failed: " + e.getMessage());
            }
            try {
                generateErrorFile(history, entityType, result, recordLogs);
            } catch (Exception e) {
                log.warn("Failed to generate error file: {}", e.getMessage());
            }

        } catch (Exception e) {
            long elapsed = System.currentTimeMillis() - startTime;
            result.setProcessingTimeMs(elapsed);
            result.getErrors().add("Import failed: " + e.getMessage());
            history.setStatus("FAILED");
            history.setProcessingTimeMs(elapsed);
            history.setFailedCount(result.getErrorCount());
            history.setCompletedAt(LocalDateTime.now());
            try {
                importHistoryRepository.save(history);
            } catch (Exception e2) {
                log.warn("Failed to save failed history: {}", e2.getMessage());
            }
        }

        return result;
    }

    private UUID getTenantId() {
        try {
            return TenantContext.getCurrentTenantId();
        } catch (IllegalStateException e) {
            return DEFAULT_TENANT_ID;
        }
    }

    private ImportHistory createImportHistory(UUID tenantId, String entityType, MultipartFile file,
                                               String format, String importMode, long startTime) {
        ImportHistory history = ImportHistory.builder()
            .tenantId(tenantId)
            .fileName(file.getOriginalFilename())
            .originalFileName(file.getOriginalFilename())
            .importType(entityType)
            .fileFormat(format.toLowerCase())
            .importMode(importMode)
            .status("PROCESSING")
            .startedAt(LocalDateTime.now())
            .fileSizeBytes(file.getSize())
            .build();
        history = importHistoryRepository.save(history);

        try {
            Path dir = Paths.get(IMPORT_FILES_DIR);
            Files.createDirectories(dir);
            String storedName = history.getId() + "_" + file.getOriginalFilename();
            Path targetPath = dir.resolve(storedName);
            Files.copy(file.getInputStream(), targetPath, StandardCopyOption.REPLACE_EXISTING);
            history.setStoredFilePath(targetPath.toString());
            importHistoryRepository.save(history);
        } catch (IOException e) {
            log.warn("Failed to store uploaded file: {}", e.getMessage());
        }

        return history;
    }

    private ImportResult buildResult(String entityType, MultipartFile file, String format, int totalRecords) {
        ImportResult result = new ImportResult();
        result.setEntityType(entityType);
        result.setFileName(file.getOriginalFilename());
        result.setFormat(format.toLowerCase());
        result.setTotalRecords(totalRecords);
        return result;
    }

    private void saveRecordLogs(List<ImportRecordLog> recordLogs) {
        if (!recordLogs.isEmpty()) {
            importRecordLogRepository.saveAll(recordLogs);
        }
    }

    private void updateHistoryOnComplete(ImportHistory history, ImportResult result, long elapsed) {
        history.setStatus(result.getErrorCount() == 0 ? "COMPLETED" :
            result.getSuccessCount() > 0 ? "COMPLETED_WITH_ERRORS" : "FAILED");
        history.setTotalRecords(result.getTotalRecords());
        history.setSuccessCount(result.getSuccessCount());
        history.setFailedCount(result.getErrorCount());
        history.setDuplicateCount(result.getSkippedCount());
        history.setProcessingTimeMs(elapsed);
        history.setCompletedAt(LocalDateTime.now());
        importHistoryRepository.save(history);
    }

    private void generateErrorFile(ImportHistory history, String entityType, ImportResult result,
                                    List<ImportRecordLog> recordLogs) {
        if (recordLogs.isEmpty() || result.getErrorCount() == 0) return;

        try {
            Path dir = Paths.get(IMPORT_FILES_DIR);
            Files.createDirectories(dir);
            String errorFileName = history.getId() + "_errors_" + history.getOriginalFileName();
            Path errorPath = dir.resolve(errorFileName);

            try (BufferedWriter writer = Files.newBufferedWriter(errorPath)) {
                writer.write("Row Number,Status,Error Code,Error Message,Suggested Resolution");
                writer.newLine();
                for (ImportRecordLog log : recordLogs) {
                    if (!"SUCCESS".equals(log.getStatus())) {
                        writer.write(escapeCsv(String.valueOf(log.getRowNumber())) + ",");
                        writer.write(escapeCsv(log.getStatus()) + ",");
                        writer.write(escapeCsv(log.getErrorCode() != null ? log.getErrorCode() : "") + ",");
                        writer.write(escapeCsv(log.getErrorMessage() != null ? log.getErrorMessage() : "") + ",");
                        writer.write(escapeCsv(log.getSuggestedResolution() != null ? log.getSuggestedResolution() : ""));
                        writer.newLine();
                    }
                }
            }

            history.setErrorFilePath(errorPath.toString());
            importHistoryRepository.save(history);
        } catch (IOException e) {
            log.warn("Failed to generate error file: {}", e.getMessage());
        }
    }

    private ImportRecordLog createRecordLog(UUID historyId, UUID tenantId, int rowNumber, String status,
                                              String errorCode, String errorMessage, String suggestedResolution,
                                              Map<String, Object> originalRow, String stage) {
        String originalJson = "";
        try {
            if (originalRow != null) originalJson = MAPPER.writeValueAsString(originalRow);
        } catch (Exception ignored) {}

        return ImportRecordLog.builder()
            .importHistoryId(historyId)
            .tenantId(tenantId)
            .rowNumber(rowNumber + 1)
            .status(status)
            .errorCode(errorCode)
            .errorMessage(errorMessage)
            .suggestedResolution(suggestedResolution)
            .originalData(originalJson)
            .stage(stage)
            .build();
    }

    private String escapeCsv(String value) {
        if (value == null) return "";
        if (value.contains(",") || value.contains("\"") || value.contains("\n") || value.contains("\r")) {
            return "\"" + value.replace("\"", "\"\"") + "\"";
        }
        return value;
    }

    private boolean isMode(String importMode, String... modes) {
        for (String m : modes) {
            if (importMode.equals(m)) return true;
        }
        return false;
    }

    private boolean isWriteMode(String importMode) {
        return isMode(importMode, "INSERT_ONLY", "UPDATE_ONLY", "UPSERT", "CONTINUE_ON_ERROR", "STOP_ON_FIRST_ERROR");
    }

    // ---- File detection and parsing ----

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

    public List<Map<String, Object>> parseFile(MultipartFile file, String format) {
        return switch (format.toLowerCase()) {
            case "csv" -> parseCsv(file);
            case "json" -> parseJson(file);
            case "xml" -> parseXml(file);
            case "edi" -> parseEdi(file);
            case "xlsx" -> parseXlsx(file);
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
            DocumentBuilderFactory factory = DocumentBuilderFactory.newInstance();
            factory.setFeature("http://apache.org/xml/features/disallow-doctype-cache", true);
            DocumentBuilder builder = factory.newDocumentBuilder();
            org.w3c.dom.Document doc = builder.parse(file.getInputStream());
            org.w3c.dom.Element root = doc.getDocumentElement();

            List<Map<String, Object>> records = new ArrayList<>();
            org.w3c.dom.NodeList children = root.getChildNodes();

            for (int i = 0; i < children.getLength(); i++) {
                org.w3c.dom.Node child = children.item(i);
                if (child.getNodeType() == org.w3c.dom.Node.ELEMENT_NODE) {
                    org.w3c.dom.Element recordEl = (org.w3c.dom.Element) child;
                    Map<String, Object> row = new LinkedHashMap<>();
                    org.w3c.dom.NodeList fields = recordEl.getChildNodes();
                    for (int j = 0; j < fields.getLength(); j++) {
                        org.w3c.dom.Node field = fields.item(j);
                        if (field.getNodeType() == org.w3c.dom.Node.ELEMENT_NODE) {
                            String tagName = field.getNodeName();
                            String textContent = field.getTextContent();
                            row.put(tagName, textContent != null ? textContent.trim() : "");
                        }
                    }
                    records.add(row);
                }
            }

            if (records.isEmpty()) {
                Map<String, Object> fallback = new LinkedHashMap<>();
                fallback.put("rawContent", doc.getDocumentElement().getTextContent());
                records.add(fallback);
            }
            return records;
        } catch (BadRequestException e) { throw e;
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

    private List<Map<String, Object>> parseXlsx(MultipartFile file) {
        List<Map<String, Object>> records = new ArrayList<>();
        try (Workbook workbook = new XSSFWorkbook(file.getInputStream())) {
            Sheet sheet = workbook.getSheetAt(0);
            if (sheet == null) throw new BadRequestException("XLSX file has no sheets");

            Row headerRow = sheet.getRow(0);
            if (headerRow == null) throw new BadRequestException("XLSX file has no header row");

            int numCols = headerRow.getLastCellNum();
            String[] headers = new String[numCols];
            for (int i = 0; i < numCols; i++) {
                Cell cell = headerRow.getCell(i);
                headers[i] = cell != null ? getCellValueAsString(cell).trim() : "";
            }

            for (int rowIdx = 1; rowIdx <= sheet.getLastRowNum(); rowIdx++) {
                Row row = sheet.getRow(rowIdx);
                if (row == null) continue;
                Map<String, Object> record = new LinkedHashMap<>();
                boolean hasData = false;
                for (int colIdx = 0; colIdx < numCols; colIdx++) {
                    Cell cell = row.getCell(colIdx);
                    String value = cell != null ? getCellValueAsString(cell).trim() : "";
                    if (colIdx < headers.length) {
                        record.put(headers[colIdx], value);
                    }
                    if (!value.isEmpty()) hasData = true;
                }
                if (hasData) records.add(record);
            }
        } catch (BadRequestException e) { throw e;
        } catch (Exception e) { throw new BadRequestException("XLSX parse error: " + e.getMessage()); }
        return records;
    }

    private String getCellValueAsString(Cell cell) {
        return switch (cell.getCellType()) {
            case STRING -> cell.getStringCellValue();
            case NUMERIC -> {
                double val = cell.getNumericCellValue();
                if (val == Math.floor(val) && !Double.isInfinite(val)) {
                    yield String.valueOf((long) val);
                }
                yield String.valueOf(val);
            }
            case BOOLEAN -> String.valueOf(cell.getBooleanCellValue());
            case FORMULA -> {
                try { yield String.valueOf(cell.getNumericCellValue()); }
                catch (Exception e) {
                    try { yield cell.getStringCellValue(); }
                    catch (Exception e2) { yield ""; }
                }
            }
            default -> "";
        };
    }

    // ---- Import implementations ----

    private void importOrders(List<Map<String, Object>> records, ImportResult result, UUID tenantId,
                               UUID historyId, List<ImportRecordLog> recordLogs, String importMode) {
        int success = 0, errors = 0, skipped = 0;
        for (int i = 0; i < records.size(); i++) {
            if (isMode(importMode, "VALIDATE_ONLY")) {
                ImportRecordLog rl = validateOrderRow(records.get(i), i, historyId, tenantId);
                recordLogs.add(rl);
                if ("SUCCESS".equals(rl.getStatus())) success++;
                else errors++;
                continue;
            }

            try {
                Map<String, Object> row = records.get(i);

                if (isMode(importMode, "STOP_ON_FIRST_ERROR") && errors > 0) {
                    recordLogs.add(createRecordLog(historyId, tenantId, i, "SKIPPED",
                        null, "Stopped due to prior error", null, row, "import"));
                    skipped++;
                    continue;
                }

                ImportRecordLog validationLog = validateOrderRow(row, i, historyId, tenantId);
                if (!"SUCCESS".equals(validationLog.getStatus())) {
                    recordLogs.add(validationLog);
                    errors++;
                    if (isMode(importMode, "STOP_ON_FIRST_ERROR")) {
                        result.setSuccessCount(success); result.setErrorCount(errors); result.setSkippedCount(skipped);
                        return;
                    }
                    continue;
                }

                String email = strVal(row, "Email", "email", "customerEmail");
                String sku = strVal(row, "SKU", "sku", "productSku");
                String customerName = strVal(row, "CustomerName", "customerName", "name");
                String phone = strVal(row, "Phone", "phone");
                String street = strVal(row, "ShippingStreet", "shippingStreet", "street");
                String city = strVal(row, "ShippingCity", "shippingCity", "city");
                String state = strVal(row, "ShippingState", "shippingState", "state");
                String zip = strVal(row, "ShippingZip", "shippingZip", "zip", "postalCode");
                String productName = strVal(row, "Description", "description", "productName");
                int quantity = Math.max(1, intVal(row, 1, "Quantity", "quantity", "qty"));
                BigDecimal price = bdVal(row, "Price", "price", "unitPrice");
                String externalId = strVal(row, "externalId", "ExternalId", "channelOrderId");

                NxCustomer customer = customerRepository.findByTenantIdAndEmail(tenantId, email).orElse(null);
                if (customer == null) {
                    NxCustomer newCustomer = NxCustomer.builder()
                        .tenantId(tenantId).name(customerName).email(email).phone(phone)
                        .build();
                    customer = transactionTemplate.execute(s -> customerRepository.save(newCustomer));
                }

                Address shippingAddress = null;
                if (!street.isBlank()) {
                    Address addr = Address.builder()
                        .tenantId(tenantId).addressLine1(street).city(city).state(state)
                        .postalCode(zip).country("IN").addressType("SHIPPING")
                        .build();
                    shippingAddress = transactionTemplate.execute(s -> addressRepository.save(addr));
                }

                NxProductMapping productMapping = null; // optional lookup
                String finalProductName = productName;
                BigDecimal totalPrice = price.multiply(BigDecimal.valueOf(quantity));

                NxOrder order = NxOrder.builder()
                    .tenantId(tenantId)
                    .channel(strVal(row, "channel", "Channel", "source", "MANUAL"))
                    .externalId(externalId.isBlank() ? null : externalId)
                    .customerId(customer.getId())
                    .status("PENDING")
                    .shipToAddressId(shippingAddress != null ? shippingAddress.getId() : null)
                    .currency("INR")
                    .subtotal(totalPrice)
                    .shippingCost(BigDecimal.ZERO)
                    .taxAmount(BigDecimal.ZERO)
                    .total(totalPrice)
                    .build();

                NxOrder saved = transactionTemplate.execute(s -> orderRepository.save(order));

                NxOrderItem item = NxOrderItem.builder()
                    .orderId(saved.getId())
                    .sku(sku)
                    .productName(finalProductName)
                    .quantity(quantity)
                    .unitPrice(price)
                    .totalPrice(totalPrice)
                    .allocatedQty(0)
                    .build();
                transactionTemplate.execute(s -> orderItemRepository.save(item));

                success++;
                recordLogs.add(createRecordLog(historyId, tenantId, i, "SUCCESS", null, null, null, row, "import"));

            } catch (Exception e) {
                log.warn("Order import row {} failed: {}", i, e.getMessage());
                result.getErrors().add("Row " + (i + 1) + ": " + e.getMessage());
                errors++;
                recordLogs.add(createRecordLog(historyId, tenantId, i, "FAILED",
                    "ROW_ERROR", e.getMessage(), resolveSuggestion(e.getMessage()), records.get(i), "import"));

                if (isMode(importMode, "STOP_ON_FIRST_ERROR")) {
                    result.setSuccessCount(success);
                    result.setErrorCount(errors);
                    result.setSkippedCount(skipped);
                    return;
                }
            }
        }
        result.setSuccessCount(success);
        result.setErrorCount(errors);
        result.setSkippedCount(skipped);
    }

    private ImportRecordLog validateOrderRow(Map<String, Object> row, int i, UUID historyId, UUID tenantId) {
        List<String> failures = new ArrayList<>();
        if (strVal(row, "CustomerName", "customerName", "name").isBlank())
            failures.add("Missing required field: customerName");
        if (strVal(row, "SKU", "sku", "productSku").isBlank())
            failures.add("Missing required field: SKU");
        if (strVal(row, "Email", "email", "customerEmail").isBlank())
            failures.add("Missing required field: email");
        else {
            String email = strVal(row, "Email", "email", "customerEmail");
            if (!EMAIL_PATTERN.matcher(email).matches())
                failures.add("Invalid email format: " + email);
        }

        String priceStr = null;
        for (String k : List.of("Price", "price", "unitPrice")) {
            Object v = row.get(k);
            if (v != null) { priceStr = v.toString(); break; }
        }
        if (priceStr == null || priceStr.isBlank())
            failures.add("Missing required field: price");

        if (failures.isEmpty()) {
            return createRecordLog(historyId, tenantId, i, "SUCCESS", null, null, null, row, "validate");
        }
        return createRecordLog(historyId, tenantId, i, "FAILED", "VALIDATION_ERROR",
            String.join("; ", failures), "Ensure all required fields are present and valid", row, "validate");
    }

    private void importCustomers(List<Map<String, Object>> records, ImportResult result, UUID tenantId,
                                  UUID historyId, List<ImportRecordLog> recordLogs, String importMode) {
        int success = 0, errors = 0, skipped = 0;
        for (int i = 0; i < records.size(); i++) {
            if (isMode(importMode, "VALIDATE_ONLY")) {
                ImportRecordLog rl = validateCustomerRow(records.get(i), i, historyId, tenantId);
                recordLogs.add(rl);
                if ("SUCCESS".equals(rl.getStatus())) success++;
                else errors++;
                continue;
            }

            try {
                Map<String, Object> row = records.get(i);
                if (isMode(importMode, "STOP_ON_FIRST_ERROR") && errors > 0) {
                    recordLogs.add(createRecordLog(historyId, tenantId, i, "SKIPPED",
                        null, "Stopped due to prior error", null, row, "import"));
                    skipped++;
                    continue;
                }

                ImportRecordLog validationLog = validateCustomerRow(row, i, historyId, tenantId);
                if (!"SUCCESS".equals(validationLog.getStatus())) {
                    recordLogs.add(validationLog);
                    errors++;
                    if (isMode(importMode, "STOP_ON_FIRST_ERROR")) {
                        result.setSuccessCount(success); result.setErrorCount(errors); result.setSkippedCount(skipped);
                        return;
                    }
                    continue;
                }

                String email = strVal(row, "email", "Email", "customerEmail", "CustomerEmail");
                NxCustomer existing = customerRepository.findByTenantIdAndEmail(tenantId, email).orElse(null);

                if (existing != null && isMode(importMode, "INSERT_ONLY")) {
                    recordLogs.add(createRecordLog(historyId, tenantId, i, "SKIPPED", "DUPLICATE",
                        "Customer with email " + email + " already exists", "Use UPSERT mode to update", row, "import"));
                    skipped++;
                    continue;
                }

                String addrStreet = strVal(row, "street", "Street", "address");
                String addrCity = strVal(row, "city", "City");
                String addrState = strVal(row, "state", "State", "province");
                String addrZip = strVal(row, "zip", "Zip", "zipCode", "postalCode");
                String addrCountry = strVal(row, "country", "Country", "US");
                String name = strVal(row, "name", "Name", "customerName", "CustomerName");
                String phone = strVal(row, "phone", "Phone");

                Address address = null;
                if (!addrStreet.isBlank()) {
                    Address addr = Address.builder()
                        .tenantId(tenantId).addressLine1(addrStreet).city(addrCity).state(addrState)
                        .postalCode(addrZip).country(addrCountry).addressType("PRIMARY")
                        .build();
                    address = addressRepository.save(addr);
                }

                if (existing != null && isMode(importMode, "UPSERT", "UPDATE_ONLY")) {
                    NxCustomer updated = existing.toBuilder()
                        .name(name).email(email).phone(phone)
                        .addressId(address != null ? address.getId() : existing.getAddressId())
                        .build();
                    transactionTemplate.execute(s -> customerRepository.save(updated));
                } else {
                    NxCustomer cust = NxCustomer.builder()
                        .tenantId(tenantId).name(name).email(email).phone(phone)
                        .addressId(address != null ? address.getId() : null)
                        .build();
                    transactionTemplate.execute(s -> customerRepository.save(cust));
                }

                success++;
                recordLogs.add(createRecordLog(historyId, tenantId, i, "SUCCESS", null, null, null, row, "import"));

            } catch (Exception e) {
                log.warn("Customer import row {} failed: {}", i, e.getMessage());
                result.getErrors().add("Row " + (i + 1) + ": " + e.getMessage());
                errors++;
                recordLogs.add(createRecordLog(historyId, tenantId, i, "FAILED",
                    "ROW_ERROR", e.getMessage(), resolveSuggestion(e.getMessage()), records.get(i), "import"));
                if (isMode(importMode, "STOP_ON_FIRST_ERROR")) {
                    result.setSuccessCount(success); result.setErrorCount(errors); result.setSkippedCount(skipped);
                    return;
                }
            }
        }
        result.setSuccessCount(success); result.setErrorCount(errors); result.setSkippedCount(skipped);
    }

    private ImportRecordLog validateCustomerRow(Map<String, Object> row, int i, UUID historyId, UUID tenantId) {
        List<String> failures = new ArrayList<>();
        if (strVal(row, "name", "Name", "customerName", "CustomerName").isBlank())
            failures.add("Missing required field: name");
        if (strVal(row, "email", "Email", "customerEmail", "CustomerEmail").isBlank())
            failures.add("Missing required field: email");
        else {
            String email = strVal(row, "email", "Email", "customerEmail", "CustomerEmail");
            if (!EMAIL_PATTERN.matcher(email).matches())
                failures.add("Invalid email format: " + email);
        }
        if (failures.isEmpty()) {
            return createRecordLog(historyId, tenantId, i, "SUCCESS", null, null, null, row, "validate");
        }
        return createRecordLog(historyId, tenantId, i, "FAILED", "VALIDATION_ERROR",
            String.join("; ", failures), "Ensure all required fields are present and valid", row, "validate");
    }

    private void importInventory(List<Map<String, Object>> records, ImportResult result, UUID tenantId,
                                  UUID historyId, List<ImportRecordLog> recordLogs, String importMode) {
        int success = 0, errors = 0, skipped = 0;
        for (int i = 0; i < records.size(); i++) {
            if (isMode(importMode, "VALIDATE_ONLY")) {
                ImportRecordLog rl = validateInventoryRow(records.get(i), i, historyId, tenantId);
                recordLogs.add(rl);
                if ("SUCCESS".equals(rl.getStatus())) success++;
                else errors++;
                continue;
            }
            try {
                Map<String, Object> row = records.get(i);
                if (isMode(importMode, "STOP_ON_FIRST_ERROR") && errors > 0) {
                    recordLogs.add(createRecordLog(historyId, tenantId, i, "SKIPPED",
                        null, "Stopped due to prior error", null, row, "import"));
                    skipped++;
                    continue;
                }

                ImportRecordLog validationLog = validateInventoryRow(row, i, historyId, tenantId);
                if (!"SUCCESS".equals(validationLog.getStatus())) {
                    recordLogs.add(validationLog);
                    errors++;
                    if (isMode(importMode, "STOP_ON_FIRST_ERROR")) {
                        result.setSuccessCount(success); result.setErrorCount(errors); result.setSkippedCount(skipped);
                        return;
                    }
                    continue;
                }

                String sku = strVal(row, "sku", "SKU");
                List<NxInventory> existing = inventoryRepository.findByTenantIdAndSku(tenantId, sku);

                if (!existing.isEmpty() && isMode(importMode, "INSERT_ONLY")) {
                    recordLogs.add(createRecordLog(historyId, tenantId, i, "SKIPPED", "DUPLICATE",
                        "Inventory for SKU " + sku + " already exists", "Use UPSERT mode", row, "import"));
                    skipped++;
                    continue;
                }

                int qty = intVal(row, 0, "quantity", "Quantity");
                int reorder = intVal(row, 10, "reorder_level", "reorderPoint");
                int safetyStock = intVal(row, 0, "safety_stock", "safetyStock");
                int allocated = intVal(row, 0, "allocated", "quantityAllocated");
                int inTransit = intVal(row, 0, "in_transit", "quantityInTransit");

                NxInventory inv = NxInventory.builder()
                    .tenantId(tenantId).sku(sku).nodeId(null)
                    .quantityOnHand(qty).quantityAllocated(allocated)
                    .quantityInTransit(inTransit).safetyStock(safetyStock).reorderPoint(reorder)
                    .build();
                transactionTemplate.execute(s -> inventoryRepository.save(inv));

                success++;
                recordLogs.add(createRecordLog(historyId, tenantId, i, "SUCCESS", null, null, null, row, "import"));
            } catch (Exception e) {
                log.warn("Inventory row {} failed: {}", i, e.getMessage());
                result.getErrors().add("Row " + (i + 1) + ": " + e.getMessage()); errors++;
                recordLogs.add(createRecordLog(historyId, tenantId, i, "FAILED",
                    "ROW_ERROR", e.getMessage(), resolveSuggestion(e.getMessage()), records.get(i), "import"));
                if (isMode(importMode, "STOP_ON_FIRST_ERROR")) {
                    result.setSuccessCount(success); result.setErrorCount(errors); result.setSkippedCount(skipped);
                    return;
                }
            }
        }
        result.setSuccessCount(success); result.setErrorCount(errors); result.setSkippedCount(skipped);
    }

    private ImportRecordLog validateInventoryRow(Map<String, Object> row, int i, UUID historyId, UUID tenantId) {
        List<String> failures = new ArrayList<>();
        if (strVal(row, "sku", "SKU").isBlank())
            failures.add("Missing required field: sku");
        String qtyStr = null;
        for (String k : List.of("quantity", "Quantity", "qty")) {
            Object v = row.get(k);
            if (v != null) { qtyStr = v.toString(); break; }
        }
        if (qtyStr == null || qtyStr.isBlank())
            failures.add("Missing required field: quantity");
        else {
            try { int q = Integer.parseInt(qtyStr); if (q < 0) failures.add("quantity must be >= 0"); }
            catch (NumberFormatException e) { failures.add("quantity must be a valid integer"); }
        }
        if (failures.isEmpty()) {
            return createRecordLog(historyId, tenantId, i, "SUCCESS", null, null, null, row, "validate");
        }
        return createRecordLog(historyId, tenantId, i, "FAILED", "VALIDATION_ERROR",
            String.join("; ", failures), "Ensure all required fields are present", row, "validate");
    }

    private void importShipments(List<Map<String, Object>> records, ImportResult result, UUID tenantId,
                                  UUID historyId, List<ImportRecordLog> recordLogs, String importMode) {
        int success = 0, errors = 0, skipped = 0;
        for (int i = 0; i < records.size(); i++) {
            if (isMode(importMode, "VALIDATE_ONLY")) {
                ImportRecordLog rl = validateShipmentRow(records.get(i), i, historyId, tenantId);
                recordLogs.add(rl);
                if ("SUCCESS".equals(rl.getStatus())) success++;
                else errors++;
                continue;
            }
            try {
                Map<String, Object> row = records.get(i);
                if (isMode(importMode, "STOP_ON_FIRST_ERROR") && errors > 0) {
                    recordLogs.add(createRecordLog(historyId, tenantId, i, "SKIPPED",
                        null, "Stopped due to prior error", null, row, "import"));
                    skipped++;
                    continue;
                }

                ImportRecordLog validationLog = validateShipmentRow(row, i, historyId, tenantId);
                if (!"SUCCESS".equals(validationLog.getStatus())) {
                    recordLogs.add(validationLog);
                    errors++;
                    if (isMode(importMode, "STOP_ON_FIRST_ERROR")) {
                        result.setSuccessCount(success); result.setErrorCount(errors); result.setSkippedCount(skipped);
                        return;
                    }
                    continue;
                }

                String orderRef = strVal(row, "order_id", "orderId", "OrderId");
                UUID resolvedOrderId = null;
                if (!orderRef.isBlank()) {
                    NxOrder o = orderRepository.findByTenantIdAndChannelOrderId(tenantId, orderRef).orElse(null);
                    if (o != null) resolvedOrderId = o.getId();
                }

                NxShipment ship = NxShipment.builder()
                    .tenantId(tenantId)
                    .orderId(resolvedOrderId)
                    .carrierId(strVal(row, "carrier", "carrierId"))
                    .trackingNumber(strVal(row, "tracking_number", "trackingNumber"))
                    .serviceLevel(strVal(row, "service_level", "serviceLevel", "service"))
                    .status(strVal(row, "status", "Status", "PENDING"))
                    .build();
                transactionTemplate.execute(s -> shipmentRepository.save(ship));

                success++;
                recordLogs.add(createRecordLog(historyId, tenantId, i, "SUCCESS", null, null, null, row, "import"));
            } catch (Exception e) {
                log.warn("Shipment row {} failed: {}", i, e.getMessage());
                result.getErrors().add("Row " + (i + 1) + ": " + e.getMessage()); errors++;
                recordLogs.add(createRecordLog(historyId, tenantId, i, "FAILED",
                    "ROW_ERROR", e.getMessage(), resolveSuggestion(e.getMessage()), records.get(i), "import"));
                if (isMode(importMode, "STOP_ON_FIRST_ERROR")) {
                    result.setSuccessCount(success); result.setErrorCount(errors); result.setSkippedCount(skipped);
                    return;
                }
            }
        }
        result.setSuccessCount(success); result.setErrorCount(errors); result.setSkippedCount(skipped);
    }

    private ImportRecordLog validateShipmentRow(Map<String, Object> row, int i, UUID historyId, UUID tenantId) {
        List<String> failures = new ArrayList<>();
        if (strVal(row, "tracking_number", "trackingNumber").isBlank())
            failures.add("Missing required field: tracking_number");
        if (strVal(row, "carrier", "carrierId").isBlank())
            failures.add("Missing required field: carrier");
        if (failures.isEmpty()) {
            return createRecordLog(historyId, tenantId, i, "SUCCESS", null, null, null, row, "validate");
        }
        return createRecordLog(historyId, tenantId, i, "FAILED", "VALIDATION_ERROR",
            String.join("; ", failures), "Ensure tracking_number and carrier are provided", row, "validate");
    }

    private void importReturns(List<Map<String, Object>> records, ImportResult result, UUID tenantId,
                                UUID historyId, List<ImportRecordLog> recordLogs, String importMode) {
        int success = 0, errors = 0, skipped = 0;
        for (int i = 0; i < records.size(); i++) {
            if (isMode(importMode, "VALIDATE_ONLY")) {
                ImportRecordLog rl = validateReturnRow(records.get(i), i, historyId, tenantId);
                recordLogs.add(rl);
                if ("SUCCESS".equals(rl.getStatus())) success++;
                else errors++;
                continue;
            }
            try {
                Map<String, Object> row = records.get(i);
                if (isMode(importMode, "STOP_ON_FIRST_ERROR") && errors > 0) {
                    recordLogs.add(createRecordLog(historyId, tenantId, i, "SKIPPED",
                        null, "Stopped due to prior error", null, row, "import"));
                    skipped++;
                    continue;
                }

                ImportRecordLog validationLog = validateReturnRow(row, i, historyId, tenantId);
                if (!"SUCCESS".equals(validationLog.getStatus())) {
                    recordLogs.add(validationLog);
                    errors++;
                    if (isMode(importMode, "STOP_ON_FIRST_ERROR")) {
                        result.setSuccessCount(success); result.setErrorCount(errors); result.setSkippedCount(skipped);
                        return;
                    }
                    continue;
                }

                String orderRef = strVal(row, "order_id", "orderId", "OrderId");
                UUID resolvedOrderId = null;
                if (!orderRef.isBlank()) {
                    NxOrder o = orderRepository.findByTenantIdAndChannelOrderId(tenantId, orderRef).orElse(null);
                    if (o != null) resolvedOrderId = o.getId();
                }

                String email = strVal(row, "email", "Email", "customerEmail");
                UUID customerId = null;
                if (!email.isBlank()) {
                    NxCustomer c = customerRepository.findByTenantIdAndEmail(tenantId, email).orElse(null);
                    if (c != null) customerId = c.getId();
                }

                String grade = strVal(row, "condition", "grade");
                if (grade.isBlank()) grade = "USED";

                NxReturn rtn = NxReturn.builder()
                    .tenantId(tenantId)
                    .orderId(resolvedOrderId)
                    .customerId(customerId)
                    .reason(strVal(row, "reason", "Reason"))
                    .grade(grade)
                    .refundAmount(bdVal(row, "refund_amount", "refundAmount"))
                    .status("REQUESTED")
                    .rmaNumber(strVal(row, "rma_number", "rmaNumber", "RMA-" + System.currentTimeMillis()))
                    .disposition(strVal(row, "disposition", "Disposition", "RETURN_TO_STOCK"))
                    .build();
                transactionTemplate.execute(s -> returnRepository.save(rtn));

                success++;
                recordLogs.add(createRecordLog(historyId, tenantId, i, "SUCCESS", null, null, null, row, "import"));
            } catch (Exception e) {
                log.warn("Return row {} failed: {}", i, e.getMessage());
                result.getErrors().add("Row " + (i + 1) + ": " + e.getMessage()); errors++;
                recordLogs.add(createRecordLog(historyId, tenantId, i, "FAILED",
                    "ROW_ERROR", e.getMessage(), resolveSuggestion(e.getMessage()), records.get(i), "import"));
                if (isMode(importMode, "STOP_ON_FIRST_ERROR")) {
                    result.setSuccessCount(success); result.setErrorCount(errors); result.setSkippedCount(skipped);
                    return;
                }
            }
        }
        result.setSuccessCount(success); result.setErrorCount(errors); result.setSkippedCount(skipped);
    }

    private ImportRecordLog validateReturnRow(Map<String, Object> row, int i, UUID historyId, UUID tenantId) {
        List<String> failures = new ArrayList<>();
        if (strVal(row, "reason", "Reason").isBlank())
            failures.add("Missing required field: reason");
        if (strVal(row, "order_id", "orderId", "OrderId").isBlank() &&
            strVal(row, "email", "Email", "customerEmail").isBlank())
            failures.add("Either order_id or email must be provided");
        if (failures.isEmpty()) {
            return createRecordLog(historyId, tenantId, i, "SUCCESS", null, null, null, row, "validate");
        }
        return createRecordLog(historyId, tenantId, i, "FAILED", "VALIDATION_ERROR",
            String.join("; ", failures), "Ensure reason and order_id/email are provided", row, "validate");
    }

    private void importSuppliers(List<Map<String, Object>> records, ImportResult result, UUID tenantId,
                                  UUID historyId, List<ImportRecordLog> recordLogs, String importMode) {
        int success = 0, errors = 0, skipped = 0;
        for (int i = 0; i < records.size(); i++) {
            if (isMode(importMode, "VALIDATE_ONLY")) {
                ImportRecordLog rl = validateSupplierRow(records.get(i), i, historyId, tenantId);
                recordLogs.add(rl);
                if ("SUCCESS".equals(rl.getStatus())) success++;
                else errors++;
                continue;
            }
            try {
                Map<String, Object> row = records.get(i);
                if (isMode(importMode, "STOP_ON_FIRST_ERROR") && errors > 0) {
                    recordLogs.add(createRecordLog(historyId, tenantId, i, "SKIPPED",
                        null, "Stopped due to prior error", null, row, "import"));
                    skipped++;
                    continue;
                }

                ImportRecordLog validationLog = validateSupplierRow(row, i, historyId, tenantId);
                if (!"SUCCESS".equals(validationLog.getStatus())) {
                    recordLogs.add(validationLog);
                    errors++;
                    if (isMode(importMode, "STOP_ON_FIRST_ERROR")) {
                        result.setSuccessCount(success); result.setErrorCount(errors); result.setSkippedCount(skipped);
                        return;
                    }
                    continue;
                }

                String code = strVal(row, "supplier_code", "supplierCode");
                if (code.isBlank()) code = "SUP-" + (1000 + i);

                Supplier supplier = Supplier.builder()
                    .tenantId(tenantId).supplierCode(code)
                    .companyName(strVal(row, "name", "companyName"))
                    .email(strVal(row, "email", "Email"))
                    .phone(strVal(row, "phone", "Phone"))
                    .addressLine1(strVal(row, "address", "addressLine1"))
                    .city(strVal(row, "city", "City"))
                    .state(strVal(row, "state", "State"))
                    .zipCode(strVal(row, "zip", "zipCode", "Zip"))
                    .country(strVal(row, "country", "Country", "India"))
                    .status("ACTIVE")
                    .build();
                transactionTemplate.execute(s -> supplierRepository.save(supplier));

                success++;
                recordLogs.add(createRecordLog(historyId, tenantId, i, "SUCCESS", null, null, null, row, "import"));
            } catch (Exception e) {
                log.warn("Supplier row {} failed: {}", i, e.getMessage());
                result.getErrors().add("Row " + (i + 1) + ": " + e.getMessage()); errors++;
                recordLogs.add(createRecordLog(historyId, tenantId, i, "FAILED",
                    "ROW_ERROR", e.getMessage(), resolveSuggestion(e.getMessage()), records.get(i), "import"));
                if (isMode(importMode, "STOP_ON_FIRST_ERROR")) {
                    result.setSuccessCount(success); result.setErrorCount(errors); result.setSkippedCount(skipped);
                    return;
                }
            }
        }
        result.setSuccessCount(success); result.setErrorCount(errors); result.setSkippedCount(skipped);
    }

    private ImportRecordLog validateSupplierRow(Map<String, Object> row, int i, UUID historyId, UUID tenantId) {
        List<String> failures = new ArrayList<>();
        if (strVal(row, "name", "companyName").isBlank())
            failures.add("Missing required field: name/companyName");
        if (strVal(row, "email", "Email").isBlank())
            failures.add("Missing required field: email");
        else {
            String email = strVal(row, "email", "Email");
            if (!EMAIL_PATTERN.matcher(email).matches())
                failures.add("Invalid email format: " + email);
        }
        if (failures.isEmpty()) {
            return createRecordLog(historyId, tenantId, i, "SUCCESS", null, null, null, row, "validate");
        }
        return createRecordLog(historyId, tenantId, i, "FAILED", "VALIDATION_ERROR",
            String.join("; ", failures), "Ensure name and email are provided", row, "validate");
    }

    private void importPurchaseOrders(List<Map<String, Object>> records, ImportResult result, UUID tenantId,
                                       UUID historyId, List<ImportRecordLog> recordLogs, String importMode) {
        int success = 0, errors = 0, skipped = 0;
        for (int i = 0; i < records.size(); i++) {
            if (isMode(importMode, "VALIDATE_ONLY")) {
                ImportRecordLog rl = validatePurchaseOrderRow(records.get(i), i, historyId, tenantId);
                recordLogs.add(rl);
                if ("SUCCESS".equals(rl.getStatus())) success++;
                else errors++;
                continue;
            }
            try {
                Map<String, Object> row = records.get(i);
                if (isMode(importMode, "STOP_ON_FIRST_ERROR") && errors > 0) {
                    recordLogs.add(createRecordLog(historyId, tenantId, i, "SKIPPED",
                        null, "Stopped due to prior error", null, row, "import"));
                    skipped++;
                    continue;
                }

                ImportRecordLog validationLog = validatePurchaseOrderRow(row, i, historyId, tenantId);
                if (!"SUCCESS".equals(validationLog.getStatus())) {
                    recordLogs.add(validationLog);
                    errors++;
                    if (isMode(importMode, "STOP_ON_FIRST_ERROR")) {
                        result.setSuccessCount(success); result.setErrorCount(errors); result.setSkippedCount(skipped);
                        return;
                    }
                    continue;
                }

                String poNum = "PO-" + System.currentTimeMillis() + "-" + i;
                String supplierCode = strVal(row, "supplier_code", "supplierCode", "supplier_id", "SupplierId");
                UUID resolvedSupplierId = null;
                if (!supplierCode.isBlank()) {
                    List<Supplier> suppliers = supplierRepository.findByTenantId(tenantId, Pageable.unpaged()).getContent();
                    resolvedSupplierId = suppliers.stream()
                        .filter(s -> supplierCode.equals(s.getSupplierCode()))
                        .findFirst().map(Supplier::getId).orElse(null);
                }

                PurchaseOrder po = PurchaseOrder.builder()
                    .tenantId(tenantId).poNumber(poNum).supplierId(resolvedSupplierId)
                    .status(strVal(row, "status", "Status", "DRAFT"))
                    .orderDate(LocalDate.now())
                    .expectedDeliveryDate(LocalDate.now().plusDays(30))
                    .totalAmount(bdVal(row, "total_amount", "amount", "total"))
                    .notes(strVal(row, "notes", "Notes"))
                    .build();
                transactionTemplate.execute(s -> purchaseOrderRepository.save(po));

                success++;
                recordLogs.add(createRecordLog(historyId, tenantId, i, "SUCCESS", null, null, null, row, "import"));
            } catch (Exception e) {
                log.warn("PO row {} failed: {}", i, e.getMessage());
                result.getErrors().add("Row " + (i + 1) + ": " + e.getMessage()); errors++;
                recordLogs.add(createRecordLog(historyId, tenantId, i, "FAILED",
                    "ROW_ERROR", e.getMessage(), resolveSuggestion(e.getMessage()), records.get(i), "import"));
                if (isMode(importMode, "STOP_ON_FIRST_ERROR")) {
                    result.setSuccessCount(success); result.setErrorCount(errors); result.setSkippedCount(skipped);
                    return;
                }
            }
        }
        result.setSuccessCount(success); result.setErrorCount(errors); result.setSkippedCount(skipped);
    }

    private ImportRecordLog validatePurchaseOrderRow(Map<String, Object> row, int i, UUID historyId, UUID tenantId) {
        List<String> failures = new ArrayList<>();
        if (strVal(row, "supplier_code", "supplierCode", "supplier_id", "SupplierId").isBlank())
            failures.add("Missing required field: supplier_code");
        if (failures.isEmpty()) {
            return createRecordLog(historyId, tenantId, i, "SUCCESS", null, null, null, row, "validate");
        }
        return createRecordLog(historyId, tenantId, i, "FAILED", "VALIDATION_ERROR",
            String.join("; ", failures), "Ensure supplier_code is provided", row, "validate");
    }

    private void importInvoices(List<Map<String, Object>> records, ImportResult result, UUID tenantId,
                                 UUID historyId, List<ImportRecordLog> recordLogs, String importMode) {
        int success = 0, errors = 0, skipped = 0;
        for (int i = 0; i < records.size(); i++) {
            if (isMode(importMode, "VALIDATE_ONLY")) {
                ImportRecordLog rl = validateInvoiceRow(records.get(i), i, historyId, tenantId);
                recordLogs.add(rl);
                if ("SUCCESS".equals(rl.getStatus())) success++;
                else errors++;
                continue;
            }
            try {
                Map<String, Object> row = records.get(i);
                if (isMode(importMode, "STOP_ON_FIRST_ERROR") && errors > 0) {
                    recordLogs.add(createRecordLog(historyId, tenantId, i, "SKIPPED",
                        null, "Stopped due to prior error", null, row, "import"));
                    skipped++;
                    continue;
                }

                ImportRecordLog validationLog = validateInvoiceRow(row, i, historyId, tenantId);
                if (!"SUCCESS".equals(validationLog.getStatus())) {
                    recordLogs.add(validationLog);
                    errors++;
                    if (isMode(importMode, "STOP_ON_FIRST_ERROR")) {
                        result.setSuccessCount(success); result.setErrorCount(errors); result.setSkippedCount(skipped);
                        return;
                    }
                    continue;
                }

                String orderRef = strVal(row, "order_id", "orderId", "OrderId");
                UUID resolvedOrderId = null;
                if (!orderRef.isBlank()) {
                    NxOrder o = orderRepository.findByTenantIdAndChannelOrderId(tenantId, orderRef).orElse(null);
                    if (o != null) resolvedOrderId = o.getId();
                }

                Invoice invoice = Invoice.builder()
                    .tenantId(tenantId)
                    .invoiceNumber(strVal(row, "invoice_number", "invoiceNumber", "INV-" + System.currentTimeMillis()))
                    .orderId(resolvedOrderId)
                    .status(strVal(row, "status", "Status", "DRAFT"))
                    .invoiceDate(LocalDate.now())
                    .dueDate(LocalDate.now().plusDays(30))
                    .totalAmount(bdVal(row, "amount", "Amount", "total_amount", "total"))
                    .build();
                transactionTemplate.execute(s -> invoiceRepository.save(invoice));

                success++;
                recordLogs.add(createRecordLog(historyId, tenantId, i, "SUCCESS", null, null, null, row, "import"));
            } catch (Exception e) {
                log.warn("Invoice row {} failed: {}", i, e.getMessage());
                result.getErrors().add("Row " + (i + 1) + ": " + e.getMessage()); errors++;
                recordLogs.add(createRecordLog(historyId, tenantId, i, "FAILED",
                    "ROW_ERROR", e.getMessage(), resolveSuggestion(e.getMessage()), records.get(i), "import"));
                if (isMode(importMode, "STOP_ON_FIRST_ERROR")) {
                    result.setSuccessCount(success); result.setErrorCount(errors); result.setSkippedCount(skipped);
                    return;
                }
            }
        }
        result.setSuccessCount(success); result.setErrorCount(errors); result.setSkippedCount(skipped);
    }

    private ImportRecordLog validateInvoiceRow(Map<String, Object> row, int i, UUID historyId, UUID tenantId) {
        List<String> failures = new ArrayList<>();
        if (strVal(row, "invoice_number", "invoiceNumber").isBlank())
            failures.add("Missing required field: invoice_number");
        BigDecimal amt = bdVal(row, "amount", "Amount", "total_amount", "total");
        if (amt.compareTo(BigDecimal.ZERO) <= 0)
            failures.add("amount must be greater than 0");
        if (failures.isEmpty()) {
            return createRecordLog(historyId, tenantId, i, "SUCCESS", null, null, null, row, "validate");
        }
        return createRecordLog(historyId, tenantId, i, "FAILED", "VALIDATION_ERROR",
            String.join("; ", failures), "Ensure invoice_number and valid amount are provided", row, "validate");
    }

    private void importWarehouses(List<Map<String, Object>> records, ImportResult result, UUID tenantId,
                                   UUID historyId, List<ImportRecordLog> recordLogs, String importMode) {
        int success = 0, errors = 0, skipped = 0;
        for (int i = 0; i < records.size(); i++) {
            if (isMode(importMode, "VALIDATE_ONLY")) {
                ImportRecordLog rl = validateWarehouseRow(records.get(i), i, historyId, tenantId);
                recordLogs.add(rl);
                if ("SUCCESS".equals(rl.getStatus())) success++;
                else errors++;
                continue;
            }
            try {
                Map<String, Object> row = records.get(i);
                if (isMode(importMode, "STOP_ON_FIRST_ERROR") && errors > 0) {
                    recordLogs.add(createRecordLog(historyId, tenantId, i, "SKIPPED",
                        null, "Stopped due to prior error", null, row, "import"));
                    skipped++;
                    continue;
                }

                ImportRecordLog validationLog = validateWarehouseRow(row, i, historyId, tenantId);
                if (!"SUCCESS".equals(validationLog.getStatus())) {
                    recordLogs.add(validationLog);
                    errors++;
                    if (isMode(importMode, "STOP_ON_FIRST_ERROR")) {
                        result.setSuccessCount(success); result.setErrorCount(errors); result.setSkippedCount(skipped);
                        return;
                    }
                    continue;
                }

                Warehouse wh = Warehouse.builder()
                    .tenantId(tenantId)
                    .code(strVal(row, "code", "Code"))
                    .name(strVal(row, "name", "Name"))
                    .addressLine1(strVal(row, "address", "addressLine1"))
                    .city(strVal(row, "city", "City"))
                    .state(strVal(row, "state", "State"))
                    .zipCode(strVal(row, "zip", "zipCode", "Zip"))
                    .country(strVal(row, "country", "Country", "India"))
                    .totalCapacitySqm(BigDecimal.valueOf(intVal(row, 10000, "capacity_sqft", "totalCapacitySqm")))
                    .contactName(strVal(row, "manager_name", "managerName", "contactName"))
                    .contactPhone(strVal(row, "manager_phone", "managerPhone", "contactPhone"))
                    .contactEmail(strVal(row, "manager_email", "managerEmail", "contactEmail"))
                    .status("ACTIVE")
                    .build();
                transactionTemplate.execute(s -> warehouseRepository.save(wh));

                success++;
                recordLogs.add(createRecordLog(historyId, tenantId, i, "SUCCESS", null, null, null, row, "import"));
            } catch (Exception e) {
                log.warn("Warehouse row {} failed: {}", i, e.getMessage());
                result.getErrors().add("Row " + (i + 1) + ": " + e.getMessage()); errors++;
                recordLogs.add(createRecordLog(historyId, tenantId, i, "FAILED",
                    "ROW_ERROR", e.getMessage(), resolveSuggestion(e.getMessage()), records.get(i), "import"));
                if (isMode(importMode, "STOP_ON_FIRST_ERROR")) {
                    result.setSuccessCount(success); result.setErrorCount(errors); result.setSkippedCount(skipped);
                    return;
                }
            }
        }
        result.setSuccessCount(success); result.setErrorCount(errors); result.setSkippedCount(skipped);
    }

    private ImportRecordLog validateWarehouseRow(Map<String, Object> row, int i, UUID historyId, UUID tenantId) {
        List<String> failures = new ArrayList<>();
        if (strVal(row, "code", "Code").isBlank())
            failures.add("Missing required field: code");
        if (strVal(row, "name", "Name").isBlank())
            failures.add("Missing required field: name");
        if (failures.isEmpty()) {
            return createRecordLog(historyId, tenantId, i, "SUCCESS", null, null, null, row, "validate");
        }
        return createRecordLog(historyId, tenantId, i, "FAILED", "VALIDATION_ERROR",
            String.join("; ", failures), "Ensure code and name are provided", row, "validate");
    }

    private void importProducts(List<Map<String, Object>> records, ImportResult result, UUID tenantId,
                                 UUID historyId, List<ImportRecordLog> recordLogs, String importMode) {
        int success = 0, errors = 0, skipped = 0;
        for (int i = 0; i < records.size(); i++) {
            if (isMode(importMode, "VALIDATE_ONLY")) {
                ImportRecordLog rl = validateProductRow(records.get(i), i, historyId, tenantId);
                recordLogs.add(rl);
                if ("SUCCESS".equals(rl.getStatus())) success++;
                else errors++;
                continue;
            }

            try {
                if (isMode(importMode, "STOP_ON_FIRST_ERROR") && errors > 0) {
                    skipped++;
                    recordLogs.add(createRecordLog(historyId, tenantId, i, "SKIPPED",
                        null, "Stopped due to prior error", null, records.get(i), "import"));
                    continue;
                }

                Map<String, Object> row = records.get(i);

                ImportRecordLog validationLog = validateProductRow(row, i, historyId, tenantId);
                if (!"SUCCESS".equals(validationLog.getStatus())) {
                    recordLogs.add(validationLog);
                    errors++;
                    if (isMode(importMode, "STOP_ON_FIRST_ERROR")) {
                        result.setSuccessCount(success); result.setErrorCount(errors); result.setSkippedCount(skipped);
                        return;
                    }
                    continue;
                }

                String sku = strVal(row, "sku", "SKU", "productSku");
                String name = strVal(row, "name", "Name", "productName", "ProductName");
                String description = strVal(row, "description", "Description");
                String category = strVal(row, "category", "Category");
                BigDecimal price = bdVal(row, "price", "Price", "unitPrice");

                Optional<Product> existing = productRepository.findByTenantIdAndSku(tenantId, sku);

                if (existing.isPresent() && isMode(importMode, "INSERT_ONLY")) {
                    recordLogs.add(createRecordLog(historyId, tenantId, i, "SKIPPED", "DUPLICATE",
                        "Product with SKU " + sku + " already exists", "Use UPSERT mode to update", row, "import"));
                    skipped++;
                    continue;
                }

                if (existing.isPresent() && isMode(importMode, "UPSERT", "UPDATE_ONLY")) {
                    Product p = existing.get();
                    p.setProductName(name);
                    p.setDescription(description.isEmpty() ? null : description);
                    p.setCategory(category.isEmpty() ? null : category);
                    p.setUnitPrice(price);
                    transactionTemplate.execute(s -> productRepository.save(p));
                } else {
                    Product p = Product.builder()
                        .tenantId(tenantId).sku(sku)
                        .productName(name)
                        .description(description.isEmpty() ? null : description)
                        .category(category.isEmpty() ? null : category)
                        .unitPrice(price)
                        .build();
                    transactionTemplate.execute(s -> productRepository.save(p));
                }

                success++;
                recordLogs.add(createRecordLog(historyId, tenantId, i, "SUCCESS", null, null, null, row, "import"));
            } catch (Exception e) {
                log.warn("Product import row {} failed: {}", i, e.getMessage());
                result.getErrors().add("Row " + (i + 1) + ": " + e.getMessage());
                errors++;
                recordLogs.add(createRecordLog(historyId, tenantId, i, "FAILED",
                    "ROW_ERROR", e.getMessage(), resolveSuggestion(e.getMessage()), records.get(i), "import"));
                if (isMode(importMode, "STOP_ON_FIRST_ERROR")) {
                    result.setSuccessCount(success); result.setErrorCount(errors); result.setSkippedCount(skipped);
                    return;
                }
            }
        }
        result.setSuccessCount(success); result.setErrorCount(errors); result.setSkippedCount(skipped);
    }

    private ImportRecordLog validateProductRow(Map<String, Object> row, int i, UUID historyId, UUID tenantId) {
        List<String> failures = new ArrayList<>();
        if (strVal(row, "sku", "SKU", "productSku").isBlank())
            failures.add("Missing required field: sku");
        if (strVal(row, "name", "Name", "productName", "ProductName").isBlank())
            failures.add("Missing required field: name");
        if (failures.isEmpty()) {
            return createRecordLog(historyId, tenantId, i, "SUCCESS", null, null, null, row, "validate");
        }
        return createRecordLog(historyId, tenantId, i, "FAILED", "VALIDATION_ERROR",
            String.join("; ", failures), "Ensure sku and name are provided", row, "validate");
    }

    private void importGeneric(List<Map<String, Object>> records, ImportResult result, String entityType,
                                UUID tenantId, UUID historyId, List<ImportRecordLog> recordLogs) {
        int success = 0, errors = 0;
        for (int i = 0; i < records.size(); i++) {
            try {
                result.getWarnings().add("Row " + (i + 1) + " (" + entityType + "): Parsed " +
                    records.get(i).size() + " fields (no table exists for this entity)");
                recordLogs.add(createRecordLog(historyId, tenantId, i, "WARNING", "NO_TABLE",
                    "No database table exists for entity type: " + entityType,
                    "Create the table first or use a supported entity type", records.get(i), "import"));
                success++;
            } catch (Exception e) {
                result.getErrors().add("Row " + (i + 1) + ": " + e.getMessage());
                errors++;
                recordLogs.add(createRecordLog(historyId, tenantId, i, "FAILED",
                    "ROW_ERROR", e.getMessage(), resolveSuggestion(e.getMessage()), records.get(i), "import"));
            }
        }
        result.setSuccessCount(success);
        result.setErrorCount(errors);
    }

    // ---- Helper methods ----

    private void validateRequiredFields(Map<String, Object> row, int rowIndex, String... keys) {
        boolean found = false;
        for (String key : keys) {
            Object val = row.get(key);
            if (val != null && !val.toString().isBlank()) { found = true; break; }
        }
        if (!found) {
            throw new IllegalArgumentException("Missing required field (tried: " + String.join(", ", keys) + ")");
        }
    }

    private String resolveSuggestion(String error) {
        if (error == null) return "Check the row data and try again";
        String e = error.toLowerCase();
        if (e.contains("null") || e.contains("not null")) return "Ensure all required fields have non-null values";
        if (e.contains("uuid") || e.contains("foreign key") || e.contains("fk"))
            return "Ensure referenced entities exist (valid foreign keys)";
        if (e.contains("duplicate") || e.contains("unique") || e.contains("primary key"))
            return "Remove duplicate records or use UPSERT mode";
        if (e.contains("numeric") || e.contains("number") || e.contains("integer"))
            return "Ensure numeric fields contain valid numbers";
        if (e.contains("format") || e.contains("parse"))
            return "Check the field format and data types";
        return "Review the row data for errors and re-import";
    }

    // ---- Value extractors ----

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
