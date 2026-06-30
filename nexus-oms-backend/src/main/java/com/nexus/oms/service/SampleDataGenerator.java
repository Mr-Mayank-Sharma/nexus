package com.nexus.oms.service;

import com.nexus.oms.exception.BadRequestException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.io.*;
import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.*;
import java.util.concurrent.ThreadLocalRandom;

@Service
public class SampleDataGenerator {

    private static final Logger log = LoggerFactory.getLogger(SampleDataGenerator.class);
    private static final DateTimeFormatter DATE_FMT = DateTimeFormatter.ofPattern("yyyy-MM-dd");
    private static final String[] FIRST_NAMES = {"Raj", "Priya", "Amit", "Sunita", "Vikram", "Anita", "Rahul", "Deepa", "Suresh", "Lakshmi",
        "Arun", "Kavita", "Manoj", "Neha", "Ravi", "Pooja", "Sanjay", "Meera", "Vijay", "Shanti"};
    private static final String[] LAST_NAMES = {"Sharma", "Patel", "Singh", "Kumar", "Gupta", "Verma", "Reddy", "Joshi", "Nair", "Das"};
    private static final String[] CITIES = {"Mumbai", "Delhi", "Bangalore", "Hyderabad", "Chennai", "Kolkata", "Pune", "Ahmedabad", "Jaipur", "Lucknow"};
    private static final String[] STATES = {"Maharashtra", "Delhi", "Karnataka", "Telangana", "Tamil Nadu", "West Bengal", "Maharashtra", "Gujarat", "Rajasthan", "Uttar Pradesh"};
    private static final String[] PRODUCT_NAMES = {
        "Wireless Bluetooth Headphones", "USB-C Hub Adapter", "Laptop Stand Adjustable",
        "Mechanical Keyboard RGB", "Ergonomic Mouse Pad", "4K Webcam with Mic",
        "Portable SSD 1TB", "Smart LED Light Strip", "Noise Cancelling Earbuds",
        "Wireless Charging Pad", "Phone Grip Stand", "Tablet Holder Arm",
        "HDMI to USB-C Cable", "Desk Organizer Tray", "Monitor Light Bar",
        "Laptop Sleeve 15.6\"", "USB Desk Fan", "Smart Plug WiFi",
        "Bluetooth Speaker", "Cable Management Box"
    };
    private static final String[] CARRIERS = {"FedEx", "UPS", "DHL", "USPS", "BlueDart", "Delhivery", "DTDC", "Ekart"};
    private static final String[] CATEGORIES = {"Electronics", "Accessories", "Office Supplies", "Audio", "Cables", "Lighting", "Storage", "Furniture"};
    private static final String[] SUPPLIER_CODES = {"SUP-ELEC", "SUP-ACC", "SUP-OFFICE", "SUP-AUDIO", "SUP-CABLE", "SUP-LIGHT", "SUP-STORAGE", "SUP-FURN"};
    private static final String[] SUPPLIER_NAMES = {"TechWorld Distributors", "Prime Accessories Ltd", "OfficePro Supplies",
        "AudioVault Inc", "CableConnect Corp", "Luminous Lighting Co", "DataStorage Solutions", "ErgoFurn Systems"};
    private static final String[] RETURN_REASONS = {"Defective product", "Wrong item received", "Size/ Fit issue", "Damaged in transit",
        "Not as described", "Changed mind", "Duplicate order", "Quality issue"};

    public byte[] generateSample(String entityType, int count, String format) {
        if (count < 1 || count > 10000) throw new BadRequestException("Count must be between 1 and 10000");
        String data = switch (entityType) {
            case "orders" -> generateOrders(count, format);
            case "customers" -> generateCustomers(count, format);
            case "inventory" -> generateInventory(count, format);
            case "products" -> generateProducts(count, format);
            case "shipments" -> generateShipments(count, format);
            case "returns" -> generateReturns(count, format);
            case "suppliers" -> generateSuppliers(count, format);
            case "purchase-orders" -> generatePurchaseOrders(count, format);
            case "invoices" -> generateInvoices(count, format);
            case "warehouses" -> generateWarehouses(count, format);
            default -> throw new BadRequestException("Unsupported entity type: " + entityType);
        };
        return data.getBytes(StandardCharsets.UTF_8);
    }

    public String guessFileName(String entityType, String format) {
        return "sample_" + entityType + "_" + LocalDate.now().format(DATE_FMT) + "." + format;
    }

