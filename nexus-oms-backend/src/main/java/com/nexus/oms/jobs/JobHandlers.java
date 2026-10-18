package com.nexus.oms.jobs;

import com.nexus.oms.dto.SyncResult;
import com.nexus.oms.entity.NxIntegrationStore;
import com.nexus.oms.entity.NxInventory;
import com.nexus.oms.exception.BadRequestException;
import com.nexus.oms.repository.InventoryRepository;
import com.nexus.oms.service.IntegrationStoreService;
import com.nexus.oms.service.PickFailureService;
import com.nexus.oms.service.netsuite.NetSuiteInventoryBaselineService;
import com.nexus.oms.service.shopify.ShopifyInventoryBaselineService;
import com.nexus.oms.service.shopify.ShopifyOrderImportService;
import com.nexus.oms.service.shopify.ShopifyProductSyncService;
import org.springframework.stereotype.Component;

import java.util.*;

/** The job types a customer can schedule today. Each is a thin wrapper over a service that already exists and is tested. */
final class JobHandlers {

    private JobHandlers() {}

    private static final JobHandler.Param STORE = new JobHandler.Param("storeId", "Store connection", "STORE", true,
            "The integration store this job reads from.");
    private static final JobHandler.Param APPLY = new JobHandler.Param("apply", "Write the changes", "BOOLEAN", false,
            "Off = a dry run that only reports. On = create the missing stock rows.");
    private static final JobHandler.Param OVERWRITE = new JobHandler.Param("overwrite", "Overwrite existing stock", "BOOLEAN", false,
            "Only with 'Write the changes'. Off = stock Nexus already holds is kept and differences are reported.");

    /** The store must exist and belong to the tenant the job runs for. */
    static NxIntegrationStore ownStore(IntegrationStoreService stores, JobHandler.Context ctx) {
        UUID id = ctx.uuid("storeId");
        if (id == null) throw new BadRequestException("This job needs a store connection (storeId).");
        NxIntegrationStore store = stores.getStore(id);
        if (!ctx.tenantId().equals(store.getTenantId())) throw new BadRequestException("Unknown store connection.");
        return store;
    }

    @Component
    static class ShopifyOrderImport implements JobHandler {
        private final IntegrationStoreService stores;
        private final ShopifyOrderImportService service;

        ShopifyOrderImport(IntegrationStoreService stores, ShopifyOrderImportService service) {
            this.stores = stores;
            this.service = service;
        }

        public String type() { return "SHOPIFY_ORDER_IMPORT"; }
        public String label() { return "Shopify: import orders"; }
        public String description() { return "Reads new and changed orders from Shopify into Nexus. Read-only towards Shopify."; }
        public List<Param> params() { return List.of(STORE); }

        public Outcome run(Context ctx) {
            NxIntegrationStore store = ownStore(stores, ctx);
            ctx.log("Importing orders from " + store.getStoreName());
            SyncResult r = service.importOrders(store.getId());
            Outcome o = Outcome.of(r.getItemsProcessed(), r.getItemsSucceeded(), r.getItemsFailed(), r.getMessage());
            if (r.getItemsFailed() > 0) {
                o.issue("(see Sync Monitor)", r.getItemsFailed() + " order(s) could not be imported. The sync log has each one's reason.", String.valueOf(r.getSyncLogId()));
            }
            return o;
        }
    }

    @Component
    static class ShopifyProductSync implements JobHandler {
        private final IntegrationStoreService stores;
        private final ShopifyProductSyncService service;

        ShopifyProductSync(IntegrationStoreService stores, ShopifyProductSyncService service) {
            this.stores = stores;
            this.service = service;
        }

        public String type() { return "SHOPIFY_PRODUCT_SYNC"; }
        public String label() { return "Shopify: sync products"; }
        public String description() { return "Reads the product catalogue and variants from Shopify. Read-only towards Shopify."; }
        public List<Param> params() { return List.of(STORE); }

        public Outcome run(Context ctx) {
            NxIntegrationStore store = ownStore(stores, ctx);
            ctx.log("Syncing products from " + store.getStoreName());
            SyncResult r = service.syncProducts(store.getId());
            Outcome o = Outcome.of(r.getItemsProcessed(), r.getItemsSucceeded(), r.getItemsFailed(), r.getMessage());
            if (r.getItemsFailed() > 0) {
                o.issue("(see Sync Monitor)", r.getItemsFailed() + " product(s) could not be saved. The sync log names them.", String.valueOf(r.getSyncLogId()));
            }
            return o;
        }
    }

    @Component
    static class ShopifyStockBaseline implements JobHandler {
        private final IntegrationStoreService stores;
        private final ShopifyInventoryBaselineService service;

        ShopifyStockBaseline(IntegrationStoreService stores, ShopifyInventoryBaselineService service) {
            this.stores = stores;
            this.service = service;
        }

        public String type() { return "SHOPIFY_STOCK_BASELINE"; }
        public String label() { return "Shopify: bring in current stock"; }
        public String description() { return "Reads Shopify's physical on-hand per mapped location and creates opening balances in Nexus. Dry run unless you switch writing on. Never writes to Shopify."; }
        public List<Param> params() { return List.of(STORE, APPLY, OVERWRITE); }

