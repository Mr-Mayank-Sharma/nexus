package com.nexus.oms.coremigration;

import com.nexus.oms.dto.ApiResponse;
import com.nexus.oms.security.TenantContext;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/** Copies legacy data into the core model (doc 26, W4-W9). Admin only; safe to run again. */
@RestController
@RequestMapping("/core-migration")
@Tag(name = "Core migration", description = "Copy legacy records into the OFBiz-aligned core model")
public class CoreMigrationController {

    private final CustomerPartyBackfill customers;
    private final ProductBackfill products;
    private final FacilityInventoryBackfill stock;

    public CoreMigrationController(CustomerPartyBackfill customers, ProductBackfill products, FacilityInventoryBackfill stock) {
        this.customers = customers;
        this.products = products;
        this.stock = stock;
    }

    @Operation(summary = "Copy this company's fulfilment locations and stock into the facility and inventory model (run the product copy first)")
    @PostMapping("/stock")
    public ResponseEntity<ApiResponse<FacilityInventoryBackfill.Result>> stock() {
        return ResponseEntity.ok(ApiResponse.success(stock.run(TenantContext.getCurrentTenantId()), "Stock copied"));
    }

    @Operation(summary = "Copy this company's connected stores and products into the product model")
    @PostMapping("/products")
    public ResponseEntity<ApiResponse<ProductBackfill.Result>> products() {
        return ResponseEntity.ok(ApiResponse.success(products.run(TenantContext.getCurrentTenantId()), "Products copied"));
    }

    @Operation(summary = "Copy this company's customers and addresses into the party model")
    @PostMapping("/customers")
    public ResponseEntity<ApiResponse<CustomerPartyBackfill.Result>> customers() {
        return ResponseEntity.ok(ApiResponse.success(customers.run(TenantContext.getCurrentTenantId()), "Customers copied"));
    }
}
