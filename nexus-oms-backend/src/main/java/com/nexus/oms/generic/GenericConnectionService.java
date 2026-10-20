package com.nexus.oms.generic;

import com.fasterxml.jackson.databind.JsonNode;
import com.nexus.oms.exception.BadRequestException;
import com.nexus.oms.exception.ResourceNotFoundException;
import com.nexus.oms.jobs.JobHandler;
import org.springframework.stereotype.Service;

import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.util.*;

/**
 * Generic connections: create and check them, preview what a mapping produces, and run imports and exports.
 * Runs are started by the GENERIC_IMPORT and GENERIC_EXPORT job types, so they get schedules, logs and
 * per-row failure files from the job manager.
 */
@Service
public class GenericConnectionService {

    static final int PREVIEW_RECORDS = 20;
    /** Incremental imports re-read a little before the last watermark, so records changed during a run are not missed. */
    static final int OVERLAP_MINUTES = 10;

    private final GenericConnectionRepository repo;
    private final GenericHttp http;
    private final GenericOrderWriter orderWriter;
    private final GenericStockWriter stockWriter;
    private final GenericExporter exporter;

    public GenericConnectionService(GenericConnectionRepository repo, GenericHttp http, GenericOrderWriter orderWriter,
                                    GenericStockWriter stockWriter, GenericExporter exporter) {
        this.repo = repo;
        this.http = http;
        this.orderWriter = orderWriter;
        this.stockWriter = stockWriter;
        this.exporter = exporter;
    }

    public record ConnectionRequest(String name, String direction, String entityType, String channel, Object config, String secret) {}

    // ------------------------------------------------------------------ CRUD

    public List<NxGenericConnection> list(UUID tenantId) {
        return repo.findByTenantIdOrderByNameAsc(tenantId);
    }

    public NxGenericConnection get(UUID tenantId, UUID id) {
        return repo.findByIdAndTenantId(id, tenantId).orElseThrow(() -> new ResourceNotFoundException("Connection", id));
    }

    public NxGenericConnection create(UUID tenantId, ConnectionRequest r) {
        String name = required(r.name(), "name");
        if (repo.existsByTenantIdAndName(tenantId, name)) throw new BadRequestException("A connection called '" + name + "' already exists");
        NxGenericConnection c = new NxGenericConnection();
        c.setId(UUID.randomUUID());
        c.setTenantId(tenantId);
        apply(c, r, true);
        return repo.save(c);
    }

    public NxGenericConnection update(UUID tenantId, UUID id, ConnectionRequest r) {
        NxGenericConnection c = get(tenantId, id);
        String name = required(r.name(), "name");
        if (!name.equals(c.getName()) && repo.existsByTenantIdAndName(tenantId, name)) throw new BadRequestException("A connection called '" + name + "' already exists");
        apply(c, r, false);
        c.setUpdatedAt(LocalDateTime.now());
        return repo.save(c);
    }

    public void delete(UUID tenantId, UUID id) {
        repo.delete(get(tenantId, id));
    }

    /** Forget the watermark, so the next import or export starts from the beginning. */
    public NxGenericConnection resetWatermark(UUID tenantId, UUID id) {
        NxGenericConnection c = get(tenantId, id);
        c.setWatermark(null);
        return repo.save(c);
    }

    private void apply(NxGenericConnection c, ConnectionRequest r, boolean creating) {
        String direction = required(r.direction(), "direction").toUpperCase(Locale.ROOT);
        if (!Set.of("IMPORT", "EXPORT").contains(direction)) throw new BadRequestException("direction must be IMPORT or EXPORT");
        String entity = required(r.entityType(), "entityType").toLowerCase(Locale.ROOT);
        String config = r.config() == null ? "{}" : r.config() instanceof String s ? s : toJson(r.config());
        GenericConfig.parse(config, direction, entity);      // throws with a readable reason if anything is wrong
        c.setName(r.name().trim());
        c.setDirection(direction);
        c.setEntityType(entity);
        c.setChannel(r.channel() == null || r.channel().isBlank() ? null : r.channel().trim().toUpperCase(Locale.ROOT));
        c.setConfig(config);
        // an empty secret on update keeps the saved one; the UI never sees it to send it back
        if (r.secret() != null && !r.secret().isBlank()) c.setSecret(r.secret());
        else if (creating) c.setSecret(null);
    }

    // ------------------------------------------------------------------ preview

    public record PreviewRecord(String ref, Object source, Map<String, String> mapped, List<Map<String, String>> lines, List<String> problems) {}

    public record Preview(String direction, String entityType, List<GenericHttp.Page> pages, int fetched, int valid,
                          List<PreviewRecord> records, Object payload, String csv, String note) {}

