package com.nexus.oms.controller;

import com.nexus.oms.dto.ApiResponse;
import com.nexus.oms.security.TenantContext;
import com.nexus.oms.service.twin.OrderNetworkTwinService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

/** Read model for the live order-network twin. The warehouse twin is part of Nexus WMS. */
@RestController
@RequestMapping("/twin")
@Tag(name = "Digital twin", description = "Aggregated live snapshot for the Order Flow twin")
public class TwinController {

    private final OrderNetworkTwinService orderNetworkTwinService;

    public TwinController(OrderNetworkTwinService orderNetworkTwinService) {
        this.orderNetworkTwinService = orderNetworkTwinService;
    }

    @Operation(summary = "Order network flows for a window: channels → nodes → regions, lifecycle, queue, exceptions")
    @GetMapping("/orders")
    public ResponseEntity<ApiResponse<OrderNetworkTwinService.Snapshot>> orderNetwork(
            @RequestParam(defaultValue = "24") int hours) {
        return ResponseEntity.ok(ApiResponse.success(orderNetworkTwinService.snapshot(TenantContext.getCurrentTenantId(), hours)));
    }
}
