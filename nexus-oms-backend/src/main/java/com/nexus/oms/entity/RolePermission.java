package com.nexus.oms.entity;

import jakarta.persistence.*;
import jakarta.validation.constraints.*;
import lombok.*;
import java.time.LocalDateTime;
import java.util.UUID;

@Entity
@Table(name = "nx_role_permissions")
@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class RolePermission {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @Column(name = "tenant_id")
    private UUID tenantId;

    private String role;

    @Column(name = "permission_group")
    private String permissionGroup;

    @Column(name = "permission_name")
    private String permissionName;

    @Column(name = "can_view")
    private Boolean canView;

    @Column(name = "can_create")
    private Boolean canCreate;

    @Column(name = "can_edit")
    private Boolean canEdit;

    @Column(name = "can_delete")
    private Boolean canDelete;

    @Column(name = "can_approve")
    private Boolean canApprove;

    @Column(name = "created_at", nullable = false, updatable = false)
    private LocalDateTime createdAt;

    @PrePersist
    protected void onCreate() {
        createdAt = LocalDateTime.now();
    }
}
