package com.nexus.oms.pos;

import com.nexus.oms.entity.NxInventory;
import com.nexus.oms.exception.BadRequestException;
import com.nexus.oms.exception.ResourceNotFoundException;
import com.nexus.oms.repository.InventoryRepository;
import com.nexus.oms.security.TenantContext;
import com.nexus.oms.service.InventoryService;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.SecureRandom;
import java.time.Duration;
import java.time.LocalDateTime;
import java.util.*;

/**
 * Point-of-sale stock, kept apart from Nexus stock until an operator says otherwise.
 * The store's POS pushes what it counts; this compares it with Nexus's on-hand for the same store node
 * and reports each SKU as MATCH, DRIFT, STALE (the POS has gone quiet), NO_POS_DATA or NO_NEXUS_ROW.
 * Nothing changes Nexus stock except {@link #reconcile} with apply=true, and that writes ledger lines.
 */
@Service
public class PosInventoryService {

    public static final int MAX_ITEMS_PER_PUSH = 5000;

    public static class Rejected extends RuntimeException {
        private final int httpStatus;
        public Rejected(int httpStatus, String message) { super(message); this.httpStatus = httpStatus; }
        public int httpStatus() { return httpStatus; }
    }

    public record Item(String sku, Integer quantity) {}
    public record Created(NxPosSource source, String secret) {}

    public record Row(String sku, UUID nodeId, Integer nexusOnHand, Integer nexusAllocated, Integer posQuantity,
                      Integer delta, LocalDateTime posReportedAt, String status) {}

    public record Line(String sku, String outcome, Integer from, Integer to, String reason) {}
    public record ReconcileResult(boolean applied, List<Line> lines) {}

    private static final SecureRandom RANDOM = new SecureRandom();

    private final PosSourceRepository sources;
    private final PosSnapshotRepository snapshots;
    private final InventoryRepository inventory;
    private final InventoryService inventoryService;

    public PosInventoryService(PosSourceRepository sources, PosSnapshotRepository snapshots,
                               InventoryRepository inventory, InventoryService inventoryService) {
        this.sources = sources;
        this.snapshots = snapshots;
        this.inventory = inventory;
        this.inventoryService = inventoryService;
    }

    // ---- sources ----------------------------------------------------------------------------------

    @Transactional
    public Created createSource(UUID nodeId, String name, String vendor, Integer staleAfterMinutes) {
        if (name == null || name.isBlank()) throw new BadRequestException("name is required");
        if (staleAfterMinutes != null && staleAfterMinutes <= 0) throw new BadRequestException("staleAfterMinutes must be positive");
        NxPosSource s = new NxPosSource();
        s.setId(UUID.randomUUID());
        s.setTenantId(TenantContext.getCurrentTenantId());
        s.setNodeId(nodeId);
        s.setName(name.trim());
        if (vendor != null && !vendor.isBlank()) s.setVendor(vendor.trim().toUpperCase());
        if (staleAfterMinutes != null) s.setStaleAfterMinutes(staleAfterMinutes);
        String secret = newSecret();
        s.setKeyHash(hash(secret));
        return new Created(sources.save(s), secret);
    }

    @Transactional
    public Created rotateKey(UUID sourceId) {
        NxPosSource s = sources.findById(sourceId).orElseThrow(() -> new ResourceNotFoundException("PosSource", sourceId));
        String secret = newSecret();
        s.setKeyHash(hash(secret));
        return new Created(sources.save(s), secret);
    }

    public List<NxPosSource> listSources() {
        return sources.findByTenantIdOrderByCreatedAtDesc(TenantContext.getCurrentTenantId());
    }

    // ---- ingest (called by the POS, no user) ------------------------------------------------------

