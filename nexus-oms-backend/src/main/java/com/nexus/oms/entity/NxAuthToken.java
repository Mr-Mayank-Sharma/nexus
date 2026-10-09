package com.nexus.oms.entity;

import jakarta.persistence.*;
import lombok.*;

import java.time.LocalDateTime;
import java.util.UUID;

/**
 * Short-lived, single-use authentication artefact (MFA challenge, password reset,
 * SSO state, SSO exchange code). Only the SHA-256 hash of the token is stored.
 */
@Entity
@Table(name = "nx_auth_tokens")
@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class NxAuthToken {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @Column(name = "token_type", nullable = false, length = 32)
    private String tokenType;

    @ToString.Exclude
    @Column(name = "token_hash", nullable = false, length = 64)
    private String tokenHash;

    private String username;

    @Column(columnDefinition = "TEXT")
    private String payload;

    @Builder.Default
    @Column(nullable = false)
    private int attempts = 0;

    @Column(name = "expires_at", nullable = false)
    private LocalDateTime expiresAt;

    @Column(name = "created_at", nullable = false, updatable = false)
    private LocalDateTime createdAt;

    @PrePersist
    protected void onCreate() {
        createdAt = LocalDateTime.now();
    }
}
