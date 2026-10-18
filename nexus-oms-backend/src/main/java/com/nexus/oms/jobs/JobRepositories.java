package com.nexus.oms.jobs;

import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

interface NxJobRepository extends JpaRepository<NxJob, UUID> {
    List<NxJob> findByTenantIdOrderByNameAsc(UUID tenantId);
    Optional<NxJob> findByTenantIdAndName(UUID tenantId, String name);
    List<NxJob> findByEnabledTrueAndNextRunAtLessThanEqual(LocalDateTime now);
}

interface NxJobRunRepository extends JpaRepository<NxJobRun, UUID> {
    List<NxJobRun> findByTenantIdAndJobIdOrderByStartedAtDesc(UUID tenantId, UUID jobId, Pageable page);
    Optional<NxJobRun> findFirstByJobIdAndStatus(UUID jobId, String status);
    List<NxJobRun> findByStatusAndStartedAtBefore(String status, LocalDateTime before);
    @Modifying
    @Query("DELETE FROM NxJobRun r WHERE r.jobId = :jobId")
    void deleteByJobId(@Param("jobId") UUID jobId);
}

interface NxJobRunIssueRepository extends JpaRepository<NxJobRunIssue, UUID> {
    List<NxJobRunIssue> findByTenantIdAndRunId(UUID tenantId, UUID runId, Pageable page);
    long countByRunId(UUID runId);
    @Modifying
    @Query("DELETE FROM NxJobRunIssue i WHERE i.runId IN (SELECT r.id FROM NxJobRun r WHERE r.jobId = :jobId)")
    void deleteByJobId(@Param("jobId") UUID jobId);
}
