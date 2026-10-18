package com.nexus.oms.jobs;

import com.fasterxml.jackson.annotation.JsonIgnore;
import jakarta.persistence.*;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.time.LocalDateTime;
import java.util.UUID;

@Entity
@Table(name = "nx_job_runs")
@Getter
@Setter
@NoArgsConstructor
public class NxJobRun {
    public static final String RUNNING = "RUNNING", COMPLETED = "COMPLETED", PARTIAL = "PARTIAL", FAILED = "FAILED";

    @Id
    private UUID id;
    @Column(name = "tenant_id", nullable = false)
    private UUID tenantId;
    @Column(name = "job_id", nullable = false)
    private UUID jobId;
    @Column(nullable = false, length = 20)
    private String status = RUNNING;
    @Column(name = "trigger_kind", nullable = false, length = 12)
    private String triggerKind;
    @Column(name = "triggered_by", length = 120)
    private String triggeredBy;
    @Column(name = "started_at", nullable = false)
    private LocalDateTime startedAt = LocalDateTime.now();
    @Column(name = "finished_at")
    private LocalDateTime finishedAt;
    @Column(nullable = false)
    private Integer total = 0;
    @Column(nullable = false)
    private Integer succeeded = 0;
    @Column(nullable = false)
    private Integer failed = 0;
    @Column(name = "issue_count", nullable = false)
    private Integer issueCount = 0;
    @Column(columnDefinition = "TEXT")
    private String message;
    @Column(name = "log_text", columnDefinition = "TEXT")
    private String logText;
    @Column(name = "output_name", length = 200)
    private String outputName;
    /** The output file's text. Kept out of list responses; downloaded through its own endpoint. */
    @JsonIgnore
    @Column(name = "output_csv", columnDefinition = "TEXT")
    private String outputCsv;

    public boolean getHasOutput() {
        return outputCsv != null && !outputCsv.isEmpty();
    }
}
