package com.nexus.oms.repository;

import com.nexus.oms.entity.NxUser;
import org.springframework.data.jpa.repository.JpaRepository;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface UserRepository extends JpaRepository<NxUser, UUID> {
    Optional<NxUser> findByUsername(String username);
    boolean existsByUsername(String username);
    /** E-mail is not unique across tenants; callers must treat more than one match as ambiguous. */
    List<NxUser> findByEmailIgnoreCase(String email);
}
