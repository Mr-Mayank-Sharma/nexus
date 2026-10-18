package com.nexus.oms.replay;

import com.nexus.oms.exception.BadRequestException;
import com.nexus.oms.replay.ReplayEngine.*;

import java.util.*;

/** Reads the three files a replay needs. Forgiving about column names, strict about numbers, and specific about where a file is wrong. */
final class ReplayCsv {

    static final int MAX_ROWS = 250_000;
    static final int MAX_CHARS = 25_000_000;

    private ReplayCsv() {}

    record Row(int line, Map<String, String> cells) {
        String get(String... names) {
            for (String n : names) {
                String v = cells.get(n);
                if (v != null && !v.isBlank()) return v.trim();
            }
            return null;
        }
    }

    static List<Row> rows(String text, String fileLabel) {
        if (text == null || text.isBlank()) throw new BadRequestException("The " + fileLabel + " file is empty.");
        if (text.length() > MAX_CHARS) throw new BadRequestException("The " + fileLabel + " file is larger than 25 MB.");
        List<List<String>> records = split(text.startsWith("﻿") ? text.substring(1) : text);
        if (records.isEmpty()) throw new BadRequestException("The " + fileLabel + " file has no rows.");
        List<String> header = new ArrayList<>();
        for (String h : records.get(0)) header.add(h.trim().toLowerCase(Locale.ROOT).replace(' ', '_'));
        if (records.size() - 1 > MAX_ROWS) throw new BadRequestException("The " + fileLabel + " file has more than " + MAX_ROWS + " rows.");
        List<Row> out = new ArrayList<>();
        for (int i = 1; i < records.size(); i++) {
            List<String> r = records.get(i);
            if (r.size() == 1 && r.get(0).isBlank()) continue;
            Map<String, String> cells = new HashMap<>();
            for (int c = 0; c < header.size() && c < r.size(); c++) cells.put(header.get(c), r.get(c));
            out.add(new Row(i + 1, cells));
        }
        return out;
    }

    static List<Order> orders(String text) {
        List<String> errors = new ArrayList<>();
        Map<String, List<Line>> lines = new LinkedHashMap<>();
        Map<String, Double[]> coords = new HashMap<>();
        for (Row r : rows(text, "orders")) {
            String id = r.get("order_id", "order", "order_number", "name", "id");
            String sku = r.get("sku", "item", "product", "product_sku");
            Integer qty = integer(r, errors, "orders", "qty", "quantity", "units");
            if (id == null || sku == null) { errors.add("orders line " + r.line() + ": needs an order_id and a sku"); continue; }
            if (qty == null) continue;
            if (qty <= 0) { errors.add("orders line " + r.line() + ": qty must be at least 1"); continue; }
            lines.computeIfAbsent(id, k -> new ArrayList<>()).add(new Line(sku, qty));
            Double lat = number(r, errors, "orders", "lat", "latitude"), lon = number(r, errors, "orders", "lon", "lng", "longitude");
            if (lat != null && lon != null) coords.putIfAbsent(id, new Double[]{lat, lon});
        }
        fail(errors);
        List<Order> out = new ArrayList<>();
        lines.forEach((id, ls) -> {
            Double[] c = coords.get(id);
            out.add(new Order(id, ls, c == null ? null : c[0], c == null ? null : c[1]));
        });
        if (out.isEmpty()) throw new BadRequestException("The orders file has no usable rows. Columns: order_id, sku, qty (optional: lat, lon).");
        return out;
    }

    static Map<String, Map<String, Integer>> stock(String text) {
        List<String> errors = new ArrayList<>();
        Map<String, Map<String, Integer>> out = new HashMap<>();
        for (Row r : rows(text, "stock")) {
            String sku = r.get("sku", "item", "product", "product_sku");
            String node = r.get("node_id", "node", "location_id", "location", "store", "warehouse");
            Integer qty = integer(r, errors, "stock", "qty", "quantity", "on_hand", "available", "units");
            if (sku == null || node == null) { errors.add("stock line " + r.line() + ": needs a sku and a node_id"); continue; }
            if (qty == null) continue;
            out.computeIfAbsent(sku, k -> new HashMap<>()).merge(node, Math.max(0, qty), Integer::sum);
        }
        fail(errors);
        if (out.isEmpty()) throw new BadRequestException("The stock file has no usable rows. Columns: sku, node_id, qty.");
        return out;
    }

