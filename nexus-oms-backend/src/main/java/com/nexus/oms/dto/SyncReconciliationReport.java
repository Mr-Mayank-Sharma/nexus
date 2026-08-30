package com.nexus.oms.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;

/**
 * T-13: Reconciliation report — pending/queued/completed/failed counts
 * per sync direction, plus open conflict counts.
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class SyncReconciliationReport {

    private List<DirectionSummary> directions;
    private long openConflicts;
    private long resolvedConflicts;

    @Data
    @NoArgsConstructor
    @AllArgsConstructor
    @Builder
    public static class DirectionSummary {
        private String direction;      // INBOUND | OUTBOUND
        private long pending;
        private long queued;
        private long completed;
        private long failed;
    }
}
