package com.nexus.oms.service.ai;

import java.io.IOException;
import java.nio.file.Path;

public interface TrainingScriptExecutor {

    record TrainingScriptResult(int exitCode, Path outputDir, String output) {}

    TrainingScriptResult execute(Path script, Path input, Path outputDir) throws IOException;
}
