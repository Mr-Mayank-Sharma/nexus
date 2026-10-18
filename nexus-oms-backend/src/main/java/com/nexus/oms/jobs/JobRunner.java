package com.nexus.oms.jobs;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.nexus.oms.security.TenantContext;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.util.*;
import java.util.function.Function;
import java.util.stream.Collectors;

/** Executes one run: sets the tenant, calls the job's handler, and records counts, log, output and the failed rows. */
@Service
public class JobRunner {

    private static final Logger log = LoggerFactory.getLogger(JobRunner.class);
    static final int MAX_ISSUES = 10_000;
    static final int MAX_OUTPUT_CHARS = 5_000_000;
    static final int MAX_LOG_CHARS = 100_000;

    private final NxJobRepository jobs;
    private final NxJobRunRepository runs;
    private final NxJobRunIssueRepository issues;
    private final Map<String, JobHandler> handlers;
    private final ObjectMapper mapper;

    public JobRunner(NxJobRepository jobs, NxJobRunRepository runs, NxJobRunIssueRepository issues,
                     List<JobHandler> handlers, ObjectMapper mapper) {
        this.jobs = jobs;
        this.runs = runs;
        this.issues = issues;
        this.handlers = handlers.stream().collect(Collectors.toMap(JobHandler::type, Function.identity()));
        this.mapper = mapper;
    }

    public void execute(UUID runId) {
        NxJobRun run = runs.findById(runId).orElse(null);
        if (run == null) return;
        NxJob job = jobs.findById(run.getJobId()).orElse(null);
        TenantContext.setCurrentTenantId(run.getTenantId());
        JobHandler.Context ctx = null;
        try {
            if (job == null) throw new IllegalStateException("The job no longer exists.");
            JobHandler handler = handlers.get(job.getJobType());
            if (handler == null) throw new IllegalStateException("Unknown job type " + job.getJobType());
            Map<String, Object> params = mapper.readValue(job.getParams(), new TypeReference<Map<String, Object>>() {});
            ctx = new JobHandler.Context(run.getTenantId(), params);
            ctx.log("Started " + handler.label());
            JobHandler.Outcome outcome = handler.run(ctx);
            finish(run, job, ctx, outcome, null);
        } catch (Throwable t) {
            log.warn("Job run {} failed: {}", runId, t.toString());
            finish(run, job, ctx, null, t);
        } finally {
            TenantContext.clear();
        }
    }

    private void finish(NxJobRun run, NxJob job, JobHandler.Context ctx, JobHandler.Outcome o, Throwable error) {
        run.setFinishedAt(LocalDateTime.now());
        if (error != null) {
            run.setStatus(NxJobRun.FAILED);
            run.setMessage(error.getMessage() != null ? error.getMessage() : error.getClass().getSimpleName());
            if (ctx != null) ctx.log("Failed: " + run.getMessage());
        } else {
            run.setTotal(o.total());
            run.setSucceeded(o.succeeded());
            run.setFailed(o.failed());
            run.setMessage(o.message());
            run.setStatus(o.failed() > 0 ? (o.succeeded() > 0 ? NxJobRun.PARTIAL : NxJobRun.FAILED) : NxJobRun.COMPLETED);
            int saved = 0;
            List<NxJobRunIssue> rows = new ArrayList<>();
            for (JobHandler.Issue i : o.issues()) {
                if (saved++ >= MAX_ISSUES) break;
                NxJobRunIssue row = new NxJobRunIssue();
                row.setId(UUID.randomUUID());
                row.setTenantId(run.getTenantId());
                row.setRunId(run.getId());
                row.setRowRef(i.rowRef());
                row.setReason(i.reason());
                row.setRaw(i.raw());
                rows.add(row);
            }
            if (!rows.isEmpty()) issues.saveAll(rows);
            run.setIssueCount(o.issues().size());
            if (o.outputCsv != null) {
                String csv = o.outputCsv;
                if (csv.length() > MAX_OUTPUT_CHARS) {
                    csv = csv.substring(0, MAX_OUTPUT_CHARS);
                    run.setMessage((run.getMessage() == null ? "" : run.getMessage() + " ") + "The output file was cut at 5 million characters.");
                }
                run.setOutputName(o.outputName);
                run.setOutputCsv(csv);
            }
            if (o.issues().size() > MAX_ISSUES) ctx.log("Only the first " + MAX_ISSUES + " of " + o.issues().size() + " rows are kept.");
            ctx.log("Finished: " + run.getStatus() + " (" + o.succeeded() + " ok, " + o.failed() + " failed)");
        }
        if (ctx != null) {
            String text = String.join("\n", ctx.lines());
            run.setLogText(text.length() > MAX_LOG_CHARS ? text.substring(0, MAX_LOG_CHARS) : text);
        }
        runs.save(run);
        if (job != null) {
            job.setLastRunAt(run.getFinishedAt());
            job.setLastStatus(run.getStatus());
            jobs.save(job);
        }
    }
}