    /** Reads the first page (import) or builds the first batch (export) and shows the mapping's result. Saves and sends nothing. */
    public Preview preview(UUID tenantId, UUID id) {
        NxGenericConnection conn = get(tenantId, id);
        GenericConfig cfg = GenericConfig.parse(conn.getConfig(), conn.getDirection(), conn.getEntityType());
        if ("EXPORT".equals(conn.getDirection())) {
            List<GenericExporter.Item> items = exporter.collect(tenantId, conn.getEntityType(), cfg, conn.getWatermark(), 5);
            List<PreviewRecord> recs = items.stream().map(i -> new PreviewRecord(i.ref(), null, null, null, List.of())).toList();
            Object payload = items.isEmpty() ? null : GenericExporter.body(cfg, items.subList(0, Math.min(items.size(), cfg.batchSize())));
            String csv = "FILE".equals(cfg.deliveryMode()) ? GenericExporter.csv(items) : null;
            return new Preview("EXPORT", conn.getEntityType(), List.of(), items.size(), items.size(), recs, payload, csv,
                    items.isEmpty() ? "Nothing to export since the last run." : "This is what the first request would carry. Nothing was sent.");
        }
        GenericMapper mapper = new GenericMapper(cfg);
        List<PreviewRecord> out = new ArrayList<>();
        int[] fetched = {0}, valid = {0};
        List<GenericHttp.Page> pages = http.fetch(cfg, conn.getSecret(), since(conn), 1, recs -> {
            for (JsonNode rec : recs) {
                fetched[0]++;
                GenericMapper.Mapped m = mapper.map(rec);
                List<String> problems = problems(conn.getEntityType(), m);
                if (problems.isEmpty()) valid[0]++;
                if (out.size() < PREVIEW_RECORDS) out.add(new PreviewRecord(ref(conn.getEntityType(), m, fetched[0]), trimmed(rec), m.fields(), m.lines(), problems));
            }
        });
        return new Preview("IMPORT", conn.getEntityType(), pages, fetched[0], valid[0], out, null, null,
                "First page only. Nothing was saved.");
    }

    // ------------------------------------------------------------------ runs (called by the job types)

    public JobHandler.Outcome runImport(UUID tenantId, UUID id, boolean apply, JobHandler.Context ctx) {
        NxGenericConnection conn = get(tenantId, id);
        if (!"IMPORT".equals(conn.getDirection())) throw new BadRequestException("'" + conn.getName() + "' is an export connection");
        GenericConfig cfg = GenericConfig.parse(conn.getConfig(), conn.getDirection(), conn.getEntityType());
        GenericMapper mapper = new GenericMapper(cfg);
        Instant startedAt = Instant.now();
        Instant since = since(conn);
        ctx.log((apply ? "Importing " : "Dry run: reading ") + conn.getEntityType() + " from " + conn.getName()
                + (since != null && !cfg.sinceParam().isBlank() ? " changed since " + since : " (all records)"));

        Map<String, Integer> counts = new LinkedHashMap<>();
        int[] total = {0}, failed = {0};
        JobHandler.Outcome outcome = JobHandler.Outcome.of(0, 0, 0, "");
        GenericStockWriter.Run stock = "inventory".equals(conn.getEntityType()) ? stockWriter.start(tenantId, cfg.locations(), conn.getName()) : null;

        List<GenericHttp.Page> pages = http.fetch(cfg, conn.getSecret(), since, Integer.MAX_VALUE, recs -> {
            for (JsonNode rec : recs) {
                total[0]++;
                GenericMapper.Mapped m = mapper.map(rec);
                String ref = ref(conn.getEntityType(), m, total[0]);
                try {
                    String result;
                    if (stock != null) result = stock.write(m, apply).name();
                    else {
                        List<String> p = GenericOrderWriter.problems(m);
                        if (!p.isEmpty()) throw new IllegalArgumentException(String.join("; ", p));
                        result = orderWriter.write(tenantId, conn.getChannel(), conn.getName(), m, apply).name();
                    }
                    counts.merge(result, 1, Integer::sum);
                } catch (RuntimeException e) {
                    failed[0]++;
                    outcome.issue(ref, e.getMessage() == null ? e.getClass().getSimpleName() : e.getMessage(), GenericHttp.abbreviate(rec.toString(), 2000));
                }
            }
        });
        pages.forEach(p -> ctx.log("Page " + p.number() + ": " + p.records() + " record(s) from " + p.url()));
        if (pages.size() >= cfg.maxPages()) ctx.log("Stopped at the page limit (" + cfg.maxPages() + "); the next run carries on from the watermark.");

        // the watermark only moves on a real run that read everything without stopping at the page limit
        if (apply && pages.size() < cfg.maxPages()) {
            conn.setWatermark(LocalDateTime.ofInstant(startedAt, ZoneOffset.UTC));
            repo.save(conn);
        }
        String summary = summary(counts, failed[0], apply);
        ctx.log(summary);
        JobHandler.Outcome o = JobHandler.Outcome.of(total[0], total[0] - failed[0], failed[0], summary);
        outcome.issues().forEach(i -> o.issue(i.rowRef(), i.reason(), i.raw()));
        return o;
    }

