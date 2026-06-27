package com.nexus.oms.integration.dto;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

public class SyncResult {

    public enum Status { COMPLETED, PARTIAL, FAILED, RUNNING }

    private final String syncType;
    private final Status status;
    private final int totalProcessed;
    private final int itemsSucceeded;
    private final int itemsFailed;
    private final int itemsSkipped;
    private final List<String> errors;
    private final List<String> warnings;
    private final LocalDateTime startedAt;
    private final LocalDateTime completedAt;
    private final long durationMs;

    private SyncResult(Builder b) {
        this.syncType = b.syncType;
        this.status = b.status;
        this.totalProcessed = b.totalProcessed;
        this.itemsSucceeded = b.itemsSucceeded;
        this.itemsFailed = b.itemsFailed;
        this.itemsSkipped = b.itemsSkipped;
        this.errors = List.copyOf(b.errors);
        this.warnings = List.copyOf(b.warnings);
        this.startedAt = b.startedAt;
        this.completedAt = b.completedAt != null ? b.completedAt : LocalDateTime.now();
        this.durationMs = b.durationMs > 0 ? b.durationMs :
                (b.startedAt != null ? java.time.Duration.between(b.startedAt, this.completedAt).toMillis() : 0);
    }

    public static Builder builder() { return new Builder(); }

    public String getSyncType() { return syncType; }
    public Status getStatus() { return status; }
    public int getTotalProcessed() { return totalProcessed; }
    public int getItemsSucceeded() { return itemsSucceeded; }
    public int getItemsFailed() { return itemsFailed; }
    public int getItemsSkipped() { return itemsSkipped; }
    public List<String> getErrors() { return errors; }
    public List<String> getWarnings() { return warnings; }
    public LocalDateTime getStartedAt() { return startedAt; }
    public LocalDateTime getCompletedAt() { return completedAt; }
    public long getDurationMs() { return durationMs; }

    public static class Builder {
        private String syncType;
        private Status status = Status.COMPLETED;
        private int totalProcessed;
        private int itemsSucceeded;
        private int itemsFailed;
        private int itemsSkipped;
        private final List<String> errors = new ArrayList<>();
        private final List<String> warnings = new ArrayList<>();
        private LocalDateTime startedAt = LocalDateTime.now();
        private LocalDateTime completedAt = LocalDateTime.now();
        private long durationMs;

        public Builder syncType(String v) { this.syncType = v; return this; }
        public Builder status(Status v) { this.status = v; return this; }
        public Builder totalProcessed(int v) { this.totalProcessed = v; return this; }
        public Builder itemsSucceeded(int v) { this.itemsSucceeded = v; this.totalProcessed += v; return this; }
        public Builder itemsFailed(int v) { this.itemsFailed = v; this.totalProcessed += v; return this; }
        public Builder itemsSkipped(int v) { this.itemsSkipped = v; return this; }
        public Builder addError(String e) { this.errors.add(e); return this; }
        public Builder addWarning(String w) { this.warnings.add(w); return this; }
        public Builder startedAt(LocalDateTime v) { this.startedAt = v; return this; }
        public Builder completedAt(LocalDateTime v) { this.completedAt = v; return this; }
        public Builder durationMs(long v) { this.durationMs = v; return this; }
        public SyncResult build() { return new SyncResult(this); }
    }
}
