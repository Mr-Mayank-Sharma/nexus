package com.nexus.oms.dto.reconciliation;

import lombok.*;

import java.util.List;

@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class MatchResult {

    private MatchType matchType;
    private Resolution resolution;
    private List<FulfilledItem> acceptedItems;
    private List<FulfilledItem> remainingItems;
    private String notes;
}