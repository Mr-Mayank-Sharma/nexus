package com.nexus.oms.repository;

import com.nexus.oms.entity.NxIntegrationStoreSetting;
import org.springframework.data.jpa.repository.JpaRepository;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface NxIntegrationStoreSettingRepository extends JpaRepository<NxIntegrationStoreSetting, UUID> {
    List<NxIntegrationStoreSetting> findByStoreId(UUID storeId);
    Optional<NxIntegrationStoreSetting> findByStoreIdAndSettingType(UUID storeId, String settingType);
    void deleteByStoreId(UUID storeId);
}
