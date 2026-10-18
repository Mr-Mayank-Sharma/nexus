package com.nexus.oms.replay;

import java.util.*;

/** A small, fixed example so a first-time user can see a result in one click. Invented data, deterministic. */
final class ReplaySample {

    private ReplaySample() {}

    record Files(String orders, String stock, String nodes) {}

    static Files build() {
        Random rnd = new Random(42);
        String[][] nodes = {   // id, name, lat, lon, failure rate
                {"DC-EAST", "East distribution centre", "40.7", "-74.0", "0.02"},
                {"ST-NYC", "New York store", "40.75", "-73.99", "0.22"},
                {"ST-BOS", "Boston store", "42.36", "-71.06", "0.04"},
                {"ST-PHL", "Philadelphia store", "39.95", "-75.17", "0.06"}};
        String[] skus = new String[24];
        for (int i = 0; i < skus.length; i++) skus[i] = String.format("SKU-%03d", i + 1);

        StringBuilder nodeCsv = new StringBuilder("node_id,name,lat,lon,failure_rate\n");
        for (String[] n : nodes) nodeCsv.append(String.join(",", n)).append('\n');

        StringBuilder stock = new StringBuilder("sku,node_id,qty\n");
        for (String sku : skus) {
            for (String[] n : nodes) {
                boolean dc = n[0].startsWith("DC");
                int qty = dc ? 30 + rnd.nextInt(30) : (rnd.nextInt(10) < 6 ? 1 + rnd.nextInt(6) : 0);   // plenty overall, thin in stores
                if (qty > 0) stock.append(sku).append(',').append(n[0]).append(',').append(qty).append('\n');
            }
        }
        StringBuilder orders = new StringBuilder("order_id,sku,qty,lat,lon\n");
        for (int o = 1; o <= 220; o++) {
            int lines = 1 + rnd.nextInt(3);
            double lat = 39.5 + rnd.nextDouble() * 3.5, lon = -75.5 + rnd.nextDouble() * 4.5;
            Set<String> used = new HashSet<>();
            for (int l = 0; l < lines; l++) {
                String sku = skus[rnd.nextInt(skus.length)];
                if (!used.add(sku)) continue;
                orders.append(String.format(Locale.ROOT, "ORD-%04d,%s,%d,%.3f,%.3f%n", o, sku, 1 + rnd.nextInt(2), lat, lon));
            }
        }
        return new Files(orders.toString(), stock.toString(), nodeCsv.toString());
    }
}
