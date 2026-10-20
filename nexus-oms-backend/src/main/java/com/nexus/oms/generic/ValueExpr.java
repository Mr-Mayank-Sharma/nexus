package com.nexus.oms.generic;

import com.fasterxml.jackson.databind.JsonNode;
import com.nexus.oms.exception.BadRequestException;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.Instant;
import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.*;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * One mapped value. Three forms:
 * <ul>
 *   <li>a path into the record: {@code billing.email}, {@code items[0].sku}, {@code $} for the record itself</li>
 *   <li>a literal: {@code =USD}</li>
 *   <li>a template: {@code {billing.first_name} {billing.last_name}}</li>
 * </ul>
 * Any form can end with transforms after {@code |}: trim, upper, lower, int, number, cents (divides by 100),
 * date (any ISO or epoch value to an ISO date-time), default:VALUE (used when the value is empty).
 */
public final class ValueExpr {

    private static final Pattern TEMPLATE_PART = Pattern.compile("\\{([^{}]+)}");
    private static final Set<String> TRANSFORMS = Set.of("trim", "upper", "lower", "int", "number", "cents", "date", "string");

    private final String literal;        // non-null for =literal
    private final String path;           // non-null for a plain path
    private final String template;       // non-null for a template
    private final List<String> transforms;

    private ValueExpr(String literal, String path, String template, List<String> transforms) {
        this.literal = literal;
        this.path = path;
        this.template = template;
        this.transforms = transforms;
    }

    public static ValueExpr compile(String expr) {
        if (expr == null || expr.isBlank()) throw new BadRequestException("A mapped field is empty");
        String body = expr;
        List<String> transforms = new ArrayList<>();
        if (!expr.startsWith("=")) {
            String[] parts = expr.split("\\|");
            body = parts[0].trim();
            for (int i = 1; i < parts.length; i++) {
                String t = parts[i].trim();
                String name = t.contains(":") ? t.substring(0, t.indexOf(':')) : t;
                if (!TRANSFORMS.contains(name) && !"default".equals(name)) throw new BadRequestException("Unknown transform '" + t + "' in " + expr);
                transforms.add(t);
            }
        }
        if (body.startsWith("=")) return new ValueExpr(body.substring(1), null, null, transforms);
        if (body.contains("{")) {
            Matcher m = TEMPLATE_PART.matcher(body);
            while (m.find()) JsonPaths.check(m.group(1).trim());
            return new ValueExpr(null, null, body, transforms);
        }
        JsonPaths.check(body);
        return new ValueExpr(null, body, null, transforms);
    }

    /** The value as text, or null when the record has nothing there. */
    public String eval(JsonNode record) {
        String v;
        if (literal != null) v = literal;
        else if (path != null) v = text(JsonPaths.get(record, path));
        else {
            Matcher m = TEMPLATE_PART.matcher(template);
            StringBuilder sb = new StringBuilder();
            boolean any = false;
            while (m.find()) {
                String part = text(JsonPaths.get(record, m.group(1).trim()));
                if (part != null && !part.isEmpty()) any = true;
                m.appendReplacement(sb, Matcher.quoteReplacement(part == null ? "" : part));
            }
            m.appendTail(sb);
            v = any ? sb.toString().replaceAll("\\s+", " ").trim() : null;
        }
        for (String t : transforms) v = apply(t, v);
        return v;
    }

    private static String text(JsonNode n) {
        if (n == null || n.isNull() || n.isMissingNode()) return null;
        return n.isValueNode() ? n.asText() : n.toString();
    }

    static String apply(String t, String v) {
        if (t.startsWith("default:")) return v == null || v.isBlank() ? t.substring("default:".length()) : v;
        if (v == null) return null;
        return switch (t) {
            case "trim", "string" -> v.trim();
            case "upper" -> v.trim().toUpperCase(Locale.ROOT);
            case "lower" -> v.trim().toLowerCase(Locale.ROOT);
            case "int" -> String.valueOf(new BigDecimal(v.trim()).setScale(0, RoundingMode.HALF_UP).intValueExact());
            case "number" -> new BigDecimal(v.trim()).toPlainString();
            case "cents" -> new BigDecimal(v.trim()).movePointLeft(2).toPlainString();
            case "date" -> toIso(v.trim());
            default -> v;
        };
    }

    /** Accepts ISO date-times (with or without offset), plain dates, and epoch seconds or millis. */
    static String toIso(String v) {
        if (v.matches("\\d{12,}")) return Instant.ofEpochMilli(Long.parseLong(v)).toString();
        if (v.matches("\\d{9,11}")) return Instant.ofEpochSecond(Long.parseLong(v)).toString();
        try { return OffsetDateTime.parse(v).toInstant().toString(); } catch (Exception ignored) { }
        try { return java.time.LocalDateTime.parse(v.replace(' ', 'T')).toInstant(ZoneOffset.UTC).toString(); } catch (Exception ignored) { }
        try { return LocalDate.parse(v).atStartOfDay(ZoneOffset.UTC).toInstant().toString(); } catch (Exception ignored) { }
        throw new IllegalArgumentException("not a date: " + v);
    }
}
