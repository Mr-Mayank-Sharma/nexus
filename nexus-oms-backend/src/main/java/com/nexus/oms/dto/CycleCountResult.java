package com.nexus.oms.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;

/**
 * T-08: Result of a serialized cycle count — reconciles a full-store scan
 * against the EPC registry.
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class CycleCountResult {

    private int expected;       // EPCs expected at the location
    private int found;          // EPCs found in the scan
    private int missing;        // expected but not found
    private int newEpcs;        // found but never received
    private int damaged;        // found in DAMAGED status (counted separately)
    private int onHold;         // found in ON_HOLD status (counted separately)
    private List<String> missingEpcs;
    private List<String> newEpcsList;
}
