package com.nexus.oms.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;

/**
 * T-08: Result of ingesting a batch of EPC reads into a scan session.
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class RfidScanResult {

    private int totalReads;
    private int accepted;       // new EPCs accepted into the session
    private int duplicates;     // EPCs already seen in this session (deduped)
    private int rejected;       // foreign / invalid tags rejected
    private List<String> rejectedEpcs;
}
