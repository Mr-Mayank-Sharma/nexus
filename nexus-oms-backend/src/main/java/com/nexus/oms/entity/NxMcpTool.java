package com.nexus.oms.entity;

import jakarta.persistence.*;
import lombok.*;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;
import java.time.LocalDateTime;
import java.util.UUID;

/**
 * T-11: MCP server tool registry.
 *
 * Defines which OMS operations are exposed as MCP tools, their read/write
 * classification, and the REST/GraphQL endpoint they map to (single source of
 * truth for auth).
 */
@Entity
@Table(name = "nx_mcp_tools")
@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class NxMcpTool {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @Column(name = "tenant_id", nullable = false)
    private UUID tenantId;

    @Column(name = "tool_name", nullable = false, length = 128)
    private String toolName;

    @Column(columnDefinition = "TEXT")
    private String description;

    @Column(nullable = false, length = 10)
    private String access;   // READ | WRITE

    @Column(columnDefinition = "jsonb")
    @JdbcTypeCode(SqlTypes.JSON)
    private String endpoint;

    @Column(name = "is_active", nullable = false)
    private Boolean isActive;

    @Column(name = "created_at", nullable = false, updatable = false)
    private LocalDateTime createdAt;

    @PrePersist
    protected void onCreate() {
        createdAt = LocalDateTime.now();
        if (isActive == null) isActive = true;
    }
}
