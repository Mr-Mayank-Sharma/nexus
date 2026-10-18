package com.nexus.oms.jobs;

import java.util.*;

/**
 * One kind of job a customer can create and schedule. A handler describes the parameters it needs, does the
 * work, and reports counts, the rows that failed (with the reason), and optionally an output file.
 */
public interface JobHandler {

    String type();

    String label();

    String description();

    List<Param> params();

    Outcome run(Context ctx) throws Exception;

    /** @param kind STORE (an integration store id), BOOLEAN or TEXT */
    record Param(String name, String label, String kind, boolean required, String help) {}

    /** A row that failed or needs a person's attention. */
    record Issue(String rowRef, String reason, String raw) {}

    final class Context {
        private final UUID tenantId;
        private final Map<String, Object> params;
        private final List<String> lines = new ArrayList<>();

        public Context(UUID tenantId, Map<String, Object> params) {
            this.tenantId = tenantId;
            this.params = params == null ? Map.of() : params;
        }

        public UUID tenantId() { return tenantId; }

        public String text(String name) {
            Object v = params.get(name);
            return v == null ? null : String.valueOf(v).trim();
        }

        public boolean flag(String name) {
            Object v = params.get(name);
            return v instanceof Boolean b ? b : v != null && "true".equalsIgnoreCase(String.valueOf(v));
        }

        public UUID uuid(String name) {
            String v = text(name);
            if (v == null || v.isEmpty()) return null;
            return UUID.fromString(v);
        }

        public void log(String line) {
            if (lines.size() < 2000) lines.add(java.time.LocalTime.now().format(java.time.format.DateTimeFormatter.ofPattern("HH:mm:ss")) + "  " + line);
        }

        public List<String> lines() { return lines; }
    }

    final class Outcome {
        int total, succeeded, failed;
        String message;
        String outputName;
        String outputCsv;
        final List<Issue> issues = new ArrayList<>();

        public static Outcome of(int total, int succeeded, int failed, String message) {
            Outcome o = new Outcome();
            o.total = total;
            o.succeeded = succeeded;
            o.failed = failed;
            o.message = message;
            return o;
        }

        public Outcome issue(String rowRef, String reason, String raw) {
            issues.add(new Issue(rowRef, reason, raw));
            return this;
        }

        public Outcome output(String name, String csv) {
            this.outputName = name;
            this.outputCsv = csv;
            return this;
        }

        public int total() { return total; }
        public int succeeded() { return succeeded; }
        public int failed() { return failed; }
        public String message() { return message; }
        public List<Issue> issues() { return issues; }
    }
}