    @Transactional
    public Map<String, Object> ingest(UUID sourceId, String presentedKey, LocalDateTime reportedAt, List<Item> items) {
        NxPosSource source = sources.findById(sourceId).orElseThrow(() -> new Rejected(404, "Unknown POS source"));
        boolean ok = presentedKey != null && MessageDigest.isEqual(
                source.getKeyHash().getBytes(StandardCharsets.UTF_8), hash(presentedKey).getBytes(StandardCharsets.UTF_8));
        if (!ok) throw new Rejected(401, "Invalid POS key");
        if (!Boolean.TRUE.equals(source.getEnabled())) throw new Rejected(403, "This POS source is disabled");
        if (items == null || items.isEmpty()) throw new Rejected(400, "items is required");
        if (items.size() > MAX_ITEMS_PER_PUSH) throw new Rejected(400, "At most " + MAX_ITEMS_PER_PUSH + " items per push");

        LocalDateTime now = LocalDateTime.now();
        LocalDateTime stamp = reportedAt == null ? now : reportedAt;
        if (stamp.isAfter(now.plusMinutes(5))) throw new Rejected(400, "reportedAt is in the future");
        int accepted = 0, ignoredOld = 0;
        List<String> rejected = new ArrayList<>();
        for (Item it : items) {
            if (it == null || it.sku() == null || it.sku().isBlank() || it.sku().length() > 128
                    || it.quantity() == null || it.quantity() < 0) {
                rejected.add(it == null || it.sku() == null ? "(missing sku)" : it.sku());
                continue;
            }
            String sku = it.sku().trim();
            NxPosInventorySnapshot snap = snapshots.findBySourceIdAndSku(sourceId, sku).orElseGet(() -> {
                NxPosInventorySnapshot n = new NxPosInventorySnapshot();
                n.setId(UUID.randomUUID());
                n.setTenantId(source.getTenantId());
                n.setSourceId(sourceId);
                n.setNodeId(source.getNodeId());
                n.setSku(sku);
                n.setReportedAt(LocalDateTime.MIN.plusYears(1));
                return n;
            });
            if (!stamp.isAfter(snap.getReportedAt()) && snap.getQuantity() != null) {   // a late, out-of-order report
                ignoredOld++;
                continue;
            }
            snap.setQuantity(it.quantity());
            snap.setReportedAt(stamp);
            snap.setReceivedAt(now);
            snapshots.save(snap);
            accepted++;
        }
        source.setLastSnapshotAt(now);
        sources.save(source);
        return Map.of("accepted", accepted, "ignoredAsOlderThanWhatWeHold", ignoredOld, "rejectedSkus", rejected);
    }

    // ---- aggregation ------------------------------------------------------------------------------

    public List<Row> aggregate(UUID nodeId) {
        UUID tenantId = TenantContext.getCurrentTenantId();
        Map<String, NxInventory> nexus = new TreeMap<>();
        for (NxInventory i : inventory.findByTenantIdAndNodeId(tenantId, nodeId)) nexus.put(i.getSku(), i);
        Map<UUID, NxPosSource> byId = new HashMap<>();
        sources.findByNodeId(nodeId).forEach(s -> byId.put(s.getId(), s));
        Map<String, NxPosInventorySnapshot> pos = new TreeMap<>();
        for (NxPosInventorySnapshot s : snapshots.findByNodeId(nodeId)) {
            pos.merge(s.getSku(), s, (a, b) -> b.getReportedAt().isAfter(a.getReportedAt()) ? b : a);   // newest source wins
        }

        LocalDateTime now = LocalDateTime.now();
        TreeSet<String> skus = new TreeSet<>(nexus.keySet());
        skus.addAll(pos.keySet());
        List<Row> rows = new ArrayList<>();
        for (String sku : skus) {
            NxInventory n = nexus.get(sku);
            NxPosInventorySnapshot p = pos.get(sku);
            Integer onHand = n == null ? null : nz(n.getQuantityOnHand());
            Integer allocated = n == null ? null : nz(n.getQuantityAllocated());
            Integer posQty = p == null ? null : p.getQuantity();
            String status;
            Integer delta = null;
            if (p == null) status = "NO_POS_DATA";
            else if (n == null) status = "NO_NEXUS_ROW";
            else {
                delta = posQty - onHand;
                NxPosSource src = byId.get(p.getSourceId());
                long staleMin = src == null || src.getStaleAfterMinutes() == null ? 120 : src.getStaleAfterMinutes();
                if (Duration.between(p.getReportedAt(), now).toMinutes() > staleMin) status = "STALE";
                else status = delta == 0 ? "MATCH" : "DRIFT";
            }
            rows.add(new Row(sku, nodeId, onHand, allocated, posQty, delta, p == null ? null : p.getReportedAt(), status));
        }
        return rows;
    }

