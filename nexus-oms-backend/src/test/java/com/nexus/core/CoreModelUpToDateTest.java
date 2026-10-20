package com.nexus.core;

import org.junit.jupiter.api.Assumptions;
import org.junit.jupiter.api.Test;

import java.io.File;
import java.nio.charset.StandardCharsets;
import java.util.concurrent.TimeUnit;

import static org.junit.jupiter.api.Assertions.assertEquals;

/** Generated schema and entities must match scripts/core_model_spec.py; edits to generated files are caught here. */
class CoreModelUpToDateTest {

    @Test
    void generatedFilesMatchTheSpec() throws Exception {
        File script = new File("../scripts/gen_core_model.py");
        Assumptions.assumeTrue(script.exists(), "generator not found (building outside the repository)");
        Process p;
        try {
            p = new ProcessBuilder("python3", script.getPath(), "--check").redirectErrorStream(true).start();
        } catch (java.io.IOException noPython) {
            Assumptions.abort("python3 is not installed");
            return;
        }
        boolean done = p.waitFor(60, TimeUnit.SECONDS);
        String out = new String(p.getInputStream().readAllBytes(), StandardCharsets.UTF_8);
        assertEquals(true, done, "generator timed out");
        assertEquals(0, p.exitValue(), "Generated core files are out of date. Run python3 scripts/gen_core_model.py\n" + out);
    }
}
