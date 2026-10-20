package com.nexus.oms.generic;

import com.fasterxml.jackson.databind.JsonNode;

import java.util.*;

/**
 * Applies a connection's field mapping to one source record: the record's own fields, and for orders each line
 * found at {@code lines.path}. A mapping that fails on a record (a bad number, an unreadable date) does not stop the
 * run: the record comes back with the problem, and the caller lists it as a failed row with that reason.
 */
public final class GenericMapper {

    private final Map<String, ValueExpr> fields = new LinkedHashMap<>();
    private final Map<String, ValueExpr> lineFields = new LinkedHashMap<>();
    private final String linesPath;

    public GenericMapper(GenericConfig c) {
        c.fields().forEach((k, v) -> fields.put(k, ValueExpr.compile(v)));
        c.lineFields().forEach((k, v) -> lineFields.put(k, ValueExpr.compile(v)));
        this.linesPath = c.linesPath();
    }

    public record Mapped(Map<String, String> fields, List<Map<String, String>> lines, String problem) {
        public String get(String k) { return fields.get(k); }
        public boolean ok() { return problem == null; }
    }

    public Mapped map(JsonNode record) {
        Map<String, String> out = new LinkedHashMap<>();
        List<Map<String, String>> lines = new ArrayList<>();
        try {
            for (var e : fields.entrySet()) out.put(e.getKey(), eval(e.getValue(), record, e.getKey()));
            if (!linesPath.isBlank()) {
                JsonNode arr = JsonPaths.get(record, linesPath);
                if (arr.isArray()) {
                    int i = 0;
                    for (JsonNode line : arr) {
                        i++;
                        Map<String, String> l = new LinkedHashMap<>();
                        for (var e : lineFields.entrySet()) l.put(e.getKey(), eval(e.getValue(), line, "line " + i + " " + e.getKey()));
                        lines.add(l);
                    }
                }
            }
            return new Mapped(out, lines, null);
        } catch (MappingProblem p) {
            return new Mapped(out, lines, p.getMessage());
        }
    }

    private static String eval(ValueExpr x, JsonNode n, String label) {
        try {
            return x.eval(n);
        } catch (RuntimeException e) {
            throw new MappingProblem(label + ": " + e.getMessage());
        }
    }

    static final class MappingProblem extends RuntimeException {
        MappingProblem(String m) { super(m); }
    }
}