    private String generateOrders(int count, String format) {
        List<String[]> rows = new ArrayList<>();
        rows.add(new String[]{"CustomerName", "Email", "Phone", "SKU", "Description", "Quantity", "Price", "ShippingStreet", "ShippingCity", "ShippingState", "ShippingZip", "channel"});
        for (int i = 0; i < count; i++) {
            String first = rand(FIRST_NAMES), last = rand(LAST_NAMES);
            int cityIdx = randInt(CITIES.length);
            String city = CITIES[cityIdx], state = STATES[cityIdx];
            int prodIdx = randInt(PRODUCT_NAMES.length);
            int qty = randInt(1, 5);
            BigDecimal price = BigDecimal.valueOf(randInt(10, 300) + randDouble(0, 0.99));
            rows.add(new String[]{
                first + " " + last,
                first.toLowerCase() + "." + last.toLowerCase() + i + "@example.com",
                "98" + (10000000 + randInt(90000000)),
                "SKU-" + (1000 + prodIdx),
                PRODUCT_NAMES[prodIdx],
                String.valueOf(qty),
                price.setScale(2, BigDecimal.ROUND_HALF_UP).toString(),
                randInt(1, 999) + " " + rand(LAST_NAMES) + " Nagar",
                city, state,
                String.valueOf(100000 + randInt(899999)),
                "MANUAL"
            });
        }
        return toFormat(rows, format);
    }

    private String generateCustomers(int count, String format) {
        List<String[]> rows = new ArrayList<>();
        rows.add(new String[]{"name", "email", "phone", "street", "city", "state", "zip", "country"});
        Set<String> usedEmails = new HashSet<>();
        for (int i = 0; i < count; i++) {
            String first = rand(FIRST_NAMES), last = rand(LAST_NAMES);
            int cityIdx = randInt(CITIES.length);
            String email = first.toLowerCase() + "." + last.toLowerCase() + i + "@example.com";
            while (usedEmails.contains(email)) email = first.toLowerCase() + "." + last.toLowerCase() + (i + randInt(1000)) + "@example.com";
            usedEmails.add(email);
            rows.add(new String[]{
                first + " " + last, email,
                "98" + (10000000 + randInt(90000000)),
                randInt(1, 999) + " " + rand(LAST_NAMES) + " Street",
                CITIES[cityIdx], STATES[cityIdx],
                String.valueOf(100000 + randInt(899999)), "India"
            });
        }
        return toFormat(rows, format);
    }

    private String generateProducts(int count, String format) {
        List<String[]> rows = new ArrayList<>();
        rows.add(new String[]{"sku", "name", "description", "category", "price"});
        for (int i = 0; i < count && i < PRODUCT_NAMES.length; i++) {
            rows.add(new String[]{
                "SKU-" + (1000 + i), PRODUCT_NAMES[i],
                "High-quality " + PRODUCT_NAMES[i].toLowerCase() + " for professional use",
                rand(CATEGORIES),
                String.valueOf(randInt(10, 500) + randDouble(0, 0.99))
            });
        }
        // If count > available product names, generate more
        for (int i = PRODUCT_NAMES.length; i < count; i++) {
            String name = "Product " + (i + 1);
            rows.add(new String[]{
                "SKU-" + (1000 + i), name,
                "Description for " + name,
                rand(CATEGORIES),
                String.valueOf(randInt(5, 1000) + randDouble(0, 0.99))
            });
        }
        return toFormat(rows, format);
    }

    private String generateInventory(int count, String format) {
        List<String[]> rows = new ArrayList<>();
        rows.add(new String[]{"sku", "quantity", "reorder_level", "safety_stock", "allocated", "in_transit"});
        for (int i = 0; i < count; i++) {
            rows.add(new String[]{
                "SKU-" + (1000 + (i % 20)),
                String.valueOf(randInt(10, 5000)),
                String.valueOf(randInt(5, 100)),
                String.valueOf(randInt(10, 200)),
                String.valueOf(randInt(0, 50)),
                String.valueOf(randInt(0, 100))
            });
        }
        return toFormat(rows, format);
    }

