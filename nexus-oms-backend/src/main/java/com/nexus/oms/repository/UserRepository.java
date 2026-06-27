package com.nexus.oms.repository;

import com.nexus.oms.entity.NxUser;
import org.springframework.data.jpa.repository.JpaRepository;
import java.util.Optional;
import java.util.UUID;

public interface UserRepository extends JpaRepository<NxUser, UUID> {
    Optional<NxUser> findByUsername(String username);
    boolean existsByUsername(String username);
}
