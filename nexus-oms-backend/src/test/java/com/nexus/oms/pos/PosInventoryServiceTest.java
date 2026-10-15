package com.nexus.oms.pos;

import com.nexus.oms.entity.NxInventory;
import com.nexus.oms.repository.InventoryRepository;
import com.nexus.oms.security.TenantContext;
import com.nexus.oms.service.InventoryService;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.time.LocalDateTime;
import java.util.*;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;

class PosInventoryServiceTest {

    private final PosSourceRepository sources = mock(PosSourceRepository.class);
    private final PosSnapshotRepository snapshots = mock(PosSnapshotRepository.class);
    private final InventoryRepository inventory = mock(InventoryRepository.class);
    private final InventoryService inventoryService = mock(InventoryService.class);
    private final PosInventoryService service = new PosInventoryService(sources, snapshots, inventory, inventoryService);

    private final UUID tenant = UUID.randomUUID();
    private final UUID node = UUID.randomUUID();
    private NxPosSource source;
    private String secret;
    private final Map<String, NxPosInventorySnapshot> store = new HashMap<>();

    @BeforeEach
    void setUp() {
        TenantContext.setCurrentTenantId(tenant);
        when(sources.save(any())).thenAnswer(i -> i.getArgument(0));
        var created = service.createSource(node, "Main St till", "square", null);
        source = created.source();
        secret = created.secret();
        when(sources.findById(source.getId())).thenReturn(Optional.of(source));
        when(sources.findByNodeId(node)).thenReturn(List.of(source));
        when(snapshots.findBySourceIdAndSku(eq(source.getId()), any())).thenAnswer(i -> Optional.ofNullable(store.get((String) i.getArgument(1))));
        when(snapshots.save(any())).thenAnswer(i -> { var s = (NxPosInventorySnapshot) i.getArgument(0); store.put(s.getSku(), s); return s; });
        when(snapshots.findByNodeId(node)).thenAnswer(i -> new ArrayList<>(store.values()));
    }

    @AfterEach
    void tearDown() { TenantContext.clear(); }

    private NxInventory stock(String sku, int onHand, int allocated) {
        NxInventory n = new NxInventory();
        n.setSku(sku);
        n.setNodeId(node);
        n.setQuantityOnHand(onHand);
        n.setQuantityAllocated(allocated);
        return n;
    }

    private void nexusHolds(NxInventory... rows) {
        when(inventory.findByTenantIdAndNodeId(tenant, node)).thenReturn(List.of(rows));
    }

    private Map<String, PosInventoryService.Row> byStatus() {
        Map<String, PosInventoryService.Row> m = new HashMap<>();
        service.aggregate(node).forEach(r -> m.put(r.sku(), r));
        return m;
    }

    @Test
    void theSecretIsShownOnce_andOnlyItsHashIsKept() {
        assertTrue(secret.startsWith("nxpos_"));
        assertNotEquals(secret, source.getKeyHash());
        assertEquals(64, source.getKeyHash().length());
        assertEquals("SQUARE", source.getVendor());
    }

    @Test
    void wrongOrMissingKey_isRefused_andStoresNothing() {
        var items = List.of(new PosInventoryService.Item("A", 3));
        assertEquals(401, assertThrows(PosInventoryService.Rejected.class, () -> service.ingest(source.getId(), "nope", null, items)).httpStatus());
        assertEquals(401, assertThrows(PosInventoryService.Rejected.class, () -> service.ingest(source.getId(), null, null, items)).httpStatus());
        assertTrue(store.isEmpty());
    }

    @Test
    void aDisabledSource_cannotPush() {
        source.setEnabled(false);
        assertEquals(403, assertThrows(PosInventoryService.Rejected.class,
                () -> service.ingest(source.getId(), secret, null, List.of(new PosInventoryService.Item("A", 1)))).httpStatus());
    }

    @Test
    void invalidItemsAreReported_andValidOnesAccepted() {
        var r = service.ingest(source.getId(), secret, null, Arrays.asList(
                new PosInventoryService.Item("A", 4), new PosInventoryService.Item("", 1),
                new PosInventoryService.Item("B", -2), new PosInventoryService.Item("C", null), null));
        assertEquals(1, r.get("accepted"));
        assertEquals(4, ((List<?>) r.get("rejectedSkus")).size());
        assertEquals(4, store.get("A").getQuantity());
    }

