package com.nexus.core.ai;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Masks customer details before text leaves the OMS for a model (doc 25, rule 5): email addresses, phone numbers and
 * the names tools mark as personal. Each value gets a stable token ([EMAIL_1], [NAME_2]) within one run, so the model
 * can still tell people apart, and the answer is unmasked for the person who asked. One masker per run.
 */
public final class Masker {

    private static final Pattern EMAIL = Pattern.compile("[A-Za-z0-9._%+-]+@[A-Za-z0-9.-]+\\.[A-Za-z]{2,}");
    /** Ten or more digits with the usual separators, not inside a longer word (so order ids are left alone). */
    private static final Pattern PHONE = Pattern.compile("(?<![\\w])\\+?\\d[\\d\\s().-]{8,}\\d(?![\\w])");

    private final Map<String, String> tokenByValue = new LinkedHashMap<>();
    private final Map<String, Integer> countByKind = new LinkedHashMap<>();

    /** A personal name a tool returned: masked wherever it appears from now on. Returns its token. */
    public String name(String personalName) {
        if (personalName == null || personalName.isBlank()) {
            return personalName;
        }
        return tokenFor("NAME", personalName.trim());
    }

    /** Replaces every known name, and every email and phone number, with its token. */
    public String mask(String text) {
        if (text == null) {
            return null;
        }
        String masked = text;
        for (Map.Entry<String, String> known : tokenByValue.entrySet()) {
            masked = masked.replace(known.getKey(), known.getValue());
        }
        masked = replaceMatches(EMAIL, masked, "EMAIL");
        masked = replaceMatches(PHONE, masked, "PHONE");
        return masked;
    }

    /** Puts the real values back in place of their tokens. */
    public String unmask(String text) {
        if (text == null) {
            return null;
        }
        String unmasked = text;
        for (Map.Entry<String, String> known : tokenByValue.entrySet()) {
            unmasked = unmasked.replace(known.getValue(), known.getKey());
        }
        return unmasked;
    }

    private String replaceMatches(Pattern pattern, String text, String kind) {
        Matcher matcher = pattern.matcher(text);
        StringBuilder replaced = new StringBuilder();
        while (matcher.find()) {
            matcher.appendReplacement(replaced, Matcher.quoteReplacement(tokenFor(kind, matcher.group())));
        }
        matcher.appendTail(replaced);
        return replaced.toString();
    }

    /** The value's token, numbered per kind in the order values are first seen. */
    private String tokenFor(String kind, String value) {
        return tokenByValue.computeIfAbsent(value, newValue -> "[" + kind + "_" + countByKind.merge(kind, 1, Integer::sum) + "]");
    }
}
