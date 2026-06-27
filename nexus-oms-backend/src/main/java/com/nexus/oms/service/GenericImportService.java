package com.nexus.oms.service;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.nexus.oms.dto.ImportResult;
import com.nexus.oms.exception.BadRequestException;
import com.nexus.oms.security.TenantContext;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;

import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
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
            importGeneric(records, result, entityType, tenantId);

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

    private void importGeneric(List<Map<String, Object>> records, ImportResult result, String entityType, UUID tenantId) {
        int success = 0, errors = 0;
        for (int i = 0; i < records.size(); i++) {
            try {
                result.getWarnings().add("Row " + (i + 1) + " (" + entityType + "): Parsed " + records.get(i).size() + " fields");
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
}
