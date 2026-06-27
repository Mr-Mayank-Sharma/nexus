package com.nexus.oms.entity;

import jakarta.persistence.*;
import lombok.*;
import java.time.LocalDateTime;
import java.util.UUID;

@Entity
@Table(name = "nx_email_ingestion_config")
@Data @NoArgsConstructor @AllArgsConstructor @Builder
public class NxEmailIngestionConfig {
    @Id @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @Column(name = "tenant_id", nullable = false, unique = true)
    private UUID tenantId;

    private String protocol;
    private String host;
    private Integer port;
    private String username;

    @Column(name = "encrypted_password", columnDefinition = "TEXT")
    private String encryptedPassword;

    @Column(name = "use_ssl")
    private Boolean useSsl;

    @Column(name = "inbox_folder")
    private String inboxFolder;

    @Column(name = "processed_folder")
    private String processedFolder;

    @Column(name = "failed_folder")
    private String failedFolder;

    @Column(name = "polling_interval_sec")
    private Integer pollingIntervalSec;

    @Column(name = "allowed_senders", columnDefinition = "TEXT")
    private String allowedSenders;

    @Column(name = "subject_filter")
    private String subjectFilter;

    @Column(name = "auto_create_orders")
    private Boolean autoCreateOrders;

    @Column(name = "send_confirmation")
    private Boolean sendConfirmation;

    @Column(name = "is_active")
    private Boolean isActive;

    @Column(name = "last_polled_at")
    private LocalDateTime lastPolledAt;

    @Column(name = "created_at", nullable = false, updatable = false)
    private LocalDateTime createdAt;

    @Column(name = "updated_at", nullable = false)
    private LocalDateTime updatedAt;

    @PrePersist
    protected void onCreate() {
        createdAt = LocalDateTime.now();
        updatedAt = LocalDateTime.now();
        if (protocol == null) protocol = "IMAP";
        if (port == null) port = 993;
        if (useSsl == null) useSsl = true;
        if (inboxFolder == null) inboxFolder = "INBOX";
        if (processedFolder == null) processedFolder = "Processed";
        if (failedFolder == null) failedFolder = "Failed";
        if (pollingIntervalSec == null) pollingIntervalSec = 300;
        if (autoCreateOrders == null) autoCreateOrders = false;
        if (sendConfirmation == null) sendConfirmation = true;
        if (isActive == null) isActive = false;
    }

    @PreUpdate
    protected void onUpdate() {
        updatedAt = LocalDateTime.now();
    }
}
