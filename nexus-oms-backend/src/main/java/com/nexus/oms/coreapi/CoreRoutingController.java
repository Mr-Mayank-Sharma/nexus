package com.nexus.oms.coreapi;

import com.nexus.core.model.routing.OrderFacilityChange;
import com.nexus.core.service.RoutingService;
import com.nexus.core.service.RoutingSetupService;
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
 * <p>
 * Setting up is {@link RoutingSetupService}; running and moving is {@link RoutingService}. Each service method is its
 * own transaction.
 */
@RestController
@RequestMapping("/core/routing")
@Tag(name = "Core routing", description = "Routing groups, rules, runs, simulations and reallocation on the core model")
public class CoreRoutingController {

    private final RoutingSetupService setupService;
    private final RoutingService routingService;

    public CoreRoutingController(RoutingSetupService setupService, RoutingService routingService) {
        this.setupService = setupService;
        this.routingService = routingService;
    }

    public record NewGroup(String productStoreId, String name, Long scheduleMinutes) {
    }

    public record Condition(boolean sort, String field, String operator, String value) {
        RoutingSetupService.Cond toCond() {
            return new RoutingSetupService.Cond(sort, field, operator, value);
        }
    }

    public record NewRouting(String name, long sequenceNum, List<Condition> conditions) {
    }

    public record NewRule(String name, long sequenceNum, boolean split, List<Condition> conditions, Map<String, String> actions) {
    }

    public record Move(String facilityId, String reason) {
    }

    // ── setting up ──

    @Operation(summary = "Create a routing group for a store")
    @PostMapping("/groups")
    public ResponseEntity<ApiResponse<String>> group(@RequestBody NewGroup newGroup) {
        String groupId = setupService.createGroup(newGroup.productStoreId(), newGroup.name(), newGroup.scheduleMinutes());
        return ResponseEntity.ok(ApiResponse.success(groupId, "Routing group created"));
    }

    @Operation(summary = "Add a routing (which orders, in what order) to a group; it starts as a draft")
    @PostMapping("/groups/{groupId}/routings")
    public ResponseEntity<ApiResponse<String>> routing(@PathVariable String groupId, @RequestBody NewRouting newRouting) {
        String routingId = setupService.addRouting(groupId, newRouting.name(), newRouting.sequenceNum(), toConds(newRouting.conditions()));
        return ResponseEntity.ok(ApiResponse.success(routingId, "Routing added"));
    }

    @Operation(summary = "Add a rule (where to look, how to sort, split or not, what to do when nothing fits) to a routing")
    @PostMapping("/routings/{routingId}/rules")
    public ResponseEntity<ApiResponse<String>> rule(@PathVariable String routingId, @RequestBody NewRule newRule) {
        String ruleId = setupService.addRule(routingId, newRule.name(), newRule.sequenceNum(), newRule.split(), toConds(newRule.conditions()),
                newRule.actions());
        return ResponseEntity.ok(ApiResponse.success(ruleId, "Rule added"));
    }

    @Operation(summary = "Activate a routing and its draft rules")
    @PostMapping("/routings/{routingId}/activate")
    public ResponseEntity<ApiResponse<Void>> activate(@PathVariable String routingId) {
        setupService.activate(routingId);
        return ResponseEntity.ok(ApiResponse.success(null, "Routing active"));
    }

    // ── running ──

    @Operation(summary = "Run a routing group now")
    @PostMapping("/groups/{groupId}/run")
    public ResponseEntity<ApiResponse<RoutingService.RunResult>> run(@PathVariable String groupId) {
        return ResponseEntity.ok(ApiResponse.success(routingService.run(groupId, currentUser()), "Routing run finished"));
    }

    @Operation(summary = "Simulate a routing group on real orders and stock; nothing changes")
    @PostMapping("/groups/{groupId}/simulate")
    public ResponseEntity<ApiResponse<RoutingService.RunResult>> simulate(@PathVariable String groupId) {
        return ResponseEntity.ok(ApiResponse.success(routingService.simulate(groupId, currentUser()), "Simulation finished; nothing changed"));
    }

    // ── moving orders ──

    @Operation(summary = "Move a ship group to another facility by hand")
    @PostMapping("/orders/{orderId}/ship-groups/{shipGroupSeqId}/reallocate")
    public ResponseEntity<ApiResponse<Void>> reallocate(@PathVariable String orderId, @PathVariable String shipGroupSeqId, @RequestBody Move move) {
        routingService.reallocate(orderId, shipGroupSeqId, move.facilityId(), move.reason(), currentUser());
        return ResponseEntity.ok(ApiResponse.success(null, "Moved to " + move.facilityId()));
    }

    @Operation(summary = "Take every open order off a facility and send it back to routing")
    @PostMapping("/facilities/{facilityId}/move-all-off")
    public ResponseEntity<ApiResponse<Integer>> moveAllOff(@PathVariable String facilityId, @RequestBody Move move) {
        int moved = routingService.moveAllOff(facilityId, move.reason(), currentUser());
        return ResponseEntity.ok(ApiResponse.success(moved, "Orders sent back to routing"));
    }

    public record Change(String shipGroupSeqId, String fromFacilityId, String facilityId, String reasonEnumId, String comments,
                         String routingRunId, String by, String at) {
    }

    @Operation(summary = "Every location change of an order, with the reason")
    @GetMapping("/orders/{orderId}/history")
    public ResponseEntity<ApiResponse<List<Change>>> history(@PathVariable String orderId) {
        List<Change> changes = routingService.history(orderId).stream().map(CoreRoutingController::toChange).toList();
        return ResponseEntity.ok(ApiResponse.success(changes, "Routing history"));
    }

    private static Change toChange(OrderFacilityChange change) {
        return new Change(change.getShipGroupSeqId(), change.getFromFacilityId(), change.getFacilityId(), change.getChangeReasonEnumId(),
                change.getComments(), change.getRoutingRunId(), change.getChangeUserLogin(), String.valueOf(change.getChangeDatetime()));
    }

    private static List<RoutingSetupService.Cond> toConds(List<Condition> conditions) {
        return conditions == null ? List.of() : conditions.stream().map(Condition::toCond).toList();
    }

    /** The signed-in user, or null outside a request. */
    private static String currentUser() {
        try {
            return TenantContext.getCurrentUsername();
        } catch (RuntimeException noUser) {
            return null;
        }
    }
}
