package com.finops.agentsafe.experiment;

import org.springframework.stereotype.Service;

import java.io.File;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.MessageDigest;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.stream.Stream;

/**
 * Deterministic Java implementation fingerprint service for Phase 5B benchmark experiments.
 * Computes a SHA-256 digest over all relevant source and configuration files.
 */
@Service
public class ImplementationFingerprintService {

    private final File baseDir;

    public ImplementationFingerprintService() {
        this.baseDir = resolveProjectRoot();
    }

    public ImplementationFingerprintService(File baseDir) {
        this.baseDir = baseDir;
    }

    public String calculate() {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            List<Path> filesToHash = new ArrayList<>();
            Path rootPath = baseDir.toPath().toAbsolutePath().normalize();

            // Include src/main, src/test, pom.xml
            Path srcPath = rootPath.resolve("src");
            if (Files.exists(srcPath)) {
                try (Stream<Path> stream = Files.walk(srcPath)) {
                    stream.filter(Files::isRegularFile)
                          .filter(this::isRelevantFile)
                          .forEach(filesToHash::add);
                }
            }

            Path pomPath = rootPath.resolve("pom.xml");
            if (Files.exists(pomPath) && Files.isRegularFile(pomPath)) {
                filesToHash.add(pomPath);
            }

            // 1. Sort files deterministically by relative normalized path string
            filesToHash.sort(Comparator.comparing(p -> normalizeRelativePath(rootPath, p)));

            // 2. Hash relative path and file contents
            for (Path path : filesToHash) {
                String relPath = normalizeRelativePath(rootPath, path);
                byte[] pathBytes = relPath.getBytes(StandardCharsets.UTF_8);

                // Length framing for path
                digest.update((byte) (pathBytes.length >> 24));
                digest.update((byte) (pathBytes.length >> 16));
                digest.update((byte) (pathBytes.length >> 8));
                digest.update((byte) pathBytes.length);
                digest.update(pathBytes);

                // File contents
                try (InputStream is = Files.newInputStream(path)) {
                    byte[] buffer = new byte[8192];
                    int read;
                    while ((read = is.read(buffer)) != -1) {
                        digest.update(buffer, 0, read);
                    }
                }
            }

            byte[] hashBytes = digest.digest();
            StringBuilder hexString = new StringBuilder();
            for (byte b : hashBytes) {
                String hex = Integer.toHexString(0xff & b);
                if (hex.length() == 1) hexString.append('0');
                hexString.append(hex);
            }

            return hexString.toString();
        } catch (Exception e) {
            throw new RuntimeException("Failed to calculate implementation fingerprint", e);
        }
    }

    private boolean isRelevantFile(Path path) {
        String p = path.toString().replace('\\', '/');
        if (p.contains("/target/") || p.contains("/results/") || p.contains("/.git/") || p.contains("/.idea/") || p.contains("/.vscode/") || p.contains("/development-debug/")) {
            return false;
        }
        if (p.endsWith(".log") || p.endsWith(".tmp") || p.endsWith(".class") || p.endsWith(".DS_Store")) {
            return false;
        }
        return true;
    }

    private String normalizeRelativePath(Path root, Path file) {
        try {
            return root.relativize(file).toString().replace('\\', '/');
        } catch (IllegalArgumentException e) {
            return file.getFileName().toString();
        }
    }

    private static File resolveProjectRoot() {
        try {
            File currentDir = new File(".").getCanonicalFile();
            if (currentDir.getName().equalsIgnoreCase("simulator-core")) {
                return currentDir;
            }
            File simCore = new File(currentDir, "simulator-core");
            if (simCore.exists() && simCore.isDirectory()) {
                return simCore;
            }
            return currentDir;
        } catch (Exception e) {
            return new File(".");
        }
    }
}
