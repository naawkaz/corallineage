package com.corallineage.backend.versioning.dataset;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

@Service
public class DvcIntegrationService {

    private final Path workingDirectory;
    private final String dataDir;
    private final String remoteName;

    public DvcIntegrationService(
            @Value("${app.dvc.working-directory}") Path workingDirectory,
            @Value("${app.dvc.data-dir}") String dataDir,
            @Value("${app.dvc.remote-name}") String remoteName) {
        this.workingDirectory = workingDirectory;
        this.dataDir = dataDir;
        this.remoteName = remoteName;
    }

    public String commitCurrentState(String versionTag) {
        Path dataPath = workingDirectory.resolve(dataDir);
        if (!Files.exists(dataPath)) {
            throw new DvcIntegrationException(
                    "Dataset directory does not exist: " + dataPath);
        }
        run(List.of("dvc", "add", dataDir));
        run(List.of("git", "add", "--", dataDir + ".dvc"));
        run(List.of("git", "commit", "--allow-empty", "-m",
                "DVC dataset snapshot: " + versionTag, "--", dataDir + ".dvc"));
        run(List.of("dvc", "push", "-r", remoteName));
        return run(List.of("git", "rev-parse", "HEAD")).trim();
    }

    private String run(List<String> command) {
        ProcessBuilder processBuilder = new ProcessBuilder(command);
        processBuilder.directory(workingDirectory.toFile());
        processBuilder.redirectErrorStream(true);
        try {
            Process process = processBuilder.start();
            String output = new String(process.getInputStream().readAllBytes(), StandardCharsets.UTF_8);
            int exitCode = process.waitFor();
            if (exitCode != 0) {
                throw new DvcIntegrationException(
                        "Command failed with exit code " + exitCode + ": " + String.join(" ", command) + "\n" + output);
            }
            return output;
        } catch (IOException | InterruptedException ex) {
            Thread.currentThread().interrupt();
            throw new DvcIntegrationException(
                    "Failed to run command: " + String.join(" ", command), ex);
        }
    }
}
