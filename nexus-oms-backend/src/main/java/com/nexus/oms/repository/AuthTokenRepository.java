package com.nexus.oms.repository;

import com.nexus.oms.entity.NxAuthToken;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.LocalDateTime;
import java.util.Optional;
import java.util.UUID;

public interface AuthTokenRepository extends JpaRepository<NxAuthToken, UUID> {

    Optional<NxAuthToken> findByTokenTypeAndTokenHash(String tokenType, String tokenHash);

    @Modifying
    @Query("DELETE FROM NxAuthToken t WHERE t.expiresAt < :now")
    int deleteExpired(@Param("now") LocalDateTime now);

    @Modifying
    @Query("DELETE FROM NxAuthToken t WHERE t.tokenType = :type AND t.username = :username")
    int deleteByTypeAndUsername(@Param("type") String type, @Param("username") String username);
}