    static List<Node> nodes(String text) {
        List<String> errors = new ArrayList<>();
        List<Node> out = new ArrayList<>();
        Set<String> seen = new HashSet<>();
        for (Row r : rows(text, "locations")) {
            String id = r.get("node_id", "id", "location_id", "location", "code", "store");
            if (id == null) { errors.add("locations line " + r.line() + ": needs a node_id"); continue; }
            if (!seen.add(id)) { errors.add("locations line " + r.line() + ": '" + id + "' appears twice"); continue; }
            Double rate = number(r, errors, "locations", "failure_rate", "pick_failure_rate", "fail_rate");
            if (rate != null && rate > 1) rate = rate / 100.0;     // 12 means 12%
            if (rate != null && (rate < 0 || rate > 1)) { errors.add("locations line " + r.line() + ": failure_rate must be between 0 and 1 (or 0 to 100 as a percentage)"); continue; }
            out.add(new Node(id, r.get("name", "label"), number(r, errors, "locations", "lat", "latitude"), number(r, errors, "locations", "lon", "lng", "longitude"), rate));
        }
        fail(errors);
        return out;
    }

    // ---- helpers ----------------------------------------------------------------------------------------------

    private static Integer integer(Row r, List<String> errors, String file, String... names) {
        String v = r.get(names);
        if (v == null) { errors.add(file + " line " + r.line() + ": missing " + names[0]); return null; }
        try {
            double d = Double.parseDouble(v.replace(",", ""));
            return (int) Math.round(d);
        } catch (NumberFormatException e) {
            errors.add(file + " line " + r.line() + ": '" + v + "' is not a number (" + names[0] + ")");
            return null;
        }
    }

    private static Double number(Row r, List<String> errors, String file, String... names) {
        String v = r.get(names);
        if (v == null) return null;
        try {
            return Double.parseDouble(v);
        } catch (NumberFormatException e) {
            errors.add(file + " line " + r.line() + ": '" + v + "' is not a number (" + names[0] + ")");
            return null;
        }
    }

    private static void fail(List<String> errors) {
        if (errors.isEmpty()) return;
        String first = String.join("; ", errors.subList(0, Math.min(10, errors.size())));
        throw new BadRequestException(errors.size() + " problem(s) in the files. " + first + (errors.size() > 10 ? " … and " + (errors.size() - 10) + " more." : ""));
    }

    /** RFC 4180-style: commas, double quotes, doubled quotes, and newlines inside quotes. */
    static List<List<String>> split(String text) {
        List<List<String>> records = new ArrayList<>();
        List<String> cur = new ArrayList<>();
        StringBuilder cell = new StringBuilder();
        boolean quoted = false;
        for (int i = 0; i < text.length(); i++) {
            char ch = text.charAt(i);
            if (quoted) {
                if (ch == '"') {
                    if (i + 1 < text.length() && text.charAt(i + 1) == '"') { cell.append('"'); i++; } else quoted = false;
                } else cell.append(ch);
            } else if (ch == '"') quoted = true;
            else if (ch == ',') { cur.add(cell.toString()); cell.setLength(0); }
            else if (ch == '\n' || ch == '\r') {
                if (ch == '\r' && i + 1 < text.length() && text.charAt(i + 1) == '\n') i++;
                cur.add(cell.toString()); cell.setLength(0);
                records.add(cur); cur = new ArrayList<>();
            } else cell.append(ch);
        }
        if (cell.length() > 0 || !cur.isEmpty()) { cur.add(cell.toString()); records.add(cur); }
        return records;
    }
}
