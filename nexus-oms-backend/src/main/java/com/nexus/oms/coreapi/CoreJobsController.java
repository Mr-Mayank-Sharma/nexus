package com.nexus.oms.coreapi;

import com.nexus.core.job.JobService;
import com.nexus.core.job.MessageService;
import com.nexus.oms.dto.ApiResponse;
import com.nexus.oms.security.TenantContext;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.Map;

/**
 * Jobs and messages on the core model (doc 26, W10). Jobs: list, history, run now, pause, change schedule. Messages:
 * other systems (a WMS, doc 27) post a message by type with an {@code Idempotency-Key} header; it is stored at once
 * (202) and consumed by the core_messages job, and the same key again returns the first message (200). Admin only.
 */
@RestController
@RequestMapping("/core")
@Tag(name = "Core jobs and messages", description = "Scheduled jobs and the message queue between Nexus and other systems")
public class CoreJobsController {

    static final int MAX_RUNS = 200;

    private final JobService jobService;
    private final MessageService messageService;

    public CoreJobsController(JobService jobService, MessageService messageService) {
        this.jobService = jobService;
        this.messageService = messageService;
    }

    // ── jobs ──

    @Operation(summary = "Jobs with their schedule, pause state and last run")
    @GetMapping("/jobs")
    public ResponseEntity<ApiResponse<List<JobService.JobView>>> list() {
        return ResponseEntity.ok(ApiResponse.success(jobService.jobs(), "Jobs"));
    }

    @Operation(summary = "A job's latest runs")
    @GetMapping("/jobs/{jobName}/runs")
    public ResponseEntity<ApiResponse<List<JobService.Run>>> runs(@PathVariable String jobName, @RequestParam(defaultValue = "20") int limit) {
        return ResponseEntity.ok(ApiResponse.success(jobService.runs(jobName, Math.min(limit, MAX_RUNS)), "Runs"));
    }

    @Operation(summary = "Run a job now")
    @PostMapping("/jobs/{jobName}/run")
    public ResponseEntity<ApiResponse<JobService.Run>> run(@PathVariable String jobName) {
        return jobService.runJob(jobName, currentUser())
                .map(run -> ResponseEntity.ok(ApiResponse.success(run, run.hasError() ? "The job failed" : "Job finished")))
                .orElseGet(() -> ResponseEntity.status(HttpStatus.CONFLICT).body(ApiResponse.error("The job is already running")));
    }

    @Operation(summary = "Pause or resume a job")
    @PostMapping("/jobs/{jobName}/paused")
    public ResponseEntity<ApiResponse<Void>> pause(@PathVariable String jobName, @RequestBody Map<String, Boolean> body) {
        boolean paused = Boolean.TRUE.equals(body.get("paused"));
        jobService.setPaused(jobName, paused);
        return ResponseEntity.ok(ApiResponse.success(null, paused ? "Paused" : "Resumed"));
    }

    @Operation(summary = "Change when a job runs (six-field cron: second minute hour day month weekday)")
    @PostMapping("/jobs/{jobName}/schedule")
    public ResponseEntity<ApiResponse<Void>> schedule(@PathVariable String jobName, @RequestBody Map<String, String> body) {
        jobService.setSchedule(jobName, body.get("cronExpression"));
        return ResponseEntity.ok(ApiResponse.success(null, "Schedule changed"));
    }

    // ── messages ──

    @Operation(summary = "Receive a message from another system (stored at once, consumed by the core_messages job)")
    @PostMapping("/messages/{typeId}")
    public ResponseEntity<ApiResponse<MessageService.Received>> receive(@PathVariable String typeId, @RequestBody String text,
                                                                        @RequestHeader(value = "Idempotency-Key", required = false) String idempotencyKey,
                                                                        @RequestHeader(value = "X-Remote-Message-Id", required = false) String remoteMessageId) {
        MessageService.Received received = messageService.receive(typeId, text, idempotencyKey, remoteMessageId, null);
        HttpStatus status = received.duplicate() ? HttpStatus.OK : HttpStatus.ACCEPTED;
        String message = received.duplicate() ? "Already received" : "Received; it will be processed shortly";
        return ResponseEntity.status(status).body(ApiResponse.success(received, message));
    }

    @Operation(summary = "A message's status and its last error")
    @GetMapping("/messages/id/{systemMessageId}")
    public ResponseEntity<ApiResponse<MessageService.MessageView>> message(@PathVariable String systemMessageId) {
        return messageService.view(systemMessageId)
                .map(view -> ResponseEntity.ok(ApiResponse.success(view, "Message")))
                .orElseGet(() -> ResponseEntity.status(HttpStatus.NOT_FOUND).body(ApiResponse.error("No message " + systemMessageId)));
    }

    /** The signed-in user, or null outside a request. */
    private static String currentUser() {
        try {
            return TenantContext.getCurrentUsername();
        } catch (RuntimeException noUser) {
            return null;
        }
    }
}
