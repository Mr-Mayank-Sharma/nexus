package com.nexus.oms.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.Map;
import java.util.UUID;

/**
 * T-13: Request to resolve a sync conflict.
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class SyncConflictResolutionRequest {

    private String resolution;   // LOCAL_WINS | INBOUND_WINS | MERGED | MANUAL
    private Map<String, Object> mergedFields;  // for MERGED resolution
}
