package com.nexus.oms.generic;

import com.fasterxml.jackson.annotation.JsonProperty;
import com.nexus.oms.entity.SealedStringConverter;
import jakarta.persistence.*;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.time.LocalDateTime;
import java.util.UUID;

/** A system Nexus imports from or exports to, described by configuration rather than code. */
@Entity
@Table(name = "nx_generic_connections")
@Getter
@Setter
@NoArgsConstructor
public class NxGenericConnection {
    @Id
    private UUID id;
    @Column(name = "tenant_id", nullable = false)
    private UUID tenantId;
    @Column(nullable = false, length = 120)
    private String name;
    /** IMPORT or EXPORT. */
    @Column(nullable = false, length = 10)
    private String direction;
    @Column(name = "entity_type", nullable = false, length = 30)
    private String entityType;
    @Column(length = 60)
    private String channel;
    /** JSON: endpoint, auth (without the secret), paging, field mapping. See {@link GenericConfig}. */
    @Column(nullable = false, columnDefinition = "TEXT")
    private String config = "{}";
    /** Token, password or API key. Sealed at rest; accepted on write, never returned. */
    @Convert(converter = SealedStringConverter.class)
    @JsonProperty(access = JsonProperty.Access.WRITE_ONLY)
    @Column(columnDefinition = "TEXT")
    private String secret;
    private LocalDateTime watermark;
    @Column(name = "created_at", nullable = false)
    private LocalDateTime createdAt = LocalDateTime.now();
    @Column(name = "updated_at", nullable = false)
    private LocalDateTime updatedAt = LocalDateTime.now();

    @JsonProperty(access = JsonProperty.Access.READ_ONLY)
    public boolean getHasSecret() { return secret != null && !secret.isBlank(); }
}
