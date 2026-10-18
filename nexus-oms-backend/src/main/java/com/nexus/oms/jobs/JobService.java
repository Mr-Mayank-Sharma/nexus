package com.nexus.oms.jobs;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.nexus.oms.entity.NxIntegrationStore;
import com.nexus.oms.exception.BadRequestException;
import com.nexus.oms.exception.ResourceNotFoundException;
import com.nexus.oms.security.TenantContext;
import com.nexus.oms.service.IntegrationStoreService;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.util.*;
import java.util.concurrent.Executor;
import java.util.concurrent.RejectedExecutionException;
import java.util.function.Function;
import java.util.stream.Collectors;

/**
 * Jobs a customer defines and schedules. A job is a type, its parameters and an optional schedule. Creating a job
 * never runs it, and a new job starts switched off, because some job types change stock.
 */
@Service
public class JobService {

    static final int STALE_AFTER_HOURS = 3;

    private final NxJobRepository jobs;
    private final NxJobRunRepository runs;
    private final NxJobRunIssueRepository issues;
    private final Map<String, JobHandler> handlers;
    private final IntegrationStoreService stores;
    private final ObjectMapper mapper;
    private final Executor executor;
    private final JobRunner runner;

    public JobService(NxJobRepository jobs, NxJobRunRepository runs, NxJobRunIssueRepository issues,
                      List<JobHandler> handlers, IntegrationStoreService stores, ObjectMapper mapper,
                      @Qualifier("jobExecutor") Executor executor, JobRunner runner) {
        this.jobs = jobs;
        this.runs = runs;
        this.issues = issues;
        this.handlers = handlers.stream().collect(Collectors.toMap(JobHandler::type, Function.identity()));
        this.stores = stores;
        this.mapper = mapper;
        this.executor = executor;
        this.runner = runner;
    }

    public record TypeView(String type, String label, String description, List<JobHandler.Param> params) {}

    public List<TypeView> types() {
        return handlers.values().stream().sorted(Comparator.comparing(JobHandler::label))
                .map(h -> new TypeView(h.type(), h.label(), h.description(), h.params())).toList();
    }

    public List<NxJob> list() {
        return jobs.findByTenantIdOrderByNameAsc(TenantContext.getCurrentTenantId());
    }

    public NxJob get(UUID id) {
        return jobs.findById(id).filter(j -> TenantContext.getCurrentTenantId().equals(j.getTenantId()))
                .orElseThrow(() -> new ResourceNotFoundException("Job", id));
    }

    public Map<String, Object> paramsOf(NxJob job) {
        try {
            return mapper.readValue(job.getParams(), new TypeReference<Map<String, Object>>() {});
        } catch (Exception e) {
            return Map.of();
        }
    }

    public String labelOf(String type) {
        JobHandler h = handlers.get(type);
        return h == null ? type : h.label();
    }

    @Transactional
    public NxJob create(String name, String type, Map<String, Object> params, String cron, String timezone) {
        UUID tenant = TenantContext.getCurrentTenantId();
        String cleanName = cleanName(name);
        if (jobs.findByTenantIdAndName(tenant, cleanName).isPresent()) {
            throw new BadRequestException("A job named '" + cleanName + "' already exists.");
        }
        NxJob job = new NxJob();
        job.setId(UUID.randomUUID());
        job.setTenantId(tenant);
        job.setCreatedBy(TenantContext.getCurrentUsername());
        apply(job, cleanName, type, params, cron, timezone);
        return jobs.save(job);
    }

    @Transactional
    public NxJob update(UUID id, String name, String type, Map<String, Object> params, String cron, String timezone) {
        NxJob job = get(id);
        String cleanName = cleanName(name);
        jobs.findByTenantIdAndName(job.getTenantId(), cleanName).filter(j -> !j.getId().equals(id)).ifPresent(j -> {
            throw new BadRequestException("A job named '" + cleanName + "' already exists.");
        });
        apply(job, cleanName, type, params, cron, timezone);
        return jobs.save(job);
    }

    private void apply(NxJob job, String name, String type, Map<String, Object> params, String cron, String timezone) {
        JobHandler handler = handlers.get(type);
        if (handler == null) throw new BadRequestException("Unknown job type '" + type + "'.");
        Map<String, Object> clean = validateParams(handler, params);
        JobSchedule.validate(cron, timezone);
        job.setName(name);
        job.setJobType(type);
        try {
            job.setParams(mapper.writeValueAsString(clean));
        } catch (Exception e) {
            throw new BadRequestException("The parameters could not be saved.");
        }
        job.setCron(cron == null || cron.isBlank() ? null : cron.trim());
        job.setTimezone(timezone == null || timezone.isBlank() ? "UTC" : timezone.trim());
        job.setUpdatedAt(LocalDateTime.now());
        job.setNextRunAt(Boolean.TRUE.equals(job.getEnabled()) ? JobSchedule.next(job.getCron(), job.getTimezone(), nowUtc()) : null);
    }

