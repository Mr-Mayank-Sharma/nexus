package com.nexus.oms.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import java.util.UUID;

@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class SyncResult {
    private UUID syncLogId;
    private String syncType;
    private String status;
    private int itemsProcessed;
    private int itemsSucceeded;
    private int itemsFailed;
    private String message;
}
