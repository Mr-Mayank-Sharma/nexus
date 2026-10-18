package com.nexus.oms.jobs;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.nexus.oms.entity.NxIntegrationStore;
import com.nexus.oms.exception.BadRequestException;
import com.nexus.oms.security.TenantContext;
import com.nexus.oms.service.IntegrationStoreService;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

import java.time.LocalDateTime;
import java.util.*;
import java.util.concurrent.RejectedExecutionException;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

class JobServiceTest {

    private final NxJobRepository jobs = mock(NxJobRepository.class);
    private final NxJobRunRepository runs = mock(NxJobRunRepository.class);
    private final NxJobRunIssueRepository issues = mock(NxJobRunIssueRepository.class);
    private final IntegrationStoreService stores = mock(IntegrationStoreService.class);
    private final JobRunner runner = mock(JobRunner.class);
    private final UUID tenant = UUID.randomUUID();
    private final UUID storeId = UUID.randomUUID();
    private JobService service;
    private final List<Runnable> queued = new ArrayList<>();

    @BeforeEach
    void setUp() {
        TenantContext.setCurrentTenantId(tenant);
        service = new JobService(jobs, runs, issues, List.of(new FakeHandler()), stores, new ObjectMapper(), queued::add, runner);
        when(stores.getStore(storeId)).thenReturn(NxIntegrationStore.builder().id(storeId).tenantId(tenant).storeCode("s").build());
        when(jobs.save(any())).thenAnswer(inv -> inv.getArgument(0));
        when(runs.save(any())).thenAnswer(inv -> inv.getArgument(0));
        when(jobs.findByTenantIdAndName(any(), any())).thenReturn(Optional.empty());
        when(runs.findFirstByJobIdAndStatus(any(), any())).thenReturn(Optional.empty());
    }

    @AfterEach
    void tearDown() {
        TenantContext.clear();
    }

    static class FakeHandler implements JobHandler {
        public String type() { return "FAKE"; }
        public String label() { return "Fake"; }
        public String description() { return "d"; }
        public List<Param> params() {
            return List.of(new Param("storeId", "Store connection", "STORE", true, ""), new Param("apply", "Write", "BOOLEAN", false, ""));
        }
        public Outcome run(Context ctx) { return Outcome.of(1, 1, 0, "ok"); }
    }

    private Map<String, Object> goodParams() {
        return new HashMap<>(Map.of("storeId", storeId.toString(), "apply", "true"));
    }

    private NxJob existing(boolean enabled, String cron) {
        NxJob j = new NxJob();
        j.setId(UUID.randomUUID());
        j.setTenantId(tenant);
        j.setName("n");
        j.setJobType("FAKE");
        j.setCron(cron);
        j.setEnabled(enabled);
        when(jobs.findById(j.getId())).thenReturn(Optional.of(j));
        return j;
    }

    @Test
    void aNewJobIsSwitchedOff_andItsParametersAreCleaned() {
        NxJob job = service.create("  Nightly stock  ", "FAKE", goodParams(), "0 0 2 * * *", "UTC");

        assertFalse(job.getEnabled(), "creating a job must never start it");
        assertEquals("Nightly stock", job.getName());
        assertNull(job.getNextRunAt(), "a switched-off job has nothing scheduled");
        assertTrue(job.getParams().contains("\"apply\":true"), job.getParams());   // "true" became a real boolean
        assertEquals(tenant, job.getTenantId());
    }

    @Test
    void aMissingRequiredParameterIsNamed() {
        BadRequestException e = assertThrows(BadRequestException.class,
                () -> service.create("x", "FAKE", new HashMap<>(), null, "UTC"));
        assertTrue(e.getMessage().contains("Store connection"), e.getMessage());
    }

    @Test
    void anUnknownSettingOrTypeIsRefused() {
        Map<String, Object> p = goodParams();
        p.put("bogus", "1");
        assertThrows(BadRequestException.class, () -> service.create("x", "FAKE", p, null, "UTC"));
        assertThrows(BadRequestException.class, () -> service.create("x", "NOPE", goodParams(), null, "UTC"));
    }

    @Test
    void aStoreFromAnotherTenantIsRefused() {
        UUID other = UUID.randomUUID();
        when(stores.getStore(other)).thenReturn(NxIntegrationStore.builder().id(other).tenantId(UUID.randomUUID()).storeCode("o").build());

        assertThrows(BadRequestException.class, () -> service.create("x", "FAKE", Map.of("storeId", other.toString()), null, "UTC"));
    }

