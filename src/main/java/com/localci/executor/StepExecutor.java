package com.localci.executor;

import com.localci.docker.DockerManager;
import com.localci.logger.PipelineLogger;
import com.localci.model.Step;
import com.localci.model.StepResult;
import com.localci.model.StepStatus;
import com.localci.tui.TerminalUI;

import java.io.BufferedReader;
import java.io.File;
import java.io.IOException;
import java.io.InputStreamReader;
import java.util.Map;
import java.util.concurrent.TimeUnit;

/**
 * Executes a single pipeline {@link Step} either locally or
 * inside a Docker container.
 *
 * Features:
 * - Docker execution via {@link DockerManager}
 * - Local execution with OS-aware shell
 * - Timeout enforcement
 * - Retry logic
 * - Environment variable merging
 * - Log streaming through {@link TerminalUI}
 */
public class StepExecutor {

    private static final boolean IS_WINDOWS = System.getProperty("os.name").toLowerCase().contains("win");

    private final Map<String, String> globalEnv;
    private final DockerManager dockerManager;
    private final String containerId;
    private final TerminalUI ui;

    /**
     * Local-only constructor (no Docker, no TUI).
     */
    public StepExecutor(Map<String, String> globalEnv) {
        this(globalEnv, null, null, null);
    }

    /**
     * Full constructor with Docker + TUI support.
     */
    public StepExecutor(Map<String, String> globalEnv,
            DockerManager dockerManager,
            String containerId,
            TerminalUI ui) {
        this.globalEnv = (globalEnv != null) ? globalEnv : Map.of();
        this.dockerManager = dockerManager;
        this.containerId = containerId;
        this.ui = ui;
    }

    /**
     * Executes the step with retry logic.
     */
    public StepResult execute(Step step) {
        if (step.getIf() != null && !step.getIf().isBlank()) {
            boolean shouldRun = evaluateCondition(step.getIf());
            if (!shouldRun) {
                return new StepResult(step.getName(), StepStatus.SKIPPED, 0, 0, 0);
            }
        }

        int maxAttempts = step.getRetries() + 1;
        long totalStartNanos = System.nanoTime();

        for (int attempt = 1; attempt <= maxAttempts; attempt++) {
            if (attempt > 1) {
                logMessage("Retry " + attempt + "/" + maxAttempts
                        + " for '" + step.getName() + "'");
            }

            StepResult result = executeSingle(step, attempt, totalStartNanos);

            if (result.status() == StepStatus.PASSED
                    || result.status() == StepStatus.TIMED_OUT) {
                return result;
            }

            if (attempt == maxAttempts) {
                return result;
            }
        }

        long durationMs = (System.nanoTime() - totalStartNanos) / 1_000_000;
        return new StepResult(step.getName(), StepStatus.FAILED, durationMs, -1, maxAttempts);
    }

    // ── Execution strategies ─────────────────────────────

    private StepResult executeSingle(Step step, int attempt, long totalStartNanos) {
        if (dockerManager != null && containerId != null) {
            return executeInDocker(step, attempt, totalStartNanos);
        }
        return executeLocally(step, attempt, totalStartNanos);
    }

    private StepResult executeInDocker(Step step, int attempt, long startNanos) {
        try {
            String runCmd = "[ -f /workspace/.localci_env ] && . /workspace/.localci_env; " + step.getRun();
            int exitCode = dockerManager.exec(containerId, runCmd, step.getTimeout());
            long durationMs = (System.nanoTime() - startNanos) / 1_000_000;

            if (exitCode == -1) {
                logMessage("Step '" + step.getName() + "' timed out");
                return new StepResult(step.getName(), StepStatus.TIMED_OUT,
                        durationMs, -1, attempt);
            } else if (exitCode == 0) {
                return new StepResult(step.getName(), StepStatus.PASSED,
                        durationMs, 0, attempt);
            } else {
                return new StepResult(step.getName(), StepStatus.FAILED,
                        durationMs, exitCode, attempt);
            }
        } catch (IOException e) {
            long durationMs = (System.nanoTime() - startNanos) / 1_000_000;
            logMessage("Exception: " + e.getMessage());
            return new StepResult(step.getName(), StepStatus.FAILED,
                    durationMs, -1, attempt);
        }
    }

    private StepResult executeLocally(Step step, int attempt, long startNanos) {
        try {
            ProcessBuilder builder = createLocalProcessBuilder(step);
            builder.redirectErrorStream(true);

            Process process = builder.start();

            // Stream output
            try (BufferedReader reader = new BufferedReader(
                    new InputStreamReader(process.getInputStream()))) {
                String line;
                while ((line = reader.readLine()) != null) {
                    logMessage(line);
                }
            }

            boolean finished;
            if (step.getTimeout() > 0) {
                finished = process.waitFor(step.getTimeout(), TimeUnit.SECONDS);
            } else {
                process.waitFor();
                finished = true;
            }

            long durationMs = (System.nanoTime() - startNanos) / 1_000_000;

            if (!finished) {
                process.destroyForcibly();
                return new StepResult(step.getName(), StepStatus.TIMED_OUT,
                        durationMs, -1, attempt);
            }

            int exitCode = process.exitValue();
            StepStatus status = (exitCode == 0) ? StepStatus.PASSED : StepStatus.FAILED;
            return new StepResult(step.getName(), status, durationMs, exitCode, attempt);

        } catch (IOException | InterruptedException e) {
            long durationMs = (System.nanoTime() - startNanos) / 1_000_000;
            logMessage("Exception: " + e.getMessage());
            return new StepResult(step.getName(), StepStatus.FAILED,
                    durationMs, -1, attempt);
        }
    }

    private ProcessBuilder createLocalProcessBuilder(Step step) {
        ProcessBuilder builder;
        if (IS_WINDOWS) {
            builder = new ProcessBuilder("cmd", "/c", step.getRun());
        } else {
            builder = new ProcessBuilder("sh", "-c", step.getRun());
        }

        builder.environment().putAll(globalEnv);
        if (step.getEnv() != null) {
            builder.environment().putAll(step.getEnv());
        }

        if (step.getWorkingDir() != null && !step.getWorkingDir().isBlank()) {
            builder.directory(new File(step.getWorkingDir()));
        }

        return builder;
    }

    private boolean evaluateCondition(String condition) {
        try {
            if (dockerManager != null && containerId != null) {
                int exitCode = dockerManager.exec(containerId, "sh -c '" + condition + "'", 30);
                return exitCode == 0;
            } else {
                ProcessBuilder builder;
                if (IS_WINDOWS) {
                    builder = new ProcessBuilder("cmd", "/c", condition);
                } else {
                    builder = new ProcessBuilder("sh", "-c", condition);
                }
                builder.environment().putAll(globalEnv);
                Process process = builder.start();
                process.waitFor(30, TimeUnit.SECONDS);
                return process.exitValue() == 0;
            }
        } catch (Exception e) {
            logMessage("Error evaluating condition '" + condition + "': " + e.getMessage());
            return false;
        }
    }

    private void logMessage(String message) {
        if (ui != null) {
            ui.appendLog(message);
        } else {
            System.out.println(message);
        }
    }
}
