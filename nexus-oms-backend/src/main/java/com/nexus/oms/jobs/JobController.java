package com.nexus.oms.jobs;

import com.nexus.oms.dto.ApiResponse;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.nio.charset.StandardCharsets;
import java.util.*;

/** Create, schedule, run and inspect jobs; download what a run produced and the rows that failed, with the reason. */
@RestController
@RequestMapping("/jobs")
public class JobController {

    private final JobService service;

    public JobController(JobService service) {
        this.service = service;
    }

    public record JobRequest(String name, String type, Map<String, Object> params, String cron, String timezone) {}

    public record JobView(UUID id, String name, String type, String typeLabel, Map<String, Object> params, String cron,
                          String timezone, boolean enabled, java.time.LocalDateTime nextRunAt,
                          java.time.LocalDateTime lastRunAt, String lastStatus, String createdBy) {}

    private JobView view(NxJob j) {
        return new JobView(j.getId(), j.getName(), j.getJobType(), service.labelOf(j.getJobType()), service.paramsOf(j),
                j.getCron(), j.getTimezone(), Boolean.TRUE.equals(j.getEnabled()), j.getNextRunAt(), j.getLastRunAt(),
                j.getLastStatus(), j.getCreatedBy());
    }

    @GetMapping("/types")
    public ResponseEntity<ApiResponse<List<JobService.TypeView>>> types() {
        return ResponseEntity.ok(ApiResponse.success(service.types()));
    }

    @GetMapping
    public ResponseEntity<ApiResponse<List<JobView>>> list() {
        return ResponseEntity.ok(ApiResponse.success(service.list().stream().map(this::view).toList()));
    }

    @PostMapping
    public ResponseEntity<ApiResponse<JobView>> create(@RequestBody JobRequest r) {
        return ResponseEntity.ok(ApiResponse.success(view(service.create(r.name(), r.type(), r.params(), r.cron(), r.timezone())), "Job created. It is switched off until you enable it."));
    }

    @PutMapping("/{id}")
    public ResponseEntity<ApiResponse<JobView>> update(@PathVariable UUID id, @RequestBody JobRequest r) {
        return ResponseEntity.ok(ApiResponse.success(view(service.update(id, r.name(), r.type(), r.params(), r.cron(), r.timezone())), "Job saved"));
    }

    @DeleteMapping("/{id}")
    public ResponseEntity<ApiResponse<Void>> delete(@PathVariable UUID id) {
        service.delete(id);
        return ResponseEntity.ok(ApiResponse.success(null, "Job deleted"));
    }

    @PostMapping("/{id}/enable")
    public ResponseEntity<ApiResponse<JobView>> enable(@PathVariable UUID id, @RequestParam boolean enabled) {
        return ResponseEntity.ok(ApiResponse.success(view(service.setEnabled(id, enabled)), enabled ? "Job switched on" : "Job switched off"));
    }

    @PostMapping("/{id}/run")
    public ResponseEntity<ApiResponse<NxJobRun>> run(@PathVariable UUID id) {
        return ResponseEntity.ok(ApiResponse.success(service.runNow(id), "Started. Refresh to see the result."));
    }

    @GetMapping("/{id}/runs")
    public ResponseEntity<ApiResponse<List<NxJobRun>>> runs(@PathVariable UUID id, @RequestParam(defaultValue = "30") int limit) {
        return ResponseEntity.ok(ApiResponse.success(service.runsOf(id, limit)));
    }

    @GetMapping("/runs/{runId}")
    public ResponseEntity<ApiResponse<NxJobRun>> runDetail(@PathVariable UUID runId) {
        return ResponseEntity.ok(ApiResponse.success(service.run(runId)));
    }

    /** The log text is large, so it has its own call. */
    @GetMapping("/runs/{runId}/log")
    public ResponseEntity<String> log(@PathVariable UUID runId) {
        String text = service.run(runId).getLogText();
        return ResponseEntity.ok().contentType(MediaType.TEXT_PLAIN).body(text == null ? "" : text);
    }

    @GetMapping("/runs/{runId}/issues")
    public ResponseEntity<ApiResponse<List<NxJobRunIssue>>> issues(@PathVariable UUID runId, @RequestParam(defaultValue = "200") int limit) {
        return ResponseEntity.ok(ApiResponse.success(service.issuesOf(runId, limit)));
    }

    @GetMapping("/runs/{runId}/errors.csv")
    public ResponseEntity<byte[]> errorsCsv(@PathVariable UUID runId) {
        return csv("run-" + runId.toString().substring(0, 8) + "-errors.csv", service.errorsCsv(runId));
    }

    @GetMapping("/runs/{runId}/output.csv")
    public ResponseEntity<byte[]> outputCsv(@PathVariable UUID runId) {
        NxJobRun run = service.run(runId);
        if (!run.getHasOutput()) {
            return ResponseEntity.status(404).body("This run produced no file.".getBytes(StandardCharsets.UTF_8));
        }
        return csv(run.getOutputName() == null ? "output.csv" : run.getOutputName(), run.getOutputCsv());
    }

    private static ResponseEntity<byte[]> csv(String filename, String body) {
        return ResponseEntity.ok()
                .header(HttpHeaders.CONTENT_DISPOSITION, "attachment; filename=\"" + filename.replaceAll("[^A-Za-z0-9._-]", "_") + "\"")
                .contentType(MediaType.parseMediaType("text/csv;charset=UTF-8"))
                .body(body.getBytes(StandardCharsets.UTF_8));
    }
}