    private String generateShipments(int count, String format) {
        List<String[]> rows = new ArrayList<>();
        rows.add(new String[]{"order_id", "carrier", "tracking_number", "status", "service_level"});
        for (int i = 0; i < count; i++) {
            rows.add(new String[]{
                "ORD-" + (10000 + randInt(90000)),
                rand(CARRIERS),
                "TRK" + (1000000000L + randInt(900000000)),
                rand(new String[]{"PENDING", "LABELED", "SHIPPED", "IN_TRANSIT", "DELIVERED"}),
                rand(new String[]{"STANDARD", "EXPRESS", "OVER_NIGHT", "GROUND", "SAME_DAY"})
            });
        }
        return toFormat(rows, format);
    }

    private String generateReturns(int count, String format) {
        List<String[]> rows = new ArrayList<>();
        rows.add(new String[]{"order_id", "email", "reason", "condition", "refund_amount", "rma_number", "disposition"});
        for (int i = 0; i < count; i++) {
            String first = rand(FIRST_NAMES), last = rand(LAST_NAMES);
            rows.add(new String[]{
                "ORD-" + (10000 + randInt(90000)),
                first.toLowerCase() + "." + last.toLowerCase() + "@example.com",
                rand(RETURN_REASONS),
                rand(new String[]{"NEW", "LIKE_NEW", "USED", "DAMAGED"}),
                String.valueOf(randInt(5, 500) + randDouble(0, 0.99)),
                "RMA-" + System.currentTimeMillis() + "-" + i,
                rand(new String[]{"RETURN_TO_STOCK", "RESTOCK", "DONATE", "RECYCLE", "DISPOSE"})
            });
        }
        return toFormat(rows, format);
    }

    private String generateSuppliers(int count, String format) {
        List<String[]> rows = new ArrayList<>();
        rows.add(new String[]{"name", "email", "phone", "address", "city", "state", "zip", "country", "supplier_code"});
        for (int i = 0; i < count && i < SUPPLIER_NAMES.length; i++) {
            int cityIdx = randInt(CITIES.length);
            rows.add(new String[]{
                SUPPLIER_NAMES[i],
                "contact@" + SUPPLIER_NAMES[i].replaceAll("[^a-zA-Z]", "").toLowerCase() + ".com",
                "22" + (10000000 + randInt(90000000)),
                randInt(1, 999) + " " + rand(LAST_NAMES) + " Industrial Area",
                CITIES[cityIdx], STATES[cityIdx],
                String.valueOf(100000 + randInt(899999)), "India",
                SUPPLIER_CODES[i]
            });
        }
        for (int i = SUPPLIER_NAMES.length; i < count; i++) {
            int cityIdx = randInt(CITIES.length);
            String name = "Supplier " + (i + 1);
            rows.add(new String[]{
                name,
                "supplier" + (i + 1) + "@example.com",
                "22" + (10000000 + randInt(90000000)),
                randInt(1, 999) + " Business Park",
                CITIES[cityIdx], STATES[cityIdx],
                String.valueOf(100000 + randInt(899999)), "India",
                "SUP-" + (1000 + i)
            });
        }
        return toFormat(rows, format);
    }

    private String generatePurchaseOrders(int count, String format) {
        List<String[]> rows = new ArrayList<>();
        rows.add(new String[]{"supplier_code", "status", "notes", "total_amount"});
        for (int i = 0; i < count; i++) {
            rows.add(new String[]{
                rand(SUPPLIER_CODES),
                rand(new String[]{"DRAFT", "PENDING_APPROVAL", "APPROVED", "SENT", "PARTIALLY_RECEIVED", "RECEIVED", "CANCELLED"}),
                "PO for Q" + (randInt(1, 4)) + " " + LocalDate.now().getYear() + " inventory replenishment",
                String.valueOf(randInt(1000, 100000) + randDouble(0, 0.99))
            });
        }
        return toFormat(rows, format);
    }

    private String generateInvoices(int count, String format) {
        List<String[]> rows = new ArrayList<>();
        rows.add(new String[]{"invoice_number", "order_id", "status", "amount"});
        for (int i = 0; i < count; i++) {
            rows.add(new String[]{
                "INV-" + (10000 + i),
                "ORD-" + (10000 + randInt(90000)),
                rand(new String[]{"DRAFT", "SENT", "PAID", "OVERDUE", "CANCELLED", "REFUNDED"}),
                String.valueOf(randInt(100, 50000) + randDouble(0, 0.99))
            });
        }
        return toFormat(rows, format);
    }

