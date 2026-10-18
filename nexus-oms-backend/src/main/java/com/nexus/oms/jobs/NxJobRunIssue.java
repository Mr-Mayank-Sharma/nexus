package com.nexus.oms.jobs;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.util.UUID;

/** One row (or item) of a run that failed or needs attention, with the reason, for the downloadable errors file. */
@Entity
@Table(name = "nx_job_run_issues")
@Getter
@Setter
@NoArgsConstructor
public class NxJobRunIssue {
    @Id
    private UUID id;
    @Column(name = "tenant_id", nullable = false)
    private UUID tenantId;
    @Column(name = "run_id", nullable = false)
    private UUID runId;
    @Column(name = "row_ref", length = 300)
    private String rowRef;
    @Column(nullable = false, columnDefinition = "TEXT")
    private String reason;
    @Column(columnDefinition = "TEXT")
    private String raw;
}
