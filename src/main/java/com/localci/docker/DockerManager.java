package com.localci.docker;

import com.localci.logger.PipelineLogger;
import com.localci.model.Service;
import com.localci.tui.TerminalUI;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.util.Map;
import java.util.concurrent.TimeUnit;

/**
 * Manages the Docker container lifecycle by shelling out to the
 * {@code docker} CLI. No SDK dependency required.
 * <p>
 * Lifecycle:
 * 1. {@link #pullImage} — pull the image (if not cached)
 * 2. {@link #createContainer} — start a detached container
 * 3. {@link #exec} — run commands inside it
 * 4. {@link #cleanup} — stop and remove the container
 */
public class DockerManager {

    private final TerminalUI ui;

    /**
     * @param ui the terminal UI for streaming output (nullable for headless mode)
     */
    public DockerManager(TerminalUI ui) {
        this.ui = ui;
    }

    /**
     * Pulls the Docker image. Streams progress to the TUI.
     */
    public void pullImage(String image) throws IOException {
        log("Pulling image: " + image + " ...");

        ProcessBuilder pb = new ProcessBuilder("docker", "pull", image);
        pb.redirectErrorStream(true);
        Process process = pb.start();

        try (BufferedReader reader = new BufferedReader(
                new InputStreamReader(process.getInputStream()))) {
            String line;
            while ((line = reader.readLine()) != null) {
                log(line);
            }
        }

        try {
            int exitCode = process.waitFor();
            if (exitCode != 0) {
                throw new IOException("docker pull failed (exit " + exitCode + ")");
            }
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new IOException("docker pull interrupted", e);
        }

        log("Image ready: " + image);
    }

    /**
     * Creates a custom Docker bridge network for the pipeline run.
     * 
     * @return the network name/ID
     */
    public String createNetwork() throws IOException {
        String netName = "localci-net-" + System.currentTimeMillis();
        log("Creating custom Docker network: " + netName + " ...");

        ProcessBuilder pb = new ProcessBuilder("docker", "network", "create", "--driver", "bridge", netName);
        pb.redirectErrorStream(true);
        Process process = pb.start();

        String networkId;
        try (BufferedReader reader = new BufferedReader(new InputStreamReader(process.getInputStream()))) {
            networkId = reader.readLine();
            // Read remaining output to unblock the process
            while (reader.readLine() != null) {
            }
        }

        try {
            int exitCode = process.waitFor();
            if (exitCode != 0 || networkId == null || networkId.isBlank()) {
                throw new IOException("Failed to create network (exit " + exitCode + "): " + networkId);
            }
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new IOException("docker network create interrupted", e);
        }

        networkId = networkId.trim();
        // Return the name we generated instead of the hash, it's easier to debug
        return netName;
    }

    /**
     * Starts a background service container attached to the given network.
     */
    public String startService(com.localci.model.Service service, String networkId) throws IOException {
        log("Starting service: " + service.getName() + " (" + service.getImage() + ") ...");
        pullImage(service.getImage()); // Ensure image is available

        Process process = getProcess(service, networkId);

        String containerId;
        try (BufferedReader reader = new BufferedReader(new InputStreamReader(process.getInputStream()))) {
            containerId = reader.readLine();
        }

        try {
            int exitCode = process.waitFor();
            if (exitCode != 0 || containerId == null || containerId.isBlank()) {
                throw new IOException("Failed to start service " + service.getName() + " (exit " + exitCode + ")");
            }
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new IOException("docker run (service) interrupted", e);
        }

        containerId = containerId.trim().substring(0, 12);
        log("Service '" + service.getName() + "' running: " + containerId);
        return containerId;
    }

    private static Process getProcess(Service service, String networkId) throws IOException {
        var command = new java.util.ArrayList<String>();
        command.add("docker");
        command.add("run");
        command.add("-d");
        command.add("--rm");
        command.add("--name");
        command.add(service.getName()); // Allows DNS resolution by name!
        command.add("--network");
        command.add(networkId);

        if (service.getPorts() != null) {
            for (String portMap : service.getPorts()) {
                command.add("-p");
                command.add(portMap);
            }
        }

        if (service.getEnv() != null) {
            for (var entry : service.getEnv().entrySet()) {
                command.add("-e");
                command.add(entry.getKey() + "=" + entry.getValue());
            }
        }

        command.add(service.getImage());

        ProcessBuilder pb = new ProcessBuilder(command);
        pb.redirectErrorStream(true);
        return pb.start();
    }

