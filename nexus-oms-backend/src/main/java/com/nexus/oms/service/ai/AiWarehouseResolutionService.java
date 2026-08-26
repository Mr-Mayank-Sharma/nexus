package com.nexus.oms.service.ai;

import com.nexus.oms.entity.Warehouse;
import com.nexus.oms.repository.WarehouseRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

/**
 * Resolves AI predictions against the tenant's REAL warehouse network.
 *
 * Replaces the phantom "wh-1"/"wh-2"/"wh-3" heuristic IDs that referenced
 * warehouses which do not exist in nx_warehouses. Every prediction that names
 * a warehouse must name one that actually belongs to the tenant.
 */
@Service
public class AiWarehouseResolutionService {

    private static final Logger log = LoggerFactory.getLogger(AiWarehouseResolutionService.class);

    private final WarehouseRepository warehouseRepository;

    public AiWarehouseResolutionService(WarehouseRepository warehouseRepository) {
        this.warehouseRepository = warehouseRepository;
    }

    /**
     * Resolve the best fulfillment warehouse for a destination zip/pincode.
     *
     * Strategy:
     *  1. Exact zip match against an active warehouse
     *  2. Longest numeric-prefix match (works for Indian pincodes where the
     *     leading digits identify the region circle, e.g. 56xxxx = Karnataka)
     *  3. First active warehouse for the tenant
     *
     * Returns empty when the tenant has no usable warehouses — callers must
     * surface that honestly rather than inventing an ID.
     */
    public Optional<Warehouse> resolveForDestination(UUID tenantId, String destZip) {
        List<Warehouse> active = activeWarehouses(tenantId);
        if (active.isEmpty()) {
            return Optional.empty();
        }

        String normalizedDest = normalize(destZip);
        if (!normalizedDest.isEmpty()) {
            Optional<Warehouse> exact = active.stream()
                    .filter(w -> normalizedDest.equals(normalize(w.getZipCode())))
                    .findFirst();
            if (exact.isPresent()) {
                return exact;
            }

            Optional<Warehouse> prefixMatch = active.stream()
                    .filter(w -> !normalize(w.getZipCode()).isEmpty())
                    .filter(w -> commonPrefixLength(normalizedDest, normalize(w.getZipCode())) > 0)
                    .max((a, b) -> Integer.compare(
                            commonPrefixLength(normalizedDest, normalize(a.getZipCode())),
                            commonPrefixLength(normalizedDest, normalize(b.getZipCode()))));
            if (prefixMatch.isPresent()) {
                return prefixMatch;
            }
        }

        return Optional.of(active.get(0));
    }

    /**
     * Primary warehouse: first active warehouse for the tenant.
     */
    public Optional<Warehouse> primaryForTenant(UUID tenantId) {
        return activeWarehouses(tenantId).stream().findFirst();
    }

    private List<Warehouse> activeWarehouses(UUID tenantId) {
        if (tenantId == null) {
            return List.of();
        }
        try {
            List<Warehouse> byStatus = warehouseRepository.findByTenantIdAndStatus(tenantId, "ACTIVE");
            if (byStatus != null && !byStatus.isEmpty()) {
                return byStatus;
            }
            // Tolerate blank/unexpected status values but respect isActive=false
            return warehouseRepository.findByTenantId(tenantId, PageRequest.of(0, 100))
                    .getContent().stream()
                    .filter(w -> w.getIsActive() == null || Boolean.TRUE.equals(w.getIsActive()))
                    .toList();
        } catch (Exception e) {
            log.warn("Warehouse resolution failed for tenant {}: {}", tenantId, e.getMessage());
            return List.of();
        }
    }

    private String normalize(String zip) {
        return zip == null ? "" : zip.replaceAll("[^0-9A-Za-z]", "").toUpperCase();
    }

    private int commonPrefixLength(String a, String b) {
        int n = Math.min(a.length(), b.length());
        for (int i = 0; i < n; i++) {
            if (a.charAt(i) != b.charAt(i)) {
                return i;
            }
        }
        return n;
    }
}
