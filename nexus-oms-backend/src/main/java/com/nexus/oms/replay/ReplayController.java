package com.nexus.oms.replay;

import com.nexus.oms.dto.ApiResponse;
import com.nexus.oms.exception.BadRequestException;
import com.nexus.oms.replay.ReplayEngine.Costs;
import com.nexus.oms.replay.ReplayEngine.Node;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import java.nio.charset.StandardCharsets;
import java.util.*;

/**
 * Order replay: three CSV files in, a comparison of routing rules out. Nothing is stored and nothing in Nexus
 * changes. It sits under /order-routing so it carries the same permission and edition as routing.
 */
@RestController
@RequestMapping("/order-routing/replay")
public class ReplayController {

    @PostMapping(consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    public ResponseEntity<ApiResponse<Map<String, Object>>> replay(
            @RequestParam("orders") MultipartFile orders,
            @RequestParam("stock") MultipartFile stock,
            @RequestParam(value = "nodes", required = false) MultipartFile nodes,
            @RequestParam(defaultValue = "6.5") double costPerShipment,
            @RequestParam(defaultValue = "0") double costPerKm,
            @RequestParam(required = false) Double costPerFailedUnit) {
        return ResponseEntity.ok(ApiResponse.success(
                run(text(orders), text(stock), nodes == null || nodes.isEmpty() ? null : text(nodes),
                        new Costs(nonNegative(costPerShipment), nonNegative(costPerKm),
                                nonNegative(costPerFailedUnit == null ? costPerShipment : costPerFailedUnit)))));
    }

    /** Invented example files, so a first look takes one click. */
    @GetMapping("/sample")
    public ResponseEntity<ApiResponse<Map<String, String>>> sample() {
        ReplaySample.Files f = ReplaySample.build();
        return ResponseEntity.ok(ApiResponse.success(Map.of("orders", f.orders(), "stock", f.stock(), "nodes", f.nodes())));
    }

    static Map<String, Object> run(String ordersCsv, String stockCsv, String nodesCsv, Costs costs) {
        var orders = ReplayCsv.orders(ordersCsv);
        var stock = ReplayCsv.stock(stockCsv);
        List<Node> nodes = nodesCsv == null ? new ArrayList<>() : new ArrayList<>(ReplayCsv.nodes(nodesCsv));
        Set<String> known = new HashSet<>();
        nodes.forEach(n -> known.add(n.id()));
        // Any location that holds stock but is not in the locations file still takes part, with defaults.
        TreeSet<String> fromStock = new TreeSet<>();
        stock.values().forEach(m -> fromStock.addAll(m.keySet()));
        for (String id : fromStock) if (known.add(id)) nodes.add(new Node(id, id, null, null, null));
        var result = ReplayEngine.run(nodes, stock, orders, costs);
        Map<String, Object> out = new LinkedHashMap<>();
        out.put("orders", orders.size());
        out.put("orderLines", orders.stream().mapToInt(o -> o.lines().size()).sum());
        out.put("skus", stock.size());
        out.put("locations", nodes.size());
        out.put("result", result);
        return out;
    }

    private static String text(MultipartFile f) {
        try {
            return new String(f.getBytes(), StandardCharsets.UTF_8);
        } catch (Exception e) {
            throw new BadRequestException("Could not read " + f.getOriginalFilename());
        }
    }

    private static double nonNegative(double v) {
        if (v < 0 || Double.isNaN(v) || Double.isInfinite(v)) throw new BadRequestException("Costs must be zero or more.");
        return v;
    }
}
