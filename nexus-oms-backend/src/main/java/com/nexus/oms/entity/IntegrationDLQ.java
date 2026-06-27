package com.nexus.oms.entity;

import jakarta.persistence.*;
import lombok.*;
import java.time.LocalDateTime;
import java.util.UUID;

@Entity
@Table(name = "nx_integration_dlq")
@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class IntegrationDLQ {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @Column(name = "tenant_id")
    private UUID tenantId;

    @Column(name = "flow_id")
    private UUID flowId;

    @Column(name = "endpoint_id")
    private UUID endpointId;

    @Column(name = "message_id")
    private String messageId;

    @Column(name = "original_payload", columnDefinition = "text")
    private String originalPayload;

    @Column(name = "error_message", columnDefinition = "text")
    private String errorMessage;

    @Column(name = "error_stacktrace", columnDefinition = "text")
    private String errorStacktrace;

    @Column(name = "error_category")
    private String errorCategory;

    @Column(name = "retry_count")
    private Integer retryCount;

    @Column(name = "max_retries")
    private Integer maxRetries;

    @Column(name = "last_retry_at")
    private LocalDateTime lastRetryAt;

    private String status;

    @Column(name = "created_at", nullable = false, updatable = false)
    private LocalDateTime createdAt;

    @Column(name = "updated_at", nullable = false)
    private LocalDateTime updatedAt;

    @PrePersist
    protected void onCreate() {
        createdAt = LocalDateTime.now();
        updatedAt = LocalDateTime.now();
        if (retryCount == null) retryCount = 0;
        if (maxRetries == null) maxRetries = 3;
        if (status == null) status = "FAILED";
    }

    @PreUpdate
    protected void onUpdate() {
        updatedAt = LocalDateTime.now();
    }
}
