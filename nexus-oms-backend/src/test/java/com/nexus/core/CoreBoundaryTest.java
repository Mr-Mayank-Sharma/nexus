package com.nexus.core;

import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.stream.Stream;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;

/** The core is shared by the OMS and, later, the WMS, so it must never depend on OMS code. */
class CoreBoundaryTest {

    @Test
    void coreNeverImportsOmsCode() throws IOException {
        Path core = Path.of("src/main/java/com/nexus/core");
        try (Stream<Path> files = Files.walk(core)) {
            List<String> offenders = files.filter(p -> p.toString().endsWith(".java"))
                    .filter(p -> {
                        try {
                            return Files.readString(p).contains("com.nexus.oms");
                        } catch (IOException e) {
                            throw new RuntimeException(e);
                        }
                    })
                    .map(Path::toString).toList();
            assertFalse(Files.notExists(core), "core sources not found");
            assertEquals(List.of(), offenders, "core classes must not use com.nexus.oms");
        }
    }
}
