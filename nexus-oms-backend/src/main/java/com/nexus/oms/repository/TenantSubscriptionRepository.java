package com.nexus.oms.repository;

import com.nexus.oms.entity.NxTenantSubscription;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.UUID;

public interface TenantSubscriptionRepository extends JpaRepository<NxTenantSubscription, UUID> {
}
