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

    public CoreMigrationController(CustomerPartyBackfill customers) {
        this.customers = customers;
    }

    @Operation(summary = "Copy this company's customers and addresses into the party model")
    @PostMapping("/customers")
    public ResponseEntity<ApiResponse<CustomerPartyBackfill.Result>> customers() {
        return ResponseEntity.ok(ApiResponse.success(customers.run(TenantContext.getCurrentTenantId()), "Customers copied"));
    }
}