    @Test
    void aLateReport_doesNotOverwriteANewerOne() {
        LocalDateTime newer = LocalDateTime.now().minusMinutes(1);
        service.ingest(source.getId(), secret, newer, List.of(new PosInventoryService.Item("A", 9)));
        var r = service.ingest(source.getId(), secret, newer.minusMinutes(30), List.of(new PosInventoryService.Item("A", 1)));
        assertEquals(1, r.get("ignoredAsOlderThanWhatWeHold"));
        assertEquals(9, store.get("A").getQuantity());
    }

    @Test
    void aFutureTimestamp_isRejected() {
        assertEquals(400, assertThrows(PosInventoryService.Rejected.class, () -> service.ingest(source.getId(), secret,
                LocalDateTime.now().plusHours(2), List.of(new PosInventoryService.Item("A", 1)))).httpStatus());
    }

    @Test
    void aggregateClassifiesEverySku() {
        nexusHolds(stock("MATCHING", 5, 0), stock("OFF", 5, 0), stock("QUIET", 7, 0), stock("OLD", 5, 0));
        service.ingest(source.getId(), secret, null, List.of(
                new PosInventoryService.Item("MATCHING", 5), new PosInventoryService.Item("OFF", 8),
                new PosInventoryService.Item("UNKNOWN", 3), new PosInventoryService.Item("OLD", 4)));
        store.get("OLD").setReportedAt(LocalDateTime.now().minusHours(5));    // source goes stale after 2 h

        var rows = byStatus();
        assertEquals("MATCH", rows.get("MATCHING").status());
        assertEquals("DRIFT", rows.get("OFF").status());
        assertEquals(3, rows.get("OFF").delta());
        assertEquals("NO_POS_DATA", rows.get("QUIET").status());
        assertEquals("NO_NEXUS_ROW", rows.get("UNKNOWN").status());
        assertEquals("STALE", rows.get("OLD").status());
    }

    @Test
    void reconcileDefaultsToADryRun_andChangesNothing() {
        nexusHolds(stock("OFF", 5, 0));
        service.ingest(source.getId(), secret, null, List.of(new PosInventoryService.Item("OFF", 8)));

        var res = service.reconcile(node, null, false);

        assertFalse(res.applied());
        assertEquals("PLANNED", res.lines().get(0).outcome());
        verifyNoInteractions(inventoryService);
    }

    @Test
    void reconcileApply_adjustsByTheDifference_onlyForFreshDrift() {
        nexusHolds(stock("OFF", 5, 0), stock("OLD", 5, 0), stock("MATCHING", 2, 0));
        service.ingest(source.getId(), secret, null, List.of(
                new PosInventoryService.Item("OFF", 3), new PosInventoryService.Item("OLD", 9), new PosInventoryService.Item("MATCHING", 2)));
        store.get("OLD").setReportedAt(LocalDateTime.now().minusHours(5));

        var res = service.reconcile(node, null, true);

        verify(inventoryService).adjustInventoryBySku(tenant, "OFF", node, -2, "POS reconcile");
        verifyNoMoreInteractions(inventoryService);
        assertTrue(res.lines().stream().anyMatch(l -> l.sku().equals("OFF") && l.outcome().equals("APPLIED")));
        assertTrue(res.lines().stream().anyMatch(l -> l.sku().equals("OLD") && l.outcome().equals("SKIPPED")));
    }

    @Test
    void reconcileRefusesToDropBelowWhatIsAllocated() {
        nexusHolds(stock("OFF", 10, 6));
        service.ingest(source.getId(), secret, null, List.of(new PosInventoryService.Item("OFF", 4)));

        var res = service.reconcile(node, null, true);

        assertEquals("BLOCKED", res.lines().get(0).outcome());
        verifyNoInteractions(inventoryService);
    }

    @Test
    void reconcileCanBeLimitedToChosenSkus() {
        nexusHolds(stock("A", 5, 0), stock("B", 5, 0));
        service.ingest(source.getId(), secret, null, List.of(new PosInventoryService.Item("A", 6), new PosInventoryService.Item("B", 6)));

        service.reconcile(node, List.of("A"), true);

        verify(inventoryService).adjustInventoryBySku(tenant, "A", node, 1, "POS reconcile");
        verifyNoMoreInteractions(inventoryService);
    }
}
