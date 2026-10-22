package com.nexus.oms.coreapi;

import com.nexus.oms.dto.ApiResponse;
import com.nexus.oms.security.TenantContext;
import com.nexus.oms.tenancy.TenantDatabaseRegistry;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.boot.autoconfigure.jdbc.DataSourceProperties;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.UUID;

/**
 * Where the signed-in company's core data lives, and moving it to a database of its own (doc 29, phase B). Admin only
 * (the {@code /core/} prefix maps to the settings permission). The new database is created on the same server as the
 * shared one, with the same credentials.
 */
@RestController
@RequestMapping("/core/tenancy")
@Tag(name = "Core tenancy", description = "One core database per company")
public class CoreTenancyController {

    private final TenantDatabaseRegistry tenantDatabases;
    private final DataSourceProperties sharedDatabase;

    public CoreTenancyController(TenantDatabaseRegistry tenantDatabases, DataSourceProperties sharedDatabase) {
        this.tenantDatabases = tenantDatabases;
        this.sharedDatabase = sharedDatabase;
    }

    public record DatabaseView(String companyId, boolean ownDatabase) {
    }

    @Operation(summary = "Whether the signed-in company has its own core database")
    @GetMapping("/database")
    public ResponseEntity<ApiResponse<DatabaseView>> database() {
        UUID companyId = TenantContext.getCurrentTenantId();
        return ResponseEntity.ok(ApiResponse.success(new DatabaseView(companyId.toString(), tenantDatabases.hasOwnDatabase(companyId)), "Database"));
    }

    @Operation(summary = "Give the signed-in company its own core database (created and migrated now; safe to call twice)")
    @PostMapping("/database")
    public ResponseEntity<ApiResponse<DatabaseView>> provision() {
        UUID companyId = TenantContext.getCurrentTenantId();
        tenantDatabases.provision(companyId, sharedDatabase.determineUrl(), sharedDatabase.determineUsername(), sharedDatabase.determinePassword());
        return ResponseEntity.ok(ApiResponse.success(new DatabaseView(companyId.toString(), true), "The company now has its own core database"));
    }
}
