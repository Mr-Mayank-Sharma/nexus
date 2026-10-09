package com.nexus.oms.repository;

import com.nexus.oms.entity.NxUserIdentity;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;
import java.util.UUID;

public interface UserIdentityRepository extends JpaRepository<NxUserIdentity, UUID> {

    Optional<NxUserIdentity> findByProviderAndSubject(String provider, String subject);
}
