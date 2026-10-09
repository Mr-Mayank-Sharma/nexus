package com.nexus.oms.entity;

import jakarta.persistence.*;
import lombok.*;

import java.time.LocalDateTime;
import java.util.UUID;

/** Links a Nexus user to an external IdP identity by the IdP's stable subject id. */
@Entity
@Table(name = "nx_user_identities")
@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class NxUserIdentity {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @Column(name = "user_id", nullable = false)
    private UUID userId;

    @Column(name = "tenant_id")
    private UUID tenantId;

    @Column(nullable = false, length = 32)
    private String provider;

    @Column(nullable = false)
    private String subject;

    private String email;

    @Column(name = "created_at", nullable = false, updatable = false)
    private LocalDateTime createdAt;

    @PrePersist
    protected void onCreate() {
        createdAt = LocalDateTime.now();
    }
}
