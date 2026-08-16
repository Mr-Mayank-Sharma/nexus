package com.nexus.oms.service.ai;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.concurrent.TimeUnit;

@Component
public class ProcessTrainingScriptExecutor implements TrainingScriptExecutor {

    private static final Logger log = LoggerFactory.getLogger(ProcessTrainingScriptExecutor.class);

    private final long timeoutSeconds;

    public ProcessTrainingScriptExecutor() {
        this(600);
    }

    public ProcessTrainingScriptExecutor(long timeoutSeconds) {
        this.timeoutSeconds = timeoutSeconds;
    }

    @Override
    public TrainingScriptResult execute(Path script, Path input, Path outputDir) throws IOException {
        Files.createDirectories(outputDir);
        ProcessBuilder pb = new ProcessBuilder(
                "python3", script.toAbsolutePath().toString(),
                "--input", input.toAbsolutePath().toString(),
                "--output-dir", outputDir.toAbsolutePath().toString());
        pb.redirectErrorStream(true);

        log.info("Running training script: {}", pb.command());
        Process process = pb.start();
        String output;
        try {
            output = new String(process.getInputStream().readAllBytes(), StandardCharsets.UTF_8);
            boolean finished = process.waitFor(timeoutSeconds, TimeUnit.SECONDS);
            if (!finished) {
                process.destroyForcibly();
                throw new IOException("Training script timed out after " + timeoutSeconds + "s");
            }
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            process.destroyForcibly();
            throw new IOException("Training script interrupted", e);
        }

        int exitCode = process.exitValue();
        if (exitCode != 0) {
            log.warn("Training script exited {}: {}", exitCode, output);
        }
        return new TrainingScriptResult(exitCode, outputDir, output);
    }
}
