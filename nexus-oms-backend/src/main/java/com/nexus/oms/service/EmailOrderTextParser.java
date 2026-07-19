package com.nexus.oms.service;

import java.math.BigDecimal;
import java.util.*;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public class EmailOrderTextParser {

    public Map<String, Object> extractOrderFromText(String body) {
        Map<String, Object> data = new LinkedHashMap<>();
        List<Map<String, Object>> items = new ArrayList<>();

        data.put("customerName", extractField(body, "(?:Customer|Name|Bill To)[:\\s]+(.+)", 1));
        data.put("customerEmail", extractField(body, "(?:Email|E-mail)[:\\s]+([^\\s]+@[^\\s]+)", 1));
        data.put("customerPhone", extractField(body, "(?:Phone|Tel|Telephone)[:\\s]+([\\d\\-\\+\\(\\)\\s]+)", 1));

        String orderNum = extractField(body, "(?:Order|PO|Purchase Order)\\s*(?:#|No|Number)?[.:\\s]+([A-Za-z0-9\\-]+)", 1);
        data.put("orderNumber", orderNum);

        String totalStr = extractField(body, "(?:Total|Amount|Grand Total)[:\\s]*\\$?([\\d,]+(?:\\.[\\d]{2})?)", 1);
        if (totalStr != null) {
            data.put("orderTotal", totalStr.replace(",", ""));
        }

        Pattern itemPattern = Pattern.compile(
            "(?:SKU|Item|Part)\\s*[:#]?\\s*(\\S+)\\s+(?:x|X|\\*)?\\s*(\\d+)?\\s*" +
            "(?:-\\s*)?([^$\\n]+)?\\s*\\$?([\\d,.]+)", Pattern.MULTILINE);
        Matcher itemMatcher = itemPattern.matcher(body);
        while (itemMatcher.find()) {
            Map<String, Object> item = new LinkedHashMap<>();
            item.put("sku", itemMatcher.group(1));
            item.put("quantity", itemMatcher.group(2) != null ? Integer.parseInt(itemMatcher.group(2)) : 1);
            item.put("description", itemMatcher.group(3) != null ? itemMatcher.group(3).trim() : "");
            item.put("price", itemMatcher.group(4));
            items.add(item);
        }

        if (items.isEmpty()) {
            Pattern tablePattern = Pattern.compile(
                "(\\d+)\\s+(.+?)\\s+\\$?([\\d,.]+)", Pattern.MULTILINE);
            Matcher tableMatcher = tablePattern.matcher(body);
            while (tableMatcher.find()) {
                Map<String, Object> item = new LinkedHashMap<>();
                item.put("quantity", Integer.parseInt(tableMatcher.group(1)));
                item.put("description", tableMatcher.group(2).trim());
                item.put("price", tableMatcher.group(3));
                items.add(item);
            }
        }

        data.put("items", items);
        data.put("itemCount", items.size());

        String address = extractMultilineField(body, "(?:Ship To|Shipping Address|Deliver To)[:\\n]+([^\\n]+\\n[^\\n]+(?:\\n[^\\n]+)?)");
        if (address != null) {
            data.put("shippingAddress", address.trim());
        }

        String shipping = extractField(body, "(?:Ship Via|Shipping Method|Carrier)[:\\s]+(.+)", 1);
        data.put("shippingMethod", shipping);

        return data;
    }

    public BigDecimal calculateConfidence(Map<String, Object> data) {
        int signals = 0;
        int total = 6;

        if (data.get("customerName") != null && !((String) data.get("customerName")).isBlank()) signals++;
        if (data.get("customerEmail") != null && !((String) data.get("customerEmail")).isBlank()) signals++;
        if (data.get("orderTotal") != null) signals++;
        List<?> items = (List<?>) data.getOrDefault("items", Collections.emptyList());
        if (!items.isEmpty()) signals++;
        if (data.get("orderNumber") != null) signals++;
        if (data.get("shippingAddress") != null) signals++;

        return BigDecimal.valueOf(signals).divide(BigDecimal.valueOf(total), 4, java.math.RoundingMode.HALF_UP);
    }

    private String extractField(String text, String regex, int group) {
        Pattern pattern = Pattern.compile(regex, Pattern.MULTILINE | Pattern.CASE_INSENSITIVE);
        Matcher matcher = pattern.matcher(text);
        return matcher.find() ? matcher.group(group).trim() : null;
    }

    private String extractMultilineField(String text, String regex) {
        Pattern pattern = Pattern.compile(regex, Pattern.MULTILINE | Pattern.CASE_INSENSITIVE);
        Matcher matcher = pattern.matcher(text);
        return matcher.find() ? matcher.group(1).trim() : null;
    }
}