    @Test
    void aDuplicateNameIsRefused() {
        when(jobs.findByTenantIdAndName(tenant, "Dup")).thenReturn(Optional.of(new NxJob()));
        assertThrows(BadRequestException.class, () -> service.create("Dup", "FAKE", goodParams(), null, "UTC"));
    }

    @Test
    void aJobCannotBeSwitchedOnWithoutASchedule() {
        NxJob j = existing(false, null);
        BadRequestException e = assertThrows(BadRequestException.class, () -> service.setEnabled(j.getId(), true));
        assertTrue(e.getMessage().contains("Run now"), e.getMessage());
    }

    @Test
    void switchingOnSchedulesTheNextRun_andSwitchingOffClearsIt() {
        NxJob j = existing(false, "0 0 6 * * *");

        NxJob on = service.setEnabled(j.getId(), true);
        assertTrue(on.getEnabled());
        assertNotNull(on.getNextRunAt());
        assertTrue(on.getNextRunAt().isAfter(LocalDateTime.now(java.time.ZoneOffset.UTC).minusMinutes(1)));

        assertNull(service.setEnabled(j.getId(), false).getNextRunAt());
    }

    @Test
    void runNowRecordsARunAndHandsItToTheBackgroundPool() {
        NxJob j = existing(false, null);

        NxJobRun run = service.runNow(j.getId());

        assertEquals(NxJobRun.RUNNING, run.getStatus());
        assertEquals("MANUAL", run.getTriggerKind());
        assertEquals(1, queued.size(), "the work must be queued, not run on the request thread");
        queued.get(0).run();
        verify(runner).execute(run.getId());
    }

    @Test
    void aJobThatIsAlreadyRunningCannotBeStartedTwice() {
        NxJob j = existing(false, null);
        when(runs.findFirstByJobIdAndStatus(j.getId(), NxJobRun.RUNNING)).thenReturn(Optional.of(new NxJobRun()));

        assertThrows(BadRequestException.class, () -> service.runNow(j.getId()));
        assertTrue(queued.isEmpty());
    }

    @Test
    void aBusyPoolMarksTheRunFailedInsteadOfLeavingItRunningForever() {
        service = new JobService(jobs, runs, issues, List.of(new FakeHandler()), stores, new ObjectMapper(),
                r -> { throw new RejectedExecutionException(); }, runner);
        NxJob j = existing(false, null);

        assertThrows(BadRequestException.class, () -> service.runNow(j.getId()));

        ArgumentCaptor<NxJobRun> saved = ArgumentCaptor.forClass(NxJobRun.class);
        verify(runs, atLeast(2)).save(saved.capture());
        assertEquals(NxJobRun.FAILED, saved.getValue().getStatus());
    }

    @Test
    void aRunningJobCannotBeDeleted() {
        NxJob j = existing(false, null);
        when(runs.findFirstByJobIdAndStatus(j.getId(), NxJobRun.RUNNING)).thenReturn(Optional.of(new NxJobRun()));

        assertThrows(BadRequestException.class, () -> service.delete(j.getId()));
        verify(jobs, never()).delete(any());
    }

    @Test
    void aJobFromAnotherTenantIsNotFound() {
        NxJob foreign = existing(false, null);
        foreign.setTenantId(UUID.randomUUID());
        assertThrows(com.nexus.oms.exception.ResourceNotFoundException.class, () -> service.get(foreign.getId()));
    }

    @Test
    void aScheduledRunAdvancesTheClockEvenWhenThePreviousRunIsStillGoing() {
        NxJob j = existing(true, "0 0 6 * * *");
        when(runs.findFirstByJobIdAndStatus(j.getId(), NxJobRun.RUNNING)).thenReturn(Optional.of(new NxJobRun()));

        assertNull(service.runScheduled(j), "skipped, not queued twice");
        assertNotNull(j.getNextRunAt());
        assertTrue(queued.isEmpty());
    }

    @Test
    void staleRunsAreClosedOut() {
        NxJobRun stale = new NxJobRun();
        stale.setStatus(NxJobRun.RUNNING);
        when(runs.findByStatusAndStartedAtBefore(eq(NxJobRun.RUNNING), any())).thenReturn(List.of(stale));

        assertEquals(1, service.failStaleRuns());
        assertEquals(NxJobRun.FAILED, stale.getStatus());
        assertTrue(stale.getMessage().contains("Interrupted"));
    }
}
