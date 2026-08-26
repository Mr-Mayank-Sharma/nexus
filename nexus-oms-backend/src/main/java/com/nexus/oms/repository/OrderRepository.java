package com.nexus.oms.repository;

import com.nexus.oms.entity.NxOrder;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface OrderRepository extends JpaRepository<NxOrder, UUID> {

    Page<NxOrder> findByTenantIdAndStatus(UUID tenantId, String status, Pageable pageable);

    Optional<NxOrder> findByTenantIdAndChannelOrderId(UUID tenantId, String channelOrderId);

    // Channel-scoped dedup lookup — prevents cross-channel collisions (e.g. a Shopify
    // order_number matching a BigCommerce channel_order_id) from breaking imports
    Optional<NxOrder> findByTenantIdAndChannelAndChannelOrderId(UUID tenantId, String channel, String channelOrderId);

    Page<NxOrder> findByTenantId(UUID tenantId, Pageable pageable);

    List<NxOrder> findByCustomerId(UUID customerId);

    List<NxOrder> findByTenantIdAndChannel(UUID tenantId, String channel);

    @Query("SELECT o FROM NxOrder o WHERE o.tenantId = :tenantId AND LOWER(o.channel) = LOWER(:channel)")
    List<NxOrder> findByTenantIdAndChannelIgnoreCase(@Param("tenantId") UUID tenantId, @Param("channel") String channel);

    @Query("SELECT COUNT(o) FROM NxOrder o WHERE o.tenantId = :tenantId AND LOWER(o.channel) = LOWER(:channel)")
    long countByTenantIdAndChannelIgnoreCase(@Param("tenantId") UUID tenantId, @Param("channel") String channel);

    long countByTenantIdAndStatus(UUID tenantId, String status);

    long countByTenantIdAndStatusNot(UUID tenantId, String status);

    @Query("SELECT o FROM NxOrder o WHERE o.tenantId = :tenantId AND " +
           "(LOWER(o.status) LIKE LOWER(CONCAT('%', :search, '%')) OR " +
           "LOWER(o.channel) LIKE LOWER(CONCAT('%', :search, '%')) OR " +
           "LOWER(o.channelOrderId) LIKE LOWER(CONCAT('%', :search, '%')) OR " +
           "LOWER(o.trackingNumber) LIKE LOWER(CONCAT('%', :search, '%')) OR " +
           "LOWER(o.customerEmail) LIKE LOWER(CONCAT('%', :search, '%')))")
    Page<NxOrder> search(@Param("tenantId") UUID tenantId, @Param("search") String search, Pageable pageable);

    long countByTenantIdAndCreatedAtAfter(UUID tenantId, LocalDateTime after);

    @Query("SELECT COALESCE(SUM(o.total), 0) FROM NxOrder o WHERE o.tenantId = :tenantId AND o.createdAt >= :after")
    BigDecimal sumTotalByTenantIdAndCreatedAtAfter(@Param("tenantId") UUID tenantId, @Param("after") LocalDateTime after);

    @Query("SELECT o FROM NxOrder o WHERE o.tenantId = :tenantId ORDER BY o.createdAt DESC")
    List<NxOrder> findRecentByTenantId(@Param("tenantId") UUID tenantId, Pageable pageable);

    @Query("SELECT o FROM NxOrder o WHERE o.tenantId = :tenantId AND o.customerId = :customerId " +
           "AND o.createdAt >= :from AND o.createdAt < :to ORDER BY o.createdAt DESC")
    List<NxOrder> findByTenantIdAndCustomerIdAndCreatedAtBetween(@Param("tenantId") UUID tenantId,
            @Param("customerId") UUID customerId, @Param("from") LocalDateTime from, @Param("to") LocalDateTime to);

    @Query("SELECT o.createdAt FROM NxOrder o WHERE o.tenantId = :tenantId AND o.createdAt >= :from")
    List<LocalDateTime> findCreatedAtSince(@Param("tenantId") UUID tenantId, @Param("from") LocalDateTime from);

    @Query("SELECT o.createdAt, COALESCE(SUM(o.total), 0) FROM NxOrder o " +
           "WHERE o.tenantId = :tenantId AND o.createdAt >= :from GROUP BY o.createdAt")
    List<Object[]> findDailyTotalsSince(@Param("tenantId") UUID tenantId, @Param("from") LocalDateTime from);
}
