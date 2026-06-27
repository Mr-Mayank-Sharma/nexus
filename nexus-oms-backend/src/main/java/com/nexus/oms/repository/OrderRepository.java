package com.nexus.oms.repository;

import com.nexus.oms.entity.NxOrder;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface OrderRepository extends JpaRepository<NxOrder, UUID> {

    Page<NxOrder> findByTenantIdAndStatus(UUID tenantId, String status, Pageable pageable);

    Optional<NxOrder> findByTenantIdAndChannelOrderId(UUID tenantId, String channelOrderId);

    Page<NxOrder> findByTenantId(UUID tenantId, Pageable pageable);

    List<NxOrder> findByCustomerId(UUID customerId);

    long countByTenantIdAndStatus(UUID tenantId, String status);

    long countByTenantIdAndStatusNot(UUID tenantId, String status);

    @Query("SELECT o FROM NxOrder o WHERE o.tenantId = :tenantId AND " +
           "(LOWER(o.status) LIKE LOWER(CONCAT('%', :search, '%')) OR " +
           "LOWER(o.channel) LIKE LOWER(CONCAT('%', :search, '%')) OR " +
           "LOWER(o.channelOrderId) LIKE LOWER(CONCAT('%', :search, '%')) OR " +
           "LOWER(o.trackingNumber) LIKE LOWER(CONCAT('%', :search, '%')))")
    Page<NxOrder> search(@Param("tenantId") UUID tenantId, @Param("search") String search, Pageable pageable);
}
