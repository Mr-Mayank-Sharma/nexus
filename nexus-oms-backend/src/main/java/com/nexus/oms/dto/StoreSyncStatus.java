package com.nexus.oms.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;

@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class StoreSyncStatus {

    private UUID storeId;
    private String storeCode;
    private String storeName;
    private String platform;
    private boolean connected;
    private List<SyncTypeStatus> syncTypes;
    private String lastError;

    @Data
    @NoArgsConstructor
    @AllArgsConstructor
    @Builder
    public static class SyncTypeStatus {
        private String syncType;
        private boolean enabled;
        private int intervalMinutes;
        private LocalDateTime lastSyncAt;
        private String lastSyncStatus;
        private String lastSyncMessage;
    }
}