    // ---- reconcile --------------------------------------------------------------------------------

    /**
     * Sets Nexus on-hand at the store node to the POS count, for fresh DRIFT rows only. Dry run unless apply is true.
     * Refuses a correction that would leave on-hand below what is already allocated to orders.
     */
    @Transactional
    public ReconcileResult reconcile(UUID nodeId, Collection<String> onlySkus, boolean apply) {
        UUID tenantId = TenantContext.getCurrentTenantId();
        Set<String> filter = onlySkus == null || onlySkus.isEmpty() ? null : new HashSet<>(onlySkus);
        List<Line> lines = new ArrayList<>();
        for (Row r : aggregate(nodeId)) {
            if (filter != null && !filter.contains(r.sku())) continue;
            switch (r.status()) {
                case "MATCH" -> { if (filter != null) lines.add(new Line(r.sku(), "UNCHANGED", r.nexusOnHand(), r.nexusOnHand(), "Already matches")); }
                case "STALE" -> lines.add(new Line(r.sku(), "SKIPPED", r.nexusOnHand(), r.posQuantity(), "POS report is stale; wait for a fresh one"));
                case "NO_POS_DATA" -> { if (filter != null) lines.add(new Line(r.sku(), "SKIPPED", r.nexusOnHand(), null, "POS has not reported this SKU")); }
                case "NO_NEXUS_ROW" -> lines.add(new Line(r.sku(), "SKIPPED", null, r.posQuantity(), "Nexus holds no stock row for this SKU at the store; add the product first"));
                default -> {
                    if (r.posQuantity() < nz(r.nexusAllocated())) {
                        lines.add(new Line(r.sku(), "BLOCKED", r.nexusOnHand(), r.posQuantity(),
                                "POS count " + r.posQuantity() + " is below the " + r.nexusAllocated() + " already allocated to orders"));
                    } else if (!apply) {
                        lines.add(new Line(r.sku(), "PLANNED", r.nexusOnHand(), r.posQuantity(), "Would adjust by " + r.delta()));
                    } else {
                        inventoryService.adjustInventoryBySku(tenantId, r.sku(), nodeId, r.delta(), "POS reconcile");
                        lines.add(new Line(r.sku(), "APPLIED", r.nexusOnHand(), r.posQuantity(), "Adjusted by " + r.delta()));
                    }
                }
            }
        }
        return new ReconcileResult(apply, lines);
    }

    // ---- helpers ----------------------------------------------------------------------------------

    private static int nz(Integer v) { return v == null ? 0 : v; }

    static String newSecret() {
        byte[] b = new byte[32];
        RANDOM.nextBytes(b);
        return "nxpos_" + Base64.getUrlEncoder().withoutPadding().encodeToString(b);
    }

    static String hash(String secret) {
        try {
            byte[] d = MessageDigest.getInstance("SHA-256").digest(secret.getBytes(StandardCharsets.UTF_8));
            StringBuilder sb = new StringBuilder();
            for (byte x : d) sb.append(String.format("%02x", x));
            return sb.toString();
        } catch (Exception e) {
            throw new IllegalStateException(e);
        }
    }
}
