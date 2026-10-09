package com.nexus.oms.controller;

import com.nexus.oms.dto.ApiResponse;
import com.nexus.oms.security.TenantContext;
import com.nexus.oms.service.twin.WarehouseTwinService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.UUID;

/** Read models for the live digital-twin pages. */
@RestController
@RequestMapping("/twin")
@Tag(name = "Digital twin", description = "Aggregated live snapshots for the Warehouse and Order Flow twins")
public class TwinController {

    private final WarehouseTwinService warehouseTwinService;

    public TwinController(WarehouseTwinService warehouseTwinService) {
        this.warehouseTwinService = warehouseTwinService;
    }

    @Operation(summary = "Sites for the twin site switcher, with fill % and dock occupancy")
    @GetMapping("/warehouses")
    public ResponseEntity<ApiResponse<List<WarehouseTwinService.SiteSummary>>> sites() {
        return ResponseEntity.ok(ApiResponse.success(warehouseTwinService.listSites(TenantContext.getCurrentTenantId())));
    }

    @Operation(summary = "Full live snapshot of one warehouse: KPIs, zones, docks, yard, trucks, equipment, stock, shipments")
    @GetMapping("/warehouses/{id}")
    public ResponseEntity<ApiResponse<WarehouseTwinService.Snapshot>> snapshot(@PathVariable UUID id) {
        return ResponseEntity.ok(ApiResponse.success(warehouseTwinService.snapshot(TenantContext.getCurrentTenantId(), id)));
    }
}
