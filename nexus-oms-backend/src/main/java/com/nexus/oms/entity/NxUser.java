package com.nexus.oms.entity;

import jakarta.persistence.*;
import lombok.*;
import java.time.LocalDateTime;
import java.util.UUID;

@Entity
@Table(name = "nx_users")
@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class NxUser {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @Column(nullable = false, unique = true)
    private String username;

    @ToString.Exclude
    @Column(name = "password_hash", nullable = false)
    private String passwordHash;

    private String email;

    @Column(nullable = false)
    private String role;

    @Column(name = "tenant_id")
    private UUID tenantId;

    /** True once the user has enrolled and confirmed an RFC 6238 authenticator. */
    @Builder.Default
    @Column(name = "mfa_enabled", nullable = false)
    private boolean mfaEnabled = false;

    /** Base32 TOTP secret, encrypted with {@code CredentialVault}. Pending until mfaEnabled. */
    @ToString.Exclude
    @Column(name = "mfa_secret")
    private String mfaSecret;

    /** Comma-separated SHA-256 hashes of unused recovery codes. */
    @ToString.Exclude
    @Column(name = "mfa_recovery_codes")
    private String mfaRecoveryCodes;

    @Column(name = "created_at", nullable = false, updatable = false)
    private LocalDateTime createdAt;

    @Column(name = "updated_at", nullable = false)
    private LocalDateTime updatedAt;

    @PrePersist
    protected void onCreate() {
        createdAt = LocalDateTime.now();
        updatedAt = LocalDateTime.now();
    }

    @PreUpdate
    protected void onUpdate() {
        updatedAt = LocalDateTime.now();
    }
}
