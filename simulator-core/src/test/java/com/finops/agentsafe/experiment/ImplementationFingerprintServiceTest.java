package com.finops.agentsafe.experiment;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.File;
import java.nio.file.Files;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.*;

class ImplementationFingerprintServiceTest {

    @Test
    void testSameSourceTreeProducesSameFingerprint(@TempDir Path tempDir) throws Exception {
        Path src = tempDir.resolve("src/main/java/com/example");
        Files.createDirectories(src);
        Files.writeString(src.resolve("App.java"), "package com.example; public class App {}");
        Files.writeString(tempDir.resolve("pom.xml"), "<project></project>");

        ImplementationFingerprintService service1 = new ImplementationFingerprintService(tempDir.toFile());
        ImplementationFingerprintService service2 = new ImplementationFingerprintService(tempDir.toFile());

        String hash1 = service1.calculate();
        String hash2 = service2.calculate();

        assertNotNull(hash1);
        assertEquals(64, hash1.length());
        assertEquals(hash1, hash2);
    }

    @Test
    void testModifyingFileContentChangesFingerprint(@TempDir Path tempDir) throws Exception {
        Path src = tempDir.resolve("src/main/java/com/example");
        Files.createDirectories(src);
        Path file = src.resolve("App.java");
        Files.writeString(file, "package com.example; public class App {}");
        Files.writeString(tempDir.resolve("pom.xml"), "<project></project>");

        ImplementationFingerprintService service = new ImplementationFingerprintService(tempDir.toFile());
        String initialHash = service.calculate();

        Files.writeString(file, "package com.example; public class App { // modified }");
        String modifiedHash = service.calculate();

        assertNotEquals(initialHash, modifiedHash);
    }

    @Test
    void testModifyingResourceChangesFingerprint(@TempDir Path tempDir) throws Exception {
        Path res = tempDir.resolve("src/main/resources");
        Files.createDirectories(res);
        Path file = res.resolve("application.yml");
        Files.writeString(file, "spring:\n  profiles: default");

        ImplementationFingerprintService service = new ImplementationFingerprintService(tempDir.toFile());
        String initialHash = service.calculate();

        Files.writeString(file, "spring:\n  profiles: dev");
        String modifiedHash = service.calculate();

        assertNotEquals(initialHash, modifiedHash);
    }

    @Test
    void testFileOrderingDoesNotChangeFingerprint(@TempDir Path tempDir) throws Exception {
        Path src = tempDir.resolve("src/main/java/com/example");
        Files.createDirectories(src);
        Files.writeString(src.resolve("B.java"), "public class B {}");
        Files.writeString(src.resolve("A.java"), "public class A {}");

        ImplementationFingerprintService service = new ImplementationFingerprintService(tempDir.toFile());
        String hash1 = service.calculate();

        // Create new service instance and calculate again
        ImplementationFingerprintService service2 = new ImplementationFingerprintService(tempDir.toFile());
        String hash2 = service2.calculate();

        assertEquals(hash1, hash2);
    }

    @Test
    void testGeneratedResultsAndTargetFilesDoNotAffectFingerprint(@TempDir Path tempDir) throws Exception {
        Path src = tempDir.resolve("src/main/java/com/example");
        Files.createDirectories(src);
        Files.writeString(src.resolve("App.java"), "public class App {}");

        ImplementationFingerprintService service = new ImplementationFingerprintService(tempDir.toFile());
        String initialHash = service.calculate();

        // Add target and results directories with artifacts
        Path targetDir = tempDir.resolve("target/classes");
        Files.createDirectories(targetDir);
        Files.writeString(targetDir.resolve("App.class"), "bytecode");

        Path resultsDir = tempDir.resolve("results/experiments/gemini");
        Files.createDirectories(resultsDir);
        Files.writeString(resultsDir.resolve("result.json"), "{}");

        String hashWithGeneratedFiles = service.calculate();
        assertEquals(initialHash, hashWithGeneratedFiles);
    }

    @Test
    void testModelMismatchFailsClosed() {
        RepeatabilityExperimentRunner runner = new RepeatabilityExperimentRunner(null, null, null, null, null, null, null);
        
        Exception ex = assertThrows(IllegalArgumentException.class, () -> {
            runner.runExperiment("gemini-3.5-flash", true);
        });

        assertTrue(ex.getMessage().contains("CANONICAL_EXPERIMENT_ABORTED_MODEL_MISMATCH"));
    }
}