    public JobHandler.Outcome runExport(UUID tenantId, UUID id, JobHandler.Context ctx) {
        NxGenericConnection conn = get(tenantId, id);
        if (!"EXPORT".equals(conn.getDirection())) throw new BadRequestException("'" + conn.getName() + "' is an import connection");
        GenericConfig cfg = GenericConfig.parse(conn.getConfig(), conn.getDirection(), conn.getEntityType());
        List<GenericExporter.Item> items = exporter.collect(tenantId, conn.getEntityType(), cfg, conn.getWatermark(), GenericExporter.PER_RUN);
        ctx.log(items.size() + " " + conn.getEntityType() + " record(s) to " + ("FILE".equals(cfg.deliveryMode()) ? "write to a file" : "send to " + cfg.baseUrl()));
        GenericExporter.Result r = exporter.deliver(cfg, conn.getSecret(), items, conn.getWatermark());
        if (!"inventory".equals(conn.getEntityType()) && r.newWatermark() != null) {
            conn.setWatermark(r.newWatermark());
            repo.save(conn);
        }
        int failed = items.size() - r.sent();
        String msg = r.sent() + " of " + items.size() + " record(s) " + ("FILE".equals(cfg.deliveryMode()) ? "written" : "sent")
                + (failed > 0 ? "; stopped at a rejected batch, the rest go on the next run" : "")
                + (items.size() == GenericExporter.PER_RUN ? "; more are waiting for the next run" : "");
        ctx.log(msg);
        JobHandler.Outcome o = JobHandler.Outcome.of(items.size(), r.sent(), failed, msg);
        r.problems().forEach(p -> o.issue(conn.getName(), p, null));
        if (r.csv() != null) o.output(conn.getEntityType() + "-" + java.time.LocalDate.now() + ".csv", r.csv());
        return o;
    }

    // ------------------------------------------------------------------ templates

    public record Template(String key, String label, String direction, String entityType, String channel, String note, Map<String, Object> config) {}

    /** Starting points. None of these has been run against the real platform yet; each one says so. */
    public List<Template> templates() {
        return GenericTemplates.ALL;
    }

    // ------------------------------------------------------------------ helpers

    static List<String> problems(String entity, GenericMapper.Mapped m) {
        if ("orders".equals(entity)) return GenericOrderWriter.problems(m);
        List<String> p = new ArrayList<>();
        if (!m.ok()) p.add(m.problem());
        else {
            if (GenericOrderWriter.blank(m.get("sku"))) p.add("sku is empty");
            if (GenericOrderWriter.intOrNull(m.get("quantity")) == null) p.add("quantity is not a whole number");
        }
        return p;
    }

    private static String ref(String entity, GenericMapper.Mapped m, int n) {
        String k = "orders".equals(entity) ? m.get("orderId") : m.get("sku") == null ? null : m.get("sku") + (m.get("location") == null ? "" : "@" + m.get("location"));
        return k == null || k.isBlank() ? "record " + n : k;
    }

    private static Instant since(NxGenericConnection c) {
        return c.getWatermark() == null ? null : c.getWatermark().minusMinutes(OVERLAP_MINUTES).toInstant(ZoneOffset.UTC);
    }

    private static String summary(Map<String, Integer> counts, int failed, boolean apply) {
        StringBuilder sb = new StringBuilder(apply ? "" : "Dry run, nothing written. ");
        counts.forEach((k, v) -> sb.append(v).append(' ').append(k.toLowerCase(Locale.ROOT).replace('_', ' ')).append(", "));
        sb.append(failed).append(" failed");
        return sb.toString();
    }

    private static Object trimmed(JsonNode rec) {
        String s = rec.toString();
        return s.length() <= 4000 ? rec : GenericHttp.abbreviate(s, 4000);
    }

    private static String toJson(Object o) {
        try { return GenericConfig.JSON.writeValueAsString(o); } catch (Exception e) { throw new BadRequestException("config is not valid JSON"); }
    }

    private static String required(String v, String name) {
        if (v == null || v.isBlank()) throw new BadRequestException(name + " is required");
        return v.trim();
    }
}
