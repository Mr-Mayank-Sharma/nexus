package com.nexus.core.ai;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Masks customer details before text leaves the OMS for a model (doc 25, rule 5): email addresses, phone numbers and
 * the names tools mark as personal. Each value gets a stable token ([EMAIL_1], [NAME_2]) within one run, so the model
 * can still tell people apart, and the answer is unmasked for the person who asked.
 */
public final class Masker {

    private static final Pattern EMAIL = Pattern.compile("[A-Za-z0-9._%+-]+@[A-Za-z0-9.-]+\\.[A-Za-z]{2,}");
    private static final Pattern PHONE = Pattern.compile("(?<![\\w])\\+?\\d[\\d\\s().-]{8,}\\d(?![\\w])");

    private final Map<String, String> tokenByValue = new LinkedHashMap<>();
    private final Map<String, Integer> counters = new LinkedHashMap<>();

    /** A personal name a tool returned: masked wherever it appears from now on. */
    public String name(String value) {
        if (value == null || value.isBlank()) return value;
        return token("NAME", value.trim());
    }

    public String mask(String text) {
        if (text == null) return null;
        String out = text;
        for (Map.Entry<String, String> e : tokenByValue.entrySet()) out = out.replace(e.getKey(), e.getValue());
        out = replace(EMAIL, out, "EMAIL");
        out = replace(PHONE, out, "PHONE");
        return out;
    }

    public String unmask(String text) {
        if (text == null) return null;
        String out = text;
        for (Map.Entry<String, String> e : tokenByValue.entrySet()) out = out.replace(e.getValue(), e.getKey());
        return out;
    }

    private String replace(Pattern p, String text, String kind) {
        Matcher m = p.matcher(text);
        StringBuilder sb = new StringBuilder();
        while (m.find()) m.appendReplacement(sb, Matcher.quoteReplacement(token(kind, m.group())));
        m.appendTail(sb);
        return sb.toString();
    }

    private String token(String kind, String value) {
        return tokenByValue.computeIfAbsent(value, v -> "[" + kind + "_" + counters.merge(kind, 1, Integer::sum) + "]");
    }
}