    private Map<String, Object> validateParams(JobHandler handler, Map<String, Object> given) {
        Map<String, Object> in = given == null ? Map.of() : given;
        Set<String> known = handler.params().stream().map(JobHandler.Param::name).collect(Collectors.toSet());
        for (String key : in.keySet()) {
            if (!known.contains(key)) throw new BadRequestException("'" + key + "' is not a setting of " + handler.label() + ".");
        }
        Map<String, Object> out = new LinkedHashMap<>();
        for (JobHandler.Param p : handler.params()) {
            Object v = in.get(p.name());
            boolean blank = v == null || String.valueOf(v).isBlank();
            if (blank) {
                if (p.required()) throw new BadRequestException("'" + p.label() + "' is required for " + handler.label() + ".");
                continue;
            }
            switch (p.kind()) {
                case "BOOLEAN" -> out.put(p.name(), v instanceof Boolean ? v : Boolean.parseBoolean(String.valueOf(v)));
                case "STORE" -> {
                    UUID id;
                    try {
                        id = UUID.fromString(String.valueOf(v).trim());
                    } catch (IllegalArgumentException e) {
                        throw new BadRequestException("'" + p.label() + "' must be a store connection.");
                    }
                    NxIntegrationStore store = stores.getStore(id);
                    if (!TenantContext.getCurrentTenantId().equals(store.getTenantId())) {
                        throw new BadRequestException("'" + p.label() + "' must be one of your store connections.");
                    }
                    out.put(p.name(), id.toString());
                }
                default -> out.put(p.name(), String.valueOf(v).trim());
            }
        }
        return out;
    }

    private static String cleanName(String name) {
        if (name == null || name.isBlank()) throw new BadRequestException("A job needs a name.");
        String n = name.trim();
        if (n.length() > 120) throw new BadRequestException("A job name may be at most 120 characters.");
        return n;
    }

    @Transactional
    public NxJob setEnabled(UUID id, boolean enabled) {
        NxJob job = get(id);
        if (enabled && (job.getCron() == null || job.getCron().isBlank())) {
            throw new BadRequestException("Give the job a schedule before switching it on. Without one it only runs when you press Run now.");
        }
        job.setEnabled(enabled);
        job.setNextRunAt(enabled ? JobSchedule.next(job.getCron(), job.getTimezone(), nowUtc()) : null);
        job.setUpdatedAt(LocalDateTime.now());
        return jobs.save(job);
    }

    @Transactional
    public void delete(UUID id) {
        NxJob job = get(id);
        if (runs.findFirstByJobIdAndStatus(id, NxJobRun.RUNNING).isPresent()) {
            throw new BadRequestException("This job is running now. Delete it when the run has finished.");
        }
        issues.deleteByJobId(id);
        runs.deleteByJobId(id);
        jobs.delete(job);
    }

    /** Starts a run in the background and returns it at once, still RUNNING. */
    public NxJobRun runNow(UUID id) {
        return start(get(id), "MANUAL", TenantContext.getCurrentUsername());
    }

    /** Called by the scheduler for a job that is due. */
    NxJobRun runScheduled(NxJob job) {
        job.setNextRunAt(JobSchedule.next(job.getCron(), job.getTimezone(), nowUtc()));
        jobs.save(job);
        if (runs.findFirstByJobIdAndStatus(job.getId(), NxJobRun.RUNNING).isPresent()) return null;   // the last run is still going
        return start(job, "SCHEDULE", "schedule");
    }

    private NxJobRun start(NxJob job, String trigger, String by) {
        if (runs.findFirstByJobIdAndStatus(job.getId(), NxJobRun.RUNNING).isPresent()) {
            throw new BadRequestException("This job is already running. Wait for it to finish.");
        }
        NxJobRun run = new NxJobRun();
        run.setId(UUID.randomUUID());
        run.setTenantId(job.getTenantId());
        run.setJobId(job.getId());
        run.setTriggerKind(trigger);
        run.setTriggeredBy(by);
        run.setStartedAt(LocalDateTime.now());
        NxJobRun saved = runs.save(run);
        try {
            executor.execute(() -> runner.execute(saved.getId()));
        } catch (RejectedExecutionException busy) {
            saved.setStatus(NxJobRun.FAILED);
            saved.setMessage("Too many jobs are running; try again in a minute.");
            saved.setFinishedAt(LocalDateTime.now());
            runs.save(saved);
            throw new BadRequestException("Too many jobs are running; try again in a minute.");
        }
        return saved;
    }

    public List<NxJobRun> runsOf(UUID jobId, int limit) {
        NxJob job = get(jobId);
        return runs.findByTenantIdAndJobIdOrderByStartedAtDesc(job.getTenantId(), jobId, PageRequest.of(0, Math.min(Math.max(limit, 1), 100)));
    }

    public NxJobRun run(UUID runId) {
        return runs.findById(runId).filter(r -> TenantContext.getCurrentTenantId().equals(r.getTenantId()))
                .orElseThrow(() -> new ResourceNotFoundException("Job run", runId));
    }

    public List<NxJobRunIssue> issuesOf(UUID runId, int limit) {
        NxJobRun run = run(runId);
        return issues.findByTenantIdAndRunId(run.getTenantId(), runId, PageRequest.of(0, Math.min(Math.max(limit, 1), 10_000)));
    }

    public String errorsCsv(UUID runId) {
        return JobCsv.errors(issuesOf(runId, 10_000));
    }

    /** A run left RUNNING by a crash or restart would block its job forever; close it out. */
    @Transactional
    public int failStaleRuns() {
        List<NxJobRun> stale = runs.findByStatusAndStartedAtBefore(NxJobRun.RUNNING, LocalDateTime.now().minusHours(STALE_AFTER_HOURS));
        for (NxJobRun r : stale) {
            r.setStatus(NxJobRun.FAILED);
            r.setFinishedAt(LocalDateTime.now());
            r.setMessage("Interrupted: the run never reported back (the service may have restarted).");
            runs.save(r);
        }
        return stale.size();
    }

    List<NxJob> due() {
        return jobs.findByEnabledTrueAndNextRunAtLessThanEqual(nowUtc());
    }

    static LocalDateTime nowUtc() {
        return LocalDateTime.now(ZoneOffset.UTC);
    }
}
