package com.nexus.oms.entity;

import jakarta.persistence.*;
import jakarta.validation.constraints.PositiveOrZero;
import lombok.*;
import java.time.LocalDateTime;
import java.util.UUID;

@Entity
@Table(name = "nx_documents")
@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class Document {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @Column(name = "tenant_id")
    private UUID tenantId;

    @Column(name = "document_number")
    private String documentNumber;

    private String title;

    private String description;

    @Column(name = "document_type")
    private String documentType;

    private String category;

    @Column(name = "file_name")
    private String fileName;

    @PositiveOrZero
    @Column(name = "file_size")
    private Long fileSize;

    @Column(name = "mime_type")
    private String mimeType;

    @Column(name = "file_url")
    private String fileUrl;

    @Column(name = "storage_path")
    private String storagePath;

    @Column(name = "file_hash")
    private String fileHash;

    @Column(name = "current_version")
    private Integer currentVersion;

    @Column(name = "entity_type")
    private String entityType;

    @Column(name = "entity_id")
    private UUID entityId;

    @Column(name = "is_public")
    private Boolean isPublic;

    @Column(columnDefinition = "jsonb")
    private String tags;

    @Column(columnDefinition = "jsonb")
    private String metadata;

    @Column(name = "uploaded_by")
    private UUID uploadedBy;

    @Column(name = "created_at", nullable = false, updatable = false)
    private LocalDateTime createdAt;

    @Column(name = "updated_at", nullable = false)
    private LocalDateTime updatedAt;

    @PrePersist
    protected void onCreate() {
        createdAt = LocalDateTime.now();
        updatedAt = LocalDateTime.now();
        if (currentVersion == null) currentVersion = 1;
        if (isPublic == null) isPublic = false;
    }

    @PreUpdate
    protected void onUpdate() {
        updatedAt = LocalDateTime.now();
    }
}
