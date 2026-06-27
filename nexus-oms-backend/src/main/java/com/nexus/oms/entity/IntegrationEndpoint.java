package com.nexus.oms.entity;

import jakarta.persistence.*;
import lombok.*;
import java.time.LocalDateTime;
import java.util.UUID;

@Entity
@Table(name = "nx_integration_endpoints")
@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class IntegrationEndpoint {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @Column(name = "tenant_id")
    private UUID tenantId;

    @Column(nullable = false)
    private String name;

    @Column(columnDefinition = "text")
    private String description;

    @Column(name = "endpoint_type", nullable = false)
    private String endpointType;

    @Column(nullable = false)
    private String protocol;

    private String host;

    private Integer port;

    private String path;

    private String method;

    @Column(columnDefinition = "jsonb")
    private String headers;

    @Column(name = "query_params", columnDefinition = "jsonb")
    private String queryParams;

    @Column(name = "auth_type")
    private String authType;

    @Column(name = "auth_config", columnDefinition = "jsonb")
    private String authConfig;

    @Column(name = "ssl_enabled")
    private Boolean sslEnabled;

    @Column(name = "timeout_ms")
    private Integer timeoutMs;

    @Column(name = "retry_count")
    private Integer retryCount;

    @Column(name = "retry_delay_ms")
    private Integer retryDelayMs;

    @Column(name = "circuit_breaker_enabled")
    private Boolean circuitBreakerEnabled;

    @Column(name = "circuit_breaker_threshold")
    private Integer circuitBreakerThreshold;

    @Column(name = "circuit_breaker_timeout_ms")
    private Integer circuitBreakerTimeoutMs;

    @Column(name = "rate_limit_enabled")
    private Boolean rateLimitEnabled;

    @Column(name = "rate_limit_max")
    private Integer rateLimitMax;

    @Column(name = "rate_limit_window_ms")
    private Integer rateLimitWindowMs;

    @Column(name = "is_active")
    private Boolean isActive;

    @Column(columnDefinition = "jsonb")
    private String metadata;

    @Column(name = "created_at", nullable = false, updatable = false)
    private LocalDateTime createdAt;

    @Column(name = "updated_at", nullable = false)
    private LocalDateTime updatedAt;

    @PrePersist
    protected void onCreate() {
        createdAt = LocalDateTime.now();
        updatedAt = LocalDateTime.now();
        if (sslEnabled == null) sslEnabled = false;
        if (timeoutMs == null) timeoutMs = 30000;
        if (retryCount == null) retryCount = 3;
        if (retryDelayMs == null) retryDelayMs = 1000;
        if (circuitBreakerEnabled == null) circuitBreakerEnabled = true;
        if (circuitBreakerThreshold == null) circuitBreakerThreshold = 5;
        if (circuitBreakerTimeoutMs == null) circuitBreakerTimeoutMs = 30000;
        if (rateLimitEnabled == null) rateLimitEnabled = false;
        if (rateLimitMax == null) rateLimitMax = 100;
        if (rateLimitWindowMs == null) rateLimitWindowMs = 1000;
        if (isActive == null) isActive = true;
    }

    @PreUpdate
    protected void onUpdate() {
        updatedAt = LocalDateTime.now();
    }
}
