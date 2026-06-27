package com.nexus.oms.entity;

import jakarta.persistence.*;
import lombok.*;
import java.time.LocalDateTime;
import java.util.UUID;

@Entity
@Table(name = "nx_integration_messages")
@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class IntegrationMessage {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @Column(name = "tenant_id")
    private UUID tenantId;

    @Column(name = "flow_id")
    private UUID flowId;

    @Column(name = "message_id", nullable = false)
    private String messageId;

    @Column(name = "correlation_id")
    private String correlationId;

    private String source;

    @Column(name = "message_type", nullable = false)
    private String messageType;

    private String format;

    @Column(columnDefinition = "text")
    private String payload;

    @Column(name = "payload_size")
    private Long payloadSize;

    @Column(columnDefinition = "jsonb")
    private String headers;

    private String status;

    @Column(name = "error_message", columnDefinition = "text")
    private String errorMessage;

    @Column(name = "error_code")
    private String errorCode;

    @Column(name = "retry_count")
    private Integer retryCount;

    @Column(name = "max_retries")
    private Integer maxRetries;

    @Column(name = "processed_at")
    private LocalDateTime processedAt;

    @Column(name = "created_at", nullable = false, updatable = false)
    private LocalDateTime createdAt;

    @PrePersist
    protected void onCreate() {
        createdAt = LocalDateTime.now();
        if (status == null) status = "PENDING";
        if (retryCount == null) retryCount = 0;
        if (maxRetries == null) maxRetries = 3;
    }
}