        public Outcome run(Context ctx) {
            NxIntegrationStore store = ownStore(stores, ctx);
            ctx.log((ctx.flag("apply") ? "Applying" : "Dry run of") + " the stock baseline for " + store.getStoreName());
            var r = service.run(store.getId(), ctx.flag("apply"), ctx.flag("overwrite"));
            Outcome o = Outcome.of(r.checked(), r.created() + r.updated() + r.unchanged(), 0, r.message());
            for (var d : r.differences()) {
                o.issue(d.sku(), "Nexus holds " + d.nexusOnHand() + " but Shopify holds " + d.shopifyOnHand(), d.nodeId().toString());
            }
            if ("BLOCKED".equals(r.status())) throw new BadRequestException(r.message());
            return o;
        }
    }

    @Component
    static class NetSuiteStockBaseline implements JobHandler {
        private final IntegrationStoreService stores;
        private final NetSuiteInventoryBaselineService service;

        NetSuiteStockBaseline(IntegrationStoreService stores, NetSuiteInventoryBaselineService service) {
            this.stores = stores;
            this.service = service;
        }

        public String type() { return "NETSUITE_STOCK_BASELINE"; }
        public String label() { return "NetSuite: bring in stock counts"; }
        public String description() { return "Reads inventory balances from NetSuite and creates opening balances in Nexus. Dry run unless you switch writing on. Not yet run against a real NetSuite account."; }
        public List<Param> params() { return List.of(STORE, APPLY, OVERWRITE); }

        public Outcome run(Context ctx) {
            NxIntegrationStore store = ownStore(stores, ctx);
            ctx.log((ctx.flag("apply") ? "Applying" : "Dry run of") + " the NetSuite stock import for " + store.getStoreName());
            var r = service.run(store.getId(), ctx.flag("apply"), ctx.flag("overwrite"));
            Outcome o = Outcome.of(r.rows(), r.created() + r.updated() + r.unchanged(), r.unknownSku(), r.message());
            for (String sku : r.unknownSkus()) o.issue(sku, "NetSuite has this SKU but Nexus has no such product", null);
            for (var d : r.differences()) {
                o.issue(d.sku(), "Nexus holds " + d.nexusOnHand() + " but NetSuite holds " + d.netsuiteOnHand(), d.nodeId().toString());
            }
            if ("BLOCKED".equals(r.status())) throw new BadRequestException(r.message());
            return o;
        }
    }

    @Component
    static class StockExport implements JobHandler {
        private final InventoryRepository inventory;

        StockExport(InventoryRepository inventory) {
            this.inventory = inventory;
        }

        public String type() { return "STOCK_EXPORT"; }
        public String label() { return "Export stock (CSV)"; }
        public String description() { return "A CSV of Nexus stock per SKU and location: on hand, allocated, reserved, held, and what can be sold."; }
        public List<Param> params() { return List.of(new Param("nodeId", "Only this location (optional)", "TEXT", false, "A Nexus location id. Empty = all locations.")); }

        public Outcome run(Context ctx) {
            UUID only = ctx.uuid("nodeId");
            StringBuilder sb = new StringBuilder();
            JobCsv.row(sb, "sku", "location_id", "on_hand", "allocated", "reserved", "held", "safety_stock", "sellable");
            int n = 0;
            for (NxInventory i : inventory.findByTenantId(ctx.tenantId())) {
                if (only != null && !only.equals(i.getNodeId())) continue;
                int onHand = nz(i.getQuantityOnHand()), alloc = nz(i.getQuantityAllocated()), res = nz(i.getQuantityReserved());
                int held = nz(i.getQuantityDamaged()), safety = nz(i.getSafetyStock());
                JobCsv.row(sb, i.getSku(), i.getNodeId(), onHand, alloc, res, held, safety, Math.max(0, onHand - alloc - res - held - safety));
                n++;
            }
            ctx.log("Wrote " + n + " stock rows");
            return Outcome.of(n, n, 0, n + " stock row(s) exported").output("stock-" + java.time.LocalDate.now() + ".csv", sb.toString());
        }

        private static int nz(Integer v) { return v == null ? 0 : v; }
    }

    @Component
    static class PickFailureExport implements JobHandler {
        private final PickFailureService service;

        PickFailureExport(PickFailureService service) {
            this.service = service;
        }

        public String type() { return "PICK_FAILURE_EXPORT"; }
        public String label() { return "Export store pick-failure rates (CSV)"; }
        public String description() { return "How often each store failed to pick what it was allocated over the last 90 days, as routing uses it."; }
        public List<Param> params() { return List.of(); }

        public Outcome run(Context ctx) {
            StringBuilder sb = new StringBuilder();
            JobCsv.row(sb, "location_id", "allocated_units", "rejected_units", "smoothed_failure_rate");
            var rates = new ArrayList<>(service.ratesFor(ctx.tenantId()).values());
            rates.sort(Comparator.comparingDouble(PickFailureService.NodeRate::rate).reversed());
            for (var r : rates) JobCsv.row(sb, r.nodeId(), r.allocatedUnits(), r.rejectedUnits(), String.format(Locale.ROOT, "%.4f", r.rate()));
            ctx.log("Wrote " + rates.size() + " locations");
            return Outcome.of(rates.size(), rates.size(), 0, rates.size() + " location(s) exported")
                    .output("pick-failure-" + java.time.LocalDate.now() + ".csv", sb.toString());
        }
    }
}
