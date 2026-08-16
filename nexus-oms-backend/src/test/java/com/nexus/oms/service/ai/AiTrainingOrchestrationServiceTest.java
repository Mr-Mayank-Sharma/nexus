package com.nexus.oms.service.ai;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.nexus.oms.entity.ai.AiModel;
import com.nexus.oms.entity.ai.AiModelVersion;
import com.nexus.oms.entity.ai.AiTrainingJob;
import com.nexus.oms.repository.ai.AiModelRepository;
import com.nexus.oms.repository.ai.AiModelVersionRepository;
import com.nexus.oms.repository.ai.AiTrainingJobRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class AiTrainingOrchestrationServiceTest {

    @Mock
    private AiTrainingDataService trainingDataService;
    @Mock
    private AiTrainingPipelineService pipelineService;
    @Mock
    private AiArtifactService artifactService;
    @Mock
    private AiModelRepository modelRepository;
    @Mock
    private AiModelVersionRepository versionRepository;
    @Mock
    private AiTrainingJobRepository jobRepository;
    @Mock
    private TrainingScriptExecutor scriptExecutor;

    private AiTrainingOrchestrationService service;
    private ObjectMapper objectMapper = new ObjectMapper();

    private UUID tenantId;
    private UUID jobId;
    private AiTrainingJob job;

    @BeforeEach
    void setUp() throws IOException {
        Path script = Files.createTempFile("nexus-forecast", ".py");
        service = new AiTrainingOrchestrationService(trainingDataService, pipelineService, artifactService,
                modelRepository, versionRepository, jobRepository, scriptExecutor, objectMapper,
                script.toString());
        tenantId = UUID.randomUUID();
        jobId = UUID.randomUUID();

        job = AiTrainingJob.builder()
                .id(jobId)
                .tenantId(tenantId)
                .modelId(UUID.randomUUID())
                .name("Demand retrain")
                .status("PENDING")
                .build();
    }

    @Test
    void runJob_failsHonestlyWhenNoDemandHistory() {
        when(jobRepository.findById(jobId)).thenReturn(Optional.of(job));
        when(trainingDataService.getDemandRecordCount(tenantId, 90)).thenReturn(0L);

        AiTrainingJob result = service.runJob(jobId);

        assertThat(result).isSameAs(job);
        verify(pipelineService).failJob(eq(jobId), anyString());
        verify(pipelineService, never()).startJob(jobId);
    }

    @Test
    void runJob_failsWhenTrainerScriptMissing() {
        AiTrainingOrchestrationService noScript = new AiTrainingOrchestrationService(
                trainingDataService, pipelineService, artifactService,
                modelRepository, versionRepository, jobRepository, scriptExecutor, objectMapper,
                "/nonexistent/forecast.py");
        job.setTenantId(tenantId);
        when(jobRepository.findById(jobId)).thenReturn(Optional.of(job));
        when(trainingDataService.getDemandRecordCount(tenantId, 90)).thenReturn(120L);
        when(trainingDataService.exportDemandJsonl(tenantId, 90)).thenReturn("{\"sku\":\"S\",\"date\":\"2024-01-01\",\"demand\":1}\n");

        noScript.runJob(jobId);

        verify(pipelineService).startJob(jobId);
        verify(pipelineService).failJob(eq(jobId), org.mockito.ArgumentMatchers.contains("Training script not found"));
    }

    @Test
    void runJob_completesWithRealMetricsAndAttachesArtifacts() throws Exception {
        job.setTenantId(tenantId);
        when(jobRepository.findById(jobId)).thenReturn(Optional.of(job));
        when(trainingDataService.getDemandRecordCount(tenantId, 90)).thenReturn(250L);
        when(trainingDataService.exportDemandJsonl(tenantId, 90)).thenReturn(
                "{\"sku\":\"SKU-1\",\"date\":\"2024-01-01\",\"demand\":5}\n");

        AiModelVersion version = AiModelVersion.builder()
                .id(UUID.randomUUID())
                .modelId(job.getModelId())
                .version("v1.0.0")
                .status("VALIDATING")
                .build();

        when(scriptExecutor.execute(any(Path.class), any(Path.class), any(Path.class)))
                .thenAnswer(inv -> {
                    Path outputDir = inv.getArgument(2, Path.class);
                    Files.createDirectories(outputDir);
                    Files.writeString(outputDir.resolve("metrics.json"),
                            "{\"mae\": 1.25, \"rmse\": 2.5, \"wape\": 0.31, \"pinball\": 0.18}");
                    Files.writeString(outputDir.resolve("feature_columns.json"),
                            "[\"day_of_week\",\"month\"]");
                    Files.writeString(outputDir.resolve("calibration.json"),
                            "{\"SKU-1\": 1.02}");
                    Files.write(outputDir.resolve("model.onnx"), new byte[]{1, 2, 3});
                    return new TrainingScriptExecutor.TrainingScriptResult(0, outputDir, "ok");
                });

        when(versionRepository.findTopByTrainingJobIdOrderByCreatedAtDesc(jobId)).thenReturn(Optional.of(version));
        when(jobRepository.findById(jobId)).thenReturn(Optional.of(job));

        AiTrainingJob result = service.runJob(jobId);

        verify(pipelineService).startJob(jobId);
        verify(pipelineService).completeJob(eq(jobId), org.mockito.ArgumentMatchers.argThat(results -> {
            Object mae = results.get("mae");
            return mae != null && ((Number) mae).doubleValue() == 1.25;
        }));
        verify(artifactService).storeArtifact(eq(version.getModelId()), eq(version.getId()),
                eq(new byte[]{1, 2, 3}), eq("model.onnx"));
        verify(artifactService).attachMetadata(eq(tenantId), eq(version.getModelId()), eq(version.getId()),
                org.mockito.ArgumentMatchers.eq(java.util.List.of("day_of_week", "month")),
                org.mockito.ArgumentMatchers.argThat(ratios -> ratios.containsKey("SKU-1")),
                eq("GLOBAL_RATIO"));
        assertThat(result).isSameAs(job);
    }

    @Test
    void runJob_failsWhenTrainerExitsNonZero() throws Exception {
        job.setTenantId(tenantId);
        when(jobRepository.findById(jobId)).thenReturn(Optional.of(job));
        when(trainingDataService.getDemandRecordCount(tenantId, 90)).thenReturn(250L);
        when(trainingDataService.exportDemandJsonl(tenantId, 90)).thenReturn("{\"sku\":\"S\",\"date\":\"2024-01-01\",\"demand\":1}\n");
        when(scriptExecutor.execute(any(Path.class), any(Path.class), any(Path.class)))
                .thenAnswer(inv -> {
                    Path outputDir = inv.getArgument(2, Path.class);
                    Files.createDirectories(outputDir);
                    return new TrainingScriptExecutor.TrainingScriptResult(2, outputDir, "boom");
                });

        AiTrainingJob result = service.runJob(jobId);

        verify(pipelineService).startJob(jobId);
        verify(pipelineService).failJob(eq(jobId), org.mockito.ArgumentMatchers.contains("exited with code 2"));
        verify(pipelineService, never()).completeJob(any(), any());
    }

    @Test
    void runJob_failsOnScriptIoError() throws Exception {
        job.setTenantId(tenantId);
        when(jobRepository.findById(jobId)).thenReturn(Optional.of(job));
        when(trainingDataService.getDemandRecordCount(tenantId, 90)).thenReturn(250L);
        when(trainingDataService.exportDemandJsonl(tenantId, 90)).thenReturn("{}");
        when(scriptExecutor.execute(any(Path.class), any(Path.class), any(Path.class)))
                .thenThrow(new IOException("python3 not found"));

        AiTrainingJob result = service.runJob(jobId);

        verify(pipelineService).failJob(eq(jobId), org.mockito.ArgumentMatchers.contains("Trainer I/O error"));
    }
}
