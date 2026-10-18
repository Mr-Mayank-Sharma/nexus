package com.nexus.oms.jobs;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.nexus.oms.exception.BadRequestException;
import com.nexus.oms.security.TenantContext;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

import java.util.*;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

class JobRunnerTest {

    private final NxJobRepository jobs = mock(NxJobRepository.class);
    private final NxJobRunRepository runs = mock(NxJobRunRepository.class);
    private final NxJobRunIssueRepository issues = mock(NxJobRunIssueRepository.class);
    private final UUID tenant = UUID.randomUUID();
    private NxJob job;
    private NxJobRun run;

    private JobRunner runnerWith(JobHandler.Outcome outcome, RuntimeException toThrow) {
        job = new NxJob();
        job.setId(UUID.randomUUID());
        job.setTenantId(tenant);
        job.setJobType("T");
        job.setParams("{\"x\":\"1\"}");
        run = new NxJobRun();
        run.setId(UUID.randomUUID());
        run.setTenantId(tenant);
        run.setJobId(job.getId());
        when(runs.findById(run.getId())).thenReturn(Optional.of(run));
        when(jobs.findById(job.getId())).thenReturn(Optional.of(job));
        JobHandler handler = new JobHandler() {
            public String type() { return "T"; }
            public String label() { return "T"; }
            public String description() { return ""; }
            public List<Param> params() { return List.of(); }
            public Outcome run(Context ctx) {
                assertEquals(tenant, TenantContext.getCurrentTenantId(), "the job must run as its own tenant");
                ctx.log("working");
                if (toThrow != null) throw toThrow;
                return outcome;
            }
        };
        return new JobRunner(jobs, runs, issues, List.of(handler), new ObjectMapper());
    }

    @Test
    void aCleanRunIsCompleted_andTheJobRemembersIt() {
        runnerWith(JobHandler.Outcome.of(10, 10, 0, "done"), null).execute(run.getId());

        assertEquals(NxJobRun.COMPLETED, run.getStatus());
        assertEquals(10, run.getSucceeded());
        assertNotNull(run.getFinishedAt());
        assertTrue(run.getLogText().contains("working"));
        assertEquals(NxJobRun.COMPLETED, job.getLastStatus());
        assertNotNull(job.getLastRunAt());
    }

    @Test
    void someFailuresMakeItPartial_andEachFailedRowIsKeptWithItsReason() {
        var outcome = JobHandler.Outcome.of(3, 2, 1, "one failed").issue("row 7", "SKU not found", "raw,data");
        runnerWith(outcome, null).execute(run.getId());

        assertEquals(NxJobRun.PARTIAL, run.getStatus());
        assertEquals(1, run.getIssueCount());
        ArgumentCaptor<List<NxJobRunIssue>> saved = ArgumentCaptor.forClass(List.class);
        verify(issues).saveAll(saved.capture());
        assertEquals("row 7", saved.getValue().get(0).getRowRef());
        assertEquals("SKU not found", saved.getValue().get(0).getReason());
        assertEquals(tenant, saved.getValue().get(0).getTenantId());
    }

    @Test
    void ifEverythingFailedTheRunIsFailed() {
        runnerWith(JobHandler.Outcome.of(2, 0, 2, "all failed").issue("a", "x", null), null).execute(run.getId());
        assertEquals(NxJobRun.FAILED, run.getStatus());
    }

    @Test
    void anExceptionFailsTheRunWithItsMessage_andDoesNotLeakOutOfTheThread() {
        runnerWith(null, new BadRequestException("NetSuite answered 401")).execute(run.getId());

        assertEquals(NxJobRun.FAILED, run.getStatus());
        assertEquals("NetSuite answered 401", run.getMessage());
        assertTrue(run.getLogText().contains("Failed: NetSuite answered 401"));
        assertEquals(NxJobRun.FAILED, job.getLastStatus());
    }

    @Test
    void anOutputFileIsKeptWithItsName() {
        runnerWith(JobHandler.Outcome.of(1, 1, 0, "ok").output("stock.csv", "a,b\r\n"), null).execute(run.getId());

        assertEquals("stock.csv", run.getOutputName());
        assertTrue(run.getHasOutput());
    }

    @Test
    void aVeryLargeOutputIsCutAndSaysSo() {
        String big = "x".repeat(JobRunner.MAX_OUTPUT_CHARS + 10);
        runnerWith(JobHandler.Outcome.of(1, 1, 0, "ok").output("big.csv", big), null).execute(run.getId());

        assertEquals(JobRunner.MAX_OUTPUT_CHARS, run.getOutputCsv().length());
        assertTrue(run.getMessage().contains("cut"));
    }

    @Test
    void theTenantIsClearedAfterwards() {
        runnerWith(JobHandler.Outcome.of(0, 0, 0, "ok"), null).execute(run.getId());
        assertNull(TenantContext.findCurrentTenantId());
    }

    @Test
    void aMissingRunOrJobDoesNothingHarmful() {
        JobRunner runner = new JobRunner(jobs, runs, issues, List.of(), new ObjectMapper());
        assertDoesNotThrow(() -> runner.execute(UUID.randomUUID()));

        NxJobRun orphan = new NxJobRun();
        orphan.setId(UUID.randomUUID());
        orphan.setTenantId(tenant);
        orphan.setJobId(UUID.randomUUID());
        when(runs.findById(orphan.getId())).thenReturn(Optional.of(orphan));
        runner.execute(orphan.getId());
        assertEquals(NxJobRun.FAILED, orphan.getStatus());
    }
}