    /**
     * Creates and starts a detached container.
     * Mounts the workspace directory at {@code /workspace} inside the container
     * so steps can access project files (e.g. for {@code mvn build}, {@code git}).
     * Runs {@code sleep infinity} to keep it alive for exec calls.
     *
     * @param image         Docker image (e.g. "alpine:3.19")
     * @param globalEnv     global env vars to inject via -e flags
     * @param workspacePath host directory to mount (typically the pipeline file's
     *                      directory)
     * @param networkId     custom network to attach to (nullable)
     * @param cacheMounts   list of volume mappings, e.g., "npm-cache:/root/.npm"
     * @return the container ID (first 12 chars)
     */
    public String createContainer(String image, Map<String, String> globalEnv,
            String workspacePath, String networkId, java.util.List<String> cacheMounts) throws IOException {
        var command = new java.util.ArrayList<String>();
        command.add("docker");
        command.add("run");
        command.add("-d");
        command.add("--rm");

        // Attach to custom network if running services
        if (networkId != null && !networkId.isBlank()) {
            command.add("--network");
            command.add(networkId);
        }

        // Mount the host workspace into the container
        if (workspacePath != null && !workspacePath.isBlank()) {
            command.add("-v");
            command.add(workspacePath + ":/workspace");
            command.add("-w");
            command.add("/workspace");
            log("Mounting workspace: " + workspacePath + " → /workspace");
        }

        // Add cache volume mounts
        if (cacheMounts != null) {
            for (String cache : cacheMounts) {
                if (cache.contains(":")) {
                    command.add("-v");
                    command.add(cache);
                    log("Mounting cache: " + cache);
                } else {
                    log("[WARN] Invalid cache mapping: " + cache + ". Expected format 'name:container_path'");
                }
            }
        }

        // Inject global env vars
        if (globalEnv != null) {
            for (var entry : globalEnv.entrySet()) {
                command.add("-e");
                command.add(entry.getKey() + "=" + entry.getValue());
            }
        }

        command.add(image);
        command.add("sleep");
        command.add("infinity");

        ProcessBuilder pb = new ProcessBuilder(command);
        pb.redirectErrorStream(true);
        Process process = pb.start();

        String containerId;
        try (BufferedReader reader = new BufferedReader(
                new InputStreamReader(process.getInputStream()))) {
            containerId = reader.readLine();
        }

        try {
            int exitCode = process.waitFor();
            if (exitCode != 0 || containerId == null || containerId.isBlank()) {
                throw new IOException(
                        "Failed to create container (exit " + exitCode + "): " + containerId);
            }
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new IOException("docker run interrupted", e);
        }

        containerId = containerId.trim().substring(0, 12);
        log("Container started: " + containerId);
        return containerId;
    }

    /**
     * Executes a command inside the running container.
     * Streams stdout/stderr through the TUI.
     *
     * @return the exit code (-1 on timeout)
     */
    public int exec(String containerId, String command, int timeoutSecs)
            throws IOException {

        ProcessBuilder pb = new ProcessBuilder(
                "docker", "exec", containerId, "sh", "-c", command);
        pb.redirectErrorStream(true);
        Process process = pb.start();

        // Stream output line by line to the TUI
        try (BufferedReader reader = new BufferedReader(
                new InputStreamReader(process.getInputStream()))) {
            String line;
            while ((line = reader.readLine()) != null) {
                log(line);
            }
        }

        try {
            if (timeoutSecs > 0) {
                boolean finished = process.waitFor(timeoutSecs, TimeUnit.SECONDS);
                if (!finished) {
                    process.destroyForcibly();
                    return -1;
                }
            } else {
                process.waitFor();
            }
            return process.exitValue();
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            process.destroyForcibly();
            return -1;
        }
    }

    /**
     * Copies files/directories from the container to the host machine.
     *
     * @param containerId the running container
     * @param artifacts   list of paths to extract (e.g. "/workspace/target/*.jar")
     * @param destDir     the host directory to save artifacts into
     */
    public void copyArtifacts(String containerId, java.util.List<String> artifacts, String destDir) {
        if (artifacts == null || artifacts.isEmpty())
            return;

        java.io.File dest = new java.io.File(destDir);
        if (!dest.exists() && !dest.mkdirs()) {
            log("[WARN] Failed to create artifacts directory: " + destDir);
            return;
        }

        log("Extracting " + artifacts.size() + " artifact path(s) to " + destDir + " ...");

        for (String sourcePath : artifacts) {
            try {
                // E.g: docker cp containerId:/workspace/target/. ./artifacts/
                int exitCode = getExitCode(containerId, destDir, sourcePath);
                if (exitCode == 0) {
                    log("  ✓ Extracted: " + sourcePath);
                } else {
                    log("  ✗ Failed to extract (exit " + exitCode + "): " + sourcePath);
                }
            } catch (Exception e) {
                log("[WARN] Exception extracting artifact '" + sourcePath + "': " + e.getMessage());
            }
        }
    }

    private static int getExitCode(String containerId, String destDir, String sourcePath) throws IOException, InterruptedException {
        ProcessBuilder pb = new ProcessBuilder("docker", "cp", containerId + ":" + sourcePath, destDir);
        pb.redirectErrorStream(true);
        Process process = pb.start();

        // Read output to prevent freezing
        try (BufferedReader reader = new BufferedReader(new InputStreamReader(process.getInputStream()))) {
            while (reader.readLine() != null) {
            }
        }

        return process.waitFor();
    }

    /**
     * Stops the container. Called in a finally block.
     */
    public void cleanup(String containerId) {
        log("Stopping container: " + containerId + " ...");
        try {
            ProcessBuilder pb = new ProcessBuilder("docker", "stop", containerId);
            pb.redirectErrorStream(true);
            Process process = pb.start();
            process.waitFor(10, TimeUnit.SECONDS);
            log("Container removed.");
        } catch (Exception e) {
            PipelineLogger.error("Cleanup failed: " + e.getMessage());
        }
    }

    // ── Helper ───────────────────────────────────────────

    private void log(String message) {
        if (ui != null) {
            ui.appendLog(message);
        } else {
            PipelineLogger.info(message);
        }
    }
}
