package com.nexus.oms.generic;

import com.nexus.oms.dto.ApiResponse;
import com.nexus.oms.security.TenantContext;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.*;

/**
 * Generic connections: describe any system with a JSON API (endpoint, auth, paging, field mapping), preview what
 * the mapping produces, then schedule it with the GENERIC_IMPORT or GENERIC_EXPORT job type.
 */
@RestController
@RequestMapping("/generic-connections")
public class GenericConnectionController {

    private final GenericConnectionService service;

    public GenericConnectionController(GenericConnectionService service) {
        this.service = service;
    }

    @GetMapping
    public ResponseEntity<ApiResponse<List<NxGenericConnection>>> list() {
        return ResponseEntity.ok(ApiResponse.success(service.list(TenantContext.getCurrentTenantId())));
    }

    @GetMapping("/templates")
    public ResponseEntity<ApiResponse<List<GenericConnectionService.Template>>> templates() {
        return ResponseEntity.ok(ApiResponse.success(service.templates()));
    }

    /** The fields an export mapping can read, and the fields an import mapping can fill. */
    @GetMapping("/fields")
    public ResponseEntity<ApiResponse<Map<String, Object>>> fields() {
        return ResponseEntity.ok(ApiResponse.success(Map.of(
                "export", GenericExporter.VIEW_FIELDS,
                "import", Map.of(
                        "orders", List.of("orderId (required)", "orderNumber", "status", "currency", "subtotal", "shipping", "tax", "total",
                                "email", "customerName", "phone", "shipName", "shipLine1", "shipLine2", "shipCity", "shipState",
                                "shipPostalCode", "shipCountry", "orderType (B2C or B2B)", "requestedShipDate", "fulfillmentType",
                                "lines: sku (required), quantity (required), name, unitPrice"),
                        "inventory", List.of("sku (required)", "quantity (required, on hand)", "location")),
                "transforms", List.of("trim", "upper", "lower", "int", "number", "cents", "date", "default:VALUE"))));
    }

    @GetMapping("/{id}")
    public ResponseEntity<ApiResponse<NxGenericConnection>> get(@PathVariable UUID id) {
        return ResponseEntity.ok(ApiResponse.success(service.get(TenantContext.getCurrentTenantId(), id)));
    }

    @PostMapping
    public ResponseEntity<ApiResponse<NxGenericConnection>> create(@RequestBody GenericConnectionService.ConnectionRequest r) {
        return ResponseEntity.ok(ApiResponse.success(service.create(TenantContext.getCurrentTenantId(), r), "Connection saved. Preview it before you schedule it."));
    }

    @PutMapping("/{id}")
    public ResponseEntity<ApiResponse<NxGenericConnection>> update(@PathVariable UUID id, @RequestBody GenericConnectionService.ConnectionRequest r) {
        return ResponseEntity.ok(ApiResponse.success(service.update(TenantContext.getCurrentTenantId(), id, r), "Connection saved"));
    }

    @DeleteMapping("/{id}")
    public ResponseEntity<ApiResponse<Void>> delete(@PathVariable UUID id) {
        service.delete(TenantContext.getCurrentTenantId(), id);
        return ResponseEntity.ok(ApiResponse.success(null, "Connection deleted"));
    }

    @PostMapping("/{id}/preview")
    public ResponseEntity<ApiResponse<GenericConnectionService.Preview>> preview(@PathVariable UUID id) {
        return ResponseEntity.ok(ApiResponse.success(service.preview(TenantContext.getCurrentTenantId(), id)));
    }

    @PostMapping("/{id}/reset-watermark")
    public ResponseEntity<ApiResponse<NxGenericConnection>> resetWatermark(@PathVariable UUID id) {
        return ResponseEntity.ok(ApiResponse.success(service.resetWatermark(TenantContext.getCurrentTenantId(), id), "The next run starts from the beginning"));
    }
}
