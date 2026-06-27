package com.nexus.oms.repository;

import com.nexus.oms.entity.NotificationTemplate;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface NotificationTemplateRepository extends JpaRepository<NotificationTemplate, UUID> {

    Page<NotificationTemplate> findByTenantId(UUID tenantId, Pageable pageable);

    Optional<NotificationTemplate> findByTenantIdAndTemplateCodeAndChannel(UUID tenantId, String templateCode, String channel);

    List<NotificationTemplate> findByTenantIdAndChannelAndIsActive(UUID tenantId, String channel, Boolean isActive);
}
