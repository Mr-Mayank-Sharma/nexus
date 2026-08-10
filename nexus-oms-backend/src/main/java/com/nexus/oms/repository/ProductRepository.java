package com.nexus.oms.repository;

import com.nexus.oms.entity.Product;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.math.BigDecimal;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface ProductRepository extends JpaRepository<Product, UUID> {
    Page<Product> findByTenantId(UUID tenantId, Pageable pageable);
    Optional<Product> findByIdAndTenantId(UUID id, UUID tenantId);
    boolean existsByTenantIdAndSku(UUID tenantId, String sku);
    Optional<Product> findByTenantIdAndSku(UUID tenantId, String sku);

    @Query("""
            SELECT p FROM Product p WHERE p.tenantId = :tenantId
              AND (:query IS NULL OR :query = '' OR LOWER(p.sku) LIKE LOWER(CONCAT('%', :query, '%'))
                   OR LOWER(p.productName) LIKE LOWER(CONCAT('%', :query, '%')))
              AND (:category IS NULL OR :category = '' OR LOWER(p.category) LIKE LOWER(CONCAT('%', :category, '%')))
              AND (:minPrice IS NULL OR p.unitPrice >= :minPrice)
              AND (:maxPrice IS NULL OR p.unitPrice <= :maxPrice)
              AND (:active IS NULL OR p.isActive = :active)
            """)
    List<Product> search(@Param("tenantId") UUID tenantId,
                         @Param("query") String query,
                         @Param("category") String category,
                         @Param("minPrice") BigDecimal minPrice,
                         @Param("maxPrice") BigDecimal maxPrice,
                         @Param("active") Boolean active);
}
