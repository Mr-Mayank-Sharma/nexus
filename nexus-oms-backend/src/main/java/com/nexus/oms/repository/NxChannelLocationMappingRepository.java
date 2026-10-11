package com.nexus.oms.repository;

import com.nexus.oms.entity.NxChannelLocationMapping;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.UUID;

public interface NxChannelLocationMappingRepository extends JpaRepository<NxChannelLocationMapping, UUID> {
    List<NxChannelLocationMapping> findByIntegrationStoreId(UUID integrationStoreId);

    void deleteByIntegrationStoreId(UUID integrationStoreId);
}
