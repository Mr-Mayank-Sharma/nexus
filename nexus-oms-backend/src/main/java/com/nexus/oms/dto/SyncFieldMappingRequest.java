package com.nexus.oms.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.Map;
import java.util.UUID;

/**
 * T-13: Request to register/update a field-level sync mapping.
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class SyncFieldMappingRequest {

    private String entityType;
    private String fieldName;
    private String direction;   // INBOUND | OUTBOUND
    private String winner;      // LOCAL | REMOTE
    private Boolean isActive;
}
