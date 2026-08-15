package com.nexus.oms.repository;

import com.nexus.oms.entity.NxBoxTemplate;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.UUID;

public interface BoxTemplateRepository extends JpaRepository<NxBoxTemplate, UUID> {

    List<NxBoxTemplate> findByTenantIdAndIsActiveTrue(UUID tenantId);

    @Query("SELECT b FROM NxBoxTemplate b WHERE b.tenantId = :tenantId AND b.isActive = true " +
           "AND b.volumeCapacityIn3 >= :volume " +
           "ORDER BY b.volumeCapacityIn3 ASC")
    List<NxBoxTemplate> findSmallestFitting(UUID tenantId, @Param("volume") double volume);
}
