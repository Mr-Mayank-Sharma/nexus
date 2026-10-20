package com.nexus.oms.generic;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.node.MissingNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.nexus.oms.exception.BadRequestException;

import java.util.ArrayList;
import java.util.List;
import java.util.regex.Pattern;

/** Dotted paths into JSON: {@code a.b}, {@code items[0].sku}, {@code meta["x-y"]}, and {@code $} for the node itself. */
public final class JsonPaths {

    private static final Pattern VALID = Pattern.compile("^(\\$|[A-Za-z0-9_\\-@$]+(\\[\\d+])*|\\[\"[^\"]+\"])((\\.[A-Za-z0-9_\\-@$]+)|(\\[\\d+])|(\\[\"[^\"]+\"]))*$");

    private JsonPaths() {}

    public static void check(String path) {
        if (path == null || path.isBlank() || (!"$".equals(path) && !VALID.matcher(path).matches())) {
            throw new BadRequestException("Not a valid path: '" + path + "' (use a.b, items[0].sku or $)");
        }
    }

    public static JsonNode get(JsonNode node, String path) {
        if (node == null) return MissingNode.getInstance();
        if (path == null || path.isEmpty() || "$".equals(path)) return node;
        JsonNode cur = node;
        for (String step : steps(path)) {
            if (cur == null || cur.isMissingNode() || cur.isNull()) return MissingNode.getInstance();
            if (step.startsWith("#")) cur = cur.path(Integer.parseInt(step.substring(1)));
            else cur = cur.path(step);
        }
        return cur;
    }

    /** Sets a value at a dotted path, creating objects on the way (used to build export payloads). */
    public static void put(ObjectNode root, String path, String value) {
        List<String> s = steps(path);
        ObjectNode cur = root;
        for (int i = 0; i < s.size() - 1; i++) {
            JsonNode next = cur.get(s.get(i));
            cur = next instanceof ObjectNode o ? o : cur.putObject(s.get(i));
        }
        cur.put(s.get(s.size() - 1), value);
    }

    /** Splits a path into keys; array indexes come back as "#n". */
    static List<String> steps(String path) {
        List<String> out = new ArrayList<>();
        int i = 0;
        StringBuilder key = new StringBuilder();
        while (i < path.length()) {
            char c = path.charAt(i);
            if (c == '.') { flush(out, key); i++; }
            else if (c == '[') {
                flush(out, key);
                int end = path.indexOf(']', i);
                String inner = path.substring(i + 1, end);
                out.add(inner.startsWith("\"") ? inner.substring(1, inner.length() - 1) : "#" + inner);
                i = end + 1;
            } else { key.append(c); i++; }
        }
        flush(out, key);
        return out;
    }

    private static void flush(List<String> out, StringBuilder key) {
        if (key.length() > 0) { out.add(key.toString()); key.setLength(0); }
    }
}