    private String generateWarehouses(int count, String format) {
        List<String[]> rows = new ArrayList<>();
        rows.add(new String[]{"code", "name", "address", "city", "state", "zip", "country", "capacity_sqft", "manager_name", "manager_phone", "manager_email"});
        String[][] whData = {
            {"WH-MUM-01", "Mumbai Fulfillment Center", "1 JNPT Road", "Mumbai", "Maharashtra", "400001", "India", "50000", "Rajesh Mehta", "9820000001", "rajesh.mehta@nexus.com"},
            {"WH-DEL-01", "Delhi Fulfillment Center", "42 GT Karnal Road", "Delhi", "Delhi", "110001", "India", "45000", "Sunil Kapoor", "9811000001", "sunil.kapoor@nexus.com"},
            {"WH-BLR-01", "Bangalore Tech Hub", "88 Electronic City Phase 1", "Bangalore", "Karnataka", "560100", "India", "35000", "Ananya Iyer", "9845000001", "ananya.iyer@nexus.com"},
            {"WH-HYD-01", "Hyderabad Logistics Hub", "15 HITEC City", "Hyderabad", "Telangana", "500081", "India", "40000", "Venkatesh Rao", "9849000001", "venkatesh.rao@nexus.com"},
            {"WH-CHE-01", "Chennai Port Warehouse", "7 GST Road", "Chennai", "Tamil Nadu", "600001", "India", "30000", "Karthik Subramanian", "9841000001", "karthik.s@nexus.com"},
        };
        for (int i = 0; i < Math.min(count, whData.length); i++) rows.add(whData[i]);
        for (int i = whData.length; i < count; i++) {
            int ci = randInt(CITIES.length);
            String code = "WH-" + CITIES[ci].substring(0, 3).toUpperCase() + "-" + String.format("%02d", randInt(1, 9));
            String name = CITIES[ci] + " Distribution Center";
            rows.add(new String[]{
                code, name,
                randInt(1, 999) + " Industrial Area",
                CITIES[ci], STATES[ci],
                String.valueOf(100000 + randInt(899999)), "India",
                String.valueOf(randInt(10000, 100000)),
                rand(FIRST_NAMES) + " " + rand(LAST_NAMES),
                "98" + (10000000 + randInt(90000000)),
                rand(FIRST_NAMES).toLowerCase() + "." + rand(LAST_NAMES).toLowerCase() + "@nexus.com"
            });
        }
        return toFormat(rows, format);
    }

    private String toFormat(List<String[]> rows, String format) {
        return switch (format) {
            case "csv" -> toCsv(rows);
            case "json" -> toJson(rows);
            default -> toCsv(rows);
        };
    }

    private String toCsv(List<String[]> rows) {
        StringBuilder sb = new StringBuilder();
        for (String[] row : rows) {
            for (int i = 0; i < row.length; i++) {
                if (i > 0) sb.append(',');
                String val = row[i];
                if (val.contains(",") || val.contains("\"") || val.contains("\n")) {
                    sb.append('"').append(val.replace("\"", "\"\"")).append('"');
                } else {
                    sb.append(val);
                }
            }
            sb.append('\n');
        }
        return sb.toString();
    }

    private String toJson(List<String[]> rows) {
        if (rows.isEmpty()) return "[]";
        String[] headers = rows.get(0);
        StringBuilder sb = new StringBuilder("[\n");
        for (int r = 1; r < rows.size(); r++) {
            if (r > 1) sb.append(",\n");
            sb.append("  {");
            for (int c = 0; c < headers.length; c++) {
                if (c > 0) sb.append(", ");
                sb.append('"').append(escapeJson(headers[c])).append("\": \"").append(escapeJson(rows.get(r)[c])).append('"');
            }
            sb.append('}');
        }
        sb.append("\n]");
        return sb.toString();
    }

    private String escapeJson(String s) {
        return s.replace("\\", "\\\\").replace("\"", "\\\"").replace("\n", "\\n").replace("\r", "\\r").replace("\t", "\\t");
    }

    private String rand(String[] arr) { return arr[randInt(arr.length)]; }
    private int randInt(int bound) { return ThreadLocalRandom.current().nextInt(bound); }
    private int randInt(int min, int max) { return ThreadLocalRandom.current().nextInt(min, max + 1); }
    private double randDouble(double min, double max) { return ThreadLocalRandom.current().nextDouble(min, max); }
}
