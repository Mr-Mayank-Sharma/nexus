package com.nexus.oms.coreapi;

import com.nexus.core.model.routing.OrderFacilityChange;
import com.nexus.core.service.RoutingService;
import com.nexus.oms.dto.ApiResponse;
import com.nexus.oms.security.TenantContext;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.Map;

/**
 * Routing on the core model (doc 26, W9): set up routing groups, routings and rules, run or simulate a group, move
 * orders by hand or off a facility in bulk, and read why an order is where it is. Admin only (the {@code /core/}
 * prefix maps to the settings permission). The screens move onto it after the core is compared with the earlier model.
 */
@RestController
@RequestMapping("/core/routing")
@Tag(name = "Core routing", description = "Routing groups, rules, runs, simulations and reallocation on the core model")
public class CoreRoutingController {

    private final RoutingService routing;

    public CoreRoutingController(RoutingService routing) {
        this.routing = routing;
    }

    public record NewGroup(String productStoreId, String name, Long scheduleMinutes) {
    }

    public record Condition(boolean sort, String field, String operator, String value) {
        RoutingService.Cond toCond() {
            return new RoutingService.Cond(sort, field, operator, value);
        }
    }

    public record NewRouting(String name, long sequenceNum, List<Condition> conditions) {
    }

    public record NewRule(String name, long sequenceNum, boolean split, List<Condition> conditions, Map<String, String> actions) {
    }

    public record Move(String facilityId, String reason) {
    }

    @Operation(summary = "Create a routing group for a store")
    @PostMapping("/groups")
    public ResponseEntity<ApiResponse<String>> group(@RequestBody NewGroup g) {
        return ResponseEntity.ok(ApiResponse.success(routing.createGroup(g.productStoreId(), g.name(), g.scheduleMinutes()), "Routing group created"));
    }

    @Operation(summary = "Add a routing (which orders, in what order) to a group; it starts as a draft")
    @PostMapping("/groups/{groupId}/routings")
    public ResponseEntity<ApiResponse<String>> routing(@PathVariable String groupId, @RequestBody NewRouting r) {
        return ResponseEntity.ok(ApiResponse.success(routing.addRouting(groupId, r.name(), r.sequenceNum(), conds(r.conditions())), "Routing added"));
    }

    @Operation(summary = "Add a rule (where to look, how to sort, split or not, what to do when nothing fits) to a routing")
    @PostMapping("/routings/{routingId}/rules")
    public ResponseEntity<ApiResponse<String>> rule(@PathVariable String routingId, @RequestBody NewRule r) {
        return ResponseEntity.ok(ApiResponse.success(routing.addRule(routingId, r.name(), r.sequenceNum(), r.split(), conds(r.conditions()), r.actions()), "Rule added"));
    }

    @Operation(summary = "Activate a routing and its draft rules")
    @PostMapping("/routings/{routingId}/activate")
    public ResponseEntity<ApiResponse<Void>> activate(@PathVariable String routingId) {
        routing.activate(routingId);
        return ResponseEntity.ok(ApiResponse.success(null, "Routing active"));
    }

    @Operation(summary = "Run a routing group now")
    @PostMapping("/groups/{groupId}/run")
    public ResponseEntity<ApiResponse<RoutingService.RunResult>> run(@PathVariable String groupId) {
        return ResponseEntity.ok(ApiResponse.success(routing.run(groupId, user()), "Routing run finished"));
    }

    @Operation(summary = "Simulate a routing group on real orders and stock; nothing changes")
    @PostMapping("/groups/{groupId}/simulate")
    public ResponseEntity<ApiResponse<RoutingService.RunResult>> simulate(@PathVariable String groupId) {
        return ResponseEntity.ok(ApiResponse.success(routing.simulate(groupId, user()), "Simulation finished; nothing changed"));
    }

    @Operation(summary = "Move a ship group to another facility by hand")
    @PostMapping("/orders/{orderId}/ship-groups/{shipGroupSeqId}/reallocate")
    public ResponseEntity<ApiResponse<Void>> reallocate(@PathVariable String orderId, @PathVariable String shipGroupSeqId, @RequestBody Move m) {
        routing.reallocate(orderId, shipGroupSeqId, m.facilityId(), m.reason(), user());
        return ResponseEntity.ok(ApiResponse.success(null, "Moved to " + m.facilityId()));
    }

    @Operation(summary = "Take every open order off a facility and send it back to routing")
    @PostMapping("/facilities/{facilityId}/move-all-off")
    public ResponseEntity<ApiResponse<Integer>> moveAllOff(@PathVariable String facilityId, @RequestBody Move m) {
        return ResponseEntity.ok(ApiResponse.success(routing.moveAllOff(facilityId, m.reason(), user()), "Orders sent back to routing"));
    }

    public record Change(String shipGroupSeqId, String fromFacilityId, String facilityId, String reasonEnumId, String comments,
                         String routingRunId, String by, String at) {
    }

    @Operation(summary = "Every location change of an order, with the reason")
    @GetMapping("/orders/{orderId}/history")
    public ResponseEntity<ApiResponse<List<Change>>> history(@PathVariable String orderId) {
        List<Change> out = routing.history(orderId).stream().map((OrderFacilityChange c) -> new Change(c.getShipGroupSeqId(), c.getFromFacilityId(),
                c.getFacilityId(), c.getChangeReasonEnumId(), c.getComments(), c.getRoutingRunId(), c.getChangeUserLogin(),
                String.valueOf(c.getChangeDatetime()))).toList();
        return ResponseEntity.ok(ApiResponse.success(out, "Routing history"));
    }

    private static List<RoutingService.Cond> conds(List<Condition> c) {
        return c == null ? List.of() : c.stream().map(Condition::toCond).toList();
    }

    private static String user() {
        try {
            return TenantContext.getCurrentUsername();
        } catch (RuntimeException e) {
            return null;
        }
    }
}
